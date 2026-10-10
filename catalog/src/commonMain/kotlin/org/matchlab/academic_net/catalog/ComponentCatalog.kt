package org.matchlab.academic_net.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.matchlab.academic_net.components.ActionListItem
import org.matchlab.academic_net.components.AppAvatars
import org.matchlab.academic_net.components.AppBadgeCount
import org.matchlab.academic_net.components.AppBadgeDot
import org.matchlab.academic_net.components.AppButton
import org.matchlab.academic_net.components.AppButtonVariant
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
import org.matchlab.academic_net.components.InfoBanner
import org.matchlab.academic_net.components.InfoCard
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
import org.matchlab.academic_net.components.SectionHeader
import org.matchlab.academic_net.components.SettingListItem
import org.matchlab.academic_net.components.SheetHeader
import org.matchlab.academic_net.components.TagsBlock
import org.matchlab.academic_net.components.TextBlock
import org.matchlab.academic_net.components.ToggleListItem
import org.matchlab.academic_net.components.UploadCard
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
import org.matchlab.academic_net.feature.profile.ui.VerificationStatus
import org.matchlab.academic_net.feature.profile.ui.WorkCard
import org.matchlab.academic_net.feature.profile.ui.WorkCardUiModel
import org.matchlab.academic_net.feature.profile.ui.WorkType
import org.matchlab.academic_net.feature.projects.ui.CandidateCard
import org.matchlab.academic_net.feature.projects.ui.CandidateCardUiModel
import org.matchlab.academic_net.feature.projects.ui.ParticipantManageCard
import org.matchlab.academic_net.feature.projects.ui.RoleApplyCard
import org.matchlab.academic_net.feature.projects.ui.RoleApplyCardUiModel
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
            item { SharedFeatureComponents() }
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
        InfoCard("Окт 2026 — Март 2027", AppIcons.Calendar)
        UploadCard("Загрузить подтверждение", "PDF или скриншот с указанием авторства", {})
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
        NotificationListItem(NotificationListItemUiModel("Новый отклик на роль ML Engineer", "2 часа назад"))
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
        RoleApplyCard(RoleApplyCardUiModel("Mobile Developer", listOf("Flutter", "Dart")), onApplyClick = {})
        ParticipantManageCard(Initiator, onKickClick = {})
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard(StatCardUiModel("7", "новых откликов"), Modifier.weight(1f))
            StatCard(StatCardUiModel("3", "участника"), Modifier.weight(1f))
            StatCard(StatCardUiModel("2", "роли"), Modifier.weight(1f))
        }
    }
}

@Composable
private fun ProfileComponents() {
    Section(":feature:profile:ui") {
        ProfileHeaderCard(
            ProfileHeaderCardUiModel(
                name = "Арджун Мехта",
                subtitle = "Stanford University · 3 курс · Computer Science",
                avatar = Elena,
                isVerified = true,
                statuses = listOf(ProfileStatusUiModel("На проверке", AppStatusChipTone.Pending)),
                actionText = "Пригласить",
                showChatButton = true,
            ),
        )
        EducationCard(EducationCardUiModel("B.S. Computer Science", "Stanford University · выпуск 2027", VerificationStatus.Verified))
        WorkCard(WorkCardUiModel("Localized CV Pipelines on Edge Devices", "Описание работы от пользователя одной строкой", status = VerificationStatus.Verified))
        WorkCard(WorkCardUiModel("Прототип AR-навигации", "Прототип маршрутов в AR по коридорам корпуса", WorkType.Project, VerificationStatus.Rejected))
        ReviewCard(
            ReviewCardUiModel(
                authorName = "Проф. Елена Васкес",
                authorRole = "Инициатор · AI-навигация",
                authorAvatar = Elena,
                title = "Довёл модели до демо",
                rating = 5,
                text = "«Короткий текст отзыва о совместной работе.»",
            ),
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
