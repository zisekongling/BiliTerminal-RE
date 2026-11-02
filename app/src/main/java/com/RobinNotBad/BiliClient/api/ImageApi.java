package com.RobinNotBad.BiliClient.api;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import com.RobinNotBad.BiliClient.util.Result;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * 图片上传（投稿图床 upload_bfs）。
 *
 * <p>本类只负责「把本地 Uri 变成可上传的字节」与「把上传结果变成 B 站 pics 节点」两件事。
 * 真正的 HTTP 上传有意复用 {@link ReplyApi#uploadReplyImage(byte[], String, String, String)}——
 * 上游是单独抄了一份上传实现，但两处请求体（file_up / biz / category=daily / csrf）、
 * 地址（api.bilibili.com/x/dynamic/feed/draw/upload_bfs）与鉴权头完全一致，
 * 再抄一份只会让以后改上传逻辑要改两个地方。动态图与评论图走同一个 biz
 * （{@link #BIZ_DYNAMIC} = new_dyn，评论的 pictures 只认这个目录下的图），私信图走
 * {@link #BIZ_MESSAGE}（im）。
 */
public class ImageApi {

    /** 图片动态 / 评论图的业务标识（投稿图床，`/bfs/new_dyn/`）。 */
    public static final String BIZ_DYNAMIC = ReplyApi.BIZ_DYNAMIC;
    /** 私信图片的业务标识（IM 图床，`/bfs/im/`）。 */
    public static final String BIZ_MESSAGE = ReplyApi.BIZ_MESSAGE;

    /** 单张图硬上限。 */
    private static final long MAX_IMAGE_SIZE = 25L * 1024 * 1024;
    /** GIF 原样透传的上限。 */
    private static final long GIF_MAX_SIZE = 20L * 1024 * 1024;
    /** PNG 原样透传（保住透明通道）的上限。 */
    private static final long PNG_MAX_SIZE = 8L * 1024 * 1024;
    /** 重新编码时的最长边上限。 */
    private static final int MAX_COMPRESS_EDGE = 2048;
    /** 一条动态最多几张图（B 站限制）。 */
    public static final int MAX_IMAGE_COUNT = 9;

    private ImageApi() {
    }

    /** 待上传的图片：data 是原始字节，fileName / mimeType 决定服务端按什么格式解析。 */
    public static class PreparedImage {
        public final byte[] data;
        public final String fileName;
        public final String mimeType;

        public PreparedImage(byte[] data, String fileName, String mimeType) {
            this.data = data;
            this.fileName = fileName;
            this.mimeType = mimeType;
        }
    }

    /** 上传成功后的图片信息。 */
    public static class UploadedImage {
        public final String url;
        public final int width;
        public final int height;
        /**
         * 图片大小，单位 KB（小数）。
         *
         * <p>与 {@link ReplyApi.UploadImageData#img_size} 保持一致用 double：
         * 服务端返回的是小数 KB（官方示例 {@code "img_size": 6.261}，
         * bilibili-API/docs/dynamic/publish.md:66），用整型存会把小数截断。
         */
        public final double size;

        public UploadedImage(String url, int width, int height, double size) {
            this.url = url;
            this.width = width;
            this.height = height;
            this.size = size;
        }

        /** 转成动态 pics 数组里的一项。 */
        public JSONObject toDynamicPicJson() throws JSONException {
            return new JSONObject()
                    .put("img_src", url)
                    .put("img_width", width)
                    .put("img_height", height)
                    .put("img_size", size);
        }
    }

    /**
     * 准备要上传的图片。
     *
     * <p>不能一律解码后压成 JPEG：那样 GIF 动图会变成一张静帧、PNG 的透明通道会被填成底，
     * 而这两类在动态里都很常见。所以按真实类型分流：
     * <ul>
     *   <li>GIF：动图没法用 {@link Bitmap} 重新编码，只能原样透传（超过 20MB 才拒绝）</li>
     *   <li>PNG 且不超过 8MB：原样透传，保住透明通道</li>
     *   <li>其余（过大的 PNG、JPEG、WEBP 等）：按最长边采样后压成 JPEG 90</li>
     * </ul>
     *
     * <p>有意偏离上游：上游在这里读 EXIF 把照片摆正，那依赖 androidx.exifinterface，
     * 本项目没有这个依赖且不允许加，所以本实现与 {@code WriteReplyActivity} 的带图评论一致——
     * 不处理 EXIF 旋转，竖拍照片可能出现方向不对。
     *
     * @param context 用于取 ContentResolver
     * @param uri     选到的图片 Uri
     * @return 可上传的图片
     * @throws IOException 图片过大 / 读不出 / 解码失败
     */
    public static PreparedImage prepareImage(Context context, Uri uri) throws IOException {
        if (context == null || uri == null) throw new IOException("图片地址无效");
        ContentResolver resolver = context.getContentResolver();
        String mimeType = resolveImageType(resolver, uri);
        long now = System.currentTimeMillis();

        // 先按文件描述符探一下体积，超大图直接拒绝，避免在手表上把整张图读进内存就 OOM
        long declaredSize = getDeclaredSize(resolver, uri);
        if (declaredSize > MAX_IMAGE_SIZE) throw new IOException("图片过大（超过25MB）");

        byte[] raw = readAllBytes(resolver, uri);
        if (raw.length > MAX_IMAGE_SIZE) throw new IOException("图片过大（超过25MB）");

        if ("image/gif".equals(mimeType)) {
            if (raw.length > GIF_MAX_SIZE) throw new IOException("GIF过大（超过20MB），请换一张");
            return new PreparedImage(raw, "img_" + now + ".gif", "image/gif");
        }
        if ("image/png".equals(mimeType) && raw.length <= PNG_MAX_SIZE) {
            return new PreparedImage(raw, "img_" + now + ".png", "image/png");
        }

        Bitmap bitmap = decodeScaled(raw);
        if (bitmap == null) throw new IOException("解码图片失败");
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream);
        } finally {
            bitmap.recycle();
        }
        return new PreparedImage(outputStream.toByteArray(), "img_" + now + ".jpg", "image/jpeg");
    }

    /**
     * 上传图片到图床。
     *
     * @param data     图片数据，来自 {@link #prepareImage}
     * @param fileName 文件名（含扩展名）
     * @param mimeType 图片真实 MIME 类型，必须与数据真实格式一致
     * @param biz      业务标识，动态图与评论图传 {@link #BIZ_DYNAMIC}，私信图传 {@link #BIZ_MESSAGE}
     * @return 成功为 {@link UploadedImage}，失败为原始异常
     */
    public static Result<UploadedImage> uploadImage(byte[] data, String fileName, String mimeType, String biz) {
        if (data == null || data.length == 0) {
            return Result.failure(new IOException("图片数据为空"));
        }
        try {
            ReplyApi.UploadImageData raw = ReplyApi.uploadReplyImage(data, fileName, mimeType, biz).getOrThrow();
            return Result.success(new UploadedImage(raw.image_url, raw.image_width, raw.image_height, raw.img_size));
        } catch (Exception e) {
            return Result.failure(e);
        }
    }

    /**
     * 判断图片真实类型。优先用 content provider 声明的类型；
     * 有些 provider 不返回类型，这时按文件头魔数嗅探兜底。
     */
    private static String resolveImageType(ContentResolver resolver, Uri uri) {
        try {
            String declared = resolver.getType(uri);
            if (declared != null) {
                declared = declared.toLowerCase(java.util.Locale.ROOT);
                if (declared.startsWith("image/") && !"image/*".equals(declared)) return declared;
            }
        } catch (Exception ignored) {
        }
        InputStream input = null;
        try {
            input = resolver.openInputStream(uri);
            if (input == null) return "image/jpeg";
            byte[] head = new byte[12];
            int len = input.read(head);
            return sniffImageType(head, len);
        } catch (Exception e) {
            return "image/jpeg";
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    /**
     * 按文件头魔数判断类型。
     *
     * <p>必须 {@code & 0xFF}：Java 的 byte 有符号，0x89 直接比较会变成负数。
     */
    private static String sniffImageType(byte[] head, int len) {
        if (len >= 6 && (head[0] & 0xFF) == 0x47 && (head[1] & 0xFF) == 0x49 && (head[2] & 0xFF) == 0x46)
            return "image/gif";                                  // "GIF"
        if (len >= 8 && (head[0] & 0xFF) == 0x89 && (head[1] & 0xFF) == 0x50 && (head[2] & 0xFF) == 0x4E && (head[3] & 0xFF) == 0x47)
            return "image/png";                                  // ‰PNG
        if (len >= 3 && (head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8 && (head[2] & 0xFF) == 0xFF)
            return "image/jpeg";                                 // JPEG SOI
        if (len >= 12 && (head[8] & 0xFF) == 0x57 && (head[9] & 0xFF) == 0x45 && (head[10] & 0xFF) == 0x42 && (head[11] & 0xFF) == 0x50)
            return "image/webp";                                 // RIFF....WEBP
        return "image/jpeg";
    }

    /** 手动累积字节：Android 上 InputStream 没有 readAllBytes（那是 Java 9+ 的 API）。 */
    private static byte[] readAllBytes(ContentResolver resolver, Uri uri) throws IOException {
        InputStream input = resolver.openInputStream(uri);
        if (input == null) throw new IOException("无法读取图片");
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
            return outputStream.toByteArray();
        } finally {
            try {
                input.close();
            } catch (IOException ignored) {
            }
        }
    }

    /** 探测文件声明长度，拿不到就返回 -1（表示未知，不做预判）。 */
    private static long getDeclaredSize(ContentResolver resolver, Uri uri) {
        AssetFileDescriptor fd = null;
        try {
            fd = resolver.openAssetFileDescriptor(uri, "r");
            return fd == null ? -1L : fd.getLength();
        } catch (Exception e) {
            return -1L;
        } finally {
            if (fd != null) {
                try {
                    fd.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    /** 先按最长边 2048 采样再解码，避免手表直接解码超大原图时 OOM。 */
    private static Bitmap decodeScaled(byte[] raw) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(raw, 0, raw.length, bounds);
        int sampleSize = 1;
        int longest = Math.max(bounds.outWidth, bounds.outHeight);
        while (sampleSize > 0 && longest / sampleSize > MAX_COMPRESS_EDGE) sampleSize *= 2;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sampleSize;
        return BitmapFactory.decodeByteArray(raw, 0, raw.length, options);
    }
}
