package com.RobinNotBad.BiliClient.api

import com.RobinNotBad.BiliClient.model.UserInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 性别 / IP 属地展示的纯函数单测（批次 1「社交与信息」）。
 *
 * <p>三条规则必须有人钉住：
 * 1）「保密」与空串都不显示（这是用户隐私设置的直接映射，不能当成一种性别展示）；
 * 2）只有一项时不留分隔符、不留前缀；
 * 3）两项都没有时返回空串，让调用方把整行 GONE。
 */
class UserInfoDisplayTest {

    // 参数名刻意不叫 sex/ipLocation：apply 里同名字段会优先解析成接收者属性，
    // 写成 this.sex = sex 会退化成自赋值，测试就永远绿得莫名其妙。
    private fun user(sexValue: String, ipValue: String): UserInfo = UserInfo().apply {
        sex = sexValue
        ipLocation = ipValue
    }

    @Test
    fun displaySex_hidesPrivacyAndBlank() {
        assertEquals("", user("保密", "").displaySex())
        assertEquals("", user("未知", "").displaySex())
        assertEquals("", user("", "").displaySex())
        assertEquals("", user("   ", "").displaySex())
    }

    @Test
    fun displaySex_keepsRealValueAndTrims() {
        assertEquals("男", user("男", "").displaySex())
        assertEquals("女", user("女", "").displaySex())
        assertEquals("男", user(" 男 ", "").displaySex())
    }

    @Test
    fun displayIpLocation_trimsAndKeepsEmpty() {
        assertEquals("上海", user("", "上海").displayIpLocation())
        assertEquals("上海", user("", " 上海 ").displayIpLocation())
        assertEquals("", user("", "").displayIpLocation())
        assertEquals("", user("", "   ").displayIpLocation())
    }

    @Test
    fun hasGenderIpMeta_trueWhenAnyPresent() {
        assertTrue(user("男", "").hasGenderIpMeta())
        assertTrue(user("", "上海").hasGenderIpMeta())
        assertTrue(user("女", "北京").hasGenderIpMeta())
        assertFalse(user("保密", "").hasGenderIpMeta())
        assertFalse(user("", "").hasGenderIpMeta())
    }

    @Test
    fun buildGenderIpLine_bothPresentJoinedBySeparator() {
        val line = UserInfoApi.buildGenderIpLine(user("男", "上海"), SEX_FORMAT, IP_FORMAT, SEPARATOR)
        assertEquals("性别：男 · IP属地：上海", line)
    }

    @Test
    fun buildGenderIpLine_singlePresentHasNoLeadingSeparator() {
        assertEquals(
            "性别：男",
            UserInfoApi.buildGenderIpLine(user("男", ""), SEX_FORMAT, IP_FORMAT, SEPARATOR)
        )
        // 性别保密时只留 IP 属地，且不能以分隔符开头
        assertEquals(
            "IP属地：上海",
            UserInfoApi.buildGenderIpLine(user("保密", "上海"), SEX_FORMAT, IP_FORMAT, SEPARATOR)
        )
    }

    @Test
    fun buildGenderIpLine_emptyWhenNothingToShow() {
        assertEquals("", UserInfoApi.buildGenderIpLine(user("保密", ""), SEX_FORMAT, IP_FORMAT, SEPARATOR))
        assertEquals("", UserInfoApi.buildGenderIpLine(user("", ""), SEX_FORMAT, IP_FORMAT, SEPARATOR))
        // 调用方拿到 null（用户信息还没加载出来）时也不能崩
        assertEquals("", UserInfoApi.buildGenderIpLine(null, SEX_FORMAT, IP_FORMAT, SEPARATOR))
    }

    private companion object {
        // 与 res/values/social_port_strings.xml 里的模板保持一致
        const val SEX_FORMAT = "性别：%1\$s"
        const val IP_FORMAT = "IP属地：%1\$s"
        const val SEPARATOR = " · "
    }
}
