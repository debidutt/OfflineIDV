package com.ing.offlineidv.nfc.real

/** Library-independent description of one advertised PACE suite. */
internal data class PaceSuiteDescriptor(
    val oid: String,
    val parameterId: Int,
)

/** Finite CardAccess parse observation used before access-control selection. */
internal sealed interface CardAccessObservation {
    data object Absent : CardAccessObservation

    data object Malformed : CardAccessObservation

    data class Advertised(
        val paceSuites: List<PaceSuiteDescriptor>,
    ) : CardAccessObservation
}

/** Access action only; it is neither retry advice nor a verification outcome. */
internal sealed interface PassportAccessDecision {
    data class UsePace(
        val suite: PaceSuiteDescriptor,
    ) : PassportAccessDecision

    data object UseBac : PassportAccessDecision

    data object Unsupported : PassportAccessDecision

    data object Malformed : PassportAccessDecision
}

/** Deterministic PACE-first selection with an explicit, narrow BAC exception. */
internal object PassportAccessSelector {
    fun select(
        observation: CardAccessObservation,
        allowBacWhenNoCompatiblePace: Boolean,
    ): PassportAccessDecision =
        when (observation) {
            CardAccessObservation.Absent -> {
                if (allowBacWhenNoCompatiblePace) PassportAccessDecision.UseBac else PassportAccessDecision.Unsupported
            }

            CardAccessObservation.Malformed -> {
                PassportAccessDecision.Malformed
            }

            is CardAccessObservation.Advertised -> {
                observation.paceSuites
                    .asSequence()
                    .filter(::isReviewed)
                    .sortedWith(compareBy<PaceSuiteDescriptor>({ pacePriority(it.oid) }, { it.parameterId }))
                    .firstOrNull()
                    ?.let(PassportAccessDecision::UsePace)
                    ?: if (allowBacWhenNoCompatiblePace) {
                        PassportAccessDecision.UseBac
                    } else {
                        PassportAccessDecision.Unsupported
                    }
            }
        }

    private fun isReviewed(suite: PaceSuiteDescriptor): Boolean =
        suite.oid in REVIEWED_PACE_OIDS && suite.parameterId in REVIEWED_PARAMETER_IDS

    private fun pacePriority(oid: String): Int = REVIEWED_PACE_OIDS.indexOf(oid)

    /**
     * ECDH generic-mapping with AES-CBC-CMAC only, strongest key size first. The list is closed:
     * unknown OIDs, integrated mapping, DH, and 3DES are never selected.
     */
    private val REVIEWED_PACE_OIDS: List<String> =
        listOf(
            "0.4.0.127.0.7.2.2.4.2.4",
            "0.4.0.127.0.7.2.2.4.2.3",
            "0.4.0.127.0.7.2.2.4.2.2",
        )

    /** Standardized elliptic-curve domain parameters from BSI TR-03110 table 6. */
    private val REVIEWED_PARAMETER_IDS: IntRange = 8..18
}
