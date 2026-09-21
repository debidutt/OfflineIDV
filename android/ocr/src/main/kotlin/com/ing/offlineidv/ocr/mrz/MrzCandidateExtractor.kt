package com.ing.offlineidv.ocr.mrz

import com.ing.offlineidv.core.security.Redaction

/** A likely two-line TD3 input retained behind scoped access and never rendered. */
public class MrzCandidate internal constructor(
    private val firstLine: String,
    private val secondLine: String,
    public val score: Int,
) {
    /** Safe structural metadata for diagnostics; no recognized characters are exposed. */
    public val lineLengths: List<Int> = listOf(firstLine.length, secondLine.length)

    /** Supplies the candidate only to the existing MRZ parser boundary. */
    public fun <R> useText(block: (CharSequence) -> R): R = block("$firstLine\n$secondLine")

    override fun toString(): String = "MrzCandidate(score=$score, value=${Redaction.MARKER})"
}

/** Pure conservative TD3 candidate detector; parsing and validation deliberately remain elsewhere. */
public class MrzCandidateExtractor {
    /** Returns the highest-ranked plausible pair, using stable source order as the final tie-break. */
    public fun extract(recognizedText: CharSequence): MrzCandidate? {
        val fragments =
            recognizedText
                .lineSequence()
                .mapIndexedNotNull { index, line -> sanitize(index, line) }
                .toList()
        val logicalLines = buildLogicalLines(fragments)
        return logicalLines
            .flatMapIndexed { firstIndex, first ->
                logicalLines.drop(firstIndex + 1).mapNotNull { second ->
                    if (first.endIndex >= second.startIndex || second.startIndex - first.endIndex > MAX_LINE_GAP) {
                        null
                    } else {
                        candidate(first, second)
                    }
                }
            }.maxWithOrNull(compareBy<MrzCandidate> { it.score })
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
                    if (value.length in MIN_TD3_LENGTH..MAX_TD3_LENGTH) {
                        add(LogicalLine(fragment.index, fragments[end].index, value))
                    }
                    if (value.length >= MAX_TD3_LENGTH) break
                }
            }
        }

    private fun candidate(
        first: LogicalLine,
        second: LogicalLine,
    ): MrzCandidate? {
        if (!first.value.startsWith("P") || second.value.none(Char::isDigit)) return null
        val score =
            PASSPORT_PREFIX_SCORE +
                (if (first.value.startsWith("P<")) DOCUMENT_CODE_SCORE else 0) -
                kotlin.math.abs(TD3_LENGTH - first.value.length) * LENGTH_PENALTY -
                kotlin.math.abs(TD3_LENGTH - second.value.length) * LENGTH_PENALTY +
                second.value.count(Char::isDigit)
        return MrzCandidate(first.value, second.value, score)
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
        const val TD3_LENGTH: Int = 44
        const val MIN_TD3_LENGTH: Int = 38
        const val MAX_TD3_LENGTH: Int = 48
        const val MIN_FRAGMENT_LENGTH: Int = 6
        const val MAX_FRAGMENTS_PER_LINE: Int = 3
        const val MAX_LINE_GAP: Int = 2
        const val MIN_ALLOWED_RATIO: Double = 0.85
        const val PASSPORT_PREFIX_SCORE: Int = 100
        const val DOCUMENT_CODE_SCORE: Int = 30
        const val LENGTH_PENALTY: Int = 4
    }
}
