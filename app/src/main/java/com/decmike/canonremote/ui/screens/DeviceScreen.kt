package com.decmike.canonremote.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.decmike.canonremote.data.CameraRepository
import com.decmike.canonremote.data.ConnState
import com.decmike.canonremote.data.Downloader
import com.decmike.canonremote.ui.SectionTitle
import kotlinx.coroutines.launch

@Composable
fun DeviceScreen() {
    val scope = rememberCoroutineScope()
    val state by CameraRepository.state.collectAsStateWithLifecycle()
    val status by CameraRepository.status.collectAsStateWithLifecycle()
    val info = (state as? ConnState.Connected)?.info
    val host = (state as? ConnState.Connected)?.host
    val prefs = CameraRepository.prefs

    var autoDl by remember { mutableStateOf(prefs.autoDownload) }
    var autoRaw by remember { mutableStateOf(prefs.autoDownloadRaw) }
    var lvSize by remember { mutableStateOf(prefs.liveViewSize) }
    var camOff by remember { mutableStateOf(prefs.cameraScreenOff) }
    var hist by remember { mutableStateOf(prefs.histogram) }
    var keepOn by remember { mutableStateOf(prefs.keepScreenOn) }

    LaunchedEffect(Unit) { CameraRepository.refreshStatus() }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        SectionTitle("Appareil")
        Card {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                InfoRow("Modèle", info?.productName)
                InfoRow("Fabricant", info?.manufacturer)
                InfoRow("Firmware", info?.firmware)
                InfoRow("N° de série", info?.serialNumber)
                InfoRow("Adresse MAC", info?.mac)
                InfoRow("Adresse IP", host)
            }
        }

        SectionTitle("État")
        Card {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                InfoRow("Batterie", status.batteryLevel?.let { "$it %" } ?: status.battery)
                InfoRow("Objectif", status.lens)
                InfoRow("Température", status.temperature)
                status.storages.forEach { s ->
                    Spacer(Modifier.width(4.dp))
                    Text("Carte ${s.name.uppercase()}", fontWeight = FontWeight.SemiBold)
                    if (s.maxSize > 0) {
                        LinearProgressIndicator(
                            progress = { 1f - s.freeSize.toFloat() / s.maxSize }, modifier = Modifier.fillMaxWidth(),
                        )
                        Text("${gb(s.freeSize)} libres sur ${gb(s.maxSize)} · ${s.contents} fichiers",
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { scope.launch { CameraRepository.refreshStatus() } }) {
                Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(6.dp)); Text("Actualiser")
            }
            OutlinedButton(onClick = {
                scope.launch {
                    runCatching { CameraRepository.requireClient().syncDateTime() }
                        .onSuccess { CameraRepository.message("Date et heure synchronisées") }
                        .onFailure { CameraRepository.message("Synchronisation impossible : ${it.message}") }
                }
            }) { Icon(Icons.Default.Schedule, null); Spacer(Modifier.width(6.dp)); Text("Régler l'heure") }
        }

        SectionTitle("Transfert")
        PrefSwitch("Transfert automatique des nouvelles photos", autoDl) { autoDl = it; prefs.autoDownload = it }
        PrefSwitch("Inclure les fichiers RAW", autoRaw) { autoRaw = it; prefs.autoDownloadRaw = it }
        Text("Images enregistrées dans Images/${Downloader.FOLDER} (RAW dans Téléchargements/${Downloader.FOLDER}).",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        SectionTitle("Visée directe")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Taille", Modifier.weight(1f))
            listOf("small" to "Petite (fluide)", "medium" to "Moyenne").forEach { (v, l) ->
                FilterChip(selected = lvSize == v, onClick = { lvSize = v; prefs.liveViewSize = v }, label = { Text(l) })
            }
        }
        PrefSwitch("Éteindre l'écran de l'appareil pendant la visée", camOff) { camOff = it; prefs.cameraScreenOff = it }
        PrefSwitch("Histogramme", hist) { hist = it; prefs.histogram = it }
        PrefSwitch("Garder l'écran du téléphone allumé", keepOn) { keepOn = it; prefs.keepScreenOn = it }
    }
}

private fun gb(bytes: Long) = "%.1f Go".format(bytes / 1_073_741_824.0)

@Composable
private fun InfoRow(label: String, value: String?) {
    Row {
        Text(label, Modifier.width(130.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value?.takeIf { it.isNotBlank() } ?: "—")
    }
}

@Composable
private fun PrefSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
