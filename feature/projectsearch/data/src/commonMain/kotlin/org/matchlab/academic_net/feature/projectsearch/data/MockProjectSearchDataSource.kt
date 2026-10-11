package org.matchlab.academic_net.feature.projectsearch.data

import kotlinx.coroutines.delay
import kotlinx.datetime.monthsUntil
import org.matchlab.academic_net.common.model.Project
import org.matchlab.academic_net.common.model.ProjectPeriod
import org.matchlab.academic_net.common.model.ProjectStatus
import org.matchlab.academic_net.feature.projectsearch.domain.FoundProject
import org.matchlab.academic_net.feature.projectsearch.domain.ProjectDuration
import org.matchlab.academic_net.feature.projectsearch.domain.ProjectFilters
import org.matchlab.academic_net.feature.projectsearch.domain.ProjectQuery
import org.matchlab.academic_net.mockserver.MockProjectCatalog
import org.matchlab.academic_net.mockserver.MockServerProject
import org.matchlab.academic_net.utils.occursIn
import org.matchlab.academic_net.utils.searchTerms

/**
 * In-memory stand-in for the project-search server. All the searching happens here, as it will on
 * the real backend: the visibility rules, matching the text and the filters, and ordering.
 * Answers after [latencyMillis], like a network call. The projects are the [catalog], the mock
 * server's database from `:mockserver`; the initiator's rating is not used here.
 *
 * Rules of this mock (the contract for the backend):
 * - only projects that are [ProjectStatus.Recruiting] and not the viewer's own
 *   (`viewerIsInitiator == false`) are searched;
 * - the text is comma-separated terms; a project fits when at least one term occurs (ignoring
 *   case) in its title, problem, goal, tags, sphere name, the initiator's name and university,
 *   the titles of its roles or the skills of its roles; no terms means every project;
 * - the filters are combined with "and"; inside the chips of a group it is "or": the type is one
 *   of `types`, the sphere has the `sphere.id`, the length is in one of `durations`, and every
 *   keyword occurs in the same fields as the text;
 * - the order is the catalog's order, the newest first.
 */
class MockProjectSearchDataSource(
    private val catalog: List<MockServerProject> = MockProjectCatalog.all,
    private val latencyMillis: Long = DefaultLatencyMillis,
) : ProjectSearchDataSource {
    override suspend fun search(query: ProjectQuery): List<FoundProject> {
        simulateNetwork()
        return catalog
            .filter { it.isSearchable() && it.matches(query) }
            .map { FoundProject(it.project, it.initiatorUniversity) }
    }

    private suspend fun simulateNetwork() {
        if (latencyMillis > 0) delay(latencyMillis)
    }

    private companion object {
        const val DefaultLatencyMillis = 300L
    }
}

/** Published and somebody else's: the only projects a search may return. */
private fun MockServerProject.isSearchable(): Boolean =
    project.status == ProjectStatus.Recruiting && !project.viewerIsInitiator

/** Everything the text search looks at, lowercase, one field per line. */
private fun MockServerProject.haystack(): String = buildList {
    add(project.title)
    add(project.problem)
    add(project.goal)
    addAll(project.tags)
    add(project.sphere.name)
    add(project.initiator.name)
    add(initiatorUniversity)
    project.roles.forEach { role ->
        add(role.title)
        addAll(role.skills)
    }
}.joinToString(separator = "\n").lowercase()

private fun MockServerProject.matches(query: ProjectQuery): Boolean {
    val haystack = haystack()
    val terms = searchTerms(query.text)
    if (terms.isNotEmpty() && terms.none { it.occursIn(haystack) }) return false
    return project.fits(query.filters, haystack)
}

private fun Project.fits(filters: ProjectFilters, haystack: String): Boolean {
    if (filters.types.isNotEmpty() && type !in filters.types) return false
    val wantedSphere = filters.sphere
    if (wantedSphere != null && sphere.id != wantedSphere.id) return false
    if (filters.durations.isNotEmpty() && period.duration() !in filters.durations) return false
    if (filters.keywords.any { it.isNotBlank() && !it.occursIn(haystack) }) return false
    return true
}

/**
 * Months of the period counted inclusively: October 2026 to March 2027 is 6, a single month is 1.
 * A period whose end is before its start (the app does not allow it) counts as one month.
 */
internal fun ProjectPeriod.monthsCount(): Int = (start.monthsUntil(end) + 1).coerceAtLeast(1)

/** The duration chip of the period: 1-3 months, 4-6 months, 7 and more. */
internal fun ProjectPeriod.duration(): ProjectDuration = when (monthsCount()) {
    in 1..3 -> ProjectDuration.UpTo3Months
    in 4..6 -> ProjectDuration.From3To6Months
    else -> ProjectDuration.Over6Months
}
