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
data class YandexGPTFixedResponse(
    val title: String,
    val message: String,
)

// Результат работы sendMessage
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
}

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
    }

    suspend fun sendMessage(
        userMessage: String,
        messageHistory: List<Message> = emptyList(),
        responseMode: ResponseMode = ResponseMode.DEFAULT,
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
                                    text = "Ты должен отвечать только в формате JSON без дополнительного текста. \n" +
                                            "Формат ответа строго такой:\n" +
                                            "\n" +
                                            "{\n" +
                                            "  \"title\": \"название фильма, книги, рецепта, имя, в общем название объекта\",\n" +
                                            "  \"message\": \"подробное объекта\"\n" +
                                            "}\n" +
                                            "\n" +
                                            "Где:\n" +
                                            "- \"title\" — это краткое резюме ответа (1-5 слов),\n" +
                                            "- \"message\" — это основной развернутый текст ответа.\n" +
                                            "\n" +
                                            "Не используй дополнительные поля. Можешь присылать в ответ массив таких объектов если их несколько\n" +
                                            "Не добавляй пояснений, комментариев, markdown, или текста вне JSON.\n" +
                                            "Если тебя спрашивают что-то, всё равно возвращай ответ только в указанном JSON формате."
                                )
                            )
                        }

                        ResponseMode.TASK -> {
                            add(
                                Message(
                                    role = "system",
                                    text = "Ты — модель, которая собирает информацию у пользователя для формирования итогового результата (история, сценарий, ТЗ и т.д.).\n" +
                                            "\n" +
                                            "Твоя задача — задавать по одному уточняющему вопросу за одно сообщение.\n" +
                                            "НЕ ИМЕЙ ПРАВА:\n" +
                                            "- писать ответы за пользователя,\n" +
                                            "- придумывать или дополнять за пользователя,\n" +
                                            "- симулировать диалог вида «Пользователь: … Ассистент: …»,\n" +
                                            "- описывать свои действия в скобках (например: \"(создаёт историю)\", \"(думает)\", \"(формирует вывод)\"),\n" +
                                            "- использовать фразы \"могу представить\", \"хотите представлю?\" и похожие.\n" +
                                            "\n" +
                                            "Когда ты соберёшь достаточно данных, ты скажешь ровно одну фразу:\n" +
                                            "**\"Я собрал(а) все необходимые сведения. Перехожу к созданию результата.\"**\n" +
                                            "\n" +
                                            "После этой фразы ты:\n" +
                                            "1) СРАЗУ пишешь итоговый результат полностью,\n" +
                                            "2) Без вступлений, без пояснений, без описания процесса,\n" +
                                            "3) Без скобок, без режима «как будто»,\n" +
                                            "4) Просто даёшь тот текст, который должен быть результатом.\n" +
                                            "\n" +
                                            "ЕСЛИ ты не уверена, что данных достаточно — ЗАДАЙ ЕЩЁ ОДИН ВОПРОС.\n" +
                                            "Не заканчивай сбор данных раньше времени.\n" +
                                            "\n" +
                                            "В каждом своём сообщении — строго ОДИН вопрос.\n" +
                                            "\n" +
                                            "Начни с вопроса:\n" +
                                            "\"Какой итоговый результат ты хочешь получить?\""
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
                ResponseMode.TASK -> {
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
                    val text = response.result.alternatives.firstOrNull()?.message?.text ?: "Нет ответа от AI"
                    
                    if (responseMode == ResponseMode.FIXED_RESPONSE_ENABLED) {
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
                        ApiResult.Success(MessageResponse.StandardResponse(text))
                    }
                }
                else -> {
                    val errorBody = httpResponse.bodyAsText()
                    ApiResult.Error("❌ Неизвестная ошибка ${httpResponse.status.value}\n\n$errorBody")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            ApiResult.Error("❌ Ошибка подключения: ${e.message}\n\nПроверьте:\n• Интернет соединение\n• Правильность API ключа\n• Folder ID")
        }
    }
}

