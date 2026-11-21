package com.RobinNotBad.BiliClient.activity

import android.app.Activity
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.base.BaseActivity
import com.RobinNotBad.BiliClient.ui.widget.recycler.CustomGridManager
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.GlideUtil
import com.RobinNotBad.BiliClient.util.ImagePickerAlbums
import com.RobinNotBad.BiliClient.util.ImagePickerAlbums.Photo
import com.RobinNotBad.BiliClient.util.Logu
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.TerminalDialog
import com.RobinNotBad.BiliClient.util.view.TextButtonColors
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.android.material.button.MaterialButton

/**
 * 内置选图页（反馈 #53：「系统的文件不好用」）。
 *
 * 手表上的系统选图通常是一个文件管理器，要从存储根目录一层层点进 DCIM / Pictures，
 * 缩略图很小甚至没有。这里直接读 MediaStore，按时间倒序铺三列缩略图，可按相册筛选。
 *
 * **结果格式与 `ACTION_GET_CONTENT` 一致**：`RESULT_OK` + `data.data` 是 `content://media/...` 的 Uri，
 * 所以各调用点只需把拉起的 Intent 换成 [intent]，结果处理一行不用改。
 * 没给权限 / 相册为空 / 想选相册以外的文件时，顶部「系统选择器」走回原来的 `ACTION_GET_CONTENT`，
 * 选中的结果原样转交给调用方。
 */
class ImagePickerActivity : BaseActivity() {

    companion object {
        private const val SPAN_COUNT = 3

        /** 拉起内置选图页的 Intent，替代原来的 `Intent(ACTION_GET_CONTENT).setType(image)`。 */
        @JvmStatic
        fun intent(context: Context): Intent = Intent(context, ImagePickerActivity::class.java)
    }

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyText: TextView
    private lateinit var albumBtn: MaterialButton

    private var allPhotos: List<Photo> = emptyList()
    private var currentAlbum: String? = null
    private val adapter = PhotoAdapter()

    private val unnamedBucket: String by lazy { getString(R.string.text_image_picker_other) }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) loadPhotos() else showEmpty(getString(R.string.text_image_picker_no_permission))
        }

    /** 系统选择器的结果直接转交给调用方（同一个 Uri，读权限一并带上）。 */
    private val systemLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val uri = result.data?.data
            if (result.resultCode == Activity.RESULT_OK && uri != null) finishWith(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_picker)

        recyclerView = findViewById(R.id.recyclerView)
        emptyText = findViewById(R.id.empty)
        albumBtn = findViewById(R.id.btn_album)
        findViewById<View>(R.id.btn_system).setOnClickListener { openSystemPicker() }
        albumBtn.setOnClickListener { showAlbumChooser() }
        // TextButton 状态列表里的 ?attr 在 API 21/22 解析不了（会退成框架哨兵红），在这里统一兜住
        TextButtonColors.apply(albumBtn, findViewById(R.id.btn_system))

        recyclerView.layoutManager = CustomGridManager(this, SPAN_COUNT)
        recyclerView.adapter = adapter

        val permission = ImagePickerAlbums.readPermission(Build.VERSION.SDK_INT)
        // 6.0 以下安装即授予，checkSelfPermission 恒为 GRANTED，不会走到申请
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            loadPhotos()
        } else {
            permissionLauncher.launch(permission)
        }
    }

    private fun loadPhotos() {
        CenterThreadPool.run {
            val photos = try {
                queryPhotos()
            } catch (e: Exception) {
                // 个别 ROM 的 MediaStore 不完整，查询会直接抛；退回系统选择器还能用
                Logu.e("ImagePicker", "读取相册失败：${e.message}")
                null
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                when {
                    photos == null -> showEmpty(getString(R.string.text_image_picker_load_failed))
                    photos.isEmpty() -> showEmpty(getString(R.string.text_image_picker_empty))
                    else -> {
                        allPhotos = photos
                        applyAlbum(null)
                    }
                }
            }
        }
    }

    /** 只取 id 与相册名（几千张图也就几十 KB），缩略图由 Glide 按格子大小解码。 */
    private fun queryPhotos(): List<Photo> {
        val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
        val result = ArrayList<Photo>()
        contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val bucketCol = cursor.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            while (cursor.moveToNext()) {
                val bucket = if (bucketCol >= 0) cursor.getString(bucketCol) ?: "" else ""
                result.add(Photo(cursor.getLong(idCol), bucket))
            }
        }
        return result
    }

    private fun applyAlbum(album: String?) {
        currentAlbum = album
        albumBtn.text = album ?: getString(R.string.text_image_picker_all)
        adapter.submit(ImagePickerAlbums.filter(allPhotos, album, unnamedBucket))
        emptyText.visibility = View.GONE
        recyclerView.visibility = View.VISIBLE
        recyclerView.scrollToPosition(0)
    }

    private fun showAlbumChooser() {
        if (allPhotos.isEmpty()) return
        val albums = ImagePickerAlbums.group(allPhotos, unnamedBucket)
        val names = albums.map {
            getString(R.string.text_image_picker_album_item, it.name ?: getString(R.string.text_image_picker_all), it.count)
        }
        val checked = albums.indexOfFirst { it.name == currentAlbum }.coerceAtLeast(0)
        TerminalDialog.singleChoice(this, getString(R.string.text_image_picker_choose_album), names, checked) { sheet, which ->
            sheet.dialog.dismiss()
            applyAlbum(albums[which].name)
        }.show()
    }

    private fun showEmpty(text: String) {
        emptyText.text = text
        emptyText.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
    }

    private fun openSystemPicker() {
        try {
            val intent = Intent(Intent.ACTION_GET_CONTENT)
            intent.type = "image/*"
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            systemLauncher.launch(intent)
        } catch (e: Exception) {
            // 精简手表 ROM 可能连 GET_CONTENT 都没人处理
            MsgUtil.showMsg(getString(R.string.msg_no_image_picker))
        }
    }

    private fun finishWith(uri: Uri) {
        setResult(
            Activity.RESULT_OK,
            Intent().setData(uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        )
        finish()
    }

    private inner class PhotoAdapter : RecyclerView.Adapter<PhotoAdapter.Holder>() {
        private var items: List<Photo> = emptyList()

        fun submit(list: List<Photo>) {
            items = list
            @Suppress("NotifyDataSetChanged")
            notifyDataSetChanged()
        }

        inner class Holder(val image: ImageView) : RecyclerView.ViewHolder(image)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            // 正方形格子：边长 = 可用宽度 / 列数（RecyclerView 的左右 padding 要扣掉）。
            // 列表还没布局完时 width 为 0，退回内容区宽度（BaseActivity 已扣掉界面边距）
            val width = parent.width.takeIf { it > 0 } ?: window_width
            val size = ((width - parent.paddingLeft - parent.paddingRight) / SPAN_COUNT).coerceAtLeast(1)
            val image = ImageView(parent.context)
            image.layoutParams = ViewGroup.MarginLayoutParams(size, size)
            image.scaleType = ImageView.ScaleType.CENTER_CROP
            image.setPadding(2, 2, 2, 2)
            image.contentDescription = getString(R.string.text_image_picker_item)
            return Holder(image)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, items[position].id)
            val size = holder.image.layoutParams.width.coerceAtLeast(1)
            GlideUtil.with(holder.image)
                .asDrawable()
                .load(uri)
                .override(size, size)
                .centerCrop()
                // 手表内存小：缩略图用 RGB_565，与列表封面一致
                .format(DecodeFormat.PREFER_RGB_565)
                .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                .into(holder.image)
            holder.image.setOnClickListener { finishWith(uri) }
        }

        override fun getItemCount(): Int = items.size
    }
}
