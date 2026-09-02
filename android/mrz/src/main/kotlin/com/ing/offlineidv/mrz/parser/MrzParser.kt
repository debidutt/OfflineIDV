package com.ing.offlineidv.mrz.parser

import com.ing.offlineidv.mrz.model.MrzDocument
import com.ing.offlineidv.mrz.model.MrzParseResult
import java.time.LocalDate

/** Pure parser contract requiring an explicit reference date for semantic date interpretation. */
public fun interface MrzParser<T : MrzDocument> {
    /** Parses untrusted OCR-derived [input] without logging or rendering its contents. */
    public fun parse(
        input: CharSequence,
        referenceDate: LocalDate,
    ): MrzParseResult<T>
}
