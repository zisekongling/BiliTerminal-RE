package com.RobinNotBad.BiliClient.util

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import java.io.File

/**
 * 把下载好的 APK 变成可交给系统安装器的 `content://` URI。
 *
 * ## 为什么要单独拎出来
 *
 * 下载目录是可配置的。用户把「下载路径」设到外置 SD 卡后，安装包落在
 * `/storage/5C00-1D0E/...`，而 androidx 的 FileProvider 的根来自编译期资源
 * `res/xml/file_paths.xml`，它支持的所有 tag 都只认**主卷**（`<external-path>` 取
 * `Environment.getExternalStorageDirectory()`，`<external-media-path>` 只取
 * `getExternalMediaDirs()[0]`）——一个根都匹配不到，`getUriForFile` 直接抛
 * `IllegalArgumentException: Failed to find configured root`。
 *
 * 这条路径上的后果比「播不了视频」更难受：下载完成 → 安装器根本起不来 → 调用方把包
 * 改名回 `.bak` 并提示「安装失败」，用户看到的是「更新下载完了却装不上」（反馈 #222 第 3 点，
 * 华为机型上表现为「包消失了」）。所以这里和 [com.RobinNotBad.BiliClient.api.PlayerApi.getVideoUri]
 * 一样分两级：先 FileProvider（主卷、应用专属目录都命中），失败再交给运行时按所有卷算根的
 * [LocalFileProvider]。
 *
 * 只在 API 24+ 走 content:// —— 更低版本安装器还认 `file://`，且当时没有 FileProvider 的
 * `FileUriExposedException` 限制。
 */
object ApkUri {

    /**
     * @return 安装器可读的 URI
     * @throws IllegalArgumentException 两级都拿不到 URI（路径不在任何存储卷内、或 provider 没声明）。
     *   调用方**必须**接住并降级，别把异常抛进对话框回调——那是用户点「安装」的路径，崩了就等于
     *   「点了没反应」。
     */
    @JvmStatic
    fun of(context: Context, apk: File): Uri {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return Uri.fromFile(apk)
        return try {
            FileProvider.getUriForFile(context, context.packageName + ".FileProvider", apk)
        } catch (e: IllegalArgumentException) {
            // 只吞 IllegalArgumentException：FileProvider 在外置卷（Failed to find configured root）
            // 与 authority 对不上（Couldn't find meta-data for provider…）两种情况下抛的都是它，
            // 两种都该走自建 provider；其它异常类型仍向上抛，别悄悄掩盖。
            Logu.w("ApkUri", "安装包不在 FileProvider 配置根内（多为外置卷），改走 LocalFileProvider："
                    + apk.absolutePath + " -> " + e.message)
            LocalFileProvider.getUriForFile(context, apk)
                ?: throw IllegalArgumentException("安装包路径无法共享给安装器：" + apk.absolutePath, e)
        }
    }
}
