package com.ing.offlineidv.nfc

import com.ing.offlineidv.core.concurrency.CancellableOperation
import com.ing.offlineidv.core.error.IdvError
import com.ing.offlineidv.core.security.Redaction
import com.ing.offlineidv.core.session.IdvSessionId

/** Sensitive chip-access material retained only behind the NFC engine boundary. */
public class PassportAccessKey(
    value: String,
) : AutoCloseable {
    private val value: CharArray = value.toCharArray()

    /** Supplies the access material only inside a trusted NFC boundary. */
    public fun <R> useValue(block: (String) -> R): R = block(String(value))

    override fun equals(other: Any?): Boolean = other is PassportAccessKey && value.contentEquals(other.value)

    override fun hashCode(): Int = value.contentHashCode()

    /** Overwrites the mutable Atlas-owned representation. */
    override fun close() {
        value.fill('\u0000')
    }

    override fun toString(): String = "PassportAccessKey(${Redaction.MARKER})"
}

/** Printed identity representation retained behind the comparison-engine boundary. */
public class PrintedPassportData(
    value: String,
) : AutoCloseable {
    private val value: CharArray = value.toCharArray()

    /** Supplies printed comparison material only inside a trusted comparison boundary. */
    public fun <R> useValue(block: (String) -> R): R = block(String(value))

    override fun equals(other: Any?): Boolean = other is PrintedPassportData && value.contentEquals(other.value)

    override fun hashCode(): Int = value.contentHashCode()

    /** Overwrites the mutable Atlas-owned representation. */
    override fun close() {
        value.fill('\u0000')
    }

    override fun toString(): String = "PrintedPassportData(${Redaction.MARKER})"
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
) : AutoCloseable {
    private val dg1Value: CharArray = dg1Value.toCharArray()
    private val portraitBytes: ByteArray? = portraitBytes?.copyOf()

    /** Creates deterministic fake chip material without security claims. */
    public constructor(value: String) : this(value, null, PassiveAuthenticationObservation.NOT_PERFORMED)

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
            passiveAuthentication == other.passiveAuthentication

    override fun hashCode(): Int =
        31 * (31 * dg1Value.contentHashCode() + (portraitBytes?.contentHashCode() ?: 0)) +
            passiveAuthentication.hashCode()

    /** Overwrites Atlas-owned DG1 and DG2 copies. */
    override fun close() {
        dg1Value.fill('\u0000')
        portraitBytes?.fill(0)
    }

    override fun toString(): String = "ChipDataArtifact(${Redaction.MARKER})"

    internal companion object {
        fun fromRead(
            dg1Value: String,
            portraitBytes: ByteArray?,
            passiveAuthentication: PassiveAuthenticationObservation,
        ): ChipDataArtifact = ChipDataArtifact(dg1Value, portraitBytes, passiveAuthentication)
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

/** Safe chip-validation observation with optional portrait material. */
public class ChipValidationObservation(
    public val dg1Available: Boolean,
    public val dg2Available: Boolean,
    public val passiveAuthentication: PassiveAuthenticationObservation,
    public val portrait: ChipPortraitArtifact? = null,
) {
    init {
        require(!dg2Available || portrait != null) { "DG2 availability requires portrait material." }
    }

    override fun toString(): String =
        "ChipValidationObservation(dg1Available=$dg1Available, dg2Available=$dg2Available, " +
            "passiveAuthentication=$passiveAuthentication, portrait=${if (portrait == null) "absent" else Redaction.MARKER})"
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
