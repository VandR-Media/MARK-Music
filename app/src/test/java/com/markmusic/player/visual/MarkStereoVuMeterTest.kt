package com.markmusic.player.visual

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkStereoVuMeterTest {
    @Test fun needleIsBoundedAndMonotonic() {
        assertEquals(-132f, vuNeedleAngle(0f), 0.0001f)
        assertEquals(-87f, vuNeedleAngle(.5f), 0.0001f)
        assertEquals(-42f, vuNeedleAngle(1f), 0.0001f)
        assertEquals(-132f, vuNeedleAngle(-42f), 0.0001f)
        assertEquals(-42f, vuNeedleAngle(4f), 0.0001f)
        assertEquals(-132f, vuNeedleAngle(Float.NaN), 0.0001f)
    }
}
