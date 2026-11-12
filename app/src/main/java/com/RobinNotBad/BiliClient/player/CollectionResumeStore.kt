package com.RobinNotBad.BiliClient.player

import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil

/**
 * 某个合集「上次看到哪一集」的本地记录（反馈 #105 / #108）。
 *
 * 为什么不复用 [PlaySessionStore]：那份只存**最后一次**播放会话、全局一份。用户完全可能
 * 上午看合集 A、下午看合集 B，回到 A 时仍然该定位到 A 上次那一集——按合集分别记才行。
 */
data class CollectionResumeRecord(
    val seasonId: Long,
    val aid: Long,
    /**
     * 写入时刻（毫秒）。目前没有过期策略，只用于排查（确认「这条是什么时候写下的」）；
     * 保留它是为了将来要做「太久没看就别定位」时不必改数据格式。
     */
    val updatedAt: Long
)

/**
 * 「进入合集详情页该滚到哪一集」的判定（**纯函数，零 Android 依赖，有 JVM 单测**）。
 *
 * 单独拎出来的原因：这是整条链路里唯一会静默出错的地方——定位偏一格在手表上极难发现
 * （合集动辄上百集，谁也不会去数第 87 条画的是哪一集），而它的输入全是值，
 * 没必要连着 SharedPreferences 和 Adapter 一起才能在单测里验证。
 */
object CollectionResumePolicy {

    /**
     * @param fromAid 进入详情页时点进来的那个视频（`CollectionInfoActivity` 的 `fromVideo`），
     *                也是任何一项校验不通过时的回落值——保持「没有记录就按老行为定位」。
     * @param seasonId 当前合集的 `ugc_season.id`；<=0 表示拿不到可用标识，此时任何记录都不可信。
     * @param record 本地记录，可能为 null（从没在这个合集里起播过）。
     * @param memberAids 本合集里**列表上真能看到**的全部 aid。
     * @return 该定位到的 aid。
     */
    fun resolveTargetAid(
        fromAid: Long,
        seasonId: Long,
        record: CollectionResumeRecord?,
        memberAids: List<Long>
    ): Long {
        // 没有可用 seasonId 时 key 本身就没有意义（会写成 collection_resume_v1_-1），
        // 这种记录既可能来自脏数据也可能来自别的合集，一律不认。
        if (seasonId <= 0 || record == null) return fromAid
        // 值里自带 seasonId 就是为了这一步：记录被复制、key 方案改过、或从备份恢复时，
        // 值本身能证明它属于哪个合集，不必盲信「我读的是这个 key」。
        if (record.seasonId != seasonId) return fromAid
        if (record.aid <= 0) return fromAid
        // UP 可以删集、重排、甚至把视频移出合集。只凭记录就滚过去会定位到不存在的下标
        // （RecyclerView 会直接忽略）或别的集上，所以还要求这集仍然在本合集里。
        if (!memberAids.contains(record.aid)) return fromAid
        return record.aid
    }

    /**
     * `ugc_season` 拿不到 id（`seasonId <= 0`）时的降级定位。
     *
     * <p>这类合集没有可用的 key，只能退到全局的「最近一次在无 id 合集里看到哪一集」。
     * 降级槽不区分合集，所以**必须**再用「这一集还在当前列表里」兜一次：上一次可能是在
     * 另一个无 id 合集里看的，直接滚过去会定位到别的合集/不存在的下标。
     */
    fun resolveFallbackAid(fromAid: Long, lastPlayedAid: Long, memberAids: List<Long>): Long {
        if (lastPlayedAid <= 0L) return fromAid
        if (!memberAids.contains(lastPlayedAid)) return fromAid
        return lastPlayedAid
    }
}

/**
 * 记录的编解码（纯函数，便于 JVM 单测）。
 *
 * 格式：`seasonId\taid\tupdatedAt`。刻意不用 JSON / 序列化框架——这个仓库没有
 * kotlinx-serialization，为三个数字引一套编码不值得；制表符也不可能出现在数字里。
 */
object CollectionResumeCodec {

    fun encode(record: CollectionResumeRecord): String =
        "${record.seasonId}\t${record.aid}\t${record.updatedAt}"

    /**
     * @return 解析成功返回记录，任何一段不可信都返回 null（等于「没有记录」，定位回落到入口视频）。
     *
     * 这里之所以逐段校验而不是 try/catch：偏好文件可能被用户从备份恢复、被其它版本写过，
     * 坏数据不该让页面崩，但也不该被当成有效记录滚到乱七八糟的位置。
     */
    fun decode(text: String?): CollectionResumeRecord? {
        if (text.isNullOrBlank()) return null
        val parts = text.split('\t')
        if (parts.size < 3) return null
        val seasonId = parts[0].trim().toLongOrNull() ?: return null
        val aid = parts[1].trim().toLongOrNull() ?: return null
        if (seasonId <= 0 || aid <= 0) return null
        // updatedAt 只是排查信息，坏了不影响定位；给 0 而不是整条丢掉
        val updatedAt = parts[2].trim().toLongOrNull() ?: 0L
        return CollectionResumeRecord(seasonId = seasonId, aid = aid, updatedAt = updatedAt)
    }
}

/**
 * 「每个合集上次看到哪一集」的落盘层。
 *
 * 一个合集一个 key（`collection_resume_v1_<seasonId>`）而不是把全部合集塞进一个 JSON：
 * 进详情页只需要读自己那一条，合集数量涨上去也不会让每次进入都反序列化一大坨。
 */
object CollectionResumeStore {
    private const val KEY_PREFIX = "collection_resume_v1_"

    /**
     * 拿不到 `ugc_season.id` 的合集的降级槽：全局只记「最近一次在无 id 合集里看到哪一集」。
     *
     * <p>为什么还要这一格：`id` 缺失的合集恰恰是最容易漏的一类（接口偶发不给 id），
     * 按 seasonId 的键写不了，只能退到一条全局记录；读取侧见
     * [CollectionResumePolicy.resolveFallbackAid]。
     */
    private const val KEY_LAST_PLAYED = "collection_resume_last_played_v1"

    fun key(seasonId: Long): String = KEY_PREFIX + seasonId

    /**
     * 记下「这个合集停在 aid 这一集」。
     *
     * - `aid <= 0`：垃圾数据，不写；
     * - `seasonId <= 0`（合集拿不到 id）：写全局降级槽 [saveLastPlayed]；
     * - 其余：按合集分开存。
     *
     * @param updatedAt 供单测注入固定值，正式路径用当前时刻。
     */
    fun save(seasonId: Long, aid: Long, updatedAt: Long = System.currentTimeMillis()) {
        if (aid <= 0) return
        if (seasonId <= 0) {
            saveLastPlayed(aid, updatedAt)
            return
        }
        SharedPreferencesUtil.putString(
            key(seasonId),
            CollectionResumeCodec.encode(CollectionResumeRecord(seasonId, aid, updatedAt))
        )
    }

    /** 写「最近一次在无 id 合集里看到 aid 这一集」的降级槽；aid 非法时不写。 */
    fun saveLastPlayed(aid: Long, updatedAt: Long = System.currentTimeMillis()) {
        if (aid <= 0) return
        SharedPreferencesUtil.putString(KEY_LAST_PLAYED, "$aid\t$updatedAt")
    }

    /** 读降级槽；没写过 / 坏数据 / aid 非正时返回 0（调用方按「没有记录」处理）。 */
    fun lastPlayed(): Long {
        val text = SharedPreferencesUtil.getString(KEY_LAST_PLAYED, null)
        if (text.isNullOrBlank()) return 0L
        return text.substringBefore('\t').trim().toLongOrNull()?.takeIf { it > 0 } ?: 0L
    }

    /** 读不到 / 读出来是坏数据时返回 null，调用方按「没有记录」处理。 */
    fun read(seasonId: Long): CollectionResumeRecord? {
        if (seasonId <= 0) return null
        return CollectionResumeCodec.decode(SharedPreferencesUtil.getString(key(seasonId), null))
    }
}
