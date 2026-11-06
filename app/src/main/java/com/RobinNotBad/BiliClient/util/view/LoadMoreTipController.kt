package com.RobinNotBad.BiliClient.util.view

import android.view.View
import android.widget.TextView
import com.RobinNotBad.BiliClient.R

/**
 * 列表底部状态条（`@id/loadMoreTip`）的显示控制。
 *
 * 两个列表基类（`RefreshListActivity` / `RefreshMainActivity`）没有继承关系
 * （`BaseActivity` 里有向下类型判断的坑，见 `docs/architecture-map.md`），
 * 以前各写了一份一模一样的 `when` 分支和文案；这里用**组合**收成一份，
 * 避免「同一句话两处定义、改一处漏一处」。
 *
 * 策略（什么时候显示、什么时候自动收）在 [LoadMoreTipPolicy] 里，本类只做机械翻译。
 * 所有方法都要求**主线程**调用——基类统一走 `runOnUiThread`。
 */
class LoadMoreTipController(private val tip: TextView?) {

    /** 到点收起「没有更多了」。 */
    private val hideTask = Runnable {
        tip?.let {
            it.text = ""
            it.visibility = View.GONE
        }
    }

    /**
     * 按调用方的两个标志重画状态条。
     *
     * @param loading 正在加载下一页（`goOnLoad` 之后、加载回调之前为 true）
     * @param end     已到列表底部（各页自己维护的 `bottom`）
     */
    fun render(loading: Boolean, end: Boolean) {
        val view = tip ?: return
        // 任何一次重画都先取消上一次的自动收起，否则「刚显示就加载下一页」会被旧定时器提前收掉
        view.removeCallbacks(hideTask)

        val state = LoadMoreTipPolicy.stateOf(loading, end)
        if (!LoadMoreTipPolicy.isVisible(state)) {
            view.text = ""
            view.visibility = View.GONE
            return
        }

        view.setText(
            if (state == LoadMoreTipPolicy.State.END) R.string.text_list_no_more
            else R.string.text_list_loading
        )
        view.visibility = View.VISIBLE
        if (LoadMoreTipPolicy.autoHides(state)) {
            view.postDelayed(hideTask, LoadMoreTipPolicy.END_VISIBLE_MS)
        }
    }

    /**
     * 页面销毁时调用：撤掉还没到点的自动收起。
     *
     * 不撤也不崩（`postDelayed` 挂在主线程 Looper 上，2 秒后对着一个已经 detach 的
     * TextView 设 visibility 是安全的），但会让 Activity 被定时器多留 2 秒，没必要。
     */
    fun release() {
        tip?.removeCallbacks(hideTask)
    }
}
