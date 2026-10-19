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
import org.matchlab.academic_net.feature.projects.domain.NewProject
import org.matchlab.academic_net.feature.projects.domain.ProjectConflictException
import org.matchlab.academic_net.feature.projects.domain.ProjectNotFoundException
import org.matchlab.academic_net.feature.projects.domain.ProjectsCapabilities
import org.matchlab.academic_net.feature.projects.domain.RoleDraft
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
import org.matchlab.academic_net.gateway.mapping.ProjectAssembler
import org.matchlab.academic_net.gateway.mapping.plannedEnd
import org.matchlab.academic_net.gateway.mapping.plannedStart
import org.matchlab.academic_net.gateway.mapping.toProject
import org.matchlab.academic_net.utils.suspendRunCatching

/**
 * [ProjectsDataSource] over the project calls of the Gateway ([ProjectsApi], and [ProjectCatalogApi] for the list of the
 * viewer's projects) and the cache of the projects this session has read. The values the API leaves open (type, sphere,
 * status, months, action) are the codes the app defines, see `docs/api-values.md`.
 *
 * | Member | What it does |
 * |---|---|
 * | `capabilities` | all three flags are on |
 * | `observeProject` | the cache, one project of it; `null` while it is not there |
 * | `loadProject` | `GET /projects/{id}` into the cache; a 400, 403 or 404, and an answer the app cannot read (no `id` or `owner_user_id`), drop the project from the cache and give `null`; any other failure is thrown and the cache is left as it was |
 * | `getProject` | the cache without a request, else `loadProject` |
 * | `createProject` | `POST /projects` (the type and the sphere as codes, the first day of the first month and the last day of the last, a role for one person each); the answer goes into the cache and to the top of the list of the viewer's projects; any failure is thrown as the `RemoteFailure` it is |
 * | `unpublishProject` | `POST /projects/{id}/transitions` with the action "pause" and the cached revision (the project is read first if the cache does not have it); a 404 is [ProjectNotFoundException], a 409 is [ProjectConflictException] after the project was read again; the rest is thrown |
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
 * **Writes** (`createProject`, `unpublishProject`, `addRole`) run one after another, so that the second does not send the
 * revision the first has just made stale. There is no automatic retry after a failure: API-GAP G48, the API has no
 * idempotency keys, and a retry after an answer that got lost could add the role, or the project, twice. After a 409 the
 * project that was read again shows the user what the server has. Once a write went through, a failure of the request for
 * the owner's profile is not the write's: the cached owner stays (G47), or the viewer is the owner of a project just
 * created.
 *
 * **What it does not do, because the API does not define it.** API-GAP G49: a role is created for one person. API-GAP G52:
 * the members of a role are not read.
 *
 * @param assembler turns the server's project into the app's, with its owner.
 * @param viewerId the user of the session: the projects of the catalog that are theirs are "Я инициатор".
 */
class GatewayProjectsDataSource(
    private val api: ProjectsApi,
    private val catalog: ProjectCatalogApi,
    private val assembler: ProjectAssembler,
    private val viewerId: UserId,
) : ProjectsDataSource {
    override val capabilities: ProjectsCapabilities =
        ProjectsCapabilities(canCreate = true, canListMine = true, canUnpublish = true)

    /** A project of the cache and the revision the server had it at. */
    private class Entry(val project: Project, val revision: Int)

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
    override suspend fun createProject(project: NewProject): Project = writes.withLock {
        val answer = api.create(project.toDto())
        val created = projectAfterWrite(answer, known = null)
        remember(created.id, Entry(created, answer.revision))
        createdMine.update { ids -> listOf(created.id) + ids.filter { it != created.id } }
        created
    }

    // The action is the app's own code, not confirmed by the backend (docs/api-values.md).
    @OptIn(UnverifiedContract::class)
    override suspend fun unpublishProject(id: ProjectId): Project = writes.withLock {
        val known = cache.value[id] ?: read(id) ?: throw ProjectNotFoundException(id)
        val input = ProjectTransitionDto(action = ProjectActions.PAUSE, expectedRevision = known.revision)
        val answer = try {
            api.transition(id.value, input)
        } catch (failure: RemoteFailure.Http) {
            when (failure.status) {
                NOT_FOUND -> {
                    forget(id)
                    throw ProjectNotFoundException(id)
                }
                CONFLICT -> throw conflictAfterReading(id)
                else -> throw failure
            }
        }
        val project = projectAfterWrite(answer, known.project)
        remember(id, Entry(project, answer.revision))
        project
    }

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
     * Reads the catalog once per session into [catalogMine] and the cache. Nothing is kept when a request fails: the
     * failure is thrown and the next call reads again.
     */
    private suspend fun loadCatalogMine() {
        if (catalogMine.value != null) return
        catalogLoad.withLock {
            if (catalogMine.value != null) return
            val own = readCatalogMine()
            // The cache first: the list is made of it, and must not be shown with a project missing.
            own.forEach { (id, entry) -> remember(id, entry) }
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
                val revisions = ours.associate { it.id to it.revision }
                for (assembled in assembler.assemble(ours)) {
                    val project = assembled.project
                    if (project.viewerIsInitiator && project.id !in own) {
                        own[project.id] = Entry(project, revisions[project.id.value] ?: 0)
                    }
                }
            }
            if (answer.items.size < MINE_PAGE_SIZE) break
        }
        return own
    }

    /**
     * The project of the answer to a write that went through. Its owner is asked for like everywhere else (API-GAP
     * G47), but a failure of that request does not turn the write into a failure: the role, or the project, is on the
     * server already, and an error would only invite the user to do it again. The owner of the cached project [known]
     * stays then (the owner does not change with a role), as does the viewer's part in the project; a project just
     * created ([known] is `null`) has the viewer for its owner, without a name. An answer the app cannot read at all (no
     * `id` or no `owner_user_id`) is [RemoteFailure.Malformed], as for a read.
     */
    private suspend fun projectAfterWrite(answer: ProjectDto, known: Project?): Project {
        val assembled = suspendRunCatching { assembler.assemble(listOf(answer)) }.getOrNull()
        if (assembled != null) return assembled.firstOrNull()?.project ?: throw RemoteFailure.Malformed()
        val unassembled = answer.toProject(viewerId, owner = null) ?: throw RemoteFailure.Malformed()
        return if (known == null) {
            unassembled
        } else {
            unassembled.copy(initiator = known.initiator, viewerIsInitiator = known.viewerIsInitiator)
        }
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
 * The body of `POST /projects` for the project: the type and the sphere as the codes the app defines, the first day of the
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
