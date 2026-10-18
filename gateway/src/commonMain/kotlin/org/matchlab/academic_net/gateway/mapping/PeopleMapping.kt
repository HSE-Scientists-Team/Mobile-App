package org.matchlab.academic_net.gateway.mapping

import org.matchlab.academic_net.common.model.Collaborator
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.model.UserSummary
import org.matchlab.academic_net.common.paging.Page
import org.matchlab.academic_net.common.paging.PageRequest
import org.matchlab.academic_net.gateway.dto.PeopleListDto
import org.matchlab.academic_net.gateway.dto.PersonDto

/**
 * The person as a row of the people search and of the feed: the id, the name with its outer spaces cut (an empty one is
 * the UI's "Без имени"), `verified`, the university, the study and the field of study (`specialization`). `null` for a
 * record without a `user_id`: it could be neither opened nor told apart from the others. API-GAP G10: the study level is
 * the server's text as it is (blank is `null`), a year of 0 or less is "not given". API-GAP G18: no avatar.
 * API-GAP G30: the rating is not read.
 */
fun PersonDto.toCollaborator(): Collaborator? {
    if (userId.isBlank()) return null
    return Collaborator(
        user = UserSummary(id = UserId(userId), name = displayName.trim(), avatarUrl = null, isVerified = verified),
        university = university.trim(),
        study = studyOf(studyLevel, studyYear),
        field = specialization.trim(),
    )
}

/**
 * One page of the people search: the readable people in the server's order. A record without a `user_id` is left out
 * of [Page.items] but counted in [Page.received], so the next page starts after it; the total goes through [pageOf].
 */
fun PeopleListDto.toCollaboratorPage(request: PageRequest): Page<Collaborator> =
    pageOf(
        items = items.mapNotNull { it.toCollaborator() },
        received = items.size,
        reportedTotal = total,
        request = request,
    )
