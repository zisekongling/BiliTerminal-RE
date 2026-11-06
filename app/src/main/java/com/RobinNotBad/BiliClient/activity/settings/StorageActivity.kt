package com.RobinNotBad.BiliClient.activity.settings

import android.os.Bundle
import android.view.View
import android.widget.TextView
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.base.BaseActivity
import com.RobinNotBad.BiliClient.util.AppStorageManager
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.StorageMath
import com.RobinNotBad.BiliClient.util.TerminalDialog

/**
 * 存储空间页：统计图片缓存 / 临时文件 / 更新安装包 / 可用空间，并按**白名单**清理。
 *
 * ## 交互
 * - 进页面先显示「正在统计…」，后台扫一遍目录再填数字（手表上递归扫 cache 是 IO，不能占主线程）；
 * - 三个清理按钮都先弹确认框（走 [TerminalDialog.confirm]，与全项目风格一致），
 *   执行完刷新数字并提示实际腾出的体积；
 * - 「重新统计」兜底：用户可能在别的页面刚清过缓存，或插拔了存储。
 *
 * ## 与清理策略的分工
 * 本类**不含任何删除判定**：能删什么、绝不删什么全部在 [AppStorageManager] 里，
 * 这样「清理范围」只有一处真源，也才能被 JVM 单测覆盖（见 `StorageMathTest`）。
 */
class StorageActivity : BaseActivity() {

    private lateinit var rowImages: TextView
    private lateinit var rowTemp: TextView
    private lateinit var rowApk: TextView
    private lateinit var rowSpace: TextView

    /** 布局 inflate 完成前不能碰控件（asyncInflate 是异步的）。 */
    private var ready = false

    /** 统计中：避免 onResume 与手动刷新叠加出多次扫目录。 */
    private var loading = false

    /** 清理中：避免连点两个按钮并发删同一棵目录。 */
    private var working = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        asyncInflate(R.layout.activity_storage) { _, _ ->
            rowImages = findViewById(R.id.row_images)
            rowTemp = findViewById(R.id.row_temp)
            rowApk = findViewById(R.id.row_apk)
            rowSpace = findViewById(R.id.row_space)

            // 顶栏返回：与其它设置页一致（BaseActivity 的默认顶栏点击也会 exit，这里显式覆盖成 finish）
            findViewById<View>(R.id.top)?.setOnClickListener { finish() }

            findViewById<View>(R.id.btn_clear_images)?.setOnClickListener { confirmClearImages() }
            findViewById<View>(R.id.btn_clear_temp)?.setOnClickListener { confirmClearTemp() }
            findViewById<View>(R.id.btn_delete_apk)?.setOnClickListener { confirmDeleteApk() }
            findViewById<View>(R.id.btn_refresh)?.setOnClickListener { refresh() }

            ready = true
            refresh()
        }
    }

    override fun onResume() {
        super.onResume()
        // 首次 onResume 早于 asyncInflate 回调（ready=false），此时不统计；之后每次回前台刷一次
        if (ready) refresh()
    }

    // ------------------------------------------------------------ 统计

    private fun refresh() {
        if (!ready || loading || working) return
        loading = true
        CenterThreadPool.run {
            val snapshot = try {
                AppStorageManager.inspect(this)
            } catch (t: Throwable) {
                null
            }
            CenterThreadPool.runOnUiThread {
                loading = false
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (snapshot != null) render(snapshot)
            }
        }
    }

    private fun render(snapshot: AppStorageManager.Snapshot) {
        rowImages.text = getString(
            R.string.storage_row_images,
            StorageMath.formatBytes(snapshot.imageBytes)
        )
        rowTemp.text = getString(
            R.string.storage_row_temp,
            StorageMath.formatBytes(snapshot.tempBytes)
        )
        rowApk.text = if (snapshot.apkBytes > 0L) {
            getString(R.string.storage_row_apk, StorageMath.formatBytes(snapshot.apkBytes))
        } else {
            getString(R.string.storage_row_apk_none)
        }
        rowSpace.text = if (snapshot.totalBytes > 0L) {
            getString(
                R.string.storage_row_space_detail,
                StorageMath.formatBytes(snapshot.availableBytes),
                StorageMath.formatBytes(snapshot.totalBytes)
            )
        } else {
            getString(R.string.storage_row_space_unknown)
        }
    }

    // ------------------------------------------------------------ 清理

    private fun confirmClearImages() {
        if (working) return
        TerminalDialog.confirm(
            this,
            getString(R.string.storage_confirm_title),
            getString(R.string.storage_confirm_images)
        ) { clearImages() }
    }

    private fun clearImages() {
        if (!startWorking()) return
        CenterThreadPool.run {
            val result = try {
                AppStorageManager.clearImageCache(this)
            } catch (t: Throwable) {
                AppStorageManager.ClearResult(0L, true, false)
            }
            finishWorking(result)
        }
    }

    private fun confirmClearTemp() {
        if (working) return
        TerminalDialog.confirm(
            this,
            getString(R.string.storage_confirm_title),
            getString(R.string.storage_confirm_temp)
        ) { clearTemp() }
    }

    private fun clearTemp() {
        if (!startWorking()) return
        CenterThreadPool.run {
            val result = try {
                AppStorageManager.clearTempFiles(this)
            } catch (t: Throwable) {
                AppStorageManager.ClearResult(0L, true, false)
            }
            finishWorking(result)
        }
    }

    private fun confirmDeleteApk() {
        if (working) return
        TerminalDialog.confirm(
            this,
            getString(R.string.storage_confirm_title),
            getString(R.string.storage_confirm_apk)
        ) { deleteApk() }
    }

    private fun deleteApk() {
        if (!startWorking()) return
        CenterThreadPool.run {
            val result = try {
                AppStorageManager.deleteUpdatePackage(this)
            } catch (t: Throwable) {
                AppStorageManager.ClearResult(0L, true, false)
            }
            CenterThreadPool.runOnUiThread {
                working = false
                if (isFinishing || isDestroyed) return@runOnUiThread
                when {
                    result.skipped -> MsgUtil.showMsg(getString(R.string.storage_apk_none_toast))
                    result.failed -> MsgUtil.showMsg(getString(R.string.storage_clear_failed))
                    else -> MsgUtil.showMsg(getString(R.string.storage_apk_deleted))
                }
                refresh()
            }
        }
    }

    /** 进入「清理中」状态；已有任务在跑时提示并返回 false。 */
    private fun startWorking(): Boolean {
        if (working) {
            MsgUtil.showMsg(getString(R.string.storage_busy))
            return false
        }
        working = true
        return true
    }

    /** 清理结束：回到主线程提示结果并刷新数字（统计要等 working 归零才能开始）。 */
    private fun finishWorking(result: AppStorageManager.ClearResult) {
        CenterThreadPool.runOnUiThread {
            working = false
            if (isFinishing || isDestroyed) return@runOnUiThread
            when {
                result.skipped -> MsgUtil.showMsg(getString(R.string.storage_clear_nothing))
                result.failed && result.freedBytes <= 0L ->
                    MsgUtil.showMsg(getString(R.string.storage_clear_failed))
                result.freedBytes > 0L -> MsgUtil.showMsg(
                    getString(R.string.storage_clear_done, StorageMath.formatBytes(result.freedBytes))
                )
                else -> MsgUtil.showMsg(getString(R.string.storage_clear_nothing))
            }
            refresh()
        }
    }
}
