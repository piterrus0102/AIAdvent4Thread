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

@Serializable
data class SyncMessage(
    val messageId: String,
    val text: String,
    val isUser: Boolean,
    val timestamp: Long,
    val modelName: String? = null,
    val toolUsed: String? = null,
    val toolResult: String? = null,
    val screenType: String? = null
)

@Serializable
data class SyncMessagesRequest(
    val messages: List<SyncMessage>
)

@Serializable
data class SyncMessagesResponse(
    val success: Boolean,
    val synced: Int? = null,
    val error: String? = null
)

@Serializable
data class MCPModeRequest(
    val useGitHub: Boolean,
    val githubToken: String? = null
)

@Serializable
data class MCPModeResponse(
    val success: Boolean,
    val mode: String? = null,
    val error: String? = null
)

@Serializable
data class MessagesResponse(
    val success: Boolean,
    val messages: List<SyncMessage>? = null,
    val count: Int? = null,
    val error: String? = null
)

@Serializable
data class ClearMessagesResponse(
    val success: Boolean,
    val deleted: Int? = null,
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

    /**
     * Синхронизировать сообщения на сервер
     */
    suspend fun syncMessages(messages: List<SyncMessage>): Result<SyncMessagesResponse> {
        return try {
            println("[LocalServerClient] Синхронизация ${messages.size} сообщений на сервер")
            
            val response: HttpResponse = client.post("$baseUrl/api/sync-messages") {
                contentType(ContentType.Application.Json)
                setBody(SyncMessagesRequest(messages = messages))
            }

            when (response.status.value) {
                200 -> {
                    val syncResponse: SyncMessagesResponse = response.body()
                    println("[LocalServerClient] ✅ Синхронизировано сообщений: ${syncResponse.synced}")
                    Result.success(syncResponse)
                }
                else -> {
                    val errorText = response.bodyAsText()
                    println("[LocalServerClient] ❌ Ошибка синхронизации: $errorText")
                    Result.failure(Exception("Sync error: ${response.status.value}"))
                }
            }
        } catch (e: Exception) {
            println("[LocalServerClient] ❌ Исключение при синхронизации: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Переключить режим MCP (GitHub/локальный)
     */
    suspend fun setMCPMode(
        useGitHub: Boolean, 
        githubToken: String? = null
    ): Result<MCPModeResponse> {
        return try {
            println("[LocalServerClient] Переключение режима MCP: ${if (useGitHub) "GitHub" else "Локальный"}")
            
            val response: HttpResponse = client.post("$baseUrl/api/mcp-mode") {
                contentType(ContentType.Application.Json)
                setBody(MCPModeRequest(
                    useGitHub = useGitHub, 
                    githubToken = githubToken
                ))
            }

            when (response.status.value) {
                200 -> {
                    val modeResponse: MCPModeResponse = response.body()
                    println("[LocalServerClient] ✅ Режим MCP переключен: ${modeResponse.mode}")
                    Result.success(modeResponse)
                }
                else -> {
                    val errorText = response.bodyAsText()
                    println("[LocalServerClient] ❌ Ошибка переключения режима: $errorText")
                    Result.failure(Exception("MCP mode error: ${response.status.value}"))
                }
            }
        } catch (e: Exception) {
            println("[LocalServerClient] ❌ Исключение при переключении режима: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Получить сообщения из БД сервера
     */
    suspend fun getMessages(limit: Int = 100, screenType: String? = null): Result<MessagesResponse> {
        return try {
            val url = buildString {
                append("$baseUrl/api/messages?limit=$limit")
                if (screenType != null) {
                    append("&screenType=$screenType")
                }
            }
            
            println("[LocalServerClient] Запрос сообщений с сервера (limit: $limit, type: ${screenType ?: "all"})")
            
            val response: HttpResponse = client.get(url)

            when (response.status.value) {
                200 -> {
                    val messagesResponse: MessagesResponse = response.body()
                    println("[LocalServerClient] ✅ Получено сообщений: ${messagesResponse.count}")
                    Result.success(messagesResponse)
                }
                else -> {
                    val errorText = response.bodyAsText()
                    println("[LocalServerClient] ❌ Ошибка получения сообщений: $errorText")
                    Result.failure(Exception("Get messages error: ${response.status.value}"))
                }
            }
        } catch (e: Exception) {
            println("[LocalServerClient] ❌ Исключение при получении сообщений: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Очистить историю сообщений на сервере
     */
    suspend fun clearMessages(screenType: String? = "server_chat"): Result<ClearMessagesResponse> {
        return try {
            val url = buildString {
                append("$baseUrl/api/messages/clear")
                if (screenType != null) {
                    append("?screenType=$screenType")
                }
            }
            
            println("[LocalServerClient] Очистка истории сообщений (type: ${screenType ?: "all"})")
            
            val response: HttpResponse = client.delete(url)

            when (response.status.value) {
                200 -> {
                    val clearResponse: ClearMessagesResponse = response.body()
                    println("[LocalServerClient] ✅ Удалено сообщений: ${clearResponse.deleted}")
                    Result.success(clearResponse)
                }
                else -> {
                    val errorText = response.bodyAsText()
                    println("[LocalServerClient] ❌ Ошибка очистки сообщений: $errorText")
                    Result.failure(Exception("Clear messages error: ${response.status.value}"))
                }
            }
        } catch (e: Exception) {
            println("[LocalServerClient] ❌ Исключение при очистке сообщений: ${e.message}")
            Result.failure(e)
        }
    }
}

