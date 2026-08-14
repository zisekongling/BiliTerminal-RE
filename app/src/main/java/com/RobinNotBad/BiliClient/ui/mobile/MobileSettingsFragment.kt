package com.RobinNotBad.BiliClient.ui.mobile

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.settings.AboutActivity
import com.RobinNotBad.BiliClient.activity.settings.AnnouncementsActivity
import com.RobinNotBad.BiliClient.activity.settings.SettingDownloadActivity
import com.RobinNotBad.BiliClient.activity.settings.SettingInfoActivity
import com.RobinNotBad.BiliClient.activity.settings.SettingLaboratoryActivity
import com.RobinNotBad.BiliClient.activity.settings.SettingMenuActivity
import com.RobinNotBad.BiliClient.activity.settings.SettingPlayerChooseActivity
import com.RobinNotBad.BiliClient.activity.settings.SettingPrefActivity
import com.RobinNotBad.BiliClient.activity.settings.SettingRepliesActivity
import com.RobinNotBad.BiliClient.activity.settings.SettingSearchActivity
import com.RobinNotBad.BiliClient.activity.settings.SettingTerminalPlayerActivity
import com.RobinNotBad.BiliClient.activity.settings.SettingUIActivity
import com.RobinNotBad.BiliClient.activity.settings.TestActivity
import com.RobinNotBad.BiliClient.activity.settings.TodoListActivity
import com.RobinNotBad.BiliClient.activity.settings.TutorialManagerActivity
import com.RobinNotBad.BiliClient.activity.settings.UpdateActivity
import com.RobinNotBad.BiliClient.activity.settings.login.AccountSwitchActivity
import com.RobinNotBad.BiliClient.activity.settings.login.LoginActivity
import com.RobinNotBad.BiliClient.activity.settings.login.SpecialLoginActivity
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import com.google.android.material.card.MaterialCardView

/**
 * 移动端"设置"页面Fragment
 */
class MobileSettingsFragment : Fragment() {

    private var eggClick: Int = 0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_mobile_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val loginCookie = view.findViewById<MaterialCardView>(R.id.login_cookie)
        loginCookie.setOnClickListener {
            val intent = Intent(requireContext(), SpecialLoginActivity::class.java)
            intent.putExtra("login", false)
            startActivity(intent)
        }

        val accountSwitch = view.findViewById<MaterialCardView>(R.id.accountSwitch)
        accountSwitch.setOnClickListener {
            startActivity(Intent(requireContext(), AccountSwitchActivity::class.java))
        }

        val login = view.findViewById<MaterialCardView>(R.id.login)
        if (SharedPreferencesUtil.getLong("mid", 0) == 0L) {
            loginCookie.visibility = View.GONE
            login.visibility = View.VISIBLE
            login.setOnClickListener {
                val intent = Intent()
                if (Build.VERSION.SDK_INT >= 19)
                    intent.setClass(requireContext(), LoginActivity::class.java)
                else {
                    intent.setClass(requireContext(), SpecialLoginActivity::class.java)
                    intent.putExtra("login", true)
                }
                startActivity(intent)
            }
        }

        view.findViewById<MaterialCardView>(R.id.playerSetting).setOnClickListener {
            startActivity(Intent(requireContext(), SettingPlayerChooseActivity::class.java))
        }

        view.findViewById<MaterialCardView>(R.id.terminalPlayerSetting).setOnClickListener {
            startActivity(Intent(requireContext(), SettingTerminalPlayerActivity::class.java))
        }

        view.findViewById<MaterialCardView>(R.id.uiSetting).setOnClickListener {
            startActivity(Intent(requireContext(), SettingUIActivity::class.java))
        }

        view.findViewById<MaterialCardView>(R.id.menuSetting).setOnClickListener {
            startActivity(Intent(requireContext(), SettingMenuActivity::class.java))
        }

        view.findViewById<MaterialCardView>(R.id.searchSetting).setOnClickListener {
            startActivity(Intent(requireContext(), SettingSearchActivity::class.java))
        }

        view.findViewById<MaterialCardView>(R.id.prefSetting).setOnClickListener {
            startActivity(Intent(requireContext(), SettingPrefActivity::class.java))
        }

        view.findViewById<MaterialCardView>(R.id.repliesSetting).setOnClickListener {
            startActivity(Intent(requireContext(), SettingRepliesActivity::class.java))
        }

        view.findViewById<MaterialCardView>(R.id.infoSetting).setOnClickListener {
            startActivity(Intent(requireContext(), SettingInfoActivity::class.java))
        }

        view.findViewById<MaterialCardView>(R.id.laboratory).setOnClickListener {
            startActivity(Intent(requireContext(), SettingLaboratoryActivity::class.java))
        }

        view.findViewById<MaterialCardView>(R.id.downloadSetting).setOnClickListener {
            startActivity(Intent(requireContext(), SettingDownloadActivity::class.java))
        }

        view.findViewById<MaterialCardView>(R.id.checkUpdate).setOnClickListener {
            startActivity(Intent(requireContext(), UpdateActivity::class.java))
        }

        val about = view.findViewById<MaterialCardView>(R.id.about)
        about.setOnClickListener {
            startActivity(Intent(requireContext(), AboutActivity::class.java))
        }

        val eggList = resources.getStringArray(R.array.eggs)
        about.setOnLongClickListener {
            MsgUtil.showText("回声洞", eggList[eggClick])
            if (eggClick < eggList.size - 1) eggClick++
            true
        }

        view.findViewById<MaterialCardView>(R.id.announcement).setOnClickListener {
            startActivity(Intent(requireContext(), AnnouncementsActivity::class.java))
        }

        view.findViewById<MaterialCardView>(R.id.refresh_tutorial).setOnClickListener {
            startActivity(Intent(requireContext(), TutorialManagerActivity::class.java))
        }

        val test = view.findViewById<MaterialCardView>(R.id.test)
        test.visibility = if (SharedPreferencesUtil.getBoolean("developer", false)) View.VISIBLE else View.GONE
        test.setOnClickListener {
            startActivity(Intent(requireContext(), TestActivity::class.java))
        }

        view.findViewById<MaterialCardView>(R.id.todoList).setOnClickListener {
            startActivity(Intent(requireContext(), TodoListActivity::class.java))
        }

        view.findViewById<View>(R.id.scrollView).requestFocus()
    }
}