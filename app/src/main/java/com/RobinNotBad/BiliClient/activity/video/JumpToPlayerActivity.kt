package com.RobinNotBad.BiliClient.activity.video

import com.RobinNotBad.BiliClient.util.UiText

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
import com.RobinNotBad.BiliClient.util.AccountStateStore
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.SettingsKeys
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import org.json.JSONException
import java.io.IOException

class JumpToPlayerActivity : BaseActivity() {
    private var title: String? = null
    private lateinit var textView: TextView

    private var playerData: PlayerData? = null

    private var download: Int = 0

    private val launcher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { o ->
        // 播放器一返回就立刻关掉等待页，不再让"上报进度"这个网络请求把关闭动作挡住。
        // 旧实现是「先上报、再 finish」且整体包在 if (RESULT_OK) 里，结果是：
        //  · 上报慢时页面要挂着等网络；
        //  · 外部播放器（MTV/Alang）不 setResult、或设置里没选播放器时跳的「播放器选择页」
        //    返回 RESULT_CANCELED —— 这两种情况连 finish() 都不会执行，页面永远出不去。
        val resultIntent = o.data
        val isOk = o.resultCode == RESULT_OK && resultIntent != null
        val fallbackProgress = playerData?.progress ?: 0
        val fallbackCid = playerData?.cid ?: 0L
        // 外部播放器（MTV/Alang）不会回传 RESULT_OK，这时用进入播放前已加载的续播进度兜底：
        // 旧实现一律取 0，导致外部播放器场景下进度整个丢失（普通视频被详情页那次上报掩盖了问题，番剧就彻底没了）
        val progress = if (isOk) resultIntent!!.getIntExtra("progress", fallbackProgress) else fallbackProgress
        // 播放器内可能切换过分P：最终观看的 cid 以播放器回传为准，不能沿用进入时的旧 cid，
        // 否则会拿新P的进度去覆盖旧P的记录，把正确的续播位置冲掉
        val finalCid = if (isOk && resultIntent!!.hasExtra("cid")) {
            resultIntent.getLongExtra("cid", fallbackCid)
        } else fallbackCid
        val data = playerData
        // 内置播放器退出时已经自己上报过一次（并在结果里回传 progressReported=true），
        // 这里再报一遍就是同一个进度写两次，所以直接跳过；外部播放器不会回传该标记，照旧由本页兜底上报
        val reportedByPlayer = isOk && resultIntent!!.getBooleanExtra("progressReported", false)
        finish()
        if (!reportedByPlayer) reportProgressAsync(data, progress, finalCid)
    }

    /**
     * 后台异步上报观看进度，不阻塞页面关闭。
     * 本页此时已经 finish，这里只碰 [PlayerData] 与全局 Context，不再引用任何已销毁的 View。
     *
     * @param cid 最终观看的分P cid，由播放器回传而不是进入时的旧值
     */
    private fun reportProgressAsync(data: PlayerData?, progressMs: Int, cid: Long) {
        if (data == null || data.mid == 0L || data.aid == 0L) return
        // progress<=0 的上报零信息量且有害：服务端会把观看记录覆盖成"0 进度"，
        // 续播位置因此失效。旧实现只在 progressMs<=0 时早退，播了不到 1 秒（如 500ms）
        // 会被截成 0 秒发出去，这里改成按秒判断。
        val progressSec = (progressMs / 1000).toLong()
        if (progressSec <= 0) return
        CenterThreadPool.run {
            try {
                // 番剧必须走专用心跳接口：x/v2/history/report 只有 aid/cid 两个维度，
                // 拿它上报番剧不会被记成番剧记录（观看历史里不出现，续播位置也拿不到）。
                // 缺 seasonId 时不冒险走心跳（sid=0 会被服务端判参数错误 -400），退回投稿视频的上报方式。
                if (data.epid != 0L && data.seasonId != 0L) {
                    HistoryApi.reportHistoryPgc(data.aid, cid, data.epid, data.seasonId, data.seasonType, progressSec)
                } else {
                    HistoryApi.reportHistory(data.aid, cid, progressSec)
                }
            } catch (e: Exception) {
                MsgUtil.err(UiText.of("进度上报："), e)
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

        // 本页在 Manifest 里是 exported="true"，脏 Intent 完全可能不带 data，
        // 旧实现直接 playerData!!.title 会 NPE 崩溃。缺数据时只提示并允许点击退出，不再往下走。
        val data = playerData
        if (data == null) {
            setClickExit(UiText.of("视频信息缺失，无法播放\n（点击返回）"))
            return
        }

        title = data.title

        download = intent.getIntExtra("download", 0)

        data.qn = if (data.qn != -1) data.qn else SharedPreferencesUtil.getInt(SettingsKeys.PLAY_QN, 16)
        // 下载不受清晰度权限影响（可用性由服务端决定）；播放要把请求档位夹到账号权限内，
        // 否则超权请求会被服务端降级，反而可能拿到比权限内最高档更低的流
        if (download == 0) data.qn = AccountStateStore.clampQuality(data.qn)

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
                setClickExit(UiText.of("网络错误！\n请检查你的网络连接是否正常"))
            } catch (e: JSONException) {
                setClickExit("视频获取失败！\n可能的原因：\n1.本视频仅大会员可播放\n2.视频获取接口失效\n\n清除应用数据也许可以解决" + e.message)
                e.printStackTrace()
            } catch (e: ActivityNotFoundException) {
                setClickExit(UiText.of("跳转失败！\n请安装对应的播放器\n或在设置中选择正确的播放器\n或将哔哩终端和播放器同时更新到最新版本"))
                e.printStackTrace()
            }
        }
    }

    private fun jump() {
        if (isDestroyed) return
        if (download == 0) {
            val data = playerData!!
            val intent = PlayerApi.jumpToPlayer(data)
            // PlayerApi.jumpToPlayer 只把 url/aid/cid/mid/progress 这些放进了 Intent，没有番剧维度
            // （见 api/PlayerApi.java:426-451）。内置播放器的进度上报要靠 epid/seasonId/seasonType
            // 才能走对番剧心跳接口，缺了这三个 extra 会退化成投稿上报，观看记录与续播进度都不会更新。
            // 外部播放器与「播放器选择页」会忽略多余的 extra，所以在这里统一补上，不改 PlayerApi。
            intent.putExtra("epid", data.epid)
            intent.putExtra("seasonId", data.seasonId)
            intent.putExtra("seasonType", data.seasonType)
            launcher.launch(intent)
            setClickExit(UiText.of("等待退出播放后上报进度\n（点击跳过）"))
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