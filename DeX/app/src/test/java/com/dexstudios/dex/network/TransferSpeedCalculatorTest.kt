package com.dexstudios.dex.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TransferSpeedCalculatorTest {

    @Test
    fun testInstantaneousAndSmoothedSpeedCalculation() {
        val calc = TransferSpeedCalculator()

        val s1 = calc.sample(currentBytes = 0L, totalBytes = 100_000_000L, nowMs = 1000L)
        assertEquals(0L, s1.speedBps)
        assertNull(s1.etaSeconds)

        val s2 = calc.sample(currentBytes = 10_000_000L, totalBytes = 100_000_000L, nowMs = 2000L)
        assertEquals(10_000_000L, s2.speedBps)
        assertEquals(9L, s2.etaSeconds)

        val s3 = calc.sample(currentBytes = 15_000_000L, totalBytes = 100_000_000L, nowMs = 3000L)
        assertEquals(8_500_000L, s3.speedBps)
        assertNotNull(s3.etaSeconds)
        assertEquals(10L, s3.etaSeconds)
    }

    @Test
    fun testFormatSpeedUnits() {
        assertEquals("", TransferSpeedCalculator.formatSpeed(0L))
        assertEquals("500 B/s", TransferSpeedCalculator.formatSpeed(500L))
        assertEquals("250 KB/s", TransferSpeedCalculator.formatSpeed(250 * 1024L))
        assertEquals("12.5 MB/s", TransferSpeedCalculator.formatSpeed((12.5 * 1024 * 1024).toLong()))
        assertEquals("1.2 GB/s", TransferSpeedCalculator.formatSpeed((1.2 * 1024 * 1024 * 1024).toLong()))
    }

    @Test
    fun testFormatEtaUnits() {
        assertNull(TransferSpeedCalculator.calculateEtaSeconds(0L, 1000L))
        assertNull(TransferSpeedCalculator.calculateEtaSeconds(1000L, 0L))

        assertEquals("", TransferSpeedCalculator.formatEta(null))
        assertEquals("", TransferSpeedCalculator.formatEta(0L))
        assertEquals("< 5s", TransferSpeedCalculator.formatEta(3L))
        assertEquals("45s left", TransferSpeedCalculator.formatEta(45L))
        assertEquals("2m 15s left", TransferSpeedCalculator.formatEta(135L))
        assertEquals("5m left", TransferSpeedCalculator.formatEta(300L))
        assertEquals("1h 10m left", TransferSpeedCalculator.formatEta(4200L))
        assertEquals("2h left", TransferSpeedCalculator.formatEta(7200L))
    }
}
