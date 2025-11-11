package ru.piterrus.aiadvent4thread.presentation.discussion

import ru.piterrus.aiadvent4thread.data.model.ExpertRole

sealed interface DiscussionScreenIntent {
    data class TopicChanged(val topic: String) : DiscussionScreenIntent
    object StartDiscussion : DiscussionScreenIntent
    object ResetDiscussion : DiscussionScreenIntent
    data class ExpertClicked(val expert: ExpertRole, val expertNumber: Int) : DiscussionScreenIntent
    object BackClicked : DiscussionScreenIntent
}

