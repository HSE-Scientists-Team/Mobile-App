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
import org.matchlab.academic_net.feature.profile.domain.ProfileLimits
import org.matchlab.academic_net.feature.profile.domain.toDraft
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
)

/**
 * "10b Редактирование профиля": the name, the university, the study (the level as a free text, API-GAP G10, and the
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
) : ViewModel() {
    private val state = MutableStateFlow(EditProfileUiState())

    val uiState: StateFlow<EditProfileUiState> = state.asStateFlow()

    private var observing: Job? = null
    private var draftTaken = false
    private var showErrors = false

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

    /** The level of study, as the person writes it (API-GAP G10: a free text, there is no list to choose from). */
    fun onStudyLevelChange(level: String) = editDraft { it.copy(studyLevel = level) }

    /** The year of study; only digits are taken, and no more than the largest year has. */
    fun onStudyYearChange(year: String) = editDraft {
        it.copy(studyYear = year.filter(Char::isDigit).take(MaxStudyYearDigits))
    }

    fun onFieldChange(field: String) = editDraft { it.copy(field = field) }

    fun onAboutChange(about: String) = editDraft { it.copy(about = about) }

    fun onInterestTypeToggle(type: ProjectType) = editDraft {
        it.copy(interestTypes = if (type in it.interestTypes) it.interestTypes - type else it.interestTypes + type)
    }

    fun onAddInterest(tag: String) = editDraft { it.copy(interests = it.interests.withTag(tag)) }

    fun onRemoveInterest(tag: String) = editDraft { it.copy(interests = it.interests.withoutTag(tag)) }

    fun onAddSkill(tag: String) = editDraft { it.copy(skills = it.skills.withTag(tag)) }

    fun onRemoveSkill(tag: String) = editDraft { it.copy(skills = it.skills.withoutTag(tag)) }

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
                is SubmitResult.Success -> state.update { it.copy(isSaved = true) }
                is SubmitResult.Invalid -> state.update { it.copy(isSaving = false, errors = result.errors) }
                is SubmitResult.Failure -> state.update { it.copy(isSaving = false, saveFailed = true) }
            }
        }
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

    private companion object {
        /** The year of study has as many digits as the largest one: "6" is one. */
        val MaxStudyYearDigits = ProfileLimits.MaxStudyYear.toString().length
    }
}
