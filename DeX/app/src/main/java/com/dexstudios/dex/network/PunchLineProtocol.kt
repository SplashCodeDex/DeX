package com.dexstudios.dex.network

import java.io.InputStream
import java.io.OutputStream

/**
 * Punch handshake framing (plan 042 — moved verbatim from PunchSession).
 *
 * Wire law: lines are UTF-8, terminated by '\n'. A line longer than 64 KiB is
 * treated as hostile and dropped (null). Binary reads must never mix with
 * [readLine] on the same stream.
 */
internal object PunchLineProtocol {

    fun writeLine(output: OutputStream, line: String) {
        output.write((line + "\n").toByteArray(Charsets.UTF_8))
        output.flush()
    }

    /** Line reader over a raw stream (never mixes with binary reads on the same stream). */
    fun readLine(input: InputStream): String? {
        val bytes = java.io.ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            if (b == -1) return null
            if (b == '\n'.code) break
            bytes.write(b)
            if (bytes.size() > 64 * 1024) return null
        }
        return String(bytes.toByteArray(), Charsets.UTF_8)
    }
}
