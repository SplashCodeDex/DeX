package com.dexstudios.dex.core.network

import android.content.Context

interface IPullForegroundService {
    fun start(context: Context, requestId: String, count: Int)
    fun stop(context: Context)
}
