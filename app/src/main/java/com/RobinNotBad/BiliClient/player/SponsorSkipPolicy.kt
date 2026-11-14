package com.RobinNotBad.BiliClient.player

import com.RobinNotBad.BiliClient.api.SponsorBlockApi

/**
 * BilibiliSponsorBlock 片段的**纯判定逻辑**（零 Android 依赖，可直接 JVM 单测）。
 *
 * 分工与「跳过片头片尾」那套完全一致：`api/SponsorBlockApi` 只负责把 JSON 翻成原始片段，
 * 本文件只回答「这些原始片段里哪些该跳、跳到哪」，真正的 `seekTo` 由播放页完成，
 * 片段表 + 已处理集合 + 在途代次交给 `activity/player/SkipDecider`（第二个实例）。
 *
 * ## 为什么默认关闭、且不给「全部类别」
 *
 * 上游的类别里有一半是**主观判断**：`filler`（离题闲聊）、`music_offtopic`（MV 里的谈话段）、
 * `preview`（前情提要）都有人觉得"这也算正片"。上游文档自己都写了 filler 这类"不建议自动跳过"。
 * 所以默认档位是 [LEVEL_OFF]，用户主动选档后才跳；[LEVEL_ALL] 仍然**排除** `poi_highlight`
 * ——它是"精彩时刻跳转点"，不是一段广告，跳过去毫无意义。
 *
 * ## 三道保险（都是防"脏数据把用户甩到片尾"）
 *
 * 1. **类别白名单**：[categoriesFor] 之外的一律不跳；
 * 2. **只跳 skip**：`mute`（只静音）不在 v1 范围；`full`（整段视频标签）返回 `[0,0]`，
 *    若当成区间会命中 0 秒那次判定，必须靠 [ACTION_SKIP] 挡掉；
 * 3. **时长对不上就不跳**：标注的 `videoDuration` 与当前视频总时长差超过
 *    [DURATION_TOLERANCE_SEC] 时，说明这条标注是**换源/重传前**的版本，秒数已错位，直接丢弃。
 */
object SponsorSkipPolicy {

    // ---- 档位（存档里存的是这些 int 的字符串形式） ----

    const val LEVEL_OFF = 0

    /** 只跳付费推广（`sponsor`）——最保守的一档。 */
    const val LEVEL_AD = 1

    /** 再加无偿自我推广（充电名单、周边、口头推荐）。 */
    const val LEVEL_PROMO = 2

    /** 推荐档：再加三连提醒、片头、片尾（对 UGC 视频尤其有用，番剧另有片头片尾开关）。 */
    const val LEVEL_RECOMMENDED = 3

    /** 全部类别（仍不含 `poi_highlight`）。 */
    const val LEVEL_ALL = 4

    /**
     * 自定义档：类别由用户在「要跳的类别」页逐项勾选，见 [resolveCategories]。
     *
     * 勾选结果存在 `player_sponsor_block_cat_<类别>`（每类一个布尔键），
     * 所以这个档位本身不带任何预设集合——空集合就是"一个都不跳"。
     */
    const val LEVEL_CUSTOM = 5

    /** 档位个数（设置页选项数）。 */
    const val LEVEL_COUNT = 6

    /** 档位合法上界，改档位个数时一并改它。 */
    private const val LEVEL_MAX = LEVEL_CUSTOM

    // ---- 类别 id（与上游 API 一致，不要改名） ----

    /** 广告：拿到了赞助的付费推广、口播、贴片式广告。 */
    const val CAT_SPONSOR = "sponsor"

    /** 无偿/自我推广：没拿钱的自我宣传——充电名单、周边、口头推荐、致谢、慈善呼吁。 */
    const val CAT_SELFPROMO = "selfpromo"

    /** 三连/订阅提醒：求赞求关注求充电、评论互动、回应捐赠。 */
    const val CAT_INTERACTION = "interaction"

    /** 过场/开场动画：无内容的间隔、动画、免责声明、版权页。 */
    const val CAT_INTRO = "intro"

    /** 鸣谢/结束画面：片尾字幕、鸣谢、结束动画。 */
    const val CAT_OUTRO = "outro"

    /** 回顾/概要：前情提要、本期看点。 */
    const val CAT_PREVIEW = "preview"

    /** 离题闲聊/玩笑：与正片无关的填充（**主观类别，上游自己也不建议自动跳**）。 */
    const val CAT_FILLER = "filler"

    /** 音乐:非音乐部分（MV/翻唱里的谈话段）。 */
    const val CAT_MUSIC_OFFTOPIC = "music_offtopic"

    /** 精彩时刻/重点（**跳转点，不是区间，永不自动跳**）。 */
    const val CAT_POI_HIGHLIGHT = "poi_highlight"

    /** 抢先看/会员提前。 */
    const val CAT_EXCLUSIVE_ACCESS = "exclusive_access"

    /** 章节导航标记（不是广告）。 */
    const val CAT_CHAPTER = "chapter"

    /** 唯一会执行的动作类型：跳过。`mute` / `full` / `poi` / `chapter` 都不在 v1 范围。 */
    const val ACTION_SKIP = "skip"

    /**
     * 「整段视频都属于这个类别」的标签（区间固定 `[0,0]`）。
     *
     * 它不是一段可跳的区间，而是观众对**整片**的定性（比如整期都是恰饭）。跳过它毫无意义，
     * 只能在起播时提示一句「本视频被标记为整片广告」。
     */
    const val ACTION_FULL = "full"

    /**
     * 「高能/精彩片段」跳转点（`actionType == poi`）。
     *
     * 它是**点**不是区间：上游给的 `from`/`to` 通常相等，含义是"从这里往后值得看"，跳到那一刻
     * 就行。所以它既不进 [buildSegments]（那里要求 `to > from`，进去了也会被滤掉），
     * 也不进 [fullVideoCategories]，而是单独被 [highlights] 提炼成跳转点。
     */
    const val ACTION_POI = "poi"

    /** 片段在 [ViewPointSkip.Segment.type] 里的取值，与片头(1)/片尾(2)区分开，只用于去重 key 与诊断。 */
    const val TYPE_SPONSOR = 3

    /** 标注里的 `videoDuration` 与当前总时长允许的偏差（秒）。超过就当成"换源前的旧标注"。 */
    const val DURATION_TOLERANCE_SEC = 2.0

    /** 全部有意义的类别（设置页/文档按这个顺序列举，`poi_highlight` 在最后仅作说明）。 */
    val ALL_CATEGORIES: List<String> = listOf(
        CAT_SPONSOR, CAT_SELFPROMO, CAT_INTERACTION, CAT_INTRO, CAT_OUTRO, CAT_PREVIEW,
        CAT_FILLER, CAT_MUSIC_OFFTOPIC, CAT_EXCLUSIVE_ACCESS, CAT_CHAPTER, CAT_POI_HIGHLIGHT,
    )

    /**
     * 档位 → 该档要自动跳过的类别集合。
     *
     * 注意任何档位都**不含** [CAT_POI_HIGHLIGHT]：它是"高能/精彩片段"的跳转点，
     * 区间是空的，既不是广告也没有可跳的终点。
     */
    fun categoriesFor(level: Int): Set<String> = when (level) {
        LEVEL_AD -> setOf(CAT_SPONSOR)
        LEVEL_PROMO -> setOf(CAT_SPONSOR, CAT_SELFPROMO)
        LEVEL_RECOMMENDED -> setOf(CAT_SPONSOR, CAT_SELFPROMO, CAT_INTERACTION, CAT_INTRO, CAT_OUTRO)
        LEVEL_ALL -> setOf(
            CAT_SPONSOR, CAT_SELFPROMO, CAT_INTERACTION, CAT_INTRO, CAT_OUTRO, CAT_PREVIEW,
            CAT_FILLER, CAT_MUSIC_OFFTOPIC, CAT_EXCLUSIVE_ACCESS, CAT_CHAPTER,
        )
        // 自定义档没有预设——真正的集合来自用户勾选，必须走 resolveCategories
        else -> emptySet()
    }

    /**
     * 档位 + 用户勾选 → 最终要跳的类别集合。
     *
     * [LEVEL_CUSTOM] 用勾选结果（并做一次 [sanitizeCategories] 清洗），其余档位忽略勾选、
     * 只看档位预设——这样用户从「自定义」切回固定档时不会被旧的勾选残留影响。
     */
    fun resolveCategories(level: Int, custom: Set<String>): Set<String> =
        if (level == LEVEL_CUSTOM) sanitizeCategories(custom) else categoriesFor(level)

    /**
     * 清洗用户勾选：只认 [ALL_CATEGORIES] 里存在的 id，且**永远剔掉** `poi_highlight`
     * ——就算存档被手改成包含它（它是跳转点，区间为空，跳过去没有意义）。
     */
    fun sanitizeCategories(requested: Set<String>): Set<String> =
        requested.filterTo(HashSet()) { it in ALL_CATEGORIES && it != CAT_POI_HIGHLIGHT }

    /** 该档位是否真的会跳过东西（`LEVEL_OFF` 以外都算开着）。 */
    fun isEnabled(level: Int): Boolean = level != LEVEL_OFF && level in LEVEL_OFF..LEVEL_MAX

    // ---- 逐类别三档（26.11.14 起，取代上面那套六档） ----
    //
    // 换掉六档的来由（用户 26.11.14 原话：「对于除了番剧以外的其他类别的跳转功能都分三档，
    // 关闭，询问我，自动跳转」）：六档把一个决定塞进了两件事——「跳不跳」和「跳哪几类」，
    // 于是「广告自动跳、片头先问一句」这种最常见的组合根本表达不出来；而「自定义」档与
    // 逐类别勾选是两套存档，档位一改、勾选就形同虚设，用户看到的就是「自定义好像失效了」。
    //
    // 现在每个类别各自三档，存 `player_sponsor_block_skip_mode_<类别>`（值见下面三个常量）。
    // 老的六档存档由 `SponsorSkipPrefs` 一次性折算过来（[migrateLevel]），六档常量只为
    // 迁移与「详情页广告胶囊」的类别预设保留，不再出现在设置界面上。

    /** 这一类的片段完全不管（连请求结论都不用）。 */
    const val MODE_OFF = 0

    /** 这一类的片段到了就跑出来问一句，用户点「跳过」才跳。 */
    const val MODE_ASK = 1

    /** 这一类的片段到了直接跳走。 */
    const val MODE_AUTO = 2

    /** 三档个数（设置页选项数）。 */
    const val MODE_COUNT = 3

    /**
     * 能设三档的类别：全部类别去掉 `poi_highlight`。
     *
     * 与 `SponsorCategoryLabels.selectable` 同一口径——设置页一行一个类别，
     * `poi_highlight` 是跳转点（区间为空），给它是「关闭/询问/自动」都没有意义。
     */
    val MODE_CATEGORIES: List<String> = ALL_CATEGORIES.filter { it != CAT_POI_HIGHLIGHT }

    /**
     * 存档原文 → 三档。空、非数字、越界（老版本的六档数字、手改存档）一律回落 [MODE_OFF]，
     * 绝不把非法值 coerce 成「自动跳转」——那会让一个写坏的存档变成满地乱跳。
     */
    fun fromStoredMode(raw: String?): Int {
        val mode = raw?.trim()?.toIntOrNull() ?: return MODE_OFF
        return if (mode in MODE_OFF..MODE_AUTO) mode else MODE_OFF
    }

    /** 某类别的三档；[modes] 里没有这个键就是 [MODE_OFF]。 */
    fun modeOf(modes: Map<String, Int>, category: String): Int =
        (modes[category] ?: MODE_OFF).coerceIn(MODE_OFF, MODE_AUTO)

    /** 取某一档的全部类别（按 [MODE_CATEGORIES] 的顺序，供片段表过滤用）。 */
    fun categoriesWithMode(modes: Map<String, Int>, mode: Int): Set<String> =
        MODE_CATEGORIES.filterTo(LinkedHashSet()) { modeOf(modes, it) == mode }

    /** 有没有任何一个类别不是「关闭」——播放页据此决定要不要发请求。 */
    fun isAnyModeEnabled(modes: Map<String, Int>): Boolean =
        MODE_CATEGORIES.any { modeOf(modes, it) != MODE_OFF }

    /**
     * 老六档 → 逐类别三档（一次性迁移用）。
     *
     * 口径：关闭档 → 全关；「自定义」→ 当时勾上的算自动、其余关闭；其余固定档 → 该档预设的
     * 类别算自动、其余关闭。**老用户不会因为这次改版少跳一类，也不会多跳一类。**
     */
    fun migrateLevel(level: Int, custom: Set<String>): Map<String, Int> {
        val auto = if (level == LEVEL_CUSTOM) sanitizeCategories(custom) else categoriesFor(level)
        return MODE_CATEGORIES.associateWithTo(LinkedHashMap()) {
            if (it in auto) MODE_AUTO else MODE_OFF
        }
    }

    /**
     * 存档原文 → 档位。空、非数字、超范围的（老版本/手改存档）一律回落 [LEVEL_OFF]，
     * 绝不让一个非法值被 `coerce` 成"全部类别"。
     */
    fun fromStored(raw: String?): Int {
        val level = raw?.trim()?.toIntOrNull() ?: return LEVEL_OFF
        return if (level in LEVEL_OFF..LEVEL_MAX) level else LEVEL_OFF
    }

    /**
     * 原始片段 → 可交给 `SkipDecider` 的片段表（已过滤、已排序）。
     *
     * @param raw [SponsorBlockApi.fetchSegments] 的结果
     * @param allowed 允许跳的类别集合，调用方用 [resolveCategories] 从「档位 + 勾选」算出来
     * @param cid 当前分 P 的 cid；原始片段 cid 为 0（上游没给）时**放行**——总比整条丢掉好
     * @param currentDurationSec 当前视频总时长（秒），<=0 表示还没拿到，此时跳过时长校验
     * @param ignoredUuids 用户在播放页按过「不准」的标注 id（本地忽略集，见 `SponsorSkipPrefs`）。
     *   **按 UUID 而不是按区间记**：同一段广告被重新提交后区间可能变、`votes` 会变，只有 UUID 是
     *   那一条标注的身份；上游没给 UUID 的老数据只能放行（宁可跳错也不把整条表清空）。
     */
    fun buildSegments(
        raw: List<SponsorBlockApi.RawSegment>,
        allowed: Set<String>,
        cid: Long,
        currentDurationSec: Double,
        ignoredUuids: Set<String> = emptySet(),
    ): List<ViewPointSkip.Segment> {
        if (allowed.isEmpty()) return emptyList()
        return raw.asSequence()
            .filter { it.actionType == ACTION_SKIP }
            .filter { it.category in allowed }
            .filter { it.uuid.isEmpty() || it.uuid !in ignoredUuids }
            .filter { it.cid <= 0L || cid <= 0L || it.cid == cid }
            .filter { it.toSec > it.fromSec }
            .filter { it.toSec - it.fromSec <= ViewPointSkip.MAX_SEGMENT_SECONDS }
            .filter { isSameVersion(it, currentDurationSec) }
            .map { ViewPointSkip.Segment(TYPE_SPONSOR, it.fromSec, it.toSec, it.category) }
            .sortedBy { it.fromSec }
            .toList()
    }

    /**
     * 固定档位的便捷重载（自定义档没有预设集合，用它会得到空表，请改调上面的重载）。
     */
    fun buildSegments(
        raw: List<SponsorBlockApi.RawSegment>,
        level: Int,
        cid: Long,
        currentDurationSec: Double,
        ignoredUuids: Set<String> = emptySet(),
    ): List<ViewPointSkip.Segment> =
        buildSegments(raw, categoriesFor(level), cid, currentDurationSec, ignoredUuids)

    /**
     * 当前视频的「整片标签」类别（`actionType == full`），按类别去重、按 [ALL_CATEGORIES] 顺序。
     *
     * 没有任何整片标签时返回空表。**不看档位**：这是个提示，不是跳过动作——档位关着也该提示
     * 「这期整期都是广告」，那正是用户最想知道的事。
     *
     * cid 与版本校验与 [buildSegments] 同一口径：别的分 P、换源前的标签一样要丢掉。
     */
    fun fullVideoCategories(
        raw: List<SponsorBlockApi.RawSegment>,
        cid: Long,
        currentDurationSec: Double,
    ): List<String> {
        val hit = raw.asSequence()
            .filter { it.actionType == ACTION_FULL }
            .filter { it.cid <= 0L || cid <= 0L || it.cid == cid }
            .filter { isSameVersion(it, currentDurationSec) }
            .map { it.category }
            .toHashSet()
        if (hit.isEmpty()) return emptyList()
        return ALL_CATEGORIES.filter { it in hit }
    }

    /**
     * 一个跳转点（精彩时刻）。
     *
     * @param posSec 跳过去的那一秒
     * @param category 上游类别（目前只有 `poi_highlight`，留着是为了以后别的"点"型类别能复用）
     */
    data class Highlight(val posSec: Double, val category: String)

    /**
     * 原始标注 → 本 P 的跳转点（`actionType == poi`），按时间排序、按「起点 + 类别」去重。
     *
     * **不看档位**：这是一个导航入口而不是跳过动作，关掉自动跳过的人照样能点着看。
     * cid 与版本校验与 [buildSegments] 同一口径（别的分 P、换源前的旧标注都要丢）。
     *
     * 起点取 `fromSec`，只有在它非正时才退回 `toSec`——上游个别标注把点写在 `to` 上。
     * 越过总末尾超过容差的也丢：那是换源前的残留，点过去会直接停住。
     */
    fun highlights(
        raw: List<SponsorBlockApi.RawSegment>,
        cid: Long,
        currentDurationSec: Double,
    ): List<Highlight> {
        val out = LinkedHashMap<String, Highlight>()
        for (item in raw.asSequence()
            .filter { it.actionType == ACTION_POI }
            .filter { it.cid <= 0L || cid <= 0L || it.cid == cid }
            .filter { isSameVersion(it, currentDurationSec) }
            .sortedBy { it.fromSec }
        ) {
            val pos = if (item.fromSec > 0.0) item.fromSec else item.toSec
            if (pos < 0.0) continue
            // 总时长未知（<=0）时不做越界判断——此时任何一个点都没法验证，宁可全留
            if (currentDurationSec > 0.0 && pos > currentDurationSec + DURATION_TOLERANCE_SEC) continue
            out.putIfAbsent(pos.toString() + "|" + item.category, Highlight(pos, item.category))
        }
        return out.values.toList()
    }

    /**
     * 这批片段一共会省掉多少秒（四舍五入到整秒），0 表示"没有可跳的东西"。
     *
     * 播放页进度条旁边那个括号（`01:43 (1:34)`）就是拿它算出来的：1:43 是原片长，
     * 括号里是扣掉广告之后的时长。片段表本身就是**已经过档位与类别过滤**的结果，
     * 所以用户改档位、点「不准」之后这个数会跟着变——这正是想要的：括号说的是
     * 「按你现在的设置，这片的有效时长是多少」。
     */
    fun savedSeconds(segments: List<ViewPointSkip.Segment>): Int {
        val sum = segments.sumOf { (it.toSec - it.fromSec).coerceAtLeast(0.0) }
        return kotlin.math.round(sum).toInt()
    }

    /** 详情页胶囊的三种结论：没广告 / 整片都是 / 只有部分片段。 */
    const val BADGE_NONE = 0
    const val BADGE_FULL = 1
    const val BADGE_PARTIAL = 2

    /**
     * 详情页（还没开始播）用的一句话结论：这条视频和「广告」有什么关系。
     *
     * 只看恰饭口径的两类（[CAT_SPONSOR] / [CAT_SELFPROMO]），片头、预告、三连提示这些都不算
     * 广告——详情页上那句话是给"要不要点播放、进去会不会被喂广告"做判断用的，标成
     * 「含广告」却只是有个片头，会把这个标记本身变成噪音。
     *
     * **不看档位**：与 [fullVideoCategories] 同理，这是提示而非跳过动作。
     *
     * @param currentDurationSec 当前总时长，详情页拿不到准确秒数时传 0（跳过版本校验）
     */
    fun adBadge(
        raw: List<SponsorBlockApi.RawSegment>,
        cid: Long,
        currentDurationSec: Double,
    ): Int {
        val full = fullVideoCategories(raw, cid, currentDurationSec)
        if (full.any { it == CAT_SPONSOR || it == CAT_SELFPROMO }) return BADGE_FULL
        val adSegments = buildSegments(raw, categoriesFor(LEVEL_PROMO), cid, currentDurationSec)
        return if (adSegments.isEmpty()) BADGE_NONE else BADGE_PARTIAL
    }

    /**
     * 这条标注是否属于"当前这个版本的视频"。
     *
     * 两条判据：
     * - 标注自带的 `videoDuration` 与当前总时长相差不超过 [DURATION_TOLERANCE_SEC]；
     * - 片段起点没有越过视频末尾（超过说明秒数整体错位）。
     *
     * @param currentDurationSec 当前总时长，<=0（未知）时不做任何校验
     */
    fun isSameVersion(raw: SponsorBlockApi.RawSegment, currentDurationSec: Double): Boolean {
        if (currentDurationSec <= 0.0) return true
        if (raw.videoDuration > 0.0 &&
            kotlin.math.abs(raw.videoDuration - currentDurationSec) > DURATION_TOLERANCE_SEC
        ) return false
        return raw.fromSec <= currentDurationSec + DURATION_TOLERANCE_SEC
    }
}
