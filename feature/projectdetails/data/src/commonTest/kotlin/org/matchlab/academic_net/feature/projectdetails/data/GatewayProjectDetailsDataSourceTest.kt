package org.matchlab.academic_net.feature.projectdetails.data

import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlinx.coroutines.test.runTest
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.paging.PageRequest
import org.matchlab.academic_net.common.result.RemoteFailure
import org.matchlab.academic_net.feature.projectdetails.domain.InitiatorInfo
import org.matchlab.academic_net.gateway.ProjectCatalogApi
import org.matchlab.academic_net.gateway.ProjectSearchParams
import org.matchlab.academic_net.gateway.dto.ProjectDto
import org.matchlab.academic_net.gateway.dto.ProjectInputDto
import org.matchlab.academic_net.gateway.dto.ProjectListDto
import org.matchlab.academic_net.gateway.dto.ProjectRoleDto
import org.matchlab.academic_net.gateway.dto.UserProfileDto
import org.matchlab.academic_net.gateway.dto.UserProfileInputDto
import org.matchlab.academic_net.gateway.mapping.AssembledProject
import org.matchlab.academic_net.gateway.mapping.ProjectAssembler
import org.matchlab.academic_net.gateway.mapping.toProject

class GatewayProjectDetailsDataSourceTest {
    private class ScriptedCatalog : ProjectCatalogApi {
        var onGet: suspend (String) -> ProjectDto = { throw AssertionError("Unexpected GET of $it") }
        val gets = mutableListOf<String>()

        override suspend fun get(id: String): ProjectDto {
            gets += id
            return onGet(id)
        }

        override suspend fun search(params: ProjectSearchParams, page: PageRequest): ProjectListDto =
            throw AssertionError("The page of a project never searches")

        override suspend fun list(params: ProjectSearchParams, page: PageRequest): ProjectListDto =
            throw AssertionError("The page of a project never reads the catalog")
    }

    private class Assembler : ProjectAssembler {
        val owners = mutableMapOf<String, UserProfileDto>()
        var failure: Throwable? = null

        override suspend fun assemble(projects: List<ProjectDto>): List<AssembledProject> {
            failure?.let { throw it }
            return projects.mapNotNull { dto ->
                val owner = owners[dto.ownerUserId]
                dto.toProject(UserId("viewer"), owner)?.let { AssembledProject(it, owner) }
            }
        }
    }

    private val id = ProjectId("p1")
    private val api = ScriptedCatalog()
    private val assembler = Assembler()
    private val source = GatewayProjectDetailsDataSource(api, assembler)

    private fun dto(owner: String = "owner-1", roles: List<ProjectRoleDto> = emptyList()) =
        ProjectDto(id = "p1", ownerUserId = owner, details = ProjectInputDto(title = "AI-навигация", field = "Biology"), roles = roles)

    private fun owner(
        university: String? = "МГУ",
        rating: Double = 0.0,
        reviewCount: Int = 0,
        completed: Int = 0,
    ) = UserProfileDto(
        userId = "owner-1",
        details = UserProfileInputDto(displayName = "Елена", university = university),
        rating = rating,
        reviewCount = reviewCount,
        completedProjectCount = completed,
    )

    // ---- the project is there ----

    @Test
    fun aProjectIsReadFromItsIdWithItsInitiatorsFacts() = runTest {
        api.onGet = { dto() }
        assembler.owners["owner-1"] = owner(university = "  НИУ ВШЭ ", rating = 4.8, reviewCount = 5, completed = 12)

        val details = assertNotNull(source.getProjectDetails(id))

        assertEquals(listOf("p1"), api.gets)
        assertEquals("AI-навигация", details.project.title)
        assertEquals(InitiatorInfo(university = "НИУ ВШЭ", rating = 4.8, completedProjects = 12), details.initiator)
    }

    @Test
    fun aPersonWithoutReviewsHasNoRatingWhateverNumberTheServerSent() = runTest {
        // API-GAP G20: the API does not say what `rating` is when there are no reviews.
        api.onGet = { dto() }
        assembler.owners["owner-1"] = owner(rating = 3.7, reviewCount = 0)

        assertNull(source.getProjectDetails(id)?.initiator?.rating)
    }

    @Test
    fun aPersonWithAReviewHasTheirRating() = runTest {
        api.onGet = { dto() }
        assembler.owners["owner-1"] = owner(rating = 4.0, reviewCount = 1)

        assertEquals(4.0, source.getProjectDetails(id)?.initiator?.rating)
    }

    @Test
    fun aNegativeNumberOfFinishedProjectsIsZero() = runTest {
        api.onGet = { dto() }
        assembler.owners["owner-1"] = owner(completed = -3)

        assertEquals(0, source.getProjectDetails(id)?.initiator?.completedProjects)
    }

    @Test
    fun anOwnerWithoutAProfileTheViewerMaySeeIsNoReasonToFail() = runTest {
        api.onGet = { dto() }

        val details = assertNotNull(source.getProjectDetails(id))

        assertEquals(InitiatorInfo(university = "", rating = null, completedProjects = 0), details.initiator)
        assertEquals("", details.project.initiator.name)
    }

    @Test
    fun anOwnerWithoutAUniversityHasABlankOne() = runTest {
        api.onGet = { dto() }
        assembler.owners["owner-1"] = owner(university = null)

        assertEquals("", source.getProjectDetails(id)?.initiator?.university)
    }

    @Test
    fun theRolesComeAlongWithTheirPlaces() = runTest {
        api.onGet = { dto(roles = listOf(ProjectRoleDto("r1", "ML", slots = 2, occupied = 1))) }

        val project = assertNotNull(source.getProjectDetails(id)).project

        assertEquals(2, project.totalPlaces)
        assertEquals(1, project.occupiedPlaces)
    }

    // ---- the project is not there ----

    @Test
    fun aProjectTheViewerMayNotSeeIsNull() = runTest {
        for (status in listOf(400, 403, 404)) {
            api.onGet = { throw RemoteFailure.Http(status) }

            assertNull(source.getProjectDetails(id), "$status")
        }
    }

    @Test
    fun anAnswerTheAppCannotReadIsNull() = runTest {
        api.onGet = { dto(owner = "") }

        assertNull(source.getProjectDetails(id))
    }

    // ---- failures ----

    @Test
    fun anyOtherFailureIsThrownAsItIs() = runTest {
        for (failure in listOf(RemoteFailure.Http(500), RemoteFailure.Http(503), RemoteFailure.NoConnection(), RemoteFailure.Timeout(), RemoteFailure.Malformed())) {
            api.onGet = { throw failure }

            assertSame(failure, assertFailsWith<RemoteFailure> { source.getProjectDetails(id) })
        }
    }

    @Test
    fun aFailureOfTheOwnersLookupFailsThePage() = runTest {
        val failure = RemoteFailure.Http(500)
        api.onGet = { dto() }
        assembler.failure = failure

        assertSame(failure, assertFailsWith<RemoteFailure.Http> { source.getProjectDetails(id) })
    }

    @Test
    fun aCancellationPassesThrough() = runTest {
        api.onGet = { throw CancellationException("cancelled") }

        assertFailsWith<CancellationException> { source.getProjectDetails(id) }
    }
}
