package com.RobinNotBad.BiliClient.activity.video.info

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.ImageViewerActivity
import com.RobinNotBad.BiliClient.activity.settings.SettingPlayerChooseActivity
import com.RobinNotBad.BiliClient.activity.video.JumpToPlayerActivity
import com.RobinNotBad.BiliClient.adapter.video.MediaEpisodeAdapter
import com.RobinNotBad.BiliClient.api.BangumiApi
import com.RobinNotBad.BiliClient.model.Bangumi
import com.RobinNotBad.BiliClient.ui.widget.recycler.CustomLinearManager
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.GlideUtil
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy

class BangumiInfoFragment : Fragment() {
    private var mediaId: Long = 0
    private var selectedSection = 0
    private var selectedEpisode = 0
    private var dialog: Dialog? = null
    private var rootView: View? = null
    private var episodeRecyclerView: RecyclerView? = null
    private var sectionChoose: Button? = null
    private var episodeChoose: TextView? = null
    private var bangumi: Bangumi? = null

    companion object {
        @JvmStatic
        fun newInstance(mediaId: Long): BangumiInfoFragment {
            val args = Bundle()
            args.putLong("media_id", mediaId)
            val fragment = BangumiInfoFragment()
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val arguments = arguments
        if (arguments != null) {
            mediaId = arguments.getLong("media_id")
        }
        rootView = inflater.inflate(R.layout.fragment_media_info, container, false)
        return rootView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.visibility = View.GONE
        episodeRecyclerView = rootView!!.findViewById(R.id.rv_episode_list)
        CenterThreadPool
            .supplyAsyncWithLiveData { BangumiApi.getBangumi(mediaId) }
            .observe(viewLifecycleOwner) { result ->
                result.onSuccess { bangumi ->
                    this.bangumi = bangumi
                    initView()
                }.onFailure { error -> MsgUtil.err("番剧详情：", error) }
            }
    }

    @SuppressLint("SetTextI18n")
    private fun initView() {
        val imageMediaCover = rootView!!.findViewById<ImageView>(R.id.image_media_cover)
        val playButton = rootView!!.findViewById<Button>(R.id.btn_play)
        val title = rootView!!.findViewById<TextView>(R.id.text_title)
        val subtitle = rootView!!.findViewById<TextView>(R.id.text_subtitle)
        val areaType = rootView!!.findViewById<TextView>(R.id.text_area_type)
        val rating = rootView!!.findViewById<TextView>(R.id.text_rating)
        val pubTime = rootView!!.findViewById<TextView>(R.id.text_pub_time)
        val stats = rootView!!.findViewById<TextView>(R.id.text_stats)
        val styles = rootView!!.findViewById<TextView>(R.id.text_styles)
        val evaluateHeader = rootView!!.findViewById<View>(R.id.layout_evaluate_header)
        val evaluateArrow = rootView!!.findViewById<ImageView>(R.id.icon_evaluate_arrow)
        val evaluate = rootView!!.findViewById<TextView>(R.id.text_evaluate)
        val staffHeader = rootView!!.findViewById<View>(R.id.layout_staff_header)
        val staffArrow = rootView!!.findViewById<ImageView>(R.id.icon_staff_arrow)
        val staff = rootView!!.findViewById<TextView>(R.id.text_staff)
        val record = rootView!!.findViewById<TextView>(R.id.text_record)
        sectionChoose = rootView!!.findViewById(R.id.section_choose)
        episodeChoose = rootView!!.findViewById(R.id.episode_choose)
        selectedSection = 0

        rootView!!.visibility = View.GONE

        Glide.with(requireContext())
            .load(GlideUtil.url(bangumi!!.info.cover_horizontal))
            .transition(GlideUtil.getTransitionOptions())
            .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
            .placeholder(R.mipmap.placeholder)
            .into(imageMediaCover)
        imageMediaCover.setOnClickListener {
            startActivity(Intent(it.context, ImageViewerActivity::class.java).putExtra("imageList", ArrayList(listOf(bangumi!!.info.cover_horizontal))))
        }
        title.text = bangumi!!.info.title

        if (bangumi!!.info.subtitle != null && bangumi!!.info.subtitle!!.isNotEmpty()) {
            subtitle.text = bangumi!!.info.subtitle
            subtitle.visibility = View.VISIBLE
        } else {
            subtitle.visibility = View.GONE
        }

        val areaTypeText = (if (bangumi!!.info.area_name != null) bangumi!!.info.area_name else "") +
                (if (bangumi!!.info.type_name != null) " | " + bangumi!!.info.type_name else "")
        if (areaTypeText.trim().isNotEmpty()) {
            areaType.text = areaTypeText.trim()
            areaType.visibility = View.VISIBLE
        } else {
            areaType.visibility = View.GONE
        }

        if (bangumi!!.info.score > 0) {
            rating.text = String.format("评分：%.1f (%d人)", bangumi!!.info.score, bangumi!!.info.count)
            rating.visibility = View.VISIBLE
        } else {
            rating.visibility = View.GONE
        }

        if (bangumi!!.info.publish != null && bangumi!!.info.publish!!.pub_time_show != null && bangumi!!.info.publish!!.pub_time_show!!.isNotEmpty()) {
            val status = if (bangumi!!.info.publish!!.is_finish == 1) "已完结" else "连载中"
            pubTime.text = bangumi!!.info.publish!!.pub_time_show + " " + status
            pubTime.visibility = View.VISIBLE
        } else {
            pubTime.visibility = View.GONE
        }

        if (bangumi!!.info.stat != null) {
            val statBuilder = StringBuilder()
            if (bangumi!!.info.stat!!.views > 0) {
                statBuilder.append("播放：").append(formatNumber(bangumi!!.info.stat!!.views))
            }
            if (bangumi!!.info.stat!!.favorites > 0) {
                if (statBuilder.isNotEmpty()) statBuilder.append(" ")
                statBuilder.append("收藏：").append(formatNumber(bangumi!!.info.stat!!.favorites))
            }
            if (bangumi!!.info.stat!!.series_follow > 0) {
                if (statBuilder.isNotEmpty()) statBuilder.append(" ")
                statBuilder.append("追番：").append(formatNumber(bangumi!!.info.stat!!.series_follow))
            }
            if (statBuilder.isNotEmpty()) {
                stats.text = statBuilder.toString()
                stats.visibility = View.VISIBLE
            } else {
                stats.visibility = View.GONE
            }
        } else {
            stats.visibility = View.GONE
        }

        if (bangumi!!.info.styles != null && bangumi!!.info.styles!!.isNotEmpty()) {
            val styleText = "标签：" + bangumi!!.info.styles!!.joinToString(" ")
            styles.text = styleText
            styles.visibility = View.VISIBLE
        } else {
            styles.visibility = View.GONE
        }

        if (bangumi!!.info.evaluate != null && bangumi!!.info.evaluate!!.trim().isNotEmpty()) {
            evaluate.text = bangumi!!.info.evaluate!!.trim()
            evaluateHeader.visibility = View.VISIBLE
            evaluate.visibility = View.GONE
            evaluateHeader.setOnClickListener {
                val isExpanded = evaluate.visibility == View.VISIBLE
                evaluate.visibility = if (isExpanded) View.GONE else View.VISIBLE
                evaluateArrow.animate().rotation(if (isExpanded) 0f else 180f).setDuration(200).start()
            }
        } else {
            evaluateHeader.visibility = View.GONE
            evaluate.visibility = View.GONE
        }

        if (bangumi!!.info.staff != null && bangumi!!.info.staff!!.trim().isNotEmpty()) {
            staff.text = bangumi!!.info.staff!!.trim()
            staffHeader.visibility = View.VISIBLE
            staff.visibility = View.GONE
            staffHeader.setOnClickListener {
                val isExpanded = staff.visibility == View.VISIBLE
                staff.visibility = if (isExpanded) View.GONE else View.VISIBLE
                staffArrow.animate().rotation(if (isExpanded) 0f else 180f).setDuration(200).start()
            }
        } else {
            staffHeader.visibility = View.GONE
            staff.visibility = View.GONE
        }

        if (bangumi!!.info.record != null && bangumi!!.info.record!!.trim().isNotEmpty()) {
            record.text = "备案号：" + bangumi!!.info.record!!.trim()
            record.visibility = View.VISIBLE
        } else {
            record.visibility = View.GONE
        }

        val adapter = MediaEpisodeAdapter()

        adapter.setOnItemClickListener { index ->
            selectedEpisode = index
            refreshReplies()
        }

        val indexShow = rootView!!.findViewById<TextView>(R.id.indexShow)
        indexShow.text = bangumi!!.info.indexShow

        if (bangumi!!.sectionList.isEmpty()) {
            sectionChoose!!.text = "敬请期待"
            playButton.visibility = View.GONE
            rootView!!.findViewById<View>(R.id.episodes).visibility = View.GONE
            val activity = requireActivity()
            if (activity is VideoInfoActivity) {
                activity.replyFragment?.setRefreshing(false)
            }
            return
        }

        sectionChoose!!.text = bangumi!!.sectionList[0].title + " 点击切换"
        sectionChoose!!.setOnClickListener { getSectionChooseDialog().show() }
        episodeChoose!!.setOnClickListener { getEposideChooseDialog().show() }

        adapter.setData(bangumi!!.sectionList[0].episodeList)
        episodeRecyclerView!!.layoutManager = CustomLinearManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        episodeRecyclerView!!.adapter = adapter

        playButton.setOnClickListener {
            val episode = bangumi!!.sectionList[selectedSection].episodeList[selectedEpisode]
            Glide.get(requireContext()).clearMemory()
            val intent = Intent(it.context, JumpToPlayerActivity::class.java)
            intent.putExtra("data", episode.toPlayerData())
            startActivity(intent)
        }
        playButton.setOnLongClickListener {
            val intent = Intent(it.context, SettingPlayerChooseActivity::class.java)
            startActivity(intent)
            true
        }
        onFinishLoad()

        refreshReplies()
    }

    @SuppressLint("SetTextI18n")
    private fun getSectionChooseDialog(): Dialog {
        val choices = Array(bangumi!!.sectionList.size) { i -> bangumi!!.sectionList[i].title }

        val builder = AlertDialog.Builder(requireContext())
        builder.setSingleChoiceItems(choices, selectedSection) { dialog, which ->
            selectedSection = which
            selectedEpisode = 0

            refreshReplies()
            val section = bangumi!!.sectionList[which]
            sectionChoose!!.text = section.title + " 点击切换"
            val adapter = episodeRecyclerView!!.adapter as MediaEpisodeAdapter
            adapter.setData(bangumi!!.sectionList[which].episodeList)
            episodeRecyclerView!!.scrollToPosition(0)
            episodeChoose!!.setOnClickListener { getEposideChooseDialog().show() }
            dialog.dismiss()
        }
        dialog = builder.create()

        return dialog!!
    }

    private fun getEposideChooseDialog(): Dialog {
        val episodeList = bangumi!!.sectionList[selectedSection].episodeList

        val choices = Array(episodeList.size) { i ->
            val episode = episodeList[i]
            episode.title + "." + episode.title_long
        }

        val builder = AlertDialog.Builder(requireContext())
        builder.setSingleChoiceItems(choices, selectedEpisode) { dialog, which ->
            selectedEpisode = which
            refreshReplies()

            val adapter = episodeRecyclerView!!.adapter as MediaEpisodeAdapter
            adapter.selectedItemIndex = which
            episodeRecyclerView!!.scrollToPosition(which)
            dialog.dismiss()
        }
        dialog = builder.create()

        return dialog!!
    }

    private fun refreshReplies() {
        val activity = activity
        if (activity is VideoInfoActivity) {
            activity.setCurrentAid(bangumi!!.sectionList[selectedSection].episodeList[selectedEpisode].aid)
        }
    }

    fun onFinishLoad() {
        try {
            val activity = requireActivity()
            if (activity is VideoInfoActivity) {
                activity.crossFade(view)
            }
        } catch (ignored: Exception) {
        }
    }

    private fun formatNumber(num: Int): String {
        return when {
            num >= 100000000 -> String.format("%.1f亿", num / 100000000.0)
            num >= 10000 -> String.format("%.1f万", num / 10000.0)
            else -> num.toString()
        }
    }
}