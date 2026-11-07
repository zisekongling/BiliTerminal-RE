package com.RobinNotBad.BiliClient.activity.settings

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.RobinNotBad.BiliClient.BiliTerminal
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.FeedbackActivity
import com.RobinNotBad.BiliClient.activity.ImageViewerActivity
import com.RobinNotBad.BiliClient.activity.base.BaseActivity
import com.RobinNotBad.BiliClient.util.GlideUtil
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import com.RobinNotBad.BiliClient.util.StringUtil
import com.RobinNotBad.BiliClient.util.TerminalDialog
import com.RobinNotBad.BiliClient.util.ToolsUtil
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.google.android.material.card.MaterialCardView

class AboutActivity : BaseActivity() {
    private var eggClickAuthorWords: Int = 0
    private var eggClickToUncle: Int = 0
    private var eggClickDev: Int = 0

    /** 一位开发者：名字、简介（或简介资源）、头像资源（-1 表示没有头像）、B 站 UID。 */
    private data class Developer(
        val name: String,
        val desc: String,
        val avatarRes: Int,
        val uid: Long,
        val descRes: Int = 0,
    )

    companion object {
        /** RE 版（当前这版）的主要开发者。身份由列名 `about_dev_trae` 与卡片简介位共同表达。 */
        private val DEVELOPERS_RE = listOf(
            Developer("紫色空灵", "", R.mipmap.avatar_zise, 591904067L, R.string.about_dev_trae_desc),
            Developer("千awa", "", R.mipmap.avatar_qianawa, 1786791133L, R.string.about_dev_qianawa),
            Developer("Kuinone", "", R.mipmap.avatar_kuinone, 475731079L, R.string.about_dev_kuinone_desc),
        )

        /** 主要开发者。 */
        private val DEVELOPERS_MAIN = listOf(
            Developer("RobinNotBad", "", R.mipmap.avatar_robin, 646521226L, R.string.settingtext_aboutactivity_001),
            Developer("爅峫（moye）", "", R.mipmap.avatar_moye, 394675616L, R.string.settingtext_aboutactivity_003),
        )

        /** 联合开发者。 */
        private val DEVELOPERS_JOINT = listOf(
            Developer("silent碎月", "", R.mipmap.avatar_silent, 40140732L, R.string.settingtext_aboutactivity_005),
            Developer("dudu", "", R.mipmap.avatar_dudu, 517053179L, R.string.settingtext_aboutactivity_006),
            Developer("达达", "", -1, 432128342L, R.string.settingtext_aboutactivity_008),
            Developer("huanli233", "", R.mipmap.avatar_huanli, 673815151L, R.string.settingtext_aboutactivity_009),
            Developer("Jank000.h", "", R.mipmap.avatar_jank, 661403494L, R.string.settingtext_aboutactivity_010),
        )
    }

    @SuppressLint("MissingInflatedId", "SetTextI18n", "InflateParams")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        asyncInflate(R.layout.activity_setting_about) { _, _ ->
            Log.e("debug", "进入关于页面")

            try {
                // 前两行标签加粗，值不加粗：加粗区间按标签实际长度算，翻译改了字数也不会错位
                val nameLabel = getString(R.string.text_activitysettingabout_07)
                val versionStr = SpannableString(nameLabel + "\n" + packageManager.getPackageInfo(packageName, 0).versionName)
                versionStr.setSpan(StyleSpan(Typeface.BOLD), 0, nameLabel.length, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
                (findViewById<TextView>(R.id.app_version)).text = versionStr

                val codeLabel = getString(R.string.text_activitysettingabout_06)
                val codeStr = SpannableString(codeLabel + "\n" + packageManager.getPackageInfo(packageName, 0).versionCode)
                codeStr.setSpan(StyleSpan(Typeface.BOLD), 0, codeLabel.length, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
                (findViewById<TextView>(R.id.app_version_code)).text = codeStr

                val currentLogItems = resources.getStringArray(R.array.update_log_current)
                val currentLog = StringBuilder()
                for (item in currentLogItems) currentLog.append("\n").append(item)
                val updateLogView = findViewById<TextView>(R.id.updatelog_view)
                updateLogView.text = currentLog.toString()
                StringUtil.setCopy(updateLogView, currentLog.toString())

                val versionName = packageManager.getPackageInfo(packageName, 0).versionName
                findViewById<TextView>(R.id.current_update_title).text = getString(R.string.text_update_current_version, versionName)
            } catch (e: PackageManager.NameNotFoundException) {
                e.printStackTrace()
            }

            // 开发者卡片：数据驱动动态创建（模板见 res/layout/cell_developer.xml），
            // 新增开发者只需往下面三个列表里加一项，不用再改布局。
            // 顺序与布局里的三个小标题一致：RE 版主要开发者 → 主要开发者 → 联合开发者。
            bindDevelopers(findViewById(R.id.developer_re_container), DEVELOPERS_RE)
            bindDevelopers(findViewById(R.id.developer_main_container), DEVELOPERS_MAIN)
            bindDevelopers(findViewById(R.id.developer_joint_container), DEVELOPERS_JOINT)

            findViewById<View>(R.id.author_words).setOnClickListener {
                eggClickAuthorWords++
                if (eggClickAuthorWords == 7) {
                    eggClickAuthorWords = 0
                    MsgUtil.showText(SettingText.resolve(this, "作者的话"), getString(R.string.egg_about_author_words))
                }
            }

            findViewById<View>(R.id.toUncle).setOnClickListener {
                eggClickToUncle++
                if (eggClickToUncle == 7) {
                    eggClickToUncle = 0
                    MsgUtil.showText(SettingText.resolve(this, "给叔叔"), getString(R.string.egg_about_to_uncle))
                }
            }

            // 开源协议 / 借鉴项目 / 图标许可已拆到独立页，这里只留入口
            findViewById<View>(R.id.opensource_entry).setOnClickListener {
                startActivity(Intent(this, OpenSourceActivity::class.java))
            }

            // 赞赏：先弹免责声明，点「同意」才去看收款码（点「取消」什么都不发生）。
            // 收款码交给图片查看器（能双指放大，手表屏幕小，二维码得看得清）；
            // 地址走 strings.xml 的 about_donate_url，换图不用改代码。
            // 注意 TerminalDialog 只「造」弹窗不负责显示，调用完必须 .show()（漏了就是点了没反应）。
            findViewById<View>(R.id.donate_qr_entry).setOnClickListener {
                TerminalDialog.confirm(
                    context = this,
                    title = getString(R.string.about_donate_entry),
                    message = getString(R.string.about_donate_disclaimer),
                    confirmText = getString(R.string.privacy_consent_agree), // 同意
                    confirmIsDanger = false,
                    onConfirm = { openDonateQr() }
                ).show()
            }

            findViewById<View>(R.id.sponsor_list).setOnClickListener {
                val intent = Intent(this, SponsorActivity::class.java)
                startActivity(intent)
            }

            // 26.10.09：自建反馈通道。与上面的 QQ 群 / issue 并存，不替换任何渠道。
            findViewById<View>(R.id.feedback_entry).setOnClickListener {
                startActivity(Intent(this, FeedbackActivity::class.java))
            }

            findViewById<View>(R.id.history_log_entry).setOnClickListener {
                val intent = Intent(this, UpdateHistoryActivity::class.java)
                startActivity(intent)
            }

            if (!ToolsUtil.isDebugBuild()) findViewById<View>(R.id.debug_tip).visibility = View.GONE
            findViewById<View>(R.id.version_code_card).setOnClickListener {
                if (SharedPreferencesUtil.getBoolean("developer", false)) {
                    MsgUtil.showMsg(SettingText.resolve(this, "已关闭开发者模式！"))
                    SharedPreferencesUtil.putBoolean("developer", false)
                } else {
                    eggClickDev++
                    if (eggClickDev == 7) {
                        SharedPreferencesUtil.putBoolean("developer", true)
                        MsgUtil.showMsg(SettingText.resolve(this, "已启用开发者模式！"))
                        eggClickDev = 0
                    }
                }
            }

            val scrollView = findViewById<View>(R.id.scrollView)
            scrollView.isFocusable = true
            scrollView.isFocusableInTouchMode = true
            scrollView.requestFocus()
        }
    }

    /** 打开赞赏用的收款码（图片查看器，可双指放大）。 */
    private fun openDonateQr() {
        startActivity(
            Intent(this, ImageViewerActivity::class.java)
                .putExtra("imageList", arrayListOf(getString(R.string.about_donate_url)))
        )
    }

    /** 把开发者列表渲染成卡片塞进容器。 */
    private fun bindDevelopers(container: LinearLayout, developers: List<Developer>) {
        for (dev in developers) {
            val card = layoutInflater.inflate(R.layout.cell_developer, container, false)

            card.findViewById<TextView>(R.id.dev_name).text = dev.name
            card.findViewById<TextView>(R.id.dev_desc).text =
                if (dev.descRes != 0) getString(dev.descRes) else dev.desc

            if (dev.avatarRes != -1) {
                try {
                    Glide.with(this).load(dev.avatarRes)
                        .transition(GlideUtil.getTransitionOptions())
                        .placeholder(R.mipmap.akari)
                        .error(R.mipmap.akari)
                        .apply(RequestOptions.circleCropTransform())
                        .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                        .into(card.findViewById(R.id.dev_avatar))
                } catch (ignored: Exception) {
                }
            }

            if (dev.uid != -1L) {
                card.setOnClickListener {
                    BiliTerminal.jumpToUser(this, dev.uid)
                }
            }

            container.addView(card)
        }
    }
}
