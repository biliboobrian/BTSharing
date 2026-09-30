package fr.btsharing.tether

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import fr.btsharing.data.DeviceRepository
import fr.btsharing.setup.SetupChecks

object BluetoothState {
    /** Recalcule quels appareils approuvés sont connectés (au démarrage du processus, au retour dans l'appli). */
    @SuppressLint("MissingPermission")
    fun refreshConnected(context: Context) {
        if (!SetupChecks.hasBluetoothPermissions(context)) return
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return
        if (!adapter.isEnabled) {
            DeviceRepository.replaceConnected(emptySet())
            return
        }
        val connected = DeviceRepository.devices.value.filter { d ->
            runCatching {
                val device = adapter.getRemoteDevice(d.address)
                device.javaClass.getMethod("isConnected").invoke(device) as Boolean
            }.getOrDefault(false)
        }.map { it.address }.toSet()
        DeviceRepository.replaceConnected(connected)
    }

    @SuppressLint("MissingPermission")
    fun isConnected(context: Context, address: String): Boolean = runCatching {
        val adapter = context.getSystemService(BluetoothManager::class.java)!!.adapter
        val device = adapter.getRemoteDevice(address)
        device.javaClass.getMethod("isConnected").invoke(device) as Boolean
    }.getOrDefault(false)
}
