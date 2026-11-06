package com.RobinNotBad.BiliClient.activity.settings

import android.content.Intent
import android.os.Bundle
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.base.RefreshListActivity
import com.RobinNotBad.BiliClient.adapter.SettingsAdapter
import com.RobinNotBad.BiliClient.model.SettingSection
import com.RobinNotBad.BiliClient.player.PlayerSettingsGroups

/**
 * 「完整设置」页：内置播放器的全部设置项，按七个组带标题展开。
 *
 * 设置项本身不在这里拼了——全部来自 [PlayerSettingsGroups]，好让假播放器的按钮卡片共用同一份数据。
 * 这一页只负责把它拍平、丢给 [SettingsAdapter]、并把 `list_choose` 的回写结果接回来。
 */
class SettingTerminalPlayerActivity : RefreshListActivity() {

    /** 拍平后的设置项全表（含每组前面那行 `title`）。`list_choose` 的选择结果靠它按 position 反查回写。 */
    private var sections: List<SettingSection> = emptyList()
    private var settingsAdapter: SettingsAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setPageName(getString(R.string.pagename_settingterminalplayeractivity_001))

        sections = PlayerSettingsGroups.flatten(this)

        recyclerView.setHasFixedSize(true)

        val adapter = SettingsAdapter(this, sections)
        settingsAdapter = adapter
        setAdapter(adapter)

        setRefreshing(false)

        scrollToHighlight(sections, intent.getStringExtra("highlight"))
    }

    /**
     * 承接 list_choose 的选择结果。
     *
     * SettingsAdapter 用 SettingsAdapter.REQUEST_CODE_LIST_CHOOSE 打开
     * [com.RobinNotBad.BiliClient.activity.ListChooseActivity]；回写逻辑与分组页共用
     * [applyListChooseResult]（此前只有分组页处理这个回调，所以本页的 list_choose「点了没反应」）。
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        applyListChooseResult(requestCode, resultCode, data, sections) { settingsAdapter?.notifyItemChanged(it) }
    }
}
