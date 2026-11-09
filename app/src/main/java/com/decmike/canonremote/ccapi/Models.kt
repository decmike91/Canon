package com.decmike.canonremote.ccapi

/** Plage numérique (ex. température de couleur : min 2500, max 10000, pas 100). */
data class SettingRange(val min: Int, val max: Int, val step: Int)

/**
 * Un réglage de prise de vue tel que renvoyé par GET /shooting/settings.
 *  - [value]   : la valeur lisible (chaîne) ou null
 *  - [options] : liste de valeurs possibles si le réglage est une liste
 *  - [range]   : plage si le réglage est numérique
 *  - [subOptions] : pour les réglages composés (ex. qualité d'image raw + jpeg)
 *  - [subValues]  : valeurs actuelles des sous-champs
 */
data class CameraSetting(
    val key: String,
    val value: String?,
    val options: List<String>? = null,
    val range: SettingRange? = null,
    val subOptions: Map<String, List<String>>? = null,
    val subValues: Map<String, String>? = null,
) {
    val editable: Boolean
        get() = (options != null && options.size > 1) || range != null || !subOptions.isNullOrEmpty()

    val displayValue: String
        get() = when {
            subValues != null -> subValues.entries.joinToString(" · ") { "${it.key}: ${it.value}" }
            value != null -> Exposure.pretty(key, value)
            else -> "—"
        }
}

data class DeviceInfo(
    val manufacturer: String = "",
    val productName: String = "",
    val serialNumber: String = "",
    val firmware: String = "",
    val mac: String = "",
)

data class StorageInfo(
    val name: String,
    val maxSize: Long,
    val freeSize: Long,
    val contents: Int,
)

data class CameraStatus(
    val battery: String? = null,
    val batteryLevel: Int? = null,
    val lens: String? = null,
    val temperature: String? = null,
    val storages: List<StorageInfo> = emptyList(),
)

/** Un fichier présent sur la carte mémoire. */
data class CameraFile(val url: String) {
    val name: String get() = url.substringAfterLast('/')
    val folder: String get() = url.substringBeforeLast('/').substringAfterLast('/')
    val extension: String get() = name.substringAfterLast('.', "").uppercase()
    val isRaw: Boolean get() = extension in setOf("CR2", "CR3", "CRW")
    val isVideo: Boolean get() = extension in setOf("MP4", "MOV")
    val isJpegLike: Boolean get() = extension in setOf("JPG", "JPEG", "HEIF", "HIF")
    val thumbnailUrl: String get() = "$url?kind=thumbnail"
    val displayUrl: String get() = "$url?kind=display"
    val infoUrl: String get() = "$url?kind=info"
}

class CcapiException(val code: Int, message: String) : Exception(message) {
    val busy: Boolean get() = code == 503
}

/** Abstraction minimale utilisée par les séquences (testable sans appareil). */
interface CameraControl {
    /** Faux pour la télécommande Bluetooth, qui ne peut ni lire ni modifier les réglages. */
    val supportsSettings: Boolean get() = true
    suspend fun getSetting(key: String): CameraSetting?
    suspend fun setSetting(key: String, value: String)
    suspend fun shoot(af: Boolean)
    suspend fun shutterManual(action: String, af: Boolean)
    suspend fun driveFocus(step: String)
}
