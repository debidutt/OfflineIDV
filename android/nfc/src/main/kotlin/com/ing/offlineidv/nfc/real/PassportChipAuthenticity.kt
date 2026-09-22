package com.ing.offlineidv.nfc.real

import com.ing.offlineidv.core.time.IdvClock
import com.ing.offlineidv.core.time.SystemIdvClock
import com.ing.offlineidv.nfc.ChipAuthenticationObservation
import com.ing.offlineidv.nfc.PassiveAuthenticationObservation
import net.sf.scuba.smartcards.CardServiceException
import org.jmrtd.PassportService
import org.jmrtd.Util
import org.jmrtd.lds.ChipAuthenticationInfo
import org.jmrtd.lds.ChipAuthenticationPublicKeyInfo
import org.jmrtd.lds.SODFile
import org.jmrtd.lds.SecurityInfo
import org.jmrtd.lds.icao.DG14File
import java.math.BigInteger
import java.security.MessageDigest
import java.security.PublicKey
import java.security.cert.X509Certificate
import java.security.interfaces.ECPublicKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.AlgorithmParameterSpec
import java.security.spec.MGF1ParameterSpec
import java.security.spec.PSSParameterSpec
import java.time.Instant
import java.util.Date

/**
 * Verifies signed LDS data and performs fresh chip-key possession proof.
 *
 * The component emits finite observations only. It never logs or exposes certificate, LDS, key,
 * or protocol values and it never chooses a verification outcome.
 */
internal class PassportChipAuthenticity(
    private val clock: IdvClock = SystemIdvClock,
    private val trustStore: DutchResidenceTrustStore = DutchResidenceTrustStore(),
) {
    fun verifyPassiveAuthentication(
        sod: SODFile,
        dg1Bytes: ByteArray,
    ): PassiveAuthenticationObservation =
        when (dataGroupHash(sod, DG1_NUMBER, dg1Bytes)) {
            DataGroupHashObservation.FAILED,
            DataGroupHashObservation.MISSING,
            -> PassiveAuthenticationObservation.FAILED

            DataGroupHashObservation.UNSUPPORTED -> PassiveAuthenticationObservation.UNSUPPORTED

            DataGroupHashObservation.VALID -> verifySodSignatureAndTrust(sod)
        }

    fun verifyDg14Hash(
        sod: SODFile,
        dg14Bytes: ByteArray,
    ): DataGroupHashObservation = dataGroupHash(sod, DG14_NUMBER, dg14Bytes)

    fun authenticateChip(
        service: PassportService,
        dg14: DG14File,
    ): ChipAuthenticationObservation {
        val selected = ChipAuthenticationSelector.select(dg14) ?: return ChipAuthenticationObservation.UNSUPPORTED
        return try {
            service.doEACCA(
                selected.keyId,
                selected.protocolOid,
                selected.publicKeyOid,
                selected.publicKey,
            )
            ChipAuthenticationObservation.SUCCEEDED
        } catch (_: CardServiceException) {
            ChipAuthenticationObservation.AUTHENTICATION_FAILED
        } catch (_: RuntimeException) {
            ChipAuthenticationObservation.TECHNICAL_ERROR
        }
    }

    private fun verifySodSignatureAndTrust(sod: SODFile): PassiveAuthenticationObservation {
        val digestAlgorithm = sod.digestAlgorithm ?: return PassiveAuthenticationObservation.UNSUPPORTED
        val signerDigestAlgorithm = sod.signerInfoDigestAlgorithm ?: return PassiveAuthenticationObservation.UNSUPPORTED
        val signatureAlgorithm = sod.digestEncryptionAlgorithm ?: return PassiveAuthenticationObservation.UNSUPPORTED
        if (digestAlgorithm !in ALLOWED_DIGESTS || signerDigestAlgorithm !in ALLOWED_DIGESTS) {
            return PassiveAuthenticationObservation.UNSUPPORTED
        }
        if (signatureAlgorithm !in ALLOWED_SIGNATURES) return PassiveAuthenticationObservation.UNSUPPORTED
        val signatureParameters = sod.digestEncryptionAlgorithmParams
        if (!PassportSignaturePolicy.isReviewedParameters(signatureAlgorithm, signerDigestAlgorithm, signatureParameters)) {
            return PassiveAuthenticationObservation.UNSUPPORTED
        }

        val certificates = sod.docSigningCertificates ?: return PassiveAuthenticationObservation.FAILED
        if (certificates.size != 1) return PassiveAuthenticationObservation.FAILED
        val documentSigner = certificates.single()
        if (!signerIdentifierMatches(sod, documentSigner)) return PassiveAuthenticationObservation.FAILED
        if (!isReviewedPublicKey(documentSigner.publicKey)) return PassiveAuthenticationObservation.UNSUPPORTED

        val signatureValid =
            try {
                val signature = Util.getSignature(signatureAlgorithm)
                signatureParameters?.let(signature::setParameter)
                signature.initVerify(documentSigner.publicKey)
                signature.update(sod.eContent)
                signature.verify(sod.encryptedDigest)
            } catch (_: Exception) {
                false
            }
        if (!signatureValid) return PassiveAuthenticationObservation.FAILED

        return when (trustStore.verify(documentSigner, clock.now())) {
            SignerTrustObservation.TRUSTED -> PassiveAuthenticationObservation.VALID

            SignerTrustObservation.UNTRUSTED -> PassiveAuthenticationObservation.FAILED

            SignerTrustObservation.STALE,
            SignerTrustObservation.ISSUER_NOT_COVERED,
            -> PassiveAuthenticationObservation.UNAVAILABLE

            SignerTrustObservation.UNSUPPORTED -> PassiveAuthenticationObservation.UNSUPPORTED

            SignerTrustObservation.TECHNICAL_ERROR -> PassiveAuthenticationObservation.TECHNICAL_ERROR
        }
    }

    private fun dataGroupHash(
        sod: SODFile,
        dataGroupNumber: Int,
        bytes: ByteArray,
    ): DataGroupHashObservation {
        val algorithm = sod.digestAlgorithm ?: return DataGroupHashObservation.UNSUPPORTED
        if (algorithm !in ALLOWED_DIGESTS) return DataGroupHashObservation.UNSUPPORTED
        val expected = sod.dataGroupHashes?.get(dataGroupNumber) ?: return DataGroupHashObservation.MISSING
        return try {
            val actual = Util.getMessageDigest(algorithm).digest(bytes)
            if (MessageDigest.isEqual(expected, actual)) {
                DataGroupHashObservation.VALID
            } else {
                DataGroupHashObservation.FAILED
            }
        } catch (_: Exception) {
            DataGroupHashObservation.UNSUPPORTED
        }
    }

    private fun signerIdentifierMatches(
        sod: SODFile,
        certificate: X509Certificate,
    ): Boolean {
        val issuer = sod.issuerX500Principal ?: return false
        val serial = sod.serialNumber ?: return false
        return issuer == certificate.issuerX500Principal && serial == certificate.serialNumber
    }

    private fun isReviewedPublicKey(key: PublicKey): Boolean =
        when (key) {
            is RSAPublicKey -> key.modulus.bitLength() >= MINIMUM_RSA_BITS
            is ECPublicKey -> key.params.curve.field.fieldSize >= MINIMUM_EC_BITS
            else -> false
        }

    private companion object {
        const val DG1_NUMBER: Int = 1
        const val DG14_NUMBER: Int = 14
        const val MINIMUM_RSA_BITS: Int = 2048
        const val MINIMUM_EC_BITS: Int = 256

        val ALLOWED_DIGESTS: Set<String> = setOf("SHA-256", "SHA-384", "SHA-512")
        val ALLOWED_SIGNATURES: Set<String> =
            setOf(
                "SHA256withRSA",
                "SHA384withRSA",
                "SHA512withRSA",
                "SHA256withECDSA",
                "SHA384withECDSA",
                "SHA512withECDSA",
                PassportSignaturePolicy.RSA_PSS_ALGORITHM,
            )
    }
}

/** Strict parameter policy for the only reviewed parameterized SOD signature algorithm. */
internal object PassportSignaturePolicy {
    const val RSA_PSS_ALGORITHM: String = "SSAwithRSA/PSS"

    fun isReviewedParameters(
        signatureAlgorithm: String,
        signerDigestAlgorithm: String,
        parameters: AlgorithmParameterSpec?,
    ): Boolean {
        if (signatureAlgorithm != RSA_PSS_ALGORITHM) return parameters == null
        val pss = parameters as? PSSParameterSpec ?: return false
        val mgf = pss.mgfParameters as? MGF1ParameterSpec ?: return false
        val expectedSaltLength = DIGEST_LENGTHS[signerDigestAlgorithm] ?: return false
        return pss.digestAlgorithm == signerDigestAlgorithm &&
            pss.mgfAlgorithm.equals("MGF1", ignoreCase = true) &&
            mgf.digestAlgorithm == signerDigestAlgorithm &&
            pss.saltLength == expectedSaltLength &&
            pss.trailerField == PSSParameterSpec.TRAILER_FIELD_BC
    }

    private val DIGEST_LENGTHS: Map<String, Int> = mapOf("SHA-256" to 32, "SHA-384" to 48, "SHA-512" to 64)
}

internal enum class DataGroupHashObservation {
    VALID,
    FAILED,
    MISSING,
    UNSUPPORTED,
}

/** Read-only, Netherlands-residence-permit trust snapshot shipped inside the signed app. */
internal class DutchResidenceTrustStore {
    private val anchor: X509Certificate? by lazy(::loadReviewedAnchor)

    fun snapshotAvailable(referenceTime: Instant): Boolean =
        !referenceTime.isBefore(SNAPSHOT_EFFECTIVE_AT) &&
            referenceTime.isBefore(SNAPSHOT_STALE_AT) &&
            anchor != null

    fun verify(
        documentSigner: X509Certificate,
        referenceTime: Instant,
    ): SignerTrustObservation {
        if (referenceTime.isBefore(SNAPSHOT_EFFECTIVE_AT) || !referenceTime.isBefore(SNAPSHOT_STALE_AT)) {
            return SignerTrustObservation.STALE
        }
        val trustAnchor = anchor ?: return SignerTrustObservation.TECHNICAL_ERROR
        if (documentSigner.issuerX500Principal != trustAnchor.subjectX500Principal) {
            return SignerTrustObservation.ISSUER_NOT_COVERED
        }
        if (!isReviewedSigner(documentSigner)) return SignerTrustObservation.UNSUPPORTED
        return try {
            val validationDate = Date.from(referenceTime)
            trustAnchor.checkValidity(validationDate)
            documentSigner.checkValidity(validationDate)
            documentSigner.verify(trustAnchor.publicKey, Util.getBouncyCastleProvider())
            SignerTrustObservation.TRUSTED
        } catch (_: Exception) {
            SignerTrustObservation.UNTRUSTED
        }
    }

    private fun loadReviewedAnchor(): X509Certificate? =
        try {
            val resource = requireNotNull(javaClass.getResourceAsStream(TRUST_RESOURCE))
            val certificate =
                resource.use { input ->
                    Util.getCertificateFactory("X.509").generateCertificate(input) as X509Certificate
                }
            val fingerprint = Util.getMessageDigest("SHA-256").digest(certificate.encoded)
            if (!MessageDigest.isEqual(fingerprint, EXPECTED_FINGERPRINT)) return null
            if (certificate.basicConstraints < 0 || certificate.keyUsage?.getOrNull(KEY_CERT_SIGN_INDEX) != true) return null
            certificate.verify(certificate.publicKey, Util.getBouncyCastleProvider())
            certificate
        } catch (_: Exception) {
            null
        }

    private fun isReviewedSigner(certificate: X509Certificate): Boolean {
        if (certificate.basicConstraints >= 0) return false
        if (certificate.keyUsage?.getOrNull(DIGITAL_SIGNATURE_INDEX) != true) return false
        val signatureAlgorithm = certificate.sigAlgName
        if (signatureAlgorithm !in REVIEWED_CERTIFICATE_SIGNATURES) return false
        return when (val key = certificate.publicKey) {
            is RSAPublicKey -> key.modulus.bitLength() >= MINIMUM_RSA_BITS
            is ECPublicKey -> key.params.curve.field.fieldSize >= MINIMUM_EC_BITS
            else -> false
        }
    }

    private companion object {
        const val TRUST_RESOURCE: String = "/com/ing/offlineidv/nfc/trust/nl-residence-csca-4.pem"
        const val DIGITAL_SIGNATURE_INDEX: Int = 0
        const val KEY_CERT_SIGN_INDEX: Int = 5
        const val MINIMUM_RSA_BITS: Int = 2048
        const val MINIMUM_EC_BITS: Int = 256

        val SNAPSHOT_EFFECTIVE_AT: Instant = Instant.parse("2026-09-22T00:00:00Z")
        val SNAPSHOT_STALE_AT: Instant = Instant.parse("2027-01-22T00:00:00Z")
        val REVIEWED_CERTIFICATE_SIGNATURES: Set<String> =
            setOf(
                "SHA256withRSA",
                "SHA384withRSA",
                "SHA512withRSA",
                "SHA256withECDSA",
                "SHA384withECDSA",
                "SHA512withECDSA",
            )
        val EXPECTED_FINGERPRINT: ByteArray =
            byteArrayOf(
                0x0F,
                0xD7.toByte(),
                0xE3.toByte(),
                0xBE.toByte(),
                0x92.toByte(),
                0x3B,
                0xDB.toByte(),
                0xE8.toByte(),
                0x3B,
                0x4E,
                0x4B,
                0x08,
                0xE2.toByte(),
                0x59,
                0x74,
                0x65,
                0x5B,
                0x5E,
                0x55,
                0xF6.toByte(),
                0x61,
                0x44,
                0x8B.toByte(),
                0x9A.toByte(),
                0xFA.toByte(),
                0xA4.toByte(),
                0xA7.toByte(),
                0x8F.toByte(),
                0x76,
                0x0E,
                0xD2.toByte(),
                0xBC.toByte(),
            )
    }
}

internal enum class SignerTrustObservation {
    TRUSTED,
    UNTRUSTED,
    STALE,
    ISSUER_NOT_COVERED,
    UNSUPPORTED,
    TECHNICAL_ERROR,
}

internal data class SelectedChipAuthentication(
    val keyId: BigInteger?,
    val protocolOid: String,
    val publicKeyOid: String,
    val publicKey: PublicKey,
)

/** Closed-suite deterministic selector; unknown, 3DES, DH, ambiguous, and weak keys fail closed. */
internal object ChipAuthenticationSelector {
    fun select(file: DG14File): SelectedChipAuthentication? {
        val securityInfos = file.securityInfos ?: return null
        val protocols = securityInfos.filterIsInstance<ChipAuthenticationInfo>()
        val publicKeys = securityInfos.filterIsInstance<ChipAuthenticationPublicKeyInfo>()
        return protocols
            .asSequence()
            .filter { info -> info.version == ChipAuthenticationInfo.VERSION_1 }
            .filter { info -> info.objectIdentifier in REVIEWED_PROTOCOL_OIDS }
            .sortedBy { info -> REVIEWED_PROTOCOL_OIDS.indexOf(info.objectIdentifier) }
            .mapNotNull { info -> selectKey(info, protocols, publicKeys) }
            .firstOrNull()
    }

    private fun selectKey(
        info: ChipAuthenticationInfo,
        protocols: List<ChipAuthenticationInfo>,
        publicKeys: List<ChipAuthenticationPublicKeyInfo>,
    ): SelectedChipAuthentication? {
        val candidates =
            publicKeys.filter { publicKeyInfo ->
                publicKeyInfo.objectIdentifier == SecurityInfo.ID_PK_ECDH &&
                    keyIdMatches(info.keyId, publicKeyInfo.keyId, protocols.size, publicKeys.size) &&
                    isReviewedEcKey(publicKeyInfo.subjectPublicKey)
            }
        if (candidates.size != 1) return null
        val selected = candidates.single()
        return SelectedChipAuthentication(
            keyId = info.keyId,
            protocolOid = info.objectIdentifier,
            publicKeyOid = selected.objectIdentifier,
            publicKey = selected.subjectPublicKey,
        )
    }

    private fun keyIdMatches(
        protocolKeyId: BigInteger?,
        publicKeyId: BigInteger?,
        protocolCount: Int,
        publicKeyCount: Int,
    ): Boolean =
        when {
            protocolKeyId != null && publicKeyId != null -> protocolKeyId == publicKeyId
            protocolKeyId == null && publicKeyId == null -> protocolCount == 1 && publicKeyCount == 1
            else -> false
        }

    private fun isReviewedEcKey(key: PublicKey): Boolean = key is ECPublicKey && key.params.curve.field.fieldSize >= MINIMUM_EC_BITS

    private const val MINIMUM_EC_BITS: Int = 256

    /** ECDH with AES-CBC-CMAC, strongest key size first. */
    private val REVIEWED_PROTOCOL_OIDS: List<String> =
        listOf(
            SecurityInfo.ID_CA_ECDH_AES_CBC_CMAC_256,
            SecurityInfo.ID_CA_ECDH_AES_CBC_CMAC_192,
            SecurityInfo.ID_CA_ECDH_AES_CBC_CMAC_128,
        )
}
