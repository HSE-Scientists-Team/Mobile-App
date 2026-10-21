package org.matchlab.academic_net.gateway.mapping

import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.ProjectJoin
import org.matchlab.academic_net.common.model.ProjectJoinId
import org.matchlab.academic_net.common.model.ProjectJoinStatus
import org.matchlab.academic_net.common.model.ProjectJoinType
import org.matchlab.academic_net.common.model.RoleId
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.paging.Page
import org.matchlab.academic_net.common.paging.PageRequest
import org.matchlab.academic_net.gateway.dto.ProjectJoinDto
import org.matchlab.academic_net.gateway.dto.ProjectJoinListDto

/**
 * The record as the app shows it, or `null` when it cannot be shown: `id`, `project_id` or `role_id` is blank (API-GAP
 * G55, G66). `type` and `status` are read by [ProjectJoinType.fromCode] and [ProjectJoinStatus.fromCode] (unknown is
 * `null`); a blank candidate is `null`; the message is trimmed; the time is [instantOfUnixSeconds].
 */
fun ProjectJoinDto.toProjectJoin(): ProjectJoin? {
    if (id.isBlank() || projectId.isBlank() || roleId.isBlank()) return null
    return ProjectJoin(
        id = ProjectJoinId(id),
        projectId = ProjectId(projectId),
        roleId = RoleId(roleId),
        candidateId = candidateUserId.takeIf { it.isNotBlank() }?.let(::UserId),
        type = ProjectJoinType.fromCode(type),
        status = ProjectJoinStatus.fromCode(status),
        message = message.trim(),
        createdAt = instantOfUnixSeconds(createdAtUnix),
    )
}

/** One page of records: the readable ones of [ProjectJoinListDto.items], `received` is all the items sent ([pageOf]). */
fun ProjectJoinListDto.toProjectJoinPage(request: PageRequest): Page<ProjectJoin> =
    pageOf(items.mapNotNull { it.toProjectJoin() }, received = items.size, reportedTotal = total, request = request)
