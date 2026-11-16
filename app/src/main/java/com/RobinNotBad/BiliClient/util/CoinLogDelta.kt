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
 * <h3>为什么不按正负推单位</h3>
 * 26.11.14 的第一版曾假定「支出不可能是小数硬币，所以负的小数是以 0.1 枚为单位记账」，把 `-0.1`
 * 显示成 `-1`。这是**没有任何样本支撑的推断**：接口文档里出现的 `delta` 只有整数支出（`-1`）
 * 与小数收入（`0.1` / `0.2`），一条负小数都没有。既然没有样本，就不该凭空换算单位——真出现
 * 负小数时按原值显示，比显示一个自己编出来的 10 倍数字更不容易骗人。
 *
 * 全是十进制精确算术（[BigDecimal]），不碰二进制浮点的 `0.1 * 10 == 1.0000000000000002` 这类坑。
 */
object CoinLogDelta {

    /**
     * 变化量 → 展示文本。
     *
     * - 整数（含 0）：正数前置 `+`，负数直接带负号，与旧行为一致（`+1` / `-1` / `0`）。
     * - 正的小数：原样显示（`0.1` → `+0.1`），以前会被截断成 `0`。
     * - 负的小数：同样原样显示（`-0.1` → `-0.1`），不乘 10。
     *
     * @param delta 接口返回的原始变化量（正值为收入，负值为支出）
     */
    @JvmStatic
    fun text(delta: Double): String {
        if (!delta.isFinite()) return "0"

        val value = BigDecimal.valueOf(delta).stripTrailingZeros()
        // 0 与 -0.0 先归一：`stripTrailingZeros()` 在 minSdk 19 那代老 libcore 上对 0 会原样留着
        // 小数位（`0.0` 的 scale 仍是 1，JDK-6480539），`toPlainString()` 于是给出 "0.0"。
        // 桌面 JVM 上抓不到这个差异，所以这里不去依赖它的返回值。
        if (value.signum() == 0) return "0"

        val digits = value.toPlainString()
        return if (value.signum() > 0) "+$digits" else digits
    }
}
