package org.matchlab.academic_net.catalog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import org.matchlab.academic_net.R
import org.matchlab.academic_net.components.ActionListItem
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
import org.matchlab.academic_net.components.AppStatusChip
import org.matchlab.academic_net.components.AppStatusChipTone
import org.matchlab.academic_net.components.AppSwitch
import org.matchlab.academic_net.components.AppTabs
import org.matchlab.academic_net.components.AppTagChip
import org.matchlab.academic_net.components.AppTagsField
import org.matchlab.academic_net.components.AppTextField
import org.matchlab.academic_net.components.AppTopBar
import org.matchlab.academic_net.components.ApplicationCard
import org.matchlab.academic_net.components.Avatar
import org.matchlab.academic_net.components.CandidateCard
import org.matchlab.academic_net.components.ChatBubble
import org.matchlab.academic_net.components.ChatComposer
import org.matchlab.academic_net.components.ChatListItem
import org.matchlab.academic_net.components.EducationCard
import org.matchlab.academic_net.components.InfoBanner
import org.matchlab.academic_net.components.InfoCard
import org.matchlab.academic_net.components.InitiatorCard
import org.matchlab.academic_net.components.NotificationListItem
import org.matchlab.academic_net.components.ParticipantManageCard
import org.matchlab.academic_net.components.PersonCard
import org.matchlab.academic_net.components.PersonListItem
import org.matchlab.academic_net.components.ProfileHeaderCard
import org.matchlab.academic_net.components.ProjectCard
import org.matchlab.academic_net.components.RadioListItem
import org.matchlab.academic_net.components.ReviewCard
import org.matchlab.academic_net.components.RoleApplyCard
import org.matchlab.academic_net.components.RoleCard
import org.matchlab.academic_net.components.SectionHeader
import org.matchlab.academic_net.components.SettingListItem
import org.matchlab.academic_net.components.SheetHeader
import org.matchlab.academic_net.components.StatCard
import org.matchlab.academic_net.components.TagsBlock
import org.matchlab.academic_net.components.TextBlock
import org.matchlab.academic_net.components.ToggleListItem
import org.matchlab.academic_net.components.UploadCard
import org.matchlab.academic_net.components.VacantRoleCard
import org.matchlab.academic_net.components.VerificationStatus
import org.matchlab.academic_net.components.WorkCard
import org.matchlab.academic_net.theme.AcademicNetTheme

/** Debug-only screen with every design system component, filled with the Figma sample data. */
class CatalogActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            AcademicNetTheme {
                ComponentCatalog()
            }
        }
    }
}

private val NoInsets = WindowInsets(0)

@Composable
private fun ComponentCatalog() {
    val elena = painterResource(R.drawable.sample_avatar_elena)
    val arjun = painterResource(R.drawable.sample_avatar_arjun)
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item {
            Section("Действия") {
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
                }
            }
        }
        item {
            Section("Поля и выбор") {
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
            }
        }
        item {
            Section("Навигация и каркас") {
                var tab by remember { mutableStateOf(4) }
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
                var tabs by remember { mutableStateOf(0) }
                AppTabs(listOf("Обзор", "Участники", "Отклики"), tabs, { tabs = it })
                AppTopBar("Заголовок экрана", onNavigateBack = {}, windowInsets = NoInsets, actions = {
                    AppIconButton(AppIcons.MoreVertical, null, {})
                })
                AppTopBar("Заголовок экрана", subtitle = "Подзаголовок", onNavigateBack = {}, windowInsets = NoInsets, actions = {
                    AppIconButton(AppIcons.MoreVertical, null, {})
                })
            }
        }
        item {
            Section("Блоки, статусы и аватары") {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(elena, null, size = 64.dp)
                    Avatar(arjun, null, size = 48.dp)
                    Avatar(elena, null, size = 32.dp)
                    Avatar(arjun, null, size = 24.dp)
                    AppBadgeCount(2)
                    AppBadgeDot()
                }
                InfoBanner("Пояснение к разделу или правило сервиса")
                TagsBlock("Теги", listOf("Machine Learning", "Machine Learning", "Machine Learning"))
                TextBlock("Заголовок раздела", "Текст раздела на несколько строк — описание проблемы, цели или любое другое описание.")
            }
        }
        item {
            Section("Строки списков") {
                ActionListItem("Действие", onClick = {}, subtitle = "Короткое пояснение, что произойдёт")
                ChatListItem("Проф. Елена Васкес", "Готовы созвониться завтра в 15:00?", "10:22", elena, onClick = {}, unreadCount = 2)
                NotificationListItem("Новый отклик на роль ML Engineer", "2 часа назад")
                PersonListItem("Арджун Мехта", "Stanford · 3 курс · Computer Science", arjun, verified = true, onClick = {})
                var option by remember { mutableStateOf(false) }
                RadioListItem("Вариант ответа", option, { option = !option })
                SettingListItem("Название настройки", AppIcons.Lock, onClick = {}, subtitle = "Пояснение к настройке")
                var visible by remember { mutableStateOf(true) }
                ToggleListItem("Показывать меня в поиске", visible, { visible = it }, subtitle = "Пояснение к переключателю")
            }
        }
        item {
            Section("Чат") {
                ChatBubble("Текст сообщения в диалоге", "10:22", outgoing = false)
                ChatBubble("Текст сообщения в диалоге", "10:22", outgoing = true)
                var message by remember { mutableStateOf("") }
                ChatComposer(message, { message = it }, onSend = {}, onAttach = {}, placeholder = "Сообщение…")
            }
        }
        item {
            Section("Карточки") {
                ApplicationCard("AI-навигация по кампусу", "Роль: ML Engineer · отклик 2 дня назад", "Отозвать отклик", {}, onMessage = {})
                CandidateCard(
                    name = "Арджун Мехта",
                    subtitle = "Stanford · 3 курс бакалавриата · CS",
                    avatar = elena,
                    tags = listOf("Machine Learning", "Machine Learning"),
                    note = "Совпадение 92% · отклик 2 дня назад",
                    verified = true,
                    onAccept = {},
                    onDecline = {},
                    onMessage = {},
                )
                InfoCard("Окт 2026 — Март 2027", AppIcons.Calendar)
                ProjectCard(
                    title = "AI-навигация по кампусу",
                    author = "Проф. Елена Васкес · Stanford",
                    authorAvatar = arjun,
                    type = "Научный",
                    tags = listOf("Поиск соавторов", "Computer Science"),
                    footer = "4 места в команде · окт 2026 — мар 2027",
                    authorVerified = true,
                    onAccept = {},
                    onDecline = {},
                    onMessage = {},
                )
                ReviewCard("Проф. Елена Васкес", "Инициатор · AI-навигация", elena, "Довёл модели до демо", 5, "«Короткий текст отзыва о совместной работе.»")
                UploadCard("Загрузить подтверждение", "PDF или скриншот с указанием авторства", {})
                WorkCard("Localized CV Pipelines on Edge Devices", "Описание работы от пользователя одной строкой", status = VerificationStatus.Verified)
                RoleCard("ML Engineer", "Арджун Мехта", elena, kickText = "Выгнать", onKick = {}, onMessage = {})
                VacantRoleCard("ML Engineer", listOf("Machine Learning", "Machine Learning"), onDelete = {}, onEdit = {})
                ProfileHeaderCard(
                    name = "Арджун Мехта",
                    subtitle = "Stanford University · 3 курс · Computer Science",
                    avatar = elena,
                    verified = true,
                    statuses = { AppStatusChip("На проверке", AppStatusChipTone.Pending) },
                    actionText = "Пригласить",
                    onAction = {},
                    onMessage = {},
                )
                InitiatorCard("Проф. Елена Васкес", "Инициатор · Stanford University", "★ 4.9 · 12 завершённых проектов", elena, verified = true, onMessage = {})
                PersonCard("Даниил Крюков", "ИТМО", elena)
                EducationCard("B.S. Computer Science", "Stanford University · выпуск 2027", status = VerificationStatus.Verified)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatCard("7", "новых откликов", Modifier.weight(1f))
                    StatCard("3", "участника", Modifier.weight(1f))
                    StatCard("2", "роли", Modifier.weight(1f))
                }
                ParticipantManageCard(
                    name = "Проф. Елена Васкес",
                    subtitle = "Инициатор · Stanford University",
                    stats = "★ 4.9 · 12 завершённых проектов",
                    avatar = elena,
                    kickText = "Выгнать",
                    onKick = {},
                    verified = true,
                    onMessage = {},
                )
                RoleApplyCard("Mobile Developer", listOf("Flutter", "Dart"), "Откликнуться", {})
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
        content()
    }
}
