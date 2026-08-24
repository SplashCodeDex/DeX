package com.dexstudios.dex.core.network

import kotlinx.serialization.Serializable

@Serializable
data class RegisterDto(
    val alias: String,
    val version: String,
    val deviceModel: String,
    val deviceType: String,
    val fingerprint: String,
    val port: Int,
    val quicPort: Int = DeXPorts.QUIC,
    val tcpFallbackPort: Int = DeXPorts.PULL,
    val protocol: String,
    val download: Boolean,
    val identityHash: String? = null,
    val googleSub: String? = null,
    val battery: Int? = null,
    val isCharging: Boolean? = null,
    val wifiBand: String? = null,
    val wifiSsid: String? = null,
)

@Serializable
data class PrepareUploadRequestDto(val info: RegisterDto, val files: Map<String, FileDto>)

@Serializable
data class FileDto(
    val id: String,
    val fileName: String,
    val size: Long,
    val fileType: String,
    val sha256: String? = null,
    val preview: String? = null,
    val partialHash: String? = null,
    val token: String? = null,
    val relativePath: String? = null,
)

@Serializable
data class PrepareUploadResponseDto(val sessionId: String, val files: Map<String, String>)

data class DiscoveredDevice(
    val ip: String,
    val info: RegisterDto,
    val lastSeenTimestamp: Long = System.currentTimeMillis(),
    // True for the synthetic WAN target — its IP must never be treated as a LAN address
    val viaWan: Boolean = false,
    // True for same-email devices advertised by the PC (transfers route via NAT punch)
    val viaRoster: Boolean = false,
)

@Serializable
data class PairRequestDto(val alias: String, val fingerprint: String, val pin: String, val token: String? = null)

@Serializable
data class PullFileDto(val fileId: String, val fileName: String, val size: Long, val token: String? = null, val relativePath: String? = null)

@Serializable
data class PublicAddressDto(val address: String)

// ---- Direct phone-to-phone (NAT-punched) transfer protocol ----

@Serializable
data class EndpointInfoDto(val targetFingerprint: String = "", val ip: String = "", val port: Int = 0)

@Serializable
data class PeerEndpointDto(val peerFingerprint: String = "", val ip: String = "", val port: Int = 0)

@Serializable
data class RosterDeviceDto(val fingerprint: String = "", val alias: String = "", val deviceType: String = "mobile")

@Serializable
data class RosterDto(val devices: List<RosterDeviceDto> = emptyList())

@Serializable
data class PunchFileDto(val id: String, val fileName: String, val size: Long, val relativePath: String? = null)

@Serializable
data class PunchManifestDto(val type: String = "manifest", val sessionId: String = "", val fingerprint: String, val identityHash: String, val alias: String, val files: List<PunchFileDto>)

@Serializable
data class PunchFileHeaderDto(val type: String = "file", val fileId: String, val size: Long, val offset: Long = 0)

/** Receiver's per-file progress report; lets the sender resume from the last byte. */
@Serializable
data class PunchResumeInfoDto(val type: String = "resume-info", val sessionId: String = "", val files: Map<String, Long> = emptyMap())

@Serializable
data class PunchDoneDto(val type: String = "done", val sessionId: String = "")

// ---- PC File Explorer over the WebSocket (phone exposes shared folders) ----

@Serializable
data class SharedFolderDto(val id: String = "", val name: String = "", val uri: String = "")

/** A single entry in a shared folder listing (file or subfolder). */
@Serializable
data class FolderEntryDto(val name: String, val uri: String, val isDirectory: Boolean = false, val size: Long = 0, val thumb: String? = null)

/** File the PC wants the phone to push back to it (reusing the upload path). */
@Serializable
data class PullFileDto2(val name: String = "", val uri: String = "", val size: Long = 0)
