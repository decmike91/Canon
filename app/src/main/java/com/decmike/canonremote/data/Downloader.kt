package com.decmike.canonremote.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import com.decmike.canonremote.ccapi.CameraFile
import com.decmike.canonremote.ccapi.CcapiClient

/** Enregistre les fichiers de l'appareil dans la galerie du téléphone (dossier CanonRemote). */
object Downloader {

    const val FOLDER = "CanonRemote"

    private fun mime(file: CameraFile) = when (file.extension) {
        "JPG", "JPEG" -> "image/jpeg"
        "HEIF", "HIF" -> "image/heif"
        "MP4" -> "video/mp4"
        "MOV" -> "video/quicktime"
        "CR3" -> "image/x-canon-cr3"
        "CR2" -> "image/x-canon-cr2"
        else -> "application/octet-stream"
    }

    suspend fun save(
        context: Context,
        client: CcapiClient,
        file: CameraFile,
        progress: (Long, Long) -> Unit = { _, _ -> },
    ): Uri {
        val resolver = context.contentResolver
        val (collection, dir) = when {
            file.isJpegLike -> MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) to Environment.DIRECTORY_PICTURES
            file.isVideo -> MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) to Environment.DIRECTORY_MOVIES
            // Les RAW ne sont pas acceptés par la collection Images : dossier Téléchargements
            else -> MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) to Environment.DIRECTORY_DOWNLOADS
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
            put(MediaStore.MediaColumns.MIME_TYPE, if (file.isRaw) "application/octet-stream" else mime(file))
            put(MediaStore.MediaColumns.RELATIVE_PATH, "$dir/$FOLDER")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: error("Impossible de créer le fichier ${file.name}")
        try {
            resolver.openOutputStream(uri)?.use { out -> client.download(file, out, progress) }
                ?: error("Impossible d'écrire ${file.name}")
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            return uri
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
    }
}
