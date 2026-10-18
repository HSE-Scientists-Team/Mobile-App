package org.matchlab.academic_net.feature.collaborators.ui

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.matchlab.academic_net.common.model.Collaborator
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.RoleId
import org.matchlab.academic_net.common.model.Study
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.model.UserSummary
import org.matchlab.academic_net.common.paging.Page
import org.matchlab.academic_net.common.paging.PageRequest
import org.matchlab.academic_net.feature.collaborators.domain.CollaboratorQuery
import org.matchlab.academic_net.feature.collaborators.domain.CollaboratorsCapabilities
import org.matchlab.academic_net.feature.collaborators.domain.CollaboratorsRepository
import org.matchlab.academic_net.feature.collaborators.domain.RoleRequirement
import org.matchlab.academic_net.feature.collaborators.domain.RoleRequirementProvider
import org.matchlab.academic_net.feature.collaborators.domain.usecase.CountCollaboratorsUseCase
import org.matchlab.academic_net.feature.collaborators.domain.usecase.GetRoleRequirementUseCase
import org.matchlab.academic_net.feature.collaborators.domain.usecase.SearchCollaboratorsUseCase

// What the tests of the people search share: the main dispatcher of a test, a fake repository that plays the server (with
// gates to hold an answer back), a fake provider of the role, and the people to find.

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

internal fun person(id: String, name: String = "Имя $id"): Collaborator =
    Collaborator(UserSummary(UserId(id), name), "МГУ", Study("магистратура", 2), "physics")

/** The people `u1`..`uN`, the way the server lists them. */
internal fun people(count: Int): List<Collaborator> = List(count) { person("u${it + 1}") }

/** The ids `u<from>`..`u<to>` of the server's list. */
internal fun userIds(from: Int, to: Int): List<String> = (from..to).map { "u$it" }

/** One request to the search, as the repository saw it. */
internal data class SearchCall(val query: CollaboratorQuery, val page: PageRequest)

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
 * - The same for the counts: [countFailure], [countGated], [countGates], [countAnswer].
 */
internal class FakeCollaboratorsRepository(var server: List<Collaborator> = emptyList()) : CollaboratorsRepository {
    override var capabilities = CollaboratorsCapabilities(projectTypeFilter = false)

    val searches = mutableListOf<SearchCall>()
    val counts = mutableListOf<CollaboratorQuery>()

    var serve: (CollaboratorQuery, PageRequest) -> Page<Collaborator> = { _, page -> sliceOfServer(page) }
    var failure: Throwable? = null
    var gated = false
    val gates = mutableListOf<Gate>()

    var countAnswer: (CollaboratorQuery) -> Int = { server.size }
    var countFailure: Throwable? = null
    var countGated = false
    val countGates = mutableListOf<Gate>()

    /** Gated requests go on after a cancellation, like an answer that comes just as the request is given up. */
    var lateAnswers = false

    fun sliceOfServer(page: PageRequest): Page<Collaborator> =
        Page(server.drop(page.offset).take(page.limit), server.size, page)

    override suspend fun search(query: CollaboratorQuery, page: PageRequest): Page<Collaborator> {
        searches += SearchCall(query, page)
        if (gated) {
            val gate = Gate().also { gates += it }
            if (lateAnswers) gate.awaitEvenIfCancelled() else gate.await()
        }
        failure?.let { throw it }
        return serve(query, page)
    }

    override suspend fun count(query: CollaboratorQuery): Int {
        counts += query
        if (countGated) {
            val gate = Gate().also { countGates += it }
            if (lateAnswers) gate.awaitEvenIfCancelled() else gate.await()
        }
        countFailure?.let { throw it }
        return countAnswer(query)
    }
}

/** The role a search is started for: what it asks for, whether it fails, and a gate to hold the answer back. */
internal class FakeRoleProvider : RoleRequirementProvider {
    var requirement: RoleRequirement? = RoleRequirement("Цифровой двойник", "ML Engineer", listOf("Python", "Computer Vision"))
    var failure: Throwable? = null
    var gate: Gate? = null
    val asked = mutableListOf<Pair<ProjectId, RoleId>>()

    override suspend fun getRoleRequirement(projectId: ProjectId, roleId: RoleId): RoleRequirement? {
        asked += projectId to roleId
        gate?.await()
        failure?.let { throw it }
        return requirement
    }
}

internal fun uiDependencies(
    repository: FakeCollaboratorsRepository,
    provider: RoleRequirementProvider = FakeRoleProvider(),
): CollaboratorsUiDependencies = CollaboratorsUiDependencies(
    getRoleRequirement = GetRoleRequirementUseCase(provider),
    searchCollaborators = SearchCollaboratorsUseCase(repository),
    countCollaborators = CountCollaboratorsUseCase(repository),
    capabilities = repository.capabilities,
)
