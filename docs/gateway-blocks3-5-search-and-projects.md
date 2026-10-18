# Блоки 3–5 на MatchLab Gateway: архитектура

> **Исторический проект.** Реализация следует `docs/api-gaps.md` (правило «без догадок»: словари, синонимы значений, `GatewayVocabulary`, запись
> неподтверждённых значений отменены) и контрактам `docs/contract-b3.md`, `docs/contract-p45.md`; расхождения решает код и реестр пробелов.
> Здесь это в первую очередь раздел 3.2 и всё, что пишет или читает `type`, `status`, `action`, `mine`, `duration`, `planned_*`; повтор при 409 не делается (G48).

Статус: проект `architect` на 2026-10-18; блоки 3–5 затем реализованы в коде с отступлениями (см. заметку выше). Исходное состояние: шаг 1 (`:gateway`, `AccountApi`, `SessionComponent`, `Backend`, `RemoteFailure`, `failureText`) и блок 2 (`ProfilesApi`, `UserProfileDto`, `UserDirectory`, маппинг профиля) уже в коде. Новых библиотек и модулей нет.

## 0. Что нового по сравнению с `docs/gateway-integration.md`

| # | Было | Стало | Почему |
|---|---|---|---|
| 1 | Неизвестные значения на сервер не пишем, такие действия ждут бэкенда | Пишем одно лучшее предположение из `GatewayVocabulary` (`@UnverifiedContract`, один файл). Та же таблица — в `sprint-1.md` | Пользователь решил «реализуй то, что есть». Если догадка неверна, правится одна строка |
| 2 | Значений нет | `type`: `scientific`/`project`; «Снять с публикации» = `action: "pause"`; `mine=owner`; `planned_*`: ISO-дата (первый день первого месяца, последний день последнего); статусы читаются с синонимами (см. 3.2) | Аналогия с `kind`, глаголы из `summary` переходов, словарь сервера (`owner_user_id`, `members`) |
| 3 | Блок 5 шлёт `status` («только набор») и `mine` («без своих»), свои проекты исключаются на клиенте | В поиск проектов **не шлём** ни `status`, ни `mine`. Свои проекты и проекты в других статусах показываем: на карточке чип статуса, у своего проекта подпись «Вы инициатор» | Неверная строка в постоянном фильтре молча обнуляет главный поиск, и пустой ответ не отличить от пустой базы. Исключение на клиенте ломает `total` и смещения. Макет 02 сам показывает карточку со статусом «Начат» |
| 4 | Срок в одном месте описан как «одиночный выбор, `duration`», в другом — «фильтр скрыт» | Группа «Сроки» убирается и из 03, и из домена (`ProjectDuration`, `ProjectFilters.durations`, правило мока) | Значения не угадать. Видимый фильтр без эффекта обманывает и в списке, и в «Найдено N». Альтернатива — оставить домен и скрыть только чипы: это мёртвый код |
| 5 | Шапка ленты: `GET /users/me/profile` | Без запроса: аватар — иллюстрация по `viewerId` из сессии | В шапке только аватар. Запрос ничего не добавляет, а без сети показал бы иллюстрацию по умолчанию. Запрос вернётся вместе с настоящими аватарами |
| 6 | Маппинг `Project` в `gateway.mapping` | Плюс интерфейс `ProjectAssembler` в `:gateway`: общий для projects, projectsearch и projectdetails, подтягивает профиль владельца, а участников — только в «Моих проектах» | DRY. Тесты data фич идут на фейке сборщика, без `UserDirectory` |
| 7 | `ProjectStatus` получает статусы сервера | Плюс `Project.type`, `status`, `period` становятся nullable | Терпимое чтение доходит до UI: неизвестное значение прячет чип или срок, а список «Моих проектов» не падает и не пустеет |
| 8 | `Role` получает `slots` и `members` | `slots`, `occupied` (счёт сервера) и `members`. `members` может быть пустым при `occupied > 0`. «N из M занято» считает места | В API `occupied` и `members` раздельны. Поиску и странице 04 участники не нужны, их загрузка — лишние N+1 |
| 9 | `Page`, `PageRequest` | Плюс `PagedList` (склейка страниц без повторов) и `Page.received` | Список на сервере сдвигается между страницами, приходит дубль, а два одинаковых ключа в `LazyColumn` роняют приложение |
| 10 | При 409 обновить и повторить один раз | Перед повтором проверить, не выполнено ли действие уже (роль появилась, статус уже снят) | Ответ потерялся, пользователь повторил, сервер вернул 409 — слепой повтор добавил бы роль дважды |
| 11 | — | `POST /projects` с исходом `Timeout` даёт отдельный текст «Проект мог опубликоваться…» и обновляет «Мои проекты» | Ключей идемпотентности нет, а таймаут — единственный неоднозначный исход |
| 12 | «Показать N» на 08b — длина ответа | `count(query)` = `GET /search/people?limit=1` → `total`, с дебаунсом 300 мс | Длина страницы не равна числу найденных |
| 13 | При чтении `Sphere(id = field, name = field)` | Каталог из 12 сфер в режиме Gateway тоже `id == name` | Сервер различает сферу по тексту. Иначе выбранная в пикере сфера не совпадёт с прочитанной |
| 14 | — | Полноэкранная ошибка: заголовок экрана + `failureText(cause)`. Ошибка блока или футера: короткий текст + «Повторить» | Одно правило для всех сетевых ошибок |

## 1. Что уже есть (переиспользуем)

- **Модель и результаты.** `Project`, `Role`, `RoleMember`, `ProjectPeriod`, `Sphere` лежат в `/Users/samvel/Desktop/Study/Methodology/AcademicNet/common/src/commonMain/kotlin/org/matchlab/academic_net/common/model/Project.kt`. `Collaborator` — в `/Users/samvel/Desktop/Study/Methodology/AcademicNet/common/src/commonMain/kotlin/org/matchlab/academic_net/common/model/Collaborator.kt`. `LoadResult` (нужен для `loadProject`) — в `/Users/samvel/Desktop/Study/Methodology/AcademicNet/common/src/commonMain/kotlin/org/matchlab/academic_net/common/result/LoadResult.kt`. `suspendRunCatching` — в `/Users/samvel/Desktop/Study/Methodology/AcademicNet/utils/src/commonMain/kotlin/org/matchlab/academic_net/utils/Results.kt`.
- **Компоненты.** `LoadingState` и `MessageState` (уже поглощают касания) — `/Users/samvel/Desktop/Study/Methodology/AcademicNet/components/src/commonMain/kotlin/org/matchlab/academic_net/components/States.kt`. `projectCardUiModel`, `label()`, `rolesCountText` — `/Users/samvel/Desktop/Study/Methodology/AcademicNet/components/src/commonMain/kotlin/org/matchlab/academic_net/components/project/ProjectLabels.kt`. `ProjectHeader`, `RolesSectionHeader`, `ProjectPeriodCard` — `/Users/samvel/Desktop/Study/Methodology/AcademicNet/components/src/commonMain/kotlin/org/matchlab/academic_net/components/project/ProjectSections.kt`. `RoleApplyCard` — `/Users/samvel/Desktop/Study/Methodology/AcademicNet/components/src/commonMain/kotlin/org/matchlab/academic_net/components/RoleCards.kt`. `PersonCard`, `PersonName`, `PersonListItem`, `SpherePickerSheet`, `FiltersSheet`, `personStatsText` — там же, в `components/`. Из шага 1 — `failureText(cause)`.
- **Фичи.** Все `Mock…DataSource` остаются: для `Backend.Mock`, для тестов ViewModel и как «сервер» мок-режима. Стражи повторного входа уже есть: `isPublishing` + `publishedProjectId` в `/Users/samvel/Desktop/Study/Methodology/AcademicNet/feature/projects/ui/src/commonMain/kotlin/org/matchlab/academic_net/feature/projects/ui/create/CreateProjectViewModel.kt`; `isUnpublishing`, `isSavingRole` в `/Users/samvel/Desktop/Study/Methodology/AcademicNet/feature/projects/ui/src/commonMain/kotlin/org/matchlab/academic_net/feature/projects/ui/management/ProjectManagementViewModel.kt`; `loadJob` и `peopleJob` в VM поиска и ленты.
- **`:shared`.** Адаптеры `/Users/samvel/Desktop/Study/Methodology/AcademicNet/shared/src/commonMain/kotlin/org/matchlab/academic_net/ProjectsRoleRequirementProvider.kt` и `/Users/samvel/Desktop/Study/Methodology/AcademicNet/shared/src/commonMain/kotlin/org/matchlab/academic_net/ProjectsSphereProvider.kt` не меняются. Маршруты и `navigateOnce`/`navigateSingleTop` в `/Users/samvel/Desktop/Study/Methodology/AcademicNet/shared/src/commonMain/kotlin/org/matchlab/academic_net/ui/navigation/AppNavHost.kt` не меняются.
- **Блок 2.** `UserDirectory.profiles(ids)` (кэш, не более 4 параллельно, 400/404 → `null`); `UserProfileDto` → `UserSummary`; помощник «уровень и курс» для `Collaborator`; `GatewayVocabulary`, если блок 2 его создал (разделы ниже дописываются в тот же объект). Если блок 2 сделал футер «Загрузка… / Повторить» для 09b или подпись для пустого имени — берём их.

## 2. Раскладка по модулям

```
:common      + paging/{Page.kt (Page, PageRequest), PagedList.kt}; Project/Role/ProjectStatus (block 4)
:gateway     + PeopleSearchApi, RecommendationsApi (3); ProjectsApi (4); ProjectCatalogApi (5)
             + dto/{PeopleDtos, ProjectDtos}; contract/GatewayVocabulary (project sections)
             + mapping/{PageMapping, PeopleMapping, ProjectMapping, ProjectAssembler}
:components  + paging/LoadMore.kt; project status labels, rolePlacesText, nameOrPlaceholder
feature:{collaborators,feed,projects,projectsearch,projectdetails}:data
             → implementation(project(":gateway")); Gateway…DataSource next to the mock
:shared      DataSourcesModule: `when (backend)` for 5 sources + ProjectAssembler; new use cases in modules
```

Фича по-прежнему не зависит от другой фичи. `UserDirectory` и `ProjectAssembler` — инфраструктура `:gateway`, поэтому data фич зовут их напрямую, адаптер в `:shared` не нужен.

### Пересмотр таблицы блоков (по фактическому коду)

| Фича | domain | data | ui | Нет в API / потери | N+1 |
|---|---|---|---|---|---|
| collaborators (08, 08b) | Удаляются `ScoredCollaborator` и `CollaboratorQuery.role`. `search(query, page): Page<Collaborator>`, `count(query)`, `CountCollaboratorsUseCase` | `GatewayCollaboratorsDataSource`. Мок: страницы, `total`, без роли, порядок «верифицированные, потом по имени» | Страницы, «Найдено N» из `total`, футер, «Показать N» через `count`, ошибка с причиной | Ни совпадения, ни порядка по роли. `interest`, `my_university`, `open_only` в UI нет. Рейтинг человека не показываем: без `review_count` ноль неотличим от «нет отзывов» | нет |
| feed (01, 01c) | без изменений | `GatewayFeedDataSource`: `limit=10`, без самого зрителя. `getViewer` без запроса | Люди грузятся при первом выборе «Люди», живут столько же, сколько VM. Пустое состояние с «Найти людей» | Подбора под роли нет: сервер отдаёт случайных людей, открытых к сотрудничеству | нет |
| projects (05, 06, 07, 07b, 07c) | `ProjectConflictException`, `refreshMyProjects`, `loadProject`, `UnpublishProjectResult.Conflict` | `GatewayProjectsDataSource`: кэш сессии + `revision` + `Mutex` + политика 409. `SphereCatalog` | 06: «Повторить». 07: загрузка и повтор, конфликт, баннеры новых статусов, места. 05: таймаут | Описания роли в форме нет. «Опубликовать снова», «Начать», «Завершить», «Отменить» не показываем. `moderation_hidden`, `pending_join_count` не показываем. Больше 100 проектов не грузим | Участники через `profiles(memberIds)` (в спринте 1 их нет). Инициатор — сам зритель, 1 запрос из кэша |
| projectsearch (02, 03) | Удаляются `ProjectDuration` и `durations`. `search(query, page): Page<FoundProject>` | `GatewayProjectSearchDataSource` + сборщик. Мок со страницами | Страницы, `total`, футер. «Сроки» убраны. Карточка своего проекта | Не шлём `duration`, `status`, `mine`. Ключевые слова уходят как теги | Владельцы страницы: 1 + K запросов (K ≤ 20, не более 4 параллельно, TTL 5 мин) |
| projectdetails (04) | без изменений | `GatewayProjectDetailsDataSource` | Места «N из M», заглушка для пустого имени | — | 1 + 1 (владелец) |

## 3. Сквозные контракты

### 3.1 `:common/paging` (владелец — этот проект)

```kotlin
package org.matchlab.academic_net.common.paging

/**
 * Which part of a long server list to ask for: [limit] records from [offset]. The bounds are the
 * Gateway's (`limit` 1..100, `offset` 0..10 000); a request outside them is a programming error.
 */
data class PageRequest(val offset: Int = 0, val limit: Int = DefaultLimit) {
    init {
        require(offset in 0..MaxOffset) { "offset $offset is out of 0..$MaxOffset" }
        require(limit in 1..MaxLimit) { "limit $limit is out of 1..$MaxLimit" }
    }

    companion object {
        const val DefaultLimit = 20
        const val MaxLimit = 100
        const val MaxOffset = 10_000
        val First = PageRequest()
    }
}

/**
 * One page of a list the server keeps.
 *
 * @property items the records the app could read, in the server's order
 * @property total the whole list's size as the server counted it when answering
 * @property request what was asked for
 * @property received how many records the server sent, readable or not: the next page starts after
 *  them, so a skipped record does not shift the offsets
 */
data class Page<out T>(
    val items: List<T>,
    val total: Int,
    val request: PageRequest,
    val received: Int = items.size,
) {
    init { require(received >= items.size && total >= 0) }

    /** Where the next page starts. */
    val nextOffset: Int get() = request.offset + received

    /**
     * Whether more may be asked for: the server sent something (an empty page ends the list even
     * when [total] says otherwise), [total] counts more than has been received so far, and the next
     * offset is one the server accepts.
     */
    val hasMore: Boolean get() = received > 0 && nextOffset < total && nextOffset <= PageRequest.MaxOffset

    /** The page after this one, of the same size; `null` when [hasMore] is `false`. */
    fun next(): PageRequest? = if (hasMore) PageRequest(nextOffset, request.limit) else null

    fun <R> map(transform: (T) -> R): Page<R> = Page(items.map(transform), total, request, received)
}

/**
 * The pages of one list received so far, joined: what a screen that loads more on scroll shows.
 * The server's list may shift between two requests, so a record can come twice; the repeat is
 * dropped by its key, and a lazy list never gets two items with one key.
 *
 * @property total never less than [items].size
 * @property next the request for the next page; `null` once the list is over
 */
data class PagedList<T>(
    val items: List<T> = emptyList(),
    val total: Int = 0,
    val next: PageRequest? = PageRequest.First,
) {
    val hasMore: Boolean get() = next != null

    /** This list followed by the records of [page] whose [key] it does not have yet. */
    fun append(page: Page<T>, key: (T) -> Any): PagedList<T>

    companion object {
        /** The list made of its first [page]; repeats inside the page are dropped too. */
        fun <T> of(page: Page<T>, key: (T) -> Any): PagedList<T>
    }
}
```

Правило `hasMore` закреплено в KDoc и в `PageTest`. Нормализацию ответа делает только `:gateway` в `pageOf(items, received, reportedTotal: Long, request)`: `total = max(reportedTotal, offset + received)`, обрезка до `Int.MAX_VALUE`. Моки строят `Page` с точным `total`.

### 3.2 `GatewayVocabulary`: разделы блоков 3–5

```kotlin
package org.matchlab.academic_net.gateway.contract

/**
 * Strings of the Gateway API that docs/openapi.yaml (v2.4.0) types as bare `string` without
 * listing the values. Each is our best guess, not checked against the live server, and lives only
 * here; the same table is in docs/sprint-1.md. Reading is tolerant: case, surrounding spaces, `-`
 * and ` ` (read as `_`) do not matter, the listed synonyms are accepted, anything else is `null`
 * (reported in debug builds). Writing sends exactly the value defined here.
 */
@UnverifiedContract
object GatewayVocabulary {
    /** `details.type`, `?type=`, `?project_type=`, `project_types`: by analogy with achievement `kind`. */
    object ProjectTypes {
        fun toWire(type: ProjectType): String = when (type) {
            ProjectType.Scientific -> "scientific"
            ProjectType.Practical -> "project"
        }
        fun fromWire(value: String): ProjectType? // scientific|science|research; project|practical|applied
    }

    /** `Project.status`; read only, `?status=` is not sent. */
    object ProjectStatuses {
        fun fromWire(value: String): ProjectStatus?
        // recruiting|published|open                                         -> Recruiting
        // paused|recruiting_paused|recruitment_paused|unpublished|hidden    -> Unpublished
        // in_progress|started|active                                        -> Started
        // completed|finished|done                                           -> Completed
        // cancelled|canceled                                                -> Cancelled
        // draft and anything else                                           -> null
    }

    /** `ProjectTransitionRequest.action`. */
    object ProjectActions {
        /** "Снять с публикации" (07c) = "приостановить набор" of the API; the only action of sprint 1. */
        const val Unpublish = "pause"
        // Not sent in sprint 1: "publish", "start", "complete", "cancel".
    }

    /** `?mine=` of GET /projects: a string, unlike the boolean filters of the people search. */
    object MyProjects {
        const val Owner = "owner" // "Я инициатор"; "member" for "Я участник" comes with sprint 2
    }

    /** `field`: the API identifies a field by its text, so the app sends the sphere's name. */
    object Fields {
        fun toWire(sphere: Sphere): String = sphere.name
        fun fromWire(value: String): Sphere = value.trim().let { Sphere(id = it, name = it) }
    }

    /** `planned_start` / `planned_end`: ISO dates, the first day of the first month and the last of the last. */
    object PlannedDates {
        fun start(month: YearMonth): String = month.firstDay.toString() // "2026-10-01"
        fun end(month: YearMonth): String = month.lastDay.toString()    // "2027-03-31"
        /** "2026-10", "2026-10-01" or an RFC 3339 time: the date as written, no time zone shift. */
        fun parse(value: String): YearMonth?
    }

    /** `ProjectMember.status`: a member is shown unless the status says they are gone. */
    object MemberStatuses {
        fun isGone(value: String): Boolean // left|removed|kicked|excluded|declined
    }
    // `duration`: not guessable; the filter is hidden and nothing is sent.
}
```

| Значение | Чтение | Запись |
|---|---|---|
| `type` | синонимы → enum, иначе `null`: чип типа скрыт | `scientific` / `project`. Это обязательное поле `POST /projects`; в фильтрах `?type=` и `?project_type=` уходит, только если выбран ровно один тип |
| `status` | синонимы → enum, иначе `null`: нет чипа, баннера 07c и действий | не отправляется |
| `action` | — | `pause` («Снять с публикации»); остальные действия не используются |
| `planned_*` | `YYYY-MM`, `YYYY-MM-DD` или RFC 3339 (берётся дата как написана); не прочиталось или конец раньше начала → `period = null`, срок скрыт | `YYYY-MM-01` и последний день месяца окончания |
| `mine` | — | `owner`; клиент дополнительно оставляет только `owner_user_id == viewerId` |
| `field` | `Sphere(field, field)` | `sphere.name` |
| `ProjectMember.status` | `left`, `removed`, `kicked`, `excluded`, `declined` не показываются; остальные, включая неизвестные, показываются | — |
| `duration` | — | не отправляется; фильтр убран |
| Отклики (спринт 2, в коде нет) | `type`: `application` / `invitation`; `status`: `pending` / `accepted` / `declined` / `withdrawn` | — |

Если блок 2 уже завёл `ProjectTypes` для `project_types`, значения должны совпасть (`scientific`/`project`). Расхождение решается до блока 4. В местах использования стоит `@OptIn(UnverifiedContract::class)` на уровне файла.

### 3.3 Сборка проекта: `ProjectAssembler` (`gateway/mapping`)

```kotlin
/** A project and its owner's profile; [owner] is `null` when the owner has no profile the viewer may see. */
data class AssembledProject(val project: Project, val owner: UserProfileDto?)

/** Turns the server's projects into the app's: the people they name by id are looked up. */
interface ProjectAssembler {
    /** The readable [projects] in their order; all their people are asked for in one [UserDirectory.profiles]. */
    suspend fun assemble(projects: List<ProjectDto>, withMembers: Boolean): List<AssembledProject>
}

class DirectoryProjectAssembler(users: UserDirectory, viewerId: UserId) : ProjectAssembler

suspend fun ProjectAssembler.assemble(project: ProjectDto, withMembers: Boolean): AssembledProject? =
    assemble(listOf(project), withMembers).firstOrNull()

/** Public so that fakes of the assembler in the features' tests build projects the same way. */
fun ProjectDto.toProject(viewerId: UserId, people: Map<String, UserProfileDto?>, withMembers: Boolean): Project?
```

Правила `toProject`:

- Пустой `id` → `null`. `type`, `status`, `field`, `planned_*` читаются через словарь.
- `initiator` = профиль владельца → `UserSummary` (маппинг блока 2). Профиля нет → `UserSummary(UserId(owner), name = "")`.
- `viewerIsInitiator = owner_user_id == viewerId`.
- Роль: используется верхнеуровневый `roles`, а не `details.roles`. Роль без `id` отбрасывается. `slots ≤ 0` → 1. `members` — участники с этим `role_id`, кроме ушедших; строятся только при `withMembers`. `occupied = max(occupied, members.size)`.
- `RoleMember.rating = rating`, только если `review_count > 0`.
- Ошибки `UserDirectory`, кроме 400/404, пробрасываются.

### 3.4 Общий UI и протокол страниц в ViewModel

```kotlin
// :components/paging/LoadMore.kt
/** What the end of a list that loads more on scroll shows. */
enum class LoadMoreState { Idle, Loading, Failed, End }

/** "Загрузка…" with a 20dp spinner while Loading; "Не удалось загрузить" and "Повторить" when Failed. */
@Composable fun LoadMoreFooter(state: LoadMoreState, onRetry: () -> Unit, modifier: Modifier = Modifier)

/**
 * Calls [onLoadMore] when the list is within [threshold] items of its end while [state] is Idle.
 * Restarts on every change of [state], so a short page that does not fill the screen loads the next at once.
 */
@Composable fun LoadMoreEffect(listState: LazyListState, state: LoadMoreState, onLoadMore: () -> Unit, threshold: Int = 5)

// :components/person: the name of a person, or "Без имени" | "No name" when blank. PersonName uses it too.
@Composable fun nameOrPlaceholder(name: String): String

// :components/project/ProjectLabels.kt: "1 из 3 мест занято" (plural by slots); shown only when slots > 1.
@Composable fun rolePlacesText(occupied: Int, slots: Int): String
```

Протокол страниц одинаков для 08 и 02:

1. **Новый поиск** (текст с дебаунсом 300 мс, «поиск» на клавиатуре, «Применить», «Повторить»). Отменяет `searchJob` и `loadMoreJob`, увеличивает поколение запроса и берёт `PageRequest(0, pageSize)`. При успехе `paged = PagedList.of(page, key)`, `loadMore = Idle`, если `hasMore`, иначе `End`. При сбое без результатов на экране — полноэкранная ошибка.
2. **`onLoadMore()`.** Ничего не делает, если `paged.next == null` или уже идёт `searchJob` либо `loadMoreJob`. Иначе ставит `Loading`. При успехе `paged.append(page, key)`, при сбое `Failed`. «Повторить» в футере зовёт тот же `onLoadMore()`.
3. **Ответ чужого поколения** состояние не трогает.
4. **Размер страницы** — параметр конструктора VM (`pageSize = PageRequest.DefaultLimit`), чтобы тесты на моке из 10 человек проверяли несколько страниц.

## 4. Блок 3 «Собрать команду» (08, 08b, 01, 01c)

### `:gateway`

```kotlin
/** Every call throws RemoteFailure on failure; CancellationException passes through. */
interface PeopleSearchApi {
    suspend fun search(params: PeopleSearchParams, page: PageRequest): PeopleListDto  // GET /api/v1/search/people
}
interface RecommendationsApi {
    suspend fun people(limit: Int): PeopleListDto                                     // GET /api/v1/recommendations/people
}
/** Only what the app sends; blank strings, empty lists and `false` are not sent. */
data class PeopleSearchParams(
    val q: String? = null,
    val university: String? = null,
    val projectType: String? = null,       // a GatewayVocabulary.ProjectTypes wire value
    val skills: List<String> = emptyList(), // skill=…&skill=…
    val verifiedOnly: Boolean = false,
    val verifiedProjects: Boolean = false,
    val publications: Boolean = false,
)
// GatewaySession: val peopleSearch: PeopleSearchApi; val recommendations: RecommendationsApi

// dto/PeopleDtos.kt (the spec has no examples here: defaults are protojson zero values)
@Serializable data class PersonDto(
    @SerialName("user_id") val userId: String = "",
    @SerialName("display_name") val displayName: String = "",
    @SerialName("avatar_file_id") val avatarFileId: String = "",
    val university: String = "",
    @SerialName("study_level") val studyLevel: String = "",
    @SerialName("study_year") val studyYear: Int = 0,
    val specialization: String = "",
    val skills: List<String> = emptyList(),
    val interests: List<String> = emptyList(),
    @SerialName("open_to_collaboration") val openToCollaboration: Boolean = false,
    val verified: Boolean = false,
    val rating: Double = 0.0,
    @SerialName("completed_project_count") val completedProjectCount: Int = 0,
)
@Serializable data class PeopleListDto(
    val items: List<PersonDto> = emptyList(),
    @Serializable(with = FlexibleLongSerializer::class) val total: Long = 0,
)

// mapping/PeopleMapping.kt: null without user_id; study via block 2's helper; avatar_file_id is ignored
fun PersonDto.toCollaborator(): Collaborator?
// mapping/PageMapping.kt
fun <T> pageOf(items: List<T>, received: Int, reportedTotal: Long, request: PageRequest): Page<T>
```

### collaborators

```kotlin
// domain
data class CollaboratorQuery(val text: String = "", val filters: CollaboratorFilters = CollaboratorFilters()) // role removed
// ScoredCollaborator is deleted; CollaboratorFilters, RoleReference, RoleRequirement, RoleRequirementProvider stay
interface CollaboratorsRepository {
    /** One [page] of the people the server finds for [query], in its order. */
    suspend fun search(query: CollaboratorQuery, page: PageRequest): Page<Collaborator>
    /** How many people the server finds for [query]: "Показать N" of the filters. */
    suspend fun count(query: CollaboratorQuery): Int
}
class SearchCollaboratorsUseCase(r) { suspend operator fun invoke(query: CollaboratorQuery, page: PageRequest): Result<Page<Collaborator>> }
class CountCollaboratorsUseCase(r) { suspend operator fun invoke(query: CollaboratorQuery): Result<Int> }

// data
interface CollaboratorsDataSource { suspend fun search(query, page): Page<Collaborator>; suspend fun count(query): Int }
class MockCollaboratorsDataSource(people = MockCollaborators.all, latencyMillis = 300) // no RoleRequirementProvider
class GatewayCollaboratorsDataSource(private val api: PeopleSearchApi) : CollaboratorsDataSource
// toParams: q = text.trim() or null (commas pass as typed); verified_only/verified_projects/publications when set;
// project_type = workTypes.singleOrNull(); university = trimmed or null; skill = keywords.
// count = api.search(params, PageRequest(0, 1)).let { max(it.total, it.items.size) }

// ui
data class CollaboratorItem(val user: UserSummary, val subtitle: String) // matchPercent removed; subtitle by block 2's formatter
data class CollaboratorSearchUiState(
    val isLoading: Boolean = true,
    val error: Throwable? = null,            // was hasError
    val roleTitle: String? = null,
    val query: String = "",
    val filters: CollaboratorFilters = CollaboratorFilters(),
    val results: List<CollaboratorItem> = emptyList(),
    val total: Int = 0,                      // "Найдено N"
    val loadMore: LoadMoreState = LoadMoreState.End,
    val filtersSheet: FiltersSheetState? = null, // previewCount from count(), debounced 300 ms
)
class CollaboratorSearchViewModel(role: RoleReference?, dependencies: CollaboratorsUiDependencies, pageSize: Int = PageRequest.DefaultLimit) {
    fun onLoadMore() // + the existing handlers
}
class CollaboratorsUiDependencies(val getRoleRequirement, val searchCollaborators, val countCollaborators)
// CollaboratorSearchRoute: signature unchanged.
```

Новый контракт мока для `sprint-1.md`: фильтры как раньше, роли нет; порядок «верифицированные первыми, потом по имени»; ответ — срез `offset/limit`, `total` = число подходящих.

### feed

```kotlin
class GatewayFeedDataSource(private val api: RecommendationsApi, private val viewerId: UserId, private val limit: Int = 10) : FeedDataSource {
    /** No request: the header shows only the avatar, which is keyed by the id. */
    override suspend fun getViewer(): UserSummary = UserSummary(viewerId, name = "")
    /** The server's random people open to collaboration, without the viewer and records without an id. */
    override suspend fun getRecommendedPeople(): List<Collaborator>
}

sealed interface FeedPeopleState {
    data object NotLoaded : FeedPeopleState
    data object Loading : FeedPeopleState
    data class Failed(val cause: Throwable) : FeedPeopleState
    data class Loaded(val people: List<FeedPerson>) : FeedPeopleState
}
data class FeedUiState(val scope: FeedScope = FeedScope.Projects, val viewerAvatarKey: String? = null, val people: FeedPeopleState = FeedPeopleState.NotLoaded)
// FeedViewModel: no request at init; onScopeSelect(People) loads only from NotLoaded; onPeopleRetry() only from Failed.
// The answer lives as long as the VM (entry "main"), so switching the scope back and forth does not ask again.
// Empty state: "Пока нет рекомендаций" + new text + Outlined "Найти людей" -> the existing onSearchPeopleClick.
```

### Ответ → результат

| Вызов | 200 | 400 / 422* | 401 | 403 / 404 | 5xx, `Timeout`, `NoConnection` |
|---|---|---|---|---|---|
| `search/people` (страница) | `Page`: записи без `user_id` отброшены, `total ≥ received` | первая страница — полноэкранная ошибка; догрузка — футер `Failed` | слушатель сессии → выход, `Http(401)` | как 400 | как 400, текст по `failureText(cause)` |
| `search/people?limit=1` (`count`) | `max(total, items)` | превью остаётся «Показать», ошибки не показываем | выход | превью «Показать» | превью «Показать» |
| `recommendations/people?limit=10` | люди без зрителя | блок `Failed` + «Повторить» | выход | блок `Failed` | блок `Failed` |

\* 422 в спецификации этих путей нет; если придёт, обрабатывается как 400.

### DI и навигация

```kotlin
// DataSourcesModule (@SessionScope)
@Provides @SessionScope
fun collaboratorsDataSource(backend: Backend, gateway: Lazy<GatewaySession>): CollaboratorsDataSource = when (backend) {
    Backend.Mock -> MockCollaboratorsDataSource()
    is Backend.Gateway -> GatewayCollaboratorsDataSource(gateway.value.peopleSearch)
}
@Provides @SessionScope
fun feedDataSource(backend: Backend, gateway: Lazy<GatewaySession>, session: Session): FeedDataSource = when (backend) {
    Backend.Mock -> MockFeedDataSource()
    is Backend.Gateway -> GatewayFeedDataSource(gateway.value.recommendations, session.userId)
}
// CollaboratorsModule: + countCollaborators = CountCollaboratorsUseCase(repository)
```

`AppNavHost` не меняется: кнопка пустого состояния ленты идёт через существующий `onSearchPeopleClick` → `navigateSingleTop(entry, Routes.PeopleSearch)`.

### Удалить / переписать / добавить

- **Удалить:** `ScoredCollaborator`, `CollaboratorQuery.role`, `matchPercent()` и `ByRelevance` в мок-источнике, `CollaboratorItem.matchPercent`, параметр `requirements` у `MockCollaboratorsDataSource`.
- **Переписать:**
  - domain: `/Users/samvel/Desktop/Study/Methodology/AcademicNet/feature/collaborators/domain/src/commonMain/kotlin/org/matchlab/academic_net/feature/collaborators/domain/{Collaborator.kt, CollaboratorsRepository.kt, usecase/CollaboratorUseCases.kt}`;
  - data: `/Users/samvel/Desktop/Study/Methodology/AcademicNet/feature/collaborators/data/src/commonMain/kotlin/org/matchlab/academic_net/feature/collaborators/data/{CollaboratorsDataSource.kt, CollaboratorsRepositoryImpl.kt, mock/MockCollaboratorsDataSource.kt}`;
  - ui: `/Users/samvel/Desktop/Study/Methodology/AcademicNet/feature/collaborators/ui/src/commonMain/kotlin/org/matchlab/academic_net/feature/collaborators/ui/{CollaboratorSearchViewModel.kt, CollaboratorSearchScreen.kt, CollaboratorsUiDependencies.kt}`;
  - feed: `/Users/samvel/Desktop/Study/Methodology/AcademicNet/feature/feed/ui/src/commonMain/kotlin/org/matchlab/academic_net/feature/feed/ui/{FeedViewModel.kt, FeedScreen.kt}` и строки ленты (ru и en);
  - DI: `di/CollaboratorsModule.kt` и `di/DataSourcesModule.kt` в `/Users/samvel/Desktop/Study/Methodology/AcademicNet/shared/src/commonMain/kotlin/org/matchlab/academic_net/di/`.
- **Добавить:**
  - `/Users/samvel/Desktop/Study/Methodology/AcademicNet/common/src/commonMain/kotlin/org/matchlab/academic_net/common/paging/Page.kt` и `PagedList.kt`;
  - `/Users/samvel/Desktop/Study/Methodology/AcademicNet/components/src/commonMain/kotlin/org/matchlab/academic_net/components/paging/LoadMore.kt`, если его нет от блока 2;
  - в `:gateway`: `PeopleSearchApi`, `RecommendationsApi`, `dto/PeopleDtos.kt`, `mapping/{PeopleMapping, PageMapping}.kt` и Ktor-реализации рядом с `AccountApi`;
  - `GatewayCollaboratorsDataSource.kt`, `GatewayFeedDataSource.kt`;
  - `implementation(project(":gateway"))` в collaborators:data и feed:data;
  - записи в `:catalog` (футер в двух состояниях).

### Тесты

- **`:common`:** `PageTest` — пустая страница, `total` достигнут, `received > items`, граница `MaxOffset`, границы `PageRequest`. `PagedListTest` — повторы внутри страницы и между страницами, `total ≥ items`, `next = null` в конце.
- **`:gateway` (MockEngine):**
  - метод, путь, Bearer;
  - `limit` и `offset` всегда; необязательные параметры только при значении; `skill` повторяется; булевы флаги только при `true`. Проверять разобранные `url.parameters`, а не сырую строку;
  - разбор: все поля, отсутствующие поля, `null` вместо массивов, `total` строкой и числом;
  - коды ответа → `RemoteFailure.Http`; `recommendations?limit=10`;
  - `toCollaborator` (нет id → `null`) и `pageOf`.
- **domain:** обе use case — успех, сбой, отмена проходит насквозь.
- **data:**
  - `GatewayCollaboratorsDataSourceTest`: каждый фильтр → параметр; ноль или два типа → без `project_type`; `count` использует `limit=1`.
  - `MockCollaboratorsDataSourceTest`: порядок, срезы, `total`.
  - `GatewayFeedDataSourceTest`: зритель исключён; `getViewer` не зовёт API.
- **ui:**
  - `CollaboratorSearchViewModelTest`: первая страница и `total`; догрузка; двойной `onLoadMore` даёт один запрос; дубли не попадают в список; `Failed` → повтор; новый текст отменяет догрузку; ответ старого поколения отброшен; превью с дебаунсом.
  - `FeedViewModelTest`: на старте запроса нет; при повторном выборе «Люди» нового запроса нет; двойное «Повторить» даёт один запрос; пустой ответ.
- **`:shared`:** граф сессии в режиме Gateway отдаёт новые источники без сети; в режиме mock `GatewaySession` не создаётся.

### Коммиты и распределение

1. «Add pages of server lists to :common» — `domain-data`.
2. «Add the load-more footer and trigger to :components» — `compose-ui`. Если блок 2 положил футер в `feature:profile:ui`, коммит переносит его (после переноса ресурсов — `./gradlew clean`).
3. «Add the people search and recommendations to :gateway» — `domain-data`.
4. «Search people page by page»: domain, data, DI — `domain-data`; VM и экран — `compose-ui`. Один коммит, потому что смена контракта ломает компиляцию ui.
5. «Recommend people on the feed from the server» — то же разделение.
6. Документация: `sprint-1.md` и правило страниц в `CLAUDE.md` («списки с догрузкой — `PagedList` и `LoadMore*`»).

Коммиты 1–3 параллелятся. Для 4 и 5 `compose-ui` начинает, как только `domain-data` положил в рабочее дерево интерфейсы domain.

## 5. Блок 4 «Управлять проектом» (05, 06, 07, 07b, 07c)

### `:common` и `:components`

```kotlin
enum class ProjectStatus { Recruiting, Unpublished, Started, Completed, Cancelled }
// Unpublished = "Снят с публикации" = the server's paused recruiting.

data class Role(
    val id: RoleId,
    val title: String,
    val skills: List<String> = emptyList(),
    /** How many people the role is for; the app creates roles for one. */
    val slots: Int = 1,
    /** The people on the role the viewer is shown; empty in lists even when [occupied] > 0. */
    val members: List<RoleMember> = emptyList(),
    /** Places taken as the server counts them. */
    val occupied: Int = members.size,
) {
    val isVacant: Boolean get() = occupied < slots
    val vacantPlaces: Int get() = (slots - occupied).coerceAtLeast(0)
}

data class Project(
    /* id, title, sphere, problem, goal, tags, initiator, roles, viewerIsInitiator as now */
    val type: ProjectType?,        // null: the server's value is unknown to the app
    val status: ProjectStatus?,    // null: unknown, no management actions
    val period: ProjectPeriod?,    // null: the dates could not be read
) {
    val vacantRoles: List<Role> get() = roles.filter { it.isVacant }
    /** "2 из 4 занято" counts places; a role for one person counts as one. */
    val occupiedPlaces: Int get() = roles.sumOf { minOf(it.occupied, it.slots) }
    val totalPlaces: Int get() = roles.sumOf { it.slots }
}
```

`Role.member` и `Project.occupiedRolesCount` удаляются.

В `:components`:

- `ProjectStatus.label()` получает три новые подписи;
- `projectCardUiModel(type: ProjectType?, status: ProjectStatus?, …, period: ProjectPeriod?)`;
- `ProjectCardUiModel.type: String?`;
- `ProjectHeader(type: ProjectType?, status: ProjectStatus?, sphere)` — без чипов для `null` и пустых значений;
- `projectCardFooter(rolesCount, period?)`;
- `RoleApplyCardUiModel(role, skills, occupied = 0, slots = 1)` со строкой мест при `slots > 1`;
- `rolePlacesText`, `nameOrPlaceholder`.

### `:gateway`

```kotlin
interface ProjectsApi {
    suspend fun create(input: ProjectInputDto): ProjectDto                                // POST /api/v1/projects -> 201
    suspend fun list(params: ProjectQueryParams, page: PageRequest): ProjectListDto      // GET /api/v1/projects
    suspend fun get(id: String): ProjectDto                                               // GET /api/v1/projects/{id}
    suspend fun addRole(id: String, input: ProjectRoleInputDto): ProjectDto              // POST …/{id}/roles -> 200
    suspend fun transition(id: String, request: ProjectTransitionRequestDto): ProjectDto // POST …/{id}/transitions -> 200
}
/** Query of GET /projects and GET /search/projects; only what the app sends. */
data class ProjectQueryParams(
    val q: String? = null, val type: String? = null, val field: String? = null,
    val tags: List<String> = emptyList(), // tag=…&tag=…
    val mine: String? = null,
)
// GatewaySession: val projects: ProjectsApi

// dto/ProjectDtos.kt: one ProjectInputDto for the request and for Project.details
@Serializable data class ProjectInitialRoleDto(val title: String, val description: String? = null,
    val skills: List<String> = emptyList(), val slots: Int)
@Serializable data class ProjectInputDto(
    val id: String? = null,
    val title: String = "", val type: String = "", val field: String = "",
    val problem: String = "", val goal: String = "", val tags: List<String> = emptyList(),
    @SerialName("planned_start") val plannedStart: String = "",
    @SerialName("planned_end") val plannedEnd: String = "",
    val roles: List<ProjectInitialRoleDto> = emptyList(),         // required on create: [] is sent
    @SerialName("expected_revision") val expectedRevision: Int? = null,
)
@Serializable data class ProjectRoleDto(val id: String = "", val title: String = "", val description: String = "",
    val skills: List<String> = emptyList(), val slots: Int = 0, val occupied: Int = 0)
@Serializable data class ProjectMemberDto(@SerialName("user_id") val userId: String = "",
    @SerialName("role_id") val roleId: String = "", @SerialName("role_title") val roleTitle: String = "",
    val status: String = "")
@Serializable data class ProjectDto(
    val id: String = "",
    @SerialName("owner_user_id") val ownerUserId: String = "",
    val details: ProjectInputDto = ProjectInputDto(),
    val status: String = "",
    val revision: Int = 0,
    val roles: List<ProjectRoleDto> = emptyList(),
    val members: List<ProjectMemberDto> = emptyList(),
    @SerialName("created_at_unix") @Serializable(with = FlexibleLongSerializer::class) val createdAtUnix: Long = 0,
    @SerialName("completed_at_unix") @Serializable(with = FlexibleLongSerializer::class) val completedAtUnix: Long = 0,
    @SerialName("pending_join_count") val pendingJoinCount: Int = 0,
    @SerialName("moderation_hidden") val moderationHidden: Boolean = false,
)
@Serializable data class ProjectListDto(val items: List<ProjectDto> = emptyList(),
    @Serializable(with = FlexibleLongSerializer::class) val total: Long = 0)
@Serializable data class ProjectRoleInputDto(val title: String, val description: String? = null,
    val skills: List<String> = emptyList(), val slots: Int,
    @SerialName("expected_revision") val expectedRevision: Int)   // always sent, even 0
@Serializable data class ProjectTransitionRequestDto(val action: String,
    @SerialName("expected_revision") val expectedRevision: Int)
```

К этому коммиту относятся также `GatewayVocabulary` (3.2), `ProjectMapping` и `ProjectAssembler` (3.3).

### projects: domain

```kotlin
/** The server refused a change because the project had changed meanwhile (409), also after a reload and one more try. */
class ProjectConflictException(val projectId: ProjectId) : IllegalStateException("Project ${projectId.value} changed on the server")

interface ProjectsRepository {
    /** The viewer's own projects as last loaded, newest first; `null` until loaded once. Never fails. */
    fun observeMyProjects(): Flow<List<Project>?>
    /** Loads them from the server into [observeMyProjects]. Throws on failure. */
    suspend fun refreshMyProjects()
    /** The project as last loaded or changed in this session; `null` while unknown. Never fails. */
    fun observeProject(id: ProjectId): Flow<Project?>
    /** Asks the server and updates [observeProject]; `null` when the viewer may not see such a project. */
    suspend fun loadProject(id: ProjectId): Project?
    /** From this session's cache, else from the server. */
    suspend fun getProject(id: ProjectId): Project?
    suspend fun publishProject(project: NewProject): Project
    /** @throws ProjectNotFoundException, ProjectConflictException */
    suspend fun unpublishProject(id: ProjectId): Project
    /** @throws ProjectNotFoundException, ProjectConflictException */
    suspend fun addRole(projectId: ProjectId, role: RoleDraft): Role
    suspend fun getSpheres(): List<Sphere>
}
class ObserveMyProjectsUseCase(r)  { operator fun invoke(): Flow<List<Project>?> }
class RefreshMyProjectsUseCase(r)  { suspend operator fun invoke(): Result<Unit> }
class ObserveProjectUseCase(r)     { operator fun invoke(id: ProjectId): Flow<Project?> }
class LoadProjectUseCase(r)        { suspend operator fun invoke(id: ProjectId): LoadResult<Project> }
// PublishProjectUseCase, AddRoleUseCase (a conflict is SubmitResult.Failure(ProjectConflictException)), GetSpheresUseCase: as now
sealed interface UnpublishProjectResult { /* Unpublished, NotPublished, NotFound, Failure as now */ data object Conflict : UnpublishProjectResult }
// UnpublishProjectUseCase: ProjectNotFoundException on the second step -> NotFound (today it is Failure)
```

### projects: data

`ProjectsDataSource` повторяет репозиторий, `ProjectsRepositoryImpl` передаёт вызовы как есть.

`MockProjectsDataSource`: `observeMyProjects()` отдаёт `null` до первого `refreshMyProjects()`; добавлен `loadProject`; роли создаются с `slots = 1`; конфликтов не бывает.

`SphereCatalog` (`internal`): 12 сфер с `id == name`, имена те же, что в моке.

`GatewayProjectsDataSource(api: ProjectsApi, assembler: ProjectAssembler, viewerId: UserId, spheres = SphereCatalog.spheres)`. Кэш сессии — `MutableStateFlow` с полями `entries: Map<ProjectId, Entry(project, revision)>` и `mine: List<ProjectId>?`.

- **`refreshMyProjects`.** `list(mine = Owner, PageRequest(0, 100))` → оставить `owner_user_id == viewerId` → отсортировать по `created_at_unix`, новые первыми → `assemble(withMembers = true)` → записать в кэш, при одинаковом id побеждает бо́льшая ревизия → `mine` = id в этом порядке. Если `total > 100`, это пишется в лог.
- **`loadProject`.** 400/403/404 → убрать из кэша, вернуть `null`.
- **Записи** идут под общим `Mutex`.
  - `create`: тело собирается из `NewProject` — тип и `field` через словарь, даты через `PlannedDates`, `roles` с `slots = 1` (пустой список отправляется как `[]`). Ответ кладётся в кэш и в начало `mine`, только если список уже загружен.
  - `addRole`: ревизия берётся из кэша; если проекта в кэше нет, сначала `loadProject`. Новая роль в ответе — та, чьего id не было в кэше; при нескольких таких выбирается совпадающая по названию.
  - `unpublish`: `transition(Unpublish, revision)`.
- **409.** Перезагрузить проект. `addRole`: если появилась роль с тем же названием и новым id, это успех без повтора. `unpublish`: если статус уже `Unpublished`, это успех. Иначе ровно один повтор с новой ревизией; второй 409 → `ProjectConflictException`. Ответ 404 на запись → удалить из кэша и бросить `ProjectNotFoundException`.

### projects: ui

```kotlin
data class MyProjectsUiState(val isLoading: Boolean = true, val projects: List<MyProjectItem> = emptyList(), val error: Throwable? = null)
class MyProjectsViewModel(observeMyProjects, refreshMyProjects) { fun onRetry() } // refresh at init; retry guarded by its Job
// MyProjectItem: type: ProjectType?, status: ProjectStatus?, period: ProjectPeriod?

data class VacantRoleItem(val id: RoleId, val title: String, val skills: List<String>, val canFindPeople: Boolean,
    val occupied: Int, val slots: Int)  // one card; "N из M мест занято" when slots > 1
// OccupiedRoleItem: one per member of every role (as now, from members)
data class ProjectDetails(/* as now */ val type: ProjectType?, val status: ProjectStatus?, val period: ProjectPeriod?,
    val occupiedPlaces: Int, val totalPlaces: Int) { val canUnpublish get() = status == ProjectStatus.Recruiting }
sealed interface ProjectActionError { data object Conflict : ProjectActionError; data class Failed(val cause: Throwable) : ProjectActionError }
data class ProjectManagementUiState(
    val isLoading: Boolean = true,
    val project: ProjectDetails? = null,
    val loadError: Throwable? = null,           // nothing to show and loading failed: "Повторить"
    val isUnpublishConfirmVisible: Boolean = false,
    val isUnpublishing: Boolean = false,
    val roleEditor: RoleEditorState? = null,
    val isSavingRole: Boolean = false,
    val actionError: ProjectActionError? = null, // shown in the role sheet while it is open
)
class ProjectManagementViewModel(projectId, observeProject, loadProject, unpublishProject, addRole) { fun onRetry() }
// loadProject at init; the cached project is shown at once. After a conflict the data source has already reloaded the project.

sealed interface PublishFailure { data object Unconfirmed : PublishFailure; data class Failed(val cause: Throwable) : PublishFailure }
// CreateProjectUiState.publishFailed -> publishFailure: PublishFailure?; RemoteFailure.Timeout -> Unconfirmed + refreshMyProjects()
class CreateProjectViewModel(publishProject, getSpheres, refreshMyProjects) // guards isPublishing / publishedProjectId kept
class ProjectsUiDependencies(observeMyProjects, refreshMyProjects, observeProject, loadProject, publishProject, unpublishProject, addRole, getSpheres)
// RoleEditorSheet(…, errorText: String? = null): a red line above the buttons. ParticipantManageCard: untouched.
// Banners of 07c for Started / Completed / Cancelled / null; actions only for Recruiting. Routes: unchanged.
```

### Ответ → результат

| Вызов | 2xx | 400 / 422 | 401 | 403 | 404 | 409 | 5xx, `Timeout`, `NoConnection` |
|---|---|---|---|---|---|---|---|
| `GET /projects?mine=owner&limit=100` | чужие отброшены, новые первыми, кэш | ошибка + «Повторить» (вероятно, не угадан `mine`) | выход | ошибка | ошибка | ошибка | ошибка с `failureText` |
| `GET /projects/{id}` | кэш, `Found` | `NotFound`, убрать из кэша | выход | `NotFound` | `NotFound` | `Failure` | `Failure`; если проект в кэше — показываем кэш |
| `POST /projects` | 201: кэш (+ в начало `mine`) → `Success` | `Failed` | выход | `Failed` | `Failed` | `Failed` | `Timeout` → `Unconfirmed` и обновление списка; остальное → `Failed` |
| `POST /{id}/roles` | новая роль, кэш | `Failed` | выход | `Failed` | `ProjectNotFoundException` | проверка → повтор → `Conflict` | `Failed` |
| `POST /{id}/transitions` (`pause`) | `Unpublished`, кэш | `Failed` (вероятно, не угадан `action`) | выход | `Failed` | `NotFound` | проверка → повтор → `Conflict` | `Failed` |

### DI и навигация

```kotlin
@Provides @SessionScope
fun projectAssembler(gateway: GatewaySession, session: Session): ProjectAssembler =
    DirectoryProjectAssembler(gateway.users, session.userId)

@Provides @SessionScope
fun projectsDataSource(backend: Backend, gateway: Lazy<GatewaySession>, assembler: Lazy<ProjectAssembler>, session: Session): ProjectsDataSource =
    when (backend) {
        Backend.Mock -> MockProjectsDataSource()
        is Backend.Gateway -> GatewayProjectsDataSource(gateway.value.projects, assembler.value, session.userId)
    }
// ProjectsModule: + RefreshMyProjectsUseCase, LoadProjectUseCase
```

`AppNavHost` без изменений: `onPublished` уже идёт через `navigateOnce(entry, …) { popUpTo(...) }`. Id в маршрутах — UUID (hex и дефисы), экранировать их не нужно.

### Удалить / переписать / добавить

- **Удалить:** `Role.member`, `Project.occupiedRolesCount`, `CreateProjectUiState.publishFailed`, `ProjectManagementUiState.actionFailed`.
- **Переписать (коммит модели):**
  - `/Users/samvel/Desktop/Study/Methodology/AcademicNet/common/src/commonMain/kotlin/org/matchlab/academic_net/common/model/Project.kt` и `ProjectTest.kt`;
  - `ProjectLabels.kt`, `ProjectSections.kt`, `ProjectCard.kt`, `RoleCards.kt`, строки `:components`;
  - `/Users/samvel/Desktop/Study/Methodology/AcademicNet/mockserver/src/commonMain/kotlin/org/matchlab/academic_net/mockserver/MockProjectCatalog.kt` и его тест;
  - `MockProjectsData.kt`; `MyProjectsViewModel.kt`, `ProjectManagementViewModel.kt`, `ProjectManagementScreen.kt`;
  - `ProjectSearchViewModel.kt`, `ProjectSearchScreen.kt`, `ProjectDetailsViewModel.kt`, `ProjectDetailsScreen.kt`;
  - `MockProjectSearchDataSource.kt` (тип и статус), `ComponentCatalog.kt`; фикстуры тестов domain.
- **Переписать (коммит фичи):** `ProjectsRepository.kt`, `usecase/{ObserveProjects, UnpublishProject}.kt`, `ProjectsDataSource.kt`, `ProjectsRepositoryImpl.kt`, `mock/MockProjectsDataSource.kt`, `CreateProjectViewModel.kt`, `MyProjectsScreen.kt` («Повторить»), `role/RoleEditorSheet.kt`, `ProjectsUiDependencies.kt`, строки projects (ru и en), `di/ProjectsModule.kt`, `di/DataSourcesModule.kt`.
- **Добавить:** в `:gateway` — `ProjectsApi`, `dto/ProjectDtos.kt`, разделы `GatewayVocabulary`, `mapping/{ProjectMapping, ProjectAssembler}.kt`; в projects:data — `GatewayProjectsDataSource.kt`, `SphereCatalog.kt`; в domain — `RefreshMyProjectsUseCase`, `LoadProjectUseCase`, `ProjectConflictException`.

### Тесты

- **`:common`:** места, вакансия при `slots > 1`, `occupied` по умолчанию.
- **`:gateway` (MockEngine):**
  - пять методов: путь, метод, Bearer; в `create` тело в snake_case, `roles: []` присутствует, без `id` и `expected_revision`; в `addRole` и `transition` `expected_revision` есть; в списке `mine=owner`;
  - разбор `ProjectDto`: полный, пустой объект, `null` массивы, `*_unix` строкой и числом;
  - `GatewayVocabulary`: каждое значение и синоним, регистр, `-` и пробел, неизвестное → `null`;
  - `PlannedDates`: `2026-10`, `2026-10-01`, `2026-10-01T23:30:00-05:00` → октябрь; месяц 13 и мусор → `null`; 29 февраля в високосный год;
  - `toProject`: все правила из 3.3;
  - `DirectoryProjectAssembler`: один вызов `profiles` без повторов; участники только при `withMembers`; владелец без профиля даёт заглушку; исключение пробрасывается.
- **domain:** `RefreshMyProjectsUseCase`, `LoadProjectUseCase`, `Unpublish` с `Conflict` и `NotFound` на втором шаге, `AddRole` с конфликтом.
- **data, `GatewayProjectsDataSourceTest` на фейках `ProjectsApi` и `ProjectAssembler`:**
  - список: `null` до загрузки, чужие проекты отброшены, сортировка;
  - тело `create`; кэш после `create` при загруженном и незагруженном списке;
  - `load` 400/403/404 → `null` и удаление из кэша, 500 → исключение;
  - ревизия в записях; 409 → повтор → успех; 409 дважды → конфликт; роль уже добавлена → успех без повтора; уже снят → успех;
  - бо́льшая ревизия побеждает; параллельные записи идут по очереди;
  - сферы: 12, `id == name`.
  - Плюс `MockProjectsDataSourceTest`.
- **ui:**
  - `MyProjectsViewModelTest`: загрузка, ошибка без кэша → «Повторить», ошибка при кэше, двойной повтор → один запрос;
  - `ProjectManagementViewModelTest`: кэш сразу, `NotFound`, `loadError` → повтор, `Conflict` у роли (шит открыт) и у снятия, существующие стражи;
  - `CreateProjectViewModelTest`: таймаут → `Unconfirmed` + обновление списка, двойная публикация → один `createProject`.

### Коммиты и распределение

7. «Model projects as the server has them»: `:common`, моки, фикстуры — `domain-data`; `:components`, VM и экраны трёх фич, `:catalog` — `compose-ui`. Механическая правка, должна собираться целиком.
8. «Add the projects API and the vocabulary to :gateway» — `domain-data`.
9. «Manage projects on the server»: domain, data, DI — `domain-data`; VM, экраны, строки — `compose-ui`.
10. Документация: `sprint-1.md` (таблица словаря, допущения), правило словаря в `CLAUDE.md`.

## 6. Блок 5 «Найти подходящий проект» (02, 03) + «Оценить проект» (04)

### `:gateway`

```kotlin
interface ProjectCatalogApi {
    suspend fun search(params: ProjectQueryParams, page: PageRequest): ProjectListDto  // GET /api/v1/search/projects
    suspend fun get(id: String): ProjectDto                                           // GET /api/v1/projects/{id}
}
// GatewaySession: val projectCatalog: ProjectCatalogApi (the same internal Ktor class as ProjectsApi)
```

### projectsearch

```kotlin
// domain: ProjectDuration and ProjectFilters.durations are deleted
data class ProjectFilters(val types: Set<ProjectType> = emptySet(), val sphere: Sphere? = null, val keywords: List<String> = emptyList())
interface ProjectSearchRepository { suspend fun search(query: ProjectQuery, page: PageRequest): Page<FoundProject> }
class SearchProjectsUseCase(r) { suspend operator fun invoke(query: ProjectQuery, page: PageRequest): Result<Page<FoundProject>> }
// FoundProject, ProjectQuery, SphereProvider, GetSearchSpheresUseCase: unchanged

// data
interface ProjectSearchDataSource { suspend fun search(query: ProjectQuery, page: PageRequest): Page<FoundProject> }
class MockProjectSearchDataSource // rules as now minus duration; slices offset/limit, total = matches
class GatewayProjectSearchDataSource(private val api: ProjectCatalogApi, private val assembler: ProjectAssembler) : ProjectSearchDataSource
// toParams: q = trimmed or null; type = types.singleOrNull(); field = Fields.toWire(sphere); tag = keywords.
// Not sent: status, mine, duration, profile_user_id.
// search: assemble(dto.items, withMembers = false) -> FoundProject(project, owner university or "")
//   -> pageOf(items, received = dto.items.size, dto.total, page)

// ui
data class ProjectSearchItem(/* as now */ val isOwn: Boolean, val type: ProjectType?, val status: ProjectStatus?, val period: ProjectPeriod?)
// Author line: isOwn -> "Вы инициатор"; else non-blank parts of nameOrPlaceholder(name) and university, joined by " · ".
data class ProjectSearchUiState(/* as now; hasError -> error: Throwable? */ val total: Int = 0, val loadMore: LoadMoreState = LoadMoreState.End)
class ProjectSearchViewModel(dependencies, pageSize: Int = PageRequest.DefaultLimit) { fun onLoadMore() }
// ProjectFiltersSheet: the "Сроки" group is removed. ProjectSearchRoute: unchanged.
```

### projectdetails

```kotlin
class GatewayProjectDetailsDataSource(private val api: ProjectCatalogApi, private val assembler: ProjectAssembler) : ProjectDetailsDataSource {
    // get: 400/403/404 -> null; assemble(dto, withMembers = false) -> ProjectDetails(project, owner.toInitiatorInfo())
}
// UserProfileDto?.toInitiatorInfo(): university or "", rating only if review_count > 0, completed_project_count or 0
// ui: ProjectDetailsUiModel with nullable type/status/period and occupiedPlaces/totalPlaces (commit 7);
//     VacantRoleUiModel(occupied, slots); the initiator's name through nameOrPlaceholder; Error(cause) for failureText.
```

Правило мока «только статус Поиск соавторов» остаётся: мок играет правдоподобный сервер. Реальный сервер решает видимость сам, клиентский фильтр по статусу не добавляем.

### Ответ → результат

| Вызов | 200 | 400 / 422 | 401 | 403 | 404 | 5xx, `Timeout`, `NoConnection` |
|---|---|---|---|---|---|---|
| `GET /search/projects` | владельцы через сборщик → `Page<FoundProject>` | первая страница — ошибка; догрузка — футер `Failed` | выход | как 400 | как 400 | как 400 |
| профиль владельца (`UserDirectory`) | имя, вуз, щит | 400/404 → заглушка «Без имени», вуз пустой | выход | исключение → сбой страницы | — | исключение → сбой страницы |
| `GET /projects/{id}` (04) | `ProjectDetails` | «Проект не найден» | выход | «Проект не найден» | «Проект не найден» | ошибка + «Повторить» |

### DI и навигация

```kotlin
@Provides @SessionScope
fun projectSearchDataSource(backend: Backend, gateway: Lazy<GatewaySession>, assembler: Lazy<ProjectAssembler>): ProjectSearchDataSource =
    when (backend) {
        Backend.Mock -> MockProjectSearchDataSource()
        is Backend.Gateway -> GatewayProjectSearchDataSource(gateway.value.projectCatalog, assembler.value)
    }
@Provides @SessionScope
fun projectDetailsDataSource(backend: Backend, gateway: Lazy<GatewaySession>, assembler: Lazy<ProjectAssembler>): ProjectDetailsDataSource =
    when (backend) {
        Backend.Mock -> MockProjectDetailsDataSource()
        is Backend.Gateway -> GatewayProjectDetailsDataSource(gateway.value.projectCatalog, assembler.value)
    }
```

`AppNavHost` без изменений. Карточка своего проекта открывает 04 только для чтения, как уже сделано для `viewerIsInitiator`.

### Удалить / переписать / добавить

- **Удалить:**
  - `ProjectDuration`, `ProjectFilters.durations`;
  - `monthsCount()` и `duration()` в `/Users/samvel/Desktop/Study/Methodology/AcademicNet/feature/projectsearch/data/src/commonMain/kotlin/org/matchlab/academic_net/feature/projectsearch/data/MockProjectSearchDataSource.kt`;
  - чипы срока в `ProjectFiltersSheet.kt`;
  - ключи `projectsearch_group_duration`, `projectsearch_duration_up_to_3`, `projectsearch_duration_3_to_6`, `projectsearch_duration_over_6` — в обоих файлах строк.
- **Переписать:**
  - `/Users/samvel/Desktop/Study/Methodology/AcademicNet/feature/projectsearch/domain/src/commonMain/kotlin/org/matchlab/academic_net/feature/projectsearch/domain/{ProjectSearch.kt, ProjectSearchRepository.kt, ProjectSearchUseCases.kt}`;
  - `ProjectSearchDataSource.kt`, `ProjectSearchRepositoryImpl.kt`, `ProjectSearchViewModel.kt`, `ProjectSearchScreen.kt`;
  - `/Users/samvel/Desktop/Study/Methodology/AcademicNet/shared/src/commonTest/kotlin/org/matchlab/academic_net/ProjectSearchAndDetailsTest.kt`;
  - `di/DataSourcesModule.kt`.
- **Добавить:** `ProjectCatalogApi`, `GatewayProjectSearchDataSource.kt`, `GatewayProjectDetailsDataSource.kt`, строку `projectsearch_you_initiator`, `implementation(project(":gateway"))` в projectsearch:data и projectdetails:data.

### Тесты

- **`:gateway` (MockEngine):** `search/projects` — `q`, ровно один `type`, `field`, повторяющийся `tag`; нет `status`, `mine`, `duration`. Плюс `get` через `ProjectCatalogApi`.
- **domain:** `ProjectSearchTest` без сроков; use case со страницей.
- **data:**
  - `GatewayProjectSearchDataSourceTest`: параметры; вуз владельца; владелец без профиля; `received` и `total`; сбой сборщика → исключение;
  - `MockProjectSearchDataSourceTest`: срезы и `total`;
  - `GatewayProjectDetailsDataSourceTest`: `rating = null` при `review_count = 0`; 400/403/404 → `null`; 500 → исключение.
- **ui:** `ProjectSearchViewModelTest` — протокол страниц как на 08, «Сбросить фильтры», `isOwn`; `ProjectDetailsViewModelTest` — места.
- **`:shared`:** `ProjectSearchAndDetailsTest` обходит все страницы мок-поиска, и каждая находка открывается по id.

### Коммиты и распределение

11. «Add the project catalog API to :gateway» — `domain-data`.
12. «Find projects page by page on the server» (projectsearch) — `domain-data` (domain, data, DI) и `compose-ui` (VM, экран, шит).
13. «Open project pages from the server» (projectdetails) — так же.
14. Документация.

## 7. Пробелы дизайна и допущения (в `docs/sprint-1.md`)

**Заменить устаревшие утверждения:**

- «Пагинации нет»;
- «Срок — число месяцев…» и правило сроков в контракте мок-поиска;
- «Свои проекты … не показываются»;
- «Процент совпадения … остаётся в модели» и «совпадение — доля навыков роли» в контракте мок-поиска людей;
- «рекомендации людей для проектов пользователя»;
- «С бэкендом модуль удаляется целиком» (это уже сделал шаг 1).

**Блок 3:**

- Футер догрузки на 08 и 02 сделан как на 09b (фаза 3 Figma): «Загрузка…» / «Не удалось загрузить» + «Повторить».
- «Найдено N» и «Показать N» берутся из `total` сервера.
- Поиск на роль — это поиск по навыкам роли в строке поиска. Роль на сервер не уходит, процента совпадения и порядка по роли нет.
- «Ключевые слова» уходят как навыки (`skill`). `q` уходит как введено, запятые не разбираются.
- Рекомендации — случайные люди, открытые к сотрудничеству. Заголовок «Люди для ваших проектов» оставлен из макета. Пустое состояние (макета нет): «Пока нет рекомендаций» / «Здесь появятся люди, открытые к сотрудничеству» + Outlined «Найти людей». Сам зритель в рекомендациях не показывается.
- Рекомендации загружаются при первом выборе «Люди» и не перезапрашиваются, пока жив экран.
- Рейтинг людей в поиске не показывается: у `Person` нет `review_count`.

**Блок 4:**

- Статусы «Начат», «Завершён», «Отменён»: чипы и баннеры 07c (текстов в макете нет).
- Нераспознанный статус: баннер «Статус проекта не распознан: действия недоступны».
- Нераспознанные тип или сроки: чип или срок не показываются.
- Роль на несколько мест — одна карточка «N из M мест занято». Счётчик «N из M занято» считает места.
- Конфликт (409) — сообщение под вкладками или в шите роли (ошибки в шите в макете нет).
- Таймаут публикации — отдельный текст с советом проверить «Мои проекты».
- «Мои проекты» и 07: ошибка загрузки с «Повторить».
- «Снять с публикации» = «приостановить набор» сервера. Остальные переходы не показываются.
- «Мои проекты» загружают не больше 100 проектов.
- Участник без профиля показывается как «Без имени».

**Блок 5:**

- Фильтр «Сроки» убран.
- В поиск могут попасть свои проекты (подпись «Вы инициатор», страница 04 только для чтения) и проекты в других статусах (с чипом статуса).
- Ключевые слова ищутся по тегам.
- Тип проекта уходит на сервер, только если выбран ровно один.
- Инициатор без профиля: «Без имени», без вуза, «0 завершённых проектов».

**Общее:**

- Таблица словаря из 3.2 с пометкой «не проверено на живом сервере».
- В режиме Gateway `Sphere.id == name`.
- Полноэкранные ошибки показывают `failureText(cause)`.

**Английские тексты (наш перевод):** "Started", "Completed", "Cancelled", "%1$d of %2$d spots filled", "No name", "Loading…", "Couldn't load", "People open to collaboration will appear here", "Find people", "Couldn't load the project", "The project has changed. We've refreshed it: check and try again", "The server didn't answer in time. The project may have been published: check My projects before trying again", "The app doesn't recognise the project's status: no actions are available", "You're the initiator".

Русские тексты:

- «Начат», «Завершён», «Отменён»;
- «%1$d из %2$d места занято» (one) / «%1$d из %2$d мест занято» (остальные формы);
- «Без имени», «Загрузка…», «Не удалось загрузить», «Найти людей»;
- «Не удалось загрузить проект»;
- «Проект начат: команда работает над ним», «Проект завершён», «Проект отменён»;
- «Проект изменился. Мы обновили данные — проверьте и повторите действие»;
- «Сервер не ответил вовремя. Проект мог опубликоваться — проверьте «Мои проекты» перед повтором»;
- «Статус проекта не распознан: действия недоступны»;
- «Вы инициатор».

## 8. Порядок, параллельность, риски

**Порядок.** Блок 3 (коммиты 1–6) → блок 4 (7–10) → блок 5 (11–14). Коммиты в `:common`, `:components` и `:gateway` (1, 2, 3, 8, 11) новый код ещё не используют и собираются сами по себе. Коммиты фич меняют domain, data и ui вместе: смена контракта domain ломает компиляцию ui. Внутри коммита `domain-data` и `compose-ui` работают параллельно, как только интерфейсы domain лежат в рабочем дереве.

**Проверка (`tester`) после каждого коммита фичи:**

- `./gradlew :androidApp:assembleDebug`;
- host-тесты затронутых модулей, например `:feature:collaborators:ui:testAndroidHostTest`;
- `:koverHtmlReport`;
- компиляция iOS: `KONAN_DATA_DIR=<scratchpad>/konan-data ./gradlew :shared:compileKotlinIosSimulatorArm64 :shared:compileKotlinIosArm64` (линковки и iOS-тестов нет);
- эмулятор `-s emulator-5554` в режиме `-Pmatchlab.backend=mock`: догрузка, футер, пустое состояние ленты, баннеры 07c.

**Риски:**

1. **Неверные догадки словаря.** Покажут 400 на создании, на «Снять с публикации» и в списке «Моих проектов». Это видно по общей ошибке и логу, исправление — одна строка. Альтернатива (не рекомендую): перебирать варианты при 400 (`owner` → `true`, `pause` → `pause_recruitment`).
2. **Смысл `q`, `skill`, `tag` неизвестен.** Поиск на роль со строкой «Computer Vision, Python» на сервере может вернуть пусто. Пользователь правит строку поиска.
3. **Сдвиг списка между страницами.** Дубли убирает `PagedList`. Пропуск записи при удалении на сервере принимаем.
4. **N+1 владельцев.** Первая страница поиска проектов — до 21 запроса (не более 4 параллельно). Медленно, но кэш на 5 минут помогает. Просьба к бэкенду ниже.
5. **Нет идемпотентности.** На таймауте `POST /projects` возможен дубль проекта; смягчено отдельным текстом и обновлением списка. Ktor `HttpRequestRetry` не включать. OkHttp может молча повторить POST на протухшем соединении; решение принято: `retryOnConnectionFailure(false)` в движке Android (G48, `docs/gateway-integration.md`).
6. **Коммит 7 большой.** Nullable-поля задевают 3 фичи, `:components` и `:catalog`; всё ловит компилятор. `LocalizationResourcesTest` требует совпадения ключей ru и en, в том числе после удаления ключей срока.
7. **Зависимость от блока 2.** Имена полей `UserProfileDto`, помощник «уровень и курс» для `Collaborator`, значения `ProjectTypes`, место футера и заглушки имени. Пока блок 2 не в коде, контракты блоков 3–5 опираются на его поля в терминах спецификации.
8. **Мок и Gateway ведут себя по-разному.** Мок скрывает свои и неактивные проекты, Gateway — как решит сервер. Записано в допущениях.
9. **iOS.** Платформенного кода нет. `kotlinx-datetime` (`YearMonth.firstDay`/`lastDay`/`parse`, `LocalDate.yearMonth` — проверено по исходникам 0.8.0) общий. Проверка — только компиляцией.

## 9. Вопросы бэкенду (работу не останавливают)

1. Значения `Project.type`, `Project.status`, `transitions.action` (что значит «приостановить набор» — скрывает ли проект из поиска), `?mine=`, `?duration=`, `ProjectMember.status`, а также `type` и `status` откликов. Формат `planned_start` и `planned_end`.
2. Исключает ли `/search/projects` по умолчанию свои проекты и проекты не в наборе? Какие значения принимает `status` в фильтре?
3. Разрешён ли `roles: []` в `POST /projects`? Значит ли 409 на `roles` и `transitions` только «ревизия устарела», и можно ли отличить по `error` конфликт состояния?
4. `field` — свободный текст или справочник? Будет ли эндпоинт со списком сфер?
5. Смысл `q` (поля, запятые), `skill[]` и `tag[]` (точное совпадение, И или ИЛИ), порядок выдачи и его устойчивость между страницами; есть ли курсор. Точен ли `total`.
6. Встроить в `Project` сводку владельца (имя, вуз, `verified`, `rating`, `review_count`, `completed_project_count`) или сделать `GET /users?ids=` — это снимет N+1.
7. `Idempotency-Key` для `POST /projects` (и `POST /complaints`).
8. Добавить `review_count` в `Person`. Исключает ли `/recommendations/people` самого зрителя, и какие границы у `limit`?
9. Что `moderation_hidden` значит для владельца и нужно ли его показывать?
10. Как запустить сервер для приёмки (Docker Compose с Mailpit)?

**Согласовать с блоком 2:** `ProjectTypes` = `scientific`/`project`; один помощник «уровень и курс» для `PersonDto` и `UserProfileDto`; футер догрузки и заглушка имени лежат в `:components`; маппинг `UserProfileDto → UserSummary` публичный, чтобы его использовал `toProject`.
