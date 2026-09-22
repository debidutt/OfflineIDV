package com.ing.offlineidv.verification.model

/** Safe NFC validation summary with no chip payload or data-group content. */
public class ChipValidationSummary(
    public val dg1Available: Boolean,
    public val dg2Available: Boolean,
    public val passiveAuthentication: PassiveAuthenticationStatus,
    public val chipAuthentication: ChipAuthenticationStatus = ChipAuthenticationStatus.NOT_PERFORMED,
    public val portraitReference: VerificationArtifactReference? = null,
) {
    init {
        require(portraitReference == null || portraitReference.kind == VerificationArtifactKind.CHIP_PORTRAIT) {
            "portraitReference must address a chip portrait"
        }
        require(!dg2Available || portraitReference != null) {
            "A DG2 availability claim requires an opaque portrait reference."
        }
    }

    override fun equals(other: Any?): Boolean =
        other is ChipValidationSummary &&
            dg1Available == other.dg1Available &&
            dg2Available == other.dg2Available &&
            passiveAuthentication == other.passiveAuthentication &&
            chipAuthentication == other.chipAuthentication &&
            portraitReference == other.portraitReference

    override fun hashCode(): Int {
        var result = dg1Available.hashCode()
        result = 31 * result + dg2Available.hashCode()
        result = 31 * result + passiveAuthentication.hashCode()
        result = 31 * result + chipAuthentication.hashCode()
        return 31 * result + (portraitReference?.hashCode() ?: 0)
    }

    override fun toString(): String =
        "ChipValidationSummary(dg1Available=$dg1Available, dg2Available=$dg2Available, " +
            "passiveAuthentication=$passiveAuthentication, chipAuthentication=$chipAuthentication, " +
            "portraitReference=$portraitReference)"
}
