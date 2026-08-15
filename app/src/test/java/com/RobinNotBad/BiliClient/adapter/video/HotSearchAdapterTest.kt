package com.RobinNotBad.BiliClient.adapter.video

import org.junit.Assert.assertEquals
import org.junit.Test

class HotSearchAdapterTest {

    @Test
    fun formatHeat_belowTenThousand_returnsPlainNumber() {
        assertEquals("9999", HotSearchAdapter.formatHeat(9999L))
        assertEquals("0", HotSearchAdapter.formatHeat(0L))
    }

    @Test
    fun formatHeat_atLeastTenThousand_returnsTenThousandUnit() {
        assertEquals("1.0万", HotSearchAdapter.formatHeat(10000L))
        assertEquals("1.2万", HotSearchAdapter.formatHeat(12345L))
        assertEquals("1000.0万", HotSearchAdapter.formatHeat(9999999L))
    }
}
