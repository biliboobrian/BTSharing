package fr.btsharing.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import fr.btsharing.R
import fr.btsharing.data.TetherMode
import fr.btsharing.ui.MainActivity

object Notifications {
    const val SERVICE_ID = 1
    private const val ACTION_ID = 2
    private const val CHANNEL_SERVICE = "service"
    private const val CHANNEL_ACTION = "action"

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_SERVICE, "Surveillance en arrière-plan", NotificationManager.IMPORTANCE_MIN)
                .apply { setShowBadge(false) },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ACTION, "Partage à activer", NotificationManager.IMPORTANCE_HIGH),
        )
    }

    fun service(context: Context, text: String): Notification =
        NotificationCompat.Builder(context, CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("BTSharing actif")
            .setContentText(text)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(openApp(context))
            .build()

    fun updateService(context: Context, text: String) = notify(context, SERVICE_ID, service(context, text))

    /** Dernier recours : Android refuse l'activation automatique, on propose un raccourci en un geste. */
    fun showActionNeeded(context: Context, mode: TetherMode) {
        val pi = PendingIntent.getActivity(
            context, 1, tetherSettingsIntent(context),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, CHANNEL_ACTION)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Activer le partage de connexion")
            .setContentText("${mode.label} : appuyez pour l'activer (configurez Shizuku pour l'automatiser).")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        notify(context, ACTION_ID, n)
    }

    fun cancelActionNeeded(context: Context) =
        context.getSystemService(NotificationManager::class.java).cancel(ACTION_ID)

    fun tetherSettingsIntent(context: Context): Intent {
        val candidates = listOf(
            Intent().setComponent(ComponentName("com.android.settings", "com.android.settings.TetherSettings")),
            Intent("android.settings.TETHER_SETTINGS"),
            Intent(Settings.ACTION_WIRELESS_SETTINGS),
        )
        val intent = candidates.firstOrNull { it.resolveActivity(context.packageManager) != null } ?: candidates.last()
        return intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private fun openApp(context: Context) = PendingIntent.getActivity(
        context, 0, Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun notify(context: Context, id: Int, notification: Notification) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED && android.os.Build.VERSION.SDK_INT >= 33
        ) return
        context.getSystemService(NotificationManager::class.java).notify(id, notification)
    }
}
