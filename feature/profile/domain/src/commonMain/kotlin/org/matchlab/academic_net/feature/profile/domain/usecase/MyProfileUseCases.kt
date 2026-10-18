package org.matchlab.academic_net.feature.profile.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.matchlab.academic_net.common.result.LoadResult
import org.matchlab.academic_net.common.result.SubmitResult
import org.matchlab.academic_net.feature.profile.domain.DocumentDraft
import org.matchlab.academic_net.feature.profile.domain.DocumentDraftError
import org.matchlab.academic_net.feature.profile.domain.DocumentId
import org.matchlab.academic_net.feature.profile.domain.EditOutcome
import org.matchlab.academic_net.feature.profile.domain.MyProfile
import org.matchlab.academic_net.feature.profile.domain.MyProfileRepository
import org.matchlab.academic_net.feature.profile.domain.PortfolioChange
import org.matchlab.academic_net.feature.profile.domain.PortfolioWork
import org.matchlab.academic_net.feature.profile.domain.ProfileDocument
import org.matchlab.academic_net.feature.profile.domain.ProfileDraft
import org.matchlab.academic_net.feature.profile.domain.ProfileDraftError
import org.matchlab.academic_net.feature.profile.domain.VerificationResult
import org.matchlab.academic_net.feature.profile.domain.VerificationResultId
import org.matchlab.academic_net.feature.profile.domain.VerificationSubject
import org.matchlab.academic_net.feature.profile.domain.WorkDraft
import org.matchlab.academic_net.feature.profile.domain.WorkDraftError
import org.matchlab.academic_net.feature.profile.domain.WorkId
import org.matchlab.academic_net.feature.profile.domain.savePortfolioItem
import org.matchlab.academic_net.feature.profile.domain.toDraft
import org.matchlab.academic_net.utils.suspendRunCatching

/** "Мой профиль": the viewer's own profile with what is being checked in it. */
class ObserveMyProfileUseCase(private val repository: MyProfileRepository) {
    operator fun invoke(): Flow<MyProfile> = repository.observeMyProfile()
}

/** Asks the server for the viewer's profile again; a failed refresh is a failed [Result], cancellation is rethrown. */
class RefreshMyProfileUseCase(private val repository: MyProfileRepository) {
    suspend operator fun invoke(): Result<Unit> = suspendRunCatching { repository.refreshMyProfile() }
}

/** "Редактирование профиля": saves the name, the study, the text about the person, the interests and the skills. */
class UpdateProfileUseCase(private val repository: MyProfileRepository) {
    suspend operator fun invoke(draft: ProfileDraft): SubmitResult<MyProfile, ProfileDraftError> {
        val normalized = draft.normalized()
        val errors = normalized.validate()
        if (errors.isNotEmpty()) return SubmitResult.Invalid(errors)

        return suspendRunCatching { repository.updateProfile(normalized) }.fold(
            onSuccess = { SubmitResult.Success(it) },
            onFailure = { SubmitResult.Failure(it) },
        )
    }
}

/**
 * "Добавить научную или проектную работу": the work is added, and with a link or a proof file it is also sent for
 * verification. [PortfolioChange.Saved.sent] says whether it was sent.
 */
class AddWorkUseCase(private val repository: MyProfileRepository) {
    suspend operator fun invoke(draft: WorkDraft): SubmitResult<PortfolioChange<PortfolioWork>, WorkDraftError> {
        val normalized = draft.normalized()
        val errors = normalized.validate()
        if (errors.isNotEmpty()) return SubmitResult.Invalid(errors)

        return suspendRunCatching { repository.createWork(normalized) }.fold(
            onSuccess = { SubmitResult.Success(it) },
            onFailure = { SubmitResult.Failure(it) },
        )
    }
}

/** "Загрузить документы об образовании и сертификаты": the document is added and sent for verification. */
class AddDocumentUseCase(private val repository: MyProfileRepository) {
    suspend operator fun invoke(draft: DocumentDraft): SubmitResult<PortfolioChange<ProfileDocument>, DocumentDraftError> {
        val normalized = draft.normalized()
        val errors = normalized.validate()
        if (errors.isNotEmpty()) return SubmitResult.Invalid(errors)

        return suspendRunCatching { repository.createDocument(normalized) }.fold(
            onSuccess = { SubmitResult.Success(it) },
            onFailure = { SubmitResult.Failure(it) },
        )
    }
}

/** The form of a work being edited: the work as the server has it now. */
class GetMyWorkUseCase(private val repository: MyProfileRepository) {
    suspend operator fun invoke(id: WorkId): LoadResult<PortfolioWork> = loadResult { repository.getMyWork(id) }
}

/** The form of a document being edited: the document as the server has it now. */
class GetMyDocumentUseCase(private val repository: MyProfileRepository) {
    suspend operator fun invoke(id: DocumentId): LoadResult<ProfileDocument> = loadResult { repository.getMyDocument(id) }
}

/**
 * Saves the form of a work being edited, which was opened on [base]. Nothing changed means no request (a save would
 * reset the confirmation for nothing); the form is compared with [base] after both went through the same
 * normalization, so a text the server keeps with a double space or a trailing blank is not a change the user made.
 * With [send] the work is also sent for verification when it has a link or a proof file. A work that was saved but
 * could not be sent is [PortfolioChange.Saved] with `sent = false`. See `savePortfolioItem` for the whole table.
 */
class SaveWorkUseCase(private val repository: MyProfileRepository) {
    suspend operator fun invoke(
        base: PortfolioWork,
        draft: WorkDraft,
        send: Boolean,
    ): SubmitResult<PortfolioChange<PortfolioWork>, WorkDraftError> {
        val normalized = draft.normalized()
        val errors = normalized.validate()
        if (errors.isNotEmpty()) return SubmitResult.Invalid(errors)

        return suspendRunCatching {
            savePortfolioItem(
                base = base,
                // Both sides are normalized: the form was normalized above, while the record is as the server has it.
                changed = normalized != base.toDraft().normalized(),
                send = send,
                sendsForVerification = normalized.sendsForVerification,
                update = { repository.updateWork(base.id, normalized, base.revision) },
                submit = { repository.submitForVerification(VerificationSubject.Work(base.id)) },
            )
        }.fold(
            onSuccess = { SubmitResult.Success(it) },
            onFailure = { SubmitResult.Failure(it) },
        )
    }
}

/** Like [SaveWorkUseCase], for the form of a document being edited, which was opened on [base]. */
class SaveDocumentUseCase(private val repository: MyProfileRepository) {
    suspend operator fun invoke(
        base: ProfileDocument,
        draft: DocumentDraft,
        send: Boolean,
    ): SubmitResult<PortfolioChange<ProfileDocument>, DocumentDraftError> {
        val normalized = draft.normalized()
        val errors = normalized.validate()
        if (errors.isNotEmpty()) return SubmitResult.Invalid(errors)

        return suspendRunCatching {
            savePortfolioItem(
                base = base,
                // Both sides are normalized: the form was normalized above, while the record is as the server has it.
                changed = normalized != base.toDraft().normalized(),
                send = send,
                sendsForVerification = normalized.sendsForVerification,
                update = { repository.updateDocument(base.id, normalized, base.revision) },
                submit = { repository.submitForVerification(VerificationSubject.Document(base.id, base.kind)) },
            )
        }.fold(
            onSuccess = { SubmitResult.Success(it) },
            onFailure = { SubmitResult.Failure(it) },
        )
    }
}

/** "Отправить на проверку" a work or a document that is already added. */
class SubmitForVerificationUseCase(private val repository: MyProfileRepository) {
    suspend operator fun invoke(subject: VerificationSubject): Result<EditOutcome<Unit>> =
        suspendRunCatching { repository.submitForVerification(subject) }
}

/** "Удалить работу" / "Удалить документ"; a record that is gone already counts as deleted. */
class DeletePortfolioItemUseCase(private val repository: MyProfileRepository) {
    suspend operator fun invoke(subject: VerificationSubject): Result<EditOutcome<Unit>> =
        suspendRunCatching { repository.delete(subject) }
}

/**
 * "Получать уведомление о результате проверки": the decisions of the moderators, newest first.
 * API-GAP G12: empty while the server gives no statuses.
 */
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
