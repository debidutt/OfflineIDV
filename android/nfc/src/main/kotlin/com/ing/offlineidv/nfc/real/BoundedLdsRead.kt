package com.ing.offlineidv.nfc.real

import java.io.ByteArrayInputStream
import java.io.InputStream

internal class LdsReadLimitExceeded : Exception()

internal inline fun <R> InputStream.useBoundedBytes(
    maximumBytes: Int,
    block: (InputStream) -> R,
): R = useBoundedByteArray(maximumBytes) { bytes -> ByteArrayInputStream(bytes).use(block) }

internal inline fun <R> InputStream.useBoundedByteArray(
    maximumBytes: Int,
    block: (ByteArray) -> R,
): R {
    require(maximumBytes > 0) { "maximumBytes must be positive" }
    val bytes = use { source -> source.readBounded(maximumBytes) }
    return try {
        block(bytes)
    } finally {
        bytes.fill(0)
    }
}

private fun InputStream.readBounded(maximumBytes: Int): ByteArray {
    val working = ByteArray(maximumBytes + 1)
    var count = 0
    try {
        while (count < working.size) {
            val read = read(working, count, working.size - count)
            if (read < 0) break
            if (read == 0) {
                val single = read()
                if (single < 0) break
                working[count++] = single.toByte()
            } else {
                count += read
            }
        }
        if (count > maximumBytes) throw LdsReadLimitExceeded()
        return working.copyOf(count)
    } finally {
        working.fill(0)
    }
}
