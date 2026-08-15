package com.RobinNotBad.BiliClient.activity.user

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.ImageView
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.base.BaseActivity
import com.RobinNotBad.BiliClient.api.UserInfoApi
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.GlideUtil
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.google.android.material.card.MaterialCardView
import java.io.ByteArrayOutputStream

class EditProfileActivity : BaseActivity() {

    private lateinit var avatarIcon: ImageView
    private var isUploading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_profile)

        if (SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0) == 0L) {
            MsgUtil.showMsg("还没有登录喵~")
            finish()
            return
        }

        avatarIcon = findViewById(R.id.upload_avatar_icon)
        val uploadAvatarCard = findViewById<MaterialCardView>(R.id.upload_avatar)
        val editSignCard = findViewById<MaterialCardView>(R.id.edit_sign)
        val editUserInfoCard = findViewById<MaterialCardView>(R.id.edit_user_info)

        val currentAvatar = SharedPreferencesUtil.getString("avatar", "")
        if (currentAvatar.isNotEmpty()) {
            Glide.with(this)
                .load(GlideUtil.url(currentAvatar))
                .apply(RequestOptions.circleCropTransform())
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .into(avatarIcon)
        }

        uploadAvatarCard.setOnClickListener { selectImage() }

        editSignCard.setOnClickListener {
            val intent = Intent(this, EditSignActivity::class.java)
            startActivity(intent)
        }

        editUserInfoCard.setOnClickListener {
            val intent = Intent(this, EditUserInfoActivity::class.java)
            startActivity(intent)
        }

        findViewById<android.view.View>(R.id.pageName).setOnClickListener { finish() }
    }

    private fun selectImage() {
        if (isUploading) {
            MsgUtil.showMsg("正在上传中...")
            return
        }
        if (!SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.cookie_refresh, true)) {
            MsgUtil.showDialog("无法上传", "上一次的Cookie刷新失败了，\n您可能需要重新登录以进行敏感操作", -1)
            return
        }
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, REQUEST_CODE_PICK_IMAGE)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_PICK_IMAGE && resultCode == RESULT_OK && data != null) {
            val imageUri = data.data
            if (imageUri != null) {
                uploadAvatar(imageUri)
            }
        }
    }

    private fun uploadAvatar(imageUri: Uri) {
        isUploading = true
        MsgUtil.showMsg("正在上传头像...")

        CenterThreadPool.run {
            try {
                val inputStream = contentResolver.openInputStream(imageUri)
                if (inputStream == null) {
                    runOnUiThread {
                        isUploading = false
                        MsgUtil.showMsg("无法读取图片")
                    }
                    return@run
                }
                val bitmap: Bitmap? = BitmapFactory.decodeStream(inputStream)
                inputStream.close()

                if (bitmap == null) {
                    runOnUiThread {
                        isUploading = false
                        MsgUtil.showMsg("无法解码图片")
                    }
                    return@run
                }

                val baos = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, baos)
                val imageData = baos.toByteArray()
                val fileName = "avatar_" + System.currentTimeMillis() + ".jpg"

                val result = UserInfoApi.uploadAvatar(imageData, fileName)
                val code = result.getInt("code")
                val message = result.optString("message", "")

                if (!isDestroyed) {
                    runOnUiThread {
                        isUploading = false
                        if (code == 0) {
                            MsgUtil.showMsg("头像上传成功，等待审核")
                            val data = result.optJSONObject("data")
                            if (data != null) {
                                val faceUrl = data.optString("url", "")
                                if (faceUrl.isNotEmpty()) {
                                    SharedPreferencesUtil.putString("avatar", faceUrl)
                                    Glide.with(this@EditProfileActivity)
                                        .load(GlideUtil.url(faceUrl))
                                        .apply(RequestOptions.circleCropTransform())
                                        .diskCacheStrategy(DiskCacheStrategy.NONE)
                                        .into(avatarIcon)
                                }
                            }
                        } else {
                            val errorMsg = when (code) {
                                -101 -> "账号未登录"
                                -102 -> "CSRF校验失败"
                                -111 -> "图片格式不支持"
                                -112 -> "图片过大"
                                -400 -> "请求参数错误"
                                -403 -> "CSRF验证失败"
                                else -> if (message.isNotEmpty()) message else "上传失败"
                            }
                            MsgUtil.showMsg("$errorMsg (错误码:$code)")
                        }
                    }
                }
            } catch (e: Exception) {
                if (!isDestroyed) {
                    runOnUiThread {
                        isUploading = false
                        MsgUtil.err("上传头像失败", e)
                    }
                }
            }
        }
    }

    companion object {
        private const val REQUEST_CODE_PICK_IMAGE = 1001
    }
}
