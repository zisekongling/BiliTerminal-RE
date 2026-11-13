package com.RobinNotBad.BiliClient.helper.sql

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.RobinNotBad.BiliClient.util.MsgUtil

class DownloadSqlHelper(context: Context?) : SQLiteOpenHelper(context, "download.db", null, 5) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("create table download(id INTEGER primary key autoincrement," +
                "type TEXT," +
                "state TEXT," +
                "aid BIGINT," +
                "cid BIGINT," +
                "qn INTEGER," +
                "title TEXT," +
                "child TEXT," +
                "cover TEXT," +
                "download_type TEXT DEFAULT 'video'," +
                "audio_url TEXT," +
                // 分段下载清单（JSON，见 service/download/DownloadSegmentPlan.toJson）：
                // 多段 durl 的下载要靠它跨"暂停/重启 App"记住每段地址与字节数（反馈 #87/#90）。
                "segments TEXT," +
                // 上次失败的可读原因（HTTP 码 / 第几段失败 / 校验不过），列表直接显示给用户。
                "fail_reason TEXT)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion >= newVersion) return
        try {
            // 逐级迁移：任何旧版本升上来都先尝试 ALTER 补列，保住历史下载记录（续传进度、任务列表）；
            // 此前是 oldVersion == 3 && newVersion == 4 的精确相等判断，0/1/2 版本升上来会直接 drop table，
            // 用户升级 App 就丢光全部下载记录。
            if (oldVersion < 4) {
                try {
                    db.execSQL("ALTER TABLE download ADD COLUMN download_type TEXT DEFAULT 'video'")
                    db.execSQL("ALTER TABLE download ADD COLUMN audio_url TEXT")
                } catch (e: Throwable) {
                    // ALTER 失败（列已存在、表结构损坏）时**不再 drop table 重建**。
                    // 以前这里建一次新表，等于把用户的历史下载记录（任务列表、续传进度、元数据）全部删掉 ——
                    // 这正是本次改动要避免的数据丢失。宁可让新字段暂时读不到（个别下载功能报错、可在日志里
                    // 看到原因），也绝不主动删除用户数据。
                    MsgUtil.err(e)
                }
            }
            // 版本 5（反馈 #87/#90）：多个分段清单列与失败原因列。老记录这两列是 NULL，
            // 读取侧按"没有清单 / 没有原因"处理（见 DownloadSection 的 cursor 兼容写法）。
            if (oldVersion < 5) {
                try {
                    db.execSQL("ALTER TABLE download ADD COLUMN segments TEXT")
                    db.execSQL("ALTER TABLE download ADD COLUMN fail_reason TEXT")
                } catch (e: Throwable) {
                    // 同上面的原则：ALTER 失败也绝不 drop table，宁可新列读不到。
                    MsgUtil.err(e)
                }
            }
        } catch (e: Throwable) {
            MsgUtil.err(e)
        }
    }
}