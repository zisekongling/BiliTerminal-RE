package com.RobinNotBad.BiliClient.util;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class StringUtilTest {

    @Test
    public void getTextHeightWithSize_firstCall_doesNotUnboxNull() {
        Throwable caught = null;
        try {
            StringUtil.getTextHeightWithSize(null);
        } catch (Throwable t) {
            caught = t;
        }
        assertFalse("首次调用触发 NullPointerException：" + caught, caught instanceof NullPointerException);
        assertTrue(caught == null || caught instanceof RuntimeException);
    }
}
