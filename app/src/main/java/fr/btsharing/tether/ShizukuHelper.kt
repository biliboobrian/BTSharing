package fr.btsharing.tether

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku
import kotlin.coroutines.resume

object ShizukuHelper {
    const val PACKAGE = "moe.shizuku.privileged.api"
    const val REQUEST_CODE = 4242

    enum class Status { NOT_INSTALLED, NOT_RUNNING, NO_PERMISSION, READY }

    fun isInstalled(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(PACKAGE, 0); true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    fun status(context: Context): Status {
        if (!Shizuku.pingBinder() || Shizuku.isPreV11()) {
            return if (isInstalled(context)) Status.NOT_RUNNING else Status.NOT_INSTALLED
        }
        return if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) Status.READY
        else Status.NO_PERMISSION
    }

    /** Attend que le binder Shizuku soit reçu (il arrive peu après le démarrage du processus). */
    suspend fun awaitReady(context: Context): Boolean {
        if (!Shizuku.pingBinder()) {
            val timeout = if (isInstalled(context)) 4000L else 800L // 800 ms : cas de Sui (root)
            var listener: Shizuku.OnBinderReceivedListener? = null
            try {
                withTimeoutOrNull(timeout) {
                    suspendCancellableCoroutine { cont ->
                        val l = Shizuku.OnBinderReceivedListener { if (cont.isActive) cont.resume(Unit) }
                        listener = l
                        Shizuku.addBinderReceivedListenerSticky(l)
                    }
                }
            } finally {
                listener?.let { Shizuku.removeBinderReceivedListener(it) }
            }
        }
        return runCatching { status(context) == Status.READY }.getOrDefault(false)
    }

    /** Ouvre l'action adaptée à l'état de Shizuku (installer, démarrer, autoriser). */
    fun resolve(context: Context) {
        when (status(context)) {
            Status.NOT_INSTALLED -> {
                val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$PACKAGE"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(market) }.onFailure { context.startActivity(web) }
            }
            Status.NOT_RUNNING -> context.packageManager.getLaunchIntentForPackage(PACKAGE)
                ?.let { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            Status.NO_PERMISSION -> Shizuku.requestPermission(REQUEST_CODE)
            Status.READY -> Unit
        }
    }
}
