package com.markmusic.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassRenderingPolicyTest {
    @Test fun informationalCardsNeverSubscribeToSensorParallax() {
        assertFalse(GlassRenderingPolicy.usesParallax(hasClickAction = false))
        assertTrue(GlassRenderingPolicy.usesParallax(hasClickAction = true))
    }

    @Test fun passivePanelsUseShallowShadowButInteractiveTilesRetainFeedback() {
        assertEquals(5f, GlassRenderingPolicy.elevationDp(false, false, 1f), 0.001f)
        assertEquals(7f, GlassRenderingPolicy.elevationDp(false, false, 2f), 0.001f)
        assertEquals(15f, GlassRenderingPolicy.elevationDp(true, false, 1f), 0.001f)
        assertEquals(3f, GlassRenderingPolicy.elevationDp(true, true, 1f), 0.001f)
    }
}
