# ReBiliClient 网络 API 层深度代码审查报告

- 审查范围：`app/src/main/java/com/RobinNotBad/BiliClient/api/` 全部 41 个文件（Java + Kotlin 混编）
- 审查重点：JSON 解析健壮性、网络错误处理、URL/参数构造、Cookie/登录态、线程与回调、幻觉功能（对照 `bilibili-API/` 快照）、资源泄漏
- 说明：所有结论均对应具体代码行；部分结论依据对 B 站接口行为与 OkHttp/org.json 实现的分析，标注"待实测"的条目建议在真机验证后确认

---

## Critical（崩溃 / 数据错误 / 安全）

### 1. `CookieRefreshApi.java:99` —— Cookie 刷新成功后 `DedeUserID` 解析崩溃
```java
SharedPreferencesUtil.putLong(SharedPreferencesUtil.mid, Long.parseLong(NetWorkUtil.getInfoFromCookie("DedeUserID", cookies_new)));
```
**问题描述**：`getInfoFromCookie` 在 cookie 串中找不到 `DedeUserID` 时返回 `""`，`Long.parseLong("")` 抛出 `NumberFormatException`（RuntimeException，非声明异常）。
**影响**：刷新流程已执行到"确认刷新"成功之后（登录态几乎已经更换），此时崩溃会把用户卡在中间状态；若刷新接口返回的 Set-Cookie 因任何原因未完整写入（如 `cookies_new` 仍是旧值或为空），则每次刷新必崩。
**修复建议**：先取字符串，判空/校验后解析；解析失败时回滚 `cookies_old` 并返回 false。

### 2. `FavoriteApi.java:285` / `LikeCoinFavApi.java:48` —— `mid` 后两位截取越界崩溃
```java
String strMid = String.valueOf(SharedPreferencesUtil.getLong("mid", 0));
String addFid = fid + strMid.substring(strMid.length() - 2);
```
**问题描述**：未登录时 `mid = 0`，`strMid = "0"`，`"0".substring(-1)` 抛 `StringIndexOutOfBoundsException`（未捕获，直接崩溃）。该方法还被 `LikeCoinFavApi.favorite()`（点赞收藏链路）复制了一份。
**影响**：未登录（或本地 mid 缺失）状态下任何触发"添加到收藏夹/收藏"的入口都会崩溃。
**修复建议**：提取公共函数 `buildMediaId(fid, mid)`，对 `mid` 位数不足 2 位（含 0）时直接返回 `fid` 或走 `fid * 100 + mid % 100` 的算术式（`add_media_ids` 需要"完整 media_id"，即 `fid + mid 后两位`，见 `bilibili-API/docs/fav/info.md` 对 `id = 原始id+创建者mid尾号2位` 的说明）。

### 3. `FavoriteApi.java:254` —— `opus_id` 解析 NumberFormatException 崩溃
```java
opus.id = Long.parseLong(item.getString("opus_id"));
```
**问题描述**：`opus/favlist` 响应中若某条 `opus_id` 缺失、为 null 或非纯数字（服务端数据结构变化时常见），`getString` 抛 `JSONException` 或 `parseLong` 抛 `NumberFormatException`，两者均未被逐条捕获。
**影响**：收藏图文列表加载时单条脏数据导致整个页面崩溃。
**修复建议**：改为 `optString("opus_id","0")` + try-catch 或 `optLong`，单条解析失败跳过。

### 4. `DynamicApi.java:349` —— 分页 offset 解析 NumberFormatException 崩溃
```java
long offset_new = has_more ? Long.parseLong(data.getString("offset")) : -1;
```
**问题描述**：`has_more=true` 时若 `data.offset` 缺失或为 null（风控/降级响应下 B 站可能返回不完整数据），`getString` 抛 `JSONException`，或 `offset` 为非数字字符串时 `parseLong` 抛 `NumberFormatException`，后者未被捕获。
**影响**：动态流加载时一页异常数据直接中断整页刷新。
**修复建议**：`optString("offset","")` + 判空 + try-catch；解析失败按无更多数据（-1）处理。

### 5. `DynamicApi.java:220` —— @ 用户名未转义正则元字符，PatternSyntaxException 崩溃 / 匹配错乱
```java
Pattern pattern = Pattern.compile("@" + key + " ");
```
**问题描述**：`key`（用户名）直接拼进正则，未做 `Pattern.quote`；且只匹配"@用户名+空格"形式。用户名含 `[`、`(`、`.`、`\` 等正则元字符时 `compile` 抛 `PatternSyntaxException`（RuntimeException，未捕获）。
**影响**：在动态里 @ 一个昵称含方括号（如 `[官方]xxx`）等字符的用户 → 发送动态崩溃；即使不崩溃，句尾不带空格的 @（如 `@某某。`）也匹配不到，at 解析漏配。
**修复建议**：`Pattern.compile("@" + Pattern.quote(key) + " ")`；同时把"@xxx"按"后接非用户名结尾字符"或直接做字符串 indexOf 扫描替代正则。

---

## High（明显逻辑 bug / 资源泄漏 / 敏感信息）

### 6. `PrivateMsgApi.java:157` —— 会话列表添加条件写反，逻辑恒等于"仅无 account_info 才添加"
```java
if (!sessionJson.has("account_info") && sessionJson.isNull("account_info"))
    sessionList.add(session);
```
**问题描述**：org.json 的 `isNull(key)` 对"键不存在"同样返回 true，因此该条件化简为 `!has("account_info")`。任何带 `account_info` 字段的会话都被跳过；只有缺少该字段的会话才进列表。
**影响**：一旦 `get_sessions` 响应包含 `account_info`（服务端加字段即触发），私信会话列表整体变空；当前可能因 B 站不返回该字段而未暴露，属定时炸弹。同文件 `getNewSessionsList`（无此检查）写法正确，可对照。
**修复建议**：改为 `if (sessionJson.has("account_info") && !sessionJson.isNull("account_info"))` 或直接删掉该判断。

### 7. `ReplyApi.java:153` —— 发送评论 message 未 URL 编码（与 231 行带图版不一致）
```java
+ "&message=" + text + "&jsonp=jsonp&csrf=" + ...
```
**问题描述**：不带图评论走手拼 form 字符串，`text` 原样拼接；而带图重载（`sendReply` 六参版，Line 231）用了 `URLEncoder.encode`。`NetWorkUtil.post` 把整个字符串当 form body 发送，`&`、`=`、`+` 会改变参数切分。
**影响**：评论内容含 `&`（如 "A&B"）时正文被截断、多出无效参数，服务端可能拒绝或只收到截断文本；含 `+` 时被解码为空格。
**修复建议**：统一用 `URLEncoder.encode(text, "UTF-8")`（与带图版一致）。

### 8. `PrivateMsgApi.java:217` —— 私信内容未 URL 编码，消息被截断
```java
+ "&msg[content]=" + content + "&msg[receiver_type]=1&csrf=" + ...
```
**问题描述**：`content` 原样拼进 form body。私信文本（尤其含 `&`、`=`）或图片消息的 JSON content（含 `{}`、`"`、`:`）会破坏参数解析。
**影响**：含 `&` 的私信发送后内容丢失；图片私信（content 为 JSON）大概率发送失败。
**修复建议**：`URLEncoder.encode(content, "UTF-8")`；更稳妥是改用 `FormData` 或 OkHttp `FormBody`。

### 9. `DanmakuApi.java:30,42` —— 弹幕 msg 未 URL 编码
```java
String arg = "type=1&oid=" + cid + "&msg=" + msg + "&bvid=" + bvid + ...
```
**问题描述**：同上模式，弹幕文本含 `&`/`=` 时被截断或参数错乱。
**影响**：发送含特殊字符弹幕失败或发送错误内容。
**修复建议**：`URLEncoder.encode(msg, "UTF-8")`。

### 10. `PlayerApi.java:236-241` —— getVideo 10 分钟缓存未纳入 qn/cid，切换画质后 10 分钟内不刷新 URL
```java
if (System.currentTimeMillis() - playerData.timeStamp < 600000)
    return;
```
**问题描述**：缓存键只是"同一 `PlayerData` 对象 + 时间"，未比较 `qn`、`cid`。实际调用链确认会复用同一对象：`PlayerActivity.kt:2368-2373`（`playerData.qn = newQuality; PlayerApi.getVideo(playerData, false)`）、`PlayerActivity.kt:2724-2736`、`LocalListActivity.kt:382-383`。
**影响**：播放中切换清晰度/分页、或本地缓存换画质时，10 分钟内重复调用直接 return，播放/下载仍用旧画质 URL——用户可见的"切画质无效"bug。
**修复建议**：缓存命中条件增加 `qn`、`cid` 比较，或缓存失效时强制刷新。

### 11. `SearchApi.java:24-25,43` —— 静态可变 `seid`/`search_keyword` 并发串号
```java
public static String seid = "";
public static String search_keyword = "";
```
**问题描述**：全局静态状态，A 页面搜索后 `seid` 被写，B 页面（或同页并发翻页/改关键词）读到 A 的 `seid` 拼进自己的请求。
**影响**：并发/快速切换搜索词时结果错乱、翻页串页。
**修复建议**：`seid` 随请求返回、随调用传参，不做全局静态；或至少以"关键词+线程"维度隔离。

### 12. `NetWorkUtil.java:100-109` —— 手动重定向拦截器丢弃原 Response，连接泄漏
```java
if (response.isRedirect() && location != null) {
    ...
    Request newRequest = request.newBuilder().url(location).build();
    return chain.proceed(newRequest);   // 原 response 从未 close
}
```
**问题描述**：`getOkHttpInstance()` 全局关闭了 OkHttp 自动跟随重定向（`followRedirects(false)`），改在拦截器里手动 `chain.proceed(newRequest)`，但被替换的原 `response` 没有 close。
**影响**：每次遇到 302/301 都泄漏一个 Response（占用连接池），B 站 API 常发生重定向（如域名跳转、`/read/cv` 加斜杠跳转），长时间使用连接池被占满后新请求阻塞/失败。
**修复建议**：`return` 前 `response.close()`；或干脆开启 `followRedirects(true)` 让 OkHttp 自己关。

### 13. `CookiesApi.java:158` —— checkCookies 首页请求返回值未关闭，连接泄漏
```java
NetWorkUtil.get("https://www.bilibili.com/");
```
**问题描述**：`get` 返回的 `Response` 被丢弃，body 从未读取/关闭（对比 `getJson` 有 try-with-resources）。
**影响**：每次 `checkCookies` 泄漏一个连接；该方法在登录/启动链路频繁调用，长期运行累积。
**修复建议**：`try (Response r = NetWorkUtil.get(...)) { r.body() != null && r.body().close(); }` 或直接调 `getJson` 变体。

### 14. `PrivateMsgApi.java:92-96,226` —— 私信内容与完整 Cookie 打印到 logcat
```java
for (PrivateMessage i : list) Log.e("msg", i.name + "." + i.uid + "..." + i.content + ...);
Log.e("debug-发送私信", NetWorkUtil.webHeaders.toString());  // 含 Cookie: SESSDATA=...
```
**问题描述**：逐条打印全部私信内容；`webHeaders.toString()` 会打出完整 Cookie 头（含 `SESSDATA`、`bili_jct` 登录凭据）。
**影响**：敏感数据落盘 logcat，adb 日志/系统错误报告/崩溃上报（`uploadStack` 会上传堆栈，虽不含 logcat，但本地日志可被有 adb 权限者读取）可窃取会话与聊天记录。
**修复建议**：删除这两处日志；如需调试只打 uid 与消息条数，绝不打印 Cookie 与消息正文。

### 15. `AppInfoApi.java:120,139,163,186` —— 第三方接口使用明文 http://
```java
String url = "http://api.biliterminal.cn/terminal/announcement/get_list?from=" + ...
```
**问题描述**：公告、赞助列表、崩溃堆栈上传全部走明文 HTTP，无 TLS。
**影响**：中间人可篡改公告内容、注入假更新提示，或截获崩溃堆栈（含设备信息）。
**修复建议**：升级为 `https://`（若服务器支持）；否则至少对公告/更新类内容做签名校验。

### 16. `SearchApi.java:94,128,145` —— 图片 URL 硬编码 `http:` 前缀
```java
String cover = "http:" + card.getString("pic");
```
**问题描述**：B 站图片链接为 `//i0.hdslb.com/...` 协议相对形式，代码统一补 `http:`。
**影响**：明文图片流量，部分网络环境（强制 HTTPS/内容安全策略）加载失败；应补 `https:`。
**修复建议**：改为 `"https:" + ...`。

---

## Medium（健壮性 / 参数错误 / 数据准确性）

### 17. `UserInfoApi.java:135` —— 硬币余额 `getInt` 解析失败或截断
```java
return data.has("money") ? data.getInt("money") : 0;
```
**问题描述**：`/site/getCoin` 的 `money` 为浮点（如 `172.4`）或字符串小数（`"172.40"`）；快照 `bilibili-API/docs/login/login_info.md` 亦注明 `money` 在硬币为 0 时为 `null`。org.json 的 `getInt` 对浮点直接截断（172.4→172），对字符串小数/`null` 抛 `JSONException`。
**影响**：硬币数显示错误（少小数部分）或恒显示 0（字符串小数场景）。
**修复建议**：`data.optDouble("money", 0)` 或 `optString` 后 `BigDecimal` 解析，UI 按两位小数展示。

### 18. `LikeCoinFavApi.java:67` —— 字段名拼写错误 `dislik`（应为 `dislike`）
```java
videoInfo.stats.disliked = data.optBoolean("dislik");
```
**问题描述**：`/x/web-interface/archive/relation` 的真实字段为 `dislike`（见 `bilibili-API/docs/video/info.md`、`action.md` 大量示例），此处少写末尾 `e`。
**影响**：点踩状态恒为 false，界面"踩过"标记永远不显示（纯数据错误，不崩溃）。
**修复建议**：改为 `optBoolean("dislike")`。

### 19. `ArticleApi.java:192` —— `time_zone_offset` 参数数值错误
```java
String url = "https://api.bilibili.com/x/polymer/web-dynamic/v1/opus/detail?id=" + opusId + "&time_zone_offset=" + TimeZone.getDefault().getRawOffset() / 100000;
```
**问题描述**：`getRawOffset()` 单位是毫秒（东八区 = 28800000），除以 100000 得 288；而 B 站该参数要求**分钟**（如 `timezone_offset=-480`，见 `DynamicApi` 中其他调用）。数值差近一倍且符号为正（应为东八区 -480）。
**影响**：opus 详情接口时区相关展示错误；若服务端校验该参数可能直接返回异常数据。
**修复建议**：`-(getRawOffset() / 60000)`，与项目内其他 `-480` 用法保持一致。

### 20. `EmoteApi.java:150-156` —— `setPackage` 调用错误的接口（幻觉功能）
```java
String url = "https://api.bilibili.com/bapis/main.community.interface.emote.EmoteService/AllPackages" + ... .put("ids", idsSb.toString()).put("type", isAdd ? 0 : 1);
```
**问题描述**：`AllPackages` 是"获取全部表情包列表"的查询接口，不是设置接口；添加/移除表情包应调用独立的设置接口。且全局搜索确认 `setPackage` 无任何调用方（死代码）。
**影响**：若未来接入 UI 调用必失败（返回列表数据而非操作结果）；当前是误导性的死代码。
**修复建议**：删除该方法，或对照真实接口（EmoteService 的 Set/Update 类方法）重写。

### 21. `BangumiApi.java:38` / `SearchApi.java:113-114` / `DynamicApi.java:482` —— media_id / season_id 混入 aid / bvid 字段
```java
card.aid = bangumi.getLong("media_id");                      // BangumiApi:38
long aid = card.getLong("media_id");
String bvid = card.getString("season_id");                   // SearchApi:113-114
card.aid = BangumiApi.getMdidFromEpid(bangumi.optLong("epid", 0)); // DynamicApi:482
```
**问题描述**：番剧卡片的 `media_id`/`season_id` 被塞进 `VideoCard.aid`/`bvid`。下游 `VideoCardAdapter.fetchVideoInfo`（VideoCardAdapter.kt:166-171）与 `QualityChooserActivity` 只按 `aid/bvid` 调 `VideoInfoApi.getVideoInfo`（普通视频接口），不识别 `type="media_bangumi"`。
**影响**：从番剧列表/搜索结果长按快速缓存番剧 → 用 media_id 当 aid 请求 → "获取视频信息失败"。数据模型字段语义被跨层复用，是崩溃之外的隐性错误源。
**修复建议**：VideoCard 增加独立 `mediaId/seasonId` 字段，消费端按 `type` 分派到番剧接口；不要在 aid/bvid 里塞别的 ID。

### 22. `MessageApi.java:80-87,185-191,284-290,376-403` —— 业务 code 未检查，错误当成功
**问题描述**：`getLikeMsg/getReplyMsg/getAtMsg/getSystemMsg/getUnread` 均不检查 `code`，只要 `data` 存在就当成功。
**影响**：未登录（-101）、风控（-352/-412）、账号异常时界面显示"没有消息"，用户无法区分"无消息"与"拉取失败"。
**修复建议**：入口统一 `if (code != 0) throw new JSONException(message)` 或返回带错误码的 Result。

### 23. `VideoInfoApi.java:190` —— `is_upower_exclusive` 缺省值取 true
```java
videoInfo.upowerExclusive = data.optBoolean("is_upower_exclusive", true);
```
**问题描述**：字段缺失时默认"是充电专属"，与常识相反（默认应为 false）。若该字段在部分视频响应中不出现，会把普通视频误判为充电专属，可能影响下载/清晰度逻辑。
**修复建议**：`optBoolean("is_upower_exclusive", false)`。

### 24. `ShortVideoFeedApi.kt:104` —— `body!!` 强解包 NPE 未被捕获
```kotlin
val body = JSONObject(response.body!!.string())
```
**问题描述**：`catch` 只覆盖 `JSONException`/`IOException`，`body` 为 null 时抛 `KotlinNullPointerException` 直接崩溃。
**修复建议**：`response.body?.string() ?: return emptyList()`。

### 25. `ShortVideoFeedApi.kt:17,36` —— `currentPage` 静态可变 + 页码当游标
```kotlin
private var currentPage = 1
... append("&display_id=").append(currentPage)
```
**问题描述**：object 单例的全局页码，多入口并发调用互相干扰；且 B 站 story feed 的 `display_id` 是服务端下发的分页游标（上一页响应里带），用自增页码替代会导致翻页重复/跳变。
**修复建议**：游标随响应返回并作为参数传入；去掉全局 `currentPage`。

### 26. `BangumiApi.java:197` —— 嵌套 requireNonNull 未覆盖 body
```java
JSONObject all = new JSONObject(Objects.requireNonNull(Objects.requireNonNull(NetWorkUtil.get(url)).body()).string());
```
**问题描述**：`get(url).body()` 可能为 null，`requireNonNull` 只包住了 Response，body 为 null 时 `.string()` 抛 NPE。
**修复建议**：显式判空 body 并抛带上下文的 IOException/JSONException。

### 27. `DynamicApi.java:360-362` —— 单条动态解析失败中断整页加载
```java
for (...) { dynamicList.add(analyzeDynamic(items.getJSONObject(i))); }
```
**问题描述**：`analyzeDynamic` 内部多处 `getJSONObject/getString`（如 `module_author.mid`、`three_point_items`）未做防御，外层只 catch 了 major 部分的异常；单条脏数据抛 `JSONException` 会中断整个列表刷新（对比 `analyzeTextContent` 已有容错，风格不一致）。
**影响**：feed 中一条异常动态（风控降级、新卡片类型）导致整页拉取失败。
**修复建议**：逐条 try-catch，失败条目降级为占位卡片继续。

### 28. `LoginApi.java:29-30` —— 静态 `oauthKey`/`tvAuthCode` 并发登录互相覆盖
**问题描述**：Web 登录与 TV 登录共用两个静态字段；同时打开两个登录页/二维码时，先发出的 poll 会拿着后生成的 key 轮询。
**影响**：并发登录时二维码状态错乱（扫 A 码查 B 的状态）。
**修复建议**：key 作为返回值/参数传递，不落静态字段。

### 29. `UserInfoApi.java:148` —— 空间搜索 keyword 未编码直接拼 URL
```java
url += "keyword=" + searchKeyword + "&mid=" + mid + ...
```
**问题描述**：`searchKeyword` 未做 URL 编码。虽然 `signWBI` 内部 `Uri.encode` 会兜底编码，但 keyword 含 `&`（如 "a&b"）时在编码前已被视为参数分隔符。
**影响**：含 `&`/`#` 的搜索词解析错乱（与 SearchApi 已编码的写法不一致）。
**修复建议**：拼 URL 前 `URLEncoder.encode(searchKeyword, "UTF-8")`。

### 30. `FavoriteApi.java:65` —— 收藏夹 videos 空数组时取 [0] 崩溃
```java
favoriteFolder.cover = folder.getJSONArray("videos").getJSONObject(0).getString("pic");
```
**问题描述**：`getBoxList` 返回的 `videos` 为空数组（空收藏夹）时 `getJSONObject(0)` 抛 `JSONException`，且外层无 try-catch。
**影响**：加载他人收藏夹列表时，存在空收藏夹即整页失败。
**修复建议**：先判 `length() > 0`，否则 cover 置空。

### 31. `NetWorkUtil.java:393-443` —— `saveCookiesFromResponse` 无同步，多线程竞态丢更新
**问题描述**：`putCookie/setCookies/getCookies` 均有 `synchronized(NetWorkUtil.class)`，但拦截器回调的 `saveCookiesFromResponse`（每个响应都会触发）没有加锁；并发请求时"读旧值→合并→写回"会互相覆盖，丢失其他线程刚写入的 Cookie。
**影响**：并发场景下偶发 Cookie 丢失（如 bili_ticket 刷新后被旧响应覆盖回旧值），触发登录态异常。
**修复建议**：`saveCookiesFromResponse` 整体加同一把锁。

### 32. `AppInfoApi.java:194` —— `SimpleDateFormat` 使用 12 小时制 `hh`
**问题描述**：赞助列表时间 `"yyyy-MM-dd hh:mm"`，下午时间显示为 01:30 而非 13:30。
**修复建议**：改 `HH:mm`。

### 33. `LiveApi.java:352-353` / `VideoInfoApi.java:152` —— 循环/方法内反复新建 SimpleDateFormat
**问题描述**：`analyzeLiveRooms` 每个直播间 new 一个 `SimpleDateFormat`（非线程安全类），`getInfoByJson` 每次调用 new。
**影响**：性能小损耗 + 局部使用本身无并发问题，但属于可复用的明显优化点。
**修复建议**：提取 `static final ThreadLocal<SimpleDateFormat>`。

---

## Low（风格 / 性能 / 边界小问题）

### 34. `ArticleApi.java:123,149,169,186` —— `assert resp.body() != null` 用于运行时判空
**问题描述**：`assert` 在 release 构建被禁用，`body` 为 null 时直接 NPE；且这四处与项目其他 `.body().string()` 无判空的写法一致。
**修复建议**：统一改为显式判空并抛出带上下文异常；`assert` 只用于调试期不变量。

### 35. `HistoryApi.java:85` / `WatchLaterApi.java:53,64` / `UserInfoApi.java:208,225,245` / `LoginApi.java:77,95,126` 等 —— `post(...).body().string()` 无判空
**问题描述**：全项目约十余处直接对 `response.body()` 调 `.string()`，未判空即 NPE（虽概率低）。
**修复建议**：在 `NetWorkUtil` 增加 `postJsonBody(...)` 便捷方法统一判空，或逐处补判断。

### 36. `PlayerApi.java:296-307` —— 番剧播放参数 `fnvar` 拼写错误（应为 `fnver`）
**问题描述**：B 站播放参数为 `fnval`/`fnver`，`fnvar` 是非标准参数名（幻觉字段），会被服务端忽略；因 `fnval=1` 已正确提供，功能不受影响。
**修复建议**：删除或改为 `fnver=0`。

### 37. `ConfInfoApi.java:41-43,75-77` —— WBI 缓存三字段非原子写
**问题描述**：`lastWbiQuery/lastWbiWts/lastWbiSignedUrl` 三个 volatile 分开赋值，极端并发下可能读到"query 已换、签名未换"的组合，返回错误签名。
**修复建议**：合并为单个 `volatile` 不可变对象（含三字段）整体替换。

### 38. `DanmakuApi.java:137-158` —— 每次调用新建线程池
**问题描述**：`getAllVideoDanmaku` 每次调用 `Executors.newFixedThreadPool`，且分段数按 `maxDuration/360+1` 计算，恰好整 6 分钟倍数的视频会少取末尾一段（边界为开区间）。
**修复建议**：复用全局线程池；段数改为 `maxDuration/360 + (maxDuration%360==0?0:1)` 后统一 +1 取整覆盖。

### 39. `DynamicApi.java:107` —— 请求体整体打日志（缩进风格问题）
**问题描述**：`Logu.v("publishComplex reqBody=" + reqBody)` 打印完整请求体且代码缩进异常；`publishTextContent/relayVideo` 的三元嵌套可读性差。
**修复建议**：日志降级为 debug 或截断；重构三元表达式。

### 40. `FavoriteApi.java:77-83,242,290,302,315,329,340` / `ReplyApi.java:155,235,247,268` / `LikeCoinFavApi.java:24,33,42,53` / `PrivateMsgApi.java:92,127,225` —— 生产代码残留大量 `Log.e("debug-...")` 调试日志
**问题描述**：收藏/评论/点赞/私信等高频路径每条操作都打 `Log.e`，且多为整段响应体。
**影响**：日志噪音大、拖慢性能，部分含业务数据。
**修复建议**：删除或统一走 `Logu.d` 并按需开启。

### 41. `PlayerApi.java:470-534` —— 字幕/看点/互动图版本各发一次 `/x/player/wbi/v2` 请求
**问题描述**：`getSubtitleLinks`、`getViewPoints`、`getInteractionGraphVersion` 三次调用各自完整请求同一接口。
**修复建议**：合并为一次请求、按需取字段。

### 42. `MessageApi.java:87-89,191,290` —— 大链式 `getJSONObject("data")...getJSONArray("items")` 无中间变量
**问题描述**：一长串无判空链式调用，任何一环缺失都抛 `JSONException`（已声明 throws，但定位难、可读性差）。
**修复建议**：拆变量 + opt 系列。

### 43. `DynamicApi.java:515` —— 双重 JSON 解析无判空
```java
JSONObject live_rcmd = new JSONObject(major.getJSONObject("live_rcmd").getString("content")).getJSONObject("live_play_info");
```
**问题描述**：`content` 为 null 或非 JSON 时抛异常——但外层 major 解析已有 try-catch 降级（Line 563），故降为 Low。
**修复建议**：判空 + 单独 try-catch 提升可读性。

### 44. `HistoryApi.java:30` —— `progress` 为 -1 时拼出空参数 `progress=`
**问题描述**：`"&progress=" + (progress >= 0 ? progress : "")` 生成 `progress=&platform=...`，依赖服务端宽容。
**修复建议**：负值时整段跳过。

### 45. `UserInfoApi.java:298` —— 修改资料请求 Origin 与目标域不一致
**问题描述**：请求 `api.bilibili.com/x/member/web/update`，但 `Origin: https://account.bilibili.com`、`Referer: https://www.bilibili.com/`，两两不一致，可能被风控系统判定异常。
**修复建议**：统一 Origin/Referer 为实际调用来源。

---

## Info（备注 / 确认无误项）

### 46. `DanmakuApi.java:31,43` —— `rnd = currentTimeMillis() * 1000000` 与官方文档一致
`bilibili-API/docs/danmaku/action.md` 明确 `rnd = 当前时间戳*1000000`，代码正确（1.7e18 未溢出 long），审查中一度误判，特此澄清。

### 47. `FavoriteApi.java:117,125` —— `attr` 位判断经核对正确
`bilibili-API/docs/fav/info.md`：位 0 = 私有（1 私有）、位 1 = 默认收藏夹（0 默认）。代码 `(attr & 1) != 0` 跳过私有、`(attr & 2) == 0` 判默认，均正确。

### 48. `ConfInfoApi.java:36-39,54-61,80-88` —— WBI 签名主流程与官方算法一致
MIXIN_KEY_ENC_TAB、img_key+sub_key 拼接、参数排序 + `&wts=` + md5、`w_rid` 追加，均符合 WBI 规范；仅缓存非原子（见 #37）。`Uri.encode` 的允许字符集与 B 站 `encodeURIComponent` 风格一致，含中文 query 时签名与请求 URL 编码一致（OkHttp `HttpUrl.parse` 对 query 内未编码字符会自行编码、不返回 null，已排除 NPE 崩溃风险）。

### 49. `BilibiliIDConverter.java` —— BV/AV 互转算法正确
`table`、位置数组 `{11,10,3,8,4,6}`、`xor=177451812`、`add=8728348608L` 均与公开算法一致；`Math.pow` 用于 58 进制换算，数值在 double 精确范围内，无精度问题。

### 50. `CookiesApi.java:61-65,228` —— `activeCookieInfo` 为未启用死代码
方法内调用已被注释（Line 63、228），且注释自述"payload 是我瞎jb弄得"；当前无实际影响，接入前需重做 payload。

### 51. `NetWorkUtil.java:121-147` —— API<=22 信任所有证书为死代码
minSdk 24 下 `Build.VERSION.SDK_INT > 22` 恒真，该 TrustAll 分支不会执行；但若未来下调 minSdk 会引入中间人风险，建议直接删除。

### 52. `SearchApi.java:33-37,57-61` —— 搜索接口 WBI 签名参数编码提醒
关键词先经 `URLEncoder.encode`（空格→`+`）再交给 `signWBI`，B 站 WBI 规范对空格要求 `%20`。多数场景工作正常，但**含空格关键词的签名兼容性建议真机实测**；若遇 -403 可改为 `%20` 编码。

### 53. `AppTokenRefreshApi.java` —— 整体质量良好
刷新接口、一次性轮换落盘、cookie_info 解析与兼容处理均正确，`updateCookies` 有异常兜底，是本目录中少有的健壮实现，可作其他 API 的参照。

---

## 统计汇总

| 严重度 | 数量 |
| --- | --- |
| Critical | 5 |
| High | 11 |
| Medium | 17 |
| Low | 12 |
| Info | 8 |
| **合计** | **53** |

## Top 5 最严重问题

1. **`CookieRefreshApi.java:99`** — 刷新成功后 `DedeUserID` 为空时 `parseLong` 崩溃，登录态中途断裂。
2. **`FavoriteApi.java:285` / `LikeCoinFavApi.java:48`** — 未登录收藏时 `mid` 后两位截取越界，直接崩溃。
3. **`NetWorkUtil.java:100-109`** — 全局重定向拦截器不关闭原响应，每次 302 泄漏连接，长期运行网络瘫痪。
4. **`ReplyApi.java:153` / `PrivateMsgApi.java:217`** — 评论/私信内容未 URL 编码，含 `&` 等字符时内容被截断、请求参数错乱。
5. **`PrivateMsgApi.java:157`** — 会话列表添加条件写反，服务端一加 `account_info` 字段私信列表即整体变空。

（本报告仅审查，未修改任何源代码。）
