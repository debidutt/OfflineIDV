package com.ing.offlineidv.core.config

import com.ing.offlineidv.core.error.ConfigurationFailure
import com.ing.offlineidv.core.error.IdvError
import com.ing.offlineidv.core.result.IdvResult
import com.ing.offlineidv.core.verification.VerificationRequirement
import java.time.Duration

/** Explicit runtime mode. Production is always the default. */
public enum class IdvMode {
    PRODUCTION,
    DEMO,
}

/** Synthetic scenario selected only when [IdvMode.DEMO] is explicitly configured. */
public enum class DemoScenario {
    SUCCESS,
    INVALID_MRZ,
    MRZ_AMBIGUITY,
    EXPIRED_DOCUMENT,
    NFC_TIMEOUT_THEN_SUCCESS,
    NFC_TIMEOUT_EXHAUSTED,
    NFC_UNAVAILABLE,
    CHIP_MISMATCH,
    PASSIVE_AUTH_FAILURE,
    SELFIE_QUALITY_FAILURE,
    NFC_TIMEOUT,
    CHIP_DATA_MISMATCH,
    FACE_MISMATCH,
    FACE_INCONCLUSIVE,
    TECHNICAL_FAILURE,
    USER_CANCELLED,
    SESSION_EXPIRED,
    CANCELLATION,
}

/**
 * Immutable SDK configuration with explicit production and demo factories.
 *
 * The default factory cannot silently enable Demo Mode. A future composition root must also keep
 * demo implementations out of production release bindings.
 */
public class IdvConfig private constructor(
    public val mode: IdvMode,
    public val demoScenario: DemoScenario?,
    public val sessionTimeout: Duration,
    requirements: Set<VerificationRequirement>,
) {
    public val requirements: Set<VerificationRequirement> = requirements.toSet()

    override fun toString(): String =
        "IdvConfig(mode=$mode, demoScenario=$demoScenario, " +
            "sessionTimeout=$sessionTimeout, requirements=$requirements)"

    public companion object {
        private val defaultRequirements: Set<VerificationRequirement> =
            setOf(
                VerificationRequirement.MRZ_CHECKSUM,
                VerificationRequirement.NFC_READ,
                VerificationRequirement.MRZ_CHIP_CONSISTENCY,
                VerificationRequirement.FACE_MATCH,
            )

        /** Creates production configuration; this is the safe default entry point. */
        public fun production(
            sessionTimeout: Duration = Duration.ofMinutes(10),
            requirements: Set<VerificationRequirement> = defaultRequirements,
        ): IdvResult<IdvConfig> = create(IdvMode.PRODUCTION, null, sessionTimeout, requirements)

        /** Creates explicitly labelled synthetic Demo Mode configuration. */
        public fun demo(
            scenario: DemoScenario,
            sessionTimeout: Duration = Duration.ofMinutes(10),
            requirements: Set<VerificationRequirement> = defaultRequirements,
        ): IdvResult<IdvConfig> = create(IdvMode.DEMO, scenario, sessionTimeout, requirements)

        private fun create(
            mode: IdvMode,
            scenario: DemoScenario?,
            sessionTimeout: Duration,
            requirements: Set<VerificationRequirement>,
        ): IdvResult<IdvConfig> {
            if (sessionTimeout.isZero || sessionTimeout.isNegative) {
                return IdvResult.Failure(
                    IdvError.Configuration(ConfigurationFailure.INVALID_SESSION_TIMEOUT),
                )
            }
            if (requirements.isEmpty()) {
                return IdvResult.Failure(
                    IdvError.Configuration(ConfigurationFailure.EMPTY_REQUIREMENTS),
                )
            }
            return IdvResult.Success(
                IdvConfig(mode, scenario, sessionTimeout, requirements),
            )
        }
    }
}
