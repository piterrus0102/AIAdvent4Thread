package ru.piterrus.aiadvent4thread.presentation.serverchat

sealed class ServerChatScreenIntent {
    data class InputTextChanged(val text: String) : ServerChatScreenIntent()
    object SendMessageClicked : ServerChatScreenIntent()
    object BackClicked : ServerChatScreenIntent()
    object ScrolledToBottom : ServerChatScreenIntent()
    object CheckConnection : ServerChatScreenIntent()
    data class GitHubToggleChanged(val enabled: Boolean) : ServerChatScreenIntent()
    object GitHubAuthDialogDismissed : ServerChatScreenIntent()
    data class GitHubTokenChanged(val token: String) : ServerChatScreenIntent()
    object GitHubAuthConfirmed : ServerChatScreenIntent()
    object ClearChatClicked : ServerChatScreenIntent()
    data class CopyMessageToClipboard(val text: String) : ServerChatScreenIntent()
}

