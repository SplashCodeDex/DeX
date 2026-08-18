package com.dexstudios.dex.core.network.security

import java.io.File
import java.security.KeyStore

object CertificateGenerator {
    private val keyStoreFile = File(System.getProperty("java.io.tmpdir"), "dex_cert.jks")
    const val PASSWORD = "dexpassword"
    
    fun getOrCreateKeyStore(): KeyStore {
        if (!keyStoreFile.exists()) {
            generateKeyStore()
        }
        
        return keyStoreFile.inputStream().use {
            val ks = KeyStore.getInstance("JKS")
            ks.load(it, PASSWORD.toCharArray())
            ks
        }
    }
    
    private fun generateKeyStore() {
        try {
            keyStoreFile.delete()
            val javaHome = System.getProperty("java.home")
            
            // Handle keytool.exe on Windows
            val keytoolName = if (System.getProperty("os.name").lowercase().contains("windows")) "keytool.exe" else "keytool"
            val keytool = File(javaHome, "bin/$keytoolName").absolutePath
            
            val process = ProcessBuilder(
                keytool,
                "-genkeypair",
                "-alias", "dex",
                "-keyalg", "RSA",
                "-keysize", "2048",
                "-validity", "3650",
                "-keystore", keyStoreFile.absolutePath,
                "-storepass", PASSWORD,
                "-keypass", PASSWORD,
                "-dname", "CN=DeX, OU=DeXStudios, O=DeXStudios, L=Unknown, ST=Unknown, C=Unknown"
            ).inheritIO().start()
            
            process.waitFor()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
