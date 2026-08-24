package com.dexstudios.dex.core.network.security

import java.io.File
import java.security.KeyStore

object CertificateGenerator {
    private val appDataDir = File(System.getProperty("user.home"), ".dex/security").apply { mkdirs() }
    private val keyStoreFile = File(appDataDir, "dex_cert.jks")
    private val passwordFile = File(appDataDir, ".dex_cert_pwd")

    fun getPassword(): String {
        if (!passwordFile.exists()) {
            passwordFile.writeText(java.util.UUID.randomUUID().toString())
        }
        return passwordFile.readText()
    }

    fun getOrCreateKeyStore(): KeyStore {
        if (!keyStoreFile.exists()) {
            generateKeyStore(getPassword())
        }

        return try {
            keyStoreFile.inputStream().use {
                val ks = KeyStore.getInstance("JKS")
                ks.load(it, getPassword().toCharArray())
                ks
            }
        } catch (e: Exception) {
            // Password mismatch or corrupted keystore
            keyStoreFile.delete()
            passwordFile.delete()
            val newPassword = getPassword()
            generateKeyStore(newPassword)
            keyStoreFile.inputStream().use {
                val ks = KeyStore.getInstance("JKS")
                ks.load(it, newPassword.toCharArray())
                ks
            }
        }
    }

    private fun generateKeyStore(password: String) {
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
                "-storepass", password,
                "-keypass", password,
                "-dname", "CN=DeX, OU=DeXStudios, O=DeXStudios, L=Unknown, ST=Unknown, C=Unknown",
            ).inheritIO().start()

            process.waitFor()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
