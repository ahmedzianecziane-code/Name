package com.example

import com.example.filter.DlssColorCalibration
import com.example.filter.DlssPreset
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun zeroIntensity_returnsTransparentIdentity() {
        val params = DlssColorCalibration.computeParams(DlssPreset.BALANCED, 0)
        assertEquals(0f, params.curveFactor, 0.0001f)
        assertEquals(0f, params.baseTintAlpha, 0.0001f)
        assertEquals(0f, params.shadowVignetteAlpha, 0.0001f)
        assertEquals(0f, params.specularGlowAlpha, 0.0001f)
        assertArrayEquals(
            floatArrayOf(
                1f, 0f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f, 0f,
                0f, 0f, 1f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            ),
            params.colorMatrixValues,
            0.0001f
        )
    }

    @Test
    fun intensityCurve_increasesMonotonicallyWithoutWashingOut() {
        val low = DlssColorCalibration.computeParams(DlssPreset.CINEMATIC, 20)
        val mid = DlssColorCalibration.computeParams(DlssPreset.CINEMATIC, 60)
        val max = DlssColorCalibration.computeParams(DlssPreset.CINEMATIC, 100)

        assertTrue(low.curveFactor < mid.curveFactor && mid.curveFactor < max.curveFactor)
        assertTrue(max.baseTintAlpha < 0.15f)
        assertTrue(max.shadowVignetteAlpha < 0.30f)
    }

    @Test
    fun presets_cycleCleanly() {
        assertEquals(DlssPreset.CINEMATIC, DlssPreset.BALANCED.next())
        assertEquals(DlssPreset.COMPETITIVE, DlssPreset.CINEMATIC.next())
        assertEquals(DlssPreset.BALANCED, DlssPreset.COMPETITIVE.next())
    }
}
