package com.example.filter

import kotlin.math.pow

enum class DlssPreset(
    val id: String,
    val title: String,
    val subtitle: String,
    val shortLabel: String
) {
    BALANCED(
        id = "balanced",
        title = "Balanced",
        subtitle = "DLSS 5 Standard",
        shortLabel = "STD"
    ),
    CINEMATIC(
        id = "cinematic",
        title = "Cinematic Ray-Traced",
        subtitle = "Deep Shadows & Warm Highlights",
        shortLabel = "RTX"
    ),
    COMPETITIVE(
        id = "competitive",
        title = "Crisp Competitive",
        subtitle = "High Clarity & Vibrancy",
        shortLabel = "PRO"
    );

    fun next(): DlssPreset {
        val values = entries
        return values[(ordinal + 1) % values.size]
    }

    companion object {
        fun fromId(id: String?): DlssPreset =
            entries.firstOrNull { it.id == id } ?: BALANCED
    }
}

data class DlssFilterState(
    val isEnabled: Boolean = false,
    val intensity: Int = 65,
    val preset: DlssPreset = DlssPreset.BALANCED,
    val showFloatingBubble: Boolean = false,
    val isServiceRunning: Boolean = false
)

/**
 * Calibrated visual parameters derived from the DLSS 5 Neural Look curve.
 * - At 0%: curveFactor = 0f (100% transparent, original screen).
 * - At 1..100%: non-linear perceptual curve preserving white headroom and shadow detail.
 */
data class OverlayRenderParams(
    val curveFactor: Float,
    val baseTintColor: Int,
    val baseTintAlpha: Float,
    val shadowVignetteColor: Int,
    val shadowVignetteAlpha: Float,
    val specularGlowColor: Int,
    val specularGlowAlpha: Float,
    val colorMatrixValues: FloatArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is OverlayRenderParams) return false
        return curveFactor == other.curveFactor &&
            baseTintColor == other.baseTintColor &&
            baseTintAlpha == other.baseTintAlpha &&
            shadowVignetteColor == other.shadowVignetteColor &&
            shadowVignetteAlpha == other.shadowVignetteAlpha &&
            specularGlowColor == other.specularGlowColor &&
            specularGlowAlpha == other.specularGlowAlpha &&
            colorMatrixValues.contentEquals(other.colorMatrixValues)
    }

    override fun hashCode(): Int {
        var result = curveFactor.hashCode()
        result = 31 * result + baseTintColor
        result = 31 * result + baseTintAlpha.hashCode()
        result = 31 * result + shadowVignetteColor
        result = 31 * result + shadowVignetteAlpha.hashCode()
        result = 31 * result + specularGlowColor
        result = 31 * result + specularGlowAlpha.hashCode()
        result = 31 * result + colorMatrixValues.contentHashCode()
        return result
    }
}

object DlssColorCalibration {

    fun computeParams(preset: DlssPreset, intensity: Int): OverlayRenderParams {
        val clamped = intensity.coerceIn(0, 100)
        if (clamped == 0) {
            return OverlayRenderParams(
                curveFactor = 0f,
                baseTintColor = 0,
                baseTintAlpha = 0f,
                shadowVignetteColor = 0,
                shadowVignetteAlpha = 0f,
                specularGlowColor = 0,
                specularGlowAlpha = 0f,
                colorMatrixValues = identityMatrix()
            )
        }

        // Smooth perceptual curve (slightly finer precision in 1%-45% range)
        val normalized = clamped / 100f
        val curve = normalized.pow(1.12f)

        return when (preset) {
            DlssPreset.BALANCED -> {
                // DLSS 5 Standard: Deepened micro-contrast, subtle neutral-cool shadow anchor,
                // gentle specular clarity without crushing blacks or clipping whites.
                OverlayRenderParams(
                    curveFactor = curve,
                    baseTintColor = rgbInt(10, 18, 28),
                    baseTintAlpha = 0.065f * curve,
                    shadowVignetteColor = rgbInt(5, 9, 16),
                    shadowVignetteAlpha = 0.16f * curve,
                    specularGlowColor = rgbInt(214, 248, 236),
                    specularGlowAlpha = 0.045f * curve,
                    colorMatrixValues = buildColorMatrix(
                        contrast = 1f + 0.18f * curve,
                        saturation = 1f + 0.20f * curve,
                        shadowOffset = -11f * curve,
                        redGain = 1f + 0.015f * curve,
                        greenGain = 1f + 0.02f * curve,
                        blueGain = 1f + 0.025f * curve
                    )
                )
            }

            DlssPreset.CINEMATIC -> {
                // Cinematic Ray-Traced: Deeper shadow contrast + warm golden-amber bounce highlights.
                OverlayRenderParams(
                    curveFactor = curve,
                    baseTintColor = rgbInt(24, 16, 10),
                    baseTintAlpha = 0.085f * curve,
                    shadowVignetteColor = rgbInt(4, 6, 12),
                    shadowVignetteAlpha = 0.24f * curve,
                    specularGlowColor = rgbInt(255, 206, 140),
                    specularGlowAlpha = 0.065f * curve,
                    colorMatrixValues = buildColorMatrix(
                        contrast = 1f + 0.24f * curve,
                        saturation = 1f + 0.18f * curve,
                        shadowOffset = -16f * curve,
                        redGain = 1f + 0.045f * curve,
                        greenGain = 1f + 0.012f * curve,
                        blueGain = 1f - 0.035f * curve
                    )
                )
            }

            DlssPreset.COMPETITIVE -> {
                // Crisp Competitive: High clarity, vivid edge separation, minimal edge darkening
                // so peripheral HUD/targets stay crisp and bright.
                OverlayRenderParams(
                    curveFactor = curve,
                    baseTintColor = rgbInt(6, 22, 32),
                    baseTintAlpha = 0.045f * curve,
                    shadowVignetteColor = rgbInt(6, 12, 20),
                    shadowVignetteAlpha = 0.08f * curve,
                    specularGlowColor = rgbInt(198, 246, 255),
                    specularGlowAlpha = 0.06f * curve,
                    colorMatrixValues = buildColorMatrix(
                        contrast = 1f + 0.22f * curve,
                        saturation = 1f + 0.30f * curve,
                        shadowOffset = -6f * curve,
                        redGain = 1f - 0.01f * curve,
                        greenGain = 1f + 0.025f * curve,
                        blueGain = 1f + 0.045f * curve
                    )
                )
            }
        }
    }

    private fun rgbInt(r: Int, g: Int, b: Int): Int =
        (0xFF shl 24) or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)

    private fun identityMatrix(): FloatArray = floatArrayOf(
        1f, 0f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f, 0f,
        0f, 0f, 1f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    )

    private fun buildColorMatrix(
        contrast: Float,
        saturation: Float,
        shadowOffset: Float,
        redGain: Float,
        greenGain: Float,
        blueGain: Float
    ): FloatArray {
        // Rec.709 luminance weights for natural perceptual saturation
        val invSat = 1f - saturation
        val rWeight = 0.2126f * invSat
        val gWeight = 0.7152f * invSat
        val bWeight = 0.0722f * invSat

        val r0 = (rWeight + saturation) * contrast * redGain
        val r1 = gWeight * contrast * redGain
        val r2 = bWeight * contrast * redGain

        val g0 = rWeight * contrast * greenGain
        val g1 = (gWeight + saturation) * contrast * greenGain
        val g2 = bWeight * contrast * greenGain

        val b0 = rWeight * contrast * blueGain
        val b1 = gWeight * contrast * blueGain
        val b2 = (bWeight + saturation) * contrast * blueGain

        // Midpoint-centered contrast offset + micro-shadow depth anchor
        val baseTranslate = 128f * (1f - contrast) + shadowOffset

        return floatArrayOf(
            r0, r1, r2, 0f, baseTranslate,
            g0, g1, g2, 0f, baseTranslate,
            b0, b1, b2, 0f, baseTranslate,
            0f, 0f, 0f, 1f, 0f
        )
    }
}
