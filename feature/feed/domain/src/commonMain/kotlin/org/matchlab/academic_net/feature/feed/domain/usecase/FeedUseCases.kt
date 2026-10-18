package org.matchlab.academic_net.feature.feed.domain.usecase

import org.matchlab.academic_net.common.model.Collaborator
import org.matchlab.academic_net.feature.feed.domain.FeedRepository
import org.matchlab.academic_net.utils.suspendRunCatching

/** "Получать рекомендации людей на роль в проекте": the people block of the feed. */
class GetRecommendedPeopleUseCase(private val repository: FeedRepository) {
    suspend operator fun invoke(): Result<List<Collaborator>> =
        suspendRunCatching { repository.getRecommendedPeople() }
}
