package org.matchlab.academic_net.feature.projects.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.matchlab.academic_net.common.model.Project
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.Role
import org.matchlab.academic_net.common.model.RoleId
import org.matchlab.academic_net.common.model.Sphere
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.result.FeatureUnavailableException
import org.matchlab.academic_net.common.result.RemoteFailure
import org.matchlab.academic_net.feature.projects.domain.NewProject
import org.matchlab.academic_net.feature.projects.domain.ProjectConflictException
import org.matchlab.academic_net.feature.projects.domain.ProjectNotFoundException
import org.matchlab.academic_net.feature.projects.domain.ProjectsCapabilities
import org.matchlab.academic_net.feature.projects.domain.RoleDraft
import org.matchlab.academic_net.gateway.ProjectsApi
import org.matchlab.academic_net.gateway.dto.ProjectDto
import org.matchlab.academic_net.gateway.dto.ProjectRoleInputDto
import org.matchlab.academic_net.gateway.mapping.ProjectAssembler
import org.matchlab.academic_net.gateway.mapping.toProject
import org.matchlab.academic_net.utils.suspendRunCatching

/**
 * [ProjectsDataSource] over the project calls of the Gateway ([ProjectsApi]) and the cache of the projects this session
 * has read.
 *
 * | Member | What it does |
 * |---|---|
 * | `capabilities` | all three flags are off |
 * | `observeProject` | the cache, one project of it; `null` while it is not there |
 * | `loadProject` | `GET /projects/{id}` into the cache; a 400, 403 or 404, and an answer the app cannot read (no `id` or `owner_user_id`), drop the project from the cache and give `null`; any other failure is thrown and the cache is left as it was |
 * | `getProject` | the cache without a request, else `loadProject` |
 * | `addRole` | `POST /projects/{id}/roles` with the cached revision (the project is read first if the cache does not have it); a 404 is [ProjectNotFoundException], a 409 is [ProjectConflictException] after the project was read again; the rest is thrown. Once the write went through, a failure of the request for the owner's profile is not the write's: the cached owner stays (G47) |
 * | `getSpheres` | [SphereCatalog], no request |
 * | `observeMyProjects`, `createProject`, `unpublishProject` | throw [FeatureUnavailableException]: G44, G40 (with G43) and G42 |
 *
 * **The cache** keeps each project with the `revision` it was read at, the number a change has to send back. A project
 * read at an older revision than the cached one (a slow answer that arrived after a newer one) does not replace it.
 *
 * **Writes** (now only `addRole`) run one after another, so that the second does not send the revision the first has
 * just made stale. There is no automatic retry after a 409: API-GAP G48, the API has no idempotency keys, and a retry
 * after an answer that got lost could add the role twice. The project that was read again shows the user what the
 * server has.
 *
 * **What it does not do, because the API does not define it.** API-GAP G40, G41, G43: the `type`, `status` and
 * `planned_*` of a project are not read. API-GAP G44: the list of the viewer's projects is not asked for. API-GAP G42:
 * the project is not taken off publication. API-GAP G49: a role is created for one person. API-GAP G52: the members of
 * a role are not read. API-GAP G46: the spheres are the app's own list.
 *
 * @param assembler turns the server's project into the app's, with its owner.
 */
class GatewayProjectsDataSource(
    private val api: ProjectsApi,
    private val assembler: ProjectAssembler,
) : ProjectsDataSource {
    // API-GAP G40, G43 (create), G44 (the list of the viewer's projects) and G42 (unpublish) are not closed: all off.
    override val capabilities: ProjectsCapabilities =
        ProjectsCapabilities(canCreate = false, canListMine = false, canUnpublish = false)

    /** A project of the cache and the revision the server had it at. */
    private class Entry(val project: Project, val revision: Int)

    private val cache = MutableStateFlow<Map<ProjectId, Entry>>(emptyMap())

    // Held by every write; a read does not take it, so a write can read the project it needs.
    private val writes = Mutex()

    // API-GAP G44: `mine` of `GET /projects` has no list of values, so there is nothing to ask for.
    override fun observeMyProjects(): Flow<List<Project>> = flow { throw FeatureUnavailableException("G44") }

    override fun observeProject(id: ProjectId): Flow<Project?> =
        cache.map { entries -> entries[id]?.project }.distinctUntilChanged()

    override suspend fun loadProject(id: ProjectId): Project? = read(id)?.project

    override suspend fun getProject(id: ProjectId): Project? = cache.value[id]?.project ?: loadProject(id)

    // API-GAP G40, G43: `POST /projects` requires a `type` and `planned_*` the API does not define.
    override suspend fun createProject(project: NewProject): Project = throw FeatureUnavailableException("G40")

    // API-GAP G42: `action` of `POST /projects/{id}/transitions` has no list of values.
    override suspend fun unpublishProject(id: ProjectId): Project = throw FeatureUnavailableException("G42")

    override suspend fun addRole(projectId: ProjectId, role: RoleDraft): Role = writes.withLock {
        val known = cache.value[projectId] ?: read(projectId) ?: throw ProjectNotFoundException(projectId)
        val input = ProjectRoleInputDto(
            title = role.title,
            slots = ONE_PERSON, // API-GAP G49: the app creates roles for one person
            expectedRevision = known.revision,
            skills = role.skills,
        )
        val answer = try {
            api.addRole(projectId.value, input)
        } catch (failure: RemoteFailure.Http) {
            when (failure.status) {
                NOT_FOUND -> {
                    forget(projectId)
                    throw ProjectNotFoundException(projectId)
                }
                CONFLICT -> throw conflictAfterReading(projectId)
                else -> throw failure
            }
        }
        val project = projectAfterWrite(answer, known.project)
        remember(projectId, Entry(project, answer.revision))
        newRoleOf(project, knownRoleIds = known.project.roles.map { it.id }.toSet(), asked = role)
    }

    override suspend fun getSpheres(): List<Sphere> = SphereCatalog.spheres

    /**
     * Asks the server for the project and keeps the answer. `null`, with the project dropped from the cache, when the
     * viewer may not see it (a 400, 403 or 404) or the answer cannot be read; other failures are thrown.
     */
    private suspend fun read(id: ProjectId): Entry? {
        val dto = try {
            api.get(id.value)
        } catch (failure: RemoteFailure.Http) {
            if (failure.status !in NOT_VISIBLE) throw failure
            forget(id)
            return null
        }
        val assembled = assembler.assemble(listOf(dto)).firstOrNull()
        if (assembled == null) {
            forget(id)
            return null
        }
        return remember(id, Entry(assembled.project, dto.revision))
    }

    /** Keeps [entry] unless the cache has a newer revision of the project; returns what the cache has now. */
    private fun remember(id: ProjectId, entry: Entry): Entry {
        val kept = cache.updateAndGet { entries ->
            val known = entries[id]
            if (known != null && known.revision > entry.revision) entries else entries + (id to entry)
        }
        return kept.getValue(id)
    }

    private fun forget(id: ProjectId) {
        cache.update { entries -> entries - id }
    }

    /**
     * The project of the answer to a write that went through. Its owner is asked for like everywhere else (API-GAP
     * G47), but a failure of that request does not turn the write into a failure: the role is on the server already,
     * and an error would only invite the user to add it again. The owner of the cached project [known] stays then (the
     * owner does not change with a role), as does the viewer's part in the project. An answer the app cannot read at
     * all (no `id` or no `owner_user_id`) is [RemoteFailure.Malformed], as for a read.
     */
    private suspend fun projectAfterWrite(answer: ProjectDto, known: Project): Project {
        val assembled = suspendRunCatching { assembler.assemble(listOf(answer)) }.getOrNull()
        if (assembled != null) return assembled.firstOrNull()?.project ?: throw RemoteFailure.Malformed()
        return answer.toProject(viewerId = UserId(answer.ownerUserId), owner = null)
            ?.copy(initiator = known.initiator, viewerIsInitiator = known.viewerIsInitiator)
            ?: throw RemoteFailure.Malformed()
    }

    /**
     * The conflict to throw for a 409: the project is read again first, so that [observeProject] shows what the server
     * has. If that fails too, the failure is attached to the conflict, which stays the outcome.
     */
    private suspend fun conflictAfterReading(projectId: ProjectId): ProjectConflictException {
        val conflict = ProjectConflictException(projectId)
        suspendRunCatching { loadProject(projectId) }.onFailure { conflict.addSuppressed(it) }
        return conflict
    }

    /**
     * The role the server added: the one of [project] whose id the cache did not have before the request. If there are
     * several, the last one with the title that was [asked] for; if there are none, or none of several is ours, the
     * answer is not what the API promises.
     */
    private fun newRoleOf(project: Project, knownRoleIds: Set<RoleId>, asked: RoleDraft): Role {
        val added = project.roles.filter { it.id !in knownRoleIds }
        return when {
            added.size == 1 -> added.single()
            else -> added.lastOrNull { it.title == asked.title } ?: throw RemoteFailure.Malformed()
        }
    }

    private companion object {
        const val NOT_FOUND = 404
        const val CONFLICT = 409

        /** A place for one person (API-GAP G49). */
        const val ONE_PERSON = 1

        /** 400 (not a project id), 403 (not allowed to see it) and 404 (unknown): the project is not there for the viewer. */
        val NOT_VISIBLE = setOf(400, 403, 404)
    }
}
