# 未来新功能规划（基于 bilibili-API 接口文档）

> 规划日期：2026-08-27
> 依据：`bilibili-API/docs/` 接口文档快照 + 项目现状代码调研
> 原则：中文注释、不引新第三方库、复用现有基建（`CenterThreadPool`/`MsgUtil`/`BaseActivity`/`NetWorkUtil`）

## 一、背景

项目已成熟实现：视频（播放/下载/弹幕/互动视频）、直播（推荐/关注/观看）、动态（浏览/发布）、
评论/回复、私信/消息、收藏/历史/稍后再看、番剧/专栏/搜索/用户空间、创作中心等。

刚删除音频站功能（搜索/歌单/播放）。**视频 DASH 音轨、仅音频下载、播放器音频输出设置**等视频相关能力完好保留。

本规划按「价值/成本比」分三期。

---

## 〇、已实测验证的接口（2026-08-27 curl 验证）

> 以下接口均已用 curl 实测：接口存在、返回结构正常；标注「需登录」的接口在无 Cookie 时返回 `-101 账号未登录`（说明接口有效，登录后可用）。

| 接口 | 地址 | 鉴权 | 验证结果 |
|------|------|------|---------|
| 大会员签到 | `POST /pgc/activity/score/task/sign` | 需登录 | ✅ 文档确认 |
| 漫画签到 | `POST manga.bilibili.com/twirp/activity.v1.Activity/ClockIn` | 需登录 | ✅ 文档确认 |
| 漫画签到状态 | `POST .../GetClockInInfo` | 需登录 | ✅ 文档确认 |
| **直播每日签到** | `GET /xlive/web-ucenter/v1/sign/DoSign` | 需登录 | ✅ 实测 `-101` |
| **直播签到状态** | `GET /xlive/web-ucenter/v1/sign/WebGetSignInfo` | 需登录 | ✅ 实测 `-101` |
| **粉丝勋章佩戴** | `POST /xlive/web-room/v1/fansMedal/wear`（medal_id+csrf） | 需登录 | ✅ 实测 `-101` |
| 粉丝勋章列表 | `GET /xlive/app-ucenter/v1/user/GetMyMedals` | 需登录 | ✅ 文档确认 |
| 视频标签 | `GET /x/tag/archive/tags?bvid=` | 匿名 | ✅ 实测 `code:0`（已实现） |
| 入站必刷 | `GET /x/web-interface/popular/precious` | 匿名 | ✅ 实测 `code:0`（已实现） |
| 排行榜 | `GET /x/web-interface/ranking/v2` | 匿名 | ✅ 实测（已实现） |
| AI 视频总结 | `GET /x/web-interface/view/conclusion/get` | 需登录/会员 | ✅ 实测 `-403`（已实现） |
| 关注/取关 | `POST /x/relation/modify` | 需登录 | ✅ 实测 `-101`（已实现） |
| 历史删除 | `POST /x/v2/history/delete` | 需登录 | ✅ 实测 `-101`（已实现） |

**已实测确认项目缺失、可直接新增的功能**（见下节第 1~4 项）：
1. 直播每日签到（全新）
2. 粉丝勋章佩戴/取消佩戴（现有查看，无操作）
3. 大会员每日签到（现有信息页，无签到）
4. 漫画签到（全新）

---

## 二、高优先级（轻量、接口齐全、见效快）

### 1. 大会员每日签到（大积分）
- **文档**：`docs/vip/clockin.md`
- **接口**：`POST https://api.bilibili.com/pgc/activity/score/task/sign`
  - 参数：`csrf`（Cookie 中 bili_jct）；`Referer` 需在 `*.bilibili.com` 下；SESSDATA 需 URL 编码（`,`→`%2C`）
- **现状**：`VipActivity` 已展示会员信息/权益，但无签到
- **实现**：VipActivity 加"签到"按钮；新增 `VipApi.signClockin()`；复用 `NetWorkUtil.post`
- **工作量**：小（约半天）

### 2. 漫画签到（含补签）
- **文档**：`docs/manga/ClockIn.md`
- **接口**：
  - 签到：`POST https://manga.bilibili.com/twirp/activity.v1.Activity/ClockIn`（参数 `platform=android`）
  - 查状态：`POST .../GetClockInInfo`（返回连续天数 `day_count`、今日是否已签 `status`、积分 `point_infos`）
- **实现**：新建 `MangaApi.java` + 签到页（展示连续天数/积分 + 签到/补签按钮）
- **注意**：`manga.bilibili.com` 为独立域名，需确认 `NetWorkUtil` 的 Cookie 能否跨域携带
- **工作量**：小~中

### 3. 直播每日签到
- **文档**：`docs/live/user.md`（签到部分）
- **接口**（已实测 `-101 需登录`，接口有效）：
  - 签到：`GET https://api.live.bilibili.com/xlive/web-ucenter/v1/sign/DoSign`
  - 查状态：`GET https://api.live.bilibili.com/xlive/web-ucenter/v1/sign/WebGetSignInfo`
- **现状**：项目无直播签到
- **实现**：可在"我的"页或设置页加签到入口；新增 `LiveApi.signClockin()`
- **工作量**：小

### 4. 粉丝勋章佩戴 / 取消佩戴
- **文档**：`docs/live/user.md`（佩戴勋章部分）
- **接口**（已实测 `-101 需登录`，接口有效）：
  - 佩戴：`POST https://api.live.bilibili.com/xlive/web-room/v1/fansMedal/wear`（`medal_id` + `csrf`）
  - 列表：`GET https://api.live.bilibili.com/xlive/app-ucenter/v1/user/GetMyMedals`
- **现状**：`MedalWallActivity` 已有勋章列表查看，但**无佩戴/取消佩戴操作**
- **实现**：MedalWallActivity 每项加"佩戴"按钮（长按取消佩戴）；新增 `LiveApi` 对应方法
- **工作量**：小

---

## 三、中优先级（功能完整、体验提升）

### 5. 视频笔记（观看笔记）
- **文档**：`docs/note/`（list / info / action）
- **接口**：笔记列表、详情、操作；富文本正文（节点序列格式）
- **实现**：视频页加"笔记"入口，支持新建私有笔记 + 查看笔记列表
- **注意**：富文本节点解析工作量中等
- **工作量**：中

### 6. 图文/图片动态发布（含图片上传）
- **文档**：`docs/dynamic/publish.md`
  - 图片上传：`POST https://api.bilibili.com/x/dynamic/feed/draw/upload_bfs`（multipart，`file_up` + `csrf`）
  - 发布图文动态：`POST https://api.bilibili.com/x/dynamic/feed/create/dyn`（`scene=2`，`pics[]` 最多 9 张）
- **现状**：已有 `SendDynamicActivity`，需确认是否支持纯文本/图片
- **注意**：multipart 上传需确认 `NetWorkUtil` 是否支持文件上传
- **工作量**：中

### 7. 直播发弹幕
- **文档**：`docs/live/danmaku.md`
- **现状**：`LiveApi` 仅观看相关（推荐/关注/房间/播放），**无发送弹幕**
- **实现**：直播观看页补"发弹幕"（HTTP 方式）
- **注意**：直播弹幕协议较复杂，建议先做 HTTP 发送
- **工作量**：中

### 8. 充电（给 UP 主/自己充电）
- **文档**：`docs/electric/`（Bcoin / charge_list / charge_msg / monthly）
- **实现**：视频页/UP 主页加"充电"入口
- **注意**：**涉及实际扣费**，需谨慎 + 确认风控后再上
- **工作量**：中

---

## 四、低优先级（复杂/依赖外部条件）

### 9. 直播管理后台（房管功能）
- **文档**：`docs/live/manage.md`、`silent_user_manage.md`、`guard.md`
- **内容**：禁言、设房管、舰长管理等（主播向）
- **注意**：依赖登录账号为主播，受众窄
- **工作量**：大

### 10. 漫画客户端
- **文档**：`docs/manga/` 全目录（Comic/Season/Download/点券）
- **内容**：漫画浏览 / 阅读 / 下载
- **注意**：需图片阅读器、章节管理，建议独立规划
- **工作量**：大

### 11. 互动视频 / 创作中心增强
- **文档**：`docs/video/interact_video.md`、`docs/creativecenter/`
- **现状**：已有 InteractionVideo + CreativeCenter
- **改进**：补齐创作数据统计、稿件管理等
- **工作量**：中

### 12. 青少年模式 / 风控应对
- **文档**：`docs/teenager/teenager_mode.md`
- **价值**：合规需求
- **工作量**：中

---

## 五、建议落地路线

| 阶段 | 功能 | 理由 |
|------|------|------|
| 第一期 | 大会员签到、漫画签到、**直播签到** | 最小成本、立即可用、接口已验证 |
| 第一期 | **粉丝勋章佩戴** | 现有勋章页补一个操作即可 |
| 第二期 | 视频笔记、图文动态发布 | 功能完整、体验提升 |
| 第三期 | 直播发弹幕、充电 | 交互增强 |
| 待评估 | 漫画客户端、直播管理 | 工作量大、受众窄 |

---

## 六、备注

- 实现前建议先在 `docs/superpowers/specs/` 写设计文档（遵循项目惯例）。
- 涉及写操作（签到/发布/充电）务必先确认账号风控与接口可用性，参考 `NetWorkUtil` 的 DOCTYPE/风控重试机制。
- 音频站功能已删除，规划中不再包含音乐/歌单相关内容。
