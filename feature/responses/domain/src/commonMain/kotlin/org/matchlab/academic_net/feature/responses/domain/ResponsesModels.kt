package org.matchlab.academic_net.feature.responses.domain

import org.matchlab.academic_net.common.model.Collaborator
import org.matchlab.academic_net.common.model.Project
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.ProjectJoin
import org.matchlab.academic_net.common.model.ProjectJoinStatus
import org.matchlab.academic_net.common.model.Role

/** The longest text of an application or an invitation the app sends. API-GAP G60: the API names no limit. */
const val JoinMessageMaxLength = 500

/**
 * The candidate of an application as 07d shows them.
 *
 * @property person name, university, study and field; without a profile the candidate's id with an empty name
 * @property skills the skills of the profile; empty without one
 * @property hasProfile the public profile could be read (`false`: "Без имени", no chat, no tap)
 */
data class Candidate(
    val person: Collaborator,
    val skills: List<String>,
    val hasProfile: Boolean,
)

/** A record of the initiator's project with its candidate; [candidate] is `null` when the server named none (G55). */
data class ProjectResponse(
    val join: ProjectJoin,
    val candidate: Candidate?,
)

/** A record of the viewer with the project it is about; [role] is `null` when the project no longer has the role. */
data class MyResponse(
    val join: ProjectJoin,
    val project: Project,
    val role: Role?,
)

/** The initiator's project with its applications (invitations left out), as 07d shows them. */
data class ProjectResponses(
    val project: Project,
    val responses: List<ProjectResponse>,
)

/** Why the server refused an action on an application or an invitation. */
enum class JoinRefusal {
    /** 403: no rights, a blocked account or an e-mail that is not confirmed. */
    NotAllowed,

    /** 409: the role is taken, the record is decided already, or there is such a record. */
    Conflict,

    /** 404: the project, the role or the record is gone. */
    NotFound,
}

/** The server refused (403, 404, 409); [refusal] says how. Other failures are thrown as the `RemoteFailure` they are. */
class JoinRefusedException(val refusal: JoinRefusal) : IllegalStateException("Refused: $refusal")

/** The viewer may not see the applications of the project (403) or it is gone (404). */
class ProjectResponsesUnavailableException(val projectId: ProjectId) :
    NoSuchElementException("Responses of project ${projectId.value} are not available")

/**
 * The record waits for a decision: its status is `Pending`. A status the app does not know (`null`) is not waiting: the
 * server lists its statuses (`pending`, `accepted`, `rejected`, `cancelled`), so a record with another text must not get
 * buttons that the server would answer with a conflict. The record is shown all the same, without a decision to take.
 *
 * The rules of the statuses, used by the use cases and by the UI:
 *
 * - **Actions on 15.** A record of type `Application` that [isWaitingForDecision] has "Отозвать отклик". A record of type
 *   `Invitation` that [isWaitingForDecision] has ✓ ×. A record with `type = null` has no actions.
 * - **Actions on 07d.** ✓ × are on the records that [isWaitingForDecision]; `Accepted`, `Rejected` and `Cancelled` ones
 *   have a chip and no buttons.
 * - **The numbers "· N".** The tabs of 15 and the chips of 07d count the records that [isWaitingForDecision].
 */
val ProjectJoin.isWaitingForDecision: Boolean
    get() = status == ProjectJoinStatus.Pending
