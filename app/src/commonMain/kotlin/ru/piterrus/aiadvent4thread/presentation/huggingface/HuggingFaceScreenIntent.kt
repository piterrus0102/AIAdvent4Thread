package ru.piterrus.aiadvent4thread.presentation.huggingface

sealed interface HuggingFaceScreenIntent {
    data class TabSelected(val index: Int) : HuggingFaceScreenIntent
    data class SthenoInputChanged(val input: String) : HuggingFaceScreenIntent
    data class MiniMaxInputChanged(val input: String) : HuggingFaceScreenIntent
    data class Qwen2InputChanged(val input: String) : HuggingFaceScreenIntent
    object SendSthenoMessage : HuggingFaceScreenIntent
    object SendMiniMaxMessage : HuggingFaceScreenIntent
    object SendQwen2Message : HuggingFaceScreenIntent
    object ClearSthenoHistory : HuggingFaceScreenIntent
    object ClearMiniMaxHistory : HuggingFaceScreenIntent
    object ClearQwen2History : HuggingFaceScreenIntent
    data class Qwen2ThinkingModeChanged(val enabled: Boolean) : HuggingFaceScreenIntent
    object BackClicked : HuggingFaceScreenIntent
}

