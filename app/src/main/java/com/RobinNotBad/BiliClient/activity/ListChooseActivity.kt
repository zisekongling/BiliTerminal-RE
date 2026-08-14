package com.RobinNotBad.BiliClient.activity

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.base.BaseActivity
import com.RobinNotBad.BiliClient.listener.OnItemClickListener
import com.RobinNotBad.BiliClient.listener.OnItemLongClickListener
import com.RobinNotBad.BiliClient.ui.widget.recycler.CustomLinearManager

class ListChooseActivity : BaseActivity() {

    private lateinit var displayNames: List<String>
    private var actualValues: List<String>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_simple_list)
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        findViewById<View>(R.id.top).setOnClickListener {
            setResult(RESULT_CANCELED)
            finish()
        }

        val intent = intent
        if (intent.getStringExtra("title") != null) {
            (findViewById<TextView>(R.id.pageName)).text = intent.getStringExtra("title")
        } else {
            (findViewById<TextView>(R.id.pageName)).text = "请选择"
        }
        if (intent.getSerializableExtra("items") == null) {
            finish()
        } else {
            @Suppress("UNCHECKED_CAST")
            this.displayNames = intent.getSerializableExtra("items") as List<String>
        }
        
        @Suppress("UNCHECKED_CAST")
        this.actualValues = intent.getSerializableExtra("values") as List<String>?

        val adapter = Adapter(this)
        adapter.setNameList(displayNames)

        val originalPosition = intent.getIntExtra("position", -1)
        adapter.setOnItemClickListener { position ->
            val resultIntent = Intent()
            resultIntent.putExtra("item", displayNames[position])
            resultIntent.putExtra("value", if (actualValues != null && position < actualValues!!.size) actualValues!![position] else displayNames[position])
            resultIntent.putExtra("position", originalPosition)
            setResult(RESULT_OK, resultIntent)
            finish()
        }

        recyclerView.layoutManager = CustomLinearManager(this)
        recyclerView.adapter = adapter
    }

    class Adapter(private val context: Context) : RecyclerView.Adapter<com.RobinNotBad.BiliClient.adapter.QualityChooseAdapter.Holder>() {

        private var nameList: List<String> = ArrayList()

        private var onItemClickListener: OnItemClickListener? = null
        private var onItemLongClickListener: OnItemLongClickListener? = null

        fun setOnItemClickListener(listener: OnItemClickListener?) {
            this.onItemClickListener = listener
        }

        @SuppressLint("NotifyDataSetChanged")
        fun setNameList(newList: List<String>) {
            this.nameList = newList
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): com.RobinNotBad.BiliClient.adapter.QualityChooseAdapter.Holder {
            val view = LayoutInflater.from(context).inflate(R.layout.cell_choose, parent, false)
            return com.RobinNotBad.BiliClient.adapter.QualityChooseAdapter.Holder(view)
        }

        override fun onBindViewHolder(holder: com.RobinNotBad.BiliClient.adapter.QualityChooseAdapter.Holder, position: Int) {
            holder.folder_name.text = nameList[position]

            holder.itemView.setOnClickListener {
                onItemClickListener?.onItemClick(position)
            }

            holder.itemView.setOnLongClickListener { view ->
                if (onItemLongClickListener != null) {
                    onItemLongClickListener!!.onItemLongClick(position)
                    true
                } else false
            }
        }

        override fun getItemCount(): Int {
            return nameList.size
        }
    }
}