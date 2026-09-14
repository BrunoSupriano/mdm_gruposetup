package br.com.gruposetup.mdm

import okhttp3.OkHttpClient
import java.net.InetAddress
import java.net.Socket
import java.security.KeyStore
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/** Forca TLS 1.2 em sockets no Android 5.x, onde nao vem habilitado por padrao. */
class Tls12SocketFactory(private val delegate: SSLSocketFactory) : SSLSocketFactory() {

    private val tls = arrayOf("TLSv1.2")

    override fun getDefaultCipherSuites(): Array<String> = delegate.defaultCipherSuites
    override fun getSupportedCipherSuites(): Array<String> = delegate.supportedCipherSuites

    private fun patch(s: Socket): Socket {
        if (s is SSLSocket) s.enabledProtocols = tls
        return s
    }

    override fun createSocket(s: Socket, host: String, port: Int, autoClose: Boolean): Socket =
        patch(delegate.createSocket(s, host, port, autoClose))
    override fun createSocket(host: String, port: Int): Socket = patch(delegate.createSocket(host, port))
    override fun createSocket(host: String, port: Int, localHost: InetAddress, localPort: Int): Socket =
        patch(delegate.createSocket(host, port, localHost, localPort))
    override fun createSocket(host: InetAddress, port: Int): Socket = patch(delegate.createSocket(host, port))
    override fun createSocket(address: InetAddress, port: Int, localAddress: InetAddress, localPort: Int): Socket =
        patch(delegate.createSocket(address, port, localAddress, localPort))

    companion object {
        fun enable(builder: OkHttpClient.Builder) {
            try {
                val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
                tmf.init(null as KeyStore?)
                val x509 = tmf.trustManagers.firstOrNull { it is X509TrustManager } as? X509TrustManager ?: return
                val ctx = SSLContext.getInstance("TLSv1.2")
                ctx.init(null, arrayOf<TrustManager>(x509), null)
                builder.sslSocketFactory(Tls12SocketFactory(ctx.socketFactory), x509)
            } catch (e: Exception) { /* ignora e segue com defaults */ }
        }
    }
}
