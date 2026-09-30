package fr.btsharing.tether

import android.content.Context
import android.util.Log
import fr.btsharing.data.TetherMode
import kotlinx.coroutines.delay

object TetherController {
    private const val TAG = "TetherController"

    /**
     * Active/désactive le partage de connexion.
     * 1. Shizuku (shell/root) : Android 11 et suivants, Wi‑Fi et Bluetooth.
     * 2. Bluetooth uniquement : ancienne API BluetoothPan (Android 11).
     * @return true si l'opération a réussi.
     */
    suspend fun setTethering(context: Context, mode: TetherMode, enable: Boolean): Boolean {
        val type = when (mode) {
            TetherMode.WIFI -> ShizukuTethering.TYPE_WIFI
            TetherMode.BLUETOOTH -> ShizukuTethering.TYPE_BLUETOOTH
        }
        if (ShizukuHelper.awaitReady(context)) {
            val result = runCatching { ShizukuTethering.setTethering(type, enable) }
                .onFailure { Log.w(TAG, "Échec via Shizuku", it) }
                .getOrNull()
            Log.i(TAG, "Shizuku $mode enable=$enable -> $result")
            if (result == ShizukuTethering.TETHER_ERROR_NO_ERROR) return settled()
        }
        if (mode == TetherMode.BLUETOOTH && BluetoothPanProxy.setTethering(context, enable)) {
            delay(700)
            if (BluetoothPanProxy.isTetheringOn(context) == enable) return settled()
        }
        return false
    }

    private suspend fun settled(): Boolean {
        delay(500)
        TetherStateMonitor.refresh()
        return true
    }
}
