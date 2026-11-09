package com.decmike.canonremote.ui.screens

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.decmike.canonremote.ble.BleRemote
import com.decmike.canonremote.ble.BleState
import com.decmike.canonremote.ccapi.Exposure
import com.decmike.canonremote.data.CameraRepository
import com.decmike.canonremote.jobs.JobRunner
import com.decmike.canonremote.jobs.ShootJob
import com.decmike.canonremote.jobs.ShootingService
import com.decmike.canonremote.ui.SectionTitle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private fun blePermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= 31) arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)

@Composable
fun BluetoothScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by BleRemote.state.collectAsStateWithLifecycle()
    val devices by BleRemote.devices.collectAsStateWithLifecycle()
    val scanning by BleRemote.scanning.collectAsStateWithLifecycle()

    fun granted() = blePermissions().all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
    var hasPerm by remember { mutableStateOf(granted()) }
    var enabled by remember { mutableStateOf(granted() && BleRemote.isEnabled) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        hasPerm = granted(); enabled = hasPerm && BleRemote.isEnabled
    }
    val enableLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        enabled = BleRemote.isEnabled
    }
    DisposableEffect(Unit) { onDispose { BleRemote.stopScan() } }

    fun act(label: String, block: suspend () -> Unit) = scope.launch {
        try { block() } catch (e: Exception) { CameraRepository.message("$label : ${e.message}") }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") }
            Text("Télécommande Bluetooth", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (state is BleState.Connected) {
                TextButton(onClick = { BleRemote.disconnect() }) { Text("Déconnecter") }
            }
        }

        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            when {
                !hasPerm -> Card {
                    Column(Modifier.padding(16.dp)) {
                        Text("L'application a besoin de l'autorisation Bluetooth pour trouver l'appareil photo.")
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { permLauncher.launch(blePermissions()) }) { Text("Autoriser") }
                    }
                }
                !enabled -> Card {
                    Column(Modifier.padding(16.dp)) {
                        Text("Le Bluetooth du téléphone est désactivé.")
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { enableLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) }) { Text("Activer le Bluetooth") }
                    }
                }
                state is BleState.Connected -> RemotePanel(state as BleState.Connected, ::act)
                else -> PairingPanel(state, devices, scanning, scope)
            }
        }
    }
}

@Composable
private fun PairingPanel(
    state: BleState,
    devices: List<com.decmike.canonremote.ble.BleDevice>,
    scanning: Boolean,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    val connecting = state is BleState.Connecting
    var showAll by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { if (!connecting) BleRemote.startScan() }

    val last = BleRemote.lastAddress
    if (last != null) {
        SectionTitle("Dernier appareil")
        Card(
            onClick = { scope.launch { BleRemote.connect(last, BleRemote.lastName ?: last) } },
            enabled = !connecting, modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.BluetoothConnected, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(BleRemote.lastName ?: last, fontWeight = FontWeight.SemiBold)
                    Text("Toucher pour se reconnecter", style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = { BleRemote.forget() }) { Text("Oublier") }
            }
        }
    }

    when (state) {
        is BleState.Connecting -> Row(Modifier.padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(12.dp))
            Text(state.step)
        }
        is BleState.Failed -> Card(Modifier.fillMaxWidth().padding(top = 12.dp)) {
            Text(state.message, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.primary)
        }
        else -> {}
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        SectionTitle("Appareils à proximité", Modifier.weight(1f))
        if (scanning) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
        else TextButton(onClick = { BleRemote.startScan() }) { Text("Rechercher") }
    }
    val list = if (showAll) devices else devices.filter { it.looksLikeCanon }
    if (list.isEmpty()) {
        Text(
            if (scanning) "Recherche en cours…" else "Aucun appareil Canon trouvé.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    list.forEach { d ->
        Card(
            onClick = { scope.launch { BleRemote.connect(d.address, d.name, forcePairing = !d.bonded) } },
            enabled = !connecting,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Bluetooth, null)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(d.name, fontWeight = FontWeight.SemiBold)
                    Text(
                        buildString {
                            append(d.address)
                            d.rssi?.let { append(" · $it dBm") }
                            if (d.bonded) append(" · associé")
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Afficher tous les appareils Bluetooth", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = showAll, onCheckedChange = { showAll = it })
    }

    SectionTitle("Premier appairage")
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("1. Sur l'appareil : Menu › Réseau (ou Communication sans fil) › Réglages Bluetooth › Bluetooth : Activé.")
            Text("2. Choisissez « Télécommande » puis « Appairage » : l'appareil attend une télécommande BR-E1.")
            Text("3. Touchez votre appareil dans la liste ci-dessus. Acceptez la demande d'association si Android la propose.")
            Text("4. Pour déclencher, sélectionnez le mode d'acquisition « Télécommande / retardateur » si votre boîtier le demande.")
            Text("Aucun Wi-Fi ni activation CCAPI n'est nécessaire.", color = MaterialTheme.colorScheme.secondary)
        }
    }
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun RemotePanel(state: BleState.Connected, act: (String, suspend () -> Unit) -> Any) {
    val context = LocalContext.current
    val progress by CameraRepository.jobProgress.collectAsStateWithLifecycle()
    var bulbStart by remember { mutableLongStateOf(0L) }
    var bulbElapsed by remember { mutableIntStateOf(0) }
    var countdown by remember { mutableStateOf<Int?>(null) }
    var recording by remember { mutableStateOf(false) }

    var count by rememberSaveable { mutableStateOf("100") }
    var bulb by rememberSaveable { mutableStateOf(false) }
    var exposure by rememberSaveable { mutableStateOf("1:00") }
    var interval by rememberSaveable { mutableStateOf("5") }
    var startDelay by rememberSaveable { mutableStateOf("0") }

    LaunchedEffect(bulbStart) {
        while (bulbStart > 0) {
            bulbElapsed = ((System.currentTimeMillis() - bulbStart) / 1000).toInt()
            delay(250)
        }
    }

    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.BluetoothConnected, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text("Connecté à ${state.name}", fontWeight = FontWeight.SemiBold)
    }

    // ------------------------------------------------ déclencheur
    Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(140.dp)
                .clip(CircleShape)
                .border(5.dp, Color.White, CircleShape)
                .padding(10.dp)
                .clip(CircleShape)
                .background(if (bulbStart > 0) MaterialTheme.colorScheme.primary else Color.White)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { act("Déclenchement") { BleRemote.shoot(true) } })
                },
            contentAlignment = Alignment.Center,
        ) {
            countdown?.let { Text("$it", color = Color.Black, fontSize = 48.sp, fontWeight = FontWeight.Bold) }
            if (bulbStart > 0) {
                Text("${bulbElapsed / 60}:${"%02d".format(bulbElapsed % 60)}", color = Color.White,
                    fontSize = 32.sp, fontWeight = FontWeight.Bold)
            }
        }
    }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        // AF : maintenir
        Box(
            Modifier
                .weight(1f)
                .height(56.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .pointerInput(Unit) {
                    detectTapGestures(onPress = {
                        act("AF") { BleRemote.focusPress() }
                        tryAwaitRelease()
                        act("AF") { BleRemote.focusRelease() }
                    })
                },
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CenterFocusStrong, null); Spacer(Modifier.width(6.dp)); Text("AF (maintenir)")
            }
        }
        OutlinedButton(
            onClick = { act("Retardateur") { BleRemote.shootDelayed() } },
            modifier = Modifier.weight(1f).height(56.dp),
        ) { Icon(Icons.Default.Timer, null); Spacer(Modifier.width(6.dp)); Text("2 s") }
    }
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = {
                act("Pose B") {
                    if (bulbStart == 0L) { BleRemote.shutterManual("full_press", false); bulbStart = System.currentTimeMillis() }
                    else { BleRemote.shutterManual("release", false); bulbStart = 0L }
                }
            },
            modifier = Modifier.weight(1f).height(56.dp),
        ) { Text(if (bulbStart > 0) "Fermer la pose B" else "Ouvrir la pose B") }
        OutlinedButton(
            onClick = { act("Vidéo") { BleRemote.movie(); recording = !recording } },
            modifier = Modifier.weight(1f).height(56.dp),
        ) {
            Icon(if (recording) Icons.Default.Stop else Icons.Default.Videocam, null,
                tint = if (recording) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.width(6.dp)); Text(if (recording) "Stop vidéo" else "Vidéo")
        }
    }
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Zoom (PowerShot)", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        listOf(false to Icons.Default.ZoomOut, true to Icons.Default.ZoomIn).forEach { (tele, icon) ->
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)
                    .pointerInput(tele) {
                        detectTapGestures(onPress = {
                            act("Zoom") { BleRemote.zoom(tele, true) }
                            tryAwaitRelease()
                            act("Zoom") { BleRemote.zoom(tele, false) }
                        })
                    },
                contentAlignment = Alignment.Center,
            ) { Icon(icon, if (tele) "Zoom +" else "Zoom −") }
        }
    }
    Text(
        "Pose B : réglez l'appareil en mode B (ou M › BULB) puis ouvrez/fermez l'obturateur ici.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 8.dp),
    )

    // ------------------------------------------------ intervallomètre Bluetooth
    SectionTitle("Intervallomètre Bluetooth")
    if (progress.running || progress.finished) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    buildString {
                        append(if (progress.total > 0) "Image ${progress.shot} / ${progress.total}" else "Image ${progress.shot}")
                        if (progress.phase.isNotBlank()) append(" · ${progress.phase}")
                        progress.countdownSec?.let { append(" ${Exposure.formatDuration(it.toDouble())}") }
                    },
                )
                progress.error?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                val f = progress.fraction
                if (progress.running) {
                    Spacer(Modifier.height(6.dp))
                    if (f != null) LinearProgressIndicator(progress = { f }, modifier = Modifier.fillMaxWidth())
                    else LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
        }
    }
    Field("Nombre d'images (0 = illimité)", count, KeyboardType.Number) { count = it }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Poses longues en mode B", Modifier.weight(1f))
        Switch(checked = bulb, onCheckedChange = { bulb = it })
    }
    if (bulb) Field("Temps de pose (s ou mm:ss)", exposure, KeyboardType.Text) { exposure = it }
    else Text("La vitesse réglée sur l'appareil est utilisée.", style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    Field("Pause entre deux poses (s ou mm:ss)", interval, KeyboardType.Text) { interval = it }
    Field("Délai avant départ (s)", startDelay, KeyboardType.Number) { startDelay = it }

    val exp = if (bulb) parseSeconds(exposure)?.coerceAtLeast(30.5) else null
    val job = run {
        val n = count.toIntOrNull()
        val i = parseSeconds(interval)
        if (n == null || i == null || (bulb && exp == null)) null
        else ShootJob.Interval(n, exp, i, startDelay.toIntOrNull() ?: 0)
    }
    job?.let { j -> JobRunner.estimate(j)?.let { Text("Durée estimée : ${Exposure.formatDuration(it)}", color = MaterialTheme.colorScheme.secondary) } }
    Spacer(Modifier.height(8.dp))
    if (progress.running) {
        Button(onClick = { ShootingService.stop(context) }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Icon(Icons.Default.Stop, null); Spacer(Modifier.width(8.dp)); Text("Arrêter")
        }
    } else {
        Button(
            onClick = { job?.let { ShootingService.start(context, it, bluetooth = true) } },
            enabled = job != null, modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text("Démarrer") }
    }
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun Field(label: String, value: String, type: KeyboardType, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = type),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    )
}
