package com.dexstudios.dex.core.network

/**
 * Every TCP port the DeX desktop stack binds or targets. No listener construction,
 * client URL, or fallback may restate a number inline — reference these constants.
 */
object DeXPorts {
    const val QUIC = 48423
    const val HTTPS = 48424

    /** Loopback-only Google OAuth browser-redirect listener (legacy WPF Kestrel contract). */
    const val OAUTH_CALLBACK = 48425
    const val PULL = 48426

    /** Loopback-only control plane (settings/sign-in triggers from the UI process). */
    const val LOOPBACK_CONTROL = 28425

    /** LocalSend protocol default — the port peers advertise when nothing else is set. */
    const val LOCALSEND_DEFAULT = 53317
}
