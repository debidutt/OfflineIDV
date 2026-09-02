package com.ing.offlineidv.camera.quality

import org.junit.Assert.assertEquals
import org.junit.Test

public class ImageQualityAnalyzerTest {
    private val thresholds =
        ImageQualityThresholds(
            minimumWidth = 4,
            minimumHeight = 4,
            darkMeanLuma = 45.0,
            brightMeanLuma = 220.0,
            minimumEdgeEnergy = 180.0,
        )
    private val analyzer = ImageQualityAnalyzer(thresholds)

    @Test
    public fun `adequate high contrast image is accepted`() {
        assertEquals(ImageQualityAssessment.Accepted, analyzer.analyze(checkerboard(4, 4, 60, 190)))
    }

    @Test
    public fun `very dark image is rejected`() {
        assertRejected(DocumentQualityIssue.TOO_DARK, uniform(4, 4, 10))
    }

    @Test
    public fun `very bright image is rejected`() {
        assertRejected(DocumentQualityIssue.TOO_BRIGHT, uniform(4, 4, 245))
    }

    @Test
    public fun `flat image is rejected as blurry`() {
        assertRejected(DocumentQualityIssue.TOO_BLURRY, uniform(4, 4, 128))
    }

    @Test
    public fun `low resolution image is rejected before other checks`() {
        assertRejected(DocumentQualityIssue.INSUFFICIENT_RESOLUTION, uniform(3, 4, 10))
    }

    @Test
    public fun `brightness thresholds are deterministic at boundary`() {
        val boundaryAnalyzer = ImageQualityAnalyzer(thresholds.copy(minimumEdgeEnergy = 0.0))

        assertEquals(ImageQualityAssessment.Accepted, boundaryAnalyzer.analyze(uniform(4, 4, 45)))
        assertEquals(
            ImageQualityAssessment.Rejected(DocumentQualityIssue.TOO_DARK),
            boundaryAnalyzer.analyze(uniform(4, 4, 44)),
        )
    }

    private fun assertRejected(
        issue: DocumentQualityIssue,
        image: LumaImage,
    ) {
        assertEquals(ImageQualityAssessment.Rejected(issue), analyzer.analyze(image))
    }

    private fun uniform(
        width: Int,
        height: Int,
        value: Int,
    ): LumaImage = LumaImage(width, height, IntArray(width * height) { value })

    private fun checkerboard(
        width: Int,
        height: Int,
        low: Int,
        high: Int,
    ): LumaImage = LumaImage(width, height, IntArray(width * height) { if (it % 2 == 0) low else high })
}
