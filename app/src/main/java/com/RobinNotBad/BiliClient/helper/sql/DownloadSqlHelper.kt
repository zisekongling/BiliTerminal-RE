package com.RobinNotBad.BiliClient.helper.sql

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.RobinNotBad.BiliClient.util.MsgUtil

class DownloadSqlHelper(context: Context?) : SQLiteOpenHelper(context, "download.db", null, 4) {
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
                "audio_url TEXT)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion != newVersion)
            try {
                if (oldVersion == 3 && newVersion == 4) {
                    db.execSQL("ALTER TABLE download ADD COLUMN download_type TEXT DEFAULT 'video'")
                    db.execSQL("ALTER TABLE download ADD COLUMN audio_url TEXT")
                } else {
                    db.execSQL("drop table if exists download")
                    onCreate(db)
                }
            } catch (e: Throwable) {
                MsgUtil.err(e)
            }
    }
}