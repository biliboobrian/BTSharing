package fr.btsharing.tether

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import fr.btsharing.AppScope
import fr.btsharing.data.TetherMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Observe l'état réel du partage de connexion (Wi‑Fi et Bluetooth). */
object TetherStateMonitor {
    private const val ACTION_TETHER_STATE_CHANGED = "android.net.conn.TETHER_STATE_CHANGED"
    private const val EXTRA_ACTIVE_TETHER = "tetherArray"
    private const val ACTION_WIFI_AP_STATE_CHANGED = "android.net.wifi.WIFI_AP_STATE_CHANGED"
    private const val EXTRA_WIFI_AP_STATE = "wifi_state"
    private const val WIFI_AP_STATE_ENABLED = 13
    private const val ACTION_BT_TETHERING_CHANGED = "android.bluetooth.action.TETHERING_STATE_CHANGED"

    private val _active = MutableStateFlow<Set<TetherMode>>(emptySet())
    val active: StateFlow<Set<TetherMode>> = _active.asStateFlow()

    @Volatile private var tetheredIfaces: List<String> = emptyList()
    @Volatile private var wifiApOn = false
    @Volatile private var btTetheringOn = false
    private var started = false
    private lateinit var appContext: Context

    fun isActive(mode: TetherMode) = mode in _active.value

    fun start(context: Context) {
        if (started) return
        started = true
        appContext = context.applicationContext
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                when (intent.action) {
                    ACTION_TETHER_STATE_CHANGED ->
                        tetheredIfaces = intent.getStringArrayListExtra(EXTRA_ACTIVE_TETHER).orEmpty()
                    ACTION_WIFI_AP_STATE_CHANGED ->
                        wifiApOn = intent.getIntExtra(EXTRA_WIFI_AP_STATE, 0) == WIFI_AP_STATE_ENABLED
                }
                refresh()
            }
        }
        val filter = IntentFilter().apply {
            addAction(ACTION_TETHER_STATE_CHANGED)
            addAction(ACTION_WIFI_AP_STATE_CHANGED)
            addAction(ACTION_BT_TETHERING_CHANGED)
        }
        // Diffusions système protégées : aucun risque à exporter le receiver.
        ContextCompat.registerReceiver(appContext, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        refresh()
    }

    /** Relit l'état Bluetooth (pas toujours diffusé) puis recalcule. */
    fun refresh() {
        if (!started) return
        AppScope.launch {
            BluetoothPanProxy.isTetheringOn(appContext)?.let { btTetheringOn = it }
            recompute()
        }
    }

    private fun recompute() {
        val modes = mutableSetOf<TetherMode>()
        val wifiIface = tetheredIfaces.any { it.startsWith("wlan") || it.startsWith("ap") || it.startsWith("swlan") }
        if (wifiApOn || wifiIface) modes += TetherMode.WIFI
        if (btTetheringOn || tetheredIfaces.any { it.startsWith("bt") }) modes += TetherMode.BLUETOOTH
        _active.value = modes
    }
}
