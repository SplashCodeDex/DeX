package com.dexstudios.dex.network

import android.content.Context
import android.content.Intent
import com.dexstudios.dex.BuildConfig
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import timber.log.Timber

/**
 * Google Sign-In for the verified email identity. The sign-in happens once per device;
 * the verified email becomes the shared identity hash (same-email auto-trust) and is
 * pushed to the connected PC via the "set-email" WebSocket message.
 *
 * Requires a Google Cloud OAuth client ID (Android type) — provide it at build time via
 * -PGOOGLE_SIGN_IN_CLIENT_ID. Until then the feature is gracefully hidden.
 */
object GoogleSignInManager {
    fun isConfigured(): Boolean = BuildConfig.GOOGLE_SIGN_IN_CLIENT_ID.isNotBlank()

    fun client(context: Context): GoogleSignInClient? {
        if (!isConfigured()) return null
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestId()
            .build()
        return GoogleSignIn.getClient(context, options)
    }

    fun signInIntent(context: Context): Intent? = client(context)?.signInIntent

    /** Extracts the signed-in account from the activity result, or null on failure. */
    fun handleResult(data: Intent?): GoogleSignInAccount? = try {
        GoogleSignIn.getSignedInAccountFromIntent(data).getResult(ApiException::class.java)
    } catch (e: ApiException) {
        Timber.e(e, "Google Sign-In failed: ${e.statusCode}")
        null
    }

    /**
     * Applies a verified account to the device identity: email (drives the shared identity
     * hash), the Google account ID (the unguessable trust key), plus the profile name and
     * avatar used by the UI. Returns the verified email, or null when the account is invalid.
     */
    fun applyToDeviceConfig(account: GoogleSignInAccount, deviceConfig: DeviceConfig): String? {
        val email = account.email
        if (email.isNullOrBlank()) return null
        deviceConfig.email = email
        deviceConfig.setGoogleSub(account.id ?: "")
        deviceConfig.setGoogleProfile(account.displayName ?: "", account.photoUrl?.toString() ?: "")
        return email
    }

    /**
     * Propagates this device's identity + profile to the connected PC (same-email mesh).
     * The PC accepts it only from verified devices or after a local confirmation.
     */
    fun pushIdentityToPc(wsService: WebSocketClientService, deviceConfig: DeviceConfig) {
        val email = deviceConfig.email
        if (email.isBlank()) return
        val payload = kotlinx.serialization.json.buildJsonObject {
            put("type", "set-email")
            putJsonObject("data") {
                put("email", email)
                put("name", deviceConfig.profileName)
                put("picture", deviceConfig.profilePicture)
                put("sub", deviceConfig.googleSub)
            }
        }
        wsService.sendMessage(payload.toString())
        // The PC's identity may have just changed: reconnect so this phone re-verifies instantly
        wsService.reconnectNow()
    }
}
