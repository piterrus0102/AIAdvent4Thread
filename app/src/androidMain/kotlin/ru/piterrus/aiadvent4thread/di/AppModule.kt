package ru.piterrus.aiadvent4thread.di

import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module
import ru.piterrus.aiadvent4thread.BuildConfig
import ru.piterrus.aiadvent4thread.PreferencesManager
import ru.piterrus.aiadvent4thread.data.client.HuggingFaceClient
import ru.piterrus.aiadvent4thread.data.client.YandexGPTClient
import ru.piterrus.aiadvent4thread.data.model.ResponseMode
import ru.piterrus.aiadvent4thread.data.model.TemperatureResult
import ru.piterrus.aiadvent4thread.data.model.ExpertRole
import ru.piterrus.aiadvent4thread.data.repository.IChatRepository
import ru.piterrus.aiadvent4thread.database.ChatDatabase
import ru.piterrus.aiadvent4thread.database.ChatRepository
import ru.piterrus.aiadvent4thread.presentation.chat.ChatScreenViewModel
import ru.piterrus.aiadvent4thread.presentation.discussion.DiscussionScreenViewModel
import ru.piterrus.aiadvent4thread.presentation.expert.ExpertDetailScreenViewModel
import ru.piterrus.aiadvent4thread.presentation.huggingface.HuggingFaceScreenViewModel
import ru.piterrus.aiadvent4thread.presentation.search.SearchResultsScreenViewModel
import ru.piterrus.aiadvent4thread.presentation.start.StartScreenViewModel
import ru.piterrus.aiadvent4thread.presentation.temperature.TemperatureDetailScreenViewModel

/**
 * Главный модуль приложения с зависимостями
 */
val appModule = module {
    // Data Layer - Clients
    single { 
        YandexGPTClient(
            apiKey = BuildConfig.YANDEX_API_KEY,
            catalogId = BuildConfig.YANDEX_FOLDER_ID
        )
    }
    
    single { 
        HuggingFaceClient(
            huggingFaceToken = BuildConfig.HUGGINGFACE_TOKEN
        )
    }
    
    // Data Layer - Database & Repository
    single { ChatDatabase.getDatabase(get()) }
    
    single<IChatRepository> {
        ChatRepository(
            messageDao = get<ChatDatabase>().chatMessageDao(),
            searchResultDao = get<ChatDatabase>().searchResultDao()
        )
    }
    
    // Android-specific
    single { PreferencesManager(get()) }
    
    // Presentation Layer - ViewModels
    viewModel {
        StartScreenViewModel()
    }
    
    viewModel { (responseMode: ResponseMode) ->
        ChatScreenViewModel(
            gptClient = get(),
            repository = get(),
            initialResponseMode = responseMode
        )
    }
    
    viewModel {
        HuggingFaceScreenViewModel(
            hfClient = get()
        )
    }
    
    viewModel {
        DiscussionScreenViewModel(
            gptClient = get()
        )
    }
    
    viewModel { (messageId: Long) ->
        SearchResultsScreenViewModel(
            messageId = messageId,
            repository = get()
        )
    }
    
    viewModel { (temperatureResult: TemperatureResult) ->
        TemperatureDetailScreenViewModel(
            temperatureResult = temperatureResult
        )
    }
    
    viewModel { (expert: ExpertRole, expertNumber: Int) ->
        ExpertDetailScreenViewModel(
            expert = expert,
            expertNumber = expertNumber
        )
    }
}

