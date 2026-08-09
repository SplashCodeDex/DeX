package com.dexstudios.dex.network

import android.content.Context
import android.content.Intent
import com.dexstudios.dex.BuildConfig
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import timber.log.Timber

/**
 * Google Sign-In for the verified email identity. The sign-in happens once per device;
 * the verified email becomes the shared identity hash (same-email auto-trust) for
 * THIS device only — the PC has its own independent sign-in and is never auto-signed-in.
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
}
