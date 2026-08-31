package com.lilac.anime.network

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.content.pm.ServiceInfo
import android.util.Log
import androidx.core.app.NotificationCompat
import com.lilac.anime.R
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.Executors

/** DNS-only VPN: captures only the virtual DNS server, then forwards DNS over HTTPS. */
class LteDnsVpnService : VpnService() {
    companion object {
        const val ACTION_START = "com.lilac.anime.network.START_LTE_DOH"
        const val ACTION_STOP = "com.lilac.anime.network.STOP_LTE_DOH"
        private const val TAG = "LteDnsVpn"
        private const val CHANNEL = "lte_dns"
        private const val NOTIFICATION_ID = 7311
        private const val VPN_ADDRESS = "10.111.222.1"
        private const val DNS_ADDRESS = "10.111.222.3"
        @Volatile var running = false
            private set
    }

    private var tun: ParcelFileDescriptor? = null
    private var worker: java.util.concurrent.Future<*>? = null
    private val executor = Executors.newSingleThreadExecutor()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> if (isCellular()) startVpn()
            ACTION_STOP -> stopVpn()
        }
        return START_STICKY
    }

    private fun startVpn() {
        if (tun != null) return
        if (prepare(this) != null) {
            Log.w(TAG, "VPN permission is not granted")
            return
        }

        createChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification())
        }

        try {
            tun = Builder()
                .setSession("LilacAnime LTE DNS")
                .setMtu(1500)
                .addAddress(VPN_ADDRESS, 32)
                // DNS-only: do NOT add 0.0.0.0/0.
                .addRoute(DNS_ADDRESS, 32)
                .addDnsServer(DNS_ADDRESS)
                .setBlocking(true)
                .establish()

            if (tun == null) {
                Log.e(TAG, "establish() returned null")
                stopVpn()
                return
            }

            running = true
            val fd = tun!!.fileDescriptor
            worker = executor.submit {
                val doh = LteDoHClient(this)
                val handler = DnsPacketHandler(doh)
                FileInputStream(fd).use { input ->
                    FileOutputStream(fd).use { output ->
                        val buffer = ByteArray(32768)
                        while (running && !Thread.currentThread().isInterrupted) {
                            val n = input.read(buffer)
                            if (n <= 0) continue
                            val packet = buffer.copyOf(n)
                            val response = handler.handle(packet)
                            if (response != null) output.write(response)
                        }
                    }
                }
            }
            Log.i(TAG, "LTE DNS VPN started")
        } catch (t: Throwable) {
            Log.e(TAG, "VPN start failed", t)
            stopVpn()
        }
    }

    private fun stopVpn() {
        running = false
        worker?.cancel(true)
        worker = null
        try { tun?.close() } catch (_: Throwable) {}
        tun = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        Log.i(TAG, "LTE DNS VPN stopped")
    }

    private fun isCellular(): Boolean {
        val cm = getSystemService(ConnectivityManager::class.java) ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "LTE DNS 보호", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun notification(): Notification =
        NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("LilacAnime DNS 보호")
            .setContentText("LTE에서 DNS-over-HTTPS 사용 중")
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

    override fun onRevoke() {
        stopVpn()
        super.onRevoke()
    }

    override fun onDestroy() {
        stopVpn()
        executor.shutdownNow()
        super.onDestroy()
    }
}
