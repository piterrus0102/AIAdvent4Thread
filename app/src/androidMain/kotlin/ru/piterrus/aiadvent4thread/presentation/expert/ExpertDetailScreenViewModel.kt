package ru.piterrus.aiadvent4thread.presentation.expert

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.piterrus.aiadvent4thread.data.model.ExpertRole

class ExpertDetailScreenViewModel(
    expert: ExpertRole,
    expertNumber: Int
) : ViewModel() {
    private val _state = MutableStateFlow(ExpertDetailScreenState(
        expert = expert,
        expertNumber = expertNumber
    ))
    val state: StateFlow<ExpertDetailScreenState> = _state.asStateFlow()
    
    private val _commandFlow = MutableSharedFlow<ExpertDetailScreenCommand>(extraBufferCapacity = 1)
    val commandFlow: SharedFlow<ExpertDetailScreenCommand> = _commandFlow.asSharedFlow()
    
    fun intentToAction(intent: ExpertDetailScreenIntent) {
        when (intent) {
            is ExpertDetailScreenIntent.BackClicked -> {
                viewModelScope.launch {
                    _commandFlow.emit(ExpertDetailScreenCommand.NavigateBack)
                }
            }
        }
    }
}

