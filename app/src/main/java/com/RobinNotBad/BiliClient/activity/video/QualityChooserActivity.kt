package com.RobinNotBad.BiliClient.activity.video

import com.RobinNotBad.BiliClient.util.UiText

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.base.BaseActivity
import com.RobinNotBad.BiliClient.adapter.QualityChooseAdapter
import com.RobinNotBad.BiliClient.listener.OnItemClickListener
import com.RobinNotBad.BiliClient.api.PlayerApi
import com.RobinNotBad.BiliClient.api.VipApi
import com.RobinNotBad.BiliClient.model.LocalVideo
import com.RobinNotBad.BiliClient.model.PlayerData
import com.RobinNotBad.BiliClient.ui.widget.recycler.CustomLinearManager
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.Logu
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import com.RobinNotBad.BiliClient.util.TerminalContext
import java.util.Arrays

class QualityChooserActivity : BaseActivity() {

    private var qns: IntArray? = null
    private var isAudioOnlyOption = false
    private val mainHandler = Handler(Looper.getMainLooper())

    /** 与 [qns] 一一对应的显示文案，回调时用来回填「清晰度：XXX」。 */
    private var qualityLabels: ArrayList<String> = ArrayList()

    /** 只挑不下载：番剧缓存页要的是「选了哪一档」，下载由它自己入队。 */
    private var pickOnly = false

    // 强制高分辨率选项：qn值 -> 显示名称
    private val forcedQualityMap = linkedMapOf(
        120 to UiText.of("4K 超清"),
        112 to UiText.of("1080P 高码率"),
        80 to UiText.of("1080P 高清")
    )

    // 每个强制选项对应的 fnval（4048: DASH|HDR|4K|杜比全景声|杜比视界|8K|AV1）
    private val forcedQualityFnval = mapOf(
        120 to 4048,
        112 to 4048,
        80 to 4048
    )

    // 标记哪些位置是强制添加的（非API返回）
    private val forcedPositions = mutableSetOf<Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_simple_list)
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        findViewById<View>(R.id.top).setOnClickListener {
            setResult(RESULT_CANCELED)
            finish()
        }

        (findViewById<TextView>(R.id.pageName)).text = UiText.of("请选择清晰度")

        // 番剧缓存页过来的：只挑不下载，列表来自 pgc 取流接口的 accept_quality。
        // 缓存页刚打开、还没拉完剧集时拿不到采样集，这时 epid 为 0 也走这条，
        // 里面对不上采样就直接给固定四档——绝不能落到下面投稿视频那条分支上
        val bgmEpid = intent.getLongExtra(EXTRA_BGM_EPID, 0)
        pickOnly = intent.getBooleanExtra(EXTRA_PICK_ONLY, false)
        if (bgmEpid > 0 || pickOnly) {
            setupBangumiQuality(recyclerView)
            return
        }

        val aid = intent.getLongExtra("aid", 0)
        val bvid = intent.getStringExtra("bvid")

        TerminalContext.getInstance().getVideoInfoByAidOrBvId(aid, bvid).observe(this) { result ->
            result.onSuccess { videoInfo ->
                val adapter = QualityChooseAdapter(this)
                val page = intent.getIntExtra("page", 0)
                CenterThreadPool.run {
                    try {
                        val playerData = videoInfo.toPlayerData(page)
                        // 以最高画质请求，确保1080P等高清选项出现在列表中
                        playerData.qn = 127
                        PlayerApi.getVideo(playerData, true)
                        qns = playerData.qnValueList

                        val qualityList = ArrayList(Arrays.asList(*playerData.qnStrList))

                        // 如果开启了强制高分辨率选项，添加API未返回的高分辨率选项
                        val forceHighQuality = SharedPreferencesUtil.getBoolean(
                            "force_high_quality_options", true
                        )
                        forcedPositions.clear()
                        if (forceHighQuality) {
                            val existingQns = qns?.toSet() ?: emptySet()
                            for ((qn, name) in forcedQualityMap) {
                                if (qn !in existingQns) {
                                    qualityList.add("$name [强制]")
                                    forcedPositions.add(qualityList.size - 1)
                                }
                            }
                        }

                        qualityList.add(UiText.of("仅音频"))
                        isAudioOnlyOption = true

                        qualityLabels = qualityList
                        runOnUiThread { adapter.nameList = qualityList }
                    } catch (e: Exception) {
                        runOnUiThread { MsgUtil.showMsg(UiText.of("清晰度列表获取失败！")) }
                        e.printStackTrace()
                    }
                }
                adapter.onItemClickListener = OnItemClickListener { position ->
                    if (qns == null) return@OnItemClickListener

                    if (isAudioOnlyOption && position == qns!!.size + forcedPositions.size) {
                        // 仅音频选项。只挑不下载的模式下不会出现这一项（番剧只有音视频合一的 durl），
                        // 这里守卫一下，免得哪天真冒出来就悄悄替用户下了个音频
                        if (pickOnly) return@OnItemClickListener
                        CenterThreadPool.run {
                            try {
                                val playerData = videoInfo.toPlayerData(page)
                                playerData.qn = qns!![0]
                                PlayerApi.getVideoDash(playerData)

                                if (playerData.audioUrl == null || playerData.audioUrl!!.isEmpty()) {
                                    runOnUiThread { MsgUtil.showMsg(UiText.of("该视频没有可用的音频流")) }
                                    return@run
                                }

                                PlayerApi.startDownloadingAudioOnly(videoInfo, page, qns!![0], playerData.audioUrl!!)
                                mainHandler.postDelayed({ finish() }, 200)
                            } catch (e: Exception) {
                                runOnUiThread {
                                    MsgUtil.showMsg(UiText.fmt("获取音频信息失败：%1\$s", e.message))
                                    e.printStackTrace()
                                }
                            }
                        }
                    } else if (position in forcedPositions) {
                        // 强制高分辨率选项：需要先验证该分辨率是否可用
                        val forcedIndex = forcedPositions.toList().indexOf(position)
                        val forcedEntry = forcedQualityMap.entries.elementAtOrNull(forcedIndex)
                        if (forcedEntry == null) return@OnItemClickListener

                        val targetQn = forcedEntry.key
                        val fnval = forcedQualityFnval[targetQn] ?: 16
                        val qualityName = forcedEntry.value

                        if (pickOnly) {
                            finishWithPick(targetQn, qualityName)
                            return@OnItemClickListener
                        }

                        CenterThreadPool.run {
                            try {
                                // 1080P高码率(112)与4K(120)仅大会员可下载，其余强制项无需校验
                                if (targetQn >= 112) {
                                    val vipInfo = VipApi.getVipInfo()
                                    if (!vipInfo.isVip) {
                                        runOnUiThread {
                                            MsgUtil.showMsg("${qualityName}仅大会员可下载喵~")
                                        }
                                        return@run
                                    }
                                }

                                val playerData = videoInfo.toPlayerData(page)
                                playerData.qn = targetQn

                                val success = PlayerApi.tryGetVideoWithFnval(playerData, fnval)
                                if (success) {
                                    // 获取成功，使用DASH格式下载
                                    PlayerApi.startDownloading(videoInfo, page, targetQn)
                                    mainHandler.postDelayed({ finish() }, 200)
                                } else {
                                    runOnUiThread {
                                        MsgUtil.showMsg("该视频不支持${qualityName}分辨率，缓存失败")
                                    }
                                }
                            } catch (e: Exception) {
                                runOnUiThread {
                                    MsgUtil.showMsg("获取${qualityName}失败：${e.message}")
                                    e.printStackTrace()
                                }
                            }
                        }
                    } else {
                        // 正常API返回的分辨率选项
                        val qn = qns!![position]
                        if (pickOnly) {
                            finishWithPick(qn, qualityLabels.getOrNull(position))
                            return@OnItemClickListener
                        }
                        PlayerApi.startDownloading(videoInfo, page, qn)
                        mainHandler.postDelayed({ finish() }, 200)
                    }
                }

                recyclerView.layoutManager = CustomLinearManager(this)
                recyclerView.adapter = adapter
            }
        }
    }

    /**
     * 番剧清晰度列表：走 pgc 取流接口自己带回来的 accept_quality / accept_description。
     *
     * 与投稿视频的区别：番剧只有 durl（音视频合一的 mp4），没有「仅音频」也没有
     * DASH 的强制高码率档，所以这里不追加那些项；拿到列表就直接一档对一档地列出来。
     *
     * 会员集、地区限制等情况下取流可能直接失败（[PlayerApi.NoPlayUrlException]），
     * 此时退回固定四档：反正这一刻只是让用户先挑一个值，真正下载时会再取一次流，
     * 到那时失败会按失败原因显示在下载列表里。
     */
    private fun setupBangumiQuality(recyclerView: RecyclerView) {
        val adapter = QualityChooseAdapter(this)
        CenterThreadPool.run {
            var labels = ArrayList<String>()
            var values = intArrayOf()
            try {
                val epid = intent.getLongExtra(EXTRA_BGM_EPID, 0)
                if (epid <= 0) throw IllegalStateException("没有可采样的剧集，直接用固定四档")
                val data = PlayerData(PlayerData.TYPE_BANGUMI)
                data.aid = intent.getLongExtra(EXTRA_BGM_AID, 0)
                data.cid = intent.getLongExtra(EXTRA_BGM_CID, 0)
                data.epid = epid
                data.seasonId = intent.getLongExtra(EXTRA_BGM_SEASON_ID, 0)
                data.seasonType = intent.getIntExtra(EXTRA_BGM_SEASON_TYPE, 0).takeIf { it > 0 } ?: 1
                data.qn = 127
                PlayerApi.getBangumi(data)
                val names = data.qnStrList
                val valueList = data.qnValueList
                if (names == null || valueList == null || valueList.isEmpty()) {
                    throw IllegalStateException("番剧清晰度列表为空")
                }
                labels = ArrayList(Arrays.asList(*names))
                values = valueList
            } catch (e: Exception) {
                labels = ArrayList(
                    Arrays.asList(
                        getString(R.string.cache_quality_1080p),
                        getString(R.string.cache_quality_720p),
                        getString(R.string.cache_quality_480p),
                        getString(R.string.cache_quality_360p)
                    )
                )
                values = intArrayOf(80, 64, 32, 16)
                // 会员集 / 地区限制时取流会直接失败：这时退回四档就够用了，别弹红字吓人，
                // 真正下载时会再取一次流，失败原因那时显示在下载列表里
                Logu.e("QualityChooser", "番剧清晰度获取失败，回落固定四档：" + e.message)
            }
            qns = values
            qualityLabels = labels
            runOnUiThread { adapter.nameList = labels }
        }
        adapter.onItemClickListener = OnItemClickListener { position ->
            val qn = qns?.getOrNull(position) ?: return@OnItemClickListener
            finishWithPick(qn, qualityLabels.getOrNull(position))
        }
        recyclerView.layoutManager = CustomLinearManager(this)
        recyclerView.adapter = adapter
    }

    /** 只挑不下载：把选中的档位回给调用方（番剧缓存页靠它回填「清晰度：XXX」）。 */
    private fun finishWithPick(qn: Int, label: CharSequence?) {
        val text = label?.toString()?.takeIf { it.isNotEmpty() } ?: LocalVideo.getQualityLabel(qn)
        setResult(
            RESULT_OK,
            Intent().putExtra(EXTRA_PICK_QN, qn).putExtra(EXTRA_PICK_LABEL, text)
        )
        finish()
    }

    companion object {
        /** 只挑不下载模式：点任意档位只回 qn，不触发任何下载。 */
        const val EXTRA_PICK_ONLY = "pick_only"

        /** 当前选中的清晰度（进页面时传进去、选完回传给调用方）。 */
        const val EXTRA_PICK_QN = "pick_qn"
        const val EXTRA_PICK_LABEL = "pick_label"

        /** 番剧模式：给了 epid 就走 pgc 取流拿清晰度列表，且永不下载。 */
        const val EXTRA_BGM_AID = "bgm_aid"
        const val EXTRA_BGM_CID = "bgm_cid"
        const val EXTRA_BGM_EPID = "bgm_epid"
        const val EXTRA_BGM_SEASON_ID = "bgm_season_id"
        const val EXTRA_BGM_SEASON_TYPE = "bgm_season_type"
    }
}
