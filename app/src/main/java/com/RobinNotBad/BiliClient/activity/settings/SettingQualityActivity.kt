package com.RobinNotBad.BiliClient.activity.settings

import android.os.Bundle
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.base.BaseActivity
import com.RobinNotBad.BiliClient.adapter.QualityChooseAdapter
import com.RobinNotBad.BiliClient.listener.OnItemClickListener
import com.RobinNotBad.BiliClient.player.QualityAccessPolicy
import com.RobinNotBad.BiliClient.ui.widget.recycler.CustomLinearManager
import com.RobinNotBad.BiliClient.util.AccountStateStore
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.NetworkPlaybackQuality
import com.RobinNotBad.BiliClient.util.SettingsKeys
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import java.util.LinkedHashMap

/**
 * 起播默认清晰度设置（26.11.06 起支持「按网络类型区分」）。
 *
 * ## 三种状态（同一页内切换，返回键逐级退回）
 * - **[Mode.BASE]**：先列当前账号选得到的档位（点一下 = 存 [SettingsKeys.PLAY_QN] 并退出，
 *   与改造前完全一致），末尾追加两行「Wi-Fi / 移动数据默认清晰度（当前：…）」，
 *   点进去是各自的子列表；
 * - **[Mode.WIFI] / [Mode.MOBILE]**：首行「跟随默认」（存 -1），其后是档位。
 *
 * 两个新键默认 -1 = 跟随默认，所以**没进过这个页面的用户行为零变化**。
 *
 * ## 为什么档位表只列账号权限内的
 * 未登录看不到 1080P 及以上，非大会员看不到 1080P60/4K。与播放器清晰度面板的锁态同源
 * （[QualityAccessPolicy]），只是设置页直接隐藏——设置项没有「点一下看看提示」的语境，
 * 留着一堆点不动的选项更迷惑。
 */
class SettingQualityActivity : BaseActivity() {

    /** 当前正在选哪一项。 */
    private enum class Mode { BASE, WIFI, MOBILE }

    private lateinit var adapter: QualityChooseAdapter
    private lateinit var recyclerView: RecyclerView
    private var mode = Mode.BASE
    private var accountState: AccountStateStore.State? = null

    companion object {
        /** qn 档位表；`SettingPlayerChooseActivity` 也用它把已存 qn 反查成显示名。 */
        @JvmField
        val qnMap: LinkedHashMap<String, Int> = LinkedHashMap<String, Int>().apply {
            put("360P", 16)
            put("720P", 64)
            put("1080P", 80)
            put("1080P60", 116)
            put("4K", 120)
        }

        /** 顶栏返回 / 返回键的统一处理：子列表先回主列表，主列表才真正退出。 */
        private fun isBase(mode: Mode): Boolean = mode == Mode.BASE
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        asyncInflate(R.layout.activity_simple_list) { _, _ ->
            recyclerView = findViewById(R.id.recyclerView)
            findViewById<View>(R.id.top)?.setOnClickListener { onBackStep() }

            adapter = QualityChooseAdapter(this)
            adapter.onItemClickListener = OnItemClickListener { onRowClick(it) }

            recyclerView.layoutManager = CustomLinearManager(this)
            recyclerView.adapter = adapter

            // 先用缓存渲染，避免进设置要等一次网络；后台查到真实状态再刷新一次列表
            accountState = AccountStateStore.cached()
            render()
            CenterThreadPool.run {
                val state = AccountStateStore.refresh()
                CenterThreadPool.runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        accountState = state
                        render()
                    }
                }
            }
        }
    }

    override fun onBackPressed() {
        // 先尊重「禁用返回键」开关：开着时返回键在任何层级都不动作（与 BaseActivity 一致）
        if (SharedPreferencesUtil.getBoolean(SettingsKeys.BACK_DISABLE, false)) return
        // 子列表不直接关页面：先退回「主列表」，否则用户在 Wi-Fi 子页按返回会以为选错了
        if (!isBase(mode)) {
            mode = Mode.BASE
            render()
            return
        }
        super.onBackPressed()
    }

    // ------------------------------------------------------------ 渲染

    /** 当前账号权限内可选的档位名（按 qnMap 原顺序）。 */
    private fun allowedNames(): List<String> {
        val state = accountState ?: AccountStateStore.cached()
        val maxQn = QualityAccessPolicy.maxUnlockedQn(state.loggedIn, state.vip)
        val names = ArrayList<String>(qnMap.size)
        for ((name, qn) in qnMap) {
            if (qn <= maxQn) names.add(name)
        }
        return names
    }

    /** 把已存 qn 转成显示名；不认识（含 -1）时显示「跟随默认」。 */
    private fun qualityLabel(qn: Int): String {
        for ((name, value) in qnMap) {
            if (value == qn) return name
        }
        return getString(R.string.net_quality_follow_default)
    }

    private fun render() {
        val names = ArrayList<String>()
        when (mode) {
            Mode.BASE -> {
                names.addAll(allowedNames())
                names.add(
                    getString(
                        R.string.net_quality_row_wifi,
                        qualityLabel(
                            SharedPreferencesUtil.getInt(
                                SettingsKeys.PLAY_QN_WIFI,
                                NetworkPlaybackQuality.FOLLOW_DEFAULT
                            )
                        )
                    )
                )
                names.add(
                    getString(
                        R.string.net_quality_row_mobile,
                        qualityLabel(
                            SharedPreferencesUtil.getInt(
                                SettingsKeys.PLAY_QN_MOBILE,
                                NetworkPlaybackQuality.FOLLOW_DEFAULT
                            )
                        )
                    )
                )
                setPageName(getString(R.string.net_quality_page_title))
            }

            Mode.WIFI -> {
                names.add(getString(R.string.net_quality_follow_default))
                names.addAll(allowedNames())
                setPageName(getString(R.string.net_quality_wifi_title))
            }

            Mode.MOBILE -> {
                names.add(getString(R.string.net_quality_follow_default))
                names.addAll(allowedNames())
                setPageName(getString(R.string.net_quality_mobile_title))
            }
        }
        adapter.nameList = names
    }

    // ------------------------------------------------------------ 点击

    private fun onBackStep() {
        if (isBase(mode)) {
            setResult(RESULT_CANCELED)
            finish()
        } else {
            mode = Mode.BASE
            render()
        }
    }

    private fun onRowClick(position: Int) {
        when (mode) {
            Mode.BASE -> onBaseClick(position)
            Mode.WIFI -> onNetworkClick(SettingsKeys.PLAY_QN_WIFI, position)
            Mode.MOBILE -> onNetworkClick(SettingsKeys.PLAY_QN_MOBILE, position)
        }
    }

    /** 主列表：前 N 行是档位（选中即保存并退出），最后两行进入网络子列表。 */
    private fun onBaseClick(position: Int) {
        val allowed = allowedNames()
        when {
            position < allowed.size -> {
                val qn = qnMap[allowed[position]] ?: return
                SharedPreferencesUtil.putInt(SettingsKeys.PLAY_QN, qn)
                finish()
            }

            position == allowed.size -> {
                mode = Mode.WIFI
                render()
            }

            position == allowed.size + 1 -> {
                mode = Mode.MOBILE
                render()
            }
        }
    }

    /** 网络子列表：第 0 行是「跟随默认」(-1)，其余是档位；选完回主列表（不退出）。 */
    private fun onNetworkClick(prefKey: String, position: Int) {
        if (position == 0) {
            SharedPreferencesUtil.putInt(prefKey, NetworkPlaybackQuality.FOLLOW_DEFAULT)
        } else {
            val allowed = allowedNames()
            val index = position - 1
            if (index < 0 || index >= allowed.size) return
            val qn = qnMap[allowed[index]] ?: return
            SharedPreferencesUtil.putInt(prefKey, qn)
        }
        mode = Mode.BASE
        render()
    }
}
