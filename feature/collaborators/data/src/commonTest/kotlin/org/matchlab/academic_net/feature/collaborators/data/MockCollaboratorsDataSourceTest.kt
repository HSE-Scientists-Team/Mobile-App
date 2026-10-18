package org.matchlab.academic_net.feature.collaborators.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.matchlab.academic_net.common.model.Collaborator
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.ProjectType
import org.matchlab.academic_net.common.model.RoleId
import org.matchlab.academic_net.common.model.Study
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.model.UserSummary
import org.matchlab.academic_net.feature.collaborators.data.mock.MockCollaborators
import org.matchlab.academic_net.feature.collaborators.data.mock.MockCollaboratorsDataSource
import org.matchlab.academic_net.feature.collaborators.data.mock.MockPerson
import org.matchlab.academic_net.feature.collaborators.data.mock.matchPercent
import org.matchlab.academic_net.feature.collaborators.domain.CollaboratorFilters
import org.matchlab.academic_net.feature.collaborators.domain.CollaboratorQuery
import org.matchlab.academic_net.feature.collaborators.domain.RoleReference
import org.matchlab.academic_net.feature.collaborators.domain.RoleRequirement
import org.matchlab.academic_net.feature.collaborators.domain.RoleRequirementProvider

private fun person(
    name: String,
    skills: List<String> = emptyList(),
    university: String = "МГУ",
    field: String = "физика",
    study: Study = Study("Магистратура"),
    verified: Boolean = false,
    verifiedProjects: Int = 0,
    publications: Int = 0,
    workTypes: Set<ProjectType> = emptySet(),
) = MockPerson(
    collaborator = Collaborator(UserSummary(UserId("u-$name"), name, isVerified = verified), university, study, field),
    skills = skills,
    verifiedProjectsCount = verifiedProjects,
    publicationsCount = publications,
    workTypes = workTypes,
)

private val Project = ProjectId("p1")
private val Role = RoleId("r1")
private val RoleRef = RoleReference(Project, Role)

private class StaticRequirements(var skills: List<String>?) : RoleRequirementProvider {
    override suspend fun getRoleRequirement(projectId: ProjectId, roleId: RoleId) =
        skills?.let { RoleRequirement("Проект", "Роль", it) }
}

class MockCollaboratorsSearchTest {
    private val requirements = StaticRequirements(listOf("Computer Vision", "Python"))

    private fun source(vararg people: MockPerson) =
        MockCollaboratorsDataSource(requirements, people.toList(), latencyMillis = 0)

    private suspend fun MockCollaboratorsDataSource.names(query: CollaboratorQuery) =
        search(query).map { it.collaborator.user.name }

    private val arjun = person(
        name = "Арджун Мехта",
        skills = listOf("Computer Vision", "Python"),
        university = "Stanford",
        field = "Computer Science",
        verified = true,
        verifiedProjects = 2,
        publications = 1,
        workTypes = setOf(ProjectType.Scientific),
    )
    private val plain = person(name = "Иван Иванов")

    @Test
    fun emptyQueryFindsEveryone() = runTest {
        assertEquals(listOf("Арджун Мехта", "Иван Иванов"), source(arjun, plain).names(CollaboratorQuery()))
    }

    @Test
    fun anyTermOfTheTextIsEnough() = runTest {
        val source = source(arjun, plain)

        assertEquals(listOf("Арджун Мехта"), source.names(CollaboratorQuery(text = "kotlin, python")))
        assertEquals(emptyList(), source.names(CollaboratorQuery(text = "kotlin, ros")))
    }

    @Test
    fun textLooksAtNameUniversityFieldAndSkillsIgnoringCase() = runTest {
        val source = source(arjun)

        for (text in listOf("мехта", "STANFORD", "computer science", "vision")) {
            assertEquals(listOf("Арджун Мехта"), source.names(CollaboratorQuery(text = text)), text)
        }
    }

    @Test
    fun blankTermsAreIgnored() = runTest {
        assertEquals(2, source(arjun, plain).names(CollaboratorQuery(text = " , ,  ")).size)
    }

    @Test
    fun verifiedProfileFilter() = runTest {
        val query = CollaboratorQuery(filters = CollaboratorFilters(verifiedProfile = true))

        assertEquals(listOf("Арджун Мехта"), source(arjun, plain).names(query))
    }

    @Test
    fun verifiedProjectsAndPublicationsFilters() = runTest {
        val source = source(arjun, plain)

        assertEquals(
            listOf("Арджун Мехта"),
            source.names(CollaboratorQuery(filters = CollaboratorFilters(hasVerifiedProjects = true))),
        )
        assertEquals(
            listOf("Арджун Мехта"),
            source.names(CollaboratorQuery(filters = CollaboratorFilters(hasPublications = true))),
        )
    }

    @Test
    fun workTypeFilterNeedsAnyOfTheTypes() = runTest {
        val source = source(arjun, plain)
        fun query(vararg types: ProjectType) =
            CollaboratorQuery(filters = CollaboratorFilters(workTypes = types.toSet()))

        assertEquals(listOf("Арджун Мехта"), source.names(query(ProjectType.Scientific)))
        assertEquals(emptyList(), source.names(query(ProjectType.Practical)))
        assertEquals(listOf("Арджун Мехта"), source.names(query(ProjectType.Scientific, ProjectType.Practical)))
    }

    @Test
    fun universityFilterIsAPartOfTheName() = runTest {
        val source = source(arjun, plain)

        assertEquals(
            listOf("Арджун Мехта"),
            source.names(CollaboratorQuery(filters = CollaboratorFilters(university = "stan"))),
        )
        assertEquals(
            listOf("Иван Иванов"),
            source.names(CollaboratorQuery(filters = CollaboratorFilters(university = "мгу"))),
        )
    }

    @Test
    fun everyKeywordMustOccur() = runTest {
        val source = source(arjun)

        assertEquals(
            listOf("Арджун Мехта"),
            source.names(CollaboratorQuery(filters = CollaboratorFilters(keywords = listOf("python", "vision")))),
        )
        assertEquals(
            emptyList(),
            source.names(CollaboratorQuery(filters = CollaboratorFilters(keywords = listOf("python", "ros")))),
        )
    }

    @Test
    fun textAndFiltersAreCombinedWithAnd() = runTest {
        val query = CollaboratorQuery(
            text = "python",
            filters = CollaboratorFilters(verifiedProfile = true, university = "МГУ"),
        )

        assertEquals(emptyList(), source(arjun, plain).names(query))
    }

    @Test
    fun resultsAreScoredByTheRolesSkillsAndOrderedByThem() = runTest {
        val source = source(
            person("Борис", skills = listOf("Python")),
            person("Вера", skills = listOf("Python", "Computer Vision")),
            person("Алла", skills = listOf("Python"), verified = true),
            person("Глеб", skills = listOf("Kotlin")),
        )

        val result = source.search(CollaboratorQuery(role = RoleRef))

        // 100% first; among 50%: verified Алла before Борис; 0% last.
        assertEquals(listOf("Вера", "Алла", "Борис", "Глеб"), result.map { it.collaborator.user.name })
        assertEquals(listOf(100, 50, 50, 0), result.map { it.matchPercent })
    }

    @Test
    fun withoutARoleThereIsNoScoreAndOrderIsVerifiedThenName() = runTest {
        val source = source(person("Борис"), person("Алла"), person("Вера", verified = true))

        val result = source.search(CollaboratorQuery())

        assertTrue(result.all { it.matchPercent == null })
        assertEquals(listOf("Вера", "Алла", "Борис"), result.map { it.collaborator.user.name })
    }

    @Test
    fun unknownRoleGivesNoScore() = runTest {
        requirements.skills = null

        val result = source(arjun).search(CollaboratorQuery(role = RoleRef))

        assertNull(result.single().matchPercent)
    }

    @Test
    fun roleSkillsOnlyScoreAndDoNotRestrictThePeopleFound() = runTest {
        val source = source(person("Тимур", skills = listOf("Kotlin")), arjun)

        val result = source.search(CollaboratorQuery(text = "kotlin", role = RoleRef))

        assertEquals(listOf("Тимур"), result.map { it.collaborator.user.name })
        assertEquals(0, result.single().matchPercent)
    }
}

class MockMatchPercentTest {
    @Test
    fun fullCoverageIsHundred() {
        assertEquals(100, matchPercent(listOf("Python", "SQL"), listOf("SQL", "Python", "R")))
    }

    @Test
    fun partialCoverageIsRounded() {
        assertEquals(50, matchPercent(listOf("Python", "SQL"), listOf("Python")))
        assertEquals(33, matchPercent(listOf("a", "b", "c"), listOf("a")))
        assertEquals(67, matchPercent(listOf("a", "b", "c"), listOf("a", "b")))
    }

    @Test
    fun noCoverageIsZero() {
        assertEquals(0, matchPercent(listOf("Python"), listOf("Kotlin")))
    }

    @Test
    fun comparisonIgnoresCaseSpacesAndDuplicates() {
        assertEquals(100, matchPercent(listOf(" python ", "PYTHON"), listOf("Python")))
    }

    @Test
    fun nothingRequiredMeansNoScore() {
        assertNull(matchPercent(emptyList(), listOf("Python")))
        assertNull(matchPercent(listOf("  "), listOf("Python")))
    }
}

class MockCollaboratorsDataTest {
    @Test
    fun peopleHaveUniqueIds() {
        val ids = MockCollaborators.all.map { it.collaborator.user.id }

        assertEquals(ids.size, ids.toSet().size)
    }
}
