package fr.btsharing.tether

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** Accès au profil Bluetooth PAN (caché) par réflexion. */
@SuppressLint("MissingPermission")
object BluetoothPanProxy {
    private const val PAN = 5 // BluetoothProfile.PAN

    @Volatile
    private var proxy: BluetoothProfile? = null

    private suspend fun get(context: Context): BluetoothProfile? {
        proxy?.let { return it }
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return null
        return withTimeoutOrNull(3000) {
            suspendCancellableCoroutine { cont ->
                val listener = object : BluetoothProfile.ServiceListener {
                    override fun onServiceConnected(profile: Int, p: BluetoothProfile) {
                        proxy = p
                        if (cont.isActive) cont.resume(p)
                    }

                    override fun onServiceDisconnected(profile: Int) {
                        proxy = null
                    }
                }
                val ok = runCatching { adapter.getProfileProxy(context.applicationContext, listener, PAN) }
                    .getOrDefault(false)
                if (!ok && cont.isActive) cont.resume(null)
            }
        }
    }

    suspend fun isTetheringOn(context: Context): Boolean? = runCatching {
        val pan = get(context) ?: return null
        pan.javaClass.getMethod("isTetheringOn").invoke(pan) as Boolean
    }.getOrNull()

    /** Méthode historique : fonctionne sans privilège sur Android 11 (refusée ensuite). */
    suspend fun setTethering(context: Context, enable: Boolean): Boolean = runCatching {
        val pan = get(context) ?: return false
        pan.javaClass.getMethod("setBluetoothTethering", Boolean::class.javaPrimitiveType).invoke(pan, enable)
        true
    }.getOrDefault(false)
}
