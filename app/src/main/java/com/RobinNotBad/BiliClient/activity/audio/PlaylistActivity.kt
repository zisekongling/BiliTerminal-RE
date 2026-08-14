package com.RobinNotBad.BiliClient.activity.audio

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.base.InstanceActivity
import com.RobinNotBad.BiliClient.api.AudioApi
import com.RobinNotBad.BiliClient.model.Playlist
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.google.android.material.tabs.TabLayoutMediator

class PlaylistActivity : InstanceActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        asyncInflate(R.layout.activity_playlist) { _, _ ->
            setMenuClick()

            val tabLayout = findViewById<com.google.android.material.tabs.TabLayout>(R.id.tabLayout)
            val viewPager = findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.viewPager)

            viewPager.adapter = object : FragmentStateAdapter(this) {
                override fun getItemCount(): Int = 3

                override fun createFragment(position: Int): Fragment {
                    return when (position) {
                        0 -> PlaylistPageFragment.newInstance(PlaylistPageFragment.TYPE_MY)
                        1 -> PlaylistPageFragment.newInstance(PlaylistPageFragment.TYPE_HOT)
                        2 -> PlaylistPageFragment.newInstance(PlaylistPageFragment.TYPE_RANK)
                        else -> PlaylistPageFragment.newInstance(PlaylistPageFragment.TYPE_MY)
                    }
                }
            }

            TabLayoutMediator(tabLayout, viewPager) { tab, position ->
                tab.text = when (position) {
                    0 -> "我的歌单"
                    1 -> "热门歌单"
                    2 -> "热门榜单"
                    else -> ""
                }
            }.attach()
        }
    }

    class PlaylistPageFragment : Fragment() {
        private var type = 0
        private var page = 1
        private var bottom = false
        private var playlistList = ArrayList<Playlist>()
        private var adapter: PlaylistAdapter? = null

        companion object {
            const val TYPE_MY = 0
            const val TYPE_HOT = 1
            const val TYPE_RANK = 2

            fun newInstance(type: Int): PlaylistPageFragment {
                val fragment = PlaylistPageFragment()
                val args = Bundle()
                args.putInt("type", type)
                fragment.arguments = args
                return fragment
            }
        }

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            type = arguments?.getInt("type", 0) ?: 0
        }

        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
            return inflater.inflate(R.layout.fragment_simple_refresh, container, false)
        }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)

            val recyclerView = view.findViewById<RecyclerView>(R.id.recyclerView)
            recyclerView.layoutManager = LinearLayoutManager(requireContext())

            adapter = PlaylistAdapter(playlistList) { playlist ->
                val intent = Intent(requireContext(), PlaylistDetailActivity::class.java)
                intent.putExtra("mlid", if (type == TYPE_MY) playlist.id else playlist.menuId)
                intent.putExtra("title", playlist.title)
                intent.putExtra("author", playlist.uname)
                startActivity(intent)
            }
            recyclerView.adapter = adapter

            recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    if (!recyclerView.canScrollVertically(1) && !bottom) {
                        loadMore()
                    }
                }
            })

            loadMore()
        }

        private fun loadMore() {
            CenterThreadPool.run {
                try {
                    val list = when (type) {
                        TYPE_MY -> AudioApi.getMyPlaylists(page)
                        TYPE_HOT -> AudioApi.getHotPlaylists(page)
                        TYPE_RANK -> AudioApi.getRankPlaylists(page)
                        else -> null
                    }

                    if (list == null || list.isEmpty()) {
                        bottom = true
                    } else {
                        page++
                        CenterThreadPool.runOnUiThread {
                            val lastSize = playlistList.size
                            playlistList.addAll(list)
                            adapter?.notifyItemRangeInserted(lastSize, list.size)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    CenterThreadPool.runOnUiThread { MsgUtil.showMsg("加载失败") }
                }
            }
        }
    }

    class PlaylistAdapter(
        private val list: List<Playlist>,
        private val onClick: (Playlist) -> Unit
    ) : RecyclerView.Adapter<PlaylistViewHolder>() {

        init { setHasStableIds(true) }

        override fun getItemId(position: Int): Long = list[position].id

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlaylistViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.cell_playlist, parent, false)
            return PlaylistViewHolder(view)
        }

        override fun onBindViewHolder(holder: PlaylistViewHolder, position: Int) {
            val playlist = list[position]
            holder.titleView.text = playlist.title
            holder.authorView.text = playlist.uname
            holder.countView.text = "${playlist.songCount}首"
            holder.itemView.setOnClickListener { onClick(playlist) }
        }

        override fun getItemCount(): Int = list.size
    }

    class PlaylistViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val titleView: TextView = view.findViewById(R.id.playlist_title)
        val authorView: TextView = view.findViewById(R.id.playlist_author)
        val countView: TextView = view.findViewById(R.id.playlist_count)
    }
}