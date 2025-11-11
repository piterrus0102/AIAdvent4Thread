package ru.piterrus.aiadvent4thread.data.model

import kotlinx.serialization.Serializable

// Результат работы HuggingFace API
sealed class HuggingFaceResult {
    data class Success(
        val text: String,
        val timeTaken: Long,
        val tokensUsed: Int? = null,
        val thinkingContent: String? = null  // Для Qwen3
    ) : HuggingFaceResult()
    data class Error(val message: String) : HuggingFaceResult()
}

// Модели HuggingFace
enum class HFModel(val displayName: String, val modelPath: String, val endpoint: String) {
    STHENO(
        "L3-8B-Stheno",
        "Sao10K/L3-8B-Stheno-v3.2",
        "https://router.huggingface.co/hf-inference/models/Sao10K/L3-8B-Stheno-v3.2"
    ),
    MINIMAX(
        "MiniMax-M2",
        "MiniMaxAI/MiniMax-M2:novita",
        "https://router.huggingface.co/hf-inference/models/MiniMaxAI/MiniMax-M2:novita"
    ),
    QWEN2(
        "Qwen2.5-7B",
        "Qwen/Qwen2.5-7B-Instruct",
        "https://router.huggingface.co/hf-inference/models/Qwen/Qwen2.5-7B-Instruct"
    )
}

// OpenAI-совместимый формат для нового API HuggingFace
@Serializable
data class HFChatCompletionMessage(
    val role: String,
    val content: String
)

@Serializable
data class ChatCompletionRequest(
    val model: String,
    val messages: List<HFChatCompletionMessage>,
    val stream: Boolean = false,
    val max_tokens: Int? = null,
    val temperature: Double? = null,
    val top_p: Double? = null
)

@Serializable
data class ChatChoice(
    val message: HFChatCompletionMessage,
    val finish_reason: String? = null
)

@Serializable
data class ChatCompletionResponse(
    val choices: List<ChatChoice>
)

// Модель сообщения HuggingFace
data class HFChatMessage(
    val text: String,
    val isUser: Boolean,
    val model: HFModel,
    val timeTaken: Long? = null,
    val tokensUsed: Int? = null,
    val thinkingContent: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

