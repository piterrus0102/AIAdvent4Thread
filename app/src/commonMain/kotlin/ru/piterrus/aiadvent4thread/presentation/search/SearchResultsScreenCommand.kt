package ru.piterrus.aiadvent4thread.presentation.search

sealed interface SearchResultsScreenCommand {
    object NavigateBack : SearchResultsScreenCommand
}

