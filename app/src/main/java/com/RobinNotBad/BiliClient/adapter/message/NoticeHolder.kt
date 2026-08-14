package com.RobinNotBad.BiliClient.adapter.message

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import com.RobinNotBad.BiliClient.BiliTerminal
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.user.info.UserInfoActivity
import com.RobinNotBad.BiliClient.adapter.video.VideoCardHolder
import com.RobinNotBad.BiliClient.api.ReplyApi
import com.RobinNotBad.BiliClient.model.MessageCard
import com.RobinNotBad.BiliClient.model.Reply
import com.RobinNotBad.BiliClient.model.VideoCard
import com.RobinNotBad.BiliClient.util.GlideUtil
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.StringUtil
import com.RobinNotBad.BiliClient.util.TerminalContext
import com.RobinNotBad.BiliClient.util.ToolsUtil
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import java.text.SimpleDateFormat

class NoticeHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    lateinit var avaterList: LinearLayout
    lateinit var action: TextView
    lateinit var pubdate: TextView
    lateinit var extraCard: ConstraintLayout

    init {
        avaterList = itemView.findViewById(R.id.avatar_list)
        action = itemView.findViewById(R.id.action)
        pubdate = itemView.findViewById(R.id.pubdate)
        extraCard = itemView.findViewById(R.id.extraCard)
    }

    @SuppressLint("SetTextI18n")
    fun showMessage(message: MessageCard, context: Context) {
        avaterList.removeAllViews()
        if (message.user.isEmpty()) avaterList.visibility = View.GONE
        else avaterList.visibility = View.VISIBLE
        for (i in message.user.indices) {
            val imageView = ImageView(context)
            Glide.with(BiliTerminal.context)
                .asDrawable()
                .load(GlideUtil.url(message.user[i].avatar))
                .transition(GlideUtil.getTransitionOptions())
                .placeholder(R.mipmap.akari)
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .apply(RequestOptions.circleCropTransform())
                .into(imageView)
            val imageSize: Int = ToolsUtil.dp2px(32f)
            val imageLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            imageLp.width = imageSize
            imageLp.height = imageSize
            imageView.layoutParams = imageLp
            imageView.left = ToolsUtil.dp2px(3f)
            val finalI = i
            imageView.setOnClickListener {
                val intent = Intent()
                intent.setClass(context, UserInfoActivity::class.java)
                intent.putExtra("mid", message.user[finalI].mid)
                context.startActivity(intent)
            }
            avaterList.addView(imageView)

            val view = View(context)
            val viewLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            viewLp.width = ToolsUtil.dp2px(3f)
            viewLp.height = ToolsUtil.dp2px(32f)
            view.layoutParams = viewLp
            avaterList.addView(view)
        }

        if (message.timeStamp != 0L) {
            @SuppressLint("SimpleDateFormat")
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm")
            pubdate.text = sdf.format(message.timeStamp * 1000)
        } else pubdate.text = message.timeDesc

        action.text = message.content
        StringUtil.setCopy(action)

        if (message.videoCard != null) {
            val childVideoCard: VideoCard = message.videoCard
            val holder = VideoCardHolder(View.inflate(context, R.layout.cell_dynamic_video, extraCard))
            holder.showVideoCard(childVideoCard, context)
            holder.itemView.findViewById<View>(R.id.videoCardView).setOnClickListener {
                TerminalContext.getInstance().enterVideoDetailPage(context, 0, childVideoCard.bvid)
            }
        }
        if (message.replyInfo != null || message.dynamicInfo != null) {
            val childReply: Reply = message.replyInfo ?: message.dynamicInfo!!
            val holder = ReplyCardHolder(View.inflate(context, R.layout.cell_message_reply, extraCard))
            holder.showReplyCard(childReply)
            holder.itemView.findViewById<View>(R.id.cardView).setOnClickListener {
                try {
                    if (message.itemType == "reply" || message.getType == MessageCard.GET_TYPE_REPLY) {
                        val seekReply = if (message.rootId == 0L) message.sourceId else message.rootId
                        when (message.businessId) {
                            ReplyApi.REPLY_TYPE_VIDEO_CHILD -> {
                                MsgUtil.showMsg("视频的子评论暂时无法定位，也许以后会做吧……")
                                TerminalContext.getInstance().enterVideoDetailPage(context, 0, childReply.ofBvid, null, seekReply)
                            }
                            ReplyApi.REPLY_TYPE_VIDEO -> {
                                TerminalContext.getInstance().enterVideoDetailPage(context, 0, childReply.ofBvid, null, seekReply)
                            }
                            ReplyApi.REPLY_TYPE_DYNAMIC_CHILD -> {
                                MsgUtil.showMsg("动态的子评论暂时无法跳转，也许以后会做吧……")
                            }
                            ReplyApi.REPLY_TYPE_DYNAMIC -> {
                                TerminalContext.getInstance().enterDynamicDetailPage(context, message.subjectId, 0, seekReply)
                            }
                            ReplyApi.REPLY_TYPE_ARTICLE -> {
                                TerminalContext.getInstance().enterArticleDetailPage(context, message.subjectId, seekReply)
                            }
                            else -> {
                                MsgUtil.showMsg("不支持这个类型喵：" + message.businessId)
                            }
                        }
                    } else {
                        when (message.getType) {
                            MessageCard.GET_TYPE_LIKE, MessageCard.GET_TYPE_AT -> {
                                when (message.itemType) {
                                    "video" -> {
                                        TerminalContext.getInstance().enterVideoDetailPage(context, 0, childReply.ofBvid)
                                    }
                                    "dynamic" -> {
                                        TerminalContext.getInstance().enterDynamicDetailPage(context, message.subjectId)
                                    }
                                    "article" -> {
                                        TerminalContext.getInstance().enterArticleDetailPage(context, message.subjectId)
                                    }
                                    else -> {
                                        MsgUtil.showMsg("不支持这个类型喵：" + message.itemType)
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    MsgUtil.err("跳转出错？", e)
                }
            }
        }
    }
}