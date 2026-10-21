# Блок p45 «Проекты, сторона чтения»: межслойные контракты для параллельной работы domain-data и compose-ui

Экраны: 05, 06, 07, 07b, 07c (фича `projects`), 02, 03 (`projectsearch`), 04 (`projectdetails`). Корень репозитория — `/Users/samvel/Desktop/Study/Methodology/AcademicNet`. Оба агента реализуют этот документ буквально. Если чего-то не хватает, агент принимает простое решение в своём слое и пишет о нём в отчёте. Править чужой слой нельзя.

## 0. Что изменилось по сравнению с прежним проектом

Прежний проект — разделы 5 и 6 `/Users/samvel/Desktop/Study/Methodology/AcademicNet/docs/gateway-blocks3-5-search-and-projects.md`.

| # | Было | Стало | Почему |
|---|---|---|---|
| 1 | `GatewayVocabulary`: догадки о `type`, `status`, `action=pause`, `mine=owner`, формате `planned_*`, статусе участника | Словаря нет. Строки `type`, `status`, `planned_*` есть в DTO, но не читаются: `Project.type`, `status`, `period` всегда `null` (G40, G41, G43) | Правило «без догадок» из addendum |
| 2 | В `ProjectStatus` добавлялись Started, Completed, Cancelled и баннеры 07c для них | Enum не меняется | Статусов с сервера нет, заполнять новые значения нечем |
| 3 | В `ProjectsApi` были `create`, `list(mine)`, `transition` | Остаются только `get` и `addRole`. Флаги `ProjectsCapabilities(false, false, false)`. Заблокированные операции бросают `FeatureUnavailableException` | G40 и G43, G44, G42 |
| 4 | `Flow<List<Project>?>` + `refreshMyProjects`, `PublishFailure.Unconfirmed`, `UnpublishProjectResult.Conflict` | Ничего из этого не вводится, `observeMyProjects()` остаётся прежним | Всё это было для вызовов, которых нет |
| 5 | 409 в `addRole`: проверить, не добавилась ли роль, и повторить один раз | Перечитать проект и бросить `ProjectConflictException`. Автоповтора нет | Ключей идемпотентности нет (G48). Автоповтор после потерянного ответа мог бы добавить роль дважды. Перечитанный проект и так показывает результат |
| 6 | `assemble(projects, withMembers)`, участники загружались через `profiles(memberIds)` | `assemble(projects)`. `Role.members` пуст, занятость берётся из `occupied` сервера | У `ProjectMember.status` нет перечня значений (новый G52) |
| 7 | Удалить `ProjectDuration` и `durations`, тип отправлять, если выбран ровно один | Домен остаётся. Группы «Тип» и «Сроки» скрыты флагами `ProjectSearchCapabilities`, `types` и `durations` не отправляются | Закрыть пробел = поменять флаг |
| 8 | `ProjectQueryParams(q, type, field, tags, mine)` | `ProjectSearchParams(q, field, tags)` | Неопределённый параметр нельзя отправить: его нет в типе |
| 9 | Все `Mock…DataSource` остаются | Удаляются вместе с `MockProjectCatalog` и `MockPeople(Seed)`. В DI только Gateway, без `when (backend)` | Правило «заменил мок — удали мок» |
| 10 | Отбрасывается проект без `id` | Отбрасывается и проект без `owner_user_id` | Пустой id даёт маршрут `profile/`, который не совпадает с `profile/{userId}`, и приложение падает |
| 11 | 07 открывается из «Мои проекты» и после создания | В Gateway 07, 07b и 07c недостижимы (06 и 05 выключены). Код работает через `loadProject` | Addendum разрешает, это помечено |
| 12 | Агенты работают по очереди | Агенты работают параллельно, синхронизация через маркер `p45.domain-ready` | Требование пользователя |

### 0.1 Что берётся из блока 3

Считаю, что блок 3 уже в дереве.

- **`pageOf`** в `/Users/samvel/Desktop/Study/Methodology/AcademicNet/gateway/src/commonMain/kotlin/org/matchlab/academic_net/gateway/mapping/PageMapping.kt`. Если файла нет, domain-data создаёт его ровно так:
  ```kotlin
  /**
   * One page of a server list: [items] the app could read out of [received] records the server sent. The total is the
   * server's [reportedTotal], never less than `request.offset + received`, cut to `Int.MAX_VALUE`.
   */
  fun <T> pageOf(items: List<T>, received: Int, reportedTotal: Long, request: PageRequest): Page<T>
  ```
- **Повторяющийся параметр запроса** (`skill=…&skill=…` в блоке 3, здесь `tag=…&tag=…`). Используется то, что блок 3 добавил в `ApiRequests`. Если ничего нет, domain-data добавляет в `ApiRequests.call` и `ApiRequests.get` последним параметром перед `parse` `repeated: Map<String, List<String>> = emptyMap()`: каждое значение уходит отдельным `parameter(name, value)`, порядок сохраняется. Существующие вызовы не меняются.
- **Протокол страниц во ViewModel** — как в `CollaboratorSearchViewModel` блока 3 (раздел 3.4 прежнего проекта). compose-ui повторяет его, а не придумывает свой.
- Уже есть в коде: `Page`, `PageRequest`, `PagedList` в `:common/paging`; `LoadMoreState`, `LoadMoreFooter`, `LoadMoreEffect` в `:components/paging`; `nameOrPlaceholder`, `failureText`, `InfoBanner`, `UserDirectory`, `toUserSummary()`, `ratingOrNull()`.

## 1. Как блок работает

| Экран | Что делает пользователь | Что уходит на сервер и приходит | Что отключено пробелами и как это видно |
|---|---|---|---|
| 02 Поиск | Вводит текст (поиск через 300 мс после последней клавиши), жмёт «поиск», «Применить», листает | `GET /search/projects?limit=20&offset=…[&q][&field][&tag…]`. Затем профили владельцев: `GET /users/{id}/profile` через `UserDirectory`, не больше 4 одновременно, кэш 5 минут (G47) | Нет чипов типа и статуса и нет сроков (G40, G41, G43). На карточке: название, автор («Имя · вуз», для своего проекта «Вы инициатор»), чип сферы, «N ролей». «Найдено N» — из `total`. Внизу футер догрузки |
| 03 Фильтры | Выбирает сферу из клиентского списка из 12 (G46) и ключевые слова (уходят как `tag`) | — | Группы «Тип проекта» (G40) и «Сроки» (G45) скрыты |
| 04 Проект | Открывает карточку из 02 | `GET /projects/{id}` и профиль владельца | Нет чипов типа и статуса, нет карточки сроков. 400, 403, 404 — «Проект не найден». Роли: «N из M занято» считает места; у роли на несколько мест строка «N из M мест занято». Чат с инициатором локальный (G50). Свой проект — только чтение, без чата |
| 06 Мои проекты | Вкладка «Проекты» | Ничего | «Я инициатор»: сообщение «Список ваших проектов пока недоступен на сервере», пояснение «Создание проектов пока недоступно на сервере», кнопки создания нет (G44, G40, G43). «Я участник» — прежняя заглушка |
| 05 Создание | В Gateway недостижим | — | Если всё же открыт, «Опубликовать» показывает «Создание проектов пока недоступно на сервере», запрос не уходит |
| 07, 07b, 07c | В Gateway недостижимы (вход только из 06 и 05) | `GET /projects/{id}`; `POST /projects/{id}/roles` с `slots=1` (G49) и `expected_revision` | 409 — проект перечитывается, в шите роли текст о конфликте. Участники не показываются (G52). В 07c нет «Снять с публикации» (G42), вместо этого баннер «Сервер пока не сообщает статус проекта…» (G41) |

## 2. Контракты

### 2.1 `:common` (domain-data, часть domain-ready)

**`/Users/samvel/Desktop/Study/Methodology/AcademicNet/common/src/commonMain/kotlin/org/matchlab/academic_net/common/model/Project.kt`** — замена файла целиком:

```kotlin
package org.matchlab.academic_net.common.model

import kotlinx.datetime.YearMonth

/**
 * Field of science or practice a project belongs to ("Computer Science", "Биология", ...).
 *
 * API-GAP G46: the API names a field by its text (`field` of a project and of the search) and has no list of fields,
 * so a sphere read from the server or picked from the app's list has that text as both [id] and [name].
 */
data class Sphere(
    val id: String,
    val name: String,
)

/** The kind of a project. API-GAP G40: the server's `type` has no list of values, so the Gateway never gives one. */
enum class ProjectType {
    /** "Научный": research, ends with a publication. */
    Scientific,

    /** "Проектный": practical work, ends with a product. */
    Practical,
}

/** Where a project is in its life. API-GAP G41: the server's `status` has no list of values, so the Gateway never gives one. */
enum class ProjectStatus {
    /** Published and looking for people for its vacant roles. */
    Recruiting,

    /** Taken off publication by the initiator: hidden from search, responses are paused. */
    Unpublished,
}

/** Person who works on a [Role], with the numbers shown on the project's role list. */
data class RoleMember(
    val user: UserSummary,
    /** Average of reviews, `null` while there are none. */
    val rating: Double? = null,
    val completedProjects: Int = 0,
)

/**
 * A position in the project's team: [slots] places, [occupied] of them taken.
 *
 * @property slots how many people the role is for, at least 1. The app creates roles for one person (API-GAP G49).
 * @property members the people on the role the app knows. API-GAP G52: the server's members are not read, so on the
 *  Gateway the list is empty even when [occupied] is more than 0.
 * @property occupied places taken as the server counts them; never negative, may be more than [members].size.
 */
data class Role(
    val id: RoleId,
    val title: String,
    val skills: List<String> = emptyList(),
    val slots: Int = 1,
    val members: List<RoleMember> = emptyList(),
    val occupied: Int = members.size,
) {
    init {
        require(slots >= 1) { "A role is for at least one person: slots = $slots" }
        require(occupied >= 0) { "Taken places cannot be negative: occupied = $occupied" }
    }

    /** A place is free: the initiator may look for people for the role. */
    val isVacant: Boolean get() = occupied < slots

    /** Free places; 0 when the server counts as many taken places as there are, or more. */
    val vacantPlaces: Int get() = (slots - occupied).coerceAtLeast(0)
}

/** The months a project runs, from [start] to [end] inclusive; [end] is not before [start]. */
data class ProjectPeriod(
    val start: YearMonth,
    val end: YearMonth,
)

/**
 * A project as the app shows it. What the API does not define is `null` ("unknown"), never a guess: the screens
 * leave out what they cannot show.
 *
 * @property type API-GAP G40: `null` when unknown, which is always so on the Gateway.
 * @property status API-GAP G41: `null` when unknown, always so on the Gateway; no management action is offered then.
 * @property period API-GAP G43: `null` when unknown, always so on the Gateway (the format of `planned_*` is not defined).
 * @property viewerIsInitiator the user who asked is the project's owner (`owner_user_id`). Only the initiator manages
 *  the project and looks for people for its roles.
 */
data class Project(
    val id: ProjectId,
    val title: String,
    val type: ProjectType?,
    val sphere: Sphere,
    /** What problem the project solves. */
    val problem: String,
    /** What the project should produce. */
    val goal: String,
    val tags: List<String>,
    val initiator: UserSummary,
    val roles: List<Role>,
    val status: ProjectStatus?,
    val period: ProjectPeriod?,
    val viewerIsInitiator: Boolean,
) {
    /** The roles with a free place, in the order of [roles]. */
    val vacantRoles: List<Role> get() = roles.filter { it.isVacant }

    /** Taken places of all roles, a role counted up to its [Role.slots]: the "2" of "2 из 4 занято". */
    val occupiedPlaces: Int get() = roles.sumOf { minOf(it.occupied, it.slots) }

    /** Places of all roles: the "4" of "2 из 4 занято". */
    val totalPlaces: Int get() = roles.sumOf { it.slots }
}
```

`Role.member`, `Role.isVacant` в старом смысле и `Project.occupiedRolesCount` удалены.

**`/Users/samvel/Desktop/Study/Methodology/AcademicNet/common/src/commonMain/kotlin/org/matchlab/academic_net/common/result/FeatureUnavailableException.kt`** — новый файл:

```kotlin
package org.matchlab.academic_net.common.result

/**
 * The server the app talks to cannot do this yet, because its API leaves out what the operation needs (values of a
 * field, a format, a call). [gap] is the id of the gap in `docs/api-gaps.md`, e.g. "G40".
 *
 * The screens do not get here in the normal course: they read the capability flags of their feature (`…Capabilities`)
 * and hide or switch off the action with a word of explanation. A data source throws this when it is asked anyway,
 * instead of sending a request on the off chance. It is not a [RemoteFailure]: nothing was sent, and asking again does
 * not help until the gap is closed.
 */
class FeatureUnavailableException(val gap: String) :
    UnsupportedOperationException("Not available on this server: API gap $gap")
```

### 2.2 `:gateway` (domain-data)

**`/Users/samvel/Desktop/Study/Methodology/AcademicNet/gateway/src/commonMain/kotlin/org/matchlab/academic_net/gateway/ProjectsApi.kt`** — новый файл:

```kotlin
package org.matchlab.academic_net.gateway

import org.matchlab.academic_net.gateway.dto.ProjectDto
import org.matchlab.academic_net.gateway.dto.ProjectRoleInputDto

/**
 * The calls of the viewer's own projects (07, 07b). Every call throws `RemoteFailure` when it fails and lets a
 * `CancellationException` through, like [AccountApi].
 *
 * Only what the API defines is here. API-GAP G40, G43: no `POST /projects` (its required `type` has no list of values,
 * `planned_*` no format). API-GAP G44: no `GET /projects?mine=`. API-GAP G42: no `POST /projects/{id}/transitions`.
 */
interface ProjectsApi {
    /** `GET /api/v1/projects/{id}` → 200. [id] goes into the path escaped. */
    suspend fun get(id: String): ProjectDto

    /**
     * `POST /api/v1/projects/{id}/roles` → 200: the whole project with the new role. A stale
     * [ProjectRoleInputDto.expectedRevision] is answered with a 409.
     */
    suspend fun addRole(id: String, input: ProjectRoleInputDto): ProjectDto
}
```

**`/Users/samvel/Desktop/Study/Methodology/AcademicNet/gateway/src/commonMain/kotlin/org/matchlab/academic_net/gateway/ProjectCatalogApi.kt`** — новый файл:

```kotlin
package org.matchlab.academic_net.gateway

import org.matchlab.academic_net.common.paging.PageRequest
import org.matchlab.academic_net.gateway.dto.ProjectDto
import org.matchlab.academic_net.gateway.dto.ProjectListDto

/**
 * Everybody's projects, as the search (02, 03) and the page of a project (04) read them. Every call throws
 * `RemoteFailure` when it fails and lets a `CancellationException` through, like [AccountApi].
 */
interface ProjectCatalogApi {
    /**
     * `GET /api/v1/search/projects`: `limit` and `offset` of [page] always, then what [params] has.
     *
     * Never sent, because the API does not define their values: `type` (API-GAP G40), `status` (G41), `mine` and
     * `profile_user_id` (G44), `duration` (G45).
     */
    suspend fun search(params: ProjectSearchParams, page: PageRequest): ProjectListDto

    /** `GET /api/v1/projects/{id}` → 200. [id] goes into the path escaped. */
    suspend fun get(id: String): ProjectDto
}

/**
 * What the project search sends besides the page. `null`, blank strings and blank tags are not sent.
 *
 * @property q the search text as the user typed it (API-GAP G53: what the server matches it against is not described).
 * @property field the name of a field of science, as text (API-GAP G46).
 * @property tags `tag=…&tag=…`, in this order (API-GAP G53: "all of them" or "any of them" is not described).
 */
data class ProjectSearchParams(
    val q: String? = null,
    val field: String? = null,
    val tags: List<String> = emptyList(),
)
```

**`/Users/samvel/Desktop/Study/Methodology/AcademicNet/gateway/src/commonMain/kotlin/org/matchlab/academic_net/gateway/dto/ProjectDtos.kt`** — новый файл:

```kotlin
package org.matchlab.academic_net.gateway.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// The shapes of the project calls of docs/openapi.yaml (schemas Project, ProjectInput, ProjectRole, ProjectList,
// ProjectRoleInput): only what the app reads or sends, the client's Json skips the rest. The strings without a list
// of values or a format (`type`, `status`, `planned_start`, `planned_end`) stay strings nothing parses
// (API-GAP G40, G41, G43).

/** `ProjectInput` as the `details` of [ProjectDto]: what the owner wrote about the project. */
@Serializable
data class ProjectInputDto(
    val title: String = "",
    /** API-GAP G40: no list of values; not read. */
    val type: String = "",
    /** API-GAP G46: the field of science as text. */
    val field: String = "",
    val problem: String = "",
    val goal: String = "",
    val tags: List<String> = emptyList(),
    /** API-GAP G43: no format; not read. */
    @SerialName("planned_start") val plannedStart: String = "",
    /** API-GAP G43: no format; not read. */
    @SerialName("planned_end") val plannedEnd: String = "",
)

/** `ProjectRole`: a position for [slots] people, [occupied] of them taken; `0` when the server left a number out. */
@Serializable
data class ProjectRoleDto(
    val id: String = "",
    val title: String = "",
    val skills: List<String> = emptyList(),
    val slots: Int = 0,
    val occupied: Int = 0,
)

/** `Project`. [revision] is the version a change sends back as `expected_revision`. */
@Serializable
data class ProjectDto(
    val id: String = "",
    @SerialName("owner_user_id") val ownerUserId: String = "",
    val details: ProjectInputDto = ProjectInputDto(),
    /** API-GAP G41: no list of values; not read. */
    val status: String = "",
    val revision: Int = 0,
    val roles: List<ProjectRoleDto> = emptyList(),
)

/** `ProjectList`: one page and the size of the whole list (int64, a string or a number). */
@Serializable
data class ProjectListDto(
    val items: List<ProjectDto> = emptyList(),
    @Serializable(with = FlexibleLongSerializer::class) val total: Long = 0,
)

/** `ProjectRoleInput`, the body of `POST /projects/{id}/roles`; [title], [slots], [expectedRevision] are required. */
@Serializable
data class ProjectRoleInputDto(
    val title: String,
    val slots: Int,
    @SerialName("expected_revision") val expectedRevision: Int,
    val skills: List<String> = emptyList(),
    val description: String? = null,
)
```

**`/Users/samvel/Desktop/Study/Methodology/AcademicNet/gateway/src/commonMain/kotlin/org/matchlab/academic_net/gateway/mapping/ProjectMapping.kt`** — новый файл, публичный: им пользуются фейки в тестах фич.

```kotlin
package org.matchlab.academic_net.gateway.mapping

import org.matchlab.academic_net.common.model.Project
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.Role
import org.matchlab.academic_net.common.model.RoleId
import org.matchlab.academic_net.common.model.Sphere
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.model.UserSummary
import org.matchlab.academic_net.gateway.dto.ProjectDto
import org.matchlab.academic_net.gateway.dto.ProjectRoleDto
import org.matchlab.academic_net.gateway.dto.UserProfileDto

/**
 * The project as the app shows it, or `null` when it cannot be shown: the server sent no `id` or no `owner_user_id`.
 *
 * [owner] is the owner's public profile; without one the initiator is the owner's id with an empty name (the UI
 * writes "Без имени"). Type, status and months are `null` (API-GAP G40, G41, G43); the sphere is the `field` text
 * (G46); roles come from the top-level `roles`, a role without an id is dropped, `slots` below 1 is 1 (G49), members
 * are not read (G52). [viewerId] tells the viewer's own projects.
 */
fun ProjectDto.toProject(viewerId: UserId, owner: UserProfileDto?): Project? {
    if (id.isBlank() || ownerUserId.isBlank()) return null
    val field = details.field.trim()
    val ownerId = UserId(ownerUserId)
    return Project(
        id = ProjectId(id),
        title = details.title.trim(),
        type = null, // API-GAP G40: `type` has no list of values
        sphere = Sphere(id = field, name = field), // API-GAP G46
        problem = details.problem.trim(),
        goal = details.goal.trim(),
        tags = details.tags.cleaned(),
        initiator = owner?.toUserSummary()?.copy(id = ownerId) ?: UserSummary(ownerId, name = ""),
        roles = roles.mapNotNull { it.toRole() },
        status = null, // API-GAP G41: `status` has no list of values
        period = null, // API-GAP G43: `planned_*` have no format
        viewerIsInitiator = ownerUserId == viewerId.value,
    )
}

private fun ProjectRoleDto.toRole(): Role? =
    if (id.isBlank()) {
        null
    } else {
        Role(
            id = RoleId(id),
            title = title.trim(),
            skills = skills.cleaned(),
            slots = slots.coerceAtLeast(1), // API-GAP G49
            members = emptyList(), // API-GAP G52: ProjectMember.status has no list of values
            occupied = occupied.coerceAtLeast(0),
        )
    }

private fun List<String>.cleaned(): List<String> = map(String::trim).filter(String::isNotEmpty)
```

**`/Users/samvel/Desktop/Study/Methodology/AcademicNet/gateway/src/commonMain/kotlin/org/matchlab/academic_net/gateway/mapping/ProjectAssembler.kt`** — новый файл:

```kotlin
package org.matchlab.academic_net.gateway.mapping

import org.matchlab.academic_net.common.model.Project
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.gateway.UserDirectory
import org.matchlab.academic_net.gateway.dto.ProjectDto
import org.matchlab.academic_net.gateway.dto.UserProfileDto

/** A project as the app shows it, with the public profile of its owner (`null`: none the viewer may see). */
data class AssembledProject(
    val project: Project,
    val owner: UserProfileDto?,
)

/**
 * Turns the server's projects into the app's. API-GAP G47: a project names its owner by id only, so the owners are
 * looked up separately. Shared by the projects, the search and the page of a project; their data layers are tested
 * with a fake of it.
 */
fun interface ProjectAssembler {
    /**
     * The readable [projects] (see [toProject]) in their order, with their owners. The owners are asked for together,
     * each distinct id once; a failure of that request, other than "no such person", is thrown.
     */
    suspend fun assemble(projects: List<ProjectDto>): List<AssembledProject>
}

/** The [ProjectAssembler] of a session: owners come from [users]; [viewerId] tells the viewer's own projects. */
class DirectoryProjectAssembler(
    private val users: UserDirectory,
    private val viewerId: UserId,
) : ProjectAssembler {
    override suspend fun assemble(projects: List<ProjectDto>): List<AssembledProject> {
        val readable = projects.filter { it.id.isNotBlank() && it.ownerUserId.isNotBlank() }
        if (readable.isEmpty()) return emptyList()
        val owners = users.profiles(readable.map { it.ownerUserId })
        return readable.mapNotNull { dto ->
            val owner = owners[dto.ownerUserId]
            dto.toProject(viewerId, owner)?.let { AssembledProject(it, owner) }
        }
    }
}
```

**Изменение `/Users/samvel/Desktop/Study/Methodology/AcademicNet/gateway/src/commonMain/kotlin/org/matchlab/academic_net/gateway/GatewaySession.kt`** (KDoc класса тоже обновить):

```kotlin
    // One Ktor class serves both interfaces: the projects and the page of a project read the same GET /projects/{id}.
    private val projectCalls = KtorProjectsApi(requests)

    /** The viewer's own projects: reading one and adding a role to it. */
    val projects: ProjectsApi = projectCalls

    /** Everybody's projects: the search and the page of a project. */
    val projectCatalog: ProjectCatalogApi = projectCalls
```

**Внутреннее (`internal`):** `/Users/samvel/Desktop/Study/Methodology/AcademicNet/gateway/src/commonMain/kotlin/org/matchlab/academic_net/gateway/internal/KtorProjectsApi.kt`:
- `internal class KtorProjectsApi(requests: ApiRequests) : ProjectsApi, ProjectCatalogApi`, один `override suspend fun get(id)` на оба интерфейса;
- `get` — `GET projects/${pathSegment(id)}`;
- `addRole` — `POST projects/${pathSegment(id)}/roles`, тело `ProjectRoleInputDto`;
- `search` — `GET search/projects`. `limit` и `offset` уходят всегда. `q` и `field` — только непустые. `tag` повторяется для каждого непустого тега в исходном порядке. Ничего другого не отправляется.

### 2.3 `feature:projects:domain` (domain-data, часть domain-ready)

Каталог: `/Users/samvel/Desktop/Study/Methodology/AcademicNet/feature/projects/domain/src/commonMain/kotlin/org/matchlab/academic_net/feature/projects/domain/`.

**`ProjectsCapabilities.kt`** — новый файл:

```kotlin
package org.matchlab.academic_net.feature.projects.domain

/**
 * What the projects feature can do with the server it talks to. The screens read the flags and hide, or switch off
 * with a word of explanation, what the server cannot do, instead of showing made-up data. Every flag is closed by
 * gaps of `docs/api-gaps.md`: when the backend fills them, the data source sets the flag and implements the call.
 *
 * @property canCreate API-GAP G40, G43: `POST /projects` requires `type` (no list of values) and `planned_start` /
 *  `planned_end` (no format). When `false` there is no "Создать проект", and [ProjectsRepository.publishProject] throws
 *  `FeatureUnavailableException`.
 * @property canListMine API-GAP G44: `mine` of `GET /projects` has no list of values. When `false` "Я инициатор" shows
 *  a message and asks nothing; [ProjectsRepository.observeMyProjects] fails with `FeatureUnavailableException`.
 * @property canUnpublish API-GAP G42 (and G41): `action` of the transitions has no list of values, and the status of a
 *  project is unknown. When `false` 07c has no "Снять с публикации"; [ProjectsRepository.unpublishProject] throws
 *  `FeatureUnavailableException`.
 */
data class ProjectsCapabilities(
    val canCreate: Boolean,
    val canListMine: Boolean,
    val canUnpublish: Boolean,
)
```

**`ProjectsRepository.kt`** — замена файла целиком:

```kotlin
package org.matchlab.academic_net.feature.projects.domain

import kotlinx.coroutines.flow.Flow
import org.matchlab.academic_net.common.model.Project
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.Role
import org.matchlab.academic_net.common.model.Sphere

/** Requested project does not exist (or the user is not allowed to see it). */
class ProjectNotFoundException(val projectId: ProjectId) :
    NoSuchElementException("Project ${projectId.value} not found")

/**
 * The server refused a change because the project had changed since it was read (a 409). By the time this is thrown
 * the project has been read again where possible, so [ProjectsRepository.observeProject] shows what the server has:
 * the user checks and tries again.
 */
class ProjectConflictException(val projectId: ProjectId) :
    IllegalStateException("Project ${projectId.value} changed on the server")

/**
 * Projects of the current user as their initiator. Suspending functions throw on failure (no network, a server
 * error, [org.matchlab.academic_net.common.result.FeatureUnavailableException] for what [capabilities] switch off);
 * the use cases turn that into results.
 */
interface ProjectsRepository {
    /** What the server behind the projects can do; the screens hide the rest. */
    val capabilities: ProjectsCapabilities

    /** The viewer's projects, newest first. API-GAP G44: fails unless [ProjectsCapabilities.canListMine]. */
    fun observeMyProjects(): Flow<List<Project>>

    /**
     * The project as last read or changed in this session: `null` until [loadProject] has read it, and after the
     * server said there is no such project. Never fails.
     */
    fun observeProject(id: ProjectId): Flow<Project?>

    /** Reads the project from the server into [observeProject]; `null` when the viewer may not see such a project. */
    suspend fun loadProject(id: ProjectId): Project?

    /** The project as last read in this session, else as [loadProject] reads it. */
    suspend fun getProject(id: ProjectId): Project?

    /** Creates the project already published. API-GAP G40, G43: only with [ProjectsCapabilities.canCreate]. */
    suspend fun publishProject(project: NewProject): Project

    /**
     * Hides the project from search. API-GAP G42: only with [ProjectsCapabilities.canUnpublish].
     * @throws ProjectNotFoundException
     */
    suspend fun unpublishProject(id: ProjectId): Project

    /**
     * Adds a vacant role for one person to the project (API-GAP G49).
     * @throws ProjectNotFoundException the project is gone (404)
     * @throws ProjectConflictException the project changed meanwhile (409); it has been read again
     */
    suspend fun addRole(projectId: ProjectId, role: RoleDraft): Role

    /** The spheres a project can belong to: the app's list (API-GAP G46). */
    suspend fun getSpheres(): List<Sphere>
}
```

**`usecase/LoadProject.kt`** — новый файл:

```kotlin
package org.matchlab.academic_net.feature.projects.domain.usecase

import org.matchlab.academic_net.common.model.Project
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.result.LoadResult
import org.matchlab.academic_net.feature.projects.domain.ProjectsRepository
import org.matchlab.academic_net.utils.suspendRunCatching

/**
 * "Просматривать роли" (07): reads one project from the server. Its value also comes through
 * [ObserveProjectUseCase]; a cancellation of the caller is rethrown, not a failure.
 */
class LoadProjectUseCase(private val repository: ProjectsRepository) {
    suspend operator fun invoke(id: ProjectId): LoadResult<Project> =
        suspendRunCatching { repository.loadProject(id) }.fold(
            onSuccess = { project -> if (project == null) LoadResult.NotFound else LoadResult.Found(project) },
            onFailure = { LoadResult.Failure(it) },
        )
}
```

**Без изменения сигнатур** (меняется только KDoc): `ObserveMyProjectsUseCase` (G44), `ObserveProjectUseCase` (новый смысл `null`), `PublishProjectUseCase`, `UnpublishProjectUseCase` с `UnpublishProjectResult`, `AddRoleUseCase` (конфликт приходит как `SubmitResult.Failure(ProjectConflictException)`), `GetSpheresUseCase`, `ProjectDraft`, `RoleDraft`, `NewProject`.

### 2.4 `feature:projectsearch:domain` (domain-data, часть domain-ready)

Каталог: `/Users/samvel/Desktop/Study/Methodology/AcademicNet/feature/projectsearch/domain/src/commonMain/kotlin/org/matchlab/academic_net/feature/projectsearch/domain/`.

**`ProjectSearchCapabilities.kt`** — новый файл:

```kotlin
package org.matchlab.academic_net.feature.projectsearch.domain

/**
 * What the project search can ask the server it talks to; the filters sheet hides the rest. Closing a gap of
 * `docs/api-gaps.md` is setting its flag in the data source and sending the parameter.
 *
 * @property typeFilter API-GAP G40: `type` has no list of values. When `false` the "Тип проекта" group of 03 is hidden
 *  and [ProjectFilters.types] is never sent.
 * @property durationFilter API-GAP G45: `duration` has no list of values. When `false` the "Сроки" group of 03 is
 *  hidden and [ProjectFilters.durations] is never sent.
 */
data class ProjectSearchCapabilities(
    val typeFilter: Boolean,
    val durationFilter: Boolean,
)
```

**`ProjectSearchRepository.kt`** — замена файла целиком:

```kotlin
package org.matchlab.academic_net.feature.projectsearch.domain

import org.matchlab.academic_net.common.model.Sphere
import org.matchlab.academic_net.common.paging.Page
import org.matchlab.academic_net.common.paging.PageRequest

/** Search of published projects; the server matches and orders them. The call throws on failure (e.g. no network). */
interface ProjectSearchRepository {
    /** What the server can filter by. */
    val capabilities: ProjectSearchCapabilities

    /**
     * One [page] of the projects the server finds for [query], in the server's order. API-GAP G53: the order, its
     * stability between pages and what the search includes (the viewer's own projects, other statuses) are the
     * server's and are not described.
     */
    suspend fun search(query: ProjectQuery, page: PageRequest): Page<FoundProject>
}

/**
 * The spheres the sphere filter offers. They belong to the projects feature, which implements this where the app is
 * assembled, so the two features do not depend on each other.
 */
interface SphereProvider {
    /** Every sphere a project can belong to, in the order the filter shows them. */
    suspend fun getSpheres(): List<Sphere>
}
```

**`ProjectSearchUseCases.kt`** — замена файла целиком:

```kotlin
package org.matchlab.academic_net.feature.projectsearch.domain

import org.matchlab.academic_net.common.model.Sphere
import org.matchlab.academic_net.common.paging.Page
import org.matchlab.academic_net.common.paging.PageRequest
import org.matchlab.academic_net.utils.suspendRunCatching

/** "Найти подходящий проект": one page of the server's answer; the search itself is the server's. */
class SearchProjectsUseCase(private val repository: ProjectSearchRepository) {
    suspend operator fun invoke(query: ProjectQuery, page: PageRequest): Result<Page<FoundProject>> =
        suspendRunCatching { repository.search(query, page) }
}

/** The spheres to pick from in the filters sheet. */
class GetSearchSpheresUseCase(private val provider: SphereProvider) {
    suspend operator fun invoke(): Result<List<Sphere>> =
        suspendRunCatching { provider.getSpheres() }
}
```

**`ProjectSearch.kt`** — меняется только KDoc, типы и поля те же:
- `ProjectDuration` и `ProjectFilters.durations`: «API-GAP G45: offered only with `ProjectSearchCapabilities.durationFilter`»;
- `types`: «API-GAP G40: only with `typeFilter`»;
- `sphere`: «the server is sent `Sphere.name` as `field` (API-GAP G46)»;
- `keywords`: «sent as tags, `tag=…`»;
- `ProjectQuery.text`: «as typed; API-GAP G53»;
- `FoundProject.initiatorUniversity`: «the university of the owner's profile; blank when unknown».

### 2.5 `feature:projectdetails:domain`

Изменений нет. `ProjectDetails(project, initiator)`, `InitiatorInfo(university, rating?, completedProjects)`, `ProjectDetailsRepository.getProjectDetails(id): ProjectDetails?`, `GetProjectDetailsUseCase` с `ProjectDetailsResult` (Found, NotFound, Failure(cause)) остаются как есть.

### 2.6 data-модули (domain-data)

`build.gradle.kts` всех трёх модулей: убрать `api(project(":mockserver"))` или `implementation(project(":mockserver"))`, добавить `implementation(project(":gateway"))` с комментарием, как в profile-data. Обновить шапочный комментарий.

**projects-data** — каталог `/Users/samvel/Desktop/Study/Methodology/AcademicNet/feature/projects/data/src/commonMain/kotlin/org/matchlab/academic_net/feature/projects/data/`.

```kotlin
/** Where the viewer's projects come from: the Gateway ([GatewayProjectsDataSource]). Mirrors ProjectsRepository. */
interface ProjectsDataSource {
    val capabilities: ProjectsCapabilities
    fun observeMyProjects(): Flow<List<Project>>
    fun observeProject(id: ProjectId): Flow<Project?>
    suspend fun loadProject(id: ProjectId): Project?
    suspend fun getProject(id: ProjectId): Project?
    suspend fun createProject(project: NewProject): Project
    suspend fun unpublishProject(id: ProjectId): Project
    suspend fun addRole(projectId: ProjectId, role: RoleDraft): Role
    suspend fun getSpheres(): List<Sphere>
}

/** Passes everything to the source as it is; `publishProject` is `createProject`. */
class ProjectsRepositoryImpl(private val dataSource: ProjectsDataSource) : ProjectsRepository

class GatewayProjectsDataSource(
    private val api: ProjectsApi,
    private val assembler: ProjectAssembler,
) : ProjectsDataSource

/** API-GAP G46: the client's list of 12 spheres, `id == name`. */
internal object SphereCatalog { val spheres: List<Sphere> }
// "Computer Science", "Data Science", "Инженерия", "Математика", "Физика", "Химия",
// "Биология", "Медицина", "Экономика", "Социология", "Лингвистика", "Дизайн" -> Sphere(it, it)
```

Как работает `GatewayProjectsDataSource`:
- **Кэш.** `MutableStateFlow<Map<ProjectId, Entry(project, revision)>>`. Новая запись не заменяет запись с большей ревизией.
- **Сериализация записей.** Все записи (сейчас это только `addRole`) идут под одним `Mutex`.
- **Флаги.** `capabilities = ProjectsCapabilities(canCreate = false, canListMine = false, canUnpublish = false)`.

| Метод | Поведение |
|---|---|
| `observeMyProjects` | `flow { throw FeatureUnavailableException("G44") }` (пометка `// API-GAP G44`) |
| `observeProject(id)` | `cache.map { it[id]?.project }.distinctUntilChanged()` |
| `loadProject(id)` | `api.get(id.value)` → `assembler.assemble(listOf(dto)).firstOrNull()` → кэш, вернуть проект. `Http` 400, 403, 404 или нечитаемый ответ → убрать из кэша, вернуть `null`. Прочее пробрасывается, кэш не трогается |
| `getProject(id)` | Есть в кэше — вернуть без запроса, иначе `loadProject(id)` |
| `createProject` | `throw FeatureUnavailableException("G40")` (`// API-GAP G40, G43`) |
| `unpublishProject` | `throw FeatureUnavailableException("G42")` |
| `addRole(projectId, role)` | Ревизия из кэша; если проекта нет, сначала `loadProject` (`null` → `ProjectNotFoundException`). Тело: `ProjectRoleInputDto(title = role.title, slots = 1, expectedRevision = revision, skills = role.skills)`. На 200: собрать проект, положить в кэш. Новая роль — та, чьего id не было в кэше до запроса; если таких несколько, берётся последняя с тем же названием; если ни одной — `RemoteFailure.Malformed()`. 404 → убрать из кэша, `ProjectNotFoundException`. 409 → `loadProject(projectId)`, затем `ProjectConflictException(projectId)`; сбой перечитывания добавляется к нему как suppressed. Остальное пробрасывается |
| `getSpheres` | `SphereCatalog.spheres`, без запроса |

**projectsearch-data** — каталог `/Users/samvel/Desktop/Study/Methodology/AcademicNet/feature/projectsearch/data/src/commonMain/kotlin/org/matchlab/academic_net/feature/projectsearch/data/`.

```kotlin
interface ProjectSearchDataSource {
    val capabilities: ProjectSearchCapabilities
    suspend fun search(query: ProjectQuery, page: PageRequest): Page<FoundProject>
}

class ProjectSearchRepositoryImpl(private val dataSource: ProjectSearchDataSource) : ProjectSearchRepository

class GatewayProjectSearchDataSource(
    private val api: ProjectCatalogApi,
    private val assembler: ProjectAssembler,
) : ProjectSearchDataSource {
    override val capabilities = ProjectSearchCapabilities(typeFilter = false, durationFilter = false)

    override suspend fun search(query: ProjectQuery, page: PageRequest): Page<FoundProject> {
        val answer = api.search(query.toParams(), page)
        val found = assembler.assemble(answer.items)
            .map { FoundProject(it.project, it.owner?.details?.university.orEmpty().trim()) }
        return pageOf(found, received = answer.items.size, reportedTotal = answer.total, request = page)
    }
}

// API-GAP G40, G45: filters.types and filters.durations are never sent.
internal fun ProjectQuery.toParams() = ProjectSearchParams(
    q = text.trim().ifEmpty { null },
    field = filters.sphere?.name?.trim()?.ifEmpty { null }, // API-GAP G46
    tags = filters.keywords.map(String::trim).filter(String::isNotEmpty),
)
```

Свои проекты на клиенте не отфильтровываются. Подпись «Вы инициатор» UI ставит по `project.viewerIsInitiator`.

**projectdetails-data** — каталог `/Users/samvel/Desktop/Study/Methodology/AcademicNet/feature/projectdetails/data/src/commonMain/kotlin/org/matchlab/academic_net/feature/projectdetails/data/`. `ProjectDetailsDataSource` и `ProjectDetailsRepositoryImpl` не меняются.

```kotlin
class GatewayProjectDetailsDataSource(
    private val api: ProjectCatalogApi,
    private val assembler: ProjectAssembler,
) : ProjectDetailsDataSource {
    // getProjectDetails: api.get(id.value); Http 400/403/404 -> null; assembler drops the project -> null;
    // ProjectDetails(assembled.project, assembled.owner.toInitiatorInfo()); other failures are thrown.
}

internal fun UserProfileDto?.toInitiatorInfo() = InitiatorInfo(
    university = this?.details?.university.orEmpty().trim(),
    rating = this?.ratingOrNull(), // API-GAP G20
    completedProjects = this?.completedProjectCount?.coerceAtLeast(0) ?: 0,
)
```

### 2.7 ui-модули (compose-ui): видимое за пределами модуля

**`/Users/samvel/Desktop/Study/Methodology/AcademicNet/feature/projects/ui/src/commonMain/kotlin/org/matchlab/academic_net/feature/projects/ui/ProjectsUiDependencies.kt`** — точный конструктор, его собирает DI:

```kotlin
package org.matchlab.academic_net.feature.projects.ui

import org.matchlab.academic_net.feature.projects.domain.ProjectsCapabilities
import org.matchlab.academic_net.feature.projects.domain.usecase.AddRoleUseCase
import org.matchlab.academic_net.feature.projects.domain.usecase.GetSpheresUseCase
import org.matchlab.academic_net.feature.projects.domain.usecase.LoadProjectUseCase
import org.matchlab.academic_net.feature.projects.domain.usecase.ObserveMyProjectsUseCase
import org.matchlab.academic_net.feature.projects.domain.usecase.ObserveProjectUseCase
import org.matchlab.academic_net.feature.projects.domain.usecase.PublishProjectUseCase
import org.matchlab.academic_net.feature.projects.domain.usecase.UnpublishProjectUseCase

/**
 * What the screens of the projects feature need from the outside; the app creates it once and hands it to every
 * `…Route`.
 *
 * @param capabilities what the server behind the projects can do (API-GAP G40-G44). Without
 *  [ProjectsCapabilities.canListMine] "Я инициатор" shows a message instead of the list, without
 *  [ProjectsCapabilities.canCreate] there is no "Создать проект", without [ProjectsCapabilities.canUnpublish] 07c has
 *  no "Снять с публикации".
 */
class ProjectsUiDependencies(
    val observeMyProjects: ObserveMyProjectsUseCase,
    val observeProject: ObserveProjectUseCase,
    val loadProject: LoadProjectUseCase,
    val publishProject: PublishProjectUseCase,
    val unpublishProject: UnpublishProjectUseCase,
    val addRole: AddRoleUseCase,
    val getSpheres: GetSpheresUseCase,
    val capabilities: ProjectsCapabilities,
)
```

**`/Users/samvel/Desktop/Study/Methodology/AcademicNet/feature/projectsearch/ui/src/commonMain/kotlin/org/matchlab/academic_net/feature/projectsearch/ui/ProjectSearchUiDependencies.kt`** — точный конструктор:

```kotlin
class ProjectSearchUiDependencies(
    val searchProjects: SearchProjectsUseCase,
    val getSpheres: GetSearchSpheresUseCase,
    /** API-GAP G40, G45: the "Тип проекта" and "Сроки" groups of 03 are shown only when the flags allow. */
    val capabilities: ProjectSearchCapabilities,
)
```

`ProjectDetailsUiDependencies(getProjectDetails)` не меняется.

**Сигнатуры Route не меняются**, поэтому `AppNavHost` не трогается:
- `MyProjectsRoute(dependencies, onCreateProject, onProjectClick, modifier)`;
- `CreateProjectRoute(dependencies, onClose, onPublished, modifier)`;
- `ProjectManagementRoute(dependencies, projectId, onNavigateBack, onFindCollaborators, modifier)`;
- `ProjectSearchRoute(dependencies, onNavigateBack, modifier, onProjectClick, autoFocusSearch)`;
- `ProjectDetailsRoute(dependencies, projectId, onNavigateBack, onInitiatorClick, onContactInitiator, modifier)`.

**Поведение экранов обязательно; имена внутренних классов и полей выбирает compose-ui.**

- **06.** `MyProjectsViewModel(observeMyProjects, capabilities)`.
  - При `!canListMine` `observeMyProjects()` не вызывается. Экран: `MessageState(title = projects_list_unavailable, text = projects_create_unavailable, если !canCreate)`.
  - FAB есть только при `canCreate`.
  - `FeatureUnavailableException` в потоке показывается так же, как недоступность, а не как «Не удалось загрузить».
- **05.** Если `PublishProjectUseCase` вернул `SubmitResult.Failure(FeatureUnavailableException)`, показывается `projects_create_unavailable`. Стражи `isPublishing` и `publishedProjectId` остаются.
- **07.** `ProjectManagementViewModel(projectId, observeProject, loadProject, unpublishProject, addRole, capabilities)`.
  - Загрузка:
    - `loadProject` вызывается при создании VM; проект из кэша показывается сразу;
    - `NotFound` — «Проект не найден»;
    - `Failure` при пустом экране — `MessageState(projects_load_project_error, text = failureText(cause), «Повторить»)`, повтор защищён своим `Job`.
  - Обзор: чипы типа и статуса только при известном значении, `ProjectPeriodCard` только при `period != null`.
  - **07b:**
    - заголовок `RolesSectionHeader(occupiedPlaces, totalPlaces)`;
    - участники — карточками из `role.members` (в Gateway их нет);
    - роль, у которой есть свободные места или участники неизвестны, — карточкой роли со строкой `rolePlacesText`, если `slots > 1` или роль не вакантна;
    - «Найти людей» — только при `viewerIsInitiator && isVacant`.
  - Шит роли:
    - `ProjectConflictException` — шит остаётся открытым, внутри `projects_conflict`;
    - прочий сбой — в шите `failureText(cause)`;
    - `ProjectNotFoundException` — шит закрывается, экран показывает «Проект не найден».
  - **07c:**
    - «Снять с публикации» — только при `capabilities.canUnpublish && status == Recruiting`;
    - при `status == null` баннер `projects_banner_status_unknown`.
  - Стражи `isSavingRole` и `isUnpublishing` остаются.
- **02 и 03.** `ProjectSearchViewModel(dependencies, pageSize: Int = PageRequest.DefaultLimit)`.
  - Протокол страниц:
    - новый поиск отменяет поиск и догрузку, увеличивает поколение и берёт `PageRequest(0, pageSize)`;
    - пока идёт новый поиск, догрузка выключена (`End`);
    - `onLoadMore` ничего не делает, если `next == null` или запрос уже идёт;
    - ответ чужого поколения отбрасывается;
    - склейка через `PagedList` по `project.id`.
  - «Найдено N» — `PagedList.total`.
  - Сбой первой страницы — `MessageState(projectsearch_error, text = failureText(cause), «Повторить»)`; сбой догрузки — `LoadMoreState.Failed`.
  - Экран: `LazyColumn` + `LoadMoreEffect` + `LoadMoreFooter`.
  - Флаги `typeFilter` и `durationFilter` попадают в состояние шита (так их можно проверить тестом); группы показываются только при `true`.
  - Строка автора: свой проект — `projectsearch_you_initiator`; чужой — непустые части `nameOrPlaceholder(name)` и вуза через « · ».
- **04.** Тип, статус и срок nullable. Заголовок ролей считает места. `RoleApplyCard` получает `occupied` и `slots`. Имя инициатора — через `nameOrPlaceholder`. Ошибка — `Error(cause)`: `projectdetails_error` + `failureText(cause)` + «Повторить».

### 2.8 `:components` (compose-ui; потребители — только модули compose-ui)

```kotlin
// ProjectCard.kt
data class ProjectCardUiModel(/* …as now… */ val type: String? /* null: no type chip (G40) */, /* …as now… */)

// project/ProjectLabels.kt
@Composable fun projectCardFooter(rolesCount: Int, period: ProjectPeriod?): String   // "4 роли" or "4 роли · окт 2026 — мар 2027"
@Composable fun projectCardUiModel(
    title: String, author: String, avatarKey: String, isAuthorVerified: Boolean,
    type: ProjectType?, status: ProjectStatus?, sphere: String, rolesCount: Int, period: ProjectPeriod?,
): ProjectCardUiModel                                                                // tags = known status + non-blank sphere
/** "1 из 3 мест занято" | "1 of 3 spots filled"; the plural form follows [slots]. */
@Composable fun rolePlacesText(occupied: Int, slots: Int): String

// project/ProjectSections.kt — chips only for known type/status and a non-blank sphere
@Composable fun ProjectHeader(title: String, type: ProjectType?, status: ProjectStatus?, sphere: String, modifier: Modifier = Modifier)

// RoleCards.kt — the places line under the title when slots > 1
data class RoleApplyCardUiModel(val role: String, val skills: List<String>, val occupied: Int = 0, val slots: Int = 1)
```

### 2.9 `:shared`

**`/Users/samvel/Desktop/Study/Methodology/AcademicNet/shared/src/commonMain/kotlin/org/matchlab/academic_net/di/DataSourcesModule.kt`** (domain-data). Три мок-провайдера заменяются, добавляется сборщик. Импортировать `Session` из `feature.auth.domain`, `ProjectAssembler` и `DirectoryProjectAssembler` из `gateway.mapping`. Абзац KDoc о фичах на моках обновить.

```kotlin
    /** API-GAP G47: a project names its owner by id only; owners are read through the session's directory. */
    @Provides
    @SessionScope
    fun projectAssembler(gateway: GatewaySession, session: Session): ProjectAssembler =
        DirectoryProjectAssembler(gateway.users, viewerId = session.userId)

    /** API-GAP G40, G43, G44, G42: creating, "Мои проекты" and unpublishing are off in the source's capabilities. */
    @Provides
    @SessionScope
    fun projectsDataSource(gateway: GatewaySession, assembler: ProjectAssembler): ProjectsDataSource =
        GatewayProjectsDataSource(gateway.projects, assembler)

    /** API-GAP G40, G45: the type and duration filters are off; only `q`, `field` and `tag` are sent. */
    @Provides
    @SessionScope
    fun projectSearchDataSource(gateway: GatewaySession, assembler: ProjectAssembler): ProjectSearchDataSource =
        GatewayProjectSearchDataSource(gateway.projectCatalog, assembler)

    @Provides
    @SessionScope
    fun projectDetailsDataSource(gateway: GatewaySession, assembler: ProjectAssembler): ProjectDetailsDataSource =
        GatewayProjectDetailsDataSource(gateway.projectCatalog, assembler)
```

**`ProjectsModule.kt`** (domain-data):

```kotlin
    @Provides
    fun projectsUiDependencies(repository: ProjectsRepository): ProjectsUiDependencies = ProjectsUiDependencies(
        observeMyProjects = ObserveMyProjectsUseCase(repository),
        observeProject = ObserveProjectUseCase(repository),
        loadProject = LoadProjectUseCase(repository),
        publishProject = PublishProjectUseCase(repository),
        unpublishProject = UnpublishProjectUseCase(repository),
        addRole = AddRoleUseCase(repository),
        getSpheres = GetSpheresUseCase(repository),
        capabilities = repository.capabilities,
    )
```

**`ProjectSearchModule.kt`** (domain-data):

```kotlin
    @Provides
    fun projectSearchUiDependencies(repository: ProjectSearchRepository, spheres: SphereProvider): ProjectSearchUiDependencies =
        ProjectSearchUiDependencies(
            searchProjects = SearchProjectsUseCase(repository),
            getSpheres = GetSearchSpheresUseCase(spheres),
            capabilities = repository.capabilities,
        )
```

Без изменений: `ProjectDetailsModule.kt`, `SessionComponent.kt`, `SessionDependencies.kt`, адаптеры `ProjectsRoleRequirementProvider.kt` и `ProjectsSphereProvider.kt`, `App.kt`, `AppNavHost.kt`. id в маршрутах — UUID по спецификации (`format: uuid`), экранировать их не нужно.

## 3. Ответ сервера → результат

401 на любом вызове сессии: `ApiRequests` сообщает `AuthRepository.expireSession(token)`, тот перепроверяет токен и при подтверждении меняет граф. Сам вызов заканчивается `Http(401)` и обрабатывается как сбой.

| Вызов | 2xx | 400 | 403 | 404 | 409 | 5xx, Timeout, NoConnection, Malformed |
|---|---|---|---|---|---|---|
| `GET /search/projects`, первая страница | `Page`: записи без id или владельца отброшены, «Найдено N» | Полноэкранная ошибка + `failureText` + «Повторить» | Как 400 | Как 400 | Как 400 | Как 400; текст зависит от вида сбоя |
| `GET /search/projects`, догрузка | Страница дописана, повторы отброшены | Футер `Failed` + «Повторить» | Как 400 | Как 400 | Как 400 | Как 400 |
| `GET /users/{id}/profile` (владельцы в 02, 04, 07) | Имя, вуз, щит, рейтинг при `review_count > 0` | Владелец `null`: «Без имени», вуза нет, 0 проектов | Как 400 | Как 400 | Сбой всего экрана или страницы | Сбой всего экрана или страницы |
| `GET /projects/{id}` (04) | `Found` | `NotFound`: «Проект не найден» | `NotFound` | `NotFound` | `Failure` | `Failure`: «Не удалось загрузить проект» + `failureText` + «Повторить» |
| `GET /projects/{id}` (07, `loadProject`) | Кэш, экран | `NotFound`, проект убран из кэша | `NotFound` | `NotFound` | `Failure` | Без кэша — ошибка + «Повторить»; с кэшем — показываем кэш |
| `POST /projects/{id}/roles` | Новая роль, кэш, шит закрыт | `Failure`: в шите `failureText` | `Failure` | `ProjectNotFoundException`: шит закрыт, «Проект не найден» | Перечитать проект, `ProjectConflictException`: в шите `projects_conflict` | `Failure`: в шите `failureText` |
| `POST /projects`, `GET /projects?mine`, `POST …/transitions` | Не вызываются: `FeatureUnavailableException` (G40, G44, G42). UI до них не доходит благодаря флагам | | | | | |

## 4. Кто какие файлы правит

Агенты не правят `docs/*`, `CLAUDE.md` и `README.md`. Вместо этого в отчёте они дают блоки «Для docs/sprint-1.md» и «Строки для api-gaps.md».

| Путь | Владелец |
|---|---|
| `/Users/samvel/Desktop/Study/Methodology/AcademicNet/common/src/commonMain/**` | domain-data |
| `/Users/samvel/Desktop/Study/Methodology/AcademicNet/gateway/**`, включая `GatewaySession.kt` и `ApiRequests.kt` | domain-data |
| `/Users/samvel/Desktop/Study/Methodology/AcademicNet/mockserver/**` | domain-data |
| `/Users/samvel/Desktop/Study/Methodology/AcademicNet/feature/{projects,projectsearch,projectdetails}/{domain,data}/**`, включая `build.gradle.kts` и удаление тестов | domain-data |
| `/Users/samvel/Desktop/Study/Methodology/AcademicNet/shared/src/commonMain/kotlin/org/matchlab/academic_net/di/**` (в том числе `DataSourcesModule.kt`) и адаптеры в `shared/src/commonMain/kotlin/org/matchlab/academic_net/` | domain-data |
| `/Users/samvel/Desktop/Study/Methodology/AcademicNet/shared/src/commonTest/**` (только удаления и правки из раздела 5) | domain-data |
| `/Users/samvel/Desktop/Study/Methodology/AcademicNet/components/**`, включая `strings.xml` | compose-ui |
| `/Users/samvel/Desktop/Study/Methodology/AcademicNet/feature/{projects,projectsearch,projectdetails}/ui/**`, включая `strings.xml`, `build.gradle.kts`, `…UiDependencies.kt` и удаление тестов | compose-ui |
| `/Users/samvel/Desktop/Study/Methodology/AcademicNet/catalog/**` (`ComponentCatalog.kt`) | compose-ui |
| `/Users/samvel/Desktop/Study/Methodology/AcademicNet/shared/src/commonMain/kotlin/org/matchlab/academic_net/ui/**` (`AppNavHost.kt`) и `App.kt` | compose-ui; правок не ожидается |
| `common/src/commonTest/**`, тесты domain, которые сломались из-за контракта, `shared/src/androidHostTest/**` | tester (агенты не трогают) |
| `settings.gradle.kts`, корневой `build.gradle.kts`, `libs.versions.toml`, `androidApp/`, `iosApp/` и прочие фичи | никто |

## 5. Что удаляется (правило «удали мок»)

**domain-data:**
- `/Users/samvel/Desktop/Study/Methodology/AcademicNet/feature/projects/data/src/commonMain/kotlin/org/matchlab/academic_net/feature/projects/data/mock/` целиком (`MockProjectsData.kt`, `MockProjectsDataSource.kt`);
- `/Users/samvel/Desktop/Study/Methodology/AcademicNet/feature/projects/data/src/commonTest/kotlin/org/matchlab/academic_net/feature/projects/data/MockProjectsDataSourceTest.kt` и `ProjectsRepositoryImplTest.kt`;
- `/Users/samvel/Desktop/Study/Methodology/AcademicNet/feature/projectsearch/data/src/commonMain/kotlin/org/matchlab/academic_net/feature/projectsearch/data/MockProjectSearchDataSource.kt` (вместе с `monthsCount` и `duration`) и его тест в `commonTest`;
- `/Users/samvel/Desktop/Study/Methodology/AcademicNet/feature/projectdetails/data/src/commonMain/kotlin/org/matchlab/academic_net/feature/projectdetails/data/MockProjectDetailsDataSource.kt` и его тест в `commonTest`;
- `:mockserver`:
  - `MockProjectCatalog.kt` (вместе с `MockServerProject`) и `MockProjectCatalogTest.kt`;
  - `MockPeople.kt`, `MockPeopleSeed.kt` и `MockPeopleTest.kt` — перед удалением проверить `grep -rn "MockPeople\|MockServerPerson\|MockServerDocument\|MockServerReview\|MockCheckState" --include='*.kt'`: вне `:mockserver` и удаляемых тестов пользователей быть не должно;
  - в `MockAccountsTest` удалить тест `viewerIsTheUserWhoseProfileTheMockPeopleHold`;
  - в KDoc `MockAccounts` убрать ссылку на `MockPeople`, обновить комментарий в `mockserver/build.gradle.kts`;
  - `MockAccounts` и `MockTrustedDomains` остаются: ими пока пользуется мок входа (его удалит задача A tester'а);
- `:shared/commonTest`:
  - удалить `ProjectSearchAndDetailsTest.kt`, `ProjectsSphereProviderTest.kt`, `ProjectsRoleRequirementProviderTest.kt` и `MockServerIdsTest.kt`, если он ещё есть;
  - в `di/AppComponentTest.kt` удалить `collaboratorsSeeWhatProjectsScreensChange`, `projectSearchFindsTheMockCatalog`, `aFoundProjectOpensAsAProjectPage` — иначе они пойдут на реальный `api.matchlab.space`;
  - там же заменить `MockAccounts.viewer.id` на `UserId("user-me")` и убрать импорт `:mockserver`: после удаления `api(":mockserver")` у data-модулей он пропадёт из classpath `:shared`.

**compose-ui:**
- в `/Users/samvel/Desktop/Study/Methodology/AcademicNet/feature/projects/ui/src/commonTest/kotlin/org/matchlab/academic_net/feature/projects/ui/`: `TestSetup.kt`, `create/CreateProjectViewModelTest.kt`, `list/MyProjectsViewModelTest.kt`, `management/ProjectManagementViewModelTest.kt`. `role/RoleEditorStateTest.kt` остаётся;
- `/Users/samvel/Desktop/Study/Methodology/AcademicNet/feature/projectsearch/ui/src/commonTest/kotlin/org/matchlab/academic_net/feature/projectsearch/ui/ProjectSearchViewModelTest.kt`;
- `/Users/samvel/Desktop/Study/Methodology/AcademicNet/feature/projectdetails/ui/src/commonTest/kotlin/org/matchlab/academic_net/feature/projectdetails/ui/ProjectDetailsViewModelTest.kt`;
- `implementation(project(":feature:<x>:data"))` в `commonTest` трёх ui-модулей.

Мок-ресурсов (картинок, строк) у этих фич нет. На моках остаются только чаты (G50).

## 6. Порядок работы и синхронизация

**domain-data:**
1. **domain-ready:**
   - `/Users/samvel/Desktop/Study/Methodology/AcademicNet/common/src/commonMain/kotlin/org/matchlab/academic_net/common/model/Project.kt`;
   - `/Users/samvel/Desktop/Study/Methodology/AcademicNet/common/src/commonMain/kotlin/org/matchlab/academic_net/common/result/FeatureUnavailableException.kt`;
   - в `/Users/samvel/Desktop/Study/Methodology/AcademicNet/feature/projects/domain/src/commonMain/kotlin/org/matchlab/academic_net/feature/projects/domain/`: `ProjectsCapabilities.kt`, `ProjectsRepository.kt`, `usecase/LoadProject.kt`, KDoc в `usecase/*`;
   - в `/Users/samvel/Desktop/Study/Methodology/AcademicNet/feature/projectsearch/domain/src/commonMain/kotlin/org/matchlab/academic_net/feature/projectsearch/domain/`: `ProjectSearchCapabilities.kt`, `ProjectSearchRepository.kt`, `ProjectSearchUseCases.kt`, KDoc в `ProjectSearch.kt`.

   После этого скомпилировать (раздел 7) и выполнить `mkdir -p /private/tmp/claude-501/-Users-samvel-Desktop-Study-Methodology-AcademicNet/62f647b7-10a2-490f-83d6-d4b77bca2823/scratchpad/sync && touch /private/tmp/claude-501/-Users-samvel-Desktop-Study-Methodology-AcademicNet/62f647b7-10a2-490f-83d6-d4b77bca2823/scratchpad/sync/p45.domain-ready`. С этого момента `:common` и domain меняются только совместимыми добавлениями, о каждом — в отчёте.
2. `:gateway`: DTO, два API, `KtorProjectsApi`, `GatewaySession`, mapping, assembler, `pageOf` и повторяющиеся параметры, если их не оставил блок 3.
3. Data-модули, их `build.gradle.kts`, удаления из раздела 5 и `:mockserver`.
4. `:shared/di` и правки `:shared/commonTest` из раздела 5. `:shared` не компилируется.
5. Компиляция для iOS.

**compose-ui:**
1. До маркера Gradle не запускать вообще: каждый ui-модуль и `:components` тянут `:common`, а его в это время правит domain-data. Можно писать:
   - строки (ru и en, ключи 1:1);
   - `:components`;
   - `…UiDependencies`;
   - ViewModel и экраны по разделу 2;
   - `:catalog`;
   - удаление тестов.

   Наличие маркера проверять командой `test -f …/sync/p45.domain-ready` между шагами.
2. После маркера скомпилировать свои модули (раздел 7) и сразу исправить ошибки.

| Модуль | Новые ключи (ru / en) |
|---|---|
| `:components` | plural `components_role_places`: one «%1$d из %2$d места занято», few, many и other «%1$d из %2$d мест занято» / one "%1$d of %2$d spot filled", other "%1$d of %2$d spots filled" |
| projects ui | `projects_list_unavailable` «Список ваших проектов пока недоступен на сервере» / "The server can't list your projects yet" |
| projects ui | `projects_create_unavailable` «Создание проектов пока недоступно на сервере» / "The server can't create projects yet" |
| projects ui | `projects_banner_status_unknown` «Сервер пока не сообщает статус проекта: действия управления недоступны» / "The server doesn't report the project's status yet: no management actions are available" |
| projects ui | `projects_conflict` «Проект изменился на сервере — проверьте данные и повторите действие» / "The project has changed on the server: check it and try again" |
| projects ui | `projects_load_project_error` «Не удалось загрузить проект» / "Couldn't load the project" |
| projects ui | `projects_retry` «Повторить» / "Retry" |
| projectsearch ui | `projectsearch_you_initiator` «Вы инициатор» / "You're the initiator" |

## 7. Команды проверки (только компиляция)

`G=/private/tmp/claude-501/-Users-samvel-Desktop-Study-Methodology-AcademicNet/62f647b7-10a2-490f-83d6-d4b77bca2823/scratchpad/tools/gw.sh`. Gradle запускается только через этот скрипт: он не даёт двум агентам собирать одновременно. Тесты, `:shared` и `assembleDebug` агенты не запускают.

- domain-data, шаг 1: `$G :common:compileAndroidMain :feature:projects:domain:compileAndroidMain :feature:projectsearch:domain:compileAndroidMain :feature:projectdetails:domain:compileAndroidMain --console=plain`.
- domain-data, шаги 2–3: `$G :gateway:compileAndroidMain :mockserver:compileAndroidMain :feature:projects:data:compileAndroidMain :feature:projectsearch:data:compileAndroidMain :feature:projectdetails:data:compileAndroidMain --console=plain`.
- domain-data, iOS: `KONAN_DATA_DIR=/private/tmp/claude-501/-Users-samvel-Desktop-Study-Methodology-AcademicNet/62f647b7-10a2-490f-83d6-d4b77bca2823/scratchpad/konan-data $G :gateway:compileKotlinIosSimulatorArm64 :feature:projects:data:compileKotlinIosSimulatorArm64 :feature:projectsearch:data:compileKotlinIosSimulatorArm64 :feature:projectdetails:data:compileKotlinIosSimulatorArm64 --console=plain`.
- compose-ui, после маркера: `$G :components:compileAndroidMain :feature:projects:ui:compileAndroidMain :feature:projectsearch:ui:compileAndroidMain :feature:projectdetails:ui:compileAndroidMain :catalog:compileAndroidMain --console=plain`.

Основная сессия потом собирает `:androidApp:assembleDebug` и `:shared` для iOS. Тесты `:common` (`ProjectTest`), `:feature:projects:domain` (`FakeProjectsRepository`) и `:feature:projectsearch:domain` (`FakeProjectSearchRepository`) не скомпилируются, пока их не перенесёт tester.

## 8. Что должен покрыть tester (критичное)

- **`:common`:**
  - `Role`: `isVacant` и `vacantPlaces` при `slots > 1`, `occupied > slots`, `require` в `init`;
  - `Project`: `occupiedPlaces`, `totalPlaces`, `vacantRoles`;
  - `FeatureUnavailableException.gap`.
- **`:gateway`** (MockEngine, разобранные `url.parameters`):
  - путь, метод и Bearer у трёх вызовов;
  - в `search/projects` всегда есть `limit` и `offset`; `q` и `field` — только непустые; `tag` повторяется по порядку; никогда нет `type`, `status`, `mine`, `duration`, `profile_user_id`;
  - тело `addRole` в snake_case, всегда с `slots` и `expected_revision` (в том числе 0), без `description: null`;
  - разбор `{}`, `null` вместо массивов, `total` строкой и числом;
  - `toProject`: все правила раздела 2.2, включая отбрасывание без id или владельца и `viewerIsInitiator`;
  - `DirectoryProjectAssembler`: один `profiles` с уникальными id, порядок, владелец без профиля, исключение пробрасывается, пустой вход — без запросов;
  - `pageOf`, если его создал этот блок.
- **domain:**
  - `LoadProjectUseCase`: Found, NotFound, Failure, отмена проходит насквозь;
  - `SearchProjectsUseCase` со страницей;
  - `AddRoleUseCase` с `ProjectConflictException` даёт `Failure`.
- **data:**
  - `GatewayProjectsDataSource`:
    - флаги `false`;
    - три недоступные операции бросают исключение с нужным `gap` и не делают запросов;
    - `loadProject`: 200 → кэш и `observeProject`; 400, 403, 404 → `null` и удаление из кэша; 500 → исключение, кэш цел;
    - `getProject` из кэша без запроса;
    - `addRole`: ревизия из кэша; загрузка, если проекта нет; новая роль по разнице id; 404; 409 → перечитать и конфликт, в том числе когда перечитывание упало;
    - запись с меньшей ревизией не затирает кэш;
    - две записи идут по очереди;
    - 12 сфер, `id == name`.
  - `GatewayProjectSearchDataSource`: параметры (`types` и `durations` игнорируются), вуз владельца, `received` и `total`, сбой сборщика.
  - `GatewayProjectDetailsDataSource`: 400, 403, 404 → `null`; 500 → исключение; `rating = null` при `review_count = 0`; владелец без профиля.
- **ui:**
  - `ProjectSearchViewModel`:
    - первая страница и `total`;
    - догрузка;
    - двойной `onLoadMore` даёт один запрос;
    - повторы не попадают в список;
    - `Failed`, затем повтор;
    - новый текст отменяет догрузку;
    - ответ старого поколения отброшен;
    - ошибка первой страницы с причиной;
    - флаги в состоянии шита;
    - свой проект.
  - `MyProjectsViewModel`: без `canListMine` нет вызова; флаг `canCreate`.
  - `ProjectManagementViewModel`: загрузка на старте, кэш, NotFound, повтор, конфликт в шите, `canUnpublish` только при флаге и `Recruiting`, существующие стражи.
  - `CreateProjectViewModel`: `FeatureUnavailableException`.
  - `ProjectDetailsViewModel`: места, `Error(cause)`.
- **`:shared`:**
  - граф сессии Gateway на `GatewayFactory` с `MockEngine` собирает новые источники без сети;
  - сквозная связка «поиск → страница → роль → 08» на `MockEngine` взамен удалённых тестов `AppComponentTest`;
  - адаптеры `ProjectsRoleRequirementProvider` и `ProjectsSphereProvider` на фейке `ProjectsRepository`;
  - `LocalizationResourcesTest` зелёный.
- **Миграция тестов с моков:** всё удалённое в разделе 5, а также `ProjectTest` и фейки репозиториев в тестах domain.

## 9. Допущения, открытые решения, пробелы API

**Допущения** (для `docs/sprint-1.md`, разделы «Управлять проектом», «Найти подходящий проект», «Оценить проект»):
1. 07, 07b и 07c в Gateway недостижимы. Альтернатива, которую я не рекомендую из-за объёма: кнопка «Управлять» на 04 для своего проекта.
2. 409 — перечитать проект и показать сообщение, без автоповтора. Альтернатива — один автоповтор, как в прежнем проекте.
3. Если не загрузился профиль владельца (кроме 400, 403, 404), падает вся страница. Альтернатива — показать «Без имени».
4. Свои проекты и проекты в любых статусах могут попасть в поиск: свой подписан «Вы инициатор» и открывается только для чтения.
5. Ключевые слова уходят как `tag`, текст — как `q` без крайних пробелов, страница — 20 записей.
6. Сферы — клиентский список из 12, на сервер уходит название.
7. Участники ролей не показываются, места считает `occupied`.
8. id в маршрутах — UUID.
9. Протокол страниц продублирован в VM поиска людей и VM поиска проектов. Общий помощник выносить в `:components/paging`, когда появится третий такой экран.

**Устаревшее в `docs/sprint-1.md`, что нужно заменить:**
- «Пагинации нет»;
- «Срок — число месяцев…»;
- «Свои проекты … не показываются»;
- контракты мок-серверов поиска и страницы проекта;
- «Статус … всегда «Поиск соавторов»»;
- `MockServerIdsTest`;
- «id … нужно кодировать»;
- «Список сфер — моковый»;
- описание `:mockserver`.

В `CLAUDE.md`: `:mockserver` теперь хранит только аккаунты и реестр доменов; соглашение «ViewModel-тесты против `Mock…DataSource`» заменить на «против fake-репозиториев».

**Затронутые пробелы API:**
- G40, G41, G42, G43, G44, G45 — что отключено, см. разделы 0 и 1;
- G46 — сфера как текст;
- G47 — N+1 по владельцам, до 21 запроса на страницу, не больше 4 одновременно;
- G48 — для ролей смягчено: повтор с устаревшей ревизией получит 409, роль не задвоится;
- G49 — `slots = 1`;
- G51, G50 — без изменений.

**Новые строки для `/Users/samvel/Desktop/Study/Methodology/AcademicNet/docs/api-gaps.md`:**

| ID | Чего не хватает в API | Где сказывается | Что делаем сейчас | Нужно от бэкенда |
|---|---|---|---|---|
| **G52** | У `ProjectMember.status` нет перечня значений | 07b: карточки участников | `members` не читаются, занятость берётся из `occupied`. Роль без свободных мест — карточка без «Найти людей» | Перечень статусов участника |
| **G53** | В `/search/projects` не описаны смысл `q` и `tag[]` (поля, «И» или «ИЛИ»), порядок выдачи и его устойчивость между страницами, попадают ли в выдачу свои и неопубликованные проекты | 02, 03 | `q` уходит как введено, ключевые слова — как `tag`. Порядок серверный. Повторы между страницами отбрасываются по id | Описание семантики и порядка |

**Риски:**
- Смена модели в `:common` ломает сборку `:components`, трёх ui-модулей и `:catalog` до шага compose-ui. Ловит компилятор, а параллельность защищена маркером.
- `:shared` проверяет только основная сессия, поэтому имена в разделах 2.7 и 2.9 должны совпасть буквально.
- `LocalizationResourcesTest` требует, чтобы ключи ru и en совпадали 1:1.
- Для iOS платформенного кода нет, проверяется только компиляция; линковки и iOS-тестов нет.
