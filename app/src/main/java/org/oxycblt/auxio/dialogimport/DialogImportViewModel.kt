/*
 * Copyright (c) 2023 Auxio Project
 * DialogImportViewModel.kt is part of Auxio.
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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber as L

@HiltViewModel
class DialogImportViewModel
@Inject
constructor(
    private val importManager: DialogImportManager,
) : ViewModel() {

    private val _state = MutableStateFlow<DialogImportUiState>(DialogImportUiState.Idle)
    val state: StateFlow<DialogImportUiState> = _state.asStateFlow()

    private var importJob: Job? = null
    private var totalTracks: Int = 0
    private var processedTracks: Int = 0

    fun isImporting(): Boolean = importJob?.isActive == true

    fun import(request: DialogImportRequest) {
        if (isImporting()) {
            L.w("Import already running; ignoring new request")
            return
        }

        val job =
            viewModelScope.launch {
                totalTracks = 0
                processedTracks = 0
                _state.value = DialogImportUiState.Running(DialogImportProgress.PreparingSources, 0, 0)

                try {
                    val result =
                        importManager.import(request) { progress ->
                            when (progress) {
                                is DialogImportProgress.GeneratingAudio -> totalTracks = progress.total
                                is DialogImportProgress.GeneratingTrack -> {
                                    processedTracks = progress.index
                                    totalTracks = progress.total
                                }
                                else -> {}
                            }
                            _state.value =
                                DialogImportUiState.Running(progress, processedTracks, totalTracks)
                        }
                    _state.value = DialogImportUiState.Success(result)
                } catch (e: Exception) {
                    L.e(e, "Failed to import dialog album")
                    _state.value = DialogImportUiState.Error(e.message ?: "")
                }
            }
        job.invokeOnCompletion { importJob = null }
        importJob = job
    }

    fun consumeTerminalState() {
        if (_state.value is DialogImportUiState.Success || _state.value is DialogImportUiState.Error) {
            _state.value = DialogImportUiState.Idle
        }
    }
}

sealed class DialogImportUiState {
    data object Idle : DialogImportUiState()
    data class Running(
        val progress: DialogImportProgress,
        val processedTracks: Int,
        val totalTracks: Int,
    ) : DialogImportUiState()
    data class Success(val result: DialogImportResult) : DialogImportUiState()
    data class Error(val message: String) : DialogImportUiState()
}
