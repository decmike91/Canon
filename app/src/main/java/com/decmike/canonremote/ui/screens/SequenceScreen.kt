package com.decmike.canonremote.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.decmike.canonremote.ccapi.Exposure
import com.decmike.canonremote.data.CameraRepository
import com.decmike.canonremote.jobs.HdrMethod
import com.decmike.canonremote.jobs.JobRunner
import com.decmike.canonremote.jobs.ShootJob
import com.decmike.canonremote.jobs.ShootingService
import com.decmike.canonremote.ui.SectionTitle

/** "1/125" -> 0.008 ; "2:30" -> 150 ; "1.5" ou "1,5" -> 1.5 */
fun parseSeconds(text: String): Double? {
    val t = text.trim().replace(',', '.')
    if (t.isEmpty()) return null
    if (t.contains('/')) {
        val (a, b) = t.split('/', limit = 2)
        val d = b.toDoubleOrNull() ?: return null
        return (a.toDoubleOrNull() ?: return null) / d
    }
    if (t.contains(':')) {
        val parts = t.split(':').map { it.toDoubleOrNull() ?: return null }
        return parts.fold(0.0) { acc, v -> acc * 60 + v }
    }
    return t.toDoubleOrNull()
}

@Composable
fun SequenceScreen() {
    val context = LocalContext.current
    val progress by CameraRepository.jobProgress.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }

    // intervallomètre
    var count by rememberSaveable { mutableStateOf("100") }
    var useCameraExposure by rememberSaveable { mutableStateOf(true) }
    var exposure by rememberSaveable { mutableStateOf("30") }
    var interval by rememberSaveable { mutableStateOf("5") }
    var startToStart by rememberSaveable { mutableStateOf(false) }
    var startDelay by rememberSaveable { mutableStateOf("0") }
    var afEach by rememberSaveable { mutableStateOf(false) }

    // HDR
    var hdrShots by rememberSaveable { mutableIntStateOf(3) }
    var hdrStep by rememberSaveable { mutableStateOf(1.0) }
    var hdrMethod by rememberSaveable { mutableStateOf(HdrMethod.AUTO) }
    var hdrRepeats by rememberSaveable { mutableStateOf("1") }
    var hdrInterval by rememberSaveable { mutableStateOf("10") }

    // focus stacking
    var fsShots by rememberSaveable { mutableStateOf("10") }
    var fsSize by rememberSaveable { mutableIntStateOf(1) }
    var fsSteps by rememberSaveable { mutableStateOf("3") }
    var fsFar by rememberSaveable { mutableStateOf(true) }
    var fsSettle by rememberSaveable { mutableStateOf("1") }

    val delaySec = startDelay.toIntOrNull() ?: 0
    val job: ShootJob? = when (tab) {
        0 -> {
            val n = count.toIntOrNull()
            val e = if (useCameraExposure) null else parseSeconds(exposure)
            val i = parseSeconds(interval)
            if (n == null || i == null || (!useCameraExposure && e == null)) null
            else ShootJob.Interval(n, e, i, delaySec, startToStart, afEach)
        }
        1 -> ShootJob.Hdr(hdrShots, hdrStep, hdrMethod, hdrRepeats.toIntOrNull()?.coerceAtLeast(1) ?: 1,
            parseSeconds(hdrInterval) ?: 0.0, delaySec, afEach)
        else -> {
            val n = fsShots.toIntOrNull()
            val s = fsSteps.toIntOrNull()
            if (n == null || s == null) null
            else ShootJob.FocusStack(n.coerceAtLeast(2), fsSize, s.coerceAtLeast(1), fsFar, parseSeconds(fsSettle) ?: 1.0, delaySec)
        }
    }

    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            listOf("Intervallomètre", "HDR", "Focus stack").forEachIndexed { i, t ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t, maxLines = 1) })
            }
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            // -------------------------------------------- progression
            if (progress.running || progress.finished) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(progress.title, fontWeight = FontWeight.Bold)
                        Text(
                            buildString {
                                append(if (progress.total > 0) "Image ${progress.shot} / ${progress.total}" else "Image ${progress.shot}")
                                if (progress.phase.isNotBlank()) append(" · ${progress.phase}")
                                progress.countdownSec?.let { append(" ${Exposure.formatDuration(it.toDouble())}") }
                            },
                        )
                        progress.error?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                        Spacer(Modifier.height(8.dp))
                        val f = progress.fraction
                        if (progress.running) {
                            if (f != null) LinearProgressIndicator(progress = { f }, modifier = Modifier.fillMaxWidth())
                            else LinearProgressIndicator(Modifier.fillMaxWidth())
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            when (tab) {
                0 -> {
                    SectionTitle("Préréglages")
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AssistChip(onClick = { count = "300"; useCameraExposure = true; interval = "5"; startToStart = true }, label = { Text("Timelapse") })
                        AssistChip(onClick = { count = "20"; useCameraExposure = false; exposure = "30"; interval = "2"; startToStart = false }, label = { Text("Astro 30 s") })
                        AssistChip(onClick = { count = "120"; useCameraExposure = false; exposure = "30"; interval = "1"; startToStart = false }, label = { Text("Filé d'étoiles") })
                        AssistChip(onClick = { count = "1"; useCameraExposure = false; exposure = "5:00"; interval = "0" }, label = { Text("Pose longue 5 min") })
                        AssistChip(onClick = { count = "10"; useCameraExposure = false; exposure = "2:00"; interval = "3"; startToStart = false }, label = { Text("Poses ND 2 min") })
                    }
                    SectionTitle("Programme")
                    NumberField("Nombre d'images (0 = illimité)", count) { count = it }
                    SwitchRow("Temps de pose réglé sur l'appareil", useCameraExposure) { useCameraExposure = it }
                    if (!useCameraExposure) {
                        NumberField("Temps de pose (ex. 1/250, 30, 2:30)", exposure, KeyboardType.Text) { exposure = it }
                        parseSeconds(exposure)?.let {
                            Text(
                                if (it > 30) "Pose B : ${Exposure.formatDuration(it)} (appareil en mode M ou B)"
                                else "Vitesse : ${Exposure.formatDuration(it)}",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary,
                            )
                        }
                    }
                    NumberField(if (startToStart) "Intervalle début à début (s ou mm:ss)" else "Pause entre deux poses (s ou mm:ss)", interval, KeyboardType.Text) { interval = it }
                    SwitchRow("Mesurer l'intervalle d'un début de pose au suivant", startToStart) { startToStart = it }
                }
                1 -> {
                    SectionTitle("Bracketing")
                    Text("Nombre d'images")
                    ChipRow(listOf(3, 5, 7, 9), hdrShots, { "$it" }) { hdrShots = it }
                    Text("Écart entre les images")
                    ChipRow(listOf(1 / 3.0, 2 / 3.0, 1.0, 1.5, 2.0, 3.0), hdrStep, {
                        when (it) { 1 / 3.0 -> "⅓ IL"; 2 / 3.0 -> "⅔ IL"; else -> "${it.toString().removeSuffix(".0")} IL" }
                    }) { hdrStep = it }
                    Text("Méthode")
                    ChipRow(HdrMethod.entries, hdrMethod, { it.label }) { hdrMethod = it }
                    val half = (hdrShots - 1) / 2.0 * hdrStep
                    Text(
                        "Plage : −${"%.1f".format(half)} à +${"%.1f".format(half)} IL. En mode M la vitesse varie, sinon la correction d'exposition.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary,
                    )
                    SectionTitle("HDR timelapse")
                    NumberField("Nombre de séries", hdrRepeats) { hdrRepeats = it }
                    if ((hdrRepeats.toIntOrNull() ?: 1) > 1) NumberField("Pause entre les séries (s ou mm:ss)", hdrInterval, KeyboardType.Text) { hdrInterval = it }
                }
                else -> {
                    SectionTitle("Empilement de mise au point")
                    Text("Faites d'abord la mise au point sur le plan le plus proche (ou le plus lointain), objectif AF requis.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    NumberField("Nombre d'images", fsShots) { fsShots = it }
                    Text("Taille du pas")
                    ChipRow(listOf(1, 2, 3), fsSize, { listOf("", "Fin", "Moyen", "Large")[it] }) { fsSize = it }
                    NumberField("Pas entre deux images", fsSteps) { fsSteps = it }
                    SwitchRow("Vers l'infini (sinon vers le proche)", fsFar) { fsFar = it }
                    NumberField("Attente après chaque image (s)", fsSettle, KeyboardType.Decimal) { fsSettle = it }
                }
            }

            SectionTitle("Options")
            NumberField("Délai avant départ (s)", startDelay) { startDelay = it }
            if (tab != 2) SwitchRow("Autofocus avant chaque déclenchement", afEach) { afEach = it }

            Spacer(Modifier.height(12.dp))
            job?.let { j ->
                JobRunner.estimate(j)?.let {
                    Text("Durée estimée : ${Exposure.formatDuration(it)}", color = MaterialTheme.colorScheme.secondary)
                }
            }
            Spacer(Modifier.height(12.dp))
            if (progress.running) {
                Button(
                    onClick = { ShootingService.stop(context) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) { Icon(Icons.Default.Stop, null); Spacer(Modifier.width(8.dp)); Text("Arrêter la séquence") }
            } else {
                Button(
                    onClick = { job?.let { ShootingService.start(context, it) } },
                    enabled = job != null,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) { Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text("Démarrer") }
            }
            Text(
                "La séquence continue écran éteint (notification active). Gardez le téléphone à portée du Wi-Fi de l'appareil.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun NumberField(label: String, value: String, type: KeyboardType = KeyboardType.Number, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = type),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    )
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun <T> ChipRow(options: List<T>, selected: T, label: (T) -> String, onPick: (T) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        options.forEach { o -> FilterChip(selected = o == selected, onClick = { onPick(o) }, label = { Text(label(o)) }) }
    }
}
