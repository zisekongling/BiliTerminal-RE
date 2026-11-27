package com.RobinNotBad.BiliClient.activity

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.ImagePickerActivity
import com.RobinNotBad.BiliClient.activity.base.BaseActivity
import com.RobinNotBad.BiliClient.api.TerminalApi
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.FeedbackImage
import com.RobinNotBad.BiliClient.util.GlideUtil
import com.RobinNotBad.BiliClient.util.LogExporter
import com.RobinNotBad.BiliClient.util.Logu
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.SettingsKeys
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import com.RobinNotBad.BiliClient.util.TerminalDialog
import com.RobinNotBad.BiliClient.util.ToolsUtil
import com.google.android.material.button.MaterialButton

/**
 * 反馈与建议（26.10.09 新增）。
 *
 * <p>这是**本项目自建的一条反馈通道**，与上游原有的 QQ 群 / issue 渠道并存，不替换任何东西：
 * 内容直接 POST 到 [TerminalApi.BASE_URL]（自建服务器，见 `server/rebiliterminal_api.py`），
 * 走的是不带 Cookie 的自建请求头，所以**不需要登录**也能发。
 *
 * <h3>隐私边界</h3>
 * 只发三类东西：用户自己写的内容、用户选的联系方式（可空）、以及版本号与机型。
 * 账号 ID 默认**不带**，只有用户在本页（或设置页）显式打开「附带我的账号 ID」才会带上；
 * 这个开关的状态存在 [SettingsKeys.FEEDBACK_ATTACH_MID]，默认 false。
 * 应用日志同样默认**不附带**：只有用户在本页勾选「附带应用日志」才会把
 * [Logu.ringSnapshot] 的**内存日志快照**（已脱敏）塞进 `log` 字段——反馈是用户主动行为，
 * 由他决定带不带（与崩溃页的默认勾选相反）。本页另有「导出日志到文件」入口，走
 * [LogExporter]，只把日志留在本机、不经过网络。
 * 改这里的字段之前先去看 `TerminalApi.buildFeedbackPayload`，两边的字段表必须一致。
 *
 * <h3>为什么不用 ViewModel / Retrofit</h3>
 * 这是工程既有约定（`api/` 全是 static + `org.json`，没有 DI）。请求与解析已经拆开：
 * 解析在 [TerminalApi] 的纯函数里，有 JVM 单测覆盖；本类只负责收集输入与渲染状态。
 */
class FeedbackActivity : BaseActivity() {

    companion object {
        /** 与服务端 `CONTENT_MAX` 保持一致。 */
        private const val MAX_CONTENT = 4000
    }

    /** 当前选中的分类（wire 值，不是显示名）。 */
    private var category: String = TerminalApi.CATEGORY_BUG

    /** 是否附带账号 ID。默认读设置；本页改动会立刻写回设置，两个入口共用一份状态。 */
    private var attachMid: Boolean = false

    /** 发送中。挡住连点造成的重复提交（服务端也有每小时 10 次的限流兜底）。 */
    private var sending: Boolean = false

    /** 已经挑好、还没上传的截图（本地 Uri，最多 [FeedbackImage.MAX_COUNT] 张）。 */
    private val pickedImages = ArrayList<Uri>()

    /**
     * 服务端有没有开图片上传（`GET /feedback/config` 的 `image_enabled`）。
     *
     * <p>拿不到（网络失败 / 老服务端没有这个接口）就当关着：入口整块不显示。
     * 这是「服务端一个开关可随时关掉」在客户端那一半——关掉后用户看不到入口，
     * 而不是点了才发现 403。
     */
    private var imageEnabled: Boolean = false

    private var screenshotCard: View? = null
    private var screenshotTitle: TextView? = null
    private var screenshotRow: LinearLayout? = null

    /**
     * 选图。走本项目内置的 [ImagePickerActivity]（系统文件选择器在部分手表 ROM 上很难用），
     * 结果格式与 ACTION_GET_CONTENT 一致：RESULT_OK + data.data 为 content:// Uri。
     */
    private val pickImageLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val uri = result.data?.data
            if (result.resultCode != RESULT_OK || uri == null) return@registerForActivityResult
            if (pickedImages.size >= FeedbackImage.MAX_COUNT) {
                MsgUtil.showMsg(getString(R.string.msg_feedback_image_limit, FeedbackImage.MAX_COUNT))
                return@registerForActivityResult
            }
            pickedImages.add(uri)
            renderImages()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        asyncInflate(R.layout.activity_feedback) { _, _ ->
            setPageName(getString(R.string.pagename_feedback))

            val categoryText = findViewById<TextView>(R.id.category_text)
            val categoryCard = findViewById<View>(R.id.category_card)
            val contentEdit = findViewById<EditText>(R.id.content_edit)
            val contentCount = findViewById<TextView>(R.id.content_count)
            val contactEdit = findViewById<EditText>(R.id.contact_edit)
            val attachCard = findViewById<View>(R.id.attach_mid_card)
            val attachText = findViewById<TextView>(R.id.attach_mid_text)
            val attachLogCheck = findViewById<CheckBox>(R.id.attach_log_check)
            val exportLogCard = findViewById<View>(R.id.export_log_card)
            val submitBtn = findViewById<MaterialButton>(R.id.submit_btn)
            val statusView = findViewById<TextView>(R.id.submit_status)
            val imageCard = findViewById<View>(R.id.screenshot_card)
            val imageHint = findViewById<TextView>(R.id.screenshot_hint)
            screenshotCard = imageCard
            screenshotTitle = findViewById(R.id.screenshot_title)
            screenshotRow = findViewById(R.id.screenshot_row)
            imageHint.text = getString(R.string.feedback_screenshot_hint, FeedbackImage.MAX_COUNT)
            imageCard.setOnClickListener {
                pickImageLauncher.launch(ImagePickerActivity.intent(this))
            }

            // ---------- 分类 ----------
            categoryText.text = labelOf(category)
            categoryCard.setOnClickListener {
                val items = TerminalApi.CATEGORIES.map { labelOf(it) }
                val checked = TerminalApi.CATEGORIES.indexOf(category).coerceAtLeast(0)
                TerminalDialog.singleChoice(
                    this, getString(R.string.feedback_category_label), items, checked
                ) { sheet, index ->
                    category = TerminalApi.CATEGORIES[index]
                    categoryText.text = labelOf(category)
                    sheet.dialog.dismiss()
                }.show()
            }

            // ---------- 字数 ----------
            contentEdit.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    contentCount.text = "${s?.length ?: 0} / $MAX_CONTENT"
                }

                override fun afterTextChanged(s: Editable?) {}
            })
            contentCount.text = "0 / $MAX_CONTENT"

            // ---------- 附带账号 ID ----------
            attachMid = SharedPreferencesUtil.getBoolean(SettingsKeys.FEEDBACK_ATTACH_MID, false)

            fun renderAttach() {
                attachText.text = getString(
                    if (attachMid) R.string.feedback_attach_mid_on else R.string.feedback_attach_mid_off
                )
            }

            renderAttach()
            attachCard.setOnClickListener {
                attachMid = !attachMid
                SharedPreferencesUtil.putBoolean(SettingsKeys.FEEDBACK_ATTACH_MID, attachMid)
                renderAttach()
            }

            // ---------- 附带应用日志 ----------
            // 默认不勾选，且**不持久化**：每次进页面都是关的，避免上次的选择被悄悄沿用
            // （与「附带账号 ID」不同——那个是长期偏好，这个是一次性决定）。
            attachLogCheck.isChecked = false

            // ---------- 导出日志到文件 ----------
            // 与提交是两条独立路径：这里只落本机文件并调起系统分享，不经过网络。
            exportLogCard.setOnClickListener { LogExporter.export(this) }

            // ---------- 截图（26.11.27 新增） ----------
            // 只问服务端一个布尔值，不拉整张反馈列表；拿不到就当没开，入口不出现。
            CenterThreadPool.run {
                val enabled = TerminalApi.fetchFeedbackImageEnabled()
                CenterThreadPool.runOnUiThread {
                    if (isDestroyed) return@runOnUiThread
                    imageEnabled = enabled
                    renderImages()
                }
            }

            // ---------- 发送 ----------
            submitBtn.setOnClickListener {
                if (sending) return@setOnClickListener

                val content = contentEdit.text?.toString()?.trim().orEmpty()
                if (content.isEmpty()) {
                    MsgUtil.showMsg(getString(R.string.feedback_empty))
                    return@setOnClickListener
                }
                if (content.length > MAX_CONTENT) {
                    MsgUtil.showMsg(getString(R.string.feedback_too_long))
                    return@setOnClickListener
                }
                val contact = contactEdit.text?.toString()?.trim().orEmpty()
                // 在主线程读一次勾选状态与截图列表，带进子线程用（View 不跨线程访问）
                val attachLog = attachLogCheck.isChecked
                val images = ArrayList(pickedImages)

                sending = true
                submitBtn.isEnabled = false
                statusView.visibility = View.VISIBLE
                statusView.text = getString(R.string.feedback_sending)

                CenterThreadPool.run {
                    // 关掉开关时传 0：服务端那条记录里根本不会有 mid 这个字段值，
                    // 不是「传了但不显示」——隐私上这两者的区别很重要。
                    val mid = if (attachMid) SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0L) else 0L
                    // 勾选才取内存日志快照；拼接最长 64K 字符，放在子线程避免任何主线程开销。
                    val log = if (attachLog) Logu.ringSnapshot() else ""

                    // 先把截图逐张传上去换成 key，再随反馈一起提交：
                    // 任何一张失败就整条中止并保留用户写的内容，不会出现「文字发出去、图没跟上」的半截状态。
                    val outcome = FeedbackImage.uploadAll(this@FeedbackActivity, images) { index, total ->
                        if (total > 1) {
                            val progress = getString(R.string.feedback_image_uploading, index + 1, total)
                            runOnUiThread { if (!isDestroyed) statusView.text = progress }
                        }
                    }
                    val failed = outcome.failure
                    if (failed != null) {
                        runOnUiThread {
                            sending = false
                            if (isDestroyed) return@runOnUiThread
                            submitBtn.isEnabled = true
                            statusView.text = if (failed.code == FeedbackImage.CODE_UNREADABLE) {
                                getString(R.string.feedback_image_unreadable)
                            } else {
                                getString(R.string.feedback_image_fail, failed.message)
                            }
                        }
                        return@run
                    }

                    val result = TerminalApi.submitFeedback(category, content, log, contact, mid, outcome.keys)

                    runOnUiThread {
                        sending = false
                        if (isDestroyed) return@runOnUiThread
                        submitBtn.isEnabled = true
                        if (result.code >= 0) {
                            statusView.text = getString(R.string.feedback_ok, result.code)
                            // 成功才清空，失败时留着内容让用户重发，不然写的一大段就没了
                            contentEdit.setText("")
                            contactEdit.setText("")
                            // 截图已经随这次反馈送达，清掉本地选择，避免下次反馈又把同一批图再传一遍
                            pickedImages.clear()
                            renderImages()
                            // 日志已随这次反馈送达，清空内存缓冲，避免下次反馈把同一份旧日志再发一遍。
                            // 只在「确实勾选附带」且「提交成功」时清：没勾选就没送出去、失败则要留着重试。
                            // 代价是清完之后用户当场再点「导出日志」会得到空提示——可接受：缓冲是滚动的、
                            // 新日志会立刻重新累积，而想要文件留档的人本就该走导出入口（那条路径不清缓冲）。
                            if (attachLog) Logu.ringClear()
                        } else {
                            statusView.text = getString(R.string.feedback_fail, result.message)
                        }
                    }
                }
            }
        }
    }

    /**
     * 重画截图区：标题显示 n/3，缩略图行按当前选择重建。
     *
     * <p>开关关着（或 config 还没回来）时整块 GONE——用户看不到入口，不会「点了才发现不能用」。
     */
    private fun renderImages() {
        val card = screenshotCard ?: return
        val title = screenshotTitle ?: return
        val row = screenshotRow ?: return
        if (!imageEnabled) {
            card.visibility = View.GONE
            return
        }
        card.visibility = View.VISIBLE
        title.text = getString(R.string.feedback_screenshot_add, pickedImages.size, FeedbackImage.MAX_COUNT)
        row.removeAllViews()
        if (pickedImages.isEmpty()) {
            row.visibility = View.GONE
            return
        }
        row.visibility = View.VISIBLE
        val size = ToolsUtil.dp2px(48f)
        val gap = ToolsUtil.dp2px(6f)
        pickedImages.forEachIndexed { index, uri ->
            val thumb = ImageView(this)
            val lp = LinearLayout.LayoutParams(size, size)
            lp.marginEnd = gap
            thumb.layoutParams = lp
            thumb.scaleType = ImageView.ScaleType.CENTER_CROP
            // 缩略图直接读本地 content:// 预览，不经过服务器；点一下是「移除」，不是放大
            GlideUtil.request(thumb, uri.toString(), 6, 0)
            thumb.setOnClickListener { confirmRemoveImage(index) }
            row.addView(thumb)
        }
    }

    /** 移除一张截图前确认一次：手表屏小，缩略图很容易误触。 */
    private fun confirmRemoveImage(index: Int) {
        if (index < 0 || index >= pickedImages.size) return
        TerminalDialog.confirm(
            this,
            getString(R.string.msg_feedback_image_remove_title),
            getString(R.string.msg_feedback_image_remove_body),
            onConfirm = {
                if (index < pickedImages.size) {
                    pickedImages.removeAt(index)
                    renderImages()
                }
            }
        ).show()
    }

    private fun labelOf(value: String): String = when (value) {
        TerminalApi.CATEGORY_BUG -> getString(R.string.feedback_category_bug)
        TerminalApi.CATEGORY_SUGGESTION -> getString(R.string.feedback_category_suggestion)
        TerminalApi.CATEGORY_CONTENT -> getString(R.string.feedback_category_content)
        TerminalApi.CATEGORY_PERFORMANCE -> getString(R.string.feedback_category_performance)
        else -> getString(R.string.feedback_category_other)
    }
}
