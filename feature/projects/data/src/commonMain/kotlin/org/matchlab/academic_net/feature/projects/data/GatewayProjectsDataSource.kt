package org.matchlab.academic_net.feature.projects.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
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
import org.matchlab.academic_net.common.model.Spheres
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.paging.PageRequest
import org.matchlab.academic_net.common.result.RemoteFailure
import org.matchlab.academic_net.feature.projects.domain.MemberNotRemovableException
import org.matchlab.academic_net.feature.projects.domain.NewProject
import org.matchlab.academic_net.feature.projects.domain.ProjectConflictException
import org.matchlab.academic_net.feature.projects.domain.ProjectInfo
import org.matchlab.academic_net.feature.projects.domain.ProjectLifecycleAction
import org.matchlab.academic_net.feature.projects.domain.ProjectNotFoundException
import org.matchlab.academic_net.feature.projects.domain.ProjectsCapabilities
import org.matchlab.academic_net.feature.projects.domain.RoleDraft
import org.matchlab.academic_net.feature.projects.domain.RoleNotRemovableException
import org.matchlab.academic_net.gateway.ProjectCatalogApi
import org.matchlab.academic_net.gateway.ProjectSearchParams
import org.matchlab.academic_net.gateway.ProjectsApi
import org.matchlab.academic_net.gateway.contract.ProjectActions
import org.matchlab.academic_net.gateway.contract.UnverifiedContract
import org.matchlab.academic_net.gateway.dto.ProjectCreateDto
import org.matchlab.academic_net.gateway.dto.ProjectDto
import org.matchlab.academic_net.gateway.dto.ProjectInitialRoleDto
import org.matchlab.academic_net.gateway.dto.ProjectRoleInputDto
import org.matchlab.academic_net.gateway.dto.ProjectTransitionDto
import org.matchlab.academic_net.gateway.dto.ProjectUpdateDto
import org.matchlab.academic_net.gateway.mapping.ProjectAssembler
import org.matchlab.academic_net.gateway.mapping.ProjectTeamReader
import org.matchlab.academic_net.gateway.mapping.plannedEnd
import org.matchlab.academic_net.gateway.mapping.plannedStart
import org.matchlab.academic_net.gateway.mapping.toProject
import org.matchlab.academic_net.gateway.mapping.withTeam
import org.matchlab.academic_net.utils.suspendRunCatching

/**
 * [ProjectsDataSource] over the project calls of the Gateway ([ProjectsApi], and [ProjectCatalogApi] for the list of the
 * viewer's projects) and the cache of the projects this session has read. The values the API leaves open (type, sphere,
 * status, months, action) are the codes the app defines, see `docs/api-values.md`.
 *
 * | Member | What it does |
 * |---|---|
 * | `capabilities` | all four flags are on |
 * | `observeProject` | the cache, one project of it; `null` while it is not there |
 * | `loadProject` | `GET /projects/{id}` and the profiles of its team ([ProjectTeamReader]; they fill `Role.members`) into the cache; a 400, 403 or 404, and an answer the app cannot read (no `id` or `owner_user_id`), drop the project from the cache and give `null`; any other failure is thrown and the cache is left as it was |
 * | `getProject` | the cache without a request, else `loadProject` |
 * | `createProject` | `POST /projects` (the type as text and the sphere as its name, the first day of the first month and the last day of the last, a role for one person each); the answer goes into the cache and to the top of the list of the viewer's projects; any failure is thrown as the `RemoteFailure` it is |
 * | `unpublishProject` | `POST /projects/{id}/transitions` with the action "pause" and the cached revision (the project is read first if the cache does not have it); a 404 is [ProjectNotFoundException], a 409 is [ProjectConflictException] after the project was read again; the rest is thrown |
 * | `changeLifecycle` | like `unpublishProject`, with the action "start", "finish" or "cancel" of [ProjectActions] |
 * | `updateProject` | `PUT /projects/{id}` with the description, without roles (API-GAP G63), and the cached revision; 404 and 409 as for `unpublishProject` |
 * | `updateRole` | `PUT /projects/{id}/roles/{role_id}` with the new title and skills; `slots` and `description` are those the cache has (the PUT replaces the role); a 409 is [ProjectConflictException] after the project was read again, a 404 reads the project again too: gone is [ProjectNotFoundException], still there (the role is gone) is [ProjectConflictException] |
 * | `removeRole` | `DELETE /projects/{id}/roles/{role_id}?expected_revision=` with the cached revision; a 403 or 409 is [RoleNotRemovableException] after the project was read again (API-GAP G64); a 404 reads the project again: gone is [ProjectNotFoundException], still there counts as deleted and the project is returned |
 * | `removeMember` | `DELETE /projects/{id}/members/{user_id}` with the lock held; the answer is the project (the team is read after it); a 403 or 409 is [MemberNotRemovableException] after the project was read again (API-GAP G65); a 404 reads the project again: gone is [ProjectNotFoundException], still there counts as removed and the project is returned |
 * | `addRole` | `POST /projects/{id}/roles` with the cached revision (the project is read first if the cache does not have it); a 404 is [ProjectNotFoundException], a 409 is [ProjectConflictException] after the project was read again; the rest is thrown |
 * | `observeMyProjects` | the first collection reads `GET /projects` (see below); a failure of that is thrown to the collector, which asks again by collecting again |
 * | `getSpheres` | `Spheres.all`, no request |
 *
 * **The cache** keeps each project with the `revision` it was read at, the number a change has to send back. A project
 * read at an older revision than the cached one (a slow answer that arrived after a newer one) does not replace it.
 *
 * **The list of the viewer's projects** is not a request of its own in the API (the meaning of `mine` is not described), so
 * the first collection of `observeMyProjects` reads the catalog `GET /projects` in pages of 100, at most 10 pages (1000
 * projects) and stops at the first page that is not full, keeps the projects whose `owner_user_id` is [viewerId] (only they
 * are assembled, so the owners of the others are not looked up), drops a repeat of an `id`, and puts them into the cache. The
 * list is made of the cache: the projects created in this session come first, newest first, then the ones the catalog
 * gave, in the server's order. So the list follows every change of a cached project (a project taken off publication, a
 * role added, a project that is gone) without a request.
 *
 * **Writes** (`createProject`, `unpublishProject`, `changeLifecycle`, `updateProject`, `updateRole`, `removeRole`, `removeMember`, `addRole`) run one after another, so that the second does not send the
 * revision the first has just made stale. The answer to a write is in the cache as soon as the API has given it; the owner
 * and the team are read after that, outside the lock, and replace it. There is no automatic retry after a failure: API-GAP G48, the API has no
 * idempotency keys, and a retry after an answer that got lost could add the role, or the project, twice. After a 409 the
 * project that was read again shows the user what the server has. Once a write went through, a failure of the request for
 * the owner's profile is not the write's: the cached owner stays (G47), or the viewer is the owner of a project just
 * created.
 *
 * **The team.** The project of `loadProject` and the project of the answer to a write get the people of their `members`
 * on their roles (`Role.members`, [withTeam]): their profiles are read by [team], one request per person (API-GAP G47),
 * and a person whose profile cannot be read stays on the role without one. The list of the viewer's projects does not
 * read the team: a project of it has no `Role.members` until it is read on its own.
 *
 * **What it does not do, because the API does not define it.** API-GAP G49: a role is created for one person. API-GAP G52:
 * the status of a member is not read. API-GAP G63: the roles are never sent with the description of the project.
 *
 * @param assembler turns the server's project into the app's, with its owner.
 * @param team reads the profiles of the people of a project.
 * @param viewerId the user of the session: the projects of the catalog that are theirs are "Я инициатор".
 */
class GatewayProjectsDataSource(
    private val api: ProjectsApi,
    private val catalog: ProjectCatalogApi,
    private val assembler: ProjectAssembler,
    private val team: ProjectTeamReader,
    private val viewerId: UserId,
) : ProjectsDataSource {
    override val capabilities: ProjectsCapabilities =
        ProjectsCapabilities(canCreate = true, canListMine = true, canUnpublish = true, canChangeLifecycle = true)

    /**
     * A project of the cache, the revision the server had it at and the `description` of each of its roles as the
     * server wrote it (the app's [Role] has none, and a role update must send it back).
     */
    private class Entry(
        val project: Project,
        val revision: Int,
        val roleDescriptions: Map<RoleId, String> = emptyMap(),
    )

    private val cache = MutableStateFlow<Map<ProjectId, Entry>>(emptyMap())

    // Held by every write; a read does not take it, so a write can read the project it needs.
    private val writes = Mutex()

    // The ids of the viewer's projects the first read of the catalog found, in the server's order; null until it is done.
    private val catalogMine = MutableStateFlow<List<ProjectId>?>(null)

    // The ids of the projects the viewer created in this session, the newest first.
    private val createdMine = MutableStateFlow<List<ProjectId>>(emptyList())

    // Held by the first read of the catalog, so that two collectors do not read it twice.
    private val catalogLoad = Mutex()

    override fun observeMyProjects(): Flow<List<Project>> = flow {
        loadCatalogMine()
        emitAll(
            combine(createdMine, catalogMine.filterNotNull(), cache) { created, found, entries ->
                (created + found).distinct().mapNotNull { id -> entries[id]?.project }
            }.distinctUntilChanged(),
        )
    }

    override fun observeProject(id: ProjectId): Flow<Project?> =
        cache.map { entries -> entries[id]?.project }.distinctUntilChanged()

    override suspend fun loadProject(id: ProjectId): Project? = read(id)?.project

    override suspend fun getProject(id: ProjectId): Project? = cache.value[id]?.project ?: loadProject(id)

    // An answer without `id` or `owner_user_id` is `RemoteFailure.Malformed` although the project is already on the server:
    // pressing "Опубликовать" again would make a second one (API-GAP G48, no idempotency keys).
    override suspend fun createProject(project: NewProject): Project = write {
        val answer = api.create(project.toDto())
        val written = keep(ProjectId(answer.id), answer, known = null)
        createdMine.update { ids -> listOf(written.id) + ids.filter { it != written.id } }
        written
    }

    // The action is the app's own code, not confirmed by the backend (docs/api-values.md).
    @OptIn(UnverifiedContract::class)
    override suspend fun unpublishProject(id: ProjectId): Project = transition(id, ProjectActions.PAUSE)

    // The actions are the app's own codes, not confirmed by the backend (docs/api-values.md).
    @OptIn(UnverifiedContract::class)
    override suspend fun changeLifecycle(id: ProjectId, action: ProjectLifecycleAction): Project =
        transition(
            id,
            when (action) {
                ProjectLifecycleAction.Start -> ProjectActions.START
                ProjectLifecycleAction.Complete -> ProjectActions.COMPLETE
                ProjectLifecycleAction.Cancel -> ProjectActions.CANCEL
            },
        )

    // API-GAP G63: the roles and the id of ProjectInput are not sent; the roles change by their own calls.
    override suspend fun updateProject(id: ProjectId, info: ProjectInfo): Project = write {
        val known = cache.value[id] ?: read(id) ?: throw ProjectNotFoundException(id)
        val input = ProjectUpdateDto(
            title = info.title,
            type = info.type.code,
            field = info.sphere.id,
            problem = info.problem,
            goal = info.goal,
            tags = info.tags,
            plannedStart = info.period.plannedStart(),
            plannedEnd = info.period.plannedEnd(),
            expectedRevision = known.revision,
        )
        val answer = try {
            api.update(id.value, input)
        } catch (failure: RemoteFailure.Http) {
            throw notFoundOrConflict(id, failure)
        }
        keep(id, answer, known.project)
    }

    override suspend fun updateRole(projectId: ProjectId, roleId: RoleId, role: RoleDraft): Role {
        val project = write { changeRole(projectId, roleId, role) }
        // The cache has the answer already; an answer without the edited role is not what the API promises.
        return project.roles.firstOrNull { it.id == roleId } ?: throw RemoteFailure.Malformed()
    }

    /** The locked part of [updateRole]: the PUT of the role and the answer in the cache. */
    private suspend fun changeRole(projectId: ProjectId, roleId: RoleId, role: RoleDraft): Written {
        val known = cache.value[projectId] ?: read(projectId) ?: throw ProjectNotFoundException(projectId)
        // The role is not in the project as the app has it: the project changed meanwhile.
        val current = known.project.roles.firstOrNull { it.id == roleId } ?: throw conflictAfterReading(projectId)
        val input = ProjectRoleInputDto(
            title = role.title,
            slots = current.slots, // API-GAP G49: read as at least 1, sent back as it is
            expectedRevision = known.revision,
            skills = role.skills,
            // The PUT replaces the role: its description goes back as it came (nothing when it was empty).
            description = known.roleDescriptions[roleId]?.takeIf { it.isNotEmpty() },
        )
        val answer = try {
            api.updateRole(projectId.value, roleId.value, input)
        } catch (failure: RemoteFailure.Http) {
            when (failure.status) {
                NOT_FOUND -> throw missingAfterReading(projectId, failure)
                CONFLICT -> throw conflictAfterReading(projectId)
                else -> throw failure
            }
        }
        return keep(projectId, answer, known.project)
    }

    override suspend fun removeRole(projectId: ProjectId, roleId: RoleId): Project = write {
        val known = cache.value[projectId] ?: read(projectId) ?: throw ProjectNotFoundException(projectId)
        val answer = try {
            api.deleteRole(projectId.value, roleId.value, known.revision)
        } catch (failure: RemoteFailure.Http) {
            when (failure.status) {
                NOT_FOUND -> {
                    // The project read again is the answer, when the role is gone from it; a role that is still there
                    // means the server and the app disagree: the project changed.
                    val reread = read(projectId)?.project ?: throw ProjectNotFoundException(projectId)
                    if (reread.roles.any { it.id == roleId }) throw ProjectConflictException(projectId)
                    return@write Written(projectId, reread, answer = null)
                }
                FORBIDDEN, CONFLICT -> {
                    // API-GAP G64: the server's reason is not described; the project is read again for what it has.
                    val reread = suspendRunCatching { read(projectId) }
                    if (reread.isSuccess && reread.getOrNull() == null) throw ProjectNotFoundException(projectId)
                    throw RoleNotRemovableException(projectId, roleId)
                }
                else -> throw failure
            }
        }
        keep(projectId, answer, known.project)
    }

    override suspend fun removeMember(projectId: ProjectId, userId: UserId): Project = write {
        val known = cache.value[projectId] ?: read(projectId) ?: throw ProjectNotFoundException(projectId)
        val answer = try {
            api.removeMember(projectId.value, userId.value)
        } catch (failure: RemoteFailure.Http) {
            when (failure.status) {
                NOT_FOUND -> {
                    // The project read again is the answer: gone is not found, still there counts as removed.
                    val reread = read(projectId)?.project ?: throw ProjectNotFoundException(projectId)
                    return@write Written(projectId, reread, answer = null)
                }
                FORBIDDEN, CONFLICT -> {
                    // API-GAP G65: the server's reason is not described; the project is read again for what it has.
                    val reread = suspendRunCatching { read(projectId) }
                    if (reread.isSuccess && reread.getOrNull() == null) throw ProjectNotFoundException(projectId)
                    throw MemberNotRemovableException(projectId, userId)
                }
                else -> throw failure
            }
        }
        keep(projectId, answer, known.project)
    }

    override suspend fun addRole(projectId: ProjectId, role: RoleDraft): Role {
        var knownRoleIds = emptySet<RoleId>()
        val project = write {
            val known = cache.value[projectId] ?: read(projectId) ?: throw ProjectNotFoundException(projectId)
            knownRoleIds = known.project.roles.map { it.id }.toSet()
            val input = ProjectRoleInputDto(
                title = role.title,
                slots = ONE_PERSON, // API-GAP G49: the app creates roles for one person
                expectedRevision = known.revision,
                skills = role.skills,
            )
            val answer = try {
                api.addRole(projectId.value, input)
            } catch (failure: RemoteFailure.Http) {
                throw notFoundOrConflict(projectId, failure)
            }
            keep(projectId, answer, known.project)
        }
        return newRoleOf(project, knownRoleIds, asked = role)
    }

    override suspend fun getSpheres(): List<Sphere> = Spheres.all

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
        return remember(id, Entry(withTeam(assembled.project), dto.revision, dto.roleDescriptions()))
    }

    /**
     * [project] with the people of its team on its roles. Their profiles are asked for through [team], which never fails
     * the team for one person; should it fail anyway, the project stays without the people on its roles, as in a list.
     */
    private suspend fun withTeam(project: Project): Project {
        if (project.members.isEmpty()) return project
        val profiles = suspendRunCatching { team.read(project.members) }.getOrNull() ?: return project
        return project.withTeam(profiles)
    }

    /**
     * What a write left behind: the project as the cache has it, and the server's [answer] to settle it from (`null` when
     * the project came from a read, which is settled already).
     */
    private class Written(val id: ProjectId, val project: Project, val answer: ProjectDto?)

    /**
     * Runs a write one after another with the others ([writes]) and then settles it. The locked part ends with the
     * answer of the API in the cache ([keep]); the owner and the team are read after the lock is released ([settle]).
     */
    private suspend fun write(block: suspend () -> Written): Project = settle(writes.withLock { block() })

    /**
     * The first end of a write that went through: the project of [answer] goes into the cache at once, with the revision
     * and the role descriptions of the answer, so that a coroutine cancelled while the profiles are read afterwards
     * leaves no stale revision behind. It has no team yet and, as the owner is not asked for, the owner of [known] (the
     * owner does not change with a write; a project just created has the viewer for its owner, without a name), and the
     * viewer's part in the project. An answer the app cannot read at all (no `id` or `owner_user_id`) is
     * [RemoteFailure.Malformed], as for a read.
     */
    private fun keep(id: ProjectId, answer: ProjectDto, known: Project?): Written {
        val unassembled = answer.toProject(viewerId, owner = null) ?: throw RemoteFailure.Malformed()
        val project = if (known == null) {
            unassembled
        } else {
            unassembled.copy(initiator = known.initiator, viewerIsInitiator = known.viewerIsInitiator)
        }
        remember(id, Entry(project, answer.revision, answer.roleDescriptions()))
        return Written(id, project, answer)
    }

    /**
     * The second end of a write: the owner of the project and its team are asked for like everywhere else (API-GAP G47)
     * and the project with them replaces the one [keep] cached, unless a newer revision has come meanwhile. A failure of
     * the owner's request does not turn the write into a failure (the change is on the server, an error would only invite
     * the user to do it again): the project stays as [keep] left it. Cancelled here, the write has been cached anyway.
     */
    private suspend fun settle(written: Written): Project {
        val answer = written.answer ?: return written.project
        val assembled = suspendRunCatching { assembler.assemble(listOf(answer)) }.getOrNull()
        val base = assembled?.firstOrNull()?.project ?: written.project
        val project = withTeam(base)
        remember(written.id, Entry(project, answer.revision, answer.roleDescriptions()))
        return project
    }

    /**
     * Moves the project with the [action] code and the cached revision (the project is read first if the cache does not
     * have it). A 404 is [ProjectNotFoundException], a 409 is [ProjectConflictException] after the project was read again.
     */
    private suspend fun transition(id: ProjectId, action: String): Project = write {
        val known = cache.value[id] ?: read(id) ?: throw ProjectNotFoundException(id)
        val answer = try {
            api.transition(id.value, ProjectTransitionDto(action = action, expectedRevision = known.revision))
        } catch (failure: RemoteFailure.Http) {
            throw notFoundOrConflict(id, failure)
        }
        keep(id, answer, known.project)
    }

    /**
     * What a failed write of the project [id] is: a 404 is [ProjectNotFoundException] and the project leaves the cache, a
     * 409 is [ProjectConflictException] after the project was read again; any other failure is [failure] itself. Returned,
     * not thrown, so that the caller's `throw` shows where the call ends.
     */
    private suspend fun notFoundOrConflict(id: ProjectId, failure: RemoteFailure.Http): Throwable =
        when (failure.status) {
            NOT_FOUND -> {
                forget(id)
                ProjectNotFoundException(id)
            }
            CONFLICT -> conflictAfterReading(id)
            else -> failure
        }

    /**
     * A 404 of a call that names a part of the project (a role): the project is read again. Gone is
     * [ProjectNotFoundException]; still there means the part is gone, which is [ProjectConflictException] (the project
     * the user saw has changed). If the project cannot be read, the conflict is returned with that failure attached.
     */
    private suspend fun missingAfterReading(id: ProjectId, failure: RemoteFailure.Http): Throwable {
        val reread = suspendRunCatching { read(id) }
        reread.exceptionOrNull()?.let { return ProjectConflictException(id).also { conflict -> conflict.addSuppressed(it) } }
        return if (reread.getOrNull() == null) ProjectNotFoundException(id) else ProjectConflictException(id).also { it.addSuppressed(failure) }
    }

    private fun ProjectDto.roleDescriptions(): Map<RoleId, String> =
        roles.filter { it.id.isNotEmpty() }.associate { RoleId(it.id) to it.description }

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
     * Reads the catalog once per session into [catalogMine] and the cache. Nothing is kept when a request fails: the
     * failure is thrown and the next call reads again.
     */
    private suspend fun loadCatalogMine() {
        if (catalogMine.value != null) return
        catalogLoad.withLock {
            if (catalogMine.value != null) return
            val own = readCatalogMine()
            // The cache first: the list is made of it, and must not be shown with a project missing.
            // A project the cache already has at this revision stays as it is: it may be the richer one (it has its team).
            own.forEach { (id, entry) -> if (cache.value[id]?.let { it.revision >= entry.revision } != true) remember(id, entry) }
            catalogMine.value = own.keys.toList()
        }
    }

    /** The viewer's projects of the catalog, in the server's order, each once. */
    private suspend fun readCatalogMine(): Map<ProjectId, Entry> {
        val own = linkedMapOf<ProjectId, Entry>()
        val seen = mutableSetOf<String>()
        for (pageIndex in 0 until MINE_MAX_PAGES) {
            val page = PageRequest(offset = pageIndex * MINE_PAGE_SIZE, limit = MINE_PAGE_SIZE)
            val answer = catalog.list(ProjectSearchParams(), page)
            // Only the viewer's own are assembled: the owners of the others are of no interest here.
            val ours = answer.items.filter { dto ->
                dto.id.isNotBlank() && dto.ownerUserId == viewerId.value && seen.add(dto.id)
            }
            if (ours.isNotEmpty()) {
                val byId = ours.associateBy { it.id }
                for (assembled in assembler.assemble(ours)) {
                    val project = assembled.project
                    if (project.viewerIsInitiator && project.id !in own) {
                        val dto = byId[project.id.value]
                        own[project.id] = Entry(project, dto?.revision ?: 0, dto?.roleDescriptions().orEmpty())
                    }
                }
            }
            if (answer.items.size < MINE_PAGE_SIZE) break
        }
        return own
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
        const val FORBIDDEN = 403
        const val CONFLICT = 409

        /** 400 (not a project id), 403 (not allowed to see it) and 404 (unknown): the project is not there for the viewer. */
        val NOT_VISIBLE = setOf(400, 403, 404)

        /** The catalog is read in the largest pages the API gives, up to [MINE_MAX_PAGES] of them. */
        const val MINE_PAGE_SIZE = PageRequest.MaxLimit
        const val MINE_MAX_PAGES = 10
    }
}

/** A place for one person (API-GAP G49): every role the app creates has one. */
private const val ONE_PERSON = 1

/**
 * The body of `POST /projects` for the project: the type as the text and the sphere as the name the app defines, the first day of the
 * month it starts in and the last day of the month it ends in, a role for one person each (`docs/api-values.md`).
 */
internal fun NewProject.toDto(): ProjectCreateDto = ProjectCreateDto(
    title = title,
    type = type.code,
    field = sphere.id,
    problem = problem,
    goal = goal,
    tags = tags,
    plannedStart = period.plannedStart(),
    plannedEnd = period.plannedEnd(),
    roles = roles.map { role -> ProjectInitialRoleDto(title = role.title, skills = role.skills, slots = ONE_PERSON) },
)
