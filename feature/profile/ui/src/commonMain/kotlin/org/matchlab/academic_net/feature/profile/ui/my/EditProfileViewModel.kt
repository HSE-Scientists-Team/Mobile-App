package org.matchlab.academic_net.feature.profile.ui.my

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.matchlab.academic_net.common.model.ProjectType
import org.matchlab.academic_net.common.result.SubmitResult
import org.matchlab.academic_net.feature.profile.domain.PortfolioWork
import org.matchlab.academic_net.feature.profile.domain.ProfileDocument
import org.matchlab.academic_net.feature.profile.domain.ProfileDraft
import org.matchlab.academic_net.feature.profile.domain.ProfileDraftError
import org.matchlab.academic_net.feature.profile.domain.UploadFile
import org.matchlab.academic_net.feature.profile.domain.toDraft
import org.matchlab.academic_net.feature.profile.domain.usecase.ChangeAvatarUseCase
import org.matchlab.academic_net.feature.profile.domain.usecase.ObserveMyProfileUseCase
import org.matchlab.academic_net.feature.profile.domain.usecase.UpdateProfileUseCase
import org.matchlab.academic_net.utils.withTag
import org.matchlab.academic_net.utils.withoutTag

/**
 * @property loadFailure why the profile could not be loaded (the form is not there then, "Повторить" asks again);
 *  `null` while it was
 * @property draft the form as the user is editing it; taken from the profile once, so later changes of the profile (a
 *  new work) do not overwrite the typing
 * @property documents and [works] are always what the profile has now: adding or changing one on its own screen shows up
 *  here at once
 * @property errors the problems of the draft; empty until the first "Сохранить", then kept up
 *  to date while editing
 * @property isSaving the form is being saved, or it has been and the screen is about to close: nothing is saved again
 * @property isSaved set once the changes are saved: the screen closes
 */
@Immutable
data class EditProfileUiState(
    val isLoading: Boolean = true,
    val loadFailure: Throwable? = null,
    val draft: ProfileDraft = ProfileDraft(),
    val documents: List<ProfileDocument> = emptyList(),
    val works: List<PortfolioWork> = emptyList(),
    val errors: Set<ProfileDraftError> = emptySet(),
    val isSaving: Boolean = false,
    val saveFailed: Boolean = false,
    val isSaved: Boolean = false,
    val avatar: AvatarState = AvatarState(),
)

/**
 * The photo of the profile on "10b".
 *
 * @property userKey the viewer, whose picture it is; `null` while the profile is loading
 * @property hasProfile the account has a profile (a name). The photo is a field of the profile, so a photo chosen without
 * one waits and is uploaded after the first save of the form
 * @property isUploading the photo is being uploaded and saved; the control waits
 * @property chosen the photo the person has just chosen, shown at once, while it is uploaded and after; `null` before a
 * choice and when the upload failed (the old photo stays). Without a profile it is the photo that waits for the save
 * @property failure why the last change failed; `null` if it did not (it is cleared by the next try)
 */
@Immutable
data class AvatarState(
    val userKey: String? = null,
    val hasProfile: Boolean = false,
    val isUploading: Boolean = false,
    val chosen: UploadFile? = null,
    val failure: Throwable? = null,
)

/**
 * "10b Редактирование профиля": the name, the university, the study (the level as a free text, docs/api-values.md, and the
 * year), the field, the text about the person, the interests and the skills. Documents and works are added and
 * edited on their own screens and appear in the lists here as the profile changes; "Сохранить" saves the form. A new
 * account (API-GAP G19) has no name: the form starts empty, and its first save creates the profile.
 *
 * **One save at a time**: [onSaveClick] does nothing while the form is being saved and after it has been (the screen is
 * about to close), so a double tap cannot save twice.
 *
 * **The form waits while it is saved** ([EditProfileUiState.isSaving]): the fields are switched off, and a change that
 * comes all the same (a late key, a tap on a chip) is ignored, as the forms of a work and of a document do. The save
 * has its own copy of the form, so such a change would be shown on the screen and never reach the server.
 */
class EditProfileViewModel(
    private val observeMyProfile: ObserveMyProfileUseCase,
    private val updateProfile: UpdateProfileUseCase,
    private val changeAvatar: ChangeAvatarUseCase,
) : ViewModel() {
    private val state = MutableStateFlow(EditProfileUiState())

    val uiState: StateFlow<EditProfileUiState> = state.asStateFlow()

    private var observing: Job? = null
    private var draftTaken = false
    private var showErrors = false

    // The photo chosen before the profile existed: it goes to the server after the profile is saved.
    private var waitingPhoto: UploadFile? = null

    init {
        observe()
    }

    /** "Повторить" after the profile could not be loaded. */
    fun onRetry() {
        state.update { it.copy(isLoading = true, loadFailure = null) }
        observe()
    }

    fun onNameChange(name: String) = editDraft { it.copy(name = name) }

    fun onUniversityChange(university: String) = editDraft { it.copy(university = university) }

    /** The level of study, as the person writes it (docs/api-values.md: a free text, there is no list to choose from). */
    fun onStudyLevelChange(level: String) = editDraft { it.copy(studyLevel = level) }

    /** The year of study; only digits are taken. How large the year may be is the server's to judge. */
    fun onStudyYearChange(year: String) = editDraft { it.copy(studyYear = year.filter(Char::isDigit)) }

    fun onFieldChange(field: String) = editDraft { it.copy(field = field) }

    fun onAboutChange(about: String) = editDraft { it.copy(about = about) }

    fun onInterestTypeToggle(type: ProjectType) = editDraft {
        it.copy(interestTypes = if (type in it.interestTypes) it.interestTypes - type else it.interestTypes + type)
    }

    fun onAddInterest(tag: String) = editDraft { it.copy(interests = it.interests.withTag(tag)) }

    fun onRemoveInterest(tag: String) = editDraft { it.copy(interests = it.interests.withoutTag(tag)) }

    fun onAddSkill(tag: String) = editDraft { it.copy(skills = it.skills.withTag(tag)) }

    fun onRemoveSkill(tag: String) = editDraft { it.copy(skills = it.skills.withoutTag(tag)) }

    /**
     * A photo was chosen. For an account with a profile it is uploaded and becomes the avatar at once (not with
     * "Сохранить": the photo is not a field of the form); one change at a time, a failure is shown under the photo and
     * the old picture stays. For an account with no profile yet (the first fill) the photo waits: "Сохранить" creates
     * the profile first and uploads the photo after it.
     */
    fun onAvatarChosen(photo: UploadFile) {
        val current = state.value
        if (current.avatar.userKey == null || current.avatar.isUploading || current.isSaving) return
        if (!current.avatar.hasProfile) {
            waitingPhoto = photo
            state.update { it.copy(avatar = it.avatar.copy(chosen = photo, failure = null)) }
            return
        }
        waitingPhoto = null
        state.update { it.copy(avatar = it.avatar.copy(isUploading = true, chosen = photo, failure = null)) }
        viewModelScope.launch {
            changeAvatar(photo).fold(
                onSuccess = { state.update { it.copy(avatar = it.avatar.copy(isUploading = false)) } },
                onFailure = { cause ->
                    state.update { it.copy(avatar = it.avatar.copy(isUploading = false, chosen = null, failure = cause)) }
                },
            )
        }
    }

    /** The photo could not be read or prepared: the same message as a failed upload. */
    fun onAvatarUnreadable(cause: Throwable) {
        state.update { it.copy(avatar = it.avatar.copy(failure = cause)) }
    }

    fun onSaveClick() {
        val current = state.value
        // Nothing to save while the profile is not there, while a save is running, and once the
        // changes are saved: the form stays on the screen until it has faded out.
        if (current.isSaving || current.isSaved || current.isLoading || current.loadFailure != null) return
        val errors = current.draft.normalized().validate()
        showErrors = true
        if (errors.isNotEmpty()) {
            state.update { it.copy(errors = errors) }
            return
        }
        state.update { it.copy(errors = emptySet(), isSaving = true, saveFailed = false) }
        viewModelScope.launch {
            when (val result = updateProfile(current.draft)) {
                // The save is done: `isSaving` stays set, so that nothing is saved again while the screen closes.
                is SubmitResult.Success -> uploadWaitingPhoto()
                is SubmitResult.Invalid -> state.update { it.copy(isSaving = false, errors = result.errors) }
                is SubmitResult.Failure -> state.update { it.copy(isSaving = false, saveFailed = true) }
            }
        }
    }

    /**
     * The profile is saved, so the account has one: the photo chosen before that goes up now. The form closes when it is
     * there. If it cannot be uploaded the profile stays saved, the form stays open with the cause under the photo, and
     * the next "Сохранить" saves the profile again and tries the photo again.
     */
    private suspend fun uploadWaitingPhoto() {
        val photo = waitingPhoto
        if (photo == null) {
            state.update { it.copy(isSaved = true) }
            return
        }
        state.update { it.copy(avatar = it.avatar.copy(isUploading = true, failure = null)) }
        changeAvatar(photo).fold(
            onSuccess = {
                waitingPhoto = null
                state.update { it.copy(isSaved = true, avatar = it.avatar.copy(isUploading = false)) }
            },
            onFailure = { cause ->
                state.update { it.copy(isSaving = false, avatar = it.avatar.copy(isUploading = false, failure = cause)) }
            },
        )
    }

    private fun observe() {
        observing?.cancel()
        observing = viewModelScope.launch {
            observeMyProfile()
                // A failure of the stream after the form is shown keeps the form: nothing is lost.
                .catch { cause ->
                    state.update { current ->
                        current.copy(isLoading = false, loadFailure = if (draftTaken) null else cause)
                    }
                }
                .collect { profile ->
                    state.update { current ->
                        current.copy(
                            isLoading = false,
                            loadFailure = null,
                            draft = if (draftTaken) current.draft else profile.toDraft(),
                            documents = profile.profile.documents,
                            works = profile.allWorks,
                            avatar = current.avatar.copy(
                                userKey = profile.profile.person.user.id.value,
                                hasProfile = !profile.needsName,
                            ),
                        )
                    }
                    draftTaken = true
                }
        }
    }

    private fun editDraft(transform: (ProfileDraft) -> ProfileDraft) {
        state.update { current ->
            // The form is being saved, or has been and is about to close: the save has its own copy of it.
            if (current.isSaving) return@update current
            val draft = transform(current.draft)
            current.copy(
                draft = draft,
                errors = if (showErrors) draft.normalized().validate() else current.errors,
                saveFailed = false,
            )
        }
    }
}
