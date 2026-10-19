package org.matchlab.academic_net.feature.projectsearch.ui

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.matchlab.academic_net.common.model.Project
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.Role
import org.matchlab.academic_net.common.model.RoleId
import org.matchlab.academic_net.common.model.ProjectPeriod
import org.matchlab.academic_net.common.model.ProjectStatus
import org.matchlab.academic_net.common.model.ProjectType
import org.matchlab.academic_net.common.model.Sphere
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.model.UserSummary
import org.matchlab.academic_net.common.paging.Page
import org.matchlab.academic_net.common.paging.PageRequest
import org.matchlab.academic_net.feature.projectsearch.domain.FoundProject
import org.matchlab.academic_net.feature.projectsearch.domain.GetSearchSpheresUseCase
import org.matchlab.academic_net.feature.projectsearch.domain.ProjectQuery
import org.matchlab.academic_net.feature.projectsearch.domain.ProjectSearchCapabilities
import org.matchlab.academic_net.feature.projectsearch.domain.ProjectSearchRepository
import org.matchlab.academic_net.feature.projectsearch.domain.SearchProjectsUseCase
import org.matchlab.academic_net.feature.projectsearch.domain.SphereProvider

// What the tests of the project search share: the main dispatcher of a test, a fake repository that plays the server (with
// gates to hold an answer back), a fake provider of the spheres, and the projects to find.

@OptIn(ExperimentalCoroutinesApi::class)
internal fun installMainDispatcher() = Dispatchers.setMain(UnconfinedTestDispatcher())

@OptIn(ExperimentalCoroutinesApi::class)
internal fun uninstallMainDispatcher() = Dispatchers.resetMain()

/** Lets [millis] of virtual time pass and runs what is due at the end of it. */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun TestScope.passTime(millis: Long) {
    testScheduler.advanceTimeBy(millis)
    testScheduler.runCurrent()
}

internal val ComputerScience = Sphere("Computer Science", "Computer Science")
internal val Biology = Sphere("Биология", "Биология")

/** A hit with the project `p<id>`; the viewer's own when [own]. */
internal fun found(
    id: String,
    own: Boolean = false,
    university: String = "МГУ",
    roles: Int = 1,
    sphere: Sphere? = ComputerScience,
    type: ProjectType? = null,
    status: ProjectStatus? = null,
    period: ProjectPeriod? = null,
): FoundProject = FoundProject(
    project = Project(
        id = ProjectId("p$id"),
        title = "Проект $id",
        type = type,
        sphere = sphere,
        problem = "Проблема",
        goal = "Цель",
        tags = emptyList(),
        initiator = UserSummary(UserId("u$id"), "Автор $id", isVerified = true),
        roles = List(roles) { Role(RoleId("r$it"), "Роль $it") },
        status = status,
        period = period,
        viewerIsInitiator = own,
    ),
    initiatorUniversity = university,
)

/** The hits `p1`..`pN`, the way the server lists them. */
internal fun foundProjects(count: Int): List<FoundProject> = List(count) { found("${it + 1}") }

/** The ids `p<from>`..`p<to>` of the server's list. */
internal fun projectIds(from: Int, to: Int): List<String> = (from..to).map { "p$it" }

/** One request to the search, as the repository saw it. */
internal data class SearchCall(val query: ProjectQuery, val page: PageRequest)

/** An answer held back until the test opens the gate. */
internal class Gate {
    private val opened = CompletableDeferred<Unit>()

    /** Waits for [open]; a cancellation of the waiter ends the wait. */
    suspend fun await() = opened.await()

    /** Waits for [open] and keeps going after a cancellation of the waiter: an answer that arrives too late. */
    suspend fun awaitEvenIfCancelled() = withContext(NonCancellable) { opened.await() }

    fun open() {
        opened.complete(Unit)
    }
}

/**
 * The server behind the search. It lists [server] in order and answers a request with the slice the request asks for and
 * the whole count as the total, unless the test changes [serve].
 *
 * - [failure]: every request fails with it (after its gate).
 * - [gated]: every request waits at a gate of its own; [gates] holds the gates of the requests made while it was on, in
 *   order. With [lateAnswers] the wait ignores a cancellation of the request.
 */
internal class FakeProjectSearchRepository(var server: List<FoundProject> = emptyList()) : ProjectSearchRepository {
    override var capabilities = ProjectSearchCapabilities(typeFilter = false, durationFilter = false)

    val searches = mutableListOf<SearchCall>()

    var serve: (ProjectQuery, PageRequest) -> Page<FoundProject> = { _, page -> sliceOfServer(page) }
    var failure: Throwable? = null
    var gated = false
    val gates = mutableListOf<Gate>()

    /** Gated requests go on after a cancellation, like an answer that comes just as the request is given up. */
    var lateAnswers = false

    fun sliceOfServer(page: PageRequest): Page<FoundProject> =
        Page(server.drop(page.offset).take(page.limit), server.size, page)

    override suspend fun search(query: ProjectQuery, page: PageRequest): Page<FoundProject> {
        searches += SearchCall(query, page)
        if (gated) {
            val gate = Gate().also { gates += it }
            if (lateAnswers) gate.awaitEvenIfCancelled() else gate.await()
        }
        failure?.let { throw it }
        return serve(query, page)
    }
}

/** The spheres of the filter: what it answers, whether it fails, and how many times it was asked. */
internal class FakeSphereProvider(var spheres: List<Sphere> = listOf(ComputerScience, Biology)) : SphereProvider {
    var failure: Throwable? = null
    var calls = 0
    var gate: Gate? = null

    override suspend fun getSpheres(): List<Sphere> {
        calls++
        gate?.await()
        failure?.let { throw it }
        return spheres
    }
}

internal fun uiDependencies(
    repository: FakeProjectSearchRepository,
    spheres: FakeSphereProvider = FakeSphereProvider(),
): ProjectSearchUiDependencies = ProjectSearchUiDependencies(
    searchProjects = SearchProjectsUseCase(repository),
    getSpheres = GetSearchSpheresUseCase(spheres),
    capabilities = repository.capabilities,
)
