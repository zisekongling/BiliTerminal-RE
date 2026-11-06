package com.RobinNotBad.BiliClient.util

import android.content.Context

/**
 * 设备档位画像（批次 2 弹幕内核）。
 *
 * 为什么要有这一层：清晰度、弹幕行数、解码方式这三个参数原来散落在各调用点，要么写死、
 * 要么各自去读一份 [PerformanceManager] 的实时状态。批次 2 起统一从这里取一份**不可变快照**
 * [Profile]，好处有两个：
 *
 * 1. **纯逻辑可单测**：档位 → 参数的映射是纯函数（[tierOf] / [profileForTier] / [capDanmakuMaxLines]），
 *    不需要 Context，单测直接断言三档取值（见 `DeviceProfileTest`）；
 * 2. **同一段逻辑里只有一个真相**：播放器里多处同时读档位时，不会出现「弹幕按低档限行数、
 *    清晰度却按中档」的撕裂。
 *
 * 档位语义**完全沿用** [PerformanceManager]：低端 = 硬件档位为低**且**用户没有手动开高性能模式
 * （即 `isLowPerfLevel` 的定义），所以这里只接收它算好的三个输入，不自己重新判断硬件。
 */
object DeviceProfile {

    /** 低端档：[PerformanceManager.PERF_LEVEL_LOW] 且用户未手动开高性能模式。 */
    const val TIER_LOW = 0

    /** 中端档：默认档（硬件探测失败、还没测出结果时也落在这里）。 */
    const val TIER_MEDIUM = 1

    /** 高端档：硬件档位为高，或用户手动开了高性能模式。 */
    const val TIER_HIGH = 2

    /**
     * 一份完整的设备画像。
     *
     * @param level 档位，取值 [TIER_LOW] / [TIER_MEDIUM] / [TIER_HIGH]
     * @param playQn 默认清晰度（B 站 qn：16=360P、32=480P、64=720P）。传给
     *   `DashData.getVideoStream(qn)` 是安全的：它先找同 qn（同档内 H.264 优先），找不到就回落到
     *   「不高于该 qn 的最高档」，最后才回落到最低档 —— 所以即使视频没有 720P 也不会取不到流。
     * @param danmakuMaxLines 同屏滚动弹幕行数上限。用户在设置里调得更大也会被这里压住
     *   （见 [capDanmakuMaxLines]）
     * @param preferSoftwareDecode 是否优先软解：低端机上硬解更容易花屏/黑屏，且部分老 SoC 的
     *   HEVC 硬解直接不可用；中高端硬解既省电又省 CPU，只在花屏时才回退
     */
    data class Profile(
        val level: Int,
        val playQn: Int,
        val danmakuMaxLines: Int,
        val preferSoftwareDecode: Boolean
    )

    /**
     * 低端档：512MB~1GB RAM、四核 A7/A53 级别的方屏/圆屏手表。
     *
     * - `playQn = 16`（360P）：这类设备的解码器跑 480P 以上 AVC 就开始掉帧，而手表屏幕物理分辨率
     *   本来也吃不下 720P 的细节；弹幕体积小、同屏速度快，360P 是唯一能稳住 30fps 的档；
     * - `danmakuMaxLines = 6`：DFM 每帧要对**每条同屏弹幕**做碰撞/避让判定，6 行是这类设备
     *   渲染线程还能留出绘制余量的上限（再高会先掉帧、再出现弹幕互相压字）；
     * - `preferSoftwareDecode = true`：老 SoC 的硬解对 B 站常见的 fMP4 + HEVC 支持参差，
     *   表现为花屏/黑屏/音画不同步，软解虽然费电但稳定。
     */
    private val LOW = Profile(
        level = TIER_LOW,
        playQn = 16,
        danmakuMaxLines = 6,
        preferSoftwareDecode = true
    )

    /**
     * 中端档：2GB RAM、主流手表（450x450 左右、方屏为主）。
     *
     * - `playQn = 32`（480P）：高于屏幕分辨率但不到下一档，解码余量足够，缩放到表盘上比 360P 明显清楚；
     * - `danmakuMaxLines = 10`：与设置页「弹幕最大行数」的默认值一致（见
     *   `SettingTerminalPlayerActivity` 里 `PLAYER_DANMAKU_MAXLINE` 的默认 "10"）。
     *   中端机把上限收到这个默认值：用户手调更高时同屏更容易互相遮挡，而更高档才有余量去渲染；
     * - `preferSoftwareDecode = false`：这个档位的硬解已经稳，硬解省电对一天一充的手表很关键。
     */
    private val MEDIUM = Profile(
        level = TIER_MEDIUM,
        playQn = 32,
        danmakuMaxLines = 10,
        preferSoftwareDecode = false
    )

    /**
     * 高端档：3GB 以上 RAM、骁龙 W5/W4100 级别，或用户手动开了高性能模式。
     *
     * - `playQn = 64`（720P）：硬件与屏幕都能吃下，且 720P 是手表端「再高也没有意义」的物理上限；
     * - `danmakuMaxLines = 15`：等于 `DanmakuManager` 读设置的默认值（`player_danmaku_maxline`
     *   默认 15），也就是高档不额外加码 —— 15 已经是圆屏上不会糊成一片的实用上限；
     * - `preferSoftwareDecode = false`：高性能设备硬解最稳，软解只会空耗电量与发热。
     */
    private val HIGH = Profile(
        level = TIER_HIGH,
        playQn = 64,
        danmakuMaxLines = 15,
        preferSoftwareDecode = false
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
     * 供播放器每帧/每次加载弹幕时取用：档位在进程内是 [PerformanceManager] 缓好的几个
     * `@Volatile` 字段，读取代价可以忽略。
     */
    fun current(): Profile = profileOf(
        PerformanceManager.getCurrentPerfLevel(),
        PerformanceManager.isLowPerfDevice(),
        PerformanceManager.isEffectiveHighPerf()
    )
}
