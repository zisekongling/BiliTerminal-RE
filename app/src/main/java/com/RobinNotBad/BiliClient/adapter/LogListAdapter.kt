package com.RobinNotBad.BiliClient.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.NonNull
import androidx.recyclerview.widget.RecyclerView
import com.RobinNotBad.BiliClient.R

/**
 * 经验 / 硬币变化记录的公共适配器。
 *
 * 合并自原 `CoinLogAdapter` 与 `ExpLogAdapter`：两者的布局文件曾逐字节相同
 * （`cell_coin_log.xml` ≡ `cell_exp_log.xml`，现统一为 `cell_log.xml`），
 * 代码唯一差异是 delta 文案与模型类型，因此改为泛型 + 行数据映射。
 *
 * @param logList  流水数据，元素类型由调用方决定
 * @param bindRow  把一条流水映射为一行要展示的文案
 */
class LogListAdapter<T>(
    private val context: Context,
    private val logList: MutableList<T>,
    private val bindRow: (T) -> Row
) : RecyclerView.Adapter<LogListAdapter.ViewHolder>() {

    /** 一行要展示的三个字段。 */
    data class Row(val delta: String, val reason: String?, val time: String?)

    @NonNull
    override fun onCreateViewHolder(@NonNull parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.cell_log, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(@NonNull holder: ViewHolder, position: Int) {
        if (position < 0 || position >= logList.size) return
        val item = logList[position] ?: return

        val row = bindRow(item)
        holder.delta.text = row.delta
        holder.reason.text = row.reason
        holder.time.text = row.time
    }

    override fun getItemCount(): Int = logList.size

    class ViewHolder(@NonNull itemView: View) : RecyclerView.ViewHolder(itemView) {
        val delta: TextView = itemView.findViewById(R.id.delta)
        val reason: TextView = itemView.findViewById(R.id.reason)
        val time: TextView = itemView.findViewById(R.id.time)
    }
}
