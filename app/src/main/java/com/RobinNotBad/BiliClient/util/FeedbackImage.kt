package com.RobinNotBad.BiliClient.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.RobinNotBad.BiliClient.api.TerminalApi
import com.RobinNotBad.BiliClient.model.ApiResult
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 反馈截图：选图之后的「压缩 → 上传 → 拿 key」这一段（26.11.27 新增）。
 *
 * <p>为什么要单独一个工具类：提交反馈与追加说明两处都要走同一套流程，规则一旦写两遍就会漂移。
 * 这里是**唯一的真相**：长边上限、JPEG 质量、大小上限、张数上限，以及「压缩失败怎么办」。
 *
 * <h3>与服务端的契约</h3>
 * 服务端（`server/rebiliterminal_api.py` 的 `POST /terminal/feedback/image`）收的是**裸字节**，
 * 不是 multipart。26.11.27 加固后**服务端自己解析图片尺寸**（PNG 的 IHDR / JPEG 的 SOF 段），
 * 客户端的 `X-Image-Width` / `X-Image-Height` 头不再被信任、也不用再发。
 * 它只认 JPEG / PNG 的魔数，单张与合计上限都是 10MB，另外还限像素数与长边（见服务端注释）。
 * 所以这里统一压成 JPEG：PNG 截图（手表截图常是 PNG）体积太大，直接传白白多占流量。
 *
 * <h3>为什么不保留原图</h3>
 * 反馈截图是给开发者看「哪里不对」，长边 1600 已经比手表屏幕大一个数量级；
 * 再大只会拖慢上传、白占存储。压完通常 200~500 KB。
 *
 * <h3>错误码</h3>
 * [upload] 复用 [TerminalApi.uploadFeedbackImage] 的 [ApiResult]：成功 `code == 0` 且
 * `message` 就是**图片 key**（不是给人看的文字），失败 `code < 0`。
 * [uploadUri] 额外多一个 [CODE_UNREADABLE]：这张图读不出来或压不到上限以内。
 */
object FeedbackImage {

    /** 最多几张。与服务端 `FEEDBACK_IMAGE_MAX_COUNT` 保持一致。 */
    const val MAX_COUNT = 3

    /** 解码后的长边上限（像素）。 */
    const val MAX_EDGE = 1600

    /**
     * 本地压缩的目标大小上限。
     *
     * 服务端 `FEEDBACK_IMAGE_MAX_BYTES` 是 10MB（三张合计也是 10MB），这里刻意定得**远小于**它：
     * 手表端网络本来就不快，200~500 KB 的图已经足够看清问题，没必要把用户的流量和上传等待
     * 花在 10MB 的原图上。哪天服务端放宽上限也不用抬这里。
     */
    const val MAX_BYTES = 2 * 1024 * 1024

    /** 首次压缩质量；超限时按 [JPEG_QUALITY_STEP] 递减，最低 [JPEG_QUALITY_MIN]。 */
    private const val JPEG_QUALITY_START = 85
    private const val JPEG_QUALITY_MIN = 55
    private const val JPEG_QUALITY_STEP = 15

    /** 原图字节上限：超过这个大小直接放弃，避免一张超大图把内存吃光。 */
    private const val RAW_LIMIT_BYTES = 32 * 1024 * 1024

    /** 「这张图读不出来 / 压不到上限以内」。 */
    const val CODE_UNREADABLE = -5

    /**
     * 压缩好、可以直接上传的一张图。
     *
     * @property data JPEG 字节
     * @property width 压缩后的宽（上报给服务端，只用于控制台显示）
     * @property height 压缩后的高
     */
    class Encoded(val data: ByteArray, val width: Int, val height: Int)

    /**
     * 解码采样率：让解码后的长边尽量接近但不超过 [maxEdge]，避免整张原图进内存。
     *
     * <p>纯函数，有 JVM 单测（[com.RobinNotBad.BiliClient.util.FeedbackImageTest]）。
     * 非法入参统一回落 1（不采样），让 [BitmapFactory] 自己去报错。
     */
    @JvmStatic
    fun sampleSizeFor(width: Int, height: Int, maxEdge: Int): Int {
        if (width <= 0 || height <= 0 || maxEdge <= 0) return 1
        var w = width
        var h = height
        var sample = 1
        while (max(w, h) / 2 >= maxEdge) {
            w /= 2
            h /= 2
            sample *= 2
        }
        return sample
    }

    /**
     * 读图 → 采样 → 缩到长边 [MAX_EDGE] → 压成 JPEG 且不超过 [MAX_BYTES]。
     *
     * <p>**阻塞调用**（读 Uri、解码、压缩），必须在 [CenterThreadPool] 里跑。
     * 返回 null 表示这张图读不出来或怎么压都超限，调用方按 [CODE_UNREADABLE] 提示。
     */
    @JvmStatic
    fun encode(context: Context, uri: Uri): Encoded? {
        // 一次读成字节再解两次（尺寸 + 像素）：同一 Uri 反复 openInputStream 在部分
        // 国产 ROM 上第二次会失败（云同步相册、跨应用 provider），表现就是「这张图读不出来」。
        val bytes = readBytes(context, uri) ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            Logu.w("反馈截图", "解不出尺寸，可能不是图片：" + uri + " bytes=" + bytes.size)
            return null
        }

        val opts = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, MAX_EDGE)
        }
        val decoded: Bitmap = try {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: run {
                Logu.w("反馈截图", "解码返回空：" + bounds.outWidth + "x" + bounds.outHeight)
                null
            }
        } catch (e: OutOfMemoryError) {
            Logu.w("反馈截图", "解码内存不足，跳过这张：" + bounds.outWidth + "x" + bounds.outHeight)
            null
        } ?: return null

        val scaled = scaleDown(decoded)
        val width = scaled.width
        val height = scaled.height
        try {
            var quality = JPEG_QUALITY_START
            while (true) {
                val out = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
                val bytes = out.toByteArray()
                if (bytes.size <= MAX_BYTES) return Encoded(bytes, width, height)
                if (quality <= JPEG_QUALITY_MIN) {
                    Logu.w("反馈截图", "压到最低质量仍超限，放弃这张图：" + bytes.size)
                    return null
                }
                quality -= JPEG_QUALITY_STEP
            }
        } finally {
            if (!scaled.isRecycled) scaled.recycle()
        }
    }

    /**
     * 把 Uri 一次性读成字节数组。读不到（或大得离谱）返回 null，并留下日志。
     *
     * <p>为什么不用 decodeStream：解码要读两遍（先尺寸后像素），而同一 Uri 第二次
     * openInputStream 在部分 ROM 上会失败。代价是整张原图进内存 —— 截图与相册照片
     * 几 MB 量级，比「读不出来」划算；[RAW_LIMIT_BYTES] 兜住异常大的文件。
     */
    private fun readBytes(context: Context, uri: Uri): ByteArray? {
        return try {
            val stream = context.contentResolver.openInputStream(uri)
            if (stream == null) {
                Logu.w("反馈截图", "打不开这个图片：" + uri)
                return null
            }
            stream.use {
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(64 * 1024)
                var total = 0
                while (true) {
                    val read = it.read(buffer)
                    if (read <= 0) break
                    total += read
                    if (total > RAW_LIMIT_BYTES) {
                        Logu.w("反馈截图", "原图太大，放弃这张：" + total + " " + uri)
                        return null
                    }
                    out.write(buffer, 0, read)
                }
                if (out.size() <= 0) {
                    Logu.w("反馈截图", "这个图片是空文件：" + uri)
                    return null
                }
                out.toByteArray()
            }
        } catch (e: Exception) {
            Logu.w("反馈截图", "读取图片失败：" + e.message + " " + uri)
            null
        }
    }

    /** 把长边压到 [MAX_EDGE] 以内；本来就不超就原样返回。 */
    private fun scaleDown(source: Bitmap): Bitmap {
        val longEdge = max(source.width, source.height)
        if (longEdge <= MAX_EDGE) return source
        val ratio = MAX_EDGE.toFloat() / longEdge
        val w = max(1, (source.width * ratio).roundToInt())
        val h = max(1, (source.height * ratio).roundToInt())
        val scaled = Bitmap.createScaledBitmap(source, w, h, true)
        if (scaled !== source && !source.isRecycled) source.recycle()
        return scaled
    }

    /**
     * 上传一张已经压好的图。**阻塞调用**，必须在 [CenterThreadPool] 里跑。
     *
     * @return 成功时 `code == 0`、`message` 为图片 key；失败见 [TerminalApi.uploadFeedbackImage]
     */
    @JvmStatic
    fun upload(encoded: Encoded): ApiResult =
        TerminalApi.uploadFeedbackImage(encoded.data, encoded.width, encoded.height)

    /**
     * 一步到位：压缩 + 上传。**阻塞调用**，必须在 [CenterThreadPool] 里跑。
     *
     * @return 成功时 `code == 0`、`message` 为图片 key；读不出来时 `code ==` [CODE_UNREADABLE]
     */
    @JvmStatic
    fun uploadUri(context: Context, uri: Uri): ApiResult {
        val encoded = encode(context, uri) ?: return ApiResult(CODE_UNREADABLE, "")
        return upload(encoded)
    }

    /**
     * 一批图的逐张上传结果。
     *
     * @property keys 已经传成功的 key（按选择顺序）；失败时它是**前缀**，调用方整条丢弃即可
     * @property failure 第一张失败的返回；null 表示全部成功
     */
    class UploadOutcome(val keys: ArrayList<String>, val failure: ApiResult?) {
        val ok: Boolean get() = failure == null
    }

    /**
     * 逐张上传一批图。**阻塞调用**，必须在 [CenterThreadPool] 里跑。
     *
     * <p>失败即停：不继续传后面的图（省流量），也不返回半截结果让调用方去拼 ——
     * 反馈与追加说明都是「要么整条带上图，要么一张都不带」，半截状态比没有更糟。
     *
     * @param onProgress 第几张开始时回调（index 从 0 起），调用方自己决定要不要回主线程显示
     */
    @JvmStatic
    fun uploadAll(
        context: Context,
        uris: List<Uri>,
        onProgress: ((index: Int, total: Int) -> Unit)? = null
    ): UploadOutcome {
        val keys = ArrayList<String>(uris.size)
        for ((index, uri) in uris.withIndex()) {
            onProgress?.invoke(index, uris.size)
            val result = uploadUri(context, uri)
            if (result.code != 0) return UploadOutcome(keys, result)
            keys.add(result.message)
        }
        return UploadOutcome(keys, null)
    }
}
