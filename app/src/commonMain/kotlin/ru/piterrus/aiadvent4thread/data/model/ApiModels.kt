package ru.piterrus.aiadvent4thread.data.model

import kotlinx.serialization.Serializable

// Результат работы API
sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Error(val message: String) : ApiResult<Nothing>()
}

// Типы ответов от сервера
sealed class MessageResponse {
    data class StandardResponse(val text: String) : MessageResponse()
    data class FixedResponse(
        val results: List<YandexGPTFixedResponse>,
        val rawText: String
    ) : MessageResponse()
    data class TemperatureComparisonResponse(
        val results: List<TemperatureResult>
    ) : MessageResponse()
}

@Serializable
data class YandexGPTFixedResponse(
    val title: String,
    val message: String,
)

data class TemperatureResult(
    val temperature: Double,
    val text: String,
    val shortQuery: String
)

// Модель сообщения чата
data class ChatMessage(
    val id: Long = 0,
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val responseMode: ResponseMode = ResponseMode.DEFAULT,
    val rawResponse: String? = null,
    val temperatureResults: List<TemperatureResult>? = null
)

// Режимы ответа
enum class ResponseMode(val value: Int) {
    DEFAULT(0),
    FIXED_RESPONSE_ENABLED(1),
    TASK(2),
    TEMPERATURE_COMPARISON(3);
    
    companion object {
        fun fromInt(value: Int): ResponseMode {
            return entries.find { it.value == value } ?: DEFAULT
        }
    }
}

