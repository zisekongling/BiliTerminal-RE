package com.RobinNotBad.BiliClient.api;

import com.RobinNotBad.BiliClient.util.NetWorkUtil;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * 评论区翻译的 gRPC 客户端（{@code bilibili.main.community.reply.v1.Reply/TranslateReply}）。
 *
 * <p>协议来源：反编译 App 9.16.1 + 线上实测（见 docs 之外的调研材料：`translate_reply.mjs`、
 * `评论区翻译接口文档.md`）。这里是**手写**的 protobuf 编解码与 gRPC 帧处理，原因有三：
 * <ol>
 *   <li>项目约定不引入新第三方依赖（原 protobuf-javalite 已在 26.09.11 死代码清理中移除）；</li>
 *   <li>这条链路只用到 varint 与 length-delimited 两种 wire type、unary 一个帧，整套 gRPC 栈
 *       （grpc-java + protoc 代码生成 + 几 MB 运行时）换不来任何收益；</li>
 *   <li>gRPC unary 本质上只是「HTTP/2 POST + 5 字节帧头 + trailers」，OkHttp 已经负责 HTTP/2 与 TLS，
 *       需要补的只有下面这 200 行。</li>
 * </ol>
 *
 * <p><b>成功判据是 translated_content 有没有值，而不是 reply_control.translation_switch。</b>
 * switch 表示「服务端认为当前应该展示原文还是译文」：实测翻译成功时它恒为
 * {@code SHOW_ORIGIN(3)}（服务端记录的是原文态），失败时为 {@code UNSUPPORTED(1)}。用 switch
 * 判成功会把每一条成功都当成失败。
 *
 * <p><b>空 map / 没有译文都不是错误。</b>rpid 不存在、目标语言与源语言相同（zh→zh）、内容级不可译
 * （纯表情/纯数字/@ 提及）时，服务端都返回 {@code grpc-status: 0}，只是不带译文。真正的错误只由
 * HTTP 状态码、{@code grpc-status}、{@code bili-status-code} 表示。所以本类只在真正的错误上抛
 * {@link IOException}，其余返回空 map，由调用方按「暂时无法翻译该内容」降级，不要把它当网络错误重试
 * （换语言重试也没用，这是内容级判定）。
 *
 * <p><b>x-bili-locale-bin 只发最小的 language</b>：文档 §4.3 实测「只带 c_locale.language」就能成功，
 * 且同一条评论换语言重试结果一致，说明服务端只按 language 决策。完整 Locale 还要带
 * s_locale/timezone/utc_offset/always_translate 等设备状态，与「把这条评论翻成什么语言」无关，
 * 只会让请求多出几个可能随服务端策略变化的字段。
 *
 * <p><b>HTTP/2 是硬前提</b>：OkHttp 在 Android 5.0+ 才能靠平台 ALPN 协商 h2；Android 4.4（API 19/20）
 * 没有 ALPN，只能退回 HTTP/1.1，gRPC 网关会直接拒绝。这是 minSdk 19 老设备上的已知限制，
 * 调用方必须捕获 {@link IOException} 降级成「翻译失败」，不能假定这个功能一定可用。
 *
 * <p>全部方法同步阻塞，调用方按项目约定放进 {@code CenterThreadPool.run { }}，不要在主线程调用。
 */
public class ReplyTranslateApi {

    /** gRPC 服务地址（HTTP/2 + TLS，游客可用，不需要登录态与签名）。 */
    public static final String GRPC_URL =
            "https://grpc.biliapi.net/bilibili.main.community.reply.v1.Reply/TranslateReply";

    /**
     * 单次请求最多携带的 rpid 数量。
     *
     * <p>文档 §5.4 建议 ≤20：批量越大越容易触发风控，且服务端没有验证过更大的批量。
     * 当前 UI 每次只翻一条，数组参数是为了保留批量能力。
     */
    public static final int MAX_BATCH_RPIDS = 20;

    /**
     * 评论区「文A」翻译入口是否对用户可见。
     *
     * <p>暂时关闭：接口对「外语评论 → 中文」这条链路没有找到样本做过真机验证，先把入口藏起来，
     * 实现、单测与文档全部保留；验证通过后把这里改回 {@code true} 就恢复显示，不用再动 UI 代码。
     *
     * <p>入口关掉时 UI 侧不显示按钮、不显示译文区，也不会挂点击事件，用户看不到任何翻译痕迹。
     */
    public static final boolean UI_ENABLED = false;

    /**
     * 评论区译文的目标语言：简体中文（{@code zh}）。
     *
     * <p>App 只有中文界面（{@code resConfigs "zh"}），所以译文固定成中文、<b>不跟随系统语言</b>：
     * 否则系统语言是英文的手表会把中文评论翻成英文，与界面语言不一致。
     *
     * <p>取的是文档 §4.3 实测过的 {@code language=zh} 最小头（base64 {@code CgQKAnpo}）。
     * 服务端拿它跟源语言比较：源评论已经是中文时返回空译文——这是「无需翻译」而不是错误，
     * 见类注释里对空 map 的说明。
     */
    public static final Locale TARGET_LOCALE = Locale.SIMPLIFIED_CHINESE;

    /** 响应体大小上限：翻译响应很小（≤20 条评论），超过这个量级说明拿到的不是预期数据。 */
    private static final int MAX_RESPONSE_BYTES = 8 * 1024 * 1024;

    /** protobuf wire type：0=varint、1=fixed64、2=length-delimited、5=fixed32（3/4 的 group 已废弃，不支持）。 */
    private static final int WIRE_VARINT = 0;
    private static final int WIRE_FIXED64 = 1;
    private static final int WIRE_LENGTH_DELIMITED = 2;
    private static final int WIRE_FIXED32 = 5;

    // 字段号来自实测（文档 §3 字段表 + 反编译的 TranslateReplyReq/TranslateReplyResp）。
    // 这些数字是协议本身，不能"顺手"改：改错一个字节服务端就会解成别的 type/oid，或直接回 12002。
    private static final int REQ_FIELD_TYPE = 1;
    private static final int REQ_FIELD_OID = 2;
    private static final int REQ_FIELD_RPIDS = 3;

    private static final int RESP_FIELD_TRANSLATED_REPLIES = 1;
    private static final int MAP_ENTRY_FIELD_KEY = 1;
    private static final int MAP_ENTRY_FIELD_VALUE = 2;
    private static final int REPLY_INFO_FIELD_TRANSLATED_CONTENT = 17;
    private static final int CONTENT_FIELD_MESSAGE = 1;

    /** RFC 4648 标准 base64 字母表（见 {@link #base64(byte[])} 为什么不用系统实现）。 */
    private static final char[] BASE64_ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray();

    private ReplyTranslateApi() {
        // 纯静态工具类，不实例化
    }

    // ------------------------------------------------------------------
    // 编码：请求 protobuf body + gRPC 帧
    // ------------------------------------------------------------------

    /**
     * 编码 {@code TranslateReplyReq} 的 protobuf body（**不含** gRPC 的 5 字节帧头）。
     *
     * <pre>
     * TranslateReplyReq {
     *   int64 type = 1;               // 评论区主题类型：视频=1、专栏=12、动态=17、相簿=11、音频=14、课堂=33
     *   int64 oid = 2;                // 视频为 aid（不是 bvid）；动态为动态 id；专栏为 cvid
     *   repeated int64 rpids = 3;     // packed：tag=0x1A + 长度 + 连续 varint
     * }
     * </pre>
     *
     * <p>为什么在编码前就校验而不等服务端报错：type/oid 配错时服务端只回一个
     * {@code 12002「当前页面评论功能已关闭」}，与"评论区真的关了"无法区分；参数非法时本地直接抛
     * {@link IllegalArgumentException}，问题立刻暴露在调用点上。
     *
     * @param type  评论区主题类型，必须 &gt; 0（0=NONE，服务端必然报错）
     * @param oid   主题 id（视频传 aid）
     * @param rpids 评论 id 列表，1~{@link #MAX_BATCH_RPIDS} 条
     * @return protobuf 编码的请求消息
     * @throws IllegalArgumentException type/rpids 非法
     */
    public static byte[] encodeTranslateRequest(int type, long oid, long[] rpids) {
        checkRequestArgs(type, rpids);

        ByteArrayOutputStream out = new ByteArrayOutputStream(32);
        writeTag(out, REQ_FIELD_TYPE, WIRE_VARINT);
        writeVarint(out, type);
        writeTag(out, REQ_FIELD_OID, WIRE_VARINT);
        writeVarint(out, oid);

        // repeated int64 在 proto3 里默认 packed：先拼好所有 varint，再作为一个
        // length-delimited 字段（tag=0x1A）写出去。逐个写 tag 也能被解析，
        // 但那不是文档实测的字节形态，没必要给自己加不确定性。
        ByteArrayOutputStream packed = new ByteArrayOutputStream(rpids.length * 5);
        for (long rpid : rpids) {
            writeVarint(packed, rpid);
        }
        byte[] packedBytes = packed.toByteArray();
        writeTag(out, REQ_FIELD_RPIDS, WIRE_LENGTH_DELIMITED);
        writeVarint(out, packedBytes.length);
        out.write(packedBytes, 0, packedBytes.length);
        return out.toByteArray();
    }

    /**
     * 给 protobuf body 套上 gRPC 帧头（unary 调用只发一帧）。
     *
     * <pre>
     * +--------+------------------+---------------------------+
     * | 1 byte |    4 bytes BE    |  protobuf 编码的消息        |
     * |  0x00  |  message length  |  TranslateReplyReq        |
     * +--------+------------------+---------------------------+
     * </pre>
     *
     * <p>首字节是 compressed-flag，只能写 0：服务端不使用压缩；写成 1 表示 body 被压缩，
     * 但这里并没有压缩，会把服务端解出乱码。
     *
     * @param payload protobuf 编码的消息体
     * @return 可直接作为 HTTP body 发送的 gRPC 帧
     */
    public static byte[] wrapGrpcFrame(byte[] payload) {
        if (payload == null) {
            throw new IllegalArgumentException("payload 不能为 null：gRPC 帧必须携带消息体");
        }
        int length = payload.length;
        byte[] frame = new byte[5 + length];
        frame[0] = 0;
        frame[1] = (byte) (length >>> 24);
        frame[2] = (byte) (length >>> 16);
        frame[3] = (byte) (length >>> 8);
        frame[4] = (byte) length;
        System.arraycopy(payload, 0, frame, 5, length);
        return frame;
    }

    // ------------------------------------------------------------------
    // 解码：响应 protobuf body
    // ------------------------------------------------------------------

    /**
     * 解码 {@code TranslateReplyResp} 的 protobuf body，返回「rpid → 译文」。
     *
     * <pre>
     * TranslateReplyResp { map&lt;int64, ReplyInfo&gt; translated_replies = 1; }
     * map entry: field1 = key(rpid, varint), field2 = value(ReplyInfo)
     * ReplyInfo.translated_content = 17 (Content)
     * Content.message               = 1  (string)
     * </pre>
     *
     * <p>只收「有非空译文」的条目：
     * <ul>
     *   <li>请求里不存在的 rpid 不会出现在 map 里；</li>
     *   <li>不可译/未译的条目会出现在 map 里，但 ReplyInfo 没有 translated_content
     *       （或 message 为空串）；</li>
     *   <li>这两种都不算错误（grpc-status 仍是 0），返回空 map 即可，由 UI 保持原文。</li>
     * </ul>
     *
     * <p>未知字段一律按 wire type 跳过而不是报错：服务端随时会给 ReplyInfo 加字段，解析器不能因此崩。
     * 只有「数据被截断」或「遇到不认识的 wire type」才抛 {@link IOException}——这两种情况意味着
     * 拿到的字节流本身有问题，硬读下去只会解析出完全不存在的 rpid/译文。
     *
     * @param payload 响应 protobuf body（不含 gRPC 帧头），null/空数组按空 map 处理
     * @return LinkedHashMap，顺序与服务端 map entry 顺序一致
     * @throws IOException 数据截断、varint 超过 64 位或 wire type 不支持
     */
    public static Map<Long, String> parseTranslateResponse(byte[] payload) throws IOException {
        Map<Long, String> translated = new LinkedHashMap<>();
        if (payload == null || payload.length == 0) {
            return translated;
        }

        Cursor cursor = new Cursor(payload, 0, payload.length);
        while (!cursor.atEnd()) {
            long tag = cursor.readVarint();
            int fieldNumber = (int) (tag >>> 3);
            int wireType = (int) (tag & 0x7);
            if (fieldNumber == RESP_FIELD_TRANSLATED_REPLIES && wireType == WIRE_LENGTH_DELIMITED) {
                int entryLength = cursor.readLength("translated_replies 的 map entry");
                int entryStart = cursor.position();
                cursor.advance(entryLength);
                readMapEntry(payload, entryStart, entryLength, translated);
            } else {
                cursor.skipField(wireType);
            }
        }
        return translated;
    }

    // ------------------------------------------------------------------
    // 目标语言头
    // ------------------------------------------------------------------

    /**
     * 构造 {@code x-bili-locale-bin} 请求头的值（base64 过的 Locale protobuf）。
     *
     * <pre>
     * Locale { LocaleIds c_locale = 1; }        // 只发最小头：不带 s_locale/timezone/...
     * LocaleIds { string language = 1; }        // ISO 639-1 小写，如 en/ja/zh
     * </pre>
     *
     * <p>这个头决定「翻译成什么语言」，**不发或发错都不会报错**，只会静默返回没有译文的空结果，
     * 所以三个实测值（en/ja/zh）必须在单测里钉死。
     *
     * <p>language 为空（例如 {@link Locale#ROOT}）时回退 "en"：空字符串编码出来的 Locale 等于
     * 没发头，服务端一律判 UNSUPPORTED。
     *
     * @param locale 目标语言，null 按 "en" 处理
     * @return 可直接放进 {@code x-bili-locale-bin} 的 base64 字符串
     */
    public static String buildLocaleHeader(Locale locale) {
        String language = locale == null ? null : locale.getLanguage();
        if (language == null || language.isEmpty()) {
            language = "en";
        }
        language = language.toLowerCase(Locale.ROOT);
        byte[] languageBytes = language.getBytes(StandardCharsets.UTF_8);

        // LocaleIds{language="xx"}：长度必须按实际字节数写，不能假定正好 2 个字符
        ByteArrayOutputStream localeIds = new ByteArrayOutputStream(languageBytes.length + 2);
        writeTag(localeIds, 1, WIRE_LENGTH_DELIMITED);
        writeVarint(localeIds, languageBytes.length);
        localeIds.write(languageBytes, 0, languageBytes.length);
        byte[] localeIdsBytes = localeIds.toByteArray();

        // Locale{c_locale=LocaleIds}
        ByteArrayOutputStream localeBytes = new ByteArrayOutputStream(localeIdsBytes.length + 2);
        writeTag(localeBytes, 1, WIRE_LENGTH_DELIMITED);
        writeVarint(localeBytes, localeIdsBytes.length);
        localeBytes.write(localeIdsBytes, 0, localeIdsBytes.length);
        return base64(localeBytes.toByteArray());
    }

    // ------------------------------------------------------------------
    // 网络调用（同步阻塞）
    // ------------------------------------------------------------------

    /**
     * 批量翻译评论（同步阻塞，调用方放进线程池）。
     *
     * <p>请求头只带文档实测必需/有益的最小集合：
     * <ul>
     *   <li>{@code content-type: application/grpc}（由 RequestBody 的 MediaType 自动写入，
     *       少了会被网关当普通请求处理）；</li>
     *   <li>{@code x-bili-locale-bin}：决定是否翻译、翻译成什么语言；</li>
     *   <li>{@code te: trailers}、{@code user-agent: grpc-java-okhttp/1.60.0}、
     *       {@code grpc-accept-encoding: identity}：标准 gRPC 客户端头，实测不带也能成功，
     *       但带上能保证网关按 gRPC 语义处理、且响应体不压缩（本类不实现帧解压）。</li>
     * </ul>
     *
     * <p>错误处理分三层：
     * <ol>
     *   <li>HTTP 非 2xx → {@code IOException("HTTP xxx")}；</li>
     *   <li>HTTP 200 但 {@code grpc-status}/{@code bili-status-code} 非 "0"（trailers-only 业务错误，
     *       例如 12002「当前页面评论功能已关闭」）→ 抛出带错误码的 IOException；</li>
     *   <li>body 帧损坏（帧长越界、compressed-flag=1）→ IOException。</li>
     * </ol>
     * 其余情况（空 body、空 map、条目没有译文）一律返回空 map，不抛异常。
     *
     * @param type         评论区主题类型，必须 &gt; 0
     * @param oid          主题 id（视频传 aid）
     * @param rpids        评论 id 列表，1~{@link #MAX_BATCH_RPIDS} 条
     * @param localeHeader {@link #buildLocaleHeader(Locale)} 的返回值；null/空表示不发该头
     *                     （服务端会返回 UNSUPPORTED，无译文但不报错）
     * @return rpid → 译文，无译文为空 map
     * @throws IOException 网络错误、HTTP 错误、gRPC 业务错误或响应帧损坏
     */
    public static Map<Long, String> translateReplies(int type, long oid, long[] rpids, String localeHeader)
            throws IOException {
        // 参数非法不该真的发一次请求出去（UI 误用时既能立刻暴露，也避免白白触发风控）
        checkRequestArgs(type, rpids);

        byte[] frame = wrapGrpcFrame(encodeTranslateRequest(type, oid, rpids));
        RequestBody requestBody = RequestBody.create(MediaType.parse("application/grpc"), frame);
        // 走 NetWorkUtil 的构造函数拿带账号代际标签的 Builder：这条 gRPC 请求不经过
        // NetWorkUtil 的通用发送方法，自己 new 出来的请求没有标签，代际隔离对它失效。
        Request.Builder builder = NetWorkUtil.newTaggedRequestBuilder()
                .url(GRPC_URL)
                .post(requestBody)
                .addHeader("te", "trailers")
                .addHeader("user-agent", "grpc-java-okhttp/1.60.0")
                .addHeader("grpc-accept-encoding", "identity");
        if (localeHeader != null && !localeHeader.isEmpty()) {
            builder.addHeader("x-bili-locale-bin", localeHeader);
        }

        // 复用全 App 唯一 OkHttpClient（NetWorkUtil），不要在这里 new：新 client 会重开连接池、
        // 丢掉既有的 TLS/代理/超时配置，也绕开项目的请求头与日志约定。
        try (Response response = NetWorkUtil.getOkHttpInstance().newCall(builder.build()).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code());
            }
            // trailers-only 业务错误：HTTP 仍是 200、body 为空，错误码在响应头里
            checkGrpcError(response.header("bili-status-code"),
                    response.header("grpc-status"), response.header("grpc-message"));

            ResponseBody responseBody = response.body();
            byte[] raw = responseBody == null ? new byte[0] : responseBody.bytes();
            if (raw.length > MAX_RESPONSE_BYTES) {
                throw new IOException("翻译响应异常：body 长度 " + raw.length + " 字节，超过预期上限");
            }

            // 成功响应的 grpc-status 在 HTTP/2 trailers 里（headers 里通常没有），body 读完后才能取。
            // 必须在解析 body 之前/之后都查一次：只查 headers 会漏掉"头干净、trailers 报错"的情况。
            checkGrpcErrorFromTrailers(response);

            if (raw.length == 0) {
                return new LinkedHashMap<>();
            }
            return parseGrpcFrames(raw);
        }
    }

    /**
     * 翻译单条评论（同步阻塞）。
     *
     * <p>便利方法，内部就是 {@link #translateReplies(int, long, long[], String)} + 取 map。
     * 「没有译文」返回 null（不是异常），调用方保持原文即可；真正的错误照常抛 {@link IOException}。
     *
     * @param type         评论区主题类型，必须 &gt; 0
     * @param oid          主题 id（视频传 aid）
     * @param rpid         评论 id
     * @param localeHeader {@link #buildLocaleHeader(Locale)} 的返回值
     * @return 译文；不可译/不存在的评论返回 null
     * @throws IOException 见 {@link #translateReplies(int, long, long[], String)}
     */
    public static String translateReply(int type, long oid, long rpid, String localeHeader) throws IOException {
        return translateReplies(type, oid, new long[]{rpid}, localeHeader).get(rpid);
    }

    // ------------------------------------------------------------------
    // 参数校验
    // ------------------------------------------------------------------

    private static void checkRequestArgs(int type, long[] rpids) {
        if (type <= 0) {
            throw new IllegalArgumentException(
                    "type 必须大于 0：视频=1、相簿=11、专栏=12、音频=14、动态=17、课堂=33（0=NONE，服务端必然报 12002）");
        }
        if (rpids == null || rpids.length == 0) {
            throw new IllegalArgumentException("rpids 不能为空：一次请求至少要翻译一条评论");
        }
        if (rpids.length > MAX_BATCH_RPIDS) {
            throw new IllegalArgumentException(
                    "rpids 一次最多 " + MAX_BATCH_RPIDS + " 条：批量越大越容易触发风控，服务端未验证更大批量");
        }
    }

    // ------------------------------------------------------------------
    // protobuf 编码基础
    // ------------------------------------------------------------------

    private static void writeTag(ByteArrayOutputStream out, int fieldNumber, int wireType) {
        writeVarint(out, ((long) fieldNumber << 3) | (wireType & 0x7));
    }

    /**
     * 写一个 varint。
     *
     * <p>int64 在 protobuf 里按**无符号 64 位**编码：负数（实际请求里不会出现，type/oid/rpid 都是正数）
     * 用其 64 位补码写出 10 字节，而不是抛异常或按有符号截断。写成有符号会让 wire 数据与服务端
     * 理解不一致，所以这里统一用 {@code >>>}（无符号右移）。
     */
    private static void writeVarint(ByteArrayOutputStream out, long value) {
        while ((value & ~0x7FL) != 0) {
            out.write((int) ((value & 0x7F) | 0x80));
            value >>>= 7;
        }
        out.write((int) value);
    }

    // ------------------------------------------------------------------
    // protobuf 解码基础
    // ------------------------------------------------------------------

    /** 解析 map entry：field1=key(rpid)，field2=value(ReplyInfo)。 */
    private static void readMapEntry(byte[] buffer, int offset, int length, Map<Long, String> out)
            throws IOException {
        Cursor cursor = new Cursor(buffer, offset, offset + length);
        Long rpid = null;
        String message = null;
        while (!cursor.atEnd()) {
            long tag = cursor.readVarint();
            int fieldNumber = (int) (tag >>> 3);
            int wireType = (int) (tag & 0x7);
            if (fieldNumber == MAP_ENTRY_FIELD_KEY && wireType == WIRE_VARINT) {
                rpid = cursor.readVarint();
            } else if (fieldNumber == MAP_ENTRY_FIELD_VALUE && wireType == WIRE_LENGTH_DELIMITED) {
                int valueLength = cursor.readLength("map entry 的 ReplyInfo");
                int valueStart = cursor.position();
                cursor.advance(valueLength);
                message = readTranslatedMessage(buffer, valueStart, valueLength);
            } else {
                cursor.skipField(wireType);
            }
        }
        // 没有 key 或没有非空译文都不收：前者无法对上评论，后者对 UI 等于没翻译
        if (rpid != null && message != null && !message.isEmpty()) {
            out.put(rpid, message);
        }
    }

    /** 在 ReplyInfo 里找 translated_content（field 17）。 */
    private static String readTranslatedMessage(byte[] buffer, int offset, int length) throws IOException {
        String message = null;
        Cursor cursor = new Cursor(buffer, offset, offset + length);
        while (!cursor.atEnd()) {
            long tag = cursor.readVarint();
            int fieldNumber = (int) (tag >>> 3);
            int wireType = (int) (tag & 0x7);
            if (fieldNumber == REPLY_INFO_FIELD_TRANSLATED_CONTENT && wireType == WIRE_LENGTH_DELIMITED) {
                int contentLength = cursor.readLength("ReplyInfo.translated_content");
                int contentStart = cursor.position();
                cursor.advance(contentLength);
                String text = readContentMessage(buffer, contentStart, contentLength);
                if (text != null && !text.isEmpty()) {
                    message = text;
                }
            } else {
                cursor.skipField(wireType);
            }
        }
        return message;
    }

    /** 在 Content 里找 message（field 1，UTF-8 字符串）。 */
    private static String readContentMessage(byte[] buffer, int offset, int length) throws IOException {
        String message = "";
        Cursor cursor = new Cursor(buffer, offset, offset + length);
        while (!cursor.atEnd()) {
            long tag = cursor.readVarint();
            int fieldNumber = (int) (tag >>> 3);
            int wireType = (int) (tag & 0x7);
            if (fieldNumber == CONTENT_FIELD_MESSAGE && wireType == WIRE_LENGTH_DELIMITED) {
                int messageLength = cursor.readLength("Content.message");
                int messageStart = cursor.position();
                cursor.advance(messageLength);
                message = new String(buffer, messageStart, messageLength, StandardCharsets.UTF_8);
            } else {
                cursor.skipField(wireType);
            }
        }
        return message;
    }

    /**
     * protobuf 读取游标（限定在一个 [start, end) 区间内）。
     *
     * <p>所有读取都做边界检查：protobuf 的 length 是攻击面（响应字节流由服务端/中间人决定），
     * 声明长度超过剩余字节时必须立刻抛异常，绝不能带着越界偏移继续读。
     */
    private static final class Cursor {
        private final byte[] buffer;
        private final int end;
        private int position;

        Cursor(byte[] buffer, int start, int end) {
            this.buffer = buffer;
            this.position = start;
            this.end = end;
        }

        boolean atEnd() {
            return position >= end;
        }

        int position() {
            return position;
        }

        void advance(int count) throws IOException {
            if (count < 0 || count > end - position) {
                throw new IOException("protobuf 数据被截断：还需要 " + count + " 字节，实际只剩 " + (end - position) + " 字节");
            }
            position += count;
        }

        /** 读 varint，返回 long（按无符号 64 位理解）。 */
        long readVarint() throws IOException {
            long result = 0;
            int shift = 0;
            while (position < end) {
                int b = buffer[position++] & 0xFF;
                result |= (long) (b & 0x7F) << shift;
                if ((b & 0x80) == 0) {
                    return result;
                }
                shift += 7;
                if (shift > 63) {
                    throw new IOException("protobuf varint 超过 64 位：响应数据已损坏");
                }
            }
            throw new IOException("protobuf 数据被截断：消息在 varint 中间结束");
        }

        /** 读 length-delimited 字段的长度，并校验它没有超过剩余字节数。 */
        int readLength(String what) throws IOException {
            long length = readVarint();
            if (length < 0 || length > end - position) {
                throw new IOException("protobuf 数据被截断：" + what + " 声明长度 " + length
                        + " 字节，实际只剩 " + (end - position) + " 字节");
            }
            return (int) length;
        }

        /** 跳过不认识的字段（服务端新增字段时不能崩）。 */
        void skipField(int wireType) throws IOException {
            switch (wireType) {
                case WIRE_VARINT:
                    readVarint();
                    break;
                case WIRE_FIXED64:
                    advance(8);
                    break;
                case WIRE_LENGTH_DELIMITED:
                    advance(readLength("length-delimited 字段"));
                    break;
                case WIRE_FIXED32:
                    advance(4);
                    break;
                default:
                    throw new IOException("不支持的 protobuf wire type=" + wireType
                            + "：只支持 0/1/2/5（收到的可能不是 protobuf 数据）");
            }
        }
    }

    // ------------------------------------------------------------------
    // gRPC 帧 / 错误处理
    // ------------------------------------------------------------------

    /** 逐帧解析响应 body，合并每一帧的结果。 */
    private static Map<Long, String> parseGrpcFrames(byte[] raw) throws IOException {
        Map<Long, String> merged = new LinkedHashMap<>();
        int offset = 0;
        while (offset + 5 <= raw.length) {
            int compressedFlag = raw[offset] & 0xFF;
            long frameLength = ((long) (raw[offset + 1] & 0xFF) << 24)
                    | ((long) (raw[offset + 2] & 0xFF) << 16)
                    | ((long) (raw[offset + 3] & 0xFF) << 8)
                    | (long) (raw[offset + 4] & 0xFF);
            if (compressedFlag != 0) {
                throw new IOException("不支持压缩的 gRPC 响应（compressed-flag=" + compressedFlag + "）");
            }
            int payloadStart = offset + 5;
            long remaining = raw.length - (long) payloadStart;
            if (frameLength > remaining) {
                throw new IOException("gRPC 帧长度越界：声明 " + frameLength + " 字节，实际只剩 " + remaining + " 字节");
            }
            int payloadLength = (int) frameLength;
            merged.putAll(parseTranslateResponse(
                    Arrays.copyOfRange(raw, payloadStart, payloadStart + payloadLength)));
            offset = payloadStart + payloadLength;
        }
        // 尾部不足 5 字节时忽略：unary 调用正常只会收到完整帧，残帧没有可解析内容；
        // 这里不抛异常，避免把一次内容为空的正常响应误判成失败。
        return merged;
    }

    /** 按「先 bili-status-code、再 grpc-status」的口径检查错误码，非 "0" 即业务失败。 */
    private static void checkGrpcError(String biliStatusCode, String grpcStatus, String grpcMessage)
            throws IOException {
        StringBuilder codes = new StringBuilder();
        appendFailedCode(codes, "bili-status-code", biliStatusCode);
        appendFailedCode(codes, "grpc-status", grpcStatus);
        if (codes.length() == 0) {
            return;
        }
        String detail = (grpcMessage == null || grpcMessage.isEmpty()) ? "" : "，grpc-message=" + grpcMessage;
        throw new IOException("翻译失败（" + codes + detail + "）");
    }

    private static void appendFailedCode(StringBuilder out, String name, String value) {
        if (value == null) {
            return;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty() || "0".equals(trimmed)) {
            return;
        }
        if (out.length() > 0) {
            out.append(", ");
        }
        out.append(name).append('=').append(trimmed);
    }

    /**
     * 读 HTTP/2 trailers 并检查错误码。
     *
     * <p>文档的实测结论是「业务错误放在响应头里、成功时 grpc-status 在 trailers 里」，原来以为
     * OkHttp 拿不到 trailers；实际上 OkHttp 4.12 提供了 {@code Response.trailers()}，只要 body 已读尽
     * 就能拿到（HTTP/2 成功响应必然带 trailers）。所以这里在 body 读完后补查一次，覆盖
     * 「响应头干净、trailers 报错」这种边角情况。
     *
     * <p>trailers 是**补充**信息：拿不到（HTTP/1.1、连接提前关闭、实现变化）时静默跳过，
     * 不能因此把一次成功调用变成失败。
     */
    private static void checkGrpcErrorFromTrailers(Response response) throws IOException {
        Headers trailers;
        try {
            trailers = response.trailers();
        } catch (Exception ignored) {
            return;
        }
        checkGrpcError(trailers.get("bili-status-code"),
                trailers.get("grpc-status"), trailers.get("grpc-message"));
    }

    // ------------------------------------------------------------------
    // base64（RFC 4648）
    // ------------------------------------------------------------------

    /**
     * 标准 base64 编码（RFC 4648 字母表、不换行、末尾用 '=' 补位）。
     *
     * <p>为什么不直接用现成的：
     * <ul>
     *   <li>{@code java.util.Base64} 是 Android API 26 才有的类，minSdk 19 上用会
     *       {@code NoClassDefFoundError}；</li>
     *   <li>{@code android.util.Base64} 在纯 JVM 单测里只有会抛 {@code RuntimeException("Stub!")} 的桩，
     *       用它就没法单测 locale 头（而三个实测值是这条链路唯一能离线验证的锚点）；</li>
     * </ul>
     * 自己实现 30 行，就能在设备与单测里跑出完全相同的结果。
     */
    private static String base64(byte[] data) {
        StringBuilder out = new StringBuilder((data.length + 2) / 3 * 4);
        int i = 0;
        while (i + 3 <= data.length) {
            int b0 = data[i++] & 0xFF;
            int b1 = data[i++] & 0xFF;
            int b2 = data[i++] & 0xFF;
            out.append(BASE64_ALPHABET[b0 >>> 2]);
            out.append(BASE64_ALPHABET[((b0 & 0x03) << 4) | (b1 >>> 4)]);
            out.append(BASE64_ALPHABET[((b1 & 0x0F) << 2) | (b2 >>> 6)]);
            out.append(BASE64_ALPHABET[b2 & 0x3F]);
        }
        switch (data.length - i) {
            case 1:
                int b0 = data[i] & 0xFF;
                out.append(BASE64_ALPHABET[b0 >>> 2]);
                out.append(BASE64_ALPHABET[(b0 & 0x03) << 4]);
                out.append("==");
                break;
            case 2:
                int c0 = data[i] & 0xFF;
                int c1 = data[i + 1] & 0xFF;
                out.append(BASE64_ALPHABET[c0 >>> 2]);
                out.append(BASE64_ALPHABET[((c0 & 0x03) << 4) | (c1 >>> 4)]);
                out.append(BASE64_ALPHABET[(c1 & 0x0F) << 2]);
                out.append('=');
                break;
            default:
                break;
        }
        return out.toString();
    }
}
