package com.RobinNotBad.BiliClient.util

import com.RobinNotBad.BiliClient.R

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.RobinNotBad.BiliClient.BiliTerminal
import com.RobinNotBad.BiliClient.BuildConfig
import com.RobinNotBad.BiliClient.api.TerminalApi
import com.RobinNotBad.BiliClient.model.UpdateConfig
import kotlinx.coroutines.CancellationException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit

object UpdateManager {

    private const val APK_FILE_NAME = "bili_terminal_update.apk"

/** 内测 `.txt` 的体积上限：公告式展示只该读小文件，超过这个量级一律报错。 */
private const val BETA_TEXT_MAX_BYTES = 2L * 1024 * 1024
    private const val TAG = "更新检查"

    @Volatile
    private var downloadCanceled = false

    /**
     * 更新专用客户端。信任「系统根证书 + 内置 ISRG 根证书」（见 [ExtraTrust]）：
     * 内测分享站用 Let's Encrypt 证书，Android 7.1 及以下的系统证书库里没有它的根，
     * 不加这一步会报 `Trust anchor for certification path not found`。
     */
    private val okHttpClient: OkHttpClient by lazy {
        ExtraTrust.apply(OkHttpClient.Builder(), BiliTerminal.context)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    /**
     * 「检查更新」专用客户端：在 [okHttpClient] 之上加 **20s 总闸**（`callTimeout`）。
     *
     * 为什么需要：连接超时 15s / 读超时 60s 都是**单阶段**上限，DNS 慢、TLS 握手卡、服务端
     * 一点点吐字节时整次调用可以远超 20s，而更新检查是在启动路径上报的——它挂住就会连带
     * 拖慢启动。`callTimeout` 覆盖「DNS + 连接 + 写 + 读」全流程，是真正的总时限。
     *
     * 为什么下载**不用**这个 client：APK 几十 MB，读超时 60s 在慢网下是**合法**的，
     * 套 20s 总闸会把正常下载打断（下载只看单阶段超时，不设总时限）。
     */
    private val checkClient: OkHttpClient by lazy {
        okHttpClient.newBuilder()
            .callTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    @Volatile
    private var cachedConfig: UpdateConfig? = null

    data class DownloadProgress(val bytesDownloaded: Long, val totalBytes: Long, val progress: Float)

    fun getCurrentVersion(): Int {
        return try {
            BiliTerminal.context?.let {
                it.packageManager.getPackageInfo(it.packageName, 0).versionCode ?: 0
            } ?: 0
        } catch (e: Exception) {
            0
        }
    }

    fun getCachedConfig(): UpdateConfig? = cachedConfig

    fun checkUpdate(onResult: (UpdateConfig) -> Unit, onError: (String) -> Unit) {
        CenterThreadPool.run {
            try {
                val config = doFetchUpdateConfig()
                CenterThreadPool.runOnUiThread { onResult(config) }
            } catch (e: IOException) {
                CenterThreadPool.runOnUiThread { onError("网络错误：${e.message}") }
            } catch (e: Exception) {
                CenterThreadPool.runOnUiThread { onError("解析错误：${e.message}") }
            }
        }
    }

    fun hasUpdate(config: UpdateConfig): Boolean {
        val hasUpdate = config.versionCode > getCurrentVersion()
        if (!hasUpdate) {
            deleteOldApkFile()
        }
        return hasUpdate
    }

    /**
     * 发现新版本后是否要**自动跳到更新/下载页**（26.10.25 用户要求）。
     *
     * 纯函数（不碰 Context / SharedPreferences），便于 JVM 单测。三个条件缺一不可：
     * 1. 不是强制更新——强制更新由启动拦截链路直接拉起更新页，这里再跳会重复；
     * 2. 发行版确实比已装版本新；
     * 3. 这个版本还没被提示过——`update_last_new_version` 由更新页打开时写入，
     *    所以「一个版本只自动跳一次」；用户看过却选择暂不更新，就不再反复打扰。
     *
     * @param installedVersionCode 当前已安装版本（见 [getCurrentVersion]）
     * @param lastSeenVersion 上次打开更新页时记下的版本号（`update_last_new_version`）
     */
    fun shouldAutoOpenUpdatePage(
        config: UpdateConfig,
        installedVersionCode: Int,
        lastSeenVersion: Int
    ): Boolean =
        !config.isForceUpdate &&
            config.versionCode > installedVersionCode &&
            config.versionCode != lastSeenVersion

    /**
     * 内测包解析：用户输入的纯数字内测码 → 分享站上的 APK 直链。
     *
     * 分享站有两个域名（[BetaUpdate.HOSTS]），哪个域名有包就从哪个下载：
     * 按顺序 POST `/share/select/`，第一个返回**安装包**的域名即采用。
     *
     * 失败提示分两种：**两个域名都没有这个包、或对应的文件不是 `.apk`** 时，对用户就是
     * 「内测码错误」（分享站不校验上传内容，内测码可能对应一张图片）；只有真的访问不了
     * 分享站时才报网络原因。两种都不在这里做版本比较：内测包的版本号可能等于或低于当前版本。
     */
    fun resolveBetaShare(
        code: String,
        onResult: (BetaUpdate.ShareFile, String) -> Unit,
        onError: (String) -> Unit
    ) {
        CenterThreadPool.run {
            val normalized = BetaUpdate.normalizeCode(code)
            if (normalized == null) {
                val msg = BiliTerminal.context?.getString(R.string.msg_update_beta_code_invalid)
                    ?: "内测码只能是数字"
                CenterThreadPool.runOnUiThread { onError(msg) }
                return@run
            }

            val errors = ArrayList<String>()
            var networkFailed = false
            Logu.i(TAG, "解析内测分享链接：${BetaUpdate.buildShareUrl(normalized)}")
            for (host in BetaUpdate.HOSTS) {
                try {
                    when (val result = selectBetaShare(host, normalized)) {
                        is BetaUpdate.Result.Found -> {
                            Logu.i(TAG, "内测包来自 $host：${result.file.name}")
                            CenterThreadPool.runOnUiThread { onResult(result.file, result.host) }
                            return@run
                        }

                        is BetaUpdate.Result.NotFound -> {
                            Logu.e(TAG, "$host 上没有内测包：${result.message}")
                            errors.add("${hostName(host)}：${result.message}")
                        }
                    }
                } catch (e: Exception) {
                    Logu.e(TAG, "访问 $host 失败：${e.message}")
                    networkFailed = true
                    errors.add("${hostName(host)}：${e.message ?: "网络错误"}")
                }
            }
            // 两个域名都没有这个包（或对应的根本不是安装包）时，对用户就是「内测码错误」——
            // 拼一长串域名细节既看不懂也没用；只有真的连不上时才报网络原因。
            val ctx = BiliTerminal.context
            val message = if (!networkFailed) {
                ctx?.getString(R.string.msg_update_beta_code_wrong) ?: "内测码错误"
            } else {
                ctx?.getString(R.string.text_update_beta_failed, errors.joinToString("；"))
                    ?: "获取内测包失败：${errors.joinToString("；")}"
            }
            CenterThreadPool.runOnUiThread { onError(message) }
        }
    }

    private fun hostName(host: String): String = host.substringAfter("://")

    /**
     * 内测码解析到的是 `.txt` 时，把正文取回来当公告展示（与「公告列表」点开的页面一致）。
     *
     * 不落盘、不走 `downloadApk`：文本不是安装包，没必要占更新缓存；
     * 超过 [BETA_TEXT_MAX_BYTES] 直接报错，避免把一份大文件读进内存。
     */
    fun downloadBetaText(url: String, onResult: (String) -> Unit, onError: (String) -> Unit) {
        CenterThreadPool.run {
            try {
                val request = Request.Builder().url(url).get().build()
                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                    val length = response.body?.contentLength() ?: -1L
                    if (length > BETA_TEXT_MAX_BYTES) throw IOException("文本过大")
                    val bytes = response.body?.bytes() ?: ByteArray(0)
                    if (bytes.size > BETA_TEXT_MAX_BYTES) throw IOException("文本过大")
                    // 分享站导出的 txt 可能带 BOM，去掉以免正文第一行前多一个不可见字符
                    val text = BetaUpdate.decodeText(bytes)
                    CenterThreadPool.runOnUiThread { onResult(text) }
                }
            } catch (e: Exception) {
                Logu.e(TAG, "下载内测文本失败：${e.message}")
                val reason = e.message ?: "网络错误"
                val msg = BiliTerminal.context?.getString(R.string.text_update_beta_text_failed, reason)
                    ?: "获取内测文本失败：$reason"
                CenterThreadPool.runOnUiThread { onError(msg) }
            }
        }
    }

    /** POST `/share/select/`，把 HTTP 层错误与业务层「文件不存在」区分开。 */
    private fun selectBetaShare(host: String, code: String): BetaUpdate.Result {
        val body = BetaUpdate.buildSelectBody(code)
            .toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url(BetaUpdate.selectUrl(host))
            .post(body)
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            val parsed = if (text.isNotBlank()) BetaUpdate.parseSelectResponse(text, host) else null
            return when {
                parsed is BetaUpdate.Result.Found -> parsed
                !response.isSuccessful -> {
                    // 非 2xx 时尽量带上响应体里的业务原因（有的部署 404 也会回 JSON）
                    val reason = (parsed as? BetaUpdate.Result.NotFound)?.message
                    throw IOException(if (reason.isNullOrBlank()) "HTTP ${response.code}" else "HTTP ${response.code}：$reason")
                }
                parsed is BetaUpdate.Result.NotFound -> parsed
                else -> throw IOException("响应体为空")
            }
        }
    }

    private fun getApkFile(context: Context? = BiliTerminal.context): File? {
        return context?.let {
            val apkDir = File(it.externalCacheDir ?: it.cacheDir, "update")
            File(apkDir, APK_FILE_NAME)
        }
    }

    /**
     * 已下载的更新安装包（**存在才返回**，否则 null）。
     *
     * 给「存储空间」页统计与删除用。路径拼接仍只在本类里做一次，避免存储页另抄一份
     * `externalCacheDir/update/<文件名>`——那种重复在改文件名/换目录时必然漏改。
     */
    fun currentUpdatePackageFile(context: Context? = BiliTerminal.context): File? {
        val file = getApkFile(context) ?: return null
        return if (file.isFile) file else null
    }

    fun deleteOldApkFile() {
        try {
            val apkFile = getApkFile()
            if (apkFile?.exists() == true) {
                apkFile.delete()
            }
        } catch (e: Exception) {
        }
    }

    /**
     * 更新检查的来源，按顺序尝试，**前一个失败才用后一个**：
     * 1. Gitee 发行版（首发渠道，国内直连快）；
     * 2. GitHub 发行版（兜底）。
     *
     * 两者都是公开仓库，读 release **不需要 token**。
     * 注意：这里不再读 123pan 上单独部署的 config.json —— 版本信息以发行版本身为准，
     * 少一处要人工同步的远端文件（历史上它就漂移过）。
     *
     * 三通道（见 `docs/version-channels.md` §5）下每条源有两种取法：
     * - 稳定版走 `/releases/latest`，响应小但**必须校验 tag 不带后缀**；
     * - 测试版 / 实验版走 `/releases?per_page=N`，自己按 tag 后缀过滤后取 `versionCode` 最大的一条。
     */
    private val releaseApis = listOf(
        "Gitee" to "https://gitee.com/api/v5/repos/zisekongling/bili-terminal-re/releases",
        "GitHub" to "https://api.github.com/repos/zisekongling/BiliTerminal-RE/releases"
    )

    /**
     * 列表模式的每页条数。
     *
     * 实验版一天可能发好几次，30 条足够覆盖最近几天；再大也没意义——响应体里每条都带完整更新日志，
     * 手表上网时是在给启动路径加负担。
     */
    private const val RELEASE_LIST_PAGE_SIZE = 30

    /** 内部信号：`/releases/latest` 返回的那条其实不是稳定版（说明该源不排除预发布）。 */
    private class NotStableReleaseException(val tag: String) :
        IOException("latest 指向的不是稳定版（tag=$tag）")

    /** 更新检查当前用的通道（用户在设置里选的，没选过就跟着当前这个包的通道走）。 */
    fun currentChannel(): String = UpdateChannel.current()

    /**
     * 用户切换了更新通道。
     *
     * 除了记下新通道，还要清掉「上一个通道选出的那条版本」留下的状态：不清的话，beta 发过一条
     * `forceUpdate`、用户切回 stable 之后，那条强制更新仍然会拦启动（`forceUpdateBlocking` 是
     * 内存标志 + SP 一起判的），可 stable 通道里根本没有那个版本。
     */
    fun onChannelChanged(newChannel: String) {
        UpdateChannel.save(newChannel)
        cachedConfig = null
        BiliTerminal.clearForceUpdate()
        SharedPreferencesUtil.removeValue("update_last_new_version")
    }

    private fun doFetchUpdateConfig(): UpdateConfig {
        val channel = currentChannel()
        val errors = ArrayList<String>()
        for ((name, base) in releaseApis) {
            try {
                return fetchRelease(base, channel)
            } catch (e: Exception) {
                Logu.e(TAG, "从 $name 取发行版信息失败（通道 $channel）：${e.message}")
                errors.add("$name：${e.message}")
            }
        }
        throw IOException("所有更新源都失败（${errors.joinToString("；")}）")
    }

    private fun fetchRelease(base: String, channel: String): UpdateConfig {
        if (channel == UpdateChannel.STABLE) {
            // 快路径：latest 只有一条、体积小。但万一这个源不过滤预发布，拿到的就是测试版包，
            // 把它推给稳定用户是最坏的事故——所以校验不过就改走列表，绝不将错就错。
            try {
                return fetchLatest("$base/latest", channel)
            } catch (e: NotStableReleaseException) {
                Logu.w(TAG, "$base 的 latest 不是稳定版（tag=${e.tag}），改走发行版列表")
            }
        }
        return fetchBestFromList(base, channel)
    }

    /** 检查更新统一走 checkClient（20s callTimeout 总闸），下载走 okHttpClient（见 checkClient 注释）。 */
    private fun fetchBody(url: String): String {
        val request = Request.Builder().url(url).get().build()
        // use{} 保证失败分支抛异常时连接也归还（OkHttp 只在 body 读到 EOF 时才自动归还）
        return checkClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("服务器响应错误: ${response.code}")
            }
            response.body?.string() ?: throw IOException("响应体为空")
        }
    }

    private fun fetchLatest(url: String, channel: String): UpdateConfig {
        val json = JSONObject(fetchBody(url))
        val tag = json.optString("tag_name", "")
        if (!UpdateChannel.accepts(channel, tag)) throw NotStableReleaseException(tag)
        return buildConfig(json, tag)
    }

    /**
     * 列表模式：拉最近的发行版，按渠道过滤后取最新的一条。
     *
     * **不能依赖接口返回顺序**：Gitee 的 `/releases` 实测是升序、GitHub 是降序，两者相反，
     * 只能按 `versionCode` 自己比（比较逻辑在 [UpdateChannel.bestOf]）。
     */
    private fun fetchBestFromList(base: String, channel: String): UpdateConfig {
        val body = fetchBody("$base?per_page=$RELEASE_LIST_PAGE_SIZE&page=1")
        if (body.trimStart().startsWith("{")) {
            // 限流 / 鉴权失败时 GitHub 回的是对象而不是数组，报错要说清是这一层，别让人去猜解析器
            throw IOException("发行版列表接口返回了对象而非数组（可能被限流）：${body.take(120)}")
        }
        val array = JSONArray(body)
        val candidates = ArrayList<UpdateChannel.Candidate>()
        val byTag = HashMap<String, JSONObject>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val tag = obj.optString("tag_name", "")
            if (!UpdateChannel.accepts(channel, tag)) continue
            val code = UpdateRelease.parseMeta(obj.optString("body", ""), tag).versionCode
            if (code <= 0) continue
            candidates.add(UpdateChannel.Candidate(tag, code))
            byTag[tag] = obj
        }
        val best = UpdateChannel.bestOf(candidates, channel)
            ?: throw IOException("发行版列表里没有「$channel」通道可用的版本（共 ${array.length()} 条）")
        return buildConfig(byTag.getValue(best.tag), best.tag)
    }

    /**
     * 把一条 release JSON（Gitee / GitHub 字段形状一致）解析成 [UpdateConfig]。
     *
     * 版本号优先取 CI 写进 Release 说明的机器可读元数据，缺失时按 tag 推算；
     * 下载直链按设备 ABI 从附件里挑（Gitee 还会自动带 `{tag}.zip` 源码归档，必须按精确文件名匹配）。
     */
    private fun buildConfig(json: JSONObject, tag: String): UpdateConfig {
        val body = json.optString("body", "")
        val meta = UpdateRelease.parseMeta(body, tag)

        if (meta.versionCode <= 0) {
            // 宁可报错也不要把 0 当成版本号：否则客户端会「永远收不到更新」且毫无提示
            throw IOException("无法确定发行版版本号（tag=「$tag」，且 Release 说明里没有元数据）")
        }

        val assets = parseAssets(json)
        // Build.SUPPORTED_ABIS 是 API 21 的静态字段，minSdk 19 上直接读会 NoSuchFieldError，
        // 统一走 TerminalApi.supportedAbis()（内部有 SDK_INT 闸 + CPU_ABI 回落）。
        // 共存版必须只认自己那套附件名（app-coexist-*），否则会下到普通版、被 ApkVerifier 拒装。
        val downloadUrl = UpdateRelease.pickApkUrl(
            assets,
            TerminalApi.supportedAbis().toList(),
            UpdateRelease.apkNamePrefix(BuildConfig.COEXIST)
        ) ?: throw IOException("发行版里找不到可用的 APK 附件（共 ${assets.size} 个附件）")

        val config = UpdateConfig(
            meta.versionCode,
            meta.versionName,
            UpdateRelease.stripMeta(body),
            downloadUrl,
            meta.forceUpdate
        )
        cachedConfig = config
        return config
    }

    private fun parseAssets(json: JSONObject): List<UpdateRelease.ReleaseAsset> {
        val assetsJson = json.optJSONArray("assets") ?: return emptyList()
        val assets = ArrayList<UpdateRelease.ReleaseAsset>()
        for (i in 0 until assetsJson.length()) {
            val a = assetsJson.optJSONObject(i) ?: continue
            val name = a.optString("name", "")
            val downloadUrl = a.optString("browser_download_url", "")
            if (name.isNotEmpty() && downloadUrl.isNotEmpty()) {
                assets.add(UpdateRelease.ReleaseAsset(name, downloadUrl))
            }
        }
        return assets
    }

    fun downloadApk(
        url: String,
        context: Context,
        onProgress: (DownloadProgress) -> Unit,
        onComplete: (File) -> Unit,
        onError: (String) -> Unit
    ) {
        downloadCanceled = false
        CenterThreadPool.run {
            try {
                val apkDir = File(context.externalCacheDir ?: context.cacheDir, "update")
                if (!apkDir.exists()) apkDir.mkdirs()

                val existingFile = File(apkDir, APK_FILE_NAME)
                var downloadedBytes = if (existingFile.exists()) existingFile.length() else 0L

                val requestBuilder = Request.Builder().url(url).get()
                if (downloadedBytes > 0) {
                    requestBuilder.addHeader("Range", "bytes=$downloadedBytes-")
                }

                var response = okHttpClient.newCall(requestBuilder.build()).execute()

                if (!response.isSuccessful && response.code != 206) {
                    // 请求失败：丢弃残片从头重试
                    response.close()
                    downloadedBytes = 0L
                    existingFile.delete()
                    val retryRequest = Request.Builder().url(url).get().build()
                    response = okHttpClient.newCall(retryRequest).execute()
                    if (!response.isSuccessful) {
                        // 重试仍失败：body 不会有人读，必须显式关闭，否则连接不归还
                        response.close()
                        CenterThreadPool.runOnUiThread { onError(context.getString(R.string.text_update_download_failed, response.code.toString())) }
                        return@run
                    }
                } else if (downloadedBytes > 0 && response.code != 206) {
                    // 服务器忽略 Range 头返回 200 全量：必须丢弃旧残片从头写，
                    // 否则全量内容会被追加到残片之后，产出永久损坏的 APK
                    existingFile.delete()
                }

                val result = writeResponseToFile(response, existingFile, onProgress)

                // 26.10.04 批次 4（E6）：交给系统安装器之前先自查包名与签名。
                // 不通过就删掉文件（继续留残片会被下次的 Range 续传当成"已下载一部分"，永远修不好），
                // 并把中文原因交给调用方展示。
                val verifyResult = ApkVerifier.verify(context, result)
                if (!verifyResult.ok) {
                    result.delete()
                    CenterThreadPool.runOnUiThread { onError(context.getString(R.string.text_update_verify_failed, verifyResult.message)) }
                    return@run
                }

                CenterThreadPool.runOnUiThread { onComplete(result) }
            } catch (e: CancellationException) {
            } catch (e: IOException) {
                CenterThreadPool.runOnUiThread { onError(context.getString(R.string.text_update_download_failed, e.message)) }
            } catch (e: Exception) {
                CenterThreadPool.runOnUiThread { onError(context.getString(R.string.text_update_download_error, e.message)) }
            }
        }
    }

    @Throws(IOException::class, CancellationException::class)
    private fun writeResponseToFile(
        response: okhttp3.Response,
        file: File,
        onProgress: (DownloadProgress) -> Unit
    ): File {
        val body = response.body ?: throw IOException("响应体为空")
        val remainingBytes = body.contentLength()
        val inputStream: InputStream = body.byteStream()
        val append = file.exists()
        val outputStream = FileOutputStream(file, append)

        val buffer = ByteArray(8192)
        var bytesRead: Int
        var totalRead = if (append) file.length() else 0L
        
        val totalSize = if (remainingBytes > 0) {
            if (append) remainingBytes + totalRead else remainingBytes
        } else {
            0L
        }
        
        var lastReportTime = System.currentTimeMillis()

        try {
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                if (downloadCanceled) {
                    throw CancellationException("下载已取消")
                }
                outputStream.write(buffer, 0, bytesRead)
                totalRead += bytesRead

                val now = System.currentTimeMillis()
                if (now - lastReportTime >= 200) {
                    lastReportTime = now
                    val progress = if (totalSize > 0) totalRead.toFloat() / totalSize.toFloat() else 0f
                    CenterThreadPool.runOnUiThread {
                        onProgress(DownloadProgress(totalRead, totalSize, progress.coerceIn(0f, 1f)))
                    }
                }
            }
            outputStream.flush()
        } finally {
            inputStream.close()
            outputStream.close()
            response.close()
        }

        CenterThreadPool.runOnUiThread {
            onProgress(DownloadProgress(totalRead, totalRead, 1f))
        }
        return file
    }

    fun cancelDownload() {
        downloadCanceled = true
    }

    fun installApk(context: Context, apkFile: File) {
        val uri: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            FileProvider.getUriForFile(context, "${context.packageName}.FileProvider", apkFile)
        } else {
            Uri.fromFile(apkFile)
        }

        val intent = Intent(Intent.ACTION_VIEW)
        intent.setDataAndType(uri, "application/vnd.android.package-archive")
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val resInfoList = context.packageManager.queryIntentActivities(intent, 0)
            for (resolveInfo in resInfoList) {
                val packageName = resolveInfo.activityInfo.packageName
                context.grantUriPermission(packageName, uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
        }

        context.startActivity(intent)
    }
}