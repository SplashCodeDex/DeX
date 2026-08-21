import kotlinx.serialization.json.*

val msg = "{\"alias\":\"Nicholas Adima's S21\",\"version\":\"2.0\",\"deviceModel\":\"SM-G991B\",\"deviceType\":\"mobile\",\"fingerprint\":\"7f3043aa-49c3-4e1d-aa1f-a48f3dcdb3f9\",\"port\":48424,\"quicPort\":48423,\"tcpFallbackPort\":48426,\"protocol\":\"https\",\"download\":false,\"identityHash\":\"fc76dae8-db35-47b2-b178-6e3e9d100f24\"}"

fun main() {
    try {
        val json = Json { ignoreUnknownKeys = true }.parseToJsonElement(msg).jsonObject
        val fp = json["fingerprint"]?.jsonPrimitive?.contentOrNull ?: ""
        println("Success! Fingerprint: $fp")
    } catch (e: Exception) {
        println("FAILED: ${e.message}")
    }
}
main()
