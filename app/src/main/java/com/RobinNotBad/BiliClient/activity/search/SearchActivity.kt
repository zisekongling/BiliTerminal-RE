package com.RobinNotBad.BiliClient.activity.search

import com.RobinNotBad.BiliClient.util.UiText

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView

import android.text.TextUtils

import androidx.annotation.NonNull
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2

import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.base.InstanceActivity
import com.RobinNotBad.BiliClient.adapter.SearchHistoryAdapter
import com.RobinNotBad.BiliClient.adapter.SearchSuggestionsAdapter
import com.RobinNotBad.BiliClient.api.SearchApi
import com.RobinNotBad.BiliClient.ui.widget.recycler.CustomLinearManager
import com.RobinNotBad.BiliClient.util.TerminalDialog
import com.RobinNotBad.BiliClient.util.JsonUtil
import com.RobinNotBad.BiliClient.util.LinkUrlUtil
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import com.RobinNotBad.BiliClient.util.ToolsUtil

import org.json.JSONArray
import org.json.JSONException

import java.util.Objects

class SearchActivity : InstanceActivity() {
    private var lastKeyword = "≠~`"
    private lateinit var historyRecyclerview: RecyclerView
    private lateinit var suggestionsRecyclerview: RecyclerView
    lateinit var searchHistoryAdapter: SearchHistoryAdapter
    lateinit var searchSuggestionsAdapter: SearchSuggestionsAdapter
    lateinit var viewPager: ViewPager2
    lateinit var keywordInput: EditText
    private lateinit var searchBar: ConstraintLayout

    /** 搜索框是否展开。滚动收起时的动画见 [onScrolled]（26.10.26 起与「没加排序之前」逐字一致）。 */
    private var searchBarVisible = true

    // 搜索结果排序入口：搜索框内部左侧的图标按钮（视频 / 专栏）。控件在本页而不是 fragment 里：
    // fragment 的 ViewPager 只对齐到顶栏下方、搜索框压在 fragment 上面，控件放进去会被搜索框盖住
    // （26.10.24 模拟器实测）。
    private lateinit var sortButton: ImageView

    /** 输入框没给排序图标让位时的 paddingStart（XML 里是 8dp），收起图标时还原成它。 */
    private var keywordPaddingStart = 0

    /** 至少搜过一次才显示排序入口：还没搜索时页面上是历史记录，给「排序」没有意义。 */
    private var hasSearched = false

    private var refreshing = false
    private var animate_last: Long = 0
    lateinit var handler: Handler
    lateinit var searchHistory: ArrayList<String>
    lateinit var searchSuggestions: ArrayList<String>
    private var suggestionRunnable: Runnable? = null

    // 搜索建议的「代际令牌」：每次输入自增，异步返回的建议回调只认自己那一代。
    // 为什么需要：removeCallbacks 只能取消还没开始跑的延时任务，已经派发进 Thread 的请求取消不掉，
    // 慢响应返回时会覆盖新响应（建议乱序）；用令牌可以在回调落地时把过期结果整包丢掉。
    private var suggestionGeneration = 0
    private var suggestionsEnabled: Boolean = false
    private var defaultSearchContent: String? = null
    private var defaultSearchContentEnabled: Boolean = false

    // 搜索类别动态列表：根据用户设置动态管理启用的搜索类别及排序
    private lateinit var categoryList: ArrayList<String>

    // 默认搜索类别顺序（番剧紧跟视频，与上游一致）
    private val defaultCategoryOrder = arrayOf("video", "bangumi", "article", "user", "live")

    // 这组是「关键词匹配表」，不是展示文案：它只参与 keyword.contains 判定，
    // 一旦被 UiText 包成资源文案，判定键就跟着改名一起漂，彩蛋会静默失效。
    // 依据 docs/copy-inventory/STRINGS-MIGRATION.md §4「用作 key 的字符串不要迁移」。
    var specialList = arrayOf("心理疾病", "自杀", "自尽", "自残", "抑郁", "双相", "安眠药")

    @SuppressLint("MissingInflatedId", "NotifyDataSetChanged", "InflateParams")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        asyncInflate(R.layout.activity_search) { _, _ ->
            // 教程（含这页的左右翻页提示）改由 BaseActivity 按 Tutorials 注册表集中触发

            handler = Handler()

            suggestionsEnabled = SharedPreferencesUtil.getBoolean("search_suggestions_enable", true)
            defaultSearchContentEnabled = SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.SEARCH_DEFAULT_CONTENT_ENABLE, false)

            if (defaultSearchContentEnabled) {
                Thread {
                    try {
                        defaultSearchContent = SearchApi.getDefaultSearchContent()
                        if (defaultSearchContent != null && defaultSearchContent!!.isNotEmpty()) {
                            runOnUiThread { keywordInput.hint = defaultSearchContent }
                        }
                    } catch (e: Exception) {
                        Log.e("SearchActivity", "获取默认搜索内容失败", e)
                    }
                }.start()
            }

            viewPager = findViewById(R.id.viewPager)

            // 构建启用的搜索类别列表
            buildCategoryList()

            val searchBtn = findViewById<View>(R.id.search)
            keywordInput = findViewById(R.id.keywordInput)
            searchBar = findViewById(R.id.searchbar)
            historyRecyclerview = findViewById(R.id.history_recyclerview)
            suggestionsRecyclerview = findViewById(R.id.suggestions_recyclerview)

            viewPager.offscreenPageLimit = maxOf(1, categoryList.size - 1)
            viewPager.isUserInputEnabled = true

            keywordInput.onFocusChangeListener = View.OnFocusChangeListener { _, b ->
                if (b) {
                    val keyword = keywordInput.text.toString()
                    // 只有「输入框有内容 且 确实拿到了建议」才显示建议列表，
                    // 否则会出现建议列表空白、历史记录又看不见的情况
                    if (keyword.isEmpty() || !suggestionsEnabled || searchSuggestions.isEmpty()) {
                        showHistoryDropdown()
                    } else {
                        showSuggestionsDropdown()
                    }
                } else {
                    hideDropdowns()
                }
            }
            showHistoryDropdown()
            // 动态适配器：根据用户设置决定显示的搜索类别和顺序
            val vpfAdapter: FragmentStateAdapter = object : FragmentStateAdapter(this) {
                override fun getItemCount(): Int {
                    return categoryList.size
                }

                @NonNull
                override fun createFragment(position: Int): Fragment {
                    return createFragmentForCategory(categoryList[position])
                }
            }
            viewPager.adapter = vpfAdapter
            // 标题随类别页变化：搜索-视频 / 搜索-番剧 / 搜索-专栏 / 搜索-用户 / 搜索-直播
            updatePageName(viewPager.currentItem)

            viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                private var lastPosition = -1

                override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
                    if (position != 0 && position != lastPosition) {
                        // 这里**不要**再调 onScrolled(...) 去模拟一次滚动：那个方向是「收起搜索框」，
                        // 而空关键词时分类页没有任何内容可滚，收起后再也没有事件把搜索框拉回来
                        // （反馈 #42：输入框隐藏，只能切到别的页面再切回搜索）。搜索框的显隐只由
                        // 列表真实滚动驱动，见 onScrolled()。
                        lastPosition = position
                    }
                    super.onPageScrolled(position, positionOffset, positionOffsetPixels)
                }

                override fun onPageSelected(position: Int) {
                    super.onPageSelected(position)
                    updatePageName(position)
                    val fragmentCurr = supportFragmentManager.findFragmentByTag("f$position")
                    if (fragmentCurr != null) {
                        (fragmentCurr as SearchFragment).refresh()
                    }
                    refreshSortBar()
                    // FragmentStateAdapter 是懒创建的：首次切到某个分类时 findFragmentByTag
                    // 可能还是 null，那一刻 refreshSortBar 只能判成「本分类不支持排序」把图标藏掉。
                    // 下一帧补算一次即可（幂等，按当时的 currentItem 算，不会自旋）。
                    viewPager.post { refreshSortBar() }
                }
            })

            setupSortBar()

            searchBtn.setOnClickListener { searchKeyword(keywordInput.text.toString()) }
            searchBtn.setOnLongClickListener { jumpToTargetId(it) }
            keywordInput.setOnEditorActionListener { textView, actionId, event ->
                if (actionId == EditorInfo.IME_ACTION_SEND || actionId == EditorInfo.IME_ACTION_DONE || event != null &&
                    KeyEvent.KEYCODE_ENTER == event.keyCode && KeyEvent.ACTION_DOWN == event.action) {
                    searchKeyword(keywordInput.text.toString())
                }
                false
            }
            // 首次点击输入框时，系统只会把焦点交还给输入框，不会自动拉起输入法
            // （全工程之前没有一处 showSoftInput），用户会觉得「点了没反应」。
            // 用 post 把拉起动作延后到本轮点击事件处理完之后：否则刚 grant 的焦点会被同一轮事件撤回，键盘仍然不弹。
            keywordInput.setOnClickListener { v ->
                v.post {
                    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.showSoftInput(v, InputMethodManager.SHOW_IMPLICIT)
                }
            }

            try {
                searchHistory = JsonUtil.jsonToArrayList(
                    JSONArray(SharedPreferencesUtil.getString(SharedPreferencesUtil.search_history, "[]")),
                    false)
            } catch (e: JSONException) {
                runOnUiThread { MsgUtil.err(e) }
                searchHistory = ArrayList()
            }
            searchHistoryAdapter = SearchHistoryAdapter(this, searchHistory)
            // 回调里的 position 是列表变化前绑定的，点下去那一刻才算数：越界就当没点
            // （26.11.07 线上崩溃 #230：IndexOutOfBoundsException: Index: 1, Size: 1）
            searchHistoryAdapter.setOnClickListener { position ->
                if (position < 0 || position >= searchHistory.size) return@setOnClickListener
                keywordInput.setText(searchHistory[position])
            }
            searchHistoryAdapter.setOnLongClickListener { position ->
                if (position < 0 || position >= searchHistory.size) return@setOnLongClickListener
                MsgUtil.showMsg(UiText.of("删除成功"))
                searchHistory.removeAt(position)
                searchHistoryAdapter.notifyItemRemoved(position)
                searchHistoryAdapter.notifyItemRangeChanged(position, searchHistory.size - position)
                SharedPreferencesUtil.putString(SharedPreferencesUtil.search_history,
                    JSONArray(searchHistory).toString())
            }
            historyRecyclerview.layoutManager = CustomLinearManager(this)
            historyRecyclerview.adapter = searchHistoryAdapter
            if (searchHistory.size > 4) {
                historyRecyclerview.isFocusable = true
                historyRecyclerview.isFocusableInTouchMode = true
                historyRecyclerview.requestFocus()
            }

            searchSuggestions = ArrayList()
            searchSuggestionsAdapter = SearchSuggestionsAdapter(this, searchSuggestions)
            searchSuggestionsAdapter.setOnClickListener { position ->
                if (position < 0 || position >= searchSuggestions.size) return@setOnClickListener
                val suggestion = searchSuggestions[position]
                // 中文输入法还在组词（composing）状态时直接 setText，会把组合区的半成品和整词叠加，出现串字；
                // 先清掉组合态再整词替换
                keywordInput.clearComposingText()
                keywordInput.setText(suggestion)
                keywordInput.setSelection(suggestion.length)
                searchKeyword(suggestion)
            }
            suggestionsRecyclerview.layoutManager = CustomLinearManager(this)
            suggestionsRecyclerview.adapter = searchSuggestionsAdapter

            if (suggestionsEnabled) {
                keywordInput.addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
                    override fun afterTextChanged(s: Editable) {
                        // searchKeyword() 内部也会 setText/clearFocus，会再次回调到这里；
                        // 不拦住就会在搜索执行过程中又发起一次建议请求
                        if (refreshing) return

                        val keyword = s.toString()

                        if (suggestionRunnable != null) {
                            handler.removeCallbacks(suggestionRunnable!!)
                        }

                        if (keyword.isEmpty()) {
                            runOnUiThread {
                                if (keywordInput.hasFocus()) {
                                    showHistoryDropdown()
                                }
                            }
                        } else {
                            // 每次输入自增代际令牌，回调只认自己那一代
                            suggestionGeneration++
                            val gen = suggestionGeneration
                            suggestionRunnable = Runnable {
                                Thread {
                                    try {
                                        val suggestions = SearchApi.getSearchSuggestions(keyword)
                                        runOnUiThread {
                                            // 过期请求直接丢弃（乱序的根因），页面已销毁也不再碰视图
                                            if (gen != suggestionGeneration || refreshing || isFinishing || isDestroyed) return@runOnUiThread
                                            searchSuggestions.clear()
                                            searchSuggestions.addAll(suggestions)
                                            searchSuggestionsAdapter.notifyDataSetChanged()

                                            if (suggestions.isNotEmpty()) {
                                                showSuggestionsDropdown()
                                            } else {
                                                showHistoryDropdown()
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Log.e("SearchActivity", "获取搜索建议失败", e)
                                    }
                                }.start()
                            }
                            handler.postDelayed(suggestionRunnable!!, 300)
                        }
                    }
                })
            }

            val intentKeyword = intent.getStringExtra("keyword")
            if (intentKeyword != null) {
                findViewById<View>(R.id.top).setOnClickListener { finish() }
                keywordInput.setText(intentKeyword)
                MsgUtil.showMsg(UiText.of("可点击标题栏返回详情页"))
                // 从话题等「点一下就期待有结果」的入口进来时直接搜（26.10.19 话题点击落地），
                // 别让用户带着关键词停在空列表上再点一次放大镜。别处只传 keyword 的调用方行为不变。
                if (intent.getBooleanExtra("auto_search", false)) {
                    // 不能在这里直接调：onCreate 阶段 ViewPager 的 fragment 还没创建，
                    // searchKeyword 里的 findFragmentByTag 全是 null，关键词送不到任何一页，
                    // 用户看到的就是「有关键词但没结果」。等页面第一帧布局完成后再搜。
                    viewPager.post {
                        if (!isFinishing && !isDestroyed) searchKeyword(intentKeyword)
                    }
                }
            }
        }
    }

    fun jumpToTargetId(view: View): Boolean {
        val text = keywordInput.text.toString()
        LinkUrlUtil.handleId(this, text)
        return true
    }

    /**
     * 标题随搜索类别页变化（搜索-视频 / 搜索-番剧 / 搜索-专栏 / 搜索-用户 / 搜索-直播）。
     * 仅修改标题文字，不影响标题栏点击（菜单/返回详情）与长按搜索按钮等隐藏功能
     */
    private fun updatePageName(position: Int) {
        if (!::categoryList.isInitialized || position < 0 || position >= categoryList.size) return
        val sub = when (categoryList[position]) {
            "video" -> UiText.of("视频")
            "bangumi" -> UiText.of("番剧")
            "article" -> UiText.of("专栏")
            "user" -> UiText.of("用户")
            "live" -> UiText.of("直播")
            else -> categoryList[position]
        }
        setPageName("搜索-$sub")
    }

    /**
     * 根据用户偏好设置构建启用的搜索类别列表
     * 视频始终启用且在第一位；其他类别根据设置决定是否显示及排序
     */
    private fun buildCategoryList() {
        categoryList = ArrayList()

        // 读取已保存的排序配置
        val sortConf = SharedPreferencesUtil.getString(SharedPreferencesUtil.SEARCH_CATEGORY_SORT, "")

        // 旧逻辑要求保存的类别数必须与默认表完全相等，一旦默认表加了新类别（本次加「番剧」），
        // 旧配置就会整份作废，用户在设置页排好的顺序也随之失效（而设置页目前只能写 4 个类别）。
        // 改成：保存的每一项都是已知类别就采用它，默认表里新增、旧配置里还没有的类别补在后面。
        val savedKeys: List<String> = if (TextUtils.isEmpty(sortConf)) emptyList() else sortConf.split(";")
        val savedKeysUsable = savedKeys.isNotEmpty() && savedKeys.all { defaultCategoryOrder.contains(it) }

        if (savedKeysUsable) {
            // 使用已保存的排序顺序
            for (key in savedKeys) {
                if (isCategoryEnabled(key)) {
                    categoryList.add(key)
                }
            }
            // 补上保存配置里还没有的类别（版本升级新增、设置页尚未提供开关的 tab）
            for (key in defaultCategoryOrder) {
                if (!savedKeys.contains(key) && isCategoryEnabled(key)) {
                    categoryList.add(key)
                }
            }
        } else {
            // 使用默认排序顺序
            for (key in defaultCategoryOrder) {
                if (isCategoryEnabled(key)) {
                    categoryList.add(key)
                }
            }
        }

        // 确保视频搜索始终存在
        if (!categoryList.contains("video")) {
            categoryList.add(0, "video")
        }
    }

    /**
     * 判断指定搜索类别是否启用
     * 视频搜索始终启用
     */
    private fun isCategoryEnabled(categoryKey: String): Boolean {
        return when (categoryKey) {
            "video" -> true  // 视频始终启用
            "bangumi" -> SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.SEARCH_CATEGORY_BANGUMI_SHOW, true)
            "article" -> SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.SEARCH_CATEGORY_ARTICLE_SHOW, true)
            "user" -> SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.SEARCH_CATEGORY_USER_SHOW, true)
            "live" -> SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.SEARCH_CATEGORY_LIVE_SHOW, true)
            else -> false
        }
    }

    /**
     * 根据类别key创建对应的搜索Fragment
     */
    private fun createFragmentForCategory(categoryKey: String): Fragment {
        return when (categoryKey) {
            "video" -> SearchVideoFragment.newInstance()
            "bangumi" -> SearchBangumiFragment.newInstance()
            "article" -> SearchArticleFragment.newInstance()
            "user" -> SearchUserFragment.newInstance()
            "live" -> SearchLiveFragment.newInstance()
            else -> SearchVideoFragment.newInstance()
        }
    }

    override fun onResume() {
        super.onResume()
        // 从设置页返回后，重新加载分类列表以应用最新设置
        if (::categoryList.isInitialized && ::viewPager.isInitialized) {
            val oldSize = categoryList.size
            buildCategoryList()
            // 如果类别列表发生变化，通知适配器更新
            if (oldSize != categoryList.size) {
                viewPager.adapter?.notifyDataSetChanged()
                viewPager.offscreenPageLimit = maxOf(1, categoryList.size - 1)
            }
            // 类别顺序/数量可能变化，刷新标题子标题
            updatePageName(viewPager.currentItem)
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun searchKeyword(str: String) {
        var keyword = str
        if (keyword.contains("Robin") || keyword.contains("robin")) {
            if (keyword.contains("撅")) {
                MsgUtil.showText(UiText.of("特殊彩蛋"), getString(R.string.egg_special))
                return
            }
            if (keyword.contains("纳西妲")) {
                MsgUtil.showText(UiText.of("特殊彩蛋"), getString(R.string.egg_robin_nahida))
                return
            }
        }
        for (s in specialList) {
            if (keyword.contains(s)) {
                MsgUtil.showText(UiText.of("特殊彩蛋"), getString(R.string.egg_warmwords_warmworld))
                return
            }
        }
        // 「紫色空灵」（RE 版主开发者）的专属彩蛋：要求同时带角色名，避免单搜开发者名误触
        if (keyword.contains("紫色空灵") && keyword.contains("纳西妲")) {
            MsgUtil.showText(UiText.of("特殊彩蛋"), getString(R.string.egg_zisekongling_nahida))
            return
        }

        if (!refreshing) {
            // 空关键词要在收起浮层之前拦下（反馈 #52）：原先先 hideDropdowns() 再判空 return，
            // 搜索没执行、历史记录却被藏起来了，只能切走页面再回来才重新出现。
            if (keyword.isEmpty()) {
                if (defaultSearchContentEnabled && defaultSearchContent != null && defaultSearchContent!!.isNotEmpty()) {
                    keyword = defaultSearchContent!!
                } else {
                    runOnUiThread { MsgUtil.showMsg(UiText.of("还没输入内容喵~")) }
                    return
                }
            }

            val manager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            val curFocus: View?
            if (getCurrentFocus().also { curFocus = it } != null) {
                manager.hideSoftInputFromWindow(curFocus!!.windowToken, InputMethodManager.HIDE_NOT_ALWAYS)
            }

            // 搜索一旦真正执行，就要收起建议面板并作废还没返回的建议请求：
            // 否则那个请求返回后会把建议卡片重新盖在搜索结果上
            suggestionGeneration++
            if (suggestionRunnable != null) handler.removeCallbacks(suggestionRunnable!!)
            runOnUiThread { hideDropdowns() }

            hasSearched = true
            runOnUiThread { refreshSortBar() }

            if (Objects.equals(lastKeyword, keyword)) {
                runOnUiThread {
                    keywordInput.clearFocus()
                    hideDropdowns()
                }
            } else {
                refreshing = true
                lastKeyword = keyword

                runOnUiThread {
                    hideDropdowns()
                    keywordInput.clearFocus()
                }

                if (!searchHistory.contains(keyword)) {
                    try {
                        searchHistory.add(0, keyword)
                        SharedPreferencesUtil.putString(SharedPreferencesUtil.search_history,
                            JSONArray(searchHistory).toString())
                        runOnUiThread {
                            searchHistoryAdapter.notifyItemInserted(0)
                            searchHistoryAdapter.notifyItemRangeChanged(0, searchHistory.size)
                            historyRecyclerview.scrollToPosition(0)
                        }
                    } catch (e: Exception) {
                        runOnUiThread { MsgUtil.err(e) }
                    }
                } else {
                    try {
                        val pos = searchHistory.indexOf(keyword)
                        searchHistory.remove(keyword)
                        searchHistory.add(0, keyword)
                        SharedPreferencesUtil.putString(SharedPreferencesUtil.search_history,
                            JSONArray(searchHistory).toString())
                        runOnUiThread {
                            searchHistoryAdapter.notifyItemMoved(pos, 0)
                            searchHistoryAdapter.notifyItemRangeChanged(0, searchHistory.size)
                            historyRecyclerview.scrollToPosition(0)
                        }
                    } catch (e: Exception) {
                        runOnUiThread { MsgUtil.err(e) }
                    }
                }

                try {
                    for (i in 0 until categoryList.size) {
                        val fragmentById = supportFragmentManager.findFragmentByTag("f$i")
                        if (fragmentById != null)
                            (fragmentById as SearchFragment).update(keyword)
                    }
                    val fragmentCurr = supportFragmentManager
                        .findFragmentByTag("f" + viewPager.currentItem)
                    if (fragmentCurr != null) {
                        (fragmentCurr as SearchFragment).refresh()
                        requestFragmentFocus()
                    }
                } catch (e: Exception) {
                    report(e)
                }
                refreshing = false
            }
        }
    }

    /**
     * 绑定排序入口（搜索框最左边的图标）。可见性与点击行为按当前分类在 [refreshSortBar] 里填。
     */
    private fun setupSortBar() {
        sortButton = findViewById(R.id.searchSortButton)
        keywordPaddingStart = keywordInput.paddingStart
        refreshSortBar()
    }

    /**
     * 按当前分类刷新排序入口：支持排序的分类（视频 / 专栏）且已搜过才显示图标，其余隐藏。
     *
     * 图标长在搜索框**内部**，显隐不改变任何布局尺寸（`ViewPager` 一直锚在顶栏下方），
     * 所以这里不联动搜索框的收起动画——那是 [onScrolled] 自己的事。
     *
     * 被风控挡下（`SearchApi.RiskControlException`，wbi 与旧接口都没结果）时图标压暗，
     * 点它会提示「请稍候再试」**并真的重拉一次**（[SearchFragment.retry]）：
     * 风控态下下拉刷新是关的、同关键词再搜也会短路，不给一条真正的重试路径图标就永久锁死了。
     */
    private fun refreshSortBar() {
        if (isFinishing || isDestroyed) return
        if (!::sortButton.isInitialized) return
        val fragment = supportFragmentManager.findFragmentByTag("f" + viewPager.currentItem) as? SearchFragment
        val options = fragment?.sortOptions().orEmpty()
        if (fragment == null || !hasSearched || options.isEmpty()) {
            applySortIconVisible(false)
            return
        }

        // 风控状态可能在后台线程变化，装一次回调让宿主跟着重画
        fragment.onSortStateChanged = { refreshSortBar() }
        applySortIconVisible(true)

        if (fragment.isSortBlocked()) {
            sortButton.alpha = 0.4f
            sortButton.contentDescription = getString(R.string.text_search_sort_blocked)
            sortButton.setOnClickListener {
                MsgUtil.showMsg(getString(R.string.msg_search_sort_retry_later))
                // 不能只弹提示不干活：风控态下下拉刷新是关的、同关键词再搜也会短路，
                // 不给一条真正的重试路径的话按钮就永久锁死了（26.10.24 审查发现）
                fragment.retry()
            }
        } else {
            sortButton.alpha = 1f
            val current = options.firstOrNull { it.order == fragment.currentSortOrder() } ?: options[0]
            // 图标没有文字，用 contentDescription 把「排序：综合」这句读屏念出来，
            // 顺带让当前档位在无障碍/调试时可见
            sortButton.contentDescription =
                getString(R.string.text_search_sort_button, getString(current.labelRes))
            sortButton.setOnClickListener { showSortDialog(fragment, options, current.order) }
        }
    }

    /**
     * 排序图标显隐 + 给图标让出输入框左侧的内边距。
     *
     * 图标是压在输入框上的（ConstraintLayout 里同起点），不占 `EditText` 的宽度，所以显示时要把
     * 输入框的 `paddingStart` 从 8dp 撑到 [SORT_ICON_SLOT_DP]（3dp 边距 + 30dp 图标 + 5dp 间隙），
     * 否则文字会从图标底下穿过去；隐藏时还原成 XML 里的 8dp。
     */
    private fun applySortIconVisible(visible: Boolean) {
        sortButton.visibility = if (visible) View.VISIBLE else View.GONE
        val paddingStart = if (visible) ToolsUtil.dp2px(SORT_ICON_SLOT_DP) else keywordPaddingStart
        if (keywordInput.paddingStart != paddingStart) {
            keywordInput.setPaddingRelative(
                paddingStart,
                keywordInput.paddingTop,
                keywordInput.paddingEnd,
                keywordInput.paddingBottom
            )
        }
    }

    /** 历史 / 建议浮层统一从这里开关（浮层盖在列表上；排序图标在搜索框内部，两者不再抢地方）。 */
    private fun showHistoryDropdown() {
        historyRecyclerview.visibility = View.VISIBLE
        suggestionsRecyclerview.visibility = View.GONE
    }

    private fun showSuggestionsDropdown() {
        historyRecyclerview.visibility = View.GONE
        suggestionsRecyclerview.visibility = View.VISIBLE
    }

    private fun hideDropdowns() {
        historyRecyclerview.visibility = View.GONE
        suggestionsRecyclerview.visibility = View.GONE
    }

    /**
     * 排序选择弹窗：与番剧选集（`BangumiInfoFragment.getEposideChooseDialog`）同一款
     * `TerminalDialog.singleChoice`——带 `▸` 引导符的单选列表，选中项即当前排序。
     *
     * 这个弹窗**不会自动关闭**（选集场景要在回调里做越界校验），所以这里自己 `dismiss()`。
     */
    private fun showSortDialog(
        fragment: SearchFragment,
        options: List<SearchSortOption>,
        currentOrder: String
    ) {
        val labels = options.map { getString(it.labelRes) }
        val checked = options.indexOfFirst { it.order == currentOrder }.coerceAtLeast(0)
        // TerminalDialog 只负责拼装，**不会自己 show**（与番剧选集同一约定），这里必须调 .show()
        TerminalDialog.singleChoice(
            context = this,
            title = getString(R.string.text_search_sort_title),
            items = labels,
            checked = checked
        ) { sheet, which ->
            options.getOrNull(which)?.let { fragment.selectSort(it.order) }
            sheet.dialog.dismiss()
            refreshSortBar()
        }.show()
    }

    /**
     * 列表滚动时收起 / 展开搜索框。
     *
     * **这段是 26.10.26 按用户要求逐字回退到「没加搜索排序之前」的实现**（当时 `c8764ba^`）：
     * 只对搜索框做 `translationY` 平移，200ms 后置 `GONE`；`ViewPager` 一直锚在顶栏下方，
     * 不参与任何高度动画。加排序功能那版把它换成了「搜索框 + 排序条共用一条 `ValueAnimator`
     * 收放真实高度」（`applyTopChrome`），现在排序入口挪进搜索框内部，不需要那套联动了。
     */
    fun onScrolled(dy: Int) {
        val height = searchBar.height + ToolsUtil.dp2px(2f)

        if (System.currentTimeMillis() - animate_last > 200) {
            if (dy > 0 && searchBarVisible) {
                animate_last = System.currentTimeMillis()
                this.searchBarVisible = false
                @SuppressLint("ObjectAnimatorBinding")
                val animator = ObjectAnimator.ofFloat(searchBar, "translationY", 0f, (-height).toFloat())
                animator.start()
                handler.postDelayed({ searchBar.visibility = View.GONE }, 200)
            }
            if (dy < -1 && !searchBarVisible) {
                animate_last = System.currentTimeMillis()
                this.searchBarVisible = true
                searchBar.visibility = View.VISIBLE
                @SuppressLint("ObjectAnimatorBinding")
                val animator_show = ObjectAnimator.ofFloat(searchBar, "translationY", (-height).toFloat(), 0f)
                animator_show.start()
            }
        }

        requestFragmentFocus()
    }

    private fun requestFragmentFocus() {
        // 输入框已经持有焦点时就不要再抢：键盘弹出会改变布局并触发列表滚动回调，
        // 而滚动回调（onScrolled）每次都会走到这里，把刚拉起的键盘又顶掉
        if (keywordInput.hasFocus()) return
        val fragmentCurr = supportFragmentManager
            .findFragmentByTag("f" + viewPager.currentItem)
        if (fragmentCurr != null) {
            (fragmentCurr as SearchFragment).refresh()
            if (fragmentCurr.view != null) {
                val recyclerView = fragmentCurr.view!!.findViewById<View>(R.id.recyclerView)
                recyclerView.isFocusable = true
                recyclerView.isFocusableInTouchMode = true
                recyclerView.requestFocus()
            }
        }
    }

    private companion object {
        /**
         * 排序图标在搜索框里占掉的横向宽度：3dp 左边距 + 30dp 图标 + 5dp 与文字的间隙。
         * 图标显示时输入框的 `paddingStart` 要撑到这么宽，收起时还原成 XML 里的 8dp。
         */
        const val SORT_ICON_SLOT_DP = 38f
    }
}