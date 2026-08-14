package com.RobinNotBad.BiliClient.adapter.message

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.model.MessageCard

class NoticeAdapter(
    var context: Context,
    var messageList: List<MessageCard>?
) : RecyclerView.Adapter<NoticeHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NoticeHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.cell_message, parent, false)
        return NoticeHolder(view)
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: NoticeHolder, position: Int) {
        if (position < 0 || position >= (messageList?.size ?: 0))
            return
        val message = messageList!![position] ?: return
        holder.showMessage(message, context)
    }

    override fun onViewRecycled(holder: NoticeHolder) {
        holder.extraCard.removeAllViews()
        super.onViewRecycled(holder)
    }

    override fun getItemCount(): Int {
        return messageList?.size ?: 0
    }
}