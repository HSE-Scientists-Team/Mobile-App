package org.matchlab.academic_net.feature.feed.domain.usecase

import kotlinx.coroutines.flow.Flow
import org.matchlab.academic_net.common.model.Collaborator
import org.matchlab.academic_net.feature.feed.domain.FeedRepository
import org.matchlab.academic_net.feature.feed.domain.RecommendedProject
import org.matchlab.academic_net.feature.feed.domain.ViewerNameSource
import org.matchlab.academic_net.utils.suspendRunCatching

/** "Получать рекомендации людей на роль в проекте": the people block of the feed. */
class GetRecommendedPeopleUseCase(private val repository: FeedRepository) {
    suspend operator fun invoke(): Result<List<Collaborator>> =
        suspendRunCatching { repository.getRecommendedPeople() }
}

/** "Рекомендации для вас": the projects block of the feed. */
class GetRecommendedProjectsUseCase(private val repository: FeedRepository) {
    suspend operator fun invoke(): Result<List<RecommendedProject>> =
        suspendRunCatching { repository.getRecommendedProjects() }
}

/** The name of the signed-in user for the avatar in the header of the feed; see [ViewerNameSource.observeName]. */
class ObserveViewerNameUseCase(private val source: ViewerNameSource) {
    operator fun invoke(): Flow<String> = source.observeName()
}
