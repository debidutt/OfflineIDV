package com.ing.offlineidv.mrz.parser

import com.ing.offlineidv.mrz.model.MrzParseResult
import com.ing.offlineidv.mrz.model.Td1ResidencePermitMrz
import com.ing.offlineidv.mrz.normalization.Td1MrzNormalizer
import com.ing.offlineidv.mrz.validation.Td1MrzValidator
import java.time.LocalDate

/** TD1 entry point composing conservative normalization and structured validation. */
public class Td1MrzParser(
    private val normalizer: Td1MrzNormalizer = Td1MrzNormalizer(),
    private val validator: Td1MrzValidator = Td1MrzValidator(),
) : MrzParser<Td1ResidencePermitMrz> {
    override fun parse(
        input: CharSequence,
        referenceDate: LocalDate,
    ): MrzParseResult<Td1ResidencePermitMrz> = validator.validate(normalizer.normalize(input), referenceDate)
}
