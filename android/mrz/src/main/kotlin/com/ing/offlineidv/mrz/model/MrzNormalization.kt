package com.ing.offlineidv.mrz.model

import com.ing.offlineidv.core.security.Redaction

/** Safe description of a deterministic normalization operation. */
public enum class MrzNormalizationChangeType {
    ASCII_CASE_FOLDED,
    OCR_SPACES_REMOVED,
    SINGLE_SEQUENCE_SPLIT,
    LINE_ENDINGS_NORMALIZED,
}

/** Non-sensitive normalization metadata. */
public data class MrzNormalizationChange(
    public val type: MrzNormalizationChangeType,
    public val lineIndex: Int? = null,
)

/** Opaque result of conservative MRZ normalization. */
public sealed interface MrzNormalizationResult {
    public val changes: List<MrzNormalizationChange>

    /** Complete supported MRZ structure whose raw normalized lines remain module-internal. */
    public class Success internal constructor(
        internal val lines: List<String>,
        changes: List<MrzNormalizationChange>,
    ) : MrzNormalizationResult {
        override val changes: List<MrzNormalizationChange> = changes.toList()
        public val lineCount: Int = lines.size

        override fun toString(): String =
            "MrzNormalizationResult.Success(lineCount=$lineCount, changes=$changes, " +
                "content=${Redaction.MARKER})"
    }

    /** Rejected input represented only by safe issue types and locations. */
    public class Failure(
        issues: List<MrzValidationIssue>,
        changes: List<MrzNormalizationChange>,
    ) : MrzNormalizationResult {
        public val issues: List<MrzValidationIssue> = issues.toList()
        override val changes: List<MrzNormalizationChange> = changes.toList()

        override fun toString(): String =
            "MrzNormalizationResult.Failure(issueCodes=${issues.map { it.type.stableCode }}, " +
                "content=${Redaction.MARKER})"
    }
}
