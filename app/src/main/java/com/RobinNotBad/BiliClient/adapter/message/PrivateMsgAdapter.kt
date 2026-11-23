package com.RobinNotBad.BiliClient.adapter.message

import com.RobinNotBad.BiliClient.util.UiText

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
import com.RobinNotBad.BiliClient.util.StringUtil
import com.RobinNotBad.BiliClient.util.TerminalContext
import com.RobinNotBad.BiliClient.util.TerminalDialog
import com.RobinNotBad.BiliClient.util.TimeUtil
import com.RobinNotBad.BiliClient.ui.appearance.ColorScheme
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
        lateinit var timeTv: TextView
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
            timeTv = view.findViewById(R.id.msg_time)
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
            // 反馈 #267：时间戳单位是秒（model/PrivateMessage.java），没时间就整块隐藏。
            val timeText = TimeUtil.formatChatTime(msg.timestamp * 1000L)
            holder.timeTv.text = timeText
            holder.timeTv.visibility = if (timeText.isEmpty()) View.GONE else View.VISIBLE
            if (selfUid == -1L) {
                selfUid = SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, -1)
            }
            if (msg.uid == selfUid) {
                holder.root.gravity = Gravity.END
                holder.textContentCard.setCardBackgroundColor(ColorScheme.PRIMARY.toInt())
                holder.textContentCard.strokeWidth = 0
            } else {
                holder.root.gravity = Gravity.START
                holder.textContentCard.setCardBackgroundColor(ColorScheme.SURFACE.toInt())
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
                        holder.tipTv.text = UiText.of("此条消息为自动回复")
                        holder.tipTv.visibility = View.VISIBLE
                    }

                    try {
                        val textContent = msg.content.getString("content")
                        // 先按纯文本上一遍链接：表情替换要同步下载图（见 EmoteUtil.replaceSingle），
                        // 等它回来才上色的话，纯文字消息也要先黑一下再变蓝。
                        val plainText = SpannableStringBuilder(textContent)
                        StringUtil.setLink(plainText)
                        holder.textContentTv.text = plainText
                        // 正文里的裸链接 / av / BV / cv 与评论区走同一个入口（[StringUtil.setLink]），
                        // 颜色取主题链接色、点击走 [StringUtil.LinkClickableSpan]；受「识别链接」开关控制。
                        // 链接要能点必须挂这个触摸监听：它自己发 onClick，不依赖 LinkMovementMethod。
                        holder.textContentTv.setOnTouchListener(StringUtil.ClickableSpanTouchListener.getInstance())
                        CenterThreadPool.run {
                            try {
                                val contentWithEmote: SpannableStringBuilder = PrivateMsgApi.textReplaceEmote(
                                    textContent,
                                    emoteArray, 1f, context
                                )
                                // 必须放在表情替换之后：表情会替换成 ImageSpan 并改变长度，先加 span 下标就全错位了
                                StringUtil.setLink(contentWithEmote)
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
                        GlideUtil.with(holder.picMsg)
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
                    holder.tipTv.text = msg.name + UiText.of("撤回了一条消息")
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
                    GlideUtil.with(holder.videoCover)
                        .asDrawable()
                        .load(if (thumb.isEmpty()) null else GlideUtil.url(thumb))
                        .apply(GlideUtil.thumbOptions(holder.videoCover))
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
                                        else MsgUtil.showMsg(UiText.of("无法解析直播间信息"))
                                    }
                                    else -> {
                                        val url = shareContent.optString("url", "")
                                        if (url.isNotEmpty()) LinkUrlUtil.handleWebURL(context, url)
                                        else MsgUtil.showMsg(UiText.of("暂不支持打开此类分享内容"))
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
                        GlideUtil.with(holder.picMsg)
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
                            holder.textContentTv.text = UiText.of("暂时无法显示该消息")
                        } else {
                            val coverUrl = firstCard.optString("cover_url", "")
                            GlideUtil.with(holder.videoCover)
                                .asDrawable()
                                .load(if (coverUrl.isEmpty()) null else GlideUtil.url(coverUrl))
                                .apply(GlideUtil.thumbOptions(holder.videoCover))
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
                    holder.textContentTv.text = UiText.of("暂时无法显示该消息")
                    holder.tipTv.visibility = View.GONE
                    holder.picMsg.visibility = View.GONE
                    holder.nameTv.visibility = View.VISIBLE
                    holder.videoCard.visibility = View.GONE
                }
            }

            holder.textContentCard.setOnLongClickListener {
                onMessageLongPress(msg, canCopy = true)
                true
            }
            // 正文自己也要能长按：链接是 ClickableSpan，按在链接上时事件被 TextView 消费，
            // 到不了外层卡片，长按就会「按着没反应、抬手反而打开链接」（评论区踩过同一个坑）。
            // [StringUtil.ClickableSpanTouchListener] 靠 isLongClickable() 判断要不要补发点击，
            // 所以这里必须挂上长按；非文本消息的正文是 GONE，挂上也不影响。
            holder.textContentTv.setOnLongClickListener {
                onMessageLongPress(msg, canCopy = true)
                true
            }
            // 图片消息原先没有长按；自己发的要能撤回（反馈 #57），所以也挂上
            holder.picMsg.setOnLongClickListener {
                if (!PrivateMsgApi.canRecall(msg, selfUid, System.currentTimeMillis() / 1000)) return@setOnLongClickListener false
                onMessageLongPress(msg, canCopy = false)
                true
            }
        } catch (err: JSONException) {
            Log.e(PrivateMessage::class.java.name, err.toString())
        }
    }

    /** 撤回由页面执行（要发请求、刷新列表），适配器只负责弹菜单。 */
    var onRecall: ((PrivateMessage) -> Unit)? = null

    /**
     * 长按消息。自己发的、仍在撤回有效期内的弹「复制 / 撤回」菜单；
     * 其余情况保持原来的直接进复制页（不多弹一层菜单）。
     */
    private fun onMessageLongPress(msg: PrivateMessage, canCopy: Boolean) {
        val text = msg.content.optString("content", "")
        val recallable = onRecall != null &&
            PrivateMsgApi.canRecall(msg, selfUid, System.currentTimeMillis() / 1000)
        if (!recallable) {
            if (canCopy && text.isNotEmpty()) openCopy(text)
            return
        }
        val items = ArrayList<String>()
        if (canCopy && text.isNotEmpty()) items.add(context.getString(R.string.btn_private_msg_copy))
        items.add(context.getString(R.string.btn_private_msg_recall))
        val recallIndex = items.lastIndex
        TerminalDialog.menu(context, null, items, danger = setOf(recallIndex)) { which ->
            if (which == recallIndex) onRecall?.invoke(msg) else openCopy(text)
        }.show()
    }

    private fun openCopy(text: String) {
        val intent = Intent(context, CopyTextActivity::class.java)
        intent.putExtra("content", text)
        context.startActivity(intent)
    }

    /** 撤回成功后从列表里摘掉原消息（服务端的撤回通知随下一次 refresh 进来）。 */
    fun removeMessage(msg: PrivateMessage) {
        val index = mPrivateMsgList.indexOf(msg)
        if (index < 0) return
        (mPrivateMsgList as MutableList).removeAt(index)
        notifyItemRemoved(index)
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