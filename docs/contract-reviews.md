# Контракт: написание отзыва (кадр 16)

Решение заказчика 2026-10-21: делаем отзывы. **Вход — из профиля другого человека**; отзыв пишется по **общему успешно завершённому проекту**,
которого выбирают в форме. Недостающие экраны рисует дизайнер в Figma. Правила проекта — `CLAUDE.md`; значения и пробелы — `docs/api-gaps.md`.

## 1. Что говорит API (`docs/openapi.yaml`, бэкенд проверен по коду)

`POST /api/v1/users/me/reviews` → 201 `UserReview`. Тело `UserReviewInput` (все поля обязательны): `project_id`, `target_user_id`, `title`,
`text`, `rating`. Проверки бэкенда (`internal/user/profile/service.go`, `repository.go`): ids — UUID; `target_user_id` ≠ автор; `title` 1..255,
`text` 1..10000 (непустые после обрезки); `rating` 1..5; проект должен быть в контексте с `completed_at <= now()`; остальное (оба участники,
не больше одного отзыва на пару «проект, автор, адресат») — ограничения БД: ответ 400/403/404/409 (текст причины в теле ошибки).
Ответы ошибок общие (`400` поля, `401` сеанс, `403` права, `404`, `409` конфликт). У `Project` есть `completed_at_unix`, `members` (`ProjectMember`:
`user_id`, `role_id`, `role_title`, `status` — `active|left|removed`), `owner_user_id`.
Фильтры `GET /api/v1/projects`: `mine` = `owner` | `participant` (участие активное, кроме собственных проектов), `status` = `completed` (значения из
PR #16 бэкенда, `docs/api-values.md`). Чужие отзывы: `GET /users/{id}/reviews?limit&offset` — в `UserReview` есть `author_user_id` и `project_id`.

## 2. Как находится «общий успешно завершённый проект» (клиент)

Для зрителя V и адресата T:
1. Проекты зрителя: `GET /projects?mine=owner&status=completed` и `GET /projects?mine=participant&status=completed` (страницы по 100,
   не больше 10 страниц на запрос, порядок сервера).
2. Проект общий, если T — владелец (`owner_user_id`) или есть в `members` (статус не `left` и не `removed`: у бэкенда вышедший участник не участник; пустой статус считается участником; если `members` в списке пусты — проект не
   предлагается и это записать в `docs/api-gaps.md`: нужен `GET /projects/{id}` для проверки команды, пока не делаем).
3. Уже оставленные отзывы исключаются: `GET /users/{T}/reviews` (страницы по 100) — проекты, у которых есть отзыв с `author_user_id == V`.
4. Результат — список `ReviewableProject(id, title, completedAt?)`; порядок: по дате завершения, свежие сверху; без повторов.
Что проект «успешно завершён» — это `status = completed` (значения `cancelled` и прочие не предлагаются). Что API не говорит и решает сервер
при отправке (оба ли были участниками, отзыв уже есть): показывается как ошибка отправки (§5).

## 3. Слои (новая фича `reviews`, копия структуры `feature/projects/*`)

`:feature:reviews:domain`, `:data`, `:ui` (+ `include` в `settings.gradle.kts`, `withHostTest {}`, `kover(...)` в корневом `build.gradle.kts`). Фича не зависит от других
фич; данные берёт из `:gateway` в своём `data`. Профиль (`feature:profile:ui`) про отзывы-запись не знает: наружу колбэк.

### 3.1 `:gateway` (владелец `domain-data`)
- `ProjectSearchParams` + `mine: String?` и `status: String?`; `ProjectCatalogApi.list` их шлёт (параметры `mine`, `status`); `search` по-прежнему
  не шлёт. Константы значений — в `:common` или рядом (`ProjectMineFilter.Owner = "owner"`, `Participant = "participant"`; `ProjectStatus.Finished.code`).
  Сбросить в KDoc «never sent». Обновить `docs/api-gaps.md` текстом в отчёте (G44: `mine` теперь описан бэкендом).
- `ProjectDto.completedAtUnix` (`completed_at_unix`, `FlexibleLongSerializer`, по умолчанию 0).
- `ReviewsApi.create(input: UserReviewInputDto): UserReviewDto` (`POST users/me/reviews`, 201); `UserReviewInputDto(projectId, targetUserId, title,
  text, rating)` с `@SerialName`; тела ошибок — как в остальных API (`RemoteFailure.Http(status, message)`).

### 3.2 `:feature:reviews:domain` (владелец `domain-data`)
```kotlin
/** A project the viewer and the person completed together and the viewer has not reviewed them in yet. */
data class ReviewableProject(val id: ProjectId, val title: String, val completedAt: Instant?)

enum class ReviewDraftError { ProjectMissing, RatingMissing, TitleBlank, TitleTooLong, TextBlank, TextTooLong }
object ReviewLimits { const val MaxTitle = 255; const val MaxText = 10_000; const val MaxRating = 5 }

data class ReviewDraft(val projectId: ProjectId? = null, val rating: Int = 0, val title: String = "", val text: String = "") {
    fun normalized(): ReviewDraft          // trims title and text
    fun validate(): Set<ReviewDraftError>   // of the normalized draft
}

/** Why the server refused a review that was well formed. */
sealed class ReviewRefusal : Exception()   // AlreadyReviewed (409), NotAllowed (403/404, or 400 with a project/participation reason), 

interface ReviewsRepository {
    /** §2. Throws `RemoteFailure` when a request fails. */
    suspend fun getReviewableProjects(targetId: UserId): List<ReviewableProject>
    /** Throws ReviewRefusal for a well-formed review the server refuses, RemoteFailure for the rest. */
    suspend fun sendReview(targetId: UserId, draft: ReviewDraft)
}
class GetReviewableProjectsUseCase(...) { suspend operator fun invoke(targetId): Result<List<ReviewableProject>> }
class SendReviewUseCase(...) { suspend operator fun invoke(targetId, draft): SubmitResult<Unit-like> }   // Invalid(errors) without a request; Success; Failure(cause)
```
Использовать `SubmitResult` и `suspendRunCatching` из `:common`/`:utils` (как в `UpdateProfileUseCase`); соглашение о форме результата взять из них. Статус
ошибок: 409 → `ReviewRefusal.AlreadyReviewed`; 403 → `NotAllowed`; 404 → `NotAllowed` (проект или человек недоступны); 400 → `NotAllowed` (причину сервера читать как
текст `RemoteFailure.Http.message`, но не показывать пользователю как есть); остальное — `RemoteFailure` как пришёл.

### 3.3 `:feature:reviews:data` (владелец `domain-data`)
`GatewayReviewsDataSource(api projects/catalog, reviews api, viewerId)` + `ReviewsRepositoryImpl`: §2 (параллельно два запроса проектов зрителя; отзывы
адресата постранично), перевод ответа в `ReviewableProject` (`completedAt` из `completedAtUnix`, 0 → null); `sendReview` → `POST` с нормализованным
черновиком, перевод ошибок. Проект без `id` отбрасывается. Тесты — `tester` (fake-API из тестового набора).

### 3.4 `:shared` (владелец `domain-data`, кроме `ui/navigation/*`)
`di/ReviewsModule.kt` (в графе сессии: data source, repository, use cases, `ReviewsUiDependencies`), `SessionDependencies.reviews`, подключение в `SessionComponent`.

## 4. UI (`:feature:reviews:ui`, `:components`, `feature:profile:ui`, `:shared/ui/navigation/*`; владелец `compose-ui`, после дизайнера)

- `ReviewFormRoute(dependencies: ReviewsUiDependencies, target: UserSummary, onClose: () -> Unit, onSent: () -> Unit)`; `ReviewFormViewModel(targetId, getProjects,
  sendReview)`; состояние: проекты (`Loading` / `Loaded(list)` / `Failed(cause)`; пустой список = «нет общих проектов»), черновик, ошибки полей после первой попытки,
  `isSending`, итог отправки (`Sent`, `AlreadyReviewed`, `NotAllowed`, `Failed(cause)`); страж повторной отправки (`isSubmitting` держится после успеха, пока экран
  закрывается); одно нажатие «Отправить» = один запрос. Проекты грузятся при открытии формы и по «Повторить»; выбор проекта — шит (радио-список) по нажатию на поле «Проект».
  Если проект один — он выбран сразу (решение до макета: уточнить у дизайнера, показывает ли форма выбор при одном проекте).
- Новый компонент `:components`: ввод оценки звёздами (`RatingInput`: 5 звёзд, нажатие ставит значение 1..5, повторное нажатие на выбранную — снимает до 0, доступность:
  «Оценка N из 5», `Role.Slider`/кнопки), по макету дизайнера; в каталог.
- Профиль чужого человека (`UserProfileScreen`): кнопка «Оставить отзыв» (по макету 09r) → колбэк `onLeaveReviewClick: (UserSummary) -> Unit`; после отправки — баннер
  «Отзыв отправлен» (по макету 09s) и перечитывание отзывов профиля (как баннер 09q «приглашение отправлено»: смотреть, как он сделан).
- Навигация (`AppNavHost`): `Routes.review(userId)`; форма открывается по `navigateOnce(entry, …)`; `target` берётся из профиля (имя уже загружено); после `onSent` — `popBackStackOnce`
  и сигнал профилю (по образцу `inviteRequests`).

## 5. Тексты и состояния (RU / EN; точные тексты и кадры — из Figma, ниже — отправная точка для дизайнера)

Форма «Отзыв о работе»: поле «Проект *» (плейсхолдер «Выберите проект»), оценка, «Заголовок *» (до 255), «Комментарий *» (до 10000), «Отправить отзыв». Шит «Общий проект»:
строки проекта «Название» / «Завершён · март 2026». Пусто: «Нет общих завершённых проектов» / «Отзыв можно оставить тому, с кем вы вместе завершили проект». Ошибки полей:
«Выберите проект», «Поставьте оценку», «Укажите заголовок», «Напишите комментарий», «Слишком длинный текст». Ошибки отправки: «Вы уже оставили этому человеку отзыв по
этому проекту», «Оставить отзыв нельзя: проект не завершён или вы в нём не участвовали», «Не удалось отправить. Проверьте соединение» (+ повтор). Успех: «Отзыв отправлен».

## 6. Тесты (`tester`)
Черновик и валидация (границы 1/255/10000, обрезка, оценка 0..5), `GetReviewableProjectsUseCase`/`SendReviewUseCase`, `GatewayReviewsDataSource` (владелец/участник, исключение
`removed`, отзывы уже оставленные, повторы, постраничность, пустой список, сбой одного из запросов, перевод 409/403/404/400), `ReviewFormViewModel` (загрузка проектов, один проект,
выбор, ошибки полей, один запрос на нажатие, итоги отправки, повтор после сбоя), шлюз (`create`, `mine`/`status` в запросе, `completed_at_unix`).
