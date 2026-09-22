package com.ing.offlineidv.demo

import org.junit.Assert.assertTrue
import org.junit.Test

public class RealAndroidDocumentProfileTest {
    @Test
    public fun `passport profile preserves chip and face requirements`() {
        val profile = RealAndroidDocumentType.PASSPORT_TD3.profile()

        assertTrue(profile.advertiseDetectedNfc)
        assertTrue(profile.policy.requireNfcRead)
        assertTrue(profile.policy.requirePrintedChipConsistency)
        assertTrue(profile.policy.requireFaceMatch)
    }

    @Test
    public fun `residence permit profile requires signed data and live chip proof but not face matching`() {
        val profile = RealAndroidDocumentType.NETHERLANDS_RESIDENCE_PERMIT_TD1.profile()

        assertTrue(profile.advertiseDetectedNfc)
        assertTrue(profile.policy.requireNfcRead)
        assertTrue(profile.policy.requirePrintedChipConsistency)
        assertTrue(profile.policy.requirePassiveAuthentication)
        assertTrue(profile.policy.requireChipAuthentication)
        assertTrue(!profile.policy.requireFaceMatch)
        assertTrue(profile.policy.requireMrzStructure)
        assertTrue(profile.policy.requireMrzCheckDigits)
    }
}
