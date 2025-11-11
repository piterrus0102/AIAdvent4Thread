package ru.piterrus.aiadvent4thread.presentation.huggingface

import ru.piterrus.aiadvent4thread.data.model.HFChatMessage
import ru.piterrus.aiadvent4thread.data.model.HFModel

data class HuggingFaceScreenState(
    val selectedTabIndex: Int = 0,
    val sthenoMessages: List<HFChatMessage> = emptyList(),
    val miniMaxMessages: List<HFChatMessage> = emptyList(),
    val qwen2Messages: List<HFChatMessage> = emptyList(),
    val sthenoInput: String = "",
    val miniMaxInput: String = "",
    val qwen2Input: String = "",
    val isSthenoLoading: Boolean = false,
    val isMiniMaxLoading: Boolean = false,
    val isQwen2Loading: Boolean = false,
    val qwen2ThinkingMode: Boolean = true
)

