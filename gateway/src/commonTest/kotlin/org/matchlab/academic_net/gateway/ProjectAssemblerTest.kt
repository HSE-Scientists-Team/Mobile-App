package org.matchlab.academic_net.gateway

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.result.RemoteFailure
import org.matchlab.academic_net.gateway.dto.ProjectDto
import org.matchlab.academic_net.gateway.dto.ProjectInputDto
import org.matchlab.academic_net.gateway.dto.UserProfileDto
import org.matchlab.academic_net.gateway.dto.UserProfileInputDto
import org.matchlab.academic_net.gateway.mapping.DirectoryProjectAssembler
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Turns the projects of the server into the app's with their owners (API-GAP G47: a project names its owner by id only,
 * the profile is a request of its own). The owners come from a real [UserDirectory] over a fake call.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProjectAssemblerTest {
    private val viewer = UserId("viewer-1")

    /** The `GET /users/{id}/profile` call: records the ids it was asked for; [missing] answer 404, [failing] answer the failure. */
    private class Profiles {
        val asked = mutableListOf<String>()
        val missing = mutableSetOf<String>()
        val failing = mutableMapOf<String, Throwable>()

        suspend fun fetch(id: String): UserProfileDto {
            asked += id
            failing[id]?.let { throw it }
            if (id in missing) throw RemoteFailure.Http(404)
            return UserProfileDto(userId = id, details = UserProfileInputDto(displayName = "Имя $id"), verified = id == "verified")
        }
    }

    private fun TestScope.assembler(profiles: Profiles) =
        DirectoryProjectAssembler(UserDirectory(profiles::fetch, backgroundScope), viewer)

    private fun dto(id: String, owner: String) =
        ProjectDto(id = id, ownerUserId = owner, details = ProjectInputDto(title = "Проект $id", field = "Biology"))

    @Test
    fun theOwnersAreAskedForTogetherEachDistinctIdOnce() = runTest {
        val profiles = Profiles()

        assembler(profiles).assemble(listOf(dto("p1", "a"), dto("p2", "b"), dto("p3", "a"), dto("p4", "c"), dto("p5", "b")))

        assertEquals(listOf("a", "b", "c"), profiles.asked.sorted())
        assertEquals(3, profiles.asked.size, "a person is asked for once however many projects they own")
    }

    @Test
    fun theProjectsKeepTheirOrderAndEachGetsItsOwner() = runTest {
        val result = assembler(Profiles()).assemble(listOf(dto("p3", "c"), dto("p1", "a"), dto("p2", "b")))

        assertEquals(listOf("p3", "p1", "p2"), result.map { it.project.id.value })
        assertEquals(listOf("Имя c", "Имя a", "Имя b"), result.map { it.project.initiator.name })
        assertEquals(listOf("c", "a", "b"), result.map { it.owner?.userId })
    }

    @Test
    fun theOwnerIsKeptBesideTheProjectForTheCallerToUse() = runTest {
        val result = assembler(Profiles()).assemble(listOf(dto("p1", "verified"))).single()

        assertEquals("verified", result.owner?.userId)
        assertTrue(result.project.initiator.isVerified)
    }

    @Test
    fun anOwnerWithoutAProfileTheViewerMaySeeStillGetsTheirProject() = runTest {
        val profiles = Profiles().apply { missing += "b" }

        val result = assembler(profiles).assemble(listOf(dto("p1", "a"), dto("p2", "b")))

        assertEquals(listOf("p1", "p2"), result.map { it.project.id.value })
        assertEquals("Имя a", result[0].project.initiator.name)
        assertNull(result[1].owner)
        assertEquals("", result[1].project.initiator.name)
        assertEquals(UserId("b"), result[1].project.initiator.id)
    }

    @Test
    fun theProjectsOfTheViewerAreMarked() = runTest {
        val result = assembler(Profiles()).assemble(listOf(dto("p1", "viewer-1"), dto("p2", "other")))

        assertEquals(listOf(true, false), result.map { it.project.viewerIsInitiator })
    }

    @Test
    fun anEmptyListAsksForNobodyAndMakesNoRequest() = runTest {
        val profiles = Profiles()

        assertEquals(emptyList(), assembler(profiles).assemble(emptyList()))
        assertEquals(emptyList(), profiles.asked)
    }

    @Test
    fun aProjectThatCannotBeShownIsDroppedAndItsOwnerIsNotAskedFor() = runTest {
        val profiles = Profiles()

        val result = assembler(profiles).assemble(listOf(dto("", "a"), dto("p2", ""), dto("p3", "b"), dto("  ", "c")))

        assertEquals(listOf("p3"), result.map { it.project.id.value })
        assertEquals(listOf("b"), profiles.asked)
    }

    @Test
    fun onlyUnreadableProjectsMeanNoRequestEither() = runTest {
        val profiles = Profiles()

        assertEquals(emptyList(), assembler(profiles).assemble(listOf(dto("", "a"), dto("p", ""))))
        assertEquals(emptyList(), profiles.asked)
    }

    @Test
    fun aFailureToReadAProfileOtherThanNoSuchPersonIsThrown() = runTest {
        val failure = RemoteFailure.Http(500)
        val profiles = Profiles().apply { failing["b"] = failure }

        val thrown = assertFailsWith<RemoteFailure.Http> {
            assembler(profiles).assemble(listOf(dto("p1", "a"), dto("p2", "b")))
        }

        assertSame(failure, thrown)
    }

    @Test
    fun noConnectionIsThrownAsItIs() = runTest {
        val profiles = Profiles().apply { failing["a"] = RemoteFailure.NoConnection() }

        assertFailsWith<RemoteFailure.NoConnection> { assembler(profiles).assemble(listOf(dto("p1", "a"))) }
    }

    @Test
    fun aCancellationPassesThrough() = runTest {
        val profiles = Profiles().apply { failing["a"] = CancellationException("cancelled") }

        assertFailsWith<CancellationException> { assembler(profiles).assemble(listOf(dto("p1", "a"))) }
    }

    @Test
    fun aProjectOfSeveralRolesKeepsTheRolesThroughTheAssembling() = runTest {
        val project = dto("p1", "a").copy(
            roles = listOf(org.matchlab.academic_net.gateway.dto.ProjectRoleDto("r", "Роль", slots = 2, occupied = 1)),
        )

        val result = assembler(Profiles()).assemble(listOf(project)).single()

        assertEquals(1, result.project.roles.size)
        assertFalse(result.project.roles.single().members.isNotEmpty())
        assertEquals(1, result.project.occupiedPlaces)
    }
}
