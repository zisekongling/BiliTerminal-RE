package com.RobinNotBad.BiliClient.helper

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.XmlResourceParser
import android.graphics.Color
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.viewpager.widget.ViewPager
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.TutorialActivity
import com.RobinNotBad.BiliClient.model.CustomText
import com.RobinNotBad.BiliClient.model.Tutorial
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import org.xmlpull.v1.XmlPullParser

class TutorialHelper {
    companion object {
        @JvmStatic
        fun loadTutorial(xml: XmlResourceParser): Tutorial? {
            try {
                xml.next()
                var eventType = xml.eventType

                var isInName = false
                var isInDescrption = false
                var isInType = false
                var isInImg = false
                var isInContentItem = false
                var isInContentItemType = false
                var isInContentItemText = false
                var isInContentItemStyle = false
                var isInContentItemColor = false

                val turtorial = Tutorial()
                val content: MutableList<CustomText> = ArrayList()
                var item: CustomText? = null

                while (eventType != XmlPullParser.END_DOCUMENT) {
                    if (eventType == XmlPullParser.START_TAG) {
                        when (xml.name) {
                            "name" -> isInName = true
                            "description" -> isInDescrption = true
                            "img" -> isInImg = true
                            "type" -> if (isInContentItem) isInContentItemType = true else isInType = true
                            "item" -> {
                                isInContentItem = true
                                item = CustomText()
                            }
                            "text" -> isInContentItemText = true
                            "style" -> isInContentItemStyle = true
                            "color" -> isInContentItemColor = true
                        }
                    } else if (eventType == XmlPullParser.END_TAG) {
                        when (xml.name) {
                            "name" -> isInName = false
                            "description" -> isInDescrption = false
                            "img" -> isInImg = false
                            "type" -> if (isInContentItem) isInContentItemType = false else isInType = false
                            "item" -> {
                                isInContentItem = false
                                content.add(item!!)
                                item = null
                            }
                            "text" -> isInContentItemText = false
                            "style" -> isInContentItemStyle = false
                            "color" -> isInContentItemColor = false
                        }
                    } else if (eventType == XmlPullParser.TEXT) {
                        if (isInName) turtorial.name = xml.text
                        else if (isInDescrption) turtorial.description = xml.text
                        else if (isInImg) turtorial.imgid = xml.text
                        else if (isInType) turtorial.type = xml.text.toInt()
                        else if (item != null) {
                            if (isInContentItemType) item.type = xml.text.toInt()
                            else if (isInContentItemText) item.text = xml.text
                            else if (isInContentItemStyle) item.style = xml.text
                            else if (isInContentItemColor) item.color = xml.text
                        }
                    }

                    xml.next()
                    eventType = xml.eventType
                }
                turtorial.content = content
                return turtorial
            } catch (e: Exception) {
                e.printStackTrace()
                return null
            }
        }

        @JvmStatic
        fun loadText(texts: List<CustomText>): SpannableStringBuilder {
            val str = SpannableStringBuilder("")
            for (text in texts) {
                if (text.type == 0) {
                    val old_len = str.length
                    str.append(text.text)
                    str.setSpan(ForegroundColorSpan(Color.parseColor(text.color)), old_len, str.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    when (text.style) {
                        "bold" -> str.setSpan(StyleSpan(Typeface.BOLD), old_len, str.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        "italic" -> str.setSpan(StyleSpan(Typeface.ITALIC), old_len, str.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        "underline" -> str.setSpan(UnderlineSpan(), old_len, str.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        "strike" -> str.setSpan(StrikethroughSpan(), old_len, str.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                } else if (text.type == 1) str.append("\n")
            }
            return str
        }

        @JvmStatic
        fun show(xml_res_id: Int, context: Context, tutorial_tag: String, tutorial_version: Int) {
            if (SharedPreferencesUtil.getInt("tutorial_ver_$tutorial_tag", -1) < tutorial_version) {
                if (SharedPreferencesUtil.getInt("tutorial_ver_$tutorial_tag", 0) != 0)
                    Toast.makeText(context.applicationContext, "教程已更新", Toast.LENGTH_SHORT).show()
                val intent = Intent(context, TutorialActivity::class.java)
                intent.putExtra("xml_id", xml_res_id)
                intent.putExtra("tag", tutorial_tag)
                intent.putExtra("version", tutorial_version)
                context.startActivity(intent)
            }
        }

        @JvmStatic
        fun showTutorialList(context: Context, array_id: Int, tutorial_key: Int) {
            try {
                var n = context.resources.getStringArray(array_id).size
                for (i in 1..context.resources.getStringArray(array_id).size) {
                    val indentify = context.resources.getIdentifier(context.packageName + ":" + context.resources.getStringArray(array_id)[i - 1], null, null)
                    if (indentify > 0)
                        show(indentify, context, context.resources.getStringArray(R.array.tutorial_list)[tutorial_key], n--)
                }
            } catch (e: Exception) {
                MsgUtil.showMsg("加载教程时遇到问题")
                e.printStackTrace()
            }
        }

        @JvmStatic
        fun showPagerTutorial(activity: Activity, pagecount: Int) {
            activity.runOnUiThread {
                val pagename = activity.javaClass.simpleName
                val textView = activity.findViewById<TextView>(R.id.text_tutorial_pager)
                if (SharedPreferencesUtil.getBoolean("tutorial_pager_$pagename", true)) {
                    Log.d("debug-tutorial", pagename)
                    textView.visibility = View.VISIBLE
                    textView.text = activity.getString(R.string.tutorial_pager, pagecount)

                    val viewPager = activity.findViewById<View>(R.id.viewPager)
                    if (viewPager is ViewPager) {
                        (viewPager as ViewPager).addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
                            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
                                if (position != 0) textView.visibility = View.GONE
                            }

                            override fun onPageSelected(position: Int) {}

                            override fun onPageScrollStateChanged(state: Int) {}
                        })
                    }
                    SharedPreferencesUtil.putBoolean("tutorial_pager_$pagename", false)
                } else {
                    textView.visibility = View.GONE
                }
            }
        }
    }
}