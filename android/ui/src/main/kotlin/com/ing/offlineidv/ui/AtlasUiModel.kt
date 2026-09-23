package com.ing.offlineidv.ui

import com.ing.offlineidv.core.config.DemoScenario
import com.ing.offlineidv.verification.model.VerificationOutcome

/** Presentation-only screen model. It cannot carry artifact references or identity fields. */
public sealed interface AtlasUiState {
    public data object Welcome : AtlasUiState

    public data class ScenarioSelector(
        public val selectedScenario: DemoScenario,
    ) : AtlasUiState

    public data object DocumentSelection : AtlasUiState

    public data object PassportInstructions : AtlasUiState

    public data object ResidencePermitInstructions : AtlasUiState

    public data class DocumentCapture(
        public val progress: AtlasProgress,
    ) : AtlasUiState

    public data class Processing(
        public val title: String,
        public val detail: String,
        public val progress: AtlasProgress,
    ) : AtlasUiState

    public data class Nfc(
        public val progress: AtlasProgress,
        public val evidence: List<AtlasEvidenceItem>,
        public val scanStatus: AtlasNfcScanStatus = AtlasNfcScanStatus.READY_TO_SCAN,
        public val canStartScan: Boolean = scanStatus == AtlasNfcScanStatus.READY_TO_SCAN,
    ) : AtlasUiState

    public data class Selfie(
        public val progress: AtlasProgress,
        public val evidence: List<AtlasEvidenceItem>,
    ) : AtlasUiState

    public data class Recovery(
        public val title: String,
        public val detail: String,
        public val canRetry: Boolean,
        public val progress: AtlasProgress,
        public val nfcScanStatus: AtlasNfcScanStatus? = null,
    ) : AtlasUiState

    public data class Result(
        public val outcome: VerificationOutcome,
        public val title: String,
        public val detail: String,
        public val evidence: List<AtlasEvidenceItem>,
    ) : AtlasUiState
}

/** Five safe high-level stages derived from reducer state and evidence. */
public data class AtlasProgress(
    public val steps: List<AtlasProgressStep>,
)

public data class AtlasProgressStep(
    public val label: String,
    public val status: AtlasStepStatus,
)

public enum class AtlasStepStatus {
    COMPLETE,
    CURRENT,
    UPCOMING,
    NEEDS_ATTENTION,
}

/** Finite safe evidence copy; raw inputs, scores, and identifiers are structurally absent. */
public data class AtlasEvidenceItem(
    public val title: String,
    public val detail: String,
    public val status: AtlasEvidenceStatus,
)

public enum class AtlasEvidenceStatus {
    CONFIRMED,
    ATTENTION,
    NOT_PERFORMED,
}

/** Explicit presentation/runtime choice available only before a session starts. */
public enum class AtlasRuntimeMode {
    DEMO,
    REAL_ANDROID,
}

/** Safe host-supplied NFC hardware status; it contains no tag or session data. */
public enum class AtlasNfcAvailability {
    UNKNOWN,
    UNAVAILABLE,
    DISABLED,
    AVAILABLE,
}

/** Finite presentation-only NFC status derived from reducer state and predefined errors. */
public enum class AtlasNfcScanStatus {
    STARTING_READER,
    READY_TO_SCAN,
    CHIP_DETECTED,
    CONNECTING,
    SCAN_IN_PROGRESS,
    SCAN_COMPLETE,
    CONNECTION_LOST,
    AUTHENTICATION_FAILED,
    UNSUPPORTED_CHIP,
    TIMED_OUT,
    SCAN_FAILED,
}

/** User intents accepted by the app-demo controller. */
public sealed interface AtlasUiAction {
    public data class SelectRuntimeMode(
        public val mode: AtlasRuntimeMode,
    ) : AtlasUiAction

    public data object Start : AtlasUiAction

    public data object ShowScenarios : AtlasUiAction

    public data object CloseScenarios : AtlasUiAction

    public data class SelectScenario(
        public val scenario: DemoScenario,
    ) : AtlasUiAction

    public data object SelectPassport : AtlasUiAction

    public data object SelectResidencePermit : AtlasUiAction

    public data object ContinuePassportInstructions : AtlasUiAction

    public data object ContinueResidencePermitInstructions : AtlasUiAction

    public data object CaptureDocument : AtlasUiAction

    public data object StartNfc : AtlasUiAction

    public data object CaptureSelfie : AtlasUiAction

    public data object Retry : AtlasUiAction

    public data object Cancel : AtlasUiAction

    public data object StartAgain : AtlasUiAction

    public data object ReturnHome : AtlasUiAction
}

/** Safe catalog row used only before a session starts. */
public data class AtlasScenarioItem(
    public val scenario: DemoScenario,
    public val title: String,
    public val description: String,
)

public data class AtlasScenarioGroup(
    public val title: String,
    public val items: List<AtlasScenarioItem>,
)

/** The fifteen intentionally supported conference scenarios, with no expected outcomes. */
public object AtlasScenarioPresentationCatalog {
    public val groups: List<AtlasScenarioGroup> =
        listOf(
            AtlasScenarioGroup(
                title = "Success",
                items = listOf(item(DemoScenario.SUCCESS, "Success", "All synthetic checks complete normally.")),
            ),
            AtlasScenarioGroup(
                title = "Document",
                items =
                    listOf(
                        item(DemoScenario.INVALID_MRZ, "Invalid MRZ", "Synthetic check digits are inconsistent."),
                        item(DemoScenario.MRZ_AMBIGUITY, "MRZ ambiguity", "A safe ambiguity signal is produced."),
                        item(DemoScenario.EXPIRED_DOCUMENT, "Expired document", "The synthetic expiry date is in the past."),
                    ),
            ),
            AtlasScenarioGroup(
                title = "NFC",
                items =
                    listOf(
                        item(
                            DemoScenario.NFC_TIMEOUT_THEN_SUCCESS,
                            "Timeout, then success",
                            "The first synthetic chip read times out.",
                        ),
                        item(
                            DemoScenario.NFC_TIMEOUT_EXHAUSTED,
                            "Timeout exhausted",
                            "Synthetic chip reads continue to time out.",
                        ),
                        item(DemoScenario.NFC_UNAVAILABLE, "NFC unavailable", "The simulated capability is unavailable."),
                        item(DemoScenario.CHIP_MISMATCH, "Chip mismatch", "Printed and synthetic chip signals differ."),
                        item(
                            DemoScenario.PASSIVE_AUTH_FAILURE,
                            "Passive auth failure",
                            "The synthetic authentication observation fails.",
                        ),
                    ),
            ),
            AtlasScenarioGroup(
                title = "Face",
                items =
                    listOf(
                        item(
                            DemoScenario.SELFIE_QUALITY_FAILURE,
                            "Selfie quality failure",
                            "Synthetic selfie quality is not accepted.",
                        ),
                        item(DemoScenario.FACE_MISMATCH, "Face mismatch", "Synthetic comparison does not match."),
                        item(DemoScenario.FACE_INCONCLUSIVE, "Face inconclusive", "Synthetic comparison is inconclusive."),
                    ),
            ),
            AtlasScenarioGroup(
                title = "Lifecycle",
                items =
                    listOf(
                        item(
                            DemoScenario.TECHNICAL_FAILURE,
                            "Technical failure",
                            "A safe local processing failure is simulated.",
                        ),
                        item(DemoScenario.USER_CANCELLED, "User cancelled", "The synthetic session is cancelled."),
                        item(DemoScenario.SESSION_EXPIRED, "Session expired", "The synthetic session expiry fires."),
                    ),
            ),
        )

    public fun titleFor(scenario: DemoScenario): String = groups.flatMap(AtlasScenarioGroup::items).first { it.scenario == scenario }.title

    private fun item(
        scenario: DemoScenario,
        title: String,
        description: String,
    ): AtlasScenarioItem = AtlasScenarioItem(scenario, title, description)
}
