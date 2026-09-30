package fr.btsharing.setup

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import fr.btsharing.data.DeviceRepository
import fr.btsharing.tether.ShizukuHelper

enum class StepId { PERMISSIONS, SHIZUKU, BATTERY, BACKGROUND, UNUSED_APP, OEM }

data class SetupStep(val id: StepId, val title: String, val text: String, val button: String)

/** Tous les réglages nécessaires pour que l'application reste active en permanence. */
object SetupChecks {

    fun requiredPermissions(): List<String> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_CONNECT)
            add(Manifest.permission.BLUETOOTH_SCAN)
        } else {
            add(Manifest.permission.ACCESS_FINE_LOCATION) // nécessaire à la recherche Bluetooth sur Android 11
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
    }

    fun missingPermissions(context: Context) = requiredPermissions().filter {
        ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
    }

    fun hasBluetoothPermissions(context: Context) =
        missingPermissions(context).none { it != Manifest.permission.POST_NOTIFICATIONS }

    fun pending(context: Context): List<SetupStep> = buildList {
        if (missingPermissions(context).isNotEmpty()) add(
            SetupStep(
                StepId.PERMISSIONS, "Autorisations",
                "Bluetooth (pour détecter vos appareils) et notifications (pour rester actif en arrière-plan).",
                "Autoriser",
            ),
        )

        when (ShizukuHelper.status(context)) {
            ShizukuHelper.Status.NOT_INSTALLED -> add(
                SetupStep(
                    StepId.SHIZUKU, "Installer Shizuku",
                    "Android réserve l'activation du partage de connexion aux applications système. " +
                        "Shizuku (gratuit, sans root) donne ce droit à BTSharing.",
                    "Installer",
                ),
            )
            ShizukuHelper.Status.NOT_RUNNING -> add(
                SetupStep(
                    StepId.SHIZUKU, "Démarrer Shizuku",
                    "Ouvrez Shizuku et démarrez-le via « Débogage sans fil ». À refaire après chaque redémarrage du téléphone (sauf avec root).",
                    "Ouvrir Shizuku",
                ),
            )
            ShizukuHelper.Status.NO_PERMISSION -> add(
                SetupStep(
                    StepId.SHIZUKU, "Autoriser BTSharing dans Shizuku",
                    "Nécessaire pour activer le partage de connexion automatiquement.",
                    "Autoriser",
                ),
            )
            ShizukuHelper.Status.READY -> Unit
        }

        val pm = context.getSystemService(PowerManager::class.java)
        if (!pm.isIgnoringBatteryOptimizations(context.packageName)) add(
            SetupStep(
                StepId.BATTERY, "Désactiver l'optimisation de la batterie",
                "Permet de rester actif écran éteint et en mode économie d'énergie.",
                "Désactiver",
            ),
        )

        val am = context.getSystemService(ActivityManager::class.java)
        if (am.isBackgroundRestricted) add(
            SetupStep(
                StepId.BACKGROUND, "Autoriser l'arrière-plan",
                "Dans « Batterie », choisissez « Non restreinte » (ou « Sans restriction »).",
                "Ouvrir",
            ),
        )

        if (!context.packageManager.isAutoRevokeWhitelisted) add(
            SetupStep(
                StepId.UNUSED_APP, "Ne jamais suspendre l'application",
                "Désactivez « Suspendre l'activité de l'appli si elle n'est pas utilisée ».",
                "Ouvrir",
            ),
        )

        if (oemIntent(context) != null && !DeviceRepository.flag(StepId.OEM.name)) add(
            SetupStep(
                StepId.OEM, "Réglage constructeur (${Build.MANUFACTURER})",
                if (Build.MANUFACTURER.equals("samsung", true))
                    "Batterie → Limites d'utilisation en arrière-plan → ajoutez BTSharing aux « Applis jamais en veille »."
                else
                    "Autorisez le démarrage automatique / l'exécution en arrière-plan de BTSharing.",
                "Ouvrir",
            ),
        )
    }

    /** Ouvre l'écran système correspondant (sauf PERMISSIONS, géré par l'activité). */
    @SuppressLint("BatteryLife")
    fun perform(context: Context, step: StepId) {
        val pkgUri = Uri.fromParts("package", context.packageName, null)
        val intent = when (step) {
            StepId.PERMISSIONS -> return
            StepId.SHIZUKU -> return ShizukuHelper.resolve(context)
            StepId.BATTERY -> Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkgUri)
            StepId.BACKGROUND -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri)
            StepId.UNUSED_APP -> Intent(Intent.ACTION_AUTO_REVOKE_PERMISSIONS, pkgUri)
            StepId.OEM -> {
                DeviceRepository.setFlag(StepId.OEM.name) // impossible à vérifier : proposé une seule fois
                oemIntent(context)
            }
        } ?: return
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    fun openAppDetails(context: Context) = context.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )

    private val OEM_COMPONENTS = listOf(
        "com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity",
        "com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
        "com.huawei.systemmanager" to "com.huawei.systemmanager.optimize.process.ProtectActivity",
        "com.hihonor.systemmanager" to "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
        "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
        "com.coloros.safecenter" to "com.coloros.safecenter.startupapp.StartupAppListActivity",
        "com.oppo.safe" to "com.oppo.safe.permission.startup.StartupAppListActivity",
        "com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
        "com.iqoo.secure" to "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity",
        "com.oneplus.security" to "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity",
        "com.samsung.android.lool" to "com.samsung.android.sm.battery.ui.BatteryActivity",
        "com.samsung.android.sm" to "com.samsung.android.sm.battery.ui.BatteryActivity",
        "com.asus.mobilemanager" to "com.asus.mobilemanager.autostart.AutoStartActivity",
        "com.letv.android.letvsafe" to "com.letv.android.letvsafe.AutobootManageActivity",
    )

    private fun oemIntent(context: Context): Intent? = OEM_COMPONENTS
        .map { (pkg, cls) -> Intent().setComponent(ComponentName(pkg, cls)) }
        .firstOrNull { it.resolveActivity(context.packageManager) != null }
}
