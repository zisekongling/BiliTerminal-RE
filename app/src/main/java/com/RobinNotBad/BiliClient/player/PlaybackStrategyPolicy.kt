package com.RobinNotBad.BiliClient.player

/**
 * 取流策略：什么时候用「音视频合一的 MP4（durl）」，什么时候必须用 DASH。
 *
 * <p>为什么普通档位要尽量走合一 MP4：DASH 是音视频两条独立流、两个播放器实例（或一个播放器的两条
 * 独立轨道），长视频、拖动、倍速时两个时钟容易漂移，表现就是「音画不同步」；合一 MP4 只有一条流、
 * 一个时钟，天然不会漂。兄弟仓库 BiliTerminal-Revival 早就改成这个口径。
 *
 * <p>为什么 1080P(80) 及以上不能走合一 MP4：本仓库用真实账号实测过，legacy {@code x/player/playurl}
 * 在 {@code fnval=0}/{@code fnval=1} 下最多只回 {@code quality=64}（720P，accept=[64,16]），
 * 只有 {@code fnval=16}（DASH）才回 {@code quality=80}（accept=[112,80,64,32,16]）。
 * 把 80 划进「普通档」会静默丢掉 1080P，所以这里只把 6/16/32/64 当普通档——
 * 这也是与兄弟仓库的**有意差异**（它的 80 走合一 MP4 是在它自己的设备分档前提下）。
 *
 * <p>本类是纯函数对象：不碰 Android API、不读配置、不做 IO，全部逻辑可 JVM 单测。
 * 「有没有统一内核（ExoPlayer）可用」由调用方传入（PlayerApi 取 util/DeviceProfile 的判定结果），
 * 这样策略类本身不需要依赖 Android 运行时。
 */
object PlaybackStrategyPolicy {

    /** 实际用哪一份流。 */
    enum class Source {
        /** 音视频合一的 MP4（durl），单流单时钟。 */
        MUXED,

        /** DASH 双流（视频 + 音轨）。 */
        DASH,
    }

    /** 普通档位：这些档位都能从合一 MP4 接口拿到（实测最高 720P）。 */
    private val PROGRESSIVE_QUALITIES = setOf(6, 16, 32, 64)

    /**
     * 降级用的完整档位序列（从高到低）。
     *
     * 80/74 也在里面：合一 MP4 请求高档位时服务端会自动降级发流（实测 fnval=1 请求 80 回 64），
     * 先试高档位不亏，失败再往下试，能尽量贴近用户原本的清晰度。
     */
    private val QUALITY_FALLBACK_ORDER = listOf(116, 112, 80, 74, 64, 32, 16, 6)

    /** 自动档（qn<=0）兜底时使用的合一流档位上限：实测合一 MP4 最高就给 720P。 */
    private const val PROGRESSIVE_MAX_QN = 64

    /**
     * 该档位是否属于「普通档」——即应该优先用合一 MP4 播放。
     *
     * @param qn 实际要播的清晰度（服务端下发的 quality，不是用户请求的档位）
     * @return true 表示 6/16/32/64（240P/360P/480P/720P），false 表示 1080P 及以上（必须走 DASH）
     */
    @JvmStatic
    fun usesProgressivePlayback(qn: Int): Boolean = qn in PROGRESSIVE_QUALITIES

    /**
     * 在「DASH 结果」与「合一 MP4 结果」之间做选择。
     *
     * <p>规则：合一 MP4 拿到的真实档位不低于 DASH 时才切过去（否则会白白降清晰度）；
     * 合一 MP4 被服务端降档时，如果还有统一内核（ExoPlayer）可用就保留 DASH（1080P 不能丢），
     * 没有统一内核时只能退回合一 MP4——纯 DASH 视频流在无统一内核的设备上就是「有画面没声音」，
     * 那比清晰度低更糟。
     *
     * @param dashQn                 DASH 实际拿到的档位
     * @param progressiveQn          合一 MP4 实际拿到的档位（<=0 表示没拿到可用 durl）
     * @param unifiedPlayerAvailable 是否有统一内核可用（ExoPlayer / UnifiedDashPlayer）
     * @return 应当采用 [Source.MUXED] 还是 [Source.DASH]
     */
    @JvmStatic
    fun chooseSource(dashQn: Int, progressiveQn: Int, unifiedPlayerAvailable: Boolean): Source {
        // 没拿到合一 MP4 的有效档位：只能留在 DASH
        if (progressiveQn <= 0) return Source.DASH
        // 合一流档位不低于 DASH：直接用合一流（单时钟，不会音画漂移）
        if (progressiveQn >= dashQn) return Source.MUXED
        // 合一流被降档：有统一内核就保 DASH 不丢清晰度，没有就只能接受合一流
        return if (unifiedPlayerAvailable) Source.DASH else Source.MUXED
    }

    /**
     * 统一播放器起播失败时的合一 MP4 降级档位序列（从高到低）。
     *
     * <p>例：请求 1080P60(116) → [116, 112, 80, 74, 64, 32, 16, 6]；
     * 请求 720P(64) → [64, 32, 16, 6]。自动档（qn<=0）从 64 开始（合一接口的上限）。
     *
     * @param qn 原始请求档位；<=0 表示自动
     * @return 依次尝试的档位列表，至少包含一项
     */
    @JvmStatic
    fun fallbackQualities(qn: Int): List<Int> {
        if (qn <= 0) {
            return QUALITY_FALLBACK_ORDER.filter { it <= PROGRESSIVE_MAX_QN }
        }
        val candidates = ArrayList<Int>(QUALITY_FALLBACK_ORDER.size + 1)
        // 请求的档位本身不在降级序列里（如 100 智能修复、126 杜比）时，先按原档位试一次，
        // 让服务端自己决定能降多少；再按标准序列往下走
        if (qn !in QUALITY_FALLBACK_ORDER) candidates.add(qn)
        for (quality in QUALITY_FALLBACK_ORDER) {
            if (quality <= qn && quality !in candidates) candidates.add(quality)
        }
        // 极端值保护：所有档位都比 qn 高（qn=1 之类）时，至少还能试一个最低档
        if (candidates.isEmpty()) candidates.add(QUALITY_FALLBACK_ORDER.last())
        return candidates
    }
}
