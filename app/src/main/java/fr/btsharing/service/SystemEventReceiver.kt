package fr.btsharing.service

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.IntentCompat
import fr.btsharing.AppScope
import fr.btsharing.tether.AutoTether
import kotlinx.coroutines.launch

/**
 * Déclaré dans le manifeste : Android réveille l'application pour ces événements
 * même si son processus a été tué.
 */
class SystemEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.intent.action.QUICKBOOT_POWERON" -> TetherService.start(app)

            BluetoothDevice.ACTION_ACL_CONNECTED,
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                val device = IntentCompat.getParcelableExtra(
                    intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java,
                ) ?: return
                val connected = intent.action == BluetoothDevice.ACTION_ACL_CONNECTED
                val pending = goAsync()
                AppScope.launch {
                    try {
                        AutoTether.onAclChanged(app, device.address, connected)
                    } finally {
                        pending.finish()
                    }
                }
            }

            BluetoothAdapter.ACTION_STATE_CHANGED -> {
                if (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1) == BluetoothAdapter.STATE_OFF) {
                    val pending = goAsync()
                    AppScope.launch {
                        try {
                            AutoTether.onBluetoothOff(app)
                        } finally {
                            pending.finish()
                        }
                    }
                }
            }
        }
    }
}
