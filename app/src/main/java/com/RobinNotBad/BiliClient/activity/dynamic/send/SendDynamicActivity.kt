package com.RobinNotBad.BiliClient.activity.dynamic.send

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.EmoteActivity
import com.RobinNotBad.BiliClient.activity.base.BaseActivity
import com.RobinNotBad.BiliClient.adapter.dynamic.DynamicHolder
import com.RobinNotBad.BiliClient.adapter.video.VideoCardHolder
import com.RobinNotBad.BiliClient.api.EmoteApi
import com.RobinNotBad.BiliClient.model.Dynamic
import com.RobinNotBad.BiliClient.model.VideoInfo
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import com.RobinNotBad.BiliClient.util.TerminalContext
import com.google.android.material.card.MaterialCardView

class SendDynamicActivity : BaseActivity() {

    private lateinit var editText: EditText

    private val emoteLauncher: ActivityResultLauncher<Intent> = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val code = result.resultCode
        val data = result.data
        if (code == RESULT_OK && data != null && data.hasExtra("text")) {
            editText.append(data.getStringExtra("text"))
        }
    }

    @SuppressLint("InflateParams")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        asyncInflate(R.layout.activity_send_dynamic) { layoutView, resId ->

            if (SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0) == 0L) {
                setResult(RESULT_CANCELED)
                finish()
                MsgUtil.showMsg("还没有登录喵~")
            }

            editText = findViewById(R.id.editText)
            val send = findViewById<MaterialCardView>(R.id.send)

            val extraCard = findViewById<FrameLayout>(R.id.forwardCard)
            var video: VideoInfo? = null
            var forward: Dynamic? = null
            if (TerminalContext.getInstance().getForwardContent() is VideoInfo) {
                video = TerminalContext.getInstance().getForwardContent() as VideoInfo
            } else {
                forward = TerminalContext.getInstance().getForwardContent() as Dynamic?
            }
            if (forward != null) {
                val childCard = View.inflate(this, R.layout.cell_dynamic, extraCard)
                val holder = DynamicHolder(childCard, this, false)
                holder.showDynamic(this, forward, false)
            } else if (video != null) {
                val holder = VideoCardHolder(LayoutInflater.from(this).inflate(R.layout.cell_video_list, extraCard))
                holder.showVideoCard(video.toCard(), this)
            }

            send.setOnClickListener {
                if (SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.cookie_refresh, true)) {
                    val text = editText.text.toString()
                    val result = Intent()
                    val bundle = this@SendDynamicActivity.intent.extras
                    if (bundle != null) result.putExtras(bundle)
                    result.putExtra("text", text)
                    setResult(RESULT_OK, result)
                    finish()
                } else
                    MsgUtil.showDialog("无法发送", "上一次的Cookie刷新失败了，\n您可能需要重新登录以进行敏感操作", -1)
            }

            findViewById<View>(R.id.emote).setOnClickListener {
                emoteLauncher.launch(Intent(this, EmoteActivity::class.java).putExtra("from", EmoteApi.BUSINESS_DYNAMIC))
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        TerminalContext.getInstance().setForwardContent(null)
    }
}