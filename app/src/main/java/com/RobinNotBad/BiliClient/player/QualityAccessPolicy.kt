package com.RobinNotBad.BiliClient.player

/**
 * 「清晰度能不能选」的纯策略：按登录态与大会员状态算出最高可选清晰度。
 *
 * 背景（26.10.30）：清晰度列表来自 `playurl` 的 `accept_quality`，它列的是「这个视频有哪些档」，
 * **不代表当前账号有权限播**。未登录时 1080P 及以上只能看个名字，选中也只会拿到 720P 的流
 * （服务端按账号权限发流，不会报错）；非大会员选 1080P60/4K 同理。与其让用户点了没反应，
 * 不如在列表里直接锁住并给出原因。
 *
 * 档位与门槛：
 * - 未登录：最高 720P（[QN_720P]）；
 * - 已登录非大会员：最高 1080P（[QN_1080P] = 80）；
 * - 大会员：不限（1080P60 = 116、4K = 120 都可选）。
 *
 * 纯函数，方便单测：真实账号状态在设备上不好凑齐三种组合。
 */
object QualityAccessPolicy {
    /** 720P 的 qn 值（与 [PlayerStreamPolicy.QN_720P] 同一含义）。 */
    const val QN_720P = PlayerStreamPolicy.QN_720P

    /** 1080P 高清的 qn 值：登录就能选。 */
    const val QN_1080P = 80

    /** 1080P 60 帧的 qn 值：从这一档起要大会员。 */
    const val QN_1080P60 = 116

    /** 4K 超清的 qn 值：大会员专属。 */
    const val QN_4K = 120

    /** 当前状态下最高可选的 qn；大会员返回 [Int.MAX_VALUE]（不设上限）。 */
    @JvmStatic
    fun maxUnlockedQn(loggedIn: Boolean, vip: Boolean): Int = when {
        !loggedIn -> QN_720P
        !vip -> QN_1080P
        else -> Int.MAX_VALUE
    }

    /** 这条清晰度在当前状态下是否被锁住（高于 [maxUnlockedQn] 即锁）。 */
    @JvmStatic
    fun isLocked(quality: Int, loggedIn: Boolean, vip: Boolean): Boolean =
        quality > maxUnlockedQn(loggedIn, vip)

    /**
     * 这一档**本身**要不要大会员（与当前账号是谁无关，新版清晰度面板据此挂「大会员」徽标）。
     *
     * 判据不是猜的：取流接口只回 `accept_quality` / `accept_description` 两个平行数组，
     * `support_formats` 里也只有 quality / format / new_description / display_desc /
     * superscript / codecs，**没有任何 vip 字段**（见 bilibili-API/docs/video/videostream_url.md）。
     * 同一份文档的 qn 表把 100 / 112 / 116 / 120 / 125 / 126 / 127 / 129 全标成「大会员认证」，
     * 而 ≤80 的档位只要登录（74 / 80）或完全不要认证——正好就是本文件已有的
     * 「已登录非大会员最高 1080P」那条线，所以直接复用 [QN_1080P]，不另立阈值。
     */
    @JvmStatic
    fun requiresVip(quality: Int): Boolean = quality > QN_1080P

    /**
     * 清晰度面板要不要补一个「4K 超清」锁定项。
     *
     * 4K 是大会员专属，服务端对没有权限的账号**不会把 120 写进 accept_quality**（实测匿名请求
     * qn=127&fnval=4048 只回 [116,80,64,32,16]），于是未登录/非大会员的面板里连 4K 这个名字都
     * 看不到，也就无从得知"开会员能解锁 4K"。所以没权限时补一个锁定项当提示位：它永远点不动，
     * 点了只会弹「开通大会员」，不会切到别的档位。
     *
     * 有权限时（大会员）不补：服务端会把真实的 120 下发，补齐也不会缺，硬补反而可能在没 4K
     * 源的视频上造出一个假档位。
     *
     * @param qualityValues 面板现有档位（来自 accept_quality）
     */
    @JvmStatic
    fun needsLocked4kHint(qualityValues: IntArray, loggedIn: Boolean, vip: Boolean): Boolean =
        isLocked(QN_4K, loggedIn, vip) && qualityValues.none { it >= QN_4K }
}
