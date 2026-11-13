package com.RobinNotBad.BiliClient.service.download

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.RobinNotBad.BiliClient.BiliTerminal
import com.RobinNotBad.BiliClient.helper.sql.DownloadSqlHelper
import com.RobinNotBad.BiliClient.model.DownloadSection
import com.RobinNotBad.BiliClient.model.VideoMeta
import com.RobinNotBad.BiliClient.util.Logu
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.VideoMetaManager
import java.io.File

/**
 * 下载任务的数据库访问层（E2 拆分 DownloadService 第 3 步）。
 *
 * 这一段原本整体住在 `DownloadService.Companion` 里，都是无状态的"开连接、跑一句 SQL、关连接"，
 * 只有 [firstDown] 是跨调用的记忆位（用户点了某一集时记下 id，让下一次调度优先取它）。
 * 搬出来只为让 DownloadService.kt 变薄，语义一个字都没动：
 *
 * 刻意保留的现状（不要"顺手修"）：
 * - 每次调用都新建 `DownloadSqlHelper` 再 close，没有连接复用/缓存——照搬。
 * - [getAll] 在"查不到数据"时返回 null，在"抛异常"时返回**空列表**：这两个分支不一样，
 *   调用方靠它区分，不要统一成一种。
 * - 表结构（download 表 13 列）与 SQL 文本原样照抄，不新增/删除/改名任何一列。
 *   26.11.11 例外：为反馈 #87/#90（多段 durl 只下第一段）新增 `segments`（分段清单 JSON）
 *   与 `fail_reason`（失败原因）两列，升级路径见 [DownloadSqlHelper.onUpgrade]（升版本 + ALTER，
 *   老记录这两列为 NULL，读出来就是"没有清单/没有原因"）。
 * - 旧代码里 `database.close()` 已经调过一次，`finally` 里还会再调一次，是幂等的冗余；
 *   原样保留（重复关闭 SQLiteDatabase 不抛异常）。
 */
internal object DownloadRepository {

    /** 用户指定优先下载的那一集（由 [DownloadService.start] 写入），-1 表示没有指定。 */
    private var firstDown: Long = -1

    /** 由 DownloadService.start() 记录用户点的那一集，供下一次 [getFirst] 优先取。 */
    fun setFirstDown(id: Long) {
        firstDown = id
    }

    fun getFirst(): DownloadSection? {
        var cursor: Cursor? = null
        var database: SQLiteDatabase? = null
        return try {
            val helper = DownloadSqlHelper(BiliTerminal.context)
            database = helper.readableDatabase

            if (firstDown >= 0)
                cursor = database.rawQuery("select * from download where id=? limit 1",
                    arrayOf(firstDown.toString()))
            if (cursor == null)
                cursor = database.rawQuery("select * from download where state=? limit 1", arrayOf("none"))

            firstDown = -1

            if (cursor == null || cursor.count == 0)
                return null

            cursor.moveToFirst()
            DownloadSection(cursor)
        } catch (e: Exception) {
            MsgUtil.err(e)
            null
        } finally {
            cursor?.close()
            database?.close()
        }
    }

    fun getAll(): ArrayList<DownloadSection>? {
        var cursor: Cursor? = null
        var database: SQLiteDatabase? = null
        return try {
            val helper = DownloadSqlHelper(BiliTerminal.context)
            database = helper.readableDatabase
            cursor = database.rawQuery("select * from download", null)
            if (cursor == null || cursor.count == 0)
                return null

            val list = ArrayList<DownloadSection>()
            while (cursor.moveToNext()) {
                list.add(DownloadSection(cursor))
            }
            list
        } catch (e: Exception) {
            MsgUtil.err(e)
            ArrayList()
        } finally {
            cursor?.close()
            database?.close()
        }
    }

    fun deleteSection(id: Long) {
        var database: SQLiteDatabase? = null
        try {
            val helper = DownloadSqlHelper(BiliTerminal.context)
            database = helper.writableDatabase
            database.execSQL("delete from download where id=?", arrayOf<Any>(id))
            database.close()
        } catch (e: Exception) {
            MsgUtil.err(e)
        } finally {
            database?.close()
        }
    }

    /** 清空整张表。当前没有调用方（死代码），仅为保持对外契约原样保留。 */
    fun clear() {
        var database: SQLiteDatabase? = null
        try {
            val helper = DownloadSqlHelper(BiliTerminal.context)
            database = helper.writableDatabase
            database.execSQL("delete from download", arrayOf<Any>())
            database.close()
        } catch (e: Exception) {
            MsgUtil.err(e)
        } finally {
            database?.close()
        }
    }

    fun setState(id: Long, state: String) {
        var database: SQLiteDatabase? = null
        try {
            val helper = DownloadSqlHelper(BiliTerminal.context)
            database = helper.writableDatabase
            database.execSQL("update download set state=? where id=?", arrayOf<Any>(state, id))
            database.close()
        } catch (e: Exception) {
            MsgUtil.err(e)
        } finally {
            database?.close()
        }
    }

    /**
     * 记下这个任务的分段清单（JSON，格式见 [DownloadSegmentPlan.toJson]）。
     *
     * 为什么要落库：清单只在取流那一刻拿得到，而下载可能被暂停/被系统杀掉进程，重启后
     * 若重新取流，服务端给的切法/地址可能已经变了——拿旧分片拼新清单就是一堆错位字节。
     */
    fun setSegments(id: Long, segmentsJson: String) {
        var database: SQLiteDatabase? = null
        try {
            val helper = DownloadSqlHelper(BiliTerminal.context)
            database = helper.writableDatabase
            database.execSQL("update download set segments=? where id=?",
                arrayOf<Any>(segmentsJson, id))
            database.close()
        } catch (e: Exception) {
            MsgUtil.err(e)
        } finally {
            database?.close()
        }
    }

    /** 读这个任务的分段清单 JSON；从没写过（老记录）时返回 null。 */
    fun getSegments(id: Long): String? {
        var cursor: Cursor? = null
        var database: SQLiteDatabase? = null
        return try {
            val helper = DownloadSqlHelper(BiliTerminal.context)
            database = helper.readableDatabase
            cursor = database.rawQuery("select segments from download where id=? limit 1",
                arrayOf(id.toString()))
            if (cursor == null || cursor.count == 0) return null
            cursor.moveToFirst()
            cursor.getString(0)
        } catch (e: Exception) {
            MsgUtil.err(e)
            null
        } finally {
            cursor?.close()
            database?.close()
        }
    }

    /**
     * 记下失败原因（已本地化的可读文字，如"服务器返回 HTTP 403"），列表直接显示。
     *
     * 传空串 = 清掉上次的原因（重新开始下载时调用）。原因本身是动态文案，不进 strings.xml。
     */
    fun setFailReason(id: Long, reason: String) {
        var database: SQLiteDatabase? = null
        try {
            val helper = DownloadSqlHelper(BiliTerminal.context)
            database = helper.writableDatabase
            database.execSQL("update download set fail_reason=? where id=?",
                arrayOf<Any>(reason, id))
            database.close()
        } catch (e: Exception) {
            MsgUtil.err(e)
        } finally {
            database?.close()
        }
    }

    /**
     * 保存视频元数据到缓存文件夹。
     *
     * 必须"先读旧 meta 再合并"，不能 new 一份整份覆盖：`.video_meta.json` 里还存着
     * 本地续播位置（progressMs/durationMs）与画质列表，重下载/换清晰度时整份覆盖会把这些冲掉。
     */
    fun saveVideoMeta(folder: File, title: String, aid: Long, cid: Long, qn: Int, downloadType: String) {
        try {
            val meta = VideoMetaManager.readMeta(folder)
            meta.title = title
            meta.aid = aid
            meta.cid = cid
            meta.qn = qn
            meta.downloadType = downloadType
            VideoMetaManager.saveMeta(folder, meta)
        } catch (e: Exception) {
            Logu.e("saveVideoMeta", "保存视频元数据失败: ${e.message}")
        }
    }

    /**
     * 更新视频元数据中的画质列表（下载完成后回调）
     */
    fun updateVideoMetaQualityLists(folder: File, qnStrList: Array<String>?, qnValueList: IntArray?) {
        try {
            if (qnStrList == null && qnValueList == null) return
            val meta = VideoMetaManager.readMeta(folder)
            meta.qnStrList = qnStrList
            meta.qnValueList = qnValueList
            VideoMetaManager.saveMeta(folder, meta)
        } catch (e: Exception) {
            Logu.e("updateVideoMeta", "更新画质列表失败: ${e.message}")
        }
    }
}
