package com.ing.offlineidv.ocr.demo

import com.ing.offlineidv.core.error.IdvError
import com.ing.offlineidv.core.error.OcrFailure
import com.ing.offlineidv.ocr.OcrDocumentInput
import com.ing.offlineidv.ocr.OcrEngine
import com.ing.offlineidv.ocr.OcrEngineResult
import com.ing.offlineidv.ocr.OcrTextArtifact

/** External OCR observations available to the deterministic fake. */
public enum class DemoOcrBehavior {
    VALID_TD3,
    INVALID_MRZ,
    AMBIGUOUS_MRZ,
    EXPIRED_DOCUMENT,
    TECHNICAL_FAILURE,
}

/** Explicit synthetic OCR implementation. It produces text and never validates an MRZ. */
public class FakeOcrEngine(
    private val behavior: DemoOcrBehavior,
) : OcrEngine {
    override fun recognize(input: OcrDocumentInput): OcrEngineResult =
        when (behavior) {
            DemoOcrBehavior.TECHNICAL_FAILURE -> {
                OcrEngineResult.Failed(IdvError.Ocr(OcrFailure.RECOGNITION_FAILED))
            }

            else -> {
                OcrEngineResult.Recognized(OcrTextArtifact(SyntheticTd3Text.forBehavior(behavior)))
            }
        }

    /** The OCR fake has no mutable session state, but exposes symmetric cleanup. */
    public fun reset() = Unit
}

/** Conspicuously synthetic TD3 OCR fixtures; verification is performed by the real MRZ module. */
private object SyntheticTd3Text {
    fun forBehavior(behavior: DemoOcrBehavior): String =
        when (behavior) {
            DemoOcrBehavior.VALID_TD3 -> {
                build()
            }

            DemoOcrBehavior.INVALID_MRZ -> {
                replace(build(), lineIndex = 1, characterIndex = 9, replacement = '0')
            }

            DemoOcrBehavior.AMBIGUOUS_MRZ -> {
                replace(build(birthDate = "000101"), lineIndex = 1, characterIndex = 13, replacement = 'O')
            }

            DemoOcrBehavior.EXPIRED_DOCUMENT -> {
                build(expiryDate = "250101")
            }

            DemoOcrBehavior.TECHNICAL_FAILURE -> {
                error("A technical-failure fixture has no OCR text.")
            }
        }

    private fun build(
        documentNumber: String = "A12B34567",
        birthDate: String = "900101",
        expiryDate: String = "301231",
    ): String {
        val documentCode = "P<"
        val issuingState = "UTO"
        val name = "TESTER<<SYNTHETIC<ATLAS".padEnd(39, '<')
        val nationality = "UTO"
        val sex = 'F'
        val optionalData = "SYNTHETIC1<<<<"
        val documentDigit = checkDigit(documentNumber)
        val birthDigit = checkDigit(birthDate)
        val expiryDigit = checkDigit(expiryDate)
        val optionalDigit = checkDigit(optionalData)
        val composite =
            documentNumber +
                documentDigit +
                birthDate +
                birthDigit +
                expiryDate +
                expiryDigit +
                optionalData +
                optionalDigit
        val line1 = documentCode + issuingState + name
        val line2 =
            documentNumber +
                documentDigit +
                nationality +
                birthDate +
                birthDigit +
                sex +
                expiryDate +
                expiryDigit +
                optionalData +
                optionalDigit +
                checkDigit(composite)
        check(line1.length == 44 && line2.length == 44)
        return "$line1\n$line2"
    }

    private fun replace(
        input: String,
        lineIndex: Int,
        characterIndex: Int,
        replacement: Char,
    ): String {
        val lines = input.split('\n').toMutableList()
        lines[lineIndex] = lines[lineIndex].replaceRange(characterIndex, characterIndex + 1, replacement.toString())
        return lines.joinToString("\n")
    }

    /** Fixture generation only; this does not validate or interpret OCR output. */
    private fun checkDigit(value: CharSequence): Char {
        val weights = intArrayOf(7, 3, 1)
        val sum =
            value
                .mapIndexed { index, character ->
                    val encoded =
                        when (character) {
                            in '0'..'9' -> character - '0'
                            in 'A'..'Z' -> character - 'A' + 10
                            '<' -> 0
                            else -> error("Synthetic fixture character is not supported.")
                        }
                    encoded * weights[index % weights.size]
                }.sum()
        return ('0'.code + (sum % 10)).toChar()
    }
}
