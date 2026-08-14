package com.RobinNotBad.BiliClient.activity.user.favorite

import android.annotation.SuppressLint
import android.os.Bundle
import android.widget.EditText

import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.base.BaseActivity
import com.RobinNotBad.BiliClient.api.FavoriteApi
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.MsgUtil

import com.google.android.material.card.MaterialCardView

class FavoriteFolderCreateActivity : BaseActivity() {

    private lateinit var editTitle: EditText
    private lateinit var editIntro: EditText
    private lateinit var btnSave: MaterialCardView

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_favorite_folder_edit)

        editTitle = findViewById(R.id.editTitle)
        editIntro = findViewById(R.id.editIntro)
        btnSave = findViewById(R.id.btnSave)
        val btnDelete = findViewById<MaterialCardView>(R.id.btnDelete)

        btnDelete.visibility = android.view.View.GONE
        setPageName("创建收藏夹")

        btnSave.setOnClickListener { createFolder() }
    }

    private fun createFolder() {
        val title = editTitle.text.toString().trim()
        if (title.isEmpty()) {
            MsgUtil.showMsg("请输入收藏夹名称")
            return
        }

        val intro = editIntro.text.toString().trim()
        btnSave.isClickable = false

        CenterThreadPool.run {
            try {
                val result = FavoriteApi.addFolder(title, intro, 0)
                runOnUiThread {
                    btnSave.isClickable = true
                    if (result == 0) {
                        MsgUtil.showMsg("创建成功")
                        setResult(RESULT_OK)
                        finish()
                    } else {
                        MsgUtil.showMsg("创建失败，错误码：$result")
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    btnSave.isClickable = true
                    report(e)
                }
            }
        }
    }
}