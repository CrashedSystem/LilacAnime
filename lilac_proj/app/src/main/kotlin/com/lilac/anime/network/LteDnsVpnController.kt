package com.lilac.anime.network

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.VpnService
import androidx.core.content.ContextCompat

object LteDnsVpnController {
    fun isRunning(): Boolean = LteDnsVpnService.running

    fun isCellular(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
    }

    fun prepareIntent(context: Context): Intent? = VpnService.prepare(context)

    fun start(context: Context) {
        if (!isCellular(context)) return
        val intent = Intent(context, LteDnsVpnService::class.java).apply {
            action = LteDnsVpnService.ACTION_START
        }
        ContextCompat.startForegroundService(context, intent)
    }

    fun stop(context: Context) {
        val intent = Intent(context, LteDnsVpnService::class.java).apply {
            action = LteDnsVpnService.ACTION_STOP
        }
        context.startService(intent)
    }
}
