package com.RobinNotBad.BiliClient.api;

import android.content.Context;
import android.os.Build;
import android.provider.Settings;
import android.text.TextUtils;

import com.RobinNotBad.BiliClient.BiliTerminal;
import com.RobinNotBad.BiliClient.BuildConfig;
import com.RobinNotBad.BiliClient.model.Announcement;
import com.RobinNotBad.BiliClient.model.ApiResult;
import com.RobinNotBad.BiliClient.model.FeedbackItem;
import com.RobinNotBad.BiliClient.model.FeedbackReply;
import com.RobinNotBad.BiliClient.util.IslandStatus;
import com.RobinNotBad.BiliClient.util.Logu;
import com.RobinNotBad.BiliClient.util.MsgUtil;
import com.RobinNotBad.BiliClient.util.NetWorkUtil;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;
import com.RobinNotBad.BiliClient.util.SettingsKeys;
import com.RobinNotBad.BiliClient.util.TerminalReportPrefs;
import com.RobinNotBad.BiliClient.util.TimeUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.UUID;

import okhttp3.Response;

/**
 * 自建终端服务（RE:哔哩终端自己的后端）的客户端入口。
 *
 * <p><b>与 {@link AppInfoApi} 的关系</b>：{@code AppInfoApi} 指向的是上游开发者
 * （RobinNotBad）的 {@code api.biliterminal.cn}，那是原作者的服务器。本 fork 的
 * 新主人另有自己的服务器，所以公告、反馈、遥测、崩溃报告走这里。上游公告接口
 * <b>客户端已不再直连</b>：上游公告由本服务端拉取、管理员在控制台逐条审核后透传
 * （{@code source=relay}），客户端只跟自建服务器要公告。
 *
 * <p><b>安全红线（务必保持）</b>：
 * <ul>
 *   <li>本类所有请求都必须显式带 {@link #headers()}，它是从
 *       {@link AppInfoApi#customHeaders} 复制的、<b>不含 Cookie</b> 的头。</li>
 *   <li>绝不可使用 {@link NetWorkUtil#getJson(String)} 或
 *       {@link NetWorkUtil#postJson(String, String)}：这两个单参重载用 {@code webHeaders}，
 *       而 {@code webHeaders} 的第一项就是 {@code Cookie}，会把 SESSDATA 发到自建服务器。</li>
 *   <li>自建接口只接受匿名 install_id，不携带任何账号凭据；UID 仅在用户于反馈页
 *       <b>显式勾选</b>后才附带（{@code mid}）。</li>
 * </ul>
 *
 * <p>服务端返回约定：成功 {@code code=0}，失败 {@code code!=0} 且带 {@code msg}，
 * HTTP 状态码一律 2xx（便于客户端统一按 body 判断）。
 */
public class TerminalApi {

    /** 自建服务基址。 */
    public static final String BASE_URL = "https://rebiliterminal.zsapp.asia/terminal";

    /** 反馈分类白名单，与服务端 `FEEDBACK_CATEGORIES` 保持一致。 */
    public static final String CATEGORY_BUG = "bug";
    public static final String CATEGORY_SUGGESTION = "suggestion";
    public static final String CATEGORY_CONTENT = "content";
    public static final String CATEGORY_PERFORMANCE = "performance";
    public static final String CATEGORY_OTHER = "other";

    public static final String[] CATEGORIES = {
            CATEGORY_BUG, CATEGORY_SUGGESTION, CATEGORY_CONTENT, CATEGORY_PERFORMANCE, CATEGORY_OTHER
    };

    /**
     * 内存档位（对比审计 P2-9 的隐私收敛部分）。
     *
     * <p>崩溃报告不再上报精确的 {@code ram_total}/{@code ram_avail} 字节数（那等于给机型
     * 再加一枚指纹），只报一个档位，0 表示取不到。档位数值**不要重排**：服务端一旦
     * 开始读，改数值会让历史数据对不上。
     */
    public static final int RAM_LEVEL_UNKNOWN = 0;

    /** &lt; 2 GiB。 */
    public static final int RAM_LEVEL_LOW = 1;

    /** 2 ~ 4 GiB。 */
    public static final int RAM_LEVEL_MID = 2;

    /** &gt;= 4 GiB。 */
    public static final int RAM_LEVEL_HIGH = 3;

    /**
     * 把总内存字节数折算成档位：按整 GiB 取整，{@code < 2} 低、{@code < 4} 中、否则高；
     * 取不到（{@code <= 0}）返回 {@link #RAM_LEVEL_UNKNOWN}。
     *
     * <p>纯函数，不碰 {@code android.*}，JVM 单测可直接覆盖。
     */
    public static int ramLevel(long totalMemBytes) {
        if (totalMemBytes <= 0) return RAM_LEVEL_UNKNOWN;
        long gib = totalMemBytes / (1024L * 1024L * 1024L);
        if (gib < 2) return RAM_LEVEL_LOW;
        if (gib < 4) return RAM_LEVEL_MID;
        return RAM_LEVEL_HIGH;
    }

    /**
     * 设备键的固定盐。改它等于把所有设备变成"新设备"（唯一设备数会翻一倍），**别随便改**。
     * 它不需要保密（就写在客户端里），作用只是让这个哈希不与别处对 ANDROID_ID 的哈希撞车。
     */
    public static final String DEVICE_KEY_SALT = "rebiliterminal-device-key-v1";

    /**
     * 当前隐私说明版本号。
     *
     * <p>只要用户可见的隐私文案（收集什么、不收集什么、怎么关）有实质变化，就必须 +1：
     * 老用户升级后会**重新看到一次**确认弹窗（见 {@link #needsPrivacyConsent()}）。
     * 改这个号的时候，`strings.xml` 里的 {@code privacy_consent_message}、
     * {@code about_to_uncle}、{@code text_setup_introduction} 三处要一起改。
     *
     * <p><b>v2（26.10.14）</b>：新增设备级去重键 {@link #getDeviceKey()}。此前那三处文案
     * 都写着"不读取 ANDROID_ID 等任何设备唯一标识"，而新方案正是要拿它派生出哈希，
     * 属于**实质变化**，所以必须升版并同步改掉那三句 —— 否则就是自相矛盾。
     *
     * <p><b>v3（26.10.16）</b>：新增「可附带应用日志」。崩溃页与反馈页各有一个勾选框，
     * 勾上才会把最近的应用日志（内存缓冲，已脱敏）随报告 / 反馈一起发送 —— 属于
     * **「收集什么」的实质变化**，所以必须升版并同步改那三处文案 + {@code desc_setting_crash_auto}。
     * 注意「已脱敏」是这句承诺的一部分：`util/LogSanitizer` 在日志**入缓冲之前**就把登录凭据等
     * 敏感参数的值替换成了占位符，文案里的这句话有代码兜着。
     */
    public static final int PRIVACY_VERSION = 3;

    private TerminalApi() {
    }

    // ==================== 纯函数：可在 JVM 单测里直接验证 ====================

    /** null 安全：服务端字段一律以字符串落库，null 会变成 JSON 的 null 而不是空串。 */
    static String safe(String value) {
        return value == null ? "" : value;
    }

    /** 把任意输入收敛到白名单分类，未知值一律归为 `other`——避免脏数据进库。 */
    public static String normalizeCategory(String category) {
        if (category == null) return CATEGORY_OTHER;
        for (String allowed : CATEGORIES) {
            if (allowed.equals(category)) return category;
        }
        return CATEGORY_OTHER;
    }

    /**
     * 崩溃报告里当天的日期键（yyyy-MM-dd，本地时区）——只用于「一天一次」的幂等判断。
     *
     * <p>放在这里而不是 {@code TelemetryReporter}，是为了让纯逻辑能在 JVM 单测里跑。
     */
    public static String dayKey(long millis) {
        return TimeUtil.formatDate(millis);
    }

    /**
     * 启动上报的载荷。
     *
     * <p>{@code device_key} 用于把同一台手机上的普通版与共存版合并成一台设备；
     * {@code is_coexist} 让后台能分开看两个版本的装机量。两者服务端都有默认值，
     * 所以老客户端不带它们也能正常计数。
     */
    public static JSONObject buildPingPayload(String installId, int versionCode, String versionName,
                                              boolean isBeta, int sdk, String brand,
                                              String device, String abi, String deviceKey,
                                              boolean isCoexist) throws JSONException {
        JSONObject data = new JSONObject();
        data.put("install_id", safe(installId));
        data.put("version_code", versionCode);
        data.put("version_name", safe(versionName));
        data.put("is_beta", isBeta);
        data.put("sdk", sdk);
        data.put("brand", safe(brand));
        data.put("device", safe(device));
        data.put("abi", safe(abi));
        data.put("device_key", safe(deviceKey));
        data.put("is_coexist", isCoexist);
        return data;
    }

    /**
     * 反馈载荷。
     *
     * <p>{@code log} 是用户**显式勾选**「附带应用日志」时才带的可选字段：内容是
     * {@code Logu.ringSnapshot()} 的内存日志快照（已在入缓冲前脱敏），服务端上限 200000 字符。
     * 未勾选时传 {@code null}/空串，这里经 {@link #safe(String)} 落成**空串而不是 JSON null**
     * ——服务端一律按字符串落库，字段缺失与 null 都会变成脏数据。老客户端不发这个字段，
     * 行为与以前完全一致。
     */
    public static JSONObject buildFeedbackPayload(String installId, String deviceKey, String category,
                                                  String content, String log, String contact, long mid,
                                                  int versionCode, String versionName, int sdk,
                                                  String brand, String device) throws JSONException {
        JSONObject data = new JSONObject();
        data.put("install_id", safe(installId));
        // 冗余一份设备键：后台要按设备统计反馈数，而 feedback 未必能和 installs 关联上
        data.put("device_key", safe(deviceKey));
        data.put("category", normalizeCategory(category));
        data.put("content", safe(content));
        // 附带的应用日志（可空，落成空串）；服务端对它是可选的
        data.put("log", safe(log));
        data.put("contact", safe(contact));
        data.put("mid", mid);
        data.put("version_code", versionCode);
        data.put("version_name", safe(versionName));
        data.put("sdk", sdk);
        data.put("brand", safe(brand));
        data.put("device", safe(device));
        return data;
    }

    /**
     * 追加回复的载荷（26.10.16 新增）。
     *
     * <p>**纯函数**，可在 JVM 单测里直接断言。
     *
     * <p>只有两个字段：反馈 id 与内容。身份**全部走请求头**（`X-Install-Id` / `X-Mid`），
     * 与「我的反馈」同一口径 —— 不在 body 里再塞一份 install_id，免得两处可能不一致，
     * 也免得又一条把身份写进请求体的路径。
     */
    public static JSONObject buildFeedbackReplyPayload(int feedbackId, String content)
            throws JSONException {
        JSONObject data = new JSONObject();
        data.put("id", feedbackId);
        data.put("content", safe(content));
        return data;
    }

    /**
     * 崩溃报告的完整载荷。
     *
     * <p>比上游 {@code /terminal/upload/stack} 多带了这些字段（用户要求「发的多一点」）：
     * 匿名 install_id、异常类名与消息（与堆栈分开）、出错线程、崩溃前的页面轨迹、
     * 系统版本 / product / model / ABI / 内存档位 / 运行时长。堆栈本身仍是主体。
     *
     * <p><b>内存只报档位</b>（对比审计 P2-9）：{@code ram_level} 取值见 {@link #ramLevel(long)}；
     * {@code ram_total}/{@code ram_avail} 仍然写进载荷，但**恒为 0**。服务端这两列本来就
     * {@code DEFAULT 0}，控制台遇到 0 会显示成「—」，所以不用改后端也不会凭空显示出错误的 MB 数。
     * 保留这两个字段名是为了不破老服务端的解析（它只按字段存在与否取值），
     * 等后端开始读 {@code ram_level} 后再删。
     *
     * <p>{@code is_coexist} 与启动上报同源：两个 flavor 在「崩溃」这个维度上也要能分开统计，
     * 缺了它就只剩 ping 那一路能区分版本。做成参数而不是在方法里直接读 {@code BuildConfig}，
     * 是为了让这枚纯函数能在 JVM 单测里钉住「true / false 都原样落进载荷」。
     */
    public static JSONObject buildCrashPayload(String installId, String deviceKey, long mid,
                                               String exception, String message, String thread,
                                               String stack, String log, String extra,
                                               int versionCode, String versionName, int sdk,
                                               String release, String brand, String device,
                                               String product, String model, String abi,
                                               int ramLevel,
                                               long uptimeSec, boolean isCoexist) throws JSONException {
        JSONObject data = new JSONObject();
        data.put("install_id", safe(installId));
        data.put("device_key", safe(deviceKey));
        data.put("mid", mid);
        data.put("exception", safe(exception));
        data.put("message", safe(message));
        data.put("thread", safe(thread));
        data.put("stack", safe(stack));
        data.put("log", safe(log));
        data.put("extra", safe(extra));
        data.put("version_code", versionCode);
        data.put("version_name", safe(versionName));
        data.put("sdk", sdk);
        data.put("release", safe(release));
        data.put("brand", safe(brand));
        data.put("device", safe(device));
        data.put("product", safe(product));
        data.put("model", safe(model));
        data.put("abi", safe(abi));
        data.put("ram_level", ramLevel);
        // 精确内存已停止采集；字段留着并置 0，避免老服务端 / 控制台读到"字段缺失"
        data.put("ram_total", 0L);
        data.put("ram_avail", 0L);
        data.put("uptime_sec", uptimeSec);
        data.put("is_coexist", isCoexist);
        return data;
    }

    /**
     * 兼容重载：{@code is_coexist} 取本包 flavor 的编译期常量（与 {@link #ping()} 里
     * 传给 {@code buildPingPayload} 的 {@code BuildConfig.COEXIST} 同源）。
     *
     * <p>保留它是为了让既有调用点与 JVM 单测不必跟着改签名；**新代码应显式传
     * {@code is_coexist}**，单测也才能覆盖两个分支。
     */
    public static JSONObject buildCrashPayload(String installId, String deviceKey, long mid,
                                               String exception, String message, String thread,
                                               String stack, String log, String extra,
                                               int versionCode, String versionName, int sdk,
                                               String release, String brand, String device,
                                               String product, String model, String abi,
                                               int ramLevel,
                                               long uptimeSec) throws JSONException {
        return buildCrashPayload(installId, deviceKey, mid, exception, message, thread, stack, log,
                extra, versionCode, versionName, sdk, release, brand, device, product, model,
                abi, ramLevel, uptimeSec, BuildConfig.COEXIST);
    }

    /**
     * 解析公告数组。服务端 {@code ctime} 是 **epoch 秒**，这里换算成毫秒后交给
     * {@link TimeUtil#formatDate(long)}。
     *
     * <p>缺 id 的条目直接跳过：没有 id 就无法参与「已读最大 id」的差量计算。
     *
     * <p>{@code source} 是服务端返回的来源标记：{@code local} 为本项目服务端发布，
     * {@code relay} 为管理员在控制台审核后从上游（原版哔哩终端）透传；旧版服务端
     * 不返回该字段时按 {@code local} 处理。
     */
    public static ArrayList<Announcement> parseAnnouncements(JSONArray data) {
        ArrayList<Announcement> list = new ArrayList<>();
        if (data == null) return list;
        for (int i = 0; i < data.length(); i++) {
            JSONObject item = data.optJSONObject(i);
            if (item == null || !item.has("id")) continue;
            Announcement announcement = new Announcement();
            announcement.id = item.optInt("id", 0);
            announcement.ctime = TimeUtil.formatDate(item.optLong("ctime", 0) * 1000);
            announcement.title = item.optString("title", "");
            announcement.content = item.optString("content", "");
            announcement.source = item.optString("source", "local");
            list.add(announcement);
        }
        return list;
    }

    /**
     * 解析「我的反馈」列表。{@code data} 形如 {@code {"items":[...],"total":1}}。
     *
     * <p>纯函数：不碰 Android 运行时，可在 JVM 单测里直接验证。{@code items} 缺失、为空或
     * 条目是 JSON {@code null} 都安全跳过。
     *
     * <p>{@code ctime} 是 **epoch 秒**，换算成毫秒后交给 {@link TimeUtil#formatDateTime(long)}，
     * 与 {@link #parseAnnouncements(JSONArray)} 同一口径。{@code replies} 可能缺失、可能是空数组，
     * 也可能多条——一律收敛成非空的列表。
     */
    public static ArrayList<FeedbackItem> parseMyFeedback(JSONObject data) {
        ArrayList<FeedbackItem> list = new ArrayList<>();
        if (data == null) return list;
        JSONArray items = data.optJSONArray("items");
        if (items == null) return list;
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) continue;

            ArrayList<FeedbackReply> replies = new ArrayList<>();
            JSONArray replyArray = item.optJSONArray("replies");
            if (replyArray != null) {
                for (int j = 0; j < replyArray.length(); j++) {
                    JSONObject reply = replyArray.optJSONObject(j);
                    if (reply == null) continue;
                    replies.add(new FeedbackReply(
                            reply.optInt("id", 0),
                            reply.optString("title", ""),
                            reply.optString("content", ""),
                            TimeUtil.formatDateTime(reply.optLong("ctime", 0) * 1000),
                            // 26.10.16：一条反馈下面现在有两侧的消息（开发者回复 / 用户追加说明）。
                            // **旧服务端不返回这个字段**，取到空串 -> isFromUser() 为 false，
                            // 正好等于升级前「只可能有开发者回复」的事实，不需要额外兼容分支。
                            reply.optString("author", "")));
                }
            }

            list.add(new FeedbackItem(
                    item.optInt("id", 0),
                    item.optString("category", ""),
                    item.optString("content", ""),
                    item.optString("contact", ""),
                    TimeUtil.formatDateTime(item.optLong("ctime", 0) * 1000),
                    item.optInt("handled", 0) != 0,
                    replies));
        }
        return list;
    }

    /**
     * 「我的反馈」请求是否应附带 {@code X-Mid}：**两个条件都满足才带**——
     * 用户勾选了「附带账号 ID」（{@link SettingsKeys#FEEDBACK_ATTACH_MID}）**且**确实已登录（有 UID）。
     *
     * <p>与提交反馈同一口径（见 {@code FeedbackActivity} 里 {@code if (attachMid) ... else 0}）：
     * 没勾选就只认本机，未登录也没有 UID 可带。
     *
     * <p>抽成独立函数是为了让这条隐私开关能在 JVM 单测里钉住：它只读 SharedPreferences
     * （单测注入 {@code FakeSharedPreferences} 即可），不碰 Android 运行时。
     */
    public static boolean shouldAttachMid() {
        if (!SharedPreferencesUtil.getBoolean(SettingsKeys.FEEDBACK_ATTACH_MID, false)) return false;
        return SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0L) > 0;
    }

    // ==================== install_id ====================

    /**
     * 取（必要时生成）匿名安装标识：一个随机 UUID。
     *
     * <p>与 B 站账号无关、不含任何设备指纹，卸载重装即换新。首次调用时写入
     * {@link SettingsKeys#TELEMETRY_INSTALL_ID}。
     */
    public static String getInstallId() {
        String id = SharedPreferencesUtil.getString(SettingsKeys.TELEMETRY_INSTALL_ID, "");
        if (TextUtils.isEmpty(id)) {
            id = UUID.randomUUID().toString().replace("-", "");
            SharedPreferencesUtil.putString(SettingsKeys.TELEMETRY_INSTALL_ID, id);
        }
        return id;
    }

    /**
     * 设备级去重键：{@code sha256(ANDROID_ID + 固定盐)} 的前 32 位。
     *
     * <p><b>为什么需要它</b>：普通版与共存版的包名不同，{@link #getInstallId()} 存在各自包内的
     * SharedPreferences 里，天然是两个值。只靠 install_id，一台手机装两个版本就会被算成两台设备。
     *
     * <p><b>为什么它有效</b>：Android 8.0+ 的 ANDROID_ID 按 (应用签名, 用户, 设备) 派生，
     * 而两个 flavor 用的是**同一个签名**，所以取值相同；8.0 以下它是设备级唯一值，同样相同。
     * <b>若将来两个 flavor 改用不同签名，这个去重会立刻失效</b> —— 换签名前先想清楚。
     *
     * <p><b>隐私</b>：原始 ANDROID_ID 只在内存里过一下，**绝不上报、也不落盘**，存下来的只有
     * 加盐哈希。取不到就返回空串，服务端会回落到 install_id 计数（不会把所有人挤进同一个空分组）。
     * 恢复出厂或切换系统用户会得到新值，此时视为一台新设备，可接受。
     */
    public static String getDeviceKey() {
        String cached = SharedPreferencesUtil.getString(SettingsKeys.TELEMETRY_DEVICE_KEY, "");
        if (!TextUtils.isEmpty(cached)) return cached;
        String raw = "";
        try {
            Context context = BiliTerminal.context;
            if (context != null) {
                raw = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
            }
        } catch (Exception e) {
            // 某些定制 ROM 会抛异常；拿不到就算了，统计不能因此崩
            Logu.w("read ANDROID_ID failed: " + e);
        }
        if (TextUtils.isEmpty(raw)) return "";
        String key = hashDeviceKey(raw);
        if (TextUtils.isEmpty(key)) return "";
        SharedPreferencesUtil.putString(SettingsKeys.TELEMETRY_DEVICE_KEY, key);
        return key;
    }

    /**
     * 把原始 ANDROID_ID 折算成设备键：{@code sha256(id + 盐)} 的前 32 位十六进制。
     *
     * <p>抽成独立方法，是为了让 JVM 单测能直接验证它的**形状**（确实是哈希、长度固定、
     * 同输入稳定复现），而不用碰 Android 运行时。因此这里**不得**使用任何 {@code android.*} 类
     * —— 单测里那些类都是 not mocked，一碰就炸。
     */
    static String hashDeviceKey(String rawAndroidId) {
        if (rawAndroidId == null || rawAndroidId.length() == 0) return "";
        String key = sha256(rawAndroidId + DEVICE_KEY_SALT);
        return key.length() > 32 ? key.substring(0, 32) : key;
    }

    /** 十六进制小写 SHA-256；失败返回空串（调用方按"没有设备键"处理）。 */
    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            Logu.w("sha256 failed: " + e);
            return "";
        }
    }

    /**
     * 匿名统计是否开启：**必须先就当前版本隐私说明点过「同意」**，未同意一律不上报。
     *
     * <p>默认值与读取落点统一在 {@link TerminalReportPrefs}，这里只叠加隐私同意这道总闸。
     */
    public static boolean isTelemetryEnabled() {
        return hasPrivacyConsent() && TerminalReportPrefs.isTelemetrySwitchOn();
    }

    /** 崩溃自动上报是否开启；同样以隐私说明的「同意」为前置条件，默认值见 {@link TerminalReportPrefs}。 */
    public static boolean isCrashReportAutoEnabled() {
        return hasPrivacyConsent() && TerminalReportPrefs.isCrashReportSwitchOn();
    }

    // ==================== 隐私说明再确认 ====================

    /**
     * 是否已经就**当前版本**的隐私说明点过「同意」。
     *
     * <p>这是所有上报的唯一总闸：{@link #isTelemetryEnabled()} 与
     * {@link #isCrashReportAutoEnabled()} 都先问它，所以「没同意就绝不上报」这件事
     * 不依赖任何一个调用点是否记得判断。
     */
    public static boolean hasPrivacyConsent() {
        return SharedPreferencesUtil.getInt(SettingsKeys.PRIVACY_CONSENT_VERSION, 0) >= PRIVACY_VERSION;
    }

    /** 是否还需要弹一次隐私说明确认（当前版本还没表过态）。 */
    public static boolean needsPrivacyConsent() {
        return SharedPreferencesUtil.getInt(SettingsKeys.PRIVACY_PROMPTED_VERSION, 0) < PRIVACY_VERSION;
    }

    /**
     * 弹窗里点了「同意」：记下同意，并把两个上报开关复位为开。
     *
     * <p>为什么要复位开关而不是「保持用户原来的选择」：能走到这个弹窗，
     * 说明用户此前要么没表过态、要么上一版明确的选过「不同意」（那会把两个开关关掉）。
     * 两种情况下开关的当前值都不是「用户在有同意基础的前提下主动关的」，
     * 所以这里按默认值复位是符合直觉的——点完同意却什么都没发生才是坑。
     * 用户之后再想关，开关就在同一页，随时可关。
     */
    public static void acceptPrivacyConsent() {
        SharedPreferencesUtil.putInt(SettingsKeys.PRIVACY_CONSENT_VERSION, PRIVACY_VERSION);
        SharedPreferencesUtil.putInt(SettingsKeys.PRIVACY_PROMPTED_VERSION, PRIVACY_VERSION);
        SharedPreferencesUtil.putBoolean(SettingsKeys.TELEMETRY_ENABLE, true);
        SharedPreferencesUtil.putBoolean(SettingsKeys.CRASH_REPORT_AUTO, true);
    }

    /**
     * 弹窗里点了「不同意」：只记「已问过」，并把两个上报开关关掉。
     *
     * <p>记「已问过」是为了不再反复弹窗（用户已经明确回答过了）；
     * 同意版本号保持原值（0 或上一版），于是 {@link #hasPrivacyConsent()} 仍为 false——
     * 这就是「可以继续用，但不上报」的全部实现。App 不会因为不同而被拦在闪屏外。
     */
    public static void declinePrivacyConsent() {
        SharedPreferencesUtil.putInt(SettingsKeys.PRIVACY_PROMPTED_VERSION, PRIVACY_VERSION);
        SharedPreferencesUtil.putBoolean(SettingsKeys.TELEMETRY_ENABLE, false);
        SharedPreferencesUtil.putBoolean(SettingsKeys.CRASH_REPORT_AUTO, false);
    }

    /**
     * 用户在「设置 - 关于与帮助」里**主动打开**统计或崩溃上报开关时调用。
     *
     * <p>主动打开就是一个明确的意思表示，等同于重新同意；这里只落同意版本号，
     * **不碰任何开关**（尤其是不能顺手把另一个开关也打开——用户只点了一个）。
     */
    public static void recordPrivacyConsentFromSettings() {
        SharedPreferencesUtil.putInt(SettingsKeys.PRIVACY_CONSENT_VERSION, PRIVACY_VERSION);
        SharedPreferencesUtil.putInt(SettingsKeys.PRIVACY_PROMPTED_VERSION, PRIVACY_VERSION);
    }

    // ==================== 网络 ====================

    /**
     * 自建接口专用请求头：{@link AppInfoApi#customHeaders}（无 Cookie）的副本，
     * 已同意当前版本隐私说明时额外带 {@code X-Install-Id}。
     *
     * <p>用副本而不是直接改 {@code customHeaders}，是因为那个列表同时被上游接口使用，
     * 往共享静态列表里加项会改变上游请求的内容。
     */
    public static ArrayList<String> headers() {
        ArrayList<String> list = new ArrayList<>(AppInfoApi.customHeaders);
        // 安装标识**只在已同意当前版本隐私说明之后**才带：它是一枚可长期追踪的随机 ID，
        // 而 `headers()` 还服务着「拉公告」这条**同意之前**就会跑的链路（闪屏一进来就调），
        // 不该在用户还没表态时就把 ID 送给服务器。
        // 判据 [hasPrivacyConsent] 只读 SharedPreferences，不取 Context、不发请求，故无递归风险。
        // 反馈相关接口靠 [feedbackHeaders] 单独补回这个头（用户主动提交，归属认人必需）。
        if (hasPrivacyConsent()) {
            list.add("X-Install-Id");
            list.add(getInstallId());
        }
        return list;
    }

    private static JSONObject post(String path, JSONObject body) throws IOException, JSONException {
        return postWithHeaders(path, body, headers());
    }

    /**
     * 用**指定**的请求头发 POST。
     *
     * <p>只有「我的反馈」相关接口需要它：那几个得走 {@link #feedbackHeaders()} 才能在用户
     * 勾选了「附带账号 ID」时带上 `X-Mid`（换设备后仍能操作自己发过的反馈）。
     *
     * <p><b>不要为了省事把 mid 加进通用 {@link #headers()}</b>：那等于对所有接口都默认带上
     * UID，与「默认不带」的隐私口径直接相悖。
     */
    private static JSONObject postWithHeaders(String path, JSONObject body,
                                              ArrayList<String> customHeaders)
            throws IOException, JSONException {
        Response response = NetWorkUtil.postJson(BASE_URL + path, body.toString(), customHeaders);
        if (response.body() == null) throw new IOException("empty response body");
        return new JSONObject(response.body().string());
    }

    private static JSONObject get(String path) throws IOException, JSONException {
        return NetWorkUtil.getJson(BASE_URL + path, headers());
    }

    /**
     * 设备首选 ABI（例：armeabi-v7a / arm64-v8a）。
     *
     * <p><b>为什么不能直接读 {@link Build#SUPPORTED_ABIS}</b>：那个静态数组字段是 API 21 才
     * 有的，minSdk 19 的设备上一执行 getstatic 就抛 {@link NoSuchFieldError}。它是
     * {@link Error} 而不是 {@link Exception}，外面套 {@code catch (Exception e)} 根本兜不住，
     * 必须先过 {@code SDK_INT} 闸。低版本回落到 API 1 就存在的、已废弃的
     * {@link Build#CPU_ABI}。
     */
    public static String firstAbi() {
        String[] abis = supportedAbis();
        return abis.length > 0 ? abis[0] : "";
    }

    /**
     * 同 {@link #firstAbi()}，但返回完整 ABI 列表（顺序就是设备优先顺序）。
     *
     * <p>见 {@link #firstAbi()} 里对 API 21 门槛的说明。
     */
    public static String[] supportedAbis() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            String[] abis = Build.SUPPORTED_ABIS;
            if (abis != null && abis.length > 0) return abis;
        }
        String cpuAbi = Build.CPU_ABI;
        return cpuAbi != null ? new String[]{cpuAbi} : new String[0];
    }

    private static void requireOk(JSONObject result) throws Exception {
        if (result.optInt("code", -1) != 0) {
            throw new Exception("错误：" + result.optString("msg", "unknown"));
        }
    }

    /**
     * 上报一次启动。服务端按 (install_id, 当天) 去重，因此一天内多次调用不会重复计数。
     *
     * @return 服务端是否接受（调用方通常不需要关心）
     */
    public static boolean ping() {
        try {
            JSONObject payload = buildPingPayload(
                    getInstallId(),
                    BuildConfig.VERSION_CODE,
                    BuildConfig.VERSION_NAME,
                    BuildConfig.BETA,
                    Build.VERSION.SDK_INT,
                    Build.BRAND,
                    Build.DEVICE,
                    firstAbi(),
                    getDeviceKey(),
                    BuildConfig.COEXIST);
            return post("/telemetry/ping", payload).optInt("code", -1) == 0;
        } catch (Exception e) {
            // 遥测是尽力而为：任何失败都不能影响用户使用，也不能弹窗打扰。
            Logu.w("telemetry ping failed: " + e);
            return false;
        }
    }

    /**
     * 提交反馈。
     *
     * @param log 用户显式勾选「附带应用日志」时传 {@code Logu.ringSnapshot()}，否则传空串/null
     * @param mid 用户显式勾选「附带我的账号」时传 UID，否则传 0
     * @return {@code ApiResult.code} 为服务端返回的反馈 id（成功）或负的错误码，{@code message} 为错误原因
     */
    public static ApiResult submitFeedback(String category, String content, String log,
                                           String contact, long mid) {
        try {
            JSONObject payload = buildFeedbackPayload(
                    getInstallId(), getDeviceKey(), category, content, log, contact, mid,
                    BuildConfig.VERSION_CODE, BuildConfig.VERSION_NAME,
                    Build.VERSION.SDK_INT, Build.BRAND, Build.DEVICE);
            JSONObject result = post("/feedback/submit", payload);
            if (result.optInt("code", -1) != 0) {
                return new ApiResult(-1, result.optString("msg", "unknown"));
            }
            return new ApiResult(result.optInt("id", -1), "");
        } catch (IOException e) {
            return new ApiResult(-2, "网络连接失败");
        } catch (JSONException e) {
            return new ApiResult(-3, "服务器返回了无法解析的内容");
        }
    }

    /**
     * 往**自己已提交的**反馈下面追加一条说明（26.10.16 新增）。
     *
     * <p>服务端会校验归属：不是自己的反馈返回 `code = 404`（与「不存在」同一个码，
     * 见服务端 `feedback_reply` 的注释）。追加成功后服务端会把该反馈**打回「待受理」**
     * （GitHub 的 reopen 语义），所以调用方要把页面上的状态同步改掉。
     *
     * <p>必须走 {@link #feedbackHeaders()}：用户勾了「附带账号 ID」时才能靠 `X-Mid`
     * 在换设备后继续回复自己发过的反馈。
     *
     * @return 成功时 {@code code} 是新消息的 id；失败时 {@code message} 是服务端或本地的原因
     */
    public static ApiResult submitFeedbackReply(int feedbackId, String content) {
        try {
            JSONObject payload = buildFeedbackReplyPayload(feedbackId, content);
            JSONObject result = postWithHeaders("/feedback/reply", payload, feedbackHeaders());
            if (result.optInt("code", -1) != 0) {
                return new ApiResult(-1, result.optString("msg", "unknown"));
            }
            return new ApiResult(result.optInt("id", -1), "");
        } catch (IOException e) {
            return new ApiResult(-2, "网络连接失败");
        } catch (JSONException e) {
            return new ApiResult(-3, "服务器返回了无法解析的内容");
        }
    }

    /**
     * 上传崩溃报告。成功时 {@code ApiResult.code} 就是给用户看的报错编号。
     */
    public static ApiResult uploadCrash(JSONObject payload) {
        try {
            JSONObject result = post("/upload/stack", payload);
            if (result.optInt("code", -1) != 0) {
                return new ApiResult(-1, result.optString("msg", "unknown"));
            }
            return new ApiResult(result.optInt("id", -1), "");
        } catch (IOException e) {
            return new ApiResult(-2, "网络连接失败");
        } catch (JSONException e) {
            return new ApiResult(-3, "服务器返回了无法解析的内容");
        }
    }

    /**
     * 启动时拉取自建公告源的增量并弹窗。
     *
     * <p>上游公告不再由客户端直连：服务端会先拉取上游、等管理员逐条审核通过后再放进
     * 这个接口（{@code source=relay}），所以本方法拿到的就是「本项目公告 + 已审核透传
     * 公告」的完整可见集。{@link SettingsKeys#TERMINAL_ANNOUNCEMENT_LAST} 仍是唯一的
     * 已读计数，透传公告沿用服务端分配的本地 id，与自建公告共处同一 id 空间。
     */
    public static void checkAnnouncement() throws Exception {
        String url = "/announcement/get_list?from="
                + SharedPreferencesUtil.getInt(SettingsKeys.TERMINAL_ANNOUNCEMENT_LAST, -1);
        JSONObject result = get(url);
        requireOk(result);

        ArrayList<Announcement> list = parseAnnouncements(result.optJSONArray("data"));
        boolean fresh = false;
        for (Announcement announcement : list) {
            if (SharedPreferencesUtil.getInt(SettingsKeys.TERMINAL_ANNOUNCEMENT_LAST, 0) < announcement.id) {
                SharedPreferencesUtil.putInt(SettingsKeys.TERMINAL_ANNOUNCEMENT_LAST, announcement.id);
                fresh = true;
            }
            MsgUtil.showText(announcement.title, announcement.content, announcement.isRelayed());
        }
        // 有新公告时给顶栏灵动岛留一个 10 秒的「公告」事件；启动线程只置标记，不碰 UI。
        if (fresh) {
            IslandStatus.markAnnouncement();
        }
    }

    /** 公告列表（全量，倒序；含已审核透传的上游公告）。 */
    public static ArrayList<Announcement> getOwnAnnouncementList() throws Exception {
        JSONObject result = get("/announcement/get_list");
        requireOk(result);
        return parseAnnouncements(result.optJSONArray("data"));
    }

    /**
     * 「我的反馈」列表。
     *
     * <p>身份靠 {@code X-Install-Id}（{@link #feedbackHeaders()} 必带）；登录用户的 UID 只在用户勾选
     * 「附带账号 ID」时才通过 {@code X-Mid} 头附带，与提交反馈同一口径——没勾选就只认本机。
     *
     * <p><b>必须显式传 {@link #feedbackHeaders()}</b>，绝不可改用
     * {@link NetWorkUtil#getJson(String)} 单参重载：那个重载用 {@code webHeaders}，
     * 第一项就是 {@code Cookie}，会把 SESSDATA 发到自建服务器。
     */
    public static ArrayList<FeedbackItem> getMyFeedback() throws Exception {
        JSONObject result = NetWorkUtil.getJson(BASE_URL + "/feedback/mine", feedbackHeaders());
        requireOk(result);
        return parseMyFeedback(result.optJSONObject("data"));
    }

    /**
     * 「我的反馈」专用请求头：{@link #headers()} 的副本，保证带 {@code X-Install-Id}，
     * 按需追加 {@code X-Mid}。
     *
     * <p>{@link #headers()} 只在用户同意隐私说明后才带安装标识（那是统计口径），但反馈的
     * 归属必须靠它认人，所以这里无条件补上——见方法体注释。
     *
     * <p>mid 走请求头而不是查询串——uvicorn 的 access log 会记完整 URL，放查询串等于把 UID
     * 写进服务器日志文件。是否附带由 {@link #shouldAttachMid()} 决定。
     */
    public static ArrayList<String> feedbackHeaders() {
        ArrayList<String> list = headers();
        // 反馈是用户**主动发起**的交互，不是后台统计：反馈的归属、「我的反馈」列表和追加回复
        // 都靠这个头认人（服务端不认 Cookie）。因此未同意隐私说明时也补上——它和提交反馈的
        // body 里的 install_id 是同一个值，去掉头并不能让这次提交变得匿名，只会让用户
        // 看不到自己刚发的反馈。
        if (!hasPrivacyConsent()) {
            list.add("X-Install-Id");
            list.add(getInstallId());
        }
        if (shouldAttachMid()) {
            list.add("X-Mid");
            list.add(String.valueOf(SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0L)));
        }
        return list;
    }
}
