package com.RobinNotBad.BiliClient.activity.reply

import com.RobinNotBad.BiliClient.util.UiText

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
import com.RobinNotBad.BiliClient.activity.ImagePickerActivity
import com.RobinNotBad.BiliClient.activity.EmoteActivity
import com.RobinNotBad.BiliClient.activity.base.BaseActivity
import com.RobinNotBad.BiliClient.api.ApiErrorText
import com.RobinNotBad.BiliClient.api.EmoteApi
import com.RobinNotBad.BiliClient.api.ImageApi
import com.RobinNotBad.BiliClient.api.PrivateMsgApi
import com.RobinNotBad.BiliClient.api.ReplyApi
import com.RobinNotBad.BiliClient.event.ReplyEvent
import com.RobinNotBad.BiliClient.model.PrivateMessage
import com.RobinNotBad.BiliClient.model.Reply
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import com.RobinNotBad.BiliClient.util.view.TextInsertion
import com.google.android.material.card.MaterialCardView
import org.greenrobot.eventbus.EventBus
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.Locale

class WriteReplyActivity : BaseActivity() {

    companion object {
        private val msgMap = mapOf(
            -101 to UiText.of("没有登录or登录信息有误？"),
            -102 to UiText.of("账号被封禁！"),
            -509 to UiText.of("请求过于频繁！"),
            12015 to UiText.of("需要评论验证码...？"),
            12016 to UiText.of("包含敏感内容！"),
            12025 to UiText.of("字数过多啦QAQ"),
            12035 to UiText.of("被拉黑了..."),
            12051 to UiText.of("重复评论，请勿刷屏！"),
            // 12066 是服务端对带图评论的拒绝码，本地快照与上游 collector 都没有收录
            //（上游 action.md 该表末尾写「其他错误码有待补充」），此处只保证不再显示裸数字
            12066 to UiText.of("图片信息异常，请重新选图后再试")
        )

        // 单张图片的绝对上限，超过就不传了（手表上传大文件基本必失败，还容易 OOM）
        private const val MAX_IMAGE_SIZE = 25L * 1024 * 1024
        // GIF 不能重新编码，只能原样透传，所以额外给一个更小的上限，避免动图把上传拖垮
        private const val GIF_MAX_SIZE = 20L * 1024 * 1024
        // PNG 原样透传的上限；超过这个体积还是压成 JPEG，否则上传体验会崩
        private const val PNG_MAX_SIZE = 8L * 1024 * 1024
        // 压缩时的最长边上限，避免手表直接解码原图就 OOM
        private const val MAX_COMPRESS_EDGE = 2048

        /**
         * 私信模式的接收方 uid。
         *
         * 写私信和写评论的界面、表情、选图、发送流程几乎一样，所以共用这一个页面：
         * 带了这个 extra（> 0）就是「写私信」，否则照旧是写评论。
         */
        const val EXTRA_PRIVATE_UID = "private_uid"
    }

    private lateinit var editText: EditText
    private lateinit var imageText: TextView

    /** 私信模式的接收方 uid；0 表示这个页面是写评论。 */
    private var privateUid: Long = 0L

    private val isPrivateMsg: Boolean
        get() = privateUid > 0L

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
            // 私信一条消息只能带一张图：再选一张是**替换**，所以私信不按上限拦截——
            // 弹一句「只能发一张」再把刚选的图丢掉，等于让人多点一次才能换图；评论才按 9 张拦
            if (!isPrivateMsg && imageList.size >= ImageApi.MAX_IMAGE_COUNT) {
                MsgUtil.showMsg(UiText.of("最多只能添加9张图片喵~"))
                return@registerForActivityResult
            }
            val uri = data.data!!
            // 私信一条消息只能带一张图，且图片在点发送时才压才传（见 sendPrivate），
            // 所以这里只是把选中的图替换进来，不预上传
            if (isPrivateMsg) {
                imageList.clear()
                imageList.add(uri.toString())
                updateImageText()
            } else addImage(uri)
        }
    }

    /**
     * 发送闸门。点下发送的**那一刻**就置位，用来挡住「请求还没回来又点一次」——
     * 原来的 `sent` 是等请求返回后才置 true，等待期里连点会发出两条评论。
     * 在后台线程里读写，所以加 @Volatile。
     */
    @Volatile
    private var sending: Boolean = false

    /**
     * 仍在上传中的图片张数。
     *
     * 选完图 [imageList] 里立刻就有这张图了，但压缩 + 上传还在后台跑。它挡的是一个更隐蔽的问题：
     * 图还没传完就发送时，[buildPictures] 只能拼出已成功的那几张，评论会**少图发出且毫无提示**。
     * 读写在主线程（[addImage] 由选图回调调用、减一也回到主线程），所以不需要加锁。
     */
    private var pendingUploads: Int = 0

    private var dontKyPlease: Boolean = true

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_write_reply)

        if (SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0) == 0L) {
            MsgUtil.showMsg(UiText.of("还没有登录喵~"))
            finish()
        }

        val intent = intent
        val oid = intent.getLongExtra("oid", 0)
        val rpid = intent.getLongExtra("rpid", 0)
        val parent = intent.getLongExtra("parent", 0)
        val replyType = intent.getIntExtra("replyType", ReplyApi.REPLY_TYPE_VIDEO)
        val parentSender = intent.getStringExtra("parentSender")
        val pos = intent.getIntExtra("pos", -1)
        privateUid = intent.getLongExtra(EXTRA_PRIVATE_UID, 0L)

        editText = findViewById(R.id.editText)
        imageText = findViewById(R.id.imageText)
        val send = findViewById<MaterialCardView>(R.id.send)

        if (isPrivateMsg) {
            // 复用同一个页面：只换标题与输入提示，发送链路在 sendPrivate 里单独走
            findViewById<TextView>(R.id.pageName).setText(R.string.pagename_write_private)
            editText.hint = getString(R.string.hint_private_message)
        }

        // 手表输入法把回车当「完成 / 下一项」，打不出换行，只能靠这张卡往光标处插一个
        findViewById<android.view.View>(R.id.newline).setOnClickListener {
            TextInsertion.insertNewline(editText)
        }

        if (parentSender != null && parentSender.isNotEmpty()) {
            editText.setText("回复 @$parentSender :")
            editText.setSelection(editText.text.length)
        }

        send.setOnClickListener {
            // 私信没有 pictures 参数、也不走评论接口，整条链路都在 sendPrivate 里
            if (isPrivateMsg) {
                sendPrivate()
                return@setOnClickListener
            }
            if (SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.cookie_refresh, true)) {
                if (sending) {
                    MsgUtil.showMsg(UiText.of("正在发送中"))
                    return@setOnClickListener
                }
                // 图还没传完就发送 = 评论少图且没有任何提示，必须在这里拦住
                if (!ReplyApi.canSendReply(pendingUploads)) {
                    MsgUtil.showMsg(ReplyApi.uploadPendingTip(pendingUploads))
                    return@setOnClickListener
                }
                sending = true
                CenterThreadPool.run {
                    try {
                        val text = editText.text.toString()
                        if (text.isEmpty() && imageList.isEmpty()) {
                            runOnUiThread { MsgUtil.showMsg(UiText.of("还没输入内容呢~")) }
                            return@run
                        }
                        if (checkKy(text) && dontKyPlease) {
                            MsgUtil.showDialog("保护措施……", getString(R.string.reply_dont_ky), 15)
                            dontKyPlease = false
                            return@run
                        }
                        // 进后台先给一次反馈：原先这段等待期界面毫无变化，点一下像没反应
                        runOnUiThread { MsgUtil.showMsg(UiText.of("正在发送…")) }
                        try {
                            val pictures = buildPictures()
                            val result = ReplyApi.sendReplyDetailed(oid, rpid, parent, text, replyType, pictures)
                            val resultReply = result.reply

                            if (result.code == 0) {
                                // 反馈 #85：/x/v2/reply/add 可能在评论还没进公开列表（审核中 /
                                // 尚未同步）时就回 code=0。以前这里直接报「发送成功」并本地插一条，
                                // 用户一刷新评论区就发现评论没了，体验上就是「发不出去」。
                                // 所以一级评论发出后先回评论区核实；查不到就如实说「暂未确认」，
                                // 并且**不插入本地列表、不关页面**，让用户能稍后自己刷新确认。
                                val isRootComment = rpid == 0L && parent == 0L
                                if (isRootComment && result.rpid > 0
                                    && !ReplyApi.isReplyVisible(oid, result.rpid, replyType)
                                ) {
                                    runOnUiThread {
                                        MsgUtil.showMsgLong(
                                            UiText.of("评论已提交，但评论区暂时查不到（可能正在审核）。稍后刷新看看，先别重复发送")
                                        )
                                    }
                                    return@run
                                }
                                if (resultReply != null) {
                                    runOnUiThread { MsgUtil.showMsg(UiText.of("发送成功>w<")) }
                                    resultReply.forceDelete = true
                                    resultReply.pubTime = UiText.of("刚刚")
                                    synchronized(uploadDataList) {
                                        for (uploadData in uploadDataList) {
                                            resultReply.pictureList.add(uploadData.image_url)
                                        }
                                    }
                                    EventBus.getDefault().post(ReplyEvent(1, resultReply, pos, oid))
                                    finish()
                                } else {
                                    // 服务端成功但没回 reply：评论已经发出去了，别报「失败」让用户重发
                                    runOnUiThread { MsgUtil.showMsg(UiText.of("发送成功>w<")) }
                                    finish()
                                }
                            } else {
                                // 已知码用本地翻译；未收录的码（如 12066）把服务端原话带上，
                                // 不再只显示一个数字
                                val reason = msgMap[result.code]
                                    ?: result.message.ifEmpty { result.code.toString() }
                                runOnUiThread {
                                    MsgUtil.showMsg(UiText.of("评论发送失败：\n") + reason)
                                }
                            }
                        } catch (e: Exception) {
                            runOnUiThread { MsgUtil.err(e) }
                        }
                    } finally {
                        // 成功、失败、还是上面几个提前 return，都要把闸门松开，
                        // 否则用户在这条评论之后再想发就永远被挡
                        sending = false
                    }
                }
            } else
                MsgUtil.showDialog(getString(R.string.text_cannot_send), getString(R.string.msg_cookie_refresh_failed), -1)
        }

        findViewById<android.view.View>(R.id.emote).setOnClickListener {
            // 表情包分业务：私信要 IM 的，评论用 reply 的，不然发出去是另一个表情集
            val business = if (isPrivateMsg) EmoteApi.BUSINESS_IM else EmoteApi.BUSINESS_REPLY
            emoteLauncher.launch(Intent(this, EmoteActivity::class.java).putExtra("from", business))
        }

        // 图片评论只对「一级评论」开放：B 站的楼中楼回复（root/parent 非 0）不支持 pictures 参数，
        // 传了也会被服务端忽略。早先这里的图片入口对楼中楼同样显示，用户选图、等压缩上传、
        // 发出去却没有图，白等一场还以为是发送失败。所以按 rpid/parent 判一级评论，
        // 不是一级评论就把入口整个收起来（imageText 一并隐藏，否则会留一个孤零零的「图片」）。
        // 私信不受这条限制——私信接口本来就带图，只是上限一张。
        val isRootCommentBox = rpid == 0L && parent == 0L
        val imageEntry = findViewById<android.view.View>(R.id.image)
        if (!isPrivateMsg && !isRootCommentBox) {
            imageEntry.visibility = android.view.View.GONE
            imageText.visibility = android.view.View.GONE
        } else imageEntry.setOnClickListener {
            // 不再按大会员前置拦截：图片评论的权限由服务端判定，客户端拦只会把
            // 「服务端其实允许」的用户挡在门外（26.11.02 用户点名：发图不需要大会员）。
            // 真正被拒时服务端会回错误码，发送失败分支会把码/原话展示出来。
            // 私信一张图是「替换」，所以不做上限拦截；评论满 9 张才提示
            if (!isPrivateMsg && imageList.size >= ImageApi.MAX_IMAGE_COUNT) {
                MsgUtil.showMsg(UiText.of("最多只能添加9张图片喵~"))
            } else {
                // 内置选图页（反馈 #53），页内可退回系统选择器
                imageLauncher.launch(ImagePickerActivity.intent(this))
            }
        }
    }

    /**
     * 私信发送。
     *
     * 与评论的关键差别：私信接口没有 pictures 参数，一条消息只能带一张图，所以这里是
     * 「先压图先上传 → 发图片消息 → 再发文字消息」，图传不上去就整条不发——
     * 半截发出去（图没发、字发了）用户更难收拾。
     */
    private fun sendPrivate() {
        if (!SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.cookie_refresh, true)) {
            MsgUtil.showDialog(getString(R.string.text_cannot_send), getString(R.string.msg_cookie_refresh_failed), -1)
            return
        }
        if (sending) {
            MsgUtil.showMsg(UiText.of("正在发送中"))
            return
        }
        sending = true
        CenterThreadPool.run {
            try {
                val text = editText.text.toString()
                val imageUri = imageList.firstOrNull()
                if (text.isEmpty() && imageUri == null) {
                    runOnUiThread { MsgUtil.showMsg(UiText.of("还没输入内容呢~")) }
                    return@run
                }
                runOnUiThread { MsgUtil.showMsg(UiText.of("正在发送…")) }
                val sender = SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0L)

                if (imageUri != null) {
                    runOnUiThread { MsgUtil.showMsg(UiText.of("正在上传图片喵…")) }
                    val prepared = ImageApi.prepareImage(this@WriteReplyActivity, Uri.parse(imageUri))
                    val uploaded = ImageApi.uploadImage(
                        prepared.data, prepared.fileName, prepared.mimeType, ImageApi.BIZ_MESSAGE
                    ).getOrThrow()
                    val content = PrivateMsgApi.buildImageContentOfKb(
                        uploaded.url, uploaded.width, uploaded.height, uploaded.size,
                        PrivateMsgApi.imageTypeOf(prepared.mimeType)
                    )
                    val error = privateSend(sender, PrivateMessage.TYPE_PIC, content.toString())
                    if (error != null) {
                        runOnUiThread { MsgUtil.showMsg(getString(R.string.uitext_writereplyactivity_024, error)) }
                        return@run
                    }
                }

                if (text.isNotEmpty()) {
                    val content = JSONObject().put("content", text).toString()
                    val error = privateSend(sender, PrivateMessage.TYPE_TEXT, content)
                    if (error != null) {
                        runOnUiThread { MsgUtil.showMsg(getString(R.string.uitext_writereplyactivity_024, error)) }
                        return@run
                    }
                }

                runOnUiThread {
                    MsgUtil.showMsg(UiText.of("发送成功"))
                    // 私信页收到 RESULT_OK 就重拉一次消息列表，用户不用自己刷新
                    setResult(RESULT_OK)
                    finish()
                }
            } catch (e: Exception) {
                runOnUiThread { MsgUtil.err(e) }
            } finally {
                sending = false
            }
        }
    }

    /** 发一条私信消息；成功返回 null，失败返回给用户看的原因。 */
    private fun privateSend(senderUid: Long, msgType: Int, content: String): String? {
        val result = PrivateMsgApi.sendMsg(senderUid, privateUid, msgType, System.currentTimeMillis() / 1000, content)
        val code = result.optInt("code", -1)
        if (code == 0) return null
        return result.optString("message").ifEmpty { code.toString() }
    }

    private fun addImage(uri: Uri) {
        imageList.add(uri.toString())
        pendingUploads++
        updateImageText()
        CenterThreadPool.run {
            try {
                val prepared = prepareImage(uri)
                // 文件名与 MIME 都按图片真实格式传，否则服务端一律按 JPEG 解析，
                // GIF 会变成静帧、PNG 透明通道会被填成底色
                // biz 用 BIZ_DYNAMIC（new_dyn）：评论的 pictures 只认这个图床目录
                val uploadResult = ReplyApi.uploadReplyImage(
                    prepared.raw, prepared.fileName, prepared.mimeType, ReplyApi.BIZ_DYNAMIC
                )
                val data = uploadResult.getOrNull()
                if (data == null) {
                    // 服务端失败只给错误码：翻成人话再弹，别让用户对着「图片上传失败」猜
                    var failText = UiText.of("图片上传失败")
                    uploadResult.onFailure { e ->
                        if (e is ReplyApi.UploadException) {
                            failText = ApiErrorText.uploadFailed(e.code)
                        }
                    }
                    runOnUiThread {
                        MsgUtil.showMsg(failText)
                        imageList.remove(uri.toString())
                    }
                    return@run
                }
                synchronized(uploadDataList) {
                    uploadDataList.add(data)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    MsgUtil.showMsg(UiText.of("图片处理失败"))
                    imageList.remove(uri.toString())
                }
            } finally {
                // 不管成功、失败还是提前 return，都要把「上传中」计数放开，
                // 否则这个计数永远回不到 0、发送会被永久拦住
                runOnUiThread {
                    pendingUploads--
                    updateImageText()
                }
            }
        }
    }

    /** 待上传的图片：raw 是原始字节，fileName/mimeType 决定服务端按什么格式解析。 */
    private class PreparedImage(val raw: ByteArray, val fileName: String, val mimeType: String)

    /**
     * 准备要上传的图片。
     *
     * 不能一律解码后压成 JPEG：那样 GIF 动图会变成一张静帧、PNG 的透明通道会被填成黑/白底，
     * 而这两类都是评论里很常见的图。所以这里按真实类型分流：
     * - GIF：动图无法用 [Bitmap] 重新编码，只能原样透传（超过 20MB 才拒绝）
     * - PNG 且不超过 8MB：原样透传，保住透明通道
     * - 其余（过大的 PNG、JPEG、WEBP 等）：按最长边采样后压成 JPEG 90
     */
    private fun prepareImage(uri: Uri): PreparedImage {
        val type = resolveImageType(uri)
        val now = System.currentTimeMillis()

        // 先按文件描述符探一下体积，超大图直接拒绝，避免在手表上把整张图读进内存就 OOM
        val declaredSize = getDeclaredSize(uri)
        if (declaredSize > MAX_IMAGE_SIZE) throw IOException("图片过大（超过25MB）")

        val raw = readAllBytes(uri)
        if (raw.size > MAX_IMAGE_SIZE) throw IOException("图片过大（超过25MB）")

        if (type == "image/gif") {
            if (raw.size > GIF_MAX_SIZE) throw IOException("GIF过大（超过20MB），请换一张")
            return PreparedImage(raw, "img_$now.gif", "image/gif")
        }
        if (type == "image/png" && raw.size <= PNG_MAX_SIZE) {
            return PreparedImage(raw, "img_$now.png", "image/png")
        }

        val bitmap = decodeScaled(raw) ?: throw IOException("解码图片失败")
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
        bitmap.recycle()
        return PreparedImage(outputStream.toByteArray(), "img_$now.jpg", "image/jpeg")
    }

    /**
     * 判断图片真实类型。优先用 content provider 声明的类型；
     * 有些 provider 不返回类型，这时按文件头魔数嗅探兜底。
     */
    private fun resolveImageType(uri: Uri): String {
        val declared = contentResolver.getType(uri)?.lowercase(Locale.ROOT)
        if (declared != null && declared.startsWith("image/") && declared != "image/*") return declared
        return try {
            contentResolver.openInputStream(uri)?.use { input ->
                val head = ByteArray(12)
                val len = input.read(head)
                sniffImageType(head, len)
            } ?: "image/jpeg"
        } catch (e: Exception) {
            "image/jpeg"
        }
    }

    private fun sniffImageType(head: ByteArray, len: Int): String {
        // 用字节值而不是字符字面量比较，避免编码/字符转换带来的歧义。
        // 注意必须 `and 0xFF`：Java 的 byte 是有符号的，0x89 直接 toInt 会变成负数
        fun b(index: Int) = head[index].toInt() and 0xFF

        if (len >= 6 && b(0) == 0x47 && b(1) == 0x49 && b(2) == 0x46)
            return "image/gif"                                  // "GIF"
        if (len >= 8 && b(0) == 0x89 && b(1) == 0x50 && b(2) == 0x4E && b(3) == 0x47)
            return "image/png"                                  // ‰PNG
        if (len >= 3 && b(0) == 0xFF && b(1) == 0xD8 && b(2) == 0xFF)
            return "image/jpeg"                                 // JPEG SOI
        if (len >= 12 && b(8) == 0x57 && b(9) == 0x45 && b(10) == 0x42 && b(11) == 0x50)
            return "image/webp"                                 // RIFF....WEBP
        return "image/jpeg"
    }

    private fun readAllBytes(uri: Uri): ByteArray {
        val inputStream: InputStream = contentResolver.openInputStream(uri) ?: throw IOException("无法读取图片")
        return inputStream.use { it.readBytes() }
    }

    /** 探测文件声明长度，拿不到就返回 -1（表示未知，不做预判）。 */
    private fun getDeclaredSize(uri: Uri): Long {
        return try {
            contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
        } catch (e: Exception) {
            -1L
        }
    }

    /** 先按最长边 2048 采样再解码，避免手表直接解码超大原图时 OOM。 */
    private fun decodeScaled(raw: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options()
        bounds.inJustDecodeBounds = true
        BitmapFactory.decodeByteArray(raw, 0, raw.size, bounds)
        var sampleSize = 1
        val longest = Math.max(bounds.outWidth, bounds.outHeight)
        while (longest / sampleSize > MAX_COMPRESS_EDGE) sampleSize *= 2
        val options = BitmapFactory.Options()
        options.inSampleSize = sampleSize
        return BitmapFactory.decodeByteArray(raw, 0, raw.size, options)
    }

    private fun buildPictures(): String {
        // 组装逻辑已抽到 ReplyApi.buildPictures（纯函数，带 JVM 单测），这里只负责加锁取快照
        synchronized(uploadDataList) {
            return ReplyApi.buildPictures(ArrayList(uploadDataList))
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updateImageText() {
        val count = imageList.size
        val base = if (count == 0) getString(R.string.btn_image) else getString(R.string.btn_image) + "($count)"
        // 有图还在上传就在按钮上写明，别让用户以为「图片(1)」= 已经可以发了
        imageText.text = if (pendingUploads > 0) "$base・上传中($pendingUploads)" else base
    }

    /**
     * 这条正文有没有「敏感词」——用于决定是否弹一次提示。
     *
     * 关键词表是判定键不是展示文案：它们只喂给 contains，不能走 UiText 变成资源文案，
     * 否则文案一改名判定就静默失配（依据 STRINGS-MIGRATION.md §4「用作 key 的字符串不要迁移」）。
     */
    private fun checkKy(str: String): Boolean {
        if (str.contains("哔哩终端")) return true
        if (str.contains("终端")) {
            return str.contains("表") || str.contains("b站") || str.contains("B站") ||
                    str.contains("bili") || str.contains("哔")
        }
        return false
    }
}