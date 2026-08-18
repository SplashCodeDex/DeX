package com.dexstudios.dex.core.network.security

import java.net.Socket
import java.security.Principal
import java.security.PrivateKey
import java.security.cert.X509Certificate
import javax.net.ssl.SSLEngine
import javax.net.ssl.X509ExtendedKeyManager

class DynamicKeyManager(
    @Volatile private var delegate: X509ExtendedKeyManager
) : X509ExtendedKeyManager() {

    fun updateKeyManager(newDelegate: X509ExtendedKeyManager) {
        this.delegate = newDelegate
    }

    override fun getClientAliases(keyType: String?, issuers: Array<out Principal>?): Array<String>? =
        delegate.getClientAliases(keyType, issuers)

    override fun chooseClientAlias(keyType: Array<out String>?, issuers: Array<out Principal>?, socket: Socket?): String? =
        delegate.chooseClientAlias(keyType, issuers, socket)

    override fun chooseEngineClientAlias(keyType: Array<out String>?, issuers: Array<out Principal>?, engine: SSLEngine?): String? =
        delegate.chooseEngineClientAlias(keyType, issuers, engine)

    override fun getServerAliases(keyType: String?, issuers: Array<out Principal>?): Array<String>? =
        delegate.getServerAliases(keyType, issuers)

    override fun chooseServerAlias(keyType: String?, issuers: Array<out Principal>?, socket: Socket?): String? =
        delegate.chooseServerAlias(keyType, issuers, socket)

    override fun chooseEngineServerAlias(keyType: String?, issuers: Array<out Principal>?, engine: SSLEngine?): String? =
        delegate.chooseEngineServerAlias(keyType, issuers, engine)

    override fun getCertificateChain(alias: String?): Array<X509Certificate>? =
        delegate.getCertificateChain(alias)

    override fun getPrivateKey(alias: String?): PrivateKey? =
        delegate.getPrivateKey(alias)
}
