package com.RobinNotBad.BiliClient.player

import com.RobinNotBad.BiliClient.util.SettingsKeys
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil

/**
 * 弹幕屏蔽词（G01，26.11.13）。
 *
 * 目标：用户填一小串词，命中的弹幕**不显示**——不区分发送者、不区分弹幕类型，
 * 只做最朴素的「文本包含」判断。手表上打不了长文，所以输入格式刻意做成一段自由文本，
 * 由逗号 / 顿号 / 分号 / 空白任意分隔（与兄弟分支 cyq 的 `filterBlockedDanmaku` 同一套口径）。
 *
 * ## 为什么是纯对象 + 不可变快照
 *
 * 判定发生在两个线程上：
 * - DFM 的**渲染线程**（`prepared()` → 解析结果过滤、`appendSegmentInternal` 逐条追加）；
 * - 播放页主线程（`addDanmaku` 之外的加载入口）。
 *
 * 那两个位置都绝不能读 SharedPreferences（IO + 锁），所以口径与「本人弹幕边框」一致：
 * 在 `DanmakuManager.configureAndPrepare` 里用 [load] 拍一次快照，之后只读这个不可变对象。
 * 效果就是「改完下次加载弹幕生效」——换视频 / 切 P / 重进播放页都会重新 prepare。
 *
 * ## 解析口径（都是为了让用户「随便填也能用」）
 *
 * - 分隔符：`, ， 、 ; ；` 与任意空白，连续分隔符不产生空词；
 * - 大小写：统一转小写后比较（中文无影响，英文标签如 `SC`/`sc` 都能命中）；
 * - 去重保序：同一词填两遍只留一个，顺序按首次出现，便于设置页原样回显；
 * - 单个词最长 [MAX_WORD_LENGTH]（超出截断），总词数上限 [MAX_WORDS]（超出丢弃尾部）——
 *   防止有人粘贴一整篇文章进设置项，把每条弹幕的比较成本拉到不可控；
 * - 词里本身含分隔符时无法表达（与兄弟分支一致），不额外做转义语法。
 */
object DanmakuBlockWordPolicy {

    /** 设置键：一段自由文本。 */
    const val KEY: String = SettingsKeys.PLAYER_DANMAKU_BLOCK_WORDS

    /** 单个屏蔽词的最大长度（字符）。 */
    const val MAX_WORD_LENGTH = 32

    /** 最多接受多少个词。 */
    const val MAX_WORDS = 200

    /** 分隔符：中西文逗号、顿号、中西文分号、任意空白。 */
    private val SEPARATORS = Regex("[,，、;；\\s]+")

    /**
     * 一份解析好的屏蔽词快照。
     *
     * [words] 已转小写、去重、保序；[NONE] 表示不屏蔽任何弹幕（此时 [isBlocked] 恒为 false，
     * 调用方也据此省掉包装 parser 的开销）。
     */
    class Snapshot(val words: List<String>) {

        val isEmpty: Boolean get() = words.isEmpty()

        /**
         * 文本是否命中任一屏蔽词。
         *
         * [text] 为 null / 空串、快照为空时一律返回 false（不拦）。
         * 单次判定是 O(词数 × 文本长度) 的朴素包含比较：词数 ≤ 200、弹幕普遍 < 60 字，
         * 在手表上也只是一次短字符串扫描；不做 Aho-Corasick 之类的自动机，
         * 那点收益不值得多一份需要维护的构建代码。
         */
        fun isBlocked(text: CharSequence?): Boolean {
            if (words.isEmpty()) return false
            val raw = text?.toString() ?: return false
            if (raw.isEmpty()) return false
            val lower = raw.lowercase()
            for (word in words) {
                if (lower.contains(word)) return true
            }
            return false
        }

        companion object {
            /** 空快照（不屏蔽）。 */
            val NONE = Snapshot(emptyList())
        }
    }

    /**
     * 解析设置里的原始文本。
     *
     * @param raw 用户填的原文（可为 null）
     * @return 不可变快照；没有任何有效词时返回 [Snapshot.NONE]
     */
    fun parse(raw: String?): Snapshot {
        if (raw.isNullOrBlank()) return Snapshot.NONE
        val out = LinkedHashSet<String>()
        for (token in raw.split(SEPARATORS)) {
            var word = token.trim()
            if (word.isEmpty()) continue
            if (word.length > MAX_WORD_LENGTH) word = word.substring(0, MAX_WORD_LENGTH)
            out.add(word.lowercase())
            if (out.size >= MAX_WORDS) break
        }
        if (out.isEmpty()) return Snapshot.NONE
        return Snapshot(out.toList())
    }

    /**
     * 从 SharedPreferences 读一次并解析。
     *
     * **只能在调用方主线程调**（会做一次 IO），`DanmakuManager.configureAndPrepare` 是唯一入口。
     */
    fun load(): Snapshot =
        parse(SharedPreferencesUtil.getString(KEY, ""))

    /**
     * 把快照还原成一段可编辑文本（设置页回显用，分隔符统一用中文顿号）。
     */
    fun toEditableText(snapshot: Snapshot): String = snapshot.words.joinToString("、")
}
