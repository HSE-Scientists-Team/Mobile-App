# Подключение к MatchLab Gateway: архитектура

Статус на 2026-10-18: **реализованы шаги 5 (`:gateway`) и 6 (вход по почте и паролю, граф сессии) и блоки 2 (профили, отзывы, правка работ и документов, настройки), 3 (поиск людей, лента) и «Проекты, сторона чтения» (поиск проектов, страница проекта, добавление роли; блоки 4–5 объединены) — по планам `docs/gateway-block2-profiles.md`, `docs/gateway-blocks3-5-search-and-projects.md` с поправкой «без догадок по API»: где планы предлагают «лучшее предположение» (значения `type`/`status`/`action`/`mine`/`duration`, формат `planned_*`, `GatewayVocabulary`), действует `docs/api-gaps.md`. Создание проекта, «Мои проекты» и «Снять с публикации» отключены пробелами G40–G44.** Межслойные контракты блоков — `docs/contract-b3.md`, `docs/contract-p45.md`. Контракт API —
`docs/openapi.yaml` (Gateway API v2.5.0). Список правок интерфейса и багов — `docs/plan-ui-and-backend.md`.

Живой сервер с машины разработки недоступен (TLS зависает), поэтому всё сверяется с примерами спецификации через
`ktor-client-mock`; приёмка на устройстве — за пользователем.

## Поправки к проекту после списка правок пользователя

- **Настройки.** Пользователь оставляет в настройках только «Открыт к сотрудничеству». Это поле **профиля**
  (`PUT /users/me/profile`), а не `/users/me/settings`. Поэтому `GatewaySettingsDataSource` из шага 1 (ниже, C6) не
  делается: настройки остаются на моке, пока не появится `ProfilesApi` (блок 2), и тогда переходят на новый тумблер.
- **Профили.** `AchievementsApi` расширяется до `PUT`, `DELETE` и `submit` (правка и удаление работ и документов);
  `getReview(userId, reviewId)` и страничная загрузка отзывов входят в блок 2.
- **Экран 10b** получает поля «Имя*», вуз, уровень и курс, направление: у нового аккаунта нет имени, а `display_name`
  задаётся только через `PUT /users/me/profile`. Нужны макеты (Figma).
- **Поле пароля** `AppPasswordField` и макет `Input/Password`.

## 1. Сквозные решения

### Модули и зависимости

```
:common ←(api)── :gateway        Ktor, serialization, DTO, API, ошибки, маппинг в модели :common
feature:*:domain → :common, :utils          о :gateway не знает
feature:*:data   → domain, :gateway (+ :mockserver для моков)
:shared          → всё + :gateway           DI и навигация
```

- DTO и API лежат в одном `:gateway`, а не в фичах: `Project` нужен трём фичам (projects, projectsearch,
  projectdetails), `Person` двум (collaborators, feed), `UserProfile` профилю, ленте и странице проекта.
- Маппинг DTO в общие модели `:common` (`Project`, `Collaborator`, `UserSummary`) живёт в `gateway.mapping`
  (появляется в блоках 3–5); модели одной фичи маппит её data.
- `:gateway` — инфраструктура, как `:utils` и `:securestore`: правила зависимостей CLAUDE.md соблюдаются.
- API группируются по потребителю, а не по тегам: тег «Профили» распадается на настройки, профили, достижения, отзывы.
  Каждый API — публичный интерфейс и `internal` реализация на Ktor, поэтому data фич тестируется фейками без Ktor.
- `:gateway`: android + iosArm64 + iosSimulatorArm64, `withHostTest {}`, плагин serialization только в нём.

### Граф DI, viewerId, переключение по AuthState

- **`AppComponent`** (`@AppScope`, весь процесс): `SecureStore`, `Backend`, `Gateway` (движок, публичный клиент,
  `AccountApi`), `AuthDataSource`, `AuthRepository`, `AuthUiDependencies`.
- **`SessionComponent`** (`@SessionScope`, одна сессия; параметры `@Component app: AppComponent`, `Session`): `GatewaySession`
  с токеном сессии, источники и репозитории всех фич, адаптеры, `SessionDependencies`. `close()` отменяет сессионный
  `CoroutineScope` и закрывает `GatewaySession`.
- `viewerId` приходит в конструкторы Gateway-источников из `session.userId`. Без сессии графа фич нет.
- `SignedIn(session)` → `AppGraph.open(session)` (для той же сессии тот же компонент: переживает поворот). `SignedOut` →
  очистка `stores.main` и `AppGraph.close()`: кэши, мок-чаты, корутины и клиент прежнего аккаунта уничтожаются
  (закрывает ограничение «Мок-данные между аккаунтами» из `sprint-1.md`).
- `DataSourcesModule` (бывший `MockDataSourcesModule`, все провайдеры `@SessionScope`) выбирает источники: подключённые фичи (профили,
  настройки, поиск людей, лента, проекты) берут `GatewaySession` и `session.userId` напрямую, без `Lazy` и без `when (backend)`;
  `when (backend)` остался у чатов (`MockChatsDataSource`, G50) и у входа (`AuthModule`, мок-вход). Поэтому в режиме Mock граф сессии не
  собирается: `Gateway` там — `IllegalStateException` (режим Mock удалит tester).
- Альтернатива: один граф, `ViewerIdProvider` в `:common` и ручной сброс кэшей в `signOut`. DI проще, но viewerId
  становится nullable, а забытый кэш покажет данные прежнего аккаунта.

### Токен и «401 → выход»

- Клиентов два, движок один. Публичный клиент (`Gateway.account`) обслуживает `/auth/*` и вызовы с явным токеном.
  Сессионный (`GatewaySession`) создаётся через `HttpClient.config {}` от публичного; движок передаётся экземпляром,
  закрытие клиента сессии его не закрывает.
- `GatewaySession` получает токен при создании и ставит `Authorization: Bearer` в каждый запрос.
- 401 на запросе сессии → `SessionRejectionListener.onSessionRejected(token)` → `AuthRepository.expireSession(token)`:
  если токен текущей сессии — стереть сессию и перейти в `SignedOut(sessionExpired = true)`; иначе (поздний ответ старой
  сессии) ничего. Вызов в `NonCancellable`.
- Не действует: 401 на `/auth/login` (неверный пароль); `GET` и `DELETE /sessions/current` идут через публичный клиент с
  явным токеном, их 401 обрабатывает репозиторий. 403 на `/sessions/current` обрабатывается как 401.

### Ошибки

- `:common/result/RemoteFailure` (sealed, видят domain и UI): `NoConnection`, `Timeout`, `Http(status, serverMessage)`,
  `Malformed`. Перевод в одном месте, в `:gateway`: не 2xx → `Http` (тело `{"error": …}` разбирается терпимо);
  таймауты → `Timeout`; `IOException`/`NSError` → `NoConnection`; ошибки JSON → `Malformed`; `CancellationException`
  и прочие исключения пробрасываются.
- Что значит код (404/400 на GET по id → «не найден», 409 → конфликт), решает data фичи.
- `failureText(cause)` в `:components`: нет связи / сервер не ответил / сервис недоступен (≥500) / что-то пошло не так.
  Текст `error` сервера пользователю не показывается.
- Тайм-ауты: подключение 10 с, запрос 20 с, отзыв токена при выходе 5 с. Автоповторов нет, на экранах «Повторить».

### Сериализация

- `Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true; coerceInputValues = true }`.
- Схема похожа на protojson: нули и пустые массивы могут не прийти или прийти `null`, поэтому у всех полей DTO есть
  дефолты. int64 (`created_at_unix`, `completed_at_unix`, `total`) читаются через `FlexibleLongSerializer` (строка или
  число; в Media числом). В запросах `null` не отправляется, обязательные поля отправляются всегда.

### «Не проверено на живом сервере»

Всё неопределённое лежит в пакете `org.matchlab.academic_net.gateway.contract` под `@UnverifiedContract`
(`@RequiresOptIn(level = WARNING)`): `ConfirmationLinks`, позже `GatewayVocabulary` (строки ↔ enum `:common`) и формат
`planned_*`. Чтение терпимое (регистр не важен, неизвестное значение → `null`, запись в лог в debug), писать неизвестные
значения на сервер нельзя — такие действия ждут ответа бэкенда. Таблица значений дублируется в `sprint-1.md`.

### Логи и секреты

- Плагин `Logging` в Ktor — только при `GatewayConfig.logCalls` (debug), `LogLevel.INFO`, `Authorization` маскируется;
  BODY и ALL никогда; тела `/auth/*` не логируются ни в каком режиме.
- `toString` маскирует `AccessToken`, `Password`, `Credentials`, `CredentialsDto`, `LoginResultDto`,
  `ConfirmEmailRequestDto`; `RemoteFailure.Http.serverMessage` не попадает в `message`.
- Найдено: `AppBasicTextField` хранит текст в `rememberSaveable`, то есть в Bundle, который система пишет на диск. Для
  пароля нужно отдельное поле без сохранения состояния (`AppPasswordField`). Пароль незавершённой регистрации живёт
  только в памяти репозитория.

### Адрес, режимы, платформы

- `GatewayConfig.PRODUCTION_BASE_URL = "https://api.matchlab.space"`, пути начинаются с `/api/v1`.
- `Backend` в `:shared`: `Mock` или `Gateway(baseUrl: String? = null, logCalls: Boolean = false)`.
- Android: Gradle-свойства `matchlab.backend=mock|gateway` (по умолчанию `gateway`) и `matchlab.gatewayUrl` → `BuildConfig`
  модуля `androidApp`; `logCalls = BuildConfig.DEBUG`. Для локального Docker Compose по желанию debug-only
  `network_security_config` с cleartext только для `10.0.2.2` и `localhost`. **В манифесте нет `INTERNET` — добавить.**
- Движки: `expect fun platformHttpEngine(config)` — OkHttp на Android, Darwin на iOS, `MockEngine` в тестах.
- iOS: `MainViewController()` не меняется (берёт `Backend.Gateway()`), `iosApp/` не трогаем.
- Режим mock остался только ради мок-входа и будет удалён: профилям, настройкам, поиску, ленте и проектам нужен сервер, граф сессии из мока
  не собирается; проверять UI запуском на эмуляторе запрещено пользователем.

### Версии (проверены на Maven Central 2026-10-17)

Ktor 3.6.0 (собран с Kotlin 2.3.x, тянет coroutines 1.11.0 и serialization-json 1.11.0; OkHttp 5.5.0 подходит нашему
minSdk 24; Darwin-артефакты для обеих iOS-архитектур есть), kotlinx-serialization-json 1.11.0 (1.12.0-RC не берём),
плагин serialization 2.4.20 (`version.ref = "kotlin"`). kotlin-inject 0.9.0 — последняя: дочерний компонент через
`@Component`-параметр с `@KmpComponentCreate` на iOS проверяется компиляцией в первом же шаге.

```toml
[versions]
ktor = "3.6.0"
kotlinx-serialization = "1.11.0"
[libraries]
ktor-client-core = { module = "io.ktor:ktor-client-core", version.ref = "ktor" }
ktor-client-logging = { module = "io.ktor:ktor-client-logging", version.ref = "ktor" }
ktor-client-okhttp = { module = "io.ktor:ktor-client-okhttp", version.ref = "ktor" }
ktor-client-darwin = { module = "io.ktor:ktor-client-darwin", version.ref = "ktor" }
ktor-client-mock = { module = "io.ktor:ktor-client-mock", version.ref = "ktor" }
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "kotlinx-serialization" }
[plugins]
kotlinSerialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
```

Все версии при реализации шага 5 подошли без правок (Ktor 3.6.0, OkHttp 5.5.0 как `okhttp-android`, kotlinx-io 0.9.1,
coroutines 1.11.0). Из каталога убраны `ktor-client-content-negotiation` и `ktor-serialization-kotlinx-json`: тела
кодируются и читаются вручную (см. «Реализация `:gateway`» ниже).

### Реализация `:gateway` (шаг 5): что вышло иначе или уточнилось

- **Тела без ContentNegotiation.** Запрос кодируется `Json.encodeToString`, ответ читается `Json.decodeFromString` в
  `ApiRequests` (то же `Json`, что в разделе «Сериализация»). Причина проверена опытом: исключение kotlinx.serialization
  цитирует разбираемый текст (`JSON input: {"user_id":…,"access_token":"…"}`), а плагин `Logging` печатает исключения
  конвейера даже на `LogLevel.INFO` (`RESPONSE … failed with exception: …`) — при битом ответе `login` токен попадал бы в
  лог. Вне конвейера Ktor исключение в лог не попадает, а в `RemoteFailure.Malformed` уходит его копия без цитаты.
- **Помощник запросов** называется `ApiRequests` (а не `SessionRequests`): один класс для публичного клиента и для клиента
  сессии. `GatewaySession.requests` (internal) — точка, на которой блоки 2–5 строят `KtorProfilesApi` и прочие:
  `get<T>`, `post<B, T>`, `postWithoutAnswer<B>`, `delete`, общий `call(...)` с `query`. `PUT` и прочее добавляются там же.
- **`:gateway` зависит от `:utils`** (`suspendRunCatching` для терпимого чтения тела ошибки), `:common` — `api`, Ktor core —
  `api` (тип `HttpClientEngine` входит в публичный API `Gateway`).
- **Таймаут сокета равен таймауту запроса** (`GatewayConfig` знает два числа). Движки получают те же значения: OkHttp —
  `connect/read/write`, Darwin — `timeoutIntervalForRequest/Resource`. Значения короче миллисекунды поднимаются до 1 мс,
  длиннее `Int.MAX_VALUE` мс (24 дня) и бесконечные становятся «без таймаута».
- **401 и слушатель.** `onSessionRejected(token)` вызывается один раз на каждый запрос сессии с ответом 401 (параллельные
  запросы дают столько же вызовов; `expireSession` идемпотентна по смыслу «токен текущей сессии»), внутри `NonCancellable`,
  до того как вызов бросит `Http(401)`. Если слушатель упал, его исключение добавляется к `Http(401)` как suppressed.
- **Отмена.** Вызов, у которого отменили корутину, всегда заканчивается `CancellationException`: Ktor «разворачивает» отмену
  с причиной в саму причину (`IOException` → ложный `NoConnection`), поэтому после любого сбоя проверяется `ensureActive()`.
  `GatewaySession.close()` делает `HttpClient.cancel()` и `close()` клиента сессии: идущие запросы получают
  `CancellationException` (чуть позже самого `close()`: Ktor отменяет их, когда клиент доработает), новые отменяются сразу.
  Движок и публичный клиент не затрагиваются — `HttpClient.config {}` от клиента, созданного по экземпляру движка, не
  управляет движком (`manageEngine = false`); это проверено тестом на `MockEngine` и на реальном OkHttp.
- **Логи.** `logCalls` пишет через `Logger.SIMPLE` (`println`: Logcat `System.out`, консоль Xcode); уровень `INFO`, `Authorization`
  маскируется на случай повышения уровня. Редиректы Ktor не отключены (ходит только GET/HEAD, `Authorization` Ktor снимает
  при смене хоста и не уходит с https на http).
- **Платформы.** `expect fun platformFailure(cause): RemoteFailure?`: на Android `null` (OkHttp бросает `IOException`),
  на iOS `DarwinHttpRequestException` → `NoConnection` (`NSURLErrorTimedOut` → `Timeout`). iOS проверен только компиляцией.
- **Тесты.** Кроме `ktor-client-mock`, в `androidHostTest` есть тесты реального OkHttp против сервера на `127.0.0.1`
  (отказ в соединении, обрыв, молчание → таймаут): наружу ничего не уходит.
- **OkHttp и `POST`.** Ktor включает `retryOnConnectionFailure(true)`: на протухшем соединении OkHttp может молча повторить
  `POST` (тело повторяемое). Для `register`/`login` последствия невелики (письмо уйдёт заново, появится ещё один токен), а
  для `POST /projects` и отправки достижений (блоки 2 и 4) возможен дубль: решение за этими блоками (например,
  `retryOnConnectionFailure(false)` в `platformHttpEngine`).

### Реализация `:shared` и `androidApp` (шаг 6c): что вышло иначе или уточнилось

- **Дочерний компонент kotlin-inject 0.9.0 работает и на iOS.** `@Component val app: AppComponent` в конструкторе `SessionComponent` и
  `@KmpComponentCreate expect fun createSessionComponent(app, session)` компилируются для `iosArm64` и `iosSimulatorArm64`, сгенерированный
  код совпадает с Android. Типы с областью родителя ребёнок берёт из `app._scoped` и из открытых свойств родителя (`app.authRepository`),
  второго `AuthRepository` не появляется. Запуск на iOS не проверялся (Xcode не установлен).
- **`GatewayFactory`** — третий параметр `createAppComponent` (по умолчанию движок платформы): через него тесты строят Gateway на
  `MockEngine`, и режим Gateway запускается без сети. В режиме Mock клиент не создаётся, а запрос его у графа — `IllegalStateException`,
  а не клиент, который звонил бы на продовый адрес.
- **`AppGraph.get(secureStore, backend)`** отдаёт `AppGraphs(app, sessions)` и запускает восстановление сессии в процессном
  `CoroutineScope` (`AppGraphs.start`), а не в `LaunchedEffect` корня: проверка токена на старте не обрывается поворотом экрана или
  пересозданием активности. `SessionGraphs` реализует `ComponentSessionGraphs` (тот же `Session` — тот же граф; другая сессия закрывает
  открытую; `close()` не делает ничего, если ничего не открыто).
- **Корень разложен на проверяемые функции:** `toRootScreen()` (`RootScreen.kt`: заставка, вход, вкладки — только по `AuthState`),
  `GraphViewModelStores.release(state, sessions)` (при `SignedIn` чистит ViewModel входа, при `SignedOut` сначала ViewModel вкладок, потом
  закрывает граф), `AuthFlow` (почта и флаг «почта подтверждена» между экранами входа, в `rememberSaveable`; пароля в нём нет), граф
  входа — `AuthNavHost.kt`, маршруты — `AuthRoutes`. Событие экрана, который пользователь уже покинул, не меняет `AuthFlow`.
- **Возврат с подтверждения на вход** — `navigateOnce(entry, auth/sign_in) { popUpTo(auth/sign_in) { inclusive = true } }`. В передаче 6b
  `popUpTo` был без `inclusive`: в стеке остались бы два экрана входа, старый под новым (по исходникам
  `NavControllerImpl.navigate` библиотеки: после `popUpTo` целевой экран без `launchSingleTop` добавляется ещё раз).
- **`SessionResources`** — `@Inject @SessionScope`: сессионный `CoroutineScope` и закрываемые ресурсы; `close()` отменяет scope, затем
  закрывает ресурсы в обратном порядке, сбой одного не мешает остальным (первый сбой бросается в конце), повторный вызов ничего не делает;
  ресурс, зарегистрированный после `close()`, закрывается сразу (поздний `Lazy<GatewaySession>` не оставляет клиента без хозяина).
  Потокобезопасен (атомарная ссылка), как и `InMemorySecureStore`.
- **Зритель.** Мок-вход сажает любого на `MockAccounts.viewer` (`user-me`); у остальных фич моков больше нет (мок-мир людей и проектов удалён),
  их Gateway-источники берут `session.userId`. `MockChatsDataSource(seed = backend == Backend.Mock)` — демо-чатов в режиме Gateway нет.
- **Android:** свойства `matchlab.backend` и `matchlab.gatewayUrl` проверяются при конфигурации сборки (неверное значение останавливает
  её с понятным сообщением), в `BuildConfig` идут `MOCK_BACKEND` и `GATEWAY_URL`; `network_security_config` с открытым HTTP только к
  `10.0.2.2` и `localhost` лежит в `src/debug`. `Backend.Gateway` проверяет адрес сразу при создании, а не при первом входе.

### Моки и `:mockserver`

Правило «заменил мок — удали мок»: когда фича переходит на API, её `Mock…DataSource`, мок-данные в `:mockserver` и тесты мока
удаляются в том же шаге. Моки профилей, настроек, поиска людей, ленты, проектов, поиска проектов и страницы проекта удалены; на
моках остались чаты (в API их нет, G50) и мок-вход `MockAuthDataSource` (`Backend.Mock`; его удаляет tester, вместе с
`Backend.Mock`). `:mockserver` хранит только аккаунт мок-входа (`MockAccounts`) и реестр доменов (`MockTrustedDomains`) и исчезнет
вместе с мок-входом. Тесты ViewModel подключённых фич идут на fake'ах из `commonTest`; `Mock…DataSource` нужны только тестам чатов и входа.
В режиме Gateway демо-чатов нет (`seed = false`).

### Тесты

- `:gateway` (commonTest, `ktor-client-mock`): для каждого метода — путь и метод; Bearer только у сессии и у явных вызовов
  `/sessions/current`, у `/auth/*` его нет; тело запроса в snake_case и без `null`; разбор фикстур из примеров
  спецификации; коды ответа → `Http(status, error)`, тело ошибки не в JSON; таймаут → `Timeout`, `IOException` →
  `NoConnection`, битый JSON → `Malformed`, отмена проходит насквозь; 401 сессии вызывает слушателя ровно один раз с её
  токеном, 401 логина — ни разу; `FlexibleLongSerializer`, `null` вместо массивов, `ConfirmationLinks`.
- data фич: фейки интерфейсов API без Ktor (перевод кодов в исходы домена, кэши, ревизии).
- ViewModel: против fake-репозиториев и fake-источников из `commonTest` своего модуля (`Mock…DataSource(latencyMillis = 0)` остались только у
  чатов и мок-входа).
- `:shared`: режим mock — регистрация → подтверждение → вход → граф сессии → выход (после повторного входа граф — другой
  экземпляр); восстановление новым компонентом на том же `InMemorySecureStore`; режим Gateway — граф собирается без сети.
- Kover: `kover(project(":gateway"))`; код в `iosMain` Kover не видит.

### Находки по спецификации

1. Вход до подтверждения почты отвечает **401**, а не 403 («в том числе до подтверждения регистрации»): «Я
   подтвердил(а)» трактует 401 как «ещё нет», 403 — как «не подтверждена или аккаунт отключён».
2. Восстановления пароля в API нет, повторная регистрация существующего адреса даёт 409 — поэтому при регистрации нужен
   повтор пароля. Длина пароля 8–72 **байта** UTF-8 (кириллическая буква — 2 байта).
3. GET-метода подтверждения нет: ссылка из письма, скорее всего, ведёт на веб-страницу, которая сама вызывает confirm.
   Основное действие — «Я подтвердил(а)», вставка ссылки — запасной путь; после confirm приложение входит само с
   паролем из памяти. `GET /users/me/email` в шаге 1 не нужен.
4. Демо-чаты в моке (`user-elena`, `user-maria`, `user-daniil`) на реальных данных ломаются (`GET /users/user-elena/profile`
   → 400): в режиме Gateway `seed = false`, а 400 на GET по id переводится в «не найден». `openConversationWith(peer)`
   получает собеседника целиком и по id в `:mockserver` не ищет, UUID ничего не ломают.
5. Сфера: отправляем **название** (`field = sphere.name`), при чтении `Sphere(id = field, name = field)`; 12 сфер — статический
   каталог клиента в `feature:projects:data`.
6. `ProjectRole` имеет `slots` и `occupied` (несколько мест), а в приложении «одна роль — один человек»; `POST /projects`
   требует `roles` (можно ли пустой массив — не проверено). У `UserAchievementList` и `UserReviewList` нет `total`; у
   `Person` нет `review_count` (рейтинг 0 = нет отзывов); у работ нет `source` (считается на клиенте, `linkSource()` из
   `profile/data/mock/WebLinks.kt` переезжает в `:utils/WebLink.kt`).
7. Ревизия проекта скрыта в data фичи (кэш хранит `revision` последнего ответа); в `:common` она появится вместе с
   редактированием (3-й спринт).
8. `Page<T>` и `PageRequest` в `:common` появляются в блоках 3 и 5; остальные списки — одним вызовом с `limit=100`.
9. `PUT /users/me/settings` заменяет настройки целиком, а контракт приложения — «патч одного поля»: очередь и последний ответ
   сервера (теперь неактуально, см. поправки).

## 2. Блоки спринта 1

Порядок: 1 → 2 → 3 → 4 → 5. Блок 1 первый: токен и граф сессии нужны всем, неопределённых значений в нём нет. Блок 2
раньше блока 3: без `display_name` поиск людей покажет людей без имени, и блок 2 вводит `UserDirectory` и маппинг
профиля. Блок 4 раньше блока 5: блок 5 переиспользует маппер `Project` и словарь значений, а в пустой базе нечего искать.
Блоки 4–5 и статусы проверки в блоке 2 ждут значений строк, которых нет в спецификации.

| Блок | Эндпоинты | Что меняется | Нет в API / потери | N+1 |
|---|---|---|---|---|
| **1. Регистрация по домену email + Защитить свои данные** | `POST /auth/register`, `/auth/email/confirm`, `/auth/login`; `GET\|DELETE /sessions/current` | auth: поток «почта + пароль → ссылка из письма → вход», `expireSession`; источник Gateway, мок переписан, 3 экрана; `:shared`: граф сессии | Нет кода из письма и лимитов (на клиенте пауза 60 с), нет восстановления пароля, deep link не делается; сессия может закончиться по 401 | — |
| **2. Оценивать других + Представить себя в профиле** | `GET /users/{id}/profile\|achievements?limit=100\|reviews`; `GET\|PUT /users/me/profile`; `GET\|POST /users/me/achievements`, `POST …/{id}/submit` (+ `PUT`, `DELETE` для правки) | `getReview(userId, reviewId)`; `Collaborator.studyInfo` → `Study(level, year)`; `ReviewerRole?`; добавление работы = создать + `submit` (исход «сохранено, но не отправлено»); профиль — 3 запроса параллельно; работы и документы — один список, делится по `kind`; `source` на клиенте; результаты проверки — из смены статусов при обновлениях (в памяти сессии), `markResultsSeen` локальный; `PUT` профиля — полная замена от свежего `GET`; `UserDirectory`; в 10b поля «Имя*», вуз, уровень и курс, направление; «Загрузить подтверждение» на 11 скрыт до загрузки файлов | Нет: GET отзыва по id; `total` работ (≤100); времени решения модерации; push (точка появляется после обновления: старт, возврат в приложение, вход на вкладку); признака верификации автора отзыва; приватного подтверждения файлом. Причина отказа — `moderation_comment` | Нет (3 запроса параллельно) |
| **3. Собрать команду (08, 08b, 01, 01c)** | `GET /search/people`, `GET /recommendations/people?limit=10`, `GET /users/me/profile` (шапка ленты) | `Page<T>`, `PageRequest` в `:common`; `ScoredCollaborator.matchPercent` убрать; `q` как введено; `verified_only`, `verified_projects`, `publications`; `project_type` — только если выбран один тип; `university`; ключевые слова → `skill[]`; догрузка при прокрутке, «Найдено N» из `total` | Роль в поиск не передаётся: ни совпадения, ни порядка по роли (строка поиска, как и раньше, заполняется навыками роли). Рекомендации — случайные люди с `open_to_collaboration`: блок ленты может быть пустым, пока никто не включил флаг. `my_university`, `open_only` в API есть, в UI нет | Нет |
| **4. Управлять проектом (05, 06, 07, 07b, 07c)** | `POST /projects`, `GET /projects?mine=…&limit=100`, `GET /projects/{id}`, `POST /projects/{id}/roles`, `POST /projects/{id}/transitions` | `ProjectStatus` получает статусы сервера (начат, завершён, отменён); `Role` получает `slots` и `members` (создаём `slots = 1`); `ProjectConflictException` для 409; кэш проектов сессии кормит `observe*` и хранит `revision`; при 409 обновить и повторить один раз; `viewerIsInitiator = owner_user_id == viewerId`; сфера ↔ `field`; срок ↔ `planned_*`; роль с `slots > 1` — одна карточка «N из M мест» | Описание роли в форме не вводится; «Опубликовать снова» в API есть, в карте стори нет | Инициатор — сам пользователь; участники — по профилю (до 2-го спринта их нет) |
| **5. Найти подходящий проект (02, 03) + Оценить проект (04)** | `GET /search/projects`, `GET /projects/{id}`, `GET /users/{owner}/profile` | `Page<FoundProject>`; срок в 03 — одиночный выбор; `q`; `type` если выбран один; `field = sphere.name`; `duration`; ключевые слова → `tag[]`; «только набор» → `status`; «без своих» → `mine`; маппер `Project` из блока 4; `InitiatorInfo` из профиля (`rating = null`, если `review_count == 0`); 400/404 → «не найден»; догрузка при прокрутке | Ключевые слова, похоже, ищутся только по тегам; правило «термины через запятую» и порядок остаются на сервере и не проверены; два срока сразу выбрать нельзя | Инициаторы страницы через `UserDirectory.profiles(ids)`: без повторов, ≤4 параллельно, TTL 5 мин → 1 + K запросов. Просьба к бэкенду: встроить сводку владельца в `Project` или сделать `GET /users?ids=` |

```kotlin
// появляется в блоке 2, в GatewaySession
class UserDirectory internal constructor(/* ProfilesApi, TimeSource */) {
    suspend fun profile(id: String): UserProfileDto?                            // 404 → null; один запрос на id; TTL 5 мин
    suspend fun profiles(ids: Collection<String>): Map<String, UserProfileDto?> // без повторов, не более 4 параллельно
    fun invalidate(id: String)                                                  // после PUT /users/me/profile
}
```

### Значения, которых нет в спецификации

| Значение или формат | Блокирует | Что делаем до ответа |
|---|---|---|
| `Project.type`, `project_types`, параметры `type` / `project_type` | 4 (создание), 2 (чипы интересов), фильтр типа в 3 и 5 | Читаем, предполагая `scientific`/`project` (по аналогии с `kind`); запись ждёт ответа |
| `Project.status`, параметр `status` | 4, 5 | Неизвестный статус при чтении → проект без действий управления |
| `transitions.action` для «снять с публикации» | 4 | — |
| Формат `planned_start` / `planned_end` | 4 (создание) | При чтении терпим `YYYY-MM`, `YYYY-MM-DD`, RFC 3339 |
| `mine` | 4 («Мои проекты»), 5 | В 5 свои проекты исключаем на клиенте (`total` может быть больше на их число) |
| `duration` | Фильтр срока в 5 | Фильтр скрыт |
| `UserAchievement.status` | 2 (статусы, очередь, результаты проверки) | Неизвестный → «не подтверждено», очередь пустая |
| `study_level`, `author_role` | Отображение в 2–3 | Неизвестное значение не показываем |
| Ответ `GET /users/me/profile` для нового аккаунта | 2 | 404 → пустой профиль, первый `PUT` его создаёт |
| Формат ссылки в письме и куда она ведёт | Не блокирует блок 1 | Принимаем ссылку с `token=`, последний сегмент пути или сам токен |

## 3. Шаг 1: фундамент, авторизация, выход

### Публичный API `:gateway`

```kotlin
package org.matchlab.academic_net.gateway

data class GatewayConfig(
    val baseUrl: String = PRODUCTION_BASE_URL,
    val connectTimeout: Duration = 10.seconds,
    val requestTimeout: Duration = 20.seconds,
    /** Debug builds only: method, URL and status; never bodies, Authorization masked. */
    val logCalls: Boolean = false,
) { companion object { const val PRODUCTION_BASE_URL = "https://api.matchlab.space" } }

/** OkHttp on Android, NSURLSession (Darwin) on iOS. */
expect fun platformHttpEngine(config: GatewayConfig): HttpClientEngine

/** Told when the server refused the token of a session: a 401 on a call made for that session. */
fun interface SessionRejectionListener { suspend fun onSessionRejected(accessToken: String) }

/** One engine, JSON, timeouts and error mapping for every call. */
class Gateway(config: GatewayConfig, engine: HttpClientEngine = platformHttpEngine(config)) {
    /** Calls not made for a session; the session check and sign-out take their token explicitly. */
    val account: AccountApi
    /** Calls of a signed-in user: [accessToken] goes into every request, a 401 goes to [onRejected]. */
    fun openSession(accessToken: String, onRejected: SessionRejectionListener): GatewaySession
}

/** The calls of one session; closing cancels its requests, the shared engine stays open. */
class GatewaySession internal constructor(/* client, token, listener */) : AutoCloseable {
    // block 2: profiles, achievements, reviews, users (UserDirectory); block 3: people, recommendations;
    // blocks 4–5: projects, projectSearch
    override fun close()
}

/** Every call throws RemoteFailure on failure; CancellationException passes through. */
interface AccountApi {
    suspend fun register(credentials: CredentialsDto): RegistrationResultDto   // POST /api/v1/auth/register → 201
    suspend fun confirmEmail(request: ConfirmEmailRequestDto)                 // POST /api/v1/auth/email/confirm → 204
    suspend fun login(credentials: CredentialsDto): LoginResultDto            // POST /api/v1/auth/login → 200
    suspend fun currentSession(accessToken: String): UserIdDto                // GET /api/v1/sessions/current
    suspend fun revokeSession(accessToken: String)                            // DELETE /api/v1/sessions/current → 204
}

// dto/
@Serializable data class CredentialsDto(val email: String, val password: String)            // toString masked
@Serializable data class RegistrationResultDto(val status: String = "")
@Serializable data class ConfirmEmailRequestDto(val token: String)                          // toString masked
@Serializable data class LoginResultDto(@SerialName("user_id") val userId: String,
                                        @SerialName("access_token") val accessToken: String)  // toString masked
@Serializable data class UserIdDto(@SerialName("user_id") val userId: String)

// contract/
@RequiresOptIn(level = RequiresOptIn.Level.WARNING,
    message = "Not checked against the live server: the OpenAPI spec does not define this value")
@Retention(AnnotationRetention.BINARY)
annotation class UnverifiedContract

@UnverifiedContract
object ConfirmationLinks {
    /** The `token` query parameter of a link, else its last path segment, else the bare token; `null` if none. */
    fun extractToken(input: String): String?
}
```

`SettingsApi` (`GET`/`PUT /users/me/settings`) в шаге 1 не нужен — см. поправки.

### `:common`

```kotlin
// common/result/RemoteFailure.kt
sealed class RemoteFailure(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class NoConnection(cause: Throwable? = null) : RemoteFailure("No connection to the server", cause)
    class Timeout(cause: Throwable? = null) : RemoteFailure("The server did not answer in time", cause)
    /** [serverMessage] is the `{"error": …}` text, for diagnostics only: never shown, not in [message]. */
    class Http(val status: Int, val serverMessage: String? = null) : RemoteFailure("HTTP $status")
    class Malformed(cause: Throwable? = null) : RemoteFailure("Unexpected answer from the server", cause)
}
```

### `feature:auth:domain`

```kotlin
@JvmInline value class AccessToken(val value: String)                    // unchanged
@JvmInline value class Password(val value: String) { override fun toString() = "Password(***)" }
data class Credentials(val email: String, val password: Password) { override fun toString() = "Credentials(***)" }
data class Session(val userId: UserId, val email: String, val accessToken: AccessToken)  // unchanged

sealed interface AuthState {
    data object Restoring : AuthState
    /** [sessionExpired]: the server stopped accepting the session; the sign-in screen says so. */
    data class SignedOut(val sessionExpired: Boolean = false) : AuthState
    data class SignedIn(val session: Session) : AuthState
}

/** [hasPassword]: the registration's password is still in memory (lost with the process). */
data class PendingRegistration(val email: String, val resendAfter: Duration, val hasPassword: Boolean)

sealed interface RegistrationOutcome {
    data class LetterSent(val pending: PendingRegistration) : RegistrationOutcome   // 201
    data object DomainNotAllowed : RegistrationOutcome                               // 403
    data object AlreadyRegistered : RegistrationOutcome                              // 409
}
sealed interface ConfirmationOutcome {
    data class Confirmed(val signedIn: Boolean) : ConfirmationOutcome   // 204 or 409; signedIn: auto sign-in worked
    data object InvalidLink : ConfirmationOutcome                        // no token in the text, 400, 404
    data object LinkExpired : ConfirmationOutcome                        // 410
    data object DomainNotAllowed : ConfirmationOutcome                   // 403
}
sealed interface SignInOutcome {
    data object SignedIn : SignInOutcome
    data object WrongCredentials : SignInOutcome     // 401 from the sign-in form
    data object NotConfirmedYet : SignInOutcome      // 401 for the address waiting for confirmation
    data object AccountUnavailable : SignInOutcome   // 403
}

enum class CredentialsError { EmailEmpty, EmailMalformed, PasswordEmpty, PasswordTooShort, PasswordTooLong, PasswordsDiffer }
enum class ConfirmationInputError { Empty }
// EmailRules — unchanged.
object PasswordRules {
    const val MinBytes = 8
    const val MaxBytes = 72
    /** Counts UTF-8 bytes; the password is never trimmed. */
    fun validate(password: String): CredentialsError?
}

interface AuthRepository {
    val authState: StateFlow<AuthState>
    suspend fun restoreSession()
    suspend fun register(credentials: Credentials): RegistrationOutcome
    fun pendingRegistration(email: String): PendingRegistration?
    /** `null`: no password in memory. */
    suspend fun resendLetter(email: String): RegistrationOutcome?
    /** [email] picks the remembered credentials for the automatic sign-in after confirmation. */
    suspend fun confirmEmail(email: String, linkOrToken: String): ConfirmationOutcome
    suspend fun signIn(credentials: Credentials): SignInOutcome
    /** "I've confirmed": `null` when no password in memory. */
    suspend fun signInPending(email: String): SignInOutcome?
    /** The server rejected [token]: signs out only if it is the current session's. */
    suspend fun expireSession(token: AccessToken)
    suspend fun signOut()
}

// use cases (ObserveAuthState, RestoreSession, SignOut — unchanged)
class SignInUseCase(r) { suspend operator fun invoke(email: String, password: String): SubmitResult<SignInOutcome, CredentialsError> }
class RegisterUseCase(r) { suspend operator fun invoke(email: String, password: String, repeatedPassword: String): SubmitResult<RegistrationOutcome, CredentialsError> }
class ConfirmEmailUseCase(r) { suspend operator fun invoke(email: String, linkOrToken: String): SubmitResult<ConfirmationOutcome, ConfirmationInputError> }
class SignInPendingUseCase(r) { suspend operator fun invoke(email: String): Result<SignInOutcome?> }
class ResendLetterUseCase(r) { suspend operator fun invoke(email: String): Result<RegistrationOutcome?> }
class GetPendingRegistrationUseCase(r) { operator fun invoke(email: String): PendingRegistration? }
```

### `feature:auth:data`

```kotlin
interface AuthDataSource {
    suspend fun register(credentials: Credentials): RegisterReply
    suspend fun confirmEmail(linkOrToken: String): ConfirmReply
    suspend fun signIn(credentials: Credentials): SignInReply
    suspend fun checkSession(token: AccessToken): SessionCheck
    suspend fun revoke(token: AccessToken)
}
sealed interface RegisterReply { data object LetterSent; data object DomainNotAllowed; data object AlreadyRegistered }
sealed interface ConfirmReply { data object Confirmed; data object AlreadyConfirmed; data object InvalidLink; data object Expired; data object DomainNotAllowed }
sealed interface SignInReply { data class Success(val session: Session); data object Unauthorized; data object Forbidden }
sealed interface SessionCheck { data class Valid(val userId: UserId); data object Rejected }

class GatewayAuthDataSource(private val api: AccountApi) : AuthDataSource
class MockAuthDataSource(latencyMillis: Long = …, timeSource: TimeSource = …, trustedDomains: Set<String> = MockTrustedDomains.domains) : AuthDataSource
class AuthRepositoryImpl(source: AuthDataSource, storage: SessionStorage, io: CoroutineDispatcher,
    timeSource: TimeSource = TimeSource.Monotonic, resendInterval: Duration = 60.seconds, revokeTimeout: Duration = 5.seconds) : AuthRepository
```

| Вызов | Ответ → результат |
|---|---|
| register | 201 → `LetterSent`, 403 → `DomainNotAllowed`, 409 → `AlreadyRegistered` |
| confirmEmail | токена в тексте нет → `InvalidLink` без запроса; 204 → `Confirmed`; 409 → `AlreadyConfirmed`; 400/404 → `InvalidLink`; 410 → `Expired`; 403 → `DomainNotAllowed` |
| signIn | 200 → `Success(Session(UserId(user_id), email, AccessToken))`, 401 → `Unauthorized`, 403 → `Forbidden` |
| checkSession | 200 → `Valid`, 401/403 → `Rejected` |
| revoke | 204 и 401 — успех |

Любой другой код или сбой сети пробрасывается как `RemoteFailure`.

Поведение репозитория:

- `restoreSession()`: сессии нет → `SignedOut()`; есть → сразу `SignedIn` (без ожидания сети), затем в том же вызове
  `checkSession`: `Rejected` или чужой `userId` → `expireSession`; `RemoteFailure` → сессия остаётся (запуск без сети),
  первый же 401 её закончит. Альтернатива: держать заставку до 3 с и восстанавливать оптимистично по таймауту.
- `register`: при `LetterSent` запоминает учётные данные и время отправки только в памяти; повтор с тем же адресом заменяет
  пароль в памяти.
- `resendLetter`: нет пароля → `null`; иначе повторная регистрация (старая ссылка перестаёт действовать); ответ
  `AlreadyRegistered` значит «почта уже подтверждена» → ViewModel вызывает `signInPending`.
- `confirmEmail`: при `Confirmed`/`AlreadyConfirmed`, если пароль этой почты есть в памяти, выполняет `signIn` и
  возвращает `Confirmed(signedIn)`.
- `signIn`: при успехе в `NonCancellable` сохраняет сессию, забывает регистрацию, ставит `SignedIn`. `Unauthorized` →
  `WrongCredentials`, `Forbidden` → `AccountUnavailable`. `signInPending`: то же, но `Unauthorized` → `NotConfirmedYet`.
- `expireSession`: только если токен совпадает с токеном текущей сессии: стирает сессию (с существующими повторами),
  забывает регистрацию, ставит `SignedOut(sessionExpired = true)`; сервер не вызывает.
- `signOut`: как сейчас, плюс забывает регистрацию; `revoke` ограничен `withTimeoutOrNull(revokeTimeout)`.

Контракт мока: реестр доменов `MockTrustedDomains` (поддомены проходят), остальные → 403; адрес с аккаунтом → 409; заявка
хранит почту, пароль и время (повтор заменяет пароль); токен письма `MockLetterToken = "123456"` (принимается и ссылка с
ним), подтверждает последнюю заявку; заявка старше 30 минут → 410, неизвестный токен → 404; вход без аккаунта или с
неверным паролем → 401, в том числе до подтверждения; `userId = MockAccounts.viewer.id`; `checkSession` принимает любой
непустой токен (мок не помнит токены между запусками).

### `feature:auth:ui`

Экраны:

- **Вход** (`auth/sign_in`, стартовый): «Вход в MatchLab», «Почта вуза», «Пароль» (с «показать»), «Войти», «Создать аккаунт»,
  `SecurityNote`; при `sessionExpired` — «Сеанс завершён. Войдите снова».
- **Регистрация** (`auth/register`): «Почта вуза», «Пароль» («От 8 символов»), «Повторите пароль», «Зарегистрироваться»,
  «Уже есть аккаунт? Войти».
- **Подтвердите почту** (`auth/confirm`): «Отправили письмо на %1$s. Откройте ссылку из письма — она действует 30 минут»;
  основная кнопка «Я подтвердил(а) почту»; поле «Ссылка из письма» и «Подтвердить»; «Отправить письмо ещё раз» с
  обратным отсчётом; назад — «Изменить почту»; если пароля в памяти нет, «Я подтвердил(а)» ведёт на вход с подставленной
  почтой, «Отправить ещё раз» — на регистрацию с почтой.
- Успешный вход ничего не навигирует: корень меняет граф по `AuthState`. Почта между экранами — в `rememberSaveable` внутри
  `AuthNavHost`, пароль — никогда.

```kotlin
@Composable fun SignInRoute(dependencies: AuthUiDependencies, email: String, sessionExpired: Boolean,
    onRegisterClick: (email: String) -> Unit, modifier: Modifier = Modifier)
@Composable fun RegisterRoute(dependencies: AuthUiDependencies, email: String, onLetterSent: (email: String) -> Unit,
    onSignInClick: (email: String) -> Unit, onNavigateBack: () -> Unit, modifier: Modifier = Modifier)
@Composable fun ConfirmEmailRoute(dependencies: AuthUiDependencies, email: String, onNavigateBack: () -> Unit,
    onSignInRequired: (email: String) -> Unit, onRegisterAgain: (email: String) -> Unit, modifier: Modifier = Modifier)

class AuthUiDependencies(
    val observeAuthState: ObserveAuthStateUseCase, val restoreSession: RestoreSessionUseCase,
    val signIn: SignInUseCase, val register: RegisterUseCase, val confirmEmail: ConfirmEmailUseCase,
    val signInPending: SignInPendingUseCase, val resendLetter: ResendLetterUseCase,
    val getPendingRegistration: GetPendingRegistrationUseCase, val signOut: SignOutUseCase,
)
```

ViewModel: `SignInViewModel`, `RegisterViewModel`, `ConfirmEmailViewModel(email, …)`; в `toString` их состояний нет почты,
пароля и ссылки.

| Ответ | Что видит пользователь |
|---|---|
| register 403, confirm 403 | «Регистрация с почтой этой организации недоступна. Используйте почту вуза» |
| register 409 | «Аккаунт с этой почтой уже есть» и кнопка «Войти» |
| confirm 204 или 409 | Вход; если пароля нет — «Почта подтверждена. Войдите» |
| confirm 404, 400 или нераспознанный текст | «Ссылка не подходит или уже использована. Вставьте ссылку из последнего письма» |
| confirm 410 | «Ссылка устарела. Отправьте письмо ещё раз» |
| login 401 на форме входа | «Неверная почта или пароль» |
| login 401 после «Я подтвердил(а)» | «Почта ещё не подтверждена. Откройте ссылку из письма или вставьте её ниже» |
| login 403 | «Почта не подтверждена или аккаунт отключён» |
| ≥500 (при регистрации 503) | «Сервис временно недоступен» («Не удалось отправить письмо. Попробуйте позже») |
| `NoConnection` / `Timeout` | «Нет связи с сервером» / «Сервер не ответил» |

### `:components`

- `AppBasicTextField` получает `visualTransformation` и `saveState: Boolean = true` (при `false` — `remember` вместо
  `rememberSaveable`).
- Новый `AppPasswordField(value, onValueChange, label, modifier, placeholder = "", errorText = null, imeAction = ImeAction.Done,
  keyboardActions = KeyboardActions.Default)`: `saveState = false`, `KeyboardType.Password`, `autoCorrectEnabled = false`,
  переключатель Eye / EyeOff с описаниями «Показать пароль» / «Скрыть пароль».
- `AppIcons.Eye` (`ic_eye`; в дизайн-системе есть только EyeOff).
- `format/FailureText.kt`: `@Composable fun failureText(cause: Throwable?): String`, строки ru и en.
- Поле пароля в `:catalog`: обычное, с ошибкой, с видимым паролем.

### `:shared`

```kotlin
sealed interface Backend {
    data object Mock : Backend
    data class Gateway(val baseUrl: String? = null, val logCalls: Boolean = false) : Backend   // null → production
}
@Composable fun App(secureStore: () -> SecureStore, backend: Backend = Backend.Gateway())

@AppScope @Component
internal abstract class AppComponent(@get:Provides val secureStore: SecureStore, @get:Provides val backend: Backend)
    : GatewayModule, AuthModule { abstract val auth: AuthUiDependencies }
@KmpComponentCreate internal expect fun createAppComponent(secureStore: SecureStore, backend: Backend): AppComponent

@SessionScope @Component
internal abstract class SessionComponent(@Component val app: AppComponent, @get:Provides val session: Session)
    : SessionGatewayModule, DataSourcesModule, ProjectsModule, ChatsModule, CollaboratorsModule,
      ProjectSearchModule, ProjectDetailsModule, ProfileModule, FeedModule, SettingsModule {
    abstract val dependencies: SessionDependencies
    abstract val resources: SessionResources          // session CoroutineScope + closeables
    fun close() = resources.close()
}
@KmpComponentCreate internal expect fun createSessionComponent(app: AppComponent, session: Session): SessionComponent

// AuthModule (AppScope)
@Provides @AppScope fun authDataSource(backend: Backend, gateway: Lazy<Gateway>): AuthDataSource = when (backend) {
    Backend.Mock -> MockAuthDataSource()
    is Backend.Gateway -> GatewayAuthDataSource(gateway.value.account)
}
// SessionGatewayModule (SessionScope)
@Provides @SessionScope fun gatewaySession(gateway: Gateway, session: Session, auth: AuthRepository, resources: SessionResources): GatewaySession =
    gateway.openSession(session.accessToken.value) { rejected -> auth.expireSession(AccessToken(rejected)) }.also(resources::closeOnEnd)
// DataSourcesModule (was MockDataSourcesModule; all @SessionScope). A snapshot of step 6: the profile, settings, people search,
// feed and projects are on the Gateway now (no `when (backend)`), only the chats keep a mock; see di/DataSourcesModule.kt.
@Provides @SessionScope fun chatsDataSource(backend: Backend): ChatsDataSource = MockChatsDataSource(seed = backend == Backend.Mock)
@Provides @SessionScope fun profileDataSource(resources: SessionResources): ProfileDataSource = MockProfileDataSource(scope = resources.scope)

@Inject @SessionScope class SessionDependencies(/* projects, chats, collaborators, feed, projectSearch, projectDetails, profile, settings */)

internal interface SessionGraphs { fun open(session: Session): SessionDependencies; fun close() }
@Composable fun AppNavHost(auth: AuthUiDependencies, sessions: SessionGraphs)
```

Навигация: `SignedOut` → `stores.main.clear()`, затем `sessions.close()`; `SignedIn` → `key(state.session) { MainNavHost(remember(state.session) {
sessions.open(it) }, onSignOut) }`; в графе входа три маршрута `auth/sign_in`, `auth/register`, `auth/confirm`; маршруты
главного графа не меняются (UUID безопасны).

### Платформы

- `androidApp/src/main/AndroidManifest.xml`: `android.permission.INTERNET`.
- `androidApp/build.gradle.kts`: `buildFeatures.buildConfig = true`; `GATEWAY_URL` из `providers.gradleProperty("matchlab.gatewayUrl")`
  (по умолчанию пусто → продовый адрес); `MOCK_BACKEND` из `matchlab.backend`.
- `MainActivity.kt` передаёт `Backend` из `BuildConfig` и `logCalls = BuildConfig.DEBUG`; превью работает в `Backend.Mock`.
- iOS не меняется.

### Что удалить, переписать, добавить

- **Удалить:** `VerifyEmailScreen.kt`, `VerifyEmailViewModel.kt`, `VerifyEmailViewModelTest.kt` (`feature/auth/ui`);
  `VerificationCodeRules`, `CodeError`, `VerificationChallenge`, `CodeRequestOutcome`, `CodeVerificationOutcome`,
  `RequestEmailCodeUseCase`, `VerifyEmailCodeUseCase`, `GetLastChallengeUseCase`.
- **Переписать:** `feature/auth/domain/.../{AuthModels,AuthRules,AuthRepository}.kt`, `usecase/AuthUseCases.kt`;
  `feature/auth/data/.../{AuthDataSource,AuthRepositoryImpl}.kt`, `mock/MockAuthDataSource.kt`; в `feature/auth/ui`:
  `SignInScreen.kt`, `SignInViewModel.kt`, `AuthUiDependencies.kt`, оба `strings.xml`; в `shared`: `App.kt`,
  `AppDependencies.kt` → `SessionDependencies.kt`, `di/{AppComponent,AppGraph,AuthModule}.kt`,
  `di/MockDataSourcesModule.kt` → `di/DataSourcesModule.kt`, `ui/navigation/AppNavHost.kt`.
- **Тесты переписать:** `AuthRulesTest`, `AuthModelsTest`, `AuthUseCasesTest`, `TestFixtures`, `AuthRepositoryImplTest`,
  `MockAuthDataSourceTest`, `SignInViewModelTest`, `AuthTestHarness`, `shared/.../di/AppComponentTest.kt`.
- **Добавить:** модуль `gateway/`; `common/.../result/RemoteFailure.kt`; `GatewayAuthDataSource` с тестами;
  `RegisterScreen`/`RegisterViewModel`, `ConfirmEmailScreen`/`ConfirmEmailViewModel` с тестами; в `:shared`: `Backend.kt`,
  `di/{SessionComponent,SessionScope,SessionResources,GatewayModule,SessionGatewayModule}.kt`, тест графа сессии; поле
  пароля, иконка и `FailureText.kt` в `:components`.
- **Подключить:** `include(":gateway")`; алиас плагина и `kover(project(":gateway"))` в корневом `build.gradle.kts`;
  `implementation(project(":gateway"))` в `feature/auth/data` и `shared`.
- **Не меняются:** `SessionStorage` и его тест, `:securestore`.

### Допущения для `docs/sprint-1.md`

- Экранов входа, регистрации и подтверждения в Figma нет; они собираются из `AppTopBar`, `AppTextField`, `AppPasswordField`,
  `AppButton`, `SecurityNote` (после правок Figma — по макетам 19, 19b, 20).
- Вход — по почте и паролю; регистрация — почта, пароль, повтор пароля; пароль 8–72 байта UTF-8, пробелы не обрезаются;
  «Забыли пароль?» нет — в API нет восстановления.
- Клиент проверяет почту только на опечатки; разрешённые домены решает сервер (403).
- Почта подтверждается ссылкой из письма (30 минут); формат ссылки не проверен; deep link не делался.
- После подтверждения приложение входит само; пароль хранится только в памяти процесса, при убитом процессе открывается
  вход с подставленной почтой.
- «Я подтвердил(а)» — попытка войти; 401 в этот момент означает «почта ещё не подтверждена».
- «Отправить письмо ещё раз» повторяет регистрацию (старая ссылка перестаёт действовать); пауза 60 с на клиенте.
- Ошибки показываются по коду ответа; текст `error` сервера не показывается.
- У токена нет срока жизни и обновления; при запуске сессия восстанавливается сразу и проверяется в фоне; 401 на любом
  авторизованном запросе завершает сеанс («Сеанс завершён»); без сети сессия сохраняется.
- Выход завершает сессию на устройстве сразу; `DELETE /sessions/current` — без гарантии, с таймаутом 5 с; граф сессии
  уничтожается вместе с кэшами.
- В режиме бэкенда чаты локальные: сообщения никуда не уходят, демо-чатов нет.
- Режим mock включается `-Pmatchlab.backend=mock`. Ничего из этого не проверено на живом сервере.
- Английские тексты — наш перевод: «Sign in», «Password», «Repeat password», «Create account», «Confirm your email», «We sent
  a link to %1$s», «I've confirmed my email», «Link from the email», «Send the email again», «Your session has ended. Sign in
  again».

### Коммиты и риски

- **Коммит 1, «Add the gateway module»:** каталог версий, корневой `build.gradle.kts`, `settings.gradle.kts`, модуль
  `:gateway`, `RemoteFailure`, тесты. Новый код нигде не используется, коммит собирается сам.
- **Коммит 2, блоки «Регистрация по домену email» + «Защитить свои данные»:** `:components`, auth, `:shared`, `androidApp`,
  тесты, `CLAUDE.md`, `docs/sprint-1.md`. Изменения `:components` можно вынести в отдельный предыдущий коммит. Domain и data
  нельзя отделять от ui: ui и `:shared` используют удаляемые use case.
- Распределение: `domain-data` — коммит 1, затем auth domain/data; `compose-ui` — `:components` и auth ui (тесты ViewModel
  сначала на фейке `AuthRepository`, потом на `MockAuthDataSource(latencyMillis = 0)`); затем `:shared` и `androidApp`;
  `tester` — сборка Android, host-тесты, Kover, компиляция iOS с чистым `KONAN_DATA_DIR` (захватывает Darwin-движок),
  эмулятор в режиме `mock`.
- Риски: дочерний компонент kotlin-inject на iOS (проверить компиляцией сразу); перевод `NSError` на iOS только компилируется;
  `AuthState.SignedOut` становится классом, меняется `when` в `AppNavHost`; провайдеры, переезжающие в граф сессии, меняют
  `@AppScope` на `@SessionScope`; `LocalizationResourcesTest` требует совпадения ключей ru и en 1:1.

## 4. Блокирующие вопросы пользователю

1. **Значения строк и форматов** из таблицы выше (тип, статус и `action` проекта, `planned_*`, `mine`, `duration`, статус
   достижения, `project_types`): без них нельзя делать блоки 4–5 и статусы проверки в блоке 2. Нужен ответ бэкенда, его
   исходники или работающий сервер.
2. **Сервер нельзя проверить с машины разработки** (TLS зависает). Это блокирует приёмку, а не разработку. Нужен способ
   запустить реальный сервер: локальный Docker Compose бэкенда (в описании API он упоминается вместе с Mailpit на `:8025`)
   или другая сеть. Смоук-проверка: `GET /health`, регистрация, ссылка из Mailpit, вход, `GET`/`PUT` профиля, выход.
3. **Подтверждение документов.** Без загрузки файлов ссылку-подтверждение можно отправить только в публичное `url`, то есть
   ссылку увидят все, кто открывает профиль. Рекомендация: отдельный блок загрузки через Media (подписанный `PUT`,
   `is_public=false`, `evidence_file_id`) до формы документа; выбор файла на iOS проверить нельзя. Альтернатива: временно
   отправлять документы без подтверждения.
