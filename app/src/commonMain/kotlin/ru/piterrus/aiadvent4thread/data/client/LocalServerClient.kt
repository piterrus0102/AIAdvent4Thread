package ru.piterrus.aiadvent4thread.data.client

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
data class ChatMessage(
    val role: String,
    val text: String
)

@Serializable
data class ChatRequest(
    val message: String,
    val history: List<ChatMessage> = emptyList()
)

@Serializable
data class ChatResponse(
    val success: Boolean,
    val message: String? = null,
    val toolUsed: String? = null,
    val toolResult: String? = null,
    val error: String? = null
)

@Serializable
data class MessageCountUpdate(
    val modelName: String,
    val count: Int
)

@Serializable
data class MessageCountResponse(
    val success: Boolean,
    val modelName: String? = null,
    val count: Int? = null,
    val models: Map<String, Int>? = null,
    val error: String? = null
)

class LocalServerClient(
    private val baseUrl: String = "http://10.0.2.2:3001" // Android emulator localhost
) {
    private val jsonParser = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = true
        encodeDefaults = true
    }

    private val client = HttpClient {
        install(ContentNegotiation) {
            json(jsonParser)
        }
        install(Logging) {
            logger = Logger.DEFAULT
            level = LogLevel.INFO
        }
    }

    /**
     * Отправить сообщение в чат (всегда используется YandexGPT)
     */
    suspend fun sendMessage(
        message: String,
        history: List<ChatMessage> = emptyList()
    ): Result<ChatResponse> {
        return try {
            println("[LocalServerClient] Отправка сообщения в YandexGPT: ${message.take(50)}...")
            
            val response: HttpResponse = client.post("$baseUrl/api/chat") {
                contentType(ContentType.Application.Json)
                setBody(ChatRequest(message = message, history = history))
            }

            when (response.status.value) {
                200 -> {
                    val chatResponse: ChatResponse = response.body()
                    println("[LocalServerClient] ✅ Получен ответ от сервера")
                    if (chatResponse.toolUsed != null) {
                        println("[LocalServerClient] 🔧 Использован инструмент: ${chatResponse.toolUsed}")
                    }
                    Result.success(chatResponse)
                }
                else -> {
                    val errorText = response.bodyAsText()
                    println("[LocalServerClient] ❌ Ошибка ${response.status.value}: $errorText")
                    Result.failure(Exception("Server error: ${response.status.value}"))
                }
            }
        } catch (e: Exception) {
            println("[LocalServerClient] ❌ Исключение: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Обновить счетчик сообщений для модели
     */
    suspend fun updateMessageCount(modelName: String, count: Int): Result<Boolean> {
        return try {
            println("[LocalServerClient] Обновление счетчика для $modelName: $count")
            
            val response: HttpResponse = client.post("$baseUrl/api/message-count") {
                contentType(ContentType.Application.Json)
                setBody(MessageCountUpdate(modelName = modelName, count = count))
            }

            when (response.status.value) {
                200 -> {
                    val countResponse: MessageCountResponse = response.body()
                    println("[LocalServerClient] ✅ Счетчик обновлен для ${countResponse.modelName}: ${countResponse.count}")
                    Result.success(countResponse.success)
                }
                else -> {
                    val errorText = response.bodyAsText()
                    println("[LocalServerClient] ❌ Ошибка обновления счетчика: $errorText")
                    Result.failure(Exception("Failed to update count"))
                }
            }
        } catch (e: Exception) {
            println("[LocalServerClient] ❌ Исключение при обновлении счетчика: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Получить счетчик сообщений для модели или всех моделей
     */
    suspend fun getMessageCount(modelName: String? = null): Result<MessageCountResponse> {
        return try {
            val url = if (modelName != null) {
                "$baseUrl/api/message-count?modelName=$modelName"
            } else {
                "$baseUrl/api/message-count"
            }
            
            println("[LocalServerClient] Запрос счетчика${if (modelName != null) " для $modelName" else " для всех моделей"}")
            
            val response: HttpResponse = client.get(url)

            when (response.status.value) {
                200 -> {
                    val countResponse: MessageCountResponse = response.body()
                    if (modelName != null) {
                        println("[LocalServerClient] ✅ Получен счетчик для $modelName: ${countResponse.count}")
                    } else {
                        println("[LocalServerClient] ✅ Получены счетчики: ${countResponse.models}")
                    }
                    Result.success(countResponse)
                }
                else -> {
                    val errorText = response.bodyAsText()
                    println("[LocalServerClient] ❌ Ошибка получения счетчика: $errorText")
                    Result.failure(Exception("Failed to get count"))
                }
            }
        } catch (e: Exception) {
            println("[LocalServerClient] ❌ Исключение при получении счетчика: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Health check сервера
     */
    suspend fun healthCheck(): Result<Boolean> {
        return try {
            val response: HttpResponse = client.get("$baseUrl/health")
            Result.success(response.status.value == 200)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

