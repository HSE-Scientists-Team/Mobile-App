package org.matchlab.academic_net.feature.profile.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.matchlab.academic_net.common.result.SubmitResult
import org.matchlab.academic_net.feature.profile.domain.AboutDraft
import org.matchlab.academic_net.feature.profile.domain.AboutDraftError
import org.matchlab.academic_net.feature.profile.domain.DocumentDraft
import org.matchlab.academic_net.feature.profile.domain.DocumentDraftError
import org.matchlab.academic_net.feature.profile.domain.MyProfile
import org.matchlab.academic_net.feature.profile.domain.MyProfileRepository
import org.matchlab.academic_net.feature.profile.domain.PortfolioWork
import org.matchlab.academic_net.feature.profile.domain.ProfileDocument
import org.matchlab.academic_net.feature.profile.domain.VerificationResult
import org.matchlab.academic_net.feature.profile.domain.VerificationResultId
import org.matchlab.academic_net.feature.profile.domain.WorkDraft
import org.matchlab.academic_net.feature.profile.domain.WorkDraftError
import org.matchlab.academic_net.utils.suspendRunCatching

/** "Мой профиль": the viewer's own profile with what is being checked in it. */
class ObserveMyProfileUseCase(private val repository: MyProfileRepository) {
    operator fun invoke(): Flow<MyProfile> = repository.observeMyProfile()
}

/** "Заполнить БИО и интересы": saves the text about the person, the interests and the skills. */
class UpdateAboutUseCase(private val repository: MyProfileRepository) {
    suspend operator fun invoke(draft: AboutDraft): SubmitResult<MyProfile, AboutDraftError> {
        val normalized = draft.normalized()
        val errors = normalized.validate()
        if (errors.isNotEmpty()) return SubmitResult.Invalid(errors)

        return suspendRunCatching { repository.updateAbout(normalized) }.fold(
            onSuccess = { SubmitResult.Success(it) },
            onFailure = { SubmitResult.Failure(it) },
        )
    }
}

/**
 * "Добавить научную или проектную работу": with a link or a proof the work goes to moderation,
 * without them it is added as unconfirmed.
 */
class AddWorkUseCase(private val repository: MyProfileRepository) {
    suspend operator fun invoke(draft: WorkDraft): SubmitResult<PortfolioWork, WorkDraftError> {
        val normalized = draft.normalized()
        val errors = normalized.validate()
        if (errors.isNotEmpty()) return SubmitResult.Invalid(errors)

        return suspendRunCatching { repository.addWork(normalized) }.fold(
            onSuccess = { SubmitResult.Success(it) },
            onFailure = { SubmitResult.Failure(it) },
        )
    }
}

/** "Загрузить документы об образовании и сертификаты": the document goes to moderation. */
class AddDocumentUseCase(private val repository: MyProfileRepository) {
    suspend operator fun invoke(draft: DocumentDraft): SubmitResult<ProfileDocument, DocumentDraftError> {
        val normalized = draft.normalized()
        val errors = normalized.validate()
        if (errors.isNotEmpty()) return SubmitResult.Invalid(errors)

        return suspendRunCatching { repository.addDocument(normalized) }.fold(
            onSuccess = { SubmitResult.Success(it) },
            onFailure = { SubmitResult.Failure(it) },
        )
    }
}

/** "Получать уведомление о результате проверки": the decisions of the moderators, newest first. */
class ObserveVerificationResultsUseCase(private val repository: MyProfileRepository) {
    operator fun invoke(): Flow<List<VerificationResult>> = repository.observeVerificationResults()
}

/** Whether there is a decision the viewer has not been shown yet: the dot on the "Профиль" tab. */
class ObserveHasUnseenResultsUseCase(private val repository: MyProfileRepository) {
    operator fun invoke(): Flow<Boolean> =
        repository.observeVerificationResults()
            .map { results -> results.any { !it.isSeen } }
            .distinctUntilChanged()
}

/** Called when the results are shown on the profile: they stop counting as news. */
class MarkResultsSeenUseCase(private val repository: MyProfileRepository) {
    suspend operator fun invoke(ids: Set<VerificationResultId>): Result<Unit> =
        suspendRunCatching { repository.markResultsSeen(ids) }
}
