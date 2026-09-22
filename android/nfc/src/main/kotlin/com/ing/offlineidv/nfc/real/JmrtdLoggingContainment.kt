package com.ing.offlineidv.nfc.real

import java.util.logging.Level
import java.util.logging.Logger

/** Installs process-local containment before Atlas invokes third-party passport code. */
internal object JmrtdLoggingContainment {
    private val namespaces = listOf("org.jmrtd", "net.sf.scuba", "org.bouncycastle")

    @Synchronized
    fun install() {
        namespaces.forEach { namespace ->
            Logger.getLogger(namespace).apply {
                level = Level.OFF
                useParentHandlers = false
                handlers.forEach { handler -> removeHandler(handler) }
            }
        }
    }
}
