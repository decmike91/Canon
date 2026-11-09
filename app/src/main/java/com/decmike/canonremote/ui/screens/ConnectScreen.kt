package com.decmike.canonremote.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.decmike.canonremote.ccapi.NetworkHelper
import com.decmike.canonremote.data.CameraRepository
import com.decmike.canonremote.data.ConnState
import com.decmike.canonremote.ui.SectionTitle
import kotlinx.coroutines.launch

@Composable
fun ConnectScreen(state: ConnState, onBluetooth: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = CameraRepository.prefs
    var host by remember { mutableStateOf(prefs.lastHost) }
    var port by remember { mutableStateOf(prefs.port.toString()) }
    var scanning by remember { mutableStateOf(false) }
    var scanProgress by remember { mutableIntStateOf(0) }
    var found by remember { mutableStateOf<List<String>?>(null) }
    val connecting = state is ConnState.Connecting

    fun connect(h: String) {
        scope.launch { CameraRepository.connect(h, port.toIntOrNull() ?: 8080) }
    }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.CameraAlt, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Canon Remote", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Contrôle Wi-Fi via la Camera Control API", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = host, onValueChange = { host = it },
            label = { Text("Adresse IP de l'appareil") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = port, onValueChange = { port = it.filter(Char::isDigit) },
            label = { Text("Port") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { connect(host) }, enabled = !connecting && host.isNotBlank(), modifier = Modifier.weight(1f)) {
                if (connecting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Icon(Icons.Default.Wifi, null)
                Spacer(Modifier.width(8.dp))
                Text(if (connecting) "Connexion…" else "Se connecter")
            }
            OutlinedButton(
                onClick = {
                    scanning = true; found = null; scanProgress = 0
                    scope.launch {
                        NetworkHelper.bindToWifi(context)
                        found = NetworkHelper.scan(context, port.toIntOrNull() ?: 8080) { scanProgress = it }
                        scanning = false
                    }
                },
                enabled = !scanning && !connecting,
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Default.Search, null); Spacer(Modifier.width(8.dp)); Text("Rechercher")
            }
        }

        if (scanning) {
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(progress = { scanProgress / 254f }, modifier = Modifier.fillMaxWidth())
            Text("Recherche sur le réseau Wi-Fi… $scanProgress/254", style = MaterialTheme.typography.bodySmall)
        }
        found?.let { list ->
            Spacer(Modifier.height(12.dp))
            if (list.isEmpty()) {
                Text(
                    if (NetworkHelper.phoneWifiIp(context) == null) "Le téléphone n'est connecté à aucun réseau Wi-Fi."
                    else "Aucun appareil trouvé. Vérifiez que la CCAPI est active sur l'appareil.",
                    color = MaterialTheme.colorScheme.primary,
                )
            } else list.forEach { ip ->
                Card(onClick = { host = ip; connect(ip) }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CameraAlt, null); Spacer(Modifier.width(12.dp))
                        Text("Appareil Canon · $ip", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        if (state is ConnState.Failed) {
            Spacer(Modifier.height(16.dp))
            Card { Text(state.message, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.primary) }
        }

        Spacer(Modifier.height(16.dp))
        Card(onClick = onBluetooth, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Bluetooth, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Télécommande Bluetooth", fontWeight = FontWeight.SemiBold)
                    Text("Déclenchement, pose B, vidéo et intervallomètre sans Wi-Fi",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { HelpNav.open(HelpTopic.HOME) }, modifier = Modifier.weight(1f)) {
                Icon(Icons.AutoMirrored.Filled.HelpOutline, null); Spacer(Modifier.width(6.dp)); Text("Aide")
            }
            OutlinedButton(onClick = { HelpNav.open(HelpTopic.EXPOSURE) }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.School, null); Spacer(Modifier.width(6.dp)); Text("Mémo photo")
            }
        }

        SectionTitle("Première connexion Wi-Fi")
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("1. Activez la Camera Control API (CCAPI) sur votre boîtier (outil d'activation Canon, une seule fois).")
                Text("2. Sur l'appareil : Réseau › Wi-Fi › Connexion à un smartphone/CCAPI. Choisissez de rejoindre votre box Wi-Fi ou utilisez le point d'accès de l'appareil.")
                Text("3. Connectez le téléphone au même réseau Wi-Fi (ou au point d'accès de l'appareil).")
                Text("4. L'adresse IP est affichée sur l'écran de l'appareil. Touchez « Rechercher » pour la trouver automatiquement.")
            }
        }
    }
}
