package ru.piterrus.aiadvent4thread.presentation.temperature

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.piterrus.aiadvent4thread.data.model.TemperatureResult

class TemperatureDetailScreenViewModel(
    temperatureResult: TemperatureResult
) : ViewModel() {
    private val _state = MutableStateFlow(TemperatureDetailScreenState(
        temperatureResult = temperatureResult
    ))
    val state: StateFlow<TemperatureDetailScreenState> = _state.asStateFlow()
    
    private val _commandFlow = MutableSharedFlow<TemperatureDetailScreenCommand>(extraBufferCapacity = 1)
    val commandFlow: SharedFlow<TemperatureDetailScreenCommand> = _commandFlow.asSharedFlow()
    
    fun intentToAction(intent: TemperatureDetailScreenIntent) {
        when (intent) {
            is TemperatureDetailScreenIntent.BackClicked -> {
                viewModelScope.launch {
                    _commandFlow.emit(TemperatureDetailScreenCommand.NavigateBack)
                }
            }
        }
    }
}

