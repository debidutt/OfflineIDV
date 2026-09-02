package com.ing.offlineidv.core.time

import java.time.Instant

/** Injectable time source used to keep expiry and orchestration tests deterministic. */
public fun interface IdvClock {
    /** Returns the current instant. */
    public fun now(): Instant
}

/** System UTC clock for production composition roots. */
public data object SystemIdvClock : IdvClock {
    override fun now(): Instant = Instant.now()
}
