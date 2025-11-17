package ru.piterrus.aiadvent4thread.presentation.mcp

sealed interface McpScreenCommand {
    object NavigateBack : McpScreenCommand
}
