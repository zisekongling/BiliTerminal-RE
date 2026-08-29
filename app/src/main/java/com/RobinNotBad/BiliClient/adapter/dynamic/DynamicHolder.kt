package com.RobinNotBad.BiliClient.adapter.dynamic

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.RobinNotBad.BiliClient.BiliTerminal
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.ImageViewerActivity
import com.RobinNotBad.BiliClient.activity.base.BaseActivity
import com.RobinNotBad.BiliClient.activity.dynamic.send.SendDynamicActivity
import com.RobinNotBad.BiliClient.activity.user.info.UserInfoActivity
import com.RobinNotBad.BiliClient.adapter.article.ArticleCardHolder
import com.RobinNotBad.BiliClient.adapter.video.VideoCardHolder
import com.RobinNotBad.BiliClient.api.DynamicApi
import com.RobinNotBad.BiliClient.model.ArticleCard
import com.RobinNotBad.BiliClient.model.Dynamic
import com.RobinNotBad.BiliClient.model.LiveRoom
import com.RobinNotBad.BiliClient.model.VoteInfo
import com.RobinNotBad.BiliClient.model.VoteOption
import com.RobinNotBad.BiliClient.model.VideoCard
import com.RobinNotBad.BiliClient.api.VoteApi
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.GlideUtil
import com.RobinNotBad.BiliClient.util.Logu
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.StringUtil
import com.RobinNotBad.BiliClient.util.TerminalContext
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestOptions
import java.io.IOException

class DynamicHolder(itemView: View, val mActivity: BaseActivity, val isChild: Boolean) :
    RecyclerView.ViewHolder(itemView) {

    companion object {
        const val GO_TO_INFO_REQUEST = 71

        @JvmStatic
        fun removeDynamicFromList(
            dynamicList: List<Dynamic>, finalPosition: Int,
            adapter: RecyclerView.Adapter<RecyclerView.ViewHolder>
        ) {
            removeDynamicFromList(dynamicList, finalPosition, adapter, false)
        }

        @JvmStatic
        fun removeDynamicFromList(
            dynamicList: List<Dynamic>, finalPosition: Int,
            adapter: RecyclerView.Adapter<RecyclerView.ViewHolder>, showRecentUp: Boolean
        ) {
            val mutableList = dynamicList as MutableList<Dynamic>
            mutableList.removeAt(finalPosition)
            val offset = if (showRecentUp) 2 else 1
            adapter.notifyItemRemoved(finalPosition + offset)
            adapter.notifyItemRangeChanged(finalPosition + offset, dynamicList.size - finalPosition)
        }

        @JvmStatic
        fun getDeleteListener(
            dynamicActivity: Activity, dynamicList: List<Dynamic>,
            finalPosition: Int, adapter: RecyclerView.Adapter<RecyclerView.ViewHolder>
        ): View.OnLongClickListener {
            return getDeleteListener(dynamicActivity, dynamicList, finalPosition, adapter, false)
        }

        @JvmStatic
        fun getDeleteListener(
            dynamicActivity: Activity, dynamicList: List<Dynamic>,
            finalPosition: Int, adapter: RecyclerView.Adapter<RecyclerView.ViewHolder>, showRecentUp: Boolean
        ): View.OnLongClickListener {
            return object : View.OnLongClickListener {
                private var longClickPosition = -1
                private var longClickTime = -1L

                override fun onLongClick(view: View): Boolean {
                    if (dynamicList[finalPosition].canDelete) {
                        val currentTime = System.currentTimeMillis()
                        if (longClickPosition == finalPosition && currentTime - longClickTime < 10000) {
                            CenterThreadPool.run {
                                try {
                                    val result = DynamicApi.deleteDynamic(dynamicList[finalPosition].dynamicId)
                                    if (result == 0) {
                                        val mutableList = dynamicList as MutableList<Dynamic>
                                        mutableList.removeAt(finalPosition)
                                        dynamicActivity.runOnUiThread {
                                            val offset = if (showRecentUp) 2 else 1
                                            adapter.notifyItemRemoved(finalPosition + offset)
                                            adapter.notifyItemRangeChanged(
                                                finalPosition + offset,
                                                dynamicList.size - finalPosition
                                            )
                                            longClickPosition = -1
                                            MsgUtil.showMsg("删除成功~")
                                        }
                                    } else {
                                        var msg = "操作失败：" + result
                                        when (result) {
                                            500404 -> msg = "已经删除过了哦~"
                                            500406 -> msg = "不是自己的动态！"
                                        }
                                        val finalMsg = msg
                                        dynamicActivity.runOnUiThread { MsgUtil.showMsg(finalMsg) }
                                    }
                                } catch (e: IOException) {
                                    dynamicActivity.runOnUiThread { MsgUtil.err(e) }
                                }
                            }
                        } else {
                            longClickPosition = finalPosition
                            longClickTime = currentTime
                            MsgUtil.showMsg("再次长按删除")
                        }
                    }
                    return true
                }
            }
        }

        @JvmStatic
        fun getDeleteListener(dynamicActivity: Activity, dynamic: Dynamic): View.OnLongClickListener {
            return object : View.OnLongClickListener {
                private var longClickTime = -1L

                override fun onLongClick(view: View): Boolean {
                    if (dynamic.canDelete) {
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - longClickTime < 10000) {
                            CenterThreadPool.run {
                                try {
                                    val result = DynamicApi.deleteDynamic(dynamic.dynamicId)
                                    if (result == 0) {
                                        dynamicActivity.runOnUiThread {
                                            dynamicActivity.setResult(
                                                Activity.RESULT_OK,
                                                if (dynamicActivity.intent.extras != null)
                                                    Intent().putExtras(dynamicActivity.intent.extras!!)
                                                else
                                                    Intent()
                                            )
                                            dynamicActivity.finish()
                                            MsgUtil.showMsg("删除成功~")
                                        }
                                    } else {
                                        var msg = "操作失败：" + result
                                        when (result) {
                                            500404 -> msg = "已经删除过了哦~"
                                            500406 -> msg = "不是自己的动态！"
                                        }
                                        val finalMsg = msg
                                        dynamicActivity.runOnUiThread { MsgUtil.showMsg(finalMsg) }
                                    }
                                } catch (e: IOException) {
                                    dynamicActivity.runOnUiThread { MsgUtil.err(e) }
                                }
                            }
                        } else {
                            longClickTime = currentTime
                            MsgUtil.showMsg("再次长按删除")
                        }
                    }
                    return true
                }
            }
        }
    }

    val username: TextView
    val content: TextView
    val title: TextView
    var pubdate: TextView? = null
    val avatar: ImageView
    val extraCard: LinearLayout
    val cell_dynamic_video: View
    val cell_dynamic_image: View
    val cell_dynamic_article: View
    var item_dynamic_share: TextView? = null
    var item_dynamic_delete: TextView? = null
    var likeCount: TextView? = null
    lateinit var cell_dynamic_child: View
    var relayDynamicLauncher: ActivityResultLauncher<Intent>? = null
    var childDynamicHolder: DynamicHolder? = null
    private var videoCardHolder: VideoCardHolder? = null
    private var articleCardHolder: ArticleCardHolder? = null
    private var lastAvatarUrl: String? = null
    private var lastImageUrl: String? = null

    // 投票相关视图
    var cell_dynamic_vote: View? = null
    var voteTitle: TextView? = null
    var voteOptionsContainer: LinearLayout? = null
    var voteJoinNum: TextView? = null
    var voteStatus: TextView? = null
    private var currentVoteInfo: VoteInfo? = null

    init {
        if (isChild) {
            username = itemView.findViewById(R.id.child_username)
            content = itemView.findViewById(R.id.child_content)
            avatar = itemView.findViewById(R.id.child_avatar)
            title = itemView.findViewById(R.id.child_title)
            extraCard = itemView.findViewById(R.id.child_extraCard)
            this.cell_dynamic_video = extraCard.findViewById(R.id.dynamic_video_child)
            this.cell_dynamic_article = extraCard.findViewById(R.id.dynamic_article_child)
            this.cell_dynamic_image = extraCard.findViewById(R.id.dynamic_image_child)
            this.cell_dynamic_child = itemView
            // 投票视图（子布局）
            val voteView = extraCard.findViewById<View>(R.id.dynamic_vote_child)
            this.cell_dynamic_vote = voteView
            if (voteView != null) {
                this.voteTitle = voteView.findViewById(R.id.vote_title)
                this.voteOptionsContainer = voteView.findViewById(R.id.vote_options_container)
                this.voteJoinNum = voteView.findViewById(R.id.vote_join_num)
                this.voteStatus = voteView.findViewById(R.id.vote_status)
            }
        } else {
            username = itemView.findViewById(R.id.username)
            pubdate = itemView.findViewById(R.id.pubdate)
            content = itemView.findViewById(R.id.content)
            avatar = itemView.findViewById(R.id.avatar)
            title = itemView.findViewById(R.id.title)
            extraCard = itemView.findViewById(R.id.extraCard)
            item_dynamic_share = itemView.findViewById(R.id.item_dynamic_share)
            likeCount = itemView.findViewById(R.id.likes)
            item_dynamic_delete = itemView.findViewById(R.id.item_dynamic_delete)
            relayDynamicLauncher = mActivity.relayDynamicLauncher
            this.cell_dynamic_child = extraCard.findViewById(R.id.dynamic_child)
            this.cell_dynamic_video = extraCard.findViewById(R.id.dynamic_video_extra)
            this.cell_dynamic_article = extraCard.findViewById(R.id.dynamic_article_extra)
            this.cell_dynamic_image = extraCard.findViewById(R.id.dynamic_image_extra)
            // 投票视图（父布局）
            val voteView = extraCard.findViewById<View>(R.id.dynamic_vote_extra)
            this.cell_dynamic_vote = voteView
            if (voteView != null) {
                this.voteTitle = voteView.findViewById(R.id.vote_title)
                this.voteOptionsContainer = voteView.findViewById(R.id.vote_options_container)
                this.voteJoinNum = voteView.findViewById(R.id.vote_join_num)
                this.voteStatus = voteView.findViewById(R.id.vote_status)
            }
        }
    }

    /**
     * 渲染投票卡片
     */
    @SuppressLint("SetTextI18n")
    private fun showVoteCard(context: Context, voteInfo: VoteInfo) {
        currentVoteInfo = voteInfo
        val voteView = cell_dynamic_vote ?: return
        val titleView = voteTitle ?: return
        val container = voteOptionsContainer ?: return
        val joinNumView = voteJoinNum ?: return
        val statusView = voteStatus ?: return

        // 设置标题
        titleView.text = if (voteInfo.title.isNotEmpty()) voteInfo.title else "投票"

        // 设置参与人数
        joinNumView.text = "${voteInfo.join_num}人参与"

        // 清空并重新填充选项
        container.removeAllViews()

        val isExpired = voteInfo.isExpired()
        val hasVoted = voteInfo.hasVoted()
        val isSingleChoice = voteInfo.choice_cnt <= 1

        // 设置状态文字
        statusView.text = when {
            isExpired -> "已结束"
            hasVoted -> "已投票"
            else -> "投票"
        }

        // 动态 feed 里的投票信息通常不带 options，需异步拉取完整投票信息
        if (voteInfo.options.isEmpty()) {
            fetchFullVoteInfo(context, voteInfo)
            voteView.visibility = View.VISIBLE
            return
        }

        for (option in voteInfo.options) {
            val optionView = createVoteOptionView(context, option, voteInfo, isExpired, hasVoted)
            container.addView(optionView)
        }

        voteView.visibility = View.VISIBLE
    }

    /**
     * 异步拉取完整投票信息（动态 feed 的 vote 只有 vote_id，无 options）
     */
    private fun fetchFullVoteInfo(context: Context, voteInfo: VoteInfo) {
        CenterThreadPool.run {
            try {
                val full = VoteApi.getVoteInfo(voteInfo.vote_id)
                if (full != null) {
                    // 合并完整信息
                    voteInfo.title = full.title
                    voteInfo.desc = full.desc
                    voteInfo.join_num = full.join_num
                    voteInfo.type = full.type
                    voteInfo.choice_cnt = full.choice_cnt
                    voteInfo.end_time = full.end_time
                    voteInfo.status = full.status
                    voteInfo.my_votes.clear()
                    voteInfo.my_votes.addAll(full.my_votes)
                    voteInfo.options.clear()
                    voteInfo.options.addAll(full.options)
                    // 用 mActivity 稳定回主线程，避免 context 非 Activity 时强转失败导致不刷新
                    mActivity.runOnUiThread {
                        showVoteCard(context, voteInfo)
                    }
                }
            } catch (e: Exception) {
                Logu.d("DynamicVote", "获取投票详情失败: " + e.message)
            }
        }
    }

    /**
     * 创建单个投票选项视图
     */
    @SuppressLint("SetTextI18n")
    private fun createVoteOptionView(
        context: Context,
        option: VoteOption,
        voteInfo: VoteInfo,
        isExpired: Boolean,
        hasVoted: Boolean
    ): View {
        val optionView = LayoutInflater.from(context).inflate(R.layout.item_vote_option, null)
        val optionText = optionView.findViewById<TextView>(R.id.option_text)
        val optionIndicator = optionView.findViewById<TextView>(R.id.option_indicator)

        // 图片投票 - 裁剪缩略图与文字并排
        if (!option.img_url.isNullOrEmpty()) {
            val thumb = optionView.findViewById<ImageView>(R.id.option_thumb)
            thumb.visibility = View.VISIBLE
            Glide.with(context)
                .load(option.img_url)
                .apply(RequestOptions().format(DecodeFormat.PREFER_RGB_565).centerCrop())
                .transition(DrawableTransitionOptions.withCrossFade())
                .into(thumb)
        }

        optionText.text = option.opt_desc

        // 已投票或投票结束后显示各选项得票数和比例
        if (isExpired || hasVoted) {
            val totalCnt = voteInfo.options.sumOf { it.cnt }
            if (totalCnt > 0) {
                val percent = (option.cnt * 100f / totalCnt).toInt()
                optionText.text = "${option.opt_desc}  ${option.cnt}票 ($percent%)"
            }
        }

        // 判断是否已选此选项
        val isSelected = voteInfo.my_votes.contains(option.opt_idx)

        if (isSelected) {
            optionView.setBackgroundResource(R.drawable.bg_vote_option_selected)
            optionIndicator.text = "✓"
            optionIndicator.setTextColor(Color.parseColor("#FE679A"))
        } else {
            optionView.setBackgroundResource(R.drawable.bg_vote_option)
            optionIndicator.text = if (isSingleChoice(voteInfo)) "○" else "□"
            optionIndicator.setTextColor(Color.parseColor("#88FFFFFF"))
        }

        // 投票结束或已投票时禁止点击
        if (isExpired || hasVoted) {
            optionView.isEnabled = false
            optionView.alpha = 0.7f
            return optionView
        }

        // 点击选项进行投票
        optionView.setOnClickListener { v ->
            // 未登录时提示
            if (SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0) == 0L) {
                MsgUtil.showMsg("请先登录")
                return@setOnClickListener
            }
            v.isEnabled = false
            CenterThreadPool.run {
                try {
                    val result = VoteApi.doVote(voteInfo.vote_id, listOf(option.opt_idx))
                    if (result == 0) {
                        // 投票成功，刷新投票信息
                        try {
                            val updatedVote = VoteApi.getVoteInfo(voteInfo.vote_id)
                            if (updatedVote != null) {
                                voteInfo.join_num = updatedVote.join_num
                                // 本地先标记已投（加入本次选中的选项），确保无论接口是否返回 my_votes 都锁定不可重复投
                                voteInfo.my_votes.clear()
                                if (!voteInfo.my_votes.contains(option.opt_idx)) {
                                    voteInfo.my_votes.add(option.opt_idx)
                                }
                                if (!updatedVote.my_votes.isEmpty()) {
                                    for (v in updatedVote.my_votes) {
                                        if (!voteInfo.my_votes.contains(v)) voteInfo.my_votes.add(v)
                                    }
                                }
                                // 同步更新各选项得票数
                                voteInfo.options.clear()
                                voteInfo.options.addAll(updatedVote.options)
                                (context as? Activity)?.runOnUiThread {
                                    showVoteCard(context, voteInfo)
                                    MsgUtil.showMsg("投票成功~")
                                }
                            }
                        } catch (e: Exception) {
                            (context as? Activity)?.runOnUiThread {
                                MsgUtil.err(e)
                                v.isEnabled = true
                            }
                        }
                    } else {
                        val msg = when (result) {
                            -111 -> "需要重新登录"
                            else -> "投票失败：$result"
                        }
                        (context as? Activity)?.runOnUiThread {
                            MsgUtil.showMsg(msg)
                            v.isEnabled = true
                        }
                    }
                } catch (e: Exception) {
                    (context as? Activity)?.runOnUiThread {
                        MsgUtil.err(e)
                        v.isEnabled = true
                    }
                }
            }
        }

        return optionView
    }

    private fun isSingleChoice(voteInfo: VoteInfo): Boolean {
        return voteInfo.choice_cnt <= 1
    }

    @SuppressLint("SetTextI18n", "ClickableViewAccessibility")
    fun showDynamic(context: Context, dynamic: Dynamic, clickable: Boolean) {
        if (!TextUtils.isEmpty(dynamic.title)) {
            title.visibility = View.VISIBLE
            title.text = dynamic.title
        } else
            title.visibility = View.GONE

        username.text = dynamic.userInfo.name
        if (!dynamic.userInfo.vip_nickname_color.isEmpty()) {
            username.setTextColor(Color.parseColor(dynamic.userInfo.vip_nickname_color))
        } else {
            username.setTextColor(0xFFFFFFFF.toInt())
        }
        if (pubdate != null)
            pubdate!!.text = dynamic.pubTime
        if (dynamic.content != null && !TextUtils.isEmpty(dynamic.content)) {
            content.visibility = View.VISIBLE
            content.text = dynamic.content
            StringUtil.setCopy(content)
            content.setOnTouchListener(StringUtil.ClickableSpanTouchListener.getInstance())
        } else
            content.visibility = View.GONE

        if (dynamic.userInfo.avatar != lastAvatarUrl) {
            lastAvatarUrl = dynamic.userInfo.avatar
            Glide.with(BiliTerminal.context).asDrawable().load(GlideUtil.url(dynamic.userInfo.avatar))
                .transition(GlideUtil.getTransitionOptions())
                .placeholder(R.mipmap.akari)
                .apply(RequestOptions.circleCropTransform())
                .format(DecodeFormat.PREFER_RGB_565)
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .override(80, 80)
                .into(avatar)
        }

        avatar.setOnClickListener {
            val intent = Intent()
            intent.setClass(context, UserInfoActivity::class.java)
            intent.putExtra("mid", dynamic.userInfo.mid)
            context.startActivity(intent)
        }

        var isPgc = false
        if (!isChild) cell_dynamic_child.visibility = View.GONE
        cell_dynamic_video.visibility = View.GONE
        cell_dynamic_image.visibility = View.GONE
        cell_dynamic_article.visibility = View.GONE
        cell_dynamic_vote?.visibility = View.GONE
        if (dynamic.major_type != null)
            when (dynamic.major_type) {
                "MAJOR_TYPE_PGC" -> isPgc = true
                "MAJOR_TYPE_ARCHIVE", "MAJOR_TYPE_UGC_SEASON" -> {
                    val childVideoCard = dynamic.major_object as VideoCard
                    if (videoCardHolder == null) {
                        videoCardHolder = VideoCardHolder(cell_dynamic_video)
                    }
                    videoCardHolder!!.showVideoCard(childVideoCard, context)
                    val finalIsPgc = isPgc
                    cell_dynamic_video.setOnClickListener {
                        TerminalContext.getInstance()
                            .enterVideoDetailPage(context, childVideoCard.aid, "", if (finalIsPgc) "media" else null)
                    }
                    cell_dynamic_video.visibility = View.VISIBLE
                }

                "MAJOR_TYPE_LIVE", "MAJOR_TYPE_LIVE_RCMD" -> {
                    val liveRoom = dynamic.major_object as LiveRoom
                    val childLiveCard = VideoCard()
                    childLiveCard.title = liveRoom.title
                    childLiveCard.cover = liveRoom.cover
                    childLiveCard.upName = liveRoom.uname
                    childLiveCard.view = ""
                    childLiveCard.type = "live"

                    if (videoCardHolder == null) {
                        videoCardHolder = VideoCardHolder(cell_dynamic_video)
                    }
                    videoCardHolder!!.showVideoCard(childLiveCard, context)
                    cell_dynamic_video.setOnClickListener {
                        TerminalContext.getInstance().enterLiveDetailPage(context, liveRoom.roomid)
                    }
                    cell_dynamic_video.visibility = View.VISIBLE
                }

                "MAJOR_TYPE_ARTICLE" -> {
                    val articleCard = dynamic.major_object as ArticleCard
                    if (articleCardHolder == null) {
                        articleCardHolder = ArticleCardHolder(cell_dynamic_article)
                    }
                    articleCardHolder!!.showArticleCard(articleCard, context)
                    cell_dynamic_article.setOnClickListener {
                        TerminalContext.getInstance().enterArticleDetailPage(context, articleCard.id)
                    }
                    cell_dynamic_article.visibility = View.VISIBLE
                }

                "MAJOR_TYPE_DRAW", "MAJOR_TYPE_OPUS" -> {
                    val pictureList: ArrayList<String> = if (dynamic.major_object is ArrayList<*>) {
                        dynamic.major_object as ArrayList<String>
                    } else {
                        ArrayList()
                    }

                    if (!pictureList.isEmpty()) {
                        val imageView = cell_dynamic_image.findViewById<ImageView>(R.id.imageView)
                        val firstImageUrl = pictureList[0]
                        if (firstImageUrl != lastImageUrl) {
                            lastImageUrl = firstImageUrl
                            Glide.with(BiliTerminal.context).asDrawable().load(GlideUtil.url(firstImageUrl))
                                .transition(GlideUtil.getTransitionOptions())
                                .placeholder(R.mipmap.placeholder)
                                .centerCrop()
                                .format(DecodeFormat.PREFER_RGB_565)
                                .sizeMultiplier(0.85f)
                                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                                .override(400, 400)
                                .into(imageView)
                        }
                        val textView = cell_dynamic_image.findViewById<TextView>(R.id.imageCount)
                        textView.text = "共" + pictureList.size + "张图片"
                        imageView.setOnClickListener {
                            val intent = Intent()
                            intent.setClass(context, ImageViewerActivity::class.java)
                            intent.putExtra("imageList", pictureList)
                            context.startActivity(intent)
                        }
                        cell_dynamic_image.visibility = View.VISIBLE
                    }
                }
            }

        // 渲染投票卡片
        if (dynamic.additional_type == "ADDITIONAL_TYPE_VOTE" && dynamic.vote != null) {
            showVoteCard(context, dynamic.vote!!)
        } else {
            cell_dynamic_vote?.visibility = View.GONE
        }

        if (dynamic.major_object == null && dynamic.dynamic_forward == null && dynamic.vote == null)
            extraCard.visibility = View.GONE
        else
            extraCard.visibility = View.VISIBLE

        if (clickable) {
            content.maxLines = 5
            if (dynamic.dynamicId != 0L) {
                (if (isChild) itemView.findViewById<View>(R.id.dynamic_child) else itemView)
                    .setOnClickListener {
                        if (context is Activity) {
                            TerminalContext.getInstance().enterDynamicDetailPageForResult(
                                context as Activity,
                                dynamic.dynamicId, adapterPosition, GO_TO_INFO_REQUEST
                            )
                        } else {
                            TerminalContext.getInstance().enterDynamicDetailPage(
                                context, dynamic.dynamicId,
                                adapterPosition
                            )
                        }
                    }
                content.setOnClickListener {
                    val targetView =
                        (if (isChild) itemView.findViewById<View>(R.id.dynamic_child) else itemView)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1) {
                        targetView.callOnClick()
                    } else {
                        targetView.performClick()
                    }
                }
            }
        } else {
            content.maxLines = 999
        }
        content.ellipsize = TextUtils.TruncateAt.END

        val onRelayClick = View.OnClickListener {
            if (relayDynamicLauncher == null) {
                return@OnClickListener
            }
            val intent = Intent()
            intent.setClass(mActivity, SendDynamicActivity::class.java)
            intent.putExtra("dynamicId", dynamic.dynamicId)
            TerminalContext.getInstance().setForwardContent(dynamic)
            relayDynamicLauncher!!.launch(intent)
        }
        if (item_dynamic_share != null && clickable)
            item_dynamic_share!!.setOnClickListener(onRelayClick)

        val onDeleteClick = View.OnClickListener { MsgUtil.showMsg("长按删除") }
        if (item_dynamic_delete != null) {
            item_dynamic_delete!!.setOnClickListener(onDeleteClick)
            item_dynamic_delete!!.visibility = View.GONE
        }

        if (likeCount != null) {
            if (dynamic.stats != null) {
                if (dynamic.stats.liked) {
                    likeCount!!.setTextColor(Color.rgb(0xfe, 0x67, 0x9a))
                    likeCount!!.setCompoundDrawablesWithIntrinsicBounds(
                        ContextCompat.getDrawable(context, R.drawable.icon_reply_like1), null, null, null
                    )
                } else {
                    likeCount!!.setTextColor(Color.rgb(0xff, 0xff, 0xff))
                    likeCount!!.setCompoundDrawablesWithIntrinsicBounds(
                        ContextCompat.getDrawable(context, R.drawable.icon_reply_like0), null, null, null
                    )
                }
                likeCount!!.text = StringUtil.toWan(dynamic.stats.like.toLong())
            } else {
                likeCount!!.visibility = View.GONE
            }
            likeCount!!.setOnClickListener {
                CenterThreadPool.run {
                    if (!dynamic.stats.liked) {
                        try {
                            if (DynamicApi.likeDynamic(dynamic.dynamicId, true) == 0) {
                                dynamic.stats.liked = true
                                (context as Activity).runOnUiThread {
                                    MsgUtil.showMsg("点赞成功")
                                    likeCount!!.text = StringUtil.toWan((++dynamic.stats.like).toLong())
                                    likeCount!!.setTextColor(Color.rgb(0xfe, 0x67, 0x9a))
                                    likeCount!!.setCompoundDrawablesWithIntrinsicBounds(
                                        ContextCompat.getDrawable(context, R.drawable.icon_reply_like1), null, null,
                                        null
                                    )
                                }
                            } else
                                (context as Activity).runOnUiThread { MsgUtil.showMsg("点赞失败") }
                        } catch (e: IOException) {
                            MsgUtil.err(e)
                        }
                    } else {
                        try {
                            if (DynamicApi.likeDynamic(dynamic.dynamicId, false) == 0) {
                                dynamic.stats.liked = false
                                (context as Activity).runOnUiThread {
                                    MsgUtil.showMsg("取消成功")
                                    likeCount!!.text = StringUtil.toWan((--dynamic.stats.like).toLong())
                                    likeCount!!.setTextColor(Color.rgb(0xff, 0xff, 0xff))
                                    likeCount!!.setCompoundDrawablesWithIntrinsicBounds(
                                        ContextCompat.getDrawable(context, R.drawable.icon_reply_like0), null, null,
                                        null
                                    )
                                }
                            } else
                                (context as Activity).runOnUiThread { MsgUtil.showMsg("取消失败") }
                        } catch (e: IOException) {
                            e.printStackTrace()
                        }
                    }
                }
            }
        }
    }
}