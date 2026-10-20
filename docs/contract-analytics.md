# Контракт: аналитика AppMetrica (реализация)

Решения заказчика 2026-10-21 (ответы на вопросы `docs/analytics.md` §7): (1) «клик на проект» — открытие страницы проекта;
(2) идентификатор пользователя передаём (`setUserProfileID`); (3) согласие: переключатель в настройках **и** чекбокс при
регистрации, по умолчанию включён, он задаёт начальное значение настройки; (4) одно приложение в AppMetrica. Ключ приложения
нужен от заказчика: пока ключа нет, аналитика выключена (`NoOpAnalytics`), всё собирается и тестируется без него.

Общие правила работы: `CLAUDE.md`. Фича не зависит от другой фичи; аналитику шлёт навигационный слой (`:shared`), фичи про неё
не знают, кроме регистрации (чекбокс) и настроек (переключатель): они отдают выбор наружу. Агенты не коммитят и не правят
`docs/*` (текст для документов дают в отчёте). Gradle — только через `scratchpad/tools/gw.sh` (см. задание агента).

## 1. События

| Событие | Когда | Параметры |
|---|---|---|
| `project_card_click` | пользователь нажал на карточку проекта в списке | `project_id`, `source` |
| `project_click` | по нажатию открылась страница проекта (04 или 07) | `project_id`, `source` |
| `user_click` | пользователь нажал на человека и открылся его профиль | `user_id`, `source` |

- Событие шлётся только когда переход **выполнен**: `navController.navigateOnce(entry, route)` вернул `true` (двойной тап — одно
  событие). Одно нажатие на карточку проекта даёт **два** события: `project_card_click` и `project_click` (разные метрики).
- Страница проекта, открытая не нажатием (после создания проекта экран сам переходит на страницу), событий не даёт.
- Пустой идентификатор не отправляется.
- В параметрах только `project_id` / `user_id` (UUID сервера) и `source`; никаких имён, почты, текстов, названий.

`source` проекта (`ProjectSource.code`): `feed_recommendations` (карточка рекомендаций на ленте), `project_search` (результат
поиска проектов), `my_projects_owner` («Я инициатор» → страница управления 07), `my_projects_member` («Я участник» → 04),
`responses` (карточка во вкладке «Отклики»).

`source` человека (`UserSource.code`): `feed_people`, `people_search`, `project_initiator` (инициатор на 04),
`project_team` (участник на 04), `project_manage_team` (участник на 07b), `project_candidates` (кандидат в откликах на проект),
`review_author` (автор отзыва: профиль, список и страница отзыва), `chat_header` (собеседник в шапке чата).

Места отправки — колбэки `AppNavHost`: все вызовы `openProfile(...)` (пользователи) и все `navigateOnce(..., Routes.project(id))` /
`Routes.projectDetails(id)` по нажатию (проекты); найти их по `rg "openProfile|Routes.project"` и сопоставить с `source`.

## 2. Модуль `:analytics` (новый, commonMain, без SDK) — владелец `domain-data`

Копия `build.gradle.kts` из `feature/feed/domain`, `withHostTest {}`, цели android + `iosArm64` + `iosSimulatorArm64`,
`include(":analytics")`. Зависимости: `api(libs.kotlinx.coroutines.core)`, `implementation(project(":securestore"))`. Пакет
`org.matchlab.academic_net.analytics`. KDoc обязателен.

```kotlin
interface Analytics {
    /** Sends [event] with string [params]. Never throws; does nothing while sending is off. */
    fun track(event: String, params: Map<String, String> = emptyMap())

    /** Ties the next events to the account [id] (the AppMetrica user profile id), or to nobody when `null`. */
    fun setUserProfileId(id: String?)

    /** The person's choice: `false` stops every sending (and drops what [track] gets), `true` allows it. */
    fun setEnabled(enabled: Boolean)
}

object NoOpAnalytics : Analytics   // всё пусто

enum class ProjectSource(val code: String) { FeedRecommendations("feed_recommendations"), ProjectSearch("project_search"),
    MyProjectsOwner("my_projects_owner"), MyProjectsMember("my_projects_member"), Responses("responses") }

enum class UserSource(val code: String) { FeedPeople("feed_people"), PeopleSearch("people_search"),
    ProjectInitiator("project_initiator"), ProjectTeam("project_team"), ProjectManageTeam("project_manage_team"),
    ProjectCandidates("project_candidates"), ReviewAuthor("review_author"), ChatHeader("chat_header") }

object AnalyticsEvents {   // имена в одном месте
    const val ProjectCardClick = "project_card_click"; const val ProjectClick = "project_click"; const val UserClick = "user_click"
    const val ProjectId = "project_id"; const val UserId = "user_id"; const val Source = "source"
}

/** Builds the events of the clicks and hands them to [analytics]; blank ids are not sent. */
class ClickTracker(private val analytics: Analytics) {
    fun projectCardClicked(projectId: String, source: ProjectSource)   // project_card_click
    fun projectOpened(projectId: String, source: ProjectSource)        // project_click
    fun userClicked(userId: String, source: UserSource)                // user_click
}

/**
 * The choice "send usage statistics" of this device, kept in the [SecureStore] under the key `analytics.statistics`
 * ("1" / "0"); not a secret, the same store holds the last e-mail. Survives sign-out. Blocking store calls run on an IO
 * dispatcher. [enabled] is `null` until the person has chosen (or the app has set the default, §4).
 */
class StatisticsPreference(store: SecureStore, io: CoroutineDispatcher = <как в AuthRepositoryImpl>) {
    val enabled: StateFlow<Boolean?>
    /** Reads the stored value once; the other calls wait for it. */
    suspend fun load()
    /** Saves [enabled] and publishes it; throws when the store cannot write (the caller decides what to do). */
    suspend fun set(enabled: Boolean)
}
```

## 3. Принятие решения о включении — `:shared` (владелец `domain-data`, кроме `ui/navigation/*`)

`internal class AnalyticsController(analytics: Analytics, preference: StatisticsPreference, authState: StateFlow<AuthState>)`
(в `:shared`, пакет `org.matchlab.academic_net`; `AuthState` из `feature.auth.domain`), `fun start(scope: CoroutineScope)`:

1. `preference.load()`; затем каждое значение `preference.enabled` → `analytics.setEnabled(value == true)`. Пока выбор не сделан
   (`null`), отправка выключена.
2. По `authState`: `SignedIn` → если выбора нет (`null`), **записать по умолчанию `true`** (вход в аккаунт на устройстве без выбора;
   настройка по умолчанию включена) и `analytics.setUserProfileId(session.userId.value)`; `SignedOut` → `setUserProfileId(null)`.
   `Restoring` ничего не делает. Решение о значении по умолчанию принимается только после `preference.load()`.
3. Сбой записи значения не роняет приложение (проглотить, значение остаётся `null` → отправка выключена).

Подключение: `App(secureStore, backend, incomingLink, onIncomingLinkHandled, analytics: Analytics = NoOpAnalytics)`;
`AppGraph.get(secureStore, backend, analytics)`; `AppComponent` получает `analytics` (`@get:Provides`) и отдаёт `preference`
(`StatisticsPreference(secureStore)`, один на процесс); `AppGraphs.start` запускает `AnalyticsController` в `processScope`.
`AppGraphs` отдаёт `clicks: ClickTracker(analytics)` и `fun onStatisticsChosen(enabled: Boolean)` (запускает `preference.set` в
`processScope`). iOS-приложение без изменений получает `NoOpAnalytics` (параметр с умолчанием).

`AppNavHost` (владелец `compose-ui`) получает ровно такую сигнатуру:

```kotlin
internal fun AppNavHost(
    auth: AuthUiDependencies,
    sessions: SessionGraphs,
    clicks: ClickTracker,
    onStatisticsChosen: (Boolean) -> Unit,
    incomingLink: String? = null,
    onIncomingLinkHandled: () -> Unit = {},
)
```

`App.kt` вызывает его с `clicks = graphs.clicks`, `onStatisticsChosen = graphs::onStatisticsChosen`. `MainNavHost` получает
`clicks: ClickTracker`, `AuthNavHost` — `onStatisticsChosen`.

## 4. Регистрация: чекбокс — владелец `compose-ui` (`feature/auth/ui`, `AuthNavHost`, строки, `:components` при необходимости)

- `RegisterUiState.shareStatistics: Boolean = true` (хранить в `SavedStateHandle` вместе с адресом, это не секрет);
  `RegisterViewModel.onShareStatisticsChange(Boolean)`. Строка под повтором пароля: чекбокс + подпись «Отправлять статистику
  использования», под ней мелким «Можно изменить в настройках» / "Send usage statistics", "You can change this in settings".
  Использовать существующий компонент выбора из `:components`, если нет — добавить `AppCheckbox`-строку в `:components` и в
  `:catalog`.
- `RegisterRoute(..., onLetterSent: (email: String, shareStatistics: Boolean) -> Unit, ...)`: значение уходит вместе с
  событием «письмо отправлено». `AuthNavHost(..., onStatisticsChosen: (Boolean) -> Unit)` вызывает `onStatisticsChosen(value)` в
  этом колбэке (до перехода на подтверждение).
- Чекбокс не блокирует регистрацию (его снятие — просто `false`).

## 5. Настройки: переключатель — владелец `domain-data` (domain, адаптер) и `compose-ui` (ui)

Домен `feature/settings/domain` (новые файлы, существующие не менять):

```kotlin
interface StatisticsSettings {
    /** Whether usage statistics are sent from this device; on until the person says otherwise. */
    val enabled: Flow<Boolean>
    suspend fun setEnabled(enabled: Boolean)
}
class ObserveStatisticsUseCase(private val settings: StatisticsSettings) { operator fun invoke(): Flow<Boolean> }
class SetStatisticsUseCase(private val settings: StatisticsSettings) { suspend operator fun invoke(enabled: Boolean): Result<Unit> }
```

Адаптер в `:shared` (владелец `domain-data`): `PreferenceStatisticsSettings(preference: StatisticsPreference) : StatisticsSettings`
(`enabled = preference.enabled.map { it ?: true }`, `setEnabled` = `preference.set`). `SettingsModule` собирает
`SettingsUiDependencies(getOpenToCollaboration, setOpenToCollaboration, observeStatistics, setStatistics)`.

UI `feature/settings/ui` (`compose-ui`): `SettingsViewModel(getOpenToCollaboration, setOpenToCollaboration, observeStatistics,
setStatistics)`; отдельный `statistics: StateFlow<Boolean>` (значение по умолчанию `true`, пока поток не ответил) и
`onStatisticsToggle(enabled: Boolean)` (сбой записи возвращает переключатель назад и показывает ту же подсказку об ошибке,
что у соседнего). Переключатель — свой раздел под «Приватность», виден в любом состоянии загрузки серверной настройки (она
локальная). Тексты (RU / EN):
- заголовок раздела «Статистика» / "Statistics";
- переключатель «Статистика использования» / "Usage statistics";
- подсказка «Помогает улучшать приложение: считаются нажатия на проекты и людей, идентификатор аккаунта и данные об устройстве.
  Сообщения и тексты профиля не передаются.» / "Helps improve the app: taps on projects and people, the account id and
  device data are counted. Messages and profile texts are not sent."

## 6. Android — владелец `domain-data` (папка `androidApp/`, `gradle/libs.versions.toml`)

- Библиотека `io.appmetrica.analytics:analytics` (в документации Яндекса на 2026-10-21 версия 8.6.0; проверить, что она
  скачивается, иначе взять последнюю доступную) — только в `:androidApp`; в `libs.versions.toml`: `appmetrica`, `appmetrica-analytics`.
- Ключ приложения: Gradle-свойство `-Pmatchlab.appmetricaKey=<ключ>` или строка в `local.properties`
  (`matchlab.appmetricaKey=…`), в `BuildConfig.APPMETRICA_API_KEY`; пустой — `NoOpAnalytics`. Ключ не коммитится.
- Класс `MatchLabApplication : Application` (в манифесте `android:name`): при непустом ключе активирует AppMetrica:
  `AppMetricaConfig.newConfigBuilder(key)` с `withDataSendingEnabled(false)` (до выбора ничего не уходит), без геолокации,
  `withAdvIdentifiersTracking(false)`, отчёты о сбоях выключены (`withCrashReporting(false)`; заказчик просил только клики и
  может включить позже), `withLogs()` в отладочной сборке. Класс `AppMetricaAnalytics(…) : Analytics`: `track` →
  `AppMetrica.reportEvent(name, params)` в `try/catch` и **только если отправка включена** (свой флаг, обновляется в
  `setEnabled`); `setUserProfileId` → `AppMetrica.setUserProfileID(id)`; `setEnabled` → `AppMetrica.setDataSendingEnabled`.
  Экземпляр один на процесс; `MainActivity` передаёт его в `App(analytics = …)`.
- Проверить имена методов по документации Яндекса (`https://appmetrica.yandex.com/docs/en/sdk/android/analytics/…`) и по самому SDK
  после подключения; если имя из контракта не существует, взять настоящее и сказать в отчёте.
- Манифест: разрешения, которые SDK добавляет сам, не дублировать; `INTERNET` уже есть.

## 7. iOS

Папку `iosApp/` агенты не правят. Общий код получает `NoOpAnalytics` по умолчанию, поэтому iOS собирается без изменений.
`domain-data` даёт в отчёте текст для документа: Swift-класс над `AppMetrica.reportEvent(name:parameters:onFailure:)`, активацию
в `didFinishLaunchingWithOptions` (с отключённой отправкой), пакет `AppMetricaCore` без `AppMetricaAdSupport`, передачу в
`App(…, analytics:)`.

## 8. Тесты (пишет `tester`, остальные только механические правки)

`ClickTracker` (имена, параметры, пустой id), `StatisticsPreference` (чтение, запись, переживает пересоздание, сбой записи),
`AnalyticsController` (отправка выключена до выбора; вход без выбора пишет `true`; выбор `false` не затирается; профиль
ставится и снимается; вход до `load()` не принимает решения), адаптер настроек, `SettingsViewModel` (переключатель, откат при сбое),
`RegisterViewModel` (чекбокс по умолчанию включён, значение попадает в колбэк, переживает пересоздание), граф DI (`App` без
аналитики получает пустую). Правки, нужные для тестируемости, — минимальные, с пометкой в отчёте.
