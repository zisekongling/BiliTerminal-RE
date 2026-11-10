# 26.11.09 头像暗边人工验收单（H05）

> 对应改动：`docs/review/fix-progress.md` §1「『关注的人』头像条出现黑色边框」（PR #24）。
> L0 已过：`:app:assembleDebug` BUILD SUCCESSFUL（26.11.09 本机实测，head `f843aa21`）。下面每项都**只能靠真机看画面**下结论。
> 预计 10 分钟；需要一张自定义背景图 + 至少两套主题。

## H05 动态页顶部头像条的暗边（用户反馈 #17）

**改的是什么**：用户报「自定义背景下，关注界面的『关注的人』列表头像有黑色边框，边框颜色推测由主题决定」，并补充「**只有滑动时**出现，其他页面没有」。用户说的这条横条 = 动态页顶部「最近更新的UP主」（`cell_recent_up_list` → `RecentUpAdapter` → `cell_up_avatar`）。那张 `MaterialCardView` 只负责把方头像裁成圆形，却继承了主题 `CardStyle` 的 `cardElevation 4dp`（亮色 2dp），阴影沿 56dp 圆周投出一圈暗边；本次把它显式压成 `app:cardElevation="0dp"`。描边不是本次改的——它自 `a7fb06be`（2026-10-30）起就已经是 `app:strokeWidth="0dp"`。

1. 设置 → 外观 → 背景图：选一张**有明暗细节**的图（纯色 / 纯深色图上看不出阴影，会出现假阴性）。
2. 进动态页，看顶部「最近更新的UP主」横条：
   - [ ] 静止时头像圆周**没有**暗边 / 黑圈
   - [ ] 手指在横条上**划过或按住**（别松手点到跳转）时，也不冒出暗边 —— **这一项是重点**，用户报的是「只在滑动时出现」；卡片从主题继承了 `clickable=true`，按下态可能抬 `translationZ`
   - [ ] 头像仍是圆形、没被裁掉边缘，「有更新」的粉色小圆点位置不变
3. 换主题再各看一遍：暗色 / 亮色至少各一套，其中一套用带独立 divider 色的主题（知乎 / 爱奇艺 / 紫幻想 / 彩虹 / 经典灰任一）。
4. 关掉自定义背景（用默认背景）扫一眼：与改前一致，没有多余描边。
5. 回归：点头像与名字都能跳到用户空间；横条左右滑动流畅；超长名字仍是省略号。

**若仍能看到暗边**：说明残留来自按下态的 `translationZ`，不是静态 elevation。下一刀是把这张 `MaterialCardView` 换成 `FrameLayout` + `ImageView`（`updateIndicator` 保持 `layout_gravity="top|end"`），并把 `clickable` / `focusable` 从卡片上摘掉——一次消掉「主题描边 / 阴影 / 按压态 translationZ / 假 clickable」四个源。
