package com.RobinNotBad.BiliClient

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import androidx.multidex.MultiDex
import com.RobinNotBad.BiliClient.activity.base.InstanceActivity
import com.RobinNotBad.BiliClient.activity.user.info.UserInfoActivity
import com.RobinNotBad.BiliClient.api.DynamicApi
import com.RobinNotBad.BiliClient.api.MessageApi
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.ui.theme.ThemeManager
import com.RobinNotBad.BiliClient.util.Logu
import com.RobinNotBad.BiliClient.util.PerformanceManager
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import com.RobinNotBad.BiliClient.util.TerminalContext
import dagger.hilt.android.HiltAndroidApp
import org.json.JSONException
import java.io.IOException
import java.lang.ref.WeakReference

private const val DELAYED_INIT_DELAY = 1000L

@HiltAndroidApp
class BiliTerminalApp : Application() {

    companion object {
        @SuppressLint("StaticFieldLeak")
        @JvmStatic
        lateinit var appInstance: BiliTerminalApp
            private set

        @SuppressLint("StaticFieldLeak")
        @JvmField
        var context: Context? = null

        @JvmField
        var DPI_FORCE_CHANGE = false

        private var instance: WeakReference<InstanceActivity> = WeakReference(null)

        @JvmStatic
        fun setInstance(instanceActivity: InstanceActivity) {
            instance = WeakReference(instanceActivity)
        }

        @JvmStatic
        fun getInstanceActivityOnTop(): InstanceActivity? = instance.get()

        @JvmStatic
        fun getFitDisplayContext(old: Context): Context {
            val dpiTimes = SharedPreferencesUtil.getFloat("dpi", 1.0f)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1) return old
            if (!DPI_FORCE_CHANGE && dpiTimes == 1.0f) return old
            return try {
                val displayMetrics = old.resources.displayMetrics
                val configuration = old.resources.configuration
                configuration.densityDpi = (displayMetrics.densityDpi * dpiTimes).toInt()
                old.createConfigurationContext(configuration)
            } catch (e: Exception) {
                old
            }
        }

        @JvmStatic
        fun getVersion(): Int {
            return context!!.packageManager.getPackageInfo(context!!.packageName, 0).versionCode
        }

        @JvmStatic
        fun isDebugBuild(): Boolean = BuildConfig.BUILD_TYPE == "debug"

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
            val intent = Intent().apply {
                setClass(context, UserInfoActivity::class.java)
                putExtra("mid", mid)
            }
            context.startActivity(intent)
        }
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        MultiDex.install(this)
    }

    override fun onCreate() {
        super.onCreate()
        appInstance = this

        if (context == null) {
            SharedPreferencesUtil.sharedPreferences = getSharedPreferences("default", MODE_PRIVATE)
            
            val theme = SharedPreferencesUtil.getString(ThemeManager.PREF_KEY_THEME, ThemeManager.THEME_BILIBILI_PINK)
            val themeResId = if (theme == ThemeManager.THEME_ZHIHU_BLUE) {
                R.style.Theme_ZhihuBlue
            } else {
                R.style.Theme_BiliClient
            }
            setTheme(themeResId)
            context = getFitDisplayContext(this)

            // 初始化性能管理器 - 设备检测与自适应优化
            PerformanceManager.init(this)

            val errorCatch = ErrorCatch.getInstance()
            errorCatch.init(context!!)

            val debugBuild = isDebugBuild()
            Logu.LOGV_ENABLED = SharedPreferencesUtil.getBoolean("dev_logv", debugBuild)
            Logu.LOGD_ENABLED = SharedPreferencesUtil.getBoolean("dev_logd", debugBuild)
            Logu.LOGI_ENABLED = SharedPreferencesUtil.getBoolean("dev_logi", debugBuild)

            scheduleDelayedInitialization()
        }
    }

    private fun scheduleDelayedInitialization() {
        Handler(Looper.getMainLooper()).postDelayed({
            performDelayedInitialization()
        }, DELAYED_INIT_DELAY)
    }

    private fun performDelayedInitialization() {
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
    }
}