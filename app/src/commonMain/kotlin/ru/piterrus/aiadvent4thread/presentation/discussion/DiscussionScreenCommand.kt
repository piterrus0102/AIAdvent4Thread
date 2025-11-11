package ru.piterrus.aiadvent4thread.presentation.discussion

import ru.piterrus.aiadvent4thread.data.model.ExpertRole

sealed interface DiscussionScreenCommand {
    data class NavigateToExpertDetail(val expert: ExpertRole, val expertNumber: Int) : DiscussionScreenCommand
    object NavigateBack : DiscussionScreenCommand
}

