package com.onlasdan.netnet.monitor

import android.content.Context
import com.onlasdan.netnet.model.DetailedNetworkDiagnostics
import com.onlasdan.netnet.model.NetworkType

object NetworkDiagnosticsHelper {

    fun getDiagnostics(context: Context): DetailedNetworkDiagnostics {
        return try {
            NetworkStateManager.getInstance(context).getDiagnostics()
        } catch (_: Throwable) {
            DetailedNetworkDiagnostics(
                connectionStatus = "Available",
                networkType = NetworkType.WIFI,
                networkName = "Network Active"
            )
        }
    }
}

