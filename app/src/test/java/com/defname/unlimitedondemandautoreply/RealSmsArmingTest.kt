package com.defname.unlimitedondemandautoreply

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RealSmsArmingTest {
    @Test
    fun realSmsArmRequiresFutureTimestamp() {
        val now = 1_000L

        assertFalse(isRealSmsArmed(now, 0L))
        assertFalse(isRealSmsArmed(now, now))
        assertFalse(isRealSmsArmed(now, now - 1L))
        assertTrue(isRealSmsArmed(now, now + 1L))
    }
}
