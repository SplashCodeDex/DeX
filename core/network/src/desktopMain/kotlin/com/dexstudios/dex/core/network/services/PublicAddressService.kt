package com.dexstudios.dex.core.network.services

import io.ktor.client.*
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Resolves this PC's public internet address so same-account phones can reach it over WAN
 * without manual configuration. UPnP result wins (authoritative for this gateway); a
 * public IP echo service is the fallback. Refreshed at most every [CACHE_TTL_MS].
 */
class PublicAddressService(private val httpClient: HttpClient, private val upnpService: DesktopUpnpService?) {
    private val mutex = Mutex()
    private var cached: String? = null
    private var cachedAt: Long = 0L

    suspend fun publicAddress(): String? = mutex.withLock {
        val now = System.currentTimeMillis()
        if (cached != null && now - cachedAt < CACHE_TTL_MS) return cached

        val fresh = upnpService?.publicIp?.value?.takeIf { it.isNotBlank() }
            ?: fetchEchoIp()
        if (!fresh.isNullOrBlank()) {
            cached = fresh
            cachedAt = now
        }
        cached
    }

    /** Forces re-resolution on the next call (e.g. after a WAN transfer failure). */
    fun invalidate() {
        cached = null
        cachedAt = 0L
    }

    private suspend fun fetchEchoIp(): String? = runCatching {
        val response = httpClient.get("https://api.ipify.org") {
            timeout { requestTimeoutMillis = 5_000 }
        }
        val body = response.bodyAsText().trim()
        body.takeIf { response.status.value == 200 && IPV4.matches(it) }
    }.getOrNull()

    private companion object {
        const val CACHE_TTL_MS = 10 * 60 * 1000L
        val IPV4 = Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")
    }
}
