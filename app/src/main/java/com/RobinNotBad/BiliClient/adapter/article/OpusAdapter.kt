package com.RobinNotBad.BiliClient.adapter.article

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.RobinNotBad.BiliClient.BiliTerminal
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.model.Opus
import com.RobinNotBad.BiliClient.util.GlideUtil
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.TerminalContext
import com.RobinNotBad.BiliClient.util.ToolsUtil
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions

class OpusAdapter(
    private var context: Context,
    private var opusList: ArrayList<Opus>
) : RecyclerView.Adapter<OpusAdapter.OpusHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OpusHolder {
        val view = LayoutInflater.from(this.context).inflate(R.layout.cell_opus, parent, false)
        return OpusHolder(view)
    }

    override fun onBindViewHolder(holder: OpusHolder, position: Int) {
        if (position < 0 || position >= opusList.size)
            return
        val opus = opusList[position] ?: return

        holder.favTimeText.text = opus.pubTime
        holder.titleText.text = opus.title

        val coverUrl = GlideUtil.url(opus.cover)
        if (coverUrl != holder.lastCoverUrl) {
            holder.lastCoverUrl = coverUrl
            Glide.with(BiliTerminal.context).load(coverUrl)
                .transition(GlideUtil.getTransitionOptions())
                .placeholder(R.mipmap.placeholder)
                .apply(RequestOptions.bitmapTransform(RoundedCorners(ToolsUtil.dp2px(5f))))
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .into(holder.coverView)
        }

        if (opus.content != null && opus.content == "内容失效") {
            holder.itemView.setOnClickListener { MsgUtil.showMsg("内容失效，无法打开") }
        } else {
            holder.itemView
                .setOnClickListener { TerminalContext.getInstance().enterOpusDetailPage(context, opus.id) }
        }
    }

    override fun onViewRecycled(holder: OpusHolder) {
        holder.lastCoverUrl = null
        super.onViewRecycled(holder)
    }

    override fun getItemCount(): Int {
        return opusList.size
    }

    class OpusHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        lateinit var coverView: ImageView
        lateinit var favTimeText: TextView
        lateinit var titleText: TextView
        var lastCoverUrl: String? = null

        init {
            coverView = itemView.findViewById(R.id.img_cover)
            favTimeText = itemView.findViewById(R.id.text_favTime)
            titleText = itemView.findViewById(R.id.text_title)
        }

    }
}