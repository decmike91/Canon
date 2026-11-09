package com.decmike.canonremote.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.decmike.canonremote.ccapi.CameraSetting
import com.decmike.canonremote.ccapi.CcapiException
import com.decmike.canonremote.data.CameraRepository
import com.decmike.canonremote.ui.GuideOverlay
import com.decmike.canonremote.ui.GuidePicker
import com.decmike.canonremote.ui.Labels
import com.decmike.canonremote.ui.parseAspect
import com.decmike.canonremote.ui.rememberGuideState
import com.decmike.canonremote.ui.SettingDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LiveViewModel : ViewModel() {
    private val _frame = MutableStateFlow<ImageBitmap?>(null)
    val frame = _frame.asStateFlow()
    private val _histogram = MutableStateFlow<IntArray?>(null)
    val histogram = _histogram.asStateFlow()
    private val _active = MutableStateFlow(false)
    val active = _active.asStateFlow()
    private val _fps = MutableStateFlow(0)
    val fps = _fps.asStateFlow()

    private var loop: Job? = null
    private var coordSpace: Pair<Int, Int>? = null
    private var frameSize = IntSize(0, 0)

    fun start() {
        if (loop?.isActive == true) return
        val c = CameraRepository.client ?: return
        val prefs = CameraRepository.prefs
        _active.value = true
        loop = viewModelScope.launch(Dispatchers.IO) {
            try {
                c.liveView(prefs.liveViewSize, if (prefs.cameraScreenOff) "off" else "keep")
            } catch (e: Exception) {
                CameraRepository.message("Visée directe impossible : ${e.message}")
                _active.value = false
                return@launch
            }
            coordSpace = runCatching { c.liveViewCoordinateSpace() }.getOrNull()
            var n = 0
            var t0 = System.currentTimeMillis()
            var errors = 0
            while (isActive) {
                try {
                    val bytes = c.liveViewFrame()
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (bmp != null) {
                        frameSize = IntSize(bmp.width, bmp.height)
                        _frame.value = bmp.asImageBitmap()
                        if (prefs.histogram && n % 3 == 0) _histogram.value = histogramOf(bmp)
                        n++
                    }
                    errors = 0
                    val now = System.currentTimeMillis()
                    if (now - t0 >= 1000) { _fps.value = n; n = 0; t0 = now }
                } catch (e: CcapiException) {
                    delay(if (e.busy) 100 else 500)
                } catch (e: Exception) {
                    if (++errors > 10) {
                        CameraRepository.message("Visée directe interrompue")
                        break
                    }
                    delay(300)
                }
            }
            _active.value = false
        }
    }

    fun stop() {
        loop?.cancel()
        loop = null
        _active.value = false
        _frame.value = null
        CameraRepository.client?.let { c ->
            CameraRepository.scope.launch { runCatching { c.liveView("off", "on") } }
        }
    }

    /** Position touchée normalisée (0..1) -> déplacement du collimateur AF. */
    fun touchAf(nx: Float, ny: Float) {
        val c = CameraRepository.client ?: return
        val (w, h) = coordSpace ?: (frameSize.width to frameSize.height)
        if (w <= 0 || h <= 0) return
        viewModelScope.launch {
            runCatching { c.setAfPosition((nx * w).toInt(), (ny * h).toInt()) }
                .onFailure { CameraRepository.message("Collimateur AF : ${it.message}") }
        }
    }

    override fun onCleared() { stop() }

    private fun histogramOf(src: Bitmap): IntArray {
        val small = Bitmap.createScaledBitmap(src, 160, (160f * src.height / src.width).toInt().coerceAtLeast(1), false)
        val px = IntArray(small.width * small.height)
        small.getPixels(px, 0, small.width, 0, 0, small.width, small.height)
        val bins = IntArray(64)
        for (p in px) {
            val r = (p shr 16) and 0xff
            val g = (p shr 8) and 0xff
            val b = p and 0xff
            val y = (r * 299 + g * 587 + b * 114) / 1000
            bins[y shr 2]++
        }
        if (small !== src) small.recycle()
        return bins
    }
}

@Composable
fun RemoteScreen(vm: LiveViewModel = viewModel()) {
    val prefs = CameraRepository.prefs
    val scope = rememberCoroutineScope()
    val frame by vm.frame.collectAsStateWithLifecycle()
    val hist by vm.histogram.collectAsStateWithLifecycle()
    val active by vm.active.collectAsStateWithLifecycle()
    val fps by vm.fps.collectAsStateWithLifecycle()
    val settings by CameraRepository.settings.collectAsStateWithLifecycle()
    val recent by CameraRepository.recent.collectAsStateWithLifecycle()

    val guides = rememberGuideState()
    var af by remember { mutableStateOf(true) }
    var timer by remember { mutableIntStateOf(0) }
    var countdown by remember { mutableStateOf<Int?>(null) }
    var bulbStart by remember { mutableLongStateOf(0L) }
    var bulbElapsed by remember { mutableIntStateOf(0) }
    var recording by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<CameraSetting?>(null) }
    var afPoint by remember { mutableStateOf<Offset?>(null) }

    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = prefs.keepScreenOn
        onDispose { view.keepScreenOn = false }
    }
    DisposableEffect(Unit) {
        vm.start()
        onDispose { vm.stop() }
    }
    LaunchedEffect(bulbStart) {
        while (bulbStart > 0) {
            bulbElapsed = ((System.currentTimeMillis() - bulbStart) / 1000).toInt()
            delay(250)
        }
    }

    fun act(label: String, block: suspend () -> Unit) {
        scope.launch {
            try { block() } catch (e: Exception) { CameraRepository.message("$label : ${e.message}") }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        // ------------------------------------------------ visée
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 2f)
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            val img = frame
            if (img != null) {
                Image(
                    img, "Visée directe",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(img.width, img.height) {
                            detectTapGestures { pos ->
                                // conversion en coordonnées image (ContentScale.Fit)
                                val scale = minOf(size.width / img.width.toFloat(), size.height / img.height.toFloat())
                                val dw = img.width * scale
                                val dh = img.height * scale
                                val ox = (size.width - dw) / 2
                                val oy = (size.height - dh) / 2
                                val nx = ((pos.x - ox) / dw).coerceIn(0f, 1f)
                                val ny = ((pos.y - oy) / dh).coerceIn(0f, 1f)
                                afPoint = pos
                                vm.touchAf(nx, ny)
                            }
                        },
                )
                GuideOverlay(
                    guides, androidx.compose.ui.geometry.Size(img.width.toFloat(), img.height.toFloat()), Modifier.fillMaxSize(),
                    cameraRatio = parseAspect(settings["stillimageaspectratio"]?.value ?: settings["aspectratio"]?.value),
                )
                afPoint?.let { p ->
                    Canvas(Modifier.fillMaxSize()) {
                        drawRect(Color(0xFF4CAF50), topLeft = Offset(p.x - 40, p.y - 40),
                            size = androidx.compose.ui.geometry.Size(80f, 80f), style = Stroke(3f))
                    }
                }
                hist?.let { h ->
                    Histogram(h, Modifier.align(Alignment.BottomStart).padding(8.dp).size(110.dp, 56.dp))
                }
                Text("$fps i/s", color = Color.White.copy(alpha = .6f), fontSize = 10.sp,
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp))
            } else {
                Text(if (active) "Démarrage de la visée…" else "Visée directe désactivée", color = Color.Gray)
            }
            countdown?.let {
                Text("$it", color = Color.White, fontSize = 72.sp, fontWeight = FontWeight.Bold)
            }
            if (bulbStart > 0) {
                Surface(color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp)) {
                    Text("BULB  ${bulbElapsed / 60}:${"%02d".format(bulbElapsed % 60)}",
                        Modifier.padding(horizontal = 10.dp, vertical = 4.dp), fontWeight = FontWeight.Bold)
                }
            }
        }

        // ------------------------------------------------ barre d'outils de visée
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (active) vm.stop() else vm.start() }) {
                Icon(if (active) Icons.Default.VisibilityOff else Icons.Default.Visibility, "Visée directe")
            }
            IconButton(onClick = { guides.pickerOpen = true }) { Icon(Icons.Default.GridOn, "Mire de composition") }
            if (guides.guide.flippable) {
                IconButton(onClick = guides::rotate) { Icon(Icons.Default.Flip, "Orientation de la mire") }
            }
            Spacer(Modifier.weight(1f))
            Text("MF", style = MaterialTheme.typography.labelMedium)
            listOf("near3" to "<<<", "near2" to "<<", "near1" to "<", "far1" to ">", "far2" to ">>", "far3" to ">>>").forEach { (s, l) ->
                Text(
                    l, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .pointerInput(s) { detectTapGestures { act("Mise au point") { CameraRepository.requireClient().driveFocus(s) } } }
                        .padding(horizontal = 6.dp, vertical = 10.dp),
                )
            }
        }

        // ------------------------------------------------ réglages rapides
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            listOf("shootingmodedial", "tv", "av", "iso", "exposure", "wb", "drive", "afoperation").forEach { key ->
                settings[key]?.let { s ->
                    AssistChip(
                        onClick = { if (s.editable) editing = s },
                        label = { Text(s.value?.let { Labels.value(key, it) } ?: "—", maxLines = 1) },
                    )
                }
            }
        }

        // ------------------------------------------------ déclenchement
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // dernière image
            Box(Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                recent.firstOrNull()?.let { f ->
                    AsyncImage(f.thumbnailUrl, f.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
            }

            // déclencheur principal
            Box(
                Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .border(4.dp, Color.White, CircleShape)
                    .padding(8.dp)
                    .clip(CircleShape)
                    .background(if (bulbStart > 0) MaterialTheme.colorScheme.primary else Color.White)
                    .pointerInput(af, timer) {
                        detectTapGestures(
                            onTap = {
                                act("Déclenchement") {
                                    val c = CameraRepository.requireClient()
                                    if (settings["tv"]?.value == "bulb" || settings["shootingmodedial"]?.value == "bulb") {
                                        if (bulbStart == 0L) {
                                            c.shutterManual("full_press", af); bulbStart = System.currentTimeMillis()
                                        } else {
                                            c.shutterManual("release", false); bulbStart = 0L
                                        }
                                    } else {
                                        for (s in timer downTo 1) { countdown = s; delay(1000) }
                                        countdown = null
                                        c.shoot(af)
                                    }
                                }
                            },
                        )
                    },
            )

            // bouton AF (maintenir)
            Box(
                Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .pointerInput(Unit) {
                        detectTapGestures(onPress = {
                            val c = CameraRepository.client ?: return@detectTapGestures
                            scope.launch { runCatching { c.shutterManual("half_press", true) } }
                            tryAwaitRelease()
                            scope.launch { runCatching { c.shutterManual("release", false) } }
                        })
                    },
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.CenterFocusStrong, null)
                    Text("AF", fontSize = 11.sp)
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(selected = af, onClick = { af = !af }, label = { Text("AF au déclenchement") })
            listOf(0, 2, 10).forEach { t ->
                FilterChip(selected = timer == t, onClick = { timer = t }, label = { Text(if (t == 0) "0 s" else "$t s") })
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                act("Vidéo") {
                    val c = CameraRepository.requireClient()
                    if (!recording) { runCatching { c.movieMode(true) }; c.record(true); recording = true }
                    else { c.record(false); recording = false }
                }
            }) {
                Icon(if (recording) Icons.Default.Stop else Icons.Default.Videocam, null,
                    tint = if (recording) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.width(6.dp))
                Text(if (recording) "Arrêter la vidéo" else "Enregistrer une vidéo")
            }
            if (recording) Icon(Icons.Default.FiberManualRecord, null, tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.CenterVertically))
        }
        Text(
            "Touchez l'image pour placer le collimateur AF. En pose B, touchez le déclencheur pour ouvrir puis refermer l'obturateur.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
    }

    GuidePicker(guides)

    editing?.let { s ->
        SettingDialog(
            s, onDismiss = { editing = null },
            onPick = { v -> editing = null; scope.launch { CameraRepository.setSetting(s.key, v) } },
            onPickObject = { v -> editing = null; scope.launch { CameraRepository.setSettingObject(s.key, v) } },
        )
    }
}

@Composable
private fun Histogram(bins: IntArray, modifier: Modifier) {
    Canvas(modifier.background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(4.dp))) {
        val max = (bins.maxOrNull() ?: 1).coerceAtLeast(1).toFloat()
        val bw = size.width / bins.size
        val path = Path().apply {
            moveTo(0f, size.height)
            bins.forEachIndexed { i, v -> lineTo(i * bw, size.height - (v / max) * size.height) }
            lineTo(size.width, size.height)
            close()
        }
        drawPath(path, Color.White.copy(alpha = 0.8f))
    }
}
