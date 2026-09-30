package fr.btsharing.tether

import android.content.Context
import fr.btsharing.data.DeviceRepository
import fr.btsharing.data.TetherMode
import fr.btsharing.service.Notifications
import fr.btsharing.service.TetherService
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Logique centrale : appareil approuvé connecté → partage activé ; dernier déconnecté → partage coupé. */
object AutoTether {
    private val mutex = Mutex()

    suspend fun onAclChanged(context: Context, address: String, connected: Boolean) = mutex.withLock {
        val device = DeviceRepository.find(address) ?: return@withLock
        DeviceRepository.setConnected(address, connected)
        TetherService.start(context)
        if (connected) enable(context, device.mode) else disableIfUnused(context, device.mode)
    }

    suspend fun onBluetoothOff(context: Context) = mutex.withLock {
        val modes = DeviceRepository.connectedDevices().map { it.mode }.toSet()
        DeviceRepository.replaceConnected(emptySet())
        modes.forEach { disableIfUnused(context, it) }
    }

    private suspend fun enable(context: Context, mode: TetherMode) {
        // Déjà actif (peut-être activé à la main) : on n'y touche pas et on ne le coupera pas.
        if (TetherStateMonitor.isActive(mode)) return
        if (TetherController.setTethering(context, mode, true)) {
            DeviceRepository.startedByApp = DeviceRepository.startedByApp + mode
            Notifications.cancelActionNeeded(context)
        } else {
            Notifications.showActionNeeded(context, mode)
        }
    }

    private suspend fun disableIfUnused(context: Context, mode: TetherMode) {
        val stillNeeded = DeviceRepository.connectedDevices().any { it.mode == mode }
        if (stillNeeded || mode !in DeviceRepository.startedByApp) return
        TetherController.setTethering(context, mode, false)
        DeviceRepository.startedByApp = DeviceRepository.startedByApp - mode
    }
}
