# 设计文档：动态投票功能（发起 + 展示 + 参与）

- 日期：2026-08-28
- 项目：ReBiliClient
- 状态：接口已通过浏览器抓包（HAR）确认；实现待编码

## 背景与目标

ReBiliClient 是 B 站第三方安卓客户端，目前**完全没有投票功能**：动态流里看不到别人发的投票卡片，也不能发起投票或参与投票。本设计为接入 B 站动态投票的**完整闭环**——发起、展示、参与。

投票的**用户提交接口（do_vote）**在本项目本地 `bilibili-API/` 快照中缺失（官方文档未收录），本次通过浏览器抓包（用户导出的 `www.bilibili.com.har`）确认了全部接口细节。

## 用户已确认的设计决策（grill 结论）

| 决策点 | 结论 |
| --- | --- |
| 接入范围 | **完整闭环**：发起 + 展示 + 参与 |
| 代码层 | 跟随现状：Kotlin 写 UI + Java API 类（`NetWorkUtil` + org.json） |
| API 落点 | 新建 `VoteApi.java` |
| 投票类型范围 | 先做**文字投票 + 单选**（`type=0`、`choice_cnt=1`），打通闭环后扩展 |
| 发布链路 | SendDynamicActivity 编辑投票 → 返回 `VoteDraft` → DynamicActivity 先 `create_vote` 拿 vote_id 再发布挂载 |
| 展示程度 | 完整卡片：标题 + 选项 + 参与人数 + 可点击投票 |
| do_vote 接口缺口 | 通过浏览器抓包补齐（本设计文档记录成果） |

## 接口调研（2026-08-28 浏览器抓包确认）

> 抓包来源：`www.bilibili.com.har`（真实投票 `vote_id=21152695`，图文投票，choice_cnt=3）

### ① create_vote — 创建投票

- **URL**: `POST https://api.vc.bilibili.com/vote_svr/v1/vote_svr/create_vote`
- **认证**: Cookie (SESSDATA)，可选 csrf (bili_jct)
- **请求**: `multipart/form-data`
- **参数**:
  - `info[title]` 投票标题（必要）
  - `info[desc]` 投票描述（非必要）
  - `info[type]` **0=文字 / 1=图片**（必要）
  - `info[choice_cnt]` 最多选几项（必要）
  - `info[duration]` 持续秒数（必要；三天 259200 / 七天 604800 / 三十天 2592000）
  - `info[options][n][desc]` 第 n 项选项文字（必要，最少 2 项，n 从 0 开始）
  - `info[options][n][img_url]` 选项图片（图片投票时）
  - `csrf`
- **响应**: `data.vote_id`（投票 ID）

```json
{ "code": 0, "data": { "vote_id": 4947171, "_gt_": 0 } }
```

### ② do_vote — 用户提交投票（本次抓包关键补充）

- **URL**: `POST https://api.bilibili.com/x/vote/do_vote?csrf=<bili_jct>`
- **Content-Type**: `application/json;charset=UTF-8`
- **请求体**（JSON）:
```json
{
  "vote_id": 21152695,
  "votes": [12, 11, 8],
  "voter_uid": 591904067,
  "status": 1,
  "op_bit": 0,
  "dynamic_id": 0,
  "csrf_token": "a2758125d22b38eabe51ec51f41559a7",
  "csrf": "a2758125d22b38eabe51ec51f41559a7"
}
```
- **响应**: **base64 编码的 JSON**（解码后为标准 `{code,message,data.vote_info}`）
```json
{"code":0,"message":"OK","ttl":1,"data":{"uid":...,"type":1,"vote_info":{...}}}
```

**重要**：
- do_vote 在 **`api.bilibili.com/x/vote/`** 域，**不在** `vote_svr` 域。
- `votes` 是**选项索引数组**（对应 `opt_idx`），支持多选（示例选 3 个）。
- 响应 body 是 **base64 字符串**，需解码后再 `new JSONObject(...)`。

### ③ vote_info — 查询投票详情

- **URL**: `GET https://api.bilibili.com/x/vote/vote_info?vote_id=21152695`
- **返回** `data` 结构:
  - `vote_id` 投票 ID
  - `title` / `desc` 标题 / 描述
  - `join_num` 参与人数
  - `type` 投票类型（1=图文）
  - `choice_cnt` 最多选几项
  - `end_time` 结束时间戳 / `ctime` 创建时间戳
  - `status` 状态（1=进行中）
  - `vote_publisher` 发起人 uid
  - `my_votes` 我的选择（`opt_idx` 数组，未投为空）
  - `options[]`: `{opt_idx, opt_desc, img_url}`（**opt_idx 从 1 开始**）

### ④ followee_votes — 关注的人投票情况

- **URL**: `GET https://api.vc.bilibili.com/vote_svr/v1/vote_svr/followee_votes?vote_id=21152695`
- 查询关注的人投了什么。

### 动态内投票的挂载（本地快照已确认）

- 富文本节点 `RICH_TEXT_NODE_TYPE_VOTE`（`rid` 指向 vote_id）
- 附加卡片 `ADDITIONAL_TYPE_VOTE`（含 vote_id/title/join_num/选项等）

## 架构现状（接入点）

ReBiliClient **没有 MVVM 新层**（无 `network/`/`di/`/`data/` 目录，无 Retrofit/Hilt/kotlinx-serialization），实际以**遗留层**为主：

- Java API 类：`api/*.java`，用 `NetWorkUtil` + org.json 静态方法。
- Kotlin Activity：`BaseActivity` + `asyncInflate` + `CenterThreadPool` 模式（非 MVVM）。
- **发布链路**：
  ```
  SendDynamicActivity(编辑器, setResult 返回 text)
    → DynamicActivity.writeDynamicLauncher(第 93-139 行, 真正调发布 API)
      → DynamicApi.publishTextContent() / publishComplex()
  ```
- **动态模型** `Dynamic.java` 很简陋：`major_type` + `major_object` 表示主体，**无 `additional` 附加卡片字段**（投票卡片需新增）。
- **解析** `DynamicApi.analyzeDynamic`（第 378-601 行）只处理了 `ADDITIONAL_TYPE_UGC`，**需加 `ADDITIONAL_TYPE_VOTE` 分支**。
- **渲染** `DynamicHolder.kt` 的 `extraCard` 下有 `cell_dynamic_video/image/article` 各卡片，需新增 `cell_dynamic_vote`。

## 实现设计

### 1. 数据模型（`model/`，Java Serializable）

**`VoteOption.java`**
```java
public class VoteOption implements Serializable {
    public int opt_idx;        // 从 1 开始
    public String opt_desc;
    public String img_url;
}
```

**`VoteInfo.java`**
```java
public class VoteInfo implements Serializable {
    public long vote_id;
    public String title;
    public String desc;
    public int join_num;
    public int type;          // 0 文字 / 1 图文
    public int choice_cnt;
    public long end_time;
    public int status;
    public long vote_publisher;
    public List<Integer> my_votes = new ArrayList<>();
    public List<VoteOption> options = new ArrayList<>();
}
```

**`VoteDraft.java`**（编辑器 → 发布链路传递的草稿对象）
```java
public class VoteDraft implements Serializable {
    public String title;
    public String desc;
    public List<String> options = new ArrayList<>(); // 选项文字
}
```

### 2. `VoteApi.java`（新建，`api/`）

```java
public class VoteApi {
    // 创建投票，返回 vote_id；失败返回 -1
    public static long createVote(VoteDraft draft) throws IOException
    // 提交投票，返回 code（0 成功）
    public static int doVote(long voteId, List<Integer> votes) throws IOException
    // 查询投票详情
    public static VoteInfo getVoteInfo(long voteId) throws IOException, JSONException
    // 关注的人投票（可选）
    public static JSONArray getFolloweeVotes(long voteId) throws IOException, JSONException
}
```

关键实现点：
- `createVote`：`NetWorkUtil.post(url, new FormData().put(...))`，multipart/form-data。
- `doVote`：`NetWorkUtil.postJson(url + "?csrf=" + csrf, jsonBody)`，application/json，**需 base64 解码响应**。
- csrf 从 `SharedPreferencesUtil.getString("csrf","")` 取；voter_uid 从 `SharedPreferencesUtil.mid` 取。

### 3. `Dynamic.java` 加附加字段

```java
public String additional_type;   // "ADDITIONAL_TYPE_VOTE" 等
public VoteInfo vote;            // 投票卡片
```

### 4. `DynamicApi.analyzeDynamic` 解析投票

在 `module_additional` 解析块（第 552-562 行）加分支：
```java
if (type.equals("ADDITIONAL_TYPE_VOTE")) {
    dynamic.additional_type = type;
    dynamic.vote = VoteApi.parseVoteInfo(module_additional.getJSONObject("vote"));
}
```
并在富文本解析 `analyzeTextContent` 中处理 `RICH_TEXT_NODE_TYPE_VOTE`（目前走 TEXT 默认分支，会显示投票标题文本，可保留）。

### 5. `SendDynamicActivity` 扩展投票编辑

- 在 `emote` 卡片下方新增"添加投票"入口（MaterialCardView 按钮）。
- 点击展开/弹出一个投票编辑区（标题 EditText + 选项 EditText 列表 + 添加/删除选项）。
- 编辑完成后存到 `VoteDraft`，随 `setResult` 的 Intent extra 返回（`putExtra("voteDraft", draft)`）。

### 6. `DynamicActivity.writeDynamicLauncher` 发布链路改造

收到投票草稿后（第 93-139 行）：
1. 若有 `VoteDraft`，先 `VoteApi.createVote(draft)` 拿 `vote_id`。
2. 用 `DynamicApi.publishComplex(...)` 的 `otherArgs` 挂载投票（或新增重载方法），把 vote_id 关联到动态。
3. 成功后刷新列表。

### 7. `DynamicHolder` 渲染投票卡片

- `cell_dynamic.xml` 的 `extraCard` 新增 `cell_dynamic_vote` 视图（标题 + 选项列表 + 参与人数 + 按钮）。
- `showDynamic` 中：若 `dynamic.additional_type == "ADDITIONAL_TYPE_VOTE"`，渲染投票卡片。
- 选项点击 → 调 `VoteApi.doVote(vote_id, 选中的 opt_idx)` → 成功后刷新显示 `my_votes`/`join_num`。

## 错误处理与边界

- **未登录**：`create_vote`/`do_vote` 需登录；未登录提示"还没有登录喵~"。
- **csrf 失效**：do_vote 返回 -111 时提示重新登录。
- **base64 解码**：do_vote 响应是 base64，需用 `android.util.Base64` 解码，解码失败按空处理。
- **选项索引**：`opt_idx` 从 1 开始（创建时 `options[n]` 从 0，注意转换）。
- **投票结束**：`end_time` 过期或 `status != 1` 时按钮禁用，只读展示。
- **选项为空**：编辑区选项少于 2 个时禁止提交。

## 待办 / 后续扩展

- [ ] 实现投票卡片渲染的完整 UI（布局 + DynamicHolder）
- [ ] 发布链路 `publishComplex` 挂载投票的参数细节仍需真机验证
- [ ] 图片投票（`type=1`）：需要图片上传，暂不做
- [ ] 多选（`choice_cnt>1`）：接口已支持，UI 后续扩展
- [ ] followee_votes（关注的人投票）展示

## 参考

- 本地接口文档快照：`bilibili-API/docs/dynamic/publish.md`（create_vote）、`bilibili-API/docs/dynamic/all.md`、`bilibili-API/docs/dynamic/dynamic_enum.md`
- 抓包文件：`www.bilibili.com.har`
