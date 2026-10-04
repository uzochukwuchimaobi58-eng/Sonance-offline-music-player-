package com.sonance.musicplayer.lyrics

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sonance.musicplayer.data.LyricLine
import com.sonance.musicplayer.data.LyricsRepository
import com.sonance.musicplayer.data.LyricsState
import com.sonance.musicplayer.model.Track
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface LyricsUiState {
    object Idle : LyricsUiState
    object Loading : LyricsUiState
    data class Success(
        val lines: List<LyricLine>,
        val isSynced: Boolean,
        val source: String,
        val activeLineIndex: Int = -1
    ) : LyricsUiState
    data class NotAvailable(val message: String = "Lyrics not available") : LyricsUiState
    data class Error(val error: String) : LyricsUiState
}

class LyricsViewModel(
    private val repository: LyricsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<LyricsUiState>(LyricsUiState.Idle)
    val uiState: StateFlow<LyricsUiState> = _uiState.asStateFlow()

    private var currentTrack: Track? = null
    private var fetchJob: Job? = null

    fun loadLyrics(track: Track?) {
        if (track == null) {
            _uiState.value = LyricsUiState.Idle
            currentTrack = null
            return
        }
        if (currentTrack?.id == track.id && _uiState.value is LyricsUiState.Success) {
            return
        }
        currentTrack = track
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            _uiState.value = LyricsUiState.Loading
            try {
                when (val result = repository.load(track)) {
                    is LyricsState.Found -> {
                        _uiState.value = LyricsUiState.Success(
                            lines = result.lines,
                            isSynced = result.synced,
                            source = result.source
                        )
                    }
                    is LyricsState.NotFound -> {
                        _uiState.value = LyricsUiState.NotAvailable("Lyrics not available")
                    }
                    else -> {
                        _uiState.value = LyricsUiState.NotAvailable("Lyrics not available")
                    }
                }
            } catch (e: Exception) {
                _uiState.value = LyricsUiState.Error(e.message ?: "Failed to load lyrics")
            }
        }
    }

    fun updatePlaybackPosition(currentPosMs: Long) {
        val current = _uiState.value
        if (current is LyricsUiState.Success && current.isSynced && current.lines.isNotEmpty()) {
            var activeIndex = -1
            for (i in current.lines.indices) {
                if (currentPosMs >= current.lines[i].timeMs) {
                    activeIndex = i
                } else {
                    break
                }
            }
            if (activeIndex != current.activeLineIndex) {
                _uiState.value = current.copy(activeLineIndex = activeIndex)
            }
        }
    }

    fun retry() {
        val track = currentTrack ?: return
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            _uiState.value = LyricsUiState.Loading
            try {
                when (val result = repository.load(track)) {
                    is LyricsState.Found -> {
                        _uiState.value = LyricsUiState.Success(
                            lines = result.lines,
                            isSynced = result.synced,
                            source = result.source
                        )
                    }
                    else -> {
                        _uiState.value = LyricsUiState.NotAvailable("Lyrics not available")
                    }
                }
            } catch (e: Exception) {
                _uiState.value = LyricsUiState.Error(e.message ?: "Failed to load lyrics")
            }
        }
    }
}
