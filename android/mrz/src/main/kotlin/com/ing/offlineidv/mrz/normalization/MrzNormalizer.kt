package com.ing.offlineidv.mrz.normalization

import com.ing.offlineidv.mrz.model.MrzNormalizationChange
import com.ing.offlineidv.mrz.model.MrzNormalizationChangeType
import com.ing.offlineidv.mrz.model.MrzNormalizationResult
import com.ing.offlineidv.mrz.model.MrzValidationIssue
import com.ing.offlineidv.mrz.model.MrzValidationIssueType

/** Conservative normalization for OCR-derived TD3 candidate text. */
public class MrzNormalizer {
    /** Normalizes [input] without silently deleting unsupported characters or incomplete content. */
    public fun normalize(input: CharSequence): MrzNormalizationResult {
        val changes = mutableListOf<MrzNormalizationChange>()
        val original = input.toString()
        val normalizedEndings = original.replace("\r\n", "\n").replace('\r', '\n')
        if (normalizedEndings != original) {
            changes += MrzNormalizationChange(MrzNormalizationChangeType.LINE_ENDINGS_NORMALIZED)
        }

        val trimmed = normalizedEndings.trim()
        val physicalLines = if (trimmed.isEmpty()) emptyList() else trimmed.split('\n')
        val preparedLines =
            physicalLines.mapIndexed { lineIndex, line ->
                prepareLine(line.trim(), lineIndex, changes)
            }

        val candidateLines =
            if (preparedLines.size == 1 && preparedLines.single().length == TD3_TOTAL_LENGTH) {
                changes += MrzNormalizationChange(MrzNormalizationChangeType.SINGLE_SEQUENCE_SPLIT)
                listOf(
                    preparedLines.single().substring(0, TD3_LINE_LENGTH),
                    preparedLines.single().substring(TD3_LINE_LENGTH, TD3_TOTAL_LENGTH),
                )
            } else {
                preparedLines
            }

        val issues = mutableListOf<MrzValidationIssue>()
        if (candidateLines.size != TD3_LINE_COUNT) {
            issues +=
                MrzValidationIssue(
                    type = MrzValidationIssueType.INCORRECT_LINE_COUNT,
                    actualLength = candidateLines.size,
                )
        }
        candidateLines.forEachIndexed { lineIndex, line ->
            if (line.length != TD3_LINE_LENGTH) {
                issues +=
                    MrzValidationIssue(
                        type = MrzValidationIssueType.INCORRECT_LINE_LENGTH,
                        lineIndex = lineIndex,
                        actualLength = line.length,
                    )
            }
            line.forEachIndexed { characterIndex, character ->
                if (!isMrzCharacter(character)) {
                    issues +=
                        MrzValidationIssue(
                            type = MrzValidationIssueType.UNSUPPORTED_CHARACTER,
                            lineIndex = lineIndex,
                            characterIndex = characterIndex,
                        )
                }
            }
        }

        return if (issues.isEmpty()) {
            MrzNormalizationResult.Success(candidateLines, changes)
        } else {
            MrzNormalizationResult.Failure(issues, changes)
        }
    }

    private fun prepareLine(
        line: String,
        lineIndex: Int,
        changes: MutableList<MrzNormalizationChange>,
    ): String {
        val withoutSpaces = line.filterNot { it == ' ' }
        if (withoutSpaces.length != line.length) {
            changes +=
                MrzNormalizationChange(
                    MrzNormalizationChangeType.OCR_SPACES_REMOVED,
                    lineIndex,
                )
        }
        var caseFolded = false
        val upper =
            withoutSpaces
                .map { character ->
                    if (character in 'a'..'z') {
                        caseFolded = true
                        (character.code - LOWERCASE_OFFSET).toChar()
                    } else {
                        character
                    }
                }.joinToString(separator = "")
        if (caseFolded) {
            changes +=
                MrzNormalizationChange(
                    MrzNormalizationChangeType.ASCII_CASE_FOLDED,
                    lineIndex,
                )
        }
        return upper
    }

    private fun isMrzCharacter(character: Char): Boolean = character in 'A'..'Z' || character in '0'..'9' || character == '<'

    private companion object {
        private const val TD3_LINE_COUNT: Int = 2
        private const val TD3_LINE_LENGTH: Int = 44
        private const val TD3_TOTAL_LENGTH: Int = TD3_LINE_COUNT * TD3_LINE_LENGTH
        private const val LOWERCASE_OFFSET: Int = 'a'.code - 'A'.code
    }
}
