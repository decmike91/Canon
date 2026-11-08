package com.decmike.canonremote.ui.screens

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.decmike.canonremote.ccapi.CameraFile
import com.decmike.canonremote.data.CameraRepository
import com.decmike.canonremote.data.Downloader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject

enum class FileFilter(val label: String) { ALL("Tout"), JPEG("JPEG/HEIF"), RAW("RAW"), VIDEO("Vidéos") }

data class GalleryState(
    val storages: List<String> = emptyList(),
    val storage: String? = null,
    val files: List<CameraFile> = emptyList(),
    val loading: Boolean = false,
    val selected: Set<String> = emptySet(),
    val filter: FileFilter = FileFilter.ALL,
    val transfer: String? = null,
    val transferFraction: Float? = null,
    val error: String? = null,
) {
    val visible: List<CameraFile>
        get() = files.filter {
            when (filter) {
                FileFilter.ALL -> true
                FileFilter.JPEG -> it.isJpegLike
                FileFilter.RAW -> it.isRaw
                FileFilter.VIDEO -> it.isVideo
            }
        }
}

class GalleryViewModel(app: Application) : AndroidViewModel(app) {
    private val _state = MutableStateFlow(GalleryState())
    val state = _state.asStateFlow()

    init { load() }

    fun load(storage: String? = _state.value.storage) {
        val c = CameraRepository.client ?: return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, files = emptyList(), selected = emptySet()) }
            try {
                val storages = c.storages()
                val st = storage?.takeIf { it in storages } ?: storages.firstOrNull()
                _state.update { it.copy(storages = storages, storage = st) }
                if (st != null) {
                    val folders = c.folders(st).sortedDescending()
                    for (folder in folders) {
                        val files = c.files(folder).sortedByDescending { it.name }
                        _state.update { it.copy(files = it.files + files) }
                    }
                }
            } catch (e: Exception) {
                _state.update { it.copy(error = "Lecture de la carte impossible : ${e.message}") }
            }
            _state.update { it.copy(loading = false) }
        }
    }

    fun setFilter(f: FileFilter) = _state.update { it.copy(filter = f) }

    fun toggle(file: CameraFile) = _state.update {
        it.copy(selected = if (file.url in it.selected) it.selected - file.url else it.selected + file.url)
    }

    fun selectAll() = _state.update { it.copy(selected = it.visible.map { f -> f.url }.toSet()) }
    fun clearSelection() = _state.update { it.copy(selected = emptySet()) }

    fun download(files: List<CameraFile>) {
        val c = CameraRepository.client ?: return
        viewModelScope.launch {
            var ok = 0
            for ((i, f) in files.withIndex()) {
                _state.update { it.copy(transfer = "Téléchargement ${i + 1}/${files.size} · ${f.name}", transferFraction = 0f) }
                runCatching {
                    Downloader.save(getApplication(), c, f) { read, total ->
                        if (total > 0) _state.update { it.copy(transferFraction = read.toFloat() / total) }
                    }
                }.onSuccess { ok++ }
                    .onFailure { CameraRepository.message("${f.name} : ${it.message}") }
            }
            _state.update { it.copy(transfer = null, transferFraction = null, selected = emptySet()) }
            CameraRepository.message("$ok fichier(s) enregistré(s) dans ${Downloader.FOLDER}")
        }
    }

    fun delete(files: List<CameraFile>) {
        val c = CameraRepository.client ?: return
        viewModelScope.launch {
            val deleted = mutableSetOf<String>()
            for (f in files) {
                runCatching { c.deleteFile(f) }
                    .onSuccess { deleted += f.url }
                    .onFailure { CameraRepository.message("Suppression de ${f.name} : ${it.message}") }
            }
            _state.update { s -> s.copy(files = s.files.filter { it.url !in deleted }, selected = emptySet()) }
        }
    }

    suspend fun info(file: CameraFile): JSONObject? =
        runCatching { CameraRepository.client?.fileInfo(file) }.getOrNull()
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GalleryScreen(vm: GalleryViewModel = viewModel()) {
    val st by vm.state.collectAsStateWithLifecycle()
    var viewerIndex by remember { mutableStateOf<Int?>(null) }
    var confirmDelete by remember { mutableStateOf<List<CameraFile>?>(null) }
    val visible = st.visible
    val selecting = st.selected.isNotEmpty()

    val idx = viewerIndex
    if (idx != null) {
        BackHandler { viewerIndex = null }
        Viewer(visible, idx, vm, onClose = { viewerIndex = null }, onDelete = { confirmDelete = listOf(it) })
    } else Column(Modifier.fillMaxSize()) {
        // ---- barre d'actions
        if (selecting) {
            BackHandler { vm.clearSelection() }
            Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primaryContainer).padding(4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = vm::clearSelection) { Icon(Icons.Default.Close, "Annuler") }
                Text("${st.selected.size} sélectionné(s)", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                IconButton(onClick = vm::selectAll) { Icon(Icons.Default.SelectAll, "Tout sélectionner") }
                IconButton(onClick = { vm.download(st.files.filter { it.url in st.selected }) }) { Icon(Icons.Default.Download, "Télécharger") }
                IconButton(onClick = { confirmDelete = st.files.filter { it.url in st.selected } }) { Icon(Icons.Default.Delete, "Supprimer") }
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (st.storages.size > 1) st.storages.forEach { s ->
                        FilterChip(selected = s == st.storage, onClick = { vm.load(s) }, label = { Text(s.substringAfterLast('/').uppercase()) })
                    }
                    FileFilter.entries.forEach { f ->
                        FilterChip(selected = st.filter == f, onClick = { vm.setFilter(f) }, label = { Text(f.label) })
                    }
                }
                IconButton(onClick = { vm.load() }) { Icon(Icons.Default.Refresh, "Actualiser") }
            }
        }
        if (st.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        st.transfer?.let { t ->
            Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                Text(t, style = MaterialTheme.typography.bodySmall)
                LinearProgressIndicator(progress = { st.transferFraction ?: 0f }, modifier = Modifier.fillMaxWidth())
            }
        }
        st.error?.let { Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(16.dp)) }
        if (!st.loading && visible.isEmpty() && st.error == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Aucun fichier", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        LazyVerticalGrid(
            GridCells.Adaptive(110.dp),
            Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(visible, key = { it.url }) { f ->
                val sel = f.url in st.selected
                Box(
                    Modifier
                        .aspectRatio(1f)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .then(if (sel) Modifier.border(3.dp, MaterialTheme.colorScheme.primary) else Modifier)
                        .combinedClickable(
                            onClick = { if (selecting) vm.toggle(f) else viewerIndex = visible.indexOf(f) },
                            onLongClick = { vm.toggle(f) },
                        ),
                ) {
                    AsyncImage(f.thumbnailUrl, f.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    Surface(color = Color.Black.copy(alpha = .55f), shape = RoundedCornerShape(topEnd = 6.dp),
                        modifier = Modifier.align(Alignment.BottomStart)) {
                        Text(f.extension, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                    }
                    if (f.isVideo) Icon(Icons.Default.PlayCircle, null, Modifier.align(Alignment.Center).size(36.dp), tint = Color.White)
                    if (sel) Icon(Icons.Default.CheckCircle, null, Modifier.align(Alignment.TopEnd).padding(4.dp),
                        tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }

    confirmDelete?.let { files ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Supprimer ${files.size} fichier(s) ?") },
            text = { Text("Les fichiers seront définitivement effacés de la carte mémoire de l'appareil.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.delete(files); confirmDelete = null
                    if (viewerIndex != null) viewerIndex = null
                }) { Text("Supprimer", color = MaterialTheme.colorScheme.primary) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Annuler") } },
        )
    }
}

@Composable
private fun Viewer(
    files: List<CameraFile>,
    start: Int,
    vm: GalleryViewModel,
    onClose: () -> Unit,
    onDelete: (CameraFile) -> Unit,
) {
    if (files.isEmpty()) { onClose(); return }
    val pager = rememberPagerState(initialPage = start.coerceIn(0, files.lastIndex)) { files.size }
    var showInfo by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<JSONObject?>(null) }
    val file = files[pager.currentPage.coerceIn(0, files.lastIndex)]
    LaunchedEffect(file.url, showInfo) { info = if (showInfo) vm.info(file) else null }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(pager, Modifier.fillMaxSize(), key = { files[it].url }) { page ->
            val f = files[page]
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                AsyncImage(
                    model = if (f.isVideo) f.thumbnailUrl else f.displayUrl,
                    contentDescription = f.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Row(Modifier.fillMaxWidth().background(Color.Black.copy(alpha = .5f)).padding(4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour", tint = Color.White) }
            Text("${file.name}  ·  ${pager.currentPage + 1}/${files.size}", Modifier.weight(1f), color = Color.White)
            IconButton(onClick = { showInfo = !showInfo }) { Icon(Icons.Default.Info, "Infos", tint = Color.White) }
            IconButton(onClick = { vm.download(listOf(file)) }) { Icon(Icons.Default.Download, "Télécharger", tint = Color.White) }
            IconButton(onClick = { onDelete(file) }) { Icon(Icons.Default.Delete, "Supprimer", tint = Color.White) }
        }
        if (showInfo) {
            Surface(
                color = Color.Black.copy(alpha = .75f), shape = RoundedCornerShape(12.dp),
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(12.dp),
            ) {
                Column(Modifier.padding(12.dp).verticalScroll(rememberScrollState())) {
                    val i = info
                    if (i == null) Text("Chargement…", color = Color.White)
                    else {
                        val labels = mapOf(
                            "filesize" to "Taille", "lastmodifieddate" to "Date", "tv" to "Vitesse", "av" to "Ouverture",
                            "iso" to "ISO", "exposure" to "Correction", "wb" to "Balance des blancs", "rating" to "Note",
                            "protect" to "Protection", "playtime" to "Durée", "focallength" to "Focale",
                        )
                        i.keys().forEach { k ->
                            Row {
                                Text(labels[k] ?: k, color = Color.Gray, modifier = Modifier.width(140.dp))
                                Spacer(Modifier.width(8.dp))
                                val v = i.opt(k)?.toString().orEmpty()
                                Text(if (k == "filesize") v.toLongOrNull()?.let { "%.1f Mo".format(it / 1_048_576.0) } ?: v else v, color = Color.White)
                            }
                        }
                    }
                }
            }
        }
        if (file.isVideo) {
            Text("Aperçu vidéo : téléchargez le fichier pour le lire.", color = Color.White,
                modifier = Modifier.align(Alignment.Center).clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = .5f)).padding(8.dp))
        }
    }
}
