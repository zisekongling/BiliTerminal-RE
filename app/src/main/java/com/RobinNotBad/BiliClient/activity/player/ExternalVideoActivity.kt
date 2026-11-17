package com.RobinNotBad.BiliClient.activity.player

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.IncomingMediaResolver
import com.RobinNotBad.BiliClient.util.Logu
import com.RobinNotBad.BiliClient.util.MsgUtil

/**
 * 外部视频入口：让文件管理器 / 相册 / 分享菜单把本地视频交给本应用播放。
 *
 * 本页是全应用第二个**有意导出**的 Activity（另一个是 [com.RobinNotBad.BiliClient.activity.GetIntentActivity]），
 * 导出理由：作为系统的视频打开方式被第三方拉起，不导出就收不到 ACTION_VIEW / ACTION_SEND。
 * 它自己不做任何播放，也不信任调用方：拿到 Uri 后交给 [IncomingMediaResolver] 解析成可读路径，
 * 再起内置的 [PlayerActivity] 播放，然后立刻 finish——导出的攻击面只有「读调用方主动授权的一个文件」。
 *
 * 只认本地文件（content:// 与 file://）。网络上随手贴个 http 链接不归这里管：那属于 B 站视频/直播，
 * 走 GetIntentActivity 的域名白名单，否则任何人都能用任意 URL 拉起播放器。
 */
class ExternalVideoActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uri = incomingUri(intent)
        if (uri == null) {
            Logu.e(TAG, "没有可用的 Uri，action=" + intent?.action)
            MsgUtil.showMsgLong(getString(R.string.msg_external_video_no_uri))
            finish()
            return
        }

        val appContext = applicationContext
        CenterThreadPool.run {
            val result = runCatching { IncomingMediaResolver.resolve(appContext, uri) }
            runOnUiThread {
                result.onSuccess { play(it) }.onFailure { error ->
                    // 解析失败的原因（没权限 / 空文件 / 空间不足）只进日志，用户看到的是一句提示：
                    // 这类错误对用户没有可操作性，抛原文反而增加困惑
                    Logu.e(TAG, "解析失败：" + uri + "，" + error)
                    MsgUtil.showMsgLong(getString(R.string.msg_external_video_unplayable))
                    finish()
                }
            }
        }
    }

    /** ACTION_VIEW 看 data，ACTION_SEND 看 EXTRA_STREAM（部分应用只填 ClipData）。 */
    @Suppress("DEPRECATION")
    private fun incomingUri(intent: Intent?): Uri? {
        if (intent == null) return null
        if (intent.action == Intent.ACTION_SEND) {
            val stream = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            if (stream != null) return stream
            return intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
        }
        return intent.data
    }

    private fun play(resolved: IncomingMediaResolver.Resolved) {
        val title = resolved.title.ifEmpty { getString(R.string.text_external_video_untitled) }
        Logu.d(TAG, "开始播放：" + resolved.path + "（临时文件=" + resolved.temporary + "）")
        // 从 application context 起播放器：解析可能比本页存活时间更长，用本页当 context 会在
        // 已 finish 的情况下抛 BadTokenException
        val playerIntent = Intent(this, PlayerActivity::class.java)
            .putExtra("url", resolved.path)
            .putExtra("title", title)
            .putExtra("danmaku", "")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        applicationContext.startActivity(playerIntent)
        finish()
    }

    companion object {
        private const val TAG = "ExternalVideo"
    }
}
