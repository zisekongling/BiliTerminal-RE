package com.RobinNotBad.BiliClient.player

/**
 * 自动跳过片头/片尾的纯判定逻辑（无 Android 依赖，可直接 JVM 单测）。
 *
 * 判定与取样分离：本文件只回答「该不该跳」，真正的 `seekTo` 由播放页完成。
 *
 * ## 根因（反馈 #99 与 #34）
 *
 * 数据来源是 `x/player/wbi/v2` 返回的 `view_points`。**这里最容易踩的坑是 `type` 字段的含义**：
 *
 * - 对 **UGC 稿件**（普通投稿，如反馈 #99 里的 `BV1U74w6XEbB`），`view_points` 下发的其实是
 *   **分段章节**（up 主自己划的视频章节），`type` 填的是**分段类型**（实测是普通分段，`type == 2`），
 *   `content` 才是章节标题（「狂躁损伤」「泄漏源」「留声机」……）。把这些段当成片尾，
 *   就会播到第 7 秒就被甩到 7 秒、一路"一直跳过片尾"——这就是反馈 #99「开自动跳过片头片尾，
 *   看 BV1U74w6XEbB 一直跳过片尾」。
 * - 只有 **番剧 / 影视的 EP 数据**里，`type == [TYPE_OP]`（1）/ [TYPE_ED]（2）才带"片头/片尾"语义。
 *
 * 所以**不能只看 `type` 就跳**：`type` 是分段类型而不是片头片尾类型，UGC 稿件也会下发分段。
 *
 * **真正生效的第一道闸是「只有 PGC 才允许自动跳过」**，由 `PlayerActivity.isPgcPlayback()`
 * 判定后传给 [buildSegments] 的 `allowAutoSkip` 参数；本文件只执行调用方给的结论，不自己判断
 * 稿件是 UGC 还是 PGC。UGC 一律拿到空表，`type` 的歧义就无从生效了。
 *
 * `content` 文本的语义识别（[isOpEdLabel]）**不参与过滤**：`content` 是 UP 主/字幕组自由填写的，
 * 番剧里常见空串、「1-1」「正片」「简介」，拿它当闸会把真片头也挡掉（用户反而觉得"开了没用"）；
 * 而且 #99 里用户反馈的「预告」类章节本来就会命中词表，白名单挡不住它。相关能力仅留作诊断/未来参考。
 *
 * 反馈 #34（「跳过片头片尾会因为视频分段也跳过」）还提醒：片段表必须绑定在当前这一集/这一P 上，
 * 生命周期由 [com.RobinNotBad.BiliClient.activity.player.SkipDecider] 负责，本文件不管。
 */
object ViewPointSkip {

    /** `view_points` 里的片段类型：片头（仅番剧 EP 数据里有此语义，UGC 章节不代表片头）。 */
    const val TYPE_OP = 1

    /** `view_points` 里的片段类型：片尾（仅番剧 EP 数据里有此语义，UGC 章节不代表片尾）。 */
    const val TYPE_ED = 2

    /**
     * 单个片段允许自动跳过的最大时长（秒）。
     *
     * 片头/片尾正常都在 3 分钟以内，这里给到 10 分钟只是兜底：万一上游下发一个
     * 「0 秒 → 整集」之类的脏区间，自动跳过会直接把用户甩到片尾，必须挡住。
     */
    const val MAX_SEGMENT_SECONDS = 600.0

    /**
     * 片头/片尾语义词表（比较前先转小写）。**仅用于 [isOpEdLabel] 的语义诊断，不参与 [buildSegments] 过滤。**
     *
     * 中文词与长英文词按「包含」匹配（`content` 里出现即可，例如「片头曲」「片尾曲 feat. xx」）；
     * 纯英文短词 `op` / `ed` **不能**按包含匹配——它们只有两个字母，会误命中大量普通章节标题
     * （如「opera」「cr**ed**it」「spr**ed**」），所以见 [EXACT_ONLY_WORDS] 与 [isOpEdLabel]。
     */
    private val LABEL_WORDS = listOf(
        "片头", "片尾", "opening", "ending", "主题曲", "前情提要", "预告",
    )

    /**
     * 只允许「整段就是它」的短词：`op` / `ed` 作为子串太容易误伤，见 [isOpEdLabel]。
     */
    private val EXACT_ONLY_WORDS = listOf("op", "ed")

    /**
     * 一个可跳过的片段。
     *
     * @param type 片段类型，[TYPE_OP] 或 [TYPE_ED]（UGC 章节的 type 没有片头片尾语义）
     * @param fromSec 片段起点（秒，**含**）
     * @param toSec 片段终点（秒，**不含**）
     * @param content 章节标题原文（`view_points[].content`）。默认空串只为兼容旧的单测/调用方；
     *                **不参与自动跳过判定**（见类说明与 [isOpEdLabel]），仅作诊断/将来参考。
     */
    data class Segment(
        val type: Int,
        val fromSec: Double,
        val toSec: Double,
        val content: String = "",
    )

    /** 该类型是否**有可能**是片头/片尾（只有 [TYPE_OP] / [TYPE_ED] 有可能；普通章节看点 type 为 0，不动）。 */
    fun isSkippable(type: Int): Boolean = type == TYPE_OP || type == TYPE_ED

    /**
     * `content` 的文本像不像片头/片尾（**语义诊断/未来参考，不参与 [buildSegments] 过滤**）。
     *
     * 为什么不拿它当闸（详见类说明）：`content` 由 UP 主/字幕组自由填写，番剧里常见空串、
     * 「1-1」「正片」「简介」，白名单会把真片头也挡掉；而 #99 用户反馈的「预告」类章节本身就
     * 命中词表，白名单挡不住它。**请不要再把它接回 [buildSegments]**——限制生效范围请用
     * [buildSegments] 的 `allowAutoSkip` 参数（由 `PlayerActivity.isPgcPlayback()` 决定）。
     *
     * 判定顺序：
     * 1. 去掉所有空白转小写，得到 `s`（中文文案里常带空格/全角空格，如「片 尾」）；
     * 2. 去掉所有非字母数字字符，得到 `compact`（于是「OP」「O.P.」「op /」都归一成 `op`）；
     * 3. 按非字母数字切词得到 `words`，用于「独立单词」判定；
     * 4. 短词 `op` / `ed`（[EXACT_ONLY_WORDS]）：只有 `compact` **精确等于**它，
     *    或 `words` 里有**独立单词**恰好等于它（如「the op song」）时才算命中；
     *    「opera」「reed」这类只是"包含 op/ed 子串"的标题一律不算；
     * 5. 中文/长英文词（[LABEL_WORDS]）：去空白小写后的 `s` **或**去掉非字母数字的 `compact`
     *    包含任一即命中（「片头曲」「OP主题曲」「片 尾」「OP 主题曲」这类都算；`compact` 参与是为了
     *    容忍标题里的空格与标点）。
     *
     * 注意这是**白名单**：没命中就返回 false，绝不能反过来"排除掉普通章节标题"当片头片尾。
     */
    fun isOpEdLabel(content: String): Boolean {
        val s = content.trim().lowercase()
        if (s.isEmpty()) return false
        // 「op」「ed」单独成词或紧挨标点（O.P. / OP！）都归一成纯字母，便于精确比较
        val compact = s.filter { it.isLetterOrDigit() }
        if (compact.isEmpty()) return false
        if (EXACT_ONLY_WORDS.any { compact == it }) return true

        // 长词/中文词：包含即命中（「片头曲」「OP主题曲」「前情提要」这类）。
        // s 与 compact 都看：s 保留原分隔，compact 去掉空白/标点，于是「片 尾」「OP 主题曲」也能命中
        // （KDoc 第 1 步承诺过要容忍中文标题里的空格）。注意这只影响诊断，不参与 buildSegments 过滤。
        if (LABEL_WORDS.any { it in s } || LABEL_WORDS.any { it in compact }) return true

        // 第三道：op/ed 作为**独立单词**出现（「the op song」），仍要求整词相等，
        // 所以 opera / reed 这类"刚好包含子串"的标题不会被误伤。
        val words = s.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }
        return words.any { it in EXACT_ONLY_WORDS }
    }

    /** 片段的稳定标识，用于「本集已处理过」的去重。 */
    fun keyOf(segment: Segment): String =
        segment.type.toString() + "@" + segment.fromSec + "-" + segment.toSec

    /**
     * 从原始 `view_points` 里筛出可跳过的片段（旧口径，**不看 `content`**）。
     *
     * 只丢弃：非片头/片尾类型（[isSkippable]）、区间反向或为空（`toSec <= fromSec`）、
     * 时长超过 [MAX_SEGMENT_SECONDS] 的脏数据。结果按起点升序，方便 [segmentAt] 用「第一个命中」的语义。
     *
     * **为什么不在这里加 `content` 白名单**：`content` 由 UP 主/字幕组自由填写，番剧片段常见空串、
     * 「1-1」「正片」「简介」，白名单会把真片头片尾也挡掉（用户反而觉得"开了没用"）；而且 #99 里
     * 用户反馈的「预告」类章节本身就命中词表，挡不住它。**限制生效范围靠 [buildSegments] 的
     * `allowAutoSkip` 参数（PGC 判定），不要把这个闸加回来。**
     */
    fun buildSegments(raw: List<Segment>): List<Segment> =
        raw.filter {
            isSkippable(it.type) &&
                it.toSec > it.fromSec &&
                (it.toSec - it.fromSec) <= MAX_SEGMENT_SECONDS
        }.sortedBy { it.fromSec }

    /**
     * 带 PGC 闸的 [buildSegments]：`allowAutoSkip == false` 时直接返回空表。
     *
     * 真正的第一道闸是「只有 PGC 才允许自动跳过」，由 `PlayerActivity.isPgcPlayback()` 判定后传入：
     * UGC 的 `view_points` 是分段/章节，其 `type` 不代表片头片尾（反馈 #99 的 BV1U74w6XEbB 就是
     * 十段全 `type == 2` 的普通章节），普通投稿一律拿到空表；真 PGC 时 `allowAutoSkip == true`，
     * 直接按 [buildSegments] 旧口径取样，**不做 `content` 白名单**。
     */
    fun buildSegments(raw: List<Segment>, allowAutoSkip: Boolean): List<Segment> =
        if (!allowAutoSkip) emptyList() else buildSegments(raw)

    /**
     * [positionSec] 落在哪个片段里。
     *
     * 区间是**左闭右开** `[fromSec, toSec)`：跳过之后播放位置正好落在 `toSec`，
     * 若把右端也算作「片段内」，下一轮判定会再次命中，形成反复跳。
     *
     * @param segments 必须是 [buildSegments] 排好序的结果
     */
    fun segmentAt(positionSec: Double, segments: List<Segment>): Segment? {
        if (positionSec.isNaN() || positionSec < 0) return null
        return segments.firstOrNull { positionSec >= it.fromSec && positionSec < it.toSec }
    }

    /**
     * 当前是否应当自动跳过；返回需要跳过的片段，不需要则返回 null。
     *
     * @param handled 本次播放里已经处理过的片段 key（自动跳过的、以及用户手动拖进去的），只跳一次
     */
    fun shouldSkip(positionSec: Double, segments: List<Segment>, handled: Set<String>): Segment? {
        val segment = segmentAt(positionSec, segments) ?: return null
        return if (handled.contains(keyOf(segment))) null else segment
    }

    /**
     * 用户**手动**把进度拖到 [positionSec] 时，应当顺手标成「已处理」的片段 key。
     *
     * 用户主动拖进片头，说明他就是想看看片头，不能下一轮定时器又把他弹走；返回 null 表示无需处理。
     */
    fun keyForManualSeek(positionSec: Double, segments: List<Segment>, handled: Set<String>): String? {
        val segment = segmentAt(positionSec, segments) ?: return null
        val key = keyOf(segment)
        return if (handled.contains(key)) null else key
    }
}
