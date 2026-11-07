package com.RobinNotBad.BiliClient.activity.settings.login

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.TextView
import com.RobinNotBad.BiliClient.BiliTerminal
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.SplashActivity
import com.RobinNotBad.BiliClient.activity.base.BaseActivity
import com.RobinNotBad.BiliClient.util.AccountManager
import com.RobinNotBad.BiliClient.util.Logu
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.NetWorkUtil
import com.RobinNotBad.BiliClient.util.SpecialLoginParser
import com.google.android.material.card.MaterialCardView

/**
 * 特殊登录：把另一台设备导出的登录信息搬过来，或者把本机的全部账号导出去。
 *
 * <p>两种模式由 intent 的 `login` 决定：true=导入（默认），false=导出。
 * 数据格式与校验逻辑都在 [SpecialLoginParser]，本页只负责「拿到文本、交给它、根据结果提示」。
 *
 * <p>**单账号与多账号的区别**（这是「不会乱」的关键）：
 * <ul>
 *     <li>单账号 = 一次完整登录：写当前凭证 + 跳启动页；</li>
 *     <li>多账号 = 只往账号列表里塞人，**不切换当前账号**，导完去「账号切换」里自己选。</li>
 * </ul>
 * 多账号若也顺手切号，用户会莫名其妙被踢到另一个身份上——所以这里刻意分开。
 */
class SpecialLoginActivity : BaseActivity() {

    private lateinit var textInput: EditText

    @SuppressLint("MissingInflatedId", "SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login_special)
        Logu.i("debug", "使用特殊登录方式")

        textInput = findViewById(R.id.loginInput)
        val confirm = findViewById<MaterialCardView>(R.id.confirm)
        val pasteLogin = findViewById<MaterialCardView>(R.id.pasteLogin)
        val pasteDesc = findViewById<TextView>(R.id.pasteDesc)
        val refuse = findViewById<MaterialCardView>(R.id.refuse)
        val copy = findViewById<MaterialCardView>(R.id.copy)
        val desc = findViewById<TextView>(R.id.desc)

        val intent = intent

        if (intent.getBooleanExtra("login", true)) {
            refuse.setOnClickListener {
                if (intent.getBooleanExtra("from_setup", false))
                    startActivity(Intent(this, SplashActivity::class.java))
                else finish()
            }

            confirm.setOnClickListener {
                importFrom(textInput.text.toString())
            }

            // 直接读剪贴板登录：先过 SpecialLoginParser 校验（单账号或多账号都认），
            // 不通过就只提示、绝不写任何登录态。
            pasteLogin.setOnClickListener {
                val clipText = readClipboardText()
                if (clipText.isNullOrBlank()) {
                    MsgUtil.showMsg(getString(R.string.msg_specialloginactivity_001))
                    return@setOnClickListener
                }
                importFrom(clipText)
            }
        } else {
            desc.setText(R.string.special_login_export)

            val exported = AccountManager.exportAllAccounts()
            if (exported.isEmpty()) {
                // 没账号可导出时不要把提示文案塞进输入框：那是一个可编辑、可全选复制的文本框，
                // 提示混进去会被当成「导出的数据」贴到别的设备上。输入框留空，提示写回说明栏。
                textInput.setText("")
                desc.setText(R.string.msg_specialloginactivity_009)
            } else {
                textInput.setText(exported)
            }
            textInput.clearFocus()

            // 导出模式下没有「登录」这件事，粘贴登录按钮一并隐藏
            pasteLogin.visibility = View.GONE
            pasteDesc.visibility = View.GONE

            refuse.visibility = View.GONE
            if (BiliTerminal.isDebugBuild()) {
                // 调试用：把框里的内容当导入数据直接写进去，方便在两台设备之间来回试
                confirm.setOnClickListener {
                    applyForDebug(textInput.text.toString())
                }
            } else confirm.visibility = View.GONE
            copy.setOnClickListener {
                val text = AccountManager.exportAllAccounts()
                if (text.isEmpty()) {
                    MsgUtil.showMsg(getString(R.string.msg_specialloginactivity_009))
                    return@setOnClickListener
                }
                val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clipData = ClipData.newPlainText("label", text)
                clipboardManager.setPrimaryClip(clipData)
                MsgUtil.showMsg(getString(R.string.msg_specialloginactivity_004))
            }
        }
    }

    /**
     * 统一导入入口：文本框输入与剪贴板内容共用同一条链路，校验失败不写任何登录态。
     *
     * <p>多账号导入后**停在当前页**：用户的当前账号没变，不存在"跳启动页"的必要，
     * 也方便连着导两份。提示里会告诉用户去「账号切换」选。
     */
    private fun importFrom(loginInfo: String) {
        when (val parsed = SpecialLoginParser.parseAny(loginInfo)) {
            is SpecialLoginParser.ParseResult.Failure -> MsgUtil.showMsg(parsed.message)

            is SpecialLoginParser.ParseResult.Single -> {
                if (!applySingle(parsed.account)) {
                    MsgUtil.showMsg(getString(R.string.msg_specialloginactivity_006))
                    return
                }
                MsgUtil.showMsg(getString(R.string.msg_specialloginactivity_005))
                startActivity(Intent(this, SplashActivity::class.java))
                finish()
            }

            is SpecialLoginParser.ParseResult.Batch -> {
                val result = AccountManager.importAccounts(
                    parsed.accounts.map { it.toAccountInfo() }
                )
                if (result.total() == 0) {
                    MsgUtil.showMsg(getString(R.string.msg_specialloginactivity_007))
                } else {
                    MsgUtil.showMsg(
                        getString(
                            R.string.msg_specialloginactivity_008,
                            result.total(), result.added, result.updated
                        )
                    )
                    textInput.setText("")
                }
            }
        }
    }

    /** 调试版导出页的确认按钮：只写数据，不跳页。 */
    private fun applyForDebug(loginInfo: String) {
        when (val parsed = SpecialLoginParser.parseAny(loginInfo)) {
            is SpecialLoginParser.ParseResult.Failure -> {
                MsgUtil.showMsg(getString(R.string.msg_specialloginactivity_003))
            }

            is SpecialLoginParser.ParseResult.Single -> {
                val ok = applySingle(parsed.account)
                MsgUtil.showMsg(
                    getString(
                        if (ok) R.string.msg_specialloginactivity_002
                        else R.string.msg_specialloginactivity_006
                    )
                )
            }

            is SpecialLoginParser.ParseResult.Batch -> {
                val result = AccountManager.importAccounts(
                    parsed.accounts.map { it.toAccountInfo() }
                )
                MsgUtil.showMsg(
                    getString(
                        if (result.total() > 0) R.string.msg_specialloginactivity_002
                        else R.string.msg_specialloginactivity_007
                    )
                )
            }
        }
    }

    private fun applySingle(account: SpecialLoginParser.Result.Success): Boolean {
        return AccountManager.applyCredentials(
            account.mid,
            account.name,
            account.cookies,
            account.refreshToken,
            account.accessKey,
            account.avatar
        )
    }

    /**
     * 解析结果 → 账号列表条目。
     *
     * csrf 用 Cookie 里的实时值兜底：导出的 JSON 里可能没有 csrf 字段（老版本导出的），
     * 而 Cookie 里几乎总带 bili_jct，不能因为缺字段就把这条判成无效。
     */
    private fun SpecialLoginParser.Result.Success.toAccountInfo(): AccountManager.AccountInfo {
        return AccountManager.AccountInfo(
            mid,
            name,
            cookies,
            refreshToken,
            accessKey,
            NetWorkUtil.pickCsrf(cookies, ""),
            avatar
        )
    }

    /** 读取剪贴板纯文本；剪贴板为空或系统拒绝读取（无焦点等）时返回 null。 */
    private fun readClipboardText(): String? {
        val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
        return try {
            val clip = clipboardManager.primaryClip ?: return null
            if (clip.itemCount == 0) return null
            clip.getItemAt(0).coerceToText(this)?.toString()
        } catch (e: Exception) {
            Logu.e("debug", "读取剪贴板失败：${e.message}")
            null
        }
    }

}
