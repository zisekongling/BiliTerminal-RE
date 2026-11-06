# 06 · 运行时提示与通知

> 收录范围：Toast / 弹窗 / 错误 / 空态 / 加载 / 通知栏 / 播放器浮层
> 编号前缀：`M`
> 生成：2026-10-18 · 基于当前工作区
> 计数：共 128 条（M-001 ~ M-128）
> **改写入口**：每条最后的 `- **改写**：` 里**已经预填了原文**（2026-10-19 起），你直接在原地改成新文案，再把该条标题加上 `〔待落地〕`；怎么配合见 [GUIDE.md](GUIDE.md) 第十一节。

## 〇、提示文案的载体与长度约束

本工程没有一套通用的「提示组件」，用户看到的临时反馈实际走**四类载体**，长度容忍度完全不同。改文案前先认准载体，否则会出现「话没说完就被截断」或「一句话占满半块表盘」。

| 载体 | 实现 | 长度 | 说明 |
| --- | --- | --- | --- |
| Toast / Snackbar | `util/MsgUtil.java` 的 `showMsg` / `showMsgLong` | 短，一句话 | 默认走 Snackbar（沉浸式、可覆盖在页面底部），用户在设置里关掉 `SNACKBAR_ENABLE` 才退回系统 Toast。两者都是 2~3 秒消失，**超过两行就要考虑换载体** |
| 弹窗（TerminalDialog） | `util/TerminalDialog.kt` | 可长，但正文在滚动区里，高度上限为屏高的 45% | 有标题 + 正文 + 按钮，能停下阅读、能要二次确认，适合「解释原因 / 要求决定」 |
| 整页弹窗（DialogActivity） | `util/MsgUtil.showDialog(title, content[, wait_time])` 拉起 `activity/DialogActivity.kt` | 可很长，整页滚动 | 启动时的免责声明、夜深提醒走这条；`wait_time` 是「知道了」按钮的倒计时封禁秒数 |
| 空态页 / 列表底部状态条 | 布局里的 `emptyView` / `loadMoreTip`，文案在 `util/view/LoadMoreTipController.kt`（两个列表基类共用） | 可多行 | 空态常驻；底部状态条**不是常驻横幅**——「正在加载…」保持到加载结束，「没有更多了」是**一次性提示**（2 秒后自动收起，26.11.06 用户反馈「横幅一直固定不会隐藏」后改的口径）；只显示四个字级别 |
| 通知栏 | `util/MsgNotifier.kt`（1029/1030）、`service/PlaybackService.kt`（1028）、`service/download/DownloadNotifier.kt`（1027） | 标题 ≤ 一行、正文 ≤ 一行 | 手表上正文常被系统截成十几个字，**关键信息要放标题** |
| 播放器中央浮层 | `activity/player/PlayerActivity.kt` 的 `loading_text0` / `loading_text1` | 极短，2~6 字 | 转圈动画下面的两行字，第二行是速度或颜文字 |

**关键方法语义**（`util/MsgUtil.java`）：

- `showMsg(String)`（`:67`）：短提示。默认 `EventBus` 发 `SnackEvent`（Snackbar），`SNACKBAR_ENABLE` 关掉时走 `toast()`。
- `showMsgLong(String)`（`:76`）：同上但停留更久（`Snackbar.LENGTH_LONG`），**长句、含换行的句子用它**。
- `toast(String)` / `toastLong(String)`（`:85` / `:89`）：强制系统 Toast，绕过 Snackbar 开关。
- `err(Throwable)` / `err(String desc, Throwable e)`（`:205` / `:209`）：异常统一出口。`desc` 会原样拼在提示最前面，然后按异常类型追加 `err_network` / `err_json` / `err_sql` / `err_login_first` / `err_rejected` 等固定文案，最终用 `showMsgLong` 显示。**业务里看到 `MsgUtil.err(e)` 就等于「底部一条长提示，内容是异常翻译」**。
- `showDialog(title, content[, wait_time])`（`:273` / `:284`）：整页弹窗（`DialogActivity`），不是 `TerminalDialog`。
- `showText(title, text)`（`:261`）：跳到 `ShowTextActivity` 读长文（公告、彩蛋、视频摘要），不属于临时提示，故本文档只登记入口文案。

**弹窗方法语义**（`util/TerminalDialog.kt`）：

- `menu(context, title, items, danger, onPick)`（`:74`）：菜单型，纯选项列表，**没有按钮行**，点空白处取消（底部提示「点空白处取消」，`:100`）。
- `singleChoice(context, title, items, checked, onPick)`（`:116`）：单选型，选中项 `▸` + 主色 + 右侧 `✓`，未选中 `›`；同样没有按钮行，不自动关闭。
- `confirm(context, title, message, confirmText = "确定", cancelText = "取消", confirmIsDanger = true, onConfirm)`（`:144`）：确认型，**有按钮行**，默认按钮文字就是「确定」「取消」，破坏性操作用 `colorError` 着色。
- `choice(...)`（`:187`）：两选一，两个按钮各带回调（隐私说明「同意/不同意」用），按钮在固定底栏。
- `input(...)`（`:226`）：输入型，默认按钮「取消 / 发送」。
- `alert(context, title, message, buttonText = "知道了")`（`:255`）：纯提示，只有一个按钮，**默认按钮文字是「知道了」**。
- 装饰字符（非用户文案，但会出现在弹窗里）：标题前缀 `›`（后接一个空格，`GLYPH_IDLE`）、选中项 `▸`、勾选 `✓`。

---

## 一、Toast 提示

### 视频播放

#### M-001 视频信息仍在加载

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/video/info/VideoInfoFragment.kt:747`、`:785`
- **出现在哪**：视频详情页点「下载」「三连」等依赖详情的按钮，但详情接口还没返回时底部短提示
- **载体**：Toast
- **用途**：挡住一次必然失败的操作，把用户注意力引回「还在加载」
- **原文**：

> 视频信息还在加载中

- **改写**：视频信息还在加载中

#### M-002 详情页数据为空

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/video/info/VideoInfoFragment.kt:223`
- **出现在哪**：视频详情页接口返回成功但正文字段缺失（被删/被屏蔽）时底部提示
- **载体**：Toast
- **用途**：解释页面为什么是空的
- **原文**：

> 视频详情页：数据为空

- **改写**：视频详情页：数据为空

#### M-003 详情页获取失败

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/video/info/VideoInfoActivity.kt:113`
- **出现在哪**：进入视频详情页时详情接口报错/视频不存在，页面加载失败后的底部提示
- **载体**：Toast（短）
- **用途**：区分「加载失败」与「内容为空」，并给一个最可能的猜测
- **原文**：

> 获取信息失败！

> 可能是视频不存在？

- **改写**：

> 获取信息失败！

> 可能是视频不存在？

#### M-004 左右滑动提示

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/video/info/VideoInfoActivity.kt:77`
- **出现在哪**：首次进入视频详情页（同一实例只提示一次）时底部提示
- **载体**：Toast（长）
- **用途**：教交互，让用户发现详情/评论分栏可以横滑
- **原文**：

> 提示：本页面可以左右滑动

- **改写**：提示：本页面可以左右滑动

#### M-005 返回初始页面

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/video/info/VideoInfoActivity.kt:187`
- **出现在哪**：详情页横滑到评论栏后再次返回，回到详情栏时提示
- **载体**：Toast
- **用途**：确认返回动作生效
- **原文**：

> 已返回初始页面

- **改写**：已返回初始页面

#### M-006 转发不支持带图

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/video/info/VideoInfoFragment.kt:111`
- **出现在哪**：视频详情页点「转发」，但动态输入框里带了图片时提示
- **载体**：Toast
- **用途**：解释失败原因并给出下一步动作
- **原文**：

> 视频转发不支持带图，请去掉图片后重试

- **改写**：视频转发不支持带图，请去掉图片后重试

#### M-007 转发结果

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/video/info/VideoInfoFragment.kt:127-128`
- **出现在哪**：视频详情页转发动态成功 / 失败后底部提示
- **载体**：Toast
- **用途**：告知转发是否成功
- **原文**：

> 转发成功~

> 转发失败

- **改写**：

> 转发成功~

> 转发失败

#### M-008 下载需要通知权限

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/video/info/VideoInfoFragment.kt:142`
- **出现在哪**：未授予通知权限时点「下载」，底部提示
- **载体**：Toast
- **用途**：解释为什么不让下载，并指向系统设置
- **原文**：

> 需要通知权限才能进行下载，请前往设置授予权限

- **改写**：需要通知权限才能进行下载，请前往设置授予权限

#### M-009 BV 号已复制

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/video/info/VideoInfoFragment.kt:421`
- **出现在哪**：详情页长按/点「复制 BV 号」后提示
- **载体**：Toast
- **用途**：确认复制成功
- **原文**：

> BV号已复制

- **改写**：BV号已复制

#### M-010 视频完整链接已复制

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/video/info/VideoInfoFragment.kt:568`
- **出现在哪**：详情页复制「完整链接」后提示
- **载体**：Toast
- **用途**：确认复制成功，且暗示复制的是长链接
- **原文**：

> 视频完整链接已复制

- **改写**：视频完整链接已复制

#### M-011 正在处理中（视频操作）

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/video/info/VideoInfoFragment.kt:438`、`:482`
- **出现在哪**：详情页点赞 / 投币请求发出到返回之间的即时反馈
- **载体**：Toast
- **用途**：防连点，告诉用户请求已发出
- **原文**：

> 正在处理中…

- **改写**：正在处理中…

#### M-012 点赞与取消

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/video/info/VideoInfoFragment.kt:452`
- **出现在哪**：详情页点赞成功后（按当前状态返回「点赞成功」或「取消成功」）
- **载体**：Toast
- **用途**：确认状态已切换
- **原文**：

> 点赞成功

> 取消成功

- **改写**：

> 点赞成功

> 取消成功

#### M-013 投币成功

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/video/info/VideoInfoFragment.kt:497`
- **出现在哪**：详情页投币成功后
- **载体**：Toast
- **用途**：确认虚拟币已投出
- **原文**：

> 投币成功

- **改写**：投币成功

#### M-014 投币到达上限

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/video/info/VideoInfoFragment.kt:511`
- **出现在哪**：同一稿件投币数已达上限仍继续投币时
- **载体**：Toast
- **用途**：解释失败原因是「规则限制」而非网络问题
- **原文**：

> 投币数量到达上限

- **改写**：投币数量到达上限

#### M-015 收藏夹添加结果

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/video/info/VideoInfoFragment.kt:533-534`
- **出现在哪**：详情页把视频加入收藏夹后，按返回码提示成功或带错误码的失败
- **载体**：Toast
- **用途**：告知收藏结果；失败时留下错误码供反馈用
- **原文**：

> 添加成功

> 添加失败，错误码：

- **备注**：失败时尾部拼接接口返回的错误码数字
- **改写**：

> 添加成功

> 添加失败，错误码：

#### M-016 已清除缓存文件夹

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/video/info/VideoInfoFragment.kt:552`
- **出现在哪**：详情页长按下载按钮清空该视频的缓存文件后
- **载体**：Toast
- **用途**：确认缓存已清理
- **原文**：

> 已清除此视频的缓存文件夹

- **改写**：已清除此视频的缓存文件夹

#### M-017 视频摘要失败

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/video/info/VideoInfoFragment.kt:581`
- **出现在哪**：详情页点「视频摘要」（AI 总结）且接口失败时
- **载体**：Toast
- **用途**：告知本次摘要没取到
- **原文**：

> 获取视频摘要失败

- **改写**：获取视频摘要失败

#### M-018 三连结果

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/video/info/VideoInfoFragment.kt:648-649`
- **出现在哪**：详情页点「一键三连」后，成功或失败提示
- **载体**：Toast
- **用途**：告知三连是否成立；失败时留错误码
- **原文**：

> 三连成功

> 三连失败，错误码：

- **备注**：失败时尾部拼接错误码
- **改写**：

> 三连成功

> 三连失败，错误码：

#### M-019 已在下载队列 / 已下载完成（详情页）

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/video/info/VideoInfoFragment.kt:778`
- **出现在哪**：详情页点下载按钮时，按文件标记判断「正在下载」或「已下完」
- **载体**：Toast（长，含换行）
- **用途**：避免重复下载并告知异常时怎么救
- **原文**：

> 已在下载队列

> 如有异常，长按可清空文件

> 已下载完成

- **改写**：

> 已在下载队列

> 如有异常，长按可清空文件

> 已下载完成

#### M-020 点击封面播放说明（开）

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/video/info/VideoInfoFragment.kt:736`
- **出现在哪**：详情页点封面起播时，若开关为「点击封面播放」开启态首次触发则弹整页说明
- **载体**：整页弹窗（`MsgUtil.showDialog`，标题「播放视频」）
- **用途**：解释新交互并告知怎么改回去
- **原文**（正文取自 `strings.xml` `tutorial_cover_play_enabled`）：

> 从现版本开始，默认状态下，点击封面将会播放视频，长按封面查看大图。
> 播放按钮仍可使用，长按播放按钮可切换播放器。
>
> 如果不习惯，你可以在 设置-详情页设置 中关闭“点击封面播放”的开关，换回老版本里点击封面查看大图的操作逻辑。

- **改写**：

> 从现版本开始，默认状态下，点击封面将会播放视频，长按封面查看大图。
> 播放按钮仍可使用，长按播放按钮可切换播放器。
>
> 如果不习惯，你可以在 设置-详情页设置 中关闭“点击封面播放”的开关，换回老版本里点击封面查看大图的操作逻辑。

#### M-021 点击封面播放说明（关）

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/video/info/VideoInfoFragment.kt:738`
- **出现在哪**：详情页点封面但「点击封面播放」为关闭态时，提示如何去打开
- **载体**：整页弹窗（标题「播放视频」）
- **用途**：引导用户找到开关
- **原文**（`strings.xml` `tutorial_cover_play_disabled`）：

> 提示：如果你想要点击封面播放视频，请在 设置-详情页设置 中打开“点击封面播放”的开关。
>
> 现版本对于新用户已将此开关默认开启，老用户仍保持原来的偏好设定。

- **改写**：

> 提示：如果你想要点击封面播放视频，请在 设置-详情页设置 中打开“点击封面播放”的开关。
>
> 现版本对于新用户已将此开关默认开启，老用户仍保持原来的偏好设定。

#### M-022 分P页已下载判断

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/video/MultiPageActivity.kt:50`
- **出现在哪**：选集页长按某一 P 触发下载时，按本地文件标记判断结果
- **载体**：Toast
- **用途**：避免重复下载
- **原文**：

> 已在下载队列

> 已下载完成

- **改写**：

> 已在下载队列

> 已下载完成

#### M-023 清晰度列表获取失败

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/video/QualityChooserActivity.kt:95`
- **出现在哪**：从详情页进入「选择清晰度/缓存」页，清晰度接口失败时
- **载体**：Toast（带感叹号）
- **用途**：解释页面为什么没有选项
- **原文**：

> 清晰度列表获取失败！

- **改写**：清晰度列表获取失败！

#### M-024 没有可用音频流

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/video/QualityChooserActivity.kt:111`、`app/src/main/java/com/RobinNotBad/BiliClient/adapter/video/VideoQuickCache.kt:96`
- **出现在哪**：缓存 DASH 视频时发现该稿件没有音频轨（常见于纯音乐/已下架音频）
- **载体**：Toast
- **用途**：解释为什么不能缓存
- **原文**：

> 该视频没有可用的音频流

- **改写**：该视频没有可用的音频流

#### M-025 获取音频信息失败

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/video/QualityChooserActivity.kt:119`
- **出现在哪**：缓存时拉取音频播放地址抛异常
- **载体**：Toast
- **用途**：告知失败环节
- **原文**：

> 获取音频信息失败：

- **备注**：尾部拼接异常信息 `e.message`
- **改写**：获取音频信息失败：

#### M-026 清晰度需大会员

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/video/QualityChooserActivity.kt:141`
- **出现在哪**：在清晰度列表里选了仅大会员可用的档位（如 1080P+）并尝试缓存
- **载体**：Toast
- **用途**：解释失败原因是权限而不是网络
- **原文**：

> 仅大会员可下载喵~

- **备注**：模板为 `"${qualityName}仅大会员可下载喵~"`，前缀是清晰度名称
- **改写**：仅大会员可下载喵~

#### M-027 不支持该分辨率

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/video/QualityChooserActivity.kt:157`
- **出现在哪**：选了一个该稿件并不提供的清晰度并点缓存
- **载体**：Toast
- **用途**：解释失败原因
- **原文**：

> 该视频不支持分辨率，缓存失败

- **备注**：模板为 `"该视频不支持${qualityName}分辨率，缓存失败"`
- **改写**：该视频不支持分辨率，缓存失败

#### M-028 获取指定清晰度失败

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/video/QualityChooserActivity.kt:162`
- **出现在哪**：拉某个清晰度的播放地址时抛异常
- **载体**：Toast
- **用途**：定位失败环节
- **原文**：

> 获取失败：

- **备注**：模板为 `"获取${qualityName}失败：${e.message}"`
- **改写**：获取失败：

#### M-029 直播已结束

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/live/LiveInfoActivity.kt:150`、`:188`
- **出现在哪**：进入直播间或从外部链接打开直播间时，接口判定直播已下播
- **载体**：Toast
- **用途**：解释为什么没有画面
- **原文**：

> 直播已结束

- **改写**：直播已结束

#### M-030 直播不存在

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/live/LiveInfoActivity.kt:236`
- **出现在哪**：进入直播间时接口报错/房间号无效
- **载体**：Toast
- **用途**：解释打不开的原因
- **原文**：

> 直播不存在

- **改写**：直播不存在

#### M-031 没有找到播放器

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/live/LiveInfoActivity.kt:169`、`app/src/main/java/com/RobinNotBad/BiliClient/activity/video/local/LocalPageChooseActivity.kt:70`
- **出现在哪**：调用外部播放器打开视频/直播，但设备上没有能响应的播放器
- **载体**：Toast
- **用途**：解释「点了没反应」的原因
- **原文**：

> 没有找到播放器，请检查是否安装

- **改写**：没有找到播放器，请检查是否安装

#### M-032 直播播放器兼容提醒

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/live/LiveInfoActivity.kt:181`
- **出现在哪**：用外部播放器打开直播时提醒
- **载体**：Toast（长）
- **用途**：提前安抚「放不出来」的预期
- **原文**：

> 若无法播放请更换为内置播放器

- **改写**：若无法播放请更换为内置播放器

#### M-033 直播仅内置播放器可播

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/live/LiveInfoActivity.kt:233`
- **出现在哪**：打开直播但外部播放器可能不支持其流格式时
- **载体**：Toast（长）
- **用途**：给出可用的替代方案
- **原文**：

> 直播可能只有内置播放器可以正常播放

- **改写**：直播可能只有内置播放器可以正常播放

#### M-034 番剧信息获取失败（卡片）

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/adapter/video/VideoCardHolder.kt:240`
- **出现在哪**：列表里点番剧卡片，补拉番剧详情失败
- **载体**：Toast
- **用途**：告知无法跳转
- **原文**：

> 番剧信息获取失败

- **改写**：番剧信息获取失败

#### M-035 番剧不支持快速缓存

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/adapter/video/VideoQuickCache.kt:33`
- **出现在哪**：长按番剧卡片选「快速缓存」
- **载体**：Toast
- **用途**：解释功能边界
- **原文**：

> 番剧暂不支持快速缓存

- **改写**：番剧暂不支持快速缓存

#### M-036 已缓存需先删除

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/adapter/video/VideoQuickCache.kt:43`、`:65`、`:87`、`:114`
- **出现在哪**：对已经缓存过的视频再次「快速缓存」
- **载体**：Toast
- **用途**：引导用户先清缓存
- **原文**：

> 该视频已缓存，请先删除原缓存文件

- **改写**：该视频已缓存，请先删除原缓存文件

#### M-037 获取视频信息失败（快速缓存）

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/adapter/video/VideoQuickCache.kt:55`、`:77`、`:103`、`:120`
- **出现在哪**：快速缓存前拉视频信息失败
- **载体**：Toast
- **用途**：告知失败环节
- **原文**：

> 获取视频信息失败

- **改写**：获取视频信息失败

#### M-038 已开始缓存

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/adapter/video/VideoQuickCache.kt:74`、`:118`
- **出现在哪**：快速缓存（整段视频）入队成功
- **载体**：Toast
- **用途**：确认任务已开始
- **原文**：

> 已开始缓存

- **改写**：已开始缓存

#### M-039 已开始缓存音频

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/adapter/video/VideoQuickCache.kt:100`
- **出现在哪**：快速缓存「仅音频」入队成功
- **载体**：Toast
- **用途**：确认任务已开始，并说明缓存的是音频
- **原文**：

> 已开始缓存音频

- **改写**：已开始缓存音频

#### M-040 视频播放失败（短视频）

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/video/ShortVideoPlayerActivity.kt:332`
- **出现在哪**：短视频页播放器回调报错
- **载体**：Toast
- **用途**：告知播放中断
- **原文**：

> 播放错误

- **改写**：播放错误

#### M-041 加载视频失败（短视频）

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/video/ShortVideoPlayerActivity.kt:647`
- **出现在哪**：短视频页拉取播放地址失败
- **载体**：Toast
- **用途**：告知失败环节
- **原文**：

> 加载视频失败

- **改写**：加载视频失败

#### M-042 播放器初始化失败

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/video/ShortVideoPlayerActivity.kt:761`
- **出现在哪**：短视频页 ijk 播放器创建失败
- **载体**：Toast
- **用途**：告知这是播放器本身的问题
- **原文**：

> 播放器初始化失败

- **改写**：播放器初始化失败

### 番剧

#### M-043 定位到上次观看

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/video/info/BangumiInfoFragment.kt:340`
- **出现在哪**：打开番剧详情页，自动滚到上次看的剧集时
- **载体**：Toast
- **用途**：解释页面为什么不在开头
- **原文**：

> 已定位到上次观看的「」

- **备注**：模板为 `"已定位到上次观看的「" + 剧集标题 + "」"`
- **改写**：已定位到上次观看的「」

#### M-044 该季暂无内容

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/video/info/BangumiInfoFragment.kt:458`
- **出现在哪**：切季成功后该季没有可展示的剧集/分区
- **载体**：Toast
- **用途**：解释空白
- **原文**：

> 该季暂无内容

- **改写**：该季暂无内容

#### M-045 切换失败

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/video/info/BangumiInfoFragment.kt:472`
- **出现在哪**：切季请求失败
- **载体**：Toast
- **用途**：给出重试预期
- **原文**：

> 切换失败，请稍后重试

- **改写**：切换失败，请稍后重试

#### M-046 未登录无法追番

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/video/info/BangumiInfoFragment.kt:554`
- **出现在哪**：未登录时点「追番」
- **载体**：Toast
- **用途**：解释为什么不让追，隐含引导登录
- **原文**：

> 未登录，无法追番

- **改写**：未登录，无法追番

#### M-047 追番结果

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/video/info/BangumiInfoFragment.kt:567`
- **出现在哪**：点「追番/取消追番」成功后
- **载体**：Toast
- **用途**：确认状态已切换
- **原文**：

> 已追番

> 已取消追番

- **改写**：

> 已追番

> 已取消追番

#### M-048 追番操作失败

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/video/info/BangumiInfoFragment.kt:569`、`:576`
- **出现在哪**：追番/取消追番请求失败（返回码非 0 或抛异常）
- **载体**：Toast
- **用途**：安抚+给重试预期
- **原文**：

> 操作失败，请稍后重试

- **改写**：操作失败，请稍后重试

#### M-049 该季暂无剧集（弹窗）

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/video/info/BangumiInfoFragment.kt:707-708`
- **出现在哪**：点「选集」但当前季没有剧集（先 Toast 再弹提示框）
- **载体**：弹窗（`TerminalDialog.alert`，只有一个按钮「知道了」）+ 同时一条 Toast
- **用途**：明确告知没有可选项，避免用户以为选集按钮坏了
- **原文**：

> 该季暂无剧集

- **改写**：该季暂无剧集

### 收藏

#### M-050 收藏夹为空

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/user/favorite/FavoriteVideoListActivity.kt:469`
- **出现在哪**：进入收藏夹详情但接口返回空列表
- **载体**：Toast
- **用途**：解释空列表不是加载失败
- **原文**：

> 收藏夹为空

- **改写**：收藏夹为空

#### M-051 无法获取收藏夹信息

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/user/favorite/FavoriteFolderListActivity.kt:44`
- **出现在哪**：收藏夹列表页首次加载失败
- **载体**：Toast
- **用途**：给重试预期
- **原文**：

> 无法获取收藏夹信息，请稍后重试

- **改写**：无法获取收藏夹信息，请稍后重试

#### M-052 默认收藏夹不能编辑（列表）

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/adapter/favorite/FavoriteFolderAdapter.kt:131`
- **出现在哪**：收藏夹列表长按「默认收藏夹」想改名/删除时
- **载体**：Toast
- **用途**：解释系统限制
- **原文**：

> 默认收藏夹不能编辑

- **改写**：默认收藏夹不能编辑

#### M-053 默认收藏夹不能编辑或删除（编辑页）

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/user/favorite/FavoriteFolderEditActivity.kt:57`
- **出现在哪**：进入默认收藏夹的编辑页
- **载体**：Toast
- **用途**：解释为什么页面上什么都不能改
- **原文**：

> 默认收藏夹不能编辑或删除

- **改写**：默认收藏夹不能编辑或删除

#### M-054 请输入收藏夹名称

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/user/favorite/FavoriteFolderEditActivity.kt:67`、`app/src/main/java/com/RobinNotBad/BiliClient/activity/user/favorite/FavoriteFolderCreateActivity.kt:40`
- **出现在哪**：保存/创建收藏夹时名称为空
- **载体**：Toast
- **用途**：表单校验提示
- **原文**：

> 请输入收藏夹名称

- **改写**：请输入收藏夹名称

#### M-055 收藏夹保存结果

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/user/favorite/FavoriteFolderEditActivity.kt:80`、`:84`
- **出现在哪**：编辑收藏夹后点保存
- **载体**：Toast
- **用途**：告知保存结果；失败时带错误码
- **原文**：

> 保存成功

> 保存失败，错误码：

- **备注**：失败时尾部拼接错误码
- **改写**：

> 保存成功

> 保存失败，错误码：

#### M-056 再次点击确认删除（收藏夹）

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/user/favorite/FavoriteFolderEditActivity.kt:99`
- **出现在哪**：编辑页点第一次「删除」时，用二次点击代替确认框
- **载体**：Toast
- **用途**：防误删，告知还需要再确认一次
- **原文**：

> 再次点击删除按钮确认删除

- **改写**：再次点击删除按钮确认删除

#### M-057 收藏夹删除结果

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/user/favorite/FavoriteFolderEditActivity.kt:117`、`:121`
- **出现在哪**：确认删除收藏夹后
- **载体**：Toast
- **用途**：告知删除结果
- **原文**：

> 删除成功

> 删除失败，错误码：

- **备注**：失败时尾部拼接错误码
- **改写**：

> 删除成功

> 删除失败，错误码：

#### M-058 收藏夹创建结果

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/user/favorite/FavoriteFolderCreateActivity.kt:53`、`:57`
- **出现在哪**：新建收藏夹提交后
- **载体**：Toast
- **用途**：告知创建结果
- **原文**：

> 创建成功

> 创建失败，错误码：

- **备注**：失败时尾部拼接错误码
- **改写**：

> 创建成功

> 创建失败，错误码：

#### M-059 多选管理引导

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/user/favorite/FavoriteVideoListActivity.kt:156`
- **出现在哪**：收藏夹里首次进入多选管理条时
- **载体**：Toast
- **用途**：教交互
- **原文**：

> 点条目勾选，再点「删除」

- **改写**：点条目勾选，再点「删除」

#### M-060 没选条目

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/user/favorite/FavoriteVideoListActivity.kt:188`、`:193`
- **出现在哪**：多选管理下未勾选任何条目就点「删除」/「移动」
- **载体**：Toast
- **用途**：挡住无效操作
- **原文**：

> 先选几条吧~

- **改写**：先选几条吧~

#### M-061 批量删除结果

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/user/favorite/FavoriteVideoListActivity.kt:207`
- **出现在哪**：收藏夹多选删除成功后
- **载体**：Toast
- **用途**：报告删了几条
- **原文**：

> 已删除 条

- **备注**：模板为 `"已删除 ${targets.size} 条"`
- **改写**：已删除 条

#### M-062 再次长按管理

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/user/favorite/FavoriteVideoListActivity.kt:319`
- **出现在哪**：收藏夹里第一次长按条目时的提示
- **载体**：Toast
- **用途**：告知长按可以重复进入管理
- **原文**：

> 再次长按管理

- **改写**：再次长按管理

#### M-063 没有别的收藏夹

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/user/favorite/FavoriteVideoListActivity.kt:370`
- **出现在哪**：长按条目选「移动/复制到其他收藏夹」，但只有当前这一个收藏夹
- **载体**：Toast
- **用途**：解释为什么没有选项
- **原文**：

> 没有别的收藏夹可以放喵~

- **改写**：没有别的收藏夹可以放喵~

#### M-064 移动/复制结果

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/user/favorite/FavoriteVideoListActivity.kt:392`
- **出现在哪**：长按条目移动到/复制到另一个收藏夹成功后
- **载体**：Toast
- **用途**：确认动作类型（移动还是复制）
- **原文**：

> 已移动

> 已复制

- **改写**：

> 已移动

> 已复制

#### M-065 取消收藏结果

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/user/favorite/FavoriteVideoListActivity.kt:416`、`:419`
- **出现在哪**：收藏夹里长按条目取消收藏后
- **载体**：Toast
- **用途**：告知结果
- **原文**：

> 已取消收藏

> 取消失败，错误码：

- **备注**：失败时尾部拼接错误码
- **改写**：

> 已取消收藏

> 取消失败，错误码：

#### M-066 收藏夹条目删除结果

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/user/favorite/FavoriteVideoListActivity.kt:305`、`:311`
- **出现在哪**：收藏夹里长按删除单条
- **载体**：Toast
- **用途**：告知结果
- **原文**：

> 删除成功

> 删除失败，错误码：

- **改写**：

> 删除成功

> 删除失败，错误码：

#### M-067 收藏夹内获取视频信息失败

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/user/favorite/FavoriteVideoListActivity.kt:498`
- **出现在哪**：收藏夹里点条目跳转前补拉视频信息失败
- **载体**：Toast
- **用途**：告知无法跳转
- **原文**：

> 获取视频信息失败

- **改写**：获取视频信息失败

#### M-068 收藏夹选择框删除/添加失败

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/adapter/favorite/FolderChooseAdapter.kt:71`、`:94`
- **出现在哪**：视频加入收藏夹时勾选/取消勾选某个收藏夹失败
- **载体**：Toast
- **用途**：告知失败并留错误码
- **原文**：

> 删除失败！错误码：

> 添加失败！错误码：

- **备注**：尾部拼接接口返回码
- **改写**：

> 删除失败！错误码：

> 添加失败！错误码：

### 动态

#### M-069 没有可操作的项（动态）

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/adapter/dynamic/DynamicHolder.kt:249`
- **出现在哪**：长按动态正文或个人空间动态卡片的「管理」按钮，但当前动态既不能复制也不能编辑/删除
- **载体**：Toast
- **用途**：解释为什么没弹菜单
- **原文**：

> 没有可操作的项

- **改写**：没有可操作的项

#### M-070 动态没有可编辑正文

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/adapter/dynamic/DynamicHolder.kt:271`
- **出现在哪**：点「编辑动态」但这条动态只有图片/视频卡片、没有文字
- **载体**：Toast
- **用途**：解释为什么进不去编辑页
- **原文**：

> 这条动态没有能编辑的正文

- **改写**：这条动态没有能编辑的正文

#### M-071 置顶动态结果

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/adapter/dynamic/DynamicHolder.kt:299`、`:301`
- **出现在哪**：从动态菜单里选「置顶动态/取消置顶」后
- **载体**：Toast
- **用途**：告知置顶状态是否已切换
- **原文**：

> 置顶成功

> 取消置顶成功

- **备注**：成功/失败文案由 `DynamicApi.topSuccessMsg(top)` / `topErrorMsg(code)` 生成，取不到时兜底 `"操作失败（$code）"`
- **改写**：

> 置顶成功

> 取消置顶成功

#### M-072 删除动态结果

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/adapter/dynamic/DynamicHolder.kt:323`、`:326`、`:328`、`:329`
- **出现在哪**：动态菜单选「删除动态」并确认后
- **载体**：Toast
- **用途**：告知删除结果；失败时按错误码给出更具体的话
- **原文**：

> 删除成功~

> 操作失败：

> 已经删除过了哦~

> 不是自己的动态！

- **备注**：`操作失败：` 尾部拼接返回码；500404 → 已经删除过了哦~；500406 → 不是自己的动态！
- **改写**：

> 删除成功~

> 操作失败：

> 已经删除过了哦~

> 不是自己的动态！

#### M-073 请先登录（动态点赞栏）

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/adapter/dynamic/DynamicHolder.kt:618`
- **出现在哪**：未登录时点动态卡片底部点赞按钮
- **载体**：Toast
- **用途**：引导登录
- **原文**：

> 请先登录

- **改写**：请先登录

#### M-074 动态投票成功

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/adapter/dynamic/DynamicHolder.kt:646`
- **出现在哪**：动态里的投票组件点「投票」成功后
- **载体**：Toast
- **用途**：确认投票已记录
- **原文**：

> 投票成功~

- **改写**：投票成功~

#### M-075 动态点赞/取消结果

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/adapter/dynamic/DynamicHolder.kt:916`、`:926`、`:935`、`:944`、`:953`
- **出现在哪**：动态卡片底部点赞按钮点击后（先「正在处理中」，成功/失败再提示）
- **载体**：Toast
- **用途**：防连点 + 告知结果
- **原文**：

> 正在处理中

> 点赞成功

> 点赞失败

> 取消成功

> 取消失败

- **改写**：

> 正在处理中

> 点赞成功

> 点赞失败

> 取消成功

> 取消失败

#### M-076 转发动态结果（动态页）

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/dynamic/DynamicActivity.kt:72`、`:74`
- **出现在哪**：动态详情页转发成功后
- **载体**：Toast
- **用途**：告知转发结果
- **原文**：

> 转发成功~

> 转发失败

- **改写**：

> 转发成功~

> 转发失败

#### M-077 还在加载中

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/dynamic/DynamicActivity.kt:112`
- **出现在哪**：动态详情还没加载完就点转发/评论等按钮
- **载体**：Toast
- **用途**：挡住无效操作
- **原文**：

> 还在加载中OvO

- **改写**：还在加载中OvO

#### M-078 还没输入内容（动态）

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/dynamic/DynamicActivity.kt:135`
- **出现在哪**：转发动态时正文输入框为空就点发送
- **载体**：Toast
- **用途**：表单校验
- **原文**：

> 还没输入内容呢~

- **改写**：还没输入内容呢~

#### M-079 定时发布与发送结果（动态）

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/dynamic/DynamicActivity.kt:183`、`:204`
- **出现在哪**：发送动态成功后（带定时则提示定时）／发送失败
- **载体**：Toast
- **用途**：确认发布结果
- **原文**：

> 已设置定时发布~

> 发送成功~

> 发送失败

- **改写**：

> 已设置定时发布~

> 发送成功~

> 发送失败

#### M-080 图片数量上限（发动态）

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/dynamic/send/SendDynamicActivity.kt:94`
- **出现在哪**：发布动态时选图超过上限
- **载体**：Toast
- **用途**：告知上限
- **原文**：

> 最多张图片

- **备注**：模板为 `"最多${ImageApi.MAX_IMAGE_COUNT}张图片"`
- **改写**：最多张图片

#### M-081 投票选项上限

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/dynamic/send/SendDynamicActivity.kt:191`
- **出现在哪**：发带投票的动态时添加第 11 个选项
- **载体**：Toast
- **用途**：告知上限
- **原文**：

> 最多10个选项

- **改写**：最多10个选项

#### M-082 Cookie 刷新失败禁止敏感操作（发动态）

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/dynamic/send/SendDynamicActivity.kt:205`
- **出现在哪**：发送动态前检测到上次 Cookie 刷新失败，弹整页提示（无倒计时，可直接关）
- **载体**：整页弹窗（`MsgUtil.showDialog`，标题「无法发送」）
- **用途**：解释为什么发不出去，并引出「重新登录」这个动作
- **原文**：

> 上一次的Cookie刷新失败了，
> 您可能需要重新登录以进行敏感操作

- **改写**：

> 上一次的Cookie刷新失败了，
> 您可能需要重新登录以进行敏感操作

#### M-083 正在发送中（动态）

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/dynamic/send/SendDynamicActivity.kt:209`
- **出现在哪**：点发送后、请求返回前
- **载体**：Toast
- **用途**：防连点
- **原文**：

> 正在发送中

- **改写**：正在发送中

#### M-084 带图不能发投票

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/dynamic/send/SendDynamicActivity.kt:227`
- **出现在哪**：同时带图片和投票准备发送
- **载体**：Toast
- **用途**：解释功能限制
- **原文**：

> 带图动态暂不支持同时发投票

- **改写**：带图动态暂不支持同时发投票

#### M-085 正在上传图片（动态）

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/dynamic/send/SendDynamicActivity.kt:247`
- **出现在哪**：带图动态点发送、图片开始上传时
- **载体**：Toast
- **用途**：告知进度阶段
- **原文**：

> 正在上传图片...

- **改写**：正在上传图片...

#### M-086 已设为定时发布

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/dynamic/send/SendDynamicActivity.kt:323`
- **出现在哪**：设置完定时发布时间后
- **载体**：Toast
- **用途**：确认定时设置已生效
- **原文**：

> 已设为定时发布

- **改写**：已设为定时发布

#### M-087 正文不能为空

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/dynamic/send/SendDynamicActivity.kt:346`
- **出现在哪**：动态正文为空（或只有图片以外什么都填）点保存/发送
- **载体**：Toast
- **用途**：表单校验
- **原文**：

> 正文不能为空

- **改写**：正文不能为空

#### M-088 正在保存（动态草稿）

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/dynamic/send/SendDynamicActivity.kt:351`
- **出现在哪**：退出编辑页自动保存草稿时
- **载体**：Toast
- **用途**：告知草稿已保存动作
- **原文**：

> 正在保存…

- **改写**：正在保存…

#### M-089 修改动态结果

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/dynamic/send/SendDynamicActivity.kt:372`、`:376`
- **出现在哪**：从长按菜单进「编辑动态」改完提交
- **载体**：Toast
- **用途**：告知修改结果
- **原文**：

> 修改成功~

> 修改失败（$code）

- **备注**：失败文案优先取 `DynamicApi.editErrorMsg(code)`，取不到时兜底 `"修改失败（$code）"`
- **改写**：

> 修改成功~

> 修改失败（$code）

#### M-090 投票表单校验

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/dynamic/send/SendDynamicActivity.kt:449`、`:453`
- **出现在哪**：发带投票的动态时投票标题为空 / 选项少于 2 个
- **载体**：Toast
- **用途**：表单校验
- **原文**：

> 请填写投票标题

> 至少需要2个选项

- **改写**：

> 请填写投票标题

> 至少需要2个选项

#### M-091 投票相关提示（投票页）

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/vote/VoteInfoActivity.kt:63`、`:92`、`:96`、`:115`、`:240`、`:292`、`:302`、`:312`、`:345`、`:348`
- **出现在哪**：投票详情页——投票 ID 解析失败、未选就提交、没选够、投票信息拉取失败、超过可选上限、未登录点投票、投票成功、删除投票
- **载体**：Toast
- **用途**：表单校验 / 结果告知
- **原文**：

> 投票ID无效

> 请先选择选项

> 还需选择 项

> 投票信息获取失败

> 最多选择 项

> 请先登录

> 投票成功~

> 删除成功

> 删除失败：

- **备注**：`还需选择 ${info.choice_cnt - selectedOptions.size} 项`、`最多选择 ${info.choice_cnt} 项`、`删除失败：$result` 为拼接式
- **改写**：

> 投票ID无效

> 请先选择选项

> 还需选择 项

> 投票信息获取失败

> 最多选择 项

> 请先登录

> 投票成功~

> 删除成功

> 删除失败：

#### M-092 用户动态页用户不存在

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/user/info/UserDynamicFragment.kt:55`
- **出现在哪**：进入某用户的动态页但用户信息拉不到
- **载体**：Toast
- **用途**：解释空白页
- **原文**：

> 用户不存在

- **改写**：用户不存在

#### M-093 内容失效无法打开

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/adapter/article/OpusAdapter.kt:53`
- **出现在哪**：列表里点一条已失效的图文动态
- **载体**：Toast
- **用途**：解释为什么打不开
- **原文**：

> 内容失效，无法打开

- **改写**：内容失效，无法打开

#### M-094 图文点赞结果

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/adapter/article/OpusContentAdapter.kt:233`、`:245`、`:256`
- **出现在哪**：专栏/图文正文底部点赞按钮——未登录、成功、失败
- **载体**：Toast
- **用途**：告知结果
- **原文**：

> 还没有登录喵~

> 点赞成功

> 取消成功

> 操作失败：

- **备注**：`操作失败：` 尾部拼接返回码
- **改写**：

> 还没有登录喵~

> 点赞成功

> 取消成功

> 操作失败：

#### M-095 图文投币结果

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/adapter/article/OpusContentAdapter.kt:270`、`:279`、`:289`、`:295`
- **出现在哪**：专栏/图文正文投币按钮
- **载体**：Toast
- **用途**：告知结果
- **原文**：

> 还没有登录喵~

> 投币成功！

> 投币数量到达上限

- **改写**：

> 还没有登录喵~

> 投币成功！

> 投币数量到达上限

#### M-096 图文收藏结果

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/adapter/article/OpusContentAdapter.kt:305`、`:315`、`:318`、`:327`、`:330`
- **出现在哪**：专栏/图文正文收藏按钮
- **载体**：Toast
- **用途**：告知结果
- **原文**：

> 还没有登录喵~

> 已取消收藏~

> 取消收藏失败

> 收藏成功~

> 收藏失败

- **改写**：

> 还没有登录喵~

> 已取消收藏~

> 取消收藏失败

> 收藏成功~

> 收藏失败

#### M-097 专栏缓存结果

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/adapter/article/ArticleCardAdapter.kt:81`、`:112`、`:114`、`:117`
- **出现在哪**：专栏卡片长按/菜单选「缓存」
- **载体**：Toast
- **用途**：告知已缓存或失败
- **原文**：

> 该专栏已缓存

> 专栏已缓存

> 获取专栏信息失败

- **改写**：

> 该专栏已缓存

> 专栏已缓存

> 获取专栏信息失败

### 消息与私信

#### M-098 私信拦截提醒

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/message/PrivateMsgActivity.kt:82`
- **出现在哪**：进入私信会话页时（每次进入提示一次）
- **载体**：Toast（长，含换行）
- **用途**：提前说明可能收不到回复，降低「发了没人理」的困惑
- **原文**：

> 私信有可能被拦截
> 尽量不要用终端发私信喵

- **改写**：

> 私信有可能被拦截
> 尽量不要用终端发私信喵

#### M-099 私信发送结果

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/message/PrivateMsgActivity.kt:164`、`:168`、`:170`、`:173`、`:246`、`:250`、`:253`
- **出现在哪**：私信会话页发文字消息——成功/失败（含服务端返回的 message）
- **载体**：Toast
- **用途**：告知发送结果
- **原文**：

> 发送成功

> 发送失败

> 发送失败：

> 发送失败：

- **备注**：`发送失败：\n$result` 与 `发送失败：$result` 两处模板不同；`result.getString("message")` 直接回显服务端话术
- **改写**：

> 发送成功

> 发送失败

> 发送失败：

> 发送失败：

#### M-100 私信空输入

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/message/PrivateMsgActivity.kt:178`
- **出现在哪**：私信输入框为空就点发送
- **载体**：Toast
- **用途**：表单校验
- **原文**：

> 你还木有输入喵~

- **改写**：你还木有输入喵~

#### M-101 私信未登录

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/message/PrivateMsgActivity.kt:188`
- **出现在哪**：未登录时进入/操作私信页
- **载体**：Toast
- **用途**：引导登录
- **原文**：

> 还没有登录喵~

- **改写**：还没有登录喵~

#### M-102 正在上传图片（私信）

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/message/PrivateMsgActivity.kt:219`
- **出现在哪**：私信发图时
- **载体**：Toast
- **用途**：告知进度阶段
- **原文**：

> 正在上传图片喵…

- **改写**：正在上传图片喵…

#### M-103 加载更多消息

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/message/PrivateMsgActivity.kt:328`、`:348`、`:355`
- **出现在哪**：私信会话上滑加载更早消息——请求中 / 成功 / 没有更多
- **载体**：Toast
- **用途**：告知翻页状态
- **原文**：

> 加载更多中...

> 已加载更多消息！

> 没有更多消息了

- **改写**：

> 加载更多中...

> 已加载更多消息！

> 没有更多消息了

#### M-104 会话置顶/移除结果

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/adapter/message/PrivateMsgSessionsAdapter.kt:169`、`:195`
- **出现在哪**：消息会话列表长按菜单——置顶/取消置顶、从会话列表移除
- **载体**：Toast
- **用途**：告知结果
- **原文**：

> 已置顶

> 已取消置顶

> 已从会话列表移除

- **改写**：

> 已置顶

> 已取消置顶

> 已从会话列表移除

#### M-105 消息设置加载/保存结果

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/message/MessageSettingsActivity.kt:62`、`:162`、`:166`、`:172`
- **出现在哪**：消息设置页拉取设置失败 / 保存成功 / 保存失败
- **载体**：Toast
- **用途**：告知结果
- **原文**：

> 获取设置失败: 未知错误

> 设置已保存

> 保存失败: $message

> 保存失败

- **备注**：`获取设置失败:` 后接服务端 message（缺省「未知错误」）；`保存失败: $message`
- **改写**：

> 获取设置失败: 未知错误

> 设置已保存

> 保存失败: $message

> 保存失败

#### M-106 通知类消息不支持跳转

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/adapter/message/NoticeHolder.kt:114`、`:130`、`:147`
- **出现在哪**：点消息中心的系统通知卡片——子评论无法定位、未知业务类型、未知条目类型
- **载体**：Toast
- **用途**：解释为什么点不动
- **原文**：

> 视频的子评论暂时无法定位，也许以后会做吧……

> 不支持这个类型喵：

- **备注**：`不支持这个类型喵：` 尾部拼接 businessId / itemType
- **改写**：

> 视频的子评论暂时无法定位，也许以后会做吧……

> 不支持这个类型喵：

#### M-107 分享消息打不开

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/adapter/message/PrivateMsgAdapter.kt:213`、`:218`
- **出现在哪**：点私信里的分享卡片——直播间信息解析失败 / 非支持的分享类型
- **载体**：Toast
- **用途**：解释为什么打不开
- **原文**：

> 无法解析直播间信息

> 暂不支持打开此类分享内容

- **改写**：

> 无法解析直播间信息

> 暂不支持打开此类分享内容

#### M-108 动态页转发/发送异常前缀

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/adapter/dynamic/UserDynamicAdapter.kt:305`、`:342`
- **出现在哪**：用户动态列表里转发/发送失败，走 `MsgUtil.err` 统一异常提示
- **载体**：Toast（长，Snackbar）
- **用途**：用统一异常话术兜底
- **原文**（异常时显示的内容模板）：

> 错误：

- **备注**：由 `MsgUtil.err` 生成，见「三、错误提示」
- **改写**：错误：

### 搜索与历史

#### M-109 搜索历史删除

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/search/SearchActivity.kt:213`
- **出现在哪**：搜索页点历史记录条目旁的删除
- **载体**：Toast
- **用途**：确认删除
- **原文**：

> 删除成功

- **改写**：删除成功

#### M-110 可点标题栏返回详情

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/search/SearchActivity.kt:301`
- **出现在哪**：从详情页跳进搜索页时提示返回路径
- **载体**：Toast
- **用途**：教交互
- **原文**：

> 可点击标题栏返回详情页

- **改写**：可点击标题栏返回详情页

#### M-111 搜索空输入

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/search/SearchActivity.kt:461`
- **出现在哪**：搜索框为空点搜索
- **载体**：Toast
- **用途**：表单校验
- **原文**：

> 还没输入内容喵~

- **改写**：还没输入内容喵~

#### M-112 搜索到底

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/search/SearchFragment.kt:38`
- **出现在哪**：搜索结果翻到最后一页仍点「加载更多」
- **载体**：Toast
- **用途**：告知数据已到底，别再点了
- **原文**：

> 已经到底啦OwO

- **改写**：已经到底啦OwO

#### M-113 搜索加载失败

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/search/SearchFragment.kt:149`
- **出现在哪**：搜索结果加载失败
- **载体**：Toast（长）
- **用途**：告知失败
- **原文**：

> 加载失败

- **改写**：加载失败

#### M-114 热搜失败

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/video/HotSearchActivity.kt:39`、`:46`
- **出现在哪**：热搜页——接口失败 / 网络异常
- **载体**：Toast（长）
- **用途**：区分「接口挂了」与「你断网了」
- **原文**：

> 获取热搜失败，请稍后重试

> 网络异常，请稍后重试

- **改写**：

> 获取热搜失败，请稍后重试

> 网络异常，请稍后重试

#### M-115 历史删除

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/user/HistoryActivity.kt:48`、`:53`、`:62`
- **出现在哪**：历史记录页长按条目删除——成功 / 失败 / 第一次长按引导
- **载体**：Toast
- **用途**：告知结果与二次确认方式
- **原文**：

> 删除成功

> 删除失败，错误码：

> 再次长按删除

- **改写**：

> 删除成功

> 删除失败，错误码：

> 再次长按删除

#### M-116 稍后再看清除/移除结果

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/user/WatchLaterActivity.kt:131`、`:134`、`:205`、`:213`
- **出现在哪**：稍后再看页——清除所有已看完、移除单条
- **载体**：Toast
- **用途**：告知结果
- **原文**：

> 已清除所有已看完

> 清除失败，错误码：

> 已移除

> 移除失败，错误码：

- **备注**：错误码为拼接
- **改写**：

> 已清除所有已看完

> 清除失败，错误码：

> 已移除

> 移除失败，错误码：

#### M-117 经验/硬币/登录记录为空

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/user/ExpLogActivity.kt:47`、`app/src/main/java/com/RobinNotBad/BiliClient/activity/user/CoinLogActivity.kt:47`、`app/src/main/java/com/RobinNotBad/BiliClient/activity/user/LoginRecordActivity.kt:51`
- **出现在哪**：个人空间的「经验明细 / 硬币明细 / 登录记录」页接口返回空列表
- **载体**：Toast
- **用途**：解释空白页
- **原文**：

> 暂无经验变化记录

> 暂无硬币变化记录

> 暂无登录记录

- **改写**：

> 暂无经验变化记录

> 暂无硬币变化记录

> 暂无登录记录

#### M-118 明细页加载失败

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/user/ExpLogActivity.kt:60`、`app/src/main/java/com/RobinNotBad/BiliClient/activity/user/CoinLogActivity.kt:64`、`app/src/main/java/com/RobinNotBad/BiliClient/activity/user/LoginRecordActivity.kt:61`
- **出现在哪**：经验/硬币/登录记录页加载异常
- **载体**：Toast
- **用途**：告知失败并留异常信息
- **原文**：

> 加载失败：

- **备注**：尾部拼接 `e.message`
- **改写**：加载失败：

### 关注与分组

#### M-119 默认分组不可改

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/user/FollowUsersActivity.kt:136`
- **出现在哪**：关注页长按「默认分组」或「特别关注」想改名/删除
- **载体**：Toast
- **用途**：解释系统限制
- **原文**：

> 默认分组和特别关注不能改名或删除

- **改写**：默认分组和特别关注不能改名或删除

#### M-120 没有别的分组可移

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/user/FollowUsersActivity.kt:168`
- **出现在哪**：长按分组里的成员想移到别的分组，但没有其他分组
- **载体**：Toast
- **用途**：解释为什么没弹选择框
- **原文**：

> 还没有别的分组可以移过去

- **改写**：还没有别的分组可以移过去

#### M-121 移动成员结果

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/user/FollowUsersActivity.kt:198`、`:202`
- **出现在哪**：确认把成员移到另一分组后
- **载体**：Toast
- **用途**：告知结果与目标分组名
- **原文**：

> 已移动到「」

- **备注**：模板为 `"已移动到「${toTag.name}」"`；失败时用 `FollowApi.tagErrorMsg(code)` 的服务端话术
- **改写**：已移动到「」

#### M-122 分组名校验

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/user/FollowUsersActivity.kt:216`、`:246`
- **出现在哪**：新建/重命名关注分组时名称不合法
- **载体**：Toast
- **用途**：表单校验
- **原文**（由 `FollowApi.checkTagName` 返回）：

> 分组名不能为空

- **备注**：其他校验失败文案同样来自该函数的返回值
- **改写**：分组名不能为空

#### M-123 分组增删改结果

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/user/FollowUsersActivity.kt:223`、`:255`、`:286`
- **出现在哪**：关注页创建/重命名/删除分组成功后
- **载体**：Toast
- **用途**：告知结果并回显新名字
- **原文**：

> 分组已创建

> 已重命名为「」

> 分组已删除

- **备注**：重命名模板为 `"已重命名为「${name.trim()}」"`
- **改写**：

> 分组已创建

> 已重命名为「」

> 分组已删除

#### M-124 头像/资料上传前置提示

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/user/EditProfileActivity.kt:69`、`:73`、`:93`、`:101`、`:111`、`:129`、`:152`
- **出现在哪**：改头像页——正在上传、Cookie 失效、读图/解码失败、上传成功待审核、失败带错误码
- **载体**：Toast + 整页弹窗（标题「无法上传」）
- **用途**：告知上传链路各阶段状态
- **原文**：

> 正在上传中...

> 正在上传头像...

> 无法读取图片

> 无法解码图片

> 头像上传成功，等待审核

> $errorMsg (错误码:$code)

- **改写**：

> 正在上传中...

> 正在上传头像...

> 无法读取图片

> 无法解码图片

> 头像上传成功，等待审核

> $errorMsg (错误码:$code)

#### M-125 修改个人描述/资料结果

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/user/EditSignActivity.kt:68`、`:73`、`:93`、`:111`；`app/src/main/java/com/RobinNotBad/BiliClient/activity/user/EditUserInfoActivity.kt:52`、`:57`、`:64`、`:75`、`:99`、`:112`
- **出现在哪**：改签名/改资料页——提交中、Cookie 失效、校验失败、提交成功、失败回显
- **载体**：Toast + 整页弹窗（标题「无法提交」）
- **用途**：告知提交链路状态
- **原文**：

> 正在提交中...

> 生日格式错误，请使用YYYY-MM-DD

> 请至少填写一项要修改的内容

> 修改成功

> 修改成功，等待审核

- **备注**：失败时 `errorMsg` 由接口 -101 等错误码映射，含「账号未登录」
- **改写**：

> 正在提交中...

> 生日格式错误，请使用YYYY-MM-DD

> 请至少填写一项要修改的内容

> 修改成功

> 修改成功，等待审核

#### M-126 会员权益领取结果

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/user/VipActivity.kt:111`、`:113`、`:115`
- **出现在哪**：大会员页点「领取」权益后
- **载体**：Toast
- **用途**：告知领取结果
- **原文**：

> 领取成功

> 今日已领取

> 领取失败: $message

- **改写**：

> 领取成功

> 今日已领取

> 领取失败: $message

#### M-127 先去成为 UP 主

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/user/CreativeCenterActivity.kt:31`
- **出现在哪**：未开通创作中心时进入该页
- **载体**：Toast
- **用途**：解释为什么没有数据并引导下一步
- **原文**：

> 先去成为UP主吧~

- **改写**：先去成为UP主吧~

#### M-128 我的页面退出登录

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/user/MySpaceActivity.kt:132`、`:149`、`:155`
- **出现在哪**：我的页面点「退出登录」——请求中、结束（成功或服务端失败）、第一次点击引导二次确认
- **载体**：Toast
- **用途**：告知退出进度与结果
- **原文**：

> 正在退出登录…

> 账号已退出

> 服务端注销失败，已清除本地登录状态

> 再点一次退出登录！

- **改写**：

> 正在退出登录…

> 账号已退出

> 服务端注销失败，已清除本地登录状态

> 再点一次退出登录！

---

## 覆盖性自查

### 已扫描

- `app/src/main/java/**` 下全部 `MsgUtil.showMsg` / `showMsgLong` / `toast` / `toastLong` / `err` / `showDialog` 调用点——**`MsgUtil.err(e)` 的异常翻译文案按「载体 + 追加规则」在本文档第〇节说明，不逐条重复登记**。
- 全部 `TerminalDialog.menu` / `singleChoice` / `confirm` / `choice` / `input` / `alert` 调用点，以及 `TerminalDialog.kt` 自己的默认按钮文字（`确定` / `取消` / `发送` / `知道了` / `点空白处取消`）。
- 通知栏三处：`util/MsgNotifier.kt`（新消息 `1029`、追番更新 `1030`）、`service/PlaybackService.kt`（`1028`）、`service/download/DownloadNotifier.kt`（`1027`），含标题与正文。
- 播放器中央浮层 `loading_text0` / `loading_text1`（位置与三处副本已登记）。
- 页面级空态与列表底部状态条的**文案来源**（`RefreshListActivity` / `RefreshMainActivity`）——**原文登记在 05 第三节**（`U-095`~`U-099`），本文档只登记「哪些条件下会换成别的措辞」。

### 明确排除（归属其它文件）

| 排除对象 | 归属 |
| --- | --- |
| 设置页的条目名与说明、设置项开关的提示 | 04 |
| 页面常驻文案（顶栏标题、按钮、栏目标签、输入框提示、列表项静态标签） | 05 |
| 关于页 / 隐私说明 / 免责声明 / 首次启动引导 / 反馈 / 捐赠 / 特殊登录 的正文 | 01 |
| 更新日志与检查更新页的提示（「正在检查更新...」「当前已是最新版」等） | 02 |
| 教程正文与教程管理页提示 | 03 |
| 彩蛋「回声洞」（`MsgUtil.showText("回声洞", …)` 的内容本身） | 07 |

### 存疑

1. 最后一节「关注与分组」是**收尾兜底节**，实际还顺带收了经验/硬币/登录记录明细、头像与资料上传、大会员权益、退出登录等条目——分类不严格，找东西时**建议直接用编号或关键词搜**，不要只按小节标题找。
2. `MsgUtil.err(e)` 的最终显示文本是「调用点传入的 `desc` + 异常类型映射的固定句」拼起来的，**逐条穷举需要枚举异常类型 × 调用点**，本文档只登记调用点的 `desc` 与 `err_*` 映射表，未穷举组合结果。
3. 少数提示只在 `BuildConfig.DEBUG` 为真时出现（调试包专有）。本文件按「出现即收录」处理，未逐条标注生效条件。
