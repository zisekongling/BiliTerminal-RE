package com.RobinNotBad.BiliClient.player

import java.util.Locale

/**
 * 播放器的「设备兼容性默认值」纯判据（O6）。
 *
 * <h3>要解决的两个真机问题</h3>
 * 1. **华为 / 鸿蒙设备的硬解花屏、绿屏、音画不同步**：这几家的 MediaCodec 实现对 H.264 的
 *    surface 直出跟 ijk 的默认参数不兼容，社区共识是默认走软解（软解在手表上更稳，代价是
 *    功耗高一点，用户可以自己改回硬解）。
 * 2. **ABI 不匹配时别再硬起 ijk**：`app/libs/` 里只有 `arm64-v8a` / `armeabi-v7a` / `x86`
 *    三套 .so。x86_64 模拟器、mips、或者被裁剪过的国产 ROM 上 `dlopen` 必然失败，而现在
 *    会先打一屏「原生库加载失败」的诊断再去起系统播放器，用户看到的是「报错」而不是「换了个播放器」。
 *
 * <h3>为什么判据要抽出来</h3>
 * 上面两条都是「读一串设备字符串、做一个集合判断」，但一旦写进 `IjkNativeLibs` 就粘上了
 * `android.os.Build` 与 Context，只能靠真机验证。抽成纯函数后可以把各家真机的 `Build.*`
 * 取值直接喂进 JVM 单测（见 `PlayerCompatPolicyTest`）。
 *
 * <h3>不做什么</h3>
 * - 不判断「该不该用 ijk」，只判断「该不该走软解 / 该不该回退系统播放器」，接不接线由调用方决定。
 * - 拿不到 ABI 信息（null / 空表）时**不擅自回退**：宁可让它照旧尝试，也不要因为读不到信息
 *   就把所有人的播放器都换掉。
 */
object PlayerCompatPolicy {

    /**
     * 命中这些关键字就走软解的厂商 / 品牌关键字（全部小写，比对前统一 lowercase）。
     *
     * `harmony` 与 `honor` 是分开的两个词：鸿蒙设备的 MANUFACTURER 常是 `HUAWEI`，而分家后的
     * 荣耀是 `HONOR`，品牌串里也可能出现 `HarmonyOS`。
     */
    val SOFTWARE_CODEC_KEYWORDS: List<String> = listOf("huawei", "honor", "harmony")

    /**
     * 本仓库真正随包发布了 .so 的 ABI（对应 `app/libs/{arm64-v8a,armeabi-v7a,x86}`）。
     *
     * 注意 `arm64-v8a` 必须算「支持」：它是现在绝大多数手表手机的主 ABI，把它当不支持会让
     * 所有人回退系统播放器。新增/删除 `app/libs/` 下的目录时这里要同步。
     */
    val SUPPORTED_NATIVE_ABIS: List<String> = listOf("arm64-v8a", "armeabi-v7a", "x86")

    /**
     * 是否建议默认软解。
     *
     * @param manufacturer `android.os.Build.MANUFACTURER`
     * @param brand `android.os.Build.BRAND`
     * @param model `android.os.Build.MODEL`（可选，个别华为机只有型号里带厂商信息）
     */
    @JvmStatic
    @JvmOverloads
    fun prefersSoftwareCodec(manufacturer: String?, brand: String?, model: String? = null): Boolean {
        val text = buildString {
            append(manufacturer.orEmpty())
            append(' ')
            append(brand.orEmpty())
            append(' ')
            append(model.orEmpty())
        }.lowercase(Locale.US)
        if (text.isBlank()) return false
        return SOFTWARE_CODEC_KEYWORDS.any { text.contains(it) }
    }

    /** 某个 ABI 是否在随包 .so 的覆盖范围内（大小写不敏感、两端空白忽略）。 */
    @JvmStatic
    fun isSupportedNativeAbi(abi: String?): Boolean {
        if (abi.isNullOrBlank()) return false
        val normalized = abi.trim().lowercase(Locale.US)
        if (normalized.isEmpty()) return false
        return SUPPORTED_NATIVE_ABIS.any { it == normalized }
    }

    /**
     * 是否应该放弃 ijk、改用系统（平台）播放器。
     *
     * @param abis 设备声明的 ABI 列表（一般就是 `Build.SUPPORTED_ABIS`）
     * @return 一个都不支持时为 true；**列表为 null / 空 / 全是空串时为 false**
     *   （信息缺失不构成回退理由：宁可照旧尝试，也不要因为读不到就把所有人的播放器换掉）
     */
    @JvmStatic
    fun shouldUsePlatformMediaPlayer(abis: List<String>?): Boolean {
        if (abis.isNullOrEmpty()) return false
        val usable = abis.filter { !it.isNullOrBlank() }
        if (usable.isEmpty()) return false
        return usable.none { isSupportedNativeAbi(it) }
    }

    /** 两参重载：老代码里通常只有 `Build.CPU_ABI` + `Build.CPU_ABI2` 两个字段。 */
    @JvmStatic
    fun shouldUsePlatformMediaPlayer(primaryAbi: String?, secondaryAbi: String?): Boolean =
        shouldUsePlatformMediaPlayer(listOfNotNull(primaryAbi, secondaryAbi))
}
