package com.RobinNotBad.BiliClient.activity

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.base.BaseActivity

class ListDialogActivity : BaseActivity() {

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_list_dialog)

        val intent = intent
        findViewById<TextView>(R.id.tip_title).text = intent.getStringExtra("title")

        val items = intent.getStringArrayListExtra("items") ?: ArrayList()
        val listView = findViewById<ListView>(R.id.listView)

        listView.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, items)
        listView.setOnItemClickListener { _, _, position, _ ->
            val result = Intent()
            result.putExtra("selected_position", position)
            setResult(RESULT_OK, result)
            finish()
        }

        findViewById<TextView>(R.id.tip_title).setOnClickListener {
            setResult(RESULT_CANCELED)
            finish()
        }
    }

    override fun onBackPressed() {
        setResult(RESULT_CANCELED)
        super.onBackPressed()
    }
}