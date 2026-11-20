package com.RobinNotBad.BiliClient.util

import com.RobinNotBad.BiliClient.util.UiText

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.SystemClock
import com.RobinNotBad.BiliClient.BuildConfig
import com.RobinNotBad.BiliClient.api.TerminalApi
import com.RobinNotBad.BiliClient.model.ApiResult
import org.json.JSONObject
import java.io.File

/**
 * 崩溃报告的采集与上传。
 *
 * <p>分工：[com.RobinNotBad.BiliClient.ErrorCatch] 在**崩溃瞬间**（主进程，随时会死）
 * 只做最便宜的事——把异常类名、消息、线程名和页面轨迹塞进 Intent；
 * 真正的采集与上传放在崩溃页所在的 `:error_activity` 进程里做（见
 * [com.RobinNotBad.BiliClient.activity.CatchActivity]）。那边内存、文件、网络都是干净的，
 * 不会把「崩溃页都打不开」变成第二种崩溃。
 *
 * <p>上传是**尽力而为**：任何异常都吞掉并返回失败 [ApiResult]，绝不能让崩溃页再崩一次。
 */
object CrashReporter {

    /**
     * 页面轨迹与应用日志两段之间的分隔行。
     *
     * <p>两段共用 `log` 字段后，管理台里看到的就是一坨连续文本，没有这行分隔根本分不清
     * 哪几行是「用户走过哪些页面」、哪几行是「应用日志」——所以分隔符的用途就是**人眼定位**。
     * 前后各留一个空行（`\n\n` / `\n`），是让它在纯文本 / 等宽字体里也自成一段。
     */
    private const val LOG_SECTION_SEPARATOR = "\n\n--- 应用日志（内存缓冲，最近若干行）---\n"

    /**
     * [upload] 的返回码：报告**没传上去，但已经存在本机**，下次启动会自动重试。
     *
     * <p>与「彻底失败」（-2 网络失败 / -4 生成失败）分开，因为对用户是两件事：
     * 「这次没发出去，但东西留着」和「什么都没留下」。崩溃页要给出完全不同的说法。
     */
    const val CODE_SAVED_LOCALLY = -5

    /** 每次冷启动最多补传几条：手表上别抢流量，同时保证攒下的报告几天内都能轮一遍。 */
    private const val MAX_RETRY_PER_START = 3

    /** 日志标签：补传与落盘的成败都记在这个标签下（用户报「补传没生效」时只有这行能查）。 */
    private const val TAG = "CrashReporter"

    /**
     * 把页面轨迹与应用日志合并进同一个 `log` 字段。
     *
     * <p>**为什么复用同一个字段而不是新增一个**：
     * 1. 服务端 `crash` 表的 `log` 列上限 200000 字符，两段拼起来也远够用，于是**服务端零改动**
     *    ——不用发新表结构、不用改管理台、不用改导出脚本；
     * 2. 老数据（只有轨迹）与新数据（轨迹 + 应用日志）在库里**同构**，消费方不需要区分两种格式，
     *    顶多是一次 `indexOf` 看有没有分隔符。
     *
     * <p>**任一段为空时不留下孤零零的分隔符**：只有一段就直接用那一段，两段都空就是空串。
     */
    private fun mergeLog(trail: String?, ringLog: String?): String {
        val t = trail.orEmpty()
        val r = ringLog.orEmpty()
        return when {
            t.isEmpty() -> r
            r.isEmpty() -> t
            else -> t + LOG_SECTION_SEPARATOR + r
        }
    }

    /** 从 Intent extra 里带过来的那些「崩溃瞬间才拿得到」的字段。 */
    @JvmStatic
    fun buildPayload(
        context: Context?,
        exception: String?,
        message: String?,
        thread: String?,
        stack: String?,
        trail: String?,
        ringLog: String?,
        uptimeSec: Long,
        extra: String? = null
    ): JSONObject {
        // 只取总内存用于**分档**，不上报精确 ram_total / ram_avail（对比审计 P2-9：
        // 精确字节数等于给机型再加一枚指纹，统计只关心「手表 / 手机大概多大内存」）。
        // 档位规则在 TerminalApi.ramLevel（纯函数，有 JVM 单测）；取不到就是 UNKNOWN(0)。
        val ramLevel = try {
            val manager = context?.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            if (manager == null) {
                TerminalApi.RAM_LEVEL_UNKNOWN
            } else {
                val info = ActivityManager.MemoryInfo()
                manager.getMemoryInfo(info)
                TerminalApi.ramLevel(info.totalMem)
            }
        } catch (ignored: Exception) {
            // 采集内存失败不影响报告主体
            TerminalApi.RAM_LEVEL_UNKNOWN
        }

        val mid = if (SharedPreferencesUtil.getBoolean(SettingsKeys.FEEDBACK_ATTACH_MID, false))
            SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0) else 0L

        return TerminalApi.buildCrashPayload(
            /* installId = */ TerminalApi.getInstallId(),
            /* deviceKey = */ TerminalApi.getDeviceKey(),
            /* mid = */ mid,
            /* exception = */ exception,
            /* message = */ message,
            /* thread = */ thread,
            /* stack = */ stack,
            /* log = */ mergeLog(trail, ringLog),
            /* extra = */ extra ?: "",
            /* versionCode = */ BuildConfig.VERSION_CODE,
            /* versionName = */ BuildConfig.VERSION_NAME,
            /* sdk = */ Build.VERSION.SDK_INT,
            /* release = */ Build.VERSION.RELEASE,
            /* brand = */ Build.BRAND,
            /* device = */ Build.DEVICE,
            /* product = */ Build.PRODUCT,
            /* model = */ Build.MODEL,
            /* abi = */ TerminalApi.firstAbi(),
            /* ramLevel = */ ramLevel,
            /* uptimeSec = */ uptimeSec,
            /* isCoexist = */ BuildConfig.COEXIST
        )
    }

    /**
     * **先落盘**：把这条崩溃存进 [PendingCrashStore]，返回存好的文件；失败返回 null。
     *
     * <p>崩溃页要等 3 秒才真正上报（那是给用户「取消附带日志」的窗口，见 `CatchActivity`），
     * 而用户在这 3 秒里点「退出」是直接 killProcess 的 —— 那条报告连组装的机会都没有。
     * 所以在页面一打开就先存一份，进程怎么死都不影响它。
     *
     * <p>**存下来的这份不带应用日志**（`ringLog` 固定传空串）：用户可能还要取消「附带日志」，
     * 而这条记录很可能在他做出选择之前就写下去了。宁可少带一段日志，也不能带上用户明确取消过的东西；
     * 3 秒后 [upload] 会按当时的勾选**重写**这个文件。
     */
    @JvmStatic
    fun prepare(
        context: Context?,
        exception: String?,
        message: String?,
        thread: String?,
        stack: String?,
        trail: String?,
        uptimeSec: Long
    ): File? = try {
        val payload = buildPayload(context, exception, message, thread, stack, trail, "", uptimeSec)
        PendingCrashStore.save(PendingCrashStore.dirOf(context), payload.toString())
    } catch (t: Throwable) {
        null
    }

    /**
     * 采集 + 上传。返回成功时 `code` 就是给用户看的报错编号。
     *
     * <p>顺序是**先落盘、再上传、成功才删**：网络失败不再等于报告消失 —— 那正是「崩溃了但后台
     * 一条记录都没有」的主因（[com.RobinNotBad.BiliClient.api.TerminalApi.uploadCrash] 撞上
     * IOException 只会返回 -2，payload 原本就活在内存里、失败即丢）。上传失败时返回
     * [CODE_SAVED_LOCALLY]，由崩溃页告诉用户「已存本机，下次重试」。
     *
     * @param trail 崩溃前用户走过的页面轨迹（`CrashTrail.snapshot()`）。
     * @param ringLog 内存日志快照（`Logu.ringSnapshot()`）；不需要附带时传空串。
     *   与 [trail] 合并进同一个 `log` 字段，理由见 [mergeLog]。
     * @param preparedFile [prepare] 已经存好的文件；传了就按本次报文重写它，省一次落盘。
     */
    @JvmStatic
    @JvmOverloads
    fun upload(
        context: Context?,
        exception: String?,
        message: String?,
        thread: String?,
        stack: String?,
        trail: String?,
        ringLog: String?,
        uptimeSec: Long,
        preparedFile: File? = null
    ): ApiResult {
        val payload = try {
            buildPayload(context, exception, message, thread, stack, trail, ringLog, uptimeSec)
        } catch (e: Exception) {
            return ApiResult(-4, UiText.of("崩溃报告生成失败"))
        }
        // 落盘放在上传之前：从这一行往下，这条崩溃就不会再因为网络、超时或进程退出而消失了。
        val stored = if (preparedFile != null && PendingCrashStore.rewrite(preparedFile, payload.toString())) {
            preparedFile
        } else {
            PendingCrashStore.save(PendingCrashStore.dirOf(context), payload.toString())
        }
        val result = try {
            TerminalApi.uploadCrash(payload)
        } catch (e: Exception) {
            ApiResult(-4, UiText.of("崩溃报告生成失败"))
        }
        if (result.code >= 0) {
            PendingCrashStore.delete(stored)
            return result
        }
        return if (stored != null) {
            ApiResult(CODE_SAVED_LOCALLY, UiText.of("已保存在本机，稍后会自动重试上传"))
        } else {
            result
        }
    }

    /**
     * 冷启动补传：把 [PendingCrashStore] 里攒下的报告按「最旧的先来」发最多 [MAX_RETRY_PER_START] 条，
     * 返回成功发出的条数。
     *
     * <p>**隐私闸不能省**：补传同样是一次对外上报，必须过 [TerminalApi.isCrashReportAutoEnabled]
     * （隐私同意 + 用户开关）。用户关掉开关之后不该再往外发一个字节——哪怕这些报告是他开着的时候攒的；
     * 关着的时候也什么都不删，等他哪天又打开。
     */
    @JvmStatic
    fun retryPending(context: Context?): Int {
        if (context == null) return 0
        val dir = PendingCrashStore.dirOf(context) ?: return 0
        val pending = PendingCrashStore.list(dir)
        // 补传本身不许出声（启动链路上、用户没点任何东西），但**结果必须留一行日志**：
        // 「补传到底有没有生效」在用户那边只能靠排查日志回答，没有这行就又要靠猜。
        if (!TerminalApi.isCrashReportAutoEnabled()) {
            if (pending.isNotEmpty()) Logu.i(TAG, "未开启崩溃自动上报，跳过补传（保留 ${pending.size} 条）")
            return 0
        }
        if (pending.isEmpty()) return 0
        var sent = 0
        for (file in pending.take(MAX_RETRY_PER_START)) {
            val text = PendingCrashStore.read(file)
            if (text == null) {
                // 坏文件 / 超大 / 空文件：删掉，别占着那 20 个名额
                Logu.w(TAG, "待传崩溃报告不可用（空/超大/非文件），已丢弃：${file.name}")
                PendingCrashStore.delete(file)
                continue
            }
            val payload = try {
                JSONObject(text)
            } catch (e: Exception) {
                Logu.w(TAG, "待传崩溃报告不是合法 JSON，已丢弃：${file.name}")
                PendingCrashStore.delete(file)
                continue
            }
            val result = try {
                TerminalApi.uploadCrash(payload)
            } catch (e: Exception) {
                null
            }
            if (result != null && result.code >= 0) {
                PendingCrashStore.delete(file)
                sent++
                Logu.i(TAG, "补传成功，报错ID=${result.code}（${file.name}）")
            } else {
                // 推到队尾，下次启动先试别的：一条死活传不上去的报告不该把后面的一起堵死
                PendingCrashStore.touch(file)
                Logu.w(TAG, "补传失败 code=${result?.code ?: -4}，推到队尾等下次启动：${file.name}")
            }
        }
        Logu.i(TAG, "崩溃报告补传结束：成功 $sent 条，待传 ${PendingCrashStore.list(dir).size} 条")
        return sent
    }

    /** 进程已运行时长（秒）。放在这里以便崩溃页与反馈页共用同一套取值逻辑。 */
    @JvmStatic
    fun uptimeSeconds(): Long = SystemClock.elapsedRealtime() / 1000
}
