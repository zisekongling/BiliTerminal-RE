package com.RobinNotBad.BiliClient.activity

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.FileProvider
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.base.BaseActivity
import com.RobinNotBad.BiliClient.api.AppInfoApi
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.FileUtil
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.NetWorkUtil
import okhttp3.Response
import okio.buffer
import okio.sink
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.Timer
import java.util.TimerTask
import java.util.zip.Inflater

class DownloadActivity : BaseActivity() {

    private lateinit var progressView: View
    private lateinit var progressText: TextView

    private var rootPath: File? = null
    private var downPath: File? = null
    private var downFile: File? = null
    private var link: String? = null
    private var scrHeight: Int = 0

    private var dldText: String = ""
    private var dldPercent: Float = 0f

    private var type: Int = 0

    private var finishFlag: Boolean = false

    private var noBiliHeaders: Boolean = false

    private val timer = Timer()
    private val showText = object : TimerTask() {
        @SuppressLint("SetTextI18n")
        override fun run() {
            val viewHeight = (dldPercent * scrHeight).toInt()
            runOnUiThread {
                progressText.text = "$dldText\n${dldPercent * 100}%"
                val params = progressView.layoutParams
                params.height = viewHeight
                progressView.layoutParams = params
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_download)

        val intent = intent

        type = intent.getIntExtra("type", 0)
        link = intent.getStringExtra("link")
        noBiliHeaders = intent.getBooleanExtra("terminal", false)

        progressText = findViewById(R.id.progressText)
        progressView = findViewById(R.id.progressView)

        scrHeight = window_height

        if (!FileUtil.checkStoragePermission()) FileUtil.requestStoragePermission(this)

        timer.schedule(showText, 100, 100)
        CenterThreadPool.run {
            if (type == 0) {
                rootPath = File(intent.getStringExtra("path")!!)
                if (!rootPath!!.exists()) rootPath!!.mkdirs()
                downFile = File(rootPath, FileUtil.getFileNameFromLink(link!!))
                download(link!!, downFile!!, "下载文件中", true)
            } else {
                val title = FileUtil.stringToFile(intent.getStringExtra("title")!!)

                rootPath = FileUtil.getVideoDownloadPath()

                if (type == 1) {
                    downPath = File(rootPath, title)
                    rootPath = downPath
                }
                if (type == 2) {
                    rootPath = File(rootPath, FileUtil.stringToFile(intent.getStringExtra("parent_title")!!))
                    downPath = File(rootPath, title)
                }

                if (!downPath!!.exists()) downPath!!.mkdirs()

                val danmaku = intent.getStringExtra("danmaku")
                val cover = intent.getStringExtra("cover")
                val dmFile = File(downPath, "danmaku.xml")
                val coverFile = File(rootPath, "cover.png")
                val videoFile = File(downPath, "video.mp4")
                downdanmu(danmaku!!, dmFile)
                if (!coverFile.exists()) download(cover!!, coverFile, "下载封面", false)
                download(link!!, videoFile, "下载视频", true)
            }
        }

    }

    @SuppressLint("SetTextI18n")
    private fun download(url: String, file: File, desc: String, exitOnFinish: Boolean) {
        dldText = desc
        try {
            val response: Response = NetWorkUtil.get(url,
                if (noBiliHeaders) AppInfoApi.customHeaders else NetWorkUtil.webHeaders)
            if (!file.exists()) file.createNewFile()
            val inputStream: InputStream = response.body!!.byteStream()
            val fileOutputStream = FileOutputStream(file)
            val bytes = ByteArray(1024 * 10)
            val TotalFileSize = response.body!!.contentLength()
            var len: Int
            while (inputStream.read(bytes).also { len = it } != -1) {
                fileOutputStream.write(bytes, 0, len)
                val CompleteFileSize = file.length()
                dldPercent = 1.0f * CompleteFileSize / TotalFileSize
            }
            inputStream.close()
            fileOutputStream.close()
            if (exitOnFinish) {
                handleDownloadComplete(file)
            }
            response.body!!.close()
            response.close()
        } catch (e: IOException) {
            runOnUiThread { MsgUtil.showMsg("下载失败") }
            e.printStackTrace()
            finish()
        }
    }

    private fun handleDownloadComplete(downloadedFile: File) {
        if (downloadedFile.name.endsWith(".bak")) {
            val apkFile = File(downloadedFile.parent, downloadedFile.name.replace(".bak", ".apk"))
            if (downloadedFile.renameTo(apkFile)) {
                if (installApk(apkFile)) {
                    runOnUiThread { MsgUtil.showMsg("下载完成，已尝试安装") }
                } else {
                    val bakFile = File(FileUtil.getDownloadPath(), downloadedFile.name)
                    apkFile.renameTo(bakFile)
                    runOnUiThread { MsgUtil.showMsg("下载完成，安装失败，已保存到下载文件夹") }
                }
            } else {
                runOnUiThread { MsgUtil.showMsg("下载完成，重命名失败") }
            }
        } else {
            runOnUiThread { MsgUtil.showMsg("下载完成") }
        }
        val timer = Timer()
        timer.schedule(object : TimerTask() {
            override fun run() {
                finishFlag = true
                finish()
            }
        }, 200)
    }

    private fun installApk(apkFile: File): Boolean {
        return try {
            val uri: Uri
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                uri = FileProvider.getUriForFile(this, packageName + ".FileProvider", apkFile)
            } else {
                uri = Uri.fromFile(apkFile)
            }
            val intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(uri, "application/vnd.android.package-archive")
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                for (resolveInfo in packageManager.queryIntentActivities(intent, 0)) {
                    grantUriPermission(resolveInfo.activityInfo.packageName, uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                }
            }
            startActivity(intent)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }


    private fun downdanmu(danmaku: String, danmakuFile: File) {
        try {
            val response: Response = NetWorkUtil.get(danmaku,
                if (noBiliHeaders) AppInfoApi.customHeaders else NetWorkUtil.webHeaders)
            var bufferedSink: okio.BufferedSink? = null
            try {
                if (!danmakuFile.exists()) danmakuFile.createNewFile()
                val sink = danmakuFile.sink()
                val decompressBytes = decompress(response.body!!.bytes())
                bufferedSink = sink.buffer()
                bufferedSink.write(decompressBytes)
                bufferedSink.close()
            } catch (e: Exception) {
                e.printStackTrace()
                finish()
            } finally {
                bufferedSink?.close()
            }
            response.body?.close()
            response.close()
        } catch (e: IOException) {
            runOnUiThread { MsgUtil.showMsg("弹幕下载失败！") }
            finish()
            e.printStackTrace()
        }
    }

    companion object {
        @JvmStatic
        fun decompress(data: ByteArray): ByteArray {
            var output: ByteArray
            val decompresser = Inflater(true)
            decompresser.reset()
            decompresser.setInput(data)
            val o = ByteArrayOutputStream(data.size)
            try {
                val buf = ByteArray(2048)
                while (!decompresser.finished()) {
                    val i = decompresser.inflate(buf)
                    o.write(buf, 0, i)
                }
                output = o.toByteArray()
            } catch (e: Exception) {
                output = data
                e.printStackTrace()
            } finally {
                try {
                    o.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
            decompresser.end()
            return output
        }
    }

    override fun onDestroy() {
        timer.cancel()
        if (!finishFlag) {
            if (type != 0 && downPath != null) FileUtil.deleteFolder(downPath!!)
            else if (downFile != null) downFile!!.delete()
        }
        super.onDestroy()
    }

    override fun onBackPressed() {
        finish()
    }
}