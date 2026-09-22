package com.ing.offlineidv.accessibility

/** Shared, non-sensitive announcements for important demo states. */
public object AtlasAccessibility {
    public const val DEMO_DISCLOSURE: String =
        "Offline Demo Mode. All information is synthetic and remains on this device."
    public const val DOCUMENT_FRAME: String =
        "Synthetic passport frame. Place all four document corners inside the guide."
    public const val NFC_SIMULATION: String =
        "Simulated NFC passport chip read. No NFC hardware is used."
    public const val NFC_READER: String =
        "NFC document chip reader. Hold the phone against the document."
    public const val SELFIE_FRAME: String =
        "Synthetic selfie frame. Center one face inside the oval guide."

    /** Produces a stable TalkBack description without exposing session or evidence values. */
    public fun progressDescription(
        currentStep: Int,
        totalSteps: Int,
        label: String,
    ): String = "Step $currentStep of $totalSteps, $label"
}
