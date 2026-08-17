package com.dexstudios.dex.core.network

import java.io.InputStream
import java.nio.channels.WritableByteChannel

interface IQuicClient {
    val lastUploadProtocol: String
    fun available(): Boolean
    fun uploadFile(ip: String, port: Int, sessionId: String, fileId: String, fileName: String, token: String, stream: InputStream, fileSize: Long, onProgress: (Long) -> Unit, onResult: (Boolean, Int) -> Unit): Any?
    fun downloadFile(ip: String, port: Int, fileId: String, token: String?, output: WritableByteChannel, onProgress: (Long) -> Unit, onResult: (Boolean, Int, String) -> Unit): Any?
}
