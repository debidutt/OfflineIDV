package com.ing.offlineidv.nfc

import com.ing.offlineidv.core.concurrency.CancellableOperation
import com.ing.offlineidv.core.error.IdvError
import com.ing.offlineidv.core.error.NfcFailure
import com.ing.offlineidv.core.security.Redaction
import com.ing.offlineidv.core.session.IdvSessionId

/** Sensitive chip-access material retained only behind the NFC engine boundary. */
public class PassportAccessKey(
    value: String,
) : AutoCloseable {
    private val value: CharArray = value.toCharArray()

    /** Supplies the access material only inside a trusted NFC boundary. */
    public fun <R> useValue(block: (String) -> R): R = block(String(value))

    internal fun fields(): MrzAccessFields? = MrzAccessFields.decode(String(value))

    override fun equals(other: Any?): Boolean = other is PassportAccessKey && value.contentEquals(other.value)

    override fun hashCode(): Int = value.contentHashCode()

    /** Overwrites the mutable Atlas-owned representation. */
    override fun close() {
        value.fill('\u0000')
    }

    override fun toString(): String = "PassportAccessKey(${Redaction.MARKER})"

    public companion object {
        /** Creates MRZ-derived BAC/PACE access material without retaining complete MRZ lines. */
        public fun fromMrzFields(
            documentNumber: String,
            dateOfBirth: String,
            expiryDate: String,
        ): PassportAccessKey = PassportAccessKey(MrzAccessFields.encode(documentNumber, dateOfBirth, expiryDate))
    }
}

/** Printed identity representation retained behind the comparison-engine boundary. */
public class PrintedPassportData(
    value: String,
) : AutoCloseable {
    private val value: CharArray = value.toCharArray()

    /** Supplies printed comparison material only inside a trusted comparison boundary. */
    public fun <R> useValue(block: (String) -> R): R = block(String(value))

    internal fun fields(): MrzComparisonFields? = MrzComparisonFields.decode(String(value))

    override fun equals(other: Any?): Boolean = other is PrintedPassportData && value.contentEquals(other.value)

    override fun hashCode(): Int = value.contentHashCode()

    /** Overwrites the mutable Atlas-owned representation. */
    override fun close() {
        value.fill('\u0000')
    }

    override fun toString(): String = "PrintedPassportData(${Redaction.MARKER})"

    public companion object {
        /** Retains only fields required for printed-versus-chip consistency. */
        public fun fromMrzFields(
            documentNumber: String,
            nationality: String,
            dateOfBirth: String,
            expiryDate: String,
        ): PrintedPassportData =
            PrintedPassportData(
                MrzComparisonFields.encode(documentNumber, nationality, dateOfBirth, expiryDate),
            )
    }
}

/** Safe NFC-read request for one session. */
public data class NfcReadRequest(
    public val sessionId: IdvSessionId,
    public val accessKey: PassportAccessKey,
)

/** Synthetic or platform-owned chip data retained outside verification state. */
public class ChipDataArtifact private constructor(
    dg1Value: String,
    portraitBytes: ByteArray?,
    internal val passiveAuthentication: PassiveAuthenticationObservation,
    internal val chipAuthentication: ChipAuthenticationObservation,
) : AutoCloseable {
    private val dg1Value: CharArray = dg1Value.toCharArray()
    private val portraitBytes: ByteArray? = portraitBytes?.copyOf()

    /** Creates deterministic fake chip material without security claims. */
    public constructor(value: String) :
        this(
            value,
            null,
            PassiveAuthenticationObservation.NOT_PERFORMED,
            ChipAuthenticationObservation.NOT_PERFORMED,
        )

    /** Supplies chip material only inside a trusted validation/comparison boundary. */
    public fun <R> useValue(block: (String) -> R): R = block(String(dg1Value))

    internal val hasDg1: Boolean
        get() = dg1Value.isNotEmpty()

    internal val hasDg2: Boolean
        get() = portraitBytes != null

    internal fun portrait(): ChipPortraitArtifact? = portraitBytes?.let(ChipPortraitArtifact::copyOf)

    override fun equals(other: Any?): Boolean =
        other is ChipDataArtifact &&
            dg1Value.contentEquals(other.dg1Value) &&
            portraitBytes.contentEqualsNullable(other.portraitBytes) &&
            passiveAuthentication == other.passiveAuthentication &&
            chipAuthentication == other.chipAuthentication

    override fun hashCode(): Int {
        var result = dg1Value.contentHashCode()
        result = 31 * result + (portraitBytes?.contentHashCode() ?: 0)
        result = 31 * result + passiveAuthentication.hashCode()
        return 31 * result + chipAuthentication.hashCode()
    }

    /** Overwrites Atlas-owned DG1 and DG2 copies. */
    override fun close() {
        dg1Value.fill('\u0000')
        portraitBytes?.fill(0)
    }

    override fun toString(): String = "ChipDataArtifact(${Redaction.MARKER})"

    public companion object {
        internal fun fromRead(
            dg1Value: String,
            portraitBytes: ByteArray?,
            passiveAuthentication: PassiveAuthenticationObservation,
            chipAuthentication: ChipAuthenticationObservation = ChipAuthenticationObservation.NOT_PERFORMED,
        ): ChipDataArtifact =
            ChipDataArtifact(
                dg1Value,
                portraitBytes,
                passiveAuthentication,
                chipAuthentication,
            )

        /** Retains only bounded DG1 fields needed for printed-versus-chip consistency. */
        public fun fromDg1Fields(
            documentNumber: String,
            nationality: String,
            dateOfBirth: String,
            expiryDate: String,
        ): ChipDataArtifact =
            ChipDataArtifact(
                dg1Value = MrzComparisonFields.encode(documentNumber, nationality, dateOfBirth, expiryDate),
                portraitBytes = null,
                passiveAuthentication = PassiveAuthenticationObservation.NOT_PERFORMED,
                chipAuthentication = ChipAuthenticationObservation.NOT_PERFORMED,
            )
    }
}

/** Portrait material returned by chip validation and retained outside verification state. */
public class ChipPortraitArtifact private constructor(
    value: ByteArray,
) : AutoCloseable {
    private val value: ByteArray = value.copyOf()

    /** Creates deterministic fake portrait material without biometric claims. */
    public constructor(value: String) : this(value.encodeToByteArray())

    /** Supplies portrait material only inside a trusted face boundary. */
    public fun <R> useValue(block: (String) -> R): R = block(value.decodeToString())

    override fun equals(other: Any?): Boolean = other is ChipPortraitArtifact && value.contentEquals(other.value)

    override fun hashCode(): Int = value.contentHashCode()

    /** Overwrites the Atlas-owned portrait copy. */
    override fun close() {
        value.fill(0)
    }

    override fun toString(): String = "ChipPortraitArtifact(${Redaction.MARKER})"

    internal companion object {
        fun copyOf(value: ByteArray): ChipPortraitArtifact = ChipPortraitArtifact(value)
    }
}

/** Current Android NFC hardware state without a verification or retry decision. */
public enum class NfcCapability {
    UNAVAILABLE,
    DISABLED,
    AVAILABLE,
}

/** Platform capability boundary used by composition and the real NFC adapter. */
public fun interface NfcCapabilityDetector {
    public fun detect(): NfcCapability
}

/** External NFC read observation. Timeout does not imply retry or a terminal outcome. */
public sealed interface NfcReadResult {
    public data class Read(
        public val artifact: ChipDataArtifact,
    ) : NfcReadResult

    public data object Timeout : NfcReadResult

    public data object Unavailable : NfcReadResult

    public data class Failed(
        public val error: IdvError,
    ) : NfcReadResult
}

/** External passport-chip read boundary. */
public fun interface PassportNfcEngine {
    public fun read(request: NfcReadRequest): NfcReadResult
}

/** Non-blocking passport-chip boundary for user-driven Android tag discovery. */
public fun interface AsyncPassportNfcEngine {
    /** Starts one read and returns a handle that closes transport and suppresses late delivery. */
    public fun read(
        request: NfcReadRequest,
        callback: (NfcReadResult) -> Unit,
    ): CancellableOperation

    /**
     * Starts one read with finite, payload-free progress observations.
     *
     * Implementations that do not expose progress remain source-compatible and delegate to the
     * terminal-result-only boundary above.
     */
    public fun read(
        request: NfcReadRequest,
        progressObserver: NfcReadProgressObserver,
        callback: (NfcReadResult) -> Unit,
    ): CancellableOperation = read(request, callback)
}

/** Payload-free progress emitted by the NFC engine; it carries no tag, key, APDU, or LDS data. */
public enum class NfcReadProgress {
    TAG_DETECTED,
    CONNECTING,
    READING,
}

/** Optional observation boundary for real NFC progress. */
public fun interface NfcReadProgressObserver {
    public fun onProgress(progress: NfcReadProgress)

    public companion object {
        public val NONE: NfcReadProgressObserver = NfcReadProgressObserver { }
    }
}

/** Coarse protocol stages accepted by privacy-safe debug diagnostics. */
public enum class NfcDiagnosticStage {
    READER_MODE,
    TAG_DISCOVERY,
    ISO_DEP,
    CARD_ACCESS,
    PACE,
    BAC,
    DG1,
    PASSIVE_AUTHENTICATION,
    DG14,
    CHIP_AUTHENTICATION,
    COMPLETE,
}

/** Closed diagnostic status containing no exception text or document data. */
public enum class NfcDiagnosticStatus {
    STARTED,
    SUCCEEDED,
    FAILED,
}

/** Closed authenticity observation suitable for payload-free device diagnostics. */
public enum class NfcDiagnosticObservation {
    NONE,
    PASSIVE_AUTH_VALID,
    PASSIVE_AUTH_FAILED,
    PASSIVE_AUTH_UNAVAILABLE,
    PASSIVE_AUTH_UNSUPPORTED,
    PASSIVE_AUTH_TECHNICAL_ERROR,
    DG14_HASH_VALID,
    DG14_HASH_FAILED,
    DG14_HASH_MISSING,
    DG14_HASH_UNSUPPORTED,
    CHIP_AUTH_SUCCEEDED,
    CHIP_AUTH_FAILED,
    CHIP_AUTH_NOT_PERFORMED,
    CHIP_AUTH_PREREQUISITE_MISSING,
    CHIP_AUTH_UNSUPPORTED,
    CHIP_AUTH_SECURE_MESSAGING_FAILED,
    CHIP_AUTH_TECHNICAL_ERROR,
}

/**
 * Privacy-safe NFC diagnostic observation.
 *
 * Only closed stage/status values and an optional predefined error code are representable. Raw
 * MRZ values, access keys, APDUs, tag identifiers, certificates, and LDS data are structurally
 * absent.
 */
public data class NfcDiagnosticEvent(
    public val stage: NfcDiagnosticStage,
    public val status: NfcDiagnosticStatus,
    public val failure: NfcFailure? = null,
    public val observation: NfcDiagnosticObservation = NfcDiagnosticObservation.NONE,
) {
    init {
        require((status == NfcDiagnosticStatus.FAILED) == (failure != null)) {
            "Only failed NFC diagnostics may contain a predefined failure."
        }
    }
}

/** Optional local-only diagnostic boundary. */
public fun interface NfcDiagnosticSink {
    public fun record(event: NfcDiagnosticEvent)

    public companion object {
        public val NONE: NfcDiagnosticSink = NfcDiagnosticSink { }
    }
}

/** Passive-authentication observation; this is not a general authenticity claim. */
public enum class PassiveAuthenticationObservation {
    VALID,
    FAILED,
    NOT_PERFORMED,
    UNAVAILABLE,
    UNSUPPORTED,
    TECHNICAL_ERROR,
}

/** Fresh proof-of-possession observation for a public key bound through Passive Authentication. */
public enum class ChipAuthenticationObservation {
    SUCCEEDED,
    AUTHENTICATION_FAILED,
    NOT_PERFORMED,
    PREREQUISITE_MISSING,
    UNSUPPORTED,
    SECURE_MESSAGING_FAILED,
    TECHNICAL_ERROR,
}

/** Safe chip-validation observation with optional portrait material. */
public class ChipValidationObservation(
    public val dg1Available: Boolean,
    public val dg2Available: Boolean,
    public val passiveAuthentication: PassiveAuthenticationObservation,
    public val chipAuthentication: ChipAuthenticationObservation = ChipAuthenticationObservation.NOT_PERFORMED,
    public val portrait: ChipPortraitArtifact? = null,
) {
    init {
        require(!dg2Available || portrait != null) { "DG2 availability requires portrait material." }
    }

    override fun toString(): String =
        "ChipValidationObservation(dg1Available=$dg1Available, dg2Available=$dg2Available, " +
            "passiveAuthentication=$passiveAuthentication, chipAuthentication=$chipAuthentication, " +
            "portrait=${if (portrait == null) "absent" else Redaction.MARKER})"
}

/** Result of chip validation without product-policy interpretation. */
public sealed interface ChipValidationResult {
    public data class Validated(
        public val observation: ChipValidationObservation,
    ) : ChipValidationResult

    public data class Failed(
        public val error: IdvError,
    ) : ChipValidationResult
}

/** External chip-data validation boundary. */
public fun interface ChipValidationEngine {
    public fun validate(artifact: ChipDataArtifact): ChipValidationResult
}

/** Printed/chip comparison observation without either compared value. */
public sealed interface PrintedChipComparisonResult {
    public data object Match : PrintedChipComparisonResult

    public data object Mismatch : PrintedChipComparisonResult

    public data object Inconclusive : PrintedChipComparisonResult

    public data class Failed(
        public val error: IdvError,
    ) : PrintedChipComparisonResult
}

/** External printed-versus-chip comparison boundary. */
public fun interface PrintedChipComparisonEngine {
    public fun compare(
        printed: PrintedPassportData,
        chip: ChipDataArtifact,
    ): PrintedChipComparisonResult
}

private fun ByteArray?.contentEqualsNullable(other: ByteArray?): Boolean =
    when {
        this == null -> other == null
        other == null -> false
        else -> contentEquals(other)
    }

internal data class MrzAccessFields(
    val documentNumber: String,
    val dateOfBirth: String,
    val expiryDate: String,
) {
    override fun toString(): String = "MrzAccessFields(${Redaction.MARKER})"

    companion object {
        fun encode(
            documentNumber: String,
            dateOfBirth: String,
            expiryDate: String,
        ): String =
            canonicalDocumentNumber(documentNumber) +
                canonicalDate(dateOfBirth, "dateOfBirth") +
                canonicalDate(expiryDate, "expiryDate")

        fun decode(value: String): MrzAccessFields? {
            if (value.length != ACCESS_LENGTH || !value.all(::isMrzCharacter)) return null
            val fields =
                MrzAccessFields(
                    documentNumber = value.substring(0, DOCUMENT_NUMBER_LENGTH),
                    dateOfBirth = value.substring(DOCUMENT_NUMBER_LENGTH, DOCUMENT_NUMBER_LENGTH + DATE_LENGTH),
                    expiryDate = value.substring(DOCUMENT_NUMBER_LENGTH + DATE_LENGTH),
                )
            return fields.takeIf { it.dateOfBirth.all(Char::isDigit) && it.expiryDate.all(Char::isDigit) }
        }
    }
}

internal data class MrzComparisonFields(
    val documentNumber: String,
    val nationality: String,
    val dateOfBirth: String,
    val expiryDate: String,
) {
    override fun toString(): String = "MrzComparisonFields(${Redaction.MARKER})"

    companion object {
        fun encode(
            documentNumber: String,
            nationality: String,
            dateOfBirth: String,
            expiryDate: String,
        ): String =
            canonicalDocumentNumber(documentNumber) +
                canonicalNationality(nationality) +
                canonicalDate(dateOfBirth, "dateOfBirth") +
                canonicalDate(expiryDate, "expiryDate")

        fun decode(value: String): MrzComparisonFields? {
            if (value.length != COMPARISON_LENGTH || !value.all(::isMrzCharacter)) return null
            val fields =
                MrzComparisonFields(
                    documentNumber = value.substring(0, DOCUMENT_NUMBER_LENGTH),
                    nationality = value.substring(DOCUMENT_NUMBER_LENGTH, DOCUMENT_NUMBER_LENGTH + NATIONALITY_LENGTH),
                    dateOfBirth =
                        value.substring(
                            DOCUMENT_NUMBER_LENGTH + NATIONALITY_LENGTH,
                            DOCUMENT_NUMBER_LENGTH + NATIONALITY_LENGTH + DATE_LENGTH,
                        ),
                    expiryDate = value.substring(DOCUMENT_NUMBER_LENGTH + NATIONALITY_LENGTH + DATE_LENGTH),
                )
            return fields.takeIf {
                it.nationality.all { character -> character in 'A'..'Z' || character == '<' } &&
                    it.dateOfBirth.all(Char::isDigit) &&
                    it.expiryDate.all(Char::isDigit)
            }
        }
    }
}

private fun canonicalDocumentNumber(value: String): String {
    val canonical = value.uppercase().trimEnd('<').padEnd(DOCUMENT_NUMBER_LENGTH, '<')
    require(canonical.length == DOCUMENT_NUMBER_LENGTH && canonical.all(::isMrzCharacter)) {
        "documentNumber must contain at most nine MRZ characters"
    }
    return canonical
}

private fun canonicalNationality(value: String): String {
    val canonical = value.uppercase()
    require(canonical.length == NATIONALITY_LENGTH && canonical.all { it in 'A'..'Z' || it == '<' }) {
        "nationality must contain exactly three MRZ letters"
    }
    return canonical
}

private fun canonicalDate(
    value: String,
    name: String,
): String {
    require(value.length == DATE_LENGTH && value.all(Char::isDigit)) { "$name must use YYMMDD digits" }
    return value
}

private fun isMrzCharacter(value: Char): Boolean = value in 'A'..'Z' || value in '0'..'9' || value == '<'

private const val DOCUMENT_NUMBER_LENGTH: Int = 9
private const val NATIONALITY_LENGTH: Int = 3
private const val DATE_LENGTH: Int = 6
private const val ACCESS_LENGTH: Int = DOCUMENT_NUMBER_LENGTH + DATE_LENGTH + DATE_LENGTH
private const val COMPARISON_LENGTH: Int = DOCUMENT_NUMBER_LENGTH + NATIONALITY_LENGTH + DATE_LENGTH + DATE_LENGTH
