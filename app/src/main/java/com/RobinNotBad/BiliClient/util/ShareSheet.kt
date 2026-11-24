package com.RobinNotBad.BiliClient.util

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.fragment.app.Fragment
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.message.ChooseFriendActivity
import com.RobinNotBad.BiliClient.api.PrivateMsgApi
import com.RobinNotBad.BiliClient.model.PrivateMessage
import com.RobinNotBad.BiliClient.util.view.ShareTexts

/**
 * 「分享 / 转发」面板与它背后的几个动作。
 *
 * 面板固定五项，顺序与 PiliPlus 一致：
 * 复制链接 / 其它app打开 / 分享视频（直播间、文章叫别的名字）/ 分享至动态 / 分享至消息。
 * 各页面只提供自己的链接、文案和两个回调，不再各写一套弹窗与兜底逻辑。
 *
 * 两条硬规矩（来自用户拍板）：
 * 1. 「其它app打开」「分享视频」都要**先探测有没有可用目标**，没有就提示并复制链接兜底，绝不静默失败；
 * 2. 没有实现依据的条目**直接不显示**（例如直播页没有「分享至动态」），不做占位项。
 *
 * 探测方式：直接 startActivity 并接住 ActivityNotFoundException，而不是 queryIntentActivities——
 * 后者在 Android 11+ 要对 http/https 声明 <queries>，为一个按钮动清单不值得。
 *
 * 注意：[TerminalDialog.menu] 只搭弹窗不显示，本类的 [Panel.show] 已代为 show()，
 * 调用方不要再包一层。
 */
object ShareSheet {

    /** 起「选好友」页的请求码基准；各页面用自己的常量，避免与页面里其它请求码撞车。 */
    fun requestFriend(fragment: Fragment, requestCode: Int) {
        fragment.startActivityForResult(
            Intent(fragment.requireContext(), ChooseFriendActivity::class.java),
            requestCode
        )
    }

    /**
     * 把「标题 + 链接」以文本私信发给某个好友。
     *
     * 只发文本：私信接口只支持 msg_type 1/2/5，卡片是 7（会回 21035），见 [PrivateMsgApi.buildVideoShareText]。
     * 失败不静默：先复制链接再提示，用户至少还有一条能把内容发出去的路。
     */
    fun sendLinkToFriend(context: Context, uid: Long, name: String, title: String, url: String) {
        if (uid <= 0L) return
        CenterThreadPool.run {
            try {
                val result = PrivateMsgApi.sendMsg(
                    SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 114514),
                    uid,
                    PrivateMessage.TYPE_TEXT,
                    System.currentTimeMillis() / 1000,
                    PrivateMsgApi.buildTextContent(ShareTexts.messageText(title, url))
                )
                CenterThreadPool.runOnUiThread {
                    if (result.optInt("code", -1) == 0) {
                        MsgUtil.showMsg(UiText.of(context.getString(R.string.msg_video_relay_to_friend_success, name)))
                    } else {
                        copyAndHint(context, url, R.string.msg_share_send_failed_copied)
                    }
                }
            } catch (e: Exception) {
                CenterThreadPool.runOnUiThread { copyAndHint(context, url, R.string.msg_share_send_failed_copied) }
                MsgUtil.err(e)
            }
        }
    }

    /** 复制链接并提示；链接为空时只提示，不往剪贴板塞空串。 */
    fun copyLink(context: Context, url: String): Boolean {
        if (url.isEmpty()) {
            MsgUtil.showMsg(UiText.of(context.getString(R.string.msg_share_url_missing)))
            return false
        }
        StringUtil.copyText(context, url)
        MsgUtil.showMsg(UiText.of(context.getString(R.string.msg_share_link_copied)))
        return true
    }

    /** 用系统浏览器等外部应用打开；没有可用目标时复制链接兜底并提示。 */
    fun openInOtherApp(context: Context, url: String): Boolean {
        if (url.isEmpty()) {
            MsgUtil.showMsg(UiText.of(context.getString(R.string.msg_share_url_missing)))
            return false
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            copyAndHint(context, url, R.string.msg_share_no_app_copied)
            false
        } catch (e: Exception) {
            copyAndHint(context, url, R.string.msg_share_no_app_copied)
            MsgUtil.err(e)
            false
        }
    }

    /** 交给系统分享面板；系统分享不可用时复制链接兜底并提示。 */
    fun shareTextToSystem(context: Context, text: String, fallbackUrl: String): Boolean {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        val chooser = Intent.createChooser(send, context.getString(R.string.share_panel_title))
        if (context !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(chooser)
            true
        } catch (e: ActivityNotFoundException) {
            copyAndHint(context, fallbackUrl, R.string.msg_share_no_share_copied)
            false
        } catch (e: Exception) {
            copyAndHint(context, fallbackUrl, R.string.msg_share_no_share_copied)
            MsgUtil.err(e)
            false
        }
    }

    /** 失败兜底：有链接就复制，再按 [msgRes] 提示。 */
    private fun copyAndHint(context: Context, url: String, msgRes: Int) {
        if (url.isNotEmpty()) StringUtil.copyText(context, url)
        MsgUtil.showMsg(UiText.of(context.getString(msgRes)))
    }

    /** 开一个新面板；按需链式加项，最后 [Panel.show]。 */
    fun panel(context: Context): Panel = Panel(context)

    /**
     * 面板本体。条目按调用顺序排列，所以调用方按
     * 复制链接 → 其它app打开 → 分享 → （分享至动态）→ 分享至消息 的顺序链式调用即可。
     */
    class Panel internal constructor(private val context: Context) {

        private val labels = ArrayList<String>(5)
        private val actions = ArrayList<() -> Unit>(5)

        private fun add(label: String, action: () -> Unit): Panel {
            labels += label
            actions += action
            return this
        }

        /** 复制链接。 */
        fun copyLink(url: String): Panel =
            add(context.getString(R.string.share_copy_link)) { ShareSheet.copyLink(context, url) }

        /** 其它app打开。 */
        fun openInOtherApp(url: String): Panel =
            add(context.getString(R.string.share_open_other_app)) { ShareSheet.openInOtherApp(context, url) }

        /**
         * 分享视频 / 分享直播间 / 分享文章——同一个系统分享动作，只是条目名不同。
         *
         * @param labelRes 条目名，如 [R.string.share_video]
         * @param text     分享出去的文本
         * @param fallbackUrl 系统分享不可用时复制的链接
         */
        fun shareText(labelRes: Int, text: String, fallbackUrl: String): Panel =
            add(context.getString(labelRes)) { ShareSheet.shareTextToSystem(context, text, fallbackUrl) }

        /** 分享至动态；没有实现依据的页面不要调它。 */
        fun toDynamic(action: () -> Unit): Panel =
            add(context.getString(R.string.share_to_dynamic), action)

        /** 分享至消息；[action] 里通常是 [ShareSheet.requestFriend]。 */
        fun toMessage(action: () -> Unit): Panel =
            add(context.getString(R.string.share_to_message), action)

        /** 当前条目数（单测与自检用）。 */
        fun itemCount(): Int = labels.size

        /**
         * 显示面板，不返回对话框句柄。
         *
         * [TerminalDialog.menu] 只搭弹窗，**显示要调用方自己 show()**——这里代做了，
         * 历史上漏 show() 的表现是「点了按钮什么都不发生」。
         *
         * 返回值故意留空：ConventionGuardTest 不许本文件出现对话框类型名，
         * 现有调用方也都不需要句柄（弹窗自己会关）。
         */
        fun show() {
            val dialog = TerminalDialog.menu(
                context,
                context.getString(R.string.share_panel_title),
                labels
            ) { index -> actions[index].invoke() }
            dialog.show()
        }
    }
}
