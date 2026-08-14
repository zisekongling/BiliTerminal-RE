package com.RobinNotBad.BiliClient.ui.live

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.RobinNotBad.BiliClient.ui.theme.BiliDimens
import com.RobinNotBad.BiliClient.ui.theme.ThemeManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ModernLiveRecommendActivity : AppCompatActivity() {

    private val viewModel: LiveRecommendViewModel by viewModels()
    private lateinit var recyclerView: RecyclerView
    private lateinit var swipeRefresh: SwipeRefreshLayout

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

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(ThemeManager.BACKGROUND)

            val toolbar = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                val pad = (BiliDimens.SPACING_LG * density).toInt()
                setPadding(pad, pad, pad, pad)
                setBackgroundColor(ThemeManager.SURFACE)
                elevation = BiliDimens.ELEVATION_CARD * density
                addView(TextView(context).apply {
                    text = "推荐直播"
                    textSize = BiliDimens.TITLE_MEDIUM
                    setTextColor(ThemeManager.TEXT_PRIMARY)
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                })
            }

            swipeRefresh = SwipeRefreshLayout(context).apply {
                setColorSchemeColors(ThemeManager.PRIMARY)
                setOnRefreshListener { viewModel.refresh() }
                recyclerView = RecyclerView(context).apply {
                    layoutManager = GridLayoutManager(context, 2)
                    clipToPadding = false
                    addOnScrollListener(object : RecyclerView.OnScrollListener() {
                        override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                            super.onScrolled(rv, dx, dy)
                            val lm = rv.layoutManager as? GridLayoutManager ?: return
                            if (lm.findLastCompletelyVisibleItemPosition() >= (lm.itemCount - 4) && dy > 0) {
                                viewModel.loadMore()
                            }
                        }
                    })
                }
                addView(recyclerView)
            }

            addView(toolbar)
            addView(swipeRefresh, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
    }

    private fun setupRecyclerView() {
        recyclerView.adapter = LiveRoomAdapter(this) { room ->
            navigateToLive(room.roomId)
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            viewModel.state.collectLatest { s ->
                (recyclerView.adapter as? LiveRoomAdapter)?.submitList(s.rooms)
                swipeRefresh.isRefreshing = s.isRefreshing
            }
        }
    }

    private fun navigateToLive(roomId: Long) {
        try {
            val intent = android.content.Intent().apply {
                setClassName(
                    this@ModernLiveRecommendActivity,
                    "com.RobinNotBad.BiliClient.activity.live.LiveInfoActivity"
                )
                putExtra("room_id", roomId)
            }
            startActivity(intent)
        } catch (_: Exception) {}
    }
}

class LiveRoomAdapter(
    private val context: android.content.Context,
    private val onClick: (LiveRoomItem) -> Unit
) : androidx.recyclerview.widget.ListAdapter<LiveRoomItem, LiveRoomAdapter.H>(
    object : androidx.recyclerview.widget.DiffUtil.ItemCallback<LiveRoomItem>() {
        override fun areItemsTheSame(o: LiveRoomItem, n: LiveRoomItem) = o.roomId == n.roomId
        override fun areContentsTheSame(o: LiveRoomItem, n: LiveRoomItem) = o == n
    }
) {
    class H(v: View) : RecyclerView.ViewHolder(v) {
        val title: TextView = v.findViewWithTag("live_title")
        val name: TextView = v.findViewWithTag("live_name")
        val online: TextView = v.findViewWithTag("live_online")
    }

    override fun onCreateViewHolder(p: ViewGroup, vt: Int): H {
        val d = context.resources.displayMetrics.density

        return H(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (BiliDimens.SPACING_SM * d).toInt()
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(ThemeManager.CARD)
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (140 * d).toInt()
            )

            val cover = LinearLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
                setBackgroundColor(ThemeManager.PRIMARY_LIGHT)
                gravity = Gravity.TOP or Gravity.END
                val tp = (4 * d).toInt()
                setPadding(0, 0, tp, 0)

                addView(TextView(context).apply {
                    tag = "live_online"
                    textSize = BiliDimens.CAPTION
                    setTextColor(ThemeManager.ON_PRIMARY.toInt() or (0xCC shl 24))
                    text = "LIVE"
                    val sp = (4 * d).toInt()
                    setPadding(sp, sp, sp, sp)
                })
            }

            addView(cover)

            addView(TextView(context).apply {
                tag = "live_title"
                textSize = BiliDimens.BODY_MEDIUM
                setTextColor(ThemeManager.TEXT_PRIMARY)
                maxLines = 1
                setPadding(0, (4 * d).toInt(), 0, 0)
            })

            addView(TextView(context).apply {
                tag = "live_name"
                textSize = BiliDimens.CAPTION
                setTextColor(ThemeManager.TEXT_SECONDARY)
                setPadding(0, (2 * d).toInt(), 0, 0)
            })
        })
    }

    override fun onBindViewHolder(h: H, pos: Int) {
        val item = getItem(pos)
        h.title.text = item.title
        h.name.text = item.userName
        h.online.text = "${formatCount(item.online)} 人观看"
        h.itemView.setOnClickListener { onClick(item) }
    }

    companion object {
        fun formatCount(c: Int) = if (c >= 10000) "${"%.1f".format(c / 10000f)}万" else c.toString()
    }
}