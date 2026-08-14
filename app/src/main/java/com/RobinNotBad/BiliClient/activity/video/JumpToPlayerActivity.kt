package com.RobinNotBad.BiliClient.activity.video

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.DownloadActivity
import com.RobinNotBad.BiliClient.activity.base.BaseActivity
import com.RobinNotBad.BiliClient.api.HistoryApi
import com.RobinNotBad.BiliClient.api.PlayerApi
import com.RobinNotBad.BiliClient.model.PlayerData
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.Logu
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import org.json.JSONException
import java.io.IOException

class JumpToPlayerActivity : BaseActivity() {
    private var title: String? = null
    private lateinit var textView: TextView

    private var playerData: PlayerData? = null

    private var download: Int = 0

    private val launcher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { o ->
        val code = o.resultCode
        val result = o.data
        Logu.d("进度回调", "onActivityResult")
        if (code == RESULT_OK && result != null) {
            val progress = result.getIntExtra("progress", 0)
            Logu.d("进度回调", progress.toString())

            CenterThreadPool.run {
                if (playerData!!.mid != 0L && playerData!!.aid != 0L) try {
                    HistoryApi.reportHistory(playerData!!.aid, playerData!!.cid, (progress / 1000).toLong())
                } catch (e: Exception) {
                    MsgUtil.err("进度上报：", e)
                }
                finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player_jump)

        textView = findViewById(R.id.text_title)

        val intent = intent
        Log.e("debug-哔哩终端-跳转页", "已接收数据")

        playerData = intent.getParcelableExtra("data")

        title = playerData!!.title

        download = intent.getIntExtra("download", 0)

        playerData!!.qn = if (playerData!!.qn != -1) playerData!!.qn else SharedPreferencesUtil.getInt("play_qn", 16)

        requestVideo()
    }

    @SuppressLint("SetTextI18n")
    private fun requestVideo() {
        CenterThreadPool.run {
            try {
                if (playerData!!.isBangumi) PlayerApi.getBangumi(playerData!!)
                else PlayerApi.getVideo(playerData!!, download != 0)

                Logu.d("history", playerData!!.progress.toString())
                jump()
            } catch (e: IOException) {
                setClickExit("网络错误！\n请检查你的网络连接是否正常")
            } catch (e: JSONException) {
                setClickExit("视频获取失败！\n可能的原因：\n1.本视频仅大会员可播放\n2.视频获取接口失效\n\n清除应用数据也许可以解决" + e.message)
                e.printStackTrace()
            } catch (e: ActivityNotFoundException) {
                setClickExit("跳转失败！\n请安装对应的播放器\n或在设置中选择正确的播放器\n或将哔哩终端和播放器同时更新到最新版本")
                e.printStackTrace()
            }
        }
    }

    private fun jump() {
        if (isDestroyed) return
        if (download == 0) {
            val intent = PlayerApi.jumpToPlayer(playerData!!)
            launcher.launch(intent)
            setClickExit("等待退出播放后上报进度\n（点击跳过）")
        } else {
            val intent = Intent()
            intent.setClass(this, DownloadActivity::class.java)
            intent.putExtra("type", download)
            intent.putExtra("link", playerData!!.videoUrl)
            intent.putExtra("danmaku", playerData!!.danmakuUrl)
            intent.putExtra("title", title)
            intent.putExtra("cover", getIntent().getStringExtra("cover"))
            if (download == 2)
                intent.putExtra("parent_title", getIntent().getStringExtra("parent_title"))
            startActivity(intent)
            finish()
        }
    }

    override fun onBackPressed() {
        finish()
    }

    private fun setClickExit(reason: String) {
        runOnUiThread {
            textView.text = reason
            textView.setOnClickListener { finish() }
        }
    }
}