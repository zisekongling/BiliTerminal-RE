# 设计文档：热搜 / 编辑个人资料 / 隐私模式

- 日期：2026-08-15
- 项目：ReBiliClient
- 状态：已获用户确认（方案 A ×3）

## 背景与目标

在 ReBiliClient 中实现三个此前缺失的功能，均以参考实现为蓝本忠实复刻：

1. **热搜**：反编译 APK（`E:\Users\ASUS\Desktop\b\decompiled`）中已有完整实现（HotSearchApi / HotSearchActivity / HotSearchAdapter / HotSearchCard），本仓库内逐份复刻。
2. **编辑个人资料**：参照 BiliTerminal（github `PianoEthan/BiliTerminal` @ 8ade4a1）的枢纽页结构 —— `EditProfileActivity`（头像上传枢纽）+ `EditUserInfoActivity`（昵称/生日/性别）。
3. **隐私模式**：完整复刻 BiliTerminal 行为 —— 设置开关 + 不记录观看历史 + 视频详情走游客 Cookie（隐藏点赞/收藏状态），播放仍用登录 Cookie 保持高清。

三者互不依赖，可独立实现。

## 用户已确认的决策

- 热搜 → 独立页面（方案 A），入口同时加旧版菜单与新版菜单。
- 编辑资料 → 枢纽页结构（方案 A），入口加在"我的"页（旧版卡片 + 新版菜单项）。
- 隐私模式 → 完整复刻（方案 A）。
- 头像选择用 `ACTION_PICK`（BiliTerminal 同款），沿用 BiliTerminal 的错误码文案映射。

## 功能一：热搜

### 新增文件

| 文件 | 说明 |
|---|---|
| `app/src/main/java/com/RobinNotBad/BiliClient/api/HotSearchApi.java` | `getHotSearch(List<HotSearchCard>)` |
| `app/src/main/java/com/RobinNotBad/BiliClient/model/HotSearchCard.java` | 数据模型 |
| `app/src/main/java/com/RobinNotBad/BiliClient/adapter/video/HotSearchAdapter.kt` | 列表适配器 |
| `app/src/main/java/com/RobinNotBad/BiliClient/activity/video/HotSearchActivity.kt` | 热搜页 |
| `app/src/main/res/layout/item_hot_search.xml` | 单元格布局 |

### 接口与解析（照搬反编译 HotSearchApi）

```java
public static boolean getHotSearch(List<HotSearchCard> list) {
    // GET https://api.bilibili.com/x/web-interface/wbi/search/square?limit=50
    // 经 ConfInfoApi.signWBI(...) 签名后 NetWorkUtil.getJson(...)
    // code != 0 || data==null || data.trending==null || data.trending.list==null → return false
    // 遍历 list：
    //   keyword  = optString("keyword", "")
    //   showName = optString("show_name", keyword)
    //   icon     = optString("icon", "")
    //   position = optInt("position", i+1)
    //   heatScore= optLong("heat_score", 0)
    // return !list.isEmpty()
}
```

### HotSearchActivity（复用现有基建）

- 继承 `com.RobinNotBad.BiliClient.activity.base.RefreshMainActivity`（已存在，封装 `activity_simple_main_refresh.xml` / swipeRefreshLayout / recyclerView / setAdapter / setOnRefreshListener / setRefreshing）。
- `setPageName("热搜")`（走 `BaseActivity.setPageName`，存在）。
- 加载：`CenterThreadPool.run { getHotSearch(...); runOnUiThread { setAdapter / notifyDataSetChanged / setRefreshing(false) } }`。
- 失败：`MsgUtil.showMsgLong("获取热搜失败，请稍后重试")` + `setRefreshing(false)`。
- 下拉刷新：`setOnRefreshListener { 重新加载 }`。

### HotSearchAdapter

- 行内四控件：`rankText`（36dp，前三 `#fb7299` 其余 `#8A8A8A`）、`iconView`（21dp，icon 空则 `View.GONE`）、`keywordText`（weight=1，显示 `showName`）、`heatText`。
- 热度格式化：`heat < 10000` 原样；否则 `String.format("%.1f万", heat / 10000.0)`；`heat <= 0` 置空。
- 点击：`Intent(context, SearchActivity::class.java).putExtra("keyword", keyword)`（SearchActivity 已在 272 行支持 `getStringExtra("keyword")` 预填搜索框）。

### item_hot_search.xml

照搬反编译布局：水平 LinearLayout（`selectableItemBackground`，12dp 左右 / 10dp 上下 padding）+ 上述四控件。

### 菜单入口

- `activity/MenuActivity.kt`：`btnNames` 追加 `put("hotsearch", Pair("热搜", HotSearchActivity::class.java))`；`getDefaultSortList()` 追加 `"hotsearch"`（13 → 14 项；`MENU_SORT` 尺寸不匹配时自动重置为默认排序，安全）。
- `ui/menu/MainMenuViewModel.kt`：`buildDefaultMenuItems` 追加 `MenuItemData("hotsearch", "热搜", "icon_ranking", MenuItemType.VIDEO, targetActivity = "video.HotSearchActivity")`（现代菜单经 `Class.forName("com.RobinNotBad.BiliClient.activity.${targetActivity}")` 反射加载遗留 Activity，已确认 193 行）。

### 字符串

- `strings.xml`：`pagename_hot_search = "热搜"`（若沿用硬编码"热搜"则可省略，实现时决定）。

## 功能二：编辑个人资料

### 新增文件

| 文件 | 说明 |
|---|---|
| `activity/user/EditProfileActivity.kt` | 枢纽页：头像展示 + 上传头像 + 编辑签名入口 + 编辑资料入口 |
| `activity/user/EditUserInfoActivity.kt` | 昵称 / 生日 / 性别表单 |
| `res/layout/activity_edit_profile.xml` | 枢纽页布局 |
| `res/layout/activity_edit_user_info.xml` | 表单布局 |

### UserInfoApi 新增两个方法（参照 BiliTerminal 362-536 行）

- `updateUserInfo(String uname, String birthday, String sex, String usersign)`
  - POST `https://api.bilibili.com/x/member/web/update`，`application/x-www-form-urlencoded`。
  - 参数：`csrf=bili_jct`、`x-bili-redirect=1`，可选 `uname`/`birthday`/`sex`/`usersign`（URLEncoder UTF-8）。
  - 性别编码：`1→男`、`2→女`。
  - Cookie 重建：`SESSDATA`、`bili_jct`、`DedeUserID`、`buvid3`（经 `NetWorkUtil.getInfoFromCookie` 从登录 Cookie 提取）。
  - Referer `https://www.bilibili.com/`，Origin `https://account.bilibili.com`，UA 用现成 `NetWorkUtil.USER_AGENT_WEB`。
  - 响应处理：`Content-Encoding` 为 `br` 走 Brotli、`gzip` 走 GZIP 解压，否则 UTF-8；解析失败返回 `{code:-1, message:"JSON解析失败..."}`。
- `uploadAvatar(byte[] imageData, String fileName)`
  - POST `https://api.bilibili.com/x/member/web/face/update`，`MultipartBody.FORM`。
  - 字段：`csrf`、`face`（文件名 + image/jpeg）、`platform=pc`、`csrf_token`。
  - Cookie 重建：`SESSDATA`、`bili_jct`、`DedeUserID`、`buvid3`、`buvid4`、`bili_ticket`。
  - Referer `https://account.bilibili.com/home`，Origin `https://account.bilibili.com`。
  - 响应处理同上（br/gzip 解压）。

> 注：BiliTerminal 实现里遗留大量 `Log.e` 调试日志，复刻时去除（本仓库无此风格）。

### EditProfileActivity

- 未登录（`SharedPreferencesUtil.getLong(mid,0)==0`）→ `MsgUtil.showMsg("还没有登录喵~")` + `finish()`。
- 头像展示：`SharedPreferencesUtil.getString("avatar","")` 非空则 Glide 圆图加载（`GlideUtil.url` + circleCrop，DiskCacheStrategy.NONE）。
- 上传入口：`cookie_refresh == false` 时弹窗拒绝；`ACTION_PICK` 选图（`REQUEST_CODE_PICK_IMAGE = 1001`）→ `BitmapFactory.decodeStream` → JPEG 90% 压缩 → `fileName = "avatar_"+时间戳+".jpg"` → `UserInfoApi.uploadAvatar`。
- 成功（code==0）：`MsgUtil.showMsg("头像上传成功，等待审核")`；若 data.url 非空则存 `SharedPreferencesUtil.putString("avatar", url)` 并刷新头像。
- 失败错误码映射：-101 账号未登录 / -102 CSRF校验失败 / -111 图片格式不支持 / -112 图片过大 / -400 请求参数错误 / -403 CSRF验证失败 / 其他用 message，兜底"上传失败"，提示 `"(错误码:"+code+")"`。
- 卡片跳转：编辑签名 → `EditSignActivity`；编辑资料 → `EditUserInfoActivity`。
- 返回：`pageName` 点击 `finish()`。

### EditUserInfoActivity

- 未登录早退同 EditProfileActivity。
- 预填：`UserInfoApi.getCurrentUserInfo()` 成功则 `etUsername.setText(name)`。
- 表单：昵称 `et_username`、生日 `et_birthday`（正则 `\d{4}-\d{2}-\d{2}`，不匹配提示"生日格式错误，请使用YYYY-MM-DD"）、性别 `rg_sex`（rb_male→1 / rb_female→2 / 未选→null）。
- 提交前校验：`cookie_refresh` 检查；三项全空提示"请至少填写一项要修改的内容"；空项以 null 提交。
- 成功：`MsgUtil.showMsg("修改成功")` + `finish()`。
- 失败错误码映射：-101 账号未登录 / -111 CSRF验证失败 / 400 昵称违规或已被占用 / 412 修改频率过高，请稍后再试 / 2001 昵称已存在 / 21003 生日格式错误 / -403 权限不足 / 其他 message，兜底"修改失败"。
- 提交防重入：`isSubmitting` + `submit.setEnabled(false)`。

### 布局

- `activity_edit_profile.xml`：TopBar（`pageName` + `timeText`）+ `RotaryScrollView` + LinearLayout；三张 `MaterialCardView`（`upload_avatar` / `edit_sign` / `edit_user_info`）＋头像圆图 `ImageView`（`upload_avatar_icon`）。
- `activity_edit_user_info.xml`：TopBar + RotaryScrollView；`et_username`、`et_birthday`（inputType 日期）、`rg_sex`（rb_male / rb_female）、提交 `submit` 卡片。沿用 `activity_edit_sign.xml` 视觉风格（background_edittext、MaterialCardView 提交键）。

### 入口

- `activity/user/MySpaceActivity.kt` + `res/layout/activity_myspace.xml`：新增 `edit_profile` 卡片（位于 `edit_sign` 卡片旁），点击 → `EditProfileActivity`。
- `ui/user/MySpaceViewModel.kt`：`getMenuItems` 在 `edit_sign` 后加 `MenuAction("edit_profile", "编辑资料", targetClassName = "com.RobinNotBad.BiliClient.activity.user.EditProfileActivity")`。

### 字符串

- `pagename_edit_profile`、`pagename_edit_user_info`、头像上传相关提示、错误码文案（实现时按 EditSign 现有文案风格并入 `strings.xml`）。

## 功能三：隐私模式

### 修改文件

| 文件 | 改动 |
|---|---|
| `util/SharedPreferencesUtil.java` | 加常量 `PRIVACY_MODE = "privacy_mode"` |
| `util/NetWorkUtil.java` | 新增 `getJsonPrivacy(String url)` |
| `api/HistoryApi.java` | `reportHistory` 隐私早退 |
| `api/VideoInfoApi.java` | `getVideoInfo(bvid)` / `getVideoInfo(aid)` 隐私分支 |
| `activity/settings/SettingPrefActivity.kt` | 新增"隐私模式"开关 |

### getJsonPrivacy 实现

```java
public static JSONObject getJsonPrivacy(String url) throws IOException, JSONException {
    ArrayList<String> headers = new ArrayList<>(webHeaders);
    headers.set(1, buildGuestCookieString());
    // 复用现有 getBodyStringWithDoctypeRetry(url, headers) 流程
    // 空响应 → throw new JSONException("在访问" + url + "时返回数据为空")
}

// 游客 Cookie：从 getCookies()（Cookies Map）剔除登录相关项后拼接
// 剔除：SESSDATA / bili_jct / DedeUserID / DedeUserID__ckMd5 / sid
// 保留：buvid3 / buvid4 / bili_ticket / bili_ticket_expires / _uuid / b_lsid / buvid_fp / b_nut / LIVE_BUVID / browser_resolution 等
```

### reportHistory

```java
public static void reportHistory(long aid, long cid, long progress) throws IOException {
    if (SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.PRIVACY_MODE, false)) return;
    // 原逻辑不变
}
```

### VideoInfoApi

两处 `getVideoInfo` 重载内：

```java
boolean privacyMode = SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.PRIVACY_MODE, false);
String json = privacyMode ? NetWorkUtil.getJsonPrivacy(url).toString() : NetWorkUtil.getJson(url).toString();
```

`LikeCoinFavApi.getVideoStats(videoInfo)` 不改（忠实 BiliTerminal，其内部仍用完整 Cookie）。

### 设置项

- `SettingPrefActivity`（基于 `SettingSection`/`SettingsAdapter` 的列表）新增"隐私模式"开关，键 `SharedPreferencesUtil.PRIVACY_MODE`。
- desc 文案复刻 BiliTerminal：`desc_privacy_mode = "开启后，浏览视频详情和播放视频时不会记录到B站历史记录中。\n仍可享受登录账号的高清画质，但视频详情页不会显示你的点赞/投币/收藏状态。"`

## 范围外（明确不做）

- 热搜不做搜索页内嵌（方案 B 驳回）。
- 隐私模式不改动播放地址请求（保高清），不改动 `getVideoStats`。
- 编辑资料不做单页合一（方案 B 驳回）。
- 不引入新第三方库；头像压缩用 `Bitmap.compress`，网络复用 `NetWorkUtil.getOkHttpInstance()`。

## 风险与验证

- **风险**：`x/member/web/update` 与 `face/update` 对 Cookie/CSRF 敏感，Cookie 重建不全可能导致 400/412。应对：严格按 BiliTerminal 字段重建，预留错误码映射文案。
- **风险**：`wbi/search/square` 依赖 WBI 签名，签名失败返回非 0 code → 热搜列表失败提示。应对：复用 `ConfInfoApi.signWBI`（本仓库已有）。
- **风险**：隐私模式游客 Cookie 拼接错误可能导致视频详情请求被风控。应对：保留全部非登录 Cookie，仅剔除 5 个登录项。
- **验证**：项目无单元测试框架；以 `gradle assembleDebug` 编译通过 + 真机手测为准（热搜列表/点击跳搜索、头像上传、昵称生日性别修改、隐私开关三行为）。

## 文件清单汇总

新增 10 文件：HotSearchApi.java / HotSearchCard.java / HotSearchAdapter.kt / HotSearchActivity.kt / item_hot_search.xml / activity_edit_profile.xml / activity_edit_user_info.xml / EditProfileActivity.kt / EditUserInfoActivity.kt。

修改 10 文件：MenuActivity.kt / MainMenuViewModel.kt / MySpaceActivity.kt / activity_myspace.xml / MySpaceViewModel.kt / SharedPreferencesUtil.java / NetWorkUtil.java / HistoryApi.java / VideoInfoApi.java / SettingPrefActivity.kt / UserInfoApi.java / strings.xml（修改数按实际落地计数）。
