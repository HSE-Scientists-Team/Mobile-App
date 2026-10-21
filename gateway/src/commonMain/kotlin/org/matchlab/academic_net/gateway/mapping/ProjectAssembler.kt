package org.matchlab.academic_net.gateway.mapping

import org.matchlab.academic_net.common.model.Project
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.gateway.UserDirectory
import org.matchlab.academic_net.gateway.dto.ProjectDto
import org.matchlab.academic_net.gateway.dto.UserProfileDto

/** A project as the app shows it, with the public profile of its owner (`null`: none the viewer may see). */
data class AssembledProject(
    val project: Project,
    val owner: UserProfileDto?,
)

/**
 * Turns the server's projects into the app's. API-GAP G47: a project names its owner by id only, so the owners are
 * looked up separately. Shared by the projects, the search and the page of a project; their data layers are tested
 * with a fake of it.
 */
fun interface ProjectAssembler {
    /**
     * The readable [projects] (see [toProject]) in their order, with their owners. The owners are asked for together,
     * each distinct id once; a failure of that request, other than "no such person", is thrown.
     */
    suspend fun assemble(projects: List<ProjectDto>): List<AssembledProject>
}

/** The [ProjectAssembler] of a session: owners come from [users]; [viewerId] tells the viewer's own projects. */
class DirectoryProjectAssembler(
    private val users: UserDirectory,
    private val viewerId: UserId,
) : ProjectAssembler {
    override suspend fun assemble(projects: List<ProjectDto>): List<AssembledProject> {
        val readable = projects.filter { it.id.isNotBlank() && it.ownerUserId.isNotBlank() }
        if (readable.isEmpty()) return emptyList()
        val owners = users.profiles(readable.map { it.ownerUserId })
        return readable.mapNotNull { dto ->
            val owner = owners[dto.ownerUserId]
            dto.toProject(viewerId, owner)?.let { AssembledProject(it, owner) }
        }
    }
}
