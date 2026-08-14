package com.RobinNotBad.BiliClient.navigation

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.net.toUri
import androidx.navigation.NavDeepLinkBuilder
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.MenuActivity
import com.RobinNotBad.BiliClient.model.PlayerData

import com.RobinNotBad.BiliClient.ui.live.ModernLiveRecommendActivity
import com.RobinNotBad.BiliClient.ui.menu.ModernMenuActivity
import com.RobinNotBad.BiliClient.ui.message.ModernMessageActivity
import com.RobinNotBad.BiliClient.ui.player.ModernPlayerActivity
import com.RobinNotBad.BiliClient.ui.search.ModernSearchActivity
import com.RobinNotBad.BiliClient.ui.splash.ModernSplashActivity
import com.RobinNotBad.BiliClient.activity.user.HistoryActivity
import com.RobinNotBad.BiliClient.ui.user.ModernMySpaceActivity
import com.RobinNotBad.BiliClient.ui.video.ModernPopularActivity
import com.RobinNotBad.BiliClient.ui.video.ModernRankingActivity
import com.RobinNotBad.BiliClient.ui.video.ModernRecommendActivity
import com.RobinNotBad.BiliClient.ui.video.ModernVideoListActivity

object AppNavigator {

    fun openSplash(context: Context): Intent {
        return Intent(context, ModernSplashActivity::class.java)
    }

    fun openMainMenu(context: Context, from: String = "start"): Intent {
        return Intent(context, ModernMenuActivity::class.java).apply {
            putExtra("from", from)
        }
    }

    fun openMainMenuLegacy(context: Context, from: String? = null): Intent {
        return Intent(context, MenuActivity::class.java).apply {
            if (from != null) putExtra("from", from)
        }
    }

    fun openRecommend(context: Context): Intent {
        return Intent(context, ModernRecommendActivity::class.java)
    }

    fun openPopular(context: Context): Intent {
        return Intent(context, ModernPopularActivity::class.java)
    }

    fun openRanking(context: Context): Intent {
        return Intent(context, ModernRankingActivity::class.java)
    }

    fun openSearch(context: Context): Intent {
        return Intent(context, ModernSearchActivity::class.java)
    }

    fun openDynamic(context: Context): Intent {
        return Intent(context, com.RobinNotBad.BiliClient.activity.dynamic.DynamicActivity::class.java)
    }

    fun openMessages(context: Context): Intent {
        return Intent(context, ModernMessageActivity::class.java)
    }

    fun openMySpace(context: Context): Intent {
        return Intent(context, ModernMySpaceActivity::class.java)
    }

    fun openHistory(context: Context): Intent {
        return Intent(context, HistoryActivity::class.java)
    }

    fun openLiveRecommend(context: Context): Intent {
        return Intent(context, ModernLiveRecommendActivity::class.java)
    }

    fun openVideoList(context: Context, type: String = "recommend"): Intent {
        return Intent(context, ModernVideoListActivity::class.java).apply {
            putExtra("type", type)
        }
    }

    fun openPlayer(
        context: Context,
        url: String,
        danmaku: String = "",
        title: String = "播放中...",
        aid: Long = 0L,
        cid: Long = 0L,
        mid: Long = 0L,
        progress: Int = 0,
        liveMode: Boolean = false,
        pagenames: ArrayList<String>? = null,
        cids: ArrayList<Long>? = null,
        currentPageIndex: Int = 0,
        qnStrList: ArrayList<String>? = null,
        qnValueList: ArrayList<Int>? = null,
        currentQuality: Int = 0
    ): Intent {
        return Intent(context, ModernPlayerActivity::class.java).apply {
            putExtra("url", url)
            putExtra("danmaku", danmaku)
            putExtra("title", title)
            putExtra("aid", aid)
            putExtra("cid", cid)
            putExtra("mid", mid)
            putExtra("progress", progress)
            putExtra("live_mode", liveMode)
            pagenames?.let { putStringArrayListExtra("pagenames", it) }
            cids?.let { putExtra("cids", it.toLongArray()) }
            putExtra("currentPageIndex", currentPageIndex)
            qnStrList?.let { putStringArrayListExtra("qnStrList", it) }
            qnValueList?.let { putIntegerArrayListExtra("qnValueList", it) }
            putExtra("currentQuality", currentQuality)
        }
    }

    fun openPlayerFromData(context: Context, data: PlayerData): Intent {
        return openPlayer(
            context = context,
            url = data.videoUrl,
            danmaku = data.danmakuUrl ?: "",
            title = data.title ?: "播放中...",
            aid = data.aid,
            cid = data.cid,
            mid = data.mid,
            progress = data.progress,
            liveMode = data.isLive,
            pagenames = data.pagenames,
            cids = data.cids?.map { it.toLong() }?.let { ArrayList(it) },
            currentPageIndex = data.currentPageIndex,
            qnStrList = data.qnStrList?.toCollection(ArrayList()),
            qnValueList = data.qnValueList?.map { it.toInt() }?.let { ArrayList(it) },
            currentQuality = data.qn
        )
    }

    fun buildDeepLink(destinationId: Int, args: Bundle? = null): android.app.PendingIntent {
        val builder = NavDeepLinkBuilder(android.app.Application())
            .setGraph(R.navigation.nav_graph)
            .setDestination(destinationId)

        if (args != null) builder.setArguments(args)

        return builder.createPendingIntent()
    }
}