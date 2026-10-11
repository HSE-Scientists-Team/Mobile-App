package org.matchlab.academic_net.feature.projectsearch.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.YearMonth
import org.matchlab.academic_net.common.model.Project
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.ProjectPeriod
import org.matchlab.academic_net.common.model.ProjectStatus
import org.matchlab.academic_net.common.model.ProjectType
import org.matchlab.academic_net.common.model.Role
import org.matchlab.academic_net.common.model.RoleId
import org.matchlab.academic_net.common.model.Sphere
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.model.UserSummary
import org.matchlab.academic_net.feature.projectsearch.domain.FoundProject
import org.matchlab.academic_net.feature.projectsearch.domain.ProjectDuration
import org.matchlab.academic_net.feature.projectsearch.domain.ProjectFilters
import org.matchlab.academic_net.feature.projectsearch.domain.ProjectQuery

private val ComputerScience = Sphere("computer-science", "Computer Science")
private val Biology = Sphere("biology", "Биология")

private fun period(startYear: Int, startMonth: Int, endYear: Int, endMonth: Int) =
    ProjectPeriod(YearMonth(startYear, startMonth), YearMonth(endYear, endMonth))

/** A project whose every searchable field is a neutral word, so a test sets only what it looks at. */
private fun record(
    id: String,
    title: String = "Title $id",
    type: ProjectType = ProjectType.Scientific,
    sphere: Sphere = ComputerScience,
    problem: String = "Problem",
    goal: String = "Goal",
    tags: List<String> = emptyList(),
    initiatorName: String = "Initiator",
    university: String = "University",
    roles: List<Role> = emptyList(),
    period: ProjectPeriod = period(2026, 10, 2026, 12),
    status: ProjectStatus = ProjectStatus.Recruiting,
    own: Boolean = false,
) = MockSearchProject(
    project = Project(
        id = ProjectId(id),
        title = title,
        type = type,
        sphere = sphere,
        problem = problem,
        goal = goal,
        tags = tags,
        initiator = UserSummary(UserId("u-$id"), initiatorName),
        roles = roles,
        status = status,
        period = period,
        viewerIsInitiator = own,
    ),
    initiatorUniversity = university,
)

private fun role(title: String, vararg skills: String) = Role(RoleId("r-$title"), title, skills.toList())

private fun searchOf(vararg projects: MockSearchProject) =
    MockProjectSearchDataSource(projects.toList(), latencyMillis = 0)

private suspend fun MockProjectSearchDataSource.ids(query: ProjectQuery = ProjectQuery()) =
    search(query).map { it.project.id.value }

private suspend fun MockProjectSearchDataSource.ids(text: String) = ids(ProjectQuery(text = text))

private suspend fun MockProjectSearchDataSource.ids(filters: ProjectFilters) =
    ids(ProjectQuery(filters = filters))

class MockProjectSearchTextTest {
    private val source = searchOf(
        record("titled", title = "Квантовый компьютер"),
        record("problem", problem = "Ржавчина разрушает мосты"),
        record("goal", goal = "Построить спектрометр"),
        record("tagged", tags = listOf("Blockchain", "IoT")),
        record("sphere", sphere = Sphere("physics", "Физика твёрдого тела")),
        record("initiator", initiatorName = "Проф. Виктория Лавров"),
        record("university", university = "Новосибирский университет"),
        record("roleTitle", roles = listOf(role("Криптограф"))),
        record("roleSkill", roles = listOf(role("Engineer", "Assembler"))),
    )

    @Test
    fun emptyTextReturnsEverySearchableProjectInCatalogOrder() = runTest {
        assertEquals(
            listOf("titled", "problem", "goal", "tagged", "sphere", "initiator", "university", "roleTitle", "roleSkill"),
            source.ids(""),
        )
    }

    @Test
    fun blankTextAndLoneCommasReturnEverything() = runTest {
        assertEquals(9, source.ids("   ").size)
        assertEquals(9, source.ids(" , ,").size)
    }

    @Test
    fun matchesTheTitle() = runTest { assertEquals(listOf("titled"), source.ids("квантовый")) }

    @Test
    fun matchesTheProblem() = runTest { assertEquals(listOf("problem"), source.ids("ржавчина")) }

    @Test
    fun matchesTheGoal() = runTest { assertEquals(listOf("goal"), source.ids("спектрометр")) }

    @Test
    fun matchesTheTags() = runTest { assertEquals(listOf("tagged"), source.ids("blockchain")) }

    @Test
    fun matchesTheSphereName() = runTest { assertEquals(listOf("sphere"), source.ids("твёрдого")) }

    @Test
    fun matchesTheInitiatorName() = runTest { assertEquals(listOf("initiator"), source.ids("лавров")) }

    @Test
    fun matchesTheUniversity() = runTest { assertEquals(listOf("university"), source.ids("новосибирский")) }

    @Test
    fun matchesTheRoleTitles() = runTest { assertEquals(listOf("roleTitle"), source.ids("криптограф")) }

    @Test
    fun matchesTheRoleSkills() = runTest { assertEquals(listOf("roleSkill"), source.ids("assembler")) }

    @Test
    fun ignoresCaseOfTheTermAndOfTheText() = runTest {
        assertEquals(listOf("tagged"), source.ids("BLOCKCHAIN"))
        assertEquals(listOf("titled"), source.ids("КВАНТОВЫЙ"))
    }

    @Test
    fun matchesAPartOfAWord() = runTest {
        assertEquals(listOf("titled"), source.ids("квант"))
    }

    @Test
    fun termsAreOrWithBlankTermsIgnored() = runTest {
        assertEquals(listOf("titled", "goal"), source.ids("спектрометр, , КВАНТОВЫЙ"))
    }

    @Test
    fun unknownTermFindsNothing() = runTest {
        assertEquals(emptyList(), source.ids("несуществующее"))
    }

    @Test
    fun aTermWithSpacesIsAPhrase() = runTest {
        val phrases = searchOf(
            record("phrase", goal = "Изучим машинное обучение"),
            record("words", goal = "Обучение машин"),
        )

        assertEquals(listOf("phrase"), phrases.ids("машинное обучение"))
    }
}

class MockProjectSearchFiltersTest {
    private val source = searchOf(
        record("sci-cs", type = ProjectType.Scientific, sphere = ComputerScience),
        record("prac-cs", type = ProjectType.Practical, sphere = ComputerScience),
        record("sci-bio", type = ProjectType.Scientific, sphere = Biology),
        record("prac-bio", type = ProjectType.Practical, sphere = Biology),
    )

    @Test
    fun noFiltersReturnsEverything() = runTest {
        assertEquals(4, source.ids(ProjectFilters()).size)
    }

    @Test
    fun typeFiltersByType() = runTest {
        assertEquals(
            listOf("prac-cs", "prac-bio"),
            source.ids(ProjectFilters(types = setOf(ProjectType.Practical))),
        )
    }

    @Test
    fun bothTypesChosenIsOr() = runTest {
        assertEquals(4, source.ids(ProjectFilters(types = ProjectType.entries.toSet())).size)
    }

    @Test
    fun sphereFiltersById() = runTest {
        assertEquals(listOf("sci-bio", "prac-bio"), source.ids(ProjectFilters(sphere = Biology)))
    }

    @Test
    fun sphereIsComparedByIdNotByName() = runTest {
        val renamed = searchOf(
            record("renamed", sphere = Sphere("biology", "Biology")),
            record("lookalike", sphere = Sphere("other", "Биология")),
        )

        assertEquals(listOf("renamed"), renamed.ids(ProjectFilters(sphere = Biology)))
    }

    @Test
    fun filtersOfDifferentGroupsAreAnd() = runTest {
        val filters = ProjectFilters(types = setOf(ProjectType.Scientific), sphere = Biology)

        assertEquals(listOf("sci-bio"), source.ids(filters))
    }

    @Test
    fun textAndFiltersAreAnd() = runTest {
        val query = ProjectQuery(text = "sci-bio, prac-cs", filters = ProjectFilters(sphere = Biology))

        // "prac-cs" fits the text but not the sphere.
        assertEquals(listOf("sci-bio"), source.ids(query))
    }
}

class MockProjectSearchDurationTest {
    private val source = searchOf(
        record("1m", period = period(2026, 12, 2026, 12)),
        record("3m", period = period(2026, 10, 2026, 12)),
        record("4m", period = period(2026, 10, 2027, 1)),
        record("6m", period = period(2026, 10, 2027, 3)),
        record("7m", period = period(2026, 10, 2027, 4)),
        record("12m", period = period(2026, 10, 2027, 9)),
    )

    private suspend fun idsFor(vararg durations: ProjectDuration) =
        source.ids(ProjectFilters(durations = durations.toSet()))

    @Test
    fun upTo3MonthsIncludesOneAndThreeMonths() = runTest {
        assertEquals(listOf("1m", "3m"), idsFor(ProjectDuration.UpTo3Months))
    }

    @Test
    fun from3To6MonthsIncludesFourAndSixMonths() = runTest {
        assertEquals(listOf("4m", "6m"), idsFor(ProjectDuration.From3To6Months))
    }

    @Test
    fun over6MonthsStartsAtSevenMonths() = runTest {
        assertEquals(listOf("7m", "12m"), idsFor(ProjectDuration.Over6Months))
    }

    @Test
    fun severalDurationsAreOr() = runTest {
        assertEquals(
            listOf("1m", "3m", "7m", "12m"),
            idsFor(ProjectDuration.UpTo3Months, ProjectDuration.Over6Months),
        )
    }

    @Test
    fun noDurationMeansAny() = runTest {
        assertEquals(6, source.ids(ProjectFilters()).size)
    }

    @Test
    fun monthsAreCountedAcrossTheNewYear() {
        assertEquals(3, period(2026, 11, 2027, 1).monthsCount())
        assertEquals(4, period(2026, 11, 2027, 2).monthsCount())
        assertEquals(6, period(2026, 12, 2027, 5).monthsCount())
        assertEquals(7, period(2026, 12, 2027, 6).monthsCount())
    }

    @Test
    fun monthsAreCountedInclusively() {
        assertEquals(1, period(2026, 5, 2026, 5).monthsCount())
        assertEquals(12, period(2026, 1, 2026, 12).monthsCount())
        assertEquals(13, period(2026, 1, 2027, 1).monthsCount())
    }

    @Test
    fun boundariesAcrossTheNewYearFallInTheRightGroup() {
        assertEquals(ProjectDuration.UpTo3Months, period(2026, 11, 2027, 1).duration())
        assertEquals(ProjectDuration.From3To6Months, period(2026, 11, 2027, 2).duration())
        assertEquals(ProjectDuration.From3To6Months, period(2026, 12, 2027, 5).duration())
        assertEquals(ProjectDuration.Over6Months, period(2026, 12, 2027, 6).duration())
    }

    @Test
    fun periodEndingBeforeItsStartCountsAsOneMonth() {
        val reversed = period(2027, 3, 2026, 10)

        assertEquals(1, reversed.monthsCount())
        assertEquals(ProjectDuration.UpTo3Months, reversed.duration())
    }
}

class MockProjectSearchKeywordsTest {
    private val source = searchOf(
        record("both", tags = listOf("Python", "Robotics")),
        record("python", tags = listOf("Python")),
        record("robotics", goal = "Заниматься роботикой и Robotics-соревнованиями"),
        record("none", tags = listOf("Law")),
    )

    @Test
    fun everyKeywordHasToOccur() = runTest {
        val filters = ProjectFilters(keywords = listOf("python", "ROBOTICS"))

        assertEquals(listOf("both"), source.ids(filters))
    }

    @Test
    fun oneKeywordIsEnough() = runTest {
        assertEquals(listOf("both", "python"), source.ids(ProjectFilters(keywords = listOf("python"))))
    }

    @Test
    fun blankKeywordsAreIgnored() = runTest {
        assertEquals(4, source.ids(ProjectFilters(keywords = listOf("", "  "))).size)
    }

    @Test
    fun unknownKeywordLeavesNothing() = runTest {
        assertEquals(emptyList(), source.ids(ProjectFilters(keywords = listOf("python", "несуществующее"))))
    }

    @Test
    fun keywordsLookInTheSameFieldsAsTheText() = runTest {
        val query = ProjectQuery(text = "Python, Robotics", filters = ProjectFilters(keywords = listOf("Robotics")))

        // The text is "or", the keyword narrows it: "python" alone does not contain "robotics".
        assertEquals(listOf("both", "robotics"), source.ids(query))
    }

    @Test
    fun allGroupsTogether() = runTest {
        val projects = searchOf(
            record(
                "fits",
                type = ProjectType.Practical,
                sphere = Biology,
                period = period(2026, 11, 2027, 3),
                tags = listOf("Python"),
                university = "ИТМО",
            ),
            record(
                "wrong-type",
                type = ProjectType.Scientific,
                sphere = Biology,
                period = period(2026, 11, 2027, 3),
                tags = listOf("Python"),
                university = "ИТМО",
            ),
            record(
                "wrong-sphere",
                type = ProjectType.Practical,
                sphere = ComputerScience,
                period = period(2026, 11, 2027, 3),
                tags = listOf("Python"),
                university = "ИТМО",
            ),
            record(
                "wrong-duration",
                type = ProjectType.Practical,
                sphere = Biology,
                period = period(2026, 11, 2026, 12),
                tags = listOf("Python"),
                university = "ИТМО",
            ),
            record(
                "wrong-keyword",
                type = ProjectType.Practical,
                sphere = Biology,
                period = period(2026, 11, 2027, 3),
                tags = listOf("Java"),
                university = "ИТМО",
            ),
            record(
                "wrong-text",
                type = ProjectType.Practical,
                sphere = Biology,
                period = period(2026, 11, 2027, 3),
                tags = listOf("Python"),
                university = "МГУ",
            ),
        )
        val query = ProjectQuery(
            text = "итмо",
            filters = ProjectFilters(
                types = setOf(ProjectType.Practical),
                sphere = Biology,
                durations = setOf(ProjectDuration.From3To6Months),
                keywords = listOf("python"),
            ),
        )

        assertEquals(listOf("fits"), projects.ids(query))
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MockProjectSearchVisibilityTest {
    private val source = searchOf(
        record("recruiting"),
        record("unpublished", status = ProjectStatus.Unpublished),
        record("own", own = true),
        record("own-unpublished", status = ProjectStatus.Unpublished, own = true),
        record("another"),
    )

    @Test
    fun unpublishedAndOwnProjectsAreNeverReturned() = runTest {
        assertEquals(listOf("recruiting", "another"), source.ids())
    }

    @Test
    fun theyAreHiddenWhateverTheQueryIs() = runTest {
        val query = ProjectQuery(text = "unpublished, own, Title")

        assertEquals(listOf("recruiting", "another"), source.ids(query))
    }

    @Test
    fun catalogOrderIsKept() = runTest {
        val ordered = searchOf(record("c"), record("a"), record("b"))

        assertEquals(listOf("c", "a", "b"), ordered.ids())
    }

    @Test
    fun resultCarriesTheProjectAndTheInitiatorsUniversity() = runTest {
        val project = record("x", university = "Stanford")

        val found = searchOf(project).search(ProjectQuery()).single()

        assertEquals(project.project, found.project)
        assertEquals("Stanford", found.initiatorUniversity)
    }

    @Test
    fun emptyCatalogFindsNothing() = runTest {
        assertEquals(emptyList(), searchOf().ids())
    }

    @Test
    fun answersAfterTheLatency() = runTest {
        val slow = MockProjectSearchDataSource(listOf(record("a")), latencyMillis = 250)

        slow.search(ProjectQuery())

        assertEquals(250, testScheduler.currentTime)
    }

    @Test
    fun zeroLatencyDoesNotWait() = runTest {
        searchOf(record("a")).search(ProjectQuery())

        assertEquals(0, testScheduler.currentTime)
    }
}

class MockProjectCatalogTest {
    private val catalog = MockProjectCatalog.all

    @Test
    fun startsWithTheThreeCardsOfTheDesign() {
        val first = catalog.take(3)

        assertEquals(
            listOf("AI-навигация по кампусу", "Обнаружение аномалий в МРТ", "Цифровой двойник лаборатории"),
            first.map { it.project.title },
        )
        assertEquals(
            listOf("Проф. Елена Васкес", "Д-р Анна Петрова", "Иван Орлов"),
            first.map { it.project.initiator.name },
        )
        assertEquals(listOf("Stanford", "Сеченовский", "ИТМО"), first.map { it.initiatorUniversity })
        assertEquals(listOf(4, 3, 5), first.map { it.project.roles.size })
        assertEquals(
            listOf(
                period(2026, 10, 2027, 3),
                period(2026, 11, 2027, 5),
                period(2026, 9, 2027, 3),
            ),
            first.map { it.project.period },
        )
    }

    @Test
    fun hasAboutTwelveProjectsWithUniqueIds() {
        assertEquals(12, catalog.size)
        assertEquals(catalog.size, catalog.map { it.project.id }.toSet().size)
    }

    @Test
    fun everyProjectIsPublishedAndNotTheViewers() {
        assertTrue(catalog.all { it.project.status == ProjectStatus.Recruiting })
        assertTrue(catalog.none { it.project.viewerIsInitiator })
    }

    @Test
    fun coversBothTypesAndEveryDuration() {
        assertEquals(ProjectType.entries.toSet(), catalog.map { it.project.type }.toSet())
        assertEquals(
            ProjectDuration.entries.toSet(),
            catalog.map { it.project.period.duration() }.toSet(),
        )
    }

    @Test
    fun hasProjectsAcrossTheNewYear() {
        assertTrue(catalog.any { it.project.period.start.year != it.project.period.end.year })
    }

    @Test
    fun sphereIdsAreTheProjectsFeaturesIds() {
        val known = setOf(
            "computer-science", "data-science", "engineering", "mathematics", "physics", "chemistry",
            "biology", "medicine", "economics", "social-sciences", "linguistics", "design",
        )

        assertTrue(catalog.all { it.project.sphere.id in known })
    }

    @Test
    fun everySphereIdHasOneName() {
        val namesById = catalog.groupBy({ it.project.sphere.id }, { it.project.sphere.name })

        assertTrue(namesById.values.all { it.toSet().size == 1 })
    }

    @Test
    fun hasSeveralSpheres() {
        assertTrue(catalog.map { it.project.sphere.id }.toSet().size >= 8)
    }

    @Test
    fun rolesOfAProjectHaveUniqueIds() {
        assertTrue(catalog.all { found -> found.project.roles.let { r -> r.map { it.id }.toSet().size == r.size } })
    }

    @Test
    fun machineLearningInRussianFindsProjects() = runTest {
        val found = MockProjectSearchDataSource(latencyMillis = 0).ids("машинное обучение")

        assertTrue(found.size >= 2, "found: $found")
    }

    @Test
    fun theDefaultSourceSearchesTheWholeCatalog() = runTest {
        assertEquals(catalog.size, MockProjectSearchDataSource(latencyMillis = 0).ids().size)
    }
}

class ProjectSearchRepositoryImplTest {
    @Test
    fun delegatesTheQueryAndReturnsTheAnswer() = runTest {
        val query = ProjectQuery(text = "Title", filters = ProjectFilters(sphere = Biology))
        val source = RecordingDataSource(searchOf(record("a", sphere = Biology), record("b")).search(query))
        val repository = ProjectSearchRepositoryImpl(source)

        val result = repository.search(query)

        assertEquals(query, source.lastQuery)
        assertEquals(listOf("a"), result.map { it.project.id.value })
    }

    @Test
    fun errorsOfTheDataSourceGoThrough() = runTest {
        val repository = ProjectSearchRepositoryImpl(object : ProjectSearchDataSource {
            override suspend fun search(query: ProjectQuery): List<FoundProject> =
                throw IllegalStateException("offline")
        })

        val failure = runCatching { repository.search(ProjectQuery()) }.exceptionOrNull()

        assertEquals("offline", failure?.message)
    }

    private class RecordingDataSource(
        private val answer: List<FoundProject>,
    ) : ProjectSearchDataSource {
        var lastQuery: ProjectQuery? = null

        override suspend fun search(query: ProjectQuery) = answer.also { lastQuery = query }
    }
}
