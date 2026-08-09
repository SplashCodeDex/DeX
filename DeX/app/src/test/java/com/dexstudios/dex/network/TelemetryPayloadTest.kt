package com.dexstudios.dex.network

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TelemetryPayloadTest {

    @Test
    fun `payload includes battery ssid and rssi`() {
        val json = Json.parseToJsonElement(buildTelemetryPayload(87, "Home_5G", -52)!!).jsonObject
        assertEquals("telemetry", json["type"]!!.jsonPrimitive.content)
        val data = json["data"]!!.jsonObject
        assertEquals(87, data["battery"]!!.jsonPrimitive.content.toInt())
        assertEquals("Home_5G", data["wifiSsid"]!!.jsonPrimitive.content)
        assertEquals(-52, data["wifiRssi"]!!.jsonPrimitive.content.toInt())
    }

    @Test
    fun `payload omits wifi fields when unavailable`() {
        val json = Json.parseToJsonElement(buildTelemetryPayload(50, null, -127)!!).jsonObject
        val data = json["data"]!!.jsonObject
        assertEquals(50, data["battery"]!!.jsonPrimitive.content.toInt())
        assertNull(data["wifiSsid"])
        assertNull(data["wifiRssi"])
    }

    @Test
    fun `payload reports ssid when rssi is unavailable`() {
        val json = Json.parseToJsonElement(buildTelemetryPayload(-1, "Home_5G", -127)!!).jsonObject
        val data = json["data"]!!.jsonObject
        assertEquals("Home_5G", data["wifiSsid"]!!.jsonPrimitive.content)
        assertNull(data["battery"])
    }

    @Test
    fun `payload is null when nothing to report`() {
        assertNull(buildTelemetryPayload(-1, null, -127))
    }
}
