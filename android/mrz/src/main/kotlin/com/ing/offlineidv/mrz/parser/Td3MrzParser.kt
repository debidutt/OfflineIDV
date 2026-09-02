package com.ing.offlineidv.mrz.parser

import com.ing.offlineidv.mrz.model.MrzParseResult
import com.ing.offlineidv.mrz.model.Td3PassportMrz
import com.ing.offlineidv.mrz.normalization.MrzNormalizer
import com.ing.offlineidv.mrz.validation.MrzValidator
import java.time.LocalDate

/** TD3 entry point composing conservative normalization and structured validation. */
public class Td3MrzParser(
    private val normalizer: MrzNormalizer = MrzNormalizer(),
    private val validator: MrzValidator = MrzValidator(),
) : MrzParser<Td3PassportMrz> {
    override fun parse(
        input: CharSequence,
        referenceDate: LocalDate,
    ): MrzParseResult<Td3PassportMrz> = validator.validate(normalizer.normalize(input), referenceDate)
}
