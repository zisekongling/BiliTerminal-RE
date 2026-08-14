package com.RobinNotBad.BiliClient.activity.player

import android.graphics.Color
import android.util.Log
import com.RobinNotBad.BiliClient.util.NetWorkUtil
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import com.netease.hearttouch.brotlij.Brotli
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject
import java.io.PrintWriter
import java.io.StringWriter
import java.io.Writer
import java.nio.charset.Charset
import java.util.Timer
import java.util.TimerTask

class PlayerDanmuClientListener : WebSocketListener() {
    @JvmField var mid: Long = 0
    @JvmField var roomid: Long = 0
    @JvmField var key: String = ""
    private var seq = 1
    private val messageData = MessageData()

    private var heartTimer: Timer? = null

    @JvmField var playerActivity: PlayerActivity? = null

    override fun onOpen(webSocket: WebSocket, response: Response) {
        super.onOpen(webSocket, response)
        Log.e("debug", "WebSocket已连接")

        heartTimer?.cancel()

        try {
            val obj = JSONObject()
            if (SharedPreferencesUtil.getBoolean("live_by_guest", false)) obj.put("uid", 0)
            else obj.put("uid", mid)
            obj.put("roomid", roomid)
            obj.put("protover", 3)
            obj.put("platform", "web")
            obj.put("buvid", NetWorkUtil.getCookies().getOrDefault("buvid3", ""))
            obj.put("type", 2)
            obj.put("key", key)

            webSocket.send(messageData.getData(3, 7, *obj.toString().toByteArray(Charset.forName("UTF-8"))))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private inner class MessageData {

        private fun getPacket(protocolVersion: Int, actionCode: Int, vararg data: Byte): ByteArray {
            val headerSize = 16
            val totalSize = headerSize + data.size
            val packet = ByteArray(totalSize)

            packet[0] = (totalSize shr 24).toByte()
            packet[1] = (totalSize shr 16).toByte()
            packet[2] = (totalSize shr 8).toByte()
            packet[3] = totalSize.toByte()

            packet[4] = 0
            packet[5] = headerSize.toByte()

            packet[6] = 0
            packet[7] = protocolVersion.toByte()

            packet[8] = 0
            packet[9] = 0
            packet[10] = 0
            packet[11] = actionCode.toByte()

            packet[12] = (seq shr 24).toByte()
            packet[13] = (seq shr 16).toByte()
            packet[14] = (seq shr 8).toByte()
            packet[15] = seq.toByte()
            seq++

            System.arraycopy(data, 0, packet, headerSize, data.size)
            Log.d("BiliClient", "getPackage totalLen=" + totalSize + ", data=" + String(data) + ", result=" + ByteString.of(*packet).hex())
            return packet
        }

        fun getData(protocolVersion: Int, actionCode: Int, vararg data: Byte): ByteString {
            return ByteString.of(*getPacket(protocolVersion, actionCode, *data))
        }

        fun getBrotliData(protocolVersion: Int, actionCode: Int, vararg data: Byte): ByteString {
            val encodedData = Brotli.compress(getPacket(protocolVersion, actionCode, *data))
            return ByteString.of(*getPacket(3, actionCode, *encodedData))
        }
    }

    override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
        super.onMessage(webSocket, bytes)

        val actionCode = bytes[11].toInt()
        when (actionCode) {
            8 -> {
                Log.e("debug", "弹幕流认证成功")
                heartTimer = Timer()
                val heartTimerTask = object : TimerTask() {
                    override fun run() {
                        Log.e("debug", "发送心跳包")
                        try {
                            webSocket.send(messageData.getData(1, 2, *"".toByteArray(Charset.forName("UTF-8"))))
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
                heartTimer!!.schedule(heartTimerTask, 3000, 32000)
            }
            5 -> plainPackage(bytes)
            else -> {}
        }
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        super.onClosed(webSocket, code, reason)
        Log.e("debug", "WebSocket连接关闭：" + reason + "(" + code + ")")

        heartTimer?.cancel()
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        super.onFailure(webSocket, t, response)

        val writer: Writer = StringWriter()
        val printWriter = PrintWriter(writer)
        t.printStackTrace(printWriter)

        Log.e("debug", "WebSocket连接失败：" + writer)

        heartTimer?.cancel()
    }

    private fun plainPackage(bytes: ByteString) {
        try {
            var result: JSONObject

            val bytes2 = bytes.substring(bytes[5].toInt())
            if (Brotli.decompress(bytes2.toByteArray()).size > 5) {
                val bytes3 = ByteString.of(*Brotli.decompress(bytes2.toByteArray()))
                result = JSONObject(bytes3.substring(bytes3[5].toInt()).utf8())
            } else if (bytes2.utf8().contains("{"))
                result = JSONObject(bytes2.utf8().substring(bytes2.utf8().indexOf("{")))
            else return

            var data: JSONObject
            when (result.getString("cmd")) {
                "DANMU_MSG" -> {
                    val info = result.getJSONArray("info")
                    val nickname = info.getJSONArray(0).getJSONObject(15).getJSONObject("user").getJSONObject("base").getString("name")
                    val content = info.getString(1)
                    if (SharedPreferencesUtil.getBoolean("player_danmaku_showsender", true))
                        playerActivity!!.addDanmaku(nickname + "：" + content, Color.WHITE)
                    else playerActivity!!.addDanmaku(content, Color.WHITE)

                    Log.e("debug", "pkg_dm")
                }
                "WATCHED_CHANGE" -> {
                    data = result.getJSONObject("data")
                    playerActivity!!.online_number = data.getString("text_large")
                }
                "INTERACT_WORD" -> {
                    data = result.getJSONObject("data")

                    if (data.getInt("msg_type") == 1)
                        playerActivity!!.addDanmaku(data.getString("uname") + " 进入了直播间", Color.CYAN, 12, 4, 0)
                }
                "SEND_GIFT" -> {
                    data = result.getJSONObject("data")
                    val content2 = data.getString("uname") + " " + data.getString("action") + data.getInt("num") + "个" + data.getString("giftName")
                    playerActivity!!.addDanmaku(content2, Color.WHITE, 25, 1, Color.argb(160, 255, 80, 80))
                }
                "ENTRY_EFFECT" -> {
                    data = result.getJSONObject("data")
                    playerActivity!!.addDanmaku(data.getString("copy_writing").replace("<%", "").replace("%>", ""), Color.WHITE, 25, 1, Color.argb(160, 80, 80, 255))
                }
                "NOTICE_MSG" -> {
                    playerActivity!!.addDanmaku(result.getString("msg_common"), Color.RED, 25, 1, Color.argb(60, 255, 255, 255))
                }
                "ROOM_CHANGE" -> {
                    data = result.getJSONObject("data")
                    playerActivity!!.runOnUiThread {
                        try {
                            playerActivity!!.text_title?.text = data.getString("title")
                        } catch (ignore: Exception) {
                        }
                    }
                }
                else -> {}
            }
        } catch (e: Exception) {
            val writer: Writer = StringWriter()
            val printWriter = PrintWriter(writer)
            e.printStackTrace(printWriter)

            Log.e("debug", "解析普通包时错误：" + writer)
        }
    }
}