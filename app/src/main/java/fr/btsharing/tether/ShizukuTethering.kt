package fr.btsharing.tether

import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.Parcel
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Appelle directement le service système "tethering" (ITetheringConnector, Android 11+)
 * avec l'identité shell/root fournie par Shizuku, qui possède TETHER_PRIVILEGED.
 *
 * L'interface AIDL étant cachée, les transactions sont écrites à la main.
 * Ordre des méthodes : 0 tether, 1 untether, 2 setUsbTethering, 3 startTethering, 4 stopTethering…
 */
object ShizukuTethering {
    const val TYPE_WIFI = 0
    const val TYPE_BLUETOOTH = 2
    const val TETHER_ERROR_NO_ERROR = 0

    private const val CONNECTOR = "android.net.ITetheringConnector"
    private const val LISTENER = "android.net.IIntResultListener"
    private const val TX_START = IBinder.FIRST_CALL_TRANSACTION + 3
    private const val TX_STOP = IBinder.FIRST_CALL_TRANSACTION + 4

    /** @return code d'erreur du service (0 = succès) ou null si pas de réponse. */
    suspend fun setTethering(type: Int, enable: Boolean): Int? {
        val service = SystemServiceHelper.getSystemService("tethering") ?: return null
        val remote = ShizukuBinderWrapper(service)
        val callerPkg = if (Shizuku.getUid() == 0) "root" else "com.android.shell"

        return withTimeoutOrNull(8000) {
            suspendCancellableCoroutine<Int> { cont ->
                val listener = ResultListener { code -> if (cont.isActive) cont.resume(code) }
                val data = Parcel.obtain()
                try {
                    data.writeInterfaceToken(CONNECTOR)
                    if (enable) {
                        data.writeInt(1) // TetheringRequestParcel non nul
                        writeRequestParcel(data, type)
                    } else {
                        data.writeInt(type)
                    }
                    data.writeString(callerPkg)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) data.writeString(null) // attributionTag
                    data.writeStrongBinder(listener)
                    remote.transact(if (enable) TX_START else TX_STOP, data, null, IBinder.FLAG_ONEWAY)
                } catch (e: Exception) {
                    if (cont.isActive) cont.resumeWithException(e)
                } finally {
                    data.recycle()
                }
            }
        }
    }

    /**
     * Parcelable AIDL structuré : taille puis champs dans l'ordre. Les versions plus récentes
     * d'Android ajoutent des champs à la fin, qui prennent alors leur valeur par défaut.
     */
    private fun writeRequestParcel(p: Parcel, type: Int) {
        val start = p.dataPosition()
        p.writeInt(0)    // taille (réécrite plus bas)
        p.writeInt(type) // tetheringType
        p.writeInt(0)    // localIPv4Address = null
        p.writeInt(0)    // staticClientAddress = null
        p.writeInt(0)    // exemptFromEntitlementCheck = false
        p.writeInt(1)    // showProvisioningUi = true
        p.writeInt(1)    // connectivityScope = CONNECTIVITY_SCOPE_GLOBAL (Android 12+)
        val end = p.dataPosition()
        p.setDataPosition(start)
        p.writeInt(end - start)
        p.setDataPosition(end)
    }

    private class ResultListener(private val onResult: (Int) -> Unit) : Binder(LISTENER) {
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code == FIRST_CALL_TRANSACTION) {
                data.enforceInterface(LISTENER)
                onResult(data.readInt())
                return true
            }
            return super.onTransact(code, data, reply, flags)
        }
    }
}
