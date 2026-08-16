package com.RobinNotBad.BiliClient.adapter.message

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.text.SpannableStringBuilder
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.RobinNotBad.BiliClient.BiliTerminal
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.CopyTextActivity
import com.RobinNotBad.BiliClient.activity.ImageViewerActivity
import com.RobinNotBad.BiliClient.api.PrivateMsgApi
import com.RobinNotBad.BiliClient.model.PrivateMessage
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.GlideUtil
import com.RobinNotBad.BiliClient.util.LinkUrlUtil
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import com.RobinNotBad.BiliClient.util.TerminalContext
import com.RobinNotBad.BiliClient.ui.theme.ThemeManager
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.android.material.card.MaterialCardView
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class PrivateMsgAdapter(
    private val mPrivateMsgList: List<PrivateMessage>,
    private val emoteArray: JSONArray,
    private val context: Context
) : RecyclerView.Adapter<PrivateMsgAdapter.ViewHolder>() {

    private var selfUid: Long = -1

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        lateinit var nameTv: TextView
        lateinit var textContentTv: TextView
        lateinit var tipTv: TextView
        lateinit var playTimesTv: TextView
        lateinit var upNameTv: TextView
        lateinit var videoTitleTv: TextView
        lateinit var textContentCard: MaterialCardView
        lateinit var videoCard: MaterialCardView
        lateinit var picMsg: ImageView
        lateinit var videoCover: ImageView
        lateinit var root: LinearLayout

        init {
            root = view.findViewById(R.id.msg_layout)
            nameTv = view.findViewById(R.id.msg_name)
            textContentTv = view.findViewById(R.id.msg_text_content)
            tipTv = view.findViewById(R.id.msg_type_tip_text)
            playTimesTv = view.findViewById(R.id.text_viewcount)
            upNameTv = view.findViewById(R.id.text_upname)
            videoTitleTv = view.findViewById(R.id.text_title)
            textContentCard = view.findViewById(R.id.msg_type_text_card)
            videoCard = view.findViewById(R.id.cardView)
            picMsg = view.findViewById(R.id.msg_type_pic)
            videoCover = view.findViewById(R.id.img_cover)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.cell_private_msg, parent, false)
        return ViewHolder(view)
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        if (position < 0 || position >= mPrivateMsgList.size)
            return
        val msg = mPrivateMsgList[position] ?: return

        try {
            holder.nameTv.text = msg.name
            if (selfUid == -1L) {
                selfUid = SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, -1)
            }
            if (msg.uid == selfUid) {
                holder.root.gravity = Gravity.END
                holder.textContentCard.setCardBackgroundColor(ThemeManager.PRIMARY.toInt())
                holder.textContentCard.strokeWidth = 0
            } else {
                holder.root.gravity = Gravity.START
                holder.textContentCard.setCardBackgroundColor(ThemeManager.SURFACE.toInt())
                holder.textContentCard.strokeWidth = 1
            }

            when (msg.type) {
                PrivateMessage.TYPE_TEXT -> {
                    holder.tipTv.visibility = View.GONE
                    holder.picMsg.visibility = View.GONE
                    holder.nameTv.visibility = View.VISIBLE
                    holder.videoCard.visibility = View.GONE
                    holder.textContentCard.visibility = View.VISIBLE

                    if (msg.msg_source >= 8 && msg.msg_source <= 11) {
                        holder.tipTv.text = "此条消息为自动回复"
                        holder.tipTv.visibility = View.VISIBLE
                    }

                    try {
                        val textContent = msg.content.getString("content")
                        holder.textContentTv.text = textContent
                        CenterThreadPool.run {
                            try {
                                val contentWithEmote: SpannableStringBuilder = PrivateMsgApi.textReplaceEmote(
                                    textContent,
                                    emoteArray, 1f, context
                                )
                                if (holder.adapterPosition == position) {
                                    (context as Activity)
                                        .runOnUiThread { holder.textContentTv.text = contentWithEmote }
                                }
                            } catch (err: Exception) {
                                Log.e("PrivateMsgAdapter", err.toString())
                            }
                        }
                    } catch (e: JSONException) {
                        Log.e("PrivateMsgAdapter", e.toString())
                    }
                }
                PrivateMessage.TYPE_FACE, PrivateMessage.TYPE_PIC -> {
                    holder.picMsg.visibility = View.VISIBLE
                    holder.tipTv.visibility = View.GONE
                    holder.nameTv.visibility = View.VISIBLE
                    holder.textContentCard.visibility = View.GONE
                    holder.videoCard.visibility = View.GONE
                    try {
                        val picUrl = msg.content.getString("url")
                        Glide.with(BiliTerminal.context)
                            .asDrawable()
                            .load(GlideUtil.url(picUrl))
                            .transition(GlideUtil.getTransitionOptions())
                            .override(512)
                            .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                            .into(holder.picMsg)
                        holder.picMsg.setOnClickListener {
                            val imageList = ArrayList<String>()
                            try {
                                imageList.add(msg.content.getString("url"))
                            } catch (e: JSONException) {
                                Log.e("PrivateMsgAdapter", e.toString())
                            }
                            val intent = Intent(context, ImageViewerActivity::class.java)
                            intent.putStringArrayListExtra("imageList", imageList)
                            context.startActivity(intent)
                        }
                    } catch (e: JSONException) {
                        Log.e("PrivateMsgAdapter", e.toString())
                    }
                }
                PrivateMessage.TYPE_RETRACT -> {
                    holder.tipTv.visibility = View.VISIBLE
                    holder.nameTv.visibility = View.GONE
                    holder.picMsg.visibility = View.GONE
                    holder.videoCard.visibility = View.GONE
                    holder.textContentCard.visibility = View.GONE
                    holder.tipTv.text = msg.name + "撤回了一条消息"
                }
                PrivateMessage.TYPE_VIDEO -> {
                    holder.videoCard.visibility = View.VISIBLE
                    holder.nameTv.visibility = View.VISIBLE
                    holder.picMsg.visibility = View.GONE
                    holder.textContentCard.visibility = View.GONE
                    holder.tipTv.visibility = View.GONE
                    holder.playTimesTv.text = ""

                    val shareContent = msg.content
                    val source = shareContent.optInt("source", 5)
                    val headline = shareContent.optString("headline", "")
                    val thumb = shareContent.optString("thumb", "")
                    holder.upNameTv.text = shareContent.optString("author", "")
                    holder.videoTitleTv.text =
                        if (headline.isNotEmpty()) headline else shareContent.optString("title", "")
                    Glide.with(BiliTerminal.context)
                        .asDrawable()
                        .load(if (thumb.isEmpty()) null else GlideUtil.url(thumb))
                        .transition(GlideUtil.getTransitionOptions())
                        .format(DecodeFormat.PREFER_RGB_565)
                        .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                        .into(holder.videoCover)
                    holder.videoCard.setOnClickListener {
                        CenterThreadPool.run {
                            try {
                                val terminal = TerminalContext.getInstance()
                                when (source) {
                                    5 -> {
                                        val bvid = shareContent.optString("bvid", "")
                                        val aid = shareContent.optLong("id", 0)
                                        if (bvid.isNotEmpty()) terminal.enterVideoDetailPage(context, bvid)
                                        else terminal.enterVideoDetailPage(context, aid)
                                    }
                                    6 -> terminal.enterOpusDetailPage(context, shareContent.getLong("id"))
                                    11 -> terminal.enterDynamicDetailPage(context, shareContent.getLong("id"))
                                    4 -> {
                                        val roomId = Regex("live\\.bilibili\\.com/(\\d+)")
                                            .find(shareContent.optString("url", ""))
                                            ?.groupValues?.get(1)?.toLongOrNull()
                                            ?: shareContent.optLong("id", 0)
                                        if (roomId > 0) terminal.enterLiveDetailPage(context, roomId)
                                        else MsgUtil.showMsg("无法解析直播间信息")
                                    }
                                    else -> {
                                        val url = shareContent.optString("url", "")
                                        if (url.isNotEmpty()) LinkUrlUtil.handleWebURL(context, url)
                                        else MsgUtil.showMsg("暂不支持打开此类分享内容")
                                    }
                                }
                            } catch (err: JSONException) {
                                Log.e("", err.toString())
                            }
                        }
                    }
                }
                PrivateMessage.TYPE_NOMAL_CARD -> {
                    holder.textContentCard.visibility = View.VISIBLE
                    holder.textContentTv.text = msg.content.optString("text", "")
                    holder.tipTv.text = msg.content.optString("title", "")
                    if (holder.tipTv.text.isEmpty()) holder.tipTv.visibility = View.GONE
                    else holder.tipTv.visibility = View.VISIBLE
                    holder.picMsg.visibility = View.GONE
                    holder.nameTv.visibility = View.VISIBLE
                    holder.videoCard.visibility = View.GONE
                    val jumpUri = msg.content.optString("jump_uri", "")
                    holder.textContentCard.setOnClickListener(null)
                    if (jumpUri.isNotEmpty()) {
                        holder.textContentCard.setOnClickListener {
                            LinkUrlUtil.handleWebURL(context, jumpUri)
                        }
                    }
                }
                PrivateMessage.TYPE_PIC_CARD -> {
                    holder.picMsg.visibility = View.VISIBLE
                    holder.tipTv.visibility = View.GONE
                    holder.nameTv.visibility = View.VISIBLE
                    holder.textContentCard.visibility = View.GONE
                    holder.videoCard.visibility = View.GONE
                    try {
                        val picUrl = msg.content.getString("pic_url")
                        Glide.with(BiliTerminal.context)
                            .asDrawable()
                            .load(GlideUtil.url(picUrl))
                            .transition(GlideUtil.getTransitionOptions())
                            .override(512)
                            .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                            .into(holder.picMsg)
                        val jumpUrl = msg.content.optString("jump_url", "")
                        holder.picMsg.setOnClickListener {
                            if (jumpUrl.isNotEmpty()) {
                                LinkUrlUtil.handleWebURL(context, jumpUrl)
                            } else {
                                val imageList = ArrayList<String>()
                                imageList.add(picUrl)
                                val intent = Intent(context, ImageViewerActivity::class.java)
                                intent.putStringArrayListExtra("imageList", imageList)
                                context.startActivity(intent)
                            }
                        }
                        val title = msg.content.optString("title", "")
                        if (title.isNotEmpty()) {
                            holder.textContentCard.visibility = View.VISIBLE
                            holder.textContentTv.text = title
                        }
                    } catch (e: JSONException) {
                        Log.e("PrivateMsgAdapter", e.toString())
                    }
                }
                PrivateMessage.TYPE_TEXT_WITH_VIDEO -> {
                    holder.videoCard.visibility = View.VISIBLE
                    holder.nameTv.visibility = View.VISIBLE
                    holder.picMsg.visibility = View.GONE
                    holder.textContentCard.visibility = View.GONE
                    holder.tipTv.visibility = View.GONE
                    holder.playTimesTv.text = ""
                    try {
                        val mainTitle = msg.content.optString("main_title", "")
                        if (mainTitle.isNotEmpty()) {
                            holder.tipTv.visibility = View.VISIBLE
                            holder.tipTv.text = mainTitle
                        }
                        val subCards = msg.content.optJSONArray("sub_cards")
                        val firstCard =
                            if (subCards != null && subCards.length() > 0) subCards.getJSONObject(0) else null
                        if (firstCard == null) {
                            holder.videoCard.visibility = View.GONE
                            holder.textContentCard.visibility = View.VISIBLE
                            holder.textContentTv.text = "暂时无法显示该消息"
                        } else {
                            val coverUrl = firstCard.optString("cover_url", "")
                            Glide.with(BiliTerminal.context)
                                .asDrawable()
                                .load(if (coverUrl.isEmpty()) null else GlideUtil.url(coverUrl))
                                .transition(GlideUtil.getTransitionOptions())
                                .format(DecodeFormat.PREFER_RGB_565)
                                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                                .into(holder.videoCover)
                            holder.upNameTv.text = firstCard.optString("field2", "")
                            holder.videoTitleTv.text = firstCard.optString("field1", "")
                            val aid = firstCard.optLong("card_id", 0)
                            val jumpUrl = firstCard.optString("jump_url", "")
                            holder.videoCard.setOnClickListener {
                                if (aid > 0) TerminalContext.getInstance().enterVideoDetailPage(context, aid)
                                else if (jumpUrl.isNotEmpty()) LinkUrlUtil.handleWebURL(context, jumpUrl)
                            }
                        }
                    } catch (e: JSONException) {
                        Log.e("PrivateMsgAdapter", e.toString())
                    }
                }
                PrivateMessage.TYPE_SYSTEM -> {
                    holder.tipTv.visibility = View.VISIBLE
                    holder.nameTv.visibility = View.GONE
                    holder.picMsg.visibility = View.GONE
                    holder.videoCard.visibility = View.GONE
                    holder.textContentCard.visibility = View.GONE
                    holder.tipTv.text = (msg.content_array.get(0) as JSONObject).getString("text")
                }
                else -> {
                    holder.textContentCard.visibility = View.VISIBLE
                    holder.textContentTv.text = "暂时无法显示该消息"
                    holder.tipTv.visibility = View.GONE
                    holder.picMsg.visibility = View.GONE
                    holder.nameTv.visibility = View.VISIBLE
                    holder.videoCard.visibility = View.GONE
                }
            }

            holder.textContentCard.setOnLongClickListener {
                try {
                    val intent = Intent(context, CopyTextActivity::class.java)
                    intent.putExtra("content", msg.content.getString("content"))
                    context.startActivity(intent)
                } catch (err: Exception) {
                    err.printStackTrace()
                }
                false
            }
        } catch (err: JSONException) {
            Log.e(PrivateMessage::class.java.name, err.toString())
        }
    }

    fun addItem(list: ArrayList<PrivateMessage>) {
        if (list.isEmpty())
            return
        (mPrivateMsgList as MutableList).addAll(0, list)
        notifyItemRangeInserted(0, list.size)
    }

    override fun getItemCount(): Int {
        return mPrivateMsgList.size
    }
}