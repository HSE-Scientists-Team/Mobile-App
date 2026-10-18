package org.matchlab.academic_net.feature.projectsearch.domain

import kotlinx.datetime.YearMonth
import org.matchlab.academic_net.common.model.Project
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.ProjectPeriod
import org.matchlab.academic_net.common.model.ProjectStatus
import org.matchlab.academic_net.common.model.ProjectType
import org.matchlab.academic_net.common.model.Sphere
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.model.UserSummary
import org.matchlab.academic_net.common.paging.Page
import org.matchlab.academic_net.common.paging.PageRequest

val ComputerScience = Sphere("computer-science", "Computer Science")
val Biology = Sphere("biology", "Биология")

fun foundProject(title: String) = FoundProject(
    project = Project(
        id = ProjectId("p-$title"),
        title = title,
        type = ProjectType.Scientific,
        sphere = ComputerScience,
        problem = "Problem",
        goal = "Goal",
        tags = emptyList(),
        initiator = UserSummary(UserId("u-$title"), "Initiator"),
        roles = emptyList(),
        status = ProjectStatus.Recruiting,
        period = ProjectPeriod(YearMonth(2026, 10), YearMonth(2027, 3)),
        viewerIsInitiator = false,
    ),
    initiatorUniversity = "МГУ",
)

/**
 * Records what it was asked and answers with the part of [results] that the requested page covers (or fails with
 * [failure]); the total is the size of [results], as a server that has no other records reports it.
 */
class FakeProjectSearchRepository(
    var results: List<FoundProject> = emptyList(),
    override val capabilities: ProjectSearchCapabilities = ProjectSearchCapabilities(
        typeFilter = false,
        durationFilter = false,
    ),
) : ProjectSearchRepository {
    var failure: Throwable? = null
    var lastQuery: ProjectQuery? = null
    var lastPage: PageRequest? = null

    override suspend fun search(query: ProjectQuery, page: PageRequest): Page<FoundProject> {
        lastQuery = query
        lastPage = page
        failure?.let { throw it }
        return Page(results.drop(page.offset).take(page.limit), results.size, page)
    }
}

class FakeSphereProvider(var spheres: List<Sphere> = emptyList()) : SphereProvider {
    var failure: Throwable? = null
    var calls = 0

    override suspend fun getSpheres(): List<Sphere> {
        calls++
        failure?.let { throw it }
        return spheres
    }
}
