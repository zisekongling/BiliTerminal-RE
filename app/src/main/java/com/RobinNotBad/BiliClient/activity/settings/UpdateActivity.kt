package com.RobinNotBad.BiliClient.activity.settings

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.BiliTerminal
import com.RobinNotBad.BiliClient.activity.base.BaseActivity
import com.RobinNotBad.BiliClient.model.UpdateConfig
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import com.RobinNotBad.BiliClient.util.UpdateManager
import java.io.File

class UpdateActivity : BaseActivity() {

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

    private var updateConfig: UpdateConfig? = null
    private var isDownloading: Boolean = false
    private var downloadFinished: Boolean = false
    private var downloadedFile: File? = null

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
        progressText = findViewById(R.id.progress_text)
        cancelBtn = findViewById(R.id.cancel_btn)
        downloadBtn = findViewById(R.id.download_btn)
        cancelDownloadBtn = findViewById(R.id.cancel_download_btn)
        retryBtn = findViewById(R.id.retry_btn)
        descScroll = findViewById(R.id.desc_scroll)

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
        retryBtn.setOnClickListener { startDownload() }

        findViewById<View>(R.id.top).setOnClickListener { onBackPressed() }

        val hasConfig = intent.getBooleanExtra("has_config", false)

        if (hasConfig) {
            val versionCode = intent.getIntExtra("version_code", 0)
            val versionName = intent.getStringExtra("version_name")
            val description = intent.getStringExtra("description")
            val downloadUrl = intent.getStringExtra("download_url")
            val forceUpdate = intent.getBooleanExtra("force_update", false)

            updateConfig = UpdateConfig(versionCode, versionName, description, downloadUrl, forceUpdate)
            showUpdateInfo()
        } else {
            showLoading(true)
            checkUpdate()
        }
    }

    private fun showLoading(show: Boolean) {
        loadingOverlay.visibility = if (show) View.VISIBLE else View.GONE
        if (show) {
            loadingText.text = "正在检查更新..."
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
                    MsgUtil.showMsg("当前已是最新版")
                    finish()
                }
                kotlin.Unit
            },
            { error ->
                showLoading(false)
                MsgUtil.showMsg("检查更新失败：$error")
                finish()
                kotlin.Unit
            }
        )
    }

    @SuppressLint("SetTextI18n")
    private fun showUpdateInfo() {
        tipTitle.text = "发现新版本"
        versionNameText.text = "版本 " + updateConfig!!.versionName + " (" + updateConfig!!.versionCode + ")"
        contentText.text = updateConfig!!.description

        SharedPreferencesUtil.putInt("update_last_new_version", updateConfig!!.versionCode)

        descScroll.visibility = View.VISIBLE
        buttonContainer.visibility = View.VISIBLE
        progressContainer.visibility = View.GONE

        if (updateConfig!!.isForceUpdate) {
            (cancelBtn as TextView).text = "退出应用"
            cancelBtn.setOnClickListener { finishAffinity() }
        } else {
            (cancelBtn as TextView).text = "取消"
            cancelBtn.setOnClickListener { finish() }
        }
    }

    private fun startDownload() {
        if (isDownloading) return
        isDownloading = true
        downloadFinished = false

        buttonContainer.visibility = View.GONE
        progressContainer.visibility = View.VISIBLE
        retryBtn.visibility = View.GONE
        cancelDownloadBtn.visibility = View.VISIBLE
        downloadProgress.progress = 0
        progressText.text = "准备下载..."

        UpdateManager.downloadApk(
            updateConfig!!.downloadUrl!!,
            this,
            { progress ->
                val percent = (progress.progress * 100).toInt()
                downloadProgress.progress = percent
                val downloadedMB = progress.bytesDownloaded / (1024 * 1024)
                val totalMB = progress.totalBytes / (1024 * 1024)
                if (totalMB > 0) {
                    progressText.text = String.format("%d%%  %dMB / %dMB", percent, downloadedMB, totalMB)
                } else {
                    progressText.text = String.format("已下载 %dMB", downloadedMB)
                }
                kotlin.Unit
            },
            { file ->
                downloadFinished = true
                downloadedFile = file
                progressText.text = "下载完成"
                downloadProgress.progress = 100
                cancelDownloadBtn.visibility = View.GONE
                retryBtn.visibility = View.GONE
                isDownloading = false
                installApk(file)
                kotlin.Unit
            },
            { error ->
                isDownloading = false
                progressText.text = "下载失败：$error"
                retryBtn.visibility = View.VISIBLE
                cancelDownloadBtn.visibility = View.VISIBLE
                kotlin.Unit
            }
        )
    }

    private fun installApk(apkFile: File) {
        UpdateManager.installApk(this, apkFile)

        if (updateConfig!!.isForceUpdate) {
            BiliTerminal.clearForceUpdate()
        } else {
            finish()
        }
    }

    override fun onBackPressed() {
        if (updateConfig != null && updateConfig!!.isForceUpdate) {
            finishAffinity()
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