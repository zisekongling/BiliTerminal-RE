package com.RobinNotBad.BiliClient.activity.live

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.ImageViewerActivity
import com.RobinNotBad.BiliClient.activity.base.BaseActivity
import com.RobinNotBad.BiliClient.activity.settings.SettingPlayerChooseActivity
import com.RobinNotBad.BiliClient.adapter.user.UpListAdapter
import com.RobinNotBad.BiliClient.adapter.video.MediaEpisodeAdapter
import com.RobinNotBad.BiliClient.api.LiveApi
import com.RobinNotBad.BiliClient.api.PlayerApi
import com.RobinNotBad.BiliClient.model.Bangumi
import com.RobinNotBad.BiliClient.model.LivePlayInfo
import com.RobinNotBad.BiliClient.model.LiveRoom
import com.RobinNotBad.BiliClient.model.PlayerData
import com.RobinNotBad.BiliClient.model.UserInfo
import com.RobinNotBad.BiliClient.ui.widget.recycler.CustomLinearManager
import com.RobinNotBad.BiliClient.util.AnimationUtils
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.GlideUtil
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import com.RobinNotBad.BiliClient.util.StringUtil
import com.RobinNotBad.BiliClient.util.TerminalContext
import com.RobinNotBad.BiliClient.util.ToolsUtil
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions
import com.google.android.material.button.MaterialButton
import java.util.concurrent.TimeUnit

class LiveInfoActivity : BaseActivity() {
    private var room_id: Long = 0
    private var room: LiveRoom? = null
    private var desc_expand: Boolean = false
    private var tags_expand: Boolean = false

    private lateinit var host_list: RecyclerView
    private var selectedHost: Int = 0
    private var hostAdapter: MediaEpisodeAdapter? = null
    private var playInfo: LivePlayInfo? = null

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        room_id = intent.getLongExtra("room_id", 0)
        if (room_id == 0L) {
            finish()
            return
        }

        asyncInflate(R.layout.activity_live_info) { layoutView, id ->
            val loading = findViewById<ImageView>(R.id.loading)
            val scrollView = findViewById<View>(R.id.scrollView)
            val cover = findViewById<ImageView>(R.id.img_cover)
            val title = findViewById<TextView>(R.id.text_title)
            val up_recyclerView = findViewById<RecyclerView>(R.id.up_recyclerView)
            val viewsCount = findViewById<TextView>(R.id.viewCount)
            val play = findViewById<MaterialButton>(R.id.play)
            val durationText = findViewById<TextView>(R.id.durationText)
            val idText = findViewById<TextView>(R.id.idText)
            val tags = findViewById<TextView>(R.id.tags)
            val description = findViewById<TextView>(R.id.description)
            val areaText = findViewById<TextView>(R.id.areaText)
            val attentionText = findViewById<TextView>(R.id.attentionText)
            val liveStatusText = findViewById<TextView>(R.id.liveStatusText)
            host_list = findViewById(R.id.host_list)
            val quality_list = findViewById<RecyclerView>(R.id.quality_list)

            AnimationUtils.crossFade(loading, scrollView)
            TerminalContext.getInstance().getLiveInfoByRoomId(room_id).observe(this) { liveInfoResult ->
                liveInfoResult.onSuccess { liveInfo ->
                    room = liveInfo.liveRoom
                    val userInfo = liveInfo.userInfo
                    playInfo = liveInfo.livePlayInfo

                    Glide.with(this).asDrawable().load(GlideUtil.url(room!!.user_cover)).placeholder(R.mipmap.placeholder)
                        .transition(GlideUtil.getTransitionOptions())
                        .apply(RequestOptions.bitmapTransform(RoundedCorners(ToolsUtil.dp2px(4f))).sizeMultiplier(0.85f).skipMemoryCache(true).dontAnimate())
                        .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                        .into(cover)

                    cover.setOnClickListener { startActivity(Intent(it.context, ImageViewerActivity::class.java).putExtra("imageList", ArrayList(listOf(room!!.user_cover)))) }

                    title.text = StringUtil.removeHtml(room!!.title)

                    val upList = ArrayList<UserInfo>()
                    if (userInfo != null) {
                        val displayUserInfo = UserInfo(userInfo.mid, userInfo.name, userInfo.avatar, "主播", 0, 0, 6, false, "", 0, "", 0)
                        upList.add(displayUserInfo)
                    }
                    val upListAdapter = UpListAdapter(this, upList)
                    up_recyclerView.setHasFixedSize(true)
                    up_recyclerView.layoutManager = CustomLinearManager(this)
                    up_recyclerView.adapter = upListAdapter

                    viewsCount.text = StringUtil.toWan(room!!.online.toLong()) + "人观看"
                    durationText.text = "直播开始于" + room!!.liveTime

                    idText.text = "房间号: $room_id" + (if (room!!.short_id > 0) " (短号: " + room!!.short_id + ")" else "")
                    tags.text = "标签：" + room!!.tags

                    tags.setOnClickListener {
                        if (tags_expand) tags.maxLines = 1
                        else tags.maxLines = 233
                        tags_expand = !tags_expand
                    }

                    areaText.text = "分区: " + (if (room!!.area_parent_name != null) room!!.area_parent_name + " > " else "") + (room!!.area_name ?: "")
                    attentionText.text = "关注数: " + StringUtil.toWan(room!!.attention.toLong())

                    val statusStr: String
                    when (room!!.live_status) {
                        0 -> statusStr = "未开播"
                        1 -> statusStr = "直播中"
                        2 -> statusStr = "轮播中"
                        else -> statusStr = "未知"
                    }
                    liveStatusText.text = "直播状态: $statusStr"

                    StringUtil.setCopy(idText, tags, title)

                    description.text = StringUtil.removeHtml(StringUtil.htmlToString(room!!.description))
                    description.setOnClickListener {
                        if (desc_expand) description.maxLines = 3
                        else description.maxLines = 512
                        desc_expand = !desc_expand
                    }
                    play.setOnClickListener {
                        CenterThreadPool.run {
                            try {
                                val codec = playInfo?.playUrl?.stream?.getOrNull(0)
                                    ?.format?.getOrNull(0)?.codec?.getOrNull(0)
                                if (codec == null || codec.url_info.isEmpty()) {
                                    runOnUiThread { MsgUtil.showMsg("直播已结束") }
                                    return@run
                                }
                                val urlInfo = codec.url_info[selectedHost.coerceIn(0, codec.url_info.size - 1)]
                                val play_url = urlInfo.host + codec.base_url + urlInfo.extra

                                val playerData = PlayerData(PlayerData.TYPE_LIVE)
                                playerData.videoUrl = play_url
                                playerData.title = "直播·" + room!!.title
                                playerData.aid = room_id
                                playerData.mid = SharedPreferencesUtil.getLong("mid", 0)

                                runOnUiThread {
                                    try {
                                        val player = PlayerApi.jumpToPlayer(playerData)
                                        startActivity(player)
                                    } catch (e: ActivityNotFoundException) {
                                        MsgUtil.showMsg("没有找到播放器，请检查是否安装")
                                    } catch (e: Exception) {
                                        MsgUtil.err(e)
                                    }
                                }
                            } catch (e: Exception) {
                                runOnUiThread { MsgUtil.err(e) }
                            }
                        }
                    }
                    play.setOnLongClickListener {
                        if (SharedPreferencesUtil.getString("player", "null") != "terminalPlayer")
                            MsgUtil.showMsgLong("若无法播放请更换为内置播放器")
                        val intent = Intent()
                        intent.setClass(this, SettingPlayerChooseActivity::class.java)
                        startActivity(intent)
                        true
                    }
                    val firstCodec = playInfo?.playUrl?.stream?.getOrNull(0)
                        ?.format?.getOrNull(0)?.codec?.getOrNull(0)
                    if (playInfo == null || playInfo!!.playUrl == null || firstCodec == null) {
                        MsgUtil.showMsg("直播已结束")
                        play.visibility = View.GONE
                    } else {
                        val qualityAdapter = MediaEpisodeAdapter()
                        val qualityList = ArrayList<Bangumi.Episode>()
                        qualityAdapter.setOnItemClickListener { index ->
                            hostAdapter!!.setData(ArrayList())
                            play.isEnabled = false
                            CenterThreadPool.run {
                                try {
                                    playInfo = LiveApi.getRoomPlayInfo(room_id, qualityList[index].id.toInt())
                                    runOnUiThread {
                                        refresh_host_list()
                                        play.isEnabled = true
                                    }
                                } catch (e: Exception) {
                                    runOnUiThread { MsgUtil.err(e) }
                                }
                            }
                        }
                        for (entry in LiveApi.QualityMap.entries) {
                            val episode = Bangumi.Episode()
                            episode.id = entry.value.toLong()
                            episode.title = entry.key
                            qualityList.add(episode)
                        }
                        qualityAdapter.setData(qualityList)
                        quality_list.layoutManager = CustomLinearManager(this, LinearLayoutManager.HORIZONTAL, false)
                        quality_list.adapter = qualityAdapter
                        qualityAdapter.selectedItemIndex = 0

                        hostAdapter = MediaEpisodeAdapter()
                        hostAdapter!!.setOnItemClickListener { i -> selectedHost = i }
                        hostAdapter!!.setData(ArrayList())
                        host_list.layoutManager = CustomLinearManager(this, LinearLayoutManager.HORIZONTAL, false)
                        runOnUiThread { host_list.adapter = hostAdapter }
                        refresh_host_list()

                        runOnUiThread {
                            scrollView.isFocusable = true
                            scrollView.isFocusableInTouchMode = true
                            scrollView.requestFocus()
                        }
                    }
                    if (SharedPreferencesUtil.getString("player", "null") != "terminalPlayer")
                        MsgUtil.showMsgLong("直播可能只有内置播放器可以正常播放")

                }.onFailure { e ->
                    runOnUiThread { MsgUtil.showMsg("直播不存在") }
                    CenterThreadPool.runOnUIThreadAfter(1, TimeUnit.MINUTES) { MsgUtil.err(e) }
                    finish()
                }
            }
        }
    }

    private fun refresh_host_list() {
        val codec = playInfo?.playUrl?.stream?.getOrNull(0)
            ?.format?.getOrNull(0)?.codec?.getOrNull(0)
        val hostList = ArrayList<Bangumi.Episode>()
        if (codec != null) {
            for (i in 0 until codec.url_info.size) {
                val episode = Bangumi.Episode()
                episode.id = i.toLong()
                episode.title = "路线" + (i + 1)
                hostList.add(episode)
            }
        }
        hostAdapter!!.setData(hostList)
        selectedHost = 0
        hostAdapter!!.selectedItemIndex = 0
    }

    override fun onDestroy() {
        TerminalContext.getInstance().leaveDetailPage()
        super.onDestroy()
    }
}