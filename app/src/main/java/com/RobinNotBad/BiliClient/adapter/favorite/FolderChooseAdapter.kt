package com.RobinNotBad.BiliClient.adapter.favorite

import android.app.Activity
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.api.FavoriteApi
import com.RobinNotBad.BiliClient.ui.theme.BiliColors
import com.RobinNotBad.BiliClient.ui.theme.ThemeManager
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import com.RobinNotBad.BiliClient.util.ToolsUtil
import com.google.android.material.card.MaterialCardView
import org.json.JSONException
import java.io.IOException

class FolderChooseAdapter(
    private val context: Context,
    private val folderList: ArrayList<String>,
    private val fidList: ArrayList<Long>,
    private val chooseState: ArrayList<Boolean>,
    private val countList: ArrayList<Int>,
    private val maxCountList: ArrayList<Int>,
    private val aid: Long
) : RecyclerView.Adapter<FolderChooseAdapter.FolderHolder>() {

    var adding: Boolean = false
    var added: Boolean = false
    var changed: Boolean = false

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FolderHolder {
        val view = LayoutInflater.from(this.context).inflate(R.layout.cell_folder_choose, parent, false)
        return FolderHolder(view)
    }

    override fun onBindViewHolder(holder: FolderHolder, position: Int) {
        if (position < 0 || position >= folderList.size)
            return
        if (position >= chooseState.size || position >= fidList.size)
            return
        if (position >= countList.size || position >= maxCountList.size)
            return

        val cardView = holder.itemView as MaterialCardView

        holder.folder_name.text = folderList[position]
        holder.count.text = countList[position].toString() + "/" + maxCountList[position]
        setCardView(cardView, chooseState[position])

        holder.itemView.setOnClickListener {
            if (!adding && position < chooseState.size && position < fidList.size) {
                adding = true
                cardView.strokeColor = BiliColors.PrimaryTransparent
                cardView.strokeWidth = ToolsUtil.dp2px(1f)

                if (chooseState[position]) {
                    CenterThreadPool.run {
                        try {
                            val result = FavoriteApi.deleteFavorite(aid, fidList[position])
                            adding = false
                            if (result == 0) {
                                chooseState[position] = false
                                (context as Activity).runOnUiThread { setCardView(cardView, false) }
                                changed = true
                            } else
                                (context as Activity).runOnUiThread {
                                    MsgUtil.showMsg("删除失败！错误码：" + result)
                                    setCardView(cardView, true)
                                }
                        } catch (e: IOException) {
                            e.printStackTrace()
                        } catch (e: JSONException) {
                            e.printStackTrace()
                        }
                    }
                } else {
                    cardView.strokeColor = BiliColors.Gray
                    cardView.strokeWidth = ToolsUtil.dp2px(0.1f)
                    CenterThreadPool.run {
                        try {
                            val result = FavoriteApi.addFavorite(aid, fidList[position])
                            adding = false
                            if (result == 0) {
                                chooseState[position] = true
                                (context as Activity).runOnUiThread { setCardView(cardView, true) }
                                changed = true
                                added = true
                            } else
                                (context as Activity).runOnUiThread {
                                    MsgUtil.showMsg("添加失败！错误码：" + result)
                                    setCardView(cardView, false)
                                }
                            if (SharedPreferencesUtil.getBoolean("fav_single", false))
                                (context as Activity).finish()
                        } catch (e: IOException) {
                            e.printStackTrace()
                        } catch (e: JSONException) {
                            e.printStackTrace()
                        }
                    }
                }
            }
        }

    }

    override fun getItemCount(): Int {
        return folderList.size
    }

    class FolderHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val folder_name: TextView = itemView.findViewById(R.id.text)
        val count: TextView = itemView.findViewById(R.id.text_count)
    }

    private fun setCardView(cardView: MaterialCardView, bool: Boolean) {
        if (bool) {
            cardView.strokeColor = ThemeManager.PRIMARY
            cardView.strokeWidth = ToolsUtil.dp2px(1f)
        } else {
            cardView.strokeColor =
                ThemeManager.BORDER
            cardView.strokeWidth = ToolsUtil.dp2px(0.1f)
        }
    }

    fun isAllDeleted(): Boolean {
        for (b in chooseState) {
            if (b)
                return false
        }
        return true
    }
}