package com.RobinNotBad.BiliClient.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import com.RobinNotBad.BiliClient.BiliTerminal
import com.RobinNotBad.BiliClient.BuildConfig
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.CrashTrail
import com.RobinNotBad.BiliClient.util.Logu
import com.RobinNotBad.BiliClient.util.MediaMerger
import com.RobinNotBad.BiliClient.util.NetWorkUtil
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import com.RobinNotBad.BiliClient.tutorial.TutorialStore
import com.RobinNotBad.BiliClient.tutorial.Tutorials
import com.RobinNotBad.BiliClient.util.SettingsKeys
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit

/**
 * 调试桥 —— 给 AI agent / 自动化脚本用的**可编程控制通道**（26.10.14）。
 *
 * <h2>为什么需要它</h2>
 * 手表端的 UI 自动化成本极高：屏幕小、控件密、`uiautomator` 在这类设备上又慢又常拿不到窗口。
 * 而此前 debug 包里**没有任何可从外部驱动的入口**——`TestActivity` 是 `exported=false`，
 * adb shell（uid 2000）根本拉不起来；想开个日志开关得点进设置页翻三层。
 * 结果就是「能编译、能跑单测，但没法自动验证真机行为」。
 *
 * <h2>怎么用</h2>
 * 用广播驱动，一条命令一个动作，结果通过 `setResultData` 回显给 adb：
 * ```
 * adb shell am broadcast -n <包名>/com.RobinNotBad.BiliClient.debug.DebugBridge \
 *     --es token rebili-agent-bridge --es cmd ping
 * ```
 * 命令表见 [help]；也可直接 `--es cmd help` 打印。
 *
 * <h2>三层安全边界（缺一不可）</h2>
 * 1. **只存在于 Debug 源集**（`app/src/debug/`）——Release 编译时这个类根本不存在，
 *    清单声明也在 `app/src/debug/AndroidManifest.xml` 里，发布包零暴露；
 * 2. **`android:permission="android.permission.DUMP"`** —— 发送方必须持有 DUMP。
 *    adb shell 持有（`dumpsys` 就靠它），本应用自己天然放行，而普通第三方应用没有，
 *    因此同设备上的恶意应用无法借它读登录态；
 * 3. **token 校验** —— 防止误投递（别家的自动化脚本恰好广播了同名 action）。
 *
 * <h2>结果回传的双保险</h2>
 * `am broadcast` 在个别 ROM 上不会转发 receiver 的 result data。所以每个命令的结果
 * **同时**写到 `logs/bridge-last.txt`，回显里也带上该路径；拿不到回显时直接读文件即可
 * （debug 包可 `adb shell run-as <包名> cat ...`）。
 */
class DebugBridge : BroadcastReceiver() {

    companion object {

        /** 命令校验用的 token。**不是**安全边界（见类注释第 2 条），只用来防误投递。 */
        const val TOKEN = "rebili-agent-bridge"

        /** 回显与结果文件的大小上限：Binder 事务有 1MB 上限，留足余量。 */
        private const val MAX_RESULT_CHARS = 120_000

        /** 每次命令的结果落盘文件名（位于 [Logu.getLogDir] 下）。 */
        private const val RESULT_FILE = "bridge-last.txt"

        /** `http` 命令的响应体落盘文件名。 */
        private const val HTTP_BODY_FILE = "bridge-http-response.txt"

        /**
         * 已声明的 Activity 全名缓存。
         *
         * <p>**刻意不再维护硬编码的包名表**：第一版按「`activity.video` → `activity.settings` → …」
         * 逐个 `Class.forName` 猜，实测立刻漏了 `activity.video.info.VideoInfoActivity`（`start` 报
         * 「找不到 Activity」）。清单才是唯一真相，从 PackageManager 取既不会过期、也不用跟着
         * 新页面维护。实测 104 个页面里**没有简单名重名**，所以按简单名匹配是无歧义的。
         */
        @Volatile
        private var cachedActivityNames: List<String>? = null

        /**
         * `dump` 命令里需要打码的键名片段。
         *
         * 与审计 S2「只记 Cookie 键名、不记值」同一条原则：`dump` 是**批量**输出，误落到日志/
         * 截图里的代价太高，所以一律只报长度；确实需要某个值用 `get --es key <键>` 精确取。
         */
        private val SENSITIVE_KEY_HINTS = listOf(
            "cookie", "csrf", "access_key", "refresh_token", "sessdata", "bili_jct", "password", "token"
        )
    }

    override fun onReceive(context: Context, intent: Intent) {
        // 广播的 onReceive 跑在主线程，而 `http` 这类命令要发网络请求——直接在这里做会 ANR。
        // goAsync() 把广播的生命周期延长到 finish()，让我们能在后台线程干活后再回填结果。
        val pending = goAsync()
        CenterThreadPool.run {
            val result = try {
                handle(context, intent)
            } catch (t: Throwable) {
                Logu.e("DebugBridge", "命令执行失败", t)
                "ERR: ${t.javaClass.simpleName}: ${t.message}"
            }
            try {
                pending.setResultData(persist(result))
            } catch (ignored: Throwable) {
                // 个别 ROM 不接受 receiver 回填结果，结果文件已经写好了，不影响使用
            } finally {
                pending.finish()
            }
        }
    }

    /** 结果落盘 + 截断。返回最终要回显的文本。 */
    private fun persist(raw: String): String {
        val text = if (raw.length > MAX_RESULT_CHARS) {
            raw.substring(0, MAX_RESULT_CHARS) + "\n…(truncated, full content in $RESULT_FILE)"
        } else raw
        try {
            val file = Logu.getLogFile(RESULT_FILE)
            file?.writeText(text)
        } catch (ignored: Throwable) {
        }
        return text
    }

    // ==================== 命令分发 ====================

    private fun handle(context: Context, intent: Intent): String {
        val token = intent.getStringExtra("token")
        if (token != TOKEN) {
            return "ERR: token 不匹配（缺少或错误）。用法见 DebugBridge 类注释。"
        }
        val cmd = intent.getStringExtra("cmd")?.trim()?.lowercase()
        if (cmd.isNullOrEmpty()) return "ERR: 缺少 --es cmd（用 --es cmd help 看命令表）"

        return when (cmd) {
            "help" -> help()
            "ping" -> ping(context)
            "agent" -> agent(context, intent)
            "get" -> get(intent)
            "set" -> set(intent)
            "remove" -> remove(intent)
            "dump" -> dump()
            "start" -> start(context, intent)
            "activities" -> activities(context)
            "notutorials" -> noTutorials()
            "http" -> http(context, intent)
            "merge" -> merge(context, intent)
            "log" -> log(intent)
            "logs" -> logs()
            "logclear" -> logClear(intent)
            "trail" -> trail()
            "logring" -> logRing(intent)
            "crash" -> crash()
            "top" -> top()
            else -> "ERR: 未知命令 $cmd（用 --es cmd help 看命令表）"
        }
    }

    private fun help(): String = """
        |DebugBridge 命令表（全部需要 --es token $TOKEN）
        |  --es cmd ping                     健康检查：包名/版本/flavor/各开关当前状态
        |  --es cmd agent --ez on <true|false>   开关 Agent 测试模式（立即生效）
        |  --es cmd get --es key <键> [--es type string|int|long|bool|float]
        |  --es cmd set --es key <键> --es value <值> [--es type ...]
        |  --es cmd remove --es key <键>
        |  --es cmd dump                     列出全部设置项（敏感值打码）
        |  --es cmd start --es activity <类名> [--es extras "aid=2;seekReply=-1"]
        |                                    类名可只写简单名（如 VideoInfoActivity）
        |                                    extras 也可写 JSON，但 adb shell 下要转义：'{\"aid\":2}'
        |                                    值前缀 int:/long:/bool:/float:/string: 可强制类型
        |                                    （如 danmaku_episode=int:1）
        |  --es cmd activities               列出所有可启动的 Activity 全名
        |  --es cmd notutorials              把全部教程标记为已读（否则教程页会盖住你拉起的页面）
        |  --es cmd http --es url <URL> [--es method GET|POST] [--es data <体>]
        |            [--es headers '<JSON>'] [--ez appheaders true]  (默认不带 Cookie)
        |  --es cmd merge --es video <视频m4s> --es audio <音频m4s> [--es out <输出mp4>]
        |                                    直接跑一次 MediaMerger（DASH 音视频重封装），
        |                                    输出默认在 Android/data/<包名>/files/debug_merge.mp4，回显轨道与采样数
        |  --es cmd log [--es file rebili.log|http.log] [--es bytes 65536]
        |  --es cmd logs                     列出日志文件及大小
        |  --es cmd logclear [--es file <名>]   清空日志（默认清业务日志）
        |  --es cmd trail                    页面轨迹（最近 20 个页面）
        |  --es cmd logring [--es inject <文本>]  内存日志缓冲（不落盘那条路）；
        |                                   带 inject 会先以 W 级写一条再取快照，用于验脱敏
        |  --es cmd crash                    在主线程抛一个测试异常，**主进程会立刻崩**。
        |                                   用于验证「崩溃 → ErrorCatch 带 ring_log → 崩溃页自动上报」整条链路
        |  --es cmd top                      当前前台 Activity
        |
        |结果同时写入 logs/$RESULT_FILE；回显为空时读该文件。
    """.trimMargin()

    private fun ping(context: Context): String {
        val logDir = Logu.getLogDir()
        return buildString {
            appendLine("OK: DebugBridge 就绪")
            appendLine("pkg=${context.packageName}")
            appendLine("flavor=${BuildConfig.FLAVOR} buildType=${BuildConfig.BUILD_TYPE}")
            appendLine("version=${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("agentMode=${BiliTerminal.AGENT_TEST_MODE}")
            appendLine("logv=${Logu.LOGV_ENABLED} logd=${Logu.LOGD_ENABLED} logi=${Logu.LOGI_ENABLED}")
            appendLine("logToFile=${Logu.LOG_TO_FILE} httpTrace=${NetWorkUtil.HTTP_TRACE}")
            appendLine("droppedLogs=${Logu.getDroppedCount()}")
            appendLine("logDir=${logDir?.absolutePath ?: "<不可用>"}")
            appendLine("overlayGranted=${canDrawOverlays(context)}")
            appendLine("top=${top()}")
        }.trimEnd()
    }

    /**
     * Agent 测试模式开关。
     *
     * 写完立刻调 [BiliTerminal.applyLogSwitches] 让开关**当场生效**——原实现里日志开关只在
     * `Application.onCreate` 读一次，改完必须重启应用，自动化流程里这就是一步"重启+重连"，
     * 又慢又容易把上下文弄丢。
     */
    private fun agent(context: Context, intent: Intent): String {
        val on = boolExtra(intent, "on", !BiliTerminal.AGENT_TEST_MODE)
        SharedPreferencesUtil.putBoolean(SettingsKeys.DEV_AGENT_MODE, on)
        BiliTerminal.applyLogSwitches()
        return "OK: agentMode=${BiliTerminal.AGENT_TEST_MODE}\n" +
                "logToFile=${Logu.LOG_TO_FILE} httpTrace=${NetWorkUtil.HTTP_TRACE}\n" +
                "logDir=${Logu.getLogDir()?.absolutePath ?: "<不可用>"}"
    }

    // ==================== 设置项读写 ====================

    private fun get(intent: Intent): String {
        val key = intent.getStringExtra("key") ?: return "ERR: 缺少 --es key"
        val type = intent.getStringExtra("type") ?: inferType(key)
        val sp = SharedPreferencesUtil.sharedPreferences
        if (sp == null || !sp.contains(key)) return "$key=<不存在>"
        // 敏感项与 dump 同一条判据：只报长度。原因见 isSensitiveKey 的注释——
        // get 的结果同样会落进 bridge-last.txt。
        if (isSensitiveKey(key)) return "$key=${maskedLength(sp.all[key])}"
        val value = when (type) {
            "int" -> sp.getInt(key, 0).toString()
            "long" -> sp.getLong(key, 0L).toString()
            "bool" -> sp.getBoolean(key, false).toString()
            "float" -> sp.getFloat(key, 0f).toString()
            else -> sp.getString(key, "")
        }
        return "$key=$value"
    }

    private fun set(intent: Intent): String {
        val key = intent.getStringExtra("key") ?: return "ERR: 缺少 --es key"
        val value = intent.getStringExtra("value") ?: return "ERR: 缺少 --es value"
        when (intent.getStringExtra("type") ?: inferType(key)) {
            "int" -> SharedPreferencesUtil.putInt(key, value.toIntOrNull() ?: return "ERR: 不是整数：$value")
            "long" -> SharedPreferencesUtil.putLong(key, value.toLongOrNull() ?: return "ERR: 不是长整数：$value")
            "bool" -> SharedPreferencesUtil.putBoolean(key, value.toBooleanStrictOrNull() ?: return "ERR: 不是布尔：$value")
            "float" -> SharedPreferencesUtil.putFloat(key, value.toFloatOrNull() ?: return "ERR: 不是小数：$value")
            else -> SharedPreferencesUtil.putString(key, value)
        }
        // 改的若是日志/测试相关开关，立刻让它生效，省掉"改完还要重启"这一步
        BiliTerminal.applyLogSwitches()
        return "OK: $key=$value"
    }

    private fun remove(intent: Intent): String {
        val key = intent.getStringExtra("key") ?: return "ERR: 缺少 --es key"
        SharedPreferencesUtil.removeValue(key)
        BiliTerminal.applyLogSwitches()
        return "OK: removed $key"
    }

    /**
     * 键名是否命中敏感清单。
     *
     * <p>`dump` 与 `get` 必须用**同一条判据**：只给 `dump` 打码而 `get` 直出，等于
     * 「`get --es key cookies` 一行就把登录 Cookie 拿走」；更糟的是 `get` 的结果与
     * `dump` 一样会写进 `$RESULT_FILE`（见 [persist]），落盘之后就不再是「只在
     * adb 会话里看到」了。
     */
    private fun isSensitiveKey(key: String): Boolean =
        SENSITIVE_KEY_HINTS.any { key.lowercase().contains(it) }

    /** 敏感项只报长度：值既不回显、也不进 `$RESULT_FILE`。 */
    private fun maskedLength(raw: Any?): String = "<已打码 len=${raw?.toString()?.length ?: 0}>"

    private fun dump(): String {
        val sp = SharedPreferencesUtil.sharedPreferences ?: return "ERR: SharedPreferences 未就绪"
        val keys = sp.all.keys.sorted()
        return buildString {
            appendLine("OK: 共 ${keys.size} 项（敏感值打码）")
            for (key in keys) {
                val raw = sp.all[key]
                val value = when {
                    isSensitiveKey(key) -> maskedLength(raw)
                    raw is String && raw.length > 200 -> raw.substring(0, 200) + "…"
                    else -> raw.toString()
                }
                appendLine("$key = $value")
            }
        }.trimEnd()
    }

    /**
     * 猜测类型：SharedPreferences 的 `getXxx` 必须用写入时的同一类型，类型错了会 ClassCastException，
     * 所以先把已存在的项按真实类型读出来。不存在的项按字符串处理（写新键时最常见）。
     */
    private fun inferType(key: String): String {
        val sp = SharedPreferencesUtil.sharedPreferences ?: return "string"
        val value = sp.all[key] ?: return "string"
        return when (value) {
            is Int -> "int"
            is Long -> "long"
            is Boolean -> "bool"
            is Float -> "float"
            else -> "string"
        }
    }

    // ==================== 页面驱动 ====================

    /**
     * 进程内拉起任意 Activity。
     *
     * 这是本桥最有价值的一条命令：全应用除 `SplashActivity` / `GetIntentActivity` 外都是
     * `exported="false"`，adb **无法**用 `am start -n` 直达（shell 不是 root，会被 AMS 拒）。
     * 而由应用自己进程内 `startActivity` 不受 exported 限制，等于把全部页面都开放给了 agent。
     *
     * 注意 Android 10 起有「后台启动 Activity」限制：应用不可见时启动会被静默丢弃。
     * 解决办法是给 debug 包授一次悬浮窗权限（`adb shell appops set <包名> SYSTEM_ALERT_WINDOW allow`），
     * 它是该限制的豁免条件之一；结果里也会提示当前是否已授权。
     */
    private fun start(context: Context, intent: Intent): String {
        val name = intent.getStringExtra("activity") ?: return "ERR: 缺少 --es activity"
        val cls = try {
            resolveActivity(context, name)
        } catch (e: Exception) {
            return "ERR: ${e.message}"
        }
        val target = Intent(context, cls).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        // 默认再叠一个 CLEAR_TOP，否则 `start` 会静默失效——实测踩过：
        // `FLAG_ACTIVITY_NEW_TASK` 在「目标页恰好是某个已存在任务的栈根」时**复用任务而不是新建实例**，
        // 于是 startActivity 只是把那个任务提到前台，栈顶停着别的页（比如教程页），agent 却拿到 "OK"。
        // CLEAR_TOP 保证「目标页之上一律清掉、它自己回到前台」，无论它在不在栈里、在不在栈顶。
        // 需要「压栈而不清栈」（测返回栈行为）时用 --ez keepstack true。
        if (!intent.getBooleanExtra("keepstack", false)) {
            target.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val extrasRaw = intent.getStringExtra("extras")
        if (!extrasRaw.isNullOrBlank()) {
            try {
                target.putExtras(parseExtras(extrasRaw))
            } catch (e: Exception) {
                return "ERR: extras 解析失败（${e.message}）。两种写法任选：\n" +
                        "  逗号/分号分隔的键值对：--es extras \"aid=2;seekReply=-1\"\n" +
                        "  JSON 对象（注意在 adb shell 下必须转义双引号）：--es extras '{\\\"aid\\\":2}'"
            }
        }
        try {
            context.startActivity(target)
        } catch (e: Exception) {
            return "ERR: 启动失败 ${e.javaClass.simpleName}: ${e.message}"
        }
        val warning = if (canDrawOverlays(context)) ""
        else "\n提示：未授予悬浮窗权限，应用不在前台时这次启动可能被系统静默拦截。" +
                "可执行 adb shell cmd appops set ${context.packageName} SYSTEM_ALERT_WINDOW allow 解决。"
        // 回显全类名：短名可能被解析成同名的另一个页面，agent 需要知道自己到底打开了哪个
        return "OK: 已请求启动 ${cls.name}$warning"
    }

    /** 列出所有可启动的 Activity 短类名，agent 不必翻源码猜名字。 */
    private fun activities(context: Context): String {
        val names = declaredActivityNames(context)
        return buildString {
            appendLine("OK: 共 ${names.size} 个")
            names.forEach { appendLine(it) }
        }.trimEnd()
    }

    /**
     * 把全部教程标记为已读，让页面不再被教程页盖住。
     *
     * <p>**这是 agent 测试页面之前几乎必须先做的一步**。实测（全新安装）：`start RecommendActivity`
     * 或 `start VideoInfoActivity` 之后，`BaseActivity.autoTriggerTutorial()` 会调
     * `Tutorials.forPage(javaClass).filter { !TutorialStore.isRead(it) }`，非空就 `TutorialPagerActivity.start(...)`
     * ——于是教程页盖在刚拉起的页面上面，`top` 看到的永远不是目标页。
     * 而 GUIDE 类教程还要求「翻到最后一页 + 停留 3 秒」才能点「已阅」，靠 UI 自动化点掉它成本极高。
     *
     * <p>这里走官方 API（[Tutorials.all] + [TutorialStore.markRead]），不自己拼 `tutorial_ver_*` 键名，
     * 免得教程 id 改名后静默失效。需要重看教程时用 `remove --es key tutorial_ver_<id>` 即可。
     */
    private fun noTutorials(): String {
        return try {
            val all = Tutorials.all
            all.forEach { TutorialStore.markRead(it) }
            "OK: 已把 ${all.size} 篇教程标记为已读（${all.joinToString { it.id }}）"
        } catch (e: Exception) {
            "ERR: 标记教程失败 ${e.javaClass.simpleName}: ${e.message}"
        }
    }

    /** 从清单读本应用全部 Activity 全名（带进程内缓存）。 */    private fun declaredActivityNames(context: Context): List<String> {
        cachedActivityNames?.let { return it }
        val names = try {
            val pm = context.packageManager
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(PackageManager.GET_ACTIVITIES.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(context.packageName, PackageManager.GET_ACTIVITIES)
            }
            info.activities
                ?.map { it.name }
                ?.filter { it.startsWith("com.RobinNotBad.BiliClient") }
                ?.sorted()
                ?: emptyList()
        } catch (e: Exception) {
            Logu.e("DebugBridge", "读取 Activity 清单失败", e)
            emptyList()
        }
        cachedActivityNames = names
        return names
    }

    /**
     * 把类名解析成 Activity。
     *
     * <p>支持全限定名，也支持只写简单名（`VideoInfoActivity`）。简单名走**清单**匹配而不是
     * 「拿一串包名挨个 `Class.forName` 猜」——后者实测漏掉了 `activity.video.info.VideoInfoActivity`
     * 这种多一层子包的页面，而清单不会过期。
     */
    private fun resolveActivity(context: Context, name: String): Class<*> {
        val candidates = if (name.contains('.')) {
            listOf(name)
        } else {
            declaredActivityNames(context).filter { it.substringAfterLast('.') == name }
        }
        if (candidates.isEmpty()) {
            throw IllegalArgumentException("找不到 Activity：$name（用 activities 命令列出全部 ${declaredActivityNames(context).size} 个）")
        }
        for (candidate in candidates) {
            try {
                val cls = Class.forName(candidate)
                if (android.app.Activity::class.java.isAssignableFrom(cls)) return cls
            } catch (ignored: ClassNotFoundException) {
                // 试下一个
            }
        }
        throw IllegalArgumentException("$name 匹配到了 ${candidates.joinToString()}，但都不是可加载的 Activity")
    }

    /**
     * 解析 extras。两种写法都支持：
     * - **键值对**：`aid=2;seekReply=-1`（分号或逗号分隔）——**推荐**，在 adb shell 下不需要任何转义；
     * - **JSON 对象**：`{"aid":2}`——注意经 `adb shell` 传递时双引号会被设备端 shell 吃掉，
     *   必须写成 `'{\"aid\":2}'` 才能到得了这里。
     *
     * <p>加第一种写法是因为实测踩过坑：`--es extras '{"group_type":"dev"}'` 传到应用侧时外层花括号
     * 还在、内层引号已被 shell 剥掉，`JSONObject` 直接解析失败。而 agent 最常传的就是
     * 「一个数字 id」这种简单参数，为它去数转义斜杠不值得。
     */
    private fun parseExtras(raw: String): Bundle {
        val bundle = Bundle()
        if (raw.trim().startsWith("{")) {
            val obj = JSONObject(raw)
            for (key in obj.keys()) {
                // 用 opt 而不是 get：get(String) 已被标记弃用，且 opt 对 JSON null 返回 JSONObject.NULL，
                // 会自然落到下面的 else 分支转成字符串，不会抛异常。
                when (val value = obj.opt(key)) {
                    is Boolean -> bundle.putBoolean(key, value)
                    is Int -> bundle.putInt(key, value)
                    is Long -> bundle.putLong(key, value)
                    is Double -> bundle.putDouble(key, value)
                    is String -> bundle.putString(key, value)
                    else -> bundle.putString(key, value?.toString() ?: "")
                }
            }
            return bundle
        }
        for (pair in raw.split(';', ',')) {
            val trimmed = pair.trim()
            if (trimmed.isEmpty()) continue
            val eq = trimmed.indexOf('=')
            if (eq <= 0) throw IllegalArgumentException("`$trimmed` 不是 key=value")
            putTyped(bundle, trimmed.substring(0, eq).trim(), trimmed.substring(eq + 1).trim())
        }
        return bundle
    }

    /**
     * 按值的内容猜类型。
     *
     * <p>Intent extra 的类型必须与接收方 `getLongExtra` / `getStringExtra` 对得上，猜错就是
     * `ClassCastException`。所以这里优先按「看起来像什么」定：纯数字 → `Long`（页面 id 都是长整型），
     * `true/false` → `Boolean`，其余 → `String`。
     *
     * <p>猜不准时必须能强制指定：`PlayerActivity` 的 `danmaku_episode` 用 `getIntExtra` 读，
     * 而 `95225` 这种纯数字会被猜成 `Long`，`getIntExtra` 于是只拿到 0（实测踩过）。
     * 所以支持值前缀 `int:` / `long:` / `bool:` / `float:` / `string:`：
     * `--es extras "danmaku_episode=int:1;danmaku_bgm_id=95225"`。
     */
    private fun putTyped(bundle: Bundle, key: String, value: String) {
        val (prefix, rest) = when {
            value.startsWith("int:", true) -> "int:" to value.substring(4).trim()
            value.startsWith("long:", true) -> "long:" to value.substring(5).trim()
            value.startsWith("bool:", true) -> "bool:" to value.substring(5).trim()
            value.startsWith("float:", true) -> "float:" to value.substring(6).trim()
            value.startsWith("string:", true) -> "string:" to value.substring(7)
            else -> "" to value
        }
        if (prefix.isNotEmpty()) {
            when (prefix) {
                "int:" -> bundle.putInt(key, rest.toIntOrNull() ?: 0)
                "long:" -> bundle.putLong(key, rest.toLongOrNull() ?: 0L)
                "bool:" -> bundle.putBoolean(key, rest.toBooleanStrictOrNull() ?: false)
                "float:" -> bundle.putFloat(key, rest.toFloatOrNull() ?: 0f)
                else -> bundle.putString(key, rest)
            }
            return
        }
        when {
            value.equals("true", true) || value.equals("false", true) ->
                bundle.putBoolean(key, value.toBoolean())
            value.toLongOrNull() != null -> bundle.putLong(key, value.toLong())
            else -> bundle.putString(key, value)
        }
    }

    // ==================== 接口探测 ====================

    /**
     * 用应用自己的身份发一次 HTTP 请求。
     *
     * 存在的意义是「接口能不能通」这件事必须由**应用进程**回答：登录态在 SharedPreferences 里、
     * 请求头由 [NetWorkUtil.webHeaders] 统一拼装、风控会认 Cookie 与 UA——外部 `curl` 复刻不了这些。
     * 响应体全文落盘，回显只给状态码、耗时和前 2000 字，避免把 Binder 事务撑爆。
     *
     * 超时刻意调短（连接 5s / 读 8s，且不重试）：本命令跑在广播的 `goAsync()` 里，
     * 前台广播有 10s 左右的执行窗口，按默认的 8s+16s×5 次重试会直接把广播拖到 ANR。
     */
    private fun http(context: Context, intent: Intent): String {
        val url = intent.getStringExtra("url") ?: return "ERR: 缺少 --es url"
        val method = (intent.getStringExtra("method") ?: "GET").uppercase()
        val data = intent.getStringExtra("data")
        val headersJson = intent.getStringExtra("headers")
        // 默认**不带**应用自己的 Cookie：这条命令的核心用途是「探一个任意 URL 能不能通」，
        // 而目标 URL 由调用方随便填，默认带登录态等于「谁都能借它把 SESSDATA 发去自己的服务器」。
        // 确实要测带登录态的接口时显式传 --ez appheaders true。
        val useAppHeaders = boolExtra(intent, "appheaders", false)

        val builder = Request.Builder().url(url)
        if (useAppHeaders) {
            val headers = NetWorkUtil.webHeaders
            var i = 0
            while (i < headers.size - 1) {
                builder.addHeader(headers[i], headers[i + 1])
                i += 2
            }
        }
        if (!headersJson.isNullOrBlank()) {
            try {
                val obj = JSONObject(headersJson)
                for (key in obj.keys()) builder.header(key, obj.getString(key))
            } catch (e: Exception) {
                return "ERR: headers 不是合法 JSON 对象：${e.message}"
            }
        }

        when (method) {
            "GET" -> builder.get()
            "POST", "PUT", "DELETE", "PATCH" -> {
                val contentType = headersJson?.let {
                    runCatching { JSONObject(it).optString("Content-Type") }.getOrNull()
                }?.takeIf { it.isNotBlank() } ?: "application/x-www-form-urlencoded"
                val body = (data ?: "").toRequestBody(contentType.toMediaTypeOrNull())
                builder.method(method, body)
            }
            else -> return "ERR: 不支持的 method=$method"
        }

        val client = NetWorkUtil.getOkHttpInstance().newBuilder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .writeTimeout(8, TimeUnit.SECONDS)
            .build()

        val startAt = System.currentTimeMillis()
        client.newCall(builder.build()).execute().use { response ->
            val cost = System.currentTimeMillis() - startAt
            val body = response.body?.string().orEmpty()
            val file = Logu.getLogFile(HTTP_BODY_FILE)
            try {
                file?.writeText(body)
            } catch (ignored: Exception) {
            }
            val preview = if (body.length > 2000) body.substring(0, 2000) + "…" else body
            return buildString {
                appendLine("OK: ${response.code} (${cost}ms) bytes=${body.length}")
                appendLine("file=${file?.absolutePath ?: "<不可用>"}")
                appendLine(preview)
            }.trimEnd()
        }
    }

    // ==================== 音视频合并验证 ====================

    /**
     * 直接调一次 [MediaMerger.mergeAv]，在真机上验证 DASH 音视频重封装这条链路。
     *
     * 存在的意义：完整下载流程要登录 + 选 1080P 才走得到合并分支，但最容易出错的是容器层
     * （MediaExtractor 读分片 MP4 + MediaMuxer 按 sampleTime 归并）——拿两条现成的 m4s 就能单独验。
     * 输出默认落在应用自己的外部文件目录（`Android/data/<包名>/files/debug_merge.mp4`），
     * 不需要任何存储权限；回显里再用 MediaExtractor 读一遍产物，确认轨道与采样数不是空壳。
     */
    private fun merge(context: Context, intent: Intent): String {
        val videoPath = intent.getStringExtra("video") ?: return "ERR: 缺少 --es video <视频片段路径>"
        val audioPath = intent.getStringExtra("audio") ?: return "ERR: 缺少 --es audio <音频片段路径>"
        val video = File(videoPath)
        val audio = File(audioPath)
        if (!video.isFile) return "ERR: 视频文件不存在：$videoPath"
        if (!audio.isFile) return "ERR: 音频文件不存在：$audioPath"

        val defaultOut = File(context.getExternalFilesDir(null) ?: context.cacheDir, "debug_merge.mp4")
        val out = File(intent.getStringExtra("out") ?: defaultOut.absolutePath)
        out.absoluteFile.parentFile?.mkdirs()

        val progressMarks = ArrayList<Int>()
        var lastMark = -1
        val startAt = System.currentTimeMillis()
        val result = MediaMerger.mergeAv(
            videoFile = video,
            audioFile = audio,
            mergedFile = out,
            onProgress = { fraction ->
                val percent = (fraction * 100).toInt()
                // 只在整十处记一个标记，回显里能看出进度回调确实按区间推进
                if (percent != lastMark && percent % 10 == 0) {
                    lastMark = percent
                    progressMarks.add(percent)
                }
            },
            shouldStop = { false }
        )
        val cost = System.currentTimeMillis() - startAt
        val resultName = when (result) {
            MediaMerger.RESULT_OK -> "OK"
            MediaMerger.RESULT_NO_TRACK -> "NO_TRACK"
            MediaMerger.RESULT_NO_SPACE -> "NO_SPACE"
            MediaMerger.RESULT_STOPPED -> "STOPPED"
            else -> "FAILED"
        }
        return buildString {
            appendLine("result=$resultName($result) cost=${cost}ms")
            appendLine("in video=${video.length()}B audio=${audio.length()}B")
            appendLine("out=${out.absolutePath} exists=${out.isFile} size=${out.length()}B")
            appendLine("progress=${if (progressMarks.isEmpty()) "<无回调>" else progressMarks.joinToString(",")}")
            if (result == MediaMerger.RESULT_OK) append(describeMp4(out))
        }.trimEnd()
    }

    /** 用 MediaExtractor 读一遍 MP4，报告轨道类型、声明时长与采样数（验证产物不是空壳）。 */
    private fun describeMp4(file: File): String {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(file.absolutePath)
            buildString {
                appendLine("tracks=${extractor.trackCount}")
                val buffer = ByteBuffer.allocateDirect(256 * 1024)
                for (i in 0 until extractor.trackCount) {
                    val format = extractor.getTrackFormat(i)
                    val mime = format.getString(MediaFormat.KEY_MIME) ?: "?"
                    extractor.selectTrack(i)
                    var samples = 0
                    var lastSampleUs = 0L
                    while (extractor.readSampleData(buffer, 0) >= 0) {
                        samples++
                        lastSampleUs = extractor.sampleTime
                        extractor.advance()
                    }
                    extractor.unselectTrack(i)
                    val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else -1L
                    appendLine("  #$i mime=$mime samples=$samples durationUs=$durationUs lastSampleUs=$lastSampleUs")
                }
            }
        } catch (e: Exception) {
            "\nERR: 读取产物失败：${e.javaClass.simpleName}: ${e.message}"
        } finally {
            runCatching { extractor.release() }
        }
    }

    // ==================== 观测 ====================

    private fun log(intent: Intent): String {
        val file = sanitizeFileName(intent.getStringExtra("file") ?: Logu.APP_LOG_NAME)
            ?: return "ERR: 非法的 file 名"
        val bytes = (intent.getStringExtra("bytes")?.toIntOrNull() ?: 65536).coerceIn(1, 200_000)
        val content = Logu.readTail(file, bytes)
        if (content.isEmpty()) return "OK: $file 为空或不存在（可用 logs 命令确认）"
        return "OK: $file 尾部 ${content.length} 字符（已丢弃行数 ${Logu.getDroppedCount()}）\n$content"
    }

    private fun logs(): String {
        val list = Logu.listLogFiles()
        return if (list.isEmpty()) "OK: 暂无日志文件（需先打开 Agent 测试模式）" else "OK:\n$list"
    }

    private fun logClear(intent: Intent): String {
        val file = sanitizeFileName(intent.getStringExtra("file") ?: Logu.APP_LOG_NAME)
            ?: return "ERR: 非法的 file 名"
        Logu.clearFile(file)
        return "OK: 已清空 $file"
    }

    private fun trail(): String {
        val snapshot = CrashTrail.snapshot()
        return if (snapshot.isEmpty()) "OK: 暂无页面轨迹" else "OK: 页面轨迹\n$snapshot"
    }

    /**
     * 读**内存日志缓冲**（`Logu` 的环形缓冲），可选先注入一条测试日志。
     *
     * <p>与 `log` 命令的区别：`log` 读的是**落盘文件**，而落盘只在 Agent 模式下才有；
     * 内存缓冲是 release 包唯一能拿到历史日志的那条路（它刻意不落盘）。加这条命令是因为
     * 「缓冲到底有没有在累积、脱敏有没有生效」光看落盘文件验证不了。
     *
     * <p>`--es inject <文本>` 会先以 W 级写一条再取快照 —— 用来验证脱敏链路：
     * 注入 `SESSDATA=abc&sign=xyz`，快照里应当出现 `SESSDATA=***&sign=***`。
     */
    private fun logRing(intent: Intent): String {
        val inject = intent.getStringExtra("inject")
        if (!inject.isNullOrEmpty()) Logu.w(inject)
        val chars = Logu.ringChars()
        val snapshot = Logu.ringSnapshot()
        if (snapshot.isEmpty()) return "OK: 内存日志缓冲为空（ringChars=0）"
        val tailLimit = 4000
        val tail = if (snapshot.length > tailLimit) snapshot.substring(snapshot.length - tailLimit) else snapshot
        var lines = 1
        for (c in snapshot) if (c == '\n') lines++
        return "OK: ringChars=$chars，$lines 行；尾部 ${tail.length} 字符：\n$tail"
    }

    private fun top(): String {
        val activity = BiliTerminal.getCurrentActivity()
        return activity?.javaClass?.name ?: "<应用不在前台>"
    }

    /**
     * 在主线程抛一个未捕获异常，**主进程会立刻崩溃**。
     *
     * <p>存在的唯一理由：验证「崩溃 → [com.RobinNotBad.BiliClient.ErrorCatch] 在崩溃瞬间把
     * `ring_log` 等 extra 塞进 Intent → `CatchActivity`（`:error_activity` 独立进程）自动上报」
     * 这整条链路。手动 `start` 崩溃页只能验证**接收端**，验证不了发送端那段代码。
     *
     * <p>刻意用 `Handler.post` 而不是直接在 `onReceive` 里抛：脱离 receiver 的调用栈，
     * 让异常真正走到 `Thread.setDefaultUncaughtExceptionHandler`。
     *
     * <p>返回值基本看不到——进程马上就死了，结果文件也来不及写。
     */
    private fun crash(): String {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            throw RuntimeException("DebugBridge 测试崩溃（验证崩溃上报链路）")
        }
        return "OK: 已请求在主线程抛出测试异常，主进程即将崩溃"
    }

    // ==================== 工具 ====================

    /** 只允许读日志目录下的文件名，挡掉 `../../` 之类的路径穿越。 */
    private fun sanitizeFileName(name: String): String? {
        if (name.isBlank()) return null
        if (name.contains('/') || name.contains('\\') || name.contains("..")) return null
        return name
    }

    /**
     * 同时兼容 `--ez on true` 与 `--es on true`：am 的写法在不同习惯下不统一。
     *
     * 这里刻意用无类型的 `Bundle.get(String)`（API 33 起被标记弃用）而不是 `getBooleanExtra`
     * 之类：调用方可能把同一个 key 传成布尔或字符串，只有先拿到 `Any?` 才能准确判别类型；
     * 换成强类型 getter 的话，类型不符时会走 `typeWarning` 打一条 logcat 噪音再返回默认值。
     */
    @Suppress("DEPRECATION")
    private fun boolExtra(intent: Intent, name: String, default: Boolean): Boolean {
        if (!intent.hasExtra(name)) return default
        return when (val raw = intent.extras?.get(name)) {
            is Boolean -> raw
            is String -> raw.toBooleanStrictOrNull() ?: default
            else -> default
        }
    }

    private fun canDrawOverlays(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)
    }
}
