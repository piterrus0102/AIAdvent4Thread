package ru.piterrus.aiadvent4thread.data.model

import kotlinx.serialization.Serializable

@Serializable
data class McpToolsResponse(
    val tools: List<McpToolInfo>
)

@Serializable
data class McpToolInfo(
    val name: String,
    val description: String? = null,
    val inputSchema: McpInputSchema? = null
)

@Serializable
data class McpInputSchema(
    val type: String? = null,
    val properties: Map<String, SchemaProperty>? = null,
    val required: List<String>? = null
)

@Serializable
data class SchemaProperty(
    val type: String? = null,
    val description: String? = null
)

@Serializable
data class McpConnectionRequest(
    val serverName: String = "filesystem"
)

@Serializable
data class McpConnectionResponse(
    val success: Boolean,
    val message: String? = null
)

