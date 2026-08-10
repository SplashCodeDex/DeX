package com.dexstudios.dex.network

import android.app.Activity
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.dexstudios.dex.BuildConfig
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import timber.log.Timber

/**
 * Google Sign-In for the verified email identity.
 * Migrated to Credential Manager API.
 */
object GoogleSignInManager {
    fun isConfigured(): Boolean = BuildConfig.GOOGLE_SIGN_IN_CLIENT_ID.isNotBlank()

    /**
     * Starts the Google Sign-In flow using Credential Manager.
     * Returns the credential on success, or null on failure.
     */
    suspend fun signIn(activity: Activity): GoogleIdTokenCredential? {
        if (!isConfigured()) return null

        val credentialManager = CredentialManager.create(activity)

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(BuildConfig.GOOGLE_SIGN_IN_CLIENT_ID)
            .setAutoSelectEnabled(true)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        return try {
            val result = credentialManager.getCredential(activity, request)
            when (val credential = result.credential) {
                is GoogleIdTokenCredential -> credential
                else -> {
                    Timber.w("Unexpected credential type: ${credential.type}")
                    null
                }
            }
        } catch (e: GetCredentialException) {
            Timber.e(e, "Google Sign-In failed: ${e.message}")
            null
        } catch (e: Exception) {
            Timber.e(e, "Google Sign-In error: ${e.message}")
            null
        }
    }

    /**
     * Applies a verified account to the device identity.
     */
    fun applyToDeviceConfig(credential: GoogleIdTokenCredential, deviceConfig: DeviceConfig): String? {
        val email = credential.id
        if (email.isBlank()) return null
        deviceConfig.email = email

        val sub = try {
            val parts = credential.idToken.split(".")
            if (parts.size >= 2) {
                val payload = String(android.util.Base64.decode(parts[1], android.util.Base64.URL_SAFE))
                org.json.JSONObject(payload).getString("sub")
            } else ""
        } catch (e: Exception) {
            ""
        }

        deviceConfig.setGoogleSub(sub)
        deviceConfig.setGoogleProfile(credential.displayName ?: "", credential.profilePictureUri?.toString() ?: "")
        return email
    }
}
