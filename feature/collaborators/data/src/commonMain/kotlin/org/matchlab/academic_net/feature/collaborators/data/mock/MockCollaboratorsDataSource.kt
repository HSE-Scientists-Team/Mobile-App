package org.matchlab.academic_net.feature.collaborators.data.mock

import kotlinx.coroutines.delay
import org.matchlab.academic_net.common.model.Collaborator
import org.matchlab.academic_net.common.model.ProjectType
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.model.UserSummary
import org.matchlab.academic_net.feature.collaborators.data.CollaboratorsDataSource
import org.matchlab.academic_net.feature.collaborators.domain.CollaboratorQuery
import org.matchlab.academic_net.feature.collaborators.domain.RoleRequirementProvider
import org.matchlab.academic_net.feature.collaborators.domain.ScoredCollaborator

/**
 * A person as the server's database has them: what the app shows ([collaborator]) plus what the
 * search looks at.
 */
data class MockPerson(
    val collaborator: Collaborator,
    val skills: List<String>,
    val verifiedProjectsCount: Int,
    val publicationsCount: Int,
    /** Types of the works in the person's portfolio. */
    val workTypes: Set<ProjectType>,
)

/**
 * In-memory stand-in for the people-search server. All the searching happens here, as it will on
 * the real backend: matching the text and filters, counting how well a person fits a role and
 * ordering. The roles' skills come from [requirements] (the server would read its projects
 * table). Answers after [latencyMillis], like a network call.
 *
 * Rules of this mock:
 * - the text is comma-separated terms; a person fits when at least one term occurs (ignoring case)
 *   in their name, university, study info, field or skills; no terms means everyone;
 * - filters are combined with "and"; every keyword has to occur;
 * - the match is the share of the role's skills the person has; people are ordered by it, then
 *   verified first, then by name.
 */
class MockCollaboratorsDataSource(
    private val requirements: RoleRequirementProvider,
    private val people: List<MockPerson> = MockCollaborators.all,
    private val latencyMillis: Long = DefaultLatencyMillis,
) : CollaboratorsDataSource {
    override suspend fun search(query: CollaboratorQuery): List<ScoredCollaborator> {
        simulateNetwork()
        val skills = query.role
            ?.let { requirements.getRoleRequirement(it.projectId, it.roleId) }
            ?.skills
            .orEmpty()
        return people
            .filter { it.matches(query) }
            .map { it.scoredFor(skills) }
            .sortedWith(ByRelevance)
    }

    private suspend fun simulateNetwork() {
        if (latencyMillis > 0) delay(latencyMillis)
    }

    private companion object {
        const val DefaultLatencyMillis = 300L
    }
}

private val ByRelevance: Comparator<ScoredCollaborator> =
    compareByDescending<ScoredCollaborator> { it.matchPercent ?: 0 }
        .thenByDescending { it.collaborator.user.isVerified }
        .thenBy { it.collaborator.user.name }

private fun MockPerson.scoredFor(roleSkills: List<String>) =
    ScoredCollaborator(collaborator, matchPercent(roleSkills, skills))

/** Share of the [required] skills that [skills] has, 0..100, `null` if nothing is required. */
internal fun matchPercent(required: List<String>, skills: List<String>): Int? {
    val needed = required.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.distinct()
    if (needed.isEmpty()) return null
    val have = skills.map { it.trim().lowercase() }.toSet()
    val covered = needed.count { it in have }
    return (covered * 100 + needed.size / 2) / needed.size
}

/** Terms of the search text: comma-separated, blanks dropped. */
private fun termsOf(text: String): List<String> =
    text.split(',').map { it.trim() }.filter { it.isNotEmpty() }

private fun MockPerson.haystack(): String = buildList {
    add(collaborator.user.name)
    add(collaborator.university)
    add(collaborator.studyInfo)
    add(collaborator.field)
    addAll(skills)
}.joinToString(separator = "\n").lowercase()

private fun String.occursIn(haystack: String): Boolean = isNotBlank() && trim().lowercase() in haystack

internal fun MockPerson.matches(query: CollaboratorQuery): Boolean {
    val filters = query.filters
    val haystack = haystack()
    val terms = termsOf(query.text)

    if (terms.isNotEmpty() && terms.none { it.occursIn(haystack) }) return false
    if (filters.verifiedProfile && !collaborator.user.isVerified) return false
    if (filters.hasVerifiedProjects && verifiedProjectsCount == 0) return false
    if (filters.hasPublications && publicationsCount == 0) return false
    if (filters.workTypes.isNotEmpty() && workTypes.none { it in filters.workTypes }) return false
    if (filters.university.isNotBlank() && !filters.university.occursIn(collaborator.university.lowercase())) {
        return false
    }
    if (filters.keywords.any { it.isNotBlank() && !it.occursIn(haystack) }) return false
    return true
}

/** The people of the Figma screens plus a few more so the filters have something to filter. */
object MockCollaborators {
    private val science = setOf(ProjectType.Scientific)
    private val practice = setOf(ProjectType.Practical)
    private val both = ProjectType.entries.toSet()

    val all: List<MockPerson> = listOf(
        MockPerson(
            collaborator = Collaborator(
                user = UserSummary(UserId("user-arjun"), "Арджун Мехта", isVerified = true),
                university = "Stanford",
                studyInfo = "3 курс бакалавриата",
                field = "Computer Science",
            ),
            skills = listOf("Computer Vision", "Python", "PyTorch", "TensorFlow"),
            verifiedProjectsCount = 3,
            publicationsCount = 1,
            workTypes = both,
        ),
        MockPerson(
            collaborator = Collaborator(
                user = UserSummary(UserId("user-maria"), "Мария Соколова", isVerified = true),
                university = "МГУ",
                studyInfo = "аспирантка",
                field = "биоинформатика",
            ),
            skills = listOf("Python", "Data Analysis", "R", "Machine Learning"),
            verifiedProjectsCount = 2,
            publicationsCount = 4,
            workTypes = science,
        ),
        MockPerson(
            collaborator = Collaborator(
                user = UserSummary(UserId("user-daniil"), "Даниил Крюков", isVerified = true),
                university = "ИТМО",
                studyInfo = "магистрант",
                field = "робототехника",
            ),
            skills = listOf("Computer Vision", "ROS", "C++", "Python"),
            verifiedProjectsCount = 1,
            publicationsCount = 0,
            workTypes = practice,
        ),
        MockPerson(
            collaborator = Collaborator(
                user = UserSummary(UserId("user-lina"), "Лина Ковач", isVerified = true),
                university = "ВШЭ",
                studyInfo = "магистрантка",
                field = "анализ данных",
            ),
            skills = listOf("SQL", "Pandas", "Python", "Tableau"),
            verifiedProjectsCount = 5,
            publicationsCount = 2,
            workTypes = both,
        ),
        MockPerson(
            collaborator = Collaborator(
                user = UserSummary(UserId("user-ilyas"), "Ильяс Каримов", isVerified = false),
                university = "КФУ",
                studyInfo = "2 курс бакалавриата",
                field = "Computer Science",
            ),
            skills = listOf("Python", "Flutter", "Dart", "Firebase"),
            verifiedProjectsCount = 0,
            publicationsCount = 0,
            workTypes = practice,
        ),
        MockPerson(
            collaborator = Collaborator(
                user = UserSummary(UserId("user-anna"), "Анна Петрова", isVerified = true),
                university = "СПбГУ",
                studyInfo = "4 курс бакалавриата",
                field = "психология",
            ),
            skills = listOf("Figma", "Интервью", "UX Research", "Usability"),
            verifiedProjectsCount = 2,
            publicationsCount = 1,
            workTypes = both,
        ),
        MockPerson(
            collaborator = Collaborator(
                user = UserSummary(UserId("user-sergey"), "Сергей Волков", isVerified = false),
                university = "МФТИ",
                studyInfo = "магистрант",
                field = "прикладная математика",
            ),
            skills = listOf("Kotlin", "Flutter", "Dart", "Android"),
            verifiedProjectsCount = 0,
            publicationsCount = 0,
            workTypes = practice,
        ),
        MockPerson(
            collaborator = Collaborator(
                user = UserSummary(UserId("user-olga"), "Ольга Никитина", isVerified = true),
                university = "Stanford",
                studyInfo = "аспирантка",
                field = "Computer Science",
            ),
            skills = listOf("Machine Learning", "Computer Vision", "Python", "TensorFlow", "CUDA"),
            verifiedProjectsCount = 4,
            publicationsCount = 6,
            workTypes = science,
        ),
        MockPerson(
            collaborator = Collaborator(
                user = UserSummary(UserId("user-timur"), "Тимур Алиев", isVerified = true),
                university = "ИТМО",
                studyInfo = "3 курс бакалавриата",
                field = "информационные системы",
            ),
            skills = listOf("Kotlin", "PostgreSQL", "Docker", "Python"),
            verifiedProjectsCount = 1,
            publicationsCount = 0,
            workTypes = practice,
        ),
        MockPerson(
            collaborator = Collaborator(
                user = UserSummary(UserId("user-eva"), "Ева Миронова", isVerified = false),
                university = "МГУ",
                studyInfo = "1 курс магистратуры",
                field = "биология",
            ),
            skills = listOf("R", "Statistics", "Data Analysis"),
            verifiedProjectsCount = 0,
            publicationsCount = 1,
            workTypes = science,
        ),
    )
}
