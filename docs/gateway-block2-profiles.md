# Блок 2 «Оценивать других» + «Представить себя в профиле»: проект подключения к Gateway

Статус на 2026-10-18: это проект `architect`, ничего не реализовано. Документ рассчитан на то, что шаг 1 (Часть 3 `docs/gateway-integration.md`) уже сделан: есть `:gateway`, `GatewaySession`, `RemoteFailure`, `SessionComponent` с `SessionResources`, `Backend`, `DataSourcesModule`, `failureText`, `FlexibleLongSerializer`, `@UnverifiedContract`, а также защита от повторных нажатий (Баг 1), иконка чата в шапке 09 и Card/Person 64 (шаги 2 и 4 плана). Предлагаю сохранить документ как «Часть 4» в `docs/gateway-integration.md`.

## 0. Что нового по сравнению с docs/gateway-integration.md

Расхождения с зафиксированным раньше, с причинами:

1. **Результаты проверки хранятся между запусками, а не «в памяти сессии».** Вводится журнал признанных статусов `ModerationLedger`; в режиме Gateway он лежит в `SecureStore`. Модерация ручная и занимает часы, процесс за это время успевает умереть. Если хранить только в памяти, решение, принятое при закрытом приложении, пользователь не увидит никогда, и стори «уведомление о проверке» фактически не работает. Альтернатива: `InMemoryModerationLedger` в обоих режимах. Это одна строка в DI, алгоритм тот же.
2. **`PUT /users/me/profile` вызывается только через `ProfilesApi.updateMyProfile { }`.** Сырого PUT в публичном API нет. Вызов идёт под `Mutex` сессии, тело кладётся поверх сырого JSON из `GET`, поэтому поля, которых нет в DTO, уходят обратно без изменений. Так же устроен `AchievementsApi.updateMine(id, expectedRevision) { }`: чтение-изменение-запись достижения живёт в `:gateway`, а не в data фичи. Если ревизия устарела, вызов падает с `Http(409)` и ничего не отправляет.
3. **Профиль по id читается только через `UserDirectory`.** У `ProfilesApi` нет метода `profile(id)`. Каталогом пользуются экран 09 и блоки 3–5. Его конструктор `UserDirectory(fetch, scope, …)` публичный, поэтому data фич тестирует его на фейке без Ktor. Ответы 400/403/404 означают `null` (раньше было только 400/404). Ответ «такого нет» кэшируется на TTL, сбой не кэшируется.
4. **`ProfileDataSource` становится «серверным»**: только ресурсы и перевод кодов в исходы, как `AuthDataSource` из шага 1. Клиентская логика переезжает в `ProfileRepositoryImpl`: сборка профиля из трёх ответов, кэш отзывов, результаты проверки, обновление состояния после правок. Мок играет сервер на уровне ресурсов (ревизии, 409, 404, модерация по таймеру). В итоге режим Mock гоняет тот же код репозитория, что и Gateway.
5. **Непроверенные значения не записываются.** В режиме Gateway в 10b скрыто поле «Уровень обучения», а чипы «Научные / Практические проекты» доступны только для чтения, пока бэкенд не подтвердит строки (`EditableProfileFields`). Сохранение профиля не стирает значения сервера, которые клиент не распознал.
6. **Где живут enum-ы словаря.** Перечисления транспорта (`AchievementKind`, `AchievementStatus`, `ReviewAuthorRole`) лежат в `gateway.contract`: `:gateway` не знает доменов фич. `StudyLevel` и `ProjectType` — enum-ы `:common`. Значения `kind` подтверждены спецификацией (это enum параметра запроса) и записываются; остальные словари только читаются.
7. **В спецификации нет примеров тел профиля, достижений и отзывов.** Фикстуры MockEngine-тестов пишутся по схемам и помечаются как «по схеме, не по примеру».
8. **Повторная отправка после правки.** Если запись уже отправлялась (статус не «не подтверждено»), правка сохраняется и сразу снова уходит на проверку: это обещает текст макета «После сохранения … снова пройдёт проверку». Исход «сохранено, но не отправлено» действует и для правки, и для добавления.
9. **Форма правки берёт свежую запись** через `GET /users/me/achievements/{id}`. При 409 форма сама перезагружает запись (несохранённый ввод теряется), поэтому текст кадра 11g поправлен.
10. **Тексты ошибок профиля и настроек** берутся из `failureText(cause)` шага 1, поэтому состояния `Error` несут `cause`.
11. **Чего не добавляем.** `SettingsApi` (`/users/me/settings`) не нужен ни одному экрану, хотя план говорил «оставить в `:gateway`». Эндпоинт `/users/me/reviews` не используется: отзывы о себе грузятся тем же `userReviews(viewerId)`.
12. **`Collaborator.studyInfo: String` заменяется на `study: Study`.** Свойство `Collaborator.subtitle` уходит из `:common` в `:components` (`subtitleText()`), потому что уровень и курс теперь переводятся. Это минимально затрагивает collaborators и feed; блок 3 строится уже на `Study`.

## 1. Что переиспользуем

- **Результаты и утилиты.** `LoadResult`, `SubmitResult` — `common/.../result/`. `suspendRunCatching`, `isWebLink`, `normalizeTags`, `withTag`/`withoutTag` — `:utils`.
- **Навигация.** `navigateOnce`, `navigateSingleTop`, `popBackStackOnce`, `ExclusiveLauncher` и `openChatWith` — `shared/.../ui/navigation/`.
- **Нажатия.** `onceClickable`, `rememberOnceClick`, `blockPointerInput` — `components/.../interaction/`.
- **Компоненты** (`:components`): `AppSelectField`, `RadioListItem`, `AppBottomSheet`, `SheetHeader`, `ToggleListItem`, `AppConfirmDialog`, `InfoBanner`, `InfoCard`, `PersonCard`, `ChatIconButton`, `AppTextField`, `AppTagsField`, `AppFilterChip`, `LoadingState`, `MessageState`, `AppSnackbarHost`. Форматирование: `yearMonthText`, `chatListTimeText`, `ratingText`/`formatRating`.
- **Части форм профиля.** `FormBottomBar`, `FormErrorText`, `ProofUploadCard`, `ProofLinkSheet` — `feature/profile/ui/.../my/FormParts.kt`. `MyProfileVisibility` и `profileTabHasBadge` остаются.
- **Домен профиля.** `VerificationSubject` (работа или документ) становится ссылкой на запись для отправки, удаления и результатов. `VerificationStatus`, `VerificationResult` и use case'ы результатов не меняются.
- **Моки.** `MockModerator` и таблицы `MockPeople` (`:mockserver`).
- **Тема.** `MaterialTheme.extendedColors.warningContainer` для статуса «на проверке».

## 2. `:gateway`: профили, достижения, отзывы

### 2.1 Публичный API

```kotlin
package org.matchlab.academic_net.gateway

class GatewaySession internal constructor(/* client, token, listener */) : AutoCloseable {
    val profiles: ProfilesApi
    val achievements: AchievementsApi
    val reviews: ReviewsApi
    /** Public profiles by user id for every feature of the session. */
    val users: UserDirectory
    /** Cancels the requests and the scope of [users]; the shared engine stays open. */
    override fun close()
}

/** The viewer's own profile. Every call throws RemoteFailure on failure; CancellationException passes through. */
interface ProfilesApi {
    /** GET /api/v1/users/me/profile → 200; Http(404) while the account has no profile. */
    suspend fun myProfile(): UserProfileDto

    /**
     * The only write of PUT /api/v1/users/me/profile, which replaces every field. One call at a time per
     * session (Mutex): GET (404 → empty details), [change] of the typed details, PUT of the result laid over
     * the raw `details` JSON of the GET (fields this DTO does not know go back as they came). Nothing is sent
     * when the GET fails, when [change] throws, or when the result has a blank display_name
     * (IllegalArgumentException). After 200 the viewer's entry of [UserDirectory] is dropped.
     */
    suspend fun updateMyProfile(change: (UserProfileInputDto) -> UserProfileInputDto): UserProfileDto
}

interface AchievementsApi {
    /** GET /api/v1/users/{user_id}/achievements?limit&offset; limit 1..100, offset 0..10000; no total. */
    suspend fun userAchievements(userId: String, limit: Int = MaxLimit, offset: Int = 0): UserAchievementListDto
    /** GET /api/v1/users/me/achievements?limit&offset: the owner's view (every status, moderation_comment). */
    suspend fun myAchievements(limit: Int = MaxLimit, offset: Int = 0): UserAchievementListDto
    /** GET /api/v1/users/me/achievements/{id}. */
    suspend fun myAchievement(id: String): UserAchievementDto
    /** POST /api/v1/users/me/achievements → 201: an unconfirmed achievement. */
    suspend fun create(input: UserAchievementInputDto): UserAchievementDto
    /**
     * The only write of PUT /api/v1/users/me/achievements/{id} (replaces the details, resets the confirmation):
     * GET {id}; revision ≠ [expectedRevision] → Http(409) without sending; [change]; PUT with expected_revision,
     * laid over the raw `details` JSON of the GET (evidence_file_id and unknown fields are kept).
     */
    suspend fun updateMine(
        id: String,
        expectedRevision: Int,
        change: (UserAchievementInputDto) -> UserAchievementInputDto,
    ): UserAchievementDto
    /** POST /api/v1/users/me/achievements/{id}/submit → 200, no body. */
    suspend fun submit(id: String): UserAchievementDto
    /** DELETE /api/v1/users/me/achievements/{id} → 204 (the server keeps the history). */
    suspend fun delete(id: String)

    companion object { const val MaxLimit = 100 }
}

interface ReviewsApi {
    /** GET /api/v1/users/{user_id}/reviews?limit&offset; limit 1..100, offset 0..10000; no total. */
    suspend fun userReviews(userId: String, limit: Int, offset: Int): UserReviewListDto
}

/**
 * Public profiles by user id, shared by the features of one session (profile, feed header, project page).
 * One request per id at a time (concurrent callers wait for the same answer), at most [maxParallel] in
 * flight, answers kept for [ttl]. 400, 403 and 404 mean "no such user" (`null`, kept for [ttl]); other
 * failures are thrown and not kept.
 */
class UserDirectory(
    /** GET /api/v1/users/{user_id}/profile; throws RemoteFailure. In the app: the session's client. */
    private val fetch: suspend (userId: String) -> UserProfileDto,
    /** Runs the shared requests: one caller's cancellation does not cancel the others' wait. */
    private val scope: CoroutineScope,
    private val timeSource: TimeSource = TimeSource.Monotonic,
    private val ttl: Duration = 5.minutes,
    maxParallel: Int = 4,
) {
    /** A blank id is `null` without a request. */
    suspend fun profile(userId: String): UserProfileDto?
    /** Each distinct id once; keys are the given ids. Throws the first failure; answers received stay cached. */
    suspend fun profiles(userIds: Collection<String>): Map<String, UserProfileDto?>
    /** Forgets [userId]; an answer that is in flight while it is forgotten is not cached. */
    suspend fun invalidate(userId: String)
}
```

Реализация `UserDirectory`:

- состояние — карты `cache` и `inFlight` под `Mutex`, `Semaphore(maxParallel)`, версия на каждый id. Ответ кладётся в кэш, только если версия за время запроса не изменилась;
- кэш без ограничения размера: сессия видит сотни профилей, не больше.

### 2.2 DTO

Политика полей:

- необязательные скаляры входного DTO — nullable со значением `null` по умолчанию: отсутствующее поле не отправляется;
- списки по умолчанию пустые;
- поля, которые есть только в ответе, — не null, с нулевыми значениями по умолчанию: protojson опускает нули;
- явный `null` от сервера превращается в значение по умолчанию (`coerceInputValues`).

```kotlin
package org.matchlab.academic_net.gateway.dto

/** UserProfileInput: the body of PUT and the `details` of UserProfile; display_name is required. */
@Serializable
data class UserProfileInputDto(
    @SerialName("display_name") val displayName: String = "",
    @SerialName("avatar_file_id") val avatarFileId: String? = null,
    val university: String? = null,
    @SerialName("study_level") val studyLevel: String? = null,
    @SerialName("study_year") val studyYear: Int? = null,
    val specialization: String? = null,
    val about: String? = null,
    @SerialName("open_to_collaboration") val openToCollaboration: Boolean? = null,
    @SerialName("project_types") val projectTypes: List<String> = emptyList(),
    val interests: List<String> = emptyList(),
    val skills: List<String> = emptyList(),
    @SerialName("contact_email") val contactEmail: String? = null,
    val telegram: String? = null,
)

@Serializable
data class UserProfileDto(
    @SerialName("user_id") val userId: String = "",
    val details: UserProfileInputDto = UserProfileInputDto(),
    val verified: Boolean = false,
    val rating: Double = 0.0,
    @SerialName("review_count") val reviewCount: Int = 0,
    @SerialName("completed_project_count") val completedProjectCount: Int = 0,
    @SerialName("positive_review_count") val positiveReviewCount: Int = 0,
)

/** UserAchievementInput: the body of POST and PUT, the `details` of UserAchievement; kind and title are required. */
@Serializable
data class UserAchievementInputDto(
    val id: String? = null,
    val kind: String = "",
    val title: String = "",
    val description: String? = null,
    val institution: String? = null,
    @SerialName("graduation_year") val graduationYear: Int? = null,
    val url: String? = null,
    @SerialName("evidence_file_id") val evidenceFileId: String? = null,
    @SerialName("expected_revision") val expectedRevision: Int? = null,
)

@Serializable
data class UserAchievementDto(
    val id: String = "",
    @SerialName("user_id") val userId: String = "",
    val details: UserAchievementInputDto = UserAchievementInputDto(),
    val status: String = "",
    val revision: Int = 0,
    @SerialName("moderation_comment") val moderationComment: String? = null,
    @SerialName("created_at_unix") @Serializable(with = FlexibleLongSerializer::class) val createdAtUnix: Long = 0,
)

@Serializable data class UserAchievementListDto(val items: List<UserAchievementDto> = emptyList())

@Serializable
data class UserReviewDto(
    val id: String = "",
    @SerialName("author_user_id") val authorUserId: String = "",
    @SerialName("author_display_name") val authorDisplayName: String = "",
    @SerialName("author_role") val authorRole: String? = null,
    @SerialName("project_id") val projectId: String = "",
    @SerialName("project_title") val projectTitle: String = "",
    @SerialName("target_user_id") val targetUserId: String = "",
    val title: String = "",
    val text: String = "",
    val rating: Int = 0,
    @SerialName("created_at_unix") @Serializable(with = FlexibleLongSerializer::class) val createdAtUnix: Long = 0,
    @SerialName("project_completed_at_unix") @Serializable(with = FlexibleLongSerializer::class)
    val projectCompletedAtUnix: Long = 0,
)

@Serializable data class UserReviewListDto(val items: List<UserReviewDto> = emptyList())
```

### 2.3 Контракт `UserProfileDto` для блоков 3–5

Профиль владельца страницы проекта, шапку ленты и участников берут через `session.users.profile(id)` или `profiles(ids)`; свой профиль — через `profiles.myProfile()`.

| Поле | Смысл | Если поля нет | В приложении |
|---|---|---|---|
| `user_id` | id пользователя | `""` | `UserId` |
| `details.display_name` | имя, обязательно в PUT | `""` | пустое: «имя не указано» |
| `details.avatar_file_id` | файл Media | `null` | не используется, `AppAvatars.forKey(id)` |
| `details.university`, `specialization`, `about` | свободный текст | `null` | `""` |
| `details.study_level` | строка словаря, не подтверждена | `null` | `Study.level` (неизвестное → `null`) |
| `details.study_year` | int32 | `null` или 0 | `Study.year` (≤ 0 → `null`) |
| `details.open_to_collaboration` | «Открыт к сотрудничеству» | `null` | `false` |
| `details.project_types` | строки словаря, не подтверждены | `[]` | `Set<ProjectType>` (неизвестное отбрасывается) |
| `details.interests`, `skills` | теги | `[]` | как есть |
| `details.contact_email`, `telegram` | контакты (не показываются) | `null` | только сохраняются при записи |
| `verified` | верификация человека сервером | `false` | `UserSummary.isVerified` |
| `rating` | средняя оценка | 0 | `ratingOrNull()`: `null` при `review_count == 0` |
| `review_count`, `positive_review_count`, `completed_project_count` | счётчики | 0 | как есть |

### 2.4 Словарь (`gateway.contract`)

```kotlin
/** One string field of the API and its values. Reading is lenient; writing needs [isConfirmed]. */
class Vocabulary<T : Any> internal constructor(
    /** For diagnostics: "UserAchievement.status". */
    val field: String,
    canonical: Map<T, String>,
    aliases: Map<String, T>,
    /** The backend confirmed the values: they may be sent. */
    val isConfirmed: Boolean,
) {
    /** Trimmed, case-insensitive, '-' and ' ' read as '_'; blank → null; unknown → null and [GatewayVocabulary.onUnknownValue]. */
    fun read(raw: String?): T?
    /** The value the server expects; IllegalStateException while not [isConfirmed]. */
    fun write(value: T): String
}

object GatewayVocabulary {
    /** UserAchievementInput.kind; confirmed by the spec's enum of the `kind` parameter. */
    val achievementKinds: Vocabulary<AchievementKind>
    @UnverifiedContract val achievementStatuses: Vocabulary<AchievementStatus>
    @UnverifiedContract val studyLevels: Vocabulary<StudyLevel>      // :common
    @UnverifiedContract val projectTypes: Vocabulary<ProjectType>    // :common; reused by blocks 3–5
    @UnverifiedContract val reviewAuthorRoles: Vocabulary<ReviewAuthorRole>
    /** Unknown values read from the server; Gateway sets a debug log when GatewayConfig.logCalls. */
    var onUnknownValue: (field: String, value: String) -> Unit
}

enum class AchievementKind { Education, Certificate, Scientific, Project }
enum class AchievementStatus { Unverified, Pending, Verified, Rejected }
enum class ReviewAuthorRole { Initiator, Participant }
```

| Словарь | Отправляем | Ещё читаем | Подтверждён |
|---|---|---|---|
| `achievementKinds` | `education`, `certificate`, `scientific`, `project` | — | да |
| `achievementStatuses` | не пишется (`unverified`, `pending`, `verified`, `rejected`) | `unconfirmed`, `draft`, `new`, `submitted`, `in_review`, `on_review`, `under_review`, `moderation`, `approved`, `confirmed`, `accepted`, `declined`, `denied` | нет |
| `studyLevels` | `bachelor`, `specialist`, `master`, `phd` | `bachelors`, `undergraduate`, `masters`, `postgraduate`, `aspirant`, `doctoral`, `бакалавриат`, `специалитет`, `магистратура`, `аспирантура` | нет |
| `projectTypes` | `scientific`, `project` | `research`, `practical`, `applied`, `научный`, `проектный` | нет |
| `reviewAuthorRoles` | не пишется (`initiator`, `participant`) | `owner`, `lead`, `member`, `collaborator` | нет |

Неизвестное значение обрабатывается так:

- `kind` — запись пропускается;
- статус — «не подтверждено», то есть очередь пустая (как решено раньше);
- уровень обучения — не показывается;
- тип проекта — отбрасывается из набора;
- роль автора — `null`.

Таблицу нужно продублировать в `sprint-1.md`.

### 2.5 Маппинг в общие модели (`gateway.mapping`, публичный)

```kotlin
fun UserProfileDto.toUserSummary(): UserSummary                      // id, display_name, verified; avatarUrl = null
@UnverifiedContract fun UserProfileDto.toCollaborator(): Collaborator // + university, studyOf(…), specialization
fun UserProfileDto.ratingOrNull(): Double?                           // null when review_count == 0
@UnverifiedContract fun studyOf(level: String?, year: Int?): Study    // year ≤ 0 → null; unknown level → null
fun instantOfUnixSeconds(seconds: Long): Instant?                     // ≤ 0 → null
```

Файлы data, которые используют непроверенный словарь, ставят `@file:OptIn(UnverifiedContract::class)`. Когда придёт ответ бэкенда, их легко найти поиском.

### 2.6 Что `internal`

Внутри модуля остаются:

- реализации `KtorProfilesApi`, `KtorAchievementsApi`, `KtorReviewsApi`;
- сырой `GET /users/{id}/profile` (используется только как `fetch` для `UserDirectory`) и сырые `PUT`;
- слияние JSON (`layOver(raw, typed)`: из сырого объекта удаляются ключи, которые знает DTO, сверху кладётся типизированный объект; ключи берутся из `serializer().descriptor.elementNames`);
- `Mutex` профиля, конструктор `Vocabulary`, построение путей через `appendPathSegments`.

Публичны: интерфейсы API, DTO, `UserDirectory`, `GatewayVocabulary`/`Vocabulary`/enum-ы, `gateway.mapping`.

### 2.7 Ответ → результат

| Вызов | Ответ → результат |
|---|---|
| `myProfile` | 200 → DTO; иначе `RemoteFailure` (404 «профиля ещё нет» решает вызывающий) |
| `updateMyProfile` | GET 404 → пустая основа; другой сбой GET → исключение, ничего не отправлено; PUT 200 → DTO и `users.invalidate(user_id)`; сбой PUT → исключение |
| `userAchievements`, `myAchievements`, `myAchievement`, `create`, `submit`, `userReviews` | 2xx → DTO; иначе `RemoteFailure` |
| `updateMine` | сбой GET → исключение; устаревшая ревизия → `Http(409)` без PUT; PUT 200 → DTO; иначе исключение |
| `delete` | 204 → `Unit`; иначе исключение (что значит 404, решает data) |
| `UserDirectory.profile` | 200 → DTO (кэш на TTL); 400/403/404 → `null` (кэш на TTL); прочее → исключение, не кэшируется |

## 3. `:common`, `:utils`, `:mockserver`, `:components`

```kotlin
// common/model/Study.kt
/** Level of higher education a person studies at; the server's spelling is GatewayVocabulary.studyLevels. */
enum class StudyLevel { Bachelor, Specialist, Master, Postgraduate }

/** Where a person is in their studies; either part may be unknown. */
data class Study(val level: StudyLevel? = null, val year: Int? = null)

// common/model/Collaborator.kt: studyInfo: String → study: Study; the `subtitle` property is removed
data class Collaborator(val user: UserSummary, val university: String, val study: Study, val field: String)

// utils/WebLink.kt: moved from profile/data/mock/WebLinks.kt, made public
fun webLinkHost(link: String): String?
fun hostIsOf(host: String, site: String): Boolean
/** "DOI 10.1234/cv", "GitHub", "arXiv" or the host; null without an http(s) link. */
fun linkSource(link: String?): String?
```

`:mockserver`:

- `MockServerPerson.studyInfo` → `study: Study`. Сид переводится так: «N курс бакалавриата» → `Study(Bachelor, N)`; «магистрант(ка)» → `Study(Master)`; «1 курс магистратуры» → `Study(Master, 1)`; «аспирант(ка)» → `Study(Postgraduate)`. Профессор, доцент, постдок и кандидат наук → `Study()`.
- `MockServerWork`: добавляются `revision: Int = 1` и `moderationComment: String? = null`; у отклонённой записи в сиде — «Нужны доказательства».
- `MockServerDocument`: добавляются `link: String? = null`, `hasProof: Boolean = true` (у сида — файл), `revision: Int = 1`, `moderationComment: String? = null`.
- Новый `MockStudyText.kt` с функцией `fun Study.mockText(): String` («3 курс бакалавриата», «аспирантура»). Она нужна только для поиска по тексту в моке collaborators.

`:components` (новые и изменённые сигнатуры):

```kotlin
// person/StudyText.kt
@Composable fun StudyLevel.label(): String                  // "Бакалавриат" | "Bachelor's" (picker)
@Composable fun studyText(study: Study): String?            // "3 курс бакалавриата", "магистратура", "2 курс"; null if empty
@Composable fun Collaborator.subtitleText(): String         // "Stanford · 3 курс бакалавриата · Computer Science"

// RatingStars.kt (+ AppIcons.StarFilled, ic_star_filled.xml)
enum class RatingStarsSize(val star: Dp, val gap: Dp) { Small(18.dp, 2.dp), Medium(24.dp, 4.dp) }
/** "Rating": [value] of 5 filled (primary), the rest outlined; clamped to 0..5; says "Оценка N из 5". */
@Composable fun RatingStars(value: Int, modifier: Modifier = Modifier, size: RatingStarsSize = RatingStarsSize.Small)

// Blocks.kt
enum class StatusBannerStatus { Unverified, Pending, Verified, Rejected }
/** "Banner/Status": radius 12, padding 12, icon 20, title Title/Small, [text] Body/Small. */
@Composable fun StatusBanner(status: StatusBannerStatus, title: String, modifier: Modifier = Modifier, text: String? = null)
enum class InfoBannerTone { Info, Error }   // Error: errorContainer / onErrorContainer; the caller passes AppIcons.XCircle
@Composable fun InfoBanner(text: String, modifier: Modifier = Modifier, icon: DrawableResource = AppIcons.ShieldCheck, tone: InfoBannerTone = InfoBannerTone.Info)

// Buttons.kt, Dialogs.kt
enum class AppButtonVariant { Filled, Tonal, Outlined, OutlinedError, Text, TextError }
@Composable fun AppButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    variant: AppButtonVariant = AppButtonVariant.Filled, enabled: Boolean = true, loading: Boolean = false) // loading: from step 6 if already there
@Composable fun AppConfirmDialog(/* as now */, destructive: Boolean = false)   // confirm button in TextError
```

Цвета `StatusBanner`:

| Статус | Фон / текст | Иконка |
|---|---|---|
| `Unverified` | `surfaceContainer` / `onSurfaceVariant` | ShieldX |
| `Pending` | `extendedColors.warningContainer` / `onWarningContainer` | Clock |
| `Verified` | `tertiaryContainer` / `onTertiaryContainer` | ShieldCheck |
| `Rejected` | `errorContainer` / `onErrorContainer` | ShieldX |

Строки `:components` (ru и en, ключи 1:1):

- `components_rating_value` («Оценка %1$d из 5»);
- четыре уровня обучения для выбора с заглавной буквы;
- четыре уровня обучения в нижнем регистре для подписи;
- четыре строки «%1$d курс бакалавриата / специалитета / магистратуры / аспирантуры» («Bachelor's, year %1$d» и т.д.);
- `components_study_year` («%1$d курс» / «Year %1$d»).

В `:catalog` добавить: `RatingStars` sm и md, `StatusBanner` ×4, `InfoBanner` в варианте error, кнопки `TextError` и `loading`.

## 4. `feature:profile:domain`

```kotlin
// ProfileModels.kt: changed and new
data class PortfolioWork(
    val id: WorkId, val type: ProjectType, val title: String, val description: String,
    val link: String?,
    /** Where the work lives, derived on the client from [link] (linkSource). */
    val source: String?,
    val status: VerificationStatus,
    /** The moderator's reason; only the owner gets it. */
    val moderationComment: String? = null,
    /** A proof file is attached on the server (uploaded elsewhere); edits keep it. */
    val hasEvidenceFile: Boolean = false,
    /** The server's version: an edit names it, a stale one is refused (409). */
    val revision: Int = 0,
)

data class ProfileDocument(
    val id: DocumentId, val kind: DocumentKind, val title: String, val issuer: String, val year: Int?,
    /** The proof link; it is public: everyone who opens the profile can get it. */
    val proof: ProofAttachment?,
    val status: VerificationStatus,
    val moderationComment: String? = null,
    val hasEvidenceFile: Boolean = false,
    val revision: Int = 0,
)

data class Review(
    val id: ReviewId, val author: UserSummary,
    /** null when the server's value is unknown to the app. */
    val authorRole: ReviewerRole?,
    val projectTitle: String,
    /** The month the project ended; null when the server did not say. */
    val projectFinished: YearMonth?,
    val title: String, val rating: Int, val text: String,
    /** The viewer wrote it: no chat button next to the author. */
    val isByViewer: Boolean = false,
)

/** A page of reviews; [hasMore]: the page was full, there may be more (the API has no total). */
data class ReviewsPage(val items: List<Review>, val hasMore: Boolean)
/** "09b": the numbers (from the profile) and the first page. */
data class UserReviews(val summary: ReviewsSummary, val firstPage: ReviewsPage)
/** "09e". */
data class UserWorks(val items: List<PortfolioWork>, val isViewer: Boolean)

data class VerificationItem(val subject: VerificationSubject, val title: String, val status: VerificationStatus,
    val moderationComment: String? = null)

data class MyProfile(
    val profile: UserProfile,
    /** Every work of the viewer, newest first; [UserProfile.works] previews some. */
    val allWorks: List<PortfolioWork>,
    val verificationQueue: List<VerificationItem>,
) {
    /** A new account: no name yet. */
    val needsName: Boolean get() = profile.person.user.name.isBlank()
}

/** Fields of "10b" the source can change now: a field with unconfirmed server values is read-only. */
data class EditableProfileFields(val studyLevel: Boolean, val interestTypes: Boolean) {
    companion object { val All = EditableProfileFields(studyLevel = true, interestTypes = true) }
}

/** Answer to a change of an existing record. */
sealed interface EditOutcome<out T> {
    data class Done<out T>(val value: T) : EditOutcome<T>
    /** Changed since it was loaded (409 or a newer revision). */
    data object Conflict : EditOutcome<Nothing>
    /** Gone (404). */
    data object NotFound : EditOutcome<Nothing>
}

/** What adding or saving a work or a document ended with. */
sealed interface PortfolioChange<out T> {
    data class Saved<out T>(val value: T, val sentForVerification: Boolean) : PortfolioChange<T>
    /** Saved, but sending for verification failed: [value] is unconfirmed and can be sent again. */
    data class SavedNotSent<out T>(val value: T) : PortfolioChange<T>
    data object Conflict : PortfolioChange<Nothing>
    data object NotFound : PortfolioChange<Nothing>
}
```

Что ещё меняется в моделях:

- `UserProfile` не меняется, кроме `person.study`.
- `UserReviews(summary, items)` заменяется новым.
- `ProofAttachment` получает KDoc «ссылка на подтверждение, уходит в публичное `url`».
- `VerificationSubject` получает KDoc «ссылка на работу или документ зрителя: проверка, отправка, удаление».

```kotlin
// ProfileDrafts.kt
object ProfileLimits { /* as now */ const val MaxNameLength = 100; const val MaxUniversityLength = 150
    const val MaxFieldLength = 100; const val MinStudyYear = 1; const val MaxStudyYear = 6 }

enum class ProfileDraftError { NameEmpty, NameTooLong, UniversityTooLong, StudyYearInvalid, FieldTooLong,
    AboutTooLong, TooManyInterests, TooManySkills }

/** "10b" (was AboutDraft). */
data class ProfileDraft(
    val name: String = "", val university: String = "", val studyLevel: StudyLevel? = null,
    /** As typed; blank when not given. */ val studyYear: String = "",
    val field: String = "", val about: String = "",
    val interestTypes: Set<ProjectType> = emptySet(), val interests: List<String> = emptyList(),
    val skills: List<String> = emptyList(),
) { val studyYearNumber: Int?; fun normalized(): ProfileDraft; fun validate(): Set<ProfileDraftError> }

/** "11 / 11b": the proof is gone (no file upload yet); [hasEvidenceFile] comes from the record being edited. */
data class WorkDraft(val type: ProjectType = ProjectType.Scientific, val title: String = "", val description: String = "",
    val link: String = "", val hasEvidenceFile: Boolean = false) {
    val sendsForVerification: Boolean get() = link.isNotBlank() || hasEvidenceFile
}
enum class WorkDraftError { TitleEmpty, TitleTooLong, DescriptionEmpty, DescriptionTooLong, LinkInvalid }

data class DocumentDraft(/* kind, title, issuer, year, proof as now */ val hasEvidenceFile: Boolean = false) {
    val sendsForVerification: Boolean get() = proof != null || hasEvidenceFile   // ProofMissing only without both
}

fun PortfolioWork.toDraft(): WorkDraft
fun ProfileDocument.toDraft(): DocumentDraft
fun MyProfile.toDraft(): ProfileDraft
```

```kotlin
// ProfileRepository.kt
interface ProfileRepository {
    /** 09: profile, portfolio and the newest reviews (three requests at once); null: no such person. */
    suspend fun getProfile(id: UserId): UserProfile?
    suspend fun getWorks(id: UserId): UserWorks?
    /** Summary (from the profile) and the first page, at once; null: no such person. */
    suspend fun getUserReviews(id: UserId): UserReviews?
    suspend fun getReviewsPage(id: UserId, offset: Int): ReviewsPage?
    /** From the pages seen in this session, else looked up page by page (at most 5 × 100); null: not found. */
    suspend fun getReview(userId: UserId, reviewId: ReviewId): Review?
}

interface MyProfileRepository {
    /** Loads on the first collection (a failure goes to the collector), then emits after every change. */
    fun observeMyProfile(): Flow<MyProfile>
    /** Asks the server again: start, return to the app, entering the tab. Coalesced; skipped within 5 s of the last success. */
    suspend fun refreshMyProfile()
    val editableFields: EditableProfileFields
    suspend fun updateProfile(draft: ProfileDraft): MyProfile
    suspend fun createWork(draft: WorkDraft): PortfolioWork
    suspend fun createDocument(draft: DocumentDraft): ProfileDocument
    suspend fun getMyWork(id: WorkId): PortfolioWork?
    suspend fun getMyDocument(id: DocumentId): ProfileDocument?
    suspend fun updateWork(id: WorkId, draft: WorkDraft, expectedRevision: Int): EditOutcome<PortfolioWork>
    suspend fun updateDocument(id: DocumentId, draft: DocumentDraft, expectedRevision: Int): EditOutcome<ProfileDocument>
    suspend fun submitForVerification(subject: VerificationSubject): EditOutcome<VerificationStatus>
    /** 404 counts as deleted. */
    suspend fun delete(subject: VerificationSubject): EditOutcome<Unit>
    fun observeVerificationResults(): Flow<List<VerificationResult>>
    suspend fun markResultsSeen(ids: Set<VerificationResultId>)
}
```

Use case'ы:

```kotlin
GetUserProfileUseCase(r)(id): LoadResult<UserProfile>
GetUserWorksUseCase(r)(id): LoadResult<UserWorks>
GetUserReviewsUseCase(r)(id): LoadResult<UserReviews>
GetReviewsPageUseCase(r)(id, offset): LoadResult<ReviewsPage>
GetReviewUseCase(r)(userId, reviewId): LoadResult<Review>
ObserveMyProfileUseCase(r)(): Flow<MyProfile>
RefreshMyProfileUseCase(r)(): Result<Unit>
UpdateProfileUseCase(r)(draft): SubmitResult<MyProfile, ProfileDraftError>
AddWorkUseCase(r)(draft): SubmitResult<PortfolioChange<PortfolioWork>, WorkDraftError>        // Saved | SavedNotSent
AddDocumentUseCase(r)(draft): SubmitResult<PortfolioChange<ProfileDocument>, DocumentDraftError>
GetMyWorkUseCase(r)(id): LoadResult<PortfolioWork>
GetMyDocumentUseCase(r)(id): LoadResult<ProfileDocument>
SaveWorkUseCase(r)(base: PortfolioWork, draft: WorkDraft, send: Boolean): SubmitResult<PortfolioChange<PortfolioWork>, WorkDraftError>
SaveDocumentUseCase(r)(base: ProfileDocument, draft: DocumentDraft, send: Boolean): SubmitResult<PortfolioChange<ProfileDocument>, DocumentDraftError>
SubmitForVerificationUseCase(r)(subject): Result<EditOutcome<VerificationStatus>>
DeletePortfolioItemUseCase(r)(subject): Result<EditOutcome<Unit>>
// ObserveVerificationResults, ObserveHasUnseenResults, MarkResultsSeen — unchanged
```

Правила добавления и сохранения живут в домене (internal `PortfolioChanges.kt`, общий для работ и документов):

| Сценарий | Запросы | Результат |
|---|---|---|
| Add, черновик не прошёл проверку | — | `Invalid` |
| Add, сбой `create` | create | `Failure` |
| Add, `!sendsForVerification` (работа без ссылки) | create | `Saved(item, false)` |
| Add, `submit` → `Done` | create, submit | `Saved(item.copy(status), true)` |
| Add, `submit` → `Conflict`, `NotFound` или исключение | create, submit | `SavedNotSent(item)` |
| Save, без изменений, `send = false` | — | `Saved(base, false)` |
| Save, без изменений, `send = true` | submit | `Done` → `Saved(…, true)`; `Conflict`/`NotFound` → то же; исключение → `Failure` |
| Save, изменено, без отправки | PUT | `Done` → `Saved(v, false)`; `Conflict`/`NotFound` → то же; исключение → `Failure` |
| Save, изменено и (`send` или `base.status ≠ Unconfirmed`) и `draft.sendsForVerification` | PUT, submit | исход PUT как выше; если `submit` не `Done` → `SavedNotSent(v)` |

## 5. `feature:profile:data`

```kotlin
/** A person's profile without the portfolio and the reviews. */
data class ProfileRecord(val person: Collaborator, val about: String, val interests: ProfileInterests,
    val skills: List<String>, val reviews: ReviewsSummary)

/** Works (newest first) and documents in the server's statuses; the repository folds them for other viewers. */
data class Portfolio(val works: List<PortfolioWork>, val documents: List<ProfileDocument>)

/** The profile server: resources and their answers, no client logic. Failures are thrown. */
interface ProfileDataSource {
    val editableFields: EditableProfileFields
    suspend fun getProfile(id: UserId): ProfileRecord?
    suspend fun getPortfolio(id: UserId): Portfolio?
    suspend fun getReviews(id: UserId, offset: Int, limit: Int): List<Review>?
    /** null: the account has no profile yet. */
    suspend fun getMyProfile(): ProfileRecord?
    suspend fun updateMyProfile(draft: ProfileDraft): ProfileRecord
    suspend fun getMyPortfolio(): Portfolio
    suspend fun getMyWork(id: WorkId): PortfolioWork?
    suspend fun getMyDocument(id: DocumentId): ProfileDocument?
    suspend fun createWork(draft: WorkDraft): PortfolioWork
    suspend fun createDocument(draft: DocumentDraft): ProfileDocument
    suspend fun updateWork(id: WorkId, draft: WorkDraft, expectedRevision: Int): EditOutcome<PortfolioWork>
    suspend fun updateDocument(id: DocumentId, draft: DocumentDraft, expectedRevision: Int): EditOutcome<ProfileDocument>
    suspend fun submitWork(id: WorkId): EditOutcome<PortfolioWork>
    suspend fun submitDocument(id: DocumentId): EditOutcome<ProfileDocument>
    /** 404 is Done. */
    suspend fun delete(subject: VerificationSubject): EditOutcome<Unit>
}

/** What the viewer has acknowledged about the moderation of their records, kept between launches. */
interface ModerationLedger {
    /** null: nothing stored for this account yet (the first load becomes the baseline). */
    suspend fun read(): Map<String, VerificationStatus>?
    suspend fun write(statuses: Map<String, VerificationStatus>)
}
class InMemoryModerationLedger : ModerationLedger

class ProfileRepositoryImpl(
    source: ProfileDataSource, ledger: ModerationLedger, viewerId: UserId, scope: CoroutineScope,
    clock: Clock = Clock.System, timeSource: TimeSource = TimeSource.Monotonic,
    minRefreshInterval: Duration = 5.seconds, reviewsPageSize: Int = 20,
) : ProfileRepository, MyProfileRepository

class GatewayProfileDataSource(profiles: ProfilesApi, achievements: AchievementsApi, reviews: ReviewsApi,
    users: UserDirectory, timeZone: () -> TimeZone = { TimeZone.currentSystemDefault() }) : ProfileDataSource
class MockProfileDataSource(scope: CoroutineScope, people: MockPeople = MockPeople.default,
    moderator: MockModerator = MockModerator(), moderationDelay: Duration = 8.seconds,
    ids: IdGenerator = UuidIdGenerator, viewerId: UserId = UserId("user-me"), latencyMillis: Long = 300) : ProfileDataSource
```

### Внутреннее устройство репозитория

Классы `internal`, у каждого свой тест:

- **`ProfileAssembly.kt`** — чистые функции:
  - сборка `UserProfile`: превью 3 работ и 2 отзывов, `worksCount`;
  - документы: сначала образование, потом по году (новые первыми, без года в конце);
  - для чужого зрителя `Pending` и `Rejected` показываются как `Unconfirmed`, `moderationComment` обнуляется;
  - очередь проверки (`Pending` и `Rejected`);
  - `isByViewer` у отзыва.
- **`getProfile(id)`.**
  - Свой id отдаётся из `MyProfileStore`.
  - Чужой: `coroutineScope` с тремя `async` — `getProfile`, `getPortfolio`, `getReviews(0, 2)`.
  - Профиль `null` → `null`, остальные запросы отменяются. `null` у портфолио или отзывов считается пустым списком.
- **`ReviewsCache`.**
  - Хранит отзывы по пользователю: `Map<UserId, Map<ReviewId, Review>>`. Его пополняет любой ответ с отзывами, в том числе превью профиля.
  - `hasMore = items.size == limit && offset + limit ≤ 10_000`.
  - При промахе `getReview` листает страницы по 100, не больше 5, пока страница полная; потом `null`.
- **`MyProfileStore`.**
  - Хранит исходные данные (запись, портфолио, превью), а не готовый `MyProfile`, и пересобирает его после каждого изменения.
  - Обновление одно на всех (общий `Deferred` в `scope`), с паузой 5 с, если его не вызвали принудительно.
  - Ответы `create`, `update`, `submit` и `delete` встраиваются в состояние без перезапроса.
- **`ModerationTracker`.** После каждой загрузки и каждого изменения своего портфолио:
  - журнал `ack` берётся из `ledger.read()`; если журнала нет, базой становятся текущие статусы;
  - для каждой записи: если `ack == Pending`, а сейчас `Verified` или `Rejected`, — непросмотренный `VerificationResult`, где `id = "$itemId:$status:$revision"`, `decidedAt = clock.now()` (времени решения в API нет), `ack` не меняется;
  - иначе `ack = текущий статус`;
  - исчезнувшие записи удаляются из журнала, затем `ledger.write`;
  - `markResultsSeen(ids)` ставит `ack = исход` и пишет журнал; результат остаётся в памяти как просмотренный (нужно `MyProfileViewModel`).

### Gateway-источник: ответ → результат

| Вызов | Ответ → результат |
|---|---|
| `getProfile(id)` | `users.profile(id)`: DTO → запись; `null` (400/403/404) → `null` |
| `getPortfolio(id)`, `getReviews(…)` | 200 → маппинг; 400/403/404 → `null` |
| `getMyProfile()` | 200 и непустое `display_name` → запись; 404 или пустое имя → `null` |
| `updateMyProfile(draft)` | `updateMyProfile { d -> … }` → запись (поля — ниже) |
| `getMyPortfolio()` | 200 → маппинг |
| `getMyWork`, `getMyDocument` | 200 и нужный `kind` → запись; другой `kind`, 400, 404 → `null` |
| `create*` | 201 → запись |
| `update*` | `updateMine`: 200 → `Done`; 409 (в том числе устаревшая ревизия) → `Conflict`; 404 → `NotFound` |
| `submit*` | 200 → `Done`; 409 → `Conflict`; 404 → `NotFound` |
| `delete` | 204 или 404 → `Done`; 409 → `Conflict` |

Прочие коды и сетевые сбои пробрасываются как `RemoteFailure`.

Маппинг (`GatewayProfileMapping.kt`, internal):

- `kind` → работа (`Scientific` ↔ `scientific`, `Practical` ↔ `project`) или документ (`education`/`certificate`).
- `url` → `link` у работы или `proof` у документа; `source = linkSource(url)`.
- `institution` → `issuer`; `graduation_year` (≤ 0 → `null`) → `year`.
- `evidence_file_id` непустой → `hasEvidenceFile`.
- Статус берётся из словаря; неизвестный → `Unconfirmed`.
- Работы сортируются по `created_at_unix` от новых к старым.
- Отзыв: месяц `project_completed_at_unix` в часовом поясе устройства; рейтинг ограничивается 0..5.

Поля `ProfileDraft` при `updateMyProfile`:

- `display_name`, `university`, `study_year`, `specialization`, `about`, `interests` и `skills` перезаписываются; пустые строки отправляются как `null`.
- `study_level` берётся из черновика, только если словарь подтверждён и уровень изменился по сравнению с прочитанным. Иначе остаётся сырое значение сервера.
- `project_types`: если словарь подтверждён — `сырые неизвестные + write(выбранные)`, иначе сырые как есть.
- Аватар, `open_to_collaboration`, контакты и незнакомые поля сохраняются.
- `editableFields = EditableProfileFields(studyLevels.isConfirmed, projectTypes.isConfirmed)`.

### Правила мока (контракт для бэкенда)

- Чтение идёт по таблицам `MockPeople`; страницы отзывов — `drop(offset).take(limit)`; неизвестный id → `null`.
- `create` → `Unconfirmed`, `revision = 1`.
- `update`: ревизия не совпадает → `Conflict`; иначе поля заменяются, статус становится `Unconfirmed`, `moderationComment = null`, `revision + 1`.
- `submit`: только из `Unconfirmed` (иначе `Conflict`), → `Pending`, `revision + 1`. Через `moderationDelay` `MockModerator` решает и снова повышает `revision`; при отклонении ставит комментарий «Нужны доказательства».
- `delete` удаляет строку.
- `editableFields = All`.
- `seedResults` и `Clock` из мока уходят: результаты считает репозиторий.

## 6. `feature:profile:ui`: контракты для compose-ui

```kotlin
@Composable fun UserProfileRoute(dependencies: ProfileUiDependencies, userId: UserId, onNavigateBack: () -> Unit,
    onMessageClick: (UserSummary) -> Unit, onAllWorksClick: (UserId) -> Unit, onAllReviewsClick: (UserId) -> Unit,
    onReviewClick: (UserId, ReviewId) -> Unit, onAuthorClick: (UserId) -> Unit, onAuthorChatClick: (UserSummary) -> Unit,
    modifier: Modifier = Modifier)
@Composable fun MyProfileRoute(dependencies: ProfileUiDependencies, onEditClick: () -> Unit, onSettingsClick: (() -> Unit)?,
    onAllWorksClick: (UserId) -> Unit, onAllReviewsClick: (UserId) -> Unit, onReviewClick: (UserId, ReviewId) -> Unit,
    onAuthorClick: (UserId) -> Unit, onAuthorChatClick: (UserSummary) -> Unit,
    onEditWork: (WorkId) -> Unit, onEditDocument: (DocumentId) -> Unit, modifier: Modifier = Modifier)
@Composable fun UserReviewsRoute(dependencies, userId: UserId, onNavigateBack: () -> Unit,
    onReviewClick: (UserId, ReviewId) -> Unit, onAuthorClick: (UserId) -> Unit, onAuthorChatClick: (UserSummary) -> Unit, modifier)
@Composable fun ReviewDetailsRoute(dependencies, userId: UserId, reviewId: ReviewId, onNavigateBack: () -> Unit,
    onAuthorClick: (UserId) -> Unit, onAuthorChatClick: (UserSummary) -> Unit, modifier)
@Composable fun UserWorksRoute(dependencies, userId: UserId, onNavigateBack: () -> Unit, onEditWork: (WorkId) -> Unit, modifier)
@Composable fun EditProfileRoute(dependencies, onClose: () -> Unit, onAddWorkClick: () -> Unit, onAddDocumentClick: () -> Unit,
    onEditWork: (WorkId) -> Unit, onEditDocument: (DocumentId) -> Unit, modifier)
@Composable fun AddWorkRoute(dependencies, onClose: () -> Unit, modifier)       // unchanged
@Composable fun AddDocumentRoute(dependencies, onClose: () -> Unit, modifier)   // unchanged
@Composable fun EditWorkRoute(dependencies, workId: WorkId, onClose: () -> Unit, modifier)              // 11b
@Composable fun EditDocumentRoute(dependencies, documentId: DocumentId, onClose: () -> Unit, modifier)  // 11c

class ProfileUiDependencies(
    val getUserProfile: GetUserProfileUseCase, val getUserWorks: GetUserWorksUseCase,
    val getUserReviews: GetUserReviewsUseCase, val getReviewsPage: GetReviewsPageUseCase, val getReview: GetReviewUseCase,
    val observeMyProfile: ObserveMyProfileUseCase, val refreshMyProfile: RefreshMyProfileUseCase,
    val editableFields: EditableProfileFields, val updateProfile: UpdateProfileUseCase,
    val addWork: AddWorkUseCase, val addDocument: AddDocumentUseCase,
    val getMyWork: GetMyWorkUseCase, val getMyDocument: GetMyDocumentUseCase,
    val saveWork: SaveWorkUseCase, val saveDocument: SaveDocumentUseCase,
    val submitForVerification: SubmitForVerificationUseCase, val deletePortfolioItem: DeletePortfolioItemUseCase,
    val observeVerificationResults: ObserveVerificationResultsUseCase,
    val observeHasUnseenResults: ObserveHasUnseenResultsUseCase, val markResultsSeen: MarkResultsSeenUseCase,
    val clock: Clock = Clock.System, val timeZone: () -> TimeZone = { TimeZone.currentSystemDefault() },
)
```

UiModel и состояния. Во всех `Error` хранится `cause`, текст строится через `failureText(cause)`.

```kotlin
data class ProfileUiModel(val person: Collaborator, /* about, interestTypes, interests, skills, documents, works,
    hasMoreWorks, rating, reviewCount, reviews, canMessage as now */)   // subtitle → person.subtitleText()

@Immutable data class ReviewCardUiModel(val authorName: String,
    /** "Инициатор · AI-навигация по кампусу"; the project alone when the role is unknown. */ val authorLine: String,
    val authorAvatar: DrawableResource, val title: String, val rating: Int,
    /** "мар 2026", in the list of reviews only. */ val date: String?, val text: String, val showChat: Boolean)
/** Card/Review: the text is cut at 3 lines, "Читать полностью" (TextButton) only when it overflows. */
@Composable fun ReviewCard(model: ReviewCardUiModel, onClick: () -> Unit, onAuthorClick: () -> Unit,
    onChatClick: () -> Unit, modifier: Modifier = Modifier)
@Composable fun WorkCard(model: WorkCardUiModel, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, showChevron: Boolean = false)
@Composable fun EducationCard(model: EducationCardUiModel, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, showChevron: Boolean = false)

sealed interface UserReviewsUiState {
    data object Loading : UserReviewsUiState
    data object NotFound : UserReviewsUiState
    data class Error(val cause: Throwable) : UserReviewsUiState
    @Immutable data class Content(val summary: ReviewsSummary, val items: List<Review>, val hasMore: Boolean,
        val isLoadingMore: Boolean = false, val loadMoreFailure: Throwable? = null) : UserReviewsUiState
}
class UserReviewsViewModel(userId: UserId, getUserReviews: GetUserReviewsUseCase, getReviewsPage: GetReviewsPageUseCase) {
    fun onRetry(); fun onLoadMore(); fun onRetryLoadMore()
}
// onLoadMore: nothing while loading, after a failure (only onRetryLoadMore) or when !hasMore;
// items = (old + new).distinctBy { id }; NotFound on a later page → hasMore = false.

ReviewDetailsUiState { Loading; NotFound; Error(cause); Content(review: Review) }   // VM(userId, reviewId, getReview)
UserWorksUiState { Loading; NotFound; Error(cause); Content(works, isViewer) }
UserProfileUiState { Loading; NotFound; Error(cause); Content(profile) }
MyProfileUiState { Loading; Error(cause); Content(profile, needsName: Boolean, queue, results) } // onScreenShown → refresh

@Immutable data class EditProfileUiState(val isLoading: Boolean = true, val loadFailure: Throwable? = null,
    val draft: ProfileDraft = ProfileDraft(), val editable: EditableProfileFields = EditableProfileFields.All,
    val documents: List<ProfileDocument> = emptyList(), val works: List<PortfolioWork> = emptyList(),
    val errors: Set<ProfileDraftError> = emptySet(), val isSaving: Boolean = false,
    val saveFailed: Boolean = false, val isSaved: Boolean = false)
// EditProfileViewModel(observeMyProfile, updateProfile, editable): + onNameChange, onUniversityChange,
// onStudyLevelSelect(StudyLevel?), onStudyYearChange, onFieldChange; getUserWorks is gone (MyProfile.allWorks)

@Immutable data class AddWorkUiState(val draft: WorkDraft = WorkDraft(), val errors: Set<WorkDraftError> = emptySet(),
    val isSubmitting: Boolean = false, val submitFailed: Boolean = false,
    /** Saved but not sent: fields are read-only, the button only sends it. */ val savedNotSent: PortfolioWork? = null,
    val isAdded: Boolean = false)
// AddWorkViewModel(addWork, submitForVerification); onProofChange is removed. AddDocument is the same, onProofChange stays.

sealed interface PortfolioEditUiState<out T, out D, out E> {
    data object Loading : PortfolioEditUiState<Nothing, Nothing, Nothing>
    data object NotFound : PortfolioEditUiState<Nothing, Nothing, Nothing>      // "Запись удалена" + cross
    data class Error(val cause: Throwable) : PortfolioEditUiState<Nothing, Nothing, Nothing>
    @Immutable data class Content<T, D, E>(
        /** The server's version the form is based on: status, comment, revision, saved link. */ val base: T,
        val draft: D, val errors: Set<E> = emptySet(), val running: PortfolioAction? = null,
        val problem: PortfolioProblem? = null, val isDone: Boolean = false) : PortfolioEditUiState<T, D, E>
}
enum class PortfolioAction { Saving, Sending, Deleting }
enum class PortfolioProblem { SaveFailed, SendFailed, SavedNotSent, DeleteFailed, Conflict }

/** One state machine for 11b and 11c. */
class PortfolioEditViewModel<T : Any, D : Any, E : Any> internal constructor(editing: PortfolioEditing<T, D, E>) : ViewModel() {
    val uiState: StateFlow<PortfolioEditUiState<T, D, E>>
    fun onRetry(); fun onDraftChange(transform: (D) -> D); fun onSaveClick(); fun onSendClick(); fun onDeleteConfirmed()
}
internal interface PortfolioEditing<T : Any, D : Any, E : Any> {
    suspend fun load(): LoadResult<T>
    fun draftOf(item: T): D
    fun errorsOf(draft: D): Set<E>
    suspend fun save(base: T, draft: D, send: Boolean): SubmitResult<PortfolioChange<T>, E>
    suspend fun delete(): Result<EditOutcome<Unit>>
}
internal class WorkEditing(id: WorkId, d: ProfileUiDependencies) : PortfolioEditing<PortfolioWork, WorkDraft, WorkDraftError>
internal class DocumentEditing(id: DocumentId, d: ProfileUiDependencies) : PortfolioEditing<ProfileDocument, DocumentDraft, DocumentDraftError>
```

Переходы `PortfolioEditViewModel`:

| Исход | Новое состояние |
|---|---|
| `Saved` | `isDone`; `running` не сбрасывается, это защита от повторного входа |
| `SavedNotSent(v)` | `base = v`, `draft = draftOf(v)`, `problem = SavedNotSent` |
| `Conflict` | `load()`, затем `Content(base = свежая, draft = draftOf(свежая), problem = Conflict)`; если перезагрузка вернула `NotFound` или ошибку — соответствующее состояние |
| `NotFound` | `NotFound` |
| `Failure` | `SaveFailed`, `SendFailed` или `DeleteFailed` |

Пока `running != null` или `isDone`, любое действие игнорируется.

Экраны (узлы Figma — в `docs/figma-handoff.md`, фаза 3):

- **09.** В шапке `ChatIconButton` (`showChatButton = canMessage`). Работы с ссылкой открывают её, шеврона нет. Документы не кликабельны. В карточке отзыва строка автора ведёт в профиль, иконка — в чат (если автор не зритель), карточка и «Читать полностью» — в 09d.
- **10.**
  - Карточки работ и документов открывают 11b и 11c, у них шеврон.
  - Если имя не указано, шапка показывает «Имя не указано».
  - В строке очереди отклонённой записи вместо «Нужны доказательства» показывается `moderationComment`, а если его нет — «Отклонено».
  - `onScreenShown` вызывает `refreshMyProfile`.
  - `profileTabHasBadge` при каждом `STARTED` (через `repeatOnLifecycle`) вызывает `refreshMyProfile`.
- **09b.**
  - Сводка: слева `displaySmall` с оценкой и `RatingStars(rating.roundToInt(), Small)`, справа счётчики.
  - Список подгружается, когда на экране видны три последних элемента.
  - Футер: «Загрузка…» со спиннером 20 dp или «Не удалось загрузить» + «Повторить».
  - Пустой список — 09g «Отзывов пока нет», ошибка — 09h.
- **09d.**
  - Колонка с прокруткой, отступ 16, зазор 16.
  - Автор — `PersonCard`: подпись — роль, без щита, чат при `!isByViewer`.
  - Заголовок `headlineMedium`, `RatingStars(Medium)` и число `titleLarge`.
  - Строка проекта: календарь и «Проект «%1$s» · завершён в %2$s» (без даты — «Проект «%1$s»»).
  - Текст `bodyLarge` в `onSurfaceVariant`.
- **10b.**
  - В начале формы «Имя *», «Вуз», строка «Уровень обучения» (`AppSelectField` и `StudyLevelSheet`: «Не указан» и 4 уровня, `RadioListItem`) плюс «Курс» (цифры) с зазором 12, затем «Направление».
  - Если `editable.studyLevel == false`, уровень скрыт и «Курс» занимает всю строку.
  - Если `editable.interestTypes == false`, чипы только для чтения.
  - Карточки работ и документов ведут в 11b и 11c.
- **11** (добавление работы).
  - «Загрузить подтверждение» скрыто. Кнопка — «Отправить на проверку» при ссылке, иначе «Добавить без подтверждения».
  - При `savedNotSent` над кнопкой показывается «Сохранено, но не отправлено на проверку. Попробуйте ещё раз», поля только для чтения.
  - Форма документа: подтверждение (ссылка) обязательно, под ним `InfoBanner` «Ссылку на подтверждение увидят все, кто откроет ваш профиль».
- **11b и 11c.**
  - Сверху `StatusBanner(status, заголовок, «Комментарий модератора: …» у отклонённой)`, при конфликте ещё `InfoBanner(tone = Error, icon = XCircle)`.
  - Поля с текущими значениями.
  - При `hasEvidenceFile` — некликабельная карточка «Подтверждение добавлено». У документа — `ProofUploadCard` и предупреждение о публичной ссылке.
  - `InfoBanner` с часами «После сохранения работа (документ) снова пройдёт проверку», если `base.status ≠ Unconfirmed && draft.sendsForVerification`.
  - Действия:
    - Outlined «Отправить на проверку», если `base.status == Unconfirmed && draft.sendsForVerification`;
    - Outlined «Открыть ссылку» (только у работы, `base.link`);
    - `TextError` «Удалить работу» / «Удалить документ» → `AppConfirmDialog(destructive = true)`.
  - Внизу `FormBottomBar` «Сохранить» с `loading`.

Строки профиля (ru и en, ключи 1:1).

Новые:

- `profile_read_more`, `profile_loading_more`;
- `profile_work_edit_title`, `profile_document_edit_title`;
- `profile_work_delete`, `profile_document_delete`, `profile_work_delete_title`, `profile_document_delete_title`;
- `profile_work_delete_text`, `profile_document_delete_text` (тексты 11d и 11h), `profile_delete_confirm`, `profile_delete_error`;
- `profile_conflict` («Запись изменилась. Показана актуальная версия — повторите правку»);
- `profile_saved_not_sent`, `profile_open_link`, `profile_status_rejected`, `profile_moderator_comment`;
- `profile_resubmit_warning_work`, `profile_resubmit_warning_document`;
- `profile_review_project_line`, `profile_review_project`;
- `profile_item_gone_title`, `profile_name_missing`;
- `profile_field_name`, `profile_field_university`, `profile_field_study_level`, `profile_study_level_none`, `profile_field_study_year`, `profile_field_field`;
- `profile_error_study_year`, `profile_error_text_too_long`;
- `profile_proof_public_warning`, `profile_message_author`.

Удаляются `profile_review_project_finished` и `profile_verification_rejected`.

## 7. Настройки: только «Открыт к сотрудничеству»

```kotlin
// feature:settings:domain
interface SettingsRepository {
    /** Whether people can find the user in the people search and recommendations; null: no profile (no name) yet. */
    suspend fun getOpenToCollaboration(): Boolean?
    /** Saves [open] and returns the value the server has now; throws ProfileMissingException without a profile. */
    suspend fun setOpenToCollaboration(open: Boolean): Boolean
}
class ProfileMissingException : IllegalStateException("The user has no profile yet")
class GetOpenToCollaborationUseCase(r) { suspend operator fun invoke(): Result<Boolean?> }
class SetOpenToCollaborationUseCase(r) { suspend operator fun invoke(open: Boolean): Result<Boolean> }

// feature:settings:data
interface SettingsDataSource { suspend fun getOpenToCollaboration(): Boolean?; suspend fun setOpenToCollaboration(open: Boolean): Boolean }
class SettingsRepositoryImpl(source: SettingsDataSource) : SettingsRepository
class GatewaySettingsDataSource(private val profiles: ProfilesApi) : SettingsDataSource
class MockSettingsDataSource(latencyMillis: Long = 400, initial: Boolean? = false) : SettingsDataSource

// feature:settings:ui
sealed interface SettingsUiState {
    data object Loading : SettingsUiState
    data class Error(val cause: Throwable) : SettingsUiState
    /** [openToCollaboration] null: no profile; the switch is off and disabled with a hint. */
    @Immutable data class Content(val openToCollaboration: Boolean?, val isSaving: Boolean = false,
        val failedToSave: Boolean = false) : SettingsUiState
}
class SettingsViewModel(get: GetOpenToCollaborationUseCase, set: SetOpenToCollaborationUseCase) {
    fun onToggle(open: Boolean); fun onSaveFailureShown(); fun onRetry()
}
class SettingsUiDependencies(val getOpenToCollaboration: GetOpenToCollaborationUseCase,
    val setOpenToCollaboration: SetOpenToCollaborationUseCase)
// SettingsRoute(dependencies, onNavigateBack, onSignOut, modifier): the signature does not change
```

| Вызов (Gateway) | Ответ → результат |
|---|---|
| `getOpenToCollaboration` | `myProfile()` 200 с непустым именем → `details.open_to_collaboration ?: false`; 404 или пустое имя → `null` |
| `setOpenToCollaboration` | `updateMyProfile { d -> if (d.displayName.isBlank()) throw ProfileMissingException(); d.copy(openToCollaboration = open) }` → значение из ответа PUT |

Поведение экрана:

- Переключение оптимистичное. Пока идёт сохранение, повторные нажатия игнорируются (`isSaving`).
- Ошибка — откат и snackbar.
- `ProfileMissingException` — откат и `openToCollaboration = null`, под выключенным переключателем подсказка «Сначала заполните профиль: укажите имя».
- Мок хранит флаг в памяти и в профиле его не показывает.

Согласованность кэшей:

- Любая запись идёт через общий `Mutex` и свежий `GET` в `updateMyProfile`, поэтому сохранение 10b не вернёт старый флаг, а переключатель не затрёт правки профиля.
- После PUT `UserDirectory` сам забывает зрителя.
- `MyProfileStore` флаг не хранит, поэтому его обновлять не нужно. Если флаг появится на 10, `ProfilesApi` нужно будет отдавать поток `myProfileChanges`.

Удаляются:

- `PrivacySettings`, `PrivacyOption`, `GetPrivacySettingsUseCase`, `SetPrivacyOptionUseCase`, `PrivacySettingsTest` и тесты, которые на них ссылаются;
- `PrivacyToggle` на экране и строки `settings_show_*`.

Добавляются `settings_open_to_collaboration`, `settings_open_to_collaboration_hint` («Вас смогут находить в поиске людей и в рекомендациях») и `settings_profile_missing_hint`. Если шаг 1 этого не сделал, `settings_sign_out_text` меняется на «Чтобы войти снова, понадобятся почта и пароль». Заголовок «Приватность и безопасность» остаётся, пока пользователь не ответил про «Видимость».

## 8. `:shared`: DI и навигация

```kotlin
// DataSourcesModule (all @SessionScope)
@Provides @SessionScope fun profileDataSource(backend: Backend, gateway: Lazy<GatewaySession>, session: Session,
    resources: SessionResources): ProfileDataSource = when (backend) {
    Backend.Mock -> MockProfileDataSource(scope = resources.scope, viewerId = session.userId)
    is Backend.Gateway -> gateway.value.let { GatewayProfileDataSource(it.profiles, it.achievements, it.reviews, it.users) }
}
@Provides @SessionScope fun moderationLedger(backend: Backend, store: SecureStore, session: Session,
    io: CoroutineDispatcher /* the provider AuthRepositoryImpl gets */): ModerationLedger = when (backend) {
    Backend.Mock -> InMemoryModerationLedger()
    is Backend.Gateway -> SecureStoreModerationLedger(store, session.userId, io)
}
@Provides @SessionScope fun settingsDataSource(backend: Backend, gateway: Lazy<GatewaySession>): SettingsDataSource =
    when (backend) { Backend.Mock -> MockSettingsDataSource(); is Backend.Gateway -> GatewaySettingsDataSource(gateway.value.profiles) }

// ProfileModule
@Provides @SessionScope fun profileRepositoryImpl(source: ProfileDataSource, ledger: ModerationLedger, session: Session,
    resources: SessionResources) = ProfileRepositoryImpl(source, ledger, viewerId = session.userId, scope = resources.scope)
// profileRepository / myProfileRepository — as now; profileUiDependencies(…, editableFields = mine.editableFields)

// SecureStoreModerationLedger.kt (:shared): key "profile.moderation.<userId>", text "v1\n<id>\t<Status>" per line;
// unreadable → null, a failed write is ignored (the result may repeat once).
```

Маршруты в `AppNavHost` (у всех есть аргументы, поэтому только `navigateOnce(entry, …)`):

```kotlin
const val ProfileReview = "profile/{userId}/reviews/{reviewId}"   // replaces "review/{reviewId}"
const val EditWork = "my_profile/works/{workId}"
const val EditDocument = "my_profile/documents/{documentId}"
fun profileReview(userId: UserId, reviewId: ReviewId) = "profile/${userId.value}/reviews/${reviewId.value}"
fun editWork(id: WorkId) = "my_profile/works/${id.value}"
fun editDocument(id: DocumentId) = "my_profile/documents/${id.value}"
```

Как подключаются колбэки:

- `onReviewClick` вызывает `navigateOnce(entry, profileReview(…))`.
- `onAuthorClick` вызывает `openProfile(entry, id)`.
- `onAuthorChatClick` и `onMessageClick` вызывают `openChatWith(entry, user)`: это уже `ExclusiveLauncher`.
- `onEditWork` и `onEditDocument` вызывают `navigateOnce` с 10, 10b и 09e.
- Закрытие форм — `popBackStackOnce(entry)` из `LaunchedEffect(isDone)`.

Новых адаптеров между фичами нет: настройки ходят в `:gateway` напрямую.

Режимы:

| Фича | Mock | Gateway |
|---|---|---|
| profile | `MockProfileDataSource` и `InMemoryModerationLedger` | `GatewayProfileDataSource` и журнал в `SecureStore` |
| settings | `MockSettingsDataSource` | `GatewaySettingsDataSource` |
| остальное | без изменений | без изменений |

Подключения в `build.gradle.kts`: `implementation(project(":gateway"))` в `feature/profile/data` и `feature/settings/data`. Новых библиотек нет.

## 9. Тесты

**`:gateway`** (`ktor-client-mock`, фикстуры по схемам):

- для каждого метода: путь, метод, `Bearer`, параметры `limit` и `offset`;
- тело `create` в snake_case и без `null`;
- `updateMyProfile`: GET, затем PUT; неизменённые поля, `contact_email` и незнакомый ключ уходят обратно; 404 становится пустой основой; исключение в `change` и пустое имя — без PUT; два параллельных вызова идут строго по очереди; после PUT вызывается `invalidate`;
- `updateMine`: `expected_revision` в теле, `evidence_file_id` сохранён, устаревшая ревизия даёт 409 без PUT;
- `submit` без тела; `delete` 204;
- разбор: `rating` 4 и 4.8, `*_unix` строкой и числом, `null` вместо массивов, `{}`;
- `UserDirectory`: один запрос на id при конкуренции; не больше 4 параллельно; TTL на `TestTimeSource`; кэш для 400/403/404; сбой не кэшируется; `invalidate` во время запроса; отмена одного ожидающего не отменяет остальных;
- словарь: регистр, алиасы, `onUnknownValue`, `write` у неподтверждённого словаря;
- маппинг: `toCollaborator`, `ratingOrNull`, `studyOf`.

**`profile:data`**:

- `GatewayProfileDataSource` на фейках `ProfilesApi`, `AchievementsApi`, `ReviewsApi` и настоящем `UserDirectory(fetch = fake)`: все строки таблицы раздела 5, сохранение непознанных `study_level` и `project_types`, сортировка, часовой пояс;
- `ProfileAssembly`: чистые функции;
- `ReviewsCache`: попадание, листание до 5 страниц, `hasMore`, потолок 10 000;
- `MyProfileStore`: одно обновление на всех, пауза, ошибка первой загрузки уходит подписчику;
- `ModerationTracker`: база, решения, свои переходы не дают результата, перезапуск с журналом, `markResultsSeen`, удаление записи;
- `ProfileRepositoryImpl` на фейке источника: параллельность трёх запросов (через задержки-«ворота»), `null` отменяет соседние запросы, `isByViewer`, изменения встраиваются в `observeMyProfile`;
- `MockProfileDataSource`: ревизии, 409, 404, сброс статуса, модерация на планировщике теста;
- `InMemoryModerationLedger`.

**`profile:domain`**: проверка `ProfileDraft`, `WorkDraft` и `DocumentDraft` (в том числе `hasEvidenceFile`); вся таблица Add/Save на фейке репозитория; `LoadResult` у `Get*`.

**`profile:ui`** (против `ProfileRepositoryImpl(MockProfileDataSource(latencyMillis = 0), InMemoryModerationLedger(), …)`, коллекторы в `backgroundScope`):

- 09b: подгрузка, дубликаты, сбой и повтор, параллельные запросы;
- 09d: попадание и промах;
- 10: обновление при показе, результат после модерации;
- 10b: поля, `editable`, имя обязательно, `isSaving`;
- Add: `SavedNotSent` и повторная отправка без второго `create`;
- `PortfolioEditViewModel`: загрузка, `NotFound`, сохранение без изменений не делает запросов, повторная отправка после правки, конфликт с перезагрузкой, удаление, двойные нажатия.

**`settings`**: VM (загрузка, нет профиля, откат, одно сохранение за раз, повтор); `GatewaySettingsDataSource` на фейке `ProfilesApi` (меняется только флаг, нет профиля); мок.

**`:shared`**: `SecureStoreModerationLedger` на `InMemorySecureStore`; граф сессии собирается в обоих режимах (Gateway — без сети); `LocalizationResourcesTest`.

Kover: domain и data каждого нового класса.

## 10. Что удалить, переписать, добавить

**Удалить.**

- `profile/data/.../mock/WebLinks.kt` и `WebLinksTest.kt` (переезжают в `:utils`).
- `PrivacySettings.kt` и его тест; `seedResults` мока.
- `Routes.Review`; `ProfileHeaderCardUiModel.actionText`, если шаг 4 его не убрал.
- `AboutDraft`, `AboutDraftError`, `UpdateAboutUseCase` (их заменяют `ProfileDraft…`); `WorkDraft.proof`, `WorkDraftError.ProofInvalid`.

**Переписать.**

- profile domain: `ProfileModels`, `ProfileDrafts`, `ProfileRepository`, оба файла use case'ов.
- profile data: `ProfileDataSource`, `ProfileRepositoryImpl`, `MockProfileDataSource`, `MockProfileMapping`, `MockModerator` (импорт из `:utils`).
- profile ui: `ProfileUiModel`, `ReviewCard`, `PortfolioCards`, `ProfileSections`, `ProfileLabels`, `ProfileTabBadge`, `ProfileUiDependencies`, все экраны и VM в `user/` и `my/`, `FormParts`, строки, `TestSetup`.
- settings: domain, data, ui целиком.
- `:shared`: `DataSourcesModule`, `ProfileModule`, `SettingsModule`, `AppNavHost`.
- `:common`: `Collaborator`; `:mockserver`: `MockPeople` и `MockPeopleSeed`.
- collaborators: `MockCollaboratorsDataSource` (`haystack` через `study.mockText()`), `CollaboratorItem(collaborator: Collaborator, matchPercent)`, экран и тесты.
- feed: `MockFeedDataSource`, `FeedScreen` (`person.subtitleText()`), тесты; `:catalog`.

**Добавить.**

- `:gateway`: `dto/{Profile,Achievement,Review}Dtos.kt`, `{Profiles,Achievements,Reviews}Api.kt` с реализациями на Ktor, `UserDirectory.kt`, `contract/GatewayVocabulary.kt`, `mapping/UserMapping.kt`, тесты и фикстуры (Kotlin-строки).
- `:common/model/Study.kt`.
- `:components`: `person/StudyText.kt`, `RatingStars.kt`, `StatusBanner`, `ic_star_filled`.
- profile data: `ProfileAssembly`, `ReviewsCache`, `MyProfileStore`, `ModerationTracker`, `ModerationLedger`, `GatewayProfileDataSource` и его маппинг.
- profile ui: `PortfolioEditViewModel`, `EditWorkScreen`, `EditDocumentScreen`, `StudyLevelSheet`.
- settings data: `GatewaySettingsDataSource`.
- `:shared`: `SecureStoreModerationLedger`.

## 11. Коммиты и распределение

1. **«Move the link source to :utils»** — `domain-data`.
2. **«Replace the study line with Study(level, year)»**: `:common`, `:mockserver`, данные моков collaborators и feed — `domain-data`; `StudyText` и три места вызова в UI — `compose-ui`.
3. **«Add the profiles, achievements and reviews of the Gateway»**: `:gateway` и тесты — `domain-data`. Новый код ещё нигде не используется, коммит собирается сам.
4. **«Add the rating, status banner and destructive actions to :components»** и `:catalog` — `compose-ui`, параллельно с 1–3.
5. **«Read profiles and reviews from the server-level data source»**: окончательные profile domain и data (с моком), экраны 09, 09b, 09d, 10, маршрут отзыва. В 10b, 11 и форме документа — только правки под новые сигнатуры, чтобы собиралось. `domain-data` начинает, `compose-ui` начинает на фейках репозитория и переходит на мок.
6. **«Edit and delete works and documents; name and study in 10b»**: только UI и маршруты — `compose-ui`.
7. **«Serve the profile from the Gateway»**: `GatewayProfileDataSource`, DI, журнал в `SecureStore`, тесты графа — `domain-data`.
8. **«Keep only "Open to collaboration" in the settings»**: domain, data, ui и DI. Зависит только от 3, поэтому может идти параллельно 5–7.
9. **«Document block 2»**: `sprint-1.md`, Часть 4 в `gateway-integration.md`.

После коммитов 5–8 `tester` прогоняет host-тесты, Kover, `:androidApp:assembleDebug`, компиляцию iOS с чистым `KONAN_DATA_DIR` и эмулятор в режиме `mock`: 5 тапов по «Все N», автору, чату, «Удалить», переключателю. Затем `reviewer`.

Альтернатива: объединить 5 и 6, если правки «чтобы собиралось» в 5 обойдутся дороже. Отделить domain и data от UI нельзя, потому что UI использует удаляемое.

## 12. Риски

- **Коммит 5 большой.** Мок, репозиторий и четыре экрана меняются вместе.
- **Коммит 2 трогает collaborators и feed, которые переделывает блок 3.** Он должен попасть в `dev` раньше, чем блок 3 начнёт реализацию.
- **Неизвестные строки статуса.** Очередь, результаты и кнопка «Отправить на проверку» могут вести себя неверно до ответа бэкенда. Если статус неизвестен, `submit` может вернуть 409, и пользователь увидит конфликт.
- **Нет ревизии у профиля.** Одновременные правки с двух устройств затирают друг друга; внутри приложения правки идут строго по очереди.
- **Фикстуры написаны по схемам**, а не по ответам: формат чисел и `*_unix` не проверен.
- **Отрицательный кэш `UserDirectory`.** Профиль, созданный меньше 5 минут назад, другие пользователи могут видеть как «не найден».
- **До блоков 3–5 в режиме Gateway** лента, поиск и страница проекта отдают мок-id (`user-arjun`), и тап по человеку ведёт на «Профиль не найден».
- **Пределы.** Больше 100 достижений не показываются; отзывы глубже offset 10 000 не грузятся.
- **Журнал на iOS (Keychain) проверен только компиляцией.** После переустановки базой снова становятся текущие статусы.
- **Перезапуск процесса на формах 11b и 11c** теряет несохранённый ввод.
- **`AppButton(loading)`** может отсутствовать после шага 6.
- **`LocalizationResourcesTest`** упадёт, если ключи ru и en расходятся.
- **Kover не видит `iosMain`.**

## 13. Пробелы дизайна и спецификации: допущения для `docs/sprint-1.md`

- Новый аккаунт: 404 на свой профиль даёт пустой профиль. Шапка 10 показывает «Имя не указано», секции — «Не заполнено». Первое сохранение 10b создаёт профиль, имя обязательно.
- «Уровень обучения» в Figma нарисован текстовым полем; в приложении это выбор из 4 уровней и «Не указан». В режиме Gateway поле скрыто, а чипы типов проектов только для чтения, пока строки не подтверждены. Значения сервера, которые клиент не распознал, при сохранении не стираются и не показываются.
- Лимиты (в макете не заданы): имя до 100, вуз до 150, направление до 100, курс от 1 до 6.
- В карточке работы (11, 11b) «Загрузить подтверждение» скрыто до загрузки файлов. Файл, приложенный в другом месте (`evidence_file_id`), показывается как «Подтверждение добавлено» и при правке сохраняется. Подтверждение документа — ссылка в публичном `url` с предупреждением.
- Правка уже отправленной записи сразу снова отправляет её на проверку. Без ссылки и файла запись остаётся «не подтверждена».
- «Сохранено, но не отправлено»: форма остаётся открытой, поля только для чтения, кнопка повторяет только отправку.
- Конфликт (409): форма загружает актуальную версию, ввод теряется. Текст 11g изменён на «Запись изменилась. Показана актуальная версия — повторите правку».
- Удалённая запись (404 при правке) — экран «Запись удалена». 404 при удалении считается успехом.
- Отзывы: автор без щита (признака нет). Роль показывается, только если она известна, иначе одно название проекта. Месяц — по `project_completed_at_unix` в часовом поясе устройства; если его нет, дата скрыта. Звёзды сводки 09b округляются. Отзыв по id ищется в просмотренных страницах, затем не дальше 500 отзывов, после этого — «Отзыв не найден».
- Результат проверки получает время обнаружения (времени решения в API нет). Результаты появляются после обновления: при старте, возврате в приложение и входе на вкладку. Пушей нет. Решения сохраняются между запусками.
- Чужим пользователям «на проверке» и «отклонено» показываются как «не подтверждено». Комментарий модератора видит только владелец.
- Строки очереди проверки остаются некликабельными. 09e для своих работ открывает правку.
- Настройки без профиля: переключатель выключен и недоступен, с подсказкой «Сначала заполните профиль: укажите имя».
- В моке профессор, доцент, постдок и кандидат наук лишились подписи статуса, потому что у них нет уровня обучения.
- Английские тексты — наш перевод: «Read more», «Edit work», «Delete this work?», «Saved, but not sent for verification», «Open to collaboration», «Bachelor's, year %1$d».

## 14. Вопросы бэкенду

Ответа не ждём, работа идёт по допущениям.

1. Значения `UserAchievement.status` и переходы между ними:
   - PUT сбрасывает запись в черновик или сразу в проверку?
   - Можно ли вызвать `submit` после отклонения?
   - Отправляется ли запись на проверку сама при `create` со ссылкой?
   - Повышает ли решение модератора `revision`?
2. Значения `study_level`, `project_types` (и `Project.type`), `author_role`.
3. Что отвечает `GET /users/me/profile` аккаунту без профиля: 404 или 200 с пустым `details`?
4. PUT профиля: отсутствующее поле означает «очистить»? Будет ли у профиля ревизия или ETag? Значение `open_to_collaboration` по умолчанию?
5. Обязателен ли `expected_revision` в PUT достижения и что происходит, если его нет?
6. Видны ли в публичном `GET /users/{id}/achievements` записи «на проверке» и «отклонено» и их `moderation_comment`? Порядок `items` в достижениях и отзывах и устойчивость листания по `offset`.
7. Ограничения длины: `display_name`, `about`, `title`, `description`, текст отзыва.
8. `rating` при `review_count == 0`: 0 или поле отсутствует?
9. Нужны: `GET /users/{id}/reviews/{reviewId}`, `total` в `UserReviewList` и `UserAchievementList`, время решения модерации, API «непросмотренных решений» или пуши, пакетный `GET /users?ids=`.
10. Будет ли закрытое поле для подтверждения (сейчас `url` публичен) или загрузка через Media с `is_public = false`?

## Ключевые файлы

- Спецификация и проектные документы: `/Users/samvel/Desktop/Study/Methodology/AcademicNet/docs/openapi.yaml`, `/Users/samvel/Desktop/Study/Methodology/AcademicNet/docs/gateway-integration.md`, `/Users/samvel/Desktop/Study/Methodology/AcademicNet/docs/figma-handoff.md`, `/Users/samvel/Desktop/Study/Methodology/AcademicNet/docs/sprint-1.md`
- `/Users/samvel/Desktop/Study/Methodology/AcademicNet/common/src/commonMain/kotlin/org/matchlab/academic_net/common/model/Collaborator.kt`
- `/Users/samvel/Desktop/Study/Methodology/AcademicNet/utils/src/commonMain/kotlin/org/matchlab/academic_net/utils/WebLink.kt`
- `/Users/samvel/Desktop/Study/Methodology/AcademicNet/mockserver/src/commonMain/kotlin/org/matchlab/academic_net/mockserver/MockPeople.kt`, `/Users/samvel/Desktop/Study/Methodology/AcademicNet/mockserver/src/commonMain/kotlin/org/matchlab/academic_net/mockserver/MockPeopleSeed.kt`
- Домен профиля: `/Users/samvel/Desktop/Study/Methodology/AcademicNet/feature/profile/domain/src/commonMain/kotlin/org/matchlab/academic_net/feature/profile/domain/` (`ProfileModels.kt`, `ProfileDrafts.kt`, `ProfileRepository.kt`, `usecase/`)
- Data профиля: `/Users/samvel/Desktop/Study/Methodology/AcademicNet/feature/profile/data/src/commonMain/kotlin/org/matchlab/academic_net/feature/profile/data/` (`ProfileDataSource.kt`, `ProfileRepositoryImpl.kt`, `mock/`)
- UI профиля: `/Users/samvel/Desktop/Study/Methodology/AcademicNet/feature/profile/ui/src/commonMain/kotlin/org/matchlab/academic_net/feature/profile/ui/` (`ReviewCard.kt`, `PortfolioCards.kt`, `ProfileSections.kt`, `user/`, `my/`)
- Настройки: `/Users/samvel/Desktop/Study/Methodology/AcademicNet/feature/settings/` (domain, data, ui)
- `/Users/samvel/Desktop/Study/Methodology/AcademicNet/shared/src/commonMain/kotlin/org/matchlab/academic_net/ui/navigation/AppNavHost.kt`, `/Users/samvel/Desktop/Study/Methodology/AcademicNet/shared/src/commonMain/kotlin/org/matchlab/academic_net/di/ProfileModule.kt`, `/Users/samvel/Desktop/Study/Methodology/AcademicNet/shared/src/commonMain/kotlin/org/matchlab/academic_net/di/SettingsModule.kt`, `/Users/samvel/Desktop/Study/Methodology/AcademicNet/shared/src/commonMain/kotlin/org/matchlab/academic_net/di/MockDataSourcesModule.kt` (после шага 1 это `DataSourcesModule.kt`)
- `/Users/samvel/Desktop/Study/Methodology/AcademicNet/components/src/commonMain/kotlin/org/matchlab/academic_net/components/` (`Blocks.kt`, `Buttons.kt`, `Dialogs.kt`, `person/`)
- `/Users/samvel/Desktop/Study/Methodology/AcademicNet/feature/collaborators/data/src/commonMain/kotlin/org/matchlab/academic_net/feature/collaborators/data/mock/MockCollaboratorsDataSource.kt`, `/Users/samvel/Desktop/Study/Methodology/AcademicNet/feature/collaborators/ui/src/commonMain/kotlin/org/matchlab/academic_net/feature/collaborators/ui/CollaboratorSearchViewModel.kt`, `/Users/samvel/Desktop/Study/Methodology/AcademicNet/feature/feed/ui/src/commonMain/kotlin/org/matchlab/academic_net/feature/feed/ui/FeedScreen.kt`
- `/Users/samvel/Desktop/Study/Methodology/AcademicNet/catalog/src/commonMain/kotlin/org/matchlab/academic_net/catalog/ComponentCatalog.kt`
