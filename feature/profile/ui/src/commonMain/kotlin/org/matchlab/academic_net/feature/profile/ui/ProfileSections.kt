package org.matchlab.academic_net.feature.profile.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.matchlab.academic_net.common.model.ProjectType
import org.matchlab.academic_net.components.AppFilterChip
import org.matchlab.academic_net.components.AppTagChip
import org.matchlab.academic_net.components.SectionHeader
import org.matchlab.academic_net.components.TagsBlock
import org.matchlab.academic_net.components.TextBlock
import org.matchlab.academic_net.feature.profile.domain.PortfolioWork
import org.matchlab.academic_net.feature.profile.domain.ReviewId
import org.matchlab.academic_net.feature.profile.ui.generated.resources.Res
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_about
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_education
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_interests
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_no_reviews
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_not_filled
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_portfolio
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_see_all
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_see_all_count
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_skills
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_works

/**
 * The sections shared by "09 Профиль коллаборатора" and "10 Мой профиль", as consecutive blocks of
 * the parent column (it spaces them): about, interests, skills, education and documents, works,
 * reviews.
 *
 * Someone else's profile hides a section with nothing in it, except the reviews that say there
 * are none. On the own profile ([isOwn]) an empty section says "Не заполнено" and a tap on it
 * leads to the editing ([onFillClick]); the works are called "Портфолио" there.
 *
 * @param onWorkClick a card of a work was tapped; the screen opens its link
 * @param onAllWorksClick "Все" of the works, shown when the profile has more than it previews
 * @param onAllReviewsClick "Все N" of the reviews, shown when the profile has more than it previews
 */
@Composable
internal fun ColumnScope.ProfileSections(
    profile: ProfileUiModel,
    isOwn: Boolean,
    onFillClick: () -> Unit,
    onWorkClick: (PortfolioWork) -> Unit,
    onAllWorksClick: () -> Unit,
    onAllReviewsClick: () -> Unit,
    onReviewClick: (ReviewId) -> Unit,
) {
    val aboutTitle = stringResource(Res.string.profile_about)
    if (profile.about.isNotBlank()) {
        TextBlock(aboutTitle, profile.about)
    } else if (isOwn) {
        EmptySection(aboutTitle, onFillClick)
    }

    val interestsTitle = stringResource(Res.string.profile_interests)
    if (profile.interestTypes.isNotEmpty() || profile.interests.isNotEmpty()) {
        InterestsBlock(interestsTitle, profile.interestTypes, profile.interests)
    } else if (isOwn) {
        EmptySection(interestsTitle, onFillClick)
    }

    val skillsTitle = stringResource(Res.string.profile_skills)
    if (profile.skills.isNotEmpty()) {
        TagsBlock(skillsTitle, profile.skills)
    } else if (isOwn) {
        EmptySection(skillsTitle, onFillClick)
    }

    val educationTitle = stringResource(Res.string.profile_education)
    if (profile.documents.isNotEmpty()) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionHeader(educationTitle)
            profile.documents.forEach { EducationCard(it.toCardUiModel()) }
        }
    } else if (isOwn) {
        EmptySection(educationTitle, onFillClick)
    }

    val worksTitle = stringResource(if (isOwn) Res.string.profile_portfolio else Res.string.profile_works)
    if (profile.works.isNotEmpty()) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionHeader(
                title = worksTitle,
                action = if (profile.hasMoreWorks) stringResource(Res.string.profile_see_all) else null,
                onAction = onAllWorksClick,
            )
            profile.works.forEach { work ->
                WorkCard(
                    model = work.toCardUiModel(listStyle = false),
                    onClick = if (work.link != null) ({ onWorkClick(work) }) else null,
                )
            }
        }
    } else if (isOwn) {
        EmptySection(worksTitle, onFillClick)
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val hasMoreReviews = profile.reviewCount > profile.reviews.size
        SectionHeader(
            title = reviewsTitle(profile.rating),
            action = if (hasMoreReviews) {
                stringResource(Res.string.profile_see_all_count, profile.reviewCount)
            } else {
                null
            },
            onAction = onAllReviewsClick,
        )
        if (profile.reviews.isEmpty()) {
            Text(
                text = stringResource(Res.string.profile_no_reviews),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        profile.reviews.forEach { review ->
            ReviewCard(
                model = review.toCardUiModel(withDate = false),
                onClick = { onReviewClick(review.id) },
                maxTextLines = ReviewPreviewLines,
            )
        }
    }
}

/**
 * Interests: the two project types as static chips with the chosen ones highlighted, then the
 * free topics as tags. The types are shown only when the person has chosen some.
 */
@Composable
private fun InterestsBlock(
    title: String,
    types: Set<ProjectType>,
    topics: List<String>,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(title)
        if (types.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ProjectType.entries.forEach { type ->
                    AppFilterChip(type.interestLabel(), selected = type in types, onClick = null)
                }
            }
        }
        if (topics.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                topics.forEach { AppTagChip(it) }
            }
        }
    }
}

/** A section of the own profile with nothing in it: the title and a tappable "Не заполнено". */
@Composable
private fun EmptySection(title: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(title)
        Text(
            text = stringResource(Res.string.profile_not_filled),
            modifier = Modifier.clickable(role = Role.Button, onClick = onClick),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** How many lines of a review the cards of the lists show. */
private const val ReviewPreviewLines = 3
