package com.RobinNotBad.BiliClient.activity.search

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.audio.AudioPlayerActivity
import com.RobinNotBad.BiliClient.model.AudioInfo
import com.RobinNotBad.BiliClient.util.CenterThreadPool

class SearchAudioFragment : SearchFragment() {
    private var audioList = ArrayList<AudioInfo>()
    private var audioAdapter: AudioAdapter? = null

    companion object {
        fun newInstance(): SearchAudioFragment = SearchAudioFragment()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        audioList = ArrayList()
        audioAdapter = AudioAdapter()
        setAdapter(audioAdapter!!)

        setOnRefreshListener { refreshInternal() }
        setOnLoadMoreListener { page -> continueLoading(page) }
    }

    private fun continueLoading(page: Int) {
        CenterThreadPool.run {
            try {
                val url = "https://api.bilibili.com/x/web-interface/wbi/search/type" +
                        "?search_type=video&page=$page&keyword=${java.net.URLEncoder.encode(keyword, "UTF-8")}" +
                        "&tids=30&order=totalrank"
                val all = com.RobinNotBad.BiliClient.api.ConfInfoApi.signWBI(url)
                val response = com.RobinNotBad.BiliClient.util.NetWorkUtil.getJson(all)

                if (response.isNull("data")) {
                    bottom = true
                    setRefreshing(false)
                    return@run
                }

                val data = response.getJSONObject("data")
                val result = data.optJSONArray("result")
                if (result == null || result.length() == 0) {
                    bottom = true
                } else {
                    if (page == 1) showEmptyView(false)
                    val list = ArrayList<AudioInfo>()
                    for (i in 0 until result.length()) {
                        val card = result.getJSONObject(i)
                        val title = card.optString("title", "")
                            .replace("<em class=\"keyword\">", "")
                            .replace("</em>", "")
                        val author = card.optString("author", "")
                        val cover = card.optString("pic", "")
                        val duration = card.optString("duration", "")
                        val durationSec = parseDuration(duration)
                        val bvid = card.optString("bvid", "")

                        val audio = AudioInfo(
                            card.optLong("aid", 0),
                            title,
                            author,
                            if (cover.startsWith("http")) cover else "https:$cover",
                            durationSec
                        )
                        audio.lyricUrl = bvid
                        list.add(audio)
                    }
                    if (list.size == 0) bottom = true
                    else CenterThreadPool.runOnUiThread {
                        val lastSize = audioList.size
                        audioList.addAll(list)
                        audioAdapter!!.notifyItemRangeInserted(lastSize + 1, audioList.size - lastSize)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                loadFail(e)
            }
            setRefreshing(false)
        }
    }

    private fun parseDuration(duration: String): Int {
        if (duration.isEmpty()) return 0
        val parts = duration.split(":")
        if (parts.size == 2) {
            return parts[0].toIntOrNull()?.times(60)?.plus(parts[1].toIntOrNull() ?: 0) ?: 0
        }
        return 0
    }

    override fun refreshInternal() {
        CenterThreadPool.runOnUiThread {
            page = 1
            if (this.audioAdapter == null)
                this.audioAdapter = AudioAdapter()
            val sizeOld = this.audioList.size
            this.audioList.clear()
            if (sizeOld != 0) this.audioAdapter!!.notifyItemRangeRemoved(0, sizeOld)
            CenterThreadPool.run { continueLoading(page) }
        }
    }

    private inner class AudioAdapter : RecyclerView.Adapter<AudioViewHolder>() {
        init { setHasStableIds(true) }

        override fun getItemId(position: Int): Long = audioList[position].sid

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AudioViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.cell_audio_list, parent, false)
            return AudioViewHolder(view)
        }

        override fun onBindViewHolder(holder: AudioViewHolder, position: Int) {
            val audio = audioList[position]
            holder.titleView.text = audio.title
            holder.authorView.text = audio.author
            if (audio.duration > 0) {
                val min = audio.duration / 60
                val sec = audio.duration % 60
                holder.durationView.text = String.format("%d:%02d", min, sec)
            }
            holder.itemView.setOnClickListener {
                val intent = Intent(holder.itemView.context, AudioPlayerActivity::class.java)
                intent.putExtra("sid", audio.sid)
                intent.putExtra("title", audio.title)
                intent.putExtra("author", audio.author)
                intent.putExtra("cover", audio.cover)
                holder.itemView.context.startActivity(intent)
            }
        }

        override fun getItemCount(): Int = audioList.size
    }

    private class AudioViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val titleView: TextView = view.findViewById(R.id.audio_title)
        val authorView: TextView = view.findViewById(R.id.audio_author)
        val durationView: TextView = view.findViewById(R.id.audio_duration)
    }
}