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
                    Glide.with(BiliTerminal.context)
                        .asDrawable()
                        .load(GlideUtil.url(msg.content.getString("thumb")))
                        .transition(GlideUtil.getTransitionOptions())
                        .format(DecodeFormat.PREFER_RGB_565)
                        .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                        .into(holder.videoCover)
                    holder.upNameTv.text = msg.content.getString("author")
                    holder.videoTitleTv.text = msg.content.getString("title")
                    holder.videoCard.setOnClickListener {
                        CenterThreadPool.run {
                            try {
                                val aid = msg.content.getLong("id")
                                TerminalContext.getInstance().enterVideoDetailPage(context, aid, "", "video")
                            } catch (err: JSONException) {
                                Log.e("", err.toString())
                            }
                        }
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