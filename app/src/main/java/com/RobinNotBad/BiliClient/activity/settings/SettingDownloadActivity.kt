package com.RobinNotBad.BiliClient.activity.settings

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.base.RefreshListActivity
import com.RobinNotBad.BiliClient.adapter.SettingsAdapter
import com.RobinNotBad.BiliClient.model.SettingSection
import com.RobinNotBad.BiliClient.util.Aria2Util
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.FileUtil
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil

class SettingDownloadActivity : RefreshListActivity() {

    private lateinit var adapter: SettingsAdapter
    private var sections: MutableList<SettingSection> = ArrayList()

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setPageName(getString(R.string.pagename_download_setting))

        buildSections()

        recyclerView.setHasFixedSize(true)

        adapter = SettingsAdapter(this, sections)
        adapter.onSettingChanged = { key, _ ->
            if (key == "cache_quick_mode") {
                recyclerView.post {
                    buildSections()
                    adapter.notifyDataSetChanged()
                }
            }
        }
        setAdapter(adapter)

        setRefreshing(false)

        findViewById<android.view.View>(R.id.pageName).setOnClickListener { finish() }

        findViewById<android.view.View>(R.id.pageName).setOnLongClickListener {
            CenterThreadPool.run {
                if (!Aria2Util.isEnabled()) {
                    runOnUiThread { MsgUtil.showMsg("请先启用高速下载模式") }
                    return@run
                }
                val status = Aria2Util.getDownloadStatus()
                runOnUiThread { MsgUtil.showText("下载引擎状态", status) }
            }
            true
        }
    }

    private fun buildSections() {
        sections.clear()
        val quickModeEnabled = SharedPreferencesUtil.getBoolean("cache_quick_mode", false)

        sections.add(SettingSection("title",
            getString(R.string.aria2_basic_title), "", "", ""))

        sections.add(SettingSection("switch",
            getString(R.string.aria2_enable),
            "aria2_enabled",
            getString(R.string.aria2_enable_desc),
            "false"))

        sections.add(SettingSection("switch",
            getString(R.string.aria2_builtin_mode),
            "aria2_builtin",
            getString(R.string.aria2_builtin_mode_desc),
            "true"))

        sections.add(SettingSection("title",
            getString(R.string.aria2_performance_title), "", "", ""))

        sections.add(SettingSection("input_int",
            getString(R.string.aria2_max_concurrent),
            "aria2_max_concurrent",
            getString(R.string.aria2_max_concurrent_desc),
            "5"))

        sections.add(SettingSection("input_int",
            getString(R.string.aria2_max_connection),
            "aria2_max_connection",
            getString(R.string.aria2_max_connection_desc),
            "16"))

        sections.add(SettingSection("input_int",
            getString(R.string.aria2_split),
            "aria2_split",
            getString(R.string.aria2_split_desc),
            "5"))

        sections.add(SettingSection("title",
            "缓存选项", "", "", ""))

        sections.add(SettingSection("switch",
            getString(R.string.cache_quick_mode),
            "cache_quick_mode",
            getString(R.string.cache_quick_mode_desc),
            "false"))

        sections.add(SettingSection("input_int",
            "并行下载视频数",
            "parallel_download_videos",
            "同时在后台并行下载的视频数量，推荐2-5个。网络环境良好时可以提高效率",
            Aria2Util.DEFAULT_PARALLEL_DOWNLOAD_VIDEOS.toString()))

        if (quickModeEnabled) {
            sections.add(SettingSection("list_choose",
                getString(R.string.cache_default_quality),
                "cache_default_quality",
                getString(R.string.cache_default_quality_desc),
                "dialog",
                SettingsAdapter.ListChooseHolder.ListChooseExtra(
                    java.util.Arrays.asList(
                        getString(R.string.cache_quality_highest),
                        getString(R.string.cache_quality_720p),
                        getString(R.string.cache_quality_360p),
                        getString(R.string.cache_quality_audio_only),
                        getString(R.string.cache_quality_dialog)
                    ),
                    java.util.Arrays.asList("highest", "64", "16", "audio_only", "dialog")
                )))
        }

        sections.add(SettingSection("title",
            getString(R.string.aria2_rpc_title), "", "", ""))

        sections.add(SettingSection("input_string",
            getString(R.string.aria2_rpc_url),
            "aria2_rpc_url",
            getString(R.string.aria2_rpc_url_desc),
            "http://127.0.0.1:6800/jsonrpc"))

        sections.add(SettingSection("input_string",
            getString(R.string.aria2_secret),
            "aria2_secret",
            getString(R.string.aria2_secret_desc),
            ""))

        sections.add(SettingSection("info",
            getString(R.string.aria2_path_info),
            FileUtil.getVideoDownloadPath().toString(),
            "",
            ""))
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1001 && resultCode == RESULT_OK && data != null) {
            val sectionPosition = data.getIntExtra("position", -1)
            val value = data.getStringExtra("value")
            if (sectionPosition >= 0 && sectionPosition < sections.size && value != null) {
                val section = sections[sectionPosition]
                SharedPreferencesUtil.putString(section.id, value)
                adapter.notifyItemChanged(sectionPosition)
            }
        }
    }
}