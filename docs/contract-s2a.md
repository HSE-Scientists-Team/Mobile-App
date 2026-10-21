# Спринт 2, блоки 0–4: межслойные контракты для параллельной работы domain-data и compose-ui

Экраны: 09 и 08b (блок 1), 04 и 07b (блок 2), 07c (блок 3), 04d и вход в него из 07 и 07c (блок 4); блок 0 служебный.
Корень репозитория — `R = /Users/samvel/Desktop/Study/Methodology/AcademicNet`, скретчпад — `S = /private/tmp/claude-501/-Users-samvel-Desktop-Study-Methodology-AcademicNet/62f647b7-10a2-490f-83d6-d4b77bca2823/scratchpad`.
Документ — единственный источник правды о границе слоёв. Оба агента реализуют его буквально. Если чего-то не хватает, агент выбирает простое решение внутри своего слоя и пишет о нём в отчёте. Чужой слой не правится.

## 0. Что изменилось по сравнению с прежними проектами

Прежние проекты: `R/docs/sprint-2.md` (раздел «Блоки: состав и решения») и `R/docs/gateway-blocks3-5-search-and-projects.md` (разделы 3.3 и 5).

| # | Было | Стало | Почему |
|---|---|---|---|
| 1 | `ProjectJoin.type` и `status` всегда `null` (G58, G59) | Свои коды чтения: `ProjectJoinType` (`1` Application, `2` Invitation), `ProjectJoinStatus` (`1` Pending, `2` Accepted, `3` Declined, `4` Withdrawn). Неизвестный код — `null`, запись не прячется | Решение оркестратора. Сервером не подтверждено |
| 2 | Статус проекта знает только `1` и `2`; переходы 3/4/5 «не используются» (G62) | `ProjectStatus` + `Started("3")`, `Finished("4")`, `Cancelled("5")`; `ProjectActions` + `START`, `COMPLETE`, `CANCEL`. Набор действий 07c зависит от статуса (таблица в 2.3) | Решение оркестратора. Граф переходов — наше допущение |
| 3 | `@UnverifiedContract` на всех непроверенных кодах | Аннотация стоит на `ProjectActions` в `:gateway`. Enum-ы в `:common` (`ProjectStatus`, `ProjectJoinType`, `ProjectJoinStatus`) помечены строкой KDoc «Not confirmed by the backend» | `:common` не зависит от `:gateway`. Так велит `api-values.md` §1 п. 4. Переносить аннотацию — лишняя правка многих файлов |
| 4 | `ComplaintsApi` входит в блок 0 | Не входит, переходит в блок 5 (только добавление нового файла в `:gateway`) | По заданию блок 0 — основа блоков 6–8. Блоки 5–8 здесь не проектируются |
| 5 | `:feature:responses:{domain,data}` создаются в блоке 0 | Не создаются, появятся в блоке 6 вместе с содержимым | Пустые модули — мёртвый код |
| 6 | `ProjectDto` + `created_at_unix`, `completed_at_unix`, `moderation_hidden` | Не читаются. Читаются `members` и `pending_join_count` | Ни блокам 1–8, ни их потребителям эти поля не нужны. При этом битый int64 в любом из них (`FlexibleLongSerializer`) уронил бы разбор всего проекта |
| 7 | `assemble(projects, withMembers)` (прежний проект, 3.3) | `ProjectAssembler` не меняется. Профили участников читает отдельный `ProjectTeamReader` | ISP/OCP: поиску участники не нужны, тесты и фейки assembler-а остаются |
| 8 | Участники фильтруются по `status` («кроме ушедших») | `ProjectMember.status` не читается и в модели его нет; показываются все `members` | Таблицы кодов статуса участника нет (G52) |
| 9 | `startProject` / `completeProject` / `cancelProject` | Один `changeLifecycle(id, ProjectLifecycleAction)` | DRY: одна ручка, один разбор 404/409. Альтернатива — три метода; у неё нет выигрыша |
| 10 | Блок «Команда» загружается вместе с проектом | Загружается вторым шагом, в блоке видна загрузка (кадр 04e) | Дизайн, пункт 8; страница не ждёт N запросов профилей |
| 11 | `Role.members` всегда пуст (G52) | Заполняется при чтении одного проекта в `projects` (07b) и в `projectdetails` (04). В списках (06, поиск) остаётся пустым | Блок 2 называет экраны 04c и 07b |

## 1. Как работают блоки

| Блок | Экран | Что делает пользователь | Сервер | Что отключено или условно |
|---|---|---|---|---|
| 1 | 09, 10 | Видит чип «Открыт к сотрудничеству» | `GET /users/{id}/profile` → `details.open_to_collaboration` (нет поля или `null` — не открыт) | — |
| 1 | 08b | В группе «Статус» включает чип «Открыт к сотрудничеству» | `GET /search/people?…&open_only=true` | — |
| 2 | 04 (и 04c — та же страница) | Под ролями видит блок «Команда»: сначала инициатор, затем участники (аватар, имя, роль, рейтинг, число завершённых); тап открывает профиль, иконка чата — локальный чат | `GET /projects/{id}` (`members`), затем `GET /users/{id}/profile` на каждого через `UserDirectory` (кэш 5 мин, до 4 одновременно) | Участник без профиля (ответ 400/403/404 или сбой) показывается как «Без имени»: без рейтинга, чата и тапа (04f). Блока нет, если кроме инициатора участников нет. G52: статус участника не читается |
| 2 | 07b | На карточках ролей видны участники роли | То же | Кнопки «Выгнать» и чата — блок 8 |
| 3 | 07c | «Начать», «Завершить», «Отменить проект» → диалог → переход | `POST /projects/{id}/transitions {action: "3"/"4"/"5", expected_revision}` | Действия зависят от статуса (2.3). Статус неизвестен — предлагаются все. 409 → проект перечитывается, текст «Действие недоступно в текущем состоянии проекта» |
| 3 | 06, 07, 04, 02 | Видит чипы «В работе», «Завершён», «Отменён» | `Project.status` 3/4/5 | Неизвестный статус — чипа нет |
| 4 | 04d (вход: карандаш в шапке 07 и строка «Редактировать проект» в 07c) | Меняет название, тип, сферу, проблему, цель, теги, сроки → «Сохранить изменения»; правит роль (карандаш), удаляет свободную роль (корзина + диалог), добавляет роль | `PUT /projects/{id}` (без `roles` и `id`, G63); `PUT /projects/{id}/roles/{role_id}`; `DELETE /projects/{id}/roles/{role_id}?expected_revision=N`; `POST /projects/{id}/roles` | Операции с ролями уходят сразу, описание — по кнопке «Сохранить». `slots` роли не редактируется (поля в макете нет), `description` возвращается как пришёл. Корзина есть только у роли с `occupied = 0` (G64). 409 → баннер «Проект изменился на сервере» |
| 0 | — | — | `ProjectJoinsApi` (6 вызовов), `ProjectsApi.update/updateRole/deleteRole/removeMember/leave` | Блоки 1–4 используют только `update`, `updateRole`, `deleteRole`; остальное — для блоков 6–8 |

## 2. Контракты

### 2.1 `:common` (domain-data; входит в domain-ready)

**`R/common/src/commonMain/kotlin/org/matchlab/academic_net/common/model/Ids.kt`** — добавить:

```kotlin
/** The id of an application or an invitation to a role of a project (`ProjectJoinRequest.id`). */
@JvmInline
value class ProjectJoinId(val value: String)
```

**`R/common/src/commonMain/kotlin/org/matchlab/academic_net/common/model/Project.kt`** — изменить только перечисленное, остальное оставить как есть.

```kotlin
enum class ProjectStatus(val code: String) {
    /** Published and looking for people for its vacant roles. */
    Recruiting("1"),

    /** Taken off publication by the initiator: hidden from search, responses are paused. */
    Unpublished("2"),

    /** "В работе": the initiator started the project (transition action "3"); the team works on it. */
    Started("3"),

    /** "Завершён": the initiator finished the project (transition action "4"). */
    Finished("4"),

    /** "Отменён": the initiator cancelled the project (transition action "5"). */
    Cancelled("5"),
    ;
    // companion object fromCode — unchanged
}

/**
 * Person who works on a [Role], with the numbers shown on the project's role list and team.
 *
 * @property hasProfile the app could read the person's public profile. Without it [user] has the id only (an empty
 *  name, the UI writes "Без имени"), [rating] is `null` and [completedProjects] is 0.
 */
data class RoleMember(
    val user: UserSummary,
    /** Average of reviews, `null` while there are none. */
    val rating: Double? = null,
    val completedProjects: Int = 0,
    val hasProfile: Boolean = true,
)

/**
 * A person on the team of a project as the server lists them (`Project.members`): who, and on which role. Their profile
 * is read separately (API-GAP G47). API-GAP G52: the server's `status` of a member has no table of values, so it is not
 * read, and no member is hidden because of it.
 *
 * @property roleId `null` when the server named no role.
 * @property roleTitle the server's `role_title`; when it is blank, the title of the role [roleId] names; else empty.
 * @property isViewer the member is the user of the session.
 */
data class ProjectMember(
    val userId: UserId,
    val roleId: RoleId?,
    val roleTitle: String,
    val isViewer: Boolean,
)
```

В `Role`: KDoc `members` заменить на «the people on the role the app knows: filled when one project is read for its page or its management (their profiles are read separately, G47), empty in lists; may differ from [occupied]». Сигнатура не меняется.

В `Project` в конец конструктора добавить два параметра со значениями по умолчанию и одно свойство:

```kotlin
    /** The team as the server lists it, in its order; a person on two roles is there twice. Empty when none was sent. */
    val members: List<ProjectMember> = emptyList(),
    /** The server's `pending_join_count`, 0 or more. API-GAP G61: what exactly it counts is not described. */
    val pendingJoinCount: Int = 0,
) {
    /** The user of the session is on the team ([ProjectMember.isViewer]); the initiator is told by [viewerIsInitiator]. */
    val viewerIsMember: Boolean get() = members.any { it.isViewer }
    // vacantRoles, occupiedPlaces, totalPlaces — unchanged
```

**`R/common/src/commonMain/kotlin/org/matchlab/academic_net/common/model/ProjectJoin.kt`** — новый файл:

```kotlin
package org.matchlab.academic_net.common.model

import kotlin.time.Instant

/**
 * What a [ProjectJoin] is: the server writes `type` itself and the API names no values, so these are the codes the app
 * reads it by. **Not confirmed by the backend** (`docs/api-values.md`); a code the app has none for is "unknown" (`null`).
 */
enum class ProjectJoinType(val code: String) {
    /** "Отклик": the candidate asked for the role. */
    Application("1"),

    /** "Приглашение": the initiator asked the candidate to take the role. */
    Invitation("2"),
    ;

    companion object {
        /** Exact match once the outer spaces are cut; anything else is `null`. */
        fun fromCode(raw: String?): ProjectJoinType? {
            val code = raw?.trim() ?: return null
            return entries.firstOrNull { it.code == code }
        }
    }
}

/**
 * Where a [ProjectJoin] is. The server writes `status` itself; these are the codes the app reads it by. **Not confirmed by
 * the backend** (`docs/api-values.md`); an unknown code is `null` and the record is shown all the same.
 */
enum class ProjectJoinStatus(val code: String) {
    /** Waiting for the other side's decision. */
    Pending("1"),

    /** Accepted: the candidate is on the team. */
    Accepted("2"),

    /** Declined by the side that decides. */
    Declined("3"),

    /** Taken back by the side that sent it. */
    Withdrawn("4"),
    ;

    companion object {
        /** Exact match once the outer spaces are cut; anything else is `null`. */
        fun fromCode(raw: String?): ProjectJoinStatus? {
            val code = raw?.trim() ?: return null
            return entries.firstOrNull { it.code == code }
        }
    }
}

/**
 * An application of a candidate to a role, or an invitation of a candidate to it (`ProjectJoinRequest`).
 *
 * @property candidateId `null` when the server left `candidate_user_id` out (API-GAP G55).
 * @property type `null` when unknown ([ProjectJoinType.fromCode]).
 * @property status `null` when unknown ([ProjectJoinStatus.fromCode]).
 * @property message the text the sender wrote, trimmed; empty when none.
 * @property createdAt `null` when the server sent no usable `created_at_unix`.
 */
data class ProjectJoin(
    val id: ProjectJoinId,
    val projectId: ProjectId,
    val roleId: RoleId,
    val candidateId: UserId?,
    val type: ProjectJoinType?,
    val status: ProjectJoinStatus?,
    val message: String,
    val createdAt: Instant?,
)
```

### 2.2 `:gateway` (domain-data; в domain-ready не входит)

**`contract/ProjectActions.kt`** — добавить константы и поправить KDoc: убрать «3, 4, 5 are not used», дописать «3 start, 4 finish, 5 cancel — the lifecycle of 07c».

```kotlin
    /** "Начать": the team starts working. */
    const val START = "3"

    /** "Завершить". */
    const val COMPLETE = "4"

    /** "Отменить". */
    const val CANCEL = "5"
```

**`dto/ProjectDtos.kt`** — изменения:

```kotlin
/** `ProjectRole`. [description] is kept as it came, to be sent back by a role update (the PUT replaces the role). */
@Serializable
data class ProjectRoleDto(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val skills: List<String> = emptyList(),
    val slots: Int = 0,
    val occupied: Int = 0,
)

/** `ProjectMember`. API-GAP G52: [status] has no table of values and is not read by the mapping. */
@Serializable
data class ProjectMemberDto(
    @SerialName("user_id") val userId: String = "",
    @SerialName("role_id") val roleId: String = "",
    @SerialName("role_title") val roleTitle: String = "",
    val status: String = "",
)

@Serializable
data class ProjectDto(
    val id: String = "",
    @SerialName("owner_user_id") val ownerUserId: String = "",
    val details: ProjectInputDto = ProjectInputDto(),
    val status: String = "",
    val revision: Int = 0,
    val roles: List<ProjectRoleDto> = emptyList(),
    val members: List<ProjectMemberDto> = emptyList(),
    /** API-GAP G61: what it counts is not described. */
    @SerialName("pending_join_count") val pendingJoinCount: Int = 0,
)

/**
 * `ProjectUpdate`, the body of `PUT /projects/{id}`: every field but [tags] is required by the API. API-GAP G63: `roles`
 * and `id` of `ProjectInput` are never sent (what the PUT does with them is not described); roles change by their own calls.
 * [tags] always goes (an empty list clears them: the PUT replaces the description).
 */
@Serializable
data class ProjectUpdateDto(
    val title: String,
    val type: String,
    val field: String,
    val problem: String,
    val goal: String,
    val tags: List<String> = emptyList(),
    @SerialName("planned_start") val plannedStart: String,
    @SerialName("planned_end") val plannedEnd: String,
    @SerialName("expected_revision") val expectedRevision: Int,
)
```

`ProjectRoleInputDto` остаётся как есть и служит телом и `POST`, и `PUT …/roles/{role_id}`. `description = null` не отправляется (`explicitNulls = false`).

**`dto/ProjectJoinDtos.kt`** — новый файл:

```kotlin
/** `ProjectJoinRequest`. [type] and [status] are the server's strings; the mapping reads them by the app's codes. */
@Serializable
data class ProjectJoinDto(
    val id: String = "",
    @SerialName("project_id") val projectId: String = "",
    @SerialName("role_id") val roleId: String = "",
    @SerialName("candidate_user_id") val candidateUserId: String = "",
    val type: String = "",
    val status: String = "",
    val message: String = "",
    @SerialName("created_at_unix") @Serializable(with = FlexibleLongSerializer::class) val createdAtUnix: Long = 0,
)

/** `ProjectJoinList`: one page and the size of the whole list (int64, a string or a number). */
@Serializable
data class ProjectJoinListDto(
    val items: List<ProjectJoinDto> = emptyList(),
    @Serializable(with = FlexibleLongSerializer::class) val total: Long = 0,
)

/**
 * `ProjectJoinInput`, the body of applications and invitations. API-GAP G60: the API names no required field; a `null`
 * field is not sent.
 */
@Serializable
data class ProjectJoinInputDto(
    @SerialName("project_id") val projectId: String? = null,
    @SerialName("role_id") val roleId: String? = null,
    @SerialName("candidate_user_id") val candidateUserId: String? = null,
    val message: String? = null,
)

/** `ProjectJoinDecisionBody`: [accept] is required. */
@Serializable
data class ProjectJoinDecisionDto(val accept: Boolean)
```

**`ProjectsApi.kt`** — добавить в интерфейс:

```kotlin
    /** `PUT /api/v1/projects/{id}` → 200: the whole project. A stale [ProjectUpdateDto.expectedRevision] is a 409. */
    suspend fun update(id: String, input: ProjectUpdateDto): ProjectDto

    /** `PUT /api/v1/projects/{id}/roles/{role_id}` → 200: the whole project. Both ids escaped; stale revision — 409. */
    suspend fun updateRole(id: String, roleId: String, input: ProjectRoleInputDto): ProjectDto

    /**
     * `DELETE /api/v1/projects/{id}/roles/{role_id}?expected_revision=…` → 200: the whole project ("удалить свободную роль
     * и отменить её отклики"). API-GAP G64: what "free" means and the code for a role that is not are not described.
     */
    suspend fun deleteRole(id: String, roleId: String, expectedRevision: Int): ProjectDto

    /** `DELETE /api/v1/projects/{id}/members/{user_id}` → 200: the whole project. API-GAP G65. */
    suspend fun removeMember(id: String, userId: String): ProjectDto

    /** `DELETE /api/v1/projects/{id}/membership` → 200: the whole project after the viewer left it. API-GAP G65. */
    suspend fun leave(id: String): ProjectDto
```

**`ProjectJoinsApi.kt`** — новый файл:

```kotlin
/**
 * Applications and invitations (`ProjectJoinRequest`). Every call throws `RemoteFailure` when it fails and lets a
 * `CancellationException` through, like [AccountApi]. Ids go into paths escaped.
 */
interface ProjectJoinsApi {
    /** `POST /api/v1/projects/{projectId}/applications` → 201. API-GAP G60. */
    suspend fun apply(projectId: String, input: ProjectJoinInputDto): ProjectJoinDto

    /** `POST /api/v1/projects/{projectId}/invitations` → 201. API-GAP G60. */
    suspend fun invite(projectId: String, input: ProjectJoinInputDto): ProjectJoinDto

    /** `GET /api/v1/projects/{projectId}/joins` (the initiator's view): `limit`, `offset` always, then what [filter] has. */
    suspend fun listForProject(projectId: String, filter: ProjectJoinFilter, page: PageRequest): ProjectJoinListDto

    /** `GET /api/v1/project-joins` (the viewer's applications and invitations). */
    suspend fun listMine(filter: ProjectJoinFilter, page: PageRequest): ProjectJoinListDto

    /** `POST /api/v1/project-joins/{joinId}/decision` `{"accept": …}` → 200. */
    suspend fun decide(joinId: String, accept: Boolean): ProjectJoinDto

    /** `DELETE /api/v1/project-joins/{joinId}` → 200: the record after it was taken back. */
    suspend fun withdraw(joinId: String): ProjectJoinDto
}

/**
 * The filters of the two lists. `null` and blank values are not sent.
 *
 * @property roleId `role_id`
 * @property status `status`: one of the `ProjectJoinStatus.code`s (not confirmed by the backend, `docs/api-values.md`)
 */
data class ProjectJoinFilter(
    val roleId: String? = null,
    val status: String? = null,
)
```

**`GatewaySession.kt`** — добавить:

```kotlin
    /** Applications and invitations: the candidate's and the initiator's side. */
    val projectJoins: ProjectJoinsApi = KtorProjectJoinsApi(requests)
```

**`internal`** (тело на усмотрение domain-data): `KtorProjectsApi` реализует 5 новых методов; новый `KtorProjectJoinsApi`. В `ApiRequests` добавить `put<B, T>(path, body: B): T` (кодирует тело через `GatewayJson`) и `deleteFor<T>(path, query: Map<String, String?> = emptyMap()): T` (DELETE с разбором ответа). Существующие `delete` и `putJson` не меняются.

**`mapping/ProjectMapping.kt`** — `ProjectDto.toProject(viewerId, owner)` дополнительно заполняет поля:

- `members`: каждый `ProjectMemberDto`, у которого непустой `user_id` (запись без него отбрасывается, G55). `roleId` — `RoleId(role_id)` или `null`, если он пустой. `roleTitle` — `role_title.trim()`, если пусто — название роли с этим id из `roles`, иначе `""`. `isViewer = user_id == viewerId.value`. Повтор пары (`user_id`, `role_id`) отбрасывается, порядок сервера сохраняется. `status` не читается (`// API-GAP G52`).
- `pendingJoinCount = pendingJoinCount.coerceAtLeast(0)` (`// API-GAP G61`).
- `Role.members` по-прежнему `emptyList()`: его заполняет `withTeam` (ниже). KDoc поправить.

**`mapping/ProjectJoinMapping.kt`** — новый файл:

```kotlin
/**
 * The record as the app shows it, or `null` when it cannot be shown: `id`, `project_id` or `role_id` is blank (API-GAP
 * G55, G66). `type` and `status` are read by [ProjectJoinType.fromCode] and [ProjectJoinStatus.fromCode] (unknown —
 * `null`); a blank candidate is `null`; the message is trimmed; the time is [instantOfUnixSeconds].
 */
fun ProjectJoinDto.toProjectJoin(): ProjectJoin?

/** One page of records: the readable ones of [ProjectJoinListDto.items], `received` = all items sent ([pageOf]). */
fun ProjectJoinListDto.toProjectJoinPage(request: PageRequest): Page<ProjectJoin>
```

**`mapping/ProjectTeam.kt`** — новый файл, публичный (им пользуются data-модули `projects` и `projectdetails`):

```kotlin
package org.matchlab.academic_net.gateway.mapping

/** A member of a project's team with the public profile the viewer may see; `null`: none could be read. */
data class TeamMemberProfile(
    val member: ProjectMember,
    val profile: UserProfileDto?,
)

/**
 * Reads the profiles of a project's team. API-GAP G47: the API names a person by id only, so each person is a request
 * of their own. Shared by the projects and the page of a project; their data layers are tested with a fake of it.
 */
fun interface ProjectTeamReader {
    /**
     * [members] in their order, each with their profile. Each distinct person is asked for once. A person whose profile
     * is missing (400, 403, 404) or whose request failed has `null`: one person never fails the team. Never throws a
     * `RemoteFailure`; a `CancellationException` passes through. An empty [members] sends nothing.
     */
    suspend fun read(members: List<ProjectMember>): List<TeamMemberProfile>
}

/** The [ProjectTeamReader] of a session: profiles come from [users] (cache 5 min, at most 4 requests at a time). */
class DirectoryProjectTeamReader(private val users: UserDirectory) : ProjectTeamReader {
    override suspend fun read(members: List<ProjectMember>): List<TeamMemberProfile>
}

/**
 * The member as a role and the team show them: the profile's name, verification, rating ([ratingOrNull], G20) and finished
 * projects (0 or more); without a profile, the member's id with an empty name and `hasProfile = false`. The id is always
 * the member's (the profile may leave `user_id` out, G55).
 */
fun TeamMemberProfile.toRoleMember(): RoleMember

/** [this] with each role's `members` set to the [team] members whose `roleId` is the role's; `occupied` is kept. */
fun Project.withTeam(team: List<TeamMemberProfile>): Project
```

### 2.3 `feature:projects:domain` (domain-data; входит в domain-ready)

**`ProjectsCapabilities.kt`** — добавить последний параметр:

```kotlin
    /**
     * 07c offers "Начать проект", "Завершить проект", "Отменить проект": `POST /projects/{id}/transitions` with the actions
     * "3", "4", "5" (`docs/api-values.md`, not confirmed by the backend). When `false` there are none of them, and
     * [ProjectsRepository.changeLifecycle] throws `FeatureUnavailableException`.
     */
    val canChangeLifecycle: Boolean = false,
```

**`ProjectLifecycle.kt`** — новый файл:

```kotlin
package org.matchlab.academic_net.feature.projects.domain

/** A move of a project through its life that its initiator makes in 07c. */
enum class ProjectLifecycleAction { Start, Complete, Cancel }

/**
 * Which moves the app offers for a status. API-GAP G62: the API does not describe which transition is allowed from which
 * status, so this table is the app's assumption; the server is the judge (a refused move is a 409). An unknown status
 * (`null`) offers everything.
 *
 * | status | actions | unpublish |
 * |---|---|---|
 * | `null` | Start, Complete, Cancel | yes |
 * | Recruiting | Start, Cancel | yes |
 * | Unpublished | Start, Cancel | no |
 * | Started | Complete, Cancel | no |
 * | Finished, Cancelled | none | no |
 */
object ProjectLifecycle {
    /** The actions for [status], in the order 07c lists them: Start, Complete, Cancel. */
    fun actionsFor(status: ProjectStatus?): List<ProjectLifecycleAction>

    /** "Снять с публикации" is offered: the status is unknown or [ProjectStatus.Recruiting]. */
    fun canUnpublish(status: ProjectStatus?): Boolean
}
```

**`ProjectInfoDraft.kt`** — новый файл (описание проекта без ролей: его меняет 04d):

```kotlin
/**
 * The description of a published project as the edit form (04d) holds it. Validated like [ProjectDraft] (the same
 * [ProjectDraftError]s, never [ProjectDraftError.RoleInvalid]).
 */
data class ProjectInfoDraft(
    val title: String = "",
    val type: ProjectType? = null,
    val sphere: Sphere? = null,
    val problem: String = "",
    val goal: String = "",
    val tags: List<String> = emptyList(),
    val start: YearMonth? = null,
    val end: YearMonth? = null,
) {
    /** The title like a tag, the texts trimmed, the tags normalized — as [ProjectDraft.normalized]. */
    fun normalized(): ProjectInfoDraft

    fun validate(): Set<ProjectDraftError>

    companion object {
        /** The form filled with what [project] has; what is unknown there (`null`) is empty and must be chosen. */
        fun of(project: Project): ProjectInfoDraft
    }
}

/** A description that passed validation, ready to be sent (`PUT /projects/{id}`). */
data class ProjectInfo(
    val title: String,
    val type: ProjectType,
    val sphere: Sphere,
    val problem: String,
    val goal: String,
    val tags: List<String>,
    val period: ProjectPeriod,
)
```

Публичный API `ProjectDraft` не меняется. Внутри его `validate()` поля описания проверяются через `ProjectInfoDraft` (DRY), а проверку ролей оставляет себе.

**`ProjectsRepository.kt`** — добавить исключение и методы:

```kotlin
/** The server refused to delete the role: it has people on it or is otherwise not free (a 403 or 409; API-GAP G64). */
class RoleNotRemovableException(val projectId: ProjectId, val roleId: RoleId) :
    IllegalStateException("Role ${roleId.value} of project ${projectId.value} cannot be removed")

    /**
     * Moves the project ([ProjectLifecycleAction] → action "3", "4", "5") with the cached revision. Only with
     * [ProjectsCapabilities.canChangeLifecycle].
     * @throws ProjectNotFoundException the project is gone (404)
     * @throws ProjectConflictException the server refused (409): stale revision or a move not allowed now; read again
     */
    suspend fun changeLifecycle(id: ProjectId, action: ProjectLifecycleAction): Project

    /**
     * Replaces the description of the project (`PUT`, without roles: API-GAP G63).
     * @throws ProjectNotFoundException (404) @throws ProjectConflictException (409; read again)
     */
    suspend fun updateProject(id: ProjectId, info: ProjectInfo): Project

    /**
     * Changes the title and the skills of the role; its places and description stay as the server has them.
     * @throws ProjectNotFoundException the project is gone
     * @throws ProjectConflictException 409, or the role is no longer there (404 with the project still there); read again
     */
    suspend fun updateRole(projectId: ProjectId, roleId: RoleId, role: RoleDraft): Role

    /**
     * Deletes the role and returns the project. A role that is already gone (404 while the project is there) counts as
     * deleted: the project read again is returned.
     * @throws ProjectNotFoundException the project is gone
     * @throws RoleNotRemovableException 403 or 409 (API-GAP G64); the project has been read again
     */
    suspend fun removeRole(projectId: ProjectId, roleId: RoleId): Project
```

**Use case'ы** (`usecase/`, по одному файлу на use case, с KDoc):

```kotlin
// usecase/ChangeProjectLifecycle.kt
sealed interface ChangeLifecycleResult {
    data class Changed(val project: Project) : ChangeLifecycleResult
    /** Not offered for the status (nothing was sent), or the server refused it (409; the project was read again). */
    data object NotAllowed : ChangeLifecycleResult
    data object NotFound : ChangeLifecycleResult
    data class Failure(val cause: Throwable) : ChangeLifecycleResult
}

/**
 * Takes the project from the cache (`getProject`; `null` → NotFound, failure → Failure); an action that is not in
 * `ProjectLifecycle.actionsFor(status)` → NotAllowed without a request; then `changeLifecycle`:
 * ProjectNotFoundException → NotFound, ProjectConflictException → NotAllowed, anything else → Failure.
 */
class ChangeProjectLifecycleUseCase(private val repository: ProjectsRepository) {
    suspend operator fun invoke(id: ProjectId, action: ProjectLifecycleAction): ChangeLifecycleResult
}

// usecase/UpdateProject.kt — normalized → validate (Invalid: nothing sent) → updateProject → Success / Failure(cause)
class UpdateProjectUseCase(private val repository: ProjectsRepository) {
    suspend operator fun invoke(id: ProjectId, draft: ProjectInfoDraft): SubmitResult<Project, ProjectDraftError>
}

// usecase/UpdateRole.kt — like AddRoleUseCase: normalized → validate → updateRole
class UpdateRoleUseCase(private val repository: ProjectsRepository) {
    suspend operator fun invoke(projectId: ProjectId, roleId: RoleId, draft: RoleDraft): SubmitResult<Role, RoleDraftError>
}

// usecase/RemoveRole.kt
sealed interface RemoveRoleResult {
    data class Removed(val project: Project) : RemoveRoleResult
    /** The role has taken places (nothing was sent), or the server refused (RoleNotRemovableException). */
    data object NotRemovable : RemoveRoleResult
    data object NotFound : RemoveRoleResult
    data class Failure(val cause: Throwable) : RemoveRoleResult
}

/**
 * `getProject` (`null` → NotFound); a role that is not in the project → Removed(project) without a request;
 * `occupied > 0` → NotRemovable without a request (API-GAP G64); else `removeRole`.
 */
class RemoveRoleUseCase(private val repository: ProjectsRepository) {
    suspend operator fun invoke(projectId: ProjectId, roleId: RoleId): RemoveRoleResult
}
```

**`usecase/UnpublishProject.kt`**: `NotPublished` теперь возвращается при `!ProjectLifecycle.canUnpublish(project.status)`; KDoc — «the project is not in a state it can be unpublished from». Остальное не меняется.

### 2.4 `feature:projectdetails:domain` (domain-data; входит в domain-ready)

```kotlin
// TeamMember.kt
/** A member of the project's team as "Команда" of 04 shows them. [roleTitle] is empty when unknown. */
data class TeamMember(
    val person: RoleMember,
    val roleTitle: String,
    val isViewer: Boolean,
)

// ProjectDetailsRepository.kt — add:
    /**
     * The team of [project] (its `members`) with their profiles, in the server's order. Never throws a `RemoteFailure`:
     * a person whose profile could not be read is a member without a profile.
     */
    suspend fun getTeam(project: Project): List<TeamMember>

// GetProjectTeamUseCase.kt
/**
 * "Команда" of 04: the members of [project] other than its initiator (by user id), each (person, role) once. Without such
 * members it returns an empty list and asks for nothing. Should the repository throw anyway (not a cancellation), the
 * members are returned without profiles, built from `project.members`.
 */
class GetProjectTeamUseCase(private val repository: ProjectDetailsRepository) {
    suspend operator fun invoke(project: Project): List<TeamMember>
}
```

### 2.5 `feature:profile:domain` и `feature:collaborators:domain` (domain-data; входят в domain-ready)

```kotlin
// profile/domain/ProfileModels.kt — UserProfile: last parameter
    /** The person said they are open to collaboration (`open_to_collaboration`; missing means no). */
    val openToCollaboration: Boolean = false,

// collaborators/domain/Collaborator.kt — CollaboratorFilters: new parameter after verifiedProfile
    /** "Открыт к сотрудничеству" of the group "Статус": sent as `open_only=true`. */
    val openOnly: Boolean = false,
// activeCount counts openOnly too.
```

### 2.6 data-модули (domain-data). Видимое снаружи — для DI

| Класс | Публичный конструктор | Что меняется |
|---|---|---|
| `GatewayProjectsDataSource` | `(api: ProjectsApi, catalog: ProjectCatalogApi, assembler: ProjectAssembler, team: ProjectTeamReader, viewerId: UserId)` | `canChangeLifecycle = true`. Реализует 4 новых метода (через мьютекс `writes`, ревизия из кэша, 404/409 — по 2.3 и разделу 3). Чтение одного проекта (`read`) и проект из ответа на запись (`projectAfterWrite`) идут через `withTeam(team.read(project.members))`. Список «Я инициатор» команду не читает. Кэш хранит `description` ролей из DTO и при `updateRole` возвращает его как есть (`null`, если пусто); `slots` берётся из кэша |
| `ProjectsDataSource` | интерфейс | зеркалит 4 новых метода репозитория |
| `ProjectsRepositoryImpl` | `(dataSource: ProjectsDataSource)` | передаёт 4 метода в источник |
| `GatewayProjectDetailsDataSource` | `(api: ProjectCatalogApi, assembler: ProjectAssembler, team: ProjectTeamReader)` | `getTeam(project)` = `team.read(project.members).map { TeamMember(it.toRoleMember(), it.member.roleTitle, it.member.isViewer) }` |
| `ProjectDetailsDataSource`, `ProjectDetailsRepositoryImpl` | без изменений конструктора | + `getTeam` |
| профиль: `ProfileRecord` | + `openToCollaboration: Boolean = false` | `toRecord`: `details.openToCollaboration == true`; `assembleProfile` передаёт значение в `UserProfile` |
| `GatewayCollaboratorsDataSource` | без изменений | `toParams`: `openOnly = filters.openOnly`; KDoc: `open_only` теперь отправляется |

### 2.7 ui-модули (compose-ui): видимое за пределами модуля

**`R/feature/projects/ui/.../ProjectsUiDependencies.kt`** — точный конструктор; DI собирает его по именам:

```kotlin
class ProjectsUiDependencies(
    val observeMyProjects: ObserveMyProjectsUseCase,
    val observeProject: ObserveProjectUseCase,
    val loadProject: LoadProjectUseCase,
    val publishProject: PublishProjectUseCase,
    val unpublishProject: UnpublishProjectUseCase,
    val addRole: AddRoleUseCase,
    val getSpheres: GetSpheresUseCase,
    val changeLifecycle: ChangeProjectLifecycleUseCase,
    val updateProject: UpdateProjectUseCase,
    val updateRole: UpdateRoleUseCase,
    val removeRole: RemoveRoleUseCase,
    val capabilities: ProjectsCapabilities,
)
```

Сигнатуры Route:

```kotlin
// management/ProjectManagementScreen.kt
@Composable
fun ProjectManagementRoute(
    dependencies: ProjectsUiDependencies,
    projectId: ProjectId,
    onNavigateBack: () -> Unit,
    onFindCollaborators: (ProjectId, RoleId) -> Unit,
    onEditProject: (ProjectId) -> Unit,
    onMemberClick: (UserId) -> Unit,
    modifier: Modifier = Modifier,
)

// edit/EditProjectScreen.kt (new): 04d. Saving successfully calls onClose (07 shows the changes from the cache).
@Composable
fun EditProjectRoute(
    dependencies: ProjectsUiDependencies,
    projectId: ProjectId,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
)
```

Поведение, которое проверит tester (внутренние имена — на усмотрение compose-ui):

- **`ProjectManagementViewModel`**:
  - 07c показывает действия `ProjectLifecycle.actionsFor(status)` только при `capabilities.canChangeLifecycle && viewerIsInitiator`; «Снять с публикации» — при `canUnpublish && viewerIsInitiator && ProjectLifecycle.canUnpublish(status)`.
  - У каждого действия диалог `AppConfirmDialog` (у «Отменить» — `destructive = true`). Подтверждение работает только при открытом диалоге и без активного запроса (страж `isChangingLifecycle`).
  - Исходы: `NotAllowed` → текст «Действие недоступно…»; `NotFound` → экран «Проект не найден»; `Failure` → `failureText`.
  - Баннеры статусов `Started`, `Finished`, `Cancelled`.
  - Строка «Редактировать проект» в 07c и карандаш в шапке 07 (только у инициатора) вызывают `onEditProject`.
  - 07b: участник роли открывает профиль, если `hasProfile`.
- **`EditProjectViewModel(projectId, observeProject, loadProject, updateProject, addRole, updateRole, removeRole, getSpheres)`**:
  - Форма заполняется один раз — из первого ненулевого проекта (`ProjectInfoDraft.of`). Список ролей потом следует за проектом из кэша, текстовые поля не перезаписываются.
  - «Сохранить»: страж `isSaving` держится и после успеха, пока экран не ушёл. Ошибки полей — как в 05.
  - Исходы сохранения: `ProjectConflictException` → баннер `projects_conflict`, форма сохраняется; `ProjectNotFoundException` → «Проект не найден»; прочее → `failureText`.
  - Роль: карандаш открывает `RoleEditorSheet`, предзаполненный, сохранение через `updateRole` под стражем.
  - Корзина только у роли с `occupied == 0` → диалог → `removeRole` под стражем. `NotRemovable` → «Роль нельзя удалить»; `NotFound` → «Проект не найден».
  - «Добавить роль» работает, как в 07.
- **Повторное использование**: поля формы 05 выносятся в общий internal-файл (например, `create/ProjectFormSections.kt`) и используются в 05 и 04d; `MonthPickerSheet`, `SpherePickerSheet`, `RoleEditorSheet` — как есть. В `RoleEditorState` можно добавить `editedRoleId: RoleId? = null`.

**`R/feature/projectdetails/ui/.../ProjectDetailsUiDependencies.kt`**:

```kotlin
class ProjectDetailsUiDependencies(
    val getProjectDetails: GetProjectDetailsUseCase,
    val getProjectTeam: GetProjectTeamUseCase,
)
```

```kotlin
@Composable
fun ProjectDetailsRoute(
    dependencies: ProjectDetailsUiDependencies,
    projectId: ProjectId,
    onNavigateBack: () -> Unit,
    onInitiatorClick: (UserId) -> Unit,
    onContactInitiator: (UserSummary) -> Unit,
    onMemberClick: (UserId) -> Unit,
    onContactMember: (UserSummary) -> Unit,
    modifier: Modifier = Modifier,
)

/** "Команда" of 04: hidden when the project has no members besides its initiator. */
sealed interface TeamUiState {
    data object Hidden : TeamUiState
    data object Loading : TeamUiState                                      // 04e
    data class Shown(val members: List<TeamMemberUiModel>) : TeamUiState   // the initiator first
}

@Immutable
data class TeamMemberUiModel(
    val user: UserSummary,
    val avatarKey: String,
    /** "Инициатор" is the screen's word when [isInitiator]; else the role title, empty when unknown. */
    val roleTitle: String,
    val rating: Double?,
    val completedProjects: Int,
    /** `false`: "Без имени", no stats line, no chat, not clickable (04f). */
    val hasProfile: Boolean,
    val isInitiator: Boolean,
    /** A profile, not the viewer, an id that is not blank. */
    val canContact: Boolean,
)
```

`ProjectDetailsUiModel` получает поле `team: TeamUiState`. `ProjectDetailsViewModel(projectId, getProjectDetails, getProjectTeam)`: после `Found` ставит `Loading`, если у проекта есть участники кроме инициатора (иначе `Hidden`), и запускает `getProjectTeam`. Строка инициатора собирается из `InitiatorInfo`; `onRetry` перечитывает и страницу, и команду.

**`profile:ui`**: `ProfileUiModel` + `openToCollaboration: Boolean`. При `true` у `ProfileHeaderCard.statuses` появляется `ProfileStatusUiModel(«Открыт к сотрудничеству», AppStatusChipTone.Success)` — и на 09, и на 10.

**`collaborators:ui`**: в группе «Статус» листа 08b добавляется чип «Открыт к сотрудничеству» → `draft.copy(openOnly = …)`.

### 2.8 `:components` (compose-ui)

- `ProjectStatus.label()` получает ветки `Started` / `Finished` / `Cancelled` и строки `components_project_status_started|finished|cancelled`. Без них `:components` не компилируется после изменения `:common`.
- Аватар неизвестного человека (Figma `60352:3709` md, `60352:3718` sm: круг `surfaceContainerHigh` + `AppIcons.User` в `onSurfaceVariant`). Рекомендация: `PersonCardUiModel.avatar: DrawableResource?`, где `null` рисует этот аватар; существующие вызовы не меняются. Альтернатива — отдельный компонент `UnknownAvatar(size)`. Добавить в `:catalog`.

### 2.9 `:shared`

Принадлежит domain-data, `di/*`:

```kotlin
// DataSourcesModule
@Provides @SessionScope
fun projectTeamReader(gateway: GatewaySession): ProjectTeamReader = DirectoryProjectTeamReader(gateway.users)

@Provides @SessionScope
fun projectsDataSource(gateway: GatewaySession, assembler: ProjectAssembler, team: ProjectTeamReader, session: Session): ProjectsDataSource =
    GatewayProjectsDataSource(gateway.projects, gateway.projectCatalog, assembler, team, viewerId = session.userId)

@Provides @SessionScope
fun projectDetailsDataSource(gateway: GatewaySession, assembler: ProjectAssembler, team: ProjectTeamReader): ProjectDetailsDataSource =
    GatewayProjectDetailsDataSource(gateway.projectCatalog, assembler, team)

// ProjectsModule.projectsUiDependencies: + changeLifecycle = ChangeProjectLifecycleUseCase(repository),
//   updateProject = UpdateProjectUseCase(repository), updateRole = UpdateRoleUseCase(repository),
//   removeRole = RemoveRoleUseCase(repository)
// ProjectDetailsModule: ProjectDetailsUiDependencies(getProjectDetails = …, getProjectTeam = GetProjectTeamUseCase(repository))
```

Принадлежит compose-ui, `ui/navigation/AppNavHost.kt`:

```kotlin
const val EditProject = "edit_project/{$ProjectArgument}"
fun editProject(id: ProjectId) = "edit_project/${id.value}"

// Routes.Project:
ProjectManagementRoute(…,
    onEditProject = { id -> navController.navigateOnce(entry, Routes.editProject(id)) },
    onMemberClick = { userId -> openProfile(entry, userId) })
// new composable(Routes.EditProject, arguments = projectId) { entry ->
EditProjectRoute(dependencies.projects, ProjectId(id), onClose = { navController.popBackStackOnce(entry) })
// Routes.ProjectDetails:
ProjectDetailsRoute(…,
    onMemberClick = { userId -> openProfile(entry, userId) },
    onContactMember = { user -> openChatWith(entry, user) })
```

`App.kt` и `SessionDependencies.kt` не меняются.

## 3. Ответ сервера → результат

| Вызов | 2xx | 400 | 401 | 403 | 404 | 409 | 5xx | Timeout / NoConnection |
|---|---|---|---|---|---|---|---|---|
| `GET /users/{id}/profile` (команда) | профиль | без профиля («Без имени») | сессия проверяется; строка без профиля | без профиля | без профиля | без профиля | без профиля, в кэш не идёт | без профиля, в кэш не идёт |
| `GET /users/{id}/profile` (09) | `openToCollaboration` из поля | как в спринте 1 | | | | | | |
| `GET /search/people?open_only=true` | страница | `failureText` + «Повторить» (как в спринте 1) | | | | | | |
| `POST …/transitions` 3/4/5 | `Changed`, проект в кэше, баннер и чип меняются | `Failure` → «Что-то пошло не так» | `Failure` + сессия | `Failure` | `NotFound` → «Проект не найден» | перечитать → `NotAllowed` → «Действие недоступно в текущем состоянии проекта» | `Failure` → «Сервис временно недоступен» | `Failure` → «Нет связи» / «Сервер не ответил» |
| `PUT /projects/{id}` | `Success` → закрыть 04d | `Failure` → `failureText` под кнопкой, форма сохраняется | `Failure` | `Failure` | `ProjectNotFoundException` → «Проект не найден» | перечитать → `ProjectConflictException` → баннер «Проект изменился на сервере» | `Failure` | `Failure` |
| `PUT …/roles/{role_id}` | `Success` → шит закрыт | `Failure` в шите | `Failure` | `Failure` | перечитать: проекта нет → «Проект не найден»; роли нет → конфликт | перечитать → конфликт в шите | `Failure` | `Failure` |
| `DELETE …/roles/{role_id}?expected_revision` | `Removed` | `Failure` | `Failure` | перечитать → `NotRemovable` → «Роль нельзя удалить» | перечитать: проекта нет → `NotFound`; роли нет → `Removed` | перечитать → `NotRemovable` | `Failure` | `Failure` |
| `POST …/roles` (04d) | как в 07 (спринт 1) | | | | | | | |
| Блок 0: `ProjectJoinsApi.*`, `removeMember`, `leave` | DTO | `RemoteFailure.Http(status)`; смысл кодов определят блоки 6–8 | | | | | | `RemoteFailure.Timeout` / `NoConnection` |

Отмена корутины нигде не превращается в сбой. Автоповторов нет (G48).

## 4. Кто какие файлы правит

| Каталог или файл | Владелец |
|---|---|
| `R/common/**` | domain-data |
| `R/gateway/**` (main) | domain-data |
| `R/feature/{projects,projectdetails,profile,collaborators}/{domain,data}/**` (main) | domain-data |
| `R/shared/src/commonMain/.../di/*` (`DataSourcesModule`, `ProjectsModule`, `ProjectDetailsModule`) | domain-data |
| `R/gateway/.../GatewaySession.kt` | domain-data |
| `R/feature/*/ui/**` (main, включая `…UiDependencies.kt` и `composeResources`) | compose-ui |
| `R/components/**`, `R/catalog/**` (`ComponentCatalog.kt`) | compose-ui |
| `R/shared/src/commonMain/.../ui/navigation/AppNavHost.kt` | compose-ui |
| Все `strings.xml` (values и values-ru, 1:1) | compose-ui |
| `*/commonTest/**` | tester; агенты тесты не компилируют и не правят |
| `R/docs/*` | никто; тексты — в отчётах |

## 5. Что удаляется

Моков у блоков 0–4 нет (`:mockserver`, `Backend.Mock` и моки проектов и профилей уже удалены). Удаляется только устаревшее:

- domain-data: KDoc и комментарии «members are not read (G52)» в `ProjectMapping.kt`, `GatewayProjectsDataSource.kt`, `Project.kt`; «3, 4, 5 are not used» в `ProjectActions`; «open_only is not sent» в `GatewayCollaboratorsDataSource`.
- compose-ui: KDoc «Editing the project and the lifecycle actions … belong to later sprints» в `ProjectManagementScreen.kt`; «The kick button and the chat button … need later stories» оставить только для «Выгнать» и чата (блок 8).
- Тесты, которые перестанут компилироваться (их чинит tester, не удаляет):
  - фейки `ProjectsRepository` (`R/feature/projects/domain/src/commonTest/.../TestFixtures.kt`, `R/feature/projects/ui/src/commonTest/.../ProjectsUiTestSupport.kt`);
  - фейки `ProjectsApi` (`R/feature/projects/data/src/commonTest/.../ProjectsTestSupport.kt`) и конструктор `GatewayProjectsDataSource`;
  - конструктор `GatewayProjectDetailsDataSource` и фейк `ProjectDetailsRepository` (`R/feature/projectdetails/{data,domain,ui}/src/commonTest/...`);
  - `ProjectsUiDependencies(...)` в тестах UI.

## 6. Порядок и синхронизация

Маркер: `S/sync/s2a.domain-ready`.

**domain-data**

1. **domain-ready**. Файлы:
   - `R/common/.../model/{Ids.kt, Project.kt, ProjectJoin.kt}`;
   - `R/feature/projects/domain/.../{ProjectsRepository.kt, ProjectsCapabilities.kt, ProjectDraft.kt, ProjectInfoDraft.kt, ProjectLifecycle.kt, usecase/ChangeProjectLifecycle.kt, usecase/UpdateProject.kt, usecase/UpdateRole.kt, usecase/RemoveRole.kt, usecase/UnpublishProject.kt}`;
   - `R/feature/projectdetails/domain/.../{TeamMember.kt, ProjectDetailsRepository.kt, GetProjectTeamUseCase.kt}`;
   - `R/feature/profile/domain/.../ProfileModels.kt`;
   - `R/feature/collaborators/domain/.../Collaborator.kt`.

   Скомпилировать (раздел 7, шаг 1), затем выполнить `mkdir -p S/sync && touch S/sync/s2a.domain-ready`. После маркера `:common` и domain меняются только совместимыми добавлениями, о каждом — в отчёте.
2. `:gateway`: DTO, `ProjectsApi`, `ProjectJoinsApi`, `ApiRequests`, `Ktor*`, mapping (`ProjectMapping`, `ProjectJoinMapping`, `ProjectTeam`), `ProjectActions`, `GatewaySession`.
3. data-модули `projects`, `projectdetails`, `profile`, `collaborators`.
4. `:shared/di` (сам не компилирует, это делает оркестратор).
5. iOS-компиляция data-модулей.

**compose-ui**

- До маркера: строки всех модулей, `UnknownAvatar` или `PersonCardUiModel.avatar?`, вёрстка 04d, 07c, блока «Команда», чипов 09 и 08b — по контрактам. Ничего не компилирует: до маркера `:common` может быть в промежуточном состоянии, и даже `:components` может не собраться.
- После маркера (`test -f S/sync/s2a.domain-ready` между шагами): `ProjectLabels`, ViewModel-и, Route-ы, `AppNavHost`, `:catalog`; компиляция из раздела 7.

Параллелить можно все четыре блока внутри каждого агента, кроме `projects:ui` (блоки 3 и 4 правят один `ProjectManagementScreen`): его делать одним проходом.

## 7. Команды проверки (только компиляция)

`G=S/tools/gw.sh` (сериализует запуски Gradle). Тесты, `:shared` и `assembleDebug` агенты не запускают.

- domain-data, шаг 1: `$G :common:compileAndroidMain :feature:projects:domain:compileAndroidMain :feature:projectdetails:domain:compileAndroidMain :feature:profile:domain:compileAndroidMain :feature:collaborators:domain:compileAndroidMain --console=plain`
- domain-data, шаги 2–3: `$G :gateway:compileAndroidMain :feature:projects:data:compileAndroidMain :feature:projectdetails:data:compileAndroidMain :feature:profile:data:compileAndroidMain :feature:collaborators:data:compileAndroidMain --console=plain`
- domain-data, iOS: `KONAN_DATA_DIR=S/konan-data $G :gateway:compileKotlinIosSimulatorArm64 :feature:projects:data:compileKotlinIosSimulatorArm64 :feature:projectdetails:data:compileKotlinIosSimulatorArm64 :feature:profile:data:compileKotlinIosSimulatorArm64 :feature:collaborators:data:compileKotlinIosSimulatorArm64 --console=plain`
- compose-ui, после маркера: `$G :components:compileAndroidMain :feature:projects:ui:compileAndroidMain :feature:projectdetails:ui:compileAndroidMain :feature:profile:ui:compileAndroidMain :feature:collaborators:ui:compileAndroidMain :catalog:compileAndroidMain --console=plain`
- compose-ui, iOS: те же модули с `compileKotlinIosSimulatorArm64` и `KONAN_DATA_DIR=S/konan-data`.

Имена задач `compileAndroidMain` и `compileKotlinIosSimulatorArm64` подтверждены логами прошлых сборок (`S/ui-p45-compile2.log`, `S/p45dd-ios2.log`).

## 8. Что должен покрыть tester (критичное)

- **`:common`**:
  - `ProjectStatus.fromCode` для "3", "4", "5", " 4 ", "6", "", `null`;
  - `ProjectJoinType.fromCode` и `ProjectJoinStatus.fromCode`: все коды и неизвестные;
  - `Project.viewerIsMember`;
  - значения `RoleMember` по умолчанию.
- **`:gateway`** (на `ktor-client-mock`):
  - 5 новых вызовов `ProjectsApi`: метод, путь с экранированием обоих id, `expected_revision` в query у DELETE, тело PUT в snake_case без `roles` и `id`, с `tags: []`;
  - 6 вызовов `ProjectJoinsApi`: пути, `role_id` и `status` не уходят, если `null` или пустые, `limit` и `offset` уходят, тело `{"accept":true}`, разбор ответа DELETE;
  - разбор DTO: `members`, `pending_join_count`, `description` роли, `total` и `created_at_unix` строкой и числом;
  - `toProject`: участник без `user_id` отброшен, fallback названия роли, `isViewer`, повторы, отрицательный `pendingJoinCount` → 0;
  - `toProjectJoin`: пустые id → `null`, коды, `createdAt` при 0 → `null`; `toProjectJoinPage`: `received` и `items`;
  - `DirectoryProjectTeamReader`: каждый id один раз, сбой одного → `null` только у него, отмена пробрасывается, порядок сохраняется, пустой список не шлёт запросов;
  - `withTeam`, `toRoleMember` без профиля;
  - значения `ProjectActions`.
- **`projects:domain`**:
  - `ProjectLifecycle`: все 6 статусов × действия и `canUnpublish`;
  - `ChangeProjectLifecycleUseCase`: все ветки, включая «нет запроса при недопустимом действии»;
  - `ProjectInfoDraft.of` с `null`-полями, `validate` и `normalized`; регрессия `ProjectDraft.validate`;
  - `UpdateProjectUseCase` и `UpdateRoleUseCase`: Invalid без вызова;
  - `RemoveRoleUseCase`: при `occupied > 0` и при отсутствующей роли запросов нет, отображение исключений в исходы;
  - `UnpublishProjectUseCase` при `Started`, `Finished`, `Cancelled` → `NotPublished`.
- **`projects:data`**:
  - `changeLifecycle`: коды 3/4/5 и ревизия из кэша, 404, 409 (перечитать), прочее;
  - `updateProject`: тело и кэш, 409;
  - `updateRole`: `slots` и `description` из кэша, 404 → перечитать (нет проекта или нет роли);
  - `removeRole`: query, 403/409 → перечитать + `RoleNotRemovableException`, 404 → `Removed` или `NotFound`;
  - записи сериализованы (две подряд шлют свежую ревизию);
  - `read` и запись заполняют `Role.members`, список «Я инициатор» читатель команды не вызывает.
- **`projectdetails`**:
  - `GetProjectTeamUseCase`: инициатор исключён, пустой список без вызова, fallback при исключении;
  - `getTeam` в data;
  - ViewModel: `Hidden` / `Loading` / `Shown`, «Повторить» перечитывает команду.
- **`profile`**: `openToCollaboration` при отсутствии поля, `null`, `false`, `true`; чип в `ProfileUiModel`.
- **`collaborators`**: `openOnly` → `open_only=true`, `activeCount`, сброс фильтров.
- **ViewModel `projects:ui`**: защита от повторного входа в каждом изменяющем действии (lifecycle, сохранение, роль, удаление; страж держится после успеха сохранения); набор действий по статусам и флагу; переходы в «не найден», конфликт и `NotAllowed`; предзаполнение 04d ровно один раз.
- **`:shared`** (graph-тесты): `ProjectTeamReader` — один на сессию; `ProjectsUiDependencies` и `ProjectDetailsUiDependencies` собираются.

## 9. Допущения, открытые решения, пробелы API

**Допущения (для `docs/sprint-2.md`, «Пробелы дизайна и допущения»)**

1. Граф переходов 07c — таблица из 2.3. При неизвестном статусе предлагаются все действия, сервер решает (409 → «Действие недоступно…»). Подтверждения — `AppConfirmDialog`, у «Отменить» он разрушающий. Тексты диалогов и баннеров наши, пока нет дизайна (пункты 5 и 6).
2. «Снять с публикации» предлагается только при статусе `Recruiting` или неизвестном.
3. Блок «Команда» на 04 виден всем; инициатор идёт первым, участники — без инициатора; блок скрыт, если других участников нет. Участник без профиля — «Без имени», без рейтинга, чата и тапа (пункт 8, 04e и 04f).
4. 04d: роли меняются сразу отдельными запросами, описание — по «Сохранить изменения»; уход без сохранения ничего не спрашивает; `slots` и `description` роли не редактируются; корзина только у роли с `occupied = 0`; удаление с подтверждением (пункты 10 и 5 ещё не нарисованы).
5. Чип «Открыт к сотрудничеству» показывается и на 09, и на 10.
6. Новые тексты (ru / en):
   - «В работе» / In progress, «Завершён» / Finished, «Отменён» / Cancelled;
   - «Начать проект» / Start the project, «Завершить проект» / Finish the project, «Отменить проект» / Cancel the project;
   - «Проект в работе» / The project is in progress, «Проект завершён» / The project is finished, «Проект отменён» / The project is cancelled;
   - «Действие недоступно в текущем состоянии проекта» / This action is not available in the project's current state;
   - «Редактировать проект» / Edit the project, «Сохранить изменения» / Save changes;
   - «Удалить роль?» / Delete the role?, «Роль нельзя удалить» / The role cannot be deleted;
   - «Команда» / Team, «Открыт к сотрудничеству» / Open to collaboration.

**Открытые решения**

- Коды `ProjectStatus` 3–5 и `ProjectJoinType` / `ProjectJoinStatus` приняты по решению оркестратора и сервером не подтверждены. Альтернатива — `null` до ответа бэкенда.
- Аватар неизвестного человека: рекомендован `PersonCardUiModel.avatar: DrawableResource?`; альтернатива — отдельный `UnknownAvatar`.

**Пробелы API блока**: G47, G48, G52 (участники читаются, статус — нет), G55, G58 и G59 (коды наши, не подтверждены), G60, G61 (`pending_join_count` читается, смысл неизвестен), G62 (статусы 3–5 наши, граф — допущение), G63, G64, G65, G66.

**Строки для `docs/api-values.md`** (дают агенты в отчётах, оркестратор вносит):

- «Статус проекта: `3` Started «В работе», `4` Finished «Завершён», `5` Cancelled «Отменён» (не подтверждено)».
- «Действие перехода: `3` начать, `4` завершить, `5` отменить — используются в 07c».
- «Тип отклика `ProjectJoinRequest.type`: `1` Application, `2` Invitation; статус `ProjectJoinRequest.status`: `1` Pending, `2` Accepted, `3` Declined, `4` Withdrawn (коды чтения приложения, не подтверждены; `ProjectJoinType`, `ProjectJoinStatus` в `:common`)».

**Строки для `docs/api-gaps.md`**:

- «G52 — частично: `members` читаются (`ProjectMember`, `Role.members` у одного проекта), `status` участника не читается».
- «G58, G59 — значения заданы кодами чтения приложения (`api-values.md`), не подтверждены».
- «G62 — статусы 3–5 заданы кодами приложения; граф переходов — допущение `ProjectLifecycle`».

Проверок не было: Gradle не запускался, на сервере ничего не проверялось, визуальная проверка на устройстве не выполнялась.