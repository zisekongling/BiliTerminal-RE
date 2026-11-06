package com.RobinNotBad.BiliClient.activity

import com.RobinNotBad.BiliClient.util.UiText

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.os.Process
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import android.widget.CheckBox
import android.widget.TextView
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.base.BaseActivity
import com.RobinNotBad.BiliClient.api.AppInfoApi
import com.RobinNotBad.BiliClient.api.TerminalApi
import com.RobinNotBad.BiliClient.model.ApiResult
import com.RobinNotBad.BiliClient.service.DownloadService
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.CrashReporter
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import com.RobinNotBad.BiliClient.util.StringUtil
import com.google.android.material.button.MaterialButton

class CatchActivity : BaseActivity() {

    companion object {
        /** 自动上报的延迟毫秒数：给用户一个真正能生效的「取消附带日志」窗口。 */
        private const val AUTO_REPORT_DELAY_MS = 3000L

        /** 同上，供文案使用；与 [AUTO_REPORT_DELAY_MS] 必须保持一致。 */
        private const val AUTO_REPORT_DELAY_SEC = 3
    }

    private var openStack: Boolean = false

    @SuppressLint("MissingInflatedId", "SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_catch)

        val reasonView = findViewById<TextView>(R.id.catch_reason)
        val stackView = findViewById<TextView>(R.id.stack)
        val btnUpload = findViewById<MaterialButton>(R.id.upload_btn)

        val intent = intent
        val stack = intent.getStringExtra("stack")

        stackView.text = stack

        // 26.10.09（自建崩溃报告）：这些 extra 由 ErrorCatch 在崩溃瞬间塞进来，
        // 因为主进程马上就被 killProcess，页面轨迹之类的东西只在那一刻存在。
        val exception = intent.getStringExtra("exception")
        val message = intent.getStringExtra("message")
        val crashThread = intent.getStringExtra("thread")
        val trail = intent.getStringExtra("trail")
        val uptime = intent.getLongExtra("uptime", 0L)

        // 26.10.16（崩溃报告附带应用日志）：内存日志快照由 ErrorCatch 在崩溃瞬间从主进程
        // 取好、经 Intent extra 带过来。**这里不能再调 Logu.ringSnapshot()**——崩溃页跑在
        // :error_activity 独立进程，那边的 Logu 内存缓冲是空的，直接调只会拿到空串。
        val ringLog = intent.getStringExtra("ring_log")

        val attachLogBox = findViewById<CheckBox>(R.id.attach_ring_log)
        if (ringLog.isNullOrEmpty()) {
            // 本次崩溃前没产生任何 W/E/F 日志：勾了也没内容可传。
            // 直接置灰 + 改文案，比留一个「勾了却什么都没带」的活勾选框更清楚——
            // 用户不会误以为日志已经附上了。
            attachLogBox.isChecked = false
            attachLogBox.isEnabled = false
            attachLogBox.text = UiText.of("本次没有可附带的日志")
        }

        val autoStatusView = findViewById<TextView>(R.id.auto_report_status)

        // 自动上报走自建服务器，与下面那个「手动上传到上游」的按钮互相独立：
        // 上游那套要求已登录、且按异常类型白名单把一部分崩溃挡在门外（见 allowUpload），
        // 而自动上报要的正是这些没被人为挑过的样本。开关默认开，用户可在设置里关。
        if (TerminalApi.isCrashReportAutoEnabled()) {
            autoStatusView.visibility = android.view.View.VISIBLE
            autoStatusView.text =
                "将在 $AUTO_REPORT_DELAY_SEC 秒后自动上报错误报告\n（这期间可取消下面的「附带应用日志」）"
            // 上面那段「点击下面的按钮上传」的说明已在 XML 里固定 gone（按钮不显示了），
            // 这里不再需要运行时收起它。
            // **不能立刻读勾选状态并上报**：页面渲染完成之前用户看不到勾选框，同步读到的
            // 永远是 XML 默认值，等于「可取消」是句空话（26.10.16 真机实测发现）。
            // 所以延后一小段，给用户一个真正能生效的取消窗口。
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                if (isDestroyed) return@postDelayed
                // 在主线程读勾选状态：CenterThreadPool 里读 View 状态不是线程安全的。
                // 没勾选就传空串，由 CrashReporter 那边判空后不拼分隔符。
                val ringLogToSend = if (attachLogBox.isChecked) ringLog.orEmpty() else ""
                autoStatusView.text = UiText.of("正在自动上报错误报告…")
                CenterThreadPool.run {
                    val res = CrashReporter.upload(
                        this@CatchActivity, exception, message, crashThread, stack, trail, ringLogToSend, uptime
                    )
                    runOnUiThread {
                        if (isDestroyed) return@runOnUiThread
                        autoStatusView.text = if (res.code >= 0)
                            "已自动上报错误报告\n报错ID：${res.code}\n如需补充说明请到「设置 - 关于 - 反馈与建议」"
                        else
                            "自动上报失败：${res.message}\n（可在「设置 - 关于与帮助」里关闭自动上报）"
                    }
                }
            }, AUTO_REPORT_DELAY_MS)
        }

        findViewById<android.view.View>(R.id.exit_btn).setOnClickListener { android.os.Process.killProcess(android.os.Process.myPid()) }

        var reasonStr: SpannableString? = null

        if (stack != null) {

            var allowUpload = false

            if (stack.contains("java.lang.NumberFormatException"))
                reasonStr = SpannableString(UiText.of("可能的崩溃原因：\n数值转换出错"))
            else if (stack.contains("java.lang.UnsatisfiedLinkError"))
                reasonStr = SpannableString(UiText.of("可能的崩溃原因：\n外部库加载出错，可能设备太老或修改了安装包"))
            else if (stack.contains("org.json.JSONException"))
                reasonStr = SpannableString(UiText.of("可能的崩溃原因：\n数据解析错误"))
            else if (stack.contains("java.lang.OutOfMemoryError"))
                reasonStr = SpannableString(UiText.of("可能的崩溃原因：\n内存爆了，这在小内存设备上很正常"))
            else
                allowUpload = true

            // **刻意不显示这个按钮**：XML 里 upload_btn 就是 gone，这里也不再设回 VISIBLE。
            // 理由：它走的是上游 api.biliterminal.cn，那台服务器只服务其自身版本——本 fork 点它
            // 一律被拒（实测返回「请使用最新版本上传」）。而崩溃上报已由上面的自建通道自动完成，
            // 留一个永远失败的按钮只会让人撞墙。上游接口本身按约定原样保留
            //（`AppInfoApi.uploadStack` 不动），将来上游若开放本版本，把 visibility 设回 VISIBLE 即可。
            if (allowUpload) btnUpload.setOnClickListener {
                btnUpload.isEnabled = false
                if (SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, -1) == -1L)
                    MsgUtil.toast(UiText.of("我们不对未登录时遇到的问题负责\n——除非它真的经常出现且非常影响使用"))
                else {
                    CenterThreadPool.run {
                        val res: ApiResult = AppInfoApi.uploadStack(stack, this@CatchActivity)
                        runOnUiThread {
                            if (isDestroyed) return@runOnUiThread
                            if (res.code >= 0)
                                btnUpload.text = "请带着你的报错ID：${res.code}\n和你崩溃前进行的操作\n去找开发者\n（提醒：开发者不保证会修好也不保证随时回复你）"
                            else btnUpload.text = res.message +
                                UiText.of("\n\n（这个按钮走的是上游服务器，本 fork 常被它按版本拒绝；") +
                                UiText.of("崩溃报告以上面自动上报的报错ID 为准）")

                            if (res.code == -1) btnUpload.isEnabled = true
                        }
                    }
                }
            }
            else btnUpload.text = UiText.of("此类型报错不可上传\n非特殊情况请勿打扰开发者谢谢喵")

        } else finish()

        findViewById<android.view.View>(R.id.restart_btn).setOnClickListener {
            finish()
            stopService(Intent(this, DownloadService::class.java))
            startActivity(Intent(this, SplashActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            Process.killProcess(Process.myPid())
        }

        if (reasonStr != null) {
            // 结束下标必须夹到字符串长度内：早先写死 8，一旦上面某条原因文案短于 8 个字
            // 就会 IndexOutOfBoundsException——崩溃页自己再崩一次，报错信息还全丢了。
            val boldEnd = minOf(8, reasonStr.length)
            if (boldEnd > 0)
                reasonStr.setSpan(StyleSpan(Typeface.BOLD), 0, boldEnd, Spanned.SPAN_INCLUSIVE_EXCLUSIVE)
            reasonView.text = reasonStr
        } else reasonView.text = UiText.of("未知的崩溃原因")

        stackView.setOnClickListener {
            openStack = !openStack
            if (openStack) stackView.maxLines = 200
            else stackView.maxLines = 5
        }

        StringUtil.setCopy(stackView)
    }

    override fun eventBusEnabled(): Boolean {
        return false
    }
}