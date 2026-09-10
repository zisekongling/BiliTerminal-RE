package com.RobinNotBad.BiliClient.tutorial

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.InputDevice
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.constraintlayout.utils.widget.ImageFilterView
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.base.BaseActivity
import com.RobinNotBad.BiliClient.ui.widget.RotaryEncoderSupport
import com.RobinNotBad.BiliClient.util.SettingsKeys
import com.google.android.material.button.MaterialButton
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

/**
 * 教程分页展示页（POC）。
 *
 * 与旧 `TutorialActivity` 的区别：
 * 1. 教程内容来自 [Tutorials] 注册表（Kotlin DSL），不再解析 XML；
 * 2. 一篇教程内部分页，左右滑动或**旋转表冠**翻页，顶部显示「当前页/总页数」；
 * 3. 同页多篇教程**串行**展示（一次一篇，点「已阅」后原地切下一篇），不再连续 startActivity 叠栈；
 * 4. 强制判定按 [TutorialKind] 区分：
 *    - GUIDE：翻到最后一页 **且** 停留满 [WAIT_SECONDS] 秒，按钮才可点，返回键无效；
 *    - NOTICE：随时可点「已阅」，返回键等同已读。
 *
 * 入参：`ids`（教程 id 列表，串行顺序）、`index`（从第几个开始）。
 */
class TutorialPagerActivity : BaseActivity() {

    companion object {
        /** GUIDE 的强制停留时长（秒）。 */
        private const val WAIT_SECONDS = 3

        /** 旋冠累积到该幅度翻一页。 */
        private const val ROTARY_STEP = 1f

        private const val EXTRA_IDS = "ids"
        private const val EXTRA_INDEX = "index"

        /**
         * 教程页是否正在前台展示。
         *
         * 页面级滑动提示（HINT）据此避让：教程盖在上面时，提示既不该显示、
         * 更不能写入「已展示」标记，否则用户永远看不到那条提示。
         */
        @JvmStatic
        @Volatile
        var isShowing: Boolean = false
            private set

        /** 启动教程页；[tutorials] 为空时什么都不做。 */
        @JvmStatic
        fun start(context: android.content.Context, tutorials: List<Tutorial>, index: Int = 0) {
            if (tutorials.isEmpty()) return
            val intent = android.content.Intent(context, TutorialPagerActivity::class.java)
                .putStringArrayListExtra(EXTRA_IDS, ArrayList(tutorials.map { it.id }))
                .putExtra(EXTRA_INDEX, index)
            context.startActivity(intent)
        }
    }

    private lateinit var pager: ViewPager2
    private lateinit var titleView: TextView
    private lateinit var progressView: TextView
    private lateinit var closeBtn: MaterialButton

    private var queue: List<String> = emptyList()
    private var queueIndex = 0
    private var tutorial: Tutorial? = null

    private var elapsedSeconds = 0
    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            elapsedSeconds++
            updateCloseButton()
            if (elapsedSeconds < WAIT_SECONDS) handler.postDelayed(this, 1000L)
        }
    }

    private val pageAdapter = PageAdapter()

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isShowing = true
        setContentView(R.layout.activity_tutorial_pager)

        titleView = findViewById(R.id.text_title)
        progressView = findViewById(R.id.text_progress)
        closeBtn = findViewById(R.id.close_btn)
        pager = findViewById(R.id.tutorial_pager)

        queue = intent.getStringArrayListExtra(EXTRA_IDS).orEmpty()
        val startIndex = intent.getIntExtra(EXTRA_INDEX, 0)

        pager.adapter = pageAdapter
        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateProgress()
                updateCloseButton()
            }
        })
        setupRotaryPaging()

        closeBtn.setOnClickListener {
            markRead()
            goNext()
        }

        if (queue.isEmpty()) {
            finish()
            return
        }
        showTutorial(startIndex)
    }

    /** 切换到队列里的第 [index] 篇教程（同一个 Activity 内切换，不叠栈）。 */
    private fun showTutorial(index: Int) {
        val id = queue.getOrNull(index)
        val target = if (id == null) null else Tutorials.byId(id)
        if (target == null) {
            finish()
            return
        }
        queueIndex = index
        tutorial = target

        // 版本号变大导致教程重新弹出时，在标题上标出来，避免用户疑惑「怎么又弹了一遍」
        titleView.text = if (TutorialStore.isUpdated(target)) {
            "${target.title}（已更新）"
        } else {
            target.title
        }
        elapsedSeconds = 0
        handler.removeCallbacks(ticker)

        pageAdapter.notifyDataSetChanged()
        pager.setCurrentItem(0, false)
        updateProgress()
        updateCloseButton()

        if (target.isMandatory) {
            handler.postDelayed(ticker, 1000L)
        }
    }

    /** 点「已阅」或 NOTICE 返回后：还有下一篇就原地切，否则关闭。 */
    private fun goNext() {
        val next = queueIndex + 1
        if (next < queue.size) {
            showTutorial(next)
        } else {
            finish()
        }
    }

    private fun markRead() {
        val target = tutorial ?: return
        TutorialStore.markRead(target)
    }

    private fun updateProgress() {
        val target = tutorial ?: return
        // 单页教程不显示页码：1/1 没有意义，还会占掉本就紧张的顶栏空间
        if (target.pageCount <= 1) {
            progressView.visibility = View.GONE
            return
        }
        progressView.visibility = View.VISIBLE
        progressView.text = String.format(
            Locale.getDefault(),
            "%d/%d",
            pager.currentItem + 1,
            target.pageCount
        )
    }

    /**
     * 更新「已阅」按钮的可用状态与文案。
     *
     * GUIDE 需要「翻到最后一页」+「停留满时长」两个条件同时满足；NOTICE 始终可点。
     */
    private fun updateCloseButton() {
        val target = tutorial ?: return
        if (!target.isMandatory) {
            closeBtn.isEnabled = true
            closeBtn.text = getString(R.string.btn_read)
            return
        }

        val readAll = pager.currentItem >= target.pageCount - 1
        val timeUp = elapsedSeconds >= WAIT_SECONDS
        val canClose = readAll && timeUp

        closeBtn.isEnabled = canClose
        closeBtn.text = when {
            canClose -> getString(R.string.btn_read)
            // 还没翻到最后一页：明确告诉用户往哪滑（水平分页，向左滑 = 下一页）
            !readAll -> "向左滑动以完成教程"
            else -> String.format(Locale.getDefault(), "已阅(%ds)", max(0, WAIT_SECONDS - elapsedSeconds))
        }
    }

    /**
     * 旋冠翻页：与 `RotaryScrollView` 用同一套开关（`ui_rotatory_enable` / `ui_rotatory_scroll`）。
     *
     * 注意：ViewPager2 内部是 RecyclerView，会自行消费部分滚动事件，
     * 因此这里直接挂在 pager 上；若真机上手感不对，需要改成自定义容器拦截。
     */
    private fun setupRotaryPaging() {
        val multiple = RotaryEncoderSupport.multipleOf(SettingsKeys.UI_ROTATORY_SCROLL)
        if (multiple <= 0f) return

        var accumulated = 0f
        pager.setOnGenericMotionListener { _, event ->
            if (event.action != MotionEvent.ACTION_SCROLL ||
                event.source != InputDevice.SOURCE_ROTARY_ENCODER
            ) {
                return@setOnGenericMotionListener false
            }
            // 与 RotaryScrollView 保持同向：向下滚 = 往后翻
            accumulated += -event.getAxisValue(MotionEvent.AXIS_SCROLL) * multiple
            if (abs(accumulated) >= ROTARY_STEP) {
                val target = tutorial
                val next = pager.currentItem + if (accumulated > 0) 1 else -1
                if (target != null && next in 0 until target.pageCount) {
                    pager.setCurrentItem(next, true)
                }
                accumulated = 0f
            }
            true
        }
    }

    override fun onBackPressed() {
        val target = tutorial ?: run { super.onBackPressed(); return }
        // GUIDE 不允许跳过：必须翻完并停留满时长后点「已阅」
        if (target.isMandatory) return
        // NOTICE 主动关闭视为已读，避免下次又弹
        markRead()
        goNext()
    }

    /** 教程页自身不参与自动触发，否则会递归弹自己。 */
    override fun tutorialAutoTriggerEnabled(): Boolean = false

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        isShowing = false
        super.onDestroy()
    }

    /** 一页 = 一段富文本 + 一张可选配图。 */
    private inner class PageAdapter : RecyclerView.Adapter<PageAdapter.PageHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_tutorial_page, parent, false)
            return PageHolder(view)
        }

        override fun onBindViewHolder(holder: PageHolder, position: Int) {
            holder.bind(tutorial?.pages?.getOrNull(position), position == itemCount - 1)
        }

        override fun getItemCount(): Int = tutorial?.pageCount ?: 0

        inner class PageHolder(view: View) : RecyclerView.ViewHolder(view) {
            private val content: TextView = view.findViewById(R.id.page_content)
            private val image: ImageFilterView = view.findViewById(R.id.page_image)

            fun bind(page: TutorialPage?, isLastPage: Boolean) {
                val spans = page?.spans.orEmpty()
                content.text = TutorialRenderer.toSpannable(spans)

                // 页内 image() 优先；最后一页若没写页内图片，回退到教程级配图。
                // 旧 TutorialActivity 就是把配图放在正文之后，语义保持一致。
                val pageImage = spans.firstOrNull { it.imageRes != null }?.imageRes
                val imageRes = pageImage ?: if (isLastPage) tutorial?.imageRes else null

                if (imageRes != null) {
                    image.setImageResource(imageRes)
                    image.visibility = View.VISIBLE
                } else {
                    image.visibility = View.GONE
                }
            }
        }
    }
}
