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
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.base.BaseActivity
import com.RobinNotBad.BiliClient.activity.user.info.UserInfoActivity
import com.RobinNotBad.BiliClient.util.GlideUtil
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import com.RobinNotBad.BiliClient.util.StringUtil
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
        /** 主要开发者。 */
        private val DEVELOPERS_MAIN = listOf(
            Developer("RobinNotBad", "和他的纳西妲酱（项目发起者|屎山奠基人）", R.mipmap.avatar_robin, 646521226L),
            Developer("爅峫（moye）", "你就说能不能用吧（代码贡献量大|屎山铺路人|想换个头像的说）", R.mipmap.avatar_moye, 394675616L),
        )

        /** 联合开发者。 */
        private val DEVELOPERS_JOINT = listOf(
            Developer("silent碎月", "我是镜流小姐的狗", R.mipmap.avatar_silent, 40140732L),
            Developer("dudu", "一个摆烂的开发者", R.mipmap.avatar_dudu, 517053179L),
            Developer("达达", "（这位开发者很懒，什么也没有留下）", -1, 432128342L),
            Developer("huanli233", "与你的日常，就是奇迹", R.mipmap.avatar_huanli, 673815151L),
            Developer("Jank000.h", "CQCQCQ，这里是BI1TIA，是否有友台能够抄收？", R.mipmap.avatar_jank, 661403494L),
            Developer("紫色空灵", "", R.mipmap.avatar_zise, 591904067L, R.string.about_dev_trae),
        )
    }

    @SuppressLint("MissingInflatedId", "SetTextI18n", "InflateParams")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        asyncInflate(R.layout.activity_setting_about) { _, _ ->
            Log.e("debug", "进入关于页面")

            try {
                val versionStr = SpannableString("版本名\n" + packageManager.getPackageInfo(packageName, 0).versionName)
                versionStr.setSpan(StyleSpan(Typeface.BOLD), 0, 3, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
                (findViewById<TextView>(R.id.app_version)).text = versionStr

                val codeStr = SpannableString("版本号\n" + packageManager.getPackageInfo(packageName, 0).versionCode)
                codeStr.setSpan(StyleSpan(Typeface.BOLD), 0, 3, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
                (findViewById<TextView>(R.id.app_version_code)).text = codeStr

                val currentLogItems = resources.getStringArray(R.array.update_log_current)
                val currentLog = StringBuilder()
                for (item in currentLogItems) currentLog.append("\n").append(item)
                val updateLogView = findViewById<TextView>(R.id.updatelog_view)
                updateLogView.text = currentLog.toString()
                StringUtil.setCopy(updateLogView, currentLog.toString())

                val versionName = packageManager.getPackageInfo(packageName, 0).versionName
                findViewById<TextView>(R.id.current_update_title).text = "本次更新 ($versionName)"
            } catch (e: PackageManager.NameNotFoundException) {
                e.printStackTrace()
            }

            // 开发者卡片：数据驱动动态创建（模板见 res/layout/cell_developer.xml），
            // 新增开发者只需往下面两个列表里加一项，不用再改布局。
            bindDevelopers(findViewById(R.id.developer_main_container), DEVELOPERS_MAIN)
            bindDevelopers(findViewById(R.id.developer_joint_container), DEVELOPERS_JOINT)

            findViewById<View>(R.id.author_words).setOnClickListener {
                eggClickAuthorWords++
                if (eggClickAuthorWords == 7) {
                    eggClickAuthorWords = 0
                    MsgUtil.showText("作者的话", getString(R.string.egg_about_author_words))
                }
            }

            findViewById<View>(R.id.toUncle).setOnClickListener {
                eggClickToUncle++
                if (eggClickToUncle == 7) {
                    eggClickToUncle = 0
                    MsgUtil.showText("给叔叔", getString(R.string.egg_about_to_uncle))
                }
            }

            // 开源协议 / 借鉴项目 / 图标许可已拆到独立页，这里只留入口
            findViewById<View>(R.id.opensource_entry).setOnClickListener {
                startActivity(Intent(this, OpenSourceActivity::class.java))
            }

            findViewById<View>(R.id.sponsor_list).setOnClickListener {
                val intent = Intent(this, SponsorActivity::class.java)
                startActivity(intent)
            }

            findViewById<View>(R.id.history_log_entry).setOnClickListener {
                val intent = Intent(this, UpdateHistoryActivity::class.java)
                startActivity(intent)
            }

            if (!ToolsUtil.isDebugBuild()) findViewById<View>(R.id.debug_tip).visibility = View.GONE
            findViewById<View>(R.id.version_code_card).setOnClickListener {
                if (SharedPreferencesUtil.getBoolean("developer", false)) {
                    MsgUtil.showMsg("已关闭开发者模式！")
                    SharedPreferencesUtil.putBoolean("developer", false)
                } else {
                    eggClickDev++
                    if (eggClickDev == 7) {
                        SharedPreferencesUtil.putBoolean("developer", true)
                        MsgUtil.showMsg("已启用开发者模式！")
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
                        .apply(RequestOptions.circleCropTransform())
                        .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                        .into(card.findViewById(R.id.dev_avatar))
                } catch (ignored: Exception) {
                }
            }

            if (dev.uid != -1L) {
                card.setOnClickListener {
                    startActivity(
                        Intent(this, UserInfoActivity::class.java).putExtra("mid", dev.uid)
                    )
                }
            }

            container.addView(card)
        }
    }
}