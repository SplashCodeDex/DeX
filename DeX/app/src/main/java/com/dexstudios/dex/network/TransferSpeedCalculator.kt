package com.dexstudios.dex.network

/**
 * Thread-safe Exponential Moving Average (EMA) transfer speed and ETA calculator for Android.
 *
 * Samples byte deltas over time intervals, applying EMA smoothing (alpha = 0.3)
 * to prevent jitter while maintaining instantaneous responsiveness.
 */
class TransferSpeedCalculator {
    private var lastBytes: Long = 0L
    private var lastTimestampMs: Long = 0L
    private var smoothedSpeedBps: Long = 0L

    data class SampleResult(
        val speedBps: Long,
        val etaSeconds: Long?,
        val formattedSpeed: String,
        val formattedEta: String,
    )

    @Synchronized
    fun reset() {
        lastBytes = 0L
        lastTimestampMs = 0L
        smoothedSpeedBps = 0L
    }

    /**
     * Records a byte progress sample and returns the updated speed and ETA calculations.
     *
     * @param currentBytes Cumulative bytes transferred so far.
     * @param totalBytes Total expected bytes for the transfer (0 if unknown).
     * @param nowMs Current epoch time in milliseconds (defaults to [System.currentTimeMillis]).
     */
    @Synchronized
    fun sample(
        currentBytes: Long,
        totalBytes: Long = 0L,
        nowMs: Long = System.currentTimeMillis(),
    ): SampleResult {
        if (lastTimestampMs == 0L) {
            lastBytes = currentBytes
            lastTimestampMs = nowMs
            return SampleResult(
                speedBps = 0L,
                etaSeconds = null,
                formattedSpeed = "",
                formattedEta = "",
            )
        }

        val deltaMs = nowMs - lastTimestampMs
        if (deltaMs >= 100) {
            val deltaBytes = (currentBytes - lastBytes).coerceAtLeast(0L)
            val instantBps = (deltaBytes * 1000L) / deltaMs

            smoothedSpeedBps = if (smoothedSpeedBps == 0L) {
                instantBps
            } else {
                (smoothedSpeedBps * 7 + instantBps * 3) / 10
            }

            lastBytes = currentBytes
            lastTimestampMs = nowMs
        }

        val remainingBytes = if (totalBytes > currentBytes) totalBytes - currentBytes else 0L
        val etaSec = calculateEtaSeconds(remainingBytes, smoothedSpeedBps)

        return SampleResult(
            speedBps = smoothedSpeedBps,
            etaSeconds = etaSec,
            formattedSpeed = formatSpeed(smoothedSpeedBps),
            formattedEta = formatEta(etaSec),
        )
    }

    companion object {
        fun calculateEtaSeconds(remainingBytes: Long, speedBps: Long): Long? {
            if (speedBps <= 0L || remainingBytes <= 0L) return null
            return remainingBytes / speedBps
        }

        fun formatSpeed(speedBps: Long): String {
            if (speedBps <= 0L) return ""
            val gb = 1024.0 * 1024.0 * 1024.0
            val mb = 1024.0 * 1024.0
            val kb = 1024.0

            return when {
                speedBps >= gb -> {
                    val v = Math.round((speedBps / gb) * 10.0) / 10.0
                    "$v GB/s"
                }
                speedBps >= mb -> {
                    val v = Math.round((speedBps / mb) * 10.0) / 10.0
                    "$v MB/s"
                }
                speedBps >= kb -> {
                    val v = (speedBps / kb).toLong()
                    "$v KB/s"
                }
                else -> "$speedBps B/s"
            }
        }

        fun formatEta(etaSeconds: Long?): String {
            if (etaSeconds == null || etaSeconds <= 0L) return ""
            if (etaSeconds < 5) return "< 5s"
            if (etaSeconds < 60) return "${etaSeconds}s left"

            val mins = etaSeconds / 60
            val secs = etaSeconds % 60
            if (mins < 60) {
                return if (secs > 0) "${mins}m ${secs}s left" else "${mins}m left"
            }

            val hours = mins / 60
            val remMins = mins % 60
            return if (remMins > 0) "${hours}h ${remMins}m left" else "${hours}h left"
        }
    }
}
