package com.lilac.anime.network

import android.net.VpnService
import android.util.Log
import okhttp3.Dns
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.InetAddress
import java.net.Socket
import javax.net.SocketFactory
import java.util.concurrent.TimeUnit

/** Sends raw DNS wire-format messages to Cloudflare DoH.
 * The sockets are protected from the VPN so the DoH connection does not loop back into TUN.
 */
class LteDoHClient(private val vpnService: VpnService) {
    companion object {
        private const val TAG = "LilacDoH"
        private const val ENDPOINT = "https://cloudflare-dns.com/dns-query"
        private val DNS_MESSAGE = "application/dns-message".toMediaType()
    }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .dns(object : Dns {
            override fun lookup(hostname: String): List<InetAddress> {
                if (hostname == "cloudflare-dns.com") {
                    // Bootstrap without relying on the system DNS resolver.
                    return listOf(
                        InetAddress.getByName("1.1.1.1"),
                        InetAddress.getByName("1.0.0.1")
                    )
                }
                return Dns.SYSTEM.lookup(hostname)
            }
        })
        .socketFactory(ProtectingSocketFactory(vpnService))
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .build()

    fun query(dnsWire: ByteArray): ByteArray? {
        val request = Request.Builder()
            .url(ENDPOINT)
            .header("Accept", "application/dns-message")
            .post(dnsWire.toRequestBody(DNS_MESSAGE))
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "DoH HTTP ${response.code}")
                    return null
                }
                val contentType = response.header("Content-Type").orEmpty()
                if (!contentType.lowercase().contains("application/dns-message")) {
                    Log.w(TAG, "Unexpected DoH content-type=$contentType")
                }
                response.body?.bytes()
            }
        } catch (t: Throwable) {
            Log.e(TAG, "DoH request failed", t)
            null
        }
    }
}

private class ProtectingSocketFactory(
    private val vpnService: VpnService
) : SocketFactory() {
    private val delegate = SocketFactory.getDefault()

    private fun protect(socket: Socket): Socket {
        if (!vpnService.protect(socket)) {
            socket.close()
            throw IllegalStateException("VpnService.protect() failed")
        }
        return socket
    }

    override fun createSocket(): Socket = protect(delegate.createSocket())
    override fun createSocket(host: String, port: Int): Socket = protect(delegate.createSocket(host, port))
    override fun createSocket(host: String, port: Int, localHost: InetAddress, localPort: Int): Socket =
        protect(delegate.createSocket(host, port, localHost, localPort))
    override fun createSocket(host: InetAddress, port: Int): Socket = protect(delegate.createSocket(host, port))
    override fun createSocket(host: InetAddress, port: Int, localHost: InetAddress, localPort: Int): Socket =
        protect(delegate.createSocket(host, port, localHost, localPort))
}
