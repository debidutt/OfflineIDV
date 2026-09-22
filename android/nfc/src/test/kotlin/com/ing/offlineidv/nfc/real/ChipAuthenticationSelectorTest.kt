package com.ing.offlineidv.nfc.real

import org.jmrtd.lds.ChipAuthenticationInfo
import org.jmrtd.lds.ChipAuthenticationPublicKeyInfo
import org.jmrtd.lds.SecurityInfo
import org.jmrtd.lds.icao.DG14File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec
import java.security.spec.MGF1ParameterSpec
import java.security.spec.PSSParameterSpec

public class ChipAuthenticationSelectorTest {
    @Test
    public fun `selection is deterministic and prefers strongest reviewed suite`() {
        val weakId = BigInteger.ONE
        val strongId = BigInteger.TWO
        val file =
            DG14File(
                listOf(
                    ChipAuthenticationInfo(SecurityInfo.ID_CA_ECDH_AES_CBC_CMAC_128, 1, weakId),
                    publicKey(weakId),
                    ChipAuthenticationInfo(SecurityInfo.ID_CA_ECDH_AES_CBC_CMAC_256, 1, strongId),
                    publicKey(strongId),
                ),
            )

        val selected = requireNotNull(ChipAuthenticationSelector.select(file))

        assertEquals(SecurityInfo.ID_CA_ECDH_AES_CBC_CMAC_256, selected.protocolOid)
        assertEquals(strongId, selected.keyId)
        assertEquals(SecurityInfo.ID_PK_ECDH, selected.publicKeyOid)
    }

    @Test
    public fun `unreviewed 3DES and version two protocols fail closed`() {
        val keyId = BigInteger.ONE
        val threeDes =
            DG14File(
                listOf(
                    ChipAuthenticationInfo(SecurityInfo.ID_CA_ECDH_3DES_CBC_CBC, 1, keyId),
                    publicKey(keyId),
                ),
            )
        val versionTwo =
            DG14File(
                listOf(
                    ChipAuthenticationInfo(SecurityInfo.ID_CA_ECDH_AES_CBC_CMAC_256, 2, keyId),
                    publicKey(keyId),
                ),
            )

        assertNull(ChipAuthenticationSelector.select(threeDes))
        assertNull(ChipAuthenticationSelector.select(versionTwo))
    }

    @Test
    public fun `ambiguous or mismatched key identifiers fail closed`() {
        val protocolId = BigInteger.ONE
        val mismatched =
            DG14File(
                listOf(
                    ChipAuthenticationInfo(SecurityInfo.ID_CA_ECDH_AES_CBC_CMAC_256, 1, protocolId),
                    publicKey(BigInteger.TWO),
                ),
            )
        val ambiguous =
            DG14File(
                listOf(
                    ChipAuthenticationInfo(SecurityInfo.ID_CA_ECDH_AES_CBC_CMAC_256, 1),
                    publicKey(null),
                    publicKey(null),
                ),
            )

        assertNull(ChipAuthenticationSelector.select(mismatched))
        assertNull(ChipAuthenticationSelector.select(ambiguous))
    }

    @Test
    public fun `embedded Dutch residence snapshot is pinned and expires closed`() {
        val store = DutchResidenceTrustStore()

        assertTrue(store.snapshotAvailable(java.time.Instant.parse("2026-09-22T12:00:00Z")))
        assertTrue(!store.snapshotAvailable(java.time.Instant.parse("2027-01-22T00:00:00Z")))
    }

    @Test
    public fun `reviewed RSA PSS parameters require matching modern digest and salt`() {
        val parameters =
            PSSParameterSpec(
                "SHA-256",
                "MGF1",
                MGF1ParameterSpec.SHA256,
                32,
                PSSParameterSpec.TRAILER_FIELD_BC,
            )

        assertTrue(PassportSignaturePolicy.isReviewedParameters("SSAwithRSA/PSS", "SHA-256", parameters))
    }

    @Test
    public fun `weak or confused RSA PSS parameters fail closed`() {
        val weak = PSSParameterSpec("SHA-1", "MGF1", MGF1ParameterSpec.SHA1, 20, PSSParameterSpec.TRAILER_FIELD_BC)
        val confused = PSSParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA384, 32, PSSParameterSpec.TRAILER_FIELD_BC)

        assertTrue(!PassportSignaturePolicy.isReviewedParameters("SSAwithRSA/PSS", "SHA-256", weak))
        assertTrue(!PassportSignaturePolicy.isReviewedParameters("SSAwithRSA/PSS", "SHA-256", confused))
    }

    private fun publicKey(keyId: BigInteger?): ChipAuthenticationPublicKeyInfo {
        val generator = KeyPairGenerator.getInstance("EC")
        generator.initialize(ECGenParameterSpec("secp256r1"))
        return ChipAuthenticationPublicKeyInfo(SecurityInfo.ID_PK_ECDH, generator.generateKeyPair().public, keyId)
    }
}
