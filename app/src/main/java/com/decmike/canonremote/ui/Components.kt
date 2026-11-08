package com.decmike.canonremote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.decmike.canonremote.ccapi.CameraSetting
import com.decmike.canonremote.ccapi.Exposure
import kotlin.math.roundToInt

/** Libellés français des réglages CCAPI les plus courants. */
object Labels {
    private val names = mapOf(
        "shootingmodedial" to "Mode de prise de vue",
        "shootingmode" to "Mode de prise de vue",
        "av" to "Ouverture",
        "tv" to "Vitesse d'obturation",
        "iso" to "Sensibilité ISO",
        "exposure" to "Correction d'exposition",
        "wb" to "Balance des blancs",
        "colortemperature" to "Température de couleur (K)",
        "wbshift" to "Correction de balance des blancs",
        "picturestyle" to "Style d'image",
        "afoperation" to "Mode AF",
        "afmethod" to "Méthode AF",
        "drive" to "Mode d'acquisition",
        "metering" to "Mode de mesure",
        "stillimagequality" to "Qualité d'image",
        "stillimageaspectratio" to "Format d'image",
        "flash" to "Flash",
        "aeb" to "Bracketing d'exposition (AEB)",
        "shutterbutton" to "Déclencheur",
        "shuttermode" to "Mode d'obturation",
        "colorspace" to "Espace colorimétrique",
        "highlighttonepriority" to "Priorité hautes lumières",
        "autolightingoptimizer" to "Auto Lighting Optimizer",
        "moviequality" to "Qualité vidéo",
        "soundrecording" to "Enregistrement du son",
        "aspectratio" to "Format",
        "imagequality" to "Qualité d'image",
        "exposureincrements" to "Paliers d'exposition",
        "isoautoexposure" to "ISO auto",
        "silentshutter" to "Déclenchement silencieux",
        "servoaf" to "AF Servo",
        "eyedetection" to "Détection de l'œil",
        "subjecttodetect" to "Sujet à détecter",
        "digitalzoom" to "Zoom numérique",
        "manualfocusassist" to "Assistance MF",
        "focusbracketing" to "Bracketing de mise au point",
    )

    private val values = mapOf(
        "m" to "Manuel (M)", "av" to "Priorité ouverture (Av)", "tv" to "Priorité vitesse (Tv)",
        "p" to "Programme (P)", "fv" to "Fv", "bulb" to "Pose B", "a+" to "Auto+", "scn" to "Scène",
        "c1" to "C1", "c2" to "C2", "c3" to "C3",
        "auto" to "Auto", "awb" to "AWB (ambiance)", "awbwhite" to "AWB (blanc)", "daylight" to "Lumière du jour",
        "shade" to "Ombre", "cloudy" to "Nuageux", "tungsten" to "Tungstène", "whitefluorescent" to "Fluorescent",
        "flash" to "Flash", "custom" to "Personnalisée", "colortemp" to "Température de couleur",
        "oneshot" to "One-Shot", "servo" to "Servo", "aifocus" to "AI Focus", "manual" to "Manuel",
        "single" to "Vue par vue", "highspeedcontinuous" to "Rafale rapide", "lowspeedcontinuous" to "Rafale lente",
        "highspeedcontinuousplus" to "Rafale rapide +", "self_10sec" to "Retardateur 10 s", "self_2sec" to "Retardateur 2 s",
        "self_continuous" to "Retardateur + rafale",
        "evaluative" to "Évaluative", "partial" to "Sélective", "spot" to "Spot", "center_weighted_average" to "Moy. pond. centrale",
        "on" to "Activé", "off" to "Désactivé", "enable" to "Activé", "disable" to "Désactivé",
    )

    fun name(key: String) = names[key] ?: key.replaceFirstChar { it.uppercase() }

    fun value(key: String, v: String): String = when (key) {
        "tv", "av", "exposure", "iso" -> Exposure.pretty(key, v)
        else -> values[v] ?: v.replace('_', ' ')
    }

    /** Réglages mis en avant dans l'écran Réglages, dans cet ordre. */
    val primary = listOf("shootingmodedial", "tv", "av", "iso", "exposure", "wb", "colortemperature",
        "afoperation", "afmethod", "drive", "metering", "stillimagequality", "picturestyle", "aeb")
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = modifier.padding(top = 16.dp, bottom = 6.dp),
    )
}

/**
 * Dialogue de modification d'un réglage : liste, plage numérique ou réglage composé.
 */
@Composable
fun SettingDialog(
    setting: CameraSetting,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
    onPickObject: (Map<String, String>) -> Unit,
) {
    when {
        setting.range != null -> {
            val r = setting.range
            var v by remember { mutableFloatStateOf(setting.value?.toFloatOrNull() ?: r.min.toFloat()) }
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(Labels.name(setting.key)) },
                text = {
                    Column {
                        val snapped = (((v - r.min) / r.step).roundToInt() * r.step + r.min).coerceIn(r.min, r.max)
                        Text("$snapped", style = MaterialTheme.typography.headlineMedium)
                        Slider(
                            value = v, onValueChange = { v = it },
                            valueRange = r.min.toFloat()..r.max.toFloat(),
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val snapped = (((v - r.min) / r.step).roundToInt() * r.step + r.min).coerceIn(r.min, r.max)
                        onPick(snapped.toString())
                    }) { Text("Appliquer") }
                },
                dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
            )
        }
        !setting.subOptions.isNullOrEmpty() -> {
            val sub = setting.subOptions
            var current by remember { mutableStateOf(setting.subValues.orEmpty()) }
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(Labels.name(setting.key)) },
                text = {
                    LazyColumn(Modifier.heightIn(max = 420.dp)) {
                        sub.forEach { (field, opts) ->
                            item { SectionTitle(field) }
                            items(opts) { o ->
                                OptionRow(Labels.value(field, o), current[field] == o) { current = current + (field to o) }
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { onPickObject(current) }) { Text("Appliquer") } },
                dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
            )
        }
        else -> {
            val opts = setting.options.orEmpty()
            val state = rememberLazyListState()
            LaunchedEffect(Unit) {
                val i = opts.indexOf(setting.value)
                if (i > 2) state.scrollToItem(i - 2)
            }
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(Labels.name(setting.key)) },
                text = {
                    LazyColumn(Modifier.heightIn(max = 420.dp), state = state) {
                        items(opts) { o -> OptionRow(Labels.value(setting.key, o), o == setting.value) { onPick(o) } }
                    }
                },
                confirmButton = { TextButton(onClick = onDismiss) { Text("Fermer") } },
            )
        }
    }
}

@Composable
private fun OptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
        if (selected) Icon(Icons.Default.Check, null)
        else Spacer(Modifier.width(24.dp))
    }
}
