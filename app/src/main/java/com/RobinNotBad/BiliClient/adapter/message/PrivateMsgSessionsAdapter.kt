package com.RobinNotBad.BiliClient.adapter.message

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.RobinNotBad.BiliClient.BiliTerminal
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.message.PrivateMsgActivity
import com.RobinNotBad.BiliClient.activity.user.info.UserInfoActivity
import com.RobinNotBad.BiliClient.model.PrivateMessage
import com.RobinNotBad.BiliClient.model.PrivateMsgSession
import com.RobinNotBad.BiliClient.model.UserInfo
import com.RobinNotBad.BiliClient.ui.widget.RadiusBackgroundSpan
import com.RobinNotBad.BiliClient.util.GlideUtil
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import org.json.JSONException

class PrivateMsgSessionsAdapter(
    val context: Context,
    val sessionsList: ArrayList<PrivateMsgSession>,
    val userMap: HashMap<Long, UserInfo>?
) : RecyclerView.Adapter<PrivateMsgSessionsAdapter.PrivateMsgSessionsHolder>() {

    private val cardRoundRadius: Int = context.resources.getDimension(R.dimen.card_round).toInt()

    companion object {
        private const val BADGE_TEXT_COLOR = Color.WHITE
        private val BADGE_BG_COLOR = Color.rgb(207, 75, 95)
        private const val BADGE_TEXT = "  未读 "
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PrivateMsgSessionsHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.cell_user_list, parent, false)
        return PrivateMsgSessionsHolder(view)
    }

    override fun onBindViewHolder(holder: PrivateMsgSessionsHolder, position: Int) {
        if (position < 0 || position >= sessionsList.size)
            return
        val msgContent = sessionsList[position] ?: return

        try {
            if (msgContent.content != null)
                when (msgContent.contentType) {
                    PrivateMessage.TYPE_TEXT -> {
                        holder.contentText.text = msgContent.content.getString("content")
                    }
                    PrivateMessage.TYPE_PIC -> {
                        holder.contentText.text = "[图片消息]"
                    }
                    PrivateMessage.TYPE_VIDEO, PrivateMessage.TYPE_PIC_CARD, PrivateMessage.TYPE_NOMAL_CARD -> {
                        holder.contentText.text = msgContent.content.getString("title")
                    }
                    PrivateMessage.TYPE_TEXT_WITH_VIDEO -> {
                        holder.contentText.text = msgContent.content.getString("reply_content")
                    }
                    PrivateMessage.TYPE_RETRACT -> {
                        holder.contentText.text = "[撤回消息]"
                    }
                    else -> {
                        holder.contentText.text = ""
                    }
                }
            else
                holder.contentText.text = ""

            holder.contentText.ellipsize = TextUtils.TruncateAt.END

            val user = userMap?.get(msgContent.talkerUid)
            if (user != null) {
                if (msgContent.unread > 0 && SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.PRIVATE_MSG_UNREAD_BADGE_ENABLE, false)) {
                    val nameStr = SpannableStringBuilder(user.name)
                    val nameLength = user.name.length
                    nameStr.append(BADGE_TEXT)
                    nameStr.setSpan(
                        RadiusBackgroundSpan(1, cardRoundRadius, BADGE_TEXT_COLOR, BADGE_BG_COLOR),
                        nameLength + 1, nameStr.length, Spanned.SPAN_INCLUSIVE_EXCLUSIVE
                    )
                    holder.nameText.text = nameStr
                } else {
                    holder.nameText.text = user.name
                }
                Glide.with(BiliTerminal.context).asDrawable().load(GlideUtil.url(user.avatar))
                    .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                    .placeholder(R.mipmap.akari)
                    .apply(RequestOptions.circleCropTransform())
                    .into(holder.avatarView)
            }

            holder.itemView.setOnClickListener {
                val intent = Intent(context, PrivateMsgActivity::class.java)
                intent.putExtra("uid", msgContent.talkerUid)
                context.startActivity(intent)
            }
            holder.itemView.setOnLongClickListener {
                val intent = Intent(context, UserInfoActivity::class.java)
                intent.putExtra("mid", msgContent.talkerUid)
                context.startActivity(intent)
                true
            }
        } catch (err: JSONException) {
            Log.e("PrivateMsgUserAdapter", err.toString())
        }
    }

    override fun getItemCount(): Int {
        return sessionsList.size
    }

    class PrivateMsgSessionsHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        lateinit var avatarView: ImageView
        lateinit var nameText: TextView
        lateinit var contentText: TextView

        init {
            avatarView = itemView.findViewById(R.id.userAvatar)
            nameText = itemView.findViewById(R.id.userName)
            contentText = itemView.findViewById(R.id.userDesc)
        }
    }
}