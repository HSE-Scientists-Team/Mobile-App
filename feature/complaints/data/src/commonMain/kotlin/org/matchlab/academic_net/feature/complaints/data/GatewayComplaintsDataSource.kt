package org.matchlab.academic_net.feature.complaints.data

import org.matchlab.academic_net.common.result.RemoteFailure
import org.matchlab.academic_net.feature.complaints.domain.Complaint
import org.matchlab.academic_net.feature.complaints.domain.ComplaintNotAllowedException
import org.matchlab.academic_net.feature.complaints.domain.ComplaintSubject
import org.matchlab.academic_net.feature.complaints.domain.ComplaintTarget
import org.matchlab.academic_net.gateway.ComplaintsApi
import org.matchlab.academic_net.gateway.ProjectCatalogApi
import org.matchlab.academic_net.gateway.UserDirectory
import org.matchlab.academic_net.gateway.dto.CreateComplaintDto
import org.matchlab.academic_net.gateway.mapping.ProjectAssembler
import org.matchlab.academic_net.gateway.mapping.toCollaborator

/**
 * [ComplaintsDataSource] over the Gateway: [ComplaintsApi] for the complaint, [UserDirectory] and [ProjectCatalogApi]
 * (with [ProjectAssembler]) for the card of the target.
 *
 * | Member | What it does |
 * |---|---|
 * | `getSubject` of a person | `GET /users/{id}/profile` (the directory); no such person is `null`; the id of the request is the person's (the profile may leave it out, API-GAP G55) |
 * | `getSubject` of a project | `GET /projects/{id}`; a 400, 403 or 404 is `null`; the title, the owner's name and university |
 * | `send` | `POST /complaints` with the code of the reason as `subject` (API-GAP G68), the text, exactly one target and no files; a 403 is [ComplaintNotAllowedException]; any other failure is thrown as the `RemoteFailure` it is. No retry: a repeat makes a second complaint (API-GAP G48) |
 */
class GatewayComplaintsDataSource(
    private val api: ComplaintsApi,
    private val users: UserDirectory,
    private val catalog: ProjectCatalogApi,
    private val assembler: ProjectAssembler,
) : ComplaintsDataSource {
    override suspend fun getSubject(target: ComplaintTarget): ComplaintSubject? = when (target) {
        is ComplaintTarget.User -> personSubject(target)
        is ComplaintTarget.Project -> projectSubject(target)
    }

    override suspend fun send(complaint: Complaint) {
        val target = complaint.target
        val input = CreateComplaintDto(
            subject = complaint.reason.code,
            description = complaint.description,
            targetUserId = (target as? ComplaintTarget.User)?.id?.value,
            targetProjectId = (target as? ComplaintTarget.Project)?.id?.value,
        )
        try {
            api.create(input)
        } catch (failure: RemoteFailure.Http) {
            if (failure.status == FORBIDDEN) throw ComplaintNotAllowedException()
            throw failure
        }
    }

    private suspend fun personSubject(target: ComplaintTarget.User): ComplaintSubject? {
        val profile = users.profile(target.id.value) ?: return null
        val person = profile.toCollaborator()
        return ComplaintSubject.PersonSubject(person.copy(user = person.user.copy(id = target.id)))
    }

    private suspend fun projectSubject(target: ComplaintTarget.Project): ComplaintSubject? {
        val dto = try {
            catalog.get(target.id.value)
        } catch (failure: RemoteFailure.Http) {
            if (failure.status in NOT_VISIBLE) return null
            throw failure
        }
        val assembled = assembler.assemble(listOf(dto)).firstOrNull() ?: return null
        return ComplaintSubject.ProjectSubject(
            title = assembled.project.title,
            initiatorName = assembled.project.initiator.name,
            initiatorUniversity = assembled.owner?.details?.university.orEmpty().trim(),
        )
    }

    private companion object {
        const val FORBIDDEN = 403

        /** 400 (not a project id), 403 (not allowed to see it) and 404 (unknown): the project is not there for the viewer. */
        val NOT_VISIBLE = setOf(400, 403, 404)
    }
}
