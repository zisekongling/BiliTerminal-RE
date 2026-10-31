package com.RobinNotBad.BiliClient

import com.RobinNotBad.BiliClient.ui.appearance.ColorMode
import com.RobinNotBad.BiliClient.util.UiText

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.multidex.MultiDex
import com.RobinNotBad.BiliClient.activity.SplashActivity
import com.RobinNotBad.BiliClient.activity.base.InstanceActivity
import com.RobinNotBad.BiliClient.activity.settings.UpdateActivity
import com.RobinNotBad.BiliClient.activity.user.info.UserInfoActivity
import com.RobinNotBad.BiliClient.api.DynamicApi
import com.RobinNotBad.BiliClient.api.MessageApi
import com.RobinNotBad.BiliClient.model.UpdateConfig
import com.RobinNotBad.BiliClient.tutorial.TutorialStore
import com.RobinNotBad.BiliClient.util.BangumiUpdateChecker
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.CrashTrail
import com.RobinNotBad.BiliClient.util.Logu
import com.RobinNotBad.BiliClient.util.MsgNotifier
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.NetWorkUtil
import com.RobinNotBad.BiliClient.util.PerformanceManager
import com.RobinNotBad.BiliClient.util.SettingsKeys
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import com.RobinNotBad.BiliClient.util.TerminalContext
import com.RobinNotBad.BiliClient.util.UpdateManager
import me.ele.uetool.UETool
import org.json.JSONException
import java.io.IOException
import java.lang.ref.WeakReference

/**
 * 应用入口（AndroidManifest.xml 的 `android:name=".BiliTerminal"` 指向本类）。
 *
 * 全局 Context 取 [context]，它是**静态字段**而非 getter——这样 Java 侧依旧是
 * `BiliTerminal.context` 的字段读法（零方法调用、零入口判空），Kotlin 侧按需 `!!`。
 */
class BiliTerminal : Application() {

    companion object {

        @SuppressLint("StaticFieldLeak")
        @JvmField
        var context: Context? = null

        @JvmField
        var DPI_FORCE_CHANGE = false

        /**
         * Agent 测试模式总开关（26.10.14）。
         *
         * <p>由 [applyLogSwitches] 按「Debug 构建 + `dev_agent_mode`」计算，Release 下恒为 false。
         * 打开后的效果：日志与 HTTP 追踪全量打开并落盘、启动时跳过 UETool 悬浮窗的权限跳转与界面遮挡。
         *
         * <p>它解决的是「agent 没法点 UI」这件事：手表屏幕上要进设置页翻三四个层级才能开日志，
         * 自动化点击既慢又不可靠；现在一条 adb 命令即可。
         *
         * <p>标 `@Volatile`：这个值可能在后台线程（调试桥的 `agent` 命令）被改，而主线程的
         * `SplashActivity` 要立刻读到——不加就可能"改了但这次启动没生效"。
         */
        @JvmField
        @Volatile
        var AGENT_TEST_MODE = false

        /** 当前处于 resumed 状态的 Activity（弱引用，不阻止回收）。给调试桥的 `top` 命令用。 */
        private var currentActivityRef: WeakReference<Activity> = WeakReference(null)

        /** 取当前前台 Activity；应用不在前台时返回 null。 */
        @JvmStatic
        fun getCurrentActivity(): Activity? = currentActivityRef.get()

        private var instance: WeakReference<InstanceActivity> = WeakReference(null)

        @Volatile
        private var forceUpdateBlocking = false

        @Volatile
        private var forceUpdateVersionCode = 0

        @Volatile
        private var forceUpdateVersionName: String? = null

        @Volatile
        private var forceUpdateDescription: String? = null

        @Volatile
        private var forceUpdateDownloadUrl: String? = null

        @JvmStatic
        fun clearForceUpdate() {
            forceUpdateBlocking = false
            forceUpdateVersionCode = 0
            forceUpdateVersionName = null
            forceUpdateDescription = null
            forceUpdateDownloadUrl = null
            SharedPreferencesUtil.removeValue("force_update_required")
            SharedPreferencesUtil.removeValue("force_update_version_code")
            SharedPreferencesUtil.removeValue("force_update_version_name")
            SharedPreferencesUtil.removeValue("force_update_description")
            SharedPreferencesUtil.removeValue("force_update_download_url")
        }

        /**
         * 非强制更新时「待自动打开更新页」的版本信息（26.10.25）。
         *
         * 检查更新是异步的，结果可能在**启动页还在播动画时**返回：此刻直接 startActivity，
         * 启动页随后拉起首屏的动作会把它盖在下面，用户根本看不到。所以先记下，
         * 等前台页就绪（[onActivityResumed] 回调）再打开。
         */
        @Volatile
        private var pendingAutoOpenUpdate: UpdateConfig? = null

        /**
         * 记下「发现新版本（非强制）」并尝试立刻打开更新页。
         *
         * 「一个版本只自动跳一次」由调用方按 `update_last_new_version` 过滤（该键在更新页
         * 打开时写入）；页面拉不起来时才退回原来那条「发现新版本」Toast，至少让用户知道有新版。
         */
        private fun requestAutoOpenUpdatePage(config: UpdateConfig) {
            pendingAutoOpenUpdate = config
            tryShowAutoOpenUpdatePage()
        }

        /**
         * 前台页就绪时打开挂起的更新页。
         *
         * 启动页（[SplashActivity]）不算就绪：它稍后还会 startActivity(首屏)，此时打开的
         * 更新页会被盖住；已在更新页时也不再叠一层。
         */
        private fun tryShowAutoOpenUpdatePage() {
            val config = pendingAutoOpenUpdate ?: return
            val activity = getCurrentActivity() ?: return
            if (activity is SplashActivity) return
            pendingAutoOpenUpdate = null
            if (activity is UpdateActivity) return
            try {
                val intent = Intent(activity, UpdateActivity::class.java)
                intent.putExtra("has_config", true)
                intent.putExtra("version_code", config.versionCode)
                intent.putExtra("version_name", config.versionName)
                intent.putExtra("description", config.description)
                intent.putExtra("download_url", config.downloadUrl)
                intent.putExtra("force_update", false)
                activity.startActivity(intent)
                Logu.i("更新检查", "发现新版本 ${config.versionName}，已自动打开更新页")
            } catch (e: Exception) {
                Logu.w("更新检查", "自动打开更新页失败：${e.message}")
                MsgUtil.showMsg(UiText.of("发现新版本 ") + config.versionName)
            }
        }

        @JvmStatic
        fun setInstance(instanceActivity: InstanceActivity) {
            instance = WeakReference(instanceActivity)
        }

        @JvmStatic
        fun getInstanceActivityOnTop(): InstanceActivity? = instance.get()

        /**
         * 重写attachBaseContext方法，用于调整应用内dpi
         * 尝试下这种风格代码是否会导致低版本设备异常
         *
         * 参数/返回保持可空：旧 Java 版无 `@Nullable`/`@NonNull` 注解，是平台类型，
         * 而 `SplashActivity.attachBaseContext` 把入参声明成了 `Context?`。写成非空会编译失败。
         *
         * @param old The origin context.
         */
        @JvmStatic
        @JvmOverloads
        fun getFitDisplayContext(old: Context?, forceDark: Boolean = false): Context? {
            val dpiTimes = SharedPreferencesUtil.getFloat("dpi", 1.0F)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1) return old
            if (old == null) return null
            val dpiChanged = DPI_FORCE_CHANGE || dpiTimes != 1.0F
            // 暗色 / 亮色模式：由我们决定 uiMode 的夜间位（亮色命中 -notnight 资源），见 ColorMode。
            // 手表系统普遍报 NIGHT_NO，所以暗色模式也必须显式写 NIGHT_YES。
            val night = ColorMode.nightBits(forceDark)
            val nightMatches = (old.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == night
            if (!dpiChanged && nightMatches) return old
            return try {
                val displayMetrics = old.resources.displayMetrics
                // 拷贝一份再改：此前直接改 resources.configuration（宿主 Resources 的活配置对象）
                val configuration = Configuration(old.resources.configuration)
                if (dpiChanged) configuration.densityDpi = (displayMetrics.densityDpi * dpiTimes).toInt()
                configuration.uiMode = (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or night
                old.createConfigurationContext(configuration)
            } catch (e: Exception) {
                //MsgUtil.err(e,old);
                old
            }
        }

        @JvmStatic
        @Throws(PackageManager.NameNotFoundException::class)
        @Suppress("DEPRECATION")
        fun getVersion(): Int =
            context!!.packageManager.getPackageInfo(context!!.packageName, 0).versionCode

        /**
         * 是否 Debug 构建。
         * 用 BuildConfig.DEBUG（编译期常量）而不是比较 BUILD_TYPE 字符串：前者能被 R8
         * 常量折叠，从而把 debug-only 分支整体 strip；后者是运行期判断，永远消除不掉（审计 M12-d）。
         */
        @JvmStatic
        fun isDebugBuild(): Boolean = BuildConfig.DEBUG

        /**
         * UETool 悬浮窗请求码（入口 Activity onActivityResult 使用）。
         *
         * 原先这几个 UETool 辅助方法挂在 [BiliTerminalApp] 上——那是个从未被实例化的
         * Application 死类（Manifest 的 Application 一直是 [BiliTerminal]），
         * 只有 SplashActivity 借它的 companion 当工具类用（审计 M13-e）。
         * 现收拢到真正的 Application 类里，死类已删除。
         */
        @JvmStatic
        val REQUEST_OVERLAY_PERMISSION_FOR_UETOOL = 10086

        /**
         * 检查是否拥有系统悬浮窗绘制权限（兼容 Android M 以下）。
         * @param context 任意可用 Context（通常传 Activity）
         */
        @JvmStatic
        fun canDrawOverlaysCompat(context: Context): Boolean {
            return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)
        }

        /**
         * 跳转到系统设置页申请悬浮窗权限。
         *
         * <p>26.10.18：和 [com.RobinNotBad.BiliClient.util.FileUtil.requestStoragePermission] 同一个坑——
         * 手表 ROM 可能**根本没有**这个设置页，裸 `startActivityForResult` 抛
         * `ActivityNotFoundException` 会把启动页带崩。拉不起来就只提示，并返回 false 让调用方
         * **继续启动流程**（否则启动页会一直等一个永远不会到来的 `onActivityResult`）。
         *
         * @return 是否成功拉起设置页
         */
        @JvmStatic
        fun requestOverlayPermission(activity: Activity): Boolean {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !canDrawOverlaysCompat(activity)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${activity.packageName}")
                )
                try {
                    activity.startActivityForResult(intent, REQUEST_OVERLAY_PERMISSION_FOR_UETOOL)
                    return true
                } catch (e: Exception) {
                    // 不只 ActivityNotFoundException：该页存在但未导出时抛 SecurityException，处理方式相同
                    Logu.w("overlay-perm", "悬浮窗设置页拉不起来：${e.javaClass.simpleName}")
                    MsgUtil.showMsgLong(UiText.of("此设备打不开悬浮窗权限设置页，UETool 调试悬浮窗不可用"))
                    return false
                }
            }
            return false
        }

        /**
         * 显示 UETool 调试悬浮窗（仅 Debug 构建有真实实现；Release 依赖 uetool-no-op 空实现）。
         */
        @JvmStatic
        fun showUEToolMenu() {
            if (isDebugBuild()) {
                try {
                    UETool.showUETMenu()
                } catch (e: Exception) {
                    // 兜底：防止 WindowManager / Context 异常导致应用崩溃
                    e.printStackTrace()
                }
            }
        }

        @JvmStatic
        fun jumpToVideo(context: Context, aid: Long) {
            TerminalContext.getInstance().enterVideoDetailPage(context, aid)
        }

        @JvmStatic
        fun jumpToVideo(context: Context, bvid: String) {
            TerminalContext.getInstance().enterVideoDetailPage(context, bvid)
        }

        @JvmStatic
        fun jumpToArticle(context: Context, cvid: Long) {
            TerminalContext.getInstance().enterArticleDetailPage(context, cvid)
        }

        @JvmStatic
        fun jumpToUser(context: Context, mid: Long) {
            val intent = Intent()
            intent.setClass(context, UserInfoActivity::class.java)
            intent.putExtra("mid", mid)
            context.startActivity(intent)
        }

        /**
         * 日志开关（两个进程都要设：崩溃页自己也会打日志）
         *
         * <p>26.10.14 起这里同时负责「Agent 测试模式」的落地：开关本身只是 SharedPreferences 里的一个布尔，
         * 真正影响行为的三处（[Logu] 的日志级别与落盘、[NetWorkUtil.HTTP_TRACE]、[AGENT_TEST_MODE]）
         * 都在这里一次性写入，保证「改开关」与「生效」是同一处代码——agent 通过调试桥改完立即调本方法即可。
         */
        @JvmStatic
        fun applyLogSwitches() {
            val debugBuild = isDebugBuild()

            // Agent 模式只在 Debug 构建下有意义：release 里 debugBuild 是编译期常量 false，
            // R8 会把整段折叠掉，开关即使被写进 SharedPreferences 也不产生任何效果。
            val agentMode = debugBuild && SharedPreferencesUtil.getBoolean(SettingsKeys.DEV_AGENT_MODE, false)
            AGENT_TEST_MODE = agentMode

            Logu.AGENT_MODE = agentMode
            Logu.LOGV_ENABLED = agentMode || SharedPreferencesUtil.getBoolean(SettingsKeys.DEV_LOGV, debugBuild)
            Logu.LOGD_ENABLED = agentMode || SharedPreferencesUtil.getBoolean(SettingsKeys.DEV_LOGD, debugBuild)
            Logu.LOGI_ENABLED = agentMode || SharedPreferencesUtil.getBoolean(SettingsKeys.DEV_LOGI, debugBuild)

            // 落盘默认随 Agent 模式打开（dev_log_file 默认 true）：agent 常在没有 adb 连接的场景下
            // 事后取证，只靠 logcat 会什么都拿不到。release 下 agentMode 恒 false，永不落盘。
            Logu.LOG_TO_FILE = agentMode && SharedPreferencesUtil.getBoolean(SettingsKeys.DEV_LOG_FILE, true)

            // HTTP 追踪只在 Agent 模式下开：它会给每次请求做一次响应体 peek，属于纯观测开销。
            NetWorkUtil.HTTP_TRACE = agentMode

            if (agentMode) {
                Logu.i("BiliTerminal", "Agent 测试模式已开启：日志全量+落盘=${Logu.LOG_TO_FILE}，HTTP追踪=${NetWorkUtil.HTTP_TRACE}")
            }
        }
    }

    /**
     * 崩溃页 `CatchActivity` 所在进程的后缀（见 AndroidManifest 里它的 `android:process`）
     */
    private val errorProcessSuffix = ":error_activity"

    /**
     * 取当前进程名。
     *
     * 26.10.04 批次 3（B8）：minSdk 19 用不了 API 28 才有的 `Application.getProcessName()`，
     * 只能读 `/proc/self/cmdline`——内核把进程名按 NUL 结尾写进去，`readLine` 会把那个 NUL 一起带回来。
     */
    private fun currentProcessName(): String = try {
        java.io.RandomAccessFile("/proc/self/cmdline", "r").use { file ->
            file.readLine()?.trimEnd('\u0000') ?: packageName
        }
    } catch (e: Exception) {
        packageName
    }

    /**
     * minSdk 19（< 21）时 Dalvik 只加载 `classes.dex`，剩下的 dex 必须在 `attachBaseContext`
     * 阶段手动装进 ClassLoader，否则一碰后面的类就是 `ClassNotFoundException`。
     * minSdk 提回 21 后连同 `androidx.multidex` 依赖一起删掉。
     */
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        MultiDex.install(this)
    }

    override fun onCreate() {
        super.onCreate()

        // 26.10.04 批次 3（B8）：崩溃页跑在独立进程 :error_activity 里，这里只做**最小初始化**——
        // 够让崩溃页把堆栈显示出来即可。其余（教程键迁移、性能检测、强制更新、未读轮询、
        // 自动更新检查、全局异常捕获）全部不碰：主进程刚崩溃、随时会被 killProcess，
        // 错误进程里再跑网络与磁盘逻辑，只会把"崩溃页都打不开"变成第二种崩溃。
        if (currentProcessName().endsWith(errorProcessSuffix)) {
            if (context == null) {
                SharedPreferencesUtil.sharedPreferences = getSharedPreferences("default", MODE_PRIVATE)
                context = getFitDisplayContext(this)
                applyLogSwitches()
            }
            return
        }

        if (context == null) {
            SharedPreferencesUtil.sharedPreferences = getSharedPreferences("default", MODE_PRIVATE)
            // 暗色 / 亮色模式：在任何 Activity 创建前告诉 AppCompat（否则它会按系统夜间位重写）
            ColorMode.applyGlobal()
            context = getFitDisplayContext(this)

            // 教程系统重构：一次性把旧键迁移到新 id（旧系统拿数组下标当 tag 且错位，详见 docs/tutorial-system-redesign.md）
            TutorialStore.migrateLegacyKeys()

            // 初始化性能管理器 - 设备检测与自适应优化
            PerformanceManager.init(this)

            forceUpdateBlocking = SharedPreferencesUtil.getBoolean("force_update_required", false)
            if (forceUpdateBlocking) {
                forceUpdateVersionCode = SharedPreferencesUtil.getInt("force_update_version_code", 0)
                forceUpdateVersionName = SharedPreferencesUtil.getString("force_update_version_name", null)
                forceUpdateDescription = SharedPreferencesUtil.getString("force_update_description", null)
                forceUpdateDownloadUrl = SharedPreferencesUtil.getString("force_update_download_url", null)
                // 已更新到强制要求的版本（安装完成重启后），解除拦截
                try {
                    if (forceUpdateVersionCode > 0 && getVersion() >= forceUpdateVersionCode) {
                        clearForceUpdate()
                    }
                } catch (ignored: PackageManager.NameNotFoundException) {
                }
            }

            registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {

                override fun onActivityPreCreated(activity: Activity, savedInstanceState: Bundle?) {
                    if (forceUpdateBlocking && activity !is UpdateActivity) {
                        val intent = Intent(activity, UpdateActivity::class.java)
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        intent.putExtra("has_config", true)
                        intent.putExtra("version_code", forceUpdateVersionCode)
                        intent.putExtra("version_name", forceUpdateVersionName)
                        intent.putExtra("description", forceUpdateDescription)
                        intent.putExtra("download_url", forceUpdateDownloadUrl)
                        intent.putExtra("force_update", true)
                        activity.startActivity(intent)
                        activity.finish()
                    }
                }

                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
                override fun onActivityStarted(activity: Activity) {}
                override fun onActivityResumed(activity: Activity) {
                    // 26.10.09（自建崩溃报告）：崩溃报告里最有用的一条是「崩溃前用户在干什么」，
                    // 而 Android 10 起应用读不到 logcat（READ_LOGS 是系统权限），只能自己记页面轨迹
                    // （见 CrashTrail）。记在 resumed 而不是 created：从返回栈重入某个页面时
                    // created 不会重放，记在 created 会让轨迹断掉。
                    CrashTrail.record(activity.javaClass.simpleName)
                    // 26.10.14：同时留一个弱引用给调试桥——agent 靠它回答「现在停在哪个页面」，
                    // 这是纯 UI 自动化做不到的（手表上 uiautomator 又慢又常拿不到窗口）。
                    currentActivityRef = WeakReference(activity)
                    // 26.10.25：检查更新结果可能在启动页期间就返回，那时不能直接跳更新页
                    // （会被启动页随后的首屏盖住），挂起到这里——首屏 resumed 后再打开。
                    tryShowAutoOpenUpdatePage()
                }

                override fun onActivityPaused(activity: Activity) {}
                override fun onActivityStopped(activity: Activity) {}
                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
                override fun onActivityDestroyed(activity: Activity) {}
            })

            val errorCatch = ErrorCatch.getInstance()
            errorCatch.init(context)

            applyLogSwitches()

            if (SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.DYNAMIC_UPDATE_CHECK_ENABLE, true)
                && SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0) != 0L
            ) {
                CenterThreadPool.run {
                    try {
                        val updateBaseline = SharedPreferencesUtil.getLong("dynamic_update_baseline", 0)
                        val updateNum = DynamicApi.checkDynamicUpdate("all", updateBaseline)
                        SharedPreferencesUtil.putInt(SharedPreferencesUtil.DYNAMIC_UPDATE_NUM, updateNum)
                    } catch (e: IOException) {
                        SharedPreferencesUtil.putInt(SharedPreferencesUtil.DYNAMIC_UPDATE_NUM, 0)
                    } catch (e: JSONException) {
                        SharedPreferencesUtil.putInt(SharedPreferencesUtil.DYNAMIC_UPDATE_NUM, 0)
                    }
                }
            }

            if (SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.MESSAGE_UPDATE_CHECK_ENABLE, true)
                && SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0) != 0L
            ) {
                CenterThreadPool.run {
                    try {
                        // 先记下上次的未读数：只有"变多了"才弹通知（见 MsgNotifier.shouldNotify）
                        val previousUnread = SharedPreferencesUtil.getInt(SharedPreferencesUtil.MESSAGE_UPDATE_NUM, 0)
                        val messageUnread = MessageApi.checkMessageUnread()
                        val privateMsgUnread = MessageApi.checkPrivateMsgUnread()
                        val totalUnread = messageUnread + privateMsgUnread
                        SharedPreferencesUtil.putInt(SharedPreferencesUtil.MESSAGE_UPDATE_NUM, totalUnread)

                        val notifyEnabled = SharedPreferencesUtil.getBoolean(SettingsKeys.PRIVATE_MSG_NOTIFY_ENABLE, true)
                        if (MsgNotifier.shouldNotify(previousUnread, totalUnread, notifyEnabled)) {
                            context?.let { MsgNotifier.notifyNewMessages(it, privateMsgUnread, messageUnread) }
                        }
                    } catch (e: IOException) {
                        // 检查失败不再把未读数清零：清零会让下一次成功检查把"老未读"当成新增未读，
                        // 网络抖一次就重复弹通知。保留上次已知值，既不误报也不丢提示。
                        Logu.w("BiliTerminal", "未读检查失败: ${e.message}")
                    } catch (e: JSONException) {
                        Logu.w("BiliTerminal", "未读检查失败: ${e.message}")
                    }
                }
            }

            // C16：追番更新提醒。只在这里检查（用户拍板不做后台定时），失败保持旧快照不清空。
            if (SharedPreferencesUtil.getBoolean(SettingsKeys.BANGUMI_UPDATE_NOTIFY_ENABLE, true)
                && SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0) != 0L
            ) {
                CenterThreadPool.run {
                    try {
                        context?.let { BangumiUpdateChecker.checkAndNotify(it) }
                    } catch (e: IOException) {
                        Logu.w("BiliTerminal", "追番更新检查失败: ${e.message}")
                    } catch (e: JSONException) {
                        Logu.w("BiliTerminal", "追番更新检查失败: ${e.message}")
                    }
                }
            }

            checkAppUpdate()
        }
    }

    private fun checkAppUpdate() {
        if (!SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.AUTO_UPDATE_CHECK_ENABLE, true)) {
            return
        }
        // 强制更新生效中：不重复检查更新，避免网络返回异常或配置变更时误解除拦截
        if (forceUpdateBlocking) {
            return
        }
        CenterThreadPool.run {
            try {
                UpdateManager.checkUpdate(
                    onResult = { config ->
                        if (UpdateManager.hasUpdate(config)) {
                            if (config.isForceUpdate) {
                                forceUpdateVersionCode = config.versionCode
                                forceUpdateVersionName = config.versionName
                                forceUpdateDescription = config.description
                                forceUpdateDownloadUrl = config.downloadUrl
                                forceUpdateBlocking = true
                                SharedPreferencesUtil.putBoolean("force_update_required", true)
                                SharedPreferencesUtil.putInt("force_update_version_code", config.versionCode)
                                SharedPreferencesUtil.putString("force_update_version_name", config.versionName)
                                SharedPreferencesUtil.putString("force_update_description", config.description)
                                SharedPreferencesUtil.putString("force_update_download_url", config.downloadUrl)
                                CenterThreadPool.runOnUiThread {
                                    val intent = Intent(context, UpdateActivity::class.java)
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    intent.putExtra("has_config", true)
                                    intent.putExtra("version_code", config.versionCode)
                                    intent.putExtra("version_name", config.versionName)
                                    intent.putExtra("description", config.description)
                                    intent.putExtra("download_url", config.downloadUrl)
                                    intent.putExtra("force_update", true)
                                    context!!.startActivity(intent)
                                }
                            } else {
                                clearForceUpdate()
                                val lastNewVersion = SharedPreferencesUtil.getInt("update_last_new_version", 0)
                                // 26.10.25 用户要求：发现新版本时不再只弹一条 Toast（手表上很容易错过），
                                // 而是直接跳到「检查更新/下载」页，用户点「下载更新」即可。
                                if (UpdateManager.shouldAutoOpenUpdatePage(
                                        config,
                                        UpdateManager.getCurrentVersion(),
                                        lastNewVersion
                                    )
                                ) {
                                    CenterThreadPool.runOnUiThread { requestAutoOpenUpdatePage(config) }
                                }
                            }
                        } else {
                            clearForceUpdate()
                        }
                    },
                    onError = { }
                )
            } catch (e: Exception) {
            }
        }
    }
}