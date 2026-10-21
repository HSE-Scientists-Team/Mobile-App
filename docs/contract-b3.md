# Блок 3 «Собрать команду» (маркер `b3`): контракты между слоями для параллельной работы domain-data и compose-ui

Статус: проект architect на 2026-10-18; блок реализован (порядок и статусы — `docs/sprint-1.md`), документ остаётся контрактом слоёв. Расхождения решает код и `docs/api-gaps.md`. Блок охватывает экраны 08 и 08b (поиск людей на роль с фильтрами, по страницам), 01 и 01c (рекомендации людей на ленте).

Основания:
- `docs/openapi.yaml` v2.5.0: `GET /api/v1/search/people`, `GET /api/v1/recommendations/people`, схемы `Person` и `PeopleList`;
- `docs/api-gaps.md`;
- правила «без догадок» и «заменил мок — удали мок».

Оба агента реализуют документ буквально. Если отклониться всё же пришлось, это отклонение пишется в отчёт, а контракт не меняется в одиночку.

---

## 0. Что нового по сравнению с прежним проектом блока

Прежний проект — раздел 4 `/Users/samvel/Desktop/Study/Methodology/AcademicNet/docs/gateway-blocks3-5-search-and-projects.md`.

| # | Было | Стало | Почему |
|---|---|---|---|
| 1 | Моки переписываются (страницы, `total`); DI через `when (backend)` и `Lazy<GatewaySession>` | `MockCollaboratorsDataSource` (вместе с `MockPerson`, `MockCollaborators`, `matchPercent`), `MockFeedDataSource` (вместе с `MockFeedData`) и их тесты удаляются. Провайдеры работают только с Gateway и берут `GatewaySession` без `Lazy`, как у профиля и настроек | Правило «удали мок»; единообразие с блоком 2 |
| 2 | `project_type = workTypes.singleOrNull()` через словарь; поле `PeopleSearchParams.projectType` | Поля `projectType` нет вовсе, `project_type` не отправляется никогда. Вводится `CollaboratorsCapabilities(projectTypeFilter = false)`, группа «Тип работы» в 08b скрыта | G11, правило «без догадок» |
| 3 | Домен ленты не меняется; `GatewayFeedDataSource.getViewer()` отдаёт `UserSummary(viewerId, "")` без запроса | `FeedRepository.getViewer`, `FeedDataSource.getViewer` и `GetViewerUseCase` удаляются. Id зрителя приходит в `FeedUiDependencies.viewerId` из сессии | Шапке нужен только id. Асинхронный вызов ради уже известного значения и объект с выдуманным пустым именем — лишний код. Альтернатива — в §9.4 |
| 4 | VM 08 хранит `loadMore: LoadMoreState`; «Повторить» в футере зовёт `onLoadMore` | Форма состояния как у 09b (`UserReviewsViewModel`): `hasMore`, `isLoadingMore`, `loadMoreFailure`, методы `onLoadMore` и `onRetryLoadMore`. Перевод в `LoadMoreState` делает новая функция `loadMoreStateOf` в `:components` | Ни один ViewModel проекта не импортирует `:components`; поведение совпадает с 09b |
| 5 | `CollaboratorItem(user, subtitle)` | `CollaboratorItem(collaborator)` (форма после блока 2); подпись строит `subtitleText()` в UI | Уровень и курс переводятся в UI (блок 2) |
| 6 | — | `ApiRequests` (internal) умеет повторять параметр: `skill=a&skill=b` | `call(query: Map<String, String?>)` не может повторить имя, а `skill[]` — массив (по умолчанию OpenAPI `form, explode`) |
| 7 | В `PeopleSearchParams` нет `interest`, `my_university`, `open_only` | Есть все определённые параметры, кроме `project_type`. Приложение отправляет только то, что есть в 08b | Клиент описывает эндпоинт целиком; «Мой вуз» включается потом одной строкой |
| 8 | Рекомендации: без самого зрителя и без записей без id | Плюс без повторов по id | Ключи `LazyColumn` должны быть уникальны |
| 9 | «Показать N» всегда считается запросом | При открытии шита число берётся из `total` актуальной выдачи без запроса. Запрос `count` уходит только после изменения черновика | Без лишнего запроса число видно сразу |
| 10 | — | `CollaboratorSearchUiState.resultsVersion`: после новой выдачи список показывается с начала | Иначе после «Применить» список остаётся на старой позиции и сразу догружает |
| 11 | Сбой новой выдачи при видимых результатах не описан | Сбой первой страницы любой выдачи — полноэкранная ошибка, прежние результаты убираются | Прежний список не соответствует новому запросу |
| 12 | — | Новые пробелы G33 (рекомендации) и G34 (порядок и `total` поиска); G30 уточнён | Правило реестра |
| 13 | — | Данные `:mockserver` в блоке 3 не удаляются, правится только KDoc (подробности — в §5) | Таблицы ещё читают моки проектов |

---

## 1. Сводка поведения блока

**08, вход с роли.** Путь: Проекты → проект → карточка вакантной роли → поиск.
- Заголовок экрана — «Коллабораторы», подзаголовок — название роли.
- В строку поиска подставляются навыки роли через «, ». Источник — `ProjectsRoleRequirementProvider`, он не меняется и пока читает мок проектов.
- Запрос: `GET /search/people?q=<строка как введена>&limit=20&offset=0`.
- Роль на сервер не уходит (G31). Процента совпадения и порядка по роли нет.

**08, вход с ленты** (строка поиска в режиме «Люди»). Строка пустая, курсор в ней, приходят все люди.

**Выдача.**
- «Найдено N» = `total` сервера.
- Страницы по 20, следующая — при прокрутке.
- Футер как на 09b: «Загрузка…» или «Не удалось загрузить» + «Повторить».
- Ввод текста запускает поиск через 300 мс после последней клавиши; «поиск» на клавиатуре и «Применить» — сразу.
- Сбой первой страницы: «Не удалось загрузить список», под ним причина (`failureText`), кнопка «Повторить».

**08b, шит фильтров.**

| Фильтр | Параметр запроса |
|---|---|
| «Верифицированный профиль» | `verified_only=true` |
| «Есть верифицированные проекты» | `verified_projects=true` |
| «Есть публикации» | `publications=true` |
| «Вуз» (текст) | `university` |
| «Ключевые слова» | `skill`, повторяется для каждого слова |
| «Тип работы» | скрыт (G11) |

«Показать N» показывает число сервера (`count` = `limit=1`, через 300 мс после изменения черновика). Пока идёт счёт или после ошибки на кнопке просто «Показать».

**01c.**
- При первом выборе «Люди» уходит `GET /recommendations/people?limit=10`. Это случайные люди, открытые к сотрудничеству (G32). Сам пользователь, повторы и записи без id отбрасываются.
- Ответ живёт, пока жив экран: переключение «Проекты ↔ Люди» не повторяет запрос.
- Пусто: «Пока нет рекомендаций» / «Здесь появятся люди, открытые к сотрудничеству» + кнопка «Найти людей» (открывает 08).
- Ошибка: «Не удалось загрузить рекомендации», причина, «Повторить».
- Аватар в шапке строится по id пользователя сессии, без запроса.

**Что отключено пробелами API.**
- G11: группа «Тип работы» скрыта.
- G30: рейтинг из `Person` не читается; в строках «List item/Person» его и так нет по макету.
- G31: роль не уходит на сервер, смысл `q` неизвестен.
- G32: рекомендации не подбираются под проекты пользователя.
- G33, G34 (новые): см. §9.1.

---

## 2. Контракты (готовый Kotlin)

### 2.1 `:common`

Без изменений.
- `Page`, `PageRequest`, `PagedList` (`common/paging`), `Collaborator`, `Study`, `UserSummary`, `UserId`, `ProjectType` используются как есть.
- `FeatureUnavailableException` в блоке 3 не нужен. Заблокированных операций нет: недоступен только один фильтр, и он не отправляется и скрыт.

### 2.2 `:gateway` (domain-data)

```kotlin
// File: /Users/samvel/Desktop/Study/Methodology/AcademicNet/gateway/src/commonMain/kotlin/org/matchlab/academic_net/gateway/PeopleSearchApi.kt
package org.matchlab.academic_net.gateway

import org.matchlab.academic_net.common.paging.PageRequest
import org.matchlab.academic_net.gateway.dto.PeopleListDto

/**
 * The people search of the Gateway ("08 Поиск коллабораторов"). Every call throws `RemoteFailure` when it fails and lets
 * a `CancellationException` through, like [AccountApi].
 */
interface PeopleSearchApi {
    /**
     * `GET /api/v1/search/people`: one [page] of the people the server finds for [params]. `limit` and `offset` are always
     * sent (their bounds are those of [PageRequest]); a filter of [params] only when it restricts something.
     */
    suspend fun search(params: PeopleSearchParams, page: PageRequest): PeopleListDto
}

/**
 * The filters of `GET /api/v1/search/people` that the spec defines. Nothing that means "no restriction" is sent: a blank
 * string, a blank item of a list, an empty list, `false`. A list goes as the parameter repeated (`skill=a&skill=b`, the
 * OpenAPI default for an array in the query).
 *
 * API-GAP G11: `project_type` has no list of values, so there is no field for it and nothing can send it.
 * API-GAP G31: there is no parameter for a role, and what [q] is matched against is not described.
 *
 * @property q `q`: the text of the search bar
 * @property university `university`
 * @property skills `skill`, repeated
 * @property interests `interest`, repeated
 * @property myUniversity `my_university=true`
 * @property openOnly `open_only=true`
 * @property verifiedOnly `verified_only=true`
 * @property verifiedProjects `verified_projects=true`
 * @property publications `publications=true`
 */
data class PeopleSearchParams(
    val q: String? = null,
    val university: String? = null,
    val skills: List<String> = emptyList(),
    val interests: List<String> = emptyList(),
    val myUniversity: Boolean = false,
    val openOnly: Boolean = false,
    val verifiedOnly: Boolean = false,
    val verifiedProjects: Boolean = false,
    val publications: Boolean = false,
)
```

```kotlin
// File: /Users/samvel/Desktop/Study/Methodology/AcademicNet/gateway/src/commonMain/kotlin/org/matchlab/academic_net/gateway/RecommendationsApi.kt
package org.matchlab.academic_net.gateway

import org.matchlab.academic_net.gateway.dto.PeopleListDto

/** What the Gateway recommends. Every call throws `RemoteFailure` when it fails and lets a `CancellationException` through. */
interface RecommendationsApi {
    /**
     * `GET /api/v1/recommendations/people?limit`: random people open to collaboration (API-GAP G32: they are not chosen
     * for the viewer's projects). API-GAP G33: the spec gives [limit] no bounds and no default and does not say whether
     * the viewer can be among the people. [limit] must be positive (`IllegalArgumentException`, no request otherwise).
     */
    suspend fun people(limit: Int): PeopleListDto
}
```

```kotlin
// File: /Users/samvel/Desktop/Study/Methodology/AcademicNet/gateway/src/commonMain/kotlin/org/matchlab/academic_net/gateway/dto/PeopleDtos.kt
package org.matchlab.academic_net.gateway.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// The shapes of the people calls of docs/openapi.yaml (schemas Person, PeopleList). Only the server sends them, so every
// field is not null and defaults to zero: protobuf JSON leaves zeros out, and a `null` is coerced to the default.
// API-GAP G10: `study_level` is a free text. API-GAP G18: `avatar_file_id` is not shown (no Media yet).
// API-GAP G30: there is no `review_count`, so `rating` cannot tell "no reviews" from a low rating; it is not read.

/** `Person`: one person of the search or of the recommendations. */
@Serializable
data class PersonDto(
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

/** `PeopleList`: a page of people and the server's count of all of them (`int64`: a string or a number). */
@Serializable
data class PeopleListDto(
    val items: List<PersonDto> = emptyList(),
    @Serializable(with = FlexibleLongSerializer::class) val total: Long = 0,
)
```

```kotlin
// File: /Users/samvel/Desktop/Study/Methodology/AcademicNet/gateway/src/commonMain/kotlin/org/matchlab/academic_net/gateway/mapping/PageMapping.kt
package org.matchlab.academic_net.gateway.mapping

import org.matchlab.academic_net.common.paging.Page
import org.matchlab.academic_net.common.paging.PageRequest

/**
 * The page the server answered [request] with: [items] are the records the app could read, [received] how many it sent,
 * readable or not. The size of the whole list is [reportedTotal], but never less than what has been received up to the
 * end of this page (`request.offset + received`, counted in Long): a total that is missing (0), negative or too small
 * does not end the list early. A total beyond [Int.MAX_VALUE] is cut to it.
 */
fun <T> pageOf(items: List<T>, received: Int, reportedTotal: Long, request: PageRequest): Page<T>
```

```kotlin
// File: /Users/samvel/Desktop/Study/Methodology/AcademicNet/gateway/src/commonMain/kotlin/org/matchlab/academic_net/gateway/mapping/PeopleMapping.kt
package org.matchlab.academic_net.gateway.mapping

import org.matchlab.academic_net.common.model.Collaborator
import org.matchlab.academic_net.common.paging.Page
import org.matchlab.academic_net.common.paging.PageRequest
import org.matchlab.academic_net.gateway.dto.PeopleListDto
import org.matchlab.academic_net.gateway.dto.PersonDto

/**
 * The person as a row of the people search and of the feed: the id, the name with its outer spaces cut (an empty one is
 * the UI's "Без имени"), `verified`, the university, the study and the field of study (`specialization`). `null` for a
 * record without a `user_id`: it could be neither opened nor told apart from the others. API-GAP G10: the study level is
 * the server's text as it is (blank is `null`), a year of 0 or less is "not given". API-GAP G18: no avatar.
 * API-GAP G30: the rating is not read.
 */
fun PersonDto.toCollaborator(): Collaborator?

/**
 * One page of the people search: the readable people in the server's order. A record without a `user_id` is left out
 * of [Page.items] but counted in [Page.received], so the next page starts after it; the total goes through [pageOf].
 */
fun PeopleListDto.toCollaboratorPage(request: PageRequest): Page<Collaborator>
```

```kotlin
// File: /Users/samvel/Desktop/Study/Methodology/AcademicNet/gateway/src/commonMain/kotlin/org/matchlab/academic_net/gateway/GatewaySession.kt
// Two new properties next to profiles / achievements / reviews / users:

    /** The people search of "08". */
    val peopleSearch: PeopleSearchApi = KtorPeopleSearchApi(requests)

    /** What the server recommends: the people of the feed ("01c"). */
    val recommendations: RecommendationsApi = KtorRecommendationsApi(requests)
```

Внутреннее устройство (тела на усмотрение domain-data, compose-ui их не видит):
- `internal class KtorPeopleSearchApi(requests: ApiRequests)`: `GET search/people`, правила `PeopleSearchParams`.
- `internal class KtorRecommendationsApi(requests: ApiRequests)`: `GET recommendations/people`, параметр `limit`, проверка `require(limit > 0)`.
- `ApiRequests.call(…)` и `get(…)` получают параметр `repeatedQuery: Map<String, List<String>> = emptyMap()`. Каждое значение уходит отдельным `name=value` (Ktor `parameter()` дописывает, а не заменяет — проверено по исходникам Ktor). Пустые строки отбрасываются. Существующие вызовы не меняются: после `query` параметры передаются по имени.
- `mapping/UserMapping.kt`: общий помощник `internal fun studyOf(level: String?, year: Int?): Study` (обрезка; пустой уровень → `null`; курс `<= 0` → `null`). Его используют `UserProfileDto.toCollaborator()` (поведение не меняется) и `PersonDto.toCollaborator()`.

### 2.3 `feature:collaborators:domain` (domain-data, входит в «domain-ready»)

```kotlin
// File: /Users/samvel/Desktop/Study/Methodology/AcademicNet/feature/collaborators/domain/src/commonMain/kotlin/org/matchlab/academic_net/feature/collaborators/domain/Collaborator.kt
package org.matchlab.academic_net.feature.collaborators.domain

import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.ProjectType
import org.matchlab.academic_net.common.model.RoleId

/** The filters of "08b Фильтры коллабораторов". Everything off means "no restriction". */
data class CollaboratorFilters(
    val verifiedProfile: Boolean = false,
    val hasVerifiedProjects: Boolean = false,
    val hasPublications: Boolean = false,
    /**
     * The types of the person's works. API-GAP G11: the API has no list of values of a project type, so only a source
     * whose [CollaboratorsCapabilities.projectTypeFilter] is on applies this, and the filters sheet offers it only then.
     */
    val workTypes: Set<ProjectType> = emptySet(),
    /** The university as typed; blank means any. How it is matched is the server's. */
    val university: String = "",
    /** "Ключевые слова": sent as the skills the server looks for. */
    val keywords: List<String> = emptyList(),
) {
    val isEmpty: Boolean get() = this == CollaboratorFilters()

    /** How many filters restrict the result: the dot on the filter button. */
    val activeCount: Int
        get() = listOf(
            verifiedProfile,
            hasVerifiedProjects,
            hasPublications,
            workTypes.isNotEmpty(),
            university.isNotBlank(),
            keywords.isNotEmpty(),
        ).count { it }
}

/**
 * The role a search is started for: the title of the screen and the first text of the search bar (the role's skills)
 * come from it. API-GAP G31: the server does not take a role, so the search itself is by the text and the filters only.
 */
data class RoleReference(
    val projectId: ProjectId,
    val roleId: RoleId,
)

/**
 * What the user asked for. Matching [text] and [filters] and ordering the people is the server's; the app sends this as
 * it is and shows the answer page by page.
 */
data class CollaboratorQuery(
    /** The search bar as typed. */
    val text: String = "",
    val filters: CollaboratorFilters = CollaboratorFilters(),
)

/** What a role asks for: the search bar of the people search starts from its skills. */
data class RoleRequirement(
    val projectTitle: String,
    val roleTitle: String,
    val skills: List<String>,
)

class RoleNotFoundException(val projectId: ProjectId, val roleId: RoleId) :
    NoSuchElementException("Role ${roleId.value} of project ${projectId.value} not found")

/**
 * What the people search can do with the server it talks to. The filters sheet reads the flags and hides what the server
 * cannot do, instead of offering a filter without effect. Every flag is closed by one gap of `docs/api-gaps.md`: when the
 * backend fills the gap, the data source turns the flag on and sends the filter.
 *
 * @property projectTypeFilter API-GAP G11: `project_type` of the search has no list of values, so the filter by the types
 * of a person's works ([CollaboratorFilters.workTypes]) is not sent, and the group "Тип работы" of "08b" is hidden.
 */
data class CollaboratorsCapabilities(
    val projectTypeFilter: Boolean,
)
```

`ScoredCollaborator` и `CollaboratorQuery.role` удаляются.

```kotlin
// File: /Users/samvel/Desktop/Study/Methodology/AcademicNet/feature/collaborators/domain/src/commonMain/kotlin/org/matchlab/academic_net/feature/collaborators/domain/CollaboratorsRepository.kt
package org.matchlab.academic_net.feature.collaborators.domain

import org.matchlab.academic_net.common.model.Collaborator
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.RoleId
import org.matchlab.academic_net.common.paging.Page
import org.matchlab.academic_net.common.paging.PageRequest

/**
 * The people search. The server matches and orders the people; the calls throw on failure (no network, a server error)
 * and the use cases turn that into a result.
 */
interface CollaboratorsRepository {
    /** What the server behind the search can do; the screens hide the rest (see [CollaboratorsCapabilities]). */
    val capabilities: CollaboratorsCapabilities

    /**
     * One [page] of the people the server finds for [query], in the server's order. [Page.total] is the server's count of
     * all of them ("Найдено N"); a record the app cannot read is not in [Page.items] but is counted in [Page.received],
     * so the next page starts after it.
     */
    suspend fun search(query: CollaboratorQuery, page: PageRequest): Page<Collaborator>

    /** How many people the server finds for [query], without loading them: "Показать N" of the filters sheet. */
    suspend fun count(query: CollaboratorQuery): Int
}

/**
 * The role a search is started for. The roles belong to the projects feature, which implements this where the app is
 * assembled, so the two features do not depend on each other.
 */
interface RoleRequirementProvider {
    /** `null` if the project or the role does not exist. */
    suspend fun getRoleRequirement(projectId: ProjectId, roleId: RoleId): RoleRequirement?
}
```

```kotlin
// File: /Users/samvel/Desktop/Study/Methodology/AcademicNet/feature/collaborators/domain/src/commonMain/kotlin/org/matchlab/academic_net/feature/collaborators/domain/usecase/CollaboratorUseCases.kt
package org.matchlab.academic_net.feature.collaborators.domain.usecase

import org.matchlab.academic_net.common.model.Collaborator
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.RoleId
import org.matchlab.academic_net.common.paging.Page
import org.matchlab.academic_net.common.paging.PageRequest
import org.matchlab.academic_net.feature.collaborators.domain.CollaboratorQuery
import org.matchlab.academic_net.feature.collaborators.domain.CollaboratorsRepository
import org.matchlab.academic_net.feature.collaborators.domain.RoleNotFoundException
import org.matchlab.academic_net.feature.collaborators.domain.RoleRequirement
import org.matchlab.academic_net.feature.collaborators.domain.RoleRequirementProvider
import org.matchlab.academic_net.utils.suspendRunCatching

/** "Искать людей в проект на роль по фильтрам": one page of the server's answer. */
class SearchCollaboratorsUseCase(private val repository: CollaboratorsRepository) {
    suspend operator fun invoke(query: CollaboratorQuery, page: PageRequest): Result<Page<Collaborator>> =
        suspendRunCatching { repository.search(query, page) }
}

/** "Показать N" of "08b": how many people the filters being edited would show. */
class CountCollaboratorsUseCase(private val repository: CollaboratorsRepository) {
    suspend operator fun invoke(query: CollaboratorQuery): Result<Int> =
        suspendRunCatching { repository.count(query) }
}

/** The role's title and skills: the title of the screen and the first text of the search bar. */
class GetRoleRequirementUseCase(private val provider: RoleRequirementProvider) {
    suspend operator fun invoke(projectId: ProjectId, roleId: RoleId): Result<RoleRequirement> =
        suspendRunCatching {
            provider.getRoleRequirement(projectId, roleId) ?: throw RoleNotFoundException(projectId, roleId)
        }
}
```

### 2.4 `feature:collaborators:data` (domain-data)

```kotlin
// File: …/feature/collaborators/data/src/commonMain/kotlin/org/matchlab/academic_net/feature/collaborators/data/CollaboratorsDataSource.kt
/** Where the people come from: the Gateway ([GatewayCollaboratorsDataSource]); see [CollaboratorsRepository]. */
interface CollaboratorsDataSource {
    val capabilities: CollaboratorsCapabilities
    suspend fun search(query: CollaboratorQuery, page: PageRequest): Page<Collaborator>
    suspend fun count(query: CollaboratorQuery): Int
}

// File: …/CollaboratorsRepositoryImpl.kt — delegates all three members to the source
class CollaboratorsRepositoryImpl(private val dataSource: CollaboratorsDataSource) : CollaboratorsRepository

// File: …/GatewayCollaboratorsDataSource.kt
/**
 * [CollaboratorsDataSource] over `GET /api/v1/search/people`. Any status but a 2xx and every failure of the network is
 * thrown as the `RemoteFailure` it is: the search has no outcome of its own.
 */
class GatewayCollaboratorsDataSource(private val api: PeopleSearchApi) : CollaboratorsDataSource {
    // API-GAP G11: `project_type` has no list of values, so the filter by the types of works is not sent.
    override val capabilities: CollaboratorsCapabilities = CollaboratorsCapabilities(projectTypeFilter = false)

    override suspend fun search(query: CollaboratorQuery, page: PageRequest): Page<Collaborator> =
        api.search(query.toParams(), page).toCollaboratorPage(page)

    override suspend fun count(query: CollaboratorQuery): Int = search(query, CountProbe).total

    private companion object {
        /** The smallest page: only its total is needed. */
        val CountProbe = PageRequest(offset = 0, limit = 1)
    }
}

/** API-GAP G31: `q` goes as typed (only the outer spaces are cut); API-GAP G11: `workTypes` is not sent. */
internal fun CollaboratorQuery.toParams(): PeopleSearchParams
```

Пути выше даны от `/Users/samvel/Desktop/Study/Methodology/AcademicNet/feature/collaborators/data/src/commonMain/kotlin/org/matchlab/academic_net/feature/collaborators/data/`.

Правила `toParams()` (их же проверяет tester):

| Поле домена | Параметр запроса | Правило |
|---|---|---|
| `text` | `q` | `trim()`; пустая строка не уходит; запятые и всё остальное — как введено (G31) |
| `filters.verifiedProfile` | `verified_only` | только `true` |
| `filters.hasVerifiedProjects` | `verified_projects` | только `true` |
| `filters.hasPublications` | `publications` | только `true` |
| `filters.university` | `university` | `trim()`; пустая строка не уходит |
| `filters.keywords` | `skill`, повторяется | `trim()`, пустые отброшены, `distinct()`, порядок сохранён |
| `filters.workTypes` | — | не уходит, пометка `// API-GAP G11` |
| — | `interest`, `my_university`, `open_only` | не уходят: в 08b их нет |
| `PageRequest` | `limit`, `offset` | уходят всегда |

`count(query)` — это `search(query, PageRequest(0, 1)).total`. После `pageOf` оно не меньше числа полученных записей.

### 2.5 `feature:feed:domain` (domain-data, входит в «domain-ready»)

```kotlin
// File: /Users/samvel/Desktop/Study/Methodology/AcademicNet/feature/feed/domain/src/commonMain/kotlin/org/matchlab/academic_net/feature/feed/domain/FeedRepository.kt
package org.matchlab.academic_net.feature.feed.domain

import org.matchlab.academic_net.common.model.Collaborator

/**
 * What the feed shows. The server chooses the recommendations; the call throws on failure (no network, a server error)
 * and the use case turns that into a result.
 */
interface FeedRepository {
    /**
     * "Люди для ваших проектов": the people the server recommends, in its order, without the viewer and without
     * repeats. API-GAP G32: the server picks random people open to collaboration, not people for the viewer's projects.
     */
    suspend fun getRecommendedPeople(): List<Collaborator>
}
```

```kotlin
// File: /Users/samvel/Desktop/Study/Methodology/AcademicNet/feature/feed/domain/src/commonMain/kotlin/org/matchlab/academic_net/feature/feed/domain/usecase/FeedUseCases.kt
package org.matchlab.academic_net.feature.feed.domain.usecase

import org.matchlab.academic_net.common.model.Collaborator
import org.matchlab.academic_net.feature.feed.domain.FeedRepository
import org.matchlab.academic_net.utils.suspendRunCatching

/** "Получать рекомендации людей на роль в проекте": the people block of the feed. */
class GetRecommendedPeopleUseCase(private val repository: FeedRepository) {
    suspend operator fun invoke(): Result<List<Collaborator>> =
        suspendRunCatching { repository.getRecommendedPeople() }
}
```

`GetViewerUseCase` и `FeedRepository.getViewer()` удаляются.

### 2.6 `feature:feed:data` (domain-data)

```kotlin
// File: /Users/samvel/Desktop/Study/Methodology/AcademicNet/feature/feed/data/src/commonMain/kotlin/org/matchlab/academic_net/feature/feed/data/FeedDataSource.kt
/** Where the feed's content comes from: the Gateway ([GatewayFeedDataSource]); see [FeedRepository]. */
interface FeedDataSource {
    suspend fun getRecommendedPeople(): List<Collaborator>
}

// FeedRepositoryImpl.kt — delegates
class FeedRepositoryImpl(private val dataSource: FeedDataSource) : FeedRepository

// GatewayFeedDataSource.kt
/** [FeedDataSource] over `GET /api/v1/recommendations/people?limit`; failures are thrown as `RemoteFailure`. */
class GatewayFeedDataSource(
    private val api: RecommendationsApi,
    private val viewerId: UserId,
    private val limit: Int = DefaultLimit,
) : FeedDataSource {
    init {
        require(limit > 0) { "The feed must ask for at least one person: $limit" }
    }

    override suspend fun getRecommendedPeople(): List<Collaborator> =
        api.people(limit).items
            .mapNotNull { it.toCollaborator() }
            // API-GAP G33: the API does not say whether the viewer can be recommended to themselves, or twice.
            .filter { it.user.id != viewerId }
            .distinctBy { it.user.id }

    companion object {
        /** How many people the feed asks for: the block is a short list, not a search. */
        const val DefaultLimit = 10
    }
}
```

`FeedRepositoryImpl.kt` и `GatewayFeedDataSource.kt` лежат в том же каталоге, что и `FeedDataSource.kt`.

### 2.7 `:components` (compose-ui)

```kotlin
// File: /Users/samvel/Desktop/Study/Methodology/AcademicNet/components/src/commonMain/kotlin/org/matchlab/academic_net/components/paging/LoadMore.kt
/**
 * The end of a list from what its view model knows: a failure is shown until the retry (even if more could be loaded),
 * then a page on its way, then whether the server may have more ([LoadMoreState.Idle]) or the list is over.
 */
fun loadMoreStateOf(isLoadingMore: Boolean, failed: Boolean, hasMore: Boolean): LoadMoreState = when {
    failed -> LoadMoreState.Failed
    isLoadingMore -> LoadMoreState.Loading
    hasMore -> LoadMoreState.Idle
    else -> LoadMoreState.End
}
```

Порядок тот же, что в 09b. Сам экран 09b в этом блоке не меняется, перевод его на функцию — §9.4. Записи в `:catalog` не нужно: это функция, а не компонент.

### 2.8 `feature:collaborators:ui` (compose-ui)

```kotlin
// File: …/feature/collaborators/ui/src/commonMain/kotlin/org/matchlab/academic_net/feature/collaborators/ui/CollaboratorsUiDependencies.kt
/** What the screens of the collaborators feature need from the outside; the app creates it once per session. */
class CollaboratorsUiDependencies(
    val getRoleRequirement: GetRoleRequirementUseCase,
    val searchCollaborators: SearchCollaboratorsUseCase,
    val countCollaborators: CountCollaboratorsUseCase,
    /** What the server behind the search can do (API-GAP G11): the filters sheet hides the rest. */
    val capabilities: CollaboratorsCapabilities,
)
```

```kotlin
// File: …/CollaboratorSearchViewModel.kt (types visible to tests)
@Immutable
data class CollaboratorItem(val collaborator: Collaborator) {
    val user: UserSummary get() = collaborator.user
}

@Immutable
data class FiltersSheetState(
    val draft: CollaboratorFilters,
    /** "Показать N"; `null` while counting or when the count failed: the button says "Показать". */
    val previewCount: Int? = null,
)

@Immutable
data class CollaboratorSearchUiState(
    /** The role or the first page of a search started by the screen itself (opening, "Повторить") is on its way. */
    val isLoading: Boolean = true,
    /** Why the role or the first page failed: the full-screen error with "Повторить"; `null` otherwise. */
    val error: Throwable? = null,
    val roleTitle: String? = null,
    val query: String = "",
    val filters: CollaboratorFilters = CollaboratorFilters(),
    /** The pages loaded so far, without repeats by user id. */
    val results: List<CollaboratorItem> = emptyList(),
    /** "Найдено N": the server's count for the search, not the size of [results]. */
    val total: Int = 0,
    /** The server may have more: the list asks for the next page as it is scrolled. */
    val hasMore: Boolean = false,
    /** The next page is on its way: the footer says "Загрузка…". */
    val isLoadingMore: Boolean = false,
    /** The next page failed: the footer offers "Повторить"; nothing is asked for until it is pressed. */
    val loadMoreFailure: Throwable? = null,
    /** Grows by one each time the first page of a new search replaces [results] (not on the next pages). */
    val resultsVersion: Int = 0,
    val filtersSheet: FiltersSheetState? = null,
)

class CollaboratorSearchViewModel(
    private val role: RoleReference?,
    private val dependencies: CollaboratorsUiDependencies,
    private val pageSize: Int = PageRequest.DefaultLimit, // require(pageSize in 1..PageRequest.MaxLimit)
) : ViewModel() {
    val uiState: StateFlow<CollaboratorSearchUiState>
    fun onRetry()
    fun onQueryChange(query: String)
    fun onSearchSubmit()
    fun onLoadMore()
    fun onRetryLoadMore()
    fun onFiltersClick()
    fun onFiltersDraftChange(draft: CollaboratorFilters)
    fun onFiltersReset()
    fun onFiltersDismiss()
    fun onFiltersApply()
}

// File: …/CollaboratorSearchScreen.kt — the public entry point is unchanged
@Composable
fun CollaboratorSearchRoute(
    dependencies: CollaboratorsUiDependencies,
    role: RoleReference?,
    onNavigateBack: () -> Unit,
    onCollaboratorClick: (UserSummary) -> Unit,
    modifier: Modifier = Modifier,
    autoFocusSearch: Boolean = false,
)
```

Пути выше даны от `/Users/samvel/Desktop/Study/Methodology/AcademicNet/feature/collaborators/ui/src/commonMain/kotlin/org/matchlab/academic_net/feature/collaborators/ui/`.

**Протокол ViewModel.** Закрытое состояние VM:
- `generation: Int`;
- `searchJob`, `loadMoreJob`, `previewJob`, `requirementJob`;
- `paged: PagedList<CollaboratorItem>`, ключ — `{ it.user.id }`;
- `activeQuery: CollaboratorQuery` — запрос, который дал `paged`;
- `requirementLoaded: Boolean`.

Правила:

1. **Старт.**
   - Без роли — новая выдача: сразу и с полноэкранной загрузкой.
   - С ролью — сначала `getRoleRequirement`. При успехе: `roleTitle = roleTitle`; `query = skills.joinToString(", ")`, но только если строка ещё пуста; затем новая выдача сразу. При сбое: `isLoading = false`, `error = cause`.

2. **Новая выдача.** Начинается всегда одинаково:
   - отменить `searchJob` и `loadMoreJob`;
   - `gen = ++generation`;
   - в состоянии: `isLoadingMore = false`, `loadMoreFailure = null`; при полноэкранной загрузке ещё `isLoading = true`, `error = null`. Прежние результаты видны до ответа;
   - запустить `searchJob`: при отложенном старте `delay(300)`, затем запрос `CollaboratorQuery(state.query, state.filters)` с `PageRequest(0, pageSize)`.

   Какие действия как запускают выдачу:

   | Действие | Старт | Полноэкранная загрузка |
   |---|---|---|
   | `onQueryChange` | через 300 мс | нет |
   | `onSearchSubmit` | сразу | нет |
   | `onFiltersApply` | сразу | нет |
   | `onRetry` | сразу | да |
   | старт экрана | сразу | да |

   Обработка ответа:
   - если `gen != generation`, ответ ничего не меняет;
   - успех: `activeQuery = query`; `paged = PagedList.of(page.map(::CollaboratorItem)) { it.user.id }`; в состоянии: `isLoading = false`, `error = null`, `results`, `total`, `hasMore = paged.hasMore`, `resultsVersion + 1`;
   - сбой: `paged` пуст; в состоянии: `isLoading = false`, `error = cause`, `results = []`, `total = 0`, `hasMore = false`.

3. **`onLoadMore()`.**
   - Ничего не делает, если идёт `searchJob` или `loadMoreJob`, если `error != null`, если `loadMoreFailure != null` или если `paged.next == null`.
   - Иначе: `isLoadingMore = true`, затем запрос `activeQuery` с `paged.next`.
   - Ответ чужого поколения ничего не меняет.
   - Успех: `paged = paged.append(…) { it.user.id }`, в состоянии обновляются `results`, `total`, `hasMore`, `isLoadingMore = false`.
   - Сбой: `isLoadingMore = false`, `loadMoreFailure = cause`.

4. **`onRetryLoadMore()`.** Работает, только если `loadMoreFailure != null` и не идёт ни `searchJob`, ни `loadMoreJob`. Сбрасывает сбой и делает то же, что пункт 3.

5. **`onRetry()`.** Работает, только если `error != null` и не идут ни `requirementJob`, ни `searchJob`. Если требования роли ещё не загружены — повторяет пункт 1, иначе — новая выдача сразу с полноэкранной загрузкой.

6. **«Показать N».**
   - `onFiltersClick()`: открыть шит с `draft = filters`. Если выдача актуальна (`!isLoading`, `error == null`, `searchJob` не идёт), `previewCount = total` и запроса нет. Иначе `previewCount = null` и запускается `updatePreview(filters)`.
   - `onFiltersDraftChange(d)`: шит с черновиком `d` и `previewCount = null`, затем `updatePreview(d)`.
   - `updatePreview(d)`: отменить `previewJob`; после `delay(300)` вызвать `countCollaborators(CollaboratorQuery(state.query, d))`. Результат применяется, только если `filtersSheet?.draft == d`; сбой молчит.
   - `onFiltersReset()` = `onFiltersDraftChange(CollaboratorFilters())`.
   - `onFiltersDismiss()`: отменить `previewJob`, закрыть шит.
   - `onFiltersApply()`: если шита нет — ничего; иначе отменить `previewJob`, `filters = draft`, закрыть шит, новая выдача сразу.

7. Константы задержек (`private companion`): `SearchDebounceMillis = 300L`, `PreviewDebounceMillis = 300L`.

**Экран 08.** Состояния по порядку:
1. `error != null` → `MessageState(title = collaborators_error, text = failureText(error), actionText = collaborators_retry, onAction = onRetry)`.
2. `isLoading` → `LoadingState()`.
3. `results.isEmpty()` → `MessageState(collaborators_empty_title, text = collaborators_empty_search)`.
4. Иначе `LazyColumn(state = listState)`:
   - `item("header")` — «Найдено N» по `total`;
   - строки по ключу `user.id.value`, подпись — `collaborator.subtitleText()`;
   - `item("footer")` — `LoadMoreFooter(loadMoreStateOf(isLoadingMore, loadMoreFailure != null, hasMore), onRetry = onRetryLoadMore)`;
   - `LoadMoreEffect(listState, <то же состояние>, onLoadMore)`;
   - при смене `resultsVersion` список показывается с начала.

Шит фильтров: `CollaboratorFiltersSheet(state, showWorkTypes = dependencies.capabilities.projectTypeFilter, …)`. Группа «Тип работы» есть только при `showWorkTypes` (`// API-GAP G11`), пояснительной строки нет (как в 10b блока 2).

### 2.9 `feature:feed:ui` (compose-ui)

```kotlin
// File: /Users/samvel/Desktop/Study/Methodology/AcademicNet/feature/feed/ui/src/commonMain/kotlin/org/matchlab/academic_net/feature/feed/ui/FeedUiDependencies.kt
class FeedUiDependencies(
    /** The signed-in user: the header draws their avatar by the id, without a request (API-GAP G18: no avatars yet). */
    val viewerId: UserId,
    val getRecommendedPeople: GetRecommendedPeopleUseCase,
)

// File: …/feature/feed/ui/FeedViewModel.kt
enum class FeedScope { Projects, People }                       // unchanged

@Immutable
data class FeedPerson(val collaborator: Collaborator) {         // unchanged
    val user: UserSummary get() = collaborator.user
}

/** The people block of "01c". */
sealed interface FeedPeopleState {
    /** "Люди" has not been shown yet: nothing was asked. */
    data object NotLoaded : FeedPeopleState
    data object Loading : FeedPeopleState
    data class Failed(val cause: Throwable) : FeedPeopleState
    @Immutable data class Loaded(val people: List<FeedPerson>) : FeedPeopleState
}

@Immutable
data class FeedUiState(
    val viewerAvatarKey: String,
    val scope: FeedScope = FeedScope.Projects,
    val people: FeedPeopleState = FeedPeopleState.NotLoaded,
)

class FeedViewModel(
    viewerId: UserId,
    private val getRecommendedPeople: GetRecommendedPeopleUseCase,
) : ViewModel() {
    val uiState: StateFlow<FeedUiState>   // starts as FeedUiState(viewerAvatarKey = viewerId.value); no request at init
    fun onScopeSelect(scope: FeedScope)   // People while NotLoaded -> Loading -> Loaded / Failed
    fun onPeopleRetry()                    // only from Failed
}

// File: …/feature/feed/ui/FeedScreen.kt — unchanged entry point; created as FeedViewModel(dependencies.viewerId, dependencies.getRecommendedPeople)
@Composable
fun FeedRoute(
    dependencies: FeedUiDependencies,
    onProfileClick: () -> Unit,
    onSearchPeopleClick: () -> Unit,
    onPersonClick: (UserSummary) -> Unit,
    modifier: Modifier = Modifier,
    onSearchProjectsClick: (() -> Unit)? = null,
)
```

`FeedViewModel.kt` и `FeedScreen.kt` лежат в `/Users/samvel/Desktop/Study/Methodology/AcademicNet/feature/feed/ui/src/commonMain/kotlin/org/matchlab/academic_net/feature/feed/ui/`.

Блок «Люди» на 01c:

| Состояние | Что показывает |
|---|---|
| `NotLoaded`, `Loading` | индикатор в списке, как сейчас |
| `Failed(cause)` | `feed_people_error`, строка `failureText(cause)`, Outlined `feed_retry` |
| `Loaded([])` | `feed_people_empty_title`, `feed_people_empty_text`, Outlined `feed_people_find` → `onSearchPeopleClick` |
| `Loaded(people)` | строки, как сейчас |

Защита от повторов: загрузка начинается только из `NotLoaded` или `Failed` и сразу ставит `Loading`. Двойное «Повторить» даёт один запрос.

**Строки (ключи в `values` и `values-ru` совпадают 1:1).**

| Модуль | Ключ | ru | en | Изменение |
|---|---|---|---|---|
| collaborators | `collaborators_error` | «Не удалось загрузить список» | "Couldn't load the list" | текст изменён; причина выводится строкой ниже |
| feed | `feed_people_empty_text` | «Здесь появятся люди, открытые к сотрудничеству» | "People open to collaboration will appear here" | текст изменён |
| feed | `feed_people_find` | «Найти людей» | "Find people" | новый ключ |

Остальные ключи не меняются. Футер догрузки берёт строки `:components`.

### 2.10 `:shared` (DI — domain-data; навигация не меняется)

```kotlin
// File: /Users/samvel/Desktop/Study/Methodology/AcademicNet/shared/src/commonMain/kotlin/org/matchlab/academic_net/di/DataSourcesModule.kt
// Replaces feedDataSource() and collaboratorsDataSource(requirements); imports of the mocks and of RoleRequirementProvider go.
    /** The people of the feed (01c). API-GAP G32: random people open to collaboration; G33: the viewer and repeats are dropped. */
    @Provides
    @SessionScope
    fun feedDataSource(gateway: GatewaySession, session: Session): FeedDataSource =
        GatewayFeedDataSource(gateway.recommendations, viewerId = session.userId)

    /** The people search (08, 08b). API-GAP G11: no project-type filter; G31: the role is not sent. */
    @Provides
    @SessionScope
    fun collaboratorsDataSource(gateway: GatewaySession): CollaboratorsDataSource =
        GatewayCollaboratorsDataSource(gateway.peopleSearch)
// The KDoc of the interface: "The profiles, the settings, the people search and the feed are the Gateway's and have no mock".
```

```kotlin
// File: /Users/samvel/Desktop/Study/Methodology/AcademicNet/shared/src/commonMain/kotlin/org/matchlab/academic_net/di/CollaboratorsModule.kt
    @Provides
    fun collaboratorsUiDependencies(
        repository: CollaboratorsRepository,
        requirements: RoleRequirementProvider,
    ): CollaboratorsUiDependencies = CollaboratorsUiDependencies(
        getRoleRequirement = GetRoleRequirementUseCase(requirements),
        searchCollaborators = SearchCollaboratorsUseCase(repository),
        countCollaborators = CountCollaboratorsUseCase(repository),
        capabilities = repository.capabilities,
    )
// roleRequirementProvider(adapter) and collaboratorsRepository(source) do not change.
```

```kotlin
// File: /Users/samvel/Desktop/Study/Methodology/AcademicNet/shared/src/commonMain/kotlin/org/matchlab/academic_net/di/FeedModule.kt
    @Provides
    fun feedUiDependencies(repository: FeedRepository, session: Session): FeedUiDependencies = FeedUiDependencies(
        viewerId = session.userId,
        getRecommendedPeople = GetRecommendedPeopleUseCase(repository),
    )
// feedRepository(source) does not change.
```

**Навигация и связи.**
- `AppNavHost.kt`, `App.kt`, `SessionDependencies.kt`, `SessionComponent.kt` не меняются: сигнатуры `FeedRoute` и `CollaboratorSearchRoute` прежние.
- Кнопка «Найти людей» идёт через существующий `onSearchPeopleClick` → `navigateSingleTop(entry, Routes.PeopleSearch)`.
- Новых маршрутов и адаптеров нет; `ProjectsRoleRequirementProvider` не меняется.
- Фичи по-прежнему не зависят друг от друга. `:gateway` — инфраструктура, его подключает только `data`.

### 2.11 `build.gradle.kts`

- `feature/collaborators/data`: `implementation(project(":gateway"))`; исправить комментарий в шапке (сейчас там «Chats feature…»).
- `feature/feed/data`: `implementation(project(":mockserver"))` заменить на `implementation(project(":gateway"))`, поправить комментарий.
- `feature/collaborators/ui` и `feature/feed/ui`: убрать из `commonTest` строку `implementation(project(":feature:<x>:data"))` вместе с комментарием.
- Новых библиотек и модулей нет. `settings.gradle.kts` и `libs.versions.toml` не меняются.

---

## 3. Ответ → результат

Текст `failureText(cause)`:
- `NoConnection` — «Нет связи с сервером»;
- `Timeout` — «Сервер не ответил»;
- `Http` с кодом от 500 — «Сервис временно недоступен»;
- остальное (400, 403, 404, 409, `Malformed`) — «Что-то пошло не так».

401 на любом вызове сессии сначала уходит слушателю. Если сервер подтверждает отказ (`expireSession`), корень открывает вход с «Сеанс завершён»; до этого экран показывает обычную ошибку своей строки ниже.

| Вызов | 200 | 400, 403, 404, 409, `Malformed` | 5xx, `Timeout`, `NoConnection` |
|---|---|---|---|
| `GET /search/people`, первая страница (`limit=20&offset=0`) | `Page` (записи без `user_id` не показаны, но учтены в смещении); «Найдено N» = `max(total, полученные)`; пусто → «Никого не нашли» | полноэкранная ошибка «Не удалось загрузить список» + «Что-то пошло не так» + «Повторить»; прежние результаты убраны | то же, причина по `failureText` |
| `GET /search/people`, следующая страница | дописывается без повторов по `user_id`; конец списка → футера нет | футер «Не удалось загрузить» + «Повторить»; список остаётся | то же |
| `GET /search/people?limit=1&offset=0` (`count`) | «Показать N» | кнопка «Показать» без числа, ошибка не показывается | то же |
| `GET /recommendations/people?limit=10` | список без зрителя, повторов и записей без id; пусто → пустое состояние с «Найти людей» | «Не удалось загрузить рекомендации» + причина + «Повторить» | то же |
| Требования роли (локально, мок проектов) | заголовок роли и текст строки поиска | `RoleNotFoundException` → полноэкранная ошибка + «Повторить» | — |

Кода 422 в спецификации этих путей нет; если придёт, он обрабатывается как 400. `CancellationException` проходит насквозь и в ошибку не превращается.

---

## 4. Владение файлами

| Каталог или файл | Владелец |
|---|---|
| `/Users/samvel/Desktop/Study/Methodology/AcademicNet/common/**` | domain-data (изменений не ждём) |
| `/Users/samvel/Desktop/Study/Methodology/AcademicNet/gateway/**`, включая `GatewaySession.kt`, `internal/ApiRequests.kt`, `mapping/UserMapping.kt` | domain-data |
| `/Users/samvel/Desktop/Study/Methodology/AcademicNet/feature/collaborators/{domain,data}/**` (код, тесты, `build.gradle.kts`) | domain-data |
| `/Users/samvel/Desktop/Study/Methodology/AcademicNet/feature/feed/{domain,data}/**` (код, тесты, `build.gradle.kts`) | domain-data |
| `/Users/samvel/Desktop/Study/Methodology/AcademicNet/mockserver/src/commonMain/kotlin/org/matchlab/academic_net/mockserver/MockPeopleSeed.kt` (только KDoc) | domain-data |
| `/Users/samvel/Desktop/Study/Methodology/AcademicNet/shared/src/commonMain/kotlin/org/matchlab/academic_net/di/{DataSourcesModule,CollaboratorsModule,FeedModule}.kt` | domain-data |
| `/Users/samvel/Desktop/Study/Methodology/AcademicNet/shared/src/commonTest/kotlin/org/matchlab/academic_net/MockServerIdsTest.kt` | domain-data (механическая правка, §5) |
| `/Users/samvel/Desktop/Study/Methodology/AcademicNet/components/**` (`paging/LoadMore.kt`; строки — без изменений) | compose-ui |
| `/Users/samvel/Desktop/Study/Methodology/AcademicNet/feature/collaborators/ui/**`, `/Users/samvel/Desktop/Study/Methodology/AcademicNet/feature/feed/ui/**` (код, `strings.xml` обеих локалей, тесты, `build.gradle.kts`) | compose-ui |
| `/Users/samvel/Desktop/Study/Methodology/AcademicNet/catalog/**` (`ComponentCatalog.kt`) | compose-ui (изменений не ждём) |
| `/Users/samvel/Desktop/Study/Methodology/AcademicNet/shared/src/commonMain/kotlin/org/matchlab/academic_net/ui/navigation/AppNavHost.kt`, `.../App.kt` | compose-ui (изменений не ждём) |
| Прочие `shared/**` (`SessionDependencies.kt`, `SessionComponent.kt`, остальные `di/*`), `feature/profile/**`, `feature/settings/**` и другие фичи | никто |
| `docs/*`, `CLAUDE.md`, `README.md` | никто: агенты кладут в отчёт блоки «Для docs/sprint-1.md» и «Строки для api-gaps.md» (готовые тексты — §9) |

---

## 5. Что удаляется

**domain-data.**
- Файл `/Users/samvel/Desktop/Study/Methodology/AcademicNet/feature/collaborators/data/src/commonMain/kotlin/org/matchlab/academic_net/feature/collaborators/data/mock/MockCollaboratorsDataSource.kt` целиком, вместе с каталогом `mock/`. В файле лежат `MockPerson`, `MockCollaborators`, `matchPercent`, `matches`, `ByRelevance`, `haystack`, `searchText`.
- Тест `/Users/samvel/Desktop/Study/Methodology/AcademicNet/feature/collaborators/data/src/commonTest/kotlin/org/matchlab/academic_net/feature/collaborators/data/MockCollaboratorsDataSourceTest.kt`.
- Файл `/Users/samvel/Desktop/Study/Methodology/AcademicNet/feature/feed/data/src/commonMain/kotlin/org/matchlab/academic_net/feature/feed/data/mock/MockFeedDataSource.kt` (вместе с `MockFeedData`) и каталог `mock/`.
- Тест `/Users/samvel/Desktop/Study/Methodology/AcademicNet/feature/feed/data/src/commonTest/kotlin/org/matchlab/academic_net/feature/feed/data/MockFeedDataSourceTest.kt`.
- Domain collaborators: `ScoredCollaborator`, `CollaboratorQuery.role`. В `.../collaborators/domain/src/commonTest/.../CollaboratorUseCasesTest.kt` удалить класс `SearchCollaboratorsUseCaseTest`, а `GetRoleRequirementUseCaseTest` и `CollaboratorFiltersTest` оставить. В `TestFixtures.kt` удалить `FakeCollaboratorsRepository`, а `collaborator()` и `FakeRoleRequirementProvider` оставить.
- Domain feed: `GetViewerUseCase`, `FeedRepository.getViewer`, `FeedDataSource.getViewer`. Тест `/Users/samvel/Desktop/Study/Methodology/AcademicNet/feature/feed/domain/src/commonTest/kotlin/org/matchlab/academic_net/feature/feed/domain/FeedUseCasesTest.kt` удалить целиком.
- `MockServerIdsTest.kt` (`:shared`): убрать импорты и строки с `MockCollaborators` и `MockFeedData` в `peopleOfOtherFeatures`; удалить тест `profilesAgreeWithTheOtherMocksOnNameAndUniversity` — он только про мок поиска людей.
- Ветка `Backend.Mock` для двух фич исчезает вместе с провайдерами моков; сам `Backend.Mock` удаляет tester (задача A).

**`:mockserver` в блоке 3 не чистится.** Проверено `grep`:
- `MockPeople`, `MockProjectCatalog`, `MockAccounts` ещё используют `MockProjectsData`, `MockProjectSearchDataSource`, `MockProjectDetailsDataSource`, `MockAuthDataSource`, их тесты и тесты `:shared`;
- люди, которые были только в моке поиска (`anna`, `olga`, `sergey`, `eva`), — авторы отзывов, из которых считаются рейтинги инициаторов в каталоге проектов.

Строки уйдут вместе с моками проектов (блоки 4–5). Правится только KDoc `MockPeopleSeed`: из перечня «the people search, the feed» убираются эти два мока. `searchTerms` и `occursIn` (`:utils`) остаются — их использует мок поиска проектов.

**compose-ui.**
- Тест `/Users/samvel/Desktop/Study/Methodology/AcademicNet/feature/collaborators/ui/src/commonTest/kotlin/org/matchlab/academic_net/feature/collaborators/ui/CollaboratorSearchViewModelTest.kt`.
- Тест `/Users/samvel/Desktop/Study/Methodology/AcademicNet/feature/feed/ui/src/commonTest/kotlin/org/matchlab/academic_net/feature/feed/ui/FeedViewModelTest.kt`.
- Тестовые зависимости `:feature:collaborators:data` и `:feature:feed:data` в `ui/build.gradle.kts` обеих фич.
- `CollaboratorItem.matchPercent`, `FeedUiState.isPeopleLoading` и `peopleFailed`, зависимость VM ленты от `GetViewerUseCase`.
- Строк только для мока нет.

Замены удалённым тестам пишет tester (§8).

---

## 6. Порядок и синхронизация

**domain-data:**
1. Первым делом — файлы «domain-ready»:
   - `/Users/samvel/Desktop/Study/Methodology/AcademicNet/feature/collaborators/domain/src/commonMain/kotlin/org/matchlab/academic_net/feature/collaborators/domain/{Collaborator.kt, CollaboratorsRepository.kt, usecase/CollaboratorUseCases.kt}`;
   - `/Users/samvel/Desktop/Study/Methodology/AcademicNet/feature/feed/domain/src/commonMain/kotlin/org/matchlab/academic_net/feature/feed/domain/{FeedRepository.kt, usecase/FeedUseCases.kt}`;
   - удаление и чистка domain-тестов из §5;
   - `:common` не меняется.
2. Скомпилировать domain (§7) и создать маркер:
   `mkdir -p /private/tmp/claude-501/-Users-samvel-Desktop-Study-Methodology-AcademicNet/62f647b7-10a2-490f-83d6-d4b77bca2823/scratchpad/sync && touch /private/tmp/claude-501/-Users-samvel-Desktop-Study-Methodology-AcademicNet/62f647b7-10a2-490f-83d6-d4b77bca2823/scratchpad/sync/b3.domain-ready`.
   **После маркера файлы domain не трогать.** Если контракта не хватает — только запись в отчёт.
3. `:gateway`: DTO, два API, Ktor-реализации, `ApiRequests`, маппинг, `GatewaySession`.
4. Data обеих фич, `build.gradle.kts`, удаление моков и их тестов.
5. `:shared/di` (три файла), `MockServerIdsTest`, KDoc `MockPeopleSeed`.
6. Компиляция Android, затем iOS (§7). Отчёт: что сделано, раздел «Удалено (моки)», «Что осталось на моках и почему» (чаты, G50; моки проектов — блоки 4–5), «Пробелы API», «Для docs/sprint-1.md», «Строки для api-gaps.md».

**compose-ui:**
1. До маркера:
   - строки обеих локалей;
   - `loadMoreStateOf` в `:components`, затем компиляция `:components` и `:catalog`;
   - код VM, экранов и `…UiDependencies` строго по §2.8–2.9;
   - удаление тестов и тестовых зависимостей из §5.

   Модули `:feature:collaborators:ui` и `:feature:feed:ui` до маркера не компилировать. Наличие маркера проверять между этапами (`test -f …/sync/b3.domain-ready`), а пока его нет — продолжать независимую работу.
2. После маркера: компиляция обоих ui-модулей, исправление своих ошибок.
3. Отчёт: что сделано, «Удалено (моки)», допущения UI, «Пробелы API».

**Основная сессия** после обоих агентов собирает `:shared` и `:androidApp:assembleDebug` и делает iOS-компиляцию `:shared`. `:shared` до этого не собирается по замыслу: DI ссылается на новые конструкторы обеих сторон. Затем tester, потом коммиты (предложение):
1. «Add the people search and recommendations to :gateway».
2. «Search people page by page»: collaborators domain, data, ui; `loadMoreStateOf`; куски DI и `MockServerIdsTest` для collaborators.
3. «Recommend people on the feed from the server»: feed целиком; куски DI и `MockServerIdsTest` для feed; KDoc `MockPeopleSeed`.
4. Документация.

Каждый из коммитов 1–3 собирается сам: после 2 лента ещё на моке.

**Риски.**
- `AppComponentTest` и прочие тесты `:shared` в режиме `Backend.Mock` падают, потому что поиску и ленте теперь нужен `GatewaySession`. Так уже с блока 2 для профиля и настроек; переводит на `GatewayFactory` + `MockEngine` tester (задача A).
- `LocalizationResourcesTest` требует ключей 1:1.
- Если `MockServerIdsTest` не поправить, не скомпилируются тесты `:shared`.
- Платформенного кода нет; iOS проверяется только компиляцией.
- Как сервер понимает `q` и `skill`, неизвестно (G31): поиск по строке «Computer Vision, Python» может вернуть пусто, пользователь правит строку.

---

## 7. Команды проверки (только компиляция, без тестов, без `:shared`, без `assembleDebug`)

Gradle — только через `/private/tmp/claude-501/-Users-samvel-Desktop-Study-Methodology-AcademicNet/62f647b7-10a2-490f-83d6-d4b77bca2823/scratchpad/tools/gw.sh` (он сериализует запуски, ставит `JAVA_HOME` и рабочий каталог). Имена задач сверены по прошлым логам сборки: `compileAndroidMain` и `compileKotlinIosSimulatorArm64`.

**domain-data:**
```bash
# 1. domain, then the marker
/private/tmp/claude-501/-Users-samvel-Desktop-Study-Methodology-AcademicNet/62f647b7-10a2-490f-83d6-d4b77bca2823/scratchpad/tools/gw.sh :common:compileAndroidMain :feature:collaborators:domain:compileAndroidMain :feature:feed:domain:compileAndroidMain --console=plain
# 2. gateway and data
/private/tmp/claude-501/-Users-samvel-Desktop-Study-Methodology-AcademicNet/62f647b7-10a2-490f-83d6-d4b77bca2823/scratchpad/tools/gw.sh :gateway:compileAndroidMain :feature:collaborators:data:compileAndroidMain :feature:feed:data:compileAndroidMain :mockserver:compileAndroidMain --console=plain
# 3. iOS, at the end
KONAN_DATA_DIR=/private/tmp/claude-501/-Users-samvel-Desktop-Study-Methodology-AcademicNet/62f647b7-10a2-490f-83d6-d4b77bca2823/scratchpad/konan-data /private/tmp/claude-501/-Users-samvel-Desktop-Study-Methodology-AcademicNet/62f647b7-10a2-490f-83d6-d4b77bca2823/scratchpad/tools/gw.sh :gateway:compileKotlinIosSimulatorArm64 :feature:collaborators:data:compileKotlinIosSimulatorArm64 :feature:feed:data:compileKotlinIosSimulatorArm64 --console=plain
```

**compose-ui:**
```bash
# any time
/private/tmp/claude-501/-Users-samvel-Desktop-Study-Methodology-AcademicNet/62f647b7-10a2-490f-83d6-d4b77bca2823/scratchpad/tools/gw.sh :components:compileAndroidMain :catalog:compileAndroidMain --console=plain
# only after scratchpad/sync/b3.domain-ready exists
/private/tmp/claude-501/-Users-samvel-Desktop-Study-Methodology-AcademicNet/62f647b7-10a2-490f-83d6-d4b77bca2823/scratchpad/tools/gw.sh :feature:collaborators:ui:compileAndroidMain :feature:feed:ui:compileAndroidMain --console=plain
# optional, at the end
KONAN_DATA_DIR=/private/tmp/claude-501/-Users-samvel-Desktop-Study-Methodology-AcademicNet/62f647b7-10a2-490f-83d6-d4b77bca2823/scratchpad/konan-data /private/tmp/claude-501/-Users-samvel-Desktop-Study-Methodology-AcademicNet/62f647b7-10a2-490f-83d6-d4b77bca2823/scratchpad/tools/gw.sh :components:compileKotlinIosSimulatorArm64 :feature:collaborators:ui:compileKotlinIosSimulatorArm64 :feature:feed:ui:compileKotlinIosSimulatorArm64 --console=plain
```

Наборы `commonTest` агенты не компилируют.

---

## 8. Что должен покрыть tester (критичное; пойдёт в раздел «Блок 3» файла `_tester-lists.md`)

**`:common/paging`** — тестов пока нет; файлы `PageTest`, `PagedListTest`:
- границы `PageRequest`: `offset` 0..10 000, `limit` 1..100, вне границ — `IllegalArgumentException`;
- `hasMore`: пустая страница при `total > 0` → `false`; `nextOffset == total` → `false`; `received > items.size` сдвигает `nextOffset`; `nextOffset > MaxOffset` → `false`;
- `next()` сохраняет `limit`;
- `PagedList`: повторы внутри страницы и между страницами отбрасываются, порядок сохраняется; `total >= items.size`; в конце `next == null`; страница из одних повторов всё равно продвигает `next`.

**`:gateway`** (`MockEngine`, `GatewayFixture`):
- `peopleSearch.search`: GET `/api/v1/search/people` с Bearer.
- Параметры проверять по разобранным `url.parameters`:
  - `limit` и `offset` уходят всегда;
  - `q` и `university` — только непустые;
  - `skill` и `interest` повторяются (`getAll`), пустые элементы отброшены;
  - булевы уходят только как `true`;
  - `project_type` не появляется никогда.
- Разбор `Person`: все поля; отсутствующие поля → значения по умолчанию; `null` вместо массивов; `total` строкой и числом; без `total` → 0.
- Ответ не 2xx → `RemoteFailure.Http(status)`; 401 вызывает слушатель ровно один раз.
- `recommendations.people(10)`: GET `/api/v1/recommendations/people?limit=10` с Bearer; `limit <= 0` → исключение без запроса.
- Существующие вызовы `ApiRequests` не сломаны (их тесты проходят).
- `PersonDto.toCollaborator()`: пустой id → `null`; имя, вуз и направление обрезаны; пустой уровень → `null`; курс `<= 0` → `null`; `verified` переносится.
- `toCollaboratorPage` и `pageOf`: нечитаемые записи учтены в `received`; `total = max(reported, offset + received)`; отрицательный `total`; `total` больше `Int.MAX_VALUE` обрезается.
- `UserProfileDto.toCollaborator()` после выноса `studyOf` ведёт себя как раньше.

**collaborators domain:**
- вернуть `SearchCollaboratorsUseCaseTest`: запрос и страница передаются как есть, успех, сбой → `Result.failure`, отмена проходит насквозь;
- то же для `CountCollaboratorsUseCase`.

**collaborators data:**
- `GatewayCollaboratorsDataSource` на fake `PeopleSearchApi`: каждая строка таблицы `toParams()`; `workTypes` не влияет на параметры; `capabilities.projectTypeFilter == false`;
- страница передаётся как есть; `count` спрашивает `PageRequest(0, 1)` и возвращает `total`;
- сбои пробрасываются;
- `CollaboratorsRepositoryImpl` делегирует все три члена.

**feed data и domain:**
- `GatewayFeedDataSource` на fake `RecommendationsApi`: `limit` 10 или заданный; зритель, записи без id и повторы отброшены; порядок сохранён; сбой пробрасывается;
- `FeedRepositoryImpl` делегирует;
- `GetRecommendedPeopleUseCase`: успех, сбой, отмена.

**`:components`:** таблица истинности `loadMoreStateOf` — ошибка важнее загрузки, загрузка важнее `hasMore`, иначе `End`.

**collaborators ui** — `CollaboratorSearchViewModelTest` на fake `CollaboratorsRepository` с «воротами», `pageSize` маленький, коллекторы в `backgroundScope`:
- без роли — один запрос `PageRequest(0, pageSize)`; заполнены `results`, `total`, `hasMore`; `resultsVersion == 1`;
- с ролью — `roleTitle` и `query` из навыков, поиск идёт с этим текстом; сбой роли → `error`, `onRetry` грузит требования заново;
- три правки текста за 300 мс → один запрос с последним текстом, до 300 мс запросов нет; `onSearchSubmit` ищет сразу;
- `onLoadMore` дописывает следующую страницу без повторов; второй вызов во время загрузки запроса не даёт; при `hasMore == false`, во время отложенного поиска и после сбоя — ничего;
- сбой догрузки → `loadMoreFailure`; `onRetryLoadMore` повторяет ту же страницу; успех сбрасывает сбой;
- новый текст во время догрузки отменяет её; ответ старого поколения состояние не трогает (ворота: запрос A, потом B, A отпущен последним);
- сбой первой страницы → `error`, `results` пусты; `onRetry` даёт загрузку и результаты; двойной `onRetry` — один запрос; `onRetry` без ошибки ничего не делает;
- `resultsVersion` растёт только от новой выдачи, догрузка его не меняет;
- шит фильтров:
  - открытие при актуальной выдаче → `previewCount == total` без запроса;
  - правка черновика → `null`, через 300 мс один `count(CollaboratorQuery(query, draft))`;
  - число для устаревшего черновика не применяется; сбой счёта → `null`;
  - «Закрыть» отменяет счёт;
  - «Применить» → фильтры применены, шит закрыт, сразу новая выдача.

**feed ui** — `FeedViewModelTest` на fake `FeedRepository` с воротами:
- при старте запросов нет; `viewerAvatarKey == viewerId.value`;
- первый выбор «Люди» → `Loading` → `Loaded`; «Проекты → Люди» не спрашивает снова;
- пустой ответ → `Loaded([])`;
- сбой → `Failed(cause)`; «Повторить» спрашивает снова, двойное «Повторить» — один запрос; в `NotLoaded`, `Loading`, `Loaded` «Повторить» ничего не делает.

**`:shared`:**
- граф в режиме Gateway (`GatewayFactory` + `MockEngine`): `dependencies.collaborators` и `.feed` собираются без запросов; `collaborators.capabilities.projectTypeFilter == false`; `feed.viewerId == session.userId`;
- `MockServerIdsTest` и `LocalizationResourcesTest` зелёные; тесты компилируются для iOS.

**Миграция тестов с моков.** Удалённые `MockCollaboratorsDataSourceTest`, `MockFeedDataSourceTest`, `FeedUseCasesTest`, `SearchCollaboratorsUseCaseTest`, `CollaboratorSearchViewModelTest`, `FeedViewModelTest` заменяются тестами выше. Fake'и лежат только в `commonTest` своих модулей.

---

## 9. Допущения, открытые вопросы, пробелы API

### 9.1 Пробелы API блока

| ID | Что отключено или скрыто | Что нужно от бэкенда |
|---|---|---|
| G11 | `project_type` не отправляется; группа «Тип работы» в 08b скрыта (`projectTypeFilter = false`) | Перечень значений типа проекта |
| G30 | `rating` из `Person` не читается; в строках 08 и 01c рейтинга нет и по макету | `review_count` в `Person` |
| G31 | Роль на сервер не уходит; строка поиска заполняется навыками роли; процента совпадения нет | Параметр роли или матчинг; описание `q` (и `skill`: «и» или «или», точное совпадение) |
| G32 | Рекомендации случайные, заголовок «Люди для ваших проектов» оставлен из макета | Подбор под проекты, если нужен |
| G33 (новый) | Отправляем `limit=10`; зритель, повторы и записи без id отбрасываются на клиенте, поэтому людей может прийти меньше 10 | Границы и умолчание `limit`; исключать ли зрителя |
| G34 (новый) | Порядок сервера; повторы между страницами отбрасываются; при сдвиге списка запись может пропасть; «Найдено N» = `max(total, offset + received)` | Описание порядка или курсор, точность `total`, обязательные поля `Person` |

### 9.2 Строки для `docs/api-gaps.md`

Строку G30 заменить, G33 и G34 добавить после G32:

```markdown
| **G30** | В `Person` (поиск/рекомендации) нет `review_count` | Строки людей 08 и 01c | `rating` из `Person` не читается: без `review_count` ноль не отличить от «нет отзывов»; в строках «List item/Person» рейтинга нет и по макету | Поле `review_count` |
| **G33** | `GET /recommendations/people`: у `limit` нет границ и значения по умолчанию; не сказано, может ли среди людей быть сам пользователь и бывают ли повторы | Лента 01c | Отправляем `limit=10`; записи без `user_id`, самого пользователя и повторы по `user_id` отбрасываем на клиенте (людей может прийти меньше 10) | Границы и умолчание `limit`; исключение зрителя на сервере |
| **G34** | `GET /search/people`: не описаны порядок выдачи, устойчивость `offset` между страницами и точность `total`; поля `Person` не обязательны | Поиск людей 08: догрузка, «Найдено N», «Показать N» | Порядок сервера; повтор между страницами отбрасывается по `user_id`; запись может пропасть, если список сдвинулся; «Найдено N» = `max(total, offset + received)`; запись без `user_id` не показывается, но учитывается в смещении | Описание порядка (или курсор), точность `total`, обязательные поля `Person` |
```

### 9.3 Для `docs/sprint-1.md` (раздел «Пробелы дизайна и допущения», блоки «Собрать команду» и «Лента»)

**Заменить устаревшее:**
- «рекомендации людей для проектов пользователя» (раздел «Модули»);
- «Рекомендации людей приходят от сервера (мок: люди из макета 01c)»;
- «Процент совпадения… остаётся в модели»;
- «Поиск целиком на стороне бэкенда… `MockCollaboratorsDataSource` играет сервер» вместе с контрактом мока;
- «Люди в моке (10 человек…)»;
- «(в моке 300 мс)» в пункте о ленивых рекомендациях;
- строку таблицы режимов «Остальные фичи — моки»: поиск людей и лента теперь только сервер.

**Добавить, блок «Собрать команду»:**
- Поиск идёт страницами по 20, следующая — при прокрутке. Футер «Загрузка…» / «Не удалось загрузить» + «Повторить» сделан как на 09b: на 08 его в макете нет.
- «Найдено N» и «Показать N» — число сервера (`total`). «Показать N» считается запросом `limit=1` через 300 мс после правки фильтров; при открытии шита число берётся из текущей выдачи; пока идёт счёт или если он не удался — «Показать».
- Поиск на роль — это строка поиска с навыками роли (`q` как введено). Роль на сервер не уходит (G31), процента совпадения и порядка по роли нет.
- «Ключевые слова» уходят навыками (`skill`), вуз — `university`. Группа «Тип работы» скрыта (G11), хотя есть в макете. «Мой вуз, Любой» из макета остаётся полем «Вуз».
- Ошибка первой страницы: «Не удалось загрузить список», причина, «Повторить». Неудачная новая выдача убирает прежние результаты.
- Новая выдача показывается с начала списка.
- Сам пользователь может оказаться в выдаче (это решает сервер). Записи без `user_id` не показываются.
- Рейтинг в строках не показывается: в макете его нет, у `Person` нет `review_count` (G30).

**Добавить, блок «Лента»:**
- Рекомендации — случайные люди, открытые к сотрудничеству (G32), не больше 10, без самого пользователя. Заголовок «Люди для ваших проектов» оставлен из макета.
- Пустое состояние (макета нет): «Пока нет рекомендаций» / «Здесь появятся люди, открытые к сотрудничеству» + Outlined «Найти людей» (открывает 08).
- Ошибка: «Не удалось загрузить рекомендации», причина, «Повторить».
- Аватар в шапке строится по id пользователя сессии, без запроса.

**Английские тексты** (наш перевод): "Couldn't load the list", "People open to collaboration will appear here", "Find people".

Визуальная проверка на устройстве не выполнялась по запрету пользователя.

### 9.4 Открытые решения (рекомендация и одна альтернатива)

1. **Зритель в шапке ленты.**
   - Принято: `FeedUiDependencies.viewerId`, `getViewer` удалён.
   - Альтернатива: оставить `getViewer()`, отдающий `UserSummary(viewerId, "")` без запроса. Меньше правок, но объект с выдуманным пустым именем.
   - Когда появятся аватары (G18), добавится отдельный use case аватара.
2. **«Мой вуз»** (`my_university` определён).
   - Рекомендация: отдельным шагом добавить в 08b чип «Мой вуз» → `myUniversity = true` (поле в `PeopleSearchParams` уже есть).
   - Альтернатива: оставить текстовое поле, как сейчас. В этом блоке не делается — объём сокращён.
3. **«Показать N» при открытии шита из `total`.**
   - Принято.
   - Альтернатива: всегда запрос через 300 мс — проще правило, но лишний запрос и число с задержкой.
4. **Общий помощник страниц.**
   - Протокол 08 (поколения, отмена, страж) повторится в 02 (блок 5). Рекомендация: при переписывании 02 вынести класс-помощник страничного поиска и тогда же перевести 09b на `loadMoreStateOf`.
   - Альтернатива: дублировать протокол в двух ViewModel.
5. **Для `CLAUDE.md`** (правит основная сессия):
   - «ViewModel-тесты против `Mock…DataSource`» — для фич без мока тесты идут на fake-репозиториях в `commonTest`;
   - в правиле «Поиск — задача бэкенда» убрать «роль»: роль не отправляется (G31).
