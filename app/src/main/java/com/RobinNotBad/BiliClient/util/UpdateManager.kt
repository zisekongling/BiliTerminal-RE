package com.RobinNotBad.BiliClient.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.RobinNotBad.BiliClient.BiliTerminal
import com.RobinNotBad.BiliClient.model.UpdateConfig
import kotlinx.coroutines.CancellationException
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit

object UpdateManager {

    private const val CONFIG_URL = "https://1816240476.v.123pan.cn/1816240476/%E7%9B%B4%E9%93%BE%E4%BC%A0%E8%BE%93/%E5%93%94%E5%93%A9%E7%BB%88%E7%AB%AF%E6%9B%B4%E6%96%B0/config.json"
    private const val APK_FILE_NAME = "bili_terminal_update.apk"

    @Volatile
    private var downloadCanceled = false

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
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

    private fun getApkFile(context: Context? = BiliTerminal.context): File? {
        return context?.let {
            val apkDir = File(it.externalCacheDir ?: it.cacheDir, "update")
            File(apkDir, APK_FILE_NAME)
        }
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

    private fun doFetchUpdateConfig(): UpdateConfig {
        val request = Request.Builder().url(CONFIG_URL).get().build()
        val response = okHttpClient.newCall(request).execute()

        if (!response.isSuccessful) {
            throw IOException("服务器响应错误: ${response.code}")
        }

        val body = response.body?.string() ?: throw IOException("响应体为空")
        response.close()

        return parseConfig(body)
    }

    private fun parseConfig(jsonStr: String): UpdateConfig {
        val json = JSONObject(jsonStr)

        val versionCode = json.optInt("versionCode", 0)
        val versionName = json.optString("versionName", "")
        val description = json.optString("description", "")
        val downloadUrl = json.optString("downloadUrl", "")
        val forceUpdate = json.optBoolean("forceUpdate", false)

        if (versionCode == 0 || downloadUrl.isEmpty()) {
            throw IOException("配置文件格式错误：缺少必要字段")
        }

        val config = UpdateConfig(versionCode, versionName, description, downloadUrl, forceUpdate)
        cachedConfig = config
        return config
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
                    downloadedBytes = 0L
                    existingFile.delete()
                    val retryRequest = Request.Builder().url(url).get().build()
                    response = okHttpClient.newCall(retryRequest).execute()
                    if (!response.isSuccessful) {
                        CenterThreadPool.runOnUiThread { onError("下载失败: ${response.code}") }
                        return@run
                    }
                }

                val result = writeResponseToFile(response, existingFile, onProgress)
                CenterThreadPool.runOnUiThread { onComplete(result) }
            } catch (e: CancellationException) {
            } catch (e: IOException) {
                CenterThreadPool.runOnUiThread { onError("下载失败：${e.message}") }
            } catch (e: Exception) {
                CenterThreadPool.runOnUiThread { onError("下载出错：${e.message}") }
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