package com.RobinNotBad.BiliClient.activity

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import com.RobinNotBad.BiliClient.BiliTerminal
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.base.BaseActivity
import com.RobinNotBad.BiliClient.util.StringUtil
import com.google.android.material.card.MaterialCardView
import org.json.JSONException
import org.json.JSONObject

class ShowTextActivity : BaseActivity() {

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_simple_text)

        val intent = intent
        var content = intent.getStringExtra("content")

        val pagename = findViewById<TextView>(R.id.pageName)
        pagename.text = intent.getStringExtra("title")


        val textView = findViewById<TextView>(R.id.textView)

        if (content != null) {
            content = content.replace("[extra_insert]", "<extra_insert>")
            val extraStart = content.indexOf("<extra_insert>")
            if (extraStart != -1) {
                try {
                    val jsonObject = JSONObject(content.substring(extraStart + 14))
                    if (jsonObject.getString("type") == "video") {
                        val videoCard =
                            View.inflate(this, R.layout.cell_message_reply, findViewById(R.id.linearLayout))
                        val cardView = videoCard.findViewById<MaterialCardView>(R.id.cardView)
                        cardView.setOnClickListener { BiliTerminal.jumpToVideo(this, jsonObject.optString("content")) }
                        val titleVideo = videoCard.findViewById<TextView>(R.id.content)
                        titleVideo.text = jsonObject.optString("title")
                        content = content.substring(0, extraStart)
                    } else {
                        content = "$content\n文本中存在无法识别的附加信息，请更新版本查看"
                    }
                } catch (e: JSONException) {
                    content = "$content\n文本中存在无法识别的附加信息，请更新版本查看"
                }
            }
        }
        if (intent.data != null) {
            content = intent.data.toString()
        }

        textView.text = content
        StringUtil.setCopy(textView)
    }
}