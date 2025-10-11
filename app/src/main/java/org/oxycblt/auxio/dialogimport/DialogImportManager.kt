/*
 * Copyright (c) 2023 Auxio Project
 * DialogImportManager.kt is part of Auxio.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.oxycblt.auxio.dialogimport

import android.content.ContentResolver
import android.content.Context
import android.content.ContentValues
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.ReturnCode
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.text.Normalizer
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.text.Charsets
import timber.log.Timber as L

private const val DIALOG_STUDY_ALBUM_ARTIST = "Dialog Study"
private const val DIALOG_STUDY_GENRE = "DialogStudy"

/**
 * Imports a dialog-focused learning album from a video file and optional subtitle file.
 */
@Singleton
class DialogImportManager
@Inject
constructor(
    @ApplicationContext private val context: Context,
) {

    private val parser = SrtSubtitleParser()

    /**
     * Import the provided [request], emitting [DialogImportProgress] via [onProgress].
     */
    suspend fun import(
        request: DialogImportRequest,
        onProgress: (DialogImportProgress) -> Unit = {},
    ): DialogImportResult {
        return withContext(Dispatchers.IO) {
            onProgress(DialogImportProgress.PreparingSources)

            val resolver = context.contentResolver
            val tempVideo = copyToCache(resolver, request.videoUri, "dialog_source")
            val providedSubtitle =
                request.subtitleUri?.let { copyToCache(resolver, it, "dialog_subtitle") }
            var extractedSubtitle: File? = null

            try {
                val albumTitle =
                    request.albumTitle?.takeIf { it.isNotBlank() }
                        ?: resolver
                            .getDisplayName(request.videoUri)
                            ?.substringBeforeLast('.')
                            ?.takeIf { it.isNotBlank() }
                        ?: throw DialogImportException("Unable to determine album title.")

                val subtitleFile =
                    providedSubtitle
                        ?: extractEmbeddedSubtitle(tempVideo).also { extractedSubtitle = it }
                        ?: throw DialogImportException(
                            "The selected video does not contain embedded subtitles and no subtitle file was provided.",
                        )

                onProgress(DialogImportProgress.ReadingSubtitles)

                val parsedCues = subtitleFile.inputStream().use(parser::parse)
                val cues = parsedCues.filter { cue -> cue.endMs > cue.startMs }
                if (cues.isEmpty()) {
                    throw DialogImportException("Subtitle file did not contain any cues.")
                }

                val destination = prepareAlbumDirectory(albumTitle)

                onProgress(DialogImportProgress.GeneratingAudio(cues.size))

                val sortedCues = cues.sortedBy { it.startMs }

                val tracks =
                    generateTracks(
                        videoFile = tempVideo,
                        cues = sortedCues,
                        albumTitle = albumTitle,
                        destination = destination,
                        onProgress = onProgress,
                    )

                onProgress(DialogImportProgress.WritingAlbumManifest)

                val albumJson = writeAlbumManifest(destination, albumTitle, tracks)

                onProgress(DialogImportProgress.Finalizing)

                val audioScanPaths = tracks.filter { it.needsScan }.map { it.file.absolutePath }
                val scanPaths = audioScanPaths + albumJson.absolutePath
                val mimeTypes =
                    audioScanPaths.map { "audio/mp4" } + "application/json"
                if (scanPaths.isNotEmpty()) {
                    MediaScannerConnection.scanFile(
                        context,
                        scanPaths.toTypedArray(),
                        mimeTypes.toTypedArray(),
                        null,
                    )
                }
                MediaScannerConnection.scanFile(
                    context,
                    scanPaths.toTypedArray(),
                    mimeTypes.toTypedArray(),
                    null,
                )

                DialogImportResult(
                    albumTitle = albumTitle,
                    trackCount = tracks.size,
                    outputDirectory = destination.directory,
                )
            } finally {
                tempVideo.deleteSafely()
                providedSubtitle?.deleteSafely()
                extractedSubtitle?.deleteSafely()
            }
        }
    }

    private fun prepareAlbumDirectory(albumTitle: String): AlbumDestination {
        val sanitizedAlbum = sanitize(albumTitle)
        val base =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        val dialogRoot = File(base, "EnglishDialog")
        if (!dialogRoot.exists() && !dialogRoot.mkdirs()) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                throw DialogImportException("Unable to create EnglishDialog music directory.")
            }
        }

        val albumDirectory = File(dialogRoot, sanitizedAlbum)
        if (!albumDirectory.exists() && !albumDirectory.mkdirs()) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                throw DialogImportException("Unable to create album destination directory.")
            }
        }
        val relativePath = "${Environment.DIRECTORY_MUSIC}/EnglishDialog/$sanitizedAlbum"
        return AlbumDestination(albumDirectory, relativePath)
    }

    private fun extractEmbeddedSubtitle(videoFile: File): File? {
        val information = FFprobeKit.getMediaInformation(videoFile.absolutePath).mediaInformation
        val stream = information?.streams?.firstOrNull { stream ->
            stream?.codecType.equals("subtitle", ignoreCase = true)
        }
        val streamIndex = stream?.index ?: return null

        val subtitleFile = File.createTempFile("dialog_embedded", ".srt", context.cacheDir)
        val session =
            FFmpegKit.execute(
                "-y",
                "-i",
                videoFile.absolutePath,
                "-map",
                "0:$streamIndex",
                "-c:s",
                "srt",
                subtitleFile.absolutePath,
                arrayOf(
                    "-y",
                    "-i",
                    videoFile.absolutePath,
                    "-map",
                    "0:$streamIndex",
                    "-c:s",
                    "srt",
                    subtitleFile.absolutePath,
                ),
            )
        if (!ReturnCode.isSuccess(session.returnCode)) {
            L.w(
                "Failed to extract embedded subtitles: %s",
                session.failStackTrace,
            )
            subtitleFile.delete()
            return null
        }
        return subtitleFile
    }

    private fun generateTracks(
        videoFile: File,
        cues: List<SubtitleCue>,
        albumTitle: String,
        destination: AlbumDestination,
        onProgress: (DialogImportProgress) -> Unit,
    ): List<GeneratedTrack> {
        val resolver = context.contentResolver
        val tracks = mutableListOf<GeneratedTrack>()
        val total = cues.size
        cues.forEachIndexed { index, cue ->
            onProgress(DialogImportProgress.GeneratingTrack(index + 1, total, cue))

            val sanitizedTitle = sanitize(cue.text).ifEmpty { "line" }
            val filename = String.format(Locale.US, "%03d_%s.m4a", index + 1, sanitizedTitle)
            val trackFile = File(destination.directory, filename)
            val titleMetadata = cue.text.replace('\n', ' ').trim().take(180)
            val duration = cue.endMs - cue.startMs
            val tempFile = File.createTempFile("dialog_track", ".m4a", context.cacheDir)

            val session =
                FFmpegKit.execute(
            val command =
                arrayOf(
                    "-y",
                    "-i",
                    videoFile.absolutePath,
                    "-ss",
                    formatTimestamp(cue.startMs),
                    "-t",
                    formatTimestamp(duration),
                    "-map",
                    "0:a:0",
                    "-vn",
                    "-ac",
                    "1",
                    "-ar",
                    "16000",
                    "-c:a",
                    "aac",
                    "-b:a",
                    "96k",
                    "-metadata",
                    "album=$albumTitle",
                    "-metadata",
                    "album_artist=$DIALOG_STUDY_ALBUM_ARTIST",
                    "-metadata",
                    "artist=$DIALOG_STUDY_ALBUM_ARTIST",
                    "-metadata",
                    "genre=$DIALOG_STUDY_GENRE",
                    "-metadata",
                    "title=$titleMetadata",
                    "-metadata",
                    "track=${index + 1}",
                    tempFile.absolutePath,
                )

            val session = FFmpegKit.execute(command)
            if (!ReturnCode.isSuccess(session.returnCode)) {
                tempFile.delete()
                throw DialogImportException(
                    buildString {
                        append("Failed to generate dialog track ${index + 1}.")
                        session.failStackTrace?.let { stack ->
                            append(' ')
                            append(stack)
                        }
                    }
                )
            }
            val audioUri =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val contentValues =
                        ContentValues().apply {
                            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                            put(MediaStore.MediaColumns.MIME_TYPE, "audio/mp4")
                            put(MediaStore.MediaColumns.RELATIVE_PATH, destination.relativePath)
                            put(MediaStore.MediaColumns.IS_PENDING, 1)
                            put(MediaStore.Audio.AudioColumns.ALBUM, albumTitle)
                            put(MediaStore.Audio.AudioColumns.ALBUM_ARTIST, DIALOG_STUDY_ALBUM_ARTIST)
                            put(MediaStore.Audio.AudioColumns.ARTIST, DIALOG_STUDY_ALBUM_ARTIST)
                            put(MediaStore.Audio.AudioColumns.TITLE, titleMetadata)
                            put(MediaStore.Audio.AudioColumns.TRACK, index + 1)
                            put(MediaStore.Audio.AudioColumns.DURATION, duration)
                        }
                    val uri =
                        resolver.insert(
                            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                            contentValues,
                        ) ?: run {
                            tempFile.delete()
                            throw DialogImportException(
                                "Failed to reserve storage for dialog track ${index + 1}.",
                            )
                        }
                    try {
                        resolver.openOutputStream(uri, "w")?.use { output ->
                            tempFile.inputStream().use { input -> input.copyTo(output) }
                        } ?: throw DialogImportException(
                            "Unable to open output stream for dialog track ${index + 1}.",
                        )

                        resolver.update(
                            uri,
                            ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                            null,
                            null,
                        )
                    } catch (e: Exception) {
                        resolver.delete(uri, null, null)
                        tempFile.delete()
                        throw DialogImportException(
                            buildString {
                                append("Failed to save dialog track ${index + 1}.")
                                e.message?.let { message ->
                                    append(' ')
                                    append(message)
                                }
                            },
                            e,
                        )
                    }
                    uri
                } else {
                    try {
                        tempFile.copyTo(trackFile, overwrite = true)
                    } catch (e: Exception) {
                        tempFile.delete()
                        throw DialogImportException(
                            buildString {
                                append("Failed to save dialog track ${index + 1}.")
                                e.message?.let { message ->
                                    append(' ')
                                    append(message)
                                }
                            },
                            e,
                        )
                    }
                    Uri.fromFile(trackFile)
                }
            tempFile.delete()
            val needsScan = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
            tracks += GeneratedTrack(trackFile, cue, index + 1, audioUri, needsScan)
        }
        return tracks
    }

    private fun writeAlbumManifest(
        destination: AlbumDestination,
        albumTitle: String,
        tracks: List<GeneratedTrack>,
    ): File {
        val albumJson = JSONObject()
        albumJson.put("album", albumTitle)
        albumJson.put("albumArtist", DIALOG_STUDY_ALBUM_ARTIST)
        albumJson.put("genre", DIALOG_STUDY_GENRE)

        val trackArray = JSONArray()
        tracks.forEach { track ->
            val trackJson = JSONObject()
            trackJson.put("track", track.position)
            trackJson.put("startMs", track.cue.startMs)
            trackJson.put("endMs", track.cue.endMs)
            trackJson.put("title", track.cue.text)
            trackJson.put("path", track.file.name)
            trackArray.put(trackJson)
        }
        albumJson.put("tracks", trackArray)

        val jsonString = albumJson.toString(2)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values =
                ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, "album.json")
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, destination.relativePath)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            val uri =
                resolver.insert(
                    MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                    values,
                ) ?: throw DialogImportException("Unable to create album manifest in media store.")
            try {
                resolver.openOutputStream(uri, "w")?.use { output ->
                    output.write(jsonString.toByteArray(Charsets.UTF_8))
                } ?: throw DialogImportException("Unable to open album manifest output stream.")

                resolver.update(
                    uri,
                    ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                    null,
                    null,
                )
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw DialogImportException("Failed to write album manifest.", e)
            }
        } else {
            val jsonFile = File(destination.directory, "album.json")
            jsonFile.parentFile?.takeIf { it?.exists() == false }?.mkdirs()
            jsonFile.writer().use { writer -> writer.write(jsonString) }
        }

        return File(destination.directory, "album.json")
    }

    private fun copyToCache(
        resolver: ContentResolver,
        uri: Uri,
        prefix: String,
    ): File {
        val extension = resolver.getExtension(uri)
        val tempFile = File.createTempFile(prefix, extension, context.cacheDir)
        resolver.openInputStream(uri)?.use { input ->
            tempFile.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: throw DialogImportException("Unable to open source uri: $uri")
        return tempFile
    }

    private fun sanitize(input: String): String {
        val normalized = Normalizer.normalize(input, Normalizer.Form.NFD)
        val asciiOnly = normalized.replace("[^a-zA-Z0-9]+".toRegex(), "_")
        val trimmed = asciiOnly.trim('_')
        return trimmed.ifEmpty { "Album" }.take(64)
    }

    private fun formatTimestamp(timeMs: Long): String {
        val hours = timeMs / 3_600_000
        val minutes = (timeMs % 3_600_000) / 60_000
        val seconds = (timeMs % 60_000) / 1000
        val millis = timeMs % 1000
        return String.format(Locale.US, "%02d:%02d:%02d.%03d", hours, minutes, seconds, millis)
    }

    private fun File.deleteSafely() {
        runCatching { delete() }
    }
}

private fun ContentResolver.getDisplayName(uri: Uri): String? {
    val projection = arrayOf(android.provider.OpenableColumns.DISPLAY_NAME)
    val cursor = query(uri, projection, null, null, null) ?: return null
    cursor.use {
        if (!it.moveToFirst()) return null
        val index = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        if (index == -1) return null
        return it.getString(index)
    }
}

private fun ContentResolver.getExtension(uri: Uri): String {
    val type = getType(uri)
    return when {
        type == null -> {
            val name = getDisplayName(uri) ?: ""
            val dotIndex = name.lastIndexOf('.')
            if (dotIndex != -1 && dotIndex < name.length - 1) {
                name.substring(dotIndex)
            } else {
                ""
            }
        }
        type.contains("mp4") -> ".mp4"
        type.contains("mkv") -> ".mkv"
        type.contains("srt") -> ".srt"
        type.contains("webm") -> ".webm"
        type.contains("x-matroska") -> ".mkv"
        else -> ""
    }
}

private data class GeneratedTrack(
    val file: File,
    val cue: SubtitleCue,
    val position: Int,
    val uri: Uri,
    val needsScan: Boolean,
)

private data class AlbumDestination(
    val directory: File,
    val relativePath: String,
)
