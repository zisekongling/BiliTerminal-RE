package com.RobinNotBad.BiliClient.activity.settings

import android.content.Intent
import android.graphics.PorterDuff
import android.graphics.drawable.LayerDrawable
import com.RobinNotBad.BiliClient.ui.appearance.ColorScheme
import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import com.RobinNotBad.BiliClient.BuildConfig
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.base.BaseActivity
import com.RobinNotBad.BiliClient.model.UpdateConfig
import com.RobinNotBad.BiliClient.util.BetaUpdate
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.Logu
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import com.RobinNotBad.BiliClient.util.UpdateChannel
import com.RobinNotBad.BiliClient.util.UpdateManager
import com.RobinNotBad.BiliClient.util.WeiseMarket
import java.io.File

class UpdateActivity : BaseActivity() {

    private companion object {
        const val TAG = "微思下载"
    }

    private lateinit var tipTitle: TextView
    private lateinit var versionNameText: TextView
    private lateinit var contentText: TextView
    private lateinit var buttonContainer: View
    private lateinit var progressContainer: View
    private lateinit var loadingOverlay: View
    private lateinit var loadingText: TextView
    private lateinit var downloadProgress: ProgressBar
    private lateinit var progressText: TextView
    private lateinit var cancelBtn: View
    private lateinit var downloadBtn: View
    private lateinit var cancelDownloadBtn: View
    private lateinit var retryBtn: View
    private lateinit var descScroll: View
    private lateinit var weseeBtn: View

    private var updateConfig: UpdateConfig? = null
    private var isDownloading: Boolean = false
    private var downloadFinished: Boolean = false
    private var downloadedFile: File? = null

    /** 内测模式：非空表示本页是从「长按检查更新 → 输入内测码」进来的。 */
    private var betaCode: String? = null
    private var betaFileSize: Long = 0L

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_update)

        tipTitle = findViewById(R.id.tip_title)
        versionNameText = findViewById(R.id.version_name)
        contentText = findViewById(R.id.content)
        buttonContainer = findViewById(R.id.button_container)
        progressContainer = findViewById(R.id.progress_container)
        loadingOverlay = findViewById(R.id.loading_overlay)
        loadingText = findViewById(R.id.loading_text)
        downloadProgress = findViewById(R.id.download_progress)
        // progress_horizontal 的进度层写死 @color/pink：只给进度层着主题主色，底槽不动
        (downloadProgress.progressDrawable?.mutate() as? LayerDrawable)
            ?.findDrawableByLayerId(android.R.id.progress)
            // 用 colorFilter 而非 DrawableCompat.wrap：API < 23 上 wrap 会返回新包装对象，改不到层里的原件
            ?.setColorFilter(ColorScheme.PRIMARY, PorterDuff.Mode.SRC_IN)
        progressText = findViewById(R.id.progress_text)
        cancelBtn = findViewById(R.id.cancel_btn)
        downloadBtn = findViewById(R.id.download_btn)
        cancelDownloadBtn = findViewById(R.id.cancel_download_btn)
        retryBtn = findViewById(R.id.retry_btn)
        descScroll = findViewById(R.id.desc_scroll)
        weseeBtn = findViewById(R.id.wesee_btn)

        cancelBtn.setOnClickListener { finish() }
        downloadBtn.setOnClickListener { startDownload() }
        cancelDownloadBtn.setOnClickListener {
            UpdateManager.cancelDownload()
            isDownloading = false
            if (updateConfig?.isForceUpdate == true) {
                showUpdateInfo()
            } else {
                finish()
            }
        }
        retryBtn.setOnClickListener {
            // 内测模式下重试要重新解析：分享站的下载地址带签名/时间窗，旧地址可能已经失效
            if (betaCode != null) resolveBeta() else startDownload()
        }

        findViewById<View>(R.id.top).setOnClickListener { onBackPressed() }

        val hasConfig = intent.getBooleanExtra("has_config", false)
        val betaCodeExtra = intent.getStringExtra("beta_code")

        when {
            !betaCodeExtra.isNullOrBlank() -> {
                betaCode = betaCodeExtra
                // 内测包与普通更新包共用同一个缓存文件，先清掉可能残留的旧包，
                // 否则 Range 续传会把两份不同的 APK 拼在一起
                UpdateManager.deleteOldApkFile()
                resolveBeta()
            }

            hasConfig -> {
                val versionCode = intent.getIntExtra("version_code", 0)
                val versionName = intent.getStringExtra("version_name")
                val description = intent.getStringExtra("description")
                val downloadUrl = intent.getStringExtra("download_url")
                val forceUpdate = intent.getBooleanExtra("force_update", false)

                updateConfig = UpdateConfig(versionCode, versionName, description, downloadUrl, forceUpdate)
                showUpdateInfo()
            }

            else -> {
                showLoading(true)
                checkUpdate()
            }
        }
    }

    /**
     * 内测模式：解析内测码 → 立刻开始下载，不做版本比较（内测包版本号可能等于当前版本）。
     */
    private fun resolveBeta() {
        val code = betaCode ?: return
        showLoading(true, getString(R.string.text_update_beta_checking))
        UpdateManager.resolveBetaShare(
            code,
            { file, _ ->
                showLoading(false)
                if (isFinishing || isDestroyed) {
                    kotlin.Unit
                } else if (BetaUpdate.isTextFileName(file.name)) {
                    // 内测码对应的是 .txt：不装包，把正文按公告样式展示
                    openBetaText(file)
                    kotlin.Unit
                } else {
                    betaFileSize = file.size
                    updateConfig = UpdateConfig(0, file.name, null, file.downloadUrl, false)
                    showBetaInfo()
                    startDownload()
                    kotlin.Unit
                }
            },
            { error ->
                showLoading(false)
                // 解析失败时 UpdateManager 已经给出完整人话（内测码错误 / 获取内测包失败：…）
                MsgUtil.showMsg(error)
                finish()
                kotlin.Unit
            }
        )
    }

    /**
     * 内测码对应的是 `.txt`：不下载安装包，把正文按「公告」的样式展示
     * （`MsgUtil.showText` → `ShowTextActivity`，与公告列表点开的是同一个页面）。
     */
    private fun openBetaText(file: BetaUpdate.ShareFile) {
        showLoading(true, getString(R.string.text_update_beta_text_loading))
        UpdateManager.downloadBetaText(
            file.downloadUrl,
            { text ->
                if (isFinishing || isDestroyed) {
                    kotlin.Unit
                } else {
                    showLoading(false)
                    MsgUtil.showText(betaTextTitle(file.name), text)
                    finish()
                    kotlin.Unit
                }
            },
            { error ->
                showLoading(false)
                MsgUtil.showMsg(error)
                finish()
                kotlin.Unit
            }
        )
    }

    /** 公告标题用分享文件名（去掉 `.txt`）；文件名不像话时退回通用标题。 */
    private fun betaTextTitle(name: String): String {
        val title = name.trim().substringBeforeLast('.', name.trim())
        return title.ifBlank { getString(R.string.text_update_beta_text_title) }
    }

    /** 内测模式的摘要区：显示文件名 / 大小 / 内测码，随后 [startDownload] 会盖上下载进度。 */
    @SuppressLint("SetTextI18n")
    private fun showBetaInfo() {
        tipTitle.text = getString(R.string.text_update_beta_title)
        versionNameText.text = getString(
            R.string.text_update_beta_version,
            updateConfig?.versionName ?: getString(R.string.text_update_beta_default_name),
            BetaUpdate.formatSize(betaFileSize)
        )
        contentText.text = getString(R.string.text_update_beta_desc, betaCode.orEmpty())

        descScroll.visibility = View.VISIBLE
        buttonContainer.visibility = View.GONE
        progressContainer.visibility = View.GONE
    }

    private fun showLoading(show: Boolean, text: String? = null) {
        loadingOverlay.visibility = if (show) View.VISIBLE else View.GONE
        if (show) {
            loadingText.text = text ?: getString(R.string.text_update_checking)
        }
    }

    private fun checkUpdate() {
        UpdateManager.checkUpdate(
            { config ->
                showLoading(false)
                if (UpdateManager.hasUpdate(config)) {
                    updateConfig = config
                    showUpdateInfo()
                } else {
                    // 带上通道名：三通道下「已是最新」这句话本身有歧义——稳定通道没更新，
                    // 不代表测试 / 实验通道也没有；不写清楚，用户会以为检查坏了。
                    MsgUtil.showMsg(
                        getString(
                            R.string.text_update_latest_channel,
                            getString(UpdateChannel.nameRes(UpdateManager.currentChannel()))
                        )
                    )
                    finish()
                }
                kotlin.Unit
            },
            { error ->
                showLoading(false)
                MsgUtil.showMsg(getString(R.string.text_update_check_failed, error))
                finish()
                kotlin.Unit
            }
        )
    }

    @SuppressLint("SetTextI18n")
    private fun showUpdateInfo() {
        tipTitle.text = getString(R.string.text_new_version_released)
        versionNameText.text = getString(R.string.text_update_version, updateConfig!!.versionName, updateConfig!!.versionCode)
        contentText.text = updateConfig!!.description

        SharedPreferencesUtil.putInt("update_last_new_version", updateConfig!!.versionCode)

        descScroll.visibility = View.VISIBLE
        buttonContainer.visibility = View.VISIBLE
        progressContainer.visibility = View.GONE

        if (updateConfig!!.isForceUpdate) {
            (cancelBtn as TextView).text = getString(R.string.btn_exit_app)
            cancelBtn.setOnClickListener { exitApp() }
        } else {
            (cancelBtn as TextView).text = getString(R.string.btn_cancel)
            cancelBtn.setOnClickListener { finish() }
        }

        // 26.11.16：去微思下载只有在「商店版本号 == 本次检测到的版本号」且剪贴板可写时才露出来，
        // 校验要走一次网络，放在后台，这里只是发个探查
        setupWeiseDownload()
    }

    /**
     * 「去微思下载」（26.11.16，仅共存版）。
     *
     * 两个条件都成立才显示按钮：
     * 1. 微思商店那条应用的 versionCode（接口里的 `verid`）与本次检测到的更新版本号**完全一致**——
     *    商店上的包是人手动传的，落后一版时点进去会把用户「更新」回旧版本，比不给按钮更糟；
     * 2. 剪贴板可写——这个按钮的约定动作是「复制链接 + 跳商店」，写不进去就不显示。
     *
     * 查版本号是网络请求，放后台；写剪贴板必须在主线程，所以分两段切回来。
     */
    private fun setupWeiseDownload() {
        weseeBtn.visibility = View.GONE
        if (!BuildConfig.COEXIST) return
        val config = updateConfig ?: return
        val targetVersion = config.versionCode
        if (targetVersion <= 0) return
        CenterThreadPool.run {
            val verid = WeiseMarket.fetchVerid()
            if (!WeiseMarket.versionMatches(verid, targetVersion)) {
                Logu.i(TAG, "商店版本 $verid 与更新版本 $targetVersion 不一致，不显示去微思下载")
                return@run
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (weseeBtn.visibility == View.VISIBLE) return@runOnUiThread
                // 能不能写剪贴板只能试着写一次（部分 ROM 会静默失败），顺便就把链接放进去了
                if (!WeiseMarket.copyPageUrl(this)) {
                    Logu.w(TAG, "剪贴板不可写，不显示去微思下载")
                    return@runOnUiThread
                }
                weseeBtn.visibility = View.VISIBLE
                weseeBtn.setOnClickListener { openWeise() }
            }
        }
    }

    /**
     * 把商店链接再写一遍剪贴板（防被别处覆盖），然后打开微思商店。
     *
     * 商店**没有**注册 apk.wysteam.cn 的 VIEW 过滤器（26.11.16 实测只落到浏览器），
     * 所以这里按包名显式启动；本机没装商店才退回浏览器，并明确告诉用户「链接已复制」。
     */
    private fun openWeise() {
        WeiseMarket.copyPageUrl(this)
        val marketIntent = WeiseMarket.buildOpenIntent(this)
        // 装了商店就以商店为准；没装、或商店本身起不来（包坏了 / 被禁用）才退浏览器
        if (marketIntent != null && startQuietly(marketIntent)) return
        if (startQuietly(WeiseMarket.buildBrowserFallbackIntent())) {
            MsgUtil.showMsg(getString(R.string.msg_weise_open_browser))
        } else {
            MsgUtil.showMsg(getString(R.string.msg_weise_open_failed))
        }
    }

    /** 起一个 Activity，失败只记日志不抛——调用方自己决定怎么兜。 */
    private fun startQuietly(intent: Intent): Boolean = try {
        startActivity(intent)
        true
    } catch (e: Exception) {
        Logu.w(TAG, "启动失败：" + e.message)
        false
    }

    private fun startDownload() {
        if (isDownloading) return
        // 下载地址可能为空：强制更新拦截是从 SP 里取 force_update_download_url 再塞进 intent 的，
        // 那个键没有值时读出 null（BiliTerminal.kt 的 getString(..., null)），页面照样会显示
        // 「下载」按钮。旧代码在这里 `updateConfig!!.downloadUrl!!` 直接 NPE（线上实测 5 例，
        // 消息为 null，正是 Kotlin `!!` 的特征）。宁可就地提示，也不要崩掉整个更新页。
        val downloadUrl = updateConfig?.downloadUrl
        if (downloadUrl.isNullOrBlank()) {
            MsgUtil.showMsg(getString(R.string.msg_update_no_download_url))
            return
        }
        isDownloading = true
        downloadFinished = false

        buttonContainer.visibility = View.GONE
        progressContainer.visibility = View.VISIBLE
        retryBtn.visibility = View.GONE
        cancelDownloadBtn.visibility = View.VISIBLE
        downloadProgress.visibility = View.VISIBLE
        downloadProgress.progress = 0
        progressText.text = getString(R.string.text_update_download_preparing)

        UpdateManager.downloadApk(
            downloadUrl,
            this,
            { progress ->
                val percent = (progress.progress * 100).toInt()
                downloadProgress.progress = percent
                val downloadedMB = progress.bytesDownloaded / (1024 * 1024)
                val totalMB = progress.totalBytes / (1024 * 1024)
                if (totalMB > 0) {
                    progressText.text = getString(R.string.text_update_progress, percent, downloadedMB, totalMB)
                } else {
                    progressText.text = getString(R.string.text_update_downloaded_mb, downloadedMB)
                }
                kotlin.Unit
            },
            { file ->
                downloadFinished = true
                downloadedFile = file
                progressText.text = getString(R.string.text_update_download_complete)
                downloadProgress.progress = 100
                cancelDownloadBtn.visibility = View.GONE
                retryBtn.visibility = View.GONE
                isDownloading = false
                installApk(file)
                kotlin.Unit
            },
            { error ->
                isDownloading = false
                progressText.text = getString(R.string.text_update_download_failed, error)
                // 380×380 手表上「进度条 + 三行错误文案 + 两个 44dp 按钮」比屏幕还高，
                // 底栏会被 RelativeLayout 顶到屏幕顶部、压住标题栏。错误态进度条已无意义、
                // 「取消下载」又与返回键重复，只留「重试下载」这一个按钮。
                downloadProgress.visibility = View.GONE
                cancelDownloadBtn.visibility = View.GONE
                retryBtn.visibility = View.VISIBLE
                kotlin.Unit
            }
        )
    }

    private fun installApk(apkFile: File) {
        UpdateManager.installApk(this, apkFile)
        // 不在此处解除强制更新：待用户完成安装、下次启动检测版本号后再解除，
        // 避免用户在系统安装器弹窗点「取消」后绕过强制更新
        finish()
    }

    /** 强制更新下的「退出应用」：关闭 Activity 栈并结束进程，避免被重新拉起后继续拦截。 */
    private fun exitApp() {
        finishAffinity()
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    override fun onBackPressed() {
        if (updateConfig != null && updateConfig!!.isForceUpdate) {
            exitApp()
        } else {
            if (isDownloading) {
                UpdateManager.cancelDownload()
            }
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!downloadFinished && isDownloading) {
            UpdateManager.cancelDownload()
        }
    }
}