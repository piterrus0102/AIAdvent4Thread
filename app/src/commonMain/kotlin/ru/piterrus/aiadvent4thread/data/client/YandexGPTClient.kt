package ru.piterrus.aiadvent4thread.data.client

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import ru.piterrus.aiadvent4thread.data.model.*

class YandexGPTClient(
    private val apiKey: String,
    private val catalogId: String,
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
        install(HttpTimeout) {
            requestTimeoutMillis = 60_000  // 60 секунд
            connectTimeoutMillis = 30_000  // 30 секунд
            socketTimeoutMillis = 60_000   // 60 секунд
        }
    }

    suspend fun sendMessage(
        userMessage: String,
        messageHistory: List<Message> = emptyList(),
        responseMode: ResponseMode = ResponseMode.DEFAULT,
        temperature: Double = 0.6,
    ): ApiResult<MessageResponse> {
        return try {
            // Создаем полную историю сообщений
            val allMessages = buildList {
                // 1. Системный промпт (если еще не добавлен)
                if (messageHistory.firstOrNull()?.role != "system") {
                    when (responseMode) {
                        ResponseMode.DEFAULT -> {
                            // Для default режима ничего не добавляем
                        }
                        ResponseMode.FIXED_RESPONSE_ENABLED -> {
                            add(
                                Message(
                                    role = "system",
                                    text = Prompts.jsonStructurePrompt
                                )
                            )
                        }

                        ResponseMode.TASK -> {
                            add(
                                Message(
                                    role = "system",
                                    text = Prompts.askerPrompt
                                )
                            )
                        }
                        
                        ResponseMode.TEMPERATURE_COMPARISON -> {
                            // Для режима сравнения температур добавляем промпт о четкости
                            add(
                                Message(
                                    role = "system",
                                    text = Prompts.temperatureComparisonPrompt
                                )
                            )
                        }
                    }
                }
                
                // 2. Вся предыдущая история
                addAll(messageHistory)
                
                // 3. Новое сообщение пользователя
                add(Message(
                    role = "user",
                    text = userMessage
                ))
            }
            val modelUri = when (responseMode) {
                ResponseMode.TASK, ResponseMode.TEMPERATURE_COMPARISON -> {
                    "gpt://$catalogId/yandexgpt/latest"
                }
                else -> {
                    "gpt://$catalogId/yandexgpt-lite/latest"
                }
            }
            val request = YandexGPTRequest(
                modelUri = modelUri,
                completionOptions = CompletionOptions(
                    stream = false,
                    temperature = temperature,
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
                    val text = response.result.alternatives.firstOrNull()?.message?.text ?: "Нет ответа от AI"
                    
                    if (responseMode == ResponseMode.FIXED_RESPONSE_ENABLED || responseMode == ResponseMode.TASK) {
                        try {
                            // В режиме FixedResponse или TASK текст ответа содержит JSON
                            // Очищаем от markdown форматирования (```json ... ``` или ``` ... ```)
                            val cleanedText = text
                                .replace("```json", "")
                                .replace("```", "")
                                .trim()
                            
                            // Пытаемся распарсить как массив
                            val results: List<YandexGPTFixedResponse> = try {
                                jsonParser.decodeFromString(cleanedText)
                            } catch (e: Exception) {
                                // Если не массив, пробуем как один объект
                                val singleResult: YandexGPTFixedResponse = jsonParser.decodeFromString(cleanedText)
                                listOf(singleResult)
                            }
                            // Сохраняем сырой текст для отладки
                            ApiResult.Success(MessageResponse.FixedResponse(results, rawText = text))
                        } catch (e: Exception) {
                            ApiResult.Error("❌ Ошибка парсинга JSON: ${e.message}\n\nПолучен текст:\n$text")
                        }
                    } else {
                        ApiResult.Success(
                            MessageResponse.StandardResponse(
                                text = text,
                                inputTextTokens = response.result.usage.inputTextTokens,
                                completionTokens = response.result.usage.completionTokens,
                                totalTokens = response.result.usage.totalTokens
                            )
                        )
                    }
                }
                else -> {
                    val errorBody = httpResponse.bodyAsText()
                    val errorMessage = parseErrorMessage(errorBody, httpResponse.status.value)
                    ApiResult.Error(errorMessage)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            ApiResult.Error("❌ Ошибка подключения: ${e.message}\n\nПроверьте:\n• Интернет соединение\n• Правильность API ключа\n• Folder ID")
        }
    }
    
    /**
     * Парсит сообщение об ошибке из ответа API и извлекает только полезную часть
     */
    private fun parseErrorMessage(errorBody: String, statusCode: Int): String {
        return try {
            val errorResponse: YandexGPTErrorResponse = jsonParser.decodeFromString(errorBody)
            val fullMessage = errorResponse.error.message
            
            // Ищем двоеточие и берем текст после него
            val colonIndex = fullMessage.indexOf(':')
            val cleanMessage = if (colonIndex != -1 && colonIndex < fullMessage.length - 1) {
                fullMessage.substring(colonIndex + 1).trim()
            } else {
                fullMessage
            }
            
            "❌ Ошибка $statusCode: $cleanMessage"
        } catch (e: Exception) {
            // Если не удалось распарсить JSON, возвращаем как есть
            "❌ Ошибка $statusCode\n\n$errorBody"
        }
    }
    
    suspend fun sendMessageWithTemperatureComparison(
        userMessage: String,
        messageHistory: List<Message> = emptyList()
    ): ApiResult<MessageResponse> {
        return try {
            println("🌡️ Начинаем сравнение температур для запроса: $userMessage")
            
            // Создаем короткое описание запроса (первые 50 символов)
            val shortQuery = if (userMessage.length > 50) {
                userMessage.take(50) + "..."
            } else {
                userMessage
            }
            
            val temperatures = listOf(0.0, 0.5, 1.0)
            val results = mutableListOf<TemperatureResult>()
            
            // Последовательно отправляем запросы с разными температурами
            for (temp in temperatures) {
                println("🌡️ Отправляем запрос с температурой $temp...")
                
                val result = sendMessage(
                    userMessage = userMessage,
                    messageHistory = messageHistory,
                    responseMode = ResponseMode.TEMPERATURE_COMPARISON,
                    temperature = temp
                )
                
                when (result) {
                    is ApiResult.Success -> {
                        val response = result.data as? MessageResponse.StandardResponse
                        if (response != null) {
                            println("✅ Получен ответ для температуры $temp")
                            results.add(
                                TemperatureResult(
                                    temperature = temp,
                                    text = response.text,
                                    shortQuery = shortQuery
                                )
                            )
                        }
                    }
                    is ApiResult.Error -> {
                        println("❌ Ошибка для температуры $temp: ${result.message}")
                        // В случае ошибки добавляем результат с сообщением об ошибке
                        results.add(
                            TemperatureResult(
                                temperature = temp,
                                text = "Ошибка: ${result.message}",
                                shortQuery = shortQuery
                            )
                        )
                    }
                }
            }
            
            println("🌡️ Завершено! Получено ${results.size} результатов")
            ApiResult.Success(MessageResponse.TemperatureComparisonResponse(results))
        } catch (e: Exception) {
            println("❌ Критическая ошибка при выполнении запросов: ${e.message}")
            e.printStackTrace()
            ApiResult.Error("❌ Ошибка при выполнении запросов: ${e.message}")
        }
    }
    
    /**
     * Суммаризирует историю сообщений для сжатия диалога.
     * Агент-суммаризатор работает "на чистую" - без памяти, только с system-промптом.
     * Возвращает текст резюме и количество токенов в ответе.
     */
    suspend fun summarizeMessages(messages: List<Message>): ApiResult<Pair<String, Int>> {
        return try {
            println("📝 Начинаем суммаризацию ${messages.size} сообщений...")
            
            // Формируем текст из истории сообщений для суммаризации
            val historyText = buildString {
                appendLine("История сообщений для суммаризации:")
                appendLine()
                messages.forEachIndexed { index, message ->
                    val roleEmoji = when (message.role) {
                        "user" -> "👤"
                        "assistant" -> "🤖"
                        else -> "⚙️"
                    }
                    appendLine("${index + 1}. $roleEmoji ${message.role.uppercase()}: ${message.text}")
                    appendLine()
                }
            }
            
            // Создаем запрос с system-промптом агента-суммаризатора
            val allMessages = listOf(
                Message(role = "system", text = Prompts.messageSummarizerPrompt),
                Message(role = "user", text = historyText)
            )
            
            val request = YandexGPTRequest(
                modelUri = "gpt://$catalogId/yandexgpt/latest",
                completionOptions = CompletionOptions(
                    stream = false,
                    temperature = 0.3,  // Низкая температура для стабильного результата
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
            
            when (httpResponse.status.value) {
                200 -> {
                    val response: YandexGPTResponse = httpResponse.body()
                    val summaryText = response.result.alternatives.firstOrNull()?.message?.text 
                        ?: "Не удалось создать резюме"
                    val completionTokens = response.result.usage.completionTokens
                    
                    println("✅ Суммаризация завершена успешно (токенов: $completionTokens)")
                    ApiResult.Success(Pair(summaryText, completionTokens))
                }
                else -> {
                    val errorBody = httpResponse.bodyAsText()
                    val errorMessage = parseErrorMessage(errorBody, httpResponse.status.value)
                    println("❌ Ошибка суммаризации: $errorMessage")
                    ApiResult.Error(errorMessage)
                }
            }
        } catch (e: Exception) {
            println("❌ Критическая ошибка при суммаризации: ${e.message}")
            e.printStackTrace()
            ApiResult.Error("❌ Ошибка при суммаризации: ${e.message}")
        }
    }
}

