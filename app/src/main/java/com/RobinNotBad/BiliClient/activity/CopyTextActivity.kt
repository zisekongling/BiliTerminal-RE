package com.RobinNotBad.BiliClient.activity

import com.RobinNotBad.BiliClient.util.UiText

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.BackgroundColorSpan
import android.widget.EditText
import androidx.core.content.ContextCompat
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.base.BaseActivity
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.StringUtil

class CopyTextActivity : BaseActivity() {
    private var content: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_copy)

        val intent = intent

        content = intent.getStringExtra("content") ?: ""

        if (content.isEmpty()) {
            finish()
            return
        }

        val edittext = findViewById<EditText>(R.id.content)
        edittext.setText(content)

        val beginEdit = findViewById<EditText>(R.id.begin_index)
        val endEdit = findViewById<EditText>(R.id.end_index)

        // 系统只在正文自己拿到焦点时才画选区高亮；而精确调整要往下面两个输入框里打字，
        // 焦点必然离开正文，选区就看不见了（反馈 #158）。所以这里按起止下标自己给选中
        // 区间铺一层主题的选区底色，焦点在不在正文都能看见选了哪一段。
        val highlightSpan = BackgroundColorSpan(
            ContextCompat.getColor(this, R.color.text_selection_highlight)
        )

        fun syncSelectionHighlight() {
            val text = edittext.text ?: return
            text.removeSpan(highlightSpan)
            // 焦点在正文时系统自己会画选区，别再叠一层
            if (edittext.hasFocus()) return
            val start = beginEdit.text.toString().toIntOrNull() ?: return
            val end = endEdit.text.toString().toIntOrNull() ?: return
            if (start < 0 || end <= start || end > text.length) return
            text.setSpan(highlightSpan, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        edittext.onFocusChangeListener = android.view.View.OnFocusChangeListener { _, b ->
            if (!b) {
                beginEdit.setText(edittext.selectionStart.toString())
                endEdit.setText(edittext.selectionEnd.toString())
            } else {
                // 正文重新拿到焦点时交还给系统原生选区，免得两层高亮同时出现
                edittext.text?.removeSpan(highlightSpan)
            }
        }


        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {
            }

            override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {
                try {
                    edittext.setSelection(
                        beginEdit.text.toString().toInt(),
                        endEdit.text.toString().toInt()
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                syncSelectionHighlight()
            }

            override fun afterTextChanged(editable: Editable) {
            }
        }
        beginEdit.addTextChangedListener(textWatcher)
        endEdit.addTextChangedListener(textWatcher)

        findViewById<android.view.View>(R.id.copy_all).setOnClickListener {
            StringUtil.copyText(this, content)
            MsgUtil.showMsg(UiText.of("已复制"))
        }
        findViewById<android.view.View>(R.id.copy).setOnClickListener {
            try {
                StringUtil.copyText(
                    this, content.substring(
                        beginEdit.text.toString().toInt(),
                        endEdit.text.toString().toInt()
                    )
                )
                MsgUtil.showMsg(UiText.of("已复制"))
            } catch (e: Exception) {
                MsgUtil.showMsg(UiText.of("复制失败，请检查选择的范围"))
            }
        }

        findViewById<android.view.View>(R.id.begin_left).setOnClickListener {
            try {
                if (beginEdit.text.toString().toInt() - 1 < 0)
                    beginEdit.setText("0")
                else
                    beginEdit.setText((beginEdit.text.toString().toInt() - 1).toString())
            } catch (e: Exception) {
                beginEdit.setText("0")
            }
        }
        findViewById<android.view.View>(R.id.begin_right).setOnClickListener {
            try {
                if (beginEdit.text.toString().toInt() + 1 > edittext.text.length)
                    beginEdit.setText(edittext.text.length.toString())
                else
                    beginEdit.setText((beginEdit.text.toString().toInt() + 1).toString())
            } catch (e: Exception) {
                beginEdit.setText("0")
            }
        }
        findViewById<android.view.View>(R.id.end_left).setOnClickListener {
            try {
                if (endEdit.text.toString().toInt() - 1 < 0)
                    endEdit.setText("0")
                else
                    endEdit.setText((endEdit.text.toString().toInt() - 1).toString())
            } catch (e: Exception) {
                endEdit.setText("0")
            }
        }
        findViewById<android.view.View>(R.id.end_right).setOnClickListener {
            try {
                if (endEdit.text.toString().toInt() + 1 > edittext.text.length)
                    endEdit.setText(edittext.text.length.toString())
                else
                    endEdit.setText((endEdit.text.toString().toInt() + 1).toString())
            } catch (e: Exception) {
                endEdit.setText("0")
            }
        }
        findViewById<android.view.View>(R.id.begin_left).setOnLongClickListener {
            beginEdit.setText("0")
            false
        }
        findViewById<android.view.View>(R.id.begin_right).setOnLongClickListener {
            beginEdit.setText(edittext.text.length.toString())
            false
        }
        findViewById<android.view.View>(R.id.end_left).setOnLongClickListener {
            endEdit.setText("0")
            false
        }
        findViewById<android.view.View>(R.id.end_right).setOnLongClickListener {
            endEdit.setText(edittext.text.length.toString())
            false
        }
    }
}