package com.RobinNotBad.BiliClient.activity.audio

import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.api.AudioApi
import com.RobinNotBad.BiliClient.model.Lyric
import com.RobinNotBad.BiliClient.util.CenterThreadPool

class LyricFragment : Fragment() {

    private var lyric: Lyric? = null
    private var lyricAdapter: LyricAdapter? = null
    private var recyclerView: RecyclerView? = null
    private var noLyricTip: TextView? = null
    private val handler = Handler(Looper.getMainLooper())
    private var syncRunnable: Runnable? = null
    private var mediaPlayer: MediaPlayer? = null
    private var currentHighlightIndex = -1
    private var sid: Long = 0

    companion object {
        fun newInstance(sid: Long): LyricFragment {
            val fragment = LyricFragment()
            val args = Bundle()
            args.putLong("sid", sid)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sid = arguments?.getLong("sid", 0) ?: 0
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_lyric, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.lyric_recyclerview)
        noLyricTip = view.findViewById(R.id.no_lyric_tip)

        recyclerView?.layoutManager = LinearLayoutManager(requireContext())
        lyricAdapter = LyricAdapter()
        recyclerView?.adapter = lyricAdapter

        loadLyric(sid)
    }

    fun setMediaPlayer(mp: MediaPlayer?) {
        mediaPlayer = mp
        if (mp != null) {
            startSync()
        }
    }

    private fun loadLyric(sid: Long) {
        CenterThreadPool.run {
            try {
                val lyric = AudioApi.getLyric(sid)
                this@LyricFragment.lyric = lyric
                CenterThreadPool.runOnUiThread {
                    if (lyric != null && lyric.lines.isNotEmpty()) {
                        lyricAdapter?.setLines(lyric.lines)
                        noLyricTip?.visibility = View.GONE
                    } else {
                        noLyricTip?.visibility = View.VISIBLE
                    }
                }
            } catch (e: Exception) {
                CenterThreadPool.runOnUiThread {
                    noLyricTip?.visibility = View.VISIBLE
                }
            }
        }
    }

    fun startSync() {
        syncRunnable = object : Runnable {
            override fun run() {
                if (mediaPlayer != null && mediaPlayer!!.isPlaying) {
                    val currentPos = mediaPlayer!!.currentPosition.toLong()
                    syncLyricToPosition(currentPos)
                }
                handler.postDelayed(this, 200)
            }
        }
        handler.post(syncRunnable!!)
    }

    fun stopSync() {
        syncRunnable?.let { handler.removeCallbacks(it) }
    }

    private fun syncLyricToPosition(positionMs: Long) {
        val lines = lyric?.lines ?: return
        val adapter = lyricAdapter ?: return

        var targetIndex = -1
        for (i in lines.indices) {
            if (lines[i].time <= positionMs) {
                targetIndex = i
            } else {
                break
            }
        }

        if (targetIndex != currentHighlightIndex && targetIndex >= 0) {
            currentHighlightIndex = targetIndex
            adapter.setHighlightIndex(targetIndex)

            recyclerView?.let { rv ->
                val layoutManager = rv.layoutManager as LinearLayoutManager
                val firstVisible = layoutManager.findFirstVisibleItemPosition()
                val lastVisible = layoutManager.findLastVisibleItemPosition()

                if (targetIndex <= firstVisible || targetIndex >= lastVisible) {
                    val targetPos = (targetIndex - 3).coerceAtLeast(0)
                    rv.smoothScrollToPosition(targetPos)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopSync()
    }

    private class LyricAdapter : RecyclerView.Adapter<LyricViewHolder>() {
        private var lines: List<Lyric.LyricLine> = emptyList()
        private var highlightIndex = -1

        fun setLines(lines: List<Lyric.LyricLine>) {
            this.lines = lines
            notifyDataSetChanged()
        }

        fun setHighlightIndex(index: Int) {
            if (index != highlightIndex) {
                val oldIndex = highlightIndex
                highlightIndex = index
                if (oldIndex >= 0 && oldIndex < lines.size) {
                    notifyItemChanged(oldIndex)
                }
                if (index >= 0 && index < lines.size) {
                    notifyItemChanged(index)
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LyricViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.cell_lyric_line, parent, false)
            return LyricViewHolder(view)
        }

        override fun onBindViewHolder(holder: LyricViewHolder, position: Int) {
            val line = lines[position]
            holder.textView.text = line.text
            holder.textView.alpha = if (position == highlightIndex) 1.0f else 0.4f
            holder.textView.textSize = if (position == highlightIndex) 15f else 12f
        }

        override fun getItemCount(): Int = lines.size
    }

    private class LyricViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textView: TextView = view.findViewById(R.id.lyric_text)
    }
}