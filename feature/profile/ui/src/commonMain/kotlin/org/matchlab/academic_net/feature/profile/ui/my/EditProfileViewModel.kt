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
import org.matchlab.academic_net.common.result.LoadResult
import org.matchlab.academic_net.common.result.SubmitResult
import org.matchlab.academic_net.feature.profile.domain.AboutDraft
import org.matchlab.academic_net.feature.profile.domain.AboutDraftError
import org.matchlab.academic_net.feature.profile.domain.MyProfile
import org.matchlab.academic_net.feature.profile.domain.PortfolioWork
import org.matchlab.academic_net.feature.profile.domain.ProfileDocument
import org.matchlab.academic_net.feature.profile.domain.usecase.GetUserWorksUseCase
import org.matchlab.academic_net.feature.profile.domain.usecase.ObserveMyProfileUseCase
import org.matchlab.academic_net.feature.profile.domain.usecase.UpdateAboutUseCase
import org.matchlab.academic_net.utils.withTag
import org.matchlab.academic_net.utils.withoutTag

/**
 * @property draft the text, the interests and the skills as the user is editing them; taken from
 *  the profile once, so later changes of the profile (a new work) do not overwrite the typing
 * @property documents and [works] are always what the profile has now: adding one on its own
 *  screen shows up here at once
 * @property errors the problems of the draft; empty until the first "Сохранить", then kept up to
 *  date while editing
 * @property isSaved set once the changes are saved: the screen closes
 */
@Immutable
data class EditProfileUiState(
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val draft: AboutDraft = AboutDraft(),
    val documents: List<ProfileDocument> = emptyList(),
    val works: List<PortfolioWork> = emptyList(),
    val errors: Set<AboutDraftError> = emptySet(),
    val isSaving: Boolean = false,
    val saveFailed: Boolean = false,
    val isSaved: Boolean = false,
)

/**
 * "10b Редактирование профиля": "заполнить БИО и интересы". Documents and works are added on
 * their own screens and appear in the lists here as the profile changes; "Сохранить" saves the
 * text, the interests and the skills.
 */
class EditProfileViewModel(
    private val observeMyProfile: ObserveMyProfileUseCase,
    private val getUserWorks: GetUserWorksUseCase,
    private val updateAbout: UpdateAboutUseCase,
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
        state.update { it.copy(isLoading = true, loadFailed = false) }
        observe()
    }

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
        if (current.isSaving || current.isSaved || current.isLoading || current.loadFailed) return
        val errors = current.draft.normalized().validate()
        showErrors = true
        if (errors.isNotEmpty()) {
            state.update { it.copy(errors = errors) }
            return
        }
        state.update { it.copy(errors = emptySet(), isSaving = true, saveFailed = false) }
        viewModelScope.launch {
            when (val result = updateAbout(current.draft)) {
                is SubmitResult.Success -> state.update { it.copy(isSaving = false, isSaved = true) }
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
                .catch { state.update { current -> current.copy(isLoading = false, loadFailed = !draftTaken) } }
                .collect { profile ->
                    val works = allWorks(profile)
                    state.update { current ->
                        current.copy(
                            isLoading = false,
                            loadFailed = false,
                            draft = if (draftTaken) current.draft else profile.toDraft(),
                            documents = profile.profile.documents,
                            works = works,
                        )
                    }
                    draftTaken = true
                }
        }
    }

    /**
     * The profile carries a preview of the works; the list here has all of them.
     *
     * If the full list cannot be loaded the preview stays (the newest works) and the failure is
     * not shown: the form of "О себе", interests and skills still works, the list of works is only
     * a reference here, and a stream that fails does not take the screen down with it. The next
     * emission of the profile (a work added through "Добавить работу") asks for the full list again.
     */
    private suspend fun allWorks(profile: MyProfile): List<PortfolioWork> {
        val preview = profile.profile.works
        if (profile.profile.worksCount <= preview.size) return preview
        val result = getUserWorks(profile.profile.person.user.id)
        return if (result is LoadResult.Found) result.value else preview
    }

    private fun editDraft(transform: (AboutDraft) -> AboutDraft) {
        state.update { current ->
            val draft = transform(current.draft)
            current.copy(
                draft = draft,
                errors = if (showErrors) draft.normalized().validate() else current.errors,
                saveFailed = false,
            )
        }
    }
}

private fun MyProfile.toDraft(): AboutDraft = AboutDraft(
    about = profile.about,
    interestTypes = profile.interests.projectTypes,
    interests = profile.interests.topics,
    skills = profile.skills,
)
