package fr.btsharing.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import fr.btsharing.data.DeviceRepository
import fr.btsharing.tether.TetherStateMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Service de premier plan permanent : garde le processus vivant (écran éteint, économie d'énergie)
 * pour réagir instantanément aux connexions Bluetooth.
 */
class TetherService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        goForeground()
        scope.launch {
            combine(DeviceRepository.devices, DeviceRepository.connected, TetherStateMonitor.active) { d, c, a ->
                summary(d.size, d.count { it.address.uppercase() in c }, a.isNotEmpty())
            }.collect { Notifications.updateService(this@TetherService, it) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        goForeground()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun goForeground() {
        val d = DeviceRepository.devices.value
        val c = DeviceRepository.connected.value
        ServiceCompat.startForeground(
            this,
            Notifications.SERVICE_ID,
            Notifications.service(this, summary(d.size, d.count { it.address.uppercase() in c }, TetherStateMonitor.active.value.isNotEmpty())),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
        )
    }

    private fun summary(total: Int, connected: Int, tethering: Boolean): String {
        val devices = if (connected > 0) "$connected/$total appareil(s) connecté(s)" else "$total appareil(s) surveillé(s)"
        return if (tethering) "$devices · partage actif" else devices
    }

    companion object {
        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, TetherService::class.java))
            } catch (e: Exception) {
                // Démarrage refusé en arrière-plan (optimisation batterie non désactivée) : le watchdog réessaiera.
                Log.w("TetherService", "Démarrage impossible", e)
            }
        }
    }
}
