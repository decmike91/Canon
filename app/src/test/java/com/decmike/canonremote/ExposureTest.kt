package com.decmike.canonremote

import com.decmike.canonremote.ccapi.Exposure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExposureTest {
    private val tvList = listOf("30\"", "15\"", "8\"", "4\"", "2\"", "1\"", "0\"5", "1/4", "1/8", "1/15", "1/30", "1/60",
        "1/80", "1/100", "1/125", "1/160", "1/200", "1/250", "1/500", "1/1000", "bulb")
    private val compList = listOf("-3", "-2_2/3", "-2_1/3", "-2", "-1_2/3", "-1_1/3", "-1", "-0_2/3", "-0_1/3", "0",
        "+0_1/3", "+0_2/3", "+1", "+1_1/3", "+1_2/3", "+2", "+2_1/3", "+2_2/3", "+3")

    @Test fun parsesShutterSpeeds() {
        assertEquals(1 / 125.0, Exposure.tvSeconds("1/125")!!, 1e-9)
        assertEquals(0.5, Exposure.tvSeconds("0\"5")!!, 1e-9)
        assertEquals(1.3, Exposure.tvSeconds("1\"3")!!, 1e-9)
        assertEquals(30.0, Exposure.tvSeconds("30\"")!!, 1e-9)
        assertNull(Exposure.tvSeconds("bulb"))
    }

    @Test fun parsesCompensation() {
        assertEquals(0.0, Exposure.compensationEv("0")!!, 1e-9)
        assertEquals(-2.0 - 2 / 3.0, Exposure.compensationEv("-2_2/3")!!, 1e-9)
        assertEquals(1.0 / 3, Exposure.compensationEv("+0_1/3")!!, 1e-9)
        assertEquals(1.5, Exposure.compensationEv("+1_1/2")!!, 1e-9)
    }

    @Test fun shiftsShutter() {
        assertEquals("1/30", Exposure.shiftTv("1/125", 2.0, tvList))
        assertEquals("1/500", Exposure.shiftTv("1/125", -2.0, tvList))
        assertEquals("1/60", Exposure.shiftTv("1/125", 1.0, tvList))
    }

    @Test fun closestValues() {
        assertEquals("+1", Exposure.closestCompensation(1.0, compList))
        assertEquals("-1_2/3", Exposure.closestCompensation(-1.7, compList))
        assertEquals("15\"", Exposure.closestTv(15.0, tvList))
    }

    @Test fun bracket() {
        assertEquals(listOf(-2.0, -1.0, 0.0, 1.0, 2.0), Exposure.bracketOffsets(5, 1.0))
        assertEquals(listOf(-0.667, 0.0, 0.667), Exposure.bracketOffsets(3, 0.667))
    }

    @Test fun formats() {
        assertEquals("1 min 15 s", Exposure.formatDuration(75.0))
        assertEquals("1/250 s", Exposure.formatDuration(0.004))
        assertEquals("0,5″", Exposure.pretty("tv", "0\"5"))
        assertEquals("f/5.6", Exposure.pretty("av", "f5.6"))
    }
}
