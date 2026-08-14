package com.RobinNotBad.BiliClient.activity.audio

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.base.InstanceActivity
import com.RobinNotBad.BiliClient.api.AudioApi
import com.RobinNotBad.BiliClient.model.AudioInfo
import com.RobinNotBad.BiliClient.model.Playlist
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.MsgUtil

class PlaylistDetailActivity : InstanceActivity() {

    private var mlid: Long = 0
    private var playlistTitle: String = ""
    private var playlistAuthor: String = ""
    private var playlist: Playlist? = null
    private var audioList = ArrayList<AudioInfo>()
    private var adapter: AudioListAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mlid = intent.getLongExtra("mlid", 0)
        playlistTitle = intent.getStringExtra("title") ?: ""
        playlistAuthor = intent.getStringExtra("author") ?: ""

        asyncInflate(R.layout.activity_playlist_detail) { _, _ ->
            setMenuClick()

            val infoView = findViewById<TextView>(R.id.playlist_info)

            val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
            recyclerView.layoutManager = LinearLayoutManager(this)

            adapter = AudioListAdapter(audioList) { audio ->
                val intent = Intent(this, AudioPlayerActivity::class.java)
                intent.putExtra("sid", audio.sid)
                intent.putExtra("title", audio.title)
                intent.putExtra("author", audio.author)
                intent.putExtra("cover", audio.cover)
                startActivity(intent)
            }
            recyclerView.adapter = adapter

            findViewById<View>(R.id.pageName).let { (it as TextView).text = playlistTitle }

            loadPlaylistDetail()
        }
    }

    private fun loadPlaylistDetail() {
        CenterThreadPool.run {
            try {
                val playlist = AudioApi.getPlaylistDetail(mlid)
                this@PlaylistDetailActivity.playlist = playlist

                if (playlist != null && playlist.sids != null) {
                    CenterThreadPool.runOnUiThread {
                        findViewById<TextView>(R.id.playlist_info).text =
                            "歌曲数: ${playlist.songCount}  播放: ${playlist.playCount}"
                    }

                    for (sid in playlist.sids) {
                        try {
                            val audio = AudioApi.getAudioInfo(sid)
                            if (audio != null) {
                                audioList.add(audio)
                                CenterThreadPool.runOnUiThread {
                                    adapter?.notifyItemInserted(audioList.size - 1)
                                }
                            }
                        } catch (_: Exception) {}
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                CenterThreadPool.runOnUiThread { MsgUtil.showMsg("加载歌单失败") }
            }
        }
    }

    private class AudioListAdapter(
        private val list: List<AudioInfo>,
        private val onClick: (AudioInfo) -> Unit
    ) : RecyclerView.Adapter<AudioListViewHolder>() {

        init { setHasStableIds(true) }

        override fun getItemId(position: Int): Long = list[position].sid

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AudioListViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.cell_audio_list, parent, false)
            return AudioListViewHolder(view)
        }

        override fun onBindViewHolder(holder: AudioListViewHolder, position: Int) {
            val audio = list[position]
            holder.titleView.text = audio.title
            holder.authorView.text = audio.author
            if (audio.duration > 0) {
                val min = audio.duration / 60
                val sec = audio.duration % 60
                holder.durationView.text = String.format("%d:%02d", min, sec)
            }
            holder.itemView.setOnClickListener { onClick(audio) }
        }

        override fun getItemCount(): Int = list.size
    }

    private class AudioListViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val titleView: TextView = view.findViewById(R.id.audio_title)
        val authorView: TextView = view.findViewById(R.id.audio_author)
        val durationView: TextView = view.findViewById(R.id.audio_duration)
    }
}