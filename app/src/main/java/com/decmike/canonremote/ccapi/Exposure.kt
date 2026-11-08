package com.decmike.canonremote.ccapi

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.roundToInt

/**
 * Outils de conversion des valeurs d'exposition renvoyées par la CCAPI.
 *
 * Formats Canon :
 *  - vitesse (tv)  : "1/8000", "1/125", "0\"5" (0,5 s), "1\"", "1\"3" (1,3 s), "30\"", "bulb"
 *  - correction    : "-3", "-2_2/3", "-0_1/3", "0", "+0_1/2", "+1", "+1_1/3"
 *  - ouverture     : "f1.8", "f5.6"
 *  - iso           : "auto", "100", "25600"
 */
object Exposure {

    /** Durée en secondes d'une valeur de vitesse, ou null si non numérique (bulb, auto…). */
    fun tvSeconds(value: String): Double? {
        val v = value.trim()
        if (v.startsWith("1/")) {
            val d = v.substring(2).toDoubleOrNull() ?: return null
            return if (d > 0) 1.0 / d else null
        }
        // 0"5 -> 0.5, 1"3 -> 1.3, 30" -> 30
        val m = Regex("""^(\d+)"(\d*)$""").find(v) ?: return v.toDoubleOrNull()
        val whole = m.groupValues[1]
        val frac = m.groupValues[2]
        return if (frac.isEmpty()) whole.toDouble() else "$whole.$frac".toDouble()
    }

    /** Valeur de correction d'exposition en IL, ou null. */
    fun compensationEv(value: String): Double? {
        val v = value.trim()
        if (v == "0" || v == "+0" || v == "-0" || v == "±0") return 0.0
        val sign = when {
            v.startsWith("-") -> -1.0
            else -> 1.0
        }
        val body = v.removePrefix("+").removePrefix("-").removePrefix("±")
        val parts = body.split("_")
        val whole = parts[0].toDoubleOrNull() ?: return null
        var frac = 0.0
        if (parts.size > 1) {
            val f = parts[1].split("/")
            if (f.size != 2) return null
            val n = f[0].toDoubleOrNull() ?: return null
            val d = f[1].toDoubleOrNull() ?: return null
            if (d == 0.0) return null
            frac = n / d
        }
        return sign * (whole + frac)
    }

    /** IL relatif d'une durée (log2 secondes). Plus la valeur est grande, plus l'image est claire. */
    fun tvEv(seconds: Double): Double = ln(seconds) / ln(2.0)

    /**
     * Choisit dans [ability] la vitesse dont l'exposition est la plus proche de
     * [base] décalée de [offsetEv] IL (positif = plus lent = plus clair).
     */
    fun shiftTv(base: String, offsetEv: Double, ability: List<String>): String? {
        val baseSec = tvSeconds(base) ?: return null
        val target = tvEv(baseSec) + offsetEv
        return ability
            .mapNotNull { v -> tvSeconds(v)?.let { v to tvEv(it) } }
            .minByOrNull { abs(it.second - target) }
            ?.first
    }

    /** Vitesse de [ability] la plus proche d'une durée en secondes. */
    fun closestTv(seconds: Double, ability: List<String>): String? {
        if (seconds <= 0) return null
        val target = tvEv(seconds)
        return ability
            .mapNotNull { v -> tvSeconds(v)?.let { v to tvEv(it) } }
            .minByOrNull { abs(it.second - target) }
            ?.first
    }

    /** Correction d'exposition de [ability] la plus proche de [ev]. */
    fun closestCompensation(ev: Double, ability: List<String>): String? =
        ability
            .mapNotNull { v -> compensationEv(v)?.let { v to it } }
            .minByOrNull { abs(it.second - ev) }
            ?.first

    /** Décalages IL d'un bracketing centré : n=5, step=1 -> [-2,-1,0,1,2]. */
    fun bracketOffsets(count: Int, stepEv: Double): List<Double> {
        require(count >= 1)
        val half = (count - 1) / 2.0
        return (0 until count).map { i -> ((i - half) * stepEv * 1000).roundToInt() / 1000.0 }
    }

    /** Affichage lisible d'une valeur CCAPI. */
    fun pretty(key: String, value: String): String = when (key) {
        "tv" -> when {
            value == "bulb" -> "BULB"
            value.contains('"') -> value.replace("\"", "″").let { if (it.endsWith("″")) it else it.replace("″", ",") + "″" }
            else -> value
        }
        "av" -> value.replace("f", "f/")
        "exposure" -> value.replace("_", " ")
        "iso" -> if (value == "auto") "ISO AUTO" else "ISO $value"
        else -> value
    }

    /** Durée lisible : 0.004 -> "1/250 s", 75 -> "1 min 15 s". */
    fun formatDuration(seconds: Double): String {
        if (seconds < 1.0 && seconds > 0) return "1/${(1 / seconds).roundToInt()} s"
        val total = seconds.roundToInt()
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return buildString {
            if (h > 0) append("$h h ")
            if (h > 0 || m > 0) append("$m min ")
            append("$s s")
        }.trim()
    }
}
