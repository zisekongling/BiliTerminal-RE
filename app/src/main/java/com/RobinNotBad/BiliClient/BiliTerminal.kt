package com.RobinNotBad.BiliClient

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import com.RobinNotBad.BiliClient.activity.base.InstanceActivity
import com.RobinNotBad.BiliClient.activity.settings.UpdateActivity
import com.RobinNotBad.BiliClient.activity.user.info.UserInfoActivity
import com.RobinNotBad.BiliClient.api.DynamicApi
import com.RobinNotBad.BiliClient.api.MessageApi
import com.RobinNotBad.BiliClient.tutorial.TutorialStore
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.Logu
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.PerformanceManager
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import com.RobinNotBad.BiliClient.util.TerminalContext
import com.RobinNotBad.BiliClient.util.UpdateManager
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
        fun getFitDisplayContext(old: Context?): Context? {
            val dpiTimes = SharedPreferencesUtil.getFloat("dpi", 1.0F)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1) return old
            if (!DPI_FORCE_CHANGE && dpiTimes == 1.0F) return old
            return try {
                val ctx = old!!
                val displayMetrics = ctx.resources.displayMetrics
                val configuration = ctx.resources.configuration
                configuration.densityDpi = (displayMetrics.densityDpi * dpiTimes).toInt()
                ctx.createConfigurationContext(configuration)
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

        @JvmStatic
        fun isDebugBuild(): Boolean = "debug" == BuildConfig.BUILD_TYPE

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
    }

    override fun onCreate() {
        super.onCreate()
        if (context == null) {
            SharedPreferencesUtil.sharedPreferences = getSharedPreferences("default", MODE_PRIVATE)
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
                override fun onActivityResumed(activity: Activity) {}
                override fun onActivityPaused(activity: Activity) {}
                override fun onActivityStopped(activity: Activity) {}
                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
                override fun onActivityDestroyed(activity: Activity) {}
            })

            val errorCatch = ErrorCatch.getInstance()
            errorCatch.init(context)

            val debugBuild = isDebugBuild()
            Logu.LOGV_ENABLED = SharedPreferencesUtil.getBoolean("dev_logv", debugBuild)
            Logu.LOGD_ENABLED = SharedPreferencesUtil.getBoolean("dev_logd", debugBuild)
            Logu.LOGI_ENABLED = SharedPreferencesUtil.getBoolean("dev_logi", debugBuild)

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
                        val messageUnread = MessageApi.checkMessageUnread()
                        val privateMsgUnread = MessageApi.checkPrivateMsgUnread()
                        val totalUnread = messageUnread + privateMsgUnread
                        SharedPreferencesUtil.putInt(SharedPreferencesUtil.MESSAGE_UPDATE_NUM, totalUnread)
                    } catch (e: IOException) {
                        SharedPreferencesUtil.putInt(SharedPreferencesUtil.MESSAGE_UPDATE_NUM, 0)
                    } catch (e: JSONException) {
                        SharedPreferencesUtil.putInt(SharedPreferencesUtil.MESSAGE_UPDATE_NUM, 0)
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
                                if (config.versionCode != lastNewVersion) {
                                    CenterThreadPool.runOnUiThread {
                                        MsgUtil.showMsg("发现新版本 " + config.versionName)
                                    }
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
