package fr.btsharing

import android.app.Application
import fr.btsharing.data.DeviceRepository
import fr.btsharing.service.Notifications
import fr.btsharing.service.TetherService
import fr.btsharing.service.WatchdogWorker
import fr.btsharing.tether.BluetoothState
import fr.btsharing.tether.TetherStateMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.lsposed.hiddenapibypass.HiddenApiBypass

/** Portée de coroutines vivant aussi longtemps que le processus. */
object AppScope : CoroutineScope by CoroutineScope(SupervisorJob() + Dispatchers.Default)

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        HiddenApiBypass.addHiddenApiExemptions("")
        DeviceRepository.init(this)
        Notifications.createChannels(this)
        TetherStateMonitor.start(this)
        BluetoothState.refreshConnected(this)
        WatchdogWorker.schedule(this)
        TetherService.start(this)
    }
}
