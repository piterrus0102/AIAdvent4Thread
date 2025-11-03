package ru.piterrus.aiadvent4thread

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class YandexGPTRequest(
    val modelUri: String,
    val completionOptions: CompletionOptions,
    val messages: List<Message>
)

@Serializable
data class CompletionOptions(
    val stream: Boolean = false,
    val temperature: Double = 0.6,
    val maxTokens: Int = 2000
)

@Serializable
data class Message(
    val role: String,
    val text: String
)

@Serializable
data class YandexGPTResponse(
    val result: Result
)

@Serializable
data class Result(
    val alternatives: List<Alternative>,
    val usage: Usage,
    val modelVersion: String
)

@Serializable
data class Alternative(
    val message: Message,
    val status: String
)

@Serializable
data class Usage(
    val inputTextTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int
)

@Serializable
data class YandexGPTError(
    val code: Int? = null,
    val message: String? = null,
    val details: List<ErrorDetail>? = null
)

@Serializable
data class ErrorDetail(
    val type: String? = null,
    val message: String? = null
)

class YandexGPTClient(
    private val apiKey: String,
    private val catalogId: String
) {
    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
                prettyPrint = true
                encodeDefaults = true  // ВАЖНО! Включаем поля с default значениями
            })
        }
        install(Logging) {
            logger = Logger.DEFAULT
            level = LogLevel.INFO
        }
    }

    suspend fun sendMessage(
        userMessage: String,
        messageHistory: List<Message> = emptyList()
    ): String {
        return try {
            // Создаем полную историю сообщений
            val allMessages = buildList {
                // 1. Системный промпт (если еще не добавлен)
                if (messageHistory.firstOrNull()?.role != "system") {
                    add(Message(
                        role = "system",
                        text = "Ты полезный AI-ассистент. Отвечай на русском языке."
                    ))
                }
                
                // 2. Вся предыдущая история
                addAll(messageHistory)
                
                // 3. Новое сообщение пользователя
                add(Message(
                    role = "user",
                    text = userMessage
                ))
            }
            
            val request = YandexGPTRequest(
                modelUri = "gpt://$catalogId/yandexgpt-lite/latest",
                completionOptions = CompletionOptions(
                    stream = false,
                    temperature = 0.6,
                    maxTokens = 2000
                ),
                messages = allMessages
            )

            val httpResponse: HttpResponse = client.post("https://llm.api.cloud.yandex.net/foundationModels/v1/completion") {
                contentType(ContentType.Application.Json)
                header("Authorization", "Api-Key $apiKey")
                header("x-folder-id", catalogId)
                setBody(request)
            }
            
            // Проверяем статус код
            when (httpResponse.status.value) {
                200 -> {
                    val response: YandexGPTResponse = httpResponse.body()
                    response.result.alternatives.firstOrNull()?.message?.text ?: "Нет ответа от AI"
                }
                400 -> {
                    val errorBody = httpResponse.bodyAsText()
                    "❌ Ошибка 400: Неверный запрос.\nПроверьте Folder ID: $catalogId\n\nДетали: $errorBody"
                }
                401 -> {
                    "❌ Ошибка 401: Неверный API ключ.\nПроверьте правильность ключа в local.properties"
                }
                403 -> {
                    "❌ Ошибка 403: Доступ запрещен.\nУбедитесь что у сервисного аккаунта есть роль 'ai.languageModels.user'"
                }
                404 -> {
                    "❌ Ошибка 404: Ресурс не найден.\nПроверьте:\n1. Folder ID: $catalogId\n2. Включен ли сервис YandexGPT в вашем каталоге"
                }
                429 -> {
                    "❌ Ошибка 429: Превышен лимит запросов.\nПодождите немного и попробуйте снова."
                }
                500, 502, 503 -> {
                    "❌ Ошибка ${httpResponse.status.value}: Проблемы на сервере Yandex.\nПопробуйте позже."
                }
                else -> {
                    val errorBody = httpResponse.bodyAsText()
                    "❌ Неизвестная ошибка ${httpResponse.status.value}\n\n$errorBody"
                }
            }
        } catch (e: Exception) {
            "❌ Ошибка подключения: ${e.message}\n\nПроверьте:\n• Интернет соединение\n• Правильность API ключа\n• Folder ID"
        }
    }

    fun close() {
        client.close()
    }
}

