package com.decmike.canonremote.jobs

import com.decmike.canonremote.ccapi.CameraControl
import com.decmike.canonremote.ccapi.Exposure
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.min

/** Programmes de prise de vue automatisés. */
sealed class ShootJob {
    abstract val startDelaySec: Int
    abstract val title: String

    /**
     * Intervallomètre / timelapse / poses longues en série.
     * @param count nombre d'images (0 = illimité)
     * @param exposureSec temps de pose imposé (null = réglage actuel de l'appareil).
     *                    Au-delà de 30 s, la pose B (bulb) est utilisée.
     * @param intervalSec pause entre deux poses
     * @param startToStart si vrai, l'intervalle est mesuré d'un début de pose au suivant
     */
    data class Interval(
        val count: Int,
        val exposureSec: Double?,
        val intervalSec: Double,
        override val startDelaySec: Int = 0,
        val startToStart: Boolean = false,
        val af: Boolean = false,
    ) : ShootJob() {
        override val title get() = "Intervallomètre"
    }

    /**
     * Bracketing d'exposition HDR.
     * @param shots nombre d'images par série (3, 5, 7, 9)
     * @param stepEv écart en IL entre deux images
     * @param repeats nombre de séries (HDR timelapse si > 1)
     */
    data class Hdr(
        val shots: Int,
        val stepEv: Double,
        val method: HdrMethod = HdrMethod.AUTO,
        val repeats: Int = 1,
        val intervalSec: Double = 0.0,
        override val startDelaySec: Int = 0,
        val af: Boolean = false,
    ) : ShootJob() {
        override val title get() = "HDR"
    }

    /** Empilement de mise au point (focus stacking). */
    data class FocusStack(
        val shots: Int,
        val stepSize: Int,          // 1 = fin, 2 = moyen, 3 = large
        val stepsPerShot: Int,
        val towardsFar: Boolean = true,
        val settleSec: Double = 1.0,
        override val startDelaySec: Int = 0,
    ) : ShootJob() {
        override val title get() = "Focus stacking"
    }
}

enum class HdrMethod(val label: String) {
    AUTO("Auto (selon le mode)"),
    SHUTTER("Vitesse (mode M)"),
    COMPENSATION("Correction d'exposition"),
}

data class JobProgress(
    val running: Boolean = false,
    val title: String = "",
    val shot: Int = 0,
    val total: Int = 0,          // 0 = illimité
    val phase: String = "",
    val countdownSec: Int? = null,
    val error: String? = null,
    val finished: Boolean = false,
) {
    val fraction: Float? get() = if (total > 0) shot.toFloat() / total else null
}

class JobRunner(
    private val camera: CameraControl,
    private val onProgress: (JobProgress) -> Unit,
) {
    private var progress = JobProgress()

    private fun update(block: JobProgress.() -> JobProgress) {
        progress = progress.block()
        onProgress(progress)
    }

    suspend fun run(job: ShootJob) {
        progress = JobProgress(running = true, title = job.title)
        onProgress(progress)
        try {
            if (job.startDelaySec > 0) countdown(job.startDelaySec.toDouble(), "Départ dans")
            when (job) {
                is ShootJob.Interval -> runInterval(job)
                is ShootJob.Hdr -> runHdr(job)
                is ShootJob.FocusStack -> runFocusStack(job)
            }
            update { copy(running = false, finished = true, phase = "Terminé", countdownSec = null) }
        } catch (e: kotlinx.coroutines.CancellationException) {
            update { copy(running = false, finished = true, phase = "Arrêté", countdownSec = null) }
            throw e
        } catch (e: Exception) {
            update { copy(running = false, finished = true, phase = "Erreur", error = e.message ?: e.toString(), countdownSec = null) }
        }
    }

    // ------------------------------------------------------------------ attente

    private suspend fun countdown(seconds: Double, label: String) {
        var remainingMs = (seconds * 1000).toLong()
        while (remainingMs > 0) {
            update { copy(phase = label, countdownSec = ceil(remainingMs / 1000.0).toInt()) }
            val step = min(remainingMs, 1000L)
            delay(step)
            remainingMs -= step
        }
        update { copy(countdownSec = null) }
    }

    // ------------------------------------------------------------------ pose

    /** Prépare le temps de pose. Renvoie (durée en secondes ou null si inconnue, bulb ?). */
    private suspend fun prepareExposure(exposureSec: Double?): Pair<Double?, Boolean> {
        if (!camera.supportsSettings) {
            // Bluetooth : la vitesse est réglée sur l'appareil ; au-delà de 30 s, pose B
            return exposureSec to (exposureSec != null && exposureSec > 30.0)
        }
        val tv = camera.getSetting("tv")
        if (exposureSec == null) {
            val v = tv?.value
            return if (v == "bulb") error("L'appareil est en pose B : indiquez un temps de pose") else (v?.let { Exposure.tvSeconds(it) } to false)
        }
        val options = tv?.options.orEmpty()
        val closest = Exposure.closestTv(exposureSec, options)
        val closestSec = closest?.let { Exposure.tvSeconds(it) }
        val exact = closestSec != null && abs(Exposure.tvEv(closestSec) - Exposure.tvEv(exposureSec)) < 0.2
        if (exact && exposureSec <= 30.0) {
            if (tv?.value != closest) camera.setSetting("tv", closest!!)
            return closestSec to false
        }
        // pose longue : mode B
        if (tv?.value != "bulb") {
            if ("bulb" in options) camera.setSetting("tv", "bulb")
            else error("Pose de ${Exposure.formatDuration(exposureSec)} : passez l'appareil en mode M ou B pour la pose longue")
        }
        return exposureSec to true
    }

    private suspend fun expose(seconds: Double?, bulb: Boolean, af: Boolean) {
        if (bulb && seconds != null) {
            camera.shutterManual("full_press", af)
            try {
                countdown(seconds, "Pose en cours")
            } finally {
                withContext(NonCancellable) { camera.shutterManual("release", false) }
            }
        } else {
            camera.shoot(af)
            val wait = (seconds ?: 0.0)
            if (wait >= 1.0) countdown(wait, "Pose en cours") else delay((wait * 1000).toLong())
        }
        delay(300) // laisse le temps à l'appareil d'enregistrer
    }

    // ------------------------------------------------------------------ programmes

    private suspend fun runInterval(job: ShootJob.Interval) {
        val (exp, bulb) = prepareExposure(job.exposureSec)
        update { copy(total = job.count) }
        var i = 0
        while (job.count == 0 || i < job.count) {
            i++
            update { copy(shot = i, phase = "Déclenchement") }
            val start = System.currentTimeMillis()
            expose(exp, bulb, job.af)
            if (job.count != 0 && i >= job.count) break
            val wait = if (job.startToStart) {
                job.intervalSec - (System.currentTimeMillis() - start) / 1000.0
            } else job.intervalSec
            if (wait > 0) countdown(wait, "Prochaine pose dans")
        }
    }

    private suspend fun runHdr(job: ShootJob.Hdr) {
        if (!camera.supportsSettings) error("Le HDR nécessite la connexion Wi-Fi (CCAPI). En Bluetooth, activez l'AEB de l'appareil.")
        val mode = camera.getSetting("shootingmodedial")?.value?.lowercase()
        val method = when (job.method) {
            HdrMethod.AUTO -> if (mode == "m" || mode == "bulb") HdrMethod.SHUTTER else HdrMethod.COMPENSATION
            else -> job.method
        }
        val key = if (method == HdrMethod.SHUTTER) "tv" else "exposure"
        val setting = camera.getSetting(key) ?: error("Réglage « $key » indisponible sur cet appareil")
        val base = setting.value ?: error("Valeur « $key » inconnue")
        val options = setting.options.orEmpty()

        // ordre Canon : 0, -1, +1, -2, +2…
        val offsets = Exposure.bracketOffsets(job.shots, job.stepEv).sortedWith(compareBy({ abs(it) }, { it }))
        val values = offsets.map { off ->
            if (method == HdrMethod.SHUTTER) Exposure.shiftTv(base, off, options)
                ?: error("Vitesse « $base » non numérique : choisissez une vitesse en mode M")
            else Exposure.closestCompensation((Exposure.compensationEv(base) ?: 0.0) + off, options)
                ?: error("Correction d'exposition non disponible")
        }
        val total = job.shots * job.repeats
        update { copy(total = total) }
        var n = 0
        try {
            for (r in 1..job.repeats) {
                for ((idx, v) in values.withIndex()) {
                    n++
                    val off = offsets[idx]
                    update { copy(shot = n, phase = "Série $r/${job.repeats} · ${if (off >= 0) "+" else ""}$off IL") }
                    if (v != setting.value || idx > 0) camera.setSetting(key, v)
                    val secs = if (method == HdrMethod.SHUTTER) Exposure.tvSeconds(v) else null
                    expose(secs ?: 0.5, false, job.af && idx == 0)
                }
                if (r < job.repeats && job.intervalSec > 0) countdown(job.intervalSec, "Prochaine série dans")
            }
        } finally {
            withContext(NonCancellable) { runCatching { camera.setSetting(key, base) } }
        }
    }

    private suspend fun runFocusStack(job: ShootJob.FocusStack) {
        if (!camera.supportsSettings) error("Le focus stacking nécessite la connexion Wi-Fi (CCAPI)")
        val dir = if (job.towardsFar) "far" else "near"
        val step = "$dir${job.stepSize.coerceIn(1, 3)}"
        update { copy(total = job.shots) }
        for (i in 1..job.shots) {
            update { copy(shot = i, phase = "Déclenchement") }
            camera.shoot(false)
            delay((job.settleSec * 1000).toLong())
            if (i < job.shots) {
                update { copy(phase = "Déplacement de la mise au point") }
                repeat(job.stepsPerShot) {
                    camera.driveFocus(step)
                    delay(200)
                }
            }
        }
    }

    companion object {
        /** Durée totale estimée en secondes (null si illimitée/inconnue). */
        fun estimate(job: ShootJob): Double? = when (job) {
            is ShootJob.Interval -> if (job.count == 0) null else {
                val e = job.exposureSec ?: 0.0
                val per = if (job.startToStart) maxOf(job.intervalSec, e) else e + job.intervalSec
                job.startDelaySec + per * (job.count - 1) + e
            }
            is ShootJob.Hdr -> job.startDelaySec + job.repeats * job.shots * 1.5 + (job.repeats - 1) * job.intervalSec
            is ShootJob.FocusStack -> job.startDelaySec + job.shots * (job.settleSec + job.stepsPerShot * 0.3)
        }
    }
}
