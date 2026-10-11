package org.matchlab.academic_net.feature.profile.ui.my

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.jetbrains.compose.resources.stringResource
import org.matchlab.academic_net.common.model.ProjectType
import org.matchlab.academic_net.components.AppButton
import org.matchlab.academic_net.components.AppButtonVariant
import org.matchlab.academic_net.components.AppFilterChip
import org.matchlab.academic_net.components.AppIcons
import org.matchlab.academic_net.components.AppTagsField
import org.matchlab.academic_net.components.AppTextField
import org.matchlab.academic_net.components.AppTopBar
import org.matchlab.academic_net.components.LoadingState
import org.matchlab.academic_net.components.MessageState
import org.matchlab.academic_net.feature.profile.domain.AboutDraftError
import org.matchlab.academic_net.feature.profile.domain.ProfileLimits
import org.matchlab.academic_net.feature.profile.ui.EducationCard
import org.matchlab.academic_net.feature.profile.ui.ProfileUiDependencies
import org.matchlab.academic_net.feature.profile.ui.WorkCard
import org.matchlab.academic_net.feature.profile.ui.generated.resources.Res
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_add_document
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_add_work
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_close
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_edit_title
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_education
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_error_about_too_long
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_error_too_many_interests
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_error_too_many_skills
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_field_about
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_field_about_hint
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_field_interest_types
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_field_interests
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_field_skills
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_load_error
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_portfolio
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_retry
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_save
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_save_error
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_saving
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_tags_hint
import org.matchlab.academic_net.feature.profile.ui.interestLabel
import org.matchlab.academic_net.feature.profile.ui.toCardUiModel

/**
 * "10b Редактирование профиля": the text about the person, the interests and the skills, the
 * documents and the works. The cross drops what was typed without asking; the documents and the
 * works are added at once on their own screens, and "Сохранить" saves the rest.
 *
 * Editing or deleting a work or a document is not in the stories of the first sprint.
 *
 * @param onClose the cross, and the screen being saved
 * @param onAddWorkClick "Добавить работу"
 * @param onAddDocumentClick "Добавить документ"
 */
@Composable
fun EditProfileRoute(
    dependencies: ProfileUiDependencies,
    onClose: () -> Unit,
    onAddWorkClick: () -> Unit,
    onAddDocumentClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = viewModel {
        EditProfileViewModel(dependencies.observeMyProfile, dependencies.getUserWorks, dependencies.updateAbout)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) onClose()
    }

    EditProfileScreen(
        state = state,
        onClose = onClose,
        onAboutChange = viewModel::onAboutChange,
        onInterestTypeToggle = viewModel::onInterestTypeToggle,
        onAddInterest = viewModel::onAddInterest,
        onRemoveInterest = viewModel::onRemoveInterest,
        onAddSkill = viewModel::onAddSkill,
        onRemoveSkill = viewModel::onRemoveSkill,
        onAddWorkClick = onAddWorkClick,
        onAddDocumentClick = onAddDocumentClick,
        onSaveClick = viewModel::onSaveClick,
        onRetry = viewModel::onRetry,
        modifier = modifier,
    )
}

@Composable
internal fun EditProfileScreen(
    state: EditProfileUiState,
    onClose: () -> Unit,
    onAboutChange: (String) -> Unit,
    onInterestTypeToggle: (ProjectType) -> Unit,
    onAddInterest: (String) -> Unit,
    onRemoveInterest: (String) -> Unit,
    onAddSkill: (String) -> Unit,
    onRemoveSkill: (String) -> Unit,
    onAddWorkClick: () -> Unit,
    onAddDocumentClick: () -> Unit,
    onSaveClick: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier
            .fillMaxSize()
            .background(colors.surfaceContainerLow)
            .imePadding(),
    ) {
        AppTopBar(
            title = stringResource(Res.string.profile_edit_title),
            onNavigateBack = onClose,
            navigationIcon = AppIcons.X,
            navigationContentDescription = stringResource(Res.string.profile_close),
        )
        when {
            state.isLoading -> LoadingState()
            state.loadFailed -> MessageState(
                title = stringResource(Res.string.profile_load_error),
                actionText = stringResource(Res.string.profile_retry),
                onAction = onRetry,
            )
            else -> {
                EditForm(
                    state = state,
                    onAboutChange = onAboutChange,
                    onInterestTypeToggle = onInterestTypeToggle,
                    onAddInterest = onAddInterest,
                    onRemoveInterest = onRemoveInterest,
                    onAddSkill = onAddSkill,
                    onRemoveSkill = onRemoveSkill,
                    onAddWorkClick = onAddWorkClick,
                    onAddDocumentClick = onAddDocumentClick,
                    modifier = Modifier.weight(1f),
                )
                FormBottomBar(
                    text = stringResource(if (state.isSaving) Res.string.profile_saving else Res.string.profile_save),
                    onClick = onSaveClick,
                    enabled = !state.isSaving,
                    errorText = if (state.saveFailed) stringResource(Res.string.profile_save_error) else null,
                )
            }
        }
    }
}

@Composable
private fun EditForm(
    state: EditProfileUiState,
    onAboutChange: (String) -> Unit,
    onInterestTypeToggle: (ProjectType) -> Unit,
    onAddInterest: (String) -> Unit,
    onRemoveInterest: (String) -> Unit,
    onAddSkill: (String) -> Unit,
    onRemoveSkill: (String) -> Unit,
    onAddWorkClick: () -> Unit,
    onAddDocumentClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val draft = state.draft
    val errors = state.errors
    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AppTextField(
            value = draft.about,
            onValueChange = onAboutChange,
            label = stringResource(Res.string.profile_field_about),
            placeholder = stringResource(Res.string.profile_field_about_hint),
            singleLine = false,
            errorText = if (AboutDraftError.AboutTooLong in errors) {
                stringResource(Res.string.profile_error_about_too_long, ProfileLimits.MaxAboutLength)
            } else {
                null
            },
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = stringResource(Res.string.profile_field_interest_types),
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProjectType.entries.forEach { type ->
                    AppFilterChip(
                        text = type.interestLabel(),
                        selected = type in draft.interestTypes,
                        onClick = { onInterestTypeToggle(type) },
                    )
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            AppTagsField(
                label = stringResource(Res.string.profile_field_interests),
                tags = draft.interests,
                onAddTag = onAddInterest,
                onRemoveTag = onRemoveInterest,
                placeholder = stringResource(Res.string.profile_tags_hint),
            )
            if (AboutDraftError.TooManyInterests in errors) {
                FormErrorText(stringResource(Res.string.profile_error_too_many_interests, ProfileLimits.MaxTags))
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            AppTagsField(
                label = stringResource(Res.string.profile_field_skills),
                tags = draft.skills,
                onAddTag = onAddSkill,
                onRemoveTag = onRemoveSkill,
                placeholder = stringResource(Res.string.profile_tags_hint),
            )
            if (AboutDraftError.TooManySkills in errors) {
                FormErrorText(stringResource(Res.string.profile_error_too_many_skills, ProfileLimits.MaxTags))
            }
        }

        SectionTitle(stringResource(Res.string.profile_education))
        state.documents.forEach { EducationCard(it.toCardUiModel()) }
        AppButton(
            text = stringResource(Res.string.profile_add_document),
            onClick = onAddDocumentClick,
            modifier = Modifier.fillMaxWidth(),
            variant = AppButtonVariant.Outlined,
        )

        SectionTitle(stringResource(Res.string.profile_portfolio))
        state.works.forEach { WorkCard(it.toCardUiModel(listStyle = true)) }
        AppButton(
            text = stringResource(Res.string.profile_add_work),
            onClick = onAddWorkClick,
            modifier = Modifier.fillMaxWidth(),
            variant = AppButtonVariant.Outlined,
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
}
