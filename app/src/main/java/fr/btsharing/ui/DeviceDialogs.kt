package fr.btsharing.ui

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import fr.btsharing.data.ApprovedDevice
import fr.btsharing.data.TetherMode
import fr.btsharing.setup.SetupChecks

private data class Candidate(val address: String, val name: String)

@SuppressLint("MissingPermission")
@Composable
fun AddDeviceDialog(existing: Set<String>, onDismiss: () -> Unit, onAdd: (ApprovedDevice) -> Unit) {
    val context = LocalContext.current
    val adapter = remember { context.getSystemService(BluetoothManager::class.java)?.adapter }
    var hasPermission by remember { mutableStateOf(SetupChecks.hasBluetoothPermissions(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        hasPermission = SetupChecks.hasBluetoothPermissions(context)
    }
    var selected by remember { mutableStateOf<Candidate?>(null) }
    var mode by remember { mutableStateOf(TetherMode.WIFI) }
    val nearby = remember { mutableStateListOf<Candidate>() }
    var scanning by remember { mutableStateOf(false) }
    val ready = hasPermission && adapter?.isEnabled == true

    val bonded = remember(ready) {
        if (!ready) emptyList()
        else adapter!!.bondedDevices.orEmpty()
            .map { Candidate(it.address, it.name ?: it.address) }
            .filter { it.address.uppercase() !in existing }
            .sortedBy { it.name.lowercase() }
    }

    // Recherche des appareils à proximité pendant que la boîte de dialogue est ouverte
    if (ready) DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                when (intent.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val d = IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                            ?: return
                        val candidate = Candidate(d.address, d.name ?: d.address)
                        val known = bonded.any { it.address == d.address } || nearby.any { it.address == d.address }
                        if (!known && d.address.uppercase() !in existing) nearby += candidate
                    }
                    android.bluetooth.BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> scanning = false
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(android.bluetooth.BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        scanning = runCatching { adapter!!.startDiscovery() }.getOrDefault(false)
        onDispose {
            runCatching { adapter!!.cancelDiscovery() }
            context.unregisterReceiver(receiver)
        }
    }

    val current = selected
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (current == null) "Ajouter un appareil" else current.name) },
        text = {
            when {
                !hasPermission -> Text("L'accès au Bluetooth est nécessaire pour lister les appareils.")
                adapter?.isEnabled != true -> Text("Activez le Bluetooth pour voir les appareils.")
                current != null -> Column {
                    Text("Quand cet appareil se connecte, activer :")
                    TetherMode.entries.forEach { m -> ModeOption(m, m == mode) { mode = m } }
                }
                else -> LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    item { SectionTitle("Appareils associés") }
                    if (bonded.isEmpty()) item { EmptyLine("Aucun (ou tous déjà ajoutés)") }
                    items(bonded, key = { "b" + it.address }) { CandidateRow(it) { selected = it } }
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SectionTitle("À proximité", Modifier.weight(1f))
                            if (scanning) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        }
                    }
                    if (nearby.isEmpty() && !scanning) item { EmptyLine("Aucun appareil détecté") }
                    items(nearby, key = { "n" + it.address }) { CandidateRow(it) { selected = it } }
                }
            }
        },
        confirmButton = {
            when {
                !hasPermission -> TextButton(onClick = {
                    permissionLauncher.launch(SetupChecks.requiredPermissions().toTypedArray())
                }) { Text("Autoriser") }
                current != null -> TextButton(onClick = {
                    onAdd(ApprovedDevice(current.address, current.name, mode))
                }) { Text("Ajouter") }
                else -> Unit
            }
        },
        dismissButton = {
            TextButton(onClick = { if (current != null) selected = null else onDismiss() }) {
                Text(if (current != null) "Retour" else "Annuler")
            }
        },
    )
}

@Composable
fun EditDeviceDialog(
    device: ApprovedDevice,
    onDismiss: () -> Unit,
    onModeChange: (TetherMode) -> Unit,
    onDelete: () -> Unit,
) {
    var mode by remember { mutableStateOf(device.mode) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(device.name) },
        text = {
            Column {
                Text(device.address, style = MaterialTheme.typography.bodySmall)
                TetherMode.entries.forEach { m -> ModeOption(m, m == mode) { mode = m; onModeChange(m) } }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
        dismissButton = {
            TextButton(onClick = onDelete) { Text("Supprimer", color = MaterialTheme.colorScheme.error) }
        },
    )
}

@Composable
private fun ModeOption(mode: TetherMode, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected = selected, onClick = onSelect).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(mode.label)
    }
}

@Composable
private fun CandidateRow(candidate: Candidate, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp)) {
        Text(candidate.name)
        Text(candidate.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    HorizontalDivider()
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.padding(top = 12.dp, bottom = 4.dp),
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.labelLarge,
    )
}

@Composable
private fun EmptyLine(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
