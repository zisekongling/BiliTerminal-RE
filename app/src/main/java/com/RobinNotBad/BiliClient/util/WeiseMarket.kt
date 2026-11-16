package com.RobinNotBad.BiliClient.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import org.json.JSONObject

/**
 * 「去微思下载」（26.11.16 新增）：把更新入口交给微思商店。
 *
 * <h3>为什么要这个</h3>
 * 应用内的更新链路是「Gitee / GitHub Release → 直链下载 → ApkVerifier 校验 → 系统安装器」，
 * 这条链在部分设备上会被浏览器/下载器打断（手表端尤其明显）。微思商店上有同一个 APK
 * （条目 id=2749），装过商店的用户从商店更新更省事。
 *
 * <h3>为什么必须先校验版本号</h3>
 * 商店上的 APK 是**共存版**、且由开发者手动上传——它可能比 Release 落后一版。
 * 用户在更新页看到「26.11.16」，点进商店却装到上一版，会以为更新坏了。
 * 所以按钮只在「商店返回的 versionCode 与检测到的更新版本号完全一致」时才出现。
 *
 * <h3>微思的接口长什么样（26.11.16 实测）</h3>
 * 详情页 https://apk.wysteam.cn/app/?id=2749 是纯前端壳，数据来自
 * POST https://api.wysteam.cn/market/app/info/ ，body 为 id=2749
 * （application/x-www-form-urlencoded）。返回的应用对象**没有 code/data 外层**，
 * verid（= versionCode）与 version（= versionName）都在顶层。
 */
object WeiseMarket {

    /** 微思商店里本应用（共存版）的条目 id。 */
    const val APP_ID = 2749

    /** 商店详情页：既是跳转目标，也是写进剪贴板的内容。 */
    const val PAGE_URL = "https://apk.wysteam.cn/app/?id=2749"

    private const val INFO_API = "https://api.wysteam.cn/market/app/info/"
    private const val TAG = "微思下载"

    /**
     * 从商店接口的 JSON 里取 versionCode（verid）。取不到 / 解析失败返回 0。
     *
     * 顶层与 data 里都试一遍：接口现在把应用对象直接放在顶层，但这类市场接口
     * 换成 code/data 包一层的写法很常见，多认一种不至于让它悄悄失效。
     * 另外**不校验 code 字段**——现在的响应根本没有它，硬等 200 会永远拿不到版本号。
     */
    @JvmStatic
    fun parseVerid(body: String?): Int {
        if (body.isNullOrBlank()) return 0
        return try {
            val root = JSONObject(body)
            val direct = root.optInt("verid", 0)
            if (direct > 0) return direct
            root.optJSONObject("data")?.optInt("verid", 0) ?: 0
        } catch (e: Exception) {
            Logu.w(TAG, "解析商店版本号失败：" + e.message)
            0
        }
    }

    /** 商店上的版本号是否就是我们要推的这个版本（两边都要大于 0，0 一律不算一致）。 */
    @JvmStatic
    fun versionMatches(verid: Int, versionCode: Int): Boolean =
        verid > 0 && versionCode > 0 && verid == versionCode

    /**
     * 查一次商店版本号。网络失败/解析失败都返回 0（调用方据此隐藏按钮，不弹错误——
     * 用户本来只是来看更新的，不该被一个辅助入口的网络错误打扰）。
     */
    fun fetchVerid(): Int = try {
        val response = NetWorkUtil.post(
            INFO_API,
            "id=2749",
            ArrayList(),
            "application/x-www-form-urlencoded"
        )
        // OkHttp 4 起 body 是 val（body() 已弃用，本仓库把弃用当错误）
        val body = response.use { it.body?.string() }
        parseVerid(body)
    } catch (e: Exception) {
        Logu.w(TAG, "查询商店版本号失败：" + e.message)
        0
    }

    /**
     * 把商店链接写进剪贴板，返回是否写成功。
     *
     * 这个动作同时承担「探测能不能写剪贴板」的职责：部分 ROM（省电模式或隐私管控打开时）
     * 会静默拒绝写入，那不显示按钮比显示了点不动更好。写入要在主线程做，调用方负责切线程。
     */
    fun copyPageUrl(context: Context): Boolean = try {
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        if (manager == null) {
            false
        } else {
            manager.setPrimaryClip(ClipData.newPlainText("微思商店", PAGE_URL))
            true
        }
    } catch (t: Throwable) {
        Logu.w(TAG, "写入剪贴板失败：" + t.message)
        false
    }

    /**
     * 已知的微思商店包名（26.11.16 实测）。
     *
     * - `com.qx.wysappmarket`：手表上装的是这个（versionName 3.8.1-CE）；
     * - `com.wys.appmarket`：另一处见到过的包名，两个都试，装哪个用哪个。
     *
     * 顺序即优先级：手表端那个在前。
     */
    val MARKET_PACKAGES: List<String> = listOf("com.qx.wysappmarket", "com.wys.appmarket")

    /**
     * 按优先级挑第一个装着的商店包名，没装返回 null（纯函数，方便单测）。
     */
    @JvmStatic
    fun pickMarketPackage(installed: (String) -> Boolean): String? =
        MARKET_PACKAGES.firstOrNull { installed(it) }

    /** 本机装着的微思商店包名，没装返回 null。 */
    fun findInstalledMarket(context: Context): String? = pickMarketPackage { pkg ->
        try {
            context.packageManager.getPackageInfo(pkg, 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 打开微思商店的 Intent；本机没装商店返回 null（调用方自行决定兜底方式）。
     *
     * 26.11.16 线上问题：原来直接 `ACTION_VIEW` 那个 https 链接，指望商店注册了
     * apk.wysteam.cn 的 VIEW 过滤器——**实测它没注册**（`cmd package query-activities`
     * 对该链接只命中 `com.huawei.browser/.Main`），于是点按钮只打开了浏览器，
     * 「跳商店」这件事根本没发生。商店只声明了 MAIN/LAUNCHER，所以只能按包名显式启动：
     * 进商店首页，链接已在剪贴板里，由用户粘贴。
     */
    fun buildOpenIntent(context: Context): Intent? {
        val pkg = findInstalledMarket(context) ?: return null
        val intent = context.packageManager.getLaunchIntentForPackage(pkg) ?: return null
        // 从 Activity 调也加上：部分 ROM 的商店会自己起新栈，带上更稳
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return intent
    }

    /** 没装商店时的兜底：用浏览器打开商店网页（此时链接已写进剪贴板）。 */
    fun buildBrowserFallbackIntent(): Intent = Intent(Intent.ACTION_VIEW, Uri.parse(PAGE_URL))
}
