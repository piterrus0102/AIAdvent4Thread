package ru.piterrus.aiadvent4thread.presentation.discussion

import ru.piterrus.aiadvent4thread.data.model.ExpertRole

data class DiscussionScreenState(
    val topic: String = "",
    val roles: List<ExpertRole> = emptyList(),
    val summary: String = "",
    val isLoadingRoles: Boolean = false,
    val isLoadingSummary: Boolean = false,
    val errorMessage: String? = null,
    val scrollPosition: Int = 0
)

