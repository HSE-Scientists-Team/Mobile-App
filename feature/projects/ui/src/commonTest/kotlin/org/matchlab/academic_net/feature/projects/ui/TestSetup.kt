package org.matchlab.academic_net.feature.projects.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.matchlab.academic_net.common.model.Project
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.Role
import org.matchlab.academic_net.common.model.Sphere
import org.matchlab.academic_net.feature.projects.data.ProjectsDataSource
import org.matchlab.academic_net.feature.projects.data.ProjectsRepositoryImpl
import org.matchlab.academic_net.feature.projects.data.mock.MockProjectsData
import org.matchlab.academic_net.feature.projects.data.mock.MockProjectsDataSource
import org.matchlab.academic_net.feature.projects.domain.NewProject
import org.matchlab.academic_net.feature.projects.domain.RoleDraft
import org.matchlab.academic_net.feature.projects.domain.usecase.AddRoleUseCase
import org.matchlab.academic_net.feature.projects.domain.usecase.GetSpheresUseCase
import org.matchlab.academic_net.feature.projects.domain.usecase.ObserveMyProjectsUseCase
import org.matchlab.academic_net.feature.projects.domain.usecase.ObserveProjectUseCase
import org.matchlab.academic_net.feature.projects.domain.usecase.PublishProjectUseCase
import org.matchlab.academic_net.feature.projects.domain.usecase.UnpublishProjectUseCase

/** Use cases over the mock data source with no latency, like the app minus the network delay. */
class TestGraph(dataSource: ProjectsDataSource = MockProjectsDataSource(latencyMillis = 0)) {
    private val repository = ProjectsRepositoryImpl(dataSource)

    val observeMyProjects = ObserveMyProjectsUseCase(repository)
    val observeProject = ObserveProjectUseCase(repository)
    val publishProject = PublishProjectUseCase(repository)
    val unpublishProject = UnpublishProjectUseCase(repository)
    val addRole = AddRoleUseCase(repository)
    val getSpheres = GetSpheresUseCase(repository)

    val firstProjectId: ProjectId = MockProjectsData.myProjects().first().id
}

/** Data source whose writes (and optionally reads of spheres) fail like a dead network. */
class FailingDataSource(
    private val delegate: ProjectsDataSource = MockProjectsDataSource(latencyMillis = 0),
    var failSpheres: Boolean = false,
) : ProjectsDataSource by delegate {
    override suspend fun createProject(project: NewProject): Project = throw NetworkDown()

    override suspend fun unpublishProject(id: ProjectId): Project = throw NetworkDown()

    override suspend fun addRole(projectId: ProjectId, role: RoleDraft): Role = throw NetworkDown()

    override suspend fun getSpheres() =
        if (failSpheres) throw NetworkDown() else delegate.getSpheres()

    class NetworkDown : RuntimeException("network is down")
}

/**
 * Counts the requests that reach the data source and answers each one after [delayMillis]: the time
 * in which a test can tap again while the first request is still on its way.
 */
class CountingDataSource(
    private val delegate: ProjectsDataSource = MockProjectsDataSource(latencyMillis = 0),
    private val delayMillis: Long = 0,
) : ProjectsDataSource by delegate {
    var created = 0
        private set
    var unpublished = 0
        private set
    var rolesAdded = 0
        private set
    var spheresRequested = 0
        private set

    override suspend fun createProject(project: NewProject): Project {
        created++
        delay(delayMillis)
        return delegate.createProject(project)
    }

    override suspend fun unpublishProject(id: ProjectId): Project {
        unpublished++
        delay(delayMillis)
        return delegate.unpublishProject(id)
    }

    override suspend fun addRole(projectId: ProjectId, role: RoleDraft): Role {
        rolesAdded++
        delay(delayMillis)
        return delegate.addRole(projectId, role)
    }

    override suspend fun getSpheres(): List<Sphere> {
        spheresRequested++
        delay(delayMillis)
        return delegate.getSpheres()
    }
}

/** Keeps the flow hot, so `stateIn(WhileSubscribed)` states are live; stops when the test ends. */
@OptIn(ExperimentalCoroutinesApi::class)
fun <T> TestScope.keepCollecting(flow: Flow<T>) {
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { flow.collect {} }
}

@OptIn(ExperimentalCoroutinesApi::class)
fun installMainDispatcher() = Dispatchers.setMain(UnconfinedTestDispatcher())

@OptIn(ExperimentalCoroutinesApi::class)
fun uninstallMainDispatcher() = Dispatchers.resetMain()
