package com.dexstudios.dex.auth

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateListOf

object AuthState {
    val pairedFingerprints = mutableStateListOf<String>()
    val pairedTokens = mutableStateMapOf<String, String>()
    val pairedTimes = mutableStateMapOf<String, Long>()
}
