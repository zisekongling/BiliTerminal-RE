package com.RobinNotBad.BiliClient.api;

import android.util.Base64;

import com.RobinNotBad.BiliClient.model.VoteDraft;
import com.RobinNotBad.BiliClient.model.VoteInfo;
import com.RobinNotBad.BiliClient.model.VoteOption;
import com.RobinNotBad.BiliClient.util.NetWorkUtil;
import com.RobinNotBad.BiliClient.util.Logu;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * 投票 API：创建投票、提交投票、查询投票详情
 */
public class VoteApi {

    /**
     * 创建投票
     *
     * @param draft 投票草稿
     * @return 投票 ID，失败返回 -1
     */
    public static long createVote(VoteDraft draft) throws IOException {
        String url = "https://api.vc.bilibili.com/vote_svr/v1/vote_svr/create_vote";
        // create_vote 用 Cookie (SESSDATA) 鉴权，不能带 access_key（会自动加），否则触发风控返回 HTML
        NetWorkUtil.FormData formData = new NetWorkUtil.FormData()
                .setAutoAddAccessKey(false)
                .put("info[title]", draft.title)
                .put("info[desc]", draft.desc != null ? draft.desc : "")
                .put("info[type]", 0)       // 0=文字投票
                .put("info[choice_cnt]", 1) // 先做单选
                .put("info[duration]", 259200) // 三天
                .put("csrf", SharedPreferencesUtil.getString(SharedPreferencesUtil.csrf, ""));
        // 添加选项（n 从 0 开始）
        for (int i = 0; i < draft.options.size(); i++) {
            formData.put("info[options][" + i + "][desc]", draft.options.get(i));
        }
        Response resp = Objects.requireNonNull(NetWorkUtil.post(url, formData.toString(), NetWorkUtil.webHeaders));
        try {
            ResponseBody body = resp.body();
            if (body == null) return -1;
            String raw = body.string();
            // 打印响应前 500 字符，便于排查风控/协议返回的 HTML
            Logu.d("create_vote_resp", raw.length() > 500 ? raw.substring(0, 500) : raw);
            JSONObject result = new JSONObject(raw);
            if (result.getInt("code") == 0 && result.has("data")) {
                return result.getJSONObject("data").getLong("vote_id");
            }
        } catch (JSONException e) {
            e.printStackTrace();
            return -1;
        }
        return -1;
    }

    /**
     * 提交投票
     *
     * @param voteId 投票 ID
     * @param votes  选项索引列表（opt_idx）
     * @return code（0 成功）
     */
    public static int doVote(long voteId, List<Integer> votes) throws IOException {
        String csrf = SharedPreferencesUtil.getString(SharedPreferencesUtil.csrf, "");
        long voterUid = SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0);
        String url = "https://api.bilibili.com/x/vote/do_vote?csrf=" + csrf;

        JSONObject jsonBody = new JSONObject();
        try {
            jsonBody.put("vote_id", voteId);
            JSONArray votesArray = new JSONArray();
            for (int v : votes) {
                votesArray.put(v);
            }
            jsonBody.put("votes", votesArray);
            jsonBody.put("voter_uid", voterUid);
            jsonBody.put("status", 1);
            jsonBody.put("op_bit", 0);
            jsonBody.put("dynamic_id", 0);
            jsonBody.put("csrf_token", csrf);
            jsonBody.put("csrf", csrf);
        } catch (JSONException e) {
            e.printStackTrace();
            return -1;
        }

        Response resp = Objects.requireNonNull(NetWorkUtil.postJson(url, jsonBody.toString()));
        try {
            ResponseBody body = resp.body();
            if (body == null) return -1;
            String raw = body.string();
            JSONObject result;
            try {
                // 优先尝试直接解析 JSON
                result = new JSONObject(raw);
            } catch (JSONException e) {
                // 部分大响应会 base64 编码，回退解码
                byte[] decoded = Base64.decode(raw, Base64.DEFAULT);
                result = new JSONObject(new String(decoded, "UTF-8"));
            }
            return result.getInt("code");
        } catch (JSONException e) {
            e.printStackTrace();
            return -1;
        }
    }

    /**
     * 查询投票详情
     *
     * @param voteId 投票 ID
     * @return VoteInfo 对象，失败返回 null
     */
    public static VoteInfo getVoteInfo(long voteId) throws IOException, JSONException {
        String url = "https://api.bilibili.com/x/vote/vote_info?vote_id=" + voteId;
        JSONObject result = NetWorkUtil.getJson(url, NetWorkUtil.webHeaders);
        if (result.getInt("code") != 0) return null;
        if (!result.has("data") || result.isNull("data")) return null;
        JSONObject data = result.getJSONObject("data");
        if (!data.has("vote_info") || data.isNull("vote_info")) return null;
        VoteInfo voteInfo = parseVoteInfo(data.getJSONObject("vote_info"));
        // my_votes 在 data 层（vote_info 对象外面），需单独读取
        JSONArray myVotes = data.optJSONArray("my_votes");
        if (myVotes != null) {
            voteInfo.my_votes.clear();
            for (int i = 0; i < myVotes.length(); i++) {
                voteInfo.my_votes.add(myVotes.getInt(i));
            }
        }
        return voteInfo;
    }

    /**
     * 从 JSON 解析 VoteInfo
     *
     * @param json 投票数据 JSON 对象
     * @return VoteInfo 对象
     */
    public static VoteInfo parseVoteInfo(JSONObject json) throws JSONException {
        VoteInfo voteInfo = new VoteInfo();
        voteInfo.vote_id = json.optLong("vote_id", 0);
        voteInfo.title = json.optString("title", "");
        voteInfo.desc = json.optString("desc", "");
        voteInfo.join_num = json.optInt("join_num", 0);
        voteInfo.type = json.optInt("type", 0);
        voteInfo.choice_cnt = json.optInt("choice_cnt", 1);
        voteInfo.end_time = json.optLong("end_time", 0);
        voteInfo.status = json.optInt("status", 0);
        voteInfo.vote_publisher = json.optLong("vote_publisher", 0);

        // 解析我的投票
        JSONArray myVotes = json.optJSONArray("my_votes");
        if (myVotes != null) {
            for (int i = 0; i < myVotes.length(); i++) {
                voteInfo.my_votes.add(myVotes.getInt(i));
            }
        }

        // 解析选项
        JSONArray options = json.optJSONArray("options");
        if (options != null) {
            for (int i = 0; i < options.length(); i++) {
                JSONObject optJson = options.getJSONObject(i);
                VoteOption option = new VoteOption();
                option.opt_idx = optJson.optInt("opt_idx", 0);
                option.opt_desc = optJson.optString("opt_desc", "");
                option.img_url = optJson.optString("img_url", "");
                option.cnt = optJson.optInt("cnt", 0);
                voteInfo.options.add(option);
            }
        }

        return voteInfo;
    }

    /**
     * 查询关注的人投票情况
     *
     * @param voteId 投票 ID
     * @return JSONArray，失败返回空数组
     */
    public static JSONArray getFolloweeVotes(long voteId) throws IOException, JSONException {
        String url = "https://api.vc.bilibili.com/vote_svr/v1/vote_svr/followee_votes?vote_id=" + voteId;
        JSONObject result = NetWorkUtil.getJson(url, NetWorkUtil.webHeaders);
        if (result.getInt("code") == 0 && result.has("data")) {
            JSONObject data = result.getJSONObject("data");
            if (data.has("votes") && !data.isNull("votes")) {
                return data.getJSONArray("votes");
            }
        }
        return new JSONArray();
    }

    /**
     * 动态发布/投票权限预判
     * 判断当前用户是否有权参与投票、评论开关等
     *
     * @param uid 当前用户 UID
     * @return pre_judge 的 data 对象；失败返回 null
     */
    public static JSONObject getPreJudge(long uid) {
        String url = "https://api.vc.bilibili.com/dynamic_repost/v1/dynamic_repost/pre_judge?uid=" + uid;
        try {
            JSONObject result = NetWorkUtil.getJson(url, NetWorkUtil.webHeaders);
            if (result.getInt("code") == 0 && result.has("data") && !result.isNull("data")) {
                return result.getJSONObject("data");
            }
        } catch (Exception e) {
            Logu.d("VotePreJudge", "权限预判失败: " + e.getMessage());
        }
        return null;
    }

    /**
     * 删除投票（仅投票发布者可删除）
     *
     * @param voteId 投票 ID
     * @return code（0 成功）
     */
    public static int deleteVote(long voteId) {
        String csrf = SharedPreferencesUtil.getString(SharedPreferencesUtil.csrf, "");
        long uid = SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0);
        String url = "https://api.bilibili.com/x/vote/delete?csrf=" + csrf;
        JSONObject jsonBody = new JSONObject();
        try {
            jsonBody.put("vote_id", voteId);
            jsonBody.put("uid", uid);
            jsonBody.put("csrf_token", csrf);
            jsonBody.put("csrf", csrf);
        } catch (JSONException e) {
            e.printStackTrace();
            return -1;
        }
        try {
            Response resp = Objects.requireNonNull(NetWorkUtil.postJson(url, jsonBody.toString()));
            ResponseBody body = resp.body();
            if (body == null) return -1;
            String raw = body.string();
            JSONObject result;
            try {
                result = new JSONObject(raw);
            } catch (JSONException e) {
                byte[] decoded = Base64.decode(raw, Base64.DEFAULT);
                result = new JSONObject(new String(decoded, "UTF-8"));
            }
            return result.getInt("code");
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
    }
}