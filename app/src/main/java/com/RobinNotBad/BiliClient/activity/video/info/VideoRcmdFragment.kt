package com.RobinNotBad.BiliClient.activity.video.info

import android.os.Bundle
import android.util.Log
import android.view.View
import com.RobinNotBad.BiliClient.activity.base.RefreshListFragment
import com.RobinNotBad.BiliClient.adapter.video.VideoCardAdapter
import com.RobinNotBad.BiliClient.api.RecommendApi
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil

class VideoRcmdFragment : RefreshListFragment() {

    private var aid: Long = 0

    companion object {
        @JvmStatic
        fun newInstance(aid: Long): VideoRcmdFragment {
            val fragment = VideoRcmdFragment()
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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        Log.e("debug-av号", aid.toString())

        if (SharedPreferencesUtil.getBoolean("ui_mobile_mode", false)) {
            recyclerView.isNestedScrollingEnabled = false
        }

        CenterThreadPool.supplyAsyncWithLiveData { RecommendApi.getRelated(aid) }
            .observe(viewLifecycleOwner) { result ->
                result.onSuccess { videoList ->
                    val adapter = VideoCardAdapter(requireContext(), videoList)
                    setAdapter(adapter)
                    setRefreshing(false)
                }.onFailure { loadFail(it) }
            }
    }
}