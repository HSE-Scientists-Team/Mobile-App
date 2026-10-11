package org.matchlab.academic_net.feature.profile.data

import kotlinx.coroutines.flow.Flow
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.feature.profile.domain.AboutDraft
import org.matchlab.academic_net.feature.profile.domain.DocumentDraft
import org.matchlab.academic_net.feature.profile.domain.MyProfile
import org.matchlab.academic_net.feature.profile.domain.MyProfileRepository
import org.matchlab.academic_net.feature.profile.domain.PortfolioWork
import org.matchlab.academic_net.feature.profile.domain.ProfileDocument
import org.matchlab.academic_net.feature.profile.domain.ProfileRepository
import org.matchlab.academic_net.feature.profile.domain.Review
import org.matchlab.academic_net.feature.profile.domain.ReviewId
import org.matchlab.academic_net.feature.profile.domain.UserProfile
import org.matchlab.academic_net.feature.profile.domain.UserReviews
import org.matchlab.academic_net.feature.profile.domain.VerificationResult
import org.matchlab.academic_net.feature.profile.domain.VerificationResultId
import org.matchlab.academic_net.feature.profile.domain.WorkDraft

/**
 * The only repository class of the feature: it implements both interfaces (the screens of other
 * people's profiles need only [ProfileRepository], the viewer's own screens need
 * [MyProfileRepository]) and passes every request to the [ProfileDataSource] as it is.
 */
class ProfileRepositoryImpl(
    private val dataSource: ProfileDataSource,
) : ProfileRepository, MyProfileRepository {
    override suspend fun getProfile(id: UserId): UserProfile? = dataSource.getProfile(id)

    override suspend fun getWorks(id: UserId): List<PortfolioWork>? = dataSource.getWorks(id)

    override suspend fun getReviews(id: UserId): UserReviews? = dataSource.getReviews(id)

    override suspend fun getReview(id: ReviewId): Review? = dataSource.getReview(id)

    override fun observeMyProfile(): Flow<MyProfile> = dataSource.observeMyProfile()

    override suspend fun updateAbout(draft: AboutDraft): MyProfile = dataSource.updateAbout(draft)

    override suspend fun addWork(draft: WorkDraft): PortfolioWork = dataSource.addWork(draft)

    override suspend fun addDocument(draft: DocumentDraft): ProfileDocument = dataSource.addDocument(draft)

    override fun observeVerificationResults(): Flow<List<VerificationResult>> =
        dataSource.observeVerificationResults()

    override suspend fun markResultsSeen(ids: Set<VerificationResultId>) = dataSource.markResultsSeen(ids)
}
