package com.RobinNotBad.BiliClient.activity.article

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.Display
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.adapter.article.OpusContentAdapter
import com.RobinNotBad.BiliClient.model.Opus
import com.RobinNotBad.BiliClient.ui.widget.recycler.CustomLinearManager
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import com.RobinNotBad.BiliClient.util.TerminalContext

class OpusInfoFragment : Fragment() {
    private var oid: Long = 0
    private lateinit var recyclerView: RecyclerView
    var opus: Opus? = null

    var onFinishLoad: Runnable? = null

    companion object {
        fun newInstance(oid: Long): OpusInfoFragment {
            val fragment = OpusInfoFragment()
            val args = Bundle()
            args.putLong("oid", oid)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (arguments != null) {
            oid = arguments!!.getLong("oid")
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_simple_list, container, false)
    }

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.recyclerView)

        if (SharedPreferencesUtil.getBoolean("ui_landscape", false) && !SharedPreferencesUtil.getBoolean("ui_mobile_mode", false)) {
            val windowManager = view.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val display = windowManager.defaultDisplay
            val metrics = DisplayMetrics()
            if (Build.VERSION.SDK_INT >= 17) display.getRealMetrics(metrics)
            else display.getMetrics(metrics)
            val paddings = metrics.widthPixels / 6
            recyclerView.setPadding(paddings, 0, paddings, 0)
        }

        TerminalContext.getInstance().getOpusById(oid)
            .observe(viewLifecycleOwner) { result ->
                result.onSuccess { opus ->
                    if (!isAdded) return@onSuccess
                    val adapter = OpusContentAdapter(requireActivity(), opus)
                    requireActivity().runOnUiThread {
                        recyclerView.layoutManager = CustomLinearManager(requireContext())
                        recyclerView.adapter = adapter

                        recyclerView.isFocusable = true
                        recyclerView.isFocusableInTouchMode = true
                        recyclerView.requestFocus()
                    }
                }.onFailure { MsgUtil.err(it) }
            }

    }
}