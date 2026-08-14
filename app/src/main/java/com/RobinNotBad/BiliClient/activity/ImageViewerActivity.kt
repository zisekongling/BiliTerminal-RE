package com.RobinNotBad.BiliClient.activity

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.viewpager.widget.ViewPager
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.base.BaseActivity
import com.RobinNotBad.BiliClient.adapter.viewpager.ViewPagerViewAdapter
import com.RobinNotBad.BiliClient.ui.widget.PhotoViewpager
import com.RobinNotBad.BiliClient.util.FileUtil
import com.RobinNotBad.BiliClient.util.GlideUtil
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.target.Target
import com.github.chrisbanes.photoview.PhotoView

class ImageViewerActivity : BaseActivity() {

    private var longClickTimestamp: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(R.style.Theme_BiliClient)
        setContentView(R.layout.activity_image_viewer)
        val intent = intent
        val imageList = intent.getStringArrayListExtra("imageList")!!

        val viewPager = findViewById<PhotoViewpager>(R.id.viewPager)
        val textView = findViewById<TextView>(R.id.text_page)

        val photoViewList = ArrayList<View>()

        val download = findViewById<ImageButton>(R.id.btn_download)
        download.setOnClickListener {
            val timeNow = System.currentTimeMillis()
            if (timeNow - longClickTimestamp < 3000) {
                val intent1 = Intent(this, DownloadActivity::class.java)
                    .putExtra("link", imageList[viewPager.currentItem])
                    .putExtra("path", FileUtil.getPicturePath().absolutePath)
                    .putExtra("type", 0)
                startActivity(intent1)
            } else MsgUtil.showMsg("再次点击下载")
            longClickTimestamp = timeNow
        }

        for (i in imageList.indices) {
            val photoView = PhotoView(this)
            try {
                Glide.with(this).asDrawable()
                    .load(GlideUtil.url_hq(imageList[i]))
                    .transition(GlideUtil.getTransitionOptions())
                    .override(Target.SIZE_ORIGINAL)
                    .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                    .into(photoView)
                photoView.maximumScale = 6.25f
            } catch (e: OutOfMemoryError) {
                MsgUtil.showMsg("超出内存，加载失败")
            } catch (e: Exception) {
                MsgUtil.err("图片查看", e)
            }

            photoViewList.add(photoView)
        }

        val vpiAdapter = ViewPagerViewAdapter(photoViewList)

        viewPager.adapter = vpiAdapter

        viewPager.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
            @SuppressLint("SetTextI18n")
            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
                if (positionOffset % 1 == 0f)
                    textView.text = "第${position + 1}/${imageList.size}张"
            }

            override fun onPageSelected(position: Int) {
            }

            override fun onPageScrollStateChanged(state: Int) {
            }
        })
    }
}