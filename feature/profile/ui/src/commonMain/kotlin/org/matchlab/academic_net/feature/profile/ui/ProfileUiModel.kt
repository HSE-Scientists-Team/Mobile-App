package org.matchlab.academic_net.feature.profile.ui

import androidx.compose.runtime.Immutable
import org.matchlab.academic_net.common.model.ProjectType
import org.matchlab.academic_net.common.model.UserSummary
import org.matchlab.academic_net.feature.profile.domain.PortfolioWork
import org.matchlab.academic_net.feature.profile.domain.ProfileDocument
import org.matchlab.academic_net.feature.profile.domain.Review
import org.matchlab.academic_net.feature.profile.domain.UserProfile
import org.matchlab.academic_net.feature.profile.domain.VerificationOutcome
import org.matchlab.academic_net.feature.profile.domain.VerificationResultId
import org.matchlab.academic_net.feature.profile.domain.VerificationSubject
import org.matchlab.academic_net.utils.ChatListTime

/**
 * A profile page as "09 Профиль коллаборатора" and "10 Мой профиль" show it. It carries no
 * localized text: the sections build their labels from these fields.
 *
 * @property subtitle the line under the name: "Stanford · 3 курс бакалавриата · Computer Science"
 * @property interestTypes the project types the person is interested in
 * @property interests free topics ("Computer Vision")
 * @property works the newest works only, see [hasMoreWorks]
 * @property hasMoreWorks there are more works than [works]: the section has "Все"
 * @property rating average of all reviews, `null` while there are none
 * @property reviewCount the number of all reviews, not only of [reviews]
 * @property reviews the newest reviews only
 * @property canMessage the viewer may start a chat with the person, i.e. it is not their own page
 */
@Immutable
data class ProfileUiModel(
    val user: UserSummary,
    val subtitle: String,
    val about: String,
    val interestTypes: Set<ProjectType>,
    val interests: List<String>,
    val skills: List<String>,
    val documents: List<ProfileDocument>,
    val works: List<PortfolioWork>,
    val hasMoreWorks: Boolean,
    val rating: Double?,
    val reviewCount: Int,
    val reviews: List<Review>,
    val canMessage: Boolean,
)

internal fun UserProfile.toUiModel(): ProfileUiModel = ProfileUiModel(
    user = person.user,
    subtitle = person.subtitle,
    about = about,
    interestTypes = interests.projectTypes,
    interests = interests.topics,
    skills = skills,
    documents = documents,
    works = works,
    hasMoreWorks = worksCount > works.size,
    rating = reviews.average,
    reviewCount = reviews.count,
    reviews = latestReviews,
    canMessage = !isViewer,
)

/**
 * A decision of a moderator in the list on the viewer's profile; [time] says which day it was
 * made, the screen turns it into text in the language of the app.
 */
@Immutable
data class VerificationResultItem(
    val id: VerificationResultId,
    val subject: VerificationSubject,
    val title: String,
    val outcome: VerificationOutcome,
    val time: ChatListTime,
)
