package com.ing.offlineidv.nfc.real

import com.ing.offlineidv.core.error.IdvError
import com.ing.offlineidv.core.error.NfcFailure
import com.ing.offlineidv.nfc.ChipAuthenticationObservation
import com.ing.offlineidv.nfc.ChipDataArtifact
import com.ing.offlineidv.nfc.MrzComparisonFields
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
) {
    fun read(
        bridge: IsoDepCardServiceBridge,
        accessKey: PassportAccessKey,
    ): NfcReadResult {
        JmrtdLoggingContainment.install()
        val fields = accessKey.fields() ?: return failure(NfcFailure.ACCESS_DENIED)
        var service: PassportService? = null
        return try {
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
            when (val cardAccess = readCardAccess(activeService, bridge)) {
                CardAccessRead.Absent -> authenticateWithBacOrReject(activeService, bridge, fields)
                is CardAccessRead.Failed -> failure(cardAccess.reason)
                is CardAccessRead.Parsed -> authenticateAndRead(activeService, bridge, fields, cardAccess)
            }
        } catch (_: CardServiceException) {
            failure(bridge.consumeFailure().toNfcFailure(NfcFailure.READ_FAILED))
        } catch (_: RuntimeException) {
            failure(bridge.consumeFailure().toNfcFailure(NfcFailure.TECHNICAL_ERROR))
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
            val parameterId = requireNotNull(paceInfo.parameterId)
            service.doPACE(
                key,
                paceInfo.objectIdentifier,
                PACEInfo.toParameterSpec(parameterId),
                parameterId,
            )
            service.sendSelectApplet(true)
            readDg1AndAuthenticity(service, bridge)
        } catch (_: CardServiceException) {
            failure(bridge.consumeFailure().toNfcFailure(NfcFailure.ACCESS_DENIED))
        } catch (_: RuntimeException) {
            failure(bridge.consumeFailure().toNfcFailure(NfcFailure.ACCESS_DENIED))
        }
    }

    private fun authenticateWithBacOrReject(
        service: PassportService,
        bridge: IsoDepCardServiceBridge,
        fields: com.ing.offlineidv.nfc.MrzAccessFields,
    ): NfcReadResult {
        if (!allowBacWhenNoCompatiblePace) return failure(NfcFailure.ACCESS_CONTROL_UNSUPPORTED)
        return try {
            service.sendSelectApplet(false)
            service.doBAC(fields.toBacKey())
            readDg1AndAuthenticity(service, bridge)
        } catch (_: CardServiceException) {
            failure(bridge.consumeFailure().toNfcFailure(NfcFailure.ACCESS_DENIED))
        } catch (_: RuntimeException) {
            failure(bridge.consumeFailure().toNfcFailure(NfcFailure.ACCESS_DENIED))
        }
    }

    private fun readDg1AndAuthenticity(
        service: PassportService,
        bridge: IsoDepCardServiceBridge,
    ): NfcReadResult =
        try {
            service
                .getInputStream(PassportService.EF_DG1, PassportService.DEFAULT_MAX_BLOCKSIZE)
                .useBoundedByteArray(MAXIMUM_DG1_BYTES) { dg1Bytes ->
                    val dg1 = DG1File(ByteArrayInputStream(dg1Bytes))
                    val mrz = dg1.mrzInfo
                    when (val result = readAuthenticity(service, bridge, dg1Bytes)) {
                        is AuthenticityRead.Failed -> {
                            failure(result.reason)
                        }

                        is AuthenticityRead.Observed -> {
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
            failure(NfcFailure.READ_LIMIT_EXCEEDED)
        } catch (_: CardServiceException) {
            failure(bridge.consumeFailure().toNfcFailure(NfcFailure.READ_FAILED))
        } catch (_: Exception) {
            failure(NfcFailure.CHIP_DATA_MALFORMED)
        }

    private fun readAuthenticity(
        service: PassportService,
        bridge: IsoDepCardServiceBridge,
        dg1Bytes: ByteArray,
    ): AuthenticityRead =
        try {
            service
                .getInputStream(PassportService.EF_SOD, PassportService.DEFAULT_MAX_BLOCKSIZE)
                .useBoundedByteArray(MAXIMUM_SOD_BYTES) { sodBytes ->
                    val sod = SODFile(ByteArrayInputStream(sodBytes))
                    val passiveAuthentication = authenticity.verifyPassiveAuthentication(sod, dg1Bytes)
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
                AuthenticityRead.Observed(
                    PassiveAuthenticationObservation.UNAVAILABLE,
                    ChipAuthenticationObservation.PREREQUISITE_MISSING,
                )
            } else {
                val transportFailure = bridge.consumeFailure()
                if (transportFailure == null) {
                    AuthenticityRead.Observed(
                        PassiveAuthenticationObservation.TECHNICAL_ERROR,
                        ChipAuthenticationObservation.PREREQUISITE_MISSING,
                    )
                } else {
                    AuthenticityRead.Failed(transportFailure.toNfcFailure(NfcFailure.READ_FAILED))
                }
            }
        } catch (_: LdsReadLimitExceeded) {
            AuthenticityRead.Failed(NfcFailure.READ_LIMIT_EXCEEDED)
        } catch (_: Exception) {
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
            service
                .getInputStream(PassportService.EF_DG14, PassportService.DEFAULT_MAX_BLOCKSIZE)
                .useBoundedByteArray(MAXIMUM_DG14_BYTES) { dg14Bytes ->
                    when (authenticity.verifyDg14Hash(sod, dg14Bytes)) {
                        DataGroupHashObservation.FAILED -> {
                            AuthenticityRead.Observed(
                                PassiveAuthenticationObservation.FAILED,
                                ChipAuthenticationObservation.PREREQUISITE_MISSING,
                            )
                        }

                        DataGroupHashObservation.MISSING -> {
                            AuthenticityRead.Observed(
                                PassiveAuthenticationObservation.VALID,
                                ChipAuthenticationObservation.PREREQUISITE_MISSING,
                            )
                        }

                        DataGroupHashObservation.UNSUPPORTED -> {
                            AuthenticityRead.Observed(
                                PassiveAuthenticationObservation.UNSUPPORTED,
                                ChipAuthenticationObservation.PREREQUISITE_MISSING,
                            )
                        }

                        DataGroupHashObservation.VALID -> {
                            val dg14 = DG14File(ByteArrayInputStream(dg14Bytes))
                            val chipAuthentication = authenticity.authenticateChip(service, dg14)
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
                AuthenticityRead.Observed(
                    PassiveAuthenticationObservation.VALID,
                    ChipAuthenticationObservation.UNSUPPORTED,
                )
            } else {
                val transportFailure = bridge.consumeFailure()
                if (transportFailure == null) {
                    AuthenticityRead.Observed(
                        PassiveAuthenticationObservation.VALID,
                        ChipAuthenticationObservation.TECHNICAL_ERROR,
                    )
                } else {
                    AuthenticityRead.Failed(transportFailure.toNfcFailure(NfcFailure.READ_FAILED))
                }
            }
        } catch (_: LdsReadLimitExceeded) {
            AuthenticityRead.Failed(NfcFailure.READ_LIMIT_EXCEEDED)
        } catch (_: Exception) {
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
