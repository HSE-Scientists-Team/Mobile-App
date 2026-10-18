package org.matchlab.academic_net.feature.profile.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.feature.profile.domain.Review
import org.matchlab.academic_net.feature.profile.domain.ReviewId

/**
 * The reviews this session has seen, by the person they are about: every answer that carries reviews (the preview of a
 * profile, a page of the list) is put here. API-GAP G14: the API has no request for one review, so the screen of a
 * review finds it here. A review that was seen again replaces the old copy.
 *
 * Kept for the life of the repository, which is one session: reviews do not change, and the next account gets a
 * repository of its own.
 */
internal class ReviewsCache {
    private val lock = Mutex()
    private val byPerson = mutableMapOf<UserId, MutableMap<ReviewId, Review>>()

    suspend fun addAll(person: UserId, reviews: List<Review>) {
        if (reviews.isEmpty()) return
        lock.withLock {
            val known = byPerson.getOrPut(person) { linkedMapOf() }
            reviews.forEach { known[it.id] = it }
        }
    }

    suspend fun find(person: UserId, id: ReviewId): Review? = lock.withLock { byPerson[person]?.get(id) }
}
