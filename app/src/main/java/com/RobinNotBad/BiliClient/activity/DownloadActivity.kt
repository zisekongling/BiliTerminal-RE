package com.RobinNotBad.BiliClient.activity

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.core.content.FileProvider
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.base.BaseActivity
import com.RobinNotBad.BiliClient.api.AppInfoApi
import com.RobinNotBad.BiliClient.service.download.DownloadPathSpec
import com.RobinNotBad.BiliClient.util.ApkVerifier
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.FileUtil
import com.RobinNotBad.BiliClient.util.Logu
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.NetWorkUtil
import com.RobinNotBad.BiliClient.util.PictureMediaStore
import okhttp3.Response
import okio.buffer
import okio.sink
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.Timer
import java.util.TimerTask

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

    /** 本次下载真正写过的文件。非正常结束时只删这些，不再删整个任务目录（见 onDestroy） */
    private val touchedFiles: MutableSet<File> = java.util.Collections.synchronizedSet(mutableSetOf<File>())

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

        // 判据是「真正的目标目录眼下能不能写」：默认视频目录按 API 分档、都免权限；
        // 用户把路径改到公共目录、或图片要写公共 Pictures/ 时，写不进去会自动降级到
        // 应用专属目录（手表 ROM 没有「所有文件访问」设置页，跳授权是死路，issue #42），
        // 只有连降级目录都写不了（存储满/只读）才返回 null 并结束页面。
        val targetRoot: File? =
            if (type == 0) FileUtil.resolvePictureDownloadDir(this, File(intent.getStringExtra("path") ?: ""))
            else FileUtil.resolveVideoDownloadDir(this)
        if (targetRoot == null) {
            // 这里要自己再补一次提示：resolvePictureDownloadDir 的失败文案走的是 Snackbar，
            // 而 Snackbar 挂在**本页窗口**上，页面一 finish 就跟着消失了（图片链路原来就是这样
            // 静默失败的）。图片下载改用 Toast —— 它不依赖页面窗口，页面结束后仍然看得见。
            if (type == 0) MsgUtil.toast(getString(R.string.msg_storage_unavailable))
            finish()
            return
        }

        timer.schedule(showText, 100, 100)
        CenterThreadPool.run {
            if (type == 0) {
                rootPath = targetRoot
                if (!rootPath!!.exists()) rootPath!!.mkdirs()
                // 文件名必须从 URL 里「净化」出来：B 站图片地址可能带 `@1048w_…` 参数后缀或
                // `?x-oss-process=…` 查询串，直接截最后一段会把参数拼进文件名 —— MediaStore 认不出
                // 扩展名（相册里看不到保存的图），个别文件系统还会直接创建失败（反馈 #17）。
                downFile = File(rootPath, PictureDownloadPolicy.fileNameFor(link!!))
                // 协议相对地址（//i0.hdslb.com/…）补 https；http 统一升 https，失败再由
                // download() 退回 http 重试一次（详见 PictureDownloadPolicy）。
                val pictureUrl = PictureDownloadPolicy.normalizeUrl(link!!) ?: link!!
                download(
                    pictureUrl,
                    downFile!!,
                    getString(R.string.text_downloading_file),
                    true,
                    schemeFallback = true
                )
            } else {
                val title = FileUtil.stringToFile(intent.getStringExtra("title")!!)

                rootPath = targetRoot

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
                val dmFile = File(downPath, DownloadPathSpec.FILE_DANMAKU)
                val coverFile = File(rootPath, DownloadPathSpec.FILE_COVER)
                val videoFile = File(downPath, DownloadPathSpec.FILE_VIDEO)
                // 每步失败时 download()/downdanmu() 已经提示并结束页面并返回 false，
                // 此时不要再接着下后面的文件：既白跑请求，又会在下不动的目录里留下空壳
                if (downdanmu(danmaku!!, dmFile)) {
                    val coverOk = coverFile.exists() ||
                        download(cover!!, coverFile, getString(R.string.text_download_cover), false)
                    if (coverOk) download(link!!, videoFile, getString(R.string.text_download_video), true)
                }
            }
        }

    }

    /**
     * 下载单个文件。返回 false 表示这次下载失败、页面已被结束（调用方不要再继续后面的步骤）。
     *
     * @param schemeFallback 失败时是否换协议再试一次（图片链路用：http/https 互换）
     */
    @SuppressLint("SetTextI18n")
    private fun download(
        url: String,
        file: File,
        desc: String,
        exitOnFinish: Boolean,
        schemeFallback: Boolean = false
    ): Boolean {
        dldText = desc
        // 每个文件从头计进度：不重置的话，上一个文件（或无 content-length 的伪进度）残留的
        // 高位百分比会让新文件一上来就显示 90%+，看着像"卡住"
        dldPercent = 0f
        // 记在外面：读流中途抛 IOException 时也要把连接关掉，否则 OkHttp 连接池会被半截响应占住
        var response: Response? = null
        try {
            // 先建文件再发请求：createNewFile() 抛 IOException 时 response 还没拿到，
            // 否则会走 catch 直接 finish，连接不归还（详见 OkHttp body 读尽才归还的语义）
            val existedBefore = file.exists()
            if (!existedBefore) file.createNewFile()
            touchedFiles.add(file)
            val resp = NetWorkUtil.get(url,
                if (noBiliHeaders) AppInfoApi.customHeaders else NetWorkUtil.webHeaders)
            response = resp
            // 非 2xx（404 / 风控页 / 没跟成功的 3xx）时 body 是错误页或 JSON，以前会原样写进
            // video.mp4 / cover.png，用户拿到一个"有内容"的坏文件；这里统一收尾并返回失败
            val httpFailureMsg =
                if (type == 0) R.string.msg_picture_download_failed else R.string.text_download_failed
            if (abortOnHttpError(resp, file, !existedBefore, httpFailureMsg)) return false
            val body = resp.body!!
            val inputStream: InputStream = body.byteStream()
            val fileOutputStream = FileOutputStream(file)
            val bytes = ByteArray(1024 * 10)
            val TotalFileSize = body.contentLength()
            var len: Int
            while (inputStream.read(bytes).also { len = it } != -1) {
                fileOutputStream.write(bytes, 0, len)
                val CompleteFileSize = file.length()
                dldPercent = if (TotalFileSize > 0) {
                    (1.0f * CompleteFileSize / TotalFileSize).coerceIn(0f, 1f)
                } else {
                    // 服务端没给 content-length：走伪进度，别拿 -1 做除数得出负数百分比
                    (dldPercent + 0.02f).coerceAtMost(0.9f)
                }
            }
            inputStream.close()
            fileOutputStream.close()
            // 完整性：服务端声明了长度却少读（连接被中途掐断）不能当成下载成功，
            // 否则半截视频会以"已完成"的样子留在本地列表里
            if (TotalFileSize > 0 && file.length() != TotalFileSize) {
                throw IOException("下载不完整：期望 " + TotalFileSize + " 字节，实际 " + file.length())
            }
            if (exitOnFinish) {
                handleDownloadComplete(file)
            }
            body.close()
            resp.close()
            return true
        } catch (e: IOException) {
            try { response?.close() } catch (_: Exception) {}
            // 图片链路兜一层协议重试：部分手表 ROM / 代理会直接掐掉明文 http，
            // 换 https 再要一次往往就成功（B 站图床两种协议都支持）。
            if (schemeFallback) {
                val alt = PictureDownloadPolicy.alternateSchemeUrl(url)
                if (alt != null) {
                    Logu.d("picture-download", "地址 $url 下载失败，改用 $alt 重试：${e.message}")
                    return download(alt, file, desc, exitOnFinish, false)
                }
            }
            showDownloadFailure(
                if (type == 0) R.string.msg_picture_download_failed else R.string.text_download_failed
            )
            e.printStackTrace()
            finish()
            return false
        }
    }

    /**
     * HTTP 非 2xx 的统一收尾：关连接、删掉本次新建的空壳文件、提示并结束页面。
     *
     * 注意两件事：① OkHttp 的连接只有把 body 读尽或关掉才会归还，所以这里必须 close；
     * ② 只有"本次新建"的文件才删 —— 覆盖已有成品前就失败的话，旧文件没被动过，不能误删。
     *
     * @return true 表示已按失败处理（调用方直接 return）
     */
    private fun abortOnHttpError(response: Response, file: File, createdHere: Boolean, msgRes: Int): Boolean {
        if (response.isSuccessful) return false
        response.close()
        if (createdHere) file.delete()
        showDownloadFailure(msgRes)
        finish()
        return true
    }

    /**
     * 下载失败的统一提示。
     *
     * 视频/番剧继续用页面内的 Snackbar（用户会盯着进度页看）；图片链路改用 Toast ——
     * 图片失败的瞬间页面就会 `finish()`，Snackbar 绑在本页窗口上会一起消失，
     * 用户看到的就是「点了没反应」（反馈 #17 的静默失败形态）。
     */
    private fun showDownloadFailure(msgRes: Int) {
        val text = getString(msgRes)
        if (type == 0) MsgUtil.toast(text) else runOnUiThread { MsgUtil.showMsg(text) }
    }

    private fun handleDownloadComplete(downloadedFile: File) {
        if (downloadedFile.name.endsWith(".bak")) {
            val apkFile = File(downloadedFile.parent, downloadedFile.name.replace(".bak", ".apk"))
            if (downloadedFile.renameTo(apkFile)) {
                if (installApk(apkFile)) {
                    runOnUiThread { MsgUtil.showMsg(getString(R.string.text_download_complete_install_tried)) }
                } else {
                    val bakFile = File(FileUtil.getDownloadPath(), downloadedFile.name)
                    apkFile.renameTo(bakFile)
                    runOnUiThread { MsgUtil.showMsg(getString(R.string.text_download_complete_install_failed)) }
                }
            } else {
                runOnUiThread { MsgUtil.showMsg(getString(R.string.text_download_complete_rename_failed)) }
            }
        } else if (type == 0) {
            // 图片下载（type 0，入口只有 ImageViewerActivity）。写完必须让系统相册能看见：
            // Android 10+ 的默认目录已经改成应用专属（安全审计 P2-2：裸写公共目录要全盘权限，
            // 手表 ROM 又根本没有那个设置页），所以这里用 MediaStore 发布一份进
            // `Pictures/哔哩终端/`；旧系统/用户自定义公共目录则只需登记扫描。
            // 发布失败不影响「下载已成功」——文件仍在应用专属目录，只是相册里看不到，
            // 因此返回值只用于换提示文案（失败也给出实际目录，用户能用文件管理器找到）。
            val publishedFolder = PictureMediaStore.publishToGallery(this, downloadedFile)
            val shownPath = publishedFolder ?: downloadedFile.parent ?: downloadedFile.absolutePath
            // 提示里带上实际保存目录：以前只有一句「下载完成」，用户即使知道去相册翻，
            // 也不知道文件落在哪个文件夹
            // 用 Toast 而不是 Snackbar：本页 200ms 后就 finish，Snackbar 绑在本页窗口上会一起消失，
            // 用户一个字都看不到（反馈 #17 的另一半：存下来了却说"没下载成功"）。
            MsgUtil.toast(getString(
                if (publishedFolder != null) R.string.msg_picture_saved
                else R.string.msg_picture_saved_fallback,
                shownPath
            ))
        } else {
            runOnUiThread { MsgUtil.showMsg(getString(R.string.text_update_download_complete)) }
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
            // 26.10.04 批次 4（E6）：这条是最老的「下载成 .bak 再改名安装」自更新链路，
            // 与 UpdateManager 那条一样，先校验包名与签名再交给系统安装器。
            // 不通过时返回 false，由调用方按既有的「安装失败」分支处理（本方法不在主线程，
            // 不在这里弹提示）。
            if (!ApkVerifier.verify(this, apkFile).ok) return false
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


    /**
     * 下载并解压弹幕。返回 false 表示失败、页面已被结束（调用方不要再继续后面的步骤）。
     */
    private fun downdanmu(danmaku: String, danmakuFile: File): Boolean {
        val existedBefore = danmakuFile.exists()
        try {
            val response: Response = NetWorkUtil.get(danmaku,
                if (noBiliHeaders) AppInfoApi.customHeaders else NetWorkUtil.webHeaders)
            // 非 2xx 时 body 是错误页/JSON，以前照样解压写进 danmaku.xml，播放器解析出来是空弹幕，
            // 用户只会觉得"这个视频的弹幕丢了"
            if (abortOnHttpError(response, danmakuFile, !existedBefore, R.string.text_danmaku_download_failed)) {
                return false
            }
            var bufferedSink: okio.BufferedSink? = null
            try {
                if (!existedBefore) danmakuFile.createNewFile()
                touchedFiles.add(danmakuFile)
                val sink = danmakuFile.sink()
                val decompressBytes = NetWorkUtil.decompress(response.body!!.bytes())
                bufferedSink = sink.buffer()
                bufferedSink.write(decompressBytes)
                bufferedSink.close()
            } catch (e: Exception) {
                e.printStackTrace()
                // 解压/写盘失败也要还连接，别让半截响应一直占着 OkHttp 连接池
                try { response.close() } catch (_: Exception) {}
                finish()
                return false
            } finally {
                bufferedSink?.close()
            }
            response.body?.close()
            response.close()
            return true
        } catch (e: IOException) {
            runOnUiThread { MsgUtil.showMsg(getString(R.string.text_danmaku_download_failed)) }
            finish()
            e.printStackTrace()
            return false
        }
    }

    override fun onDestroy() {
        timer.cancel()
        if (!finishFlag) {
            // 只删本次真正写过的文件（下载中/失败的空壳、被截断的半截文件）。
            // 以前这里 deleteFolder(整个任务目录)：重下一次同一个视频时，只要中途取消或失败，
            // 上一次已经下好的 video.mp4 / 封面 / 弹幕会被一起删掉 —— 真实数据丢失
            // （DownloadService 侧同类问题已按「只清临时产物」修过，这里是同一口径）
            val touched = synchronized(touchedFiles) { touchedFiles.toList() }
            for (f in touched) {
                if (f.isFile) f.delete()
            }
        }
        super.onDestroy()
    }

    override fun onBackPressed() {
        finish()
    }
}

/**
 * 图片下载的 URL / 本地文件名策略（纯逻辑，不碰 Android；JVM 单测见 `PictureDownloadPolicyTest`）。
 *
 * <h3>为什么需要它（反馈 #17「评论区图片无法下载」）</h3>
 * `DownloadActivity` 原来用 `FileUtil.getFileNameFromLink(url)` 直接截最后一个 `/` 之后的部分当文件名。
 * B 站图床地址里塞着两种「不是文件名」的后缀：
 * - `@…` 处理参数：`https://i0.hdslb.com/bfs/x/abc.jpg@1048w_!web-dyn`；
 * - 查询串：`https://i0.hdslb.com/bfs/face/x.jpg?x-oss-process=image/resize,w_100`。
 *
 * 直接截出来就会得到 `abc.jpg@1048w_!web-dyn` / `x.jpg?x-oss-process=image/resize,w_100`：
 * 前者在部分文件系统上 `createNewFile()` 直接失败（页面提示「下载失败」就结束），
 * 后者能落盘但 `PictureMediaStore.mimeTypeOf()` 认不出扩展名 → 不登记进媒体库 → 相册里永远看不到，
 * 用户的结论同样是「没下载成功」。这里统一净化成「合法、带图片扩展名」的文件名。
 */
internal object PictureDownloadPolicy {

    /** 文件名长度上限：给路径前缀留余量（多数文件系统单段上限 255 字节）。 */
    private const val MAX_NAME_LENGTH = 80

    /** 路径分隔符与 Windows / 部分文件系统上的非法字符。 */
    private val ILLEGAL_NAME_CHARS = "\\/:*?\"<>|".toSet()

    /**
     * 规范化图片地址：
     * - 去掉首尾空白；
     * - 协议相对地址（`//i0.hdslb.com/x.jpg`）补 `https:`；
     * - 明文 `http://` 统一升 `https://`（B 站图床与 afdiancdn 都支持 https，
     *   而部分手表 ROM / 代理会直接掐掉明文请求）；真失败时由 [alternateSchemeUrl] 退回 http 重试。
     *
     * 不是 http(s) 的地址（`content://` 等）原样返回，保持调用方语义不变。
     */
    @JvmStatic
    fun normalizeUrl(rawUrl: String?): String? {
        val trimmed = rawUrl?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        val absolute = if (trimmed.startsWith("//")) "https:$trimmed" else trimmed
        return if (absolute.startsWith("http://", ignoreCase = true))
            "https://" + absolute.substring("http://".length)
        else absolute
    }

    /** 同一地址的另一个协议；不是 http(s) 时返回 null（调用方据此放弃重试）。 */
    @JvmStatic
    fun alternateSchemeUrl(url: String): String? = when {
        url.startsWith("https://", ignoreCase = true) -> "http://" + url.substring(8)
        url.startsWith("http://", ignoreCase = true) -> "https://" + url.substring(7)
        else -> null
    }

    /** 从图片地址推出安全、带图片扩展名的本地文件名。 */
    @JvmStatic
    fun fileNameFor(rawUrl: String?): String {
        val cleaned = rawUrl?.trim().orEmpty()
        // 先剥掉查询串 / fragment，再剥掉 B 站图床的 `@…` 处理参数
        val path = cleaned.substringBefore('#').substringBefore('?')
        val segment = path.substringAfterLast('/', "").substringBefore('@').trim()
        val safe = buildString {
            for (ch in segment) {
                when {
                    ch.code < 0x20 || ch.code == 0x7F -> Unit          // 控制字符直接丢
                    ch in ILLEGAL_NAME_CHARS -> append('_')           // 非法字符换成下划线
                    else -> append(ch)
                }
            }
        }.trim('.', ' ')
        // 纯点号 / 空串在文件系统上不合法（"." 是当前目录）
        val base = if (safe.isEmpty() || safe == "." || safe == "..") "image" else safe
        return truncateName(ensureImageExtension(base))
    }

    /**
     * 补扩展名：完全没有扩展名时补 `.jpg`。
     *
     * 已经带扩展名（哪怕是 `.jfif` 这类我们认不出的）就保留原样——宁可让
     * `PictureMediaStore` 跳过登记，也不要把字节内容和扩展名对不上的文件塞进相册。
     */
    private fun ensureImageExtension(name: String): String =
        if (name.contains('.')) name else "$name.jpg"

    /** 超长文件名截断：保留扩展名，只截主干，避免 `createNewFile()` 因文件名过长失败。 */
    private fun truncateName(name: String): String {
        if (name.length <= MAX_NAME_LENGTH) return name
        val extIndex = name.lastIndexOf('.')
        val ext = if (extIndex > 0) name.substring(extIndex) else ""
        val stem = if (extIndex > 0) name.substring(0, extIndex) else name
        val keep = (MAX_NAME_LENGTH - ext.length).coerceAtLeast(1)
        return stem.take(keep) + ext
    }
}
