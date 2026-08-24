package com.dexstudios.dex.core.network.auth

import co.touchlab.kermit.Logger
import com.dexstudios.dex.core.network.DeXPorts
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters
import io.ktor.http.isSuccess
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.awt.Desktop
import java.io.File
import java.net.URI
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object GoogleOAuth {

    // Served by DeXServer's dedicated loopback listener (legacy WPF Kestrel parity — this
    // exact URI is registered in the Google Cloud Console client, never change it casually).
    private const val REDIRECT_URI = "http://127.0.0.1:${DeXPorts.OAUTH_CALLBACK}/local/oauth/callback"
    private const val AUTHORIZE_URL = "https://accounts.google.com/o/oauth2/v2/auth"
    private const val TOKEN_URL = "https://oauth2.googleapis.com/token"

    private val pending = ConcurrentHashMap<String, CompletableDeferred<String?>>()

    val baseDirectory: File by lazy {
        val osName = System.getProperty("os.name").lowercase()
        if (osName.contains("win")) {
            val localAppData = System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home")
            File(localAppData, "DeX")
        } else {
            File(System.getProperty("user.home"), ".dex_settings")
        }
    }

    @Serializable
    data class GoogleProfile(val email: String, val name: String, val picture: String, val sub: String)

    private var cachedCredentials: Pair<String, String>? = null

    private val json = Json { ignoreUnknownKeys = true }

    fun isConfigured(): Boolean = loadCredentials() != null

    class CallbackResult(val success: Boolean, val error: String?)

    fun handleCallback(state: String, code: String?): Pair<Boolean, String?> {
        val tcs = pending.remove(state)
        if (tcs != null) {
            if (code.isNullOrEmpty()) {
                tcs.complete(null)
                return Pair(false, "No authorization code in redirect.")
            }
            tcs.complete(code)
            return Pair(true, null)
        }
        return Pair(false, "No pending sign-in for this state (may have expired).")
    }

    suspend fun signInAsync(): GoogleProfile? {
        val creds = loadCredentials() ?: return null

        val secureRandom = SecureRandom()
        val verifierBytes = ByteArray(32)
        secureRandom.nextBytes(verifierBytes)
        val verifier = base64Url(verifierBytes)

        val digest = MessageDigest.getInstance("SHA-256")
        val challengeBytes = digest.digest(verifier.toByteArray(Charsets.UTF_8))
        val challenge = base64Url(challengeBytes)

        val state = UUID.randomUUID().toString().replace("-", "")

        val authUrl = buildString {
            append(AUTHORIZE_URL)
            append("?client_id=").append(URLEncoder.encode(creds.first, "UTF-8"))
            append("&redirect_uri=").append(URLEncoder.encode(REDIRECT_URI, "UTF-8"))
            append("&response_type=code")
            append("&scope=").append(URLEncoder.encode("openid email profile", "UTF-8"))
            append("&code_challenge=").append(challenge)
            append("&code_challenge_method=S256")
            append("&state=").append(state)
            append("&prompt=select_account")
        }

        val deferred = CompletableDeferred<String?>()
        pending[state] = deferred

        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI(authUrl))
            } else {
                pending.remove(state)
                log("Desktop browsing not supported.")
                return null
            }
        } catch (e: Exception) {
            pending.remove(state)
            log("Browser failed to launch: ${e.message}")
            return null
        }

        val code = withTimeoutOrNull(230_000L) {
            deferred.await()
        }

        if (code == null) {
            pending.remove(state)?.complete(null)
            return null
        }

        HttpClient().use { http ->
            val formParameters = Parameters.build {
                append("code", code)
                append("client_id", creds.first)
                append("client_secret", creds.second)
                append("redirect_uri", REDIRECT_URI)
                append("grant_type", "authorization_code")
                append("code_verifier", verifier)
            }

            val response: HttpResponse = http.post(TOKEN_URL) {
                setBody(FormDataContent(formParameters))
            }

            if (!response.status.isSuccess()) return null

            val responseBody = response.bodyAsText()
            val jsonResponse = json.decodeFromString<JsonObject>(responseBody)
            val idToken = jsonResponse["id_token"]?.jsonPrimitive?.contentOrNull ?: return null

            val parts = idToken.split(".")
            if (parts.size != 3) return null

            try {
                val decoded = String(Base64.getUrlDecoder().decode(base64Pad(parts[1])), Charsets.UTF_8)
                val claims = json.decodeFromString<JsonObject>(decoded)

                val email = claims["email"]?.jsonPrimitive?.content ?: return null
                val profile = GoogleProfile(
                    email = email,
                    name = claims["name"]?.jsonPrimitive?.content ?: "",
                    picture = claims["picture"]?.jsonPrimitive?.content ?: "",
                    sub = claims["sub"]?.jsonPrimitive?.content ?: "",
                )
                saveProfile(profile)
                return profile
            } catch (e: Exception) {
                return null
            }
        }
    }

    fun saveProfile(profile: GoogleProfile) {
        try {
            if (!baseDirectory.exists()) {
                baseDirectory.mkdirs()
            }
            val file = File(baseDirectory, "google_profile.json")
            file.writeText(json.encodeToString(profile))
        } catch (e: Exception) {
            Logger.i("GoogleOAuth error: ${e.message}")
        }
    }

    fun loadProfile(): GoogleProfile? {
        try {
            val file = File(baseDirectory, "google_profile.json")
            if (!file.exists()) return null
            return json.decodeFromString<GoogleProfile>(file.readText())
        } catch (e: Exception) {
            return null
        }
    }

    fun signOut() {
        try {
            val file = File(baseDirectory, "google_profile.json")
            if (file.exists()) file.delete()
        } catch (e: Exception) {
            Logger.i("GoogleOAuth error: ${e.message}")
        }
    }

    private fun log(message: String) {
        try {
            if (!baseDirectory.exists()) {
                baseDirectory.mkdirs()
            }
            val file = File(baseDirectory, "oauth.log")
            val timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
            file.appendText("[$timestamp] $message\n")
        } catch (e: Exception) {
            Logger.i("GoogleOAuth file I/O error: ${e.message}")
        }
    }

    private fun base64Url(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    private fun base64Pad(s: String): String = when (s.length % 4) {
        2 -> "$s=="
        3 -> "$s="
        else -> s
    }

    private fun loadCredentials(): Pair<String, String>? {
        if (cachedCredentials != null) return cachedCredentials
        val file = findCredentialsFile() ?: return null
        try {
            val content = file.readText()
            val doc = json.decodeFromString<JsonObject>(content)
            val id = doc["desktopClientId"]?.jsonPrimitive?.content
            val secret = doc["desktopClientSecret"]?.jsonPrimitive?.content
            if (!id.isNullOrEmpty() && !secret.isNullOrEmpty()) {
                cachedCredentials = Pair(id, secret)
            }
        } catch (e: Exception) {
            Logger.i("GoogleOAuth file I/O error: ${e.message}")
        }
        return cachedCredentials
    }

    private fun findCredentialsFile(): File? {
        val currentDir = File(System.getProperty("user.dir"))
        val candidates = mutableListOf(
            File(currentDir, "oauth.local.json"),
        )

        var dir: File? = currentDir
        for (i in 0..5) {
            if (dir == null) break
            candidates.add(File(dir, "oauth.local.json"))
            candidates.add(File(dir, "DeXShareTarget/oauth.local.json"))
            dir = dir.parentFile
        }

        return candidates.firstOrNull { it.exists() && it.isFile }
    }
}
