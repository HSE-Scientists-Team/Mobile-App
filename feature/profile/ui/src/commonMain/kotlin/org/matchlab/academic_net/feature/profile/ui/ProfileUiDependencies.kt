package org.matchlab.academic_net.feature.profile.ui

import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import org.matchlab.academic_net.feature.profile.domain.usecase.AddDocumentUseCase
import org.matchlab.academic_net.feature.profile.domain.usecase.AddWorkUseCase
import org.matchlab.academic_net.feature.profile.domain.usecase.GetReviewUseCase
import org.matchlab.academic_net.feature.profile.domain.usecase.GetUserProfileUseCase
import org.matchlab.academic_net.feature.profile.domain.usecase.GetUserReviewsUseCase
import org.matchlab.academic_net.feature.profile.domain.usecase.GetUserWorksUseCase
import org.matchlab.academic_net.feature.profile.domain.usecase.MarkResultsSeenUseCase
import org.matchlab.academic_net.feature.profile.domain.usecase.ObserveHasUnseenResultsUseCase
import org.matchlab.academic_net.feature.profile.domain.usecase.ObserveMyProfileUseCase
import org.matchlab.academic_net.feature.profile.domain.usecase.ObserveVerificationResultsUseCase
import org.matchlab.academic_net.feature.profile.domain.usecase.UpdateAboutUseCase
import org.matchlab.academic_net.feature.profile.ui.my.MyProfileVisibility

/**
 * What the screens of the profile feature need from the outside; the app creates it once and
 * hands it to every `…Route` and to [profileTabHasBadge].
 *
 * @param clock and [timeZone] say when a decision of a moderator was made relative to "now" in the
 *  list of results on the viewer's profile
 */
class ProfileUiDependencies(
    val getUserProfile: GetUserProfileUseCase,
    val getUserWorks: GetUserWorksUseCase,
    val getUserReviews: GetUserReviewsUseCase,
    val getReview: GetReviewUseCase,
    val observeMyProfile: ObserveMyProfileUseCase,
    val updateAbout: UpdateAboutUseCase,
    val addWork: AddWorkUseCase,
    val addDocument: AddDocumentUseCase,
    val observeVerificationResults: ObserveVerificationResultsUseCase,
    val observeHasUnseenResults: ObserveHasUnseenResultsUseCase,
    val markResultsSeen: MarkResultsSeenUseCase,
    val clock: Clock = Clock.System,
    val timeZone: () -> TimeZone = { TimeZone.currentSystemDefault() },
) {
    /**
     * Whether the viewer's own profile is on the screen. It is state of the UI, not of the data, and
     * it lives here because the profile screen and the dot on its tab (which is drawn by the
     * bottom navigation, outside of the screen) must see the same value.
     */
    internal val myProfileVisibility: MyProfileVisibility = MyProfileVisibility()
}
