package com.markmusic.player.visual

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkStereoVuMeterTest {
    @Test fun needleIsBoundedAndMonotonic() {
        assertEquals(-145f, vuNeedleAngle(0f), 0.0001f)
        assertEquals(-91.5f, vuNeedleAngle(.5f), 0.0001f)
        assertEquals(-38f, vuNeedleAngle(1f), 0.0001f)
        assertEquals(-145f, vuNeedleAngle(-42f), 0.0001f)
        assertEquals(-38f, vuNeedleAngle(4f), 0.0001f)
        assertEquals(-145f, vuNeedleAngle(Float.NaN), 0.0001f)
    }
}
