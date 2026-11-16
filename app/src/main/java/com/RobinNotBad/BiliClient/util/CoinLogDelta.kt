package com.RobinNotBad.BiliClient.util

import java.math.BigDecimal

/**
 * 「硬币变化记录」里变化量的显示文本。
 *
 * <h3>为什么单独抽成一个对象</h3>
 * 服务端返回的 `delta` 是 **num**，不是整数：接口文档 `x/member/web/coin/log` 的样本里，打赏类
 * 记录既有 `-1`，也有 `0.1` / `0.2`。这条链路以前一路用 `int`
 * （`CoinLogApi` 的 `item.getInt("delta")` + `CoinLog.delta`），小数被截断成 `0`——硬币流水里就
 * 成了「打赏显示变化量为 0」（26.11.14 反馈）。修法是把解析放宽到小数，再由这里的纯函数决定
 * 怎么显示；判断部分放纯函数是因为它能在 JVM 单测里钉住，View 层跑不起来。
 *
 * <h3>负的小数为什么乘 10</h3>
 * **支出不可能是小数硬币**：硬币只有整数枚，扣 0.1 枚不存在。所以出现「负的小数」只有一种解释
 * ——服务端对这类记录以 0.1 硬币为单位计数（官方客户端显示的整数就是它的 10 倍）。
 * 收入则相反：打赏收入本来就是小数（文档样本 `0.1` / `0.2`），原样显示才对，不能跟着乘。
 *
 * 全是十进制精确算术（[BigDecimal]），不碰二进制浮点的 `0.1 * 10 == 1.0000000000000002` 这类坑。
 */
object CoinLogDelta {

    /**
     * 变化量 → 展示文本。
     *
     * - 整数（含 0）：正数前置 `+`，负数直接带负号，与旧行为一致（`+1` / `-1` / `0`）。
     * - 负的小数：按 0.1 硬币单位还原成整数（`-0.1` → `-1`，`-0.2` → `-2`）。
     * - 正的小数：原样显示（`0.1` → `+0.1`），以前会被截断成 `0`。
     *
     * @param delta 接口返回的原始变化量（正值为收入，负值为支出）
     */
    @JvmStatic
    fun text(delta: Double): String {
        if (!delta.isFinite()) return "0"

        val value = BigDecimal.valueOf(delta).stripTrailingZeros()
        val coins = if (value.signum() < 0 && value.scale() > 0) value.movePointRight(1) else value
        val digits = coins.toPlainString()
        return if (coins.signum() > 0) "+$digits" else digits
    }
}
