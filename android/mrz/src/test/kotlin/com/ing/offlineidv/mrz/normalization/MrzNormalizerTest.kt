package com.ing.offlineidv.mrz.normalization

import com.ing.offlineidv.mrz.fixtures.SyntheticTd3Fixtures
import com.ing.offlineidv.mrz.model.MrzNormalizationChangeType
import com.ing.offlineidv.mrz.model.MrzNormalizationResult
import com.ing.offlineidv.mrz.model.MrzValidationIssueType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

public class MrzNormalizerTest {
    private val normalizer = MrzNormalizer()

    @Test
    public fun `two complete lines are accepted unchanged`() {
        val result = success(SyntheticTd3Fixtures.valid)

        assertEquals(2, result.lineCount)
        assertTrue(result.changes.isEmpty())
    }

    @Test
    public fun `one complete sequence is split safely`() {
        val oneLine = SyntheticTd3Fixtures.valid.replace("\n", "")
        val result = success(oneLine)

        assertTrue(result.changes.any { it.type == MrzNormalizationChangeType.SINGLE_SEQUENCE_SPLIT })
    }

    @Test
    public fun `lowercase ASCII is folded without locale rules`() {
        val result = success(SyntheticTd3Fixtures.valid.lowercase())

        assertTrue(result.changes.any { it.type == MrzNormalizationChangeType.ASCII_CASE_FOLDED })
    }

    @Test
    public fun `OCR-added ASCII spaces are removed`() {
        val result = success(SyntheticTd3Fixtures.addOcrSpaces(SyntheticTd3Fixtures.valid))

        assertEquals(
            2,
            result.changes.count { it.type == MrzNormalizationChangeType.OCR_SPACES_REMOVED },
        )
    }

    @Test
    public fun `CRLF is normalized`() {
        val result = success(SyntheticTd3Fixtures.valid.replace("\n", "\r\n"))

        assertTrue(
            result.changes.any { it.type == MrzNormalizationChangeType.LINE_ENDINGS_NORMALIZED },
        )
    }

    @Test
    public fun `unsupported character is rejected with location only`() {
        val input = SyntheticTd3Fixtures.replace(SyntheticTd3Fixtures.valid, 0, 10, '@')
        val result = failure(input)

        val issue = result.issues.single { it.type == MrzValidationIssueType.UNSUPPORTED_CHARACTER }
        assertEquals(0, issue.lineIndex)
        assertEquals(10, issue.characterIndex)
    }

    @Test
    public fun `incorrect line count is rejected`() {
        val result = failure(SyntheticTd3Fixtures.lines(SyntheticTd3Fixtures.valid).first())

        assertTrue(result.issues.any { it.type == MrzValidationIssueType.INCORRECT_LINE_COUNT })
    }

    @Test
    public fun `short line is rejected`() {
        val lines = SyntheticTd3Fixtures.lines(SyntheticTd3Fixtures.valid)
        val result = failure(lines[0] + "\n" + lines[1].dropLast(1))

        assertTrue(result.issues.any { it.type == MrzValidationIssueType.INCORRECT_LINE_LENGTH })
    }

    @Test
    public fun `long line is rejected`() {
        val result = failure(SyntheticTd3Fixtures.valid + "<")

        assertTrue(result.issues.any { it.type == MrzValidationIssueType.INCORRECT_LINE_LENGTH })
    }

    @Test
    public fun `internal tab is rejected rather than deleted`() {
        val input = SyntheticTd3Fixtures.replace(SyntheticTd3Fixtures.valid, 0, 10, '\t')
        val result = failure(input)

        assertTrue(result.issues.any { it.type == MrzValidationIssueType.UNSUPPORTED_CHARACTER })
    }

    private fun success(input: String): MrzNormalizationResult.Success {
        val result = normalizer.normalize(input)
        assertTrue(result is MrzNormalizationResult.Success)
        return result as MrzNormalizationResult.Success
    }

    private fun failure(input: String): MrzNormalizationResult.Failure {
        val result = normalizer.normalize(input)
        assertTrue(result is MrzNormalizationResult.Failure)
        return result as MrzNormalizationResult.Failure
    }
}
