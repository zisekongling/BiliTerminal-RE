package com.RobinNotBad.BiliClient.player

import kotlin.random.Random

/**
 * 连播队列的**纯逻辑**实现（不依赖 Android，可在 JVM 单测里直接跑）。
 *
 * 设计要点：
 * - 队列只描述「当前这一层播放列表（可能是多 P、合集、收藏夹虚拟合集、UP 主投稿、
 *   本地缓存列表）的播放顺序」，不负责取流、不负责 UI。播放器把 `pagenames/cids`
 *   映射成 [PlayQueueItem] 后交给它，切页仍旧走
 *   [com.RobinNotBad.BiliClient.activity.player.PlayerActivity.switchToPage]。
 * - [currentIndex] 是「原始列表下标」，不是播放顺序下标；切回某个下标等价于切到那一 P。
 * - 随机顺序由 [seed] 决定，同一 seed 顺序稳定，因此队列 token 落盘/恢复后随机序不会变；
 *   进入随机模式时把随机序旋转到「当前项打头」，保证从任何一 P 开始都能顺着听完剩下的。
 * - 方向语义与 B 站客户端一致：顺序/倒序/随机/单曲循环。单曲循环下自动连播仍停在当前项
 *   （[advance] 传 `auto = true` 时返回 `replayCurrent = true`），手动切歌则往后走一项。
 */
enum class PlayQueueSource(val storageValue: String) {
    SINGLE("single"),
    UPLOADER("uploader"),
    COLLECTION("collection"),
    FAVORITE("favorite"),
    LOCAL_CACHE("local_cache");

    companion object {
        fun fromStorage(value: String?): PlayQueueSource =
            entries.firstOrNull { it.storageValue == value } ?: SINGLE
    }
}

enum class PlayQueueMode(val storageValue: String) {
    SEQUENTIAL("sequential"),
    REVERSE("reverse"),
    SHUFFLE("shuffle"),
    REPEAT_ONE("repeat_one");

    /** 按钮点一下切到下一个模式，顺序固定便于用户预期。 */
    fun cycle(): PlayQueueMode = when (this) {
        SEQUENTIAL -> REVERSE
        REVERSE -> SHUFFLE
        SHUFFLE -> REPEAT_ONE
        REPEAT_ONE -> SEQUENTIAL
    }

    companion object {
        fun fromStorage(value: String?): PlayQueueMode =
            entries.firstOrNull { it.storageValue == value } ?: SEQUENTIAL
    }
}

/** 队列里的一项：aid/cid 允许为 0（虚拟合集里 cid 常为 0，切页时再解析首个 cid）。 */
data class PlayQueueItem(val aid: Long, val cid: Long, val title: String)

/**
 * 一次切歌的决策结果。
 *
 * [index] 是目标项在原始列表中的下标；[replayCurrent] 表示「单曲循环」下应该重播当前项
 * 而不是切页——调用方据此决定 seek(0)+start 还是 switchToPage。
 */
data class PlayQueueStep(val index: Int, val replayCurrent: Boolean)

class PlayQueue private constructor(
    items: List<PlayQueueItem>,
    currentIndex: Int,
    mode: PlayQueueMode,
    val seed: Long,
    source: PlayQueueSource,
    history: List<Int>
) {
    var items: List<PlayQueueItem> = items.toList()
        private set
    var currentIndex: Int = currentIndex.coerceIn(0, (this.items.size - 1).coerceAtLeast(0))
        private set
    var mode: PlayQueueMode = mode
        private set
    val source: PlayQueueSource = source
    private val history: MutableList<Int> = history.toMutableList()
    private var shuffleOrder: List<Int> = buildShuffleOrder(this.items.size, seed, this.currentIndex)
    private var shuffleCursor: Int = 0

    val size: Int get() = items.size
    fun isEmpty(): Boolean = items.isEmpty()
    fun current(): PlayQueueItem? = items.getOrNull(currentIndex)
    fun itemAt(index: Int): PlayQueueItem? = items.getOrNull(index)

    /** 当前顺序下「往后」的下标（不改变队列）。自动连播传 auto=true。 */
    fun nextIndex(auto: Boolean): Int {
        if (items.isEmpty()) return -1
        return when (mode) {
            PlayQueueMode.REPEAT_ONE -> if (auto) currentIndex else stepForward()
            PlayQueueMode.SEQUENTIAL -> if (currentIndex + 1 < items.size) currentIndex + 1 else -1
            PlayQueueMode.REVERSE -> if (currentIndex - 1 >= 0) currentIndex - 1 else -1
            PlayQueueMode.SHUFFLE ->
                if (shuffleCursor + 1 < shuffleOrder.size) shuffleOrder[shuffleCursor + 1] else -1
        }
    }

    /** 当前顺序下「往前」的下标（不改变队列）。优先走历史栈，保证随机模式下能原路返回。 */
    fun previousIndex(): Int {
        if (items.isEmpty()) return -1
        if (history.isNotEmpty()) return history[history.size - 1]
        return when (mode) {
            PlayQueueMode.REVERSE -> if (currentIndex + 1 < items.size) currentIndex + 1 else -1
            PlayQueueMode.SHUFFLE -> if (shuffleCursor > 0) shuffleOrder[shuffleCursor - 1] else -1
            PlayQueueMode.SEQUENTIAL, PlayQueueMode.REPEAT_ONE ->
                if (currentIndex - 1 >= 0) currentIndex - 1 else -1
        }
    }

    fun canNext(auto: Boolean): Boolean = nextIndex(auto) >= 0
    fun canPrevious(): Boolean = previousIndex() >= 0

    /** 自动连播：返回下一步；没有下一步返回 null。单曲循环返回 replayCurrent=true 的当前项。 */
    fun advance(auto: Boolean): PlayQueueStep? {
        if (items.isEmpty()) return null
        if (mode == PlayQueueMode.REPEAT_ONE && auto) return PlayQueueStep(currentIndex, true)
        val next = nextIndex(auto)
        if (next < 0) return null
        if (mode == PlayQueueMode.SHUFFLE && shuffleCursor + 1 < shuffleOrder.size) shuffleCursor++
        pushHistory(currentIndex)
        currentIndex = next
        return PlayQueueStep(next, false)
    }

    /** 手动上一首：只有存在可返回项时才移动。 */
    fun retreat(): PlayQueueStep? {
        val prev = previousIndex()
        if (prev < 0 || prev == currentIndex) return null
        if (history.isNotEmpty()) history.removeAt(history.size - 1)
        if (mode == PlayQueueMode.SHUFFLE) {
            val pos = shuffleOrder.indexOf(prev)
            if (pos >= 0) shuffleCursor = pos
        }
        pushHistory(currentIndex)
        currentIndex = prev
        return PlayQueueStep(prev, false)
    }

    fun setMode(newMode: PlayQueueMode) {
        if (newMode == mode) return
        mode = newMode
        if (newMode == PlayQueueMode.SHUFFLE) realignShuffle()
    }

    fun cycleMode(): PlayQueueMode {
        setMode(mode.cycle())
        return mode
    }

    /** 用户在选集面板里直接点某一 P 时调用；越界返回 null。 */
    fun select(index: Int): PlayQueueItem? {
        val item = items.getOrNull(index) ?: return null
        if (index == currentIndex) return item
        pushHistory(currentIndex)
        currentIndex = index
        if (mode == PlayQueueMode.SHUFFLE) {
            val pos = shuffleOrder.indexOf(index)
            if (pos >= 0) shuffleCursor = pos
        }
        return item
    }

    /** 换了一层播放列表（进合集/回主合集/切清晰度后重建）时整体重置。 */
    fun reset(newItems: List<PlayQueueItem>, newIndex: Int) {
        items = newItems.toList()
        currentIndex = newIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))
        shuffleOrder = buildShuffleOrder(items.size, seed, currentIndex)
        shuffleCursor = 0
        history.clear()
    }

    /** token 编解码用的稳定文本：方便落盘、进程重建后恢复。 */
    fun encode(): String {
        val sb = StringBuilder()
        sb.append(HEADER).append('\t')
            .append(source.storageValue).append('\t')
            .append(mode.storageValue).append('\t')
            .append(seed).append('\t')
            .append(currentIndex).append('\t')
            .append(history.joinToString(","))
        for (item in items) {
            sb.append('\n')
                .append(item.aid).append('\t')
                .append(item.cid).append('\t')
                .append(escape(item.title))
        }
        return sb.toString()
    }

    companion object {
        const val HEADER = "PLAYQUEUE1"
        private const val HISTORY_LIMIT = 64
        /** 单条 token 上限，防御异常输入（正常 1000 项 × 60 字节 ≈ 60KB）。 */
        const val MAX_TOKEN_CHARS = 256 * 1024

        fun create(
            items: List<PlayQueueItem>,
            currentIndex: Int = 0,
            mode: PlayQueueMode = PlayQueueMode.SEQUENTIAL,
            source: PlayQueueSource = PlayQueueSource.SINGLE,
            seed: Long = Random.nextLong()
        ): PlayQueue = PlayQueue(items, currentIndex, mode, seed, source, emptyList())

        /** 进程重建/进播放器时从 token 恢复；任何不合法都返回 null（宁可不恢复也不崩）。 */
        fun decode(token: String?): PlayQueue? {
            if (token.isNullOrBlank() || token.length > MAX_TOKEN_CHARS) return null
            val lines = token.split('\n')
            if (lines.isEmpty()) return null
            val head = lines[0].split('\t')
            if (head.size < 6 || head[0] != HEADER) return null
            val source = PlayQueueSource.fromStorage(head[1])
            val mode = PlayQueueMode.fromStorage(head[2])
            val seed = head[3].toLongOrNull() ?: return null
            val index = head[4].toIntOrNull() ?: return null
            val history = if (head[5].isBlank()) emptyList() else head[5].split(',')
                .mapNotNull { it.toIntOrNull() }
            val items = ArrayList<PlayQueueItem>(lines.size - 1)
            for (i in 1 until lines.size) {
                val parts = lines[i].split('\t')
                if (parts.size < 3) continue
                val aid = parts[0].toLongOrNull() ?: continue
                val cid = parts[1].toLongOrNull() ?: 0L
                items.add(PlayQueueItem(aid, cid, unescape(parts[2])))
            }
            if (items.isEmpty()) return null
            val historyValid = history.filter { it in items.indices }
            return PlayQueue(items, index, mode, seed, source, historyValid)
        }

        /**
         * 生成「以 [startIndex] 打头」的随机序：先按 seed 洗牌，再旋转到 startIndex。
         * 这样从第 5 集点开随机播放，也能听完第 5 集之后的全部（而不是只剩后半段）。
         */
        private fun buildShuffleOrder(size: Int, seed: Long, startIndex: Int): List<Int> {
            if (size <= 1) return List(size) { it }
            val order = MutableList(size) { it }
            val random = Random(seed)
            for (i in size - 1 downTo 1) {
                val j = random.nextInt(i + 1)
                val tmp = order[i]
                order[i] = order[j]
                order[j] = tmp
            }
            val anchor = order.indexOf(startIndex)
            if (anchor <= 0) return order
            return order.subList(anchor, size) + order.subList(0, anchor)
        }

        private fun escape(text: String?): String {
            if (text.isNullOrEmpty()) return ""
            val sb = StringBuilder(text.length)
            for (ch in text) {
                when (ch) {
                    '\\' -> sb.append("\\\\")
                    '\t' -> sb.append("\\t")
                    '\n' -> sb.append("\\n")
                    '\r' -> sb.append("\\r")
                    else -> sb.append(ch)
                }
            }
            return sb.toString()
        }

        private fun unescape(text: String): String {
            if (text.isEmpty()) return ""
            val sb = StringBuilder(text.length)
            var i = 0
            while (i < text.length) {
                val ch = text[i]
                if (ch == '\\' && i + 1 < text.length) {
                    when (text[i + 1]) {
                        '\\' -> sb.append('\\')
                        't' -> sb.append('\t')
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        else -> sb.append(text[i + 1])
                    }
                    i += 2
                } else {
                    sb.append(ch); i++
                }
            }
            return sb.toString()
        }
    }

    private fun realignShuffle() {
        shuffleOrder = buildShuffleOrder(items.size, seed, currentIndex)
        shuffleCursor = 0
    }

    private fun pushHistory(index: Int) {
        history.add(index)
        if (history.size > HISTORY_LIMIT) history.removeAt(0)
    }

    private fun stepForward(): Int = if (currentIndex + 1 < items.size) currentIndex + 1 else -1
}
