package com.RobinNotBad.BiliClient.api;

import com.RobinNotBad.BiliClient.model.Emote;
import com.RobinNotBad.BiliClient.model.EmotePackage;
import com.RobinNotBad.BiliClient.util.NetWorkUtil;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class EmoteApi {
    public static final String BUSINESS_REPLY = "reply";
    public static final String BUSINESS_DYNAMIC = "dynamic";
    /**
     * 私信表情面板。
     *
     * <p>`business=im` 是 App 端私信在用的面板；网页 Cookie 登录不保证有权限（可能回空包或非 0 码），
     * 所以调用方要用 {@link #getEmotesWithFallback(String)}，别直接调 {@link #getEmotes(String)}。
     */
    public static final String BUSINESS_IM = "im";

    /**
     * 获取表情包面板内容
     *
     * @param business business
     * @return 获取到的面板内容（表情包）
     */
    public static List<EmotePackage> getEmotes(String business) throws JSONException, IOException {
        String url = "https://api.bilibili.com/x/emote/user/panel/web?business=" + business;
        JSONObject emotePackages = NetWorkUtil.getJson(url, NetWorkUtil.webHeaders);
        if (emotePackages.getInt("code") != 0)
            throw new JSONException(emotePackages.getString("message"));

        JSONObject data = emotePackages.optJSONObject("data");
        if (data != null) {
            return analyzeEmotePackages(data.optJSONArray("packages"));
        }
        return null;
    }

    // TODO 表情包管理页

    /**
     * 纯函数：面板拉空（或不允许）时是否要回落到 reply 面板。
     *
     * <p>抽出来是为了能被 JVM 单测覆盖——「什么时候回落」是本功能唯一的判定逻辑，
     * 真机上不该靠试错确认。
     *
     * @param business      请求的 business
     * @param packageCount  拉到的表情包数量（&lt;= 0 表示空包）
     * @return true 表示应该改用 {@link #BUSINESS_REPLY} 再拉一次
     */
    public static boolean shouldFallbackToReply(String business, int packageCount) {
        if (BUSINESS_REPLY.equals(business)) return false;
        return packageCount <= 0;
    }

    /**
     * 带回落的表情面板获取：先按 {@code business} 拉，空包或该 business 不被允许（非 0 码）时回落到 reply。
     *
     * <p>回落只在「面板为空 / 服务端拒绝」时发生；网络异常（IOException）照常向上抛，
     * 让调用方显示网络错误——用 reply 面板掩盖网络问题反而更难排查。
     *
     * @param business 业务名，私信传 {@link #BUSINESS_IM}
     * @return 表情包列表；回落到 reply 也拿不到时返回空列表（不返回 null，调用方少一层判空）
     */
    public static List<EmotePackage> getEmotesWithFallback(String business) throws JSONException, IOException {
        List<EmotePackage> primary = null;
        JSONException primaryError = null;
        try {
            primary = getEmotes(business);
        } catch (JSONException e) {
            // 非 0 码（面板不可用 / 未登录表情权限）才回落；IOException 不在这里兜
            primaryError = e;
        }
        int size = primary == null ? 0 : primary.size();
        if (!shouldFallbackToReply(business, size)) return primary != null ? primary : new ArrayList<>();

        List<EmotePackage> fallback = getEmotes(BUSINESS_REPLY);
        if (fallback != null && !fallback.isEmpty()) return fallback;
        // 连 reply 也没有：主面板如果本来有数据（只是空包）就返回空，否则把主面板的错误抛给调用方
        if (primary != null) return primary;
        if (primaryError != null) throw primaryError;
        return new ArrayList<>();
    }

    /**
     * 获取“使用中”的表情包
     *
     * @param business business
     * @return 获取到的表情包
     */
    public static List<EmotePackage> getInUsePackages(String business) throws JSONException, IOException {
        String url = "https://api.bilibili.com/bapis/main.community.interface.emote.EmoteService/InUsePackages" + new NetWorkUtil.FormData().setUrlParam(true)
                .put("business", business)
                .put("csrf", NetWorkUtil.currentCsrf());
        JSONObject emotePackages = NetWorkUtil.getJson(url, NetWorkUtil.webHeaders);
        if (emotePackages.getInt("code") != 0)
            throw new JSONException(emotePackages.getString("message"));

        JSONObject data = emotePackages.optJSONObject("data");
        if (data != null) {
            return analyzeEmotePackages(data.optJSONArray("packages"));
        }
        return null;
    }

    /**
     * 全部表情
     */
    public static final int PACKAGES_TYPE_ALL = 0;
    /**
     * 装扮商城
     */
    public static final int PACKAGES_TYPE_SHOP = 1;
    /**
     * 会员专属
     */
    public static final int PACKAGES_TYPE_VIP = 2;
    /**
     * 包月充电
     */
    public static final int PACKAGES_TYPE_MONTH_CHARGE = 3;

    /**
     * 获取“待使用”的表情包
     *
     * @param business business
     * @param type     见PACKAGES_TYPE常量
     * @param pn       page页数
     * @return 获取到的表情包
     */
    public static List<EmotePackage> getMyPackages(String business, int type, int pn) throws JSONException, IOException {
        String url = "https://api.bilibili.com/bapis/main.community.interface.emote.EmoteService/MyPackages" + new NetWorkUtil.FormData().setUrlParam(true)
                .put("business", business)
                .put("csrf", NetWorkUtil.currentCsrf())
                .put("pn", pn)
                .put("ps", 12)
                .put("type", type);
        JSONObject emotePackages = NetWorkUtil.getJson(url, NetWorkUtil.webHeaders);
        if (emotePackages.getInt("code") != 0)
            throw new JSONException(emotePackages.getString("message"));

        JSONObject data = emotePackages.optJSONObject("data");
        if (data != null) {
            return analyzeEmotePackages(data.optJSONArray("packages"));
        }
        return null;
    }

    /**
     * 获取”更多表情“中的内容，或许不会用到，因为其中的内容都是需要前往购买/获取说明页的。
     *
     * @param business business
     * @param pn       page页数
     * @param search   搜索关键词
     * @return 获取到的表情包
     */
    public static List<EmotePackage> getAllPackages(String business, int pn, String search) throws JSONException, IOException {
        String url = "https://api.bilibili.com/bapis/main.community.interface.emote.EmoteService/AllPackages" + new NetWorkUtil.FormData().setUrlParam(true)
                .put("business", business)
                .put("csrf", NetWorkUtil.currentCsrf())
                .put("pn", pn)
                .put("ps", 12)
                .put("search", search);
        JSONObject emotePackages = NetWorkUtil.getJson(url, NetWorkUtil.webHeaders);
        if (emotePackages.getInt("code") != 0)
            throw new JSONException(emotePackages.getString("message"));

        JSONObject data = emotePackages.optJSONObject("data");
        if (data != null) {
            return analyzeEmotePackages(data.optJSONArray("packages"));
        }
        return null;
    }

    /**
     * 添加/移除表情包
     *
     * @param business business
     * @param isAdd    是否为添加，否则为移除
     * @param ids      要添加/移除的表情包id（们）
     * @return 返回值
     */
    public static int setPackage(String business, boolean isAdd, int... ids) throws JSONException, IOException {
        StringBuilder idsSb = new StringBuilder();
        boolean flag = true;
        for (int i : ids) {
            if (flag) {
                flag = false;
            } else {
                idsSb.append(",");
            }
            idsSb.append(i);
        }
        String url = "https://api.bilibili.com/bapis/main.community.interface.emote.EmoteService/AllPackages" + new NetWorkUtil.FormData().setUrlParam(true)
                .put("business", business)
                .put("csrf", NetWorkUtil.currentCsrf())
                .put("ids", idsSb.toString())
                .put("type", isAdd ? 0 : 1);
        JSONObject emotePackages = NetWorkUtil.getJson(url, NetWorkUtil.webHeaders);
        return emotePackages.getInt("code");
    }

    /**
     * 获取该 business 下全部表情的文本形式（如 "[doge]"）集合。
     *
     * <p>发布动态时用它把正文里的 [xxx] 识别成 type 9 表情节点；拉取失败时返回空集合，
     * 发布退化成纯文本而不是直接报错——表情只是锦上添花，不该挡住发送。
     *
     * @param business business，见 {@link #BUSINESS_DYNAMIC} / {@link #BUSINESS_REPLY}
     * @return 表情名与别名的集合，可能为空
     */
    public static Set<String> getEmoteTexts(String business) {
        Set<String> texts = new HashSet<>();
        try {
            List<EmotePackage> packages = getEmotes(business);
            if (packages != null) {
                for (EmotePackage emotePackage : packages) {
                    if (emotePackage == null || emotePackage.emotes == null) continue;
                    for (Emote emote : emotePackage.emotes) {
                        if (emote == null) continue;
                        if (emote.name != null && !emote.name.isEmpty()) texts.add(emote.name);
                        if (emote.alias != null && !emote.alias.isEmpty()) texts.add(emote.alias);
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return texts;
    }

    public static List<EmotePackage> analyzeEmotePackages(JSONArray packages) throws JSONException {
        if (packages == null) return null;
        List<EmotePackage> result = new ArrayList<>();
        for (int i = 0; i < packages.length(); i++) {
            EmotePackage emotePackage = new EmotePackage();
            JSONObject curObj = packages.getJSONObject(i);
            emotePackage.id = curObj.getInt("id");
            emotePackage.text = curObj.getString("text");
            emotePackage.url = curObj.getString("url");
            emotePackage.type = curObj.getInt("type");
            emotePackage.attr = curObj.getInt("attr");
            JSONObject meta = curObj.optJSONObject("meta");
            if (meta != null) {
                emotePackage.size = meta.optInt("size", 1);
                emotePackage.item_id = meta.optInt("item_id", -1);
            }
            JSONObject flags = curObj.optJSONObject("flags");
            if (flags != null) {
                emotePackage.permanent = flags.optBoolean("permanent", false);
            }
            List<Emote> emotes = new ArrayList<>();
            JSONArray jsonEmotes = curObj.getJSONArray("emote");
            for (int j = 0; j < jsonEmotes.length(); j++) {
                Emote emote = new Emote();
                JSONObject curEmote = jsonEmotes.getJSONObject(j);
                emote.id = curEmote.getInt("id");
                emote.packageId = curEmote.getInt("package_id");
                emote.name = curEmote.getString("text");
                emote.url = curEmote.getString("url");
                JSONObject childMeta = curEmote.optJSONObject("meta");
                if (childMeta != null) {
                    emote.size = childMeta.optInt("size", 1);
                    emote.alias = childMeta.optString("alias");
                }
                emotes.add(emote);
            }
            emotePackage.emotes = emotes;
            result.add(emotePackage);
        }
        return result;
    }
}
