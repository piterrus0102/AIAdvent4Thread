package ru.piterrus.aiadvent4thread.presentation.chat

import ru.piterrus.aiadvent4thread.data.model.TemperatureResult

sealed interface ChatScreenCommand {
    data class NavigateToSearchResults(val messageId: Long) : ChatScreenCommand
    data class NavigateToTemperatureDetail(val temperatureResult: TemperatureResult) : ChatScreenCommand
    data class ShowCopiedSnackbar(val text: String) : ChatScreenCommand
    object NavigateToStart : ChatScreenCommand
}

