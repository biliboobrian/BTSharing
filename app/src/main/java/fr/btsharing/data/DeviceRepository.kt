package fr.btsharing.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject

enum class TetherMode(val label: String) {
    WIFI("Point d'accès Wi‑Fi"),
    BLUETOOTH("Partage Bluetooth"),
}

data class ApprovedDevice(val address: String, val name: String, val mode: TetherMode)

/** Liste des appareils approuvés (persistée) + appareils actuellement connectés (en mémoire). */
object DeviceRepository {
    private const val KEY_DEVICES = "devices"
    private const val KEY_STARTED = "started_by_app"

    private lateinit var prefs: SharedPreferences

    private val _devices = MutableStateFlow<List<ApprovedDevice>>(emptyList())
    val devices: StateFlow<List<ApprovedDevice>> = _devices.asStateFlow()

    private val _connected = MutableStateFlow<Set<String>>(emptySet())
    val connected: StateFlow<Set<String>> = _connected.asStateFlow()

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.getSharedPreferences("btsharing", Context.MODE_PRIVATE)
        _devices.value = load()
    }

    fun find(address: String): ApprovedDevice? =
        _devices.value.firstOrNull { it.address.equals(address, ignoreCase = true) }

    fun add(device: ApprovedDevice) =
        save(_devices.value.filterNot { it.address.equals(device.address, true) } + device)

    fun remove(address: String) {
        save(_devices.value.filterNot { it.address.equals(address, true) })
        setConnected(address, false)
    }

    fun setMode(address: String, mode: TetherMode) =
        save(_devices.value.map { if (it.address.equals(address, true)) it.copy(mode = mode) else it })

    fun setConnected(address: String, isConnected: Boolean) {
        val key = address.uppercase()
        _connected.update { if (isConnected) it + key else it - key }
    }

    fun replaceConnected(addresses: Set<String>) {
        _connected.value = addresses.map { it.uppercase() }.toSet()
    }

    fun connectedDevices(): List<ApprovedDevice> =
        _devices.value.filter { it.address.uppercase() in _connected.value }

    /** Modes de partage que l'application a elle-même activés (pour ne couper que ceux-là). */
    var startedByApp: Set<TetherMode>
        get() = prefs.getStringSet(KEY_STARTED, emptySet())!!
            .mapNotNull { runCatching { TetherMode.valueOf(it) }.getOrNull() }.toSet()
        set(value) = prefs.edit().putStringSet(KEY_STARTED, value.map { it.name }.toSet()).apply()

    fun flag(name: String): Boolean = prefs.getBoolean("flag_$name", false)
    fun setFlag(name: String) = prefs.edit().putBoolean("flag_$name", true).apply()

    private fun load(): List<ApprovedDevice> = runCatching {
        val array = JSONArray(prefs.getString(KEY_DEVICES, "[]"))
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            ApprovedDevice(
                address = o.getString("address"),
                name = o.optString("name", o.getString("address")),
                mode = runCatching { TetherMode.valueOf(o.getString("mode")) }.getOrDefault(TetherMode.WIFI),
            )
        }
    }.getOrDefault(emptyList())

    private fun save(list: List<ApprovedDevice>) {
        _devices.value = list
        val array = JSONArray()
        list.forEach {
            array.put(JSONObject().put("address", it.address).put("name", it.name).put("mode", it.mode.name))
        }
        prefs.edit().putString(KEY_DEVICES, array.toString()).apply()
    }
}
