package com.decmike.canonremote.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.decmike.canonremote.ccapi.CameraSetting
import com.decmike.canonremote.data.CameraRepository
import com.decmike.canonremote.ui.Labels
import com.decmike.canonremote.ui.SectionTitle
import com.decmike.canonremote.ui.SettingDialog
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen() {
    val scope = rememberCoroutineScope()
    val settings by CameraRepository.settings.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<CameraSetting?>(null) }
    var loading by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf("") }

    val primary = Labels.primary.mapNotNull { settings[it] }
    val others = settings.values.filter { it.key !in Labels.primary }.sortedBy { Labels.name(it.key) }
    fun matches(s: CameraSetting) = filter.isBlank() ||
        Labels.name(s.key).contains(filter, true) || s.key.contains(filter, true)

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = filter, onValueChange = { filter = it }, singleLine = true,
                placeholder = { Text("Rechercher un réglage") }, modifier = Modifier.weight(1f),
            )
            IconButton(onClick = {
                loading = true
                scope.launch { CameraRepository.refreshSettings(); loading = false }
            }) { Icon(Icons.Default.Refresh, "Actualiser") }
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())

        if (settings.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Aucun réglage reçu. Touchez ↻ pour actualiser.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            val p = primary.filter(::matches)
            if (p.isNotEmpty()) {
                item { SectionTitle("Exposition et mise au point") }
                items(p, key = { it.key }) { s -> SettingRow(s) { editing = s } }
            }
            val o = others.filter(::matches)
            if (o.isNotEmpty()) {
                item { SectionTitle("Tous les réglages") }
                items(o, key = { it.key }) { s -> SettingRow(s) { editing = s } }
            }
        }
    }

    editing?.let { s ->
        SettingDialog(
            s, onDismiss = { editing = null },
            onPick = { v -> editing = null; scope.launch { CameraRepository.setSetting(s.key, v) } },
            onPickObject = { v -> editing = null; scope.launch { CameraRepository.setSettingObject(s.key, v) } },
        )
    }
}

@Composable
private fun SettingRow(s: CameraSetting, onClick: () -> Unit) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(enabled = s.editable, onClick = onClick)
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text(Labels.name(s.key), fontWeight = FontWeight.Medium)
                val shown = when {
                    s.subValues != null -> s.subValues.entries.joinToString(" · ") { "${it.key} : ${Labels.value(it.key, it.value)}" }
                    s.value != null -> Labels.value(s.key, s.value)
                    else -> "—"
                }
                Text(shown, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
            }
            if (s.editable) Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
    }
}
