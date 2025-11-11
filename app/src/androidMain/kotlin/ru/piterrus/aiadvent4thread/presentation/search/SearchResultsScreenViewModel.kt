package ru.piterrus.aiadvent4thread.presentation.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.piterrus.aiadvent4thread.data.repository.IChatRepository

class SearchResultsScreenViewModel(
    private val messageId: Long,
    private val repository: IChatRepository
) : ViewModel() {
    private val _state = MutableStateFlow(SearchResultsScreenState())
    val state: StateFlow<SearchResultsScreenState> = _state.asStateFlow()
    
    private val _commandFlow = MutableSharedFlow<SearchResultsScreenCommand>(extraBufferCapacity = 1)
    val commandFlow: SharedFlow<SearchResultsScreenCommand> = _commandFlow.asSharedFlow()
    
    init {
        // Загружаем результаты поиска из репозитория
        viewModelScope.launch {
            repository.getSearchResults(messageId).collect { results ->
                _state.update { it.copy(results = results) }
            }
        }
        
        // Загружаем raw response
        viewModelScope.launch {
            val message = repository.getMessageById(messageId)
            _state.update { it.copy(rawResponse = message?.rawResponse) }
        }
    }
    
    fun intentToAction(intent: SearchResultsScreenIntent) {
        when (intent) {
            is SearchResultsScreenIntent.ShowRawResponseToggled -> {
                _state.update { it.copy(showRawResponse = intent.show) }
            }
            
            is SearchResultsScreenIntent.BackClicked -> {
                viewModelScope.launch {
                    _commandFlow.emit(SearchResultsScreenCommand.NavigateBack)
                }
            }
        }
    }
}

