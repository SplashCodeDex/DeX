package com.dexstudios.dex.core.network.auth

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest
import java.util.UUID

object IdentityManager {
    var fingerprint: String = ""
    var identityHash: String = ""
    var email: String = ""
        set(value) {
            field = value
            if (value.isNotBlank()) {
                val digest = MessageDigest.getInstance("SHA-256")
                val bytes = digest.digest(value.trim().lowercase().toByteArray(Charsets.UTF_8))
                identityHash = bytes.joinToString("") { "%02x".format(it) }.lowercase()
            } else {
                identityHash = UUID.randomUUID().toString()
                googleSub = ""
            }
            persistIdentity()
        }

    var googleSub: String = ""
        set(value) {
            field = value
            persistIdentity()
        }
    
    var pairedFingerprints: MutableSet<String> = mutableSetOf()
    var pairedTokens: MutableMap<String, String> = mutableMapOf()
    var pairedLastSeen: MutableMap<String, Long> = mutableMapOf()
    var deviceAliases: MutableMap<String, String> = mutableMapOf()

    val baseDirectory: File by lazy {
        val osName = System.getProperty("os.name").lowercase()
        if (osName.contains("win")) {
            val localAppData = System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home")
            File(localAppData, "DeX")
        } else {
            File(System.getProperty("user.home"), ".dex_settings")
        }
    }

    private val fileLock = Any()
    
    @Serializable
    private data class IdentityData(
        val fingerprint: String? = null,
        val identityHash: String? = null,
        val email: String? = null,
        val googleSub: String? = null
    )

    private val json = Json { ignoreUnknownKeys = true }

    fun initialize() {
        if (!baseDirectory.exists()) {
            baseDirectory.mkdirs()
        }
        val file = File(baseDirectory, "identity.json")

        if (file.exists()) {
            try {
                val content = file.readText()
                val data = json.decodeFromString<IdentityData>(content)
                fingerprint = data.fingerprint ?: UUID.randomUUID().toString()
                email = data.email ?: ""
                googleSub = data.googleSub ?: ""

                if (email.isNotBlank()) {
                    val digest = MessageDigest.getInstance("SHA-256")
                    val bytes = digest.digest(email.trim().lowercase().toByteArray(Charsets.UTF_8))
                    identityHash = bytes.joinToString("") { "%02x".format(it) }.lowercase()
                } else {
                    identityHash = data.identityHash ?: UUID.randomUUID().toString()
                }
            } catch (e: Exception) {
                fingerprint = UUID.randomUUID().toString()
                identityHash = UUID.randomUUID().toString()
            }
        } else {
            fingerprint = UUID.randomUUID().toString()
            identityHash = UUID.randomUUID().toString()
            synchronized(fileLock) {
                file.writeText(json.encodeToString(IdentityData(fingerprint, identityHash, email, googleSub)))
            }
        }

        loadPairedDevices()
        loadDeviceAliases()
        loadPairedTokens()
        loadPairedLastSeen()
        garbageCollectOrphanedDevices()
    }

    private fun garbageCollectOrphanedDevices() {
        val now = System.currentTimeMillis()
        val thirtyDaysMs = 30L * 24 * 60 * 60 * 1000
        val toRemove = mutableListOf<String>()
        
        for (fp in pairedFingerprints.toList()) {
            val lastSeen = pairedLastSeen[fp]
            if (lastSeen != null) {
                if (now - lastSeen > thirtyDaysMs) toRemove.add(fp)
            } else {
                updateLastSeen(fp)
            }
        }
        for (fp in toRemove) {
            removePairedDevice(fp)
        }
    }

    private fun loadPairedDevices() {
        val file = File(baseDirectory, "paired_devices.json")
        if (file.exists()) {
            try {
                val content = synchronized(fileLock) { file.readText() }
                pairedFingerprints = json.decodeFromString<List<String>>(content).toMutableSet()
            } catch (e: Exception) {}
        }
    }

    fun savePairedDevice(fp: String) {
        pairedFingerprints.add(fp)
        val file = File(baseDirectory, "paired_devices.json")
        synchronized(fileLock) {
            file.writeText(json.encodeToString(pairedFingerprints.toList()))
        }
    }

    fun removePairedDevice(fp: String) {
        if (pairedFingerprints.remove(fp)) {
            val file = File(baseDirectory, "paired_devices.json")
            synchronized(fileLock) {
                file.writeText(json.encodeToString(pairedFingerprints.toList()))
            }
        }
        if (pairedTokens.remove(fp) != null) {
            val file = File(baseDirectory, "paired_tokens.json")
            synchronized(fileLock) {
                file.writeText(json.encodeToString(pairedTokens))
            }
        }
        if (pairedLastSeen.remove(fp) != null) {
            val file = File(baseDirectory, "paired_lastseen.json")
            synchronized(fileLock) {
                file.writeText(json.encodeToString(pairedLastSeen))
            }
        }
    }

    private fun loadPairedLastSeen() {
        val file = File(baseDirectory, "paired_lastseen.json")
        if (file.exists()) {
            try {
                val content = synchronized(fileLock) { file.readText() }
                pairedLastSeen = json.decodeFromString<Map<String, Long>>(content).toMutableMap()
            } catch (e: Exception) {}
        }
    }

    fun updateLastSeen(fp: String) {
        pairedLastSeen[fp] = System.currentTimeMillis()
        val file = File(baseDirectory, "paired_lastseen.json")
        synchronized(fileLock) {
            file.writeText(json.encodeToString(pairedLastSeen))
        }
    }

    private fun loadPairedTokens() {
        val file = File(baseDirectory, "paired_tokens.json")
        if (file.exists()) {
            try {
                val content = synchronized(fileLock) { file.readText() }
                pairedTokens = json.decodeFromString<Map<String, String>>(content).toMutableMap()
            } catch (e: Exception) {}
        }
    }

    fun savePairedToken(fp: String, token: String) {
        pairedTokens[fp] = token
        val file = File(baseDirectory, "paired_tokens.json")
        synchronized(fileLock) {
            file.writeText(json.encodeToString(pairedTokens))
        }
    }

    private fun loadDeviceAliases() {
        val file = File(baseDirectory, "paired_aliases.json")
        if (file.exists()) {
            try {
                val content = synchronized(fileLock) { file.readText() }
                deviceAliases = json.decodeFromString<Map<String, String>>(content).toMutableMap()
            } catch (e: Exception) {}
        }
    }

    fun setDeviceAlias(fp: String, alias: String) {
        deviceAliases[fp] = alias
        val file = File(baseDirectory, "paired_aliases.json")
        synchronized(fileLock) {
            file.writeText(json.encodeToString(deviceAliases))
        }
    }

    fun getDeviceAlias(fp: String): String {
        return deviceAliases[fp] ?: ""
    }

    fun isIdentityToken(token: String?): Boolean {
        if (token.isNullOrEmpty()) return false
        if (googleSub.isNotEmpty()) return token == googleSub
        return token == identityHash
    }

    fun isPairedTokenOrFingerprint(token: String?, fp: String?): Boolean {
        if (!fp.isNullOrEmpty() && pairedFingerprints.contains(fp)) return true
        if (!token.isNullOrEmpty() && (isIdentityToken(token) || pairedTokens.containsValue(token))) return true
        return false
    }

    private fun persistIdentity() {
        val file = File(baseDirectory, "identity.json")
        synchronized(fileLock) {
            file.writeText(json.encodeToString(IdentityData(fingerprint, identityHash, email, googleSub)))
        }
    }
}
