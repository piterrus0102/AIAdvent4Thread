package ru.piterrus.aiadvent4thread

import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

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

class HuggingFaceClient(private val huggingFaceToken: String) {

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

    suspend fun callStheno(prompt: String): HuggingFaceResult {
        return callModelV1(
            modelId = "Sao10K/L3-8B-Stheno-v3.2",
            modelName = "L3-8B-Stheno-v3.2",
            prompt = prompt
        )
    }
    
    suspend fun callMiniMax(prompt: String): HuggingFaceResult {
        return callModelV1(
            modelId = "MiniMaxAI/MiniMax-M2:novita",
            modelName = "MiniMax-M2",
            prompt = prompt
        )
    }
    
    suspend fun callQwen2(prompt: String, enableThinking: Boolean = true): HuggingFaceResult {
        // Формируем запрос с учетом режима thinking
        val formattedPrompt = if (enableThinking) {
            prompt
        } else {
            "$prompt /no_think"
        }
        
        val temperature = if (enableThinking) 0.6 else 0.7
        val topP = if (enableThinking) 0.95 else 0.8
        
        return callModelV1(
            modelId = "Qwen/Qwen2.5-7B-Instruct",
            modelName = "Qwen2.5-7B-Instruct",
            prompt = formattedPrompt,
            temperature = temperature,
            topP = topP,
            parseThinking = enableThinking
        )
    }
    // Новый метод для V1 API (OpenAI-совместимый)
    private suspend fun callModelV1(
        modelId: String,
        modelName: String,
        prompt: String,
        temperature: Double = 0.7,
        topP: Double = 0.9,
        parseThinking: Boolean = false
    ): HuggingFaceResult {
        return try {
            val startTime = System.currentTimeMillis()
            
            val request = ChatCompletionRequest(
                model = modelId,
                messages = listOf(
                    HFChatCompletionMessage(role = "user", content = prompt)
                ),
                stream = false,
                max_tokens = 2000,
                temperature = temperature,
                top_p = topP
            )
            
            val httpResponse: HttpResponse = client.post("https://router.huggingface.co/v1/chat/completions") {
                headers {
                    append(HttpHeaders.Authorization, "Bearer $huggingFaceToken")
                    append(HttpHeaders.ContentType, ContentType.Application.Json)
                }
                setBody(request)
            }
            
            val timeTaken = System.currentTimeMillis() - startTime
            
            when (httpResponse.status.value) {
                200 -> {
                    val responseText = httpResponse.bodyAsText()
                    println("📥 $modelName raw response: $responseText")
                    
                    try {
                        val response: ChatCompletionResponse = jsonParser.decodeFromString(responseText)
                        val fullText = response.choices.firstOrNull()?.message?.content ?: responseText
                        
                        // Парсим thinking content для Qwen, если включен режим
                        val thinkingContent: String?
                        val mainContent: String
                        
                        if (parseThinking) {
                            val thinkPattern = Regex("<think>(.*?)</think>", RegexOption.DOT_MATCHES_ALL)
                            val thinkMatch = thinkPattern.find(fullText)
                            
                            if (thinkMatch != null) {
                                thinkingContent = thinkMatch.groupValues[1].trim()
                                mainContent = fullText.replace(thinkMatch.value, "").trim()
                            } else {
                                thinkingContent = null
                                mainContent = fullText
                            }
                        } else {
                            thinkingContent = null
                            mainContent = fullText
                        }
                        
                        val tokensEstimate = mainContent.split(" ").size
                        
                        HuggingFaceResult.Success(
                            text = mainContent,
                            timeTaken = timeTaken,
                            tokensUsed = tokensEstimate,
                            thinkingContent = thinkingContent
                        )
                    } catch (e: Exception) {
                        val tokensEstimate = responseText.split(" ").size
                        HuggingFaceResult.Success(
                            text = responseText,
                            timeTaken = timeTaken,
                            tokensUsed = tokensEstimate,
                            thinkingContent = null
                        )
                    }
                }
                503 -> {
                    val errorBody = httpResponse.bodyAsText()
                    if (errorBody.contains("loading") || errorBody.contains("Loading")) {
                        HuggingFaceResult.Error("⏳ Модель $modelName загружается. Попробуйте через 20-30 секунд.")
                    } else {
                        HuggingFaceResult.Error("❌ Сервис временно недоступен (503): $errorBody")
                    }
                }
                else -> {
                    val errorBody = httpResponse.bodyAsText()
                    HuggingFaceResult.Error("❌ Ошибка ${httpResponse.status.value} для $modelName:\n$errorBody")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            HuggingFaceResult.Error("❌ Ошибка подключения к $modelName: ${e.message}")
        }
    }
}