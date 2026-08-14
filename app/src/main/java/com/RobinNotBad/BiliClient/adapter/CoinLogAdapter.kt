package com.RobinNotBad.BiliClient.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.NonNull
import androidx.recyclerview.widget.RecyclerView
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.model.CoinLog
class CoinLogAdapter(
    private val context: Context,
    private val logList: MutableList<CoinLog>
) : RecyclerView.Adapter<CoinLogAdapter.ViewHolder>() {

    @NonNull
    override fun onCreateViewHolder(@NonNull parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.cell_coin_log, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(@NonNull holder: ViewHolder, position: Int) {
        if (position < 0 || position >= logList.size) {
            return
        }
        val log = logList[position] ?: return

        val deltaText = if (log.delta > 0) "+" + log.delta else log.delta.toString()
        holder.delta.text = deltaText
        holder.reason.text = log.reason
        holder.time.text = log.time
    }

    override fun getItemCount(): Int {
        return if (logList != null) logList.size else 0
    }

    class ViewHolder(@NonNull itemView: View) : RecyclerView.ViewHolder(itemView) {
        val delta: TextView = itemView.findViewById(R.id.delta)
        val reason: TextView = itemView.findViewById(R.id.reason)
        val time: TextView = itemView.findViewById(R.id.time)
    }
}