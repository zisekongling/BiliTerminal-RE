package com.RobinNotBad.BiliClient.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 图床（upload_bfs）失败文案的单测。
 *
 * 这类映射的价值不在"好看"，而在**用户报障时能对上原因**：图床失败只回一个错误码，
 * 26.11.02 之前界面统一显示「图片上传失败」，看不出是没登录、凭证失效，还是图片本身被拒。
 */
class ApiErrorTextTest {

    @Test
    fun uploadFailed_notLoggedInSaysLogin() {
        // -101 = 账号未登录：用户要做的是去登录，而不是换图重试
        assertTrue(
            "未登录必须直接说没登录：${ApiErrorText.uploadFailed(-101)}",
            ApiErrorText.uploadFailed(-101).contains("登录")
        )
    }

    @Test
    fun uploadFailed_tokenProblemIsNotLoginProblem() {
        // -111 / -102 与 -101 的处置完全不同（重新登录 / 账号被封），不能给同一句
        assertFalse(
            "-111 不能和 -101 显示同一句，否则用户无从判断",
            ApiErrorText.uploadFailed(-111) == ApiErrorText.uploadFailed(-101)
        )
        assertTrue(
            "凭证失效要说是凭证问题：${ApiErrorText.uploadFailed(-111)}",
            ApiErrorText.uploadFailed(-111).contains("凭证") ||
                    ApiErrorText.uploadFailed(-111).contains("登录")
        )
    }

    @Test
    fun uploadFailed_rejectedImageTellsUserToSwapImage() {
        // 4100001 是图床对参数/格式不满时的码（本地 bilibili-API 快照未收录，按 4xx 参数错误语义处理）
        val msg = ApiErrorText.uploadFailed(4100001)
        assertTrue("被服务端拒绝的图要提示换一张：$msg", msg.contains("换"))
        assertTrue("并且要说明是图片不被接受：$msg", msg.contains("图片"))
    }

    @Test
    fun uploadFailed_rateLimitedIsExplicit() {
        assertTrue(
            "-509 要提示稍后再试，而不是让用户反复点：${ApiErrorText.uploadFailed(-509)}",
            ApiErrorText.uploadFailed(-509).contains("频繁")
        )
    }

    @Test
    fun uploadFailed_unknownCodeKeepsTheNumber() {
        // 没见过的码必须原样带上：否则用户只能描述「失败了」，无法定位
        val msg = ApiErrorText.uploadFailed(99999)
        assertTrue("兜底文案要带错误码：$msg", msg.contains("99999"))
        assertEquals("图片上传失败（错误码 99999）", msg)
    }
}
