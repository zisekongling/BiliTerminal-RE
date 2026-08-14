package com.RobinNotBad.BiliClient.activity.settings

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.base.BaseActivity
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import com.RobinNotBad.BiliClient.util.ToolsUtil
import com.google.android.material.card.MaterialCardView
import com.RobinNotBad.BiliClient.ui.theme.ThemeManager

class SettingPlayerChooseActivity : BaseActivity() {

    private var playerCurr: String = SharedPreferencesUtil.getString("player", "null")
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

    private fun updateQn() {
        if (findViewById<TextView>(R.id.qn_tv) != null) {
            val savedVal = SharedPreferencesUtil.getInt("play_qn", 16)
            for (entry in SettingQualityActivity.qnMap.entries) {
                if (entry.value == savedVal) {
                    (findViewById<TextView>(R.id.qn_tv)).text = entry.key
                    break
                }
            }
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
                Log.e("debug", "点击了$finalI")
            }
        }
    }

    private fun setChecked(position: Int) {
        checkPosition = position
        for (i in cardViewList.indices) {
            if (position == i) {
                cardViewList[i].strokeColor = ThemeManager.getPrimary(this)
                cardViewList[i].strokeWidth = ToolsUtil.dp2px(1f)
            } else {
                cardViewList[i].strokeColor = ThemeManager.getBorder(this)
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
                    MsgUtil.showDialog("提醒", "不再推荐使用小电视播放器，许多功能已不再支持，推荐使用内置播放器", -1)
                }

                "aliangPlayer" -> {
                    if (Build.VERSION.SDK_INT <= 19)
                        MsgUtil.showDialog("提醒", "您的安卓版本过低，可能无法使用凉腕播放器，可以使用内置播放器", -1)
                }
            }
            playerCurr = playerList[checkPosition + 1]
            SharedPreferencesUtil.putString("player", playerCurr)
        }
    }
}