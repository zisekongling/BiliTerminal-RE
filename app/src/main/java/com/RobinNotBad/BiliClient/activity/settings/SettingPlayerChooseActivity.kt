package com.RobinNotBad.BiliClient.activity.settings

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.TextView
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.base.BaseActivity
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.NetworkPlaybackQuality
import com.RobinNotBad.BiliClient.util.SettingsKeys
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import com.RobinNotBad.BiliClient.util.ToolsUtil
import com.RobinNotBad.BiliClient.util.UiText
import com.google.android.material.card.MaterialCardView
import com.RobinNotBad.BiliClient.ui.appearance.ColorScheme

class SettingPlayerChooseActivity : BaseActivity() {

    private var playerCurr: String = SharedPreferencesUtil.getString(SettingsKeys.PLAYER, "null")
    private lateinit var terminalPlayer: MaterialCardView
    private lateinit var mtvPlayer: MaterialCardView
    private lateinit var aliangPlayer: MaterialCardView
    private lateinit var qnChoose: MaterialCardView
    private lateinit var cardViewList: ArrayList<MaterialCardView>
    private var checkPosition: Int = -1
    private val playerList = arrayOf("null", "terminalPlayer", "mtvPlayer", "aliangPlayer")

    private var justCreate: Boolean = true


    @SuppressLint("MissingInflatedId", "SetTextI18n", "InflateParams")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        asyncInflate(R.layout.activity_setting_player_choose) { _, _ ->
            terminalPlayer = findViewById(R.id.terminalPlayer)
            mtvPlayer = findViewById(R.id.mtvPlayer)
            aliangPlayer = findViewById(R.id.aliangPlayer)
            qnChoose = findViewById(R.id.qn_choose)

            qnChoose.setOnClickListener { handleQnChoose() }

            // 26.11.06：播放器界面（按钮布局与各处设置）单独一页，从这里进。
            findViewById<View>(R.id.player_ui_choose).setOnClickListener {
                startActivity(Intent(this, SettingPlayerUiActivity::class.java))
            }

            cardViewList = ArrayList()
            cardViewList.add(terminalPlayer)
            cardViewList.add(mtvPlayer)
            cardViewList.add(aliangPlayer)

            for (i in 1 until playerList.size) {
                if (playerList[i] == playerCurr) {
                    setChecked(i - 1)
                    break
                }
            }

            setOnClick()

            // 长按「内置播放器」卡片进入内置播放器设置
            terminalPlayer.setOnLongClickListener {
                val intent = Intent()
                intent.setClass(this, SettingTerminalPlayerActivity::class.java)
                startActivity(intent)
                true
            }

            updateQn()
            justCreate = false

            val scrollView = findViewById<View>(R.id.scrollView)
            scrollView.isFocusable = true
            scrollView.isFocusableInTouchMode = true
            scrollView.requestFocus()
        }
    }

    override fun onResume() {
        super.onResume()
        updateQn()
    }

    /**
     * 刷新「分辨率」摘要。
     *
     * 显示**当前网络下的实际起播档位**（[NetworkPlaybackQuality.defaultQn]），而不是无脑读
     * [SettingsKeys.PLAY_QN]：Wi-Fi / 移动数据各自设了档位后，摘要必须跟着当前网络走，
     * 否则会出现「摘要写 720P、起播却是 1080P」这种看着像设置没生效的假象。
     */
    private fun updateQn() {
        val qnTv = findViewById<TextView>(R.id.qn_tv) ?: return
        val effective = NetworkPlaybackQuality.defaultQn(this)
        val name = SettingQualityActivity.qnMap.entries.firstOrNull { it.value == effective }?.key
            ?: UiText.of("默认")
        val hasNetworkOverride =
            SharedPreferencesUtil.getInt(SettingsKeys.PLAY_QN_WIFI, NetworkPlaybackQuality.FOLLOW_DEFAULT) > 0 ||
                SharedPreferencesUtil.getInt(SettingsKeys.PLAY_QN_MOBILE, NetworkPlaybackQuality.FOLLOW_DEFAULT) > 0
        qnTv.text = if (hasNetworkOverride) {
            getString(R.string.appearance_net_quality_effective, name)
        } else {
            name
        }
    }

    private fun handleQnChoose() {
        startActivity(Intent(this, SettingQualityActivity::class.java))
    }


    private fun setOnClick() {
        for (i in cardViewList.indices) {
            val finalI = i
            cardViewList[i].setOnClickListener {
                setChecked(finalI)
            }
        }
    }

    private fun setChecked(position: Int) {
        checkPosition = position
        for (i in cardViewList.indices) {
            if (position == i) {
                cardViewList[i].strokeColor = ColorScheme.getPrimary(this)
                cardViewList[i].strokeWidth = ToolsUtil.dp2px(1f)
            } else {
                cardViewList[i].strokeColor = ColorScheme.getBorder(this)
                cardViewList[i].strokeWidth = ToolsUtil.dp2px(0.1f)
            }
        }
        if (!justCreate) {
            when (playerList[checkPosition + 1]) {
                "terminalPlayer" -> {
                    if (playerCurr == "null")
                        startActivity(Intent(this, SettingTerminalPlayerActivity::class.java))
                }
                "mtvPlayer" -> {
                    MsgUtil.showDialog(SettingText.resolve(this, "提醒"), SettingText.resolve(this, "不再推荐使用小电视播放器，许多功能已不再支持，推荐使用内置播放器"), -1)
                }

                "aliangPlayer" -> {
                    if (Build.VERSION.SDK_INT <= 19)
                        MsgUtil.showDialog(SettingText.resolve(this, "提醒"), SettingText.resolve(this, "您的安卓版本过低，可能无法使用凉腕播放器，可以使用内置播放器"), -1)
                }
            }
            playerCurr = playerList[checkPosition + 1]
            SharedPreferencesUtil.putString(SettingsKeys.PLAYER, playerCurr)
        }
    }
}