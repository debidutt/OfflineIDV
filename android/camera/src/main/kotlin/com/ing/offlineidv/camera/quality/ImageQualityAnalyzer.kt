package com.ing.offlineidv.camera.quality

/** Configurable conservative thresholds for a basic document-image heuristic. */
public data class ImageQualityThresholds(
    public val minimumWidth: Int = 1_000,
    public val minimumHeight: Int = 600,
    public val darkMeanLuma: Double = 45.0,
    public val brightMeanLuma: Double = 220.0,
    public val minimumEdgeEnergy: Double = 180.0,
)

/** Structured technical finding; none of these values decides retry or a product outcome. */
public enum class DocumentQualityIssue {
    INSUFFICIENT_RESOLUTION,
    TOO_DARK,
    TOO_BRIGHT,
    TOO_BLURRY,
}

/** Pure luma input for deterministic quality tests and platform-independent heuristics. */
public class LumaImage(
    public val width: Int,
    public val height: Int,
    luma: IntArray,
    public val sampleWidth: Int = width,
    public val sampleHeight: Int = height,
) {
    public val luma: IntArray = luma.copyOf()

    init {
        require(width > 0 && height > 0) { "Image dimensions must be positive." }
        require(sampleWidth > 0 && sampleHeight > 0) { "Sample dimensions must be positive." }
        require(luma.size == sampleWidth * sampleHeight) { "Luma sample count must match sample dimensions." }
        require(luma.all { it in 0..255 }) { "Luma samples must be bytes." }
    }
}

/** Result of the quality heuristic, separate from reducer recovery semantics. */
public sealed interface ImageQualityAssessment {
    public data object Accepted : ImageQualityAssessment

    public data class Rejected(
        public val issue: DocumentQualityIssue,
    ) : ImageQualityAssessment
}

/** Pure basic quality analyzer using mean brightness and adjacent-pixel edge energy. */
public class ImageQualityAnalyzer(
    private val thresholds: ImageQualityThresholds = ImageQualityThresholds(),
) {
    public fun analyze(image: LumaImage): ImageQualityAssessment {
        if (image.width < thresholds.minimumWidth || image.height < thresholds.minimumHeight) {
            return ImageQualityAssessment.Rejected(DocumentQualityIssue.INSUFFICIENT_RESOLUTION)
        }
        val mean = image.luma.average()
        if (mean < thresholds.darkMeanLuma) return ImageQualityAssessment.Rejected(DocumentQualityIssue.TOO_DARK)
        if (mean > thresholds.brightMeanLuma) return ImageQualityAssessment.Rejected(DocumentQualityIssue.TOO_BRIGHT)
        if (edgeEnergy(image) < thresholds.minimumEdgeEnergy) {
            return ImageQualityAssessment.Rejected(DocumentQualityIssue.TOO_BLURRY)
        }
        return ImageQualityAssessment.Accepted
    }

    private fun edgeEnergy(image: LumaImage): Double {
        var energy = 0L
        var comparisons = 0L
        for (y in 0 until image.sampleHeight) {
            val row = y * image.sampleWidth
            for (x in 1 until image.sampleWidth) {
                val difference = image.luma[row + x] - image.luma[row + x - 1]
                energy += difference.toLong() * difference
                comparisons += 1
            }
        }
        return if (comparisons == 0L) 0.0 else energy.toDouble() / comparisons
    }
}
