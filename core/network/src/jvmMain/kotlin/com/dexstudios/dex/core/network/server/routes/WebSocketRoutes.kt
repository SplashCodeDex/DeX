package com.dexstudios.dex.core.network.server.routes

import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.consumeEach
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.buildJsonObject
import com.dexstudios.dex.core.network.server.DexRequestStore
import com.dexstudios.dex.core.network.server.WebSocketConnectionManager

fun Route.webSocketRoutes(
    pairingEngine: com.dexstudios.dex.auth.PairingEngine,
    mirrorEngine: com.dexstudios.dex.core.network.IMirrorEngine
) {
    webSocket("/ws") {
        val fingerprint = call.request.queryParameters["fingerprint"]
        if (fingerprint != null) {
            WebSocketConnectionManager.register(fingerprint, this)
        }
        println("WebSocket connection established: ${call.request.local.remoteHost} (FP: $fingerprint)")

        try {
            incoming.consumeEach { frame ->
                if (frame is Frame.Text) {
                    val text = frame.readText()
                    try {
                        val jsonElement = Json.parseToJsonElement(text)
                        val jsonObject = jsonElement.jsonObject
                        val type = jsonObject["type"]?.jsonPrimitive?.content
                        val dataObj = jsonObject["data"] as? JsonObject

                        println("Received WS message type: $type from FP: $fingerprint")

                        when (type) {
                            "list-shared-folders-reply",
                            "browse-reply",
                            "grant-shared-folder-reply",
                            "grant-reply",
                            "pull-reply",
                            "reply" -> {
                                val reqId = dataObj?.get("requestId")?.jsonPrimitive?.content
                                    ?: jsonObject["requestId"]?.jsonPrimitive?.content
                                if (reqId != null) {
                                    DexRequestStore.completeRequest(reqId, dataObj ?: jsonObject)
                                }
                            }
                            "pair-request" -> {
                                if (fingerprint != null) {
                                    val ip = call.request.local.remoteHost
                                    val pin = pairingEngine.handleInboundPairingRequest(ip, fingerprint)
                                    val promptJson = buildJsonObject {
                                        put("type", "pair-prompt")
                                        putJsonObject("data") {
                                            put("pin", pin)
                                            put("alias", "DeX Desktop")
                                            put("fingerprint", com.dexstudios.dex.core.network.auth.IdentityManager.fingerprint)
                                        }
                                    }.toString()
                                    WebSocketConnectionManager.sendRequest(fingerprint, promptJson)
                                }
                            }
                            "pair-response" -> {
                                val accepted = dataObj?.get("accepted")?.jsonPrimitive?.content?.toBoolean() == true
                                if (accepted && fingerprint != null) {
                                    com.dexstudios.dex.core.network.DeviceManager.savePairedFingerprint(fingerprint)
                                }
                                pairingEngine.handlePairResponse(accepted)
                            }
                            "pin-digit-entered" -> {
                                val count = jsonObject["count"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                                pairingEngine.handlePinDigitEntered(count)
                            }
                            "pull-progress" -> {
                                val reqId = dataObj?.get("requestId")?.jsonPrimitive?.content
                                if (reqId != null && dataObj != null) {
                                    // Progress update
                                    println("Pull progress for $reqId: $dataObj")
                                }
                            }
                            else -> {
                                // Additional protocol routing
                            }
                        }
                    } catch (e: Exception) {
                        println("Failed to parse WebSocket message: ${e.message}")
                    }
                } else if (frame is Frame.Binary) {
                    val bytes = frame.readBytes()
                    mirrorEngine.receiveFrame(bytes)
                }
            }
        } catch (e: Exception) {
            println("WebSocket error: ${e.message}")
        } finally {
            if (fingerprint != null) {
                WebSocketConnectionManager.unregister(fingerprint)
            }
            println("WebSocket connection closed: ${call.request.local.remoteHost} (FP: $fingerprint)")
        }
    }
}
