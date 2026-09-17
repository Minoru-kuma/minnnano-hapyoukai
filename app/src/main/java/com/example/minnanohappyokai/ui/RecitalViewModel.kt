package com.example.minnanohappyokai.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.minnanohappyokai.data.ProgramSnapshot
import com.example.minnanohappyokai.data.RecitalRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class RecitalUiState(
    val program: ProgramSnapshot? = null,
    val loaded: Boolean = false,
    val readError: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
)

internal class RecitalViewModel(val repository: RecitalRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(RecitalUiState())
    val state = mutableState.asStateFlow()
    private var observation: Job? = null

    init { retry() }

    fun retry() {
        observation?.cancel()
        mutableState.update { it.copy(readError = false, loaded = false) }
        observation = viewModelScope.launch {
            try {
                repository.observeProgram().collect { snapshot ->
                    mutableState.update { it.copy(program = snapshot, loaded = true, readError = false) }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.update { it.copy(readError = true) }
            }
        }
    }

    fun perform(action: suspend RecitalRepository.() -> Unit) {
        if (mutableState.value.busy) return
        mutableState.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                repository.action()
                mutableState.update { it.copy(busy = false) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.update {
                    it.copy(busy = false, error = "保存できませんでした。入力内容を確認して、もう一度お試しください。")
                }
            }
        }
    }

    fun clearError() { mutableState.update { it.copy(error = null) } }

    class Factory(private val repository: RecitalRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = RecitalViewModel(repository) as T
    }
}
