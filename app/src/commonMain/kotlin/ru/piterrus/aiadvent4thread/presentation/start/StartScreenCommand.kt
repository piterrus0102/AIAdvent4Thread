package ru.piterrus.aiadvent4thread.presentation.start

import ru.piterrus.aiadvent4thread.data.model.ResponseMode

sealed interface StartScreenCommand {
    data class NavigateToChat(val mode: ResponseMode) : StartScreenCommand
    object NavigateToDiscussion : StartScreenCommand
    object NavigateToHuggingFace : StartScreenCommand
}

