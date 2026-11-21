package com.RobinNotBad.BiliClient.service.download

import com.RobinNotBad.BiliClient.util.Logu
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * 把按清单顺序下好的临时分片拼成一个成品文件（反馈 #87/#90）。
 *
 * 两条硬要求：
 * 1. **顺序**：严格按传入顺序（= 清单 order）字节级拼接；
 * 2. **完整性**：任何一段缺失、或拼接后长度不等于各段之和，都抛 [IOException] 并删掉半成品 ——
 *    宁可让任务失败一次（用户能看到失败原因），也绝不产出一个"能打开但短了"的残片。
 *
 * 与 DASH 的 [com.RobinNotBad.BiliClient.util.MediaMerger] 不是一回事：那条链路是
 * "音轨 + 视频轨重封装成容器"，这里只是同一份文件的字节片段做首尾相接，不解析容器。
 */
internal object DownloadSegmentMerger {

    /** 读一块的大小：与 DownloadService 里整文件下载的写块保持一致（256KB），对老手表内存友好。 */
    private const val BUFFER_SIZE = 256 * 1024

    /**
     * 把 [parts] 按顺序拼进 [target]（**整份覆盖**，不是追加）。
     *
     * @return 实际写入的字节数
     * @throws IOException 分段缺失、读写出错或最终长度校验不过（此时 [target] 已被删除）
     */
    @Throws(IOException::class)
    fun merge(parts: List<File>, target: File): Long {
        if (parts.isEmpty()) throw IOException("没有分段可拼接")

        var total = 0L
        try {
            FileOutputStream(target, false).use { output ->
                parts.forEachIndexed { index, part ->
                    if (!part.isFile) throw IOException("第 $index 段缺失：${part.name}")
                    part.inputStream().use { input ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                        }
                    }
                    total += part.length()
                }
                output.flush()
            }

            val written = target.length()
            if (written != total) {
                throw IOException("拼接结果校验不过：实际 $written 字节，应为 $total 字节")
            }
            return total
        } catch (e: IOException) {
            // 失败不留半成品：它的长度对不上，留着只会被当成可播放的视频
            if (target.exists()) target.delete()
            Logu.e("DownloadSegmentMerger", "分段拼接失败：${e.message}")
            throw e
        }
    }
}
