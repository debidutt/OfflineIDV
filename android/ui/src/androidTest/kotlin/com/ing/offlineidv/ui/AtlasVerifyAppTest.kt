package com.ing.offlineidv.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.ing.offlineidv.accessibility.AtlasAccessibility
import com.ing.offlineidv.core.config.DemoScenario
import com.ing.offlineidv.verification.model.VerificationOutcome
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

public class AtlasVerifyAppTest {
    @get:Rule
    public val composeRule = createComposeRule()

    @Test
    public fun welcomeShowsProductAndSdkIdentity() {
        show(AtlasUiState.Welcome)

        composeRule.onNodeWithText("Atlas Verify").assertExists()
        composeRule.onNodeWithText("Project Atlas").assertExists()
        composeRule.onNodeWithText("SDK · Atlas Offline Identity Verification SDK").assertExists()
    }

    @Test
    public fun demoModeRemainsVisiblyDisclosedAtHostLevel() {
        show(AtlasUiState.Welcome)

        composeRule.onNodeWithTag(AtlasTestTags.OFFLINE_BADGE).assertExists()
        composeRule.onNodeWithTag(AtlasTestTags.DEMO_BADGE).assertExists()
        composeRule.onNodeWithText("DEMO MODE — not a production identity or authenticity claim").assertExists()
    }

    @Test
    public fun realModeDisclosesResidencePermitNfcScopeOnWelcome() {
        show(AtlasUiState.Welcome, AtlasRuntimeMode.REAL_ANDROID)

        composeRule
            .onNodeWithText(
                "Residence permits use three-line TD1 checks followed by protected NFC DG1 " +
                    "consistency checking; face checks are not performed.",
            ).assertExists()
        composeRule
            .onNodeWithText("DG1 CONSISTENCY ONLY — NO CHIP-AUTHENTICITY OR FACE CHECK")
            .assertExists()
    }

    @Test
    public fun startButtonDispatchesStart() {
        val actions = show(AtlasUiState.Welcome)

        composeRule.onNodeWithTag(AtlasTestTags.START).performClick()

        assertEquals(listOf(AtlasUiAction.Start), actions)
    }

    @Test
    public fun scenarioSelectorGroupsSyntheticOptions() {
        show(AtlasUiState.ScenarioSelector(DemoScenario.SUCCESS))

        listOf("Success", "Document", "NFC", "Face", "Lifecycle").forEach { label ->
            composeRule.onNodeWithText(label, useUnmergedTree = true).assertExists()
        }
    }

    @Test
    public fun passportIsAvailable() {
        show(AtlasUiState.DocumentSelection)

        composeRule.onNodeWithTag(AtlasTestTags.PASSPORT).assertHasClickAction()
    }

    @Test
    public fun demoModeKeepsNonPassportDocumentsUnavailable() {
        show(AtlasUiState.DocumentSelection)

        composeRule.onNodeWithTag(AtlasTestTags.NATIONAL_ID).assertIsNotEnabled()
        composeRule.onNodeWithTag(AtlasTestTags.RESIDENCE_PERMIT).assertIsNotEnabled()
        composeRule.onAllNodesWithText("Coming later").fetchSemanticsNodes().also { assertEquals(1, it.size) }
        composeRule.onNodeWithText("Available in Real Android Mode").assertExists()
    }

    @Test
    public fun realModeEnablesNetherlandsResidencePermit() {
        val actions = show(AtlasUiState.DocumentSelection, AtlasRuntimeMode.REAL_ANDROID)

        composeRule.onNodeWithTag(AtlasTestTags.RESIDENCE_PERMIT).assertIsEnabled().performClick()

        assertEquals(listOf(AtlasUiAction.SelectResidencePermit), actions)
        composeRule
            .onNodeWithText("Netherlands permit · three-line TD1 MRZ · NFC chip consistency")
            .assertExists()
    }

    @Test
    public fun residencePermitInstructionsDiscloseLimitedNfcScope() {
        show(AtlasUiState.ResidencePermitInstructions, AtlasRuntimeMode.REAL_ANDROID)

        composeRule.onNodeWithText("Make sure all three MRZ lines are visible.").assertExists()
        composeRule
            .onNodeWithText(
                "After the MRZ check, this flow reads bounded DG1 identity fields from the contactless chip " +
                    "and compares them with the printed MRZ. Face and chip-authenticity checks are not performed.",
            ).assertExists()
    }

    @Test
    public fun passportInstructionsShowAllCaptureGuidance() {
        show(AtlasUiState.PassportInstructions)

        composeRule.onNodeWithText("Place the passport on a flat surface.").assertExists()
        composeRule.onNodeWithText("Keep all four corners inside the frame.").assertExists()
        composeRule.onNodeWithText("Avoid glare and strong shadows.").assertExists()
        composeRule.onNodeWithText("Make sure the two MRZ lines are visible.").assertExists()
    }

    @Test
    public fun captureButtonDispatchesExistingCaptureIntent() {
        val actions = show(AtlasUiState.DocumentCapture(progress(0)))

        composeRule.onNodeWithTag(AtlasTestTags.CAPTURE).performScrollTo().performClick()

        assertEquals(listOf(AtlasUiAction.CaptureDocument), actions)
    }

    @Test
    public fun processingUsesNeutralOnDeviceCopyInBothModes() {
        val mode = mutableStateOf(AtlasRuntimeMode.DEMO)
        composeRule.setContent {
            AtlasVerifyApp(
                state = AtlasUiState.Processing("Checking the MRZ", "Checking structure.", progress(1)),
                runtimeMode = mode.value,
                onAction = {},
            )
        }

        composeRule.onNodeWithText("Checking the MRZ").assertExists()
        composeRule.onNodeWithText("On-device processing").assertExists()
        composeRule.onNodeWithTag(AtlasTestTags.PROGRESS).assertExists()

        composeRule.runOnUiThread { mode.value = AtlasRuntimeMode.REAL_ANDROID }
        composeRule.onNodeWithText("On-device processing").assertExists()
    }

    @Test
    public fun mrzScreenShowsOnlySafeEvidence() {
        show(AtlasUiState.Nfc(progress(2), listOf(confirmed("MRZ check digits"))))

        composeRule.onNodeWithText("Confirmed · MRZ check digits").assertExists()
        composeRule.onNodeWithText("Passport data extracted").assertExists()
    }

    @Test
    public fun nfcScreenDisclosesSimulation() {
        show(AtlasUiState.Nfc(progress(2), emptyList()))

        composeRule.onNodeWithText("Simulated NFC — no NFC hardware used. No authenticity claim is made.").assertExists()
        composeRule.onNodeWithContentDescription(AtlasAccessibility.NFC_SIMULATION).assertExists()
    }

    @Test
    public fun realNfcScreenShowsHardwareGuidanceWithoutDemoClaim() {
        composeRule.setContent {
            AtlasVerifyApp(
                state = AtlasUiState.Nfc(progress(2), emptyList()),
                runtimeMode = AtlasRuntimeMode.REAL_ANDROID,
                nfcAvailability = AtlasNfcAvailability.AVAILABLE,
                onAction = {},
            )
        }

        composeRule.onNodeWithText("Ready to scan").assertExists()
        composeRule.onNodeWithText("Hold the top or back of your phone against the contactless document.").assertExists()
        composeRule.onNodeWithContentDescription(AtlasAccessibility.NFC_READER).assertExists()
        composeRule.onNodeWithText("Simulated NFC — no NFC hardware used. No authenticity claim is made.").assertDoesNotExist()
    }

    @Test
    public fun realNfcScreenExplainsDisabledHardwareSafely() {
        composeRule.setContent {
            AtlasVerifyApp(
                state = AtlasUiState.Nfc(progress(2), emptyList()),
                runtimeMode = AtlasRuntimeMode.REAL_ANDROID,
                nfcAvailability = AtlasNfcAvailability.DISABLED,
                onAction = {},
            )
        }

        composeRule.onNodeWithText("NFC is turned off. Enable NFC in Android settings, then return to continue.").assertExists()
        composeRule.onNodeWithText("Ready to scan").assertExists()
    }

    @Test
    public fun realNfcCommunicationShowsIndeterminateProgressWithoutPercentage() {
        composeRule.setContent {
            AtlasVerifyApp(
                state =
                    AtlasUiState.Nfc(
                        progress(2),
                        emptyList(),
                        AtlasNfcScanStatus.SCAN_IN_PROGRESS,
                    ),
                runtimeMode = AtlasRuntimeMode.REAL_ANDROID,
                nfcAvailability = AtlasNfcAvailability.AVAILABLE,
                onAction = {},
            )
        }

        composeRule.onNodeWithText("Scan in progress").assertExists()
        composeRule.onNodeWithTag(AtlasTestTags.NFC_STATUS).assertExists()
        composeRule.onNodeWithText("Start chip scan").assertDoesNotExist()
    }

    @Test
    public fun realNfcStartDispatchesAndActiveDiscoveryHidesStartButton() {
        val actions = mutableListOf<AtlasUiAction>()
        val state = mutableStateOf(AtlasUiState.Nfc(progress(2), emptyList()))
        composeRule.setContent {
            AtlasVerifyApp(
                state = state.value,
                runtimeMode = AtlasRuntimeMode.REAL_ANDROID,
                nfcAvailability = AtlasNfcAvailability.AVAILABLE,
                onAction = actions::add,
            )
        }

        composeRule.onNodeWithText("Start chip scan").performClick()
        assertEquals(listOf(AtlasUiAction.StartNfc), actions)

        state.value = state.value.copy(canStartScan = false)
        composeRule.onNodeWithText("Ready to scan").assertExists()
        composeRule.onNodeWithText("NFC reader active. Hold the document against the phone now.").assertExists()
        composeRule.onNodeWithText("Start chip scan").assertDoesNotExist()
    }

    @Test
    public fun selfieScreenRejectsLivenessClaim() {
        show(AtlasUiState.Selfie(progress(3), emptyList()))

        composeRule
            .onNodeWithText(
                "This demo performs a synthetic comparison only. It does not perform or claim liveness detection.",
            ).assertExists()
    }

    @Test
    public fun recoveryShowsSafeActions() {
        show(AtlasUiState.Recovery("Chip read needs attention", "Try safely.", true, progress(2, true)))

        composeRule.onNodeWithTag(AtlasTestTags.RETRY).assertExists()
        composeRule.onNodeWithTag(AtlasTestTags.CANCEL).assertExists()
    }

    @Test
    public fun retryDispatchesRetryAction() {
        val actions = show(AtlasUiState.Recovery("Retry", "Try safely.", true, progress(2, true)))

        composeRule.onNodeWithTag(AtlasTestTags.RETRY).performClick()

        assertEquals(listOf(AtlasUiAction.Retry), actions)
    }

    @Test
    public fun cancelDispatchesCancelAction() {
        val actions = show(AtlasUiState.DocumentCapture(progress(0)))

        composeRule.onNodeWithTag(AtlasTestTags.CANCEL).performScrollTo().performClick()

        assertEquals(listOf(AtlasUiAction.Cancel), actions)
    }

    @Test
    public fun verifiedResultHasNeutralCopy() {
        show(result(VerificationOutcome.VERIFIED, "Verification complete"))

        composeRule.onNodeWithText("Verification complete").assertExists()
    }

    @Test
    public fun rejectedResultHasNeutralCopy() {
        show(result(VerificationOutcome.REJECTED, "Verification not approved"))

        composeRule.onNodeWithText("Verification not approved").assertExists()
    }

    @Test
    public fun inconclusiveResultHasNeutralCopy() {
        show(result(VerificationOutcome.INCONCLUSIVE, "More evidence needed"))

        composeRule.onNodeWithText("More evidence needed").assertExists()
    }

    @Test
    public fun technicalFailureResultHasNeutralCopy() {
        show(result(VerificationOutcome.TECHNICAL_FAILURE, "Something went wrong"))

        composeRule.onNodeWithText("Something went wrong").assertExists()
    }

    @Test
    public fun cancelledResultHasNeutralCopy() {
        show(result(VerificationOutcome.CANCELLED, "Verification cancelled"))

        composeRule.onNodeWithText("Verification cancelled").assertExists()
    }

    @Test
    public fun expiredResultHasNeutralCopy() {
        show(result(VerificationOutcome.EXPIRED, "Session expired"))

        composeRule.onNodeWithText("Session expired").assertExists()
    }

    @Test
    public fun resultCanStartAgain() {
        val actions = show(result(VerificationOutcome.VERIFIED, "Verification complete"))

        composeRule.onNodeWithTag(AtlasTestTags.START_AGAIN).performScrollTo().performClick()

        assertEquals(listOf(AtlasUiAction.StartAgain), actions)
    }

    @Test
    public fun scenarioSelectionIsAConfigurationAction() {
        val actions = show(AtlasUiState.ScenarioSelector(DemoScenario.SUCCESS))

        composeRule.onNodeWithText("Face mismatch").performScrollTo().performClick()

        assertEquals(listOf(AtlasUiAction.SelectScenario(DemoScenario.FACE_MISMATCH)), actions)
    }

    @Test
    public fun primarySyntheticFramesHaveTalkBackDescriptions() {
        show(AtlasUiState.DocumentCapture(progress(0)))

        composeRule.onNodeWithContentDescription(AtlasAccessibility.DOCUMENT_FRAME).assertExists()
        composeRule
            .onNodeWithTag(AtlasTestTags.PROGRESS)
            .assertContentDescriptionEquals("Step 1 of 5, Document")
    }

    @Test
    public fun criticalEvidenceStatusIsNotColorOnly() {
        show(
            AtlasUiState.Result(
                outcome = VerificationOutcome.REJECTED,
                title = "Verification not approved",
                detail = "Synthetic result.",
                evidence = listOf(AtlasEvidenceItem("MRZ check digits", "Needs review.", AtlasEvidenceStatus.ATTENTION)),
            ),
        )

        composeRule.onNodeWithText("Attention · MRZ check digits").assertTextContains("Attention")
    }

    private fun show(
        state: AtlasUiState,
        runtimeMode: AtlasRuntimeMode = AtlasRuntimeMode.DEMO,
    ): MutableList<AtlasUiAction> {
        val actions = mutableListOf<AtlasUiAction>()
        composeRule.setContent { AtlasVerifyApp(state = state, onAction = actions::add, runtimeMode = runtimeMode) }
        return actions
    }

    private fun progress(
        current: Int,
        attention: Boolean = false,
    ): AtlasProgress =
        AtlasProgress(
            listOf("Document", "MRZ", "Chip", "Face", "Decision").mapIndexed { index, label ->
                val status =
                    when {
                        index < current -> AtlasStepStatus.COMPLETE
                        index == current && attention -> AtlasStepStatus.NEEDS_ATTENTION
                        index == current -> AtlasStepStatus.CURRENT
                        else -> AtlasStepStatus.UPCOMING
                    }
                AtlasProgressStep(label, status)
            },
        )

    private fun confirmed(title: String): AtlasEvidenceItem =
        AtlasEvidenceItem(title, "Safe synthetic signal.", AtlasEvidenceStatus.CONFIRMED)

    private fun result(
        outcome: VerificationOutcome,
        title: String,
    ): AtlasUiState.Result = AtlasUiState.Result(outcome, title, "Synthetic offline result.", emptyList())
}
