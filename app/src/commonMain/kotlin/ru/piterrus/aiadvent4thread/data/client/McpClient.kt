package ru.piterrus.aiadvent4thread.data.client

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import ru.piterrus.aiadvent4thread.data.model.McpConnectionRequest
import ru.piterrus.aiadvent4thread.data.model.McpConnectionResponse
import ru.piterrus.aiadvent4thread.data.model.McpToolsResponse

class McpClient(
    private val httpClient: HttpClient
) {
    /**
     * Подключение к MCP серверу через прокси
     */
    suspend fun connect(proxyUrl: String, serverName: String = "filesystem"): Result<McpConnectionResponse> {
        return try {
            val response = httpClient.post("$proxyUrl/connect") {
                contentType(ContentType.Application.Json)
                setBody(McpConnectionRequest(serverName = serverName))
            }
            
            if (response.status.isSuccess()) {
                Result.success(response.body<McpConnectionResponse>())
            } else {
                Result.failure(Exception("Ошибка подключения: ${response.status.description}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Получение списка доступных инструментов
     */
    suspend fun getTools(proxyUrl: String): Result<McpToolsResponse> {
        return try {
            val response = httpClient.get("$proxyUrl/tools")
            
            if (response.status.isSuccess()) {
                Result.success(response.body<McpToolsResponse>())
            } else {
                Result.failure(Exception("Ошибка получения инструментов: ${response.status.description}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Отключение от MCP сервера
     */
    suspend fun disconnect(proxyUrl: String): Result<Boolean> {
        return try {
            val response = httpClient.post("$proxyUrl/disconnect")
            Result.success(response.status.isSuccess())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

