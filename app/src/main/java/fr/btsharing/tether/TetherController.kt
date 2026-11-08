package fr.btsharing.tether

import android.content.Context
import android.util.Log
import fr.btsharing.data.DeviceRepository
import fr.btsharing.data.TetherMode
import kotlinx.coroutines.delay

object TetherController {
    private const val TAG = "TetherController"

    /** Mémorisé quand Android refuse la méthode directe (opérateur / constructeur) : on propose alors Shizuku. */
    const val FLAG_DIRECT_REFUSED = "DIRECT_REFUSED"

    /**
     * Active/désactive le partage de connexion, de la méthode la plus simple à la plus contraignante :
     * 1. Direct (autorisation « Modifier les paramètres système ») : sans Shizuku ni root.
     * 2. Shizuku (shell/root), seulement s'il tourne déjà.
     * 3. Bluetooth uniquement : ancienne API BluetoothPan (Android 11).
     * @return true si l'opération a réussi.
     */
    suspend fun setTethering(context: Context, mode: TetherMode, enable: Boolean): Boolean {
        val type = when (mode) {
            TetherMode.WIFI -> ShizukuTethering.TYPE_WIFI
            TetherMode.BLUETOOTH -> ShizukuTethering.TYPE_BLUETOOTH
        }

        if (DirectTethering.canWriteSettings(context)) {
            if (enable) {
                val result = DirectTethering.start(context, type)
                Log.i(TAG, "Direct $mode enable=true -> $result")
                if (result == DirectTethering.TETHER_ERROR_NO_ERROR) return settled()
                if (result == DirectTethering.TETHER_ERROR_NO_CHANGE_TETHERING_PERMISSION ||
                    result == DirectTethering.TETHER_ERROR_PROVISIONING_FAILED
                ) DeviceRepository.setFlag(FLAG_DIRECT_REFUSED)
            } else if (DirectTethering.stop(context, type) && awaitState(mode, false)) {
                return true
            }
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

    /** Attend que l'état réel rejoigne l'état voulu (stopTethering ne renvoie rien). */
    private suspend fun awaitState(mode: TetherMode, active: Boolean): Boolean {
        repeat(6) {
            delay(500)
            TetherStateMonitor.refresh()
            delay(100)
            if (TetherStateMonitor.isActive(mode) == active) return true
        }
        return false
    }

    private suspend fun settled(): Boolean {
        delay(500)
        TetherStateMonitor.refresh()
        return true
    }
}
