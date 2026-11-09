package com.decmike.canonremote.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Mires d'aide à la composition. [flippable] : la mire peut être retournée (orientation). */
enum class CompositionGuide(val label: String, val tip: String, val flippable: Boolean = false) {
    NONE("Aucune", "Pas de mire."),
    THIRDS("Règle des tiers", "Placez le sujet ou l'horizon sur une ligne, les points forts aux intersections."),
    PHI("Section dorée", "Comme les tiers mais resserré vers le centre (rapport 0,618) : un équilibre plus naturel."),
    GRID("Quadrillage", "Grille 4×4 pour vérifier l'alignement des verticales et de l'horizon."),
    CROSS("Croix / centre", "Sujet centré : composition stable, idéale pour la symétrie."),
    DIAGONALS("Diagonales", "Les diagonales donnent du dynamisme et de la tension à l'image."),
    DIAGONAL_METHOD("Méthode des diagonales", "Lignes à 45° depuis chaque coin : alignez-y les éléments forts."),
    GOLDEN_TRIANGLES("Triangles dorés", "Une diagonale et deux perpendiculaires : idéal pour les sujets en biais.", true),
    SPIRAL("Spirale dorée", "La spirale guide l'œil vers le point fort ; placez-y le sujet.", true),
    RADIAL("Radiale", "Les lignes convergent vers le centre et y attirent le regard."),
    PERSPECTIVE("Perspective", "Point de fuite et horizon : alignez routes, rails ou bâtiments sur les fuyantes.", true),
    CIRCLE("Cercle", "Le cercle met le sujet en valeur et crée une impression d'harmonie."),
    PYRAMID("Pyramide", "La forme triangulaire apporte stabilité, utile pour les groupes."),
    V_SHAPE("Composition en V", "Les lignes en V créent de la profondeur et guident le regard."),
    L_SHAPE("Composition en L", "Un élément vertical et une base horizontale ; l'espace négatif guide l'œil.", true),
    S_CURVE("Courbe en S", "Une courbe (route, rivière) ajoute mouvement et douceur.", true),
}

/** Formats de recadrage proposés. 0 = format de l'image. */
enum class GuideAspect(val label: String, val ratio: Float) {
    AUTO("Image", 0f), R3_2("3:2", 3f / 2), R4_3("4:3", 4f / 3), R16_9("16:9", 16f / 9),
    R1_1("1:1", 1f), R4_5("4:5", 4f / 5), R2_3("2:3", 2f / 3), R9_16("9:16", 9f / 16),
}

val GuideColors = listOf(Color.White, Color(0xFFFFD54F), Color(0xFF66BB6A), Color(0xFFEF5350))

/** "3:2", "16_9", "1x1" -> ratio, sinon null. */
fun parseAspect(text: String?): Float? {
    val m = Regex("""(\d+)\s*[:_x/]\s*(\d+)""").find(text ?: return null) ?: return null
    val a = m.groupValues[1].toFloat()
    val b = m.groupValues[2].toFloat()
    return if (a > 0 && b > 0) a / b else null
}

/** Rectangle d'une image de taille [img] affichée en ContentScale.Fit dans [box]. */
fun fitRect(img: Size, box: Size): Rect {
    if (img.width <= 0 || img.height <= 0) return Rect(Offset.Zero, box)
    val s = min(box.width / img.width, box.height / img.height)
    val w = img.width * s
    val h = img.height * s
    return Rect(Offset((box.width - w) / 2, (box.height - h) / 2), Size(w, h))
}

/** Plus grand rectangle de rapport [ratio] centré dans [r]. */
fun cropRect(r: Rect, ratio: Float): Rect {
    if (ratio <= 0f) return r
    val w: Float
    val h: Float
    if (r.width / r.height > ratio) { h = r.height; w = h * ratio } else { w = r.width; h = w / ratio }
    return Rect(Offset(r.left + (r.width - w) / 2, r.top + (r.height - h) / 2), Size(w, h))
}

private const val PHI = 1.618034f

/**
 * Dessine la mire [g] dans [r]. Les mires s'adaptent au rectangle : en portrait,
 * la spirale et les triangles sont automatiquement réorientés.
 */
fun DrawScope.drawGuide(
    g: CompositionGuide,
    r: Rect,
    flipH: Boolean = false,
    flipV: Boolean = false,
    color: Color = Color.White,
    width: Float = 1.6f,
    dimOutside: Boolean = false,
) {
    if (dimOutside) {
        val dim = Color.Black.copy(alpha = 0.55f)
        drawRect(dim, Offset(0f, 0f), Size(size.width, r.top))
        drawRect(dim, Offset(0f, r.bottom), Size(size.width, size.height - r.bottom))
        drawRect(dim, Offset(0f, r.top), Size(r.left, r.height))
        drawRect(dim, Offset(r.right, r.top), Size(size.width - r.right, r.height))
        drawRect(color.copy(alpha = 0.7f), r.topLeft, r.size, style = Stroke(width))
    }
    val c = color.copy(alpha = 0.75f)
    // coordonnées normalisées (0..1) -> pixels, avec retournements
    fun p(x: Float, y: Float) = Offset(
        r.left + (if (flipH) 1 - x else x) * r.width,
        r.top + (if (flipV) 1 - y else y) * r.height,
    )
    fun line(x1: Float, y1: Float, x2: Float, y2: Float, w: Float = width, dashed: Boolean = false) =
        drawLine(c, p(x1, y1), p(x2, y2), w, pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(10f, 8f)) else null)
    fun dot(x: Float, y: Float) = drawCircle(c, width * 2.5f, p(x, y))

    when (g) {
        CompositionGuide.NONE -> {}
        CompositionGuide.THIRDS -> {
            for (i in 1..2) { val t = i / 3f; line(t, 0f, t, 1f); line(0f, t, 1f, t) }
            for (x in 1..2) for (y in 1..2) dot(x / 3f, y / 3f)
        }
        CompositionGuide.PHI -> {
            val a = 1 / PHI / PHI          // 0,382
            val b = 1 / PHI                // 0,618
            listOf(a, b).forEach { t -> line(t, 0f, t, 1f); line(0f, t, 1f, t) }
            for (x in listOf(a, b)) for (y in listOf(a, b)) dot(x, y)
        }
        CompositionGuide.GRID -> for (i in 1..3) { val t = i / 4f; line(t, 0f, t, 1f); line(0f, t, 1f, t) }
        CompositionGuide.CROSS -> {
            line(0.5f, 0f, 0.5f, 1f); line(0f, 0.5f, 1f, 0.5f)
            val s = min(r.width, r.height) * 0.08f
            drawRect(c, Offset(r.center.x - s, r.center.y - s), Size(2 * s, 2 * s), style = Stroke(width))
        }
        CompositionGuide.DIAGONALS -> { line(0f, 0f, 1f, 1f); line(1f, 0f, 0f, 1f) }
        CompositionGuide.DIAGONAL_METHOD -> {
            // lignes à 45° en pixels (s'adaptent au format)
            val d = min(r.width, r.height)
            val dx = d / r.width
            val dy = d / r.height
            line(0f, 0f, dx, dy); line(1f, 0f, 1 - dx, dy); line(0f, 1f, dx, 1 - dy); line(1f, 1f, 1 - dx, 1 - dy)
            line(dx, 0f, dx, 1f, dashed = true); line(1 - dx, 0f, 1 - dx, 1f, dashed = true)
            line(0f, dy, 1f, dy, dashed = true); line(0f, 1 - dy, 1f, 1 - dy, dashed = true)
        }
        CompositionGuide.GOLDEN_TRIANGLES -> {
            val a = p(0f, 1f)
            val b = p(1f, 0f)
            drawLine(c, a, b, width)
            // perpendiculaires depuis les deux autres coins (calcul en pixels)
            listOf(p(0f, 0f), p(1f, 1f)).forEach { q -> drawLine(c, q, foot(q, a, b), width) }
        }
        CompositionGuide.SPIRAL -> drawSpiral(r, flipH, flipV, c, width)
        CompositionGuide.RADIAL -> {
            listOf(0f to 0f, 1f to 0f, 0f to 1f, 1f to 1f, 0.5f to 0f, 0.5f to 1f, 0f to 0.5f, 1f to 0.5f)
                .forEach { (x, y) -> line(0.5f, 0.5f, x, y) }
        }
        CompositionGuide.PERSPECTIVE -> {
            val vx = 0.5f
            val vy = 1 / 3f
            line(0f, vy, 1f, vy, width * 1.3f)
            listOf(0f, 0.25f, 0.5f, 0.75f, 1f).forEach { t -> line(vx, vy, t, 1f, dashed = t != 0f && t != 1f) }
            line(vx, vy, 0f, 0f, dashed = true); line(vx, vy, 1f, 0f, dashed = true)
            line(vx, vy, 0f, 0.7f); line(vx, vy, 1f, 0.7f)
            dot(vx, vy)
        }
        CompositionGuide.CIRCLE -> {
            val rad = min(r.width, r.height) / 2
            drawCircle(c, rad * 0.92f, r.center, style = Stroke(width))
            drawCircle(c, rad * 0.55f, r.center, style = Stroke(width, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))))
            dot(0.5f, 0.5f)
        }
        CompositionGuide.PYRAMID -> { line(0.15f, 0.92f, 0.5f, 0.1f); line(0.5f, 0.1f, 0.85f, 0.92f); line(0.15f, 0.92f, 0.85f, 0.92f) }
        CompositionGuide.V_SHAPE -> { line(0.08f, 0.08f, 0.5f, 0.92f); line(0.5f, 0.92f, 0.92f, 0.08f) }
        CompositionGuide.L_SHAPE -> { line(1 / 3f, 0.08f, 1 / 3f, 2 / 3f + 0.02f); line(1 / 3f, 2 / 3f + 0.02f, 0.95f, 2 / 3f + 0.02f) }
        CompositionGuide.S_CURVE -> {
            val path = Path()
            for (i in 0..60) {
                val t = i / 60f
                val y = 0.05f + t * 0.9f
                val x = 0.5f + 0.28f * sin(t * 2 * PI.toFloat())
                val o = p(x, y)
                if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
            }
            drawPath(path, c, style = Stroke(width * 1.3f))
        }
    }
}

/** Projection orthogonale de [q] sur la droite (a, b). */
private fun foot(q: Offset, a: Offset, b: Offset): Offset {
    val ab = b - a
    val t = ((q - a).x * ab.x + (q - a).y * ab.y) / (ab.x * ab.x + ab.y * ab.y)
    return a + ab * t
}

/**
 * Spirale de Fibonacci calculée dans un rectangle d'or (φ × 1) puis étirée au
 * rectangle cible. En portrait, la spirale est transposée.
 */
private fun DrawScope.drawSpiral(r: Rect, flipH: Boolean, flipV: Boolean, c: Color, width: Float) {
    val portrait = r.height > r.width
    fun map(u: Float, v: Float): Offset {
        var nx = u / PHI
        var ny = v
        if (portrait) { val t = nx; nx = ny; ny = t }
        if (flipH) nx = 1 - nx
        if (flipV) ny = 1 - ny
        return Offset(r.left + nx * r.width, r.top + ny * r.height)
    }
    val spiral = Path()
    val boxes = Path()
    var x = 0f; var y = 0f; var w = PHI; var h = 1f
    var first = true
    fun arc(cx: Float, cy: Float, s: Float, from: Float) {
        for (k in 0..16) {
            val a = (from + 90f * k / 16) * (PI.toFloat() / 180f)
            val o = map(cx + s * cos(a), cy + s * sin(a))
            if (first) { spiral.moveTo(o.x, o.y); first = false } else spiral.lineTo(o.x, o.y)
        }
    }
    fun seg(x1: Float, y1: Float, x2: Float, y2: Float) {
        val a = map(x1, y1); val b = map(x2, y2)
        boxes.moveTo(a.x, a.y); boxes.lineTo(b.x, b.y)
    }
    for (i in 0 until 10) {
        when (i % 4) {
            0 -> { val s = h; arc(x + s, y + s, s, 180f); seg(x + s, y, x + s, y + h); x += s; w -= s }
            1 -> { val s = w; arc(x, y + s, s, 270f); seg(x, y + s, x + w, y + s); y += s; h -= s }
            2 -> { val s = h; arc(x + w - s, y, s, 0f); seg(x + w - s, y, x + w - s, y + h); w -= s }
            3 -> { val s = w; arc(x + w, y + h - s, s, 90f); seg(x, y + h - s, x + w, y + h - s); h -= s }
        }
        if (w <= 0f || h <= 0f) break
    }
    drawPath(boxes, c.copy(alpha = c.alpha * 0.45f), style = Stroke(width * 0.8f))
    drawPath(spiral, c, style = Stroke(width * 1.3f))
}

/** Aperçu d'une mire (utilisé dans le sélecteur et l'aide). */
@Composable
fun GuidePreview(g: CompositionGuide, modifier: Modifier = Modifier, ratio: Float = 3f / 2, selected: Boolean = false) {
    val outline = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Canvas(
        modifier
            .aspectRatio(ratio)
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF2B2B2B))
            .border(if (selected) 2.dp else 1.dp, outline, RoundedCornerShape(6.dp)),
    ) {
        drawGuide(g, Rect(Offset.Zero, size), color = Color(0xFFE0B04A), width = 1.4f)
    }
}

/** Sélecteur de mire, de format, d'orientation et de couleur. */
@Composable
fun GuidePickerDialog(
    guide: CompositionGuide,
    aspect: GuideAspect,
    colorIndex: Int,
    onGuide: (CompositionGuide) -> Unit,
    onAspect: (GuideAspect) -> Unit,
    onColor: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mire de composition") },
        text = {
            LazyVerticalGrid(GridCells.Fixed(3), Modifier.heightIn(max = 520.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(CompositionGuide.entries) { g ->
                    Column(Modifier.clickable { onGuide(g) }) {
                        GuidePreview(g, Modifier.fillMaxWidth(), selected = g == guide)
                        Text(g.label, fontSize = 11.sp, textAlign = TextAlign.Center, maxLines = 2,
                            modifier = Modifier.fillMaxWidth().padding(top = 2.dp))
                    }
                }
                item(span = { GridItemSpan(3) }) {
                    Column {
                        Text(guide.tip, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(vertical = 6.dp))
                        Text("Format du cadre", style = MaterialTheme.typography.labelLarge)
                        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(GuideAspect.entries.size) { i ->
                                val a = GuideAspect.entries[i]
                                FilterChip(selected = a == aspect, onClick = { onAspect(a) }, label = { Text(a.label) })
                            }
                        }
                        Text("Couleur", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 4.dp)) {
                            GuideColors.forEachIndexed { i, col ->
                                Canvas(
                                    Modifier
                                        .padding(2.dp)
                                        .clip(RoundedCornerShape(50))
                                        .border(if (i == colorIndex) 3.dp else 1.dp,
                                            if (i == colorIndex) MaterialTheme.colorScheme.primary else Color.Gray, RoundedCornerShape(50))
                                        .clickable { onColor(i) }
                                        .size(36.dp),
                                ) { drawCircle(col, size.minDimension / 2 - 6f) }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
    )
}

/** État des mires, persistant dans les préférences. */
class GuideState(private val prefs: com.decmike.canonremote.data.Prefs) {
    var guide by androidx.compose.runtime.mutableStateOf(
        runCatching { CompositionGuide.valueOf(prefs.guide) }.getOrDefault(CompositionGuide.THIRDS))
        private set
    var aspect by androidx.compose.runtime.mutableStateOf(
        runCatching { GuideAspect.valueOf(prefs.guideAspect) }.getOrDefault(GuideAspect.AUTO))
        private set
    var colorIndex by androidx.compose.runtime.mutableIntStateOf(prefs.guideColor.coerceIn(0, GuideColors.lastIndex))
        private set
    var flipH by androidx.compose.runtime.mutableStateOf(prefs.guideFlipH)
        private set
    var flipV by androidx.compose.runtime.mutableStateOf(prefs.guideFlipV)
        private set
    var pickerOpen by androidx.compose.runtime.mutableStateOf(false)

    fun setGuide(g: CompositionGuide) { guide = g; prefs.guide = g.name }
    fun setAspect(a: GuideAspect) { aspect = a; prefs.guideAspect = a.name }
    fun setColor(i: Int) { colorIndex = i; prefs.guideColor = i }
    /** Fait tourner l'orientation : normal -> miroir H -> miroir H+V -> miroir V. */
    fun rotate() {
        val (h, v) = when (flipH to flipV) {
            false to false -> true to false
            true to false -> true to true
            true to true -> false to true
            else -> false to false
        }
        flipH = h; flipV = v; prefs.guideFlipH = h; prefs.guideFlipV = v
    }
}

@Composable
fun rememberGuideState(): GuideState {
    val prefs = com.decmike.canonremote.data.CameraRepository.prefs
    return androidx.compose.runtime.remember { GuideState(prefs) }
}

/**
 * Superpose la mire sur une image de taille [imageSize] affichée en
 * ContentScale.Fit. [cameraRatio] : format d'image réglé sur l'appareil (mode Auto).
 */
@Composable
fun GuideOverlay(state: GuideState, imageSize: Size, modifier: Modifier = Modifier, cameraRatio: Float? = null) {
    if (state.guide == CompositionGuide.NONE && state.aspect == GuideAspect.AUTO && cameraRatio == null) return
    Canvas(modifier) {
        val img = fitRect(imageSize, size)
        val imgRatio = if (img.height > 0) img.width / img.height else 0f
        val ratio = when {
            state.aspect != GuideAspect.AUTO -> state.aspect.ratio
            cameraRatio != null -> cameraRatio
            else -> imgRatio
        }
        val frame = cropRect(img, ratio)
        val cropped = kotlin.math.abs(ratio - imgRatio) > 0.01f
        drawGuide(state.guide, frame, state.flipH, state.flipV, GuideColors[state.colorIndex], 1.6f, dimOutside = cropped)
    }
}

/** Dialogue de choix de la mire, à placer une fois dans l'écran. */
@Composable
fun GuidePicker(state: GuideState) {
    if (state.pickerOpen) {
        GuidePickerDialog(
            state.guide, state.aspect, state.colorIndex,
            onGuide = state::setGuide, onAspect = state::setAspect, onColor = state::setColor,
            onDismiss = { state.pickerOpen = false },
        )
    }
}
