package fr.btsharing.tether

import android.content.Context
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.lang.reflect.Proxy
import java.util.concurrent.Executor
import kotlin.coroutines.resume

/**
 * Active le partage de connexion SANS Shizuku ni root, avec l'identité de BTSharing.
 *
 * Le service système "tethering" (Android 11+) accepte les appels d'une application qui
 * possède l'autorisation « Modifier les paramètres système » (WRITE_SETTINGS), que
 * l'utilisateur accorde une fois depuis les réglages, et qui survit aux redémarrages.
 * Seule exception : si l'opérateur impose une vérification d'abonnement (« entitlement »),
 * Android exige TETHER_PRIVILEGED → il faut alors Shizuku/root (code d'erreur 14).
 *
 * L'API TetheringManager est @SystemApi : on l'appelle par réflexion (HiddenApiBypass est
 * activé dans App). Le nom du paquet appelant est fourni par TetheringManager lui-même.
 */
object DirectTethering {
    private const val TAG = "DirectTethering"
    const val TETHER_ERROR_NO_ERROR = 0
    const val TETHER_ERROR_PROVISIONING_FAILED = 11
    const val TETHER_ERROR_NO_CHANGE_TETHERING_PERMISSION = 14

    private const val TM = "android.net.TetheringManager"

    fun canWriteSettings(context: Context) = Settings.System.canWrite(context)

    private fun manager(context: Context): Any? =
        runCatching { context.applicationContext.getSystemService("tethering") }.getOrNull()

    /** @return 0 si succès, un code TETHER_ERROR_* sinon, null si l'API est introuvable / sans réponse. */
    suspend fun start(context: Context, type: Int): Int? {
        val tm = manager(context) ?: return null
        return try {
            val builderClass = Class.forName("$TM\$TetheringRequest\$Builder")
            val requestClass = Class.forName("$TM\$TetheringRequest")
            val callbackClass = Class.forName("$TM\$StartTetheringCallback")
            val builder = builderClass.getConstructor(Int::class.javaPrimitiveType).newInstance(type)
            runCatching {
                // Pas d'écran de vérification opérateur : on est en arrière-plan.
                builderClass.getMethod("setShouldShowEntitlementUi", Boolean::class.javaPrimitiveType)
                    .invoke(builder, false)
            }
            val request = builderClass.getMethod("build").invoke(builder)
            val start = tm.javaClass.getMethod("startTethering", requestClass, Executor::class.java, callbackClass)

            withTimeoutOrNull(8000) {
                suspendCancellableCoroutine { cont ->
                    val callback = Proxy.newProxyInstance(callbackClass.classLoader, arrayOf(callbackClass)) { proxy, m, args ->
                        when (m.name) {
                            "onTetheringStarted" -> if (cont.isActive) cont.resume(TETHER_ERROR_NO_ERROR)
                            "onTetheringFailed" -> if (cont.isActive) cont.resume(args?.getOrNull(0) as? Int ?: -1)
                            "hashCode" -> return@newProxyInstance System.identityHashCode(proxy)
                            "equals" -> return@newProxyInstance proxy === args?.getOrNull(0)
                            "toString" -> return@newProxyInstance "BTSharingTetherCallback"
                        }
                        null
                    }
                    start.invoke(tm, request, Executor { it.run() }, callback)
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "startTethering($type) impossible", e)
            null
        }
    }

    /** stopTethering ne renvoie pas de résultat : l'appelant vérifie l'état réel ensuite. */
    fun stop(context: Context, type: Int): Boolean {
        val tm = manager(context) ?: return false
        return runCatching {
            tm.javaClass.getMethod("stopTethering", Int::class.javaPrimitiveType).invoke(tm, type)
            true
        }.onFailure { Log.w(TAG, "stopTethering($type) impossible", it) }.getOrDefault(false)
    }
}
