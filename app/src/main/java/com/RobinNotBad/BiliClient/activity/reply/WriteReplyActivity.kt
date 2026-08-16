package com.RobinNotBad.BiliClient.activity.reply

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Pair
import android.widget.EditText
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.EmoteActivity
import com.RobinNotBad.BiliClient.activity.base.BaseActivity
import com.RobinNotBad.BiliClient.api.EmoteApi
import com.RobinNotBad.BiliClient.api.ReplyApi
import com.RobinNotBad.BiliClient.api.VipApi
import com.RobinNotBad.BiliClient.event.ReplyEvent
import com.RobinNotBad.BiliClient.model.Reply
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import com.google.android.material.card.MaterialCardView
import org.greenrobot.eventbus.EventBus
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

class WriteReplyActivity : BaseActivity() {

    companion object {
        private val msgMap = mapOf(
            -101 to "没有登录or登录信息有误？",
            -102 to "账号被封禁！",
            -509 to "请求过于频繁！",
            12015 to "需要评论验证码...？",
            12016 to "包含敏感内容！",
            12025 to "字数过多啦QAQ",
            12035 to "被拉黑了...",
            12051 to "重复评论，请勿刷屏！"
        )
    }

    private lateinit var editText: EditText
    private lateinit var imageText: TextView

    private val emoteLauncher: ActivityResultLauncher<Intent> = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val code = result.resultCode
        val data = result.data
        if (code == RESULT_OK && data != null && data.hasExtra("text")) {
            editText.append(data.getStringExtra("text"))
        }
    }

    private val imageList = ArrayList<String>()
    private val uploadDataList = ArrayList<ReplyApi.UploadImageData>()

    private val imageLauncher: ActivityResultLauncher<Intent> = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val code = result.resultCode
        val data = result.data
        if (code == RESULT_OK && data != null && data.data != null) {
            if (imageList.size >= 9) {
                MsgUtil.showMsg("最多只能添加9张图片喵~")
                return@registerForActivityResult
            }
            addImage(data.data!!)
        }
    }

    private var sent: Boolean = false
    private var dontKyPlease: Boolean = true

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_write_reply)

        if (SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0) == 0L) {
            MsgUtil.showMsg("还没有登录喵~")
            finish()
        }

        val intent = intent
        val oid = intent.getLongExtra("oid", 0)
        val rpid = intent.getLongExtra("rpid", 0)
        val parent = intent.getLongExtra("parent", 0)
        val replyType = intent.getIntExtra("replyType", ReplyApi.REPLY_TYPE_VIDEO)
        val parentSender = intent.getStringExtra("parentSender")
        val pos = intent.getIntExtra("pos", -1)

        editText = findViewById(R.id.editText)
        imageText = findViewById(R.id.imageText)
        val send = findViewById<MaterialCardView>(R.id.send)

        if (parentSender != null && parentSender.isNotEmpty()) {
            editText.setText("回复 @$parentSender :")
            editText.setSelection(editText.text.length)
        }

        send.setOnClickListener {
            if (SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.cookie_refresh, true)) {
                if (!sent) {
                    CenterThreadPool.run {
                        val text = editText.text.toString()
                        if (text.isNotEmpty() || imageList.isNotEmpty()) {
                            if (checkKy(text) && dontKyPlease) {
                                MsgUtil.showDialog("保护措施……", getString(R.string.reply_dont_ky), 15)
                                dontKyPlease = false
                                return@run
                            }
                            try {
                                val pictures = buildPictures()
                                val result = ReplyApi.sendReply(oid, rpid, parent, text, replyType, pictures)
                                val resultCode = result.first
                                val resultReply = result.second

                                sent = true

                                if (resultCode == 0) {
                                    runOnUiThread { MsgUtil.showMsg("发送成功>w<") }
                                    resultReply.forceDelete = true
                                    resultReply.pubTime = "刚刚"
                                    synchronized(uploadDataList) {
                                        for (uploadData in uploadDataList) {
                                            resultReply.pictureList.add(uploadData.image_url)
                                        }
                                    }
                                    EventBus.getDefault().post(ReplyEvent(1, resultReply, pos, oid))
                                    finish()
                                } else {
                                    val toast_msg = "评论发送失败：\n" + (msgMap.getOrDefault(resultCode, resultCode.toString()))
                                    runOnUiThread { MsgUtil.showMsg(toast_msg) }
                                    sent = false
                                }
                            } catch (e: Exception) {
                                runOnUiThread { MsgUtil.err(e) }
                            }
                        } else runOnUiThread { MsgUtil.showMsg("还没输入内容呢~") }
                    }
                } else MsgUtil.showMsg("正在发送中")
            } else
                MsgUtil.showDialog("无法发送", "上一次的Cookie刷新失败了，\n您可能需要重新登录以进行敏感操作", -1)
        }

        findViewById<android.view.View>(R.id.emote).setOnClickListener {
            emoteLauncher.launch(Intent(this, EmoteActivity::class.java).putExtra("from", EmoteApi.BUSINESS_REPLY))
        }

        findViewById<android.view.View>(R.id.image).setOnClickListener {
            // 带图评论仅大会员可用，点击前先检测大会员状态
            CenterThreadPool.run {
                try {
                    val vipInfo = VipApi.getVipInfo()
                    runOnUiThread {
                        if (vipInfo.isVip) {
                            if (imageList.size >= 9) {
                                MsgUtil.showMsg("最多只能添加9张图片喵~")
                            } else {
                                val pickIntent = Intent(Intent.ACTION_GET_CONTENT)
                                pickIntent.type = "image/*"
                                imageLauncher.launch(pickIntent)
                            }
                        } else {
                            MsgUtil.showMsg("带图评论仅大会员可用喵~")
                        }
                    }
                } catch (e: Exception) {
                    runOnUiThread { MsgUtil.err(e) }
                }
            }
        }
    }

    private fun addImage(uri: Uri) {
        imageList.add(uri.toString())
        updateImageText()
        CenterThreadPool.run {
            try {
                val compressed = compressImage(uri)
                val data = ReplyApi.uploadReplyImage(compressed, System.currentTimeMillis().toString() + ".jpg").getOrNull()
                if (data == null) {
                    runOnUiThread {
                        MsgUtil.showMsg("图片上传失败")
                        imageList.remove(uri.toString())
                        updateImageText()
                    }
                    return@run
                }
                synchronized(uploadDataList) {
                    uploadDataList.add(data)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    MsgUtil.showMsg("图片处理失败")
                    imageList.remove(uri.toString())
                    updateImageText()
                }
            }
        }
    }

    private fun compressImage(uri: Uri): ByteArray {
        val inputStream: InputStream = contentResolver.openInputStream(uri) ?: throw IOException("无法读取图片")
        val bitmap: Bitmap = BitmapFactory.decodeStream(inputStream)
        inputStream.close()
        if (bitmap == null) throw IOException("解码图片失败")
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
        bitmap.recycle()
        return outputStream.toByteArray()
    }

    private fun buildPictures(): String {
        val jsonArray = JSONArray()
        synchronized(uploadDataList) {
            for (data in uploadDataList) {
                val jsonObject = JSONObject()
                jsonObject.put("img_src", data.image_url)
                jsonObject.put("img_width", data.image_width)
                jsonObject.put("img_height", data.image_height)
                jsonObject.put("img_size", data.img_size)
                jsonArray.put(jsonObject)
            }
        }
        return jsonArray.toString()
    }

    private fun updateImageText() {
        val count = imageList.size
        imageText.text = if (count == 0) getString(R.string.btn_image) else getString(R.string.btn_image) + "($count)"
    }

    private fun checkKy(str: String): Boolean {
        if (str.contains("哔哩终端")) return true
        if (str.contains("终端")) {
            return str.contains("表") || str.contains("b站") || str.contains("B站") || str.contains("bili") || str.contains("哔")
        }
        return false
    }
}
