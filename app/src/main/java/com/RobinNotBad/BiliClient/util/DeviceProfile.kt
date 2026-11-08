package com.RobinNotBad.BiliClient.util

import android.content.Context

/**
 * 设备档位画像（批次 2 弹幕内核）。
 *
 * 为什么要有这一层：弹幕行数这个参数原来散落在各调用点，要么写死、要么各自去读一份
 * [PerformanceManager] 的实时状态。批次 2 起统一从这里取一份**不可变快照** [Profile]，
 * 好处有两个：
 *
 * 1. **纯逻辑可单测**：档位 → 参数的映射是纯函数（[tierOf] / [profileForTier] / [capDanmakuMaxLines]），
 *    不需要 Context，单测直接断言三档取值（见 `DeviceProfileTest`）；
 * 2. **同一段逻辑里只有一个真相**：播放器里多处同时读档位时，不会出现「弹幕按低档限行数、
 *    别处却按中档」的撕裂。
 *
 * 档位语义**完全沿用** [PerformanceManager]：低端 = 硬件档位为低**且**用户没有手动开高性能模式
 * （即 `isLowPerfLevel` 的定义），所以这里只接收它算好的三个输入，不自己重新判断硬件。
 *
 * **26.11.08 撤销了档位对「解码方式」和「清晰度」的干预**（这是 26.11.07 卡顿回归的根因）：
 *
 * - 原来 `preferSoftwareDecode` 让低端机默认软解，但真机诊断包实测同一台 1GB 手表软解 720P
 *   只画得出 14~17fps（源 30fps），卡顿由此而来；而「软解防花屏」没有任何线上证据
 *   （控制台全量反馈里「花屏」「绿屏」各 0 条）。用户拍板：**一般都不要软解**，解码默认恒为硬解，
 *   要软解请用户在「渲染与解码 → 解码方式」里自己选。
 * - 原来 `playQn` 给低端机封顶 360P，但主入口（`JumpToPlayerActivity` → `PlayerApi.jumpToPlayer`
 *   把 `player_data.qn` 塞进 `currentQuality` extra）根本走不到这个封顶，等于既没生效、又和
 *   用户自己配的清晰度打架。用户拍板：**清晰度听用户配置**，档位不再封顶。
 *
 * 所以现在档位只负责一件有实证的事：**弹幕同屏行数**。
 */
object DeviceProfile {

    /** 低端档：[PerformanceManager.PERF_LEVEL_LOW] 且用户未手动开高性能模式。 */
    const val TIER_LOW = 0

    /** 中端档：默认档（硬件探测失败、还没测出结果时也落在这里）。 */
    const val TIER_MEDIUM = 1

    /** 高端档：硬件档位为高，或用户手动开了高性能模式。 */
    const val TIER_HIGH = 2

    /**
     * 一份设备画像。
     *
     * @param level 档位，取值 [TIER_LOW] / [TIER_MEDIUM] / [TIER_HIGH]
     * @param danmakuMaxLines 同屏滚动弹幕行数上限。用户在设置里调得更大也会被这里压住
     *   （见 [capDanmakuMaxLines]）
     */
    data class Profile(
        val level: Int,
        val danmakuMaxLines: Int
    )

    /**
     * 低端档：512MB~1GB RAM、四核 A7/A53 级别的方屏/圆屏手表。
     *
     * - `danmakuMaxLines = 6`：DFM 每帧要对**每条同屏弹幕**做碰撞/避让判定，6 行是这类设备
     *   渲染线程还能留出绘制余量的上限（再高会先掉帧、再出现弹幕互相压字）。
     */
    private val LOW = Profile(
        level = TIER_LOW,
        danmakuMaxLines = 6
    )

    /**
     * 中端档：2GB RAM、主流手表（450x450 左右、方屏为主）。
     *
     * - `danmakuMaxLines = 10`：与设置页「弹幕最大行数」的默认值一致（见
     *   `SettingTerminalPlayerActivity` 里 `PLAYER_DANMAKU_MAXLINE` 的默认 "10"）。
     *   中端机把上限收到这个默认值：用户手调更高时同屏更容易互相遮挡，而更高档才有余量去渲染。
     */
    private val MEDIUM = Profile(
        level = TIER_MEDIUM,
        danmakuMaxLines = 10
    )

    /**
     * 高端档：3GB 以上 RAM、骁龙 W5/W4100 级别，或用户手动开了高性能模式。
     *
     * - `danmakuMaxLines = 15`：等于 `DanmakuManager` 读设置的默认值（`player_danmaku_maxline`
     *   默认 15），也就是高档不额外加码 —— 15 已经是圆屏上不会糊成一片的实用上限。
     */
    private val HIGH = Profile(
        level = TIER_HIGH,
        danmakuMaxLines = 15
    )

    /**
     * 档位判定（纯函数）。
     *
     * 顺序与 `PerformanceManager.applyPerformanceSettings()` 保持一致，避免两处判定打架：
     * 先判低端（低档 + 未手动开高性能），再判高端（手动开高性能 或 硬件高），其余都是中端。
     *
     * @param perfLevel [PerformanceManager.getCurrentPerfLevel] 的原始档位
     * @param lowPerfDevice [PerformanceManager.isLowPerfDevice] 的结果
     * @param effectiveHighPerf [PerformanceManager.isEffectiveHighPerf] 的结果
     */
    @JvmStatic
    fun tierOf(perfLevel: Int, lowPerfDevice: Boolean, effectiveHighPerf: Boolean): Int {
        if (lowPerfDevice) return TIER_LOW
        if (effectiveHighPerf || perfLevel == PerformanceManager.PERF_LEVEL_HIGH) return TIER_HIGH
        return TIER_MEDIUM
    }

    /** 档位 → 画像（纯函数）。非法档位按中端处理，绝不返回 null。 */
    @JvmStatic
    fun profileForTier(tier: Int): Profile = when (tier) {
        TIER_LOW -> LOW
        TIER_HIGH -> HIGH
        else -> MEDIUM
    }

    /** 三个判定输入直接映射成画像（纯函数，单测入口）。 */
    @JvmStatic
    fun profileOf(perfLevel: Int, lowPerfDevice: Boolean, effectiveHighPerf: Boolean): Profile =
        profileForTier(tierOf(perfLevel, lowPerfDevice, effectiveHighPerf))

    /**
     * 把用户设置里的「弹幕最大行数」压到档位上限内（纯函数）。
     *
     * 这是「接弹幕上限」的**唯一**落点：设置值非法（<=0）时按 1 行处理，超过档位上限时取上限，
     * 其余原样返回。保留用户调低的权利（用户设 3 行就还是 3 行）。
     */
    @JvmStatic
    fun capDanmakuMaxLines(requested: Int, profileMaxLines: Int): Int {
        val cap = if (profileMaxLines <= 0) 1 else profileMaxLines
        return requested.coerceIn(1, cap)
    }

    /**
     * 解析设备画像。
     *
     * [PerformanceManager.init] 内部有幂等守卫（`initialized`），重复调用无副作用；首次调用时
     * 它只做「读缓存 / 先落中档」，硬件探测在后台跑，所以本方法可以在主线程安全调用。
     */
    fun resolve(context: Context): Profile {
        PerformanceManager.init(context)
        return current()
    }

    /**
     * 读当前档位（不触发硬件探测，不碰 Context）。
     *
     * 供播放器每次加载弹幕时取用：档位在进程内是 [PerformanceManager] 缓好的几个
     * `@Volatile` 字段，读取代价可以忽略。
     */
    fun current(): Profile = profileOf(
        PerformanceManager.getCurrentPerfLevel(),
        PerformanceManager.isLowPerfDevice(),
        PerformanceManager.isEffectiveHighPerf()
    )
}
