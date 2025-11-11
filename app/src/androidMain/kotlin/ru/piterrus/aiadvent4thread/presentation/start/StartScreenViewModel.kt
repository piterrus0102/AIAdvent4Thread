package ru.piterrus.aiadvent4thread.presentation.start

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class StartScreenViewModel : ViewModel() {
    private val _state = MutableStateFlow(StartScreenState())
    val state: StateFlow<StartScreenState> = _state.asStateFlow()
    
    private val _commandFlow = MutableSharedFlow<StartScreenCommand>(extraBufferCapacity = 1)
    val commandFlow: SharedFlow<StartScreenCommand> = _commandFlow.asSharedFlow()
    
    fun intentToAction(intent: StartScreenIntent) {
        when (intent) {
            is StartScreenIntent.ModeSelected -> {
                viewModelScope.launch {
                    _commandFlow.emit(StartScreenCommand.NavigateToChat(intent.mode))
                }
            }
            is StartScreenIntent.DiscussionSelected -> {
                viewModelScope.launch {
                    _commandFlow.emit(StartScreenCommand.NavigateToDiscussion)
                }
            }
            is StartScreenIntent.HuggingFaceSelected -> {
                viewModelScope.launch {
                    _commandFlow.emit(StartScreenCommand.NavigateToHuggingFace)
                }
            }
        }
    }
}

