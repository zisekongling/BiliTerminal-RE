# 功能审查：登录/账号、设置、通用组件/对话框、基础类（group-08）

> 审查对象：RE:哔哩终端（ReBiliClient）第三方 B 站安卓客户端。
> 本文件以极其细小的颗粒度，逐文件列出每个页面/组件的全部"功能-用法"条目。
> 涉及目录：`activity/settings/login/`、`activity/settings/`、`activity/settings/setup/`、`activity/`（通用组件）、`activity/base/`（基础类）。

---

## 一、登录/账号相关

### LoginActivity.kt
- 功能：登录入口容器页，用 ViewPager 横向承载三种登录方式（扫码 / 密码 / 短信）。
  - 用法：启动后加载 `activity_simple_viewpager` 布局，`ViewPager` 内加入 `QRLoginFragment`（扫码）、`PasswordLoginFragment`（密码）、`SMSLoginFragment`（短信）三个 Fragment；页面名设为"登录"。
- 功能：左右滑动切换登录方式。
  - 用法：`offscreenPageLimit = 3` 预加载全部三个 Fragment；首次进入（`first_LoginActivity` 为 true）时用长 Toast 提示"本页面可以左右滑动切换登录方式"，之后写入 `first_LoginActivity=false` 不再提示。
- 功能：接收 `from_setup` 布尔 Extra。
  - 用法：`intent.getBooleanExtra("from_setup", false)`，透传给三个登录 Fragment，用于区分"引导流程进入"还是"普通进入"（影响部分按钮的返回行为）。
- 功能：隐藏加载圈。
  - 用法：`findViewById(R.id.loading).visibility = GONE`。

### QRLoginFragment.kt
- 功能：扫码登录（WEB 端 / TV 端两种模式）。
  - 用法：创建时 `newInstance(from_setup)` 传入 `from_setup` 参数；`onViewCreated` 中自动调用 `refreshQrCode()` 请求二维码。
- 功能：点击"切换账号"卡片（`switchAccount`）。
  - 用法：跳转 `AccountSwitchActivity`，取消扫码轮询 Timer，并 `finish()` 当前登录页。
- 功能：点击"跳过/直接进入"卡片（`jump`）。
  - 用法：若 `from_setup` 为 true 则跳转 `SplashActivity`（完成引导）；取消 Timer 并结束登录页。
- 功能：点击"特殊登录"卡片（`special`）。
  - 用法：跳转 `SpecialLoginActivity` 并携带 `from_setup` 参数；取消 Timer 并结束登录页。
- 功能：切换登录模式（`loginModeSwitch`）。
  - 用法：点击在 `isTVMode` 取反；更新按钮文案（"TV端扫码登录"/"WEB端扫码登录"）与状态文字；置灰二维码并重新 `refreshQrCode()`。
- 功能：查看登录方式说明（`helpIcon`）。
  - 用法：点击跳转 `QRLoginHelpActivity`。
- 功能：点击二维码交互。
  - 用法：若二维码失效（`need_refresh`）则重新获取；否则循环切换二维码大小：默认 → 大（guideline 0~1）→ 小（0.30~0.70）→ 默认（0.15~0.85），并 Toast 提示"切换为大/小/默认二维码"。
- 功能：获取二维码（`refreshQrCode`）。
  - 用法：TV 模式调 `LoginApi.getTVLoginQR()`；WEB 模式先 `CookiesApi.checkCookies()`（失败不阻塞）再 `LoginApi.getLoginQR()`；成功后显示 Bitmap 并启动轮询 `startLoginDetect()`。失败分类提示：网络错误 / "登录接口可能失效，请找开发者" / 其他错误，并允许点击重试。
- 功能：轮询登录状态（`startLoginDetect`）。
  - 用法：`Timer` 每 500ms 后每 1s 轮询一次；TV 模式调 `getTVLoginState()`，WEB 模式调 `getLoginState()`。
- 功能：处理 WEB 登录状态码。
  - 用法：86090=已扫描请手机确认；86101=请用官方手机端扫码；86038=二维码失效可点击重获；0=登录成功（从 Cookie 解析 `DedeUserID`→mid、`bili_jct`→csrf，保存 `refresh_token`，置 `cookie_refresh=true`、`setup=true`，`AccountManager.saveCurrentAccount()`，`NetWorkUtil.refreshHeaders()`，`LoginApi.requestSSOs()`，请求 data.url 完成 SSO，跳转 `SplashActivity` 并结束登录页）；其他=提示 API 可能变动。
- 功能：处理 TV 登录状态码。
  - 用法：86039=请用手机端扫码；86090=已扫描请确认；86038=失效可重获；0=成功（保存 `access_key`、`refresh_token`、`mid`，从 `cookie_info.cookies` 写入 Cookie 与 `bili_jct`，置 `cookie_refresh=true`、`setup=true`，`AccountManager.saveCurrentAccount()`，关闭栈顶 `InstanceActivity`，`requestSSOs()`，跳转 `SplashActivity` 并结束）。
- 功能：释放资源。
  - 用法：`onDestroy()` 中取消 Timer。

### PasswordLoginFragment.kt
- 功能：账号密码登录。
  - 用法：`newInstance(from_setup)`；页面含用户名输入框（`usernameInput`）、密码输入框（`passwordInput`）、状态文字（`statusText`）、确认按钮（`confirmBtn`）、"切换到短信登录"按钮（`switchToSmsBtn`）。
- 功能：校验输入。
  - 用法：点"确认"后若账号或密码为空，提示"请输入账号和密码"，否则进入验证码流程。
- 功能：切换至短信登录。
  - 用法：点击 `switchToSmsBtn` 把宿主 ViewPager `currentItem=2`（跳到短信页）。
- 功能：极验验证码流程（`startCaptcha`）。
  - 用法：先 `checkCookies()`（失败不阻塞）再 `LoginApi.getCaptcha()`；解析出 `token`、`gt`、`challenge` 后跳转 `CaptchaWebViewActivity` 并携带 `EXTRA_GT`、`EXTRA_CHALLENGE`，通过 `ActivityResultLauncher` 等待结果。
- 功能：处理验证码返回。
  - 用法：`RESULT_OK` 时取回 `RESULT_CHALLENGE/VALIDATE/SECCODE` 并调 `doPasswordLogin`；否则提示"验证已取消"。
- 功能：执行密码登录（`doPasswordLogin`）。
  - 用法：拼 seccode（无 `|jordan` 则补 `validate|jordan`）；`LoginApi.getWebKey()` 取 hash+公钥；`PasswordEncryptUtil.encryptPassword` 加密密码；`LoginApi.passwordLogin(...)`。
- 功能：错误码提示。
  - 用法：0=成功；-629=账号或密码错误；-662=提交超时请重试；-105=验证码错误请重试；其他=显示 message；网络错误/接口异常均有提示。
- 功能：风险验证处理（`handleRiskVerification`）。
  - 用法：`status==2` 时若 message 含"手机号"提示"需要手机号验证，请使用短信验证码登录"，否则提示风险信息。
- 功能：登录成功处理（`handleLoginSuccess`）。
  - 用法：从 Cookie 解析 mid/csrf，保存 `refresh_token`（优先 token_info），置 `cookie_refresh=true`、`setup=true`，`AccountManager.saveCurrentAccount()`、`NetWorkUtil.refreshHeaders()`、`requestSSOs()`，关闭栈顶实例，跳 `SplashActivity` 并结束。

### SMSLoginFragment.kt
- 功能：短信验证码登录。
  - 用法：`newInstance(from_setup)`；含手机号输入（`phoneInput`）、验证码输入（`smsCodeInput`）、发送验证码按钮（`sendSmsBtn`）、状态文字、确认按钮（`confirmBtn`）、"切回密码登录"按钮（`switchToPwdBtn`）。
- 功能：发送验证码。
  - 用法：点 `sendSmsBtn` 校验手机号（非空且长度≥11）；`getCaptchaAndVerify()` 先取极验验证码并跳 `CaptchaWebViewActivity`。
- 功能：验证码返回后自动发送短信（`doSendSms`）。
  - 用法：`RESULT_OK` 取回 challenge/validate/seccode，置 `captchaReady=true` 并 `doSendSms()`；`LoginApi.smsSend(phone, token, challenge, validate, seccode)` 成功后保存 `captcha_key` 并启动 60s 倒计时；-105=验证码错误；其他=发送失败。
- 功能：倒计时重发（`startCountDown`）。
  - 用法：60 秒倒计时，期间按钮显示"N s后重发"并禁用，结束恢复"获取验证码"可点。
- 功能：确认登录（`doSmsLogin`）。
  - 用法：校验手机号与验证码非空、`captchaReady` 为 true；`LoginApi.smsLogin(phone, code, captchaKey)`；0=成功走 `handleLoginSuccess`；1006=请输入正确的短信验证码；1007=短信验证码已过期；其他=失败提示。
- 功能：登录成功处理。
  - 用法：同密码登录成功流程（解析 Cookie mid/csrf、保存 refresh_token、置 setup、保存账号、刷新 header、requestSSOs、跳 Splash）。
- 功能：释放资源。
  - 用法：`onDestroy()` 取消倒计时 Timer。

### AccountSwitchActivity.kt
- 功能：账号切换/管理页面。
  - 用法：页面标题区（`pageName`）点击 `finish()`；进入/回到前台时 `refreshAccountList()` 重建账号列表。
- 功能：展示已保存账号卡片（`createAccountCard`）。
  - 用法：每账号一张 `item_account` 卡片，显示头像（Glide 圆形）、名称（带"（当前）"标记）、UID；无本地头像/昵称时后台 `UserInfoApi.getUserInfo` 拉取补全。
- 功能：切换账号。
  - 用法：点击非当前账号卡片 → `AccountManager.switchToAccount(account)`，Toast"已切换至 xxx"，带 `CLEAR_TASK|NEW_TASK` 跳转 `SplashActivity` 并结束本页；点击当前账号提示"这是当前登录的账号"。
- 功能：删除账号（长按卡片）。
  - 用法：长按卡片 → AlertDialog"删除账号"确认 → `AccountManager.removeAccount(mid)` → 刷新列表；仅当账号数>1 或当前未登录（mid==0）时可删。
- 功能：空列表提示。
  - 用法：无账号时显示"暂无已保存的账号\n登录后会自动保存账号凭证"。
- 功能：添加新账号。
  - 用法：列表底部"添加新账号"卡片点击跳 `LoginActivity`。

### CaptchaWebViewActivity.kt
- 功能：极验（Geetest）人机验证 WebView 页。
  - 用法：`setPageName("人机验证")`；接收 `EXTRA_GT`、`EXTRA_CHALLENGE` 两个 String Extra，缺失时 Toast"验证参数错误"并以 `RESULT_CANCELED` 结束。
- 功能：缩放验证页（放大/缩小/重置按钮）。
  - 用法：`btnZoomIn`/`btnZoomOut` 每次 ±25%（范围 50%~300%），`btnZoomReset` 回 100%；通过 JS 修改 viewport 的 initial-scale。
- 功能：加载验证码 HTML。
  - 用法：内嵌 HTML 注入 gt/challenge，动态加载 geetest 的 `gt.0.4.9.js` 与 `click.3.1.2.js`，`initGeetest` popup 弹出式点击验证，失败可点"重试"。
- 功能：WebView 配置。
  - 用法：开启 JS、DOM Storage、允许文件/内容访问、混合内容、`LOAD_NO_CACHE`，自定义 UA，接受 Cookie 与第三方 Cookie，注入 `Android` JS 接口。
- 功能：回传验证结果。
  - 用法：验证成功时 JS 调 `Android.onCaptchaResult(challenge, validate, seccode)`，用 `RESULT_OK` + `RESULT_CHALLENGE/RESULT_VALIDATE/RESULT_SECCODE` 返回；失败调 `Android.onCaptchaError(msg)` 以 `RESULT_CANCELED` 结束。
- 功能：释放资源。
  - 用法：`onDestroy()` 销毁 WebView。

### QRLoginHelpActivity.kt
- 功能：登录方式说明页。
  - 用法：异步加载 `activity_qr_login_help`，标题"登录方式说明"，正文为说明文案；`close_btn` 点击 `finish()`。
- 功能：展示说明内容（`getHelpContent`）。
  - 用法：固定文案说明 WEB 端扫码（Cookie 认证，适合评论/收藏/关注等接口）、TV 端扫码（额外获得 access_token，适合短视频等 OAuth 接口）、Access Token 存储（`access_key`，"我的"页显示状态）、两种方式区别（推荐 TV 端）。

### SpecialLoginActivity.kt
- 功能：特殊登录（Cookie 导入 / 登录信息导出查看）。
  - 用法：通过 `login` 布尔 Extra 区分模式；`true`=导入登录，`false`=导出查看。
- 功能（导入模式 `login=true`）：粘贴 JSON 登录凭证登录。
  - 用法：`textInput` 输入 JSON（含 `cookies`、`refresh_token`、可选 `access_key`）；点"确认"解析 → 写 mid（从 Cookie `DedeUserID`）、csrf（`bili_jct`）、`NetWorkUtil.setCookiesString`、`refresh_token`、可选 `access_key` → Toast"登录成功！" → 置 `setup=true` → `AccountManager.saveCurrentAccount()` → 跳 `SplashActivity` 并结束；JSON 解析失败提示"请检查输入的内容"。
- 功能（导入模式）：拒绝/返回。
  - 用法：点"拒绝"：`from_setup=true` 时跳 `SplashActivity`，否则 `finish()`。
- 功能（导出模式 `login=false`）：查看登录信息。
  - 用法：页面描述设为 `special_login_export`；自动把 `cookies`、`refresh_token`、`access_key` 组装成 JSON 填入输入框并清空焦点；隐藏"拒绝"按钮。
- 功能（导出模式）：复制登录信息。
  - 用法：点"复制"把 JSON 写入系统剪贴板并 Toast"已复制"。
- 功能（导出模式）：调试导入（仅 Debug）。
  - 用法：`isDebugBuild()` 时才显示"确认"按钮，可编辑 JSON 后导入 Cookie/refresh_token/access_key 并 `refreshHeaders()`；否则隐藏确认按钮。

---

## 二、设置相关

### SettingMainActivity.kt
- 功能：设置主入口页（含搜索）。
  - 用法：`InstanceActivity`，异步加载 `activity_setting_main`；`SettingsIndex.build()` 生成可搜索索引；顶部搜索框 `search_input` 带 TextWatcher。
- 功能：设置搜索过滤（`filter`）。
  - 用法：输入非空时遍历 `entries`，按 `name`/`desc` 包含匹配，命中项以搜索结果卡片展示（放大镜图标 + 标题 + 描述 + 箭头），点击 `entry.open(this)` 跳转并定位。
- 功能：一级分组入口。
  - 用法：`addGroup` 渲染 `cell_group_header` 卡片，点击带 `group_type`/`group_title` 跳 `SettingGroupActivity`（或直接跳指定页面）。
- 功能：播放与播放器分组。
  - 用法：图标播放器，描述"选择播放器、清晰度与内置播放器设置"，直接跳 `SettingPlayerChooseActivity`。
- 功能：账号与登录分组。
  - 用法：图标人，描述"登录、切换账号与查看登录信息"，跳分组 `account`。
- 功能：界面与外观分组。
  - 用法：图标 UI，描述"界面大小、主题与动画效果"，跳分组 `ui`。
- 功能：内容与浏览分组。
  - 用法：图标首页，描述"菜单、搜索、详情页、评论区与偏好"，跳分组 `content`。
- 功能：缓存与下载分组。
  - 用法：图标下载，描述"下载引擎、缓存选项与存储路径"，跳分组 `download`。
- 功能：高级与实验分组。
  - 用法：图标实验室，描述"不保证能用或者用于开发调试的功能"，跳分组 `lab`。
- 功能：关于与帮助分组。
  - 用法：图标信息，描述"版本信息、更新、公告与教程"，跳分组 `about`。
- 功能：开发者工具分组（仅 Debug）。
  - 用法：`isDebugBuild()` 时才显示，描述"功能测试、待办清单与调试日志"，跳分组 `dev`。

### SettingsIndex.kt
- 功能：全局设置搜索索引数据源。
  - 用法：`SettingsIndex.build()` 返回 `Entry(name, desc, open)` 列表，供 `SettingMainActivity` 搜索；`open` 为跳转动作（直接跳页面或 `openGroup` 带 `highlight`）。
- 功能：一级分组条目。
  - 用法：播放与播放器 / 账号与登录 / 界面与外观 / 内容与浏览 / 缓存与下载 / 高级与实验 / 关于与帮助 各一条，点击开对应分组或页面。
- 功能：子页面条目。
  - 用法：选择播放器、清晰度、内置播放器设置、登录、账号切换、查看登录信息、菜单设置、搜索设置、详情页设置、评论区设置、通用偏好、关于、检查更新、公告列表、教程管理 —— 每条直接跳对应 Activity。
- 功能：界面与外观内联项。
  - 用法：圆屏适配、界面大小、界面边距（横向）、界面边距（纵向）、设置Density、主题配色、横屏模式、开屏文字、文字跑马灯、加载渐入渐出动画 —— 打开 `ui` 分组并 `highlight` 定位。
- 功能：缓存与下载内联项。
  - 用法：启用高速下载模式、使用旧版下载器、最大同时下载数、分片数、启用快捷缓存方式、并行下载视频数、默认缓存质量、强制高分辨率选项、缓存路径、图片下载路径 —— 打开 `download` 分组定位。
- 功能：高级与实验内联项。
  - 用法：高性能模式、推荐视频去重、推荐源、新版弹幕获取方式、私信未读标记、虚拟合集、播放器旋屏兼容方案、显示视频分段、系统媒体控件、互动视频调试 —— 打开 `lab` 分组定位。
- 功能：叶子设置项（详情页/评论区/偏好/播放器）。
  - 用法：`addLeafItems` 给每条配置"打开对应设置页并 highlight 定位"；详情页（收藏夹单选/收藏成功提示/点击封面播放/显示视频标签/视频相关推荐/以游客方式观看直播/一键三连）、评论区（众生平等/粉丝铭牌消失术/昵称不换行显示）、偏好（长按复制/创作中心/搜索建议/默认搜索内容/识别链接/隐私模式/新动态数量检查/消息数量检查/最近更新的UP主/私信自动已读/夜深了/后台自动检查更新/禁用返回键/禁止视频在相册中显示/请求JPG格式图片/翻动时不加载图片/异步加载布局/新提示信息显示方式/我的关注列表分组/启用表冠适配/表冠适配灵敏度（Recycler）/表冠适配灵敏度（Scroll））、内置播放器（长按倍速/双击快进快退/双击优先还原屏幕/快进快退秒数/洗脑循环/熄屏继续播放/默认横屏/从历史位置播放/显示实时人数/听视频模式/视频可缩放/缩放时可移动/显示方式/解码方式/音频输出/显示高能进度条/弹幕允许重叠/合并重复弹幕/强制为滚动弹幕/显示直播弹幕发送者/弹幕最大行数/弹幕字号大小/弹幕不透明度/弹幕速度/自动弹出字幕选择/允许仅AI字幕/字幕校准/显示旋转按钮/显示弹幕按钮/显示清晰度按钮/显示分P按钮/互动选项字体大小）。
- 功能：开发者工具条目（仅 Debug）。
  - 用法：开发者工具分组、功能测试（跳 `TestActivity`）、TO DO清单（跳 `TodoListActivity`）。

### SettingGroupActivity.kt
- 功能：分组设置页（数据驱动）。
  - 用法：`RefreshListActivity`，接收 `group_type`/`group_title`/`highlight` Extra；按 `group_type` 调对应 `buildXxxGroup()` 生成 `SettingSection` 列表，`SettingsAdapter` 渲染；`highlight` 非空时滚动定位到同名设置项。
- 功能：账号与登录分组。
  - 用法：未登录（mid==0）时显示"登录"入口（SDK>=19 跳 `LoginActivity`，否则跳 `SpecialLoginActivity` 带 `login=true`）；"账号切换"跳 `AccountSwitchActivity`；已登录时显示"查看登录信息"（跳 `SpecialLoginActivity` 带 `login=false`）；"登录凭证状态"弹对话框展示 Cookie（是否含 SESSDATA）/Access Token（前8位）/UID/CSRF/Refresh Token 状态。
- 功能：界面与外观分组。
  - 用法："恢复默认"按钮（清空边距/DPI/Density/圆屏）；"查看预览"按钮跳 `UIPreviewActivity`；"布局适配"标题；"圆屏适配"开关（开启时自动设边距 5/3 并提示可微调）；"界面大小"输入（0.25~5.0 的 float，写入 DPI 并置 `DPI_FORCE_CHANGE`）；"界面边距（横向）"输入（int≤30）；"界面边距（纵向）"输入（int≤30）；"设置Density"输入（≥72）；"主题配色"列表选择（B站粉/知乎蓝/爱奇艺绿/紫色空灵/五彩斑斓/经典灰，切换后 commit 并 `recreate()`）；"横屏模式"开关；"开屏文字"输入（默认"欢迎使用\nRE:哔哩终端"）；"文字跑马灯"开关；"加载渐入渐出动画"开关。
- 功能：内容与浏览分组。
  - 用法：菜单设置（跳 `SettingMenuActivity`）、搜索设置（`SettingSearchActivity`）、详情页设置（`SettingInfoActivity`）、评论区设置（`SettingRepliesActivity`）、通用偏好（`SettingPrefActivity`）五个导航入口。
- 功能：缓存与下载分组。
  - 用法：标题"下载引擎"；"启用高速下载模式"开关（`aria2_enable`）；"使用旧版下载器"开关；"分片数"输入（int 默认 5）；标题"缓存选项"；"启用快捷缓存方式"开关（`CACHE_QUICK_MODE`，切换后重建分组）；"并行下载视频数"输入；开启快捷缓存时显示"默认缓存质量"列表（最高/720P/360P/仅音频/每次询问 → highest/64/16/audio_only/dialog）；"强制高分辨率选项"开关；标题"存储路径"；"缓存路径"输入；"图片下载路径"输入。
- 功能：高级与实验分组。
  - 用法：标题"性能"；"高性能模式"开关（调 `PerformanceManager.setHighPerformanceMode`，提示下次启动生效/性能优先）；标题"推荐"；"推荐视频去重"开关；"推荐源"列表（网页源/APP源/混合使用）；标题"功能开关"；"新版弹幕获取方式"开关；"私信未读标记"开关；"虚拟合集"开关；标题"播放器兼容"；"播放器旋屏兼容方案"开关；"显示视频分段"开关；"系统媒体控件"开关；"互动视频调试"开关。
- 功能：关于与帮助分组。
  - 用法："关于"导航（点击跳 `AboutActivity`；长按显示"回声洞"彩蛋文案，逐条递增）；"检查更新"跳 `UpdateActivity`；"公告列表"跳 `AnnouncementsActivity`；"教程管理"跳 `TutorialManagerActivity`。
- 功能：开发者工具分组（仅 Debug）。
  - 用法："功能测试"跳 `TestActivity`；"TO DO清单"跳 `TodoListActivity`；标题"调试日志"；"允许Logu.v"开关、"允许Logu.d"开关、"允许Logu.i"开关、"详细显示数据解析报错"开关、"详细显示列表报错"开关。
- 功能：列表选择回调。
  - 用法：`onActivityResult(requestCode==1001)` 从 `ListChooseActivity` 拿回 `position`/`value`，写入 SharedPreferences 并回调 `onSelect`。

### SettingPrefActivity.kt
- 功能：通用偏好设置页。
  - 用法：`RefreshListActivity`，标题"偏好设置"；分组"功能"：长按复制、创作中心、搜索建议、默认搜索内容、识别链接、隐私模式 开关；分组"更新提醒"：新动态数量检查、消息数量检查、最近更新的UP主、私信自动已读、夜深了、后台自动检查更新 开关；分组"交互优化"：禁用返回键、禁止视频在相册中显示、请求JPG格式图片、翻动时不加载图片、异步加载布局、新提示信息显示方式 开关；分组"视觉"：我的关注列表分组 开关；分组"表冠适配"：启用表冠适配 开关、表冠适配灵敏度（Recycler）输入、表冠适配灵敏度（Scroll）输入。
- 功能：搜索定位。
  - 用法：`scrollToHighlight(sectionList, intent.getStringExtra("highlight"))`。

### SettingRepliesActivity.kt
- 功能：评论区设置页。
  - 用法：标题"评论区设置"；三个开关：众生平等（`NO_VIP_COLOR`，隐藏 VIP 颜色）、粉丝铭牌消失术（`NO_MEDAL`，隐藏粉丝铭牌）、昵称不换行显示（`REPLY_MARQUEE_NAME`）；支持 `highlight` 定位。

### SettingInfoActivity.kt
- 功能：详情页设置页。
  - 用法：标题"详情页设置"；七个开关：收藏夹单选、收藏成功提示、点击封面播放、显示视频标签、视频相关推荐、以游客方式观看直播、一键三连；支持 `highlight` 定位。

### SettingMenuActivity.kt
- 功能：菜单顺序/启用管理页。
  - 用法：`BaseActivity`，加载 `activity_setting_menu`；`MenuSettingAdapter` 展示已启用菜单项，`ItemTouchHelper` 支持长按拖拽排序；`onChanged` 回调 `SharedPreferencesUtil.saveMenuEnabled(enabled)` 持久化；顶部 `top` 点击 `finish()`。
- 功能：菜单项名称显示。
  - 用法：通过 `MenuActivity.btnNames[key]?.first ?: key` 映射显示名称。

### SettingSearchActivity.kt
- 功能：搜索设置页。
  - 用法：异步加载 `activity_setting_search`；四个 SwitchMaterial：专栏（`search_article`）、用户（`search_user`）、音频（`search_audio`）、直播（`search_live`），初始值从 `SEARCH_CATEGORY_*_SHOW` 读取（默认 true）。
- 功能：调整排序。
  - 用法：`sort` 按钮跳 `SearchSortActivity`。
- 功能：重置默认。
  - 用法：`reset` 按钮把四个开关全设为 true 并把 `SEARCH_CATEGORY_SORT` 置空，Toast"已重置为默认设置"。
- 功能：持久化保存。
  - 用法：`onDestroy()` 时 `save()` 写入四个开关状态（视频类别始终启用）。

### SearchSortActivity.kt
- 功能：搜索类别排序页。
  - 用法：标题"搜索类别排序"；读取 `SEARCH_CATEGORY_SORT`（分号分隔），非法/为空时用默认顺序（video,article,user,audio,live）；`DragAdapter` 列表展示类别中文名。
- 功能：拖拽排序。
  - 用法：长按列表项拖动；`SearchDragCallBack` 仅允许上下拖动、禁用滑动删除；视频（位置0）固定不可拖动/不可被移动到首位；拖动时项放大 1.3 倍，松手还原。
- 功能：保存排序。
  - 用法：数据变化 `onChanged()` 与 `onPause()` 时 `save()` 写回 `SEARCH_CATEGORY_SORT`；进入/退出有 Toast 提示。

### SettingTerminalPlayerActivity.kt
- 功能：内置播放器设置页。
  - 用法：标题"内置播放器设置"；大量设置项（见下）。
- 功能：交互设置。
  - 用法：长按倍速 开关；双击快进快退 开关；双击优先还原屏幕 开关；快进快退秒数 输入（int 默认 10）；洗脑循环 开关；熄屏继续播放 开关；默认横屏 开关；从历史位置播放 开关；显示实时人数 开关；听视频模式 开关；视频可缩放 开关；缩放时可移动 开关。
- 功能：渲染/解码/音频选择。
  - 用法："显示方式" 列表（TextureView/SurfaceView）；"解码方式" 列表（硬件解码/软件解码）；"音频输出" 列表（OpenSles/AudioTrack）。
- 功能：弹幕设置。
  - 用法：显示高能进度条 开关；弹幕允许重叠 开关；合并重复弹幕 开关；强制为滚动弹幕 开关；显示直播弹幕发送者 开关；弹幕最大行数 输入（int 默认 10）；弹幕字号大小 输入（float 0.7）；弹幕不透明度 输入（float 0.5）；弹幕速度 输入（float 1.0）。
- 功能：字幕设置。
  - 用法：自动弹出字幕选择 开关；允许仅AI字幕 开关；字幕校准 输入（float 0.3，说明"将字幕提前/退后一段时间"）。
- 功能：界面按钮设置。
  - 用法：显示旋转按钮、显示弹幕按钮、显示清晰度按钮、显示分P按钮 开关；互动选项字体大小 输入（float 17.0）。
- 功能：搜索定位。
  - 用法：`scrollToHighlight(...)` 支持 highlight 定位。

### SettingPlayerChooseActivity.kt
- 功能：选择播放器页。
  - 用法：异步加载 `activity_setting_player_choose`；三张卡片：内置播放器（`terminalPlayer`）、小电视播放器（`mtvPlayer`）、凉腕播放器（`aliangPlayer`）；当前 `player` 值高亮描边。
- 功能：选择清晰度入口。
  - 用法：`qn_choose` 卡片点击跳 `SettingQualityActivity`；显示当前清晰度名（从 `play_qn` 反查 `qnMap`）。
- 功能：选择播放器。
  - 用法：点卡片 `setChecked` 高亮并保存 `player` 值（null/terminalPlayer/mtvPlayer/aliangPlayer）；选内置播放器且原为 null 时自动进入内置播放器设置页；选小电视播放器弹"不再推荐"提醒；选凉腕播放器且 SDK≤19 时提醒版本过低。
- 功能：长按内置播放器。
  - 用法：长按"内置播放器"卡片直接进入 `SettingTerminalPlayerActivity`。

### SettingQualityActivity.kt
- 功能：选择默认清晰度页。
  - 用法：标题"请选择清晰度"；列表展示 `qnMap`：360P(16)、720P(64)、1080P(80)；`QualityChooseAdapter` 点击某项 `save(position)` 写 `play_qn` 并结束；顶部点击以 `RESULT_CANCELED` 结束。

### AboutActivity.kt
- 功能：关于页面。
  - 用法：异步加载 `activity_setting_about`；显示版本名（加粗）、版本号、更新细节（`ToolsUtil.getUpdateLog`，可长按复制）。
- 功能：开发者头像卡片。
  - 用法：8 个开发者头像（Glide 圆形，占位 akari），点击跳对应 `UserInfoActivity`（携带 `mid`），UID=-1 的头像不可点击。
- 功能：彩蛋 1（作者的话）。
  - 用法：连点"作者的话"7 次弹出 `egg_about_author_words`。
- 功能：彩蛋 2（给叔叔）。
  - 用法：连点"给叔叔"7 次弹出 `egg_about_to_uncle`。
- 功能：开源图标信息。
  - 用法：点击"icon_license_list"弹出 `desc_icon_license` + 逐条 `icon_license` 数组。
- 功能：捐赠列表。
  - 用法：点击"sponsor_list"跳 `SponsorActivity`。
- 功能：开发者模式彩蛋。
  - 用法：`debug_tip` 仅 Debug 显示；连点版本号卡片 7 次启用/关闭 `developer` 开关（已启用时点一次即关闭）。

### AnnouncementsActivity.kt
- 功能：公告列表页。
  - 用法：标题"公告列表"；后台 `AppInfoApi.getAnnouncementList()` 拉取公告，`AnnouncementAdapter` 展示；失败 `report(e)` 并 Toast"连接到哔哩终端接口时发生错误"。

### SponsorActivity.kt
- 功能：捐赠列表页。
  - 用法：标题"捐赠列表"；首项插入捐赠说明（`donate_title`/`donate_desc`）；`AppInfoApi.getSponsors` 分页加载，`UserListAdapter` 展示；支持上拉加载更多（`setOnLoadMoreListener`）；到底 `bottom=true`。

### UpdateActivity.kt
- 功能：更新页。
  - 用法：`BaseActivity`，布局 `activity_update`；若 `has_config` Extra 为 true 则用 Extra（version_code/version_name/description/download_url/force_update）构造 `UpdateConfig` 直接显示；否则 `checkUpdate()` 联网检查。
- 功能：检查更新（`checkUpdate`）。
  - 用法：`UpdateManager.checkUpdate` 回调；有更新显示新版本信息，无更新 Toast"当前已是最新版"并结束；失败 Toast"检查更新失败"并结束。
- 功能：显示更新信息（`showUpdateInfo`）。
  - 用法：显示"发现新版本"、版本号、描述；记录 `update_last_new_version`；普通更新"取消"=finish，强制更新"退出应用"= `exitApp()`（finishAffinity + killProcess）。
- 功能：下载 APK（`startDownload`）。
  - 用法：`UpdateManager.downloadApk(url, ...)`，进度条显示百分比 + 已下载/总 MB；成功 `installApk`（调系统安装器）；失败显示重试按钮。
- 功能：取消下载。
  - 用法：`cancelDownloadBtn` 取消下载；强制更新下回更新信息页，否则结束。
- 功能：返回键处理。
  - 用法：强制更新时按返回=退出应用；下载中按返回取消下载后退出。

### TutorialManagerActivity.kt
- 功能：教程管理页。
  - 用法：标题"教程管理"；`buildTutorialList()` 生成 9 个普通教程（推荐/视频详情/用户主页/搜索/消息/动态/动态详情/专栏详情/短视频）+ 5 个页面滑动引导（Search/VideoInfo/UserInfo/OpusInfo/DynamicInfo）。
- 功能：教程状态卡片。
  - 用法：每教程一张卡片，显示名称、描述、"已完成/未完成"徽标、历史记录（曾完成版本）；两个按钮："通过教程"、"清除进度"。
- 功能：通过单个教程。
  - 用法：普通教程写 `tutorial_ver_$tag`（=xml 数组长度）与 `tutorial_history_$tag=true`；pager 教程写 `tutorial_pager_$tag=false`；Toast 并 `recreate()`。
- 功能：清除单个教程进度。
  - 用法：删除对应 `tutorial_ver_`/`tutorial_history_`/`tutorial_pager_` key；Toast 并 `recreate()`。
- 功能：一键通过所有。
  - 用法：`btn_pass_all` 遍历全部教程标记为完成。
- 功能：清除所有教程进度。
  - 用法：`btn_clear_all` 删除全部教程 key。
- 功能：重置页面滑动引导。
  - 用法：`btn_clear_pager` 仅删除 5 个 pager 教程 key。

### TodoListActivity.kt
- 功能：TO DO 清单页（开发者愿望清单）。
  - 用法：`InstanceActivity`，异步加载 `activity_todo_list`，静态展示；仅 Debug 分组入口可见。

### UIPreviewActivity.kt
- 功能：界面外观预览页。
  - 用法：`BaseActivity`，直接加载 `activity_setting_ui_preview` 静态展示（供界面大小/边距预览）。

### setup/SetupUIActivity.kt
- 功能：首次引导——UI 设置。
  - 用法：`BaseActivity` 加载 `activity_setup_ui`；三个输入框预填当前值：界面大小（`dpi`）、界面边距横向（`paddingH_percent`）、纵向（`paddingV_percent`）。
- 功能：圆屏适配开关（`switch_round`）。
  - 用法：开启时自动设横向 5、纵向 3 并写 `player_ui_round=true`，提示可微调；关闭时置 0、`player_ui_round=false`。
- 功能：预览按钮。
  - 用法：先 `save()` 再跳 `UIPreviewActivity`。
- 功能：确认按钮。
  - 用法：`save()` 后跳 `IntroductionActivity` 并结束本页。
- 功能：重置按钮。
  - 用法：清空边距/DPI/圆屏开关并回填输入框，Toast"恢复完成"。
- 功能：保存（`save`）。
  - 用法：界面大小 float 限 0.1~10.0 写 `dpi`；横向/纵向 int 限 ≤30 写 `paddingH_percent`/`paddingV_percent`。

### setup/IntroductionActivity.kt
- 功能：首次引导——介绍页。
  - 用法：加载 `activity_setup_introduction`；点"确认"置 `setup=true`，带 `from_setup=true` 跳 `LoginActivity`（SDK≥19）或 `SpecialLoginActivity`，并结束本页。

### TestActivity.kt（仅 Debug 开发者工具）
- 功能：功能测试页（网络请求调试）。
  - 用法：`BaseActivity` 加载 `activity_test`；含链接输入（`input_link`，预填 `dev_test_link`）、POST 数据输入（`input_data`）、WBI 签名开关（`switch_wbi`）、POST 开关（`switch_post`，开启才显示数据输入）、输出框（`output`）。
- 功能：发送请求（`btn_request`）。
  - 用法：自动补 https:// 前缀；勾选 WBI 时 `ConfInfoApi.signWBI(url)` 签名；GET 或 POST（`NetWorkUtil.get/post`），结果写入输出框。
- 功能：查看/导出 Cookies。
  - 用法：`btn_cookies` 跳 `SpecialLoginActivity` 带 `login=false`（导出查看）。
- 功能：打开专栏测试。
  - 用法：`btn_opus` 跳 `OpusInfoActivity` 带固定 id。
- 功能：崩溃彩蛋（猫娘对话）。
  - 用法：点 `btn_crash` 隐藏其他按钮、显示数据输入、改 WBI 文案为"使用R1"、描述改 `dev_catgirl_desc`；首次点击初始化 system prompt（`dev_catgirl_prompt`）并预填 API KEY（`dev_catgirl_apikey`）；后续点击流式调用 DeepSeek API（`api.deepseek.com/chat/completions`，model=deepseek-chat/reasoner，stream），逐行解析 `data:` 输出 reasoning/content，保留会话上下文；请求期间禁用输入。
- 功能：保存测试链接。
  - 用法：`onDestroy()` 时若未进入猫娘模式则保存 `dev_test_link`。

---

## 三、通用组件 / 对话框 / 基础 Activity

### DialogActivity.kt
- 功能：通用信息对话框。
  - 用法：`BaseActivity`；读取 `title`、`content` Extra；`wait_time` Extra>0 时启用倒计时（每秒更新按钮文案"知道了(Ns)"），倒计时结束才可点；`wait_time<=0` 按钮直接可用；点"知道了" `finish()`。
- 功能：禁用返回键。
  - 用法：`onBackPressed()` 为空实现，必须点按钮才能关闭。

### ConfirmDialogActivity.kt
- 功能：确认对话框。
  - 用法：`BaseActivity`；读取 `title`/`content`；"确定"= `RESULT_OK`，"取消"= `RESULT_CANCELED`，点击标题=取消，返回键=取消。

### ListDialogActivity.kt
- 功能：列表选择对话框。
  - 用法：`BaseActivity`；读取 `title`、`items`（String ArrayList）；`ListView` + `ArrayAdapter`；点击某项以 `RESULT_OK` + `selected_position` 返回；点标题/返回键 = `RESULT_CANCELED`。

### InputDialogActivity.kt
- 功能：文本输入对话框。
  - 用法：`BaseActivity`；读取 `title`、`initial_text`（预填并选中）、`hint`、`error_color`；输入框文本变化时隐藏错误文字；"确定"= `RESULT_OK` + `input_text`（trim），"取消"/点标题/返回键 = `RESULT_CANCELED`。

### ListChooseActivity.kt
- 功能：列表单选页（返回选项）。
  - 用法：`BaseActivity` 加载 `activity_simple_list`；标题取 `title` Extra（无则"请选择"）；`items`（Serializable List<String>）必填、`values`（可选对应值）、`position`（原始位置，用于回传）；点击某项 `RESULT_OK` + `item`/`value`/`position`；点顶部/返回 = `RESULT_CANCELED`；支持长按（未设置监听则不处理）。

### ImageViewerActivity.kt
- 功能：图片查看器。
  - 用法：`BaseActivity`，主题 `Theme_BiliClient`；读取 `imageList`（String ArrayList，图片 URL）；`PhotoViewpager` 全屏浏览多图，`PhotoView` 支持缩放（最大 6.25 倍），底部"第 x/N 张"页码。
- 功能：下载当前图片。
  - 用法：`btn_download` 需 3 秒内连点两次（第一次 Toast"再次点击下载"）；确认后跳 `DownloadActivity`（`link`=当前图、`path`=图片下载路径、`type=0`）。
- 功能：内存保护。
  - 用法：`OutOfMemoryError` 时 Toast"超出内存，加载失败"。

### EmoteActivity.kt
- 功能：表情选择页。
  - 用法：`BaseActivity` 加载 `activity_emote`；读取 `from` Extra（业务类型，默认 `BUSINESS_REPLY`）；`EmoteApi.getEmotes(from)` 拉取表情包，TabLayout + ViewPager 分页。
- 功能：表情包 Tab。
  - 用法：每个 Tab 显示包名与图标（Glide 异步加载），选中才显示文字标签。
- 功能：表情网格。
  - 用法：每包一个 `EmoteFragment`，`CustomGridManager` 4 列（type==4 的表情占 2 列），表情图片/文字展示（带 Tooltip 别名）。
- 功能：选择表情。
  - 用法：点击某个表情 `setResult(RESULT_OK, text=表情名)` 并结束页面。

### GetIntentActivity.kt
- 功能：Intent 路由中转（打开视频/专栏/用户）。
  - 用法：`Activity`；读 `type` Extra：`video_av`→`BiliTerminal.jumpToVideo(long)`、`video_bv`→`jumpToVideo(String)`、`article`→`jumpToArticle`、`user`→`jumpToUser`；不支持的类型 Toast"不支持打开：type"。
- 功能：URI 深度链接。
  - 用法：读取 `intent.data`，按 host：`video`→`jumpToVideo(lastPathSegment)`、`article`→`jumpToArticle(lastPathSegment)`，否则提示不支持；处理完 `finish()`。

### CatchActivity.kt
- 功能：崩溃捕获页。
  - 用法：`BaseActivity`（关闭 EventBus）；读取 `stack` Extra（无则结束）；显示堆栈文本。
- 功能：退出按钮。
  - 用法：`exit_btn` kill 本进程。
- 功能：崩溃原因分析。
  - 用法：按堆栈内容分类显示可能原因：NumberFormatException=数值转换出错、UnsatisfiedLinkError=外部库加载出错、JSONException=数据解析错误、OutOfMemoryError=内存爆了；其他=未知崩溃原因。
- 功能：上传堆栈（`upload_btn`）。
  - 用法：仅"未知原因"类可上传（其他类型提示不可上传）；未登录（mid==-1）时 Toast 提醒；登录后 `AppInfoApi.uploadStack` 上传，成功显示报错 ID，失败（-1）恢复可点。
- 功能：重启按钮。
  - 用法：`restart_btn` 停止 `DownloadService`、跳 `SplashActivity`（NEW_TASK）、kill 进程。
- 功能：堆栈展开/收起。
  - 用法：点击堆栈在 5 行/200 行间切换；`StringUtil.setCopy` 支持长按复制。

### ShowTextActivity.kt
- 功能：显示文本页。
  - 用法：`BaseActivity` 加载 `activity_simple_text`；读取 `title`（页面名）、`content`；`StringUtil.setCopy` 支持复制。
- 功能：附加信息解析。
  - 用法：content 含 `<extra_insert>` 时解析其后 JSON：type==video 则内嵌视频卡片（点击 `jumpToVideo`）；否则提示"无法识别的附加信息"。
- 功能：URI 内容。
  - 用法：`intent.data` 非空时以 URI 字符串作为内容。

### CopyTextActivity.kt
- 功能：选取文本片段复制页。
  - 用法：`BaseActivity` 加载 `activity_copy`；读取 `content`（空则结束）；显示可编辑文本（`content`）+ 起始/结束索引输入框（`begin_index`/`end_index`）。
- 功能：同步选区。
  - 用法：文本失焦时回填光标选区到起止索引；修改索引时 `setSelection` 同步文本选区。
- 功能：复制全部 / 复制片段。
  - 用法："复制全部"=复制整段；"复制"=复制 `content.substring(begin,end)`（越界提示失败）。
- 功能：微调起止索引。
  - 用法：`begin_left/right`、`end_left/right` 四个按钮 ±1（边界夹 0..length）；长按分别置 0 或 length。

### TutorialActivity.kt
- 功能：新手教程展示页。
  - 用法：`BaseActivity` 异步加载 `activity_tutorial`；读取 `xml_id`（默认 `tutorial_recommend`）经 `TutorialHelper.loadTutorial` 加载教程，显示标题（name）、正文（`loadText`）、可选配图（`imgid` 资源）。
- 功能：倒计时"已阅"按钮。
  - 用法：初始 3 秒倒计时（按钮"已阅(Ns)"禁用），结束变"已阅"可点；点击写 `tutorial_ver_$tag = version` 并 `finish()`。
- 功能：禁用返回键。
  - 用法：`onBackPressed()` 空实现，必须点"已阅"。

### SplashActivity.kt
- 功能：启动页（打字机效果）。
  - 用法：`Activity`；读取 `ui_splashtext` 开屏文字（默认"欢迎使用\nRE:哔哩终端"），每 100ms 逐字显示打字机效果。
- 功能：Debug 悬浮窗权限检查。
  - 用法：Debug 构建下 `ensureUEToolOverlayPermission()`：已授权则延迟 300ms 显示 UETool 悬浮窗；未授权跳系统授权页并在 `onActivityResult` 后继续流程（未授权 Toast 提示不阻塞）。
- 功能：启动主流程（`proceedSplashFlow`）。
  - 用法：若 `setup=true`：取菜单第一个启用项对应的 Activity（`MenuActivity.btnNames`），带 `from` 跳转（默认 `RecommendActivity`）；后台执行 Cookie 刷新检查（`checkCookieRefresh`）、`CookiesApi.checkCookies()`、`AppInfoApi.check`；若未完成设置则跳 `SetupUIActivity`。
- 功能：Cookie 刷新（`checkCookieRefresh`）。
  - 用法：`CookieRefreshApi.cookieInfo()` 判断需刷新且有 refresh_token 时，取 `getCorrespondPath`→`getRefreshCsrf`→`refreshCookie`，成功提示"Cookies已刷新"并保存账号，失败/异常提示"登录信息过期，请重新登录！"并 `resetLogin()`（清 mid/csrf/cookies/refresh_token）。

### MenuActivity.kt
- 功能：主菜单页（九宫格导航）。
  - 用法：`BaseActivity`；读取 `from` Extra 设置页面名；`btnNames` 静态表：recommend=推荐、short_video=短视频、popular=热门、precious=入站必刷、ranking=全站排行榜、hotsearch=热搜、live=直播、timeline=时间线、search=搜索、dynamic=动态、myspace=我的、message=消息、local=缓存、settings=设置。
- 功能：动态/未登录按钮。
  - 用法：未登录（mid==0）时列表头部插入"登录"按钮并移除动态/消息/我的；"动态"按钮显示新动态数（`DYNAMIC_UPDATE_NUM`）、"消息"按钮显示未读数（`MESSAGE_UPDATE_NUM`）。
- 功能：跳转（`killAndJump`）。
  - 用法：点击按钮（非当前页）关闭栈顶实例、跳对应页面带 `from`；低性能设备 `Glide.clearMemory()`；"登录"按钮跳 `LoginActivity`；然后 `finish()`。
- 功能：菜单键返回。
  - 用法：按实体菜单键 `finish()`；点顶部 `top` 也 `finish()`。

---

## 四、基础类

### base/BaseActivity.kt
- 功能：全局 Activity 基类。
  - 用法：`attachBaseContext` 应用 `BiliTerminal.getFitDisplayContext`（界面缩放/Density 适配）。
- 功能：主题应用。
  - 用法：`onCreate` 按 `ThemeManager.PREF_KEY_THEME` 选择 6 套主题（B站粉/知乎蓝/爱奇艺绿/紫色空灵/五彩斑斓/经典灰）；`applyWindowTheme` 设置窗口。
- 功能：屏幕方向。
  - 用法：`ui_landscape` 开启→传感器横屏，否则竖屏。
- 功能：圆屏边距适配。
  - 用法：`paddingH_percent`/`paddingV_percent` 计算窗口边距并 setPadding；`player_ui_round` 时底部额外 +3% 高度。
- 功能：自定义 Density。
  - 用法：`density>=72` 时 `setDensity` 更新 configuration。
- 功能：返回键禁用。
  - 用法：`back_disable` 开启时 `onBackPressed` 空实现。
- 功能：页面名设置（`setPageName`）。
  - 用法：写 `R.id.pageName` 文本。
- 功能：顶栏返回（`setTopbarExit`）。
  - 用法：`R.id.top` 点击 `finish()`（非 InstanceActivity 时自动设置）。
- 功能：圆屏适配标题（`setRound`）。
  - 用法：`player_ui_round` 时重排标题/时钟居中布局。
- 功能：菜单键返回。
  - 用法：`onKeyDown` 实体菜单键 `finish()`。
- 功能：报错（`report`）。
  - 用法：`MsgUtil.err(类名, e)` 上报异常。
- 功能：EventBus 注册。
  - 用法：`eventBusEnabled()` 默认由 `SNACKBAR_ENABLE` 控制；注册后 `onEvent(SnackEvent)`（sticky）处理 Snack 事件；`onResume` 处理待处理 Snack。
- 功能：主题变更即时重建。
  - 用法：`onResume` 检测 `appliedTheme != currentTheme` 时 `recreate()`。
- 功能：异步加载布局（`asyncInflate`）。
  - 用法：先显示 `activity_loading`，后台 `AsyncLayoutInflaterX` 加载目标布局，完成后设置内容、设置菜单/顶栏、`setRound`、回调。
- 功能：布局管理器选择（`getLayoutManager`）。
  - 用法：横屏且非强制单列时返回 3 列 `CustomGridManager`，否则 `CustomLinearManager`；`setForceSingleColumn` 强制单列。

### base/BaseFragment.kt
- 功能：Fragment 基类。
  - 用法：`runOnUiThread`（isAdded 时切主线程）、`getAppContext()`（返回 `BiliTerminal.context`）。

### base/RefreshListActivity.kt
- 功能：可下拉刷新/上拉加载的列表 Activity。
  - 用法：加载 `activity_simple_refresh`；包含 `SwipeRefreshLayout`（默认禁用+refreshing）与 `RecyclerView`；按性能设置缓存大小/共享 ViewHolder 池/预取数量。
- 功能：自动加载更多。
  - 用法：`setOnLoadMoreListener` 后，滚动到底部（最后可见项距末尾≤4）且满足条件时 `goOnLoad()`（page++、调 `listener.onLoad(page)`，防抖 500ms）。
- 功能：下拉刷新。
  - 用法：`setOnRefreshListener` 启用下拉刷新。
- 功能：空视图控制。
  - 用法：`showEmptyView`/`hideEmptyView` 切换空提示与列表可见性。
- 功能：刷新状态（`setRefreshing`）。
  - 用法：更新 SwipeRefreshLayout 转圈。
- 功能：加载完成/失败（`onLoadComplete`/`loadFail`）。
  - 用法：重置 loading 标志、失败回退 page 并报错/提示。
- 功能：设置项定位（`scrollToHighlight`）。
  - 用法：按 `name` 匹配 `SettingSection` 列表并滚动到该位置。

### base/RefreshMainActivity.kt
- 功能：主页面列表基类（`InstanceActivity`）。
  - 用法：加载 `activity_simple_main_refresh`；含下拉刷新与 RecyclerView（缓存 10、池 20）；滚动到底部（完全可见项距末尾≤3）自动 `goOnLoad()`（防抖 100ms，带同步锁）；`ImageAutoLoadScrollListener` 图片懒加载。
- 功能：方法同 RefreshListActivity（setAdapter/setOnRefreshListener/setRefreshing/setOnLoadMoreListener/loadFail）。
  - 用法：见上。

### base/RefreshListFragment.kt
- 功能：可刷新/加载的列表 Fragment（`BaseFragment`）。
  - 用法：加载 `fragment_simple_refresh`；结构与 `RefreshListActivity` 类似（含 SwipeRefreshLayout、RecyclerView、空视图、加载更多防抖 100ms、`ImageAutoLoadScrollListener`）；`getLayoutManager` 按横屏/强制单列选择网格/线性。
- 功能：刷新/空视图/失败。
  - 用法：`setRefreshing`/`showEmptyView`/`loadFail`/`report` 等同 Activity 版。

### base/InstanceActivity.kt
- 功能：实例化 Activity 基类（主界面）。
  - 用法：`onCreate` 调 `BiliTerminal.setInstance(this)` 记录当前栈顶实例；`menuClick` Runnable 跳 `MenuActivity` 并透传 `from`，带下滑进入动画。
- 功能：顶部菜单点击。
  - 用法：`setMenuClick()` 把 `R.id.top` 点击设为打开菜单。
- 功能：菜单键打开菜单。
  - 用法：`onKeyDown` 实体菜单键执行 `menuClick.run()`。

---

（共审查 50 个文件，全部条目按"功能-用法"颗粒度列出完毕。）
