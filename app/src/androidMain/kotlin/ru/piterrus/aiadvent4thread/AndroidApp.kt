package ru.piterrus.aiadvent4thread

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import ru.piterrus.aiadvent4thread.data.model.*
import ru.piterrus.aiadvent4thread.presentation.chat.*
import ru.piterrus.aiadvent4thread.presentation.discussion.*
import ru.piterrus.aiadvent4thread.presentation.expert.*
import ru.piterrus.aiadvent4thread.presentation.huggingface.*
import ru.piterrus.aiadvent4thread.presentation.search.*
import ru.piterrus.aiadvent4thread.presentation.start.*
import ru.piterrus.aiadvent4thread.presentation.temperature.*

@Composable
actual fun App(
    defaultApiKey: String,
    defaultFolderId: String,
    defaultHuggingFaceToken: String
) {
    // Навигация
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Start) }
    
    when (val screen = currentScreen) {
        is Screen.Start -> {
            val viewModel: StartScreenViewModel = koinViewModel()
            
            // Подписываемся на команды
            LaunchedEffect(viewModel) {
                viewModel.commandFlow.collect { command ->
                    when (command) {
                        is StartScreenCommand.NavigateToChat -> {
                            currentScreen = Screen.Chat(command.mode)
                        }
                        is StartScreenCommand.NavigateToDiscussion -> {
                            currentScreen = Screen.Discussion
                        }
                        is StartScreenCommand.NavigateToHuggingFace -> {
                            currentScreen = Screen.HuggingFace
                        }
                    }
                }
            }
            
            val state by viewModel.state.collectAsState()
            StartScreen(
                state = state,
                onIntent = viewModel::intentToAction
            )
        }
        
        is Screen.Chat -> {
            val viewModel: ChatScreenViewModel = koinViewModel(
                key = "chat_${screen.responseMode}"
            ) {
                parametersOf(screen.responseMode)
            }
            
            val snackbarHostState = remember { SnackbarHostState() }
            
            // Подписываемся на команды
            LaunchedEffect(viewModel) {
                viewModel.commandFlow.collect { command ->
                    when (command) {
                        is ChatScreenCommand.NavigateToSearchResults -> {
                            currentScreen = Screen.SearchResults(command.messageId)
                        }
                        is ChatScreenCommand.NavigateToTemperatureDetail -> {
                            currentScreen = Screen.TemperatureDetail(command.temperatureResult)
                        }
                        is ChatScreenCommand.NavigateToStart -> {
                            currentScreen = Screen.Start
                        }
                        is ChatScreenCommand.ShowCopiedSnackbar -> {
                            snackbarHostState.showSnackbar(
                                message = command.text,
                                duration = SnackbarDuration.Short
                            )
                        }
                    }
                }
            }
            
            val state by viewModel.state.collectAsState()
            ChatScreen(
                state = state,
                onIntent = viewModel::intentToAction,
                snackbarHostState = snackbarHostState
            )
        }
        
        is Screen.HuggingFace -> {
            val viewModel: HuggingFaceScreenViewModel = koinViewModel()
            
            // Подписываемся на команды
            LaunchedEffect(viewModel) {
                viewModel.commandFlow.collect { command ->
                    when (command) {
                        is HuggingFaceScreenCommand.NavigateBack -> {
                            currentScreen = Screen.Start
                        }
                    }
                }
            }
            
            val state by viewModel.state.collectAsState()
            HuggingFaceScreen(
                state = state,
                onIntent = viewModel::intentToAction
            )
        }
        
        is Screen.Discussion -> {
            val viewModel: DiscussionScreenViewModel = koinViewModel()
            
            // Подписываемся на команды
            LaunchedEffect(viewModel) {
                viewModel.commandFlow.collect { command ->
                    when (command) {
                        is DiscussionScreenCommand.NavigateToExpertDetail -> {
                            currentScreen = Screen.ExpertDetail(command.expert, command.expertNumber)
                        }
                        is DiscussionScreenCommand.NavigateBack -> {
                            currentScreen = Screen.Start
                        }
                    }
                }
            }
            
            val state by viewModel.state.collectAsState()
            DiscussionScreen(
                state = state,
                onIntent = viewModel::intentToAction
            )
        }
        
        is Screen.SearchResults -> {
            val viewModel: SearchResultsScreenViewModel = koinViewModel(
                key = "search_${screen.messageId}"
            ) {
                parametersOf(screen.messageId)
            }
            
            // Подписываемся на команды
            LaunchedEffect(viewModel) {
                viewModel.commandFlow.collect { command ->
                    when (command) {
                        is SearchResultsScreenCommand.NavigateBack -> {
                            // Возвращаемся на Chat экран с сохраненным режимом
                            currentScreen = Screen.Chat((currentScreen as? Screen.Chat)?.responseMode ?: ResponseMode.DEFAULT)
                        }
                    }
                }
            }
            
            val state by viewModel.state.collectAsState()
            SearchResultsScreen(
                state = state,
                onIntent = viewModel::intentToAction
            )
        }
        
        is Screen.TemperatureDetail -> {
            val viewModel: TemperatureDetailScreenViewModel = koinViewModel(
                key = "temp_${screen.temperatureResult.hashCode()}"
            ) {
                parametersOf(screen.temperatureResult)
            }
            
            // Подписываемся на команды
            LaunchedEffect(viewModel) {
                viewModel.commandFlow.collect { command ->
                    when (command) {
                        is TemperatureDetailScreenCommand.NavigateBack -> {
                            currentScreen = Screen.Chat(ResponseMode.TEMPERATURE_COMPARISON)
                        }
                    }
                }
            }
            
            val state by viewModel.state.collectAsState()
            TemperatureDetailScreen(
                state = state,
                onIntent = viewModel::intentToAction
            )
        }
        
        is Screen.ExpertDetail -> {
            val viewModel: ExpertDetailScreenViewModel = koinViewModel(
                key = "expert_${screen.expert}_${screen.expertNumber}"
            ) {
                parametersOf(screen.expert, screen.expertNumber)
            }
            
            // Подписываемся на команды
            LaunchedEffect(viewModel) {
                viewModel.commandFlow.collect { command ->
                    when (command) {
                        is ExpertDetailScreenCommand.NavigateBack -> {
                            currentScreen = Screen.Discussion
                        }
                    }
                }
            }
            
            val state by viewModel.state.collectAsState()
            ExpertDetailScreen(
                state = state,
                onIntent = viewModel::intentToAction
            )
        }
    }
}

// Sealed class для навигации
sealed class Screen {
    object Start : Screen()
    data class Chat(val responseMode: ResponseMode = ResponseMode.DEFAULT) : Screen()
    data class SearchResults(val messageId: Long) : Screen()
    object Discussion : Screen()
    data class ExpertDetail(val expert: ExpertRole, val expertNumber: Int) : Screen()
    data class TemperatureDetail(val temperatureResult: TemperatureResult) : Screen()
    object HuggingFace : Screen()
}
