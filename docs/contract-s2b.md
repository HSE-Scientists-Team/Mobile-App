# Спринт 2, блоки 5–8: контракты между слоями для параллельной работы domain-data и compose-ui

Экраны: 17, 17b–17g, 09c, 09j, 13b (блок 5); 04 «Откликнуться» и 04s–04x, 04c «Покинуть проект» и 04g–04i, 04y, 15, 15c–15p, 06b/06d «Я участник» (блоки 6 и 7); 07d и 07m–07t, вход «Отклики · N» на 07/07b/07c, 06e, 07b «Выгнать» и 07i, 07u–07w, 09 «Пригласить в проект» и 09k–09q (блок 8).
Корень репозитория — `R = /Users/samvel/Desktop/Study/Methodology/AcademicNet`, скретчпад — `S = /private/tmp/claude-501/-Users-samvel-Desktop-Study-Methodology-AcademicNet/62f647b7-10a2-490f-83d6-d4b77bca2823/scratchpad`.

Документ — единственный источник правды о границе между слоями. Оба агента реализуют его буквально. Если чего-то не хватает, агент выбирает простое решение внутри своего слоя и пишет о нём в отчёте. Чужой слой не правится. Всё из `R/docs/contract-s2a.md` (`ProjectJoin`, `ProjectJoinType/Status`, `ProjectJoinsApi`, `ProjectsApi.removeMember/leave`, `ProjectTeamReader`, `Project.members/pendingJoinCount/viewerIsMember`, `RoleMember.hasProfile`, статусы 3–5) считается готовым и не меняется.

**Предусловие.** Блоки 5–8 начинаются после того, как реализация блоков 0–4 собрана и закоммичена. Причина — общие файлы: `ProjectManagementScreen.kt`, `ProjectDetails*`, `ProjectsRepository.kt`, `AppNavHost.kt`, `DataSourcesModule.kt`, `ProjectsModule.kt`, `ProjectDetailsModule.kt`.

## 0. Что нового по сравнению с прежними проектами

Прежние проекты: `R/docs/sprint-2.md` (блоки 5–8) и `R/docs/gateway-blocks3-5-search-and-projects.md` (§3.2 «Отклики»: строковые значения `application`/`pending`…).

| # | Было | Стало | Почему |
|---|---|---|---|
| 1 | `type`/`status` отклика — строки `application`, `pending`… (gateway-blocks3-5 §3.2) или `null` (sprint-2, G58/G59). На 15 один список без действий (15h) | Коды чтения из s2a. На 15 две вкладки (15g/15c), действия строятся по типу и статусу. У записи с `type = null` нет действий, она стоит во вкладке «Отклики» | Решение оркестратора (s2a). Строковые синонимы отменены правилом `api-values.md` |
| 2 | 07d — в `responses` (sprint-2), `CandidateCard` — в `projects:ui` | 07d — `responses:ui`. `CandidateCard` переезжает в `responses:ui` | SRP: всё о записях `ProjectJoin` живёт в одной фиче. `projects:ui` карточку кандидата больше не использует |
| 3 | Списки откликов — страницами по 20, счётчики фильтра 07d — по загруженным страницам | Оба списка читаются целиком в кэш сессии: страницы по 100, не больше 10 (как «Я инициатор»). Экраны работают с локальным списком | Числа во вкладках 15g, в чипах ролей 07d/07r, точка на вкладке и «Вы откликнулись» на 04 нуждаются во всём списке. Это не поиск, правило «страницы по 20» к нему не относится. Альтернатива — в §9 |
| 4 | «Я участник» (G67) описан словами | Порт `ParticipationSource` в `projects:domain`, адаптер в `:shared` поверх `MyJoinsRepository` | Правило «фича не зависит от фичи» |
| 5 | «Вы откликнулись» на 04 не проектировался | Порт `AppliedRolesSource` в `projectdetails:domain`, адаптер в `:shared` (поток из кэша откликов) | После отклика 04x обновляется сам, без связи через навигацию |
| 6 | Названия проектов и ролей для карточек брались бы из `GET /projects/{id}` в `responses:data` | Порт `ResponsesProjectSource` в `responses:domain`, адаптер `ProjectsResponsesProjectSource` в `:shared` поверх `ProjectsRepository` (кэш, «мои проекты», перечитывание после решения) | Один кэш проектов. Логика «мои проекты» не дублируется. После «принять» 07 видит новую занятость |
| 7 | `ComplaintsApi.create` возвращает DTO | Возвращает `Unit`: ответ `{id, status, created_at}` не читается | Приложению он не нужен. Нечитаемый ответ на созданную жалобу не должен становиться ошибкой и вызывать повтор (G48) |
| 8 | Ответ на решение или отзыв с неизвестным `status` | Если код в ответе не распознан, запись получает статус по действию (`Accepted`, `Declined`, `Withdrawn`) | Иначе ✓ × вернулись бы на уже решённую запись. Это знание приложения о своём действии, а не догадка о значении сервера |
| 9 | Флаги возможностей у блоков 5–8 | Новых `…Capabilities` нет | Все ручки есть. Пробелы G58–G68 не запрещают операцию, а только делают смысл неточным |

**Переиспользуется**: `ProjectJoinsApi`, `ProjectsApi.removeMember/leave`, `toProjectJoin`/`toProjectJoinPage`, `UserDirectory`, `ProjectAssembler`, `UserProfileDto.toCollaborator()`, `instantOfUnixSeconds` (`:gateway`); `Project.viewerIsMember/pendingJoinCount/vacantRoles`, `Collaborator`, `LoadResult`, `SubmitResult` (`:common`); `ApplicationCard` (`responses:ui`); `ProjectCard`, `RoleApplyCard`, `AcceptDeclineActions`, `AppConfirmDialog`, `AppBottomSheet`, `RadioListItem`, `InfoBanner`, `AppStatusChip` (тоны `Pending`, `Success`, `Cancelled`, `Off`), `ReportIconButton`, `AppNavigationBarItem(badge)`, `LoadingState`, `MessageState`, `failureText`, `UnknownAvatar` (`:components`); `ParticipantManageCard` (`projects:ui`); `ExclusiveLauncher`, `navigateOnce` (`:shared`).

## 1. Как работают блоки

| Блок | Экран (кадры) | Что делает пользователь | Сервер | Что отключено или условно |
|---|---|---|---|---|
| 5 | 09 → 09c (`59421:1445`) → 17 (`59365:122`) | Флажок в шапке чужого профиля → шит «Пожаловаться на пользователя» → форма: причина (6 кодов), «Что произошло *» → «Отправить жалобу» → 17e «Жалоба отправлена» → «Готово» | `POST /complaints {subject: "1".."6", description, target_user_id}` | Вложений нет (G68). Блокировки нет (3-й спринт): переключатель на 17, «Заблокировать» на 09c/13b и иконка запрета не показываются. Флажка нет на своём профиле |
| 5 | 04 → 09j (`60354:6409`) → 17b (`60354:5813`) | То же для проекта: 4 причины, карточка проекта «название · инициатор · вуз» | `{subject: "1".."4", target_project_id}` | Флажка нет на своём проекте |
| 5 | 13 → меню «⋮» 13b (`59421:1473`) → 17 | «Пожаловаться» на собеседника | как у профиля | Пункта «Заблокировать» нет |
| 5 | 17c/17d/17f/17g | Ошибки полей, отправка, 403 «Подтвердите почту…», прочие сбои «Не удалось отправить жалобу…» | | Повтор создаёт новую жалобу (G48): страж `isSubmitting` |
| 6 | 04 (`59351:51`) → шит 04s (`60354:6437`) | «Откликнуться» на свободной роли → шит: роль, «Сообщение (необязательно)», счётчик «N / 500» → «Отправить отклик» → шит закрыт, на роли «Вы откликнулись» (04x) | `POST /projects/{id}/applications {project_id, role_id, message?}` | Кнопки нет у инициатора, у участника, при статусе проекта, отличном от `Recruiting` или `null`. Предел 500 клиентский (G60) |
| 6 | 04c → 04y → 04g/04h/04i | Участник: «Покинуть проект» → диалог → выполнение → баннер «Вы покинули проект», команда без него | `DELETE /projects/{id}/membership` | G65: последствия не описаны |
| 6, 7 | Вкладка «Отклики»: 15 (`59364:155`), вкладки «Отклики · N» и «Приглашения · N» | Свои отклики: чип статуса, «Отозвать отклик» (диалог 15n) у ожидающих, чат с инициатором. Приглашения (15l): «Роль: …», сообщение, ✓ (без диалога), × (диалог 15o), чат. Архив (15m) — без ✓ ×. Тап по записи открывает 04 | `GET /project-joins` (весь список), `DELETE /project-joins/{id}`, `POST /project-joins/{id}/decision` | Запись, проект которой не виден (400/403/404), не показывается. Запись без `type` — во вкладке «Отклики», без действий (G58) |
| 7 | Навигация (`59344:134`, `showDot`), 15p | Точка на вкладке «Отклики», пока есть ожидающее приглашение новее последнего просмотра 15 | тот же список; в начале сессии читается один раз | Push и экрана «Уведомления» нет (G61). «Последний просмотр» живёт в графе сессии |
| 6 | 06b «Я участник» (06d `60354:4647`) | Проекты, где пользователь участник; тап открывает 04 (вариант 04c) | `GET /project-joins` → записи `Accepted` или `null` → `GET /projects/{id}` каждого → только `viewerIsMember` | G67: до N+1 запросов при каждом открытии вкладки |
| 8 | 07/07b/07c → строка «Отклики · N» → 07d (`59352:181`) | Список откликов на роли проекта: чипы «Все · N» и «Роль · n», `Card/Candidate`, ✓ × (без диалога), чат, тап открывает профиль. После «принять» — баннер 07q, у решённых записей чип 07p | `GET /projects/{id}/joins` (весь список), `POST /project-joins/{id}/decision {accept}` | N на 07 — `pendingJoinCount` сервера (G61). Внутри 07d N — ожидающие и неизвестные записи. У роли без мест ✓ выключена, показан баннер 07r. Приглашения инициатора в 07d не показываются |
| 8 | 06 (06e `60356:7740`) | Число на карточке проекта | `pendingJoinCount` | 0 — без бейджа |
| 8 | 07b → «Выгнать» → 07i → 07u/07v | Исключение участника | `DELETE /projects/{id}/members/{user_id}` | У инициатора и у самого пользователя кнопки нет. 403/409 — «Действие недоступно». Подпись «статус неизвестен» (07w) не показывается: статус не читается ни у кого (G52) |
| 8 | 09 «Пригласить в проект» → шит 09k (`60356:7947`) → 09q | Проект (radio), роль (свободные), сообщение → «Отправить приглашение» → шит закрыт, баннер «Приглашение отправлено». Из 08 с ролью открывается тот же шит с выбранной ролью | `POST /projects/{id}/invitations {project_id, role_id, candidate_user_id, message?}` | Проекты — «Я инициатор» со статусом `Recruiting` или `null` и свободной ролью. Нет таких — 09m с «Создать проект». Кнопки нет на своём профиле |

## 2. Контракты

### 2.1 `:common` и `:utils` (domain-data; `:utils` входит в domain-ready)

`:common` не меняется.

**`R/utils/src/commonMain/kotlin/org/matchlab/academic_net/utils/ElapsedDays.kt`** — новый файл:

```kotlin
package org.matchlab.academic_net.utils

import kotlinx.datetime.TimeZone
import kotlin.time.Instant

/**
 * How many calendar days in [zone] lie between the day of [since] and the day of [now]: 0 on the same day, 1 the day
 * after. A [since] later than [now] (clock skew) is 0. The UI words it ("сегодня", "2 дня назад").
 */
fun elapsedDays(since: Instant, now: Instant, zone: TimeZone): Int
```

### 2.2 `:gateway` (domain-data; в domain-ready не входит)

Новые файлы, которые нужны только жалобам. Всё нужное откликам уже есть с блока 0.

**`ComplaintsApi.kt`**:

```kotlin
package org.matchlab.academic_net.gateway

import org.matchlab.academic_net.gateway.dto.CreateComplaintDto

/** Complaints to the moderators. Throws `RemoteFailure` when it fails and lets a `CancellationException` through. */
interface ComplaintsApi {
    /**
     * `POST /api/v1/complaints` → 201. The answer (`CreatedComplaint`) is not read: the app needs nothing of it, and an
     * answer it could not read must not turn a complaint that was made into a failure (API-GAP G48: a repeat makes a
     * second complaint).
     */
    suspend fun create(input: CreateComplaintDto)
}
```

**`dto/ComplaintDtos.kt`**:

```kotlin
/**
 * `CreateComplaint`. [subject] is the code of the reason (`docs/api-values.md`; API-GAP G68), [description] the text
 * "Что произошло". Exactly one target is set; the other is `null` and not sent. [fileIds] stays empty (G68: no Media).
 */
@Serializable
data class CreateComplaintDto(
    val subject: String,
    val description: String,
    @SerialName("target_user_id") val targetUserId: String? = null,
    @SerialName("target_project_id") val targetProjectId: String? = null,
    @SerialName("file_ids") val fileIds: List<String> = emptyList(),
)
```

**`GatewaySession.kt`** — добавить:

```kotlin
    /** Complaints about a person or a project. */
    val complaints: ComplaintsApi = KtorComplaintsApi(requests)
```

**`internal/KtorComplaintsApi.kt`** — `requests.postWithoutAnswer("complaints", input)`.

### 2.3 `feature:responses:domain` (domain-data; новый модуль; входит в domain-ready)

Пакет `org.matchlab.academic_net.feature.responses.domain`. `build.gradle.kts` — копия `feature/projects/domain` (namespace `…feature.responses.domain`, `api(":common")`, `implementation(":utils")`, `api(coroutines)`, `withHostTest {}`).

**`ResponsesModels.kt`**:

```kotlin
/** The longest text of an application or an invitation the app sends. API-GAP G60: the API names no limit. */
const val JoinMessageMaxLength = 500

/**
 * The candidate of an application as 07d shows them.
 *
 * @property person name, university, study and field; without a profile the candidate's id with an empty name
 * @property skills the skills of the profile; empty without one
 * @property hasProfile the public profile could be read (`false`: "Без имени", no chat, no tap)
 */
data class Candidate(
    val person: Collaborator,
    val skills: List<String>,
    val hasProfile: Boolean,
)

/** A record of the initiator's project with its candidate; [candidate] is `null` when the server named none (G55). */
data class ProjectResponse(
    val join: ProjectJoin,
    val candidate: Candidate?,
)

/** A record of the viewer with the project it is about; [role] is `null` when the project no longer has the role. */
data class MyResponse(
    val join: ProjectJoin,
    val project: Project,
    val role: Role?,
)

/** The initiator's project with its applications (invitations left out), as 07d shows them. */
data class ProjectResponses(
    val project: Project,
    val responses: List<ProjectResponse>,
)

/** Why the server refused an action on an application or an invitation. */
enum class JoinRefusal {
    /** 403: no rights, a blocked account or an e-mail that is not confirmed. */
    NotAllowed,

    /** 409: the role is taken, the record is decided already, or there is such a record. */
    Conflict,

    /** 404: the project, the role or the record is gone. */
    NotFound,
}

/** The server refused (403, 404, 409); [refusal] says how. Other failures are thrown as the `RemoteFailure` they are. */
class JoinRefusedException(val refusal: JoinRefusal) : IllegalStateException("Refused: $refusal")

/** The viewer may not see the applications of the project (403) or it is gone (404). */
class ProjectResponsesUnavailableException(val projectId: ProjectId) :
    NoSuchElementException("Responses of project ${projectId.value} are not available")
```

**`MyJoinsRepository.kt`**:

```kotlin
/**
 * The viewer's own side (15, 15c): their applications and the invitations they got. One list per session, read whole
 * (pages of 100, at most 10; API-GAP G66: the server's order, a repeat of an id dropped).
 */
interface MyJoinsRepository {
    /** The list as this session knows it: `null` until the first read succeeded. Never reads, never fails. */
    fun observeMyJoins(): Flow<List<ProjectJoin>?>

    /** Reads the whole list again into [observeMyJoins] and returns it. Throws on failure; the known list stays. */
    suspend fun refreshMyJoins(): List<ProjectJoin>

    /**
     * `POST /projects/{id}/applications` with the project, the role and [message] (`null`: none). The record is added
     * to the list. @throws JoinRefusedException 403, 404, 409
     */
    suspend fun apply(projectId: ProjectId, roleId: RoleId, message: String?): ProjectJoin

    /** Takes the viewer's application back; the record in the list is replaced. @throws JoinRefusedException */
    suspend fun withdraw(id: ProjectJoinId): ProjectJoin

    /** Accepts or declines an invitation; the record in the list is replaced. @throws JoinRefusedException */
    suspend fun decide(id: ProjectJoinId, accept: Boolean): ProjectJoin

    /** The time of the newest invitation the viewer has seen on 15; `null` before any. API-GAP G61. */
    fun observeInvitationsSeenAt(): Flow<Instant?>

    /** Moves [observeInvitationsSeenAt] to [upTo] unless it is later already. */
    fun markInvitationsSeen(upTo: Instant)
}
```

**`ProjectJoinsRepository.kt`**:

```kotlin
/** The initiator's side (07d, 09k). The list of each project is read whole, like [MyJoinsRepository]. */
interface ProjectJoinsRepository {
    /** The records of the project with their candidates: `null` until read. Never reads, never fails. */
    fun observeProjectResponses(projectId: ProjectId): Flow<List<ProjectResponse>?>

    /**
     * Reads all the records of the project again, with the candidates' profiles (one request per person, G47; a person
     * whose profile cannot be read has `hasProfile = false`). @throws ProjectResponsesUnavailableException 403 or 404
     */
    suspend fun refreshProjectResponses(projectId: ProjectId): List<ProjectResponse>

    /** Accepts or declines an application; the record in the list is replaced. @throws JoinRefusedException */
    suspend fun decide(id: ProjectJoinId, accept: Boolean): ProjectJoin

    /** `POST /projects/{id}/invitations` with the role, [candidateId] and [message]. @throws JoinRefusedException */
    suspend fun invite(projectId: ProjectId, roleId: RoleId, candidateId: UserId, message: String?): ProjectJoin
}
```

Одна реализация в data обслуживает оба интерфейса. `decide` обновляет запись с этим `id` во всех кэшах, где она есть.

**`ResponsesProjectSource.kt`** — порт, адаптер в `:shared`:

```kotlin
/** What the responses need to know about projects; the app implements it over the projects feature. */
interface ResponsesProjectSource {
    /** The project as this session last read it, else read now; `null`: the viewer may not see it. Throws a failure. */
    suspend fun getProject(id: ProjectId): Project?

    /** The project as the session knows it, following its changes; `null` until read. Never fails. */
    fun observeProject(id: ProjectId): Flow<Project?>

    /** Reads the project again, after a decision changed its team. Never throws (but a cancellation). */
    suspend fun refreshProject(id: ProjectId)

    /** The viewer's own projects ("Я инициатор"). Throws a failure, `FeatureUnavailableException` included. */
    suspend fun getMyProjects(): List<Project>
}
```

**Use case'ы** (`usecase/`, по одному файлу, с KDoc):

```kotlin
// usecase/JoinActionResult.kt
sealed interface JoinActionResult {
    data class Done(val join: ProjectJoin) : JoinActionResult
    /** The role has no free place: nothing was sent (only [DecideApplicationUseCase] with accept). */
    data object RoleFull : JoinActionResult
    data class Refused(val refusal: JoinRefusal) : JoinActionResult
    data class Failure(val cause: Throwable) : JoinActionResult
}

/** 15: the viewer's records with their projects. Each distinct project is asked once (`getProject`, in parallel); a
 * record whose project is `null` is left out; a failure of a project read fails the flow. `null` until the list is read. */
class ObserveMyResponsesUseCase(private val joins: MyJoinsRepository, private val projects: ResponsesProjectSource) {
    operator fun invoke(): Flow<List<MyResponse>?>
}

/** Reads the viewer's list again; a cancellation passes through. */
class RefreshMyResponsesUseCase(private val joins: MyJoinsRepository) {
    suspend operator fun invoke(): Result<Unit>
}

/** "Отозвать отклик": `withdraw`; Refused → the list is read again (best effort). */
class WithdrawApplicationUseCase(private val joins: MyJoinsRepository) {
    suspend operator fun invoke(join: ProjectJoin): JoinActionResult
}

/** ✓/× of an invitation: `decide`; Done with accept → `projects.refreshProject`; Refused → the list is read again. */
class DecideInvitationUseCase(private val joins: MyJoinsRepository, private val projects: ResponsesProjectSource) {
    suspend operator fun invoke(join: ProjectJoin, accept: Boolean): JoinActionResult
}

/**
 * The dot of the "Отклики" tab: an invitation (type Invitation, status Pending or `null`) with a `createdAt` later than
 * `observeInvitationsSeenAt` (or any, when nothing was seen). A record without a time is never new. Never fails.
 */
class ObserveHasNewInvitationsUseCase(private val joins: MyJoinsRepository) {
    operator fun invoke(): Flow<Boolean>
}

/** 15 was shown with [shown]: marks seen up to the newest `createdAt` of its invitations; none → nothing. */
class MarkInvitationsSeenUseCase(private val joins: MyJoinsRepository) {
    operator fun invoke(shown: List<ProjectJoin>)
}

/** The role of the sheet 04s. NotFound: the project is `null` or has no such role. */
data class RoleToApply(val project: Project, val role: Role)
class GetRoleToApplyUseCase(private val projects: ResponsesProjectSource) {
    suspend operator fun invoke(projectId: ProjectId, roleId: RoleId): LoadResult<RoleToApply>
}

enum class JoinMessageError { TooLong }

/** Trims [message]; longer than [JoinMessageMaxLength] → Invalid, nothing sent; blank → sent without it. */
class ApplyToRoleUseCase(private val joins: MyJoinsRepository) {
    suspend operator fun invoke(projectId: ProjectId, roleId: RoleId, message: String): SubmitResult<ProjectJoin, JoinMessageError>
}

/** 07d: `observeProject` and `observeProjectResponses` together; both known → the applications (type Invitation left out). */
class ObserveProjectResponsesUseCase(private val joins: ProjectJoinsRepository, private val projects: ResponsesProjectSource) {
    operator fun invoke(projectId: ProjectId): Flow<ProjectResponses?>
}

sealed interface RefreshResponsesResult {
    data object Done : RefreshResponsesResult
    /** The project is `null`, or the server refused the list (ProjectResponsesUnavailableException). */
    data object NotFound : RefreshResponsesResult
    data class Failure(val cause: Throwable) : RefreshResponsesResult
}
/** `getProject` (null → NotFound), then `refreshProjectResponses`. */
class RefreshProjectResponsesUseCase(private val joins: ProjectJoinsRepository, private val projects: ResponsesProjectSource) {
    suspend operator fun invoke(projectId: ProjectId): RefreshResponsesResult
}

/**
 * ✓/× of 07d. With accept: the role of [join] in `getProject` that is not vacant → RoleFull without a request. Done →
 * `projects.refreshProject` (occupancy, `pendingJoinCount`); Refused → `refreshProjectResponses` (best effort).
 */
class DecideApplicationUseCase(private val joins: ProjectJoinsRepository, private val projects: ResponsesProjectSource) {
    suspend operator fun invoke(join: ProjectJoin, accept: Boolean): JoinActionResult
}

/** A project of the sheet 09k with the roles one can be invited to. */
data class InviteProject(val project: Project, val roles: List<Role>)

/**
 * `getMyProjects`: the viewer's (viewerIsInitiator) projects with status Recruiting or `null` and at least one vacant
 * role, in their order, each with its vacant roles. FeatureUnavailableException → Found(empty). Never NotFound.
 */
class GetInviteOptionsUseCase(private val projects: ResponsesProjectSource) {
    suspend operator fun invoke(): LoadResult<List<InviteProject>>
}

data class InvitationDraft(val projectId: ProjectId? = null, val roleId: RoleId? = null, val message: String = "")
enum class InvitationError { ProjectMissing, RoleMissing, MessageTooLong }

/** Validates (nothing sent when Invalid), then `invite` with the trimmed message (blank → none). */
class InviteToProjectUseCase(private val joins: ProjectJoinsRepository) {
    suspend operator fun invoke(candidateId: UserId, draft: InvitationDraft): SubmitResult<ProjectJoin, InvitationError>
}
```

Все use case'ы превращают `JoinRefusedException` в `Refused`, прочие исключения — в `Failure`. Исключение — `SubmitResult`: у `ApplyToRole` и `InviteToProject` `JoinRefusedException` остаётся причиной в `Failure(cause)`, UI смотрит на её тип (как `ProjectConflictException` у 04d). Отмена корутины не превращается в сбой.

**Правила статусов** (KDoc в `ResponsesModels.kt`; их же использует UI):

- **Действия на 15.** У записи `Application` со статусом `Pending` или `null` есть «Отозвать отклик». У `Invitation` со статусом `Pending` или `null` есть ✓ ×. У `type = null` действий нет.
- **Действия на 07d.** ✓ × у записей со статусом `Pending` или `null`. У записей `Accepted`, `Declined`, `Withdrawn` — чип, без кнопок.
- **Числа «· N».** Во вкладках 15 и в чипах 07d считаются записи, которые ждут решения: `Pending` или `null`.

### 2.4 `feature:responses:data` (domain-data; новый модуль)

`build.gradle.kts` — копия `feature/projects/data` (`api(":feature:responses:domain")`, `implementation(":gateway")`, `:utils`, coroutines).

| Класс | Публичный конструктор | Что делает |
|---|---|---|
| `ResponsesDataSource` | интерфейс | Зеркалит оба репозитория |
| `GatewayResponsesDataSource` | `(api: ProjectJoinsApi, users: UserDirectory)` | Кэш «мои» (`MutableStateFlow<List<ProjectJoin>?>`), кэш по проекту (`Map<ProjectId, List<ProjectResponse>>`), `seenAt`. Чтение: `PageRequest(offset, 100)`, пока `Page.hasMore` и страниц не больше 10, `toProjectJoinPage`, повторы `id` отбрасываются. `filter` пустой: `status` и `role_id` не отправляются. Профили кандидатов: `users.profile(id)` через `async` (лимит 4 держит сам `UserDirectory`); сбой или `null` → `Candidate(Collaborator(UserSummary(id, ""), "", Study(null, null), ""), emptyList(), hasProfile = false)`; иначе `toCollaborator().copy(user = …copy(id = candidateId))` и `details.skills`. Запись без `candidateId` → `candidate = null`. Записи идут через мьютекс. `RemoteFailure.Http` 403/404/409 → `JoinRefusedException(NotAllowed/NotFound/Conflict)`; на списке проекта 403/404 → `ProjectResponsesUnavailableException`. Ответ, который не читается (`toProjectJoin() == null`), → `RemoteFailure.Malformed`. Статус ответа `null` → статус по действию: accept → `Accepted`, decline → `Declined`, withdraw → `Withdrawn` (`// API-GAP G59`). Отклик отправляет `ProjectJoinInputDto(projectId, roleId, message)`, приглашение — то же плюс `candidateUserId` (`// API-GAP G60`). Автоповторов нет (G48) |
| `ResponsesRepositoryImpl` | `(dataSource: ResponsesDataSource) : MyJoinsRepository, ProjectJoinsRepository` | Передаёт вызовы источнику |

### 2.5 `feature:complaints:domain` (domain-data; новый модуль; входит в domain-ready)

Пакет `org.matchlab.academic_net.feature.complaints.domain`.

```kotlin
/** Whom or what the complaint is about. */
sealed interface ComplaintTarget {
    data class User(val id: UserId) : ComplaintTarget
    data class Project(val id: ProjectId) : ComplaintTarget
}

/** A reason of a complaint: [code] goes as `subject` (`docs/api-values.md`; API-GAP G68). */
sealed interface ComplaintReason { val code: String }

/** 17, in the order of the frame. */
enum class UserComplaintReason(override val code: String) : ComplaintReason {
    Spam("1"), Insults("2"), PortfolioFraud("3"), FakeAccount("4"), Scam("5"), Other("6")
}

/** 17b, in the order of the frame. */
enum class ProjectComplaintReason(override val code: String) : ComplaintReason {
    Spam("1"), FalseInformation("2"), Scam("3"), Other("4")
}

/** The reasons offered for [target]: [UserComplaintReason] or [ProjectComplaintReason] entries. */
fun complaintReasonsFor(target: ComplaintTarget): List<ComplaintReason>

/** The card at the top of 17 / 17b. */
sealed interface ComplaintSubject {
    data class PersonSubject(val person: Collaborator) : ComplaintSubject
    data class ProjectSubject(val title: String, val initiatorName: String, val initiatorUniversity: String) : ComplaintSubject
}

enum class ComplaintDraftError { ReasonMissing, DescriptionMissing, DescriptionTooLong }

/** The form 17. A reason that is not among [complaintReasonsFor] the target is ReasonMissing. */
data class ComplaintDraft(val target: ComplaintTarget, val reason: ComplaintReason? = null, val description: String = "") {
    fun normalized(): ComplaintDraft            // description trimmed
    fun validate(): Set<ComplaintDraftError>    // description 1..ComplaintDescriptionMaxLength after trim
}

const val ComplaintDescriptionMaxLength = 10_000

/** A complaint that passed validation. */
data class Complaint(val target: ComplaintTarget, val reason: ComplaintReason, val description: String)

/** 403: the e-mail is not confirmed (or the account blocked); the form says "Подтвердите почту…". */
class ComplaintNotAllowedException : IllegalStateException("Complaints are not allowed for this account")

interface ComplaintsRepository {
    /** The card of the target; `null`: no such person or project for the viewer. Throws a failure. */
    suspend fun getSubject(target: ComplaintTarget): ComplaintSubject?

    /** `POST /complaints`. @throws ComplaintNotAllowedException 403; else the `RemoteFailure`. No retry (G48). */
    suspend fun send(complaint: Complaint)
}

class GetComplaintSubjectUseCase(private val repository: ComplaintsRepository) {
    suspend operator fun invoke(target: ComplaintTarget): LoadResult<ComplaintSubject>
}

/** normalized → validate (Invalid: nothing sent) → send → Success(Unit) / Failure(cause). */
class SendComplaintUseCase(private val repository: ComplaintsRepository) {
    suspend operator fun invoke(draft: ComplaintDraft): SubmitResult<Unit, ComplaintDraftError>
}
```

### 2.6 `feature:complaints:data` (domain-data; новый модуль)

| Класс | Публичный конструктор | Что делает |
|---|---|---|
| `ComplaintsDataSource` | интерфейс | Зеркалит репозиторий |
| `GatewayComplaintsDataSource` | `(api: ComplaintsApi, users: UserDirectory, catalog: ProjectCatalogApi, assembler: ProjectAssembler)` | Человек: `users.profile(id)` → `PersonSubject(toCollaborator() с id запроса)`, `null` → `null`. Проект: `catalog.get(id)` (400/403/404 → `null`) → `assembler.assemble` → `ProjectSubject(title, owner name, owner university)`. `send`: `CreateComplaintDto(subject = reason.code, description, target…)`; 403 → `ComplaintNotAllowedException`; прочее пробрасывается |
| `ComplaintsRepositoryImpl` | `(dataSource: ComplaintsDataSource)` | Передаёт вызовы источнику |

### 2.7 `feature:projects:domain` и `data` (domain-data; domain входит в domain-ready)

```kotlin
// ProjectsRepository.kt — add
/** The server refused to remove the member (403 or 409; API-GAP G65); the project has been read again. */
class MemberNotRemovableException(val projectId: ProjectId, val userId: UserId) :
    IllegalStateException("Member ${userId.value} of project ${projectId.value} cannot be removed")

    /**
     * `DELETE /projects/{id}/members/{user_id}` → the project. A 404 reads the project again: gone is
     * [ProjectNotFoundException], still there counts as removed (the project is returned).
     * @throws MemberNotRemovableException 403 or 409
     */
    suspend fun removeMember(projectId: ProjectId, userId: UserId): Project

// ParticipationSource.kt — port, adapter in :shared
/** API-GAP G67: the projects the viewer joined, as the responses know them. */
fun interface ParticipationSource {
    /** Ids of the projects of the viewer's records with status Accepted or `null`, each once, in their order. Reads afresh; throws a failure. */
    suspend fun joinedProjectIds(): List<ProjectId>
}

// usecase/RemoveMember.kt
sealed interface RemoveMemberResult {
    data class Removed(val project: Project) : RemoveMemberResult
    /** The member is the viewer or the initiator (nothing sent), or the server refused (MemberNotRemovableException). */
    data object NotAllowed : RemoveMemberResult
    data object NotFound : RemoveMemberResult
    data class Failure(val cause: Throwable) : RemoveMemberResult
}
class RemoveMemberUseCase(private val repository: ProjectsRepository) {
    suspend operator fun invoke(projectId: ProjectId, userId: UserId): RemoveMemberResult
}

// usecase/GetParticipatingProjects.kt
sealed interface ParticipatingProjectsResult {
    data class Loaded(val projects: List<Project>) : ParticipatingProjectsResult
    data class Failure(val cause: Throwable) : ParticipatingProjectsResult
}
/**
 * 06b: `joinedProjectIds`, then `repository.loadProject` of each (in parallel, fresh: membership may have changed), the
 * order of the ids; kept: not `null`, `viewerIsMember`, not `viewerIsInitiator`. Any failure → Failure.
 */
class GetParticipatingProjectsUseCase(private val source: ParticipationSource, private val repository: ProjectsRepository) {
    suspend operator fun invoke(): ParticipatingProjectsResult
}
```

Data: в `ProjectsDataSource` и `ProjectsRepositoryImpl` добавляется `removeMember`. В `GatewayProjectsDataSource` вызов идёт под `writes`, дальше `api.removeMember` → `keep`, 404 → `missingAfterReading` (проект на месте → вернуть его), 403/409 → перечитать и бросить `MemberNotRemovableException`. Конструктор источника не меняется.

### 2.8 `feature:projectdetails:domain` и `data` (domain-data; domain входит в domain-ready)

```kotlin
// AppliedRolesSource.kt — port, adapter in :shared
/** Which roles of a project the viewer has applied to (records of type Application, status Pending or `null`). */
fun interface AppliedRolesSource {
    /** Follows the viewer's records as the session knows them; empty while unknown. Never fails. */
    fun observeAppliedRoles(projectId: ProjectId): Flow<Set<RoleId>>
}
class ObserveAppliedRolesUseCase(private val source: AppliedRolesSource) {
    operator fun invoke(projectId: ProjectId): Flow<Set<RoleId>>
}

// ProjectDetailsRepository.kt — add
/** The server refused to let the viewer leave (403 or 409; API-GAP G65). */
class LeaveNotAllowedException(val projectId: ProjectId) : IllegalStateException("Cannot leave ${projectId.value}")
/** The project is gone for the viewer (404). */
class ProjectGoneException(val projectId: ProjectId) : NoSuchElementException("Project ${projectId.value} is gone")

    /** `DELETE /projects/{id}/membership`. @throws LeaveNotAllowedException @throws ProjectGoneException */
    suspend fun leaveProject(id: ProjectId)

// LeaveProjectUseCase.kt
sealed interface LeaveProjectResult {
    data object Left : LeaveProjectResult
    data object NotAllowed : LeaveProjectResult
    data object NotFound : LeaveProjectResult
    data class Failure(val cause: Throwable) : LeaveProjectResult
}
class LeaveProjectUseCase(private val repository: ProjectDetailsRepository) {
    suspend operator fun invoke(id: ProjectId): LeaveProjectResult
}
```

Data: конструктор становится `GatewayProjectDetailsDataSource(api: ProjectCatalogApi, projects: ProjectsApi, assembler: ProjectAssembler, team: ProjectTeamReader)`. Метод `leaveProject` вызывает `projects.leave(id)`, ответ не собирается: страницу перечитывает ViewModel, поэтому сбой чтения владельца не превращает удачный выход в ошибку. 403/409 → `LeaveNotAllowedException`, 404 → `ProjectGoneException`. В `ProjectDetailsDataSource` и `ProjectDetailsRepositoryImpl` добавляется `leaveProject`.

### 2.9 ui-модули (compose-ui): что видно снаружи

**`responses:ui`.** В `build.gradle.kts` добавить `api(project(":feature:responses:domain"))`, `implementation(":utils")`, `lifecycle-viewmodel-compose`, kotlinx-datetime — как в `projects:ui`. Пакет `…feature.responses.ui`.

```kotlin
class ResponsesUiDependencies(
    val observeMyResponses: ObserveMyResponsesUseCase,
    val refreshMyResponses: RefreshMyResponsesUseCase,
    val withdrawApplication: WithdrawApplicationUseCase,
    val decideInvitation: DecideInvitationUseCase,
    val observeHasNewInvitations: ObserveHasNewInvitationsUseCase,
    val markInvitationsSeen: MarkInvitationsSeenUseCase,
    val getRoleToApply: GetRoleToApplyUseCase,
    val applyToRole: ApplyToRoleUseCase,
    val observeProjectResponses: ObserveProjectResponsesUseCase,
    val refreshProjectResponses: RefreshProjectResponsesUseCase,
    val decideApplication: DecideApplicationUseCase,
    val getInviteOptions: GetInviteOptionsUseCase,
    val inviteToProject: InviteToProjectUseCase,
) {
    /** Whether 15 is on the screen: the dot of the tab is off while it is (like MyProfileVisibility). */
    internal val visibility: ResponsesVisibility = ResponsesVisibility()
}

/** Tab "Отклики": 15 / 15c. A tap on a record opens the project page; the chat is with the initiator. */
@Composable
fun ResponsesRoute(
    dependencies: ResponsesUiDependencies,
    onProjectClick: (ProjectId) -> Unit,
    onContactInitiator: (UserSummary) -> Unit,
    modifier: Modifier = Modifier,
)

/** The dot of the tab; reads the viewer's list once per [dependencies] (a failure is silent). */
@Composable
fun responsesTabHasBadge(dependencies: ResponsesUiDependencies): Boolean

/** 07d. */
@Composable
fun ProjectResponsesRoute(
    dependencies: ResponsesUiDependencies,
    projectId: ProjectId,
    onNavigateBack: () -> Unit,
    onCandidateClick: (UserId) -> Unit,
    onContactCandidate: (UserSummary) -> Unit,
    modifier: Modifier = Modifier,
)

/** 04s–04w. [requestId] makes a fresh view model per opening (part of its key). Success → [onApplied] (the host closes it). */
@Composable
fun ApplyToRoleSheet(
    dependencies: ResponsesUiDependencies,
    projectId: ProjectId,
    roleId: RoleId,
    requestId: Int,
    onDismiss: () -> Unit,
    onApplied: () -> Unit,
)

/** A role chosen before the sheet opened (the way from 08 with a role). */
data class InvitePreselection(val projectId: ProjectId, val roleId: RoleId)

/** 09k–09p. [onCreateProject] is the button of 09m. */
@Composable
fun InviteToProjectSheet(
    dependencies: ResponsesUiDependencies,
    candidateId: UserId,
    preselected: InvitePreselection?,
    requestId: Int,
    onDismiss: () -> Unit,
    onInvited: () -> Unit,
    onCreateProject: () -> Unit,
)

/** "Приглашение отправлено" of 09q (Banner/Info with a check). */
@Composable
fun InvitationSentBanner(modifier: Modifier = Modifier)
```

Поведение для tester. Внутренние имена выбирает compose-ui.

- **`ResponsesViewModel(observeMyResponses, refreshMyResponses, withdrawApplication, decideInvitation, markInvitationsSeen)`**:
  - `onShown()` вызывается при каждом входе на вкладку и перечитывает список. Если списка нет — загрузка или ошибка (15d/15f, «Повторить»). Если список есть, сбой перечитывания не показывается.
  - Вкладки: `Invitation` → «Приглашения», остальное → «Отклики».
  - После показа списка вызывается `markInvitationsSeen(joins)`.
  - «Отозвать» и × открывают диалог (15n, 15o), ✓ шлёт сразу. Страж — множество id в работе: повторный вход по той же записи игнорируется.
  - `Refused` → «Действие недоступно», `Failure` → `failureText`.
- **`ApplyToRoleViewModel(projectId, roleId, getRoleToApply, applyToRole)`**:
  - Поле сообщения обрезается до 500 знаков при вводе. Счётчик `N / 500`.
  - Страж `isSubmitting` держится и после успеха.
  - Исходы `Failure`: `JoinRefusedException.Conflict` → 04u, `NotAllowed` → 04v, `NotFound` → «Проект или роль больше недоступны», прочее → `failureText` (`NoConnection` даёт текст 04w).
  - `getRoleToApply` → `NotFound` показывает тот же текст вместо формы.
- **`ProjectResponsesViewModel(projectId, observeProjectResponses, refreshProjectResponses, decideApplication)`**:
  - Загрузка, пусто, ошибка: 07m/07n/07o. `NotFound` → «Проект не найден».
  - Фильтр по роли (локальный): «Все · N» и чипы ролей, у которых есть записи, с их числом.
  - ✓ × под стражем по id (07s — дуга вместо кнопок).
  - `RoleFull` и роль без мест → у ✓ `enabled = false` и баннер 07r при выбранной роли.
  - После `Done` с accept — баннер 07q: «{имя или «Без имени»} принят в роль {роль}».
  - `Refused` → 07t «Действие недоступно».
- **`InviteToProjectViewModel(candidateId, preselected, getInviteOptions, inviteToProject)`**:
  - Загрузка и пусто: 09l/09m.
  - Один проект с одной ролью выбирается сразу. `preselected` выбирается, если он есть в вариантах.
  - Кнопка активна, когда выбраны проект и роль. Страж `isSubmitting`.
  - 409 → 09o, 403 → 09p, 404 → «Проект или роль больше недоступны».

**`complaints:ui`** — новый модуль. `build.gradle.kts` — копия `feature/projects/ui` с `api(":feature:complaints:domain")` и `packageOfResClass = "org.matchlab.academic_net.feature.complaints.ui.generated.resources"`.

```kotlin
class ComplaintsUiDependencies(
    val getComplaintSubject: GetComplaintSubjectUseCase,
    val sendComplaint: SendComplaintUseCase,
)

/** 17 / 17b–17g; "Готово" of 17e calls [onDone]. */
@Composable
fun ComplaintRoute(
    dependencies: ComplaintsUiDependencies,
    target: ComplaintTarget,
    onNavigateBack: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
)

/** 09c (a person) / 09j (a project): one card "Пожаловаться на …" → [onReport]. No "Заблокировать" (3rd sprint). */
@Composable
fun ComplaintEntrySheet(target: ComplaintTarget, onReport: () -> Unit, onDismiss: () -> Unit)
```

`ComplaintViewModel(target, getComplaintSubject, sendComplaint)`:

- Карточка цели: при `NotFound` или `Failure` её нет, форма работает.
- Ошибки полей — 17c. Страж `isSubmitting` держится после успеха.
- `ComplaintNotAllowedException` → 17f. Любой другой сбой (400, 404, 409, 5xx, сеть) → 17g «Не удалось отправить жалобу. Попробуйте ещё раз».
- Успех → 17e.

**`projects:ui`**:

```kotlin
class ProjectsUiDependencies(
    /* …все параметры из contract-s2a, затем: */
    val removeMember: RemoveMemberUseCase,
    val getParticipatingProjects: GetParticipatingProjectsUseCase,
    val capabilities: ProjectsCapabilities,   // остаётся последним
)

@Composable fun MyProjectsRoute(
    dependencies: ProjectsUiDependencies,
    onCreateProject: () -> Unit,
    onProjectClick: (ProjectId) -> Unit,
    onParticipatingProjectClick: (ProjectId) -> Unit,
    modifier: Modifier = Modifier,
)

@Composable fun ProjectManagementRoute(
    dependencies: ProjectsUiDependencies,
    projectId: ProjectId,
    onNavigateBack: () -> Unit,
    onFindCollaborators: (ProjectId, RoleId) -> Unit,
    onEditProject: (ProjectId) -> Unit,
    onMemberClick: (UserId) -> Unit,
    onContactMember: (UserSummary) -> Unit,
    onResponsesClick: (ProjectId) -> Unit,
    modifier: Modifier = Modifier,
)
```

- **06**: `MyProjectItem.pendingJoinCount` → бейдж на карточке (06e). Вкладка «Я участник» на `getParticipatingProjects`: загружается при выборе вкладки и по «Повторить», статусные чипы — как на 06d.
- **07/07b/07c**: первой строкой идёт «Отклики · N» (`pendingJoinCount`; при 0 — просто «Отклики»), только у инициатора; тап вызывает `onResponsesClick`.
- **07b**: «Выгнать» → `AppConfirmDialog(destructive)` 07i → `removeMember` под стражем (07u — дуга в кнопке). `NotAllowed` → «Действие недоступно», `Failure` → 07v, `NotFound` → «Проект не найден». Кнопки нет у инициатора и у самого пользователя. Чат участника вызывает `onContactMember`. `ParticipantManageCard`: аватар 64 по центру (решение дизайна, пункт 11), параметр `removing: Boolean`.

**`projectdetails:ui`**:

```kotlin
class ProjectDetailsUiDependencies(
    val getProjectDetails: GetProjectDetailsUseCase,
    val getProjectTeam: GetProjectTeamUseCase,
    val observeAppliedRoles: ObserveAppliedRolesUseCase,
    val leaveProject: LeaveProjectUseCase,
)

@Composable fun ProjectDetailsRoute(
    dependencies: ProjectDetailsUiDependencies,
    projectId: ProjectId,
    onNavigateBack: () -> Unit,
    onInitiatorClick: (UserId) -> Unit,
    onContactInitiator: (UserSummary) -> Unit,
    onMemberClick: (UserId) -> Unit,
    onContactMember: (UserSummary) -> Unit,
    onApplyClick: (RoleId) -> Unit,
    onReportClick: () -> Unit,
    modifier: Modifier = Modifier,
)
```

Поведение `ProjectDetailsViewModel`:

- «Откликнуться» есть на свободной роли, если `!viewerIsInitiator && !viewerIsMember && status in {Recruiting, null}`. Роль из `observeAppliedRoles` показывается как «Вы откликнулись» (04x).
- «Покинуть проект» есть при `viewerIsMember && !viewerIsInitiator`. Диалог 04y → `leaveProject` под стражем (04g). `Left` → страница перечитывается, баннер 04i. `NotAllowed` → 04h. `NotFound` → «Проект не найден». `Failure` → баннер с `failureText`.
- Флажок в шапке — только при `!viewerIsInitiator`.

**`profile:ui`**: `ProfileUiModel` получает `canInvite` и `canReport` (оба равны `!isViewer`).

```kotlin
@Composable fun UserProfileRoute(
    /* …текущие параметры…, */
    onInviteClick: () -> Unit,
    onReportClick: () -> Unit,
    topBanner: (@Composable () -> Unit)? = null,   // drawn above the header (09q)
    modifier: Modifier = Modifier,
)
```

**`chats:ui`**: `ChatRoute(…, onReportPeer: (UserId) -> Unit, modifier)`. В шапке меню «⋮» (`AppIcons.MoreVertical`) с одним пунктом «Пожаловаться» (13b). Меню нет, если собеседник неизвестен или его id пустой.

### 2.10 `:components` (compose-ui)

- `ProjectCardUiModel`: добавляются `badgeCount: Int = 0` (06e, `AppBadgeCount` при > 0), `invitationRole: String? = null` и `invitationMessage: String? = null` (15l; строки между нижней строкой и кнопками).
- `RoleApplyCard(…, applied: Boolean = false)`: при `applied` кнопка «Вы откликнулись» выключена, в цветах `surfaceContainer` / `onSurfaceVariant` (04x).
- Многострочное поле с высотой рамки 96 и счётчиком (Text field/Multiline `60354:6829`). Вариант — `AppTextField(…, counterText: String? = null)` или отдельный `AppMultilineField`, решает compose-ui. Новый компонент добавить в `:catalog`.
- Перенос `CandidateCard` из `projects:ui` в `responses:ui` с новыми полями `status: StatusChipUiModel?`, `deciding: Boolean`, `acceptEnabled: Boolean` (свойства `Card/Candidate` `showStatus`/`showSpinner`). В `ApplicationCard` добавляется `status: StatusChipUiModel?`.
- Чипы статусов записей: функции в `responses:ui`. У отклика — «Ожидает» (`Pending`), «Принят», «Отклонён», «Отозван». У приглашения — «Принято», «Отклонено», «Отозвано».

### 2.11 `:shared`

**domain-data** — `R/shared/src/commonMain/kotlin/org/matchlab/academic_net/`:

```kotlin
// ProjectsResponsesProjectSource.kt
@Inject internal class ProjectsResponsesProjectSource(private val projects: ProjectsRepository) : ResponsesProjectSource
//   getProject = projects.getProject; observeProject = projects.observeProject;
//   refreshProject = suspendRunCatching { projects.loadProject(id) } (cancellation rethrown);
//   getMyProjects = projects.observeMyProjects().first()

// ResponsesParticipationSource.kt
@Inject internal class ResponsesParticipationSource(private val joins: MyJoinsRepository) : ParticipationSource
//   refreshMyJoins().filter { status == Accepted || status == null }.map { projectId }.distinct()

// ResponsesAppliedRolesSource.kt
@Inject internal class ResponsesAppliedRolesSource(private val joins: MyJoinsRepository) : AppliedRolesSource
//   observeMyJoins().map { it.orEmpty().filter { projectId == id && type == Application && (status == Pending || status == null) }.map { roleId }.toSet() }.distinctUntilChanged()

// SessionDependencies: + val responses: ResponsesUiDependencies, + val complaints: ComplaintsUiDependencies
```

```kotlin
// di/DataSourcesModule — add / change
@Provides @SessionScope
fun responsesDataSource(gateway: GatewaySession): ResponsesDataSource =
    GatewayResponsesDataSource(gateway.projectJoins, gateway.users)

@Provides @SessionScope
fun complaintsDataSource(gateway: GatewaySession, assembler: ProjectAssembler): ComplaintsDataSource =
    GatewayComplaintsDataSource(gateway.complaints, gateway.users, gateway.projectCatalog, assembler)

fun projectDetailsDataSource(gateway, assembler, team): ProjectDetailsDataSource =
    GatewayProjectDetailsDataSource(gateway.projectCatalog, gateway.projects, assembler, team)

// di/ResponsesModule (new)
@Provides @SessionScope fun responsesRepositoryImpl(source: ResponsesDataSource) = ResponsesRepositoryImpl(source)
@Provides fun myJoinsRepository(impl: ResponsesRepositoryImpl): MyJoinsRepository = impl
@Provides fun projectJoinsRepository(impl: ResponsesRepositoryImpl): ProjectJoinsRepository = impl
@Provides fun responsesProjectSource(adapter: ProjectsResponsesProjectSource): ResponsesProjectSource = adapter
@Provides fun responsesUiDependencies(mine: MyJoinsRepository, project: ProjectJoinsRepository, projects: ResponsesProjectSource): ResponsesUiDependencies

// di/ComplaintsModule (new)
@Provides @SessionScope fun complaintsRepository(source: ComplaintsDataSource): ComplaintsRepository = ComplaintsRepositoryImpl(source)
@Provides fun complaintsUiDependencies(repository: ComplaintsRepository): ComplaintsUiDependencies

// di/ProjectsModule: + participationSource(adapter: ResponsesParticipationSource): ParticipationSource = adapter;
//   projectsUiDependencies(repository, participation) + removeMember, getParticipatingProjects
// di/ProjectDetailsModule: + appliedRolesSource(adapter: ResponsesAppliedRolesSource): AppliedRolesSource = adapter;
//   + observeAppliedRoles, leaveProject
// di/SessionComponent: + ResponsesModule, ComplaintsModule
```

Также domain-data правит `shared/build.gradle.kts` (+ `responses:{domain,data,ui}`, `complaints:{domain,data,ui}`), `settings.gradle.kts` (5 новых `include`) и корневой `build.gradle.kts` (+5 `kover(project(...))`).

**compose-ui** — `ui/navigation/AppNavHost.kt` и `MainScaffold.kt`:

```kotlin
const val InviteProjectArgument = "inviteProjectId"
const val InviteRoleArgument = "inviteRoleId"
const val Profile = "profile/{$UserArgument}?$InviteProjectArgument={$InviteProjectArgument}&$InviteRoleArgument={$InviteRoleArgument}"  // both nullable, default null
const val ProjectResponses = "project/{$ProjectArgument}/responses"
const val UserComplaint = "complaint/user/{$UserArgument}"
const val ProjectComplaint = "complaint/project/{$ProjectArgument}"
fun profileForRole(id: UserId, projectId: ProjectId, roleId: RoleId) =
    "profile/${id.value}?$InviteProjectArgument=${projectId.value}&$InviteRoleArgument=${roleId.value}"
fun projectResponses(id: ProjectId) = "project/${id.value}/responses"
fun userComplaint(id: UserId) = "complaint/user/${id.value}"
fun projectComplaint(id: ProjectId) = "complaint/project/${id.value}"
```

- **Main:**
  - `badges` дополняется `TopLevelDestination.Responses`, если `responsesTabHasBadge(dependencies.responses)`.
  - `Responses -> ResponsesRoute(onProjectClick = navigateOnce(entry, projectDetails(id)), onContactInitiator = openChatWith(entry, it))`.
  - `MyProjectsRoute(onParticipatingProjectClick = navigateOnce(entry, projectDetails(id)))`.
  - Ветка `else -> TabPlaceholder` и сам `TabPlaceholder` удаляются: все 5 вкладок реализованы.
- **`Routes.Project`:** `onContactMember = openChatWith(entry, it)`, `onResponsesClick = navigateOnce(entry, projectResponses(id))`.
- **`Routes.ProjectResponses`:** `ProjectResponsesRoute(…, onCandidateClick = openProfile(entry, it), onContactCandidate = openChatWith(entry, it))`.
- **`Routes.ProjectDetails`:**
  - Состояние `apply: Pair<RoleId, Int>?` и `report: Boolean` в `rememberSaveable`.
  - `onApplyClick` → `ApplyToRoleSheet(…, onDismiss/onApplied = закрыть)`.
  - `onReportClick` → `ComplaintEntrySheet(Project)` → `onReport` = закрыть шит + `navigateOnce(entry, projectComplaint(id))`.
- **`Routes.Profile`:**
  - Аргументы приглашения → `InvitePreselection?`.
  - Состояние `inviteRequest: Int?`, `invited: Boolean`, `report: Boolean` в `rememberSaveable`.
  - `UserProfileRoute(…, onInviteClick, onReportClick, topBanner = if (invited) {{ InvitationSentBanner() }} else null)`.
  - `InviteToProjectSheet(…, onInvited = { invited = true; закрыть }, onCreateProject = { закрыть; navigateSingleTop(entry, CreateProject) })`.
  - `ComplaintEntrySheet(User)` → `navigateOnce(entry, userComplaint(id))`.
  - Проверка «пришли из того же профиля» в `Routes.Chat` сравнивает с новым `Routes.Profile`, как сейчас.
- **`Routes.Collaborators` (с ролью):** `onCollaboratorClick = { if (id не пустой) navigateOnce(entry, profileForRole(user.id, projectId, roleId)) }`.
- **`Routes.Chat`:** `onReportPeer = { navigateOnce(entry, userComplaint(it)) }`.
- **Жалобы:** `composable(UserComplaint/ProjectComplaint) { ComplaintRoute(dependencies.complaints, target, onNavigateBack = popBackStackOnce(entry), onDone = popBackStackOnce(entry)) }`.

## 3. Ответ сервера → результат

| Вызов | 2xx | 400 | 401 | 403 | 404 | 409 | 5xx | Timeout / NoConnection |
|---|---|---|---|---|---|---|---|---|
| `POST /complaints` | 17e | 17g | 17g + проверка сессии | 17f «Подтвердите почту, чтобы отправлять жалобы» | 17g | 17g | 17g | 17g |
| `GET /users/{id}/profile` (17, 07d) | карточка | без карточки или без профиля | — | — | — | — | — | без карточки или без профиля, в кэш не идёт |
| `GET /projects/{id}` (17b) | карточка | без карточки | | | | | | |
| `POST …/applications` | шит закрыт, 04x | `failureText` в шите | `failureText` | 04v | «Проект или роль больше недоступны» | 04u | `failureText` | `failureText` (04w) |
| `POST …/invitations` | шит закрыт, 09q | `failureText` | `failureText` | 09p | «Проект или роль больше недоступны» | 09o | `failureText` | `failureText` |
| `GET /project-joins` (15) | список | 15f + «Повторить» (если списка ещё нет; иначе ничего) | так же | так же | так же | так же | так же | так же |
| `GET /project-joins` (точка, 06b) | точка / 06b | точки нет; 06b — ошибка с «Повторить» | | | | | | |
| `GET /projects/{id}/joins` | 07d | 07o | 07o | «Проект не найден» | «Проект не найден» | 07o | 07o | 07o |
| `POST /project-joins/{id}/decision` (07d, 15c) | запись обновлена; при accept проект перечитан, 07q | `failureText` | `failureText` | «Действие недоступно», список перечитан | так же | так же | `failureText` | `failureText` |
| `DELETE /project-joins/{id}` | чип «Отозван» | `failureText` | `failureText` | «Действие недоступно», перечитать | так же | так же | `failureText` | `failureText` |
| `DELETE /projects/{id}/members/{u}` | карточка ушла | 07v | 07v | «Действие недоступно» | перечитать: проекта нет → «Проект не найден»; есть → `Removed` | «Действие недоступно» | 07v | 07v |
| `DELETE /projects/{id}/membership` | 04i | `failureText` | `failureText` | 04h | «Проект не найден» | 04h | `failureText` | `failureText` |

Отмена корутины нигде не превращается в сбой. Автоповторов нет (G48).

## 4. Кто какие файлы правит

| Каталог или файл | Владелец |
|---|---|
| `R/utils/**`, `R/common/**` | domain-data |
| `R/gateway/**` (main), включая `GatewaySession.kt` | domain-data |
| `R/feature/{responses,complaints}/{domain,data}/**` (новые, с `build.gradle.kts`) | domain-data |
| `R/feature/{projects,projectdetails}/{domain,data}/**` (main) | domain-data |
| `R/settings.gradle.kts`, `R/build.gradle.kts` (kover), `R/shared/build.gradle.kts` | domain-data |
| `R/shared/.../di/*`, `R/shared/.../SessionDependencies.kt`, адаптеры `R/shared/.../*Source.kt` | domain-data |
| `R/feature/responses/ui/**` (с `build.gradle.kts`), `R/feature/complaints/ui/**` (новый, с `build.gradle.kts`) | compose-ui |
| `R/feature/{projects,projectdetails,profile,chats}/ui/**` (main, `…UiDependencies.kt`, `composeResources`) | compose-ui |
| `R/components/**`, `R/catalog/**` (`ComponentCatalog.kt`, `catalog/build.gradle.kts` + `complaints:ui`) | compose-ui |
| `R/shared/.../ui/navigation/{AppNavHost.kt, MainScaffold.kt}` | compose-ui |
| Все `strings.xml` (values и values-ru, ключи 1:1) | compose-ui |
| `*/commonTest/**` | tester; агенты тесты не компилируют и не правят |
| `R/docs/*` | никто; тексты — в отчётах |

## 5. Что удаляется

Моков у блоков 5–8 нет. Удаляется устаревшее:

- **domain-data:**
  - KDoc «Applications and invitations… blocks 6–8» в `ProjectJoinsApi` и `GatewaySession` правится на актуальный.
  - Своих моков и данных нет.
- **compose-ui:**
  - `TabPlaceholder` и ветка `else` в `MainScaffold.kt` и `AppNavHost.kt`.
  - `feature/projects/ui/.../CandidateCard.kt` переносится в `responses:ui`; импорт в `ComponentCatalog.kt` обновляется.
  - Устаревший KDoc «later story / later sprint»: `ProjectDetailsScreen.kt:57`, `ProjectDetailsViewModel.kt:138`, `UserProfileScreen.kt:47`, `ChatScreen.kt:60`, `ProjectManagementScreen.kt:109,288`, `MyProjectsScreen.kt:97`, `ProjectManagementCards.kt` (абзац «redesign waits»).
- **Тесты, которые перестанут компилироваться** (чинит tester):
  - фейки `ProjectsRepository` (`projects/domain` `TestFixtures.kt`, `projects/ui` `ProjectsUiTestSupport.kt`) — `removeMember`;
  - фейк `ProjectDetailsRepository` — `leaveProject`; конструктор `GatewayProjectDetailsDataSource`;
  - `ProjectsUiDependencies(...)` и `ProjectDetailsUiDependencies(...)` в тестах UI;
  - `ProfileUiModel(...)`, если тесты строят его позиционно;
  - graph-тесты `:shared` (`SessionDependencies`).

## 6. Порядок и синхронизация

Маркер: `S/sync/s2b.domain-ready`.

**domain-data**

1. **domain-ready**. Файлы:
   - `R/settings.gradle.kts` (5 `include`), `R/feature/{responses,complaints}/{domain,data}/build.gradle.kts`;
   - `R/utils/.../ElapsedDays.kt`;
   - `R/feature/responses/domain/**` (`ResponsesModels.kt`, `MyJoinsRepository.kt`, `ProjectJoinsRepository.kt`, `ResponsesProjectSource.kt`, `usecase/*`);
   - `R/feature/complaints/domain/**`;
   - `R/feature/projects/domain/.../{ProjectsRepository.kt, ParticipationSource.kt, usecase/RemoveMember.kt, usecase/GetParticipatingProjects.kt}`;
   - `R/feature/projectdetails/domain/.../{ProjectDetailsRepository.kt, AppliedRolesSource.kt, ObserveAppliedRolesUseCase.kt, LeaveProjectUseCase.kt}`.

   Скомпилировать (раздел 7, шаг 1), затем выполнить `mkdir -p S/sync && touch S/sync/s2b.domain-ready`. После маркера domain меняется только совместимыми добавлениями, о каждом — в отчёте.
2. `:gateway`: жалобы.
3. data-модули: `responses`, `complaints`, `projects`, `projectdetails`.
4. `:shared` (DI, адаптеры, `SessionDependencies`, `shared/build.gradle.kts`, kover). Сам не компилирует, это делает оркестратор.
5. iOS-компиляция data-модулей.

**compose-ui**

- **До маркера:** строки всех модулей; `:components` (`ProjectCardUiModel`, `RoleApplyCard.applied`, многострочное поле); `complaints/ui/build.gradle.kts`, правка `responses/ui/build.gradle.kts`; вёрстка 15/15c, 07d, 04s, 09k, 17, 13b по кадрам; перенос `CandidateCard`. Ничего не компилирует: без новых domain-модулей не соберётся даже `responses:ui`.
- **После маркера** (`test -f S/sync/s2b.domain-ready` между шагами): ViewModel-и, Route-ы, правки `projects`/`projectdetails`/`profile`/`chats:ui`, `AppNavHost`, `MainScaffold`, `:catalog`; компиляция из раздела 7.

Параллелить можно всё внутри каждого агента. Исключение — `projects:ui`: 06 и 07b меняются в одном проходе.

## 7. Команды проверки (только компиляция)

`G=S/tools/gw.sh`. Тесты, `:shared` и `assembleDebug` агенты не запускают.

- domain-data, шаг 1: `$G :utils:compileAndroidMain :feature:responses:domain:compileAndroidMain :feature:complaints:domain:compileAndroidMain :feature:projects:domain:compileAndroidMain :feature:projectdetails:domain:compileAndroidMain --console=plain`
- domain-data, шаги 2–3: `$G :gateway:compileAndroidMain :feature:responses:data:compileAndroidMain :feature:complaints:data:compileAndroidMain :feature:projects:data:compileAndroidMain :feature:projectdetails:data:compileAndroidMain --console=plain`
- domain-data, iOS: `KONAN_DATA_DIR=S/konan-data $G :gateway:compileKotlinIosSimulatorArm64 :feature:responses:data:compileKotlinIosSimulatorArm64 :feature:complaints:data:compileKotlinIosSimulatorArm64 :feature:projects:data:compileKotlinIosSimulatorArm64 :feature:projectdetails:data:compileKotlinIosSimulatorArm64 --console=plain`
- compose-ui, после маркера: `$G :components:compileAndroidMain :feature:responses:ui:compileAndroidMain :feature:complaints:ui:compileAndroidMain :feature:projects:ui:compileAndroidMain :feature:projectdetails:ui:compileAndroidMain :feature:profile:ui:compileAndroidMain :feature:chats:ui:compileAndroidMain :catalog:compileAndroidMain --console=plain`
- compose-ui, iOS: те же модули с `compileKotlinIosSimulatorArm64` и `KONAN_DATA_DIR=S/konan-data`.

Новые модули — копии модулей `feature/projects` с тем же `androidLibrary`, поэтому имена задач те же (подтверждены в contract-s2a, §7).

## 8. Что должен покрыть tester (критичное)

- **`:utils`**: `elapsedDays` — тот же день, переход через полночь в зоне, «будущее» → 0.
- **`:gateway`** (`ktor-client-mock`): `POST complaints` — тело в snake_case, ровно одна цель, `file_ids: []`, 201 без чтения ответа, нечитаемый ответ — не ошибка; 403 → `Http(403)`.
- **`responses:domain`**:
  - каждый use case по всем веткам;
  - `ObserveMyResponses`: каждый проект запрашивается один раз, запись без проекта исчезает, сбой проекта роняет поток;
  - `DecideApplication`: `RoleFull` без запроса, `refreshProject` после `Done`, перечитывание после `Refused`;
  - `ObserveHasNewInvitations`: `seenAt` `null` или раньше или позже, тип `null`, `createdAt` `null`, статусы;
  - `MarkInvitationsSeen`: максимум, только приглашения;
  - `Apply` и `Invite`: длина ровно 500 и 501, пустое сообщение → `null`, валидация без вызова;
  - `GetInviteOptions`: фильтр по статусу, вакансиям, инициатору; `FeatureUnavailable` → пусто.
- **`responses:data`**:
  - постраничное чтение (100, остановка по `hasMore`, не больше 10 страниц), повторы `id`, нечитаемые записи;
  - фильтры не уходят;
  - 403/404/409 → `JoinRefusal`; 403/404 списка проекта → `ProjectResponsesUnavailableException`;
  - статус по действию при неизвестном ответе;
  - `decide` обновляет оба кэша; `apply` при кэше `null`;
  - кандидаты: каждый id один раз, сбой → `hasProfile = false`, `candidateId == null` → `candidate == null`;
  - записи сериализованы, отмена пробрасывается.
- **`complaints`**:
  - `complaintReasonsFor`, коды 1..6 и 1..4;
  - `ComplaintDraft.validate` (пробелы, 10 000 и 10 001, причина чужого набора);
  - `SendComplaint` при `Invalid` не шлёт запрос;
  - data: `subject` = код, 403 → `ComplaintNotAllowedException`, `getSubject` для человека и проекта, 400/403/404 → `null`;
  - ViewModel: страж повторной отправки (держится после успеха), 17f и 17g.
- **`projects`**:
  - `removeMember` в data: 404 → проект есть или нет, 403/409 → перечитать и исключение, кэш;
  - `RemoveMemberUseCase`: инициатор и сам пользователь без запроса;
  - `GetParticipatingProjects`: фильтр `viewerIsMember` / `!viewerIsInitiator`, порядок, сбой;
  - ViewModel: страж «Выгнать», бейдж и строка «Отклики · N», вкладка «Я участник».
- **`projectdetails`**:
  - `LeaveProjectUseCase` во всех ветках; data: 403/409/404;
  - ViewModel: видимость «Откликнуться» (инициатор, участник, статус), «Вы откликнулись» из потока, страж выхода, баннер 04i и перечитывание.
- **ViewModel `responses:ui`**:
  - 15: вкладки по типу, действия по правилам статусов, страж по id, `onShown` без ошибки при наличии списка, `markInvitationsSeen`;
  - 04s: счётчик и страж;
  - 07d: фильтр и числа, `RoleFull` и 07r, 07q, страж;
  - 09k: автовыбор, `preselected`, активность кнопки, ошибки.
- **`:shared`**:
  - адаптеры `ProjectsResponsesProjectSource` (`refreshProject` не бросает), `ResponsesParticipationSource` (фильтр статусов, `distinct`), `ResponsesAppliedRolesSource`;
  - graph-тест: `ResponsesUiDependencies`, `ComplaintsUiDependencies` собираются; один `ResponsesRepositoryImpl` на сессию обслуживает оба интерфейса.

## 9. Допущения, открытые решения, пробелы API

**Допущения (для `docs/sprint-2.md`, «Пробелы дизайна и допущения»)**

1. Оба списка откликов читаются целиком (страницы по 100, не больше 10) в кэш сессии. Экран 15 перечитывает список при каждом входе на вкладку.
2. 07d: решённые записи остаются с чипом (07p). После «принять» показывается баннер 07q. Кадр 07q с «карточка ушла» не используется, чтобы при повторном входе запись не возвращалась. Приглашения инициатора в 07d не показываются. ✓ и × шлют решение без диалога, как в макете.
3. 15: вкладки по `type`. Запись без `type` — во вкладке «Отклики», без действий (15h не используется). Записи с невидимым проектом не показываются. Строка автора на 15c — имя инициатора без вуза (вуза в `Project` нет).
4. Если код статуса в ответе на решение или отзыв не распознан, запись получает статус по действию.
5. «Вы откликнулись» — по записям типа `Application` со статусом `Pending` или `null`. «Откликнуться» не предлагается инициатору, участнику и при статусе проекта, отличном от «Набор открыт» или неизвестного.
6. Точка на «Отклики» — по ожидающим приглашениям новее последнего просмотра 15. Приглашение без времени новым не считается.
7. Жалоба: вход через шит 09c или 09j, из чата — пунктом меню 13b прямо в 17. Если цель жалобы не прочиталась, форма идёт без карточки цели.
8. «Пригласить»: варианты — свои проекты со статусом «Набор открыт» или неизвестным и свободной ролью. Из 08 с ролью шит открывается с выбранной ролью (аргументы маршрута профиля). После успеха на 09 баннер 09q до ухода с экрана.
9. Подпись «статус неизвестен» на 07b (07w) не показывается: статус участника не читается ни у кого (G52).
10. Новые тексты (ru / en), которых нет в макетах:
    - «Проект или роль больше недоступны» / The project or the role is no longer available;
    - «Отклики» (строка входа без числа) / Applications;
    - «Действие недоступно» / This action is not available;
    - «Пожаловаться» (пункт меню 13b) — `components_report`;
    - «отклик сегодня» / «отклик N дней назад» (plurals) / applied today / applied N days ago.
    
    Остальные тексты берутся из кадров (`design-progress.md`), английский — перевод compose-ui.

**Открытые решения** (рекомендация и одна альтернатива)

- **Списки откликов.** Рекомендация: читать целиком. Альтернатива: страницы по 20 с `LoadMoreFooter`, числа из `total` (G66: точность `total` не описана, числа ролей тогда неточны).
- **07d после решения.** Рекомендация: чип (07p) и баннер. Альтернатива: запись уходит из списка (07q), и на экране только ожидающие.
- **Ошибки 400 у жалобы.** Сейчас общий текст 17g. Альтернатива — отдельный текст «Проверьте текст жалобы».

**Пробелы API блоков**:

- G48 — повтор создаёт новую жалобу, новый отклик или приглашение.
- G52 — статус участника.
- G55 — записи без `id`.
- G58, G59 — коды наши, не подтверждены.
- G60 — обязательные поля и предел `message`.
- G61 — смысл `pending_join_count`, нет push.
- G65 — последствия выхода и исключения.
- G66 — порядок, `total`, нет сводки проекта в записи.
- G67 — «Я участник» через N+1 запросов.
- G68 — `subject` кодом, вложений и чтения жалоб нет.

Новых Gnn нет.

**Строки для `docs/api-values.md`**:

- «Причина жалобы на пользователя `CreateComplaint.subject`: `1` Спам или реклама, `2` Оскорбления, `3` Фальсификация в портфолио, `4` Фейковый аккаунт, `5` Мошенничество, `6` Другое (`UserComplaintReason`, `feature:complaints:domain`)».
- «Причина жалобы на проект: `1` Спам или реклама, `2` Недостоверная информация, `3` Мошенничество, `4` Другое (`ProjectComplaintReason`)».

**Строки для `docs/api-gaps.md`**:

- «G51 — закрыт: отклики, приглашения, участники подключены (блоки 6–8)».
- «G60 — отклик шлёт `project_id`, `role_id`, `message?`; приглашение — плюс `candidate_user_id`; предел 500 клиентский».
- «G61 — индикаторы внутри приложения: `pending_join_count` (06, 07), точка на вкладке по приглашениям».
- «G66 — списки читаются целиком (100 × 10), проект и кандидат — отдельными запросами».
- «G67 — «Я участник» по записям `Accepted` или `null` и `members`».
- «G68 — `subject` = код причины; вложений нет».

Проверок не было: Gradle не запускался, на сервере ничего не проверялось, визуальная проверка на устройстве не выполнялась.