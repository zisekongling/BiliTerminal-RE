package com.RobinNotBad.BiliClient.util

import org.json.JSONException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketTimeoutException

/**
 * 「我的」页加载失败归类与文案的纯函数单测（口径见 `util/MySpaceLoadErrorText.kt`）。
 *
 * 这里守的是反馈 #163 里最容易静默出错的几条：
 * 接口「没给数据」是**不抛异常**的（`UserInfoApi` 返回 mid=0 的占位对象），必须和网络异常分开，
 * 否则掉登录的人看到的还是「网络异常」这种没用的提示；反过来，超时/连接失败都算网络问题，
 * 别被归成「未知错误」。文案侧只断言「四类各有各的说法」，具体措辞改 strings.xml 不影响这里。
 */
class MySpaceLoadErrorTextTest {

    @Test
    fun reasonOf_withoutException_isNoData() {
        assertEquals(
            "接口没抛异常＝没拿到账号数据（未登录 / code≠0 / 风控降级），要提示重新登录",
            MySpaceLoadErrorText.Reason.NO_DATA,
            MySpaceLoadErrorText.reasonOf(null)
        )
    }

    @Test
    fun reasonOf_networkFailures() {
        assertEquals(MySpaceLoadErrorText.Reason.NETWORK, MySpaceLoadErrorText.reasonOf(IOException("io")))
        assertEquals(
            MySpaceLoadErrorText.Reason.NETWORK,
            MySpaceLoadErrorText.reasonOf(SocketTimeoutException("timeout"))
        )
        assertEquals(MySpaceLoadErrorText.Reason.NETWORK, MySpaceLoadErrorText.reasonOf(ConnectException("refused")))
        assertEquals(
            MySpaceLoadErrorText.Reason.NETWORK,
            MySpaceLoadErrorText.reasonOf(InterruptedIOException("interrupted"))
        )
    }

    @Test
    fun reasonOf_parseFailure() {
        assertEquals(
            MySpaceLoadErrorText.Reason.PARSE,
            MySpaceLoadErrorText.reasonOf(JSONException("unexpected token"))
        )
    }

    @Test
    fun reasonOf_otherExceptions_areUnknown() {
        assertEquals(
            MySpaceLoadErrorText.Reason.UNKNOWN,
            MySpaceLoadErrorText.reasonOf(IllegalStateException("boom"))
        )
        assertEquals(
            "RuntimeException 不是 IO 也不是 JSON，别硬塞进网络类",
            MySpaceLoadErrorText.Reason.UNKNOWN,
            MySpaceLoadErrorText.reasonOf(NullPointerException())
        )
    }

    @Test
    fun reasonOf_wrappedException_followsTheOuterOne() {
        // 外层是 IO 就是网络问题：包装进去的 JSONException 不该把「连不上」说成「解析失败」
        assertEquals(
            MySpaceLoadErrorText.Reason.NETWORK,
            MySpaceLoadErrorText.reasonOf(IOException("io", JSONException("inner")))
        )
    }

    @Test
    fun textOf_eachReasonHasItsOwnText() {
        val texts = MySpaceLoadErrorText.Reason.entries.map { MySpaceLoadErrorText.textOf(it) }
        assertEquals("四类原因要各有一句，不能有空的", 4, texts.size)
        texts.forEach { assertTrue("原因文案不能为空", it.isNotBlank()) }
        assertEquals("四类原因的文案必须互不相同", 4, texts.toSet().size)
    }

    @Test
    fun textOf_noData_tellsUserToLogInAgain() {
        // 反馈 #163 的场景：登录一段时间后自动掉登录，这时提示里必须出现「登录」，
        // 不能只是「网络异常」——用户会一直去开关 Wi-Fi。
        val text = MySpaceLoadErrorText.textOf(MySpaceLoadErrorText.Reason.NO_DATA)
        assertTrue("没拿到账号数据时要提示登录相关，实际是：$text", text.contains("登录"))
        assertNotEquals(
            "别和网络异常说成同一句",
            MySpaceLoadErrorText.textOf(MySpaceLoadErrorText.Reason.NETWORK),
            text
        )
    }
}
