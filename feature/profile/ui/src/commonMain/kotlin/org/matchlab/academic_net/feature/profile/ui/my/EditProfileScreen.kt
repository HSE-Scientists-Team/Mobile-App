package org.matchlab.academic_net.feature.profile.ui.my

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.compose.resources.stringResource
import org.matchlab.academic_net.common.model.ProjectType
import org.matchlab.academic_net.components.AppAvatars
import org.matchlab.academic_net.components.AppButton
import org.matchlab.academic_net.components.AppButtonVariant
import org.matchlab.academic_net.components.AppFilterChip
import org.matchlab.academic_net.components.AppIcons
import org.matchlab.academic_net.components.AppTagsField
import org.matchlab.academic_net.components.AppTextField
import org.matchlab.academic_net.components.AppTopBar
import org.matchlab.academic_net.components.Avatar
import org.matchlab.academic_net.components.LoadingState
import org.matchlab.academic_net.components.MessageState
import org.matchlab.academic_net.components.format.failureText
import org.matchlab.academic_net.feature.profile.domain.DocumentId
import org.matchlab.academic_net.feature.profile.domain.ProfileDraftError
import org.matchlab.academic_net.feature.profile.domain.ProfileLimits
import org.matchlab.academic_net.feature.profile.domain.WorkId
import org.matchlab.academic_net.feature.profile.ui.EducationCard
import org.matchlab.academic_net.feature.profile.ui.ProfileUiDependencies
import org.matchlab.academic_net.feature.profile.ui.WorkCard
import org.matchlab.academic_net.feature.profile.ui.generated.resources.Res
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_add_document
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_add_work
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_avatar_change
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_avatar_error
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_close
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_edit_title
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_education
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_error_about_too_long
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_error_required
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_error_study_year
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_error_text_too_long
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_error_too_many_interests
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_error_too_many_skills
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_field_about
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_field_about_hint
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_field_field
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_field_interest_types
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_field_interests
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_field_name
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_field_skills
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_field_study_level
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_field_study_year
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_field_university
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_load_error
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_portfolio
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_retry
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_save
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_save_error
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_tags_hint
import org.matchlab.academic_net.feature.profile.ui.interestLabel
import org.matchlab.academic_net.feature.profile.ui.toCardUiModel

/**
 * "10b Редактирование профиля": the name (required), the university, the level of study (a free text, docs/api-values.md) and
 * the year, the field, the text about the person, the interests and the skills, then the documents and the works. The
 * cross drops what was typed without asking. "Сохранить" saves the form; the documents and the works are added and
 * edited on their own screens, at once: their cards open for editing and have a chevron.
 *
 * The chips of the project types ("Научные проекты", "Практические проекты") are there only when the source takes them
 * (`ProfileCapabilities.interestTypes`); the types the person has chosen elsewhere and the app does not know are kept
 * as they are.
 *
 * @param onClose the cross, and the screen being saved
 * @param onAddWorkClick "Добавить работу"
 * @param onAddDocumentClick "Добавить документ"
 * @param onEditWork a card of a work
 * @param onEditDocument a card of a document
 */
@Composable
fun EditProfileRoute(
    dependencies: ProfileUiDependencies,
    onClose: () -> Unit,
    onAddWorkClick: () -> Unit,
    onAddDocumentClick: () -> Unit,
    onEditWork: (WorkId) -> Unit,
    onEditDocument: (DocumentId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = viewModel {
        EditProfileViewModel(dependencies.observeMyProfile, dependencies.updateProfile, dependencies.changeAvatar)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    // The gallery picker is remembered here, at the root of the screen (not in a sheet): on iOS a launcher in a transient
    // surface does not work.
    val photoPicker = rememberFilePickerLauncher(
        type = FileKitType.Image,
        onError = viewModel::onAvatarUnreadable,
        onResult = { file ->
            if (file != null) {
                scope.launch {
                    try {
                        viewModel.onAvatarChosen(avatarPhotoOf(file))
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (failure: Exception) {
                        viewModel.onAvatarUnreadable(failure)
                    }
                }
            }
        },
    )

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) onClose()
    }

    EditProfileScreen(
        state = state,
        interestTypesEditable = dependencies.capabilities.interestTypes,
        onClose = onClose,
        onNameChange = viewModel::onNameChange,
        onUniversityChange = viewModel::onUniversityChange,
        onStudyLevelChange = viewModel::onStudyLevelChange,
        onStudyYearChange = viewModel::onStudyYearChange,
        onFieldChange = viewModel::onFieldChange,
        onAboutChange = viewModel::onAboutChange,
        onInterestTypeToggle = viewModel::onInterestTypeToggle,
        onAddInterest = viewModel::onAddInterest,
        onRemoveInterest = viewModel::onRemoveInterest,
        onAddSkill = viewModel::onAddSkill,
        onRemoveSkill = viewModel::onRemoveSkill,
        onAddWorkClick = onAddWorkClick,
        onAddDocumentClick = onAddDocumentClick,
        onEditWork = onEditWork,
        onEditDocument = onEditDocument,
        onSaveClick = viewModel::onSaveClick,
        onRetry = viewModel::onRetry,
        onChangePhotoClick = photoPicker::launch,
        modifier = modifier,
    )
}

@Composable
internal fun EditProfileScreen(
    state: EditProfileUiState,
    interestTypesEditable: Boolean,
    onClose: () -> Unit,
    onNameChange: (String) -> Unit,
    onUniversityChange: (String) -> Unit,
    onStudyLevelChange: (String) -> Unit,
    onStudyYearChange: (String) -> Unit,
    onFieldChange: (String) -> Unit,
    onAboutChange: (String) -> Unit,
    onInterestTypeToggle: (ProjectType) -> Unit,
    onAddInterest: (String) -> Unit,
    onRemoveInterest: (String) -> Unit,
    onAddSkill: (String) -> Unit,
    onRemoveSkill: (String) -> Unit,
    onAddWorkClick: () -> Unit,
    onAddDocumentClick: () -> Unit,
    onEditWork: (WorkId) -> Unit,
    onEditDocument: (DocumentId) -> Unit,
    onSaveClick: () -> Unit,
    onRetry: () -> Unit,
    onChangePhotoClick: () -> Unit,
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
            state.loadFailure != null -> MessageState(
                title = stringResource(Res.string.profile_load_error),
                text = failureText(state.loadFailure),
                actionText = stringResource(Res.string.profile_retry),
                onAction = onRetry,
            )
            else -> {
                EditForm(
                    state = state,
                    interestTypesEditable = interestTypesEditable,
                    onNameChange = onNameChange,
                    onUniversityChange = onUniversityChange,
                    onStudyLevelChange = onStudyLevelChange,
                    onStudyYearChange = onStudyYearChange,
                    onFieldChange = onFieldChange,
                    onAboutChange = onAboutChange,
                    onInterestTypeToggle = onInterestTypeToggle,
                    onAddInterest = onAddInterest,
                    onRemoveInterest = onRemoveInterest,
                    onAddSkill = onAddSkill,
                    onRemoveSkill = onRemoveSkill,
                    onAddWorkClick = onAddWorkClick,
                    onAddDocumentClick = onAddDocumentClick,
                    onEditWork = onEditWork,
                    onEditDocument = onEditDocument,
                    onChangePhotoClick = onChangePhotoClick,
                    modifier = Modifier.weight(1f),
                )
                FormBottomBar(
                    text = stringResource(Res.string.profile_save),
                    onClick = onSaveClick,
                    enabled = !state.isSaving,
                    errorText = if (state.saveFailed) stringResource(Res.string.profile_save_error) else null,
                    loading = state.isSaving,
                )
            }
        }
    }
}

@Composable
private fun EditForm(
    state: EditProfileUiState,
    interestTypesEditable: Boolean,
    onNameChange: (String) -> Unit,
    onUniversityChange: (String) -> Unit,
    onStudyLevelChange: (String) -> Unit,
    onStudyYearChange: (String) -> Unit,
    onFieldChange: (String) -> Unit,
    onAboutChange: (String) -> Unit,
    onInterestTypeToggle: (ProjectType) -> Unit,
    onAddInterest: (String) -> Unit,
    onRemoveInterest: (String) -> Unit,
    onAddSkill: (String) -> Unit,
    onRemoveSkill: (String) -> Unit,
    onAddWorkClick: () -> Unit,
    onAddDocumentClick: () -> Unit,
    onEditWork: (WorkId) -> Unit,
    onEditDocument: (DocumentId) -> Unit,
    onChangePhotoClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val draft = state.draft
    val errors = state.errors
    val required = stringResource(Res.string.profile_error_required)
    // What is typed while the form is being saved would be lost (the save has its own copy), so the fields wait.
    val fieldsEnabled = !state.isSaving
    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AvatarEditor(state.avatar, onChangePhotoClick)
        AppTextField(
            value = draft.name,
            onValueChange = onNameChange,
            label = stringResource(Res.string.profile_field_name),
            enabled = fieldsEnabled,
            errorText = when {
                ProfileDraftError.NameEmpty in errors -> required
                ProfileDraftError.NameTooLong in errors ->
                    stringResource(Res.string.profile_error_text_too_long, ProfileLimits.MaxNameLength)
                else -> null
            },
        )
        AppTextField(
            value = draft.university,
            onValueChange = onUniversityChange,
            label = stringResource(Res.string.profile_field_university),
            enabled = fieldsEnabled,
            errorText = if (ProfileDraftError.UniversityTooLong in errors) {
                stringResource(Res.string.profile_error_text_too_long, ProfileLimits.MaxUniversityLength)
            } else {
                null
            },
        )
        // docs/api-values.md: the level of study is a free text, as in the design (there is no list of values to choose from).
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AppTextField(
                value = draft.studyLevel,
                onValueChange = onStudyLevelChange,
                label = stringResource(Res.string.profile_field_study_level),
                modifier = Modifier.weight(1f),
                enabled = fieldsEnabled,
                errorText = if (ProfileDraftError.StudyLevelTooLong in errors) {
                    stringResource(Res.string.profile_error_text_too_long, ProfileLimits.MaxStudyLevelLength)
                } else {
                    null
                },
            )
            AppTextField(
                value = draft.studyYear,
                onValueChange = onStudyYearChange,
                label = stringResource(Res.string.profile_field_study_year),
                modifier = Modifier.weight(1f),
                enabled = fieldsEnabled,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                errorText = if (ProfileDraftError.StudyYearInvalid in errors) {
                    stringResource(Res.string.profile_error_study_year, ProfileLimits.MinStudyYear)
                } else {
                    null
                },
            )
        }
        AppTextField(
            value = draft.field,
            onValueChange = onFieldChange,
            label = stringResource(Res.string.profile_field_field),
            enabled = fieldsEnabled,
            errorText = if (ProfileDraftError.FieldTooLong in errors) {
                stringResource(Res.string.profile_error_text_too_long, ProfileLimits.MaxFieldLength)
            } else {
                null
            },
        )
        AppTextField(
            value = draft.about,
            onValueChange = onAboutChange,
            label = stringResource(Res.string.profile_field_about),
            placeholder = stringResource(Res.string.profile_field_about_hint),
            singleLine = false,
            enabled = fieldsEnabled,
            errorText = if (ProfileDraftError.AboutTooLong in errors) {
                stringResource(Res.string.profile_error_about_too_long, ProfileLimits.MaxAboutLength)
            } else {
                null
            },
        )
        // The chips of the project types are there only when the source reads and writes them.
        if (interestTypesEditable) {
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
                            // A chip without a tap is a plain label: it waits while the form is saved.
                            onClick = if (fieldsEnabled) ({ onInterestTypeToggle(type) }) else null,
                        )
                    }
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
                enabled = fieldsEnabled,
            )
            if (ProfileDraftError.TooManyInterests in errors) {
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
                enabled = fieldsEnabled,
            )
            if (ProfileDraftError.TooManySkills in errors) {
                FormErrorText(stringResource(Res.string.profile_error_too_many_skills, ProfileLimits.MaxTags))
            }
        }

        SectionTitle(stringResource(Res.string.profile_education))
        state.documents.forEach { document ->
            EducationCard(
                model = document.toCardUiModel(),
                onClick = { onEditDocument(document.id) },
                showChevron = true,
            )
        }
        AppButton(
            text = stringResource(Res.string.profile_add_document),
            onClick = onAddDocumentClick,
            modifier = Modifier.fillMaxWidth(),
            variant = AppButtonVariant.Outlined,
        )

        SectionTitle(stringResource(Res.string.profile_portfolio))
        state.works.forEach { work ->
            WorkCard(
                model = work.toCardUiModel(listStyle = true),
                onClick = { onEditWork(work.id) },
                showChevron = true,
            )
        }
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

/**
 * The photo of the profile with "Изменить фото" under it. With a profile the photo is changed at once, not with
 * "Сохранить"; on the first fill (no profile yet) it waits for "Сохранить" and is uploaded after the profile. While the new one is uploaded the button turns; a failure is
 * written under it and the old photo stays.
 */
@Composable
private fun AvatarEditor(avatar: AvatarState, onChangePhotoClick: () -> Unit, modifier: Modifier = Modifier) {
    val userKey = avatar.userKey
    if (userKey == null) return
    Column(
        modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // The photo just chosen is drawn from its own bytes: it does not wait for the server to give the picture back.
        val chosen = remember(avatar.chosen) { avatar.chosen?.let { runCatching { it.bytes.decodeToImageBitmap() }.getOrNull() } }
        if (chosen != null) {
            Avatar(BitmapPainter(chosen), contentDescription = null, size = 96.dp)
        } else {
            Avatar(AppAvatars.forKey(userKey), contentDescription = null, size = 96.dp)
        }
        AppButton(
            text = stringResource(Res.string.profile_avatar_change),
            onClick = onChangePhotoClick,
            variant = AppButtonVariant.Text,
            loading = avatar.isUploading,
        )
        avatar.failure?.let { failure ->
            Text(
                text = stringResource(Res.string.profile_avatar_error, failureText(failure)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}
