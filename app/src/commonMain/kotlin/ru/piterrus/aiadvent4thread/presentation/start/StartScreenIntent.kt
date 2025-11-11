package ru.piterrus.aiadvent4thread.presentation.start

import ru.piterrus.aiadvent4thread.data.model.ResponseMode

sealed interface StartScreenIntent {
    data class ModeSelected(val mode: ResponseMode) : StartScreenIntent
    object DiscussionSelected : StartScreenIntent
    object HuggingFaceSelected : StartScreenIntent
}

