package com.RobinNotBad.BiliClient.activity.settings.login

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.TextView
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.base.BaseActivity
import com.RobinNotBad.BiliClient.util.AccountManager
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.google.android.material.card.MaterialCardView

/**
 * 导出全部账号：把本机所有已保存账号打成一个多账号信封，展示在文本框里并支持一键复制。
 *
 * <p>**为什么独立成页**：特殊登录页的导出保持 26.11.02 的单条三字段格式（只含当前账号），
 * 换机搬家要的是「所有账号一份 JSON」。两件事的用途、格式、风险完全不同，
 * 塞在同一个按钮里会让「只想备份当前账号」的人拿到一份多账号数据；
 * 拆成两页后特殊登录页的行为与 11.02 完全一致，多账号导出走本页。
 *
 * <p>格式与校验见 [SpecialLoginParser]：这里是导出侧，信封结构由
 * [AccountManager.exportAllAccounts] 生成，导入侧（特殊登录页）用 parseAny 兼容三种形态。
 *
 * <p>入口只有一个：账号切换页底部的「导出全部账号数据」（与原「导入账号数据」对称）。
 */
class ExportAllAccountsActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login_export_all)

        val desc = findViewById<TextView>(R.id.desc)
        val textInput = findViewById<EditText>(R.id.exportInput)
        val copy = findViewById<MaterialCardView>(R.id.copy)

        findViewById<View>(R.id.pageName).setOnClickListener { finish() }

        val exported = AccountManager.exportAllAccounts()
        if (exported.isEmpty()) {
            // 与特殊登录导出页同一处理：提示写回说明栏，不塞进可编辑、可全选复制的输入框，
            // 免得提示被当成「导出的数据」贴到别的设备上。
            textInput.setText("")
            desc.setText(R.string.msg_specialloginactivity_009)
        } else {
            textInput.setText(exported)
        }
        textInput.clearFocus()

        copy.setOnClickListener {
            // 重新导出一份而不是复制框里的文本：框里可能被用户改过（甚至清空），
            // 剪贴板里必须是当前的真实数据。
            val text = AccountManager.exportAllAccounts()
            if (text.isEmpty()) {
                MsgUtil.showMsg(getString(R.string.msg_specialloginactivity_009))
                return@setOnClickListener
            }
            val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboardManager.setPrimaryClip(ClipData.newPlainText("label", text))
            MsgUtil.showMsg(getString(R.string.msg_specialloginactivity_004))
        }
    }
}
