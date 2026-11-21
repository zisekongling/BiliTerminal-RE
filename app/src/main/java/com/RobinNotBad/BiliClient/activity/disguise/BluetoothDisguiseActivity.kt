package com.RobinNotBad.BiliClient.activity.disguise

import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.util.DisguisePrefs
import com.RobinNotBad.BiliClient.util.Logu

/**
 * 伪装成「蓝牙已断开」的系统提示（issue #22 的第二种样式，双击顶栏触发）。
 *
 * <h3>为什么是"假系统提示"</h3>
 * 相比计算器，它更省事也更容易被当成"正在处理耳机连接"而放过。窗口是**半透明**的
 * （`Theme.BiliClient.Disguise.Dialog`），底下的页面会透出来再压一层暗色，
 * 观感就是「系统提示框盖在当前画面上」。
 *
 * <h3>为什么清单里没有 android:screenOrientation（崩溃 #370）</h3>
 * 这一页必须是**半透明**窗口（`Theme.BiliClient.Disguise.Dialog`），而 Android 8.0/8.1 有一条校验：
 * 声明了固定方向的 activity 必须同时是「全屏不透明」的窗口，否则系统在 framework 的
 * `Activity.onCreate` 里（即下面 [onCreate] 的 `super.onCreate` 那一行）直接抛
 * `IllegalStateException: Only fullscreen opaque activities can request orientation`。
 * 所以 `AndroidManifest.xml` 里这一条**故意不写** `android:screenOrientation`，
 * 完整取舍（为什么不改主题、为什么不受「私有 Activity 必须 locked」那条约束）见清单里的注释。
 *
 * 去掉方向不影响本页布局：`activity_disguise_bluetooth.xml` 是 match_parent 的遮罩 + 居中的
 * wrap_content 卡片，圆屏留白由 [applyDisguiseRoundPadding] 按屏幕短边算，与方向无关。
 *
 * <h3>所有交互都是假的</h3>
 * 「重新连接」只把正文换成"正在重新连接…"，1.5 秒后换回「连接失败」；「取消」什么也不做。
 * 不做任何真实蓝牙操作——本应用根本没有蓝牙权限，也不能因为伪装去申请。
 *
 * <h3>退出</h3>
 * 这一款没有数字键盘，所以**长按标题 2 秒**退出（见 [setupExitGesture]）。
 * 短按标题毫无反应，旁观者不容易试出来。返回键同样被吃掉（[onBackPressed]）。
 */
class BluetoothDisguiseActivity : AppCompatActivity() {

    private lateinit var body: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_disguise_bluetooth)

        body = findViewById(R.id.disguise_bt_body)
        applyDisguiseRoundPadding(findViewById(R.id.disguise_bt_root))

        findViewById<TextView>(R.id.disguise_bt_retry).setOnClickListener {
            // 假的：只换一行字，1.5 秒后回到原样
            body.text = getString(R.string.text_disguise_bt_retrying)
            body.postDelayed({
                if (!isFinishing && !isDestroyed) body.text = getString(R.string.text_disguise_bt_failed)
            }, 1_500L)
        }
        findViewById<TextView>(R.id.disguise_bt_cancel).setOnClickListener {
            // 假的：「取消」本来就不该关掉系统提示，这里保持不动
        }

        setupExitGesture()
    }

    /**
     * 长按标题 2 秒退出伪装。
     *
     * 用 `postDelayed` 而不是系统的 `setOnLongClickListener`：手表上很多 ROM 的长按延迟被调过，
     * 这里要的是"够久、试不出来"，所以自己计时，并在 `ACTION_UP` / `ACTION_CANCEL` 时取消。
     *
     * **`ACTION_DOWN` 必须返回 true**：返回 false 时这个 View 没有成为触摸目标，
     * 系统不再给它派发后续的 `UP` / `CANCEL`，`removeCallbacks` 永远执行不到 ——
     * 于是轻点一下标题、什么也不做，2 秒后窗口自己 `finish()`，伪装直接失效。
     * 标题本身没有点击行为，消费掉这些事件不影响任何东西。
     */
    private fun setupExitGesture() {
        val title = findViewById<TextView>(R.id.disguise_bt_title)
        val exit = Runnable {
            if (isFinishing || isDestroyed) return@Runnable
            Logu.d("disguise", "长按标题，退出伪装")
            finish()
        }
        title.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> view.postDelayed(exit, HOLD_TO_EXIT_MS)
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> view.removeCallbacks(exit)
            }
            true
        }
    }

    /** 返回键被吃掉：伪装页必须只能靠长按标题退出。 */
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        // 故意什么都不做
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) return true
        return super.onKeyDown(keyCode, event)
    }

    private companion object {
        /** 长按多久才退出伪装（毫秒）。短按与 1 秒内的长按都不该露馅。 */
        const val HOLD_TO_EXIT_MS = 2_000L
    }
}
