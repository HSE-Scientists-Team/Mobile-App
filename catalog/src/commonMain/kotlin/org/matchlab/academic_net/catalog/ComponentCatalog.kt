package org.matchlab.academic_net.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.datetime.YearMonth
import org.matchlab.academic_net.common.model.ProjectPeriod
import org.matchlab.academic_net.common.model.ProjectStatus
import org.matchlab.academic_net.common.model.ProjectType
import org.matchlab.academic_net.common.model.Sphere
import org.matchlab.academic_net.components.ActionListItem
import org.matchlab.academic_net.components.AppAvatars
import org.matchlab.academic_net.components.AppBadgeCount
import org.matchlab.academic_net.components.AppBadgeDot
import org.matchlab.academic_net.components.AppButton
import org.matchlab.academic_net.components.AppButtonVariant
import org.matchlab.academic_net.components.AppCard
import org.matchlab.academic_net.components.AppFab
import org.matchlab.academic_net.components.AppFilterChip
import org.matchlab.academic_net.components.AppIconButton
import org.matchlab.academic_net.components.AppIconButtonVariant
import org.matchlab.academic_net.components.AppIcons
import org.matchlab.academic_net.components.AppInputChip
import org.matchlab.academic_net.components.AppNavigationBar
import org.matchlab.academic_net.components.AppNavigationBarItem
import org.matchlab.academic_net.components.AppRadioButton
import org.matchlab.academic_net.components.AppSearchBar
import org.matchlab.academic_net.components.AppSearchBarButton
import org.matchlab.academic_net.components.AppSnackbarHost
import org.matchlab.academic_net.components.AppSegmentedControl
import org.matchlab.academic_net.components.AppSelectField
import org.matchlab.academic_net.components.AppStatusChip
import org.matchlab.academic_net.components.AppStatusChipTone
import org.matchlab.academic_net.components.AppSwitch
import org.matchlab.academic_net.components.AppTabs
import org.matchlab.academic_net.components.AppTagChip
import org.matchlab.academic_net.components.AppTagsField
import org.matchlab.academic_net.components.AppTextField
import org.matchlab.academic_net.components.AppTopBar
import org.matchlab.academic_net.components.Avatar
import org.matchlab.academic_net.components.FilterChipGroup
import org.matchlab.academic_net.components.FiltersButton
import org.matchlab.academic_net.components.FiltersSheet
import org.matchlab.academic_net.components.InfoBanner
import org.matchlab.academic_net.components.InfoCard
import org.matchlab.academic_net.components.LoadingState
import org.matchlab.academic_net.components.MessageState
import org.matchlab.academic_net.components.NotificationListItem
import org.matchlab.academic_net.components.NotificationListItemUiModel
import org.matchlab.academic_net.components.PersonCard
import org.matchlab.academic_net.components.PersonCardUiModel
import org.matchlab.academic_net.components.PersonListItem
import org.matchlab.academic_net.components.PersonListItemUiModel
import org.matchlab.academic_net.components.ProjectCard
import org.matchlab.academic_net.components.ProjectCardUiModel
import org.matchlab.academic_net.components.RadioListItem
import org.matchlab.academic_net.components.ReportIconButton
import org.matchlab.academic_net.components.RoleApplyCard
import org.matchlab.academic_net.components.RoleApplyCardUiModel
import org.matchlab.academic_net.components.RoleCardTitle
import org.matchlab.academic_net.components.SearchTopBar
import org.matchlab.academic_net.components.SecurityNote
import org.matchlab.academic_net.components.SectionHeader
import org.matchlab.academic_net.components.SettingListItem
import org.matchlab.academic_net.components.SheetHeader
import org.matchlab.academic_net.components.SkillChips
import org.matchlab.academic_net.components.SpherePickerSheet
import org.matchlab.academic_net.components.TagsBlock
import org.matchlab.academic_net.components.TextBlock
import org.matchlab.academic_net.components.ToggleListItem
import org.matchlab.academic_net.components.UploadCard
import org.matchlab.academic_net.components.person.personStatsText
import org.matchlab.academic_net.components.person.ratingText
import org.matchlab.academic_net.components.project.ProjectDescriptionBlocks
import org.matchlab.academic_net.components.project.ProjectHeader
import org.matchlab.academic_net.components.project.ProjectPeriodCard
import org.matchlab.academic_net.components.project.RolesSectionHeader
import org.matchlab.academic_net.components.project.label
import org.matchlab.academic_net.components.project.projectCardUiModel
import org.matchlab.academic_net.feature.chats.ui.ChatBubble
import org.matchlab.academic_net.feature.chats.ui.ChatBubbleUiModel
import org.matchlab.academic_net.feature.chats.ui.ChatComposer
import org.matchlab.academic_net.feature.chats.ui.ChatComposerUiModel
import org.matchlab.academic_net.feature.chats.ui.ChatListItem
import org.matchlab.academic_net.feature.chats.ui.ChatListItemUiModel
import org.matchlab.academic_net.feature.profile.ui.EducationCard
import org.matchlab.academic_net.feature.profile.ui.EducationCardUiModel
import org.matchlab.academic_net.feature.profile.ui.ProfileHeaderCard
import org.matchlab.academic_net.feature.profile.ui.ProfileHeaderCardUiModel
import org.matchlab.academic_net.feature.profile.ui.ProfileStatusUiModel
import org.matchlab.academic_net.feature.profile.ui.ReviewCard
import org.matchlab.academic_net.feature.profile.ui.ReviewCardUiModel
import org.matchlab.academic_net.feature.profile.ui.VerificationMark
import org.matchlab.academic_net.feature.profile.ui.WorkCard
import org.matchlab.academic_net.feature.profile.ui.WorkCardUiModel
import org.matchlab.academic_net.feature.projects.ui.CandidateCard
import org.matchlab.academic_net.feature.projects.ui.CandidateCardUiModel
import org.matchlab.academic_net.feature.projects.ui.ParticipantManageCard
import org.matchlab.academic_net.feature.projects.ui.RoleCard
import org.matchlab.academic_net.feature.projects.ui.RoleCardUiModel
import org.matchlab.academic_net.feature.projects.ui.StatCard
import org.matchlab.academic_net.feature.projects.ui.StatCardUiModel
import org.matchlab.academic_net.feature.projects.ui.VacantRoleCard
import org.matchlab.academic_net.feature.projects.ui.VacantRoleCardUiModel
import org.matchlab.academic_net.feature.responses.ui.ApplicationCard
import org.matchlab.academic_net.feature.responses.ui.ApplicationCardUiModel
import org.matchlab.academic_net.theme.AcademicNetTheme

/** Every design system component, grouped by module and filled with the Figma sample data. */
@Composable
fun ComponentCatalog() {
    AcademicNetTheme {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .windowInsetsPadding(WindowInsets.safeDrawing),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item { BaseComponents() }
            item { RepeatedTapComponents() }
            item { SharedFeatureComponents() }
            item { ProjectPageComponents() }
            item { SearchComponents() }
            item { ProjectsComponents() }
            item { ProfileComponents() }
            item { ChatsComponents() }
            item { ResponsesComponents() }
        }
    }
}

private val NoInsets = WindowInsets(0)
private val Elena = AppAvatars.Elena
private val Arjun = AppAvatars.Arjun

private val Initiator = PersonCardUiModel(
    name = "Проф. Елена Васкес",
    subtitle = "Инициатор · Stanford University",
    avatar = Elena,
    isVerified = true,
    stats = "★ 4.9 · 12 завершённых проектов",
    showChatButton = true,
)

@Composable
private fun BaseComponents() {
    Section(":components — базовые") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AppButton("Откликнуться", {})
            AppButton("Написать", {}, variant = AppButtonVariant.Outlined)
            AppButton("Отмена", {}, variant = AppButtonVariant.Text)
            AppButton("Пригласить", {}, variant = AppButtonVariant.Tonal)
            AppButton("Выгнать", {}, variant = AppButtonVariant.OutlinedError)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
            AppFab(onClick = {}, contentDescription = null)
            AppIconButton(AppIcons.MoreVertical, null, {})
            AppIconButton(AppIcons.Pencil, null, {}, variant = AppIconButtonVariant.Tonal)
            ReportIconButton(onClick = {})
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AppInputChip("Machine Learning", onRemove = {})
            AppTagChip("Machine Learning")
            AppFilterChip("Research", selected = true, onClick = {})
            AppFilterChip("Research", selected = false, onClick = {})
            AppStatusChip("Открыт к сотрудничеству", AppStatusChipTone.Success)
            AppStatusChip("Не ищу проекты", AppStatusChipTone.Off)
            AppStatusChip("На проверке", AppStatusChipTone.Pending)
        }
        var query by remember { mutableStateOf("") }
        AppSearchBar(query, { query = it }, placeholder = "Поиск проектов и людей")
        AppSearchBarButton(placeholder = "Поиск людей по навыкам и интересам", onClick = {})
        var scope by remember { mutableIntStateOf(0) }
        AppSegmentedControl(listOf("Проекты", "Люди"), scope, { scope = it })
        var title by remember { mutableStateOf("") }
        AppTextField(title, { title = it }, label = "Название проекта", placeholder = "Введите текст")
        AppTextField("", {}, label = "Цель", placeholder = "Что хотите получить на выходе", errorText = "Заполните поле")
        AppSelectField(label = "Сфера", value = "Computer Science", placeholder = "Выберите сферу", onClick = {})
        AppSelectField(
            label = "Сфера",
            value = null,
            placeholder = "Выберите сферу",
            onClick = {},
            errorText = "Выберите сферу",
        )
        val tags = remember { mutableStateListOf("Machine Learning", "Python") }
        AppTagsField(
            label = "Теги",
            tags = tags,
            onAddTag = { tags += it },
            onRemoveTag = { tags -= it },
            placeholder = "Введите тег и нажмите Enter",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
            var checked by remember { mutableStateOf(false) }
            AppSwitch(checked, { checked = it })
            AppSwitch(true, {})
            var selected by remember { mutableStateOf(true) }
            AppRadioButton(selected, { selected = !selected })
            AppRadioButton(false, {})
        }
        var tab by remember { mutableIntStateOf(4) }
        AppNavigationBar(windowInsets = NoInsets) {
            listOf(
                AppIcons.Rss to "Лента",
                AppIcons.Briefcase to "Проекты",
                AppIcons.FileText to "Отклики",
                AppIcons.Message to "Чаты",
                AppIcons.User to "Профиль",
            ).forEachIndexed { index, (icon, label) ->
                AppNavigationBarItem(tab == index, { tab = index }, icon, label, badge = index == 3)
            }
        }
        SectionHeader("Раздел", action = "Все", onAction = {})
        SheetHeader("Заголовок", action = "Сбросить", onAction = {})
        var tabs by remember { mutableIntStateOf(0) }
        AppTabs(listOf("Обзор", "Участники", "Отклики"), tabs, { tabs = it })
        AppTopBar("Заголовок экрана", onNavigateBack = {}, windowInsets = NoInsets, actions = {
            ReportIconButton(onClick = {})
            AppIconButton(AppIcons.MoreVertical, null, {})
        })
        AppTopBar("Заголовок экрана", subtitle = "Подзаголовок", onNavigateBack = {}, windowInsets = NoInsets)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(Elena, null, size = 64.dp)
            Avatar(Arjun, null, size = 48.dp)
            Avatar(Elena, null, size = 32.dp)
            Avatar(Arjun, null, size = 24.dp)
            AppBadgeCount(2)
            AppBadgeDot()
        }
        InfoBanner("Пояснение к разделу или правило сервиса")
        TagsBlock("Теги", listOf("Machine Learning", "Machine Learning", "Machine Learning"))
        TextBlock("Заголовок раздела", "Текст раздела на несколько строк — описание проблемы, цели или любое другое описание.")
        ActionListItem("Действие", onClick = {}, subtitle = "Короткое пояснение, что произойдёт")
        var option by remember { mutableStateOf(false) }
        RadioListItem("Вариант ответа", option, { option = !option })
        SettingListItem("Название настройки", AppIcons.Lock, onClick = {}, subtitle = "Пояснение к настройке")
        var visible by remember { mutableStateOf(true) }
        ToggleListItem("Показывать меня в поиске", visible, { visible = it }, subtitle = "Пояснение к переключателю")
        // Saving: the row ignores taps and the switch is dimmed until the new value is stored.
        ToggleListItem("Показывать контакты", true, {}, subtitle = "Пояснение к переключателю", enabled = false)
        InfoCard("Окт 2026 — Март 2027", AppIcons.Calendar)
        UploadCard("Загрузить подтверждение", "PDF или скриншот с указанием авторства", {})
        SecurityNote("Данные входа хранятся на устройстве в зашифрованном виде")
        val snackbars = remember { SnackbarHostState() }
        val coroutineScope = rememberCoroutineScope()
        AppButton(
            text = "Показать сообщение",
            onClick = { coroutineScope.launch { snackbars.showSnackbar("Не удалось сохранить настройку. Попробуйте ещё раз") } },
            variant = AppButtonVariant.Outlined,
        )
        AppSnackbarHost(snackbars)
    }
}

/**
 * The protection from repeated taps, to try by hand: a burst of taps on a button or a card counts
 * once, the arrows of a stepper count every tap, and a loading state takes the taps that would
 * land on the button under it.
 */
@Composable
private fun RepeatedTapComponents() {
    Section(":components — повторные нажатия") {
        var opened by remember { mutableIntStateOf(0) }
        Text(
            text = "Открытий: $opened. Нажмите несколько раз подряд: засчитается одно нажатие за полсекунды",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AppButton("Открыть экран", { opened++ })
            AppIconButton(AppIcons.Pencil, "Изменить", { opened++ })
        }
        AppCard(onClick = { opened++ }) {
            Text(
                text = "Карточка: открывает экран",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        var steps by remember { mutableIntStateOf(0) }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AppIconButton(AppIcons.ArrowLeft, "Меньше", { steps-- }, repeatable = true)
            Text("Шагов: $steps (каждое нажатие считается)", color = MaterialTheme.colorScheme.onSurface)
            AppIconButton(AppIcons.ArrowRight, "Больше", { steps++ }, repeatable = true)
        }
        var underState by remember { mutableIntStateOf(0) }
        Text(
            text = "Нажатий на кнопку под загрузкой: $underState (должно остаться 0)",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(Modifier.fillMaxWidth().height(120.dp)) {
            AppButton("Старый экран", { underState++ }, Modifier.align(Alignment.Center))
            LoadingState()
        }
    }
}

@Composable
private fun SharedFeatureComponents() {
    Section(":components — общие для фич") {
        PersonCard(Initiator)
        PersonCard(PersonCardUiModel(name = "Даниил Крюков", subtitle = "ИТМО", avatar = Elena))
        PersonListItem(
            PersonListItemUiModel("Арджун Мехта", "Stanford · 3 курс · Computer Science", Arjun, isVerified = true),
            onClick = {},
        )
        PersonListItem(
            PersonListItemUiModel(
                name = "Арджун Мехта",
                subtitle = "Stanford · 3 курс бакалавриата · Computer Science",
                avatar = Arjun,
                isVerified = true,
            ),
            onClick = {},
            outlined = true,
        )
        NotificationListItem(NotificationListItemUiModel("Новый отклик на роль ML Engineer", "2 часа назад"))
        // The decisions of a moderator on the own profile.
        NotificationListItem(
            NotificationListItemUiModel("Подтверждено: Сертификат Deep Learning", "10:22", AppIcons.ShieldCheck),
        )
        NotificationListItem(
            NotificationListItemUiModel("Отклонено: Прототип AR-навигации", "Вчера", AppIcons.ShieldX),
        )
        // The rating on its own, as in the title of the reviews: "Отзывы · ★ 4.8".
        Text(
            text = "Отзывы · " + ratingText(4.8),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        ProjectCard(
            ProjectCardUiModel(
                title = "AI-навигация по кампусу",
                author = "Проф. Елена Васкес · Stanford",
                authorAvatar = Arjun,
                type = "Научный",
                footer = "4 места в команде · окт 2026 — мар 2027",
                tags = listOf("Поиск соавторов", "Computer Science"),
                isAuthorVerified = true,
                showInvitationActions = true,
            ),
        )
        // Built the way every list of projects does: labels and plurals follow the app language.
        ProjectCard(
            projectCardUiModel(
                title = "Обнаружение аномалий в МРТ",
                author = "Д-р Анна Петрова · Сеченовский",
                avatarKey = "u-anna",
                isAuthorVerified = true,
                type = ProjectType.Scientific,
                status = ProjectStatus.Recruiting,
                sphere = "Медицина",
                rolesCount = 3,
                period = ProjectPeriod(YearMonth(2026, 11), YearMonth(2027, 5)),
            ),
        )
    }
}

/** The pieces of "04 Проект" and "07 Управление проектом": the header, the blocks, the roles. */
@Composable
private fun ProjectPageComponents() {
    Section(":components — страница проекта") {
        ProjectHeader(
            title = "AI-навигация по кампусу",
            type = ProjectType.Scientific,
            status = ProjectStatus.Recruiting,
            sphere = "Computer Science",
        )
        // The line under the name: with the rating, and for a person with no reviews yet.
        PersonCard(
            PersonCardUiModel(
                name = "Проф. Елена Васкес",
                subtitle = "Инициатор · Stanford University",
                avatar = Elena,
                isVerified = true,
                stats = personStatsText(rating = 4.9, completedProjects = 12),
                showChatButton = true,
            ),
        )
        PersonCard(
            PersonCardUiModel(
                name = "Даниил Крюков",
                subtitle = "Инициатор · ИТМО",
                avatar = Arjun,
                stats = personStatsText(rating = null, completedProjects = 1),
            ),
        )
        ProjectDescriptionBlocks(
            problem = "Навигация по кампусу фрагментарна: GPS не работает внутри зданий, а карты не отражают " +
                "изменения расписания и ремонтов.",
            goal = "Собрать систему компьютерного зрения, которая распознаёт помещения и строит маршрут " +
                "внутри корпусов в реальном времени.",
            tags = listOf("Machine Learning", "Computer Vision", "Mobile Dev"),
        )
        RolesSectionHeader("Вакантные роли", occupiedCount = 2, totalCount = 4)
        RoleApplyCard(RoleApplyCardUiModel("Mobile Developer", listOf("Flutter", "Dart")), onApplyClick = {})
        // Without the button: applying is not available on the project page yet.
        RoleApplyCard(RoleApplyCardUiModel("UX Researcher", listOf("Figma", "Интервью")))
        RoleCardTitle("ML Engineer")
        SkillChips(listOf("Python", "PyTorch", "OpenCV"))
        ProjectPeriodCard(ProjectPeriod(YearMonth(2026, 10), YearMonth(2027, 3)))
    }
}

@Composable
private fun SearchComponents() {
    Section(":components — поиск и фильтры") {
        SearchTopBar(
            query = "машинное обучение",
            onQueryChange = {},
            placeholder = "Название, навык или вуз",
            onNavigateBack = {},
            backContentDescription = "Назад",
            filtersActive = true,
            onFiltersClick = {},
            filtersContentDescription = "Фильтры",
            windowInsets = NoInsets,
        )
        var emptyQuery by remember { mutableStateOf("") }
        SearchTopBar(
            query = emptyQuery,
            onQueryChange = { emptyQuery = it },
            placeholder = "Название, навык или вуз",
            onNavigateBack = {},
            backContentDescription = "Назад",
            filtersActive = false,
            onFiltersClick = {},
            filtersContentDescription = "Фильтры",
            windowInsets = NoInsets,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
            FiltersButton(active = false, onClick = {}, contentDescription = "Фильтры")
            FiltersButton(active = true, onClick = {}, contentDescription = "Фильтры")
        }
        FilterChipGroup("Тип проекта") {
            ProjectType.entries.forEachIndexed { index, type ->
                AppFilterChip(type.label(), selected = index == 0, onClick = {})
            }
        }
        FilterChipGroup("Сроки") {
            AppFilterChip("до 3 мес", selected = false, onClick = {})
            AppFilterChip("3–6 мес", selected = true, onClick = {})
            AppFilterChip("6+ мес", selected = false, onClick = {})
        }
        var filtersOpen by remember { mutableStateOf(false) }
        AppButton("Открыть FiltersSheet", { filtersOpen = true }, variant = AppButtonVariant.Outlined)
        if (filtersOpen) {
            FiltersSheet(
                title = "Фильтры",
                onDismiss = { filtersOpen = false },
                secondaryText = "Отмена",
                onSecondary = { filtersOpen = false },
                primaryText = "Применить",
                onPrimary = { filtersOpen = false },
                headerActionText = "Сбросить",
                onHeaderAction = {},
            ) {
                FilterChipGroup("Тип проекта") {
                    AppFilterChip("Научный", selected = true, onClick = {})
                    AppFilterChip("Проектный", selected = false, onClick = {})
                }
                AppSelectField("Сфера", "Computer Science", "Любая сфера", onClick = {})
            }
        }
        val spheres = remember {
            listOf(Sphere("cs", "Computer Science"), Sphere("med", "Медицина"), Sphere("iot", "IoT"))
        }
        var pickerOpen by remember { mutableStateOf(false) }
        var picked by remember { mutableStateOf<Sphere?>(null) }
        AppButton("Открыть SpherePickerSheet", { pickerOpen = true }, variant = AppButtonVariant.Outlined)
        if (pickerOpen) {
            SpherePickerSheet(
                spheres = spheres,
                selected = picked,
                loadFailed = false,
                onSelect = { picked = it; pickerOpen = false },
                onRetry = {},
                onDismiss = { pickerOpen = false },
                anyOptionLabel = "Любая сфера",
                onSelectAny = { picked = null; pickerOpen = false },
            )
        }
        Box(Modifier.fillMaxWidth().height(120.dp)) { LoadingState() }
        Box(Modifier.fillMaxWidth().height(240.dp)) {
            MessageState(
                title = "Ничего не найдено",
                text = "Измените запрос или фильтры",
                actionText = "Сбросить фильтры",
                onAction = {},
            )
        }
    }
}

@Composable
private fun ProjectsComponents() {
    Section(":feature:projects:ui") {
        CandidateCard(
            CandidateCardUiModel(
                name = "Арджун Мехта",
                subtitle = "Stanford · 3 курс бакалавриата · CS",
                avatar = Elena,
                isVerified = true,
                note = "Совпадение 92% · отклик 2 дня назад",
                tags = listOf("Machine Learning", "Machine Learning"),
                showActions = true,
            ),
        )
        RoleCard(RoleCardUiModel("ML Engineer", "Арджун Мехта", Elena, canManage = true))
        VacantRoleCard(VacantRoleCardUiModel("ML Engineer", listOf("Machine Learning", "Machine Learning"), isEditable = true))
        VacantRoleCard(VacantRoleCardUiModel("Mobile Developer", listOf("Flutter", "Dart"), canFindPeople = true))
        ParticipantManageCard(Initiator, onKickClick = {})
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard(StatCardUiModel("7", "новых откликов"), Modifier.weight(1f))
            StatCard(StatCardUiModel("3", "участника"), Modifier.weight(1f))
            StatCard(StatCardUiModel("2", "роли"), Modifier.weight(1f))
        }
    }
}

/**
 * The cards of "09 Профиль коллаборатора", "10 Мой профиль" and the lists of works and reviews,
 * in the states the screens use: the verification marks, the two kinds of work, the review cards
 * of a list (with the date) and of the page of one review (a tap on the author).
 */
@Composable
private fun ProfileComponents() {
    Section(":feature:profile:ui") {
        // Someone else's profile: the verification chip and one wide button.
        ProfileHeaderCard(
            ProfileHeaderCardUiModel(
                name = "Арджун Мехта",
                subtitle = "Stanford University · 3 курс · Computer Science",
                avatar = Elena,
                isVerified = true,
                statuses = listOf(ProfileStatusUiModel("Профиль подтверждён", AppStatusChipTone.Success)),
                actionText = "Написать",
            ),
        )
        // Own profile: no chip, no button.
        ProfileHeaderCard(
            ProfileHeaderCardUiModel(
                name = "Алексей Морозов",
                subtitle = "Stanford University · 3 курс · Computer Science",
                avatar = Arjun,
                isVerified = true,
            ),
        )
        // The three marks: confirmed, being checked (only the owner sees it) and not confirmed.
        EducationCard(EducationCardUiModel("B.S. Computer Science", "Stanford University · выпуск 2027", VerificationMark.Verified))
        EducationCard(EducationCardUiModel("Сертификат Deep Learning", "Coursera · 2025", VerificationMark.Pending))
        WorkCard(
            WorkCardUiModel(
                title = "Localized CV Pipelines on Edge Devices",
                subtitle = "Пайплайн распознавания помещений по кадрам с телефона, точность 92%",
                mark = VerificationMark.Verified,
            ),
        )
        WorkCard(
            WorkCardUiModel(
                title = "Прототип AR-навигации",
                subtitle = "Проектная работа · ссылка на демо",
                type = ProjectType.Practical,
                mark = VerificationMark.Unverified,
            ),
        )
        WorkCard(
            WorkCardUiModel(
                title = "Сегментация коридоров на Raspberry Pi",
                subtitle = "Проектная работа · GitHub",
                type = ProjectType.Practical,
                mark = VerificationMark.Pending,
            ),
            onClick = {},
        )
        ReviewCard(
            ReviewCardUiModel(
                authorName = "Проф. Елена Васкес",
                authorRole = "Инициатор · AI-навигация по кампусу · мар 2026",
                authorAvatar = Elena,
                title = "Довёл модели до демо",
                rating = 5,
                text = "«Взял на себя обучение моделей и довёл до демо. Сроки соблюдал, писал понятные отчёты.»",
            ),
            onClick = {},
            maxTextLines = 3,
        )
        // The page of one review: the whole text, the author line leads to their profile.
        ReviewCard(
            ReviewCardUiModel(
                authorName = "Лина Ковач",
                authorRole = "Участник · AI-навигация по кампусу",
                authorAvatar = Arjun,
                title = "Помогал команде с данными",
                rating = 5,
                text = "«Сильный инженер, помогал команде с пайплайном данных. Разобрался в предметной области за " +
                    "неделю, предложил свой метод разметки и защитил его перед командой.»",
            ),
            onAuthorClick = {},
        )
    }
}

@Composable
private fun ChatsComponents() {
    Section(":feature:chats:ui") {
        ChatListItem(
            ChatListItemUiModel("Проф. Елена Васкес", "Готовы созвониться завтра в 15:00?", "10:22", Elena, unreadCount = 2),
            onClick = {},
        )
        ChatBubble(ChatBubbleUiModel("Текст сообщения в диалоге", "10:22", isOutgoing = false))
        ChatBubble(ChatBubbleUiModel("Текст сообщения в диалоге", "10:22", isOutgoing = true))
        var composer by remember { mutableStateOf(ChatComposerUiModel()) }
        ChatComposer(composer, { composer = composer.copy(message = it) }, onSendClick = {}, onAttachClick = {})
        // Without the attach icon: sending files is not available yet.
        ChatComposer(composer, { composer = composer.copy(message = it) }, onSendClick = {})
    }
}

@Composable
private fun ResponsesComponents() {
    Section(":feature:responses:ui") {
        ApplicationCard(
            ApplicationCardUiModel(
                title = "AI-навигация по кампусу",
                subtitle = "Роль: ML Engineer · отклик 2 дня назад",
                actionText = "Отозвать отклик",
                showChatButton = true,
            ),
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
        content()
    }
}
