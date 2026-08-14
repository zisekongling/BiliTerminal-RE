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
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.RobinNotBad.BiliClient.ui.theme.BiliColors
import com.RobinNotBad.BiliClient.ui.theme.BiliDimens
import com.RobinNotBad.BiliClient.ui.theme.ThemeManager
import com.RobinNotBad.BiliClient.util.view.ImageAutoLoadScrollListener
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ModernVideoListActivity : AppCompatActivity() {

    private val viewModel: VideoListViewModel by viewModels()
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: VideoListAdapter
    private lateinit var loadingView: ProgressBar
    private lateinit var errorView: TextView
    private lateinit var contentLayout: FrameLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyTheme()
        setContentView(createMainLayout())
        setupRecyclerView()
        observeState()
    }

    private fun applyTheme() {
        window.statusBarColor = ThemeManager.BACKGROUND
        window.navigationBarColor = ThemeManager.BACKGROUND
    }

    private fun createMainLayout(): View {
        val density = resources.displayMetrics.density

        return FrameLayout(this).apply {
            setBackgroundColor(ThemeManager.BACKGROUND)
            id = View.generateViewId()

            val toolbar = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                val padding = (BiliDimens.SPACING_LG * density).toInt()
                setPadding(padding, padding, padding, padding)
                setBackgroundColor(ThemeManager.SURFACE)
                elevation = BiliDimens.ELEVATION_CARD * density
                id = View.generateViewId()

                val titleText = TextView(context).apply {
                    text = intent.getStringExtra("title") ?: "热门视频"
                    textSize = BiliDimens.TITLE_MEDIUM
                    setTextColor(ThemeManager.TEXT_PRIMARY)
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                }
                addView(titleText)
            }

            contentLayout = FrameLayout(context).apply {
                setId(View.generateViewId())
            }

            recyclerView = RecyclerView(context).apply {
                layoutManager = LinearLayoutManager(context)
                setPadding(0, 0, 0, (BiliDimens.SPACING_SM * density).toInt())
                clipToPadding = false
                setHasFixedSize(true)
                setItemViewCacheSize(20)
                setDrawingCacheEnabled(true)
                setDrawingCacheQuality(View.DRAWING_CACHE_QUALITY_HIGH)
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

            contentLayout.addView(recyclerView,
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            contentLayout.addView(loadingView,
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    gravity = Gravity.CENTER
                })
            contentLayout.addView(errorView,
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    gravity = Gravity.CENTER
                })

            val toolbarLp = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (48 * density).toInt()
            ).apply { gravity = Gravity.TOP }
            addView(toolbar, toolbarLp)

            val contentLp = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ).apply { topMargin = (48 * density).toInt() }
            addView(contentLayout, contentLp)
        }
    }

    private fun setupRecyclerView() {
        adapter = VideoListAdapter(this)
        recyclerView.adapter = adapter
        ImageAutoLoadScrollListener.install(recyclerView)
    }

    private fun observeState() {
        lifecycleScope.launch {
            viewModel.popularState.collectLatest { state ->
                adapter.submitList(state.items)

                loadingView.visibility = if (state.isLoading && state.items.isEmpty()) View.VISIBLE else View.GONE

                if (state.error != null && state.items.isEmpty()) {
                    errorView.text = state.error
                    errorView.visibility = View.VISIBLE
                    recyclerView.visibility = View.GONE
                } else {
                    errorView.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE
                }
            }
        }
    }
}