package ru.piterrus.aiadvent4thread.presentation.search

sealed interface SearchResultsScreenIntent {
    data class ShowRawResponseToggled(val show: Boolean) : SearchResultsScreenIntent
    object BackClicked : SearchResultsScreenIntent
}

