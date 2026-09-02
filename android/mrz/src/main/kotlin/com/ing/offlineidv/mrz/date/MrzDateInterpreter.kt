package com.ing.offlineidv.mrz.date

import com.ing.offlineidv.mrz.model.MrzDate
import com.ing.offlineidv.mrz.model.MrzExpiryStatus
import java.time.DateTimeException
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Deterministic, configurable windows for two-digit TD3 dates. */
public data class MrzDatePolicy(
    public val maximumBirthAgeYears: Long = 120,
    public val expiryPastYears: Long = 10,
    public val expiryFutureYears: Long = 20,
) {
    init {
        require(maximumBirthAgeYears > 0) { "maximumBirthAgeYears must be positive" }
        require(expiryPastYears >= 0) { "expiryPastYears must not be negative" }
        require(expiryFutureYears >= 0) { "expiryFutureYears must not be negative" }
        require(expiryPastYears + expiryFutureYears < 100) {
            "expiry interpretation window must be shorter than one century"
        }
    }
}

/** Safe reason for a date that cannot be interpreted. */
public enum class MrzDateFailure {
    MALFORMED,
    IMPOSSIBLE,
    FUTURE_BIRTH_DATE,
}

/** Date interpretation with uncertainty kept explicit. */
public sealed interface MrzDateInterpretation {
    public class Resolved(
        public val date: MrzDate,
        public val expiryStatus: MrzExpiryStatus = MrzExpiryStatus.UNKNOWN,
    ) : MrzDateInterpretation {
        override fun toString(): String = "MrzDateInterpretation.Resolved(date=$date, expiryStatus=$expiryStatus)"
    }

    public class Ambiguous(
        public val date: MrzDate,
        public val expiryStatus: MrzExpiryStatus = MrzExpiryStatus.UNKNOWN,
    ) : MrzDateInterpretation {
        override fun toString(): String = "MrzDateInterpretation.Ambiguous(date=$date, expiryStatus=$expiryStatus)"
    }

    public data class Failure(
        public val reason: MrzDateFailure,
    ) : MrzDateInterpretation
}

/** Contract for reference-date-driven birth and expiry interpretation. */
public interface MrzDateInterpreter {
    public fun interpretBirthDate(
        value: CharSequence,
        referenceDate: LocalDate,
    ): MrzDateInterpretation

    public fun interpretExpiryDate(
        value: CharSequence,
        referenceDate: LocalDate,
    ): MrzDateInterpretation
}

/** Rolling-window TD3 date interpreter defined by [MrzDatePolicy]. */
public class WindowedMrzDateInterpreter(
    private val policy: MrzDatePolicy = MrzDatePolicy(),
) : MrzDateInterpreter {
    override fun interpretBirthDate(
        value: CharSequence,
        referenceDate: LocalDate,
    ): MrzDateInterpretation {
        val parts = parseParts(value) ?: return MrzDateInterpretation.Failure(MrzDateFailure.MALFORMED)
        val oldestDate = referenceDate.minusYears(policy.maximumBirthAgeYears)
        val candidates = candidateDates(parts, oldestDate.year - 1, referenceDate.year + 1)
        if (candidates.isEmpty()) {
            return MrzDateInterpretation.Failure(MrzDateFailure.IMPOSSIBLE)
        }
        val permitted = candidates.filter { !it.isBefore(oldestDate) && !it.isAfter(referenceDate) }.sorted()
        if (permitted.isNotEmpty()) {
            val preferred = permitted.last()
            val mrzDate = MrzDate(preferred, permitted.dropLast(1))
            return if (mrzDate.isAmbiguous) {
                MrzDateInterpretation.Ambiguous(mrzDate)
            } else {
                MrzDateInterpretation.Resolved(mrzDate)
            }
        }
        if (candidates.any { it.isAfter(referenceDate) }) {
            return MrzDateInterpretation.Failure(MrzDateFailure.FUTURE_BIRTH_DATE)
        }
        val preferred = candidates.minBy { absoluteDaysBetween(referenceDate, it) }
        return MrzDateInterpretation.Ambiguous(
            MrzDate(preferred, candidates.filterNot { it == preferred }),
        )
    }

    override fun interpretExpiryDate(
        value: CharSequence,
        referenceDate: LocalDate,
    ): MrzDateInterpretation {
        val parts = parseParts(value) ?: return MrzDateInterpretation.Failure(MrzDateFailure.MALFORMED)
        val windowStart = referenceDate.minusYears(policy.expiryPastYears)
        val windowEnd = referenceDate.plusYears(policy.expiryFutureYears)
        val candidates = candidateDates(parts, windowStart.year, windowEnd.year)
        val permitted = candidates.filter { !it.isBefore(windowStart) && !it.isAfter(windowEnd) }.sorted()
        if (permitted.size == 1) {
            val date = permitted.single()
            return MrzDateInterpretation.Resolved(MrzDate(date), expiryStatus(date, referenceDate))
        }

        val surrounding =
            candidateDates(parts, referenceDate.year - CENTURY, referenceDate.year + CENTURY)
        if (surrounding.isEmpty()) {
            return MrzDateInterpretation.Failure(MrzDateFailure.IMPOSSIBLE)
        }
        val preferred = surrounding.minBy { absoluteDaysBetween(referenceDate, it) }
        return MrzDateInterpretation.Ambiguous(
            MrzDate(preferred, surrounding.filterNot { it == preferred }),
        )
    }

    private fun parseParts(value: CharSequence): DateParts? {
        if (value.length != DATE_LENGTH || value.any { it !in '0'..'9' }) return null
        return DateParts(
            year = value.substring(0, 2).toInt(),
            month = value.substring(2, 4).toInt(),
            day = value.substring(4, 6).toInt(),
        )
    }

    private fun candidateDates(
        parts: DateParts,
        firstYear: Int,
        lastYear: Int,
    ): List<LocalDate> {
        val matchingYears = (firstYear..lastYear).filter { Math.floorMod(it, CENTURY) == parts.year }
        val dates =
            matchingYears.mapNotNull { year ->
                try {
                    LocalDate.of(year, parts.month, parts.day)
                } catch (_: DateTimeException) {
                    null
                }
            }
        return dates
    }

    private fun expiryStatus(
        expiryDate: LocalDate,
        referenceDate: LocalDate,
    ): MrzExpiryStatus =
        when {
            expiryDate.isBefore(referenceDate) -> MrzExpiryStatus.EXPIRED
            expiryDate == referenceDate -> MrzExpiryStatus.EXPIRES_TODAY
            else -> MrzExpiryStatus.VALID
        }

    private fun absoluteDaysBetween(
        first: LocalDate,
        second: LocalDate,
    ): Long = kotlin.math.abs(ChronoUnit.DAYS.between(first, second))

    private data class DateParts(
        val year: Int,
        val month: Int,
        val day: Int,
    )

    private companion object {
        private const val DATE_LENGTH: Int = 6
        private const val CENTURY: Int = 100
    }
}
