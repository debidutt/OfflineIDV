package com.ing.offlineidv.nfc.real

import com.ing.offlineidv.core.error.IdvError
import com.ing.offlineidv.core.error.NfcFailure
import com.ing.offlineidv.nfc.ChipAuthenticationObservation
import com.ing.offlineidv.nfc.ChipDataArtifact
import com.ing.offlineidv.nfc.MrzComparisonFields
import com.ing.offlineidv.nfc.NfcDiagnosticEvent
import com.ing.offlineidv.nfc.NfcDiagnosticObservation
import com.ing.offlineidv.nfc.NfcDiagnosticSink
import com.ing.offlineidv.nfc.NfcDiagnosticStage
import com.ing.offlineidv.nfc.NfcDiagnosticStatus
import com.ing.offlineidv.nfc.NfcReadResult
import com.ing.offlineidv.nfc.PassiveAuthenticationObservation
import com.ing.offlineidv.nfc.PassportAccessKey
import net.sf.scuba.smartcards.CardServiceException
import org.jmrtd.BACKey
import org.jmrtd.PassportService
import org.jmrtd.lds.CardAccessFile
import org.jmrtd.lds.PACEInfo
import org.jmrtd.lds.SODFile
import org.jmrtd.lds.icao.DG14File
import org.jmrtd.lds.icao.DG1File
import java.io.ByteArrayInputStream

/**
 * Contained JMRTD adapter for protected access, bounded LDS reads, Passive Authentication, and
 * separately reported Chip Authentication.
 *
 * It does not read DG2, invoke AA/TA, emit raw cryptographic material, or choose policy outcomes.
 */
internal class JmrtdPassportProtocolReader(
    private val allowBacWhenNoCompatiblePace: Boolean = true,
    private val authenticity: PassportChipAuthenticity = PassportChipAuthenticity(),
    private val diagnosticSink: NfcDiagnosticSink = NfcDiagnosticSink.NONE,
) {
    fun read(
        bridge: IsoDepCardServiceBridge,
        accessKey: PassportAccessKey,
        onCommunicationStarted: () -> Unit = {},
    ): NfcReadResult {
        JmrtdLoggingContainment.install()
        val fields = accessKey.fields() ?: return failure(NfcFailure.ACCESS_DENIED)
        var service: PassportService? = null
        return try {
            diagnostic(NfcDiagnosticStage.ISO_DEP, NfcDiagnosticStatus.STARTED)
            val activeService =
                PassportService(
                    bridge,
                    PassportService.NORMAL_MAX_TRANCEIVE_LENGTH,
                    PassportService.NORMAL_MAX_TRANCEIVE_LENGTH,
                    PassportService.DEFAULT_MAX_BLOCKSIZE,
                    false,
                    true,
                )
            service = activeService
            activeService.open()
            diagnostic(NfcDiagnosticStage.ISO_DEP, NfcDiagnosticStatus.SUCCEEDED)
            runCatching(onCommunicationStarted)
            diagnostic(NfcDiagnosticStage.CARD_ACCESS, NfcDiagnosticStatus.STARTED)
            when (val cardAccess = readCardAccess(activeService, bridge)) {
                CardAccessRead.Absent -> {
                    diagnostic(NfcDiagnosticStage.CARD_ACCESS, NfcDiagnosticStatus.SUCCEEDED)
                    authenticateWithBacOrReject(activeService, bridge, fields)
                }

                is CardAccessRead.Failed -> {
                    diagnostic(NfcDiagnosticStage.CARD_ACCESS, NfcDiagnosticStatus.FAILED, cardAccess.reason)
                    failure(cardAccess.reason)
                }

                is CardAccessRead.Parsed -> {
                    diagnostic(NfcDiagnosticStage.CARD_ACCESS, NfcDiagnosticStatus.SUCCEEDED)
                    authenticateAndRead(activeService, bridge, fields, cardAccess)
                }
            }
        } catch (_: CardServiceException) {
            val reason = bridge.consumeFailure().toNfcFailure(NfcFailure.READ_FAILED)
            diagnostic(NfcDiagnosticStage.ISO_DEP, NfcDiagnosticStatus.FAILED, reason)
            failure(reason)
        } catch (_: RuntimeException) {
            val reason = bridge.consumeFailure().toNfcFailure(NfcFailure.TECHNICAL_ERROR)
            diagnostic(NfcDiagnosticStage.ISO_DEP, NfcDiagnosticStatus.FAILED, reason)
            failure(reason)
        } finally {
            try {
                service?.close() ?: bridge.close()
            } catch (_: RuntimeException) {
                bridge.close()
            }
        }
    }

    private fun authenticateAndRead(
        service: PassportService,
        bridge: IsoDepCardServiceBridge,
        fields: com.ing.offlineidv.nfc.MrzAccessFields,
        cardAccess: CardAccessRead.Parsed,
    ): NfcReadResult =
        when (
            val decision =
                PassportAccessSelector.select(
                    cardAccess.observation,
                    allowBacWhenNoCompatiblePace,
                )
        ) {
            PassportAccessDecision.Malformed -> {
                failure(NfcFailure.CARD_ACCESS_MALFORMED)
            }

            PassportAccessDecision.Unsupported -> {
                failure(NfcFailure.ACCESS_CONTROL_UNSUPPORTED)
            }

            PassportAccessDecision.UseBac -> {
                authenticateWithBacOrReject(service, bridge, fields)
            }

            is PassportAccessDecision.UsePace -> {
                val paceInfo =
                    cardAccess.paceInfos.firstOrNull { it.descriptorOrNull() == decision.suite }
                        ?: return failure(NfcFailure.CARD_ACCESS_MALFORMED)
                authenticateWithPaceAndRead(service, bridge, fields, paceInfo)
            }
        }

    private fun authenticateWithPaceAndRead(
        service: PassportService,
        bridge: IsoDepCardServiceBridge,
        fields: com.ing.offlineidv.nfc.MrzAccessFields,
        paceInfo: PACEInfo,
    ): NfcReadResult {
        val key = fields.toBacKey()
        return try {
            diagnostic(NfcDiagnosticStage.PACE, NfcDiagnosticStatus.STARTED)
            val parameterId = requireNotNull(paceInfo.parameterId)
            service.doPACE(
                key,
                paceInfo.objectIdentifier,
                PACEInfo.toParameterSpec(parameterId),
                parameterId,
            )
            service.sendSelectApplet(true)
            diagnostic(NfcDiagnosticStage.PACE, NfcDiagnosticStatus.SUCCEEDED)
            readDg1AndAuthenticity(service, bridge)
        } catch (_: CardServiceException) {
            val reason = bridge.consumeFailure().toNfcFailure(NfcFailure.ACCESS_DENIED)
            diagnostic(NfcDiagnosticStage.PACE, NfcDiagnosticStatus.FAILED, reason)
            failure(reason)
        } catch (_: RuntimeException) {
            val reason = bridge.consumeFailure().toNfcFailure(NfcFailure.ACCESS_DENIED)
            diagnostic(NfcDiagnosticStage.PACE, NfcDiagnosticStatus.FAILED, reason)
            failure(reason)
        }
    }

    private fun authenticateWithBacOrReject(
        service: PassportService,
        bridge: IsoDepCardServiceBridge,
        fields: com.ing.offlineidv.nfc.MrzAccessFields,
    ): NfcReadResult {
        if (!allowBacWhenNoCompatiblePace) return failure(NfcFailure.ACCESS_CONTROL_UNSUPPORTED)
        return try {
            diagnostic(NfcDiagnosticStage.BAC, NfcDiagnosticStatus.STARTED)
            service.sendSelectApplet(false)
            service.doBAC(fields.toBacKey())
            diagnostic(NfcDiagnosticStage.BAC, NfcDiagnosticStatus.SUCCEEDED)
            readDg1AndAuthenticity(service, bridge)
        } catch (_: CardServiceException) {
            val reason = bridge.consumeFailure().toNfcFailure(NfcFailure.ACCESS_DENIED)
            diagnostic(NfcDiagnosticStage.BAC, NfcDiagnosticStatus.FAILED, reason)
            failure(reason)
        } catch (_: RuntimeException) {
            val reason = bridge.consumeFailure().toNfcFailure(NfcFailure.ACCESS_DENIED)
            diagnostic(NfcDiagnosticStage.BAC, NfcDiagnosticStatus.FAILED, reason)
            failure(reason)
        }
    }

    private fun readDg1AndAuthenticity(
        service: PassportService,
        bridge: IsoDepCardServiceBridge,
    ): NfcReadResult =
        try {
            diagnostic(NfcDiagnosticStage.DG1, NfcDiagnosticStatus.STARTED)
            service
                .getInputStream(PassportService.EF_DG1, PassportService.DEFAULT_MAX_BLOCKSIZE)
                .useBoundedByteArray(MAXIMUM_DG1_BYTES) { dg1Bytes ->
                    val dg1 = DG1File(ByteArrayInputStream(dg1Bytes))
                    val mrz = dg1.mrzInfo
                    diagnostic(NfcDiagnosticStage.DG1, NfcDiagnosticStatus.SUCCEEDED)
                    when (val result = readAuthenticity(service, bridge, dg1Bytes)) {
                        is AuthenticityRead.Failed -> {
                            failure(result.reason)
                        }

                        is AuthenticityRead.Observed -> {
                            diagnostic(NfcDiagnosticStage.COMPLETE, NfcDiagnosticStatus.SUCCEEDED)
                            NfcReadResult.Read(
                                ChipDataArtifact.fromRead(
                                    dg1Value =
                                        MrzComparisonFields.encode(
                                            mrz.documentNumber,
                                            mrz.nationality,
                                            mrz.dateOfBirth,
                                            mrz.dateOfExpiry,
                                        ),
                                    portraitBytes = null,
                                    passiveAuthentication = result.passiveAuthentication,
                                    chipAuthentication = result.chipAuthentication,
                                ),
                            )
                        }
                    }
                }
        } catch (_: LdsReadLimitExceeded) {
            diagnostic(NfcDiagnosticStage.DG1, NfcDiagnosticStatus.FAILED, NfcFailure.READ_LIMIT_EXCEEDED)
            failure(NfcFailure.READ_LIMIT_EXCEEDED)
        } catch (_: CardServiceException) {
            val reason = bridge.consumeFailure().toNfcFailure(NfcFailure.READ_FAILED)
            diagnostic(NfcDiagnosticStage.DG1, NfcDiagnosticStatus.FAILED, reason)
            failure(reason)
        } catch (_: Exception) {
            diagnostic(NfcDiagnosticStage.DG1, NfcDiagnosticStatus.FAILED, NfcFailure.CHIP_DATA_MALFORMED)
            failure(NfcFailure.CHIP_DATA_MALFORMED)
        }

    private fun readAuthenticity(
        service: PassportService,
        bridge: IsoDepCardServiceBridge,
        dg1Bytes: ByteArray,
    ): AuthenticityRead =
        try {
            diagnostic(NfcDiagnosticStage.PASSIVE_AUTHENTICATION, NfcDiagnosticStatus.STARTED)
            service
                .getInputStream(PassportService.EF_SOD, PassportService.DEFAULT_MAX_BLOCKSIZE)
                .useBoundedByteArray(MAXIMUM_SOD_BYTES) { sodBytes ->
                    val sod = SODFile(ByteArrayInputStream(sodBytes))
                    val passiveAuthentication = authenticity.verifyPassiveAuthentication(sod, dg1Bytes)
                    diagnostic(
                        NfcDiagnosticStage.PASSIVE_AUTHENTICATION,
                        NfcDiagnosticStatus.SUCCEEDED,
                        observation = passiveAuthentication.toDiagnosticObservation(),
                    )
                    if (passiveAuthentication != PassiveAuthenticationObservation.VALID) {
                        return@useBoundedByteArray AuthenticityRead.Observed(
                            passiveAuthentication,
                            ChipAuthenticationObservation.PREREQUISITE_MISSING,
                        )
                    }
                    readDg14AndAuthenticate(service, bridge, sod)
                }
        } catch (error: CardServiceException) {
            if (error.sw == FILE_NOT_FOUND_STATUS) {
                diagnostic(
                    NfcDiagnosticStage.PASSIVE_AUTHENTICATION,
                    NfcDiagnosticStatus.SUCCEEDED,
                    observation = NfcDiagnosticObservation.PASSIVE_AUTH_UNAVAILABLE,
                )
                AuthenticityRead.Observed(
                    PassiveAuthenticationObservation.UNAVAILABLE,
                    ChipAuthenticationObservation.PREREQUISITE_MISSING,
                )
            } else {
                val transportFailure = bridge.consumeFailure()
                if (transportFailure == null) {
                    diagnostic(
                        NfcDiagnosticStage.PASSIVE_AUTHENTICATION,
                        NfcDiagnosticStatus.SUCCEEDED,
                        observation = NfcDiagnosticObservation.PASSIVE_AUTH_TECHNICAL_ERROR,
                    )
                    AuthenticityRead.Observed(
                        PassiveAuthenticationObservation.TECHNICAL_ERROR,
                        ChipAuthenticationObservation.PREREQUISITE_MISSING,
                    )
                } else {
                    val reason = transportFailure.toNfcFailure(NfcFailure.READ_FAILED)
                    diagnostic(NfcDiagnosticStage.PASSIVE_AUTHENTICATION, NfcDiagnosticStatus.FAILED, reason)
                    AuthenticityRead.Failed(reason)
                }
            }
        } catch (_: LdsReadLimitExceeded) {
            diagnostic(
                NfcDiagnosticStage.PASSIVE_AUTHENTICATION,
                NfcDiagnosticStatus.FAILED,
                NfcFailure.READ_LIMIT_EXCEEDED,
            )
            AuthenticityRead.Failed(NfcFailure.READ_LIMIT_EXCEEDED)
        } catch (_: Exception) {
            diagnostic(
                NfcDiagnosticStage.PASSIVE_AUTHENTICATION,
                NfcDiagnosticStatus.SUCCEEDED,
                observation = NfcDiagnosticObservation.PASSIVE_AUTH_FAILED,
            )
            AuthenticityRead.Observed(
                PassiveAuthenticationObservation.FAILED,
                ChipAuthenticationObservation.PREREQUISITE_MISSING,
            )
        }

    private fun readDg14AndAuthenticate(
        service: PassportService,
        bridge: IsoDepCardServiceBridge,
        sod: SODFile,
    ): AuthenticityRead =
        try {
            diagnostic(NfcDiagnosticStage.DG14, NfcDiagnosticStatus.STARTED)
            service
                .getInputStream(PassportService.EF_DG14, PassportService.DEFAULT_MAX_BLOCKSIZE)
                .useBoundedByteArray(MAXIMUM_DG14_BYTES) { dg14Bytes ->
                    when (authenticity.verifyDg14Hash(sod, dg14Bytes)) {
                        DataGroupHashObservation.FAILED -> {
                            diagnostic(
                                NfcDiagnosticStage.DG14,
                                NfcDiagnosticStatus.SUCCEEDED,
                                observation = NfcDiagnosticObservation.DG14_HASH_FAILED,
                            )
                            AuthenticityRead.Observed(
                                PassiveAuthenticationObservation.FAILED,
                                ChipAuthenticationObservation.PREREQUISITE_MISSING,
                            )
                        }

                        DataGroupHashObservation.MISSING -> {
                            diagnostic(
                                NfcDiagnosticStage.DG14,
                                NfcDiagnosticStatus.SUCCEEDED,
                                observation = NfcDiagnosticObservation.DG14_HASH_MISSING,
                            )
                            AuthenticityRead.Observed(
                                PassiveAuthenticationObservation.VALID,
                                ChipAuthenticationObservation.PREREQUISITE_MISSING,
                            )
                        }

                        DataGroupHashObservation.UNSUPPORTED -> {
                            diagnostic(
                                NfcDiagnosticStage.DG14,
                                NfcDiagnosticStatus.SUCCEEDED,
                                observation = NfcDiagnosticObservation.DG14_HASH_UNSUPPORTED,
                            )
                            AuthenticityRead.Observed(
                                PassiveAuthenticationObservation.UNSUPPORTED,
                                ChipAuthenticationObservation.PREREQUISITE_MISSING,
                            )
                        }

                        DataGroupHashObservation.VALID -> {
                            diagnostic(
                                NfcDiagnosticStage.DG14,
                                NfcDiagnosticStatus.SUCCEEDED,
                                observation = NfcDiagnosticObservation.DG14_HASH_VALID,
                            )
                            val dg14 = DG14File(ByteArrayInputStream(dg14Bytes))
                            diagnostic(NfcDiagnosticStage.CHIP_AUTHENTICATION, NfcDiagnosticStatus.STARTED)
                            val chipAuthentication = authenticity.authenticateChip(service, dg14)
                            diagnostic(
                                NfcDiagnosticStage.CHIP_AUTHENTICATION,
                                NfcDiagnosticStatus.SUCCEEDED,
                                observation = chipAuthentication.toDiagnosticObservation(),
                            )
                            bridge.consumeFailure()?.let {
                                return@useBoundedByteArray AuthenticityRead.Failed(
                                    it.toNfcFailure(NfcFailure.READ_FAILED),
                                )
                            }
                            AuthenticityRead.Observed(
                                PassiveAuthenticationObservation.VALID,
                                chipAuthentication,
                            )
                        }
                    }
                }
        } catch (error: CardServiceException) {
            if (error.sw == FILE_NOT_FOUND_STATUS) {
                diagnostic(
                    NfcDiagnosticStage.DG14,
                    NfcDiagnosticStatus.SUCCEEDED,
                    observation = NfcDiagnosticObservation.DG14_HASH_MISSING,
                )
                AuthenticityRead.Observed(
                    PassiveAuthenticationObservation.VALID,
                    ChipAuthenticationObservation.UNSUPPORTED,
                )
            } else {
                val transportFailure = bridge.consumeFailure()
                if (transportFailure == null) {
                    diagnostic(
                        NfcDiagnosticStage.CHIP_AUTHENTICATION,
                        NfcDiagnosticStatus.SUCCEEDED,
                        observation = NfcDiagnosticObservation.CHIP_AUTH_TECHNICAL_ERROR,
                    )
                    AuthenticityRead.Observed(
                        PassiveAuthenticationObservation.VALID,
                        ChipAuthenticationObservation.TECHNICAL_ERROR,
                    )
                } else {
                    val reason = transportFailure.toNfcFailure(NfcFailure.READ_FAILED)
                    diagnostic(NfcDiagnosticStage.DG14, NfcDiagnosticStatus.FAILED, reason)
                    AuthenticityRead.Failed(reason)
                }
            }
        } catch (_: LdsReadLimitExceeded) {
            diagnostic(NfcDiagnosticStage.DG14, NfcDiagnosticStatus.FAILED, NfcFailure.READ_LIMIT_EXCEEDED)
            AuthenticityRead.Failed(NfcFailure.READ_LIMIT_EXCEEDED)
        } catch (_: Exception) {
            diagnostic(
                NfcDiagnosticStage.CHIP_AUTHENTICATION,
                NfcDiagnosticStatus.SUCCEEDED,
                observation = NfcDiagnosticObservation.CHIP_AUTH_TECHNICAL_ERROR,
            )
            AuthenticityRead.Observed(
                PassiveAuthenticationObservation.VALID,
                ChipAuthenticationObservation.TECHNICAL_ERROR,
            )
        }

    private fun readCardAccess(
        service: PassportService,
        bridge: IsoDepCardServiceBridge,
    ): CardAccessRead =
        try {
            val file =
                service
                    .getInputStream(PassportService.EF_CARD_ACCESS, PassportService.DEFAULT_MAX_BLOCKSIZE)
                    .useBoundedBytes(MAXIMUM_CARD_ACCESS_BYTES, ::CardAccessFile)
            val paceInfos = file.securityInfos.filterIsInstance<PACEInfo>()
            val descriptors =
                paceInfos.map { info ->
                    info.descriptorOrNull() ?: return CardAccessRead.Failed(NfcFailure.CARD_ACCESS_MALFORMED)
                }
            CardAccessRead.Parsed(
                observation = CardAccessObservation.Advertised(descriptors),
                paceInfos = paceInfos,
            )
        } catch (error: CardServiceException) {
            if (error.sw == FILE_NOT_FOUND_STATUS) {
                CardAccessRead.Absent
            } else {
                CardAccessRead.Failed(
                    bridge.consumeFailure().toNfcFailure(NfcFailure.CARD_ACCESS_MALFORMED),
                )
            }
        } catch (_: LdsReadLimitExceeded) {
            CardAccessRead.Failed(NfcFailure.READ_LIMIT_EXCEEDED)
        } catch (_: Exception) {
            CardAccessRead.Failed(NfcFailure.CARD_ACCESS_MALFORMED)
        }

    private fun PACEInfo.descriptorOrNull(): PaceSuiteDescriptor? =
        try {
            val parameter = requireNotNull(parameterId).toInt()
            PaceSuiteDescriptor(objectIdentifier, parameter)
        } catch (_: Exception) {
            null
        }

    private fun com.ing.offlineidv.nfc.MrzAccessFields.toBacKey(): BACKey = BACKey(documentNumber.trimEnd('<'), dateOfBirth, expiryDate)

    private fun IsoDepCardServiceBridge.TransportFailure?.toNfcFailure(fallback: NfcFailure): NfcFailure =
        when (this) {
            IsoDepCardServiceBridge.TransportFailure.CONNECTION_TIMEOUT -> NfcFailure.CONNECTION_TIMEOUT
            IsoDepCardServiceBridge.TransportFailure.TAG_LOST -> NfcFailure.TAG_LOST
            IsoDepCardServiceBridge.TransportFailure.READ_FAILED -> NfcFailure.READ_FAILED
            IsoDepCardServiceBridge.TransportFailure.LIMIT_EXCEEDED -> NfcFailure.READ_LIMIT_EXCEEDED
            IsoDepCardServiceBridge.TransportFailure.TECHNICAL_ERROR -> NfcFailure.TECHNICAL_ERROR
            null -> fallback
        }

    private fun failure(reason: NfcFailure): NfcReadResult.Failed = NfcReadResult.Failed(IdvError.Nfc(reason))

    private fun diagnostic(
        stage: NfcDiagnosticStage,
        status: NfcDiagnosticStatus,
        failure: NfcFailure? = null,
        observation: NfcDiagnosticObservation = NfcDiagnosticObservation.NONE,
    ) {
        runCatching { diagnosticSink.record(NfcDiagnosticEvent(stage, status, failure, observation)) }
    }

    private fun PassiveAuthenticationObservation.toDiagnosticObservation(): NfcDiagnosticObservation =
        when (this) {
            PassiveAuthenticationObservation.VALID -> NfcDiagnosticObservation.PASSIVE_AUTH_VALID
            PassiveAuthenticationObservation.FAILED -> NfcDiagnosticObservation.PASSIVE_AUTH_FAILED
            PassiveAuthenticationObservation.NOT_PERFORMED -> NfcDiagnosticObservation.NONE
            PassiveAuthenticationObservation.UNAVAILABLE -> NfcDiagnosticObservation.PASSIVE_AUTH_UNAVAILABLE
            PassiveAuthenticationObservation.UNSUPPORTED -> NfcDiagnosticObservation.PASSIVE_AUTH_UNSUPPORTED
            PassiveAuthenticationObservation.TECHNICAL_ERROR -> NfcDiagnosticObservation.PASSIVE_AUTH_TECHNICAL_ERROR
        }

    private fun ChipAuthenticationObservation.toDiagnosticObservation(): NfcDiagnosticObservation =
        when (this) {
            ChipAuthenticationObservation.SUCCEEDED -> {
                NfcDiagnosticObservation.CHIP_AUTH_SUCCEEDED
            }

            ChipAuthenticationObservation.AUTHENTICATION_FAILED -> {
                NfcDiagnosticObservation.CHIP_AUTH_FAILED
            }

            ChipAuthenticationObservation.NOT_PERFORMED -> {
                NfcDiagnosticObservation.CHIP_AUTH_NOT_PERFORMED
            }

            ChipAuthenticationObservation.PREREQUISITE_MISSING -> {
                NfcDiagnosticObservation.CHIP_AUTH_PREREQUISITE_MISSING
            }

            ChipAuthenticationObservation.UNSUPPORTED -> {
                NfcDiagnosticObservation.CHIP_AUTH_UNSUPPORTED
            }

            ChipAuthenticationObservation.SECURE_MESSAGING_FAILED -> {
                NfcDiagnosticObservation.CHIP_AUTH_SECURE_MESSAGING_FAILED
            }

            ChipAuthenticationObservation.TECHNICAL_ERROR -> {
                NfcDiagnosticObservation.CHIP_AUTH_TECHNICAL_ERROR
            }
        }

    private sealed interface CardAccessRead {
        data object Absent : CardAccessRead

        data class Parsed(
            val observation: CardAccessObservation,
            val paceInfos: List<PACEInfo>,
        ) : CardAccessRead

        data class Failed(
            val reason: NfcFailure,
        ) : CardAccessRead
    }

    private sealed interface AuthenticityRead {
        data class Observed(
            val passiveAuthentication: PassiveAuthenticationObservation,
            val chipAuthentication: ChipAuthenticationObservation,
        ) : AuthenticityRead

        data class Failed(
            val reason: NfcFailure,
        ) : AuthenticityRead
    }

    private companion object {
        const val FILE_NOT_FOUND_STATUS: Int = 0x6A82
        const val MAXIMUM_CARD_ACCESS_BYTES: Int = 64 * 1024
        const val MAXIMUM_DG1_BYTES: Int = 4 * 1024
        const val MAXIMUM_DG14_BYTES: Int = 64 * 1024
        const val MAXIMUM_SOD_BYTES: Int = 1024 * 1024
    }
}
