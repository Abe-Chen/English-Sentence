/*
 * Copyright (c) 2023 Auxio Project
 * DialogImportModels.kt is part of Auxio.
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

import android.net.Uri

/** User supplied configuration for generating a dialog study album from a video source. */
data class DialogImportRequest(
    val videoUri: Uri,
    val subtitleUri: Uri? = null,
    val albumTitle: String? = null,
)

/** Summary of the generated dialog study album. */
data class DialogImportResult(
    val albumTitle: String,
    val trackCount: Int,
    val outputDirectory: java.io.File,
)

/** Exception thrown when dialog import fails. */
class DialogImportException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Progress events emitted while generating dialog study content. */
sealed class DialogImportProgress {
    data object PreparingSources : DialogImportProgress()
    data object ReadingSubtitles : DialogImportProgress()
    data class GeneratingAudio(val total: Int) : DialogImportProgress()
    data class GeneratingTrack(val index: Int, val total: Int, val cue: SubtitleCue) : DialogImportProgress()
    data object WritingAlbumManifest : DialogImportProgress()
    data object Finalizing : DialogImportProgress()
}
