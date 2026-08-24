package com.dexstudios.dex.core.network.security

import java.security.MessageDigest

object IdentityHash {
    fun calculateHash(email: String, googleSub: String, salt: String = "dex_wan_salt"): String {
        val input = "$email:$googleSub:$salt"
        val md = MessageDigest.getInstance("SHA-256")
        val hashBytes = md.digest(input.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}
