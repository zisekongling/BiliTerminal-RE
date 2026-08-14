package com.RobinNotBad.BiliClient.activity.video.info

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.adapter.video.VideoCardAdapter
import com.RobinNotBad.BiliClient.api.RecommendApi
import com.RobinNotBad.BiliClient.listener.OnLoadMoreListener
import com.RobinNotBad.BiliClient.ui.widget.recycler.CustomLinearManager
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.view.ImageAutoLoadScrollListener

class VideoRcmdMobileFragment : Fragment() {

    private var aid: Long = 0
    private var listener: OnLoadMoreListener? = null
    private var page: Int = 1
    private var bottom: Boolean = false
    private var lastLoadTimestamp: Long = 0
    private var isLoading: Boolean = false

    companion object {
        @JvmStatic
        fun newInstance(aid: Long): VideoRcmdMobileFragment {
            val fragment = VideoRcmdMobileFragment()
            val args = Bundle()
            args.putLong("aid", aid)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (arguments != null) {
            aid = requireArguments().getLong("aid")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_video_rcmd_mobile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        Log.e("debug-av号", aid.toString())

        val recyclerView = view.findViewById<RecyclerView>(R.id.recyclerView)
        recyclerView.layoutManager = CustomLinearManager(requireContext())
        recyclerView.setHasFixedSize(true)
        recyclerView.setItemViewCacheSize(10)
        recyclerView.recycledViewPool.setMaxRecycledViews(0, 20)
        recyclerView.isNestedScrollingEnabled = false

        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                if (listener != null && !recyclerView.canScrollVertically(1) && !isLoading && newState == RecyclerView.SCROLL_STATE_DRAGGING && !bottom) {
                    goOnLoad()
                }
            }

            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                if (listener != null) {
                    val manager = recyclerView.layoutManager as LinearLayoutManager
                    val lastItemPosition = manager.findLastVisibleItemPosition()
                    val itemCount = manager.itemCount
                    if (lastItemPosition >= (itemCount - 3) && dy > 0 && !isLoading && !bottom) {
                        goOnLoad()
                    }
                }
            }
        })
        ImageAutoLoadScrollListener.install(recyclerView)

        CenterThreadPool.supplyAsyncWithLiveData { RecommendApi.getRelated(aid) }
            .observe(viewLifecycleOwner) { result ->
                result.onSuccess { videoList ->
                    val adapter = VideoCardAdapter(requireContext(), videoList)
                    recyclerView.adapter = adapter
                    isLoading = false
                    expandRecyclerView(recyclerView, adapter)
                }.onFailure {
                    loadFail()
                    MsgUtil.err(it)
                }
            }
    }

    fun setOnLoadMoreListener(loadMore: OnLoadMoreListener) {
        listener = loadMore
    }

    private fun goOnLoad() {
        val timeCurrent = System.currentTimeMillis()
        if (timeCurrent - lastLoadTimestamp > 100) {
            isLoading = true
            page++
            listener?.onLoad(page)
            lastLoadTimestamp = timeCurrent
        }
    }

    fun loadFail() {
        page--
        isLoading = false
        MsgUtil.showMsgLong("加载失败")
    }

    fun isRefreshing(): Boolean = isLoading

    /**
     * 解决RecyclerView在ScrollView中只显示一个item的问题
     * 通过测量第一个item高度，计算总高度并设置到RecyclerView
     */
    private fun expandRecyclerView(recyclerView: RecyclerView, adapter: RecyclerView.Adapter<*>) {
        recyclerView.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                recyclerView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                if (adapter.itemCount > 0) {
                    val firstChild = recyclerView.getChildAt(0)
                    if (firstChild != null) {
                        val itemHeight = firstChild.measuredHeight
                        val totalHeight = itemHeight * adapter.itemCount
                        recyclerView.layoutParams = recyclerView.layoutParams.apply {
                            height = totalHeight
                        }
                    }
                }
            }
        })
    }
}