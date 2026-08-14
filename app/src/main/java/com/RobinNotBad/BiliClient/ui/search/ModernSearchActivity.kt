package com.RobinNotBad.BiliClient.ui.search

import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.RobinNotBad.BiliClient.BiliTerminal
import com.RobinNotBad.BiliClient.ui.theme.BiliColors
import com.RobinNotBad.BiliClient.ui.theme.BiliDimens
import com.RobinNotBad.BiliClient.ui.theme.ThemeManager
import com.RobinNotBad.BiliClient.ui.video.ModernVideoCardAdapter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ModernSearchActivity : AppCompatActivity() {

    private val viewModel: ModernSearchViewModel by viewModels()
    private lateinit var keywordInput: EditText
    private lateinit var historyRecycler: RecyclerView
    private lateinit var suggestionRecycler: RecyclerView
    private lateinit var resultRecycler: RecyclerView
    private lateinit var searchBar: LinearLayout
    private lateinit var resultAdapter: ModernVideoCardAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyTheme()
        setContentView(createLayout())
        observeState()

        intent.getStringExtra("keyword")?.let {
            keywordInput.setText(it)
            viewModel.search(it)
        }
    }

    private fun applyTheme() {
        window.statusBarColor = ThemeManager.BACKGROUND
        window.navigationBarColor = ThemeManager.BACKGROUND
    }

    private fun createLayout(): View {
        val density = resources.displayMetrics.density
        val pad = (BiliDimens.SPACING_MD * density).toInt()

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(ThemeManager.BACKGROUND)

            searchBar = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundColor(ThemeManager.SURFACE)
                setPadding(pad, pad, pad, pad)
                elevation = BiliDimens.ELEVATION_CARD * density

                val backBtn = TextView(context).apply {
                    text = "←"
                    textSize = BiliDimens.ICON_LG
                    setTextColor(ThemeManager.PRIMARY)
                    val bp = (BiliDimens.SPACING_SM * density).toInt()
                    setPadding(0, 0, bp, 0)
                    setOnClickListener { finish() }
                }

                keywordInput = EditText(context).apply {
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    hint = "搜索视频、用户、番剧..."
                    setHintTextColor(ThemeManager.TEXT_TERTIARY)
                    textSize = BiliDimens.BODY_LARGE
                    setTextColor(ThemeManager.TEXT_PRIMARY)
                    background = null
                    isSingleLine = true
                    imeOptions = EditorInfo.IME_ACTION_SEARCH
                    setOnEditorActionListener { _, actionId, event ->
                        if (actionId == EditorInfo.IME_ACTION_SEARCH
                            || event?.keyCode == KeyEvent.KEYCODE_ENTER
                        ) {
                            viewModel.search(text.toString())
                            true
                        } else false
                    }
                    addTextChangedListener(object : android.text.TextWatcher {
                        override fun afterTextChanged(s: android.text.Editable?) {
                            viewModel.onKeywordChanged(s?.toString() ?: "")
                        }
                        override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
                        override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
                    })
                }

                val searchBtn = TextView(context).apply {
                    text = "搜索"
                    textSize = BiliDimens.BODY_MEDIUM
                    setTextColor(ThemeManager.PRIMARY)
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    val sp = (BiliDimens.SPACING_SM * density).toInt()
                    setPadding(sp, 0, 0, 0)
                    setOnClickListener { viewModel.search(keywordInput.text.toString()) }
                }

                searchBar.addView(backBtn)
                searchBar.addView(keywordInput)
                searchBar.addView(searchBtn)
            }

            val tabLayout = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setBackgroundColor(ThemeManager.SURFACE)
                setPadding(pad, 0, pad, 0)
                listOf("视频", "番剧", "用户", "直播").forEachIndexed { idx, name ->
                    addView(TextView(context).apply {
                        text = name
                        textSize = BiliDimens.BODY_MEDIUM
                        setTextColor(if (idx == 0) ThemeManager.PRIMARY else ThemeManager.TEXT_SECONDARY)
                        val tp = (BiliDimens.SPACING_MD * density).toInt()
                        setPadding(tp, tp, tp, tp)
                        setOnClickListener { viewModel.selectTab(idx) }
                    })
                }
            }

            historyRecycler = RecyclerView(context).apply {
                layoutManager = LinearLayoutManager(context)
                visibility = View.VISIBLE
            }

            suggestionRecycler = RecyclerView(context).apply {
                layoutManager = LinearLayoutManager(context)
                visibility = View.GONE
            }

            resultRecycler = RecyclerView(context).apply {
                layoutManager = LinearLayoutManager(context)
                visibility = View.GONE
            }

            resultAdapter = ModernVideoCardAdapter(this@ModernSearchActivity) { v ->
                BiliTerminal.jumpToVideo(this@ModernSearchActivity, v.aid)
            }
            resultRecycler.adapter = resultAdapter
            historyRecycler.adapter = SearchHistoryAdapter(this@ModernSearchActivity, viewModel)
            suggestionRecycler.adapter = SearchSuggestionAdapter(this@ModernSearchActivity, viewModel)

            addView(searchBar)
            addView(tabLayout)
            addView(historyRecycler, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(suggestionRecycler, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(resultRecycler, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            viewModel.state.collectLatest { s ->
                historyRecycler.visibility = if (s.showHistory) View.VISIBLE else View.GONE
                suggestionRecycler.visibility = if (s.showSuggestions) View.VISIBLE else View.GONE
                resultRecycler.visibility = if (s.isSearching || s.searchResult.items.isNotEmpty()) View.VISIBLE else View.GONE
                resultAdapter.submitList(s.searchResult.items)
            }
        }
    }
}

class SearchHistoryAdapter(
    private val context: android.content.Context,
    private val viewModel: ModernSearchViewModel
) : RecyclerView.Adapter<SearchHistoryAdapter.H>() {

    class H(v: View) : RecyclerView.ViewHolder(v) {
        val text: TextView = v.findViewWithTag("hist")
    }

    override fun onCreateViewHolder(p: ViewGroup, vt: Int): H {
        return H(TextView(context).apply {
            tag = "hist"
            textSize = BiliDimens.BODY_MEDIUM
            setTextColor(ThemeManager.TEXT_PRIMARY)
            val pad = (8 * context.resources.displayMetrics.density).toInt()
            setPadding(pad * 2, pad, pad * 2, pad)
            setBackgroundColor(ThemeManager.CARD)
        })
    }

    override fun onBindViewHolder(h: H, pos: Int) {
        h.text.text = viewModel.state.value.searchHistory.getOrNull(pos) ?: ""
        h.itemView.setOnClickListener { viewModel.search(h.text.text.toString()) }
        h.itemView.setOnLongClickListener {
            viewModel.deleteHistoryItem(pos)
            true
        }
    }

    override fun getItemCount() = viewModel.state.value.searchHistory.size
}

class SearchSuggestionAdapter(
    private val context: android.content.Context,
    private val viewModel: ModernSearchViewModel
) : RecyclerView.Adapter<SearchSuggestionAdapter.H>() {

    class H(v: View) : RecyclerView.ViewHolder(v) {
        val text: TextView = v.findViewWithTag("sug")
    }

    override fun onCreateViewHolder(p: ViewGroup, vt: Int): H {
        return H(TextView(context).apply {
            tag = "sug"
            textSize = BiliDimens.BODY_MEDIUM
            setTextColor(ThemeManager.TEXT_PRIMARY)
            val pad = (8 * context.resources.displayMetrics.density).toInt()
            setPadding(pad * 2, pad, pad * 2, pad)
            setBackgroundColor(ThemeManager.CARD)
        })
    }

    override fun onBindViewHolder(h: H, pos: Int) {
        h.text.text = viewModel.state.value.suggestions.getOrNull(pos) ?: ""
        h.itemView.setOnClickListener { viewModel.selectSuggestion(h.text.text.toString()) }
    }

    override fun getItemCount() = viewModel.state.value.suggestions.size
}