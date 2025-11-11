package ru.piterrus.aiadvent4thread.presentation.search

import ru.piterrus.aiadvent4thread.data.model.YandexGPTFixedResponse

data class SearchResultsScreenState(
    val results: List<YandexGPTFixedResponse> = emptyList(),
    val rawResponse: String? = null,
    val showRawResponse: Boolean = false
)

