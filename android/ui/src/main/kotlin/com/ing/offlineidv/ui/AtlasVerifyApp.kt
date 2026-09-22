@file:Suppress("FunctionName") // Jetpack Compose public functions follow Android's PascalCase convention.

package com.ing.offlineidv.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ing.offlineidv.accessibility.AtlasAccessibility
import com.ing.offlineidv.ui.theme.AtlasTheme
import com.ing.offlineidv.verification.model.VerificationOutcome

/** Stable semantics tags used by the demo's Compose tests. */
public object AtlasTestTags {
    public const val OFFLINE_BADGE: String = "offline_badge"
    public const val DEMO_BADGE: String = "demo_badge"
    public const val START: String = "start"
    public const val SCENARIOS: String = "scenarios"
    public const val PASSPORT: String = "passport"
    public const val NATIONAL_ID: String = "national_id"
    public const val RESIDENCE_PERMIT: String = "residence_permit"
    public const val CONTINUE: String = "continue"
    public const val CAPTURE: String = "capture"
    public const val NFC: String = "nfc"
    public const val NFC_STATUS: String = "nfc_status"
    public const val SELFIE: String = "selfie"
    public const val RETRY: String = "retry"
    public const val CANCEL: String = "cancel"
    public const val START_AGAIN: String = "start_again"
    public const val HOME: String = "home"
    public const val PROGRESS: String = "progress"
    public const val EVIDENCE: String = "evidence"
    public const val RESULT: String = "result"
    public const val MODE_DEMO: String = "mode_demo"
    public const val MODE_REAL: String = "mode_real"
    public const val CAMERA_PREVIEW: String = "camera_preview"
}

/** Complete state-driven Atlas Verify surface. */
@Composable
public fun AtlasVerifyApp(
    state: AtlasUiState,
    onAction: (AtlasUiAction) -> Unit,
    modifier: Modifier = Modifier,
    runtimeMode: AtlasRuntimeMode = AtlasRuntimeMode.DEMO,
    nfcAvailability: AtlasNfcAvailability = AtlasNfcAvailability.UNKNOWN,
    cameraPreview: (@Composable () -> Unit)? = null,
) {
    AtlasTheme {
        Surface(
            modifier = modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding(),
            ) {
                AtlasHeader(runtimeMode)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    when (state) {
                        AtlasUiState.Welcome -> WelcomeScreen(runtimeMode, onAction)
                        is AtlasUiState.ScenarioSelector -> ScenarioSelectorScreen(state, onAction)
                        AtlasUiState.DocumentSelection -> DocumentSelectionScreen(runtimeMode, onAction)
                        AtlasUiState.PassportInstructions -> PassportInstructionsScreen(runtimeMode, onAction)
                        AtlasUiState.ResidencePermitInstructions -> ResidencePermitInstructionsScreen(onAction)
                        is AtlasUiState.DocumentCapture -> DocumentCaptureScreen(state, runtimeMode, cameraPreview, onAction)
                        is AtlasUiState.Processing -> ProcessingScreen(state, onAction)
                        is AtlasUiState.Nfc -> NfcScreen(state, runtimeMode, nfcAvailability, onAction)
                        is AtlasUiState.Selfie -> SelfieScreen(state, onAction)
                        is AtlasUiState.Recovery -> RecoveryScreen(state, onAction)
                        is AtlasUiState.Result -> ResultScreen(state, onAction)
                    }
                }
            }
        }
    }
}

@Composable
private fun AtlasHeader(runtimeMode: AtlasRuntimeMode) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Text("A", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("Atlas Verify", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Project Atlas", style = MaterialTheme.typography.labelSmall)
        }
        StatusBadge("OFFLINE", AtlasTestTags.OFFLINE_BADGE)
        Spacer(Modifier.width(6.dp))
        StatusBadge(if (runtimeMode == AtlasRuntimeMode.DEMO) "DEMO" else "REAL", AtlasTestTags.DEMO_BADGE)
    }
}

@Composable
private fun StatusBadge(
    label: String,
    tag: String,
) {
    Surface(
        modifier = Modifier.semantics { testTag = tag },
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun ScreenColumn(
    contentPadding: PaddingValues = PaddingValues(horizontal = 24.dp, vertical = 24.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier =
            Modifier
                .widthIn(max = 680.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        content = content,
    )
}

@Composable
private fun WelcomeScreen(
    runtimeMode: AtlasRuntimeMode,
    onAction: (AtlasUiAction) -> Unit,
) {
    ScreenColumn {
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Offline identity verification, made visible",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = "Explore the Atlas Offline Identity Verification SDK through an explicit local document flow.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        RuntimeModeSelector(runtimeMode, onAction)
        DisclosureCard(runtimeMode)
        Button(
            onClick = { onAction(AtlasUiAction.Start) },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .semantics { testTag = AtlasTestTags.START },
        ) {
            Text("Start verification")
        }
        if (runtimeMode == AtlasRuntimeMode.DEMO) {
            OutlinedButton(
                onClick = { onAction(AtlasUiAction.ShowScenarios) },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .semantics { testTag = AtlasTestTags.SCENARIOS },
            ) {
                Text("Demo scenarios")
            }
        }
        Text(
            text = "SDK · Atlas Offline Identity Verification SDK",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RuntimeModeSelector(
    runtimeMode: AtlasRuntimeMode,
    onAction: (AtlasUiAction) -> Unit,
) {
    Column(modifier = Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Execution mode", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        RuntimeModeRow(
            title = "Demo Mode",
            detail = "Deterministic synthetic conference flow",
            selected = runtimeMode == AtlasRuntimeMode.DEMO,
            tag = AtlasTestTags.MODE_DEMO,
        ) { onAction(AtlasUiAction.SelectRuntimeMode(AtlasRuntimeMode.DEMO)) }
        RuntimeModeRow(
            title = "Real Android Mode",
            detail = "CameraX, bundled OCR, and protected document-chip reading; face checks are not available yet",
            selected = runtimeMode == AtlasRuntimeMode.REAL_ANDROID,
            tag = AtlasTestTags.MODE_REAL,
        ) { onAction(AtlasUiAction.SelectRuntimeMode(AtlasRuntimeMode.REAL_ANDROID)) }
    }
}

@Composable
private fun RuntimeModeRow(
    title: String,
    detail: String,
    selected: Boolean,
    tag: String,
    onClick: () -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
                .semantics { testTag = tag },
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = null)
            Spacer(Modifier.width(10.dp))
            Column {
                Text(title, fontWeight = FontWeight.Bold)
                Text(detail, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun DisclosureCard(runtimeMode: AtlasRuntimeMode) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (runtimeMode == AtlasRuntimeMode.DEMO) {
                Text("Offline Demo Mode", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("No network, camera, NFC hardware, or real biometric processing is used.")
                Text("All content is synthetic and held in memory for the active session.")
                Text(
                    "DEMO MODE — not a production identity or authenticity claim",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            } else {
                Text("Real Android Mode", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("CameraX, bundled ML Kit text recognition, and protected NFC chip reading run locally on this device.")
                Text("Images and recognized text are held only for the active session. No network is used.")
                Text(
                    "Residence permits use three-line TD1 checks followed by protected NFC DG1 consistency checking; face checks are not performed.",
                )
                Text(
                    "DG1 CONSISTENCY ONLY — NO CHIP-AUTHENTICITY OR FACE CHECK",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun ScenarioSelectorScreen(
    state: AtlasUiState.ScenarioSelector,
    onAction: (AtlasUiAction) -> Unit,
) {
    ScreenColumn {
        ScreenTitle("Choose a demo scenario", "Scenario selection is locked after a session starts.")
        Column(modifier = Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            AtlasScenarioPresentationCatalog.groups.forEach { group ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        group.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.semantics { heading() },
                    )
                    group.items.forEach { item ->
                        ScenarioRow(item, state.selectedScenario == item.scenario, onAction)
                    }
                }
            }
        }
        Button(
            onClick = { onAction(AtlasUiAction.Start) },
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Text("Use selected scenario")
        }
        TextButton(
            onClick = { onAction(AtlasUiAction.CloseScenarios) },
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Text("Back")
        }
    }
}

@Composable
private fun ScenarioRow(
    item: AtlasScenarioItem,
    selected: Boolean,
    onAction: (AtlasUiAction) -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .selectable(
                    selected = selected,
                    role = Role.RadioButton,
                    onClick = { onAction(AtlasUiAction.SelectScenario(item.scenario)) },
                ),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
            ),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(
                selected = selected,
                onClick = null,
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(item.title, fontWeight = FontWeight.Bold)
                Text(item.description, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun DocumentSelectionScreen(
    runtimeMode: AtlasRuntimeMode,
    onAction: (AtlasUiAction) -> Unit,
) {
    ScreenColumn {
        ScreenTitle("Choose a document", "Select the document you want to inspect on this device.")
        DocumentOption(
            title = "Passport",
            subtitle = "Two-line TD3 MRZ with passport-chip flow",
            enabled = true,
            tag = AtlasTestTags.PASSPORT,
            onClick = { onAction(AtlasUiAction.SelectPassport) },
        )
        DocumentOption(
            title = "National ID",
            subtitle = "Coming later",
            enabled = false,
            tag = AtlasTestTags.NATIONAL_ID,
            onClick = {},
        )
        DocumentOption(
            title = "Residence permit",
            subtitle =
                if (runtimeMode == AtlasRuntimeMode.REAL_ANDROID) {
                    "Netherlands permit · three-line TD1 MRZ · NFC chip consistency"
                } else {
                    "Available in Real Android Mode"
                },
            enabled = runtimeMode == AtlasRuntimeMode.REAL_ANDROID,
            tag = AtlasTestTags.RESIDENCE_PERMIT,
            onClick = { onAction(AtlasUiAction.SelectResidencePermit) },
        )
        CancelButton(onAction)
    }
}

@Composable
private fun ResidencePermitInstructionsScreen(onAction: (AtlasUiAction) -> Unit) {
    ScreenColumn {
        ScreenTitle(
            "Get your residence permit ready",
            "This flow reads the three-line TD1 machine-readable zone on a Netherlands residence permit.",
        )
        Instruction("Place the permit on a flat, contrasting surface.")
        Instruction("Keep the entire side with the MRZ inside the frame.")
        Instruction("Avoid glare and strong shadows.")
        Instruction("Make sure all three MRZ lines are visible.")
        InfoNote(
            "After the MRZ check, this flow reads bounded DG1 identity fields from the contactless chip " +
                "and compares them with the printed MRZ. Face and chip-authenticity checks are not performed.",
        )
        Button(
            onClick = { onAction(AtlasUiAction.ContinueResidencePermitInstructions) },
            modifier = Modifier.fillMaxWidth().height(56.dp).semantics { testTag = AtlasTestTags.CONTINUE },
        ) {
            Text("Continue")
        }
        CancelButton(onAction)
    }
}

@Composable
private fun DocumentOption(
    title: String,
    subtitle: String,
    enabled: Boolean,
    tag: String,
    onClick: () -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(88.dp)
                .semantics { testTag = tag }
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (enabled) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant,
            ),
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(if (enabled) "▣" else "—", fontSize = 24.sp)
            Spacer(Modifier.width(16.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun PassportInstructionsScreen(
    runtimeMode: AtlasRuntimeMode,
    onAction: (AtlasUiAction) -> Unit,
) {
    ScreenColumn {
        ScreenTitle("Get your passport ready", "Good framing makes the machine-readable zone easier to inspect.")
        Instruction("Place the passport on a flat surface.")
        Instruction("Keep all four corners inside the frame.")
        Instruction("Avoid glare and strong shadows.")
        Instruction("Make sure the two MRZ lines are visible.")
        InfoNote(
            if (runtimeMode == AtlasRuntimeMode.DEMO) {
                "The next screen is synthetic. Camera access is never requested."
            } else {
                "Camera permission is requested only after you continue. You can deny it and return home safely."
            },
        )
        Button(
            onClick = { onAction(AtlasUiAction.ContinuePassportInstructions) },
            modifier = Modifier.fillMaxWidth().height(56.dp).semantics { testTag = AtlasTestTags.CONTINUE },
        ) {
            Text("Continue")
        }
        CancelButton(onAction)
    }
}

@Composable
private fun Instruction(text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text("✓", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun DocumentCaptureScreen(
    state: AtlasUiState.DocumentCapture,
    runtimeMode: AtlasRuntimeMode,
    cameraPreview: (@Composable () -> Unit)?,
    onAction: (AtlasUiAction) -> Unit,
) {
    ScreenColumn {
        ScreenTitle(
            "Frame the document",
            if (runtimeMode == AtlasRuntimeMode.DEMO) {
                "This synthetic capture never opens the device camera."
            } else {
                "Keep all four corners and every MRZ line visible. Avoid glare and hold steady."
            },
        )
        ProgressPanel(state.progress)
        DocumentFrame(runtimeMode, cameraPreview)
        Button(
            onClick = { onAction(AtlasUiAction.CaptureDocument) },
            modifier = Modifier.fillMaxWidth().height(56.dp).semantics { testTag = AtlasTestTags.CAPTURE },
        ) {
            Text(if (runtimeMode == AtlasRuntimeMode.DEMO) "Capture synthetic passport" else "Capture document")
        }
        CancelButton(onAction)
    }
}

@Composable
private fun DocumentFrame(
    runtimeMode: AtlasRuntimeMode,
    cameraPreview: (@Composable () -> Unit)?,
) {
    val primary = MaterialTheme.colorScheme.primary
    val scan = MaterialTheme.colorScheme.tertiary
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .aspectRatio(1.55f)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .semantics { contentDescription = AtlasAccessibility.DOCUMENT_FRAME },
        contentAlignment = Alignment.Center,
    ) {
        if (runtimeMode == AtlasRuntimeMode.REAL_ANDROID && cameraPreview != null) {
            Box(Modifier.fillMaxSize().semantics { testTag = AtlasTestTags.CAMERA_PREVIEW }) {
                cameraPreview()
            }
        }
        Canvas(Modifier.fillMaxSize().padding(20.dp)) {
            drawRoundRect(
                color = primary,
                style = Stroke(width = 5f),
                cornerRadius = CornerRadius(28f, 28f),
            )
            drawLine(
                color = scan,
                start = Offset(20f, size.height * 0.73f),
                end = Offset(size.width - 20f, size.height * 0.73f),
                strokeWidth = 6f,
                cap = StrokeCap.Round,
            )
            repeat(3) { line ->
                val y = size.height * (0.78f + line * 0.07f)
                drawLine(
                    color = primary.copy(alpha = 0.55f),
                    start = Offset(size.width * 0.12f, y),
                    end = Offset(size.width * 0.88f, y),
                    strokeWidth = 3f,
                )
            }
        }
        if (runtimeMode == AtlasRuntimeMode.DEMO) {
            Text("SYNTHETIC PASSPORT", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ProcessingScreen(
    state: AtlasUiState.Processing,
    onAction: (AtlasUiAction) -> Unit,
) {
    ScreenColumn {
        ScreenTitle(state.title, state.detail, announce = true)
        ProgressPanel(state.progress)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                CircularProgressIndicator(
                    modifier =
                        Modifier
                            .size(52.dp)
                            .semantics {
                                contentDescription = state.title
                                progressBarRangeInfo = androidx.compose.ui.semantics.ProgressBarRangeInfo.Indeterminate
                            },
                )
                Text("On-device processing", fontWeight = FontWeight.Bold)
                Text("No information leaves this device.", textAlign = TextAlign.Center)
            }
        }
        CancelButton(onAction)
    }
}

@Composable
private fun NfcScreen(
    state: AtlasUiState.Nfc,
    runtimeMode: AtlasRuntimeMode,
    nfcAvailability: AtlasNfcAvailability,
    onAction: (AtlasUiAction) -> Unit,
) {
    val statusCopy = state.scanStatus.presentationCopy(state.canStartScan)
    ScreenColumn {
        ScreenTitle(
            if (runtimeMode == AtlasRuntimeMode.DEMO) "Passport data extracted" else "Read document chip",
            if (runtimeMode == AtlasRuntimeMode.DEMO) {
                "Review the safe MRZ signals, then continue to the chip step."
            } else {
                "Follow the live NFC status below and keep the document close to your phone."
            },
            announce = true,
        )
        ProgressPanel(state.progress)
        EvidencePanel(state.evidence)
        NfcGraphic(
            if (runtimeMode == AtlasRuntimeMode.DEMO) {
                AtlasAccessibility.NFC_SIMULATION
            } else {
                AtlasAccessibility.NFC_READER
            },
        )
        if (runtimeMode == AtlasRuntimeMode.DEMO) {
            InfoNote("Simulated NFC — no NFC hardware used. No authenticity claim is made.")
            Text(
                "Real-world placement guidance: hold the phone still against the passport cover. " +
                    "In this demo, simply use the button below.",
            )
        } else {
            when (nfcAvailability) {
                AtlasNfcAvailability.DISABLED -> {
                    InfoNote("NFC is turned off. Enable NFC in Android settings, then return to continue.")
                }

                AtlasNfcAvailability.UNAVAILABLE -> {
                    InfoNote("NFC is not available on this device.")
                }

                AtlasNfcAvailability.AVAILABLE,
                AtlasNfcAvailability.UNKNOWN,
                -> {
                    InfoNote("Keep the document still until all required chip checks finish.")
                }
            }
        }
        if (runtimeMode == AtlasRuntimeMode.REAL_ANDROID) {
            Column(
                modifier =
                    Modifier.fillMaxWidth().semantics {
                        testTag = AtlasTestTags.NFC_STATUS
                        liveRegion = LiveRegionMode.Polite
                    },
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (state.scanStatus in setOf(AtlasNfcScanStatus.CONNECTING, AtlasNfcScanStatus.SCAN_IN_PROGRESS)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier =
                                Modifier.size(32.dp).semantics {
                                    progressBarRangeInfo =
                                        androidx.compose.ui.semantics.ProgressBarRangeInfo.Indeterminate
                                },
                        )
                        Text(statusCopy.first, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Text(statusCopy.first, fontWeight = FontWeight.Bold)
                }
                Text(
                    statusCopy.second,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (runtimeMode == AtlasRuntimeMode.DEMO || state.canStartScan) {
            Button(
                onClick = { onAction(AtlasUiAction.StartNfc) },
                modifier = Modifier.fillMaxWidth().height(56.dp).semantics { testTag = AtlasTestTags.NFC },
            ) {
                Text(if (runtimeMode == AtlasRuntimeMode.DEMO) "Simulate chip read" else "Start chip scan")
            }
        }
        CancelButton(onAction)
    }
}

private fun AtlasNfcScanStatus.presentationCopy(canStartScan: Boolean): Pair<String, String> =
    when (this) {
        AtlasNfcScanStatus.READY_TO_SCAN -> {
            if (canStartScan) {
                "Ready to scan" to "Hold the top or back of your phone against the contactless document."
            } else {
                "Ready to scan" to "NFC reader active. Hold the document against the phone now."
            }
        }

        AtlasNfcScanStatus.CHIP_DETECTED -> {
            "Chip detected" to "Keep the document against the phone while a secure connection is prepared."
        }

        AtlasNfcScanStatus.CONNECTING -> {
            "Connecting" to "Opening a secure connection to the document chip."
        }

        AtlasNfcScanStatus.SCAN_IN_PROGRESS -> {
            "Scan in progress" to "Reading and checking the required chip data on this device."
        }

        AtlasNfcScanStatus.SCAN_COMPLETE -> {
            "Scan complete" to "Chip communication completed. Checking the resulting verification evidence."
        }

        AtlasNfcScanStatus.CONNECTION_LOST -> {
            "Connection lost" to "Hold the document against the phone again and retry."
        }

        AtlasNfcScanStatus.AUTHENTICATION_FAILED -> {
            "Chip authentication failed" to "The document chip did not accept the protected-access attempt."
        }

        AtlasNfcScanStatus.UNSUPPORTED_CHIP -> {
            "Unsupported document chip" to "This document chip is not supported by this build."
        }

        AtlasNfcScanStatus.TIMED_OUT -> {
            "Chip scan timed out" to "Hold the document firmly against the phone and retry."
        }

        AtlasNfcScanStatus.SCAN_FAILED -> {
            "Chip scan failed" to "The chip scan could not complete."
        }
    }

@Composable
private fun NfcGraphic(description: String) {
    val primary = MaterialTheme.colorScheme.primary
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(164.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(118.dp)) {
            repeat(3) { index ->
                drawArc(
                    color = primary.copy(alpha = 1f - index * 0.2f),
                    startAngle = -55f,
                    sweepAngle = 110f,
                    useCenter = false,
                    topLeft = Offset(index * 13f, index * 13f),
                    size = Size(size.width - index * 26f, size.height - index * 26f),
                    style = Stroke(width = 5f, cap = StrokeCap.Round),
                )
            }
        }
        Text("NFC", fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SelfieScreen(
    state: AtlasUiState.Selfie,
    onAction: (AtlasUiAction) -> Unit,
) {
    ScreenColumn {
        ScreenTitle("Take a synthetic selfie", "Center one face in the guide and keep the framing steady.", announce = true)
        ProgressPanel(state.progress)
        EvidencePanel(state.evidence)
        SelfieFrame()
        InfoNote("This demo performs a synthetic comparison only. It does not perform or claim liveness detection.")
        Button(
            onClick = { onAction(AtlasUiAction.CaptureSelfie) },
            modifier = Modifier.fillMaxWidth().height(56.dp).semantics { testTag = AtlasTestTags.SELFIE },
        ) {
            Text("Capture synthetic selfie")
        }
        CancelButton(onAction)
    }
}

@Composable
private fun SelfieFrame() {
    val primary = MaterialTheme.colorScheme.primary
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(260.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .semantics { contentDescription = AtlasAccessibility.SELFIE_FRAME },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(width = 180.dp, height = 230.dp)) {
            drawOval(color = primary, style = Stroke(width = 5f))
            drawCircle(
                color = primary.copy(alpha = 0.25f),
                radius = size.minDimension * 0.19f,
                center = Offset(size.width / 2, size.height * 0.34f),
            )
            drawOval(
                color = primary.copy(alpha = 0.25f),
                topLeft = Offset(size.width * 0.25f, size.height * 0.51f),
                size = Size(size.width * 0.5f, size.height * 0.32f),
            )
        }
    }
}

@Composable
private fun RecoveryScreen(
    state: AtlasUiState.Recovery,
    onAction: (AtlasUiAction) -> Unit,
) {
    ScreenColumn {
        ScreenTitle(state.title, state.detail, announce = true)
        ProgressPanel(state.progress)
        InfoNote("No raw values, internal codes, or session identifiers are shown.")
        if (state.canRetry) {
            Button(
                onClick = { onAction(AtlasUiAction.Retry) },
                modifier = Modifier.fillMaxWidth().height(56.dp).semantics { testTag = AtlasTestTags.RETRY },
            ) {
                Text("Try this step again")
            }
        }
        OutlinedButton(
            onClick = { onAction(AtlasUiAction.Cancel) },
            modifier = Modifier.fillMaxWidth().height(56.dp).semantics { testTag = AtlasTestTags.CANCEL },
        ) {
            Text("Cancel verification")
        }
    }
}

@Composable
private fun ResultScreen(
    state: AtlasUiState.Result,
    onAction: (AtlasUiAction) -> Unit,
) {
    ScreenColumn {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .semantics {
                        testTag = AtlasTestTags.RESULT
                        liveRegion = LiveRegionMode.Polite
                        stateDescription = state.title
                    },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutcomeMark(state.outcome)
            Text(
                state.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Text(state.detail, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        EvidencePanel(state.evidence)
        Button(
            onClick = { onAction(AtlasUiAction.StartAgain) },
            modifier = Modifier.fillMaxWidth().height(56.dp).semantics { testTag = AtlasTestTags.START_AGAIN },
        ) {
            Text("Start again")
        }
        OutlinedButton(
            onClick = { onAction(AtlasUiAction.ReturnHome) },
            modifier = Modifier.fillMaxWidth().height(56.dp).semantics { testTag = AtlasTestTags.HOME },
        ) {
            Text("Return home")
        }
    }
}

@Composable
private fun OutcomeMark(outcome: VerificationOutcome) {
    val (symbol, label) =
        when (outcome) {
            VerificationOutcome.VERIFIED -> "✓" to "Verified demo result"
            VerificationOutcome.REJECTED -> "×" to "Rejected demo result"
            VerificationOutcome.INCONCLUSIVE -> "?" to "Inconclusive demo result"
            VerificationOutcome.TECHNICAL_FAILURE -> "!" to "Technical failure"
            VerificationOutcome.CANCELLED -> "—" to "Cancelled demo result"
            VerificationOutcome.EXPIRED -> "⌛" to "Expired demo session"
        }
    Box(
        modifier =
            Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, fontSize = 34.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ScreenTitle(
    title: String,
    detail: String,
    announce: Boolean = false,
) {
    Column(
        modifier =
            Modifier.semantics {
                if (announce) liveRegion = LiveRegionMode.Polite
            },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() },
        )
        Text(detail, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ProgressPanel(progress: AtlasProgress) {
    val current =
        progress.steps
            .indexOfFirst { it.status == AtlasStepStatus.CURRENT || it.status == AtlasStepStatus.NEEDS_ATTENTION }
            .let { if (it >= 0) it else progress.steps.lastIndex }
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {
                    testTag = AtlasTestTags.PROGRESS
                    contentDescription =
                        AtlasAccessibility.progressDescription(
                            currentStep = current + 1,
                            totalSteps = progress.steps.size,
                            label = progress.steps[current].label,
                        )
                },
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        progress.steps.forEach { step ->
            val (symbol, status) =
                when (step.status) {
                    AtlasStepStatus.COMPLETE -> "✓" to "Complete"
                    AtlasStepStatus.CURRENT -> "●" to "Current"
                    AtlasStepStatus.UPCOMING -> "○" to "Upcoming"
                    AtlasStepStatus.NEEDS_ATTENTION -> "!" to "Needs attention"
                }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(symbol, modifier = Modifier.width(28.dp), fontWeight = FontWeight.Bold)
                Text(step.label, modifier = Modifier.weight(1f))
                Text(status, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun EvidencePanel(evidence: List<AtlasEvidenceItem>) {
    if (evidence.isEmpty()) return
    Card(
        modifier = Modifier.fillMaxWidth().semantics { testTag = AtlasTestTags.EVIDENCE },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Safe evidence summary",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() },
            )
            evidence.forEach { item -> EvidenceRow(item) }
        }
    }
}

@Composable
private fun EvidenceRow(item: AtlasEvidenceItem) {
    val (symbol, statusLabel) =
        when (item.status) {
            AtlasEvidenceStatus.CONFIRMED -> "✓" to "Confirmed"
            AtlasEvidenceStatus.ATTENTION -> "!" to "Attention"
            AtlasEvidenceStatus.NOT_PERFORMED -> "—" to "Not performed"
        }
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier =
                Modifier
                    .size(28.dp)
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(symbol, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("$statusLabel · ${item.title}", fontWeight = FontWeight.Bold)
            Text(item.detail, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun InfoNote(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Text("i", fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(12.dp))
            Text(text)
        }
    }
}

@Composable
private fun CancelButton(onAction: (AtlasUiAction) -> Unit) {
    TextButton(
        onClick = { onAction(AtlasUiAction.Cancel) },
        modifier = Modifier.fillMaxWidth().height(52.dp).semantics { testTag = AtlasTestTags.CANCEL },
    ) {
        Text("Cancel verification")
    }
}
