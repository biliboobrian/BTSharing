package fr.btsharing.ui

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material.icons.filled.WifiTetheringOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.btsharing.AppScope
import fr.btsharing.data.ApprovedDevice
import fr.btsharing.data.DeviceRepository
import fr.btsharing.setup.SetupChecks
import fr.btsharing.setup.SetupStep
import fr.btsharing.setup.StepId
import fr.btsharing.tether.AutoTether
import fr.btsharing.tether.BluetoothState
import fr.btsharing.tether.TetherStateMonitor
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

private val ConnectedGreen = Color(0xFF2E7D32)
private val ActiveGreen = Color(0xFFB9F6CA)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    val devices by DeviceRepository.devices.collectAsStateWithLifecycle()
    val connected by DeviceRepository.connected.collectAsStateWithLifecycle()
    val activeModes by TetherStateMonitor.active.collectAsStateWithLifecycle()

    // Réévalue les réglages à chaque retour dans l'application
    var refreshTick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        refreshTick++
        BluetoothState.refreshConnected(context)
        TetherStateMonitor.refresh()
        onPauseOrDispose { }
    }
    DisposableEffect(Unit) {
        val l = Shizuku.OnRequestPermissionResultListener { _, _ -> refreshTick++ }
        val b = Shizuku.OnBinderReceivedListener { refreshTick++ }
        Shizuku.addRequestPermissionResultListener(l)
        Shizuku.addBinderReceivedListener(b)
        onDispose {
            Shizuku.removeRequestPermissionResultListener(l)
            Shizuku.removeBinderReceivedListener(b)
        }
    }
    val pending = remember(refreshTick) { SetupChecks.pending(context) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        refreshTick++
        val activity = context as Activity
        val missing = SetupChecks.missingPermissions(context)
        // Refus définitif : Android n'affiche plus la demande, on ouvre les réglages de l'appli.
        if (missing.isNotEmpty() && missing.none { ActivityCompat.shouldShowRequestPermissionRationale(activity, it) }) {
            SetupChecks.openAppDetails(context)
        }
    }
    val runStep: (SetupStep) -> Unit = { step ->
        if (step.id == StepId.PERMISSIONS) {
            permissionLauncher.launch(SetupChecks.missingPermissions(context).toTypedArray())
        } else {
            SetupChecks.perform(context, step.id)
            refreshTick++
        }
    }

    // Proposition automatique des réglages manquants, un par un
    var postponed by remember { mutableStateOf(setOf<String>()) }
    val nextStep = pending.firstOrNull { it.id.name !in postponed }
    nextStep?.let { step ->
        AlertDialog(
            onDismissRequest = { postponed = postponed + step.id.name },
            title = { Text(step.title) },
            text = { Text(step.text) },
            confirmButton = {
                TextButton(onClick = { postponed = postponed + step.id.name; runStep(step) }) { Text(step.button) }
            },
            dismissButton = {
                TextButton(onClick = { postponed = postponed + step.id.name }) { Text("Plus tard") }
            },
        )
    }

    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ApprovedDevice?>(null) }

    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text("BTSharing") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Ajouter un appareil")
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 88.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (pending.isNotEmpty()) item { SetupBanner(pending, runStep) }
            if (devices.isEmpty()) item {
                Text(
                    "Aucun appareil.\nAppuyez sur + pour en ajouter un.",
                    modifier = Modifier.fillMaxWidth().padding(top = 64.dp),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(devices, key = { it.address }) { device ->
                DeviceRow(
                    device = device,
                    isConnected = device.address.uppercase() in connected,
                    tetherActive = device.mode in activeModes,
                    onClick = { editing = device },
                )
            }
        }
    }

    if (showAdd) {
        AddDeviceDialog(
            existing = devices.map { it.address.uppercase() }.toSet(),
            onDismiss = { showAdd = false },
            onAdd = { device ->
                showAdd = false
                DeviceRepository.add(device)
                // Déjà connecté ? On applique tout de suite.
                if (BluetoothState.isConnected(context, device.address)) {
                    AppScope.launch { AutoTether.onAclChanged(context.applicationContext, device.address, true) }
                }
            },
        )
    }
    editing?.let { device ->
        EditDeviceDialog(
            device = device,
            onDismiss = { editing = null },
            onModeChange = { DeviceRepository.setMode(device.address, it) },
            onDelete = { DeviceRepository.remove(device.address); editing = null },
        )
    }
}

@Composable
private fun SetupBanner(steps: List<SetupStep>, onRun: (SetupStep) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Réglages recommandés (${steps.size})",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            steps.forEach { step ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        step.title,
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    FilledTonalButton(onClick = { onRun(step) }) { Text(step.button) }
                }
            }
        }
    }
}

@Composable
private fun DeviceRow(device: ApprovedDevice, isConnected: Boolean, tetherActive: Boolean, onClick: () -> Unit) {
    val container = if (isConnected) ConnectedGreen else MaterialTheme.colorScheme.surfaceVariant
    val content = if (isConnected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (isConnected) Icons.Filled.BluetoothConnected else Icons.Filled.Bluetooth,
                contentDescription = null,
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(device.name, fontWeight = FontWeight.SemiBold)
                Text(
                    if (isConnected) "Connecté · ${device.mode.label}" else device.mode.label,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            // Petit indicateur : le partage de connexion est-il appliqué ?
            Icon(
                if (tetherActive) Icons.Filled.WifiTethering else Icons.Filled.WifiTetheringOff,
                contentDescription = if (tetherActive) "Partage actif" else "Partage inactif",
                tint = when {
                    tetherActive && isConnected -> ActiveGreen
                    tetherActive -> ConnectedGreen
                    else -> content.copy(alpha = 0.4f)
                },
                modifier = Modifier.size(28.dp),
            )
        }
    }
}
