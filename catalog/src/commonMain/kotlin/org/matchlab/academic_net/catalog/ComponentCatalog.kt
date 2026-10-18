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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.YearMonth
import org.matchlab.academic_net.common.model.Collaborator
import org.matchlab.academic_net.common.model.ProjectPeriod
import org.matchlab.academic_net.common.model.ProjectStatus
import org.matchlab.academic_net.common.model.ProjectType
import org.matchlab.academic_net.common.model.Sphere
import org.matchlab.academic_net.common.model.Study
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.model.UserSummary
import org.matchlab.academic_net.common.result.RemoteFailure
import org.matchlab.academic_net.components.ActionListItem
import org.matchlab.academic_net.components.AppAvatars
import org.matchlab.academic_net.components.AppBadgeCount
import org.matchlab.academic_net.components.AppBadgeDot
import org.matchlab.academic_net.components.AppButton
import org.matchlab.academic_net.components.AppButtonVariant
import org.matchlab.academic_net.components.AppCard
import org.matchlab.academic_net.components.AppConfirmDialog
import org.matchlab.academic_net.components.AppFab
import org.matchlab.academic_net.components.AppFilterChip
import org.matchlab.academic_net.components.AppIconButton
import org.matchlab.academic_net.components.AppIconButtonVariant
import org.matchlab.academic_net.components.AppIcons
import org.matchlab.academic_net.components.AppInputChip
import org.matchlab.academic_net.components.AppNavigationBar
import org.matchlab.academic_net.components.AppNavigationBarItem
import org.matchlab.academic_net.components.AppPasswordField
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
import org.matchlab.academic_net.components.InfoBannerTone
import org.matchlab.academic_net.components.InfoCard
import org.matchlab.academic_net.components.LoadingState
import org.matchlab.academic_net.components.MessageState
import org.matchlab.academic_net.components.NotificationListItem
import org.matchlab.academic_net.components.NotificationListItemUiModel
import org.matchlab.academic_net.components.PersonCard
import org.matchlab.academic_net.components.PersonCardUiModel
import org.matchlab.academic_net.components.PersonListItem
import org.matchlab.academic_net.components.PersonListItemUiModel
import org.matchlab.academic_net.components.PersonName
import org.matchlab.academic_net.components.ProjectCard
import org.matchlab.academic_net.components.ProjectCardUiModel
import org.matchlab.academic_net.components.RadioListItem
import org.matchlab.academic_net.components.RatingStars
import org.matchlab.academic_net.components.RatingStarsSize
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
import org.matchlab.academic_net.components.StatusBanner
import org.matchlab.academic_net.components.StatusBannerStatus
import org.matchlab.academic_net.components.TagsBlock
import org.matchlab.academic_net.components.TextBlock
import org.matchlab.academic_net.components.ToggleListItem
import org.matchlab.academic_net.components.UploadCard
import org.matchlab.academic_net.components.format.failureText
import org.matchlab.academic_net.components.paging.LoadMoreEffect
import org.matchlab.academic_net.components.paging.LoadMoreFooter
import org.matchlab.academic_net.components.paging.LoadMoreState
import org.matchlab.academic_net.components.person.personStatsText
import org.matchlab.academic_net.components.person.ratingText
import org.matchlab.academic_net.components.person.subtitleText
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
            item { AuthComponents() }
            item { RepeatedTapComponents() }
            item { SharedFeatureComponents() }
            item { ReviewComponents() }
            item { PagingComponents() }
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
        // The segmented control: the pill slides to the tapped segment, the labels change color with it.
        var scope by remember { mutableIntStateOf(0) }
        AppSegmentedControl(listOf("Проекты", "Люди"), scope, { scope = it })
        var scope3 by remember { mutableIntStateOf(1) }
        AppSegmentedControl(listOf("Все", "Научные", "Практические"), scope3, { scope3 = it })
        // Right to left: the first option is on the right and the pill slides the other way.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            var scopeRtl by remember { mutableIntStateOf(0) }
            AppSegmentedControl(listOf("Проекты", "Люди", "Чаты"), scopeRtl, { scopeRtl = it })
        }
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
 * The pieces of the sign-in screens (19-22 of the design): the password field in its states, the button that
 * is busy, the text button that waits, the banner of a session that has ended and the words for a failed call.
 */
@Composable
private fun AuthComponents() {
    Section(":components — вход и регистрация") {
        // "Input/Password": empty with the hint; one with text (dots, the eye shows it); one with an error,
        // which takes the place of the hint. Tap the eye of the second one to see the "visible" state.
        var password by remember { mutableStateOf("") }
        AppPasswordField(password, { password = it }, label = "Пароль", hint = "От 8 символов")
        var filled by remember { mutableStateOf("Student#2026") }
        AppPasswordField(filled, { filled = it }, label = "Пароль", hint = "От 8 символов")
        var repeated by remember { mutableStateOf("Student#2025") }
        AppPasswordField(repeated, { repeated = it }, label = "Повторите пароль", errorText = "Пароли не совпадают")
        // The error line of a text field with a way out on it (registration, 409): the text wraps in the width that
        // is left, the text button stands at the end. The sign-in screens lay their buttons out 40dp tall instead of
        // the 48dp of Material 3 (the numbers of the design), so that the line is as tall as in the design; so does
        // this sample.
        AppTextField(
            value = "student@hse.ru",
            onValueChange = {},
            label = "Почта вуза",
            errorText = "Аккаунт с этой почтой уже есть",
            errorAction = {
                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                    AppButton("Войти", {}, variant = AppButtonVariant.Text)
                }
            },
        )
        // The busy button: the arc takes the place of the label, the size and the colors stay, the taps are
        // ignored. The first one is busy for two seconds after a tap (the count of taps must not grow while it
        // is busy, however often it is tapped); the rest are busy all the time.
        var signingIn by remember { mutableStateOf(false) }
        var signInTaps by remember { mutableIntStateOf(0) }
        LaunchedEffect(signingIn) {
            if (signingIn) {
                delay(2000)
                signingIn = false
            }
        }
        AppButton(
            text = "Войти (нажатий: $signInTaps)",
            onClick = {
                signInTaps++
                signingIn = true
            },
            modifier = Modifier.fillMaxWidth(),
            loading = signingIn,
        )
        AppButton("Войти", {}, Modifier.fillMaxWidth(), loading = true)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AppButton("Подтвердить", {}, variant = AppButtonVariant.Outlined, loading = true)
            AppButton("Пригласить", {}, variant = AppButtonVariant.Tonal, loading = true)
            AppButton("Выгнать", {}, variant = AppButtonVariant.OutlinedError, loading = true)
            AppButton("Отмена", {}, variant = AppButtonVariant.Text, loading = true)
        }
        // The same buttons switched off, for comparison: these are dimmed, the busy ones above are not.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AppButton("Войти", {}, enabled = false)
            AppButton("Подтвердить", {}, variant = AppButtonVariant.Outlined, enabled = false)
            AppButton("Пригласить", {}, variant = AppButtonVariant.Tonal, enabled = false)
        }
        // The text button of the countdown ("Отправить через m:ss") is switched off, the one next to it is not.
        AppButton("Отправить через 0:45", {}, Modifier.fillMaxWidth(), variant = AppButtonVariant.Text, enabled = false)
        AppButton("Изменить почту", {}, Modifier.fillMaxWidth(), variant = AppButtonVariant.Text)
        InfoBanner("Сеанс завершён. Войдите снова", icon = AppIcons.Clock)
        SecurityNote("Данные входа хранятся на устройстве в зашифрованном виде")
        // A long line wraps; the lock stays at its first line.
        SecurityNote(
            "Данные входа хранятся на устройстве в зашифрованном виде, а пароль никогда не записывается на диск " +
                "и не передаётся никому, кроме сервера",
        )
        // failureText: what the user reads for each kind of failed call.
        listOf(
            "NoConnection" to RemoteFailure.NoConnection(),
            "Timeout" to RemoteFailure.Timeout(),
            "Http 503" to RemoteFailure.Http(503, "database is down"),
            "Http 404" to RemoteFailure.Http(404, "not found"),
            "null" to null,
        ).forEach { (kind, cause) ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = kind,
                    modifier = Modifier.weight(0.3f),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = failureText(cause),
                    modifier = Modifier.weight(0.7f),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
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
        // With the chat button and the rating line; without them; a long name and a long subtitle.
        PersonCard(Initiator)
        PersonCard(PersonCardUiModel(name = "Даниил Крюков", subtitle = "ИТМО", avatar = Elena))
        PersonCard(
            PersonCardUiModel(
                name = "Александра Константинопольская-Вишневецкая",
                subtitle = "Московский государственный университет имени М. В. Ломоносова · аспирантка · биоинформатика",
                avatar = Arjun,
                isVerified = true,
                stats = "★ 4.9 · 12 завершённых проектов",
                showChatButton = true,
            ),
        )
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

/**
 * The pieces of the reviews and of the editing of a work or a document (frames 09, 09b, 11b-11g): the stars, the
 * banner of the verification status, the banner of a conflict, the buttons and the dialog that delete.
 */
@Composable
private fun ReviewComponents() {
    Section(":components — отзывы и проверка") {
        // "Rating" sm: five stars of 18dp, from none filled to all; the number is drawn next to them by a screen.
        (0..5).forEach { value ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                RatingStars(value)
                Text("$value", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
            }
        }
        // "Rating" md: 24dp. A number outside 0..5 is limited (7 draws five, -2 draws none).
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            RatingStars(4, size = RatingStarsSize.Medium)
            Text("4.8", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
            RatingStars(7, size = RatingStarsSize.Medium)
            RatingStars(-2)
        }
        // "Banner/Status", the four statuses; the rejected one with the comment of the moderator.
        StatusBanner(StatusBannerStatus.Unverified, "Не подтверждено")
        StatusBanner(StatusBannerStatus.Pending, "На проверке")
        StatusBanner(StatusBannerStatus.Verified, "Подтверждено")
        StatusBanner(StatusBannerStatus.Rejected, "Отклонено", text = "Комментарий модератора: Нужны доказательства")
        // "Banner/Info (ошибка)": the conflict of 11g; the default icon is the crossed circle.
        InfoBanner("Запись изменилась. Обновите экран и повторите", tone = InfoBannerTone.Error)
        // The buttons in the error color: "Удалить работу" under a form, "Выйти из аккаунта" (outlined).
        AppButton("Удалить работу", {}, Modifier.fillMaxWidth(), variant = AppButtonVariant.TextError)
        AppButton("Выйти из аккаунта", {}, Modifier.fillMaxWidth(), variant = AppButtonVariant.OutlinedError)
        AppButton("Удалить документ", {}, variant = AppButtonVariant.TextError, enabled = false)
        // The dialog that confirms a deletion has the confirming button in the error color; the plain one does not.
        var destructiveDialog by remember { mutableStateOf(false) }
        var plainDialog by remember { mutableStateOf(false) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AppButton("Диалог удаления", { destructiveDialog = true }, variant = AppButtonVariant.Outlined)
            AppButton("Обычный диалог", { plainDialog = true }, variant = AppButtonVariant.Outlined)
        }
        if (destructiveDialog) {
            AppConfirmDialog(
                title = "Удалить работу?",
                text = "Работа будет удалена из портфолио. Запись о проверке сохранится в истории.",
                confirmText = "Удалить",
                dismissText = "Отмена",
                onConfirm = { destructiveDialog = false },
                onDismiss = { destructiveDialog = false },
                destructive = true,
            )
        }
        if (plainDialog) {
            AppConfirmDialog(
                title = "Отозвать отклик?",
                text = "Инициатор больше не увидит ваш отклик на эту роль.",
                confirmText = "Отозвать",
                dismissText = "Отмена",
                onConfirm = { plainDialog = false },
                onDismiss = { plainDialog = false },
            )
        }
        // A person whose name is empty gets "Без имени" instead of an empty line, on every card.
        PersonName("", verified = false, style = MaterialTheme.typography.titleMedium)
        PersonName("   ", verified = true, style = MaterialTheme.typography.titleMedium)
        PersonCard(PersonCardUiModel(name = "", subtitle = "Stanford University", avatar = Arjun, showChatButton = true))
    }
}

/**
 * The end of a list that loads more as it is scrolled: the four states of the footer and a list that works
 * (40 items in pages of 10; the first page is short, so the second is asked for at once; the third fails once, so
 * "Повторить" can be tried).
 */
@Composable
private fun PagingComponents() {
    Section(":components — списки с догрузкой") {
        Text("Loading", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LoadMoreFooter(LoadMoreState.Loading, onRetry = {})
        Text("Failed", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LoadMoreFooter(LoadMoreState.Failed, onRetry = {})
        Text(
            text = "Idle и End ничего не рисуют: ниже пусто",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LoadMoreFooter(LoadMoreState.Idle, onRetry = {})
        LoadMoreFooter(LoadMoreState.End, onRetry = {})
        var run by remember { mutableIntStateOf(0) }
        AppButton("Начать заново", { run++ }, variant = AppButtonVariant.Outlined)
        key(run) { LoadMoreDemo() }
    }
}

/**
 * A list of 40 items that loads 10 at a time, driven by [LoadMoreEffect]. The first page has 3 items and does not
 * fill the list, which makes the effect ask for the next page at once; the page after that fails once.
 */
@Composable
private fun LoadMoreDemo() {
    val total = 40
    val pageSize = 10
    var loaded by remember { mutableIntStateOf(3) }
    var state by remember { mutableStateOf(LoadMoreState.Idle) }
    var failNext by remember { mutableStateOf(true) }
    var asked by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    // The guard that the screen must have: nothing is asked for while a page is on its way.
    val loadMore = {
        if (state != LoadMoreState.Loading && state != LoadMoreState.End) {
            asked++
            state = LoadMoreState.Loading
            scope.launch {
                delay(1000)
                if (failNext && loaded > 12) {
                    failNext = false
                    state = LoadMoreState.Failed
                } else {
                    loaded = minOf(total, loaded + pageSize)
                    state = if (loaded >= total) LoadMoreState.End else LoadMoreState.Idle
                }
            }
        }
    }
    LoadMoreEffect(listState = listState, state = state, onLoadMore = loadMore)
    Text(
        text = "Загружено $loaded из $total, запросов страницы: $asked, состояние: $state",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    LazyColumn(Modifier.fillMaxWidth().height(260.dp), state = listState) {
        items(loaded) { index ->
            Text(
                text = "Элемент ${index + 1}",
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        item { LoadMoreFooter(state, onRetry = loadMore) }
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
        // Still the layout from before the redesign of "Card/Person" (48dp avatar, bottom-aligned).
        ParticipantManageCard(Initiator, onKickClick = {})
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard(StatCardUiModel("7", "новых откликов"), Modifier.weight(1f))
            StatCard(StatCardUiModel("3", "участника"), Modifier.weight(1f))
            StatCard(StatCardUiModel("2", "роли"), Modifier.weight(1f))
        }
    }
}

/**
 * The cards of "09 Профиль коллаборатора", "10 Мой профиль" and the lists of works and reviews, in the states the
 * screens use: the verification marks (and none, as the Gateway gives them), the two kinds of work, the chevron of the
 * cards that open for editing, the review cards of a list (with the month) and of a profile (without it), with and
 * without the chat icon, and the line under a name that is made of the study (`subtitleText`).
 */
@Composable
private fun ProfileComponents() {
    Section(":feature:profile:ui") {
        // The line under the name is made of the university, the study (a free text level and the year) and the field.
        val student = Collaborator(
            user = UserSummary(UserId("user-arjun"), "Арджун Мехта", isVerified = true),
            university = "Stanford University",
            study = Study(level = "бакалавриат", year = 3),
            field = "Computer Science",
        )
        // Someone else's profile: the verification chip and the chat icon at the end of the first row.
        ProfileHeaderCard(
            ProfileHeaderCardUiModel(
                name = student.user.name,
                subtitle = student.subtitleText(),
                avatar = Elena,
                isVerified = true,
                statuses = listOf(ProfileStatusUiModel("Профиль подтверждён", AppStatusChipTone.Success)),
                showChatButton = true,
            ),
        )
        // Own profile: no chip, no chat icon.
        ProfileHeaderCard(
            ProfileHeaderCardUiModel(
                name = "Алексей Морозов",
                subtitle = student.subtitleText(),
                avatar = Arjun,
                isVerified = true,
            ),
        )
        // The text column is narrow next to the icon: a long name takes two lines, the subtitle wraps.
        ProfileHeaderCard(
            ProfileHeaderCardUiModel(
                name = "Александра Константинопольская-Вишневецкая",
                subtitle = Collaborator(
                    user = UserSummary(UserId("user-alexandra"), "Александра"),
                    university = "Московский государственный университет имени М. В. Ломоносова",
                    study = Study(level = "Аспирантура"),
                    field = "биоинформатика",
                ).subtitleText(),
                avatar = Elena,
                isVerified = true,
                statuses = listOf(ProfileStatusUiModel("Профиль не подтверждён", AppStatusChipTone.Off)),
                showChatButton = true,
            ),
        )
        // A new account: no name, no study: the header says so and the line under the name is not drawn.
        ProfileHeaderCard(ProfileHeaderCardUiModel(name = "Имя не указано", subtitle = "", avatar = Arjun))
        // The three marks: confirmed, being checked (only the owner sees it) and not confirmed.
        EducationCard(EducationCardUiModel("B.S. Computer Science", "Stanford University · выпуск 2027", VerificationMark.Verified))
        EducationCard(EducationCardUiModel("Сертификат Deep Learning", "Coursera · 2025", VerificationMark.Pending))
        // The own profile: the card opens for editing and has a chevron; without a mark, as the Gateway gives them.
        EducationCard(
            EducationCardUiModel("Сертификат Deep Learning", "Coursera · 2025", VerificationMark.Verified),
            onClick = {},
            showChevron = true,
        )
        EducationCard(EducationCardUiModel("Диплом бакалавра", "Stanford University · выпуск 2027"), onClick = {}, showChevron = true)
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
        // The own profile: the chevron is centered on the whole row, a long title takes two lines.
        WorkCard(
            WorkCardUiModel(
                title = "Localized CV Pipelines on Edge Devices",
                subtitle = "Научная работа · DOI 10.1234/cv",
                mark = VerificationMark.Verified,
            ),
            onClick = {},
            showChevron = true,
        )
        WorkCard(
            WorkCardUiModel(title = "Сегментация коридоров на Raspberry Pi", subtitle = "Проектная работа · GitHub", type = ProjectType.Practical),
            onClick = {},
            showChevron = true,
        )
        // A review in a list: with the month, the chat icon, a long text that is cut and "Читать полностью".
        ReviewCard(
            ReviewCardUiModel(
                authorName = "Проф. Елена Васкес",
                authorLine = "Инициатор · AI-навигация по кампусу",
                authorAvatar = Elena,
                title = "Довёл модели до демо",
                rating = 5,
                date = "мар 2026",
                text = "«Взял на себя обучение моделей и довёл их до рабочего демо. Сроки соблюдал, писал понятные отчёты, " +
                    "на встречах приходил с готовыми вариантами решений. Рекомендую в проекты, где нужен самостоятельный инженер.»",
                showChat = true,
            ),
            onClick = {},
            onAuthorClick = {},
            onChatClick = {},
        )
        // A review on a profile: no month, a short text (no "Читать полностью"), the role of the author is not known
        // (the Gateway does not tell it), a review the viewer wrote has no chat icon.
        ReviewCard(
            ReviewCardUiModel(
                authorName = "Лина Ковач",
                authorLine = "AI-навигация по кампусу",
                authorAvatar = Arjun,
                title = "Помогал команде с данными",
                rating = 4,
                date = null,
                text = "«Сильный инженер, помогал команде с пайплайном данных.»",
                showChat = false,
            ),
            onClick = {},
            onAuthorClick = {},
            onChatClick = {},
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
