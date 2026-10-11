package org.matchlab.academic_net.feature.profile.ui

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.matchlab.academic_net.common.model.ProjectType
import org.matchlab.academic_net.components.AppAvatars
import org.matchlab.academic_net.components.format.yearMonthText
import org.matchlab.academic_net.components.person.ratingText
import org.matchlab.academic_net.feature.profile.domain.DocumentKind
import org.matchlab.academic_net.feature.profile.domain.PortfolioWork
import org.matchlab.academic_net.feature.profile.domain.ProfileDocument
import org.matchlab.academic_net.feature.profile.domain.Review
import org.matchlab.academic_net.feature.profile.domain.ReviewerRole
import org.matchlab.academic_net.feature.profile.domain.VerificationStatus
import org.matchlab.academic_net.feature.profile.ui.generated.resources.Res
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_document_graduation
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_document_year
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_interest_practical
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_interest_scientific
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_reviews
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_reviews_count
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_reviews_positive
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_reviews_rated
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_role_initiator
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_role_participant
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_work_practical
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_work_scientific

/** The mark of a verification state: only the pending state has its own icon, the rest is yes or no. */
internal fun VerificationStatus.toMark(): VerificationMark = when (this) {
    VerificationStatus.Verified -> VerificationMark.Verified
    VerificationStatus.Pending -> VerificationMark.Pending
    VerificationStatus.Unconfirmed, VerificationStatus.Rejected -> VerificationMark.Unverified
}

/** "Научная работа" | "Scientific work": the kind of a work as the list of works names it. */
@Composable
internal fun ProjectType.workLabel(): String = stringResource(
    when (this) {
        ProjectType.Scientific -> Res.string.profile_work_scientific
        ProjectType.Practical -> Res.string.profile_work_practical
    },
)

/** "Научные проекты" | "Scientific projects": the project type as an interest chip. */
@Composable
internal fun ProjectType.interestLabel(): String = stringResource(
    when (this) {
        ProjectType.Scientific -> Res.string.profile_interest_scientific
        ProjectType.Practical -> Res.string.profile_interest_practical
    },
)

/** "Инициатор" | "Initiator": who the reviewer was in the reviewed project. */
@Composable
internal fun ReviewerRole.label(): String = stringResource(
    when (this) {
        ReviewerRole.Initiator -> Res.string.profile_role_initiator
        ReviewerRole.Participant -> Res.string.profile_role_participant
    },
)

/**
 * The card of a work. On a profile the second line is the description of the work; in the lists
 * of works ([listStyle]) it says what kind of work it is and where it lives:
 * "Научная работа · DOI 10.1234/cv".
 */
@Composable
internal fun PortfolioWork.toCardUiModel(listStyle: Boolean): WorkCardUiModel = WorkCardUiModel(
    title = title,
    subtitle = if (listStyle) listOfNotNull(type.workLabel(), source).joinToString(" · ") else description,
    type = type,
    mark = status.toMark(),
)

/** "Stanford University · выпуск 2027" for a diploma, "Coursera · 2025" for a certificate. */
@Composable
internal fun ProfileDocument.toCardUiModel(): EducationCardUiModel {
    val year = year
    return EducationCardUiModel(
        title = title,
        subtitle = when {
            year == null -> issuer
            kind == DocumentKind.Education -> stringResource(Res.string.profile_document_graduation, issuer, year)
            else -> stringResource(Res.string.profile_document_year, issuer, year)
        },
        mark = status.toMark(),
    )
}

/**
 * The card of a review. The author line is "Инициатор · AI-навигация по кампусу", with the month
 * the project ended added to it in the list of reviews ([withDate]).
 */
@Composable
internal fun Review.toCardUiModel(withDate: Boolean): ReviewCardUiModel {
    val line = listOf(authorRole.label(), projectTitle) + if (withDate) listOf(yearMonthText(projectFinished)) else emptyList()
    return ReviewCardUiModel(
        authorName = author.name,
        authorRole = line.joinToString(" · "),
        authorAvatar = AppAvatars.forKey(author.id.value),
        title = title,
        rating = rating,
        text = text,
    )
}

/** "Отзывы · ★ 4.8" | "Reviews · ★ 4.8"; just "Отзывы" while there is no rating. */
@Composable
internal fun reviewsTitle(rating: Double?): String =
    if (rating == null) {
        stringResource(Res.string.profile_reviews)
    } else {
        stringResource(Res.string.profile_reviews_rated, ratingText(rating))
    }

/** "12 отзывов · 11 положительных" | "12 reviews · 11 positive". */
@Composable
internal fun reviewsCountText(count: Int, positiveCount: Int): String =
    pluralStringResource(Res.plurals.profile_reviews_count, count, count) + " · " +
        pluralStringResource(Res.plurals.profile_reviews_positive, positiveCount, positiveCount)
