package org.matchlab.academic_net.feature.projects.data.mock

import kotlinx.datetime.YearMonth
import org.matchlab.academic_net.common.model.Project
import org.matchlab.academic_net.common.model.ProjectPeriod
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.ProjectStatus
import org.matchlab.academic_net.common.model.ProjectType
import org.matchlab.academic_net.common.model.Role
import org.matchlab.academic_net.common.model.RoleId
import org.matchlab.academic_net.common.model.RoleMember
import org.matchlab.academic_net.common.model.Sphere
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.model.UserSummary
import org.matchlab.academic_net.mockserver.MockAccounts

/** Sample data of the mock backend: the people, spheres and projects from the Figma screens. */
object MockProjectsData {
    /** The signed-in user of the mock backend: the account every mock sign-in lands on. */
    val currentUser: UserSummary = MockAccounts.viewer

    private val arjun = UserSummary(UserId("user-arjun"), "Арджун Мехта", isVerified = true)
    private val lina = UserSummary(UserId("user-lina"), "Лина Ковач", isVerified = true)

    val spheres: List<Sphere> = listOf(
        Sphere("computer-science", "Computer Science"),
        Sphere("data-science", "Data Science"),
        Sphere("engineering", "Инженерия"),
        Sphere("mathematics", "Математика"),
        Sphere("physics", "Физика"),
        Sphere("chemistry", "Химия"),
        Sphere("biology", "Биология"),
        Sphere("medicine", "Медицина"),
        Sphere("economics", "Экономика"),
        Sphere("social-sciences", "Социология"),
        Sphere("linguistics", "Лингвистика"),
        Sphere("design", "Дизайн"),
    )

    private fun sphere(id: String) = spheres.first { it.id == id }

    /** Projects of [currentUser], newest first. */
    fun myProjects(): List<Project> = listOf(
        Project(
            id = ProjectId("project-campus-navigation"),
            title = "AI-навигация по кампусу",
            type = ProjectType.Scientific,
            sphere = sphere("computer-science"),
            problem = "Навигация по кампусу фрагментирована: GPS не работает внутри зданий, " +
                "а карты не отражают изменения расписания и ремонтов.",
            goal = "Собрать систему компьютерного зрения, которая распознаёт помещения и " +
                "строит маршрут внутри корпусов в реальном времени.",
            tags = listOf("Machine Learning", "Computer Vision", "Mobile Dev"),
            initiator = currentUser,
            roles = listOf(
                Role(
                    id = RoleId("role-ml-engineer"),
                    title = "ML Engineer",
                    skills = listOf("TensorFlow", "Python"),
                    member = RoleMember(arjun, rating = 4.8, completedProjects = 3),
                ),
                Role(
                    id = RoleId("role-data-analyst"),
                    title = "Data Analyst",
                    skills = listOf("SQL", "Pandas"),
                    member = RoleMember(lina, rating = 4.9, completedProjects = 5),
                ),
                Role(
                    id = RoleId("role-mobile-developer"),
                    title = "Mobile Developer",
                    skills = listOf("Flutter", "Dart"),
                ),
                Role(
                    id = RoleId("role-ux-researcher"),
                    title = "UX Researcher",
                    skills = listOf("Figma", "Интервью"),
                ),
            ),
            status = ProjectStatus.Recruiting,
            period = ProjectPeriod(YearMonth(2026, 10), YearMonth(2027, 3)),
            viewerIsInitiator = true,
        ),
        Project(
            id = ProjectId("project-digital-twin"),
            title = "Цифровой двойник лаборатории",
            type = ProjectType.Practical,
            sphere = sphere("engineering"),
            problem = "Оборудование лаборатории простаивает без расписания, а студенты " +
                "тратят время на ожидание доступа к установкам.",
            goal = "Построить цифровую модель лаборатории с загрузкой оборудования " +
                "в реальном времени и онлайн-записью.",
            tags = listOf("IoT", "Digital Twin"),
            initiator = currentUser,
            roles = listOf(
                Role(RoleId("role-iot-engineer"), "IoT Engineer", listOf("MQTT", "Arduino")),
                Role(RoleId("role-backend"), "Backend Developer", listOf("Kotlin", "PostgreSQL")),
                Role(RoleId("role-frontend"), "Frontend Developer", listOf("React", "TypeScript")),
                Role(RoleId("role-3d"), "3D Artist", listOf("Blender")),
                Role(RoleId("role-pm"), "Project Manager", listOf("Agile")),
            ),
            status = ProjectStatus.Recruiting,
            period = ProjectPeriod(YearMonth(2026, 9), YearMonth(2027, 3)),
            viewerIsInitiator = true,
        ),
    )
}
