package com.RobinNotBad.BiliClient.ui.video

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.RobinNotBad.BiliClient.BiliTerminal
import com.RobinNotBad.BiliClient.ui.theme.BiliDimens
import com.RobinNotBad.BiliClient.ui.theme.ThemeManager
import com.RobinNotBad.BiliClient.ui.video.viewmodel.RankingVideoViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ModernRankingActivity : AppCompatActivity() {

    private val viewModel: RankingVideoViewModel by viewModels()
    private lateinit var adapter: ModernVideoCardAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var loadingView: ProgressBar
    private lateinit var errorView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyTheme()
        setContentView(createLayout())
        setupRecyclerView()
        observeState()
    }

    private fun applyTheme() {
        window.statusBarColor = ThemeManager.BACKGROUND
        window.navigationBarColor = ThemeManager.BACKGROUND
    }

    private fun createLayout(): View {
        val density = resources.displayMetrics.density

        return FrameLayout(this).apply {
            setBackgroundColor(ThemeManager.BACKGROUND)

            val toolbar = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                val pad = (BiliDimens.SPACING_LG * density).toInt()
                setPadding(pad, pad, pad, pad)
                setBackgroundColor(ThemeManager.SURFACE)
                elevation = BiliDimens.ELEVATION_CARD * density

                val title = TextView(context).apply {
                    text = "全站排行榜"
                    textSize = BiliDimens.TITLE_MEDIUM
                    setTextColor(ThemeManager.TEXT_PRIMARY)
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                }

                val tabLayout = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    val tabPad = (BiliDimens.SPACING_XL * density).toInt()
                    setPadding(tabPad, 0, 0, 0)
                }

                listOf("全站" to 0, "动画" to 1, "音乐" to 3, "游戏" to 4, "知识" to 36).forEach { (name, rid) ->
                    tabLayout.addView(TextView(context).apply {
                        text = name
                        textSize = BiliDimens.BODY_MEDIUM
                        setTextColor(ThemeManager.TEXT_SECONDARY)
                        val tp = (BiliDimens.SPACING_SM * density).toInt()
                        setPadding(tp, 0, tp, 0)
                        setOnClickListener { viewModel.loadRanking(rid) }
                    })
                }

                addView(title)
                addView(tabLayout)
            }

            swipeRefresh = SwipeRefreshLayout(context).apply {
                setColorSchemeColors(ThemeManager.PRIMARY, ThemeManager.SECONDARY, ThemeManager.PRIMARY_DARK)
                setOnRefreshListener { viewModel.refresh() }
                recyclerView = RecyclerView(context).apply {
                    layoutManager = LinearLayoutManager(context)
                    setPadding(0, (48 * density).toInt(), 0, 0)
                    clipToPadding = false
                }
                addView(recyclerView)
            }

            loadingView = ProgressBar(context).apply {
                isIndeterminate = true
                indeterminateTintList = android.content.res.ColorStateList.valueOf(ThemeManager.PRIMARY)
            }

            errorView = TextView(context).apply {
                gravity = Gravity.CENTER
                textSize = BiliDimens.BODY_MEDIUM
                setTextColor(ThemeManager.TEXT_TERTIARY)
                visibility = View.GONE
            }

            addView(toolbar, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (48 * density).toInt()
            ).apply { gravity = Gravity.TOP })
            addView(swipeRefresh)
            addView(loadingView, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.CENTER })
            addView(errorView, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            ))
        }
    }

    private fun setupRecyclerView() {
        adapter = ModernVideoCardAdapter(this) { video ->
            BiliTerminal.jumpToVideo(this, video.aid)
        }
        recyclerView.adapter = adapter
    }

    private fun observeState() {
        lifecycleScope.launch {
            viewModel.state.collectLatest { state ->
                adapter.submitList(state.items)
                swipeRefresh.isRefreshing = state.isRefreshing

                if (state.isLoading && state.items.isEmpty()) {
                    loadingView.visibility = View.VISIBLE
                    swipeRefresh.visibility = View.GONE
                } else {
                    loadingView.visibility = View.GONE
                    swipeRefresh.visibility = View.VISIBLE
                }

                if (state.error != null && state.items.isEmpty()) {
                    errorView.text = state.error
                    errorView.visibility = View.VISIBLE
                } else {
                    errorView.visibility = View.GONE
                }
            }
        }
    }
}