package com.RobinNotBad.BiliClient.adapter.user

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.model.UserInfo

class UpListAdapter(
    context: Context,
    userList: ArrayList<UserInfo>
) : UserListAdapter(context, userList) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserListAdapter.Holder {
        val view = LayoutInflater.from(context).inflate(R.layout.cell_up_list, parent, false)
        return Holder(view)
    }
}