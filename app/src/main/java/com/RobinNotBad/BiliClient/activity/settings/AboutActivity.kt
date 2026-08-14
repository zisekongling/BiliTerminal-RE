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

                val updateLog = ToolsUtil.getUpdateLog(this)
                (findViewById<TextView>(R.id.updatelog_view)).text = "\n更新细节：$updateLog"
                StringUtil.setCopy(findViewById(R.id.updatelog_view), updateLog)
            } catch (e: PackageManager.NameNotFoundException) {
                e.printStackTrace()
            }

            val developerAvaterViews: List<ImageView> = ArrayList<ImageView>().apply {
                add(findViewById(R.id.robinAvatar))
                add(findViewById(R.id.duduAvatar))
                add(findViewById(R.id.dadaAvatar))
                add(findViewById(R.id.moyeAvatar))
                add(findViewById(R.id.silentAvatar))
                add(findViewById(R.id.huanliAvatar))
                add(findViewById(R.id.jankAvatar))
                add(findViewById(R.id.traeAvatar))
            }
            val developerAvaters: List<Int> = ArrayList<Int>().apply {
                add(R.mipmap.avatar_robin)
                add(R.mipmap.avatar_dudu)
                add(-1)
                add(R.mipmap.avatar_moye)
                add(R.mipmap.avatar_silent)
                add(R.mipmap.avatar_huanli)
                add(R.mipmap.avatar_jank)
                add(-1)
            }
            val developerCardList: List<MaterialCardView> = ArrayList<MaterialCardView>().apply {
                add(findViewById(R.id.robin_card))
                add(findViewById(R.id.dudu_card))
                add(findViewById(R.id.dada_card))
                add(findViewById(R.id.moye_card))
                add(findViewById(R.id.silent_card))
                add(findViewById(R.id.huanli_card))
                add(findViewById(R.id.jank_card))
                add(findViewById(R.id.trae_card))
            }
            val developerUidList: List<Long> = ArrayList<Long>().apply {
                add(646521226L)
                add(517053179L)
                add(432128342L)
                add(394675616L)
                add(40140732L)
                add(673815151L)
                add(661403494L)
                add(591904067L)
            }

            for (i in developerAvaterViews.indices) {
                val finalI = i
                if (developerAvaters[i] != -1) try {
                    Glide.with(this).load(developerAvaters[i])
                        .transition(GlideUtil.getTransitionOptions())
                        .placeholder(R.mipmap.akari)
                        .apply(RequestOptions.circleCropTransform())
                        .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                        .into(developerAvaterViews[i])
                } catch (ignored: Exception) {
                }


                developerCardList[i].setOnClickListener {
                    val uid = developerUidList[finalI]
                    if (uid == -1L) return@setOnClickListener
                    val intent = Intent()
                        .setClass(this, UserInfoActivity::class.java)
                        .putExtra("mid", uid)
                    startActivity(intent)
                }
            }

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

            findViewById<View>(R.id.icon_license_list).setOnClickListener {
                val str = StringBuilder(getString(R.string.desc_icon_license))

                val logItems = resources.getStringArray(R.array.icon_license)
                for (i in logItems.indices)
                    str.append('\n').append((i + 1)).append('.').append(logItems[i])
                MsgUtil.showText("开源图标的信息", str.toString())
            }

            findViewById<View>(R.id.sponsor_list).setOnClickListener {
                val intent = Intent(this, SponsorActivity::class.java)
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
}