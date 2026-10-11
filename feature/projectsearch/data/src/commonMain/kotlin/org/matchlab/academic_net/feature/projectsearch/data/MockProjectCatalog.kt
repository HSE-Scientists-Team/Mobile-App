package org.matchlab.academic_net.feature.projectsearch.data

import kotlinx.datetime.YearMonth
import org.matchlab.academic_net.common.model.Project
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.ProjectPeriod
import org.matchlab.academic_net.common.model.ProjectStatus
import org.matchlab.academic_net.common.model.ProjectType
import org.matchlab.academic_net.common.model.Role
import org.matchlab.academic_net.common.model.RoleId
import org.matchlab.academic_net.common.model.RoleMember
import org.matchlab.academic_net.common.model.Sphere
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.model.UserSummary

/**
 * The published projects of other people on the mock server, the newest first. The first three
 * are the cards of "02 Поиск проектов"; the rest give the filters something to filter: periods of
 * 1 to 12 months (some across the new year), both types and different spheres.
 *
 * The spheres have the ids of the projects feature's `MockProjectsData.spheres`, which the app
 * offers in the sphere filter.
 */
object MockProjectCatalog {
    private val computerScience = Sphere("computer-science", "Computer Science")
    private val dataScience = Sphere("data-science", "Data Science")
    private val engineering = Sphere("engineering", "Инженерия")
    private val physics = Sphere("physics", "Физика")
    private val chemistry = Sphere("chemistry", "Химия")
    private val biology = Sphere("biology", "Биология")
    private val medicine = Sphere("medicine", "Медицина")
    private val economics = Sphere("economics", "Экономика")
    private val socialSciences = Sphere("social-sciences", "Социология")
    private val linguistics = Sphere("linguistics", "Лингвистика")
    private val design = Sphere("design", "Дизайн")

    private fun user(id: String, name: String, verified: Boolean = true) =
        UserSummary(UserId("user-$id"), name, isVerified = verified)

    private fun role(id: String, title: String, vararg skills: String, member: UserSummary? = null) = Role(
        id = RoleId("role-$id"),
        title = title,
        skills = skills.toList(),
        member = member?.let { RoleMember(it) },
    )

    private fun period(startYear: Int, startMonth: Int, endYear: Int, endMonth: Int) =
        ProjectPeriod(YearMonth(startYear, startMonth), YearMonth(endYear, endMonth))

    private fun project(
        id: String,
        title: String,
        type: ProjectType,
        sphere: Sphere,
        problem: String,
        goal: String,
        tags: List<String>,
        initiator: UserSummary,
        university: String,
        roles: List<Role>,
        period: ProjectPeriod,
    ) = MockSearchProject(
        project = Project(
            id = ProjectId("project-$id"),
            title = title,
            type = type,
            sphere = sphere,
            problem = problem,
            goal = goal,
            tags = tags,
            initiator = initiator,
            roles = roles,
            status = ProjectStatus.Recruiting,
            period = period,
            viewerIsInitiator = false,
        ),
        initiatorUniversity = university,
    )

    val all: List<MockSearchProject> = listOf(
        // The three cards of the Figma screen "02 Поиск проектов". 6 months.
        project(
            id = "found-campus-navigation",
            title = "AI-навигация по кампусу",
            type = ProjectType.Scientific,
            sphere = computerScience,
            problem = "Навигация по кампусу фрагментирована: GPS не работает внутри зданий, " +
                "а карты не отражают изменения расписания и ремонтов.",
            goal = "Собрать систему компьютерного зрения, которая распознаёт помещения и " +
                "строит маршрут внутри корпусов в реальном времени.",
            tags = listOf("Machine Learning", "Computer Vision", "Mobile Dev"),
            initiator = user("elena-vasquez", "Проф. Елена Васкес"),
            university = "Stanford",
            roles = listOf(
                role("campus-ml", "ML Engineer", "TensorFlow", "Python"),
                role("campus-mobile", "Mobile Developer", "Kotlin", "Android"),
                role("campus-data", "Data Analyst", "SQL", "Pandas"),
                role("campus-ux", "UX Researcher", "Interviews", "Figma"),
            ),
            period = period(2026, 10, 2027, 3),
        ),
        // 7 months.
        project(
            id = "found-mri-anomalies",
            title = "Обнаружение аномалий в МРТ",
            type = ProjectType.Scientific,
            sphere = medicine,
            problem = "Радиологи тратят много времени на первичный просмотр снимков, " +
                "и редкие аномалии легко пропустить.",
            goal = "Обучить модель, которая выделяет подозрительные области на МРТ: " +
                "машинное обучение на размеченных снимках клиники.",
            tags = listOf("Медицина", "Deep Learning", "DICOM"),
            initiator = user("anna-petrova-sechenov", "Д-р Анна Петрова"),
            university = "Сеченовский",
            roles = listOf(
                role("mri-ml", "ML Engineer", "PyTorch", "Python"),
                role("mri-radiologist", "Radiology Consultant", "MRI", "Annotation"),
                role("mri-stats", "Biostatistician", "R", "Statistics"),
            ),
            period = period(2026, 11, 2027, 5),
        ),
        // 7 months, a practical project.
        project(
            id = "found-lab-digital-twin",
            title = "Цифровой двойник лаборатории",
            type = ProjectType.Practical,
            sphere = engineering,
            problem = "Состояние оборудования и расписание лаборатории ведутся вручную, " +
                "поэтому простои замечают с опозданием.",
            goal = "Собрать цифровую модель лаборатории с данными датчиков и панелью " +
                "мониторинга в реальном времени.",
            tags = listOf("IoT", "Digital Twin", "Dashboards"),
            initiator = user("ivan-orlov", "Иван Орлов"),
            university = "ИТМО",
            roles = listOf(
                role("twin-iot", "IoT Engineer", "MQTT", "C++"),
                role("twin-backend", "Backend Developer", "Kotlin", "PostgreSQL", member = user("timur", "Тимур Алиев")),
                role("twin-frontend", "Frontend Developer", "TypeScript", "React"),
                role("twin-designer", "UI Designer", "Figma"),
                role("twin-qa", "QA Engineer", "Test Automation"),
            ),
            period = period(2026, 9, 2027, 3),
        ),
        // 3 months: the upper edge of "до 3 мес".
        project(
            id = "found-library-app",
            title = "Мобильное приложение библиотеки",
            type = ProjectType.Practical,
            sphere = design,
            problem = "Студенты не видят наличие книг и очередей в библиотеке и ходят туда зря.",
            goal = "Спроектировать и сделать прототип приложения с каталогом, бронированием и " +
                "картой читальных залов.",
            tags = listOf("Mobile Dev", "UX", "Прототип"),
            initiator = user("daria-smirnova", "Дарья Смирнова", verified = false),
            university = "ВШЭ",
            roles = listOf(
                role("library-designer", "Product Designer", "Figma", "UX Research"),
                role("library-mobile", "Mobile Developer", "Flutter", "Dart"),
            ),
            period = period(2026, 10, 2026, 12),
        ),
        // 4 months: the lower edge of "3–6 мес".
        project(
            id = "found-digital-habits",
            title = "Цифровые привычки студентов",
            type = ProjectType.Scientific,
            sphere = socialSciences,
            problem = "Нет данных о том, как студенты распределяют время между учёбой, " +
                "соцсетями и сном.",
            goal = "Провести опрос и серию интервью и подготовить статью о цифровых " +
                "привычках студентов.",
            tags = listOf("Опрос", "Интервью", "Статистика"),
            initiator = user("mikhail-gromov", "Михаил Громов"),
            university = "СПбГУ",
            roles = listOf(
                role("habits-sociologist", "Sociologist", "Interviews", "Qualitative Analysis"),
                role("habits-analyst", "Data Analyst", "R", "Statistics"),
                role("habits-editor", "Scientific Editor", "Academic Writing"),
            ),
            period = period(2026, 11, 2027, 2),
        ),
        // 1 month.
        project(
            id = "found-schedule-bot",
            title = "Telegram-бот для расписания",
            type = ProjectType.Practical,
            sphere = computerScience,
            problem = "Расписание факультета публикуется таблицей, в которой тяжело найти свою группу.",
            goal = "Запустить бота, который присылает расписание группы и уведомляет об изменениях.",
            tags = listOf("Telegram", "Kotlin", "Backend"),
            initiator = user("ilyas-karimov", "Ильяс Каримов", verified = false),
            university = "КФУ",
            roles = listOf(
                role("bot-backend", "Backend Developer", "Kotlin", "Docker"),
                role("bot-tester", "QA Engineer", "Test Automation"),
            ),
            period = period(2026, 12, 2026, 12),
        ),
        // 12 months, a long research.
        project(
            id = "found-perovskite-cells",
            title = "Перовскитные солнечные элементы",
            type = ProjectType.Scientific,
            sphere = physics,
            problem = "Перовскитные элементы быстро деградируют на воздухе, и срок их службы " +
                "остаётся слишком коротким.",
            goal = "Подобрать защитное покрытие и опубликовать результаты стабильности " +
                "образцов за год испытаний.",
            tags = listOf("Материаловедение", "Эксперимент", "Публикация"),
            initiator = user("natalia-egorova", "Проф. Наталья Егорова"),
            university = "МФТИ",
            roles = listOf(
                role("cells-physicist", "Experimental Physicist", "Spectroscopy", "Thin Films"),
                role("cells-chemist", "Chemist", "Synthesis"),
                role("cells-modeler", "Computational Modeler", "Python", "DFT"),
            ),
            period = period(2026, 10, 2027, 9),
        ),
        // 3 months across the new year.
        project(
            id = "found-student-budget",
            title = "Бюджет студента: финансовая грамотность",
            type = ProjectType.Practical,
            sphere = economics,
            problem = "Первокурсники не умеют планировать расходы и к середине месяца остаются без денег.",
            goal = "Сделать сервис с калькулятором бюджета и набор коротких уроков для студентов.",
            tags = listOf("Финансы", "Образование", "Web"),
            initiator = user("pavel-zaitsev", "Павел Зайцев", verified = false),
            university = "РЭУ им. Плеханова",
            roles = listOf(
                role("budget-economist", "Economist", "Excel", "Personal Finance"),
                role("budget-web", "Web Developer", "TypeScript", "React"),
            ),
            period = period(2026, 12, 2027, 2),
        ),
        // 6 months across the new year: the upper edge of "3–6 мес".
        project(
            id = "found-gut-microbiome",
            title = "Микробиом кишечника и стресс",
            type = ProjectType.Scientific,
            sphere = biology,
            problem = "Связь стресса во время сессии и состава микробиома описана на малых " +
                "выборках и противоречива.",
            goal = "Собрать образцы у студентов в течение семестра и оценить изменения состава микробиома.",
            tags = listOf("Микробиом", "Биоинформатика", "Статистика"),
            initiator = user("maria-sokolova", "Мария Соколова"),
            university = "МГУ",
            roles = listOf(
                role("microbiome-bioinformatician", "Bioinformatician", "Python", "R", "16S"),
                role("microbiome-lab", "Laboratory Assistant", "PCR"),
                role("microbiome-statistician", "Statistician", "R", "Statistics"),
            ),
            period = period(2026, 11, 2027, 4),
        ),
        // 4 months across the new year, tagged with the Russian "машинное обучение".
        project(
            id = "found-demand-forecast",
            title = "Прогноз спроса в столовых кампуса",
            type = ProjectType.Practical,
            sphere = dataScience,
            problem = "Столовые готовят слишком много или слишком мало: спрос на блюда меняется " +
                "по дням недели и сессиям.",
            goal = "Построить прогноз продаж по блюдам и показать его в простой панели для персонала.",
            tags = listOf("машинное обучение", "Time Series", "Dashboards"),
            initiator = user("lina-kovach", "Лина Ковач"),
            university = "ВШЭ",
            roles = listOf(
                role("demand-ml", "Data Scientist", "Python", "Pandas", "Time Series"),
                role("demand-bi", "BI Developer", "SQL", "Tableau"),
            ),
            period = period(2026, 10, 2027, 1),
        ),
        // 8 months.
        project(
            id = "found-russian-dialects",
            title = "Корпус региональных говоров",
            type = ProjectType.Scientific,
            sphere = linguistics,
            problem = "Региональные говоры исчезают, а записей, пригодных для анализа, мало.",
            goal = "Расшифровать и разметить аудиозаписи экспедиций и открыть корпус для исследователей.",
            tags = listOf("Корпусная лингвистика", "NLP", "Разметка"),
            initiator = user("olga-kuznetsova", "Д-р Ольга Кузнецова"),
            university = "СПбГУ",
            roles = listOf(
                role("dialects-linguist", "Linguist", "Phonetics", "Annotation"),
                role("dialects-nlp", "NLP Engineer", "Python", "spaCy"),
                role("dialects-annotator", "Annotator", "Transcription"),
            ),
            period = period(2026, 9, 2027, 4),
        ),
        // 5 months, ends in the next year.
        project(
            id = "found-green-chemistry",
            title = "Биоразлагаемая упаковка из отходов кофе",
            type = ProjectType.Scientific,
            sphere = chemistry,
            problem = "Одноразовая пластиковая упаковка в кофейнях кампуса не перерабатывается.",
            goal = "Получить образцы упаковки из кофейного жмыха и сравнить их прочность и срок разложения.",
            tags = listOf("Зелёная химия", "Материалы", "Эксперимент"),
            initiator = user("denis-belov", "Денис Белов", verified = false),
            university = "РХТУ им. Менделеева",
            roles = listOf(
                role("packaging-chemist", "Chemist", "Polymers", "Synthesis"),
                role("packaging-engineer", "Materials Engineer", "Mechanical Testing"),
            ),
            period = period(2026, 11, 2027, 3),
        ),
    )
}
