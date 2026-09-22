package com.ing.offlineidv.ocr.mrz

import com.ing.offlineidv.core.security.Redaction

/** Likely three-line TD1 input retained behind scoped access and never rendered. */
public class Td1MrzCandidate internal constructor(
    lines: List<String>,
    public val score: Int,
) {
    private val lines: List<String> = lines.toList()

    init {
        require(lines.size == TD1_LINE_COUNT) { "TD1 candidate must contain three lines" }
    }

    /** Safe structural metadata for diagnostics; no recognized characters are exposed. */
    public val lineLengths: List<Int> = lines.map(String::length)

    /** Supplies candidate content only to the trusted TD1 parser boundary. */
    public fun <R> useText(block: (CharSequence) -> R): R = block(lines.joinToString("\n"))

    override fun toString(): String = "Td1MrzCandidate(score=$score, value=${Redaction.MARKER})"

    private companion object {
        const val TD1_LINE_COUNT: Int = 3
    }
}

/** Pure conservative TD1 candidate detector; parsing and validation remain in the MRZ module. */
public class Td1MrzCandidateExtractor {
    /** Returns the highest-ranked plausible three-line candidate with stable source-order ties. */
    public fun extract(recognizedText: CharSequence): Td1MrzCandidate? {
        val fragments =
            recognizedText
                .lineSequence()
                .mapIndexedNotNull { index, line -> sanitize(index, line) }
                .toList()
        val logicalLines = buildLogicalLines(fragments)
        var best: Td1MrzCandidate? = null
        logicalLines.forEachIndexed { firstIndex, first ->
            logicalLines.drop(firstIndex + 1).forEachIndexed secondLoop@{ secondOffset, second ->
                if (!follows(first, second)) return@secondLoop
                logicalLines.drop(firstIndex + secondOffset + 2).forEach thirdLoop@{ third ->
                    if (!follows(second, third)) return@thirdLoop
                    val candidate = candidate(first, second, third) ?: return@thirdLoop
                    if (best == null || candidate.score > requireNotNull(best).score) best = candidate
                }
            }
        }
        return best
    }

    private fun sanitize(
        index: Int,
        line: String,
    ): Fragment? {
        if (line.isBlank()) return null
        val upper = line.uppercase()
        val allowed = upper.count { it.isAllowed() || it.isWhitespace() }
        if (allowed.toDouble() / upper.length < MIN_ALLOWED_RATIO) return null
        val value = upper.filter { it.isAllowed() }
        return if (value.length >= MIN_FRAGMENT_LENGTH) Fragment(index, value) else null
    }

    private fun buildLogicalLines(fragments: List<Fragment>): List<LogicalLine> =
        buildList {
            fragments.forEachIndexed { start, fragment ->
                var value = ""
                for (end in start until minOf(start + MAX_FRAGMENTS_PER_LINE, fragments.size)) {
                    if (end > start && fragments[end].index - fragments[end - 1].index > 1) break
                    value += fragments[end].value
                    if (value.length in MIN_TD1_LENGTH..MAX_TD1_LENGTH) {
                        add(LogicalLine(fragment.index, fragments[end].index, value))
                    }
                    if (value.length >= MAX_TD1_LENGTH) break
                }
            }
        }

    private fun follows(
        first: LogicalLine,
        second: LogicalLine,
    ): Boolean = first.endIndex < second.startIndex && second.startIndex - first.endIndex <= MAX_LINE_GAP

    private fun candidate(
        first: LogicalLine,
        second: LogicalLine,
        third: LogicalLine,
    ): Td1MrzCandidate? {
        if (first.value.firstOrNull() !in TD1_DOCUMENT_CODES) return null
        if (second.value.count(Char::isDigit) < MIN_MIDDLE_DIGITS) return null
        if ('<' !in third.value) return null
        val lines = listOf(first.value, second.value, third.value)
        val score =
            DOCUMENT_PREFIX_SCORE +
                (if (first.value.take(2).contains('<')) FILLER_QUALIFIER_SCORE else 0) -
                lines.sumOf { kotlin.math.abs(TD1_LENGTH - it.length) * LENGTH_PENALTY } +
                second.value.count(Char::isDigit)
        return Td1MrzCandidate(lines, score)
    }

    private fun Char.isAllowed(): Boolean = this in 'A'..'Z' || this in '0'..'9' || this == '<'

    private data class Fragment(
        val index: Int,
        val value: String,
    )

    private data class LogicalLine(
        val startIndex: Int,
        val endIndex: Int,
        val value: String,
    )

    private companion object {
        val TD1_DOCUMENT_CODES: Set<Char> = setOf('A', 'C', 'I')
        const val TD1_LENGTH: Int = 30
        const val MIN_TD1_LENGTH: Int = 26
        const val MAX_TD1_LENGTH: Int = 34
        const val MIN_FRAGMENT_LENGTH: Int = 6
        const val MAX_FRAGMENTS_PER_LINE: Int = 3
        const val MAX_LINE_GAP: Int = 2
        const val MIN_ALLOWED_RATIO: Double = 0.85
        const val MIN_MIDDLE_DIGITS: Int = 8
        const val DOCUMENT_PREFIX_SCORE: Int = 100
        const val FILLER_QUALIFIER_SCORE: Int = 15
        const val LENGTH_PENALTY: Int = 4
    }
}
