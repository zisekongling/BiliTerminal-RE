package com.RobinNotBad.BiliClient.activity

import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager.widget.ViewPager
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.base.BaseActivity
import com.RobinNotBad.BiliClient.api.EmoteApi
import com.RobinNotBad.BiliClient.model.Emote
import com.RobinNotBad.BiliClient.model.EmotePackage
import com.RobinNotBad.BiliClient.ui.widget.recycler.CustomGridManager
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.GlideUtil
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.bumptech.glide.Glide
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayout.OnTabSelectedListener
import androidx.appcompat.widget.TooltipCompat

class EmoteActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_emote)

        val loading = findViewById<ImageView>(R.id.loading)
        val tabLayout = findViewById<TabLayout>(R.id.tl_tab)
        val viewPager = findViewById<ViewPager>(R.id.viewPager)
        tabLayout.setBackgroundColor(com.RobinNotBad.BiliClient.ui.theme.ThemeManager.getPlayerBg(this))
        val onScrollListener = object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
            }
        }

        CenterThreadPool.run {
            try {
                var from = intent.getStringExtra("from")
                if (from == null) from = EmoteApi.BUSINESS_REPLY

                val packages = EmoteApi.getEmotes(from)
                runOnUiThread {
                    loading.visibility = View.GONE
                    viewPager.adapter = PagerAdapter(supportFragmentManager, packages) { origin ->
                        origin.setOnListScroll(onScrollListener)
                        origin
                    }
                    tabLayout.setupWithViewPager(viewPager)
                    tabLayout.isInlineLabel = true
                    tabLayout.addOnTabSelectedListener(object : OnTabSelectedListener {
                        override fun onTabSelected(tab: TabLayout.Tab) {
                            tab.tabLabelVisibility = TabLayout.TAB_LABEL_VISIBILITY_LABELED
                        }

                        override fun onTabUnselected(tab: TabLayout.Tab) {
                            tab.tabLabelVisibility = TabLayout.TAB_LABEL_VISIBILITY_UNLABELED
                        }

                        override fun onTabReselected(tab: TabLayout.Tab) {
                            tab.tabLabelVisibility = TabLayout.TAB_LABEL_VISIBILITY_LABELED
                        }
                    })
                    tabLayout.tabIconTint = null
                    val count = tabLayout.tabCount

                    CenterThreadPool.run {
                        for (i in 0 until count) {
                            val finalI = i

                            runOnUiThread {
                                tabLayout.getTabAt(finalI)!!.text = packages[finalI].text
                                if (finalI != 0)
                                    tabLayout.getTabAt(finalI)!!.tabLabelVisibility = TabLayout.TAB_LABEL_VISIBILITY_UNLABELED
                            }

                            try {
                                val drawable: Drawable = Glide.with(this@EmoteActivity).asDrawable()
                                    .transition(GlideUtil.getTransitionOptions())
                                    .load(packages[finalI].url)
                                    .submit().get()
                                runOnUiThread { tabLayout.getTabAt(finalI)!!.setIcon(drawable) }
                            } catch (e: java.util.concurrent.ExecutionException) {
                                MsgUtil.err("加载表情列表图标时出现错误：", e)
                                e.printStackTrace()
                            } catch (e: InterruptedException) {
                                e.printStackTrace()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { MsgUtil.err(e) }
            }
        }
    }

    class PagerAdapter(
        fm: FragmentManager,
        private val emotes: List<EmotePackage>,
        private val handler: FragmentHandler
    ) : FragmentPagerAdapter(fm) {

        fun interface FragmentHandler {
            fun handleCreateFragment(origin: EmoteFragment): Fragment
        }

        override fun getItem(position: Int): Fragment {
            return handler.handleCreateFragment(EmoteFragment.newInstance(emotes[position]))
        }

        override fun getCount(): Int {
            return emotes.size
        }
    }

    class EmoteFragment : Fragment() {
        private var emotePackage: EmotePackage? = null
        private var recyclerView: RecyclerView? = null
        private var hasListener: Boolean = false
        private var onListScroll: RecyclerView.OnScrollListener? = null

        companion object {
            @JvmStatic
            fun newInstance(emotePackage: EmotePackage): EmoteFragment {
                val emoteFragment = EmoteFragment()
                val bundle = Bundle()
                bundle.putParcelable("emotePackage", emotePackage)
                emoteFragment.arguments = bundle
                return emoteFragment
            }
        }

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            val bundle = arguments
            if (bundle != null) {
                this.emotePackage = bundle.getParcelable("emotePackage")
            }
        }

        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
            return inflater.inflate(R.layout.fragment_simple_list, container, false)
        }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)

            this.recyclerView = view.findViewById(R.id.recyclerView)
            val layoutManager = CustomGridManager(requireContext(), 4, RecyclerView.VERTICAL, false)
            layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int {
                    return if (emotePackage!!.type == 4) 2 else emotePackage!!.emotes[position].size
                }
            }
            recyclerView!!.layoutManager = layoutManager
            if (onListScroll != null) {
                hasListener = true
                recyclerView!!.addOnScrollListener(onListScroll!!)
            }

            val adapter = EmoteAdapter(emotePackage!!, requireContext())
            adapter.setOnClickEmote { emote ->
                requireActivity().setResult(RESULT_OK, Intent().putExtra("text", emote.name))
                requireActivity().finish()
            }
            recyclerView!!.addItemDecoration(
                GridSpacingItemDecoration(
                    4,
                    resources.getDimensionPixelSize(R.dimen.grid_spacing),
                    true
                )
            )
            recyclerView!!.adapter = adapter
        }

        fun setOnListScroll(onScrollListener: RecyclerView.OnScrollListener?) {
            if (this.onListScroll == null) this.onListScroll = onScrollListener
        }

        override fun onDestroyView() {
            super.onDestroyView()
            if (this.onListScroll != null && hasListener)
                recyclerView!!.removeOnScrollListener(onListScroll!!)
        }
    }

    class GridSpacingItemDecoration(
        private val spanCount: Int,
        private val spacing: Int,
        private val includeEdge: Boolean
    ) : RecyclerView.ItemDecoration() {
        override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
            val position = parent.getChildAdapterPosition(view)
            val column = position % spanCount

            if (includeEdge) {
                outRect.left = spacing - column * spacing / spanCount
                outRect.right = (column + 1) * spacing / spanCount

                if (position < spanCount) {
                    outRect.top = spacing
                }
                outRect.bottom = spacing
            } else {
                outRect.left = column * spacing / spanCount
                outRect.right = spacing - (column + 1) * spacing / spanCount
                if (position >= spanCount) {
                    outRect.top = spacing
                }
            }
        }
    }

    class EmoteAdapter(
        private val emotePackage: EmotePackage,
        private val context: Context
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        private var listener: OnClickEmoteListener? = null

        fun interface OnClickEmoteListener {
            fun onClickEmote(emote: Emote)
        }

        fun setOnClickEmote(listener: OnClickEmoteListener?) {
            this.listener = listener
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return if (viewType == 1) Holder(ImageView(context)) else TextHolder(TextView(context))
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val emote = emotePackage.emotes[position]
            if (holder is Holder) {
                Glide.with(context).asDrawable()
                    .transition(GlideUtil.getTransitionOptions())
                    .load(GlideUtil.url(emote.url))
                    .into(holder.itemView)
                holder.itemView.setOnClickListener {
                    listener?.onClickEmote(emote)
                }
            } else {
                val textHolder = holder as TextHolder
                textHolder.itemView.isSingleLine = true
                textHolder.itemView.ellipsize = TextUtils.TruncateAt.END
                textHolder.itemView.text = emote.name
                textHolder.itemView.setOnClickListener {
                    listener?.onClickEmote(emote)
                }
            }
            TooltipCompat.setTooltipText(
                holder.itemView,
                emote.alias ?: emote.name
            )
        }

        override fun getItemCount(): Int {
            return emotePackage.emotes.size
        }

        override fun getItemViewType(position: Int): Int {
            return if (emotePackage.type == 4) 0 else 1
        }

        class Holder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val itemView: ImageView = itemView as ImageView
        }

        class TextHolder(itemView: TextView) : RecyclerView.ViewHolder(itemView) {
            val itemView: TextView = itemView
        }
    }
}