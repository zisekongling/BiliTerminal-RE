package com.RobinNotBad.BiliClient.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.NonNull
import androidx.recyclerview.widget.RecyclerView
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.model.LoginRecord
class LoginRecordAdapter(
    private val context: Context,
    private val recordList: MutableList<LoginRecord>
) : RecyclerView.Adapter<LoginRecordAdapter.ViewHolder>() {

    @NonNull
    override fun onCreateViewHolder(@NonNull parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.cell_login_record, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(@NonNull holder: ViewHolder, position: Int) {
        if (position < 0 || position >= recordList.size) {
            return
        }
        val record = recordList[position] ?: return

        holder.deviceName.text = record.deviceName
        holder.loginType.text = "登录方式：" + record.loginType
        holder.loginTime.text = "登录时间：" + record.loginTime
        holder.location.text = record.location
        holder.ip.text = record.ip
    }

    override fun getItemCount(): Int {
        return if (recordList != null) recordList.size else 0
    }

    class ViewHolder(@NonNull itemView: View) : RecyclerView.ViewHolder(itemView) {
        val deviceName: TextView = itemView.findViewById(R.id.device_name)
        val loginType: TextView = itemView.findViewById(R.id.login_type)
        val loginTime: TextView = itemView.findViewById(R.id.login_time)
        val location: TextView = itemView.findViewById(R.id.location)
        val ip: TextView = itemView.findViewById(R.id.ip)
    }
}