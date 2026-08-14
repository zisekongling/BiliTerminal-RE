package com.RobinNotBad.BiliClient.activity.settings

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.TextView
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.article.OpusInfoActivity
import com.RobinNotBad.BiliClient.activity.base.BaseActivity
import com.RobinNotBad.BiliClient.activity.settings.login.SpecialLoginActivity
import com.RobinNotBad.BiliClient.api.ConfInfoApi
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.NetWorkUtil
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import com.google.android.material.card.MaterialCardView
import com.google.android.material.switchmaterial.SwitchMaterial
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import okhttp3.Response
import okhttp3.ResponseBody
import okio.BufferedSource

class TestActivity : BaseActivity() {

    private lateinit var sw_wbi: SwitchMaterial
    private lateinit var sw_post: SwitchMaterial
    private lateinit var input_link: EditText
    private lateinit var input_data: EditText
    private lateinit var output: EditText
    private lateinit var btn_crash: MaterialCardView
    private lateinit var btn_request: MaterialCardView
    private lateinit var btn_cookies: MaterialCardView
    private lateinit var btn_opus: MaterialCardView

    private var conversation: JSONArray? = null

    @SuppressLint("MutatingSharedPrefs", "SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_test)

        sw_wbi = findViewById(R.id.switch_wbi)
        sw_post = findViewById(R.id.switch_post)
        input_link = findViewById(R.id.input_link)
        input_data = findViewById(R.id.input_data)
        output = findViewById(R.id.output_json)
        btn_crash = findViewById(R.id.crash)
        btn_opus = findViewById(R.id.opus)

        input_link.setText(SharedPreferencesUtil.getString("dev_test_link", ""))

        sw_post.setOnCheckedChangeListener { _, checked ->
            input_data.visibility = if (checked) View.VISIBLE else View.GONE
        }

        btn_request = findViewById(R.id.request)

        btn_request.setOnClickListener {
            CenterThreadPool.run {
                try {
                    var url = input_link.text.toString()
                    if (!url.startsWith("https://") && !url.startsWith("http://"))
                        url = "https://$url"

                    if (sw_wbi.isChecked) url = ConfInfoApi.signWBI(url)

                    runOnUiThread {
                        output.setText("")
                        MsgUtil.showMsg("发出请求！")
                    }
                    val result: String
                    if (sw_post.isChecked) {
                        val data = input_data.text.toString()
                        result = NetWorkUtil.post(url, data).body!!.string()
                    } else {
                        result = NetWorkUtil.get(url).body!!.string()
                    }

                    runOnUiThread {
                        output.setText(result)
                        MsgUtil.showMsg("请求成功！")
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        output.setText(e.toString())
                        MsgUtil.showMsg("请求失败！")
                    }
                    e.printStackTrace()
                }
            }
        }

        btn_cookies = findViewById(R.id.cookies)
        btn_cookies.setOnClickListener {
            val intent = Intent(this, SpecialLoginActivity::class.java)
            intent.putExtra("login", false)
            startActivity(intent)
        }

        btn_opus.setOnClickListener { startActivity(Intent(this, OpusInfoActivity::class.java).putExtra("id", 781871626480254985L)) }

        btn_crash.setOnClickListener {
            input_data.visibility = View.VISIBLE
            sw_wbi.setText("使用R1")
            sw_post.visibility = View.GONE
            btn_cookies.visibility = View.GONE
            btn_request.visibility = View.GONE
            btn_opus.visibility = View.GONE
            val desc = findViewById<TextView>(R.id.desc)
            desc.text = getString(R.string.dev_catgirl_desc)

            CenterThreadPool.run {
                try {
                    if (conversation == null) {
                        conversation = JSONArray()
                        val prompt = JSONObject()
                        try {
                            prompt.put("role", "system")
                            prompt.put("content", getString(R.string.dev_catgirl_prompt))
                        } catch (e: JSONException) {
                            e.printStackTrace()
                        }
                        conversation!!.put(prompt)
                        runOnUiThread { input_link.setText(SharedPreferencesUtil.getString("dev_catgirl_apikey", "")) }
                        return@run
                    }

                    val api_key = input_link.text.toString()
                    if (api_key.isEmpty()) {
                        MsgUtil.showMsg("请在链接栏填写API KEY！")
                        return@run
                    } else {
                        SharedPreferencesUtil.putString("dev_catgirl_apikey", api_key)
                    }
                    val deepseekHeaders = ArrayList<String>().apply {
                        add("Content-Type")
                        add("application/json")
                        add("Authorization")
                        add("Bearer $api_key")
                        add("Accept")
                        add("text/event-stream")
                    }

                    val input_str = input_data.text.toString()
                    if (input_str.isEmpty()) {
                        MsgUtil.showMsg("请在POST数据栏填写文字！")
                        return@run
                    }
                    val input_json = JSONObject()
                    input_json.put("role", "user")
                    input_json.put("content", input_str)
                    conversation!!.put(input_json)

                    val requestJson = JSONObject()
                    val model = if (sw_wbi.isChecked) "reasoner" else "chat"
                    requestJson.put("model", "deepseek-$model")
                    requestJson.put("stream", true)
                    requestJson.put("messages", conversation)

                    MsgUtil.showMsg("发出请求，请等待回应！")
                    runOnUiThread {
                        btn_crash.isEnabled = false
                        output.setText("")
                        input_link.clearFocus()
                        input_data.clearFocus()
                        output.clearFocus()
                        input_link.isEnabled = false
                        input_data.isEnabled = false
                        output.isEnabled = false
                    }

                    val response = NetWorkUtil.postJson("https://api.deepseek.com/chat/completions",
                        requestJson.toString(),
                        deepseekHeaders)
                    val body = response.body ?: return@run
                    val source = body.source()

                    MsgUtil.showMsg("得到响应，请继续等待！")

                    var reasoning = sw_wbi.isChecked

                    val contentBuilder = StringBuilder()

                    while (!source.exhausted()) {
                        val line = source.readUtf8Line() ?: break
                        Log.d("debug-deepseek", line)

                        if (line.startsWith("data:")) {
                            val jsonData = line.substring(6).trim()
                            if ("[DONE]" == jsonData) break

                            val data = JSONObject(jsonData)
                            val choices = data.getJSONArray("choices")
                            val delta = choices.getJSONObject(0).getJSONObject("delta")

                            val deltaContent: String
                            if (!delta.isNull("reasoning_content")) {
                                deltaContent = delta.optString("reasoning_content")
                            } else if (!delta.isNull("content")) {
                                if (reasoning) {
                                    reasoning = false
                                    runOnUiThread { output.append("\n\n*思考结束*\n\n") }
                                }
                                deltaContent = delta.optString("content")
                            } else deltaContent = ""

                            if (!reasoning) contentBuilder.append(deltaContent)
                            runOnUiThread { output.append(deltaContent) }
                        }
                    }

                    response.close()

                    val output_str = contentBuilder.toString()
                    if (output_str.isNotEmpty()) {
                        val output_json = JSONObject()
                        output_json.put("role", "assistant")
                        output_json.put("content", output_str)
                        conversation!!.put(output_json)
                    }

                    MsgUtil.showMsg("响应结束，请查看下方文本框！")
                } catch (e: Exception) {
                    report(e)
                }

                runOnUiThread {
                    btn_crash.isEnabled = true
                    output.isEnabled = true
                    input_link.isEnabled = true
                    input_data.isEnabled = true
                }
            }
        }
    }

    override fun onDestroy() {
        if (conversation == null)
            SharedPreferencesUtil.putString("dev_test_link", input_link.text.toString())
        super.onDestroy()
    }
}