This is a Kotlin Multiplatform project targeting Android, iOS.

* [/iosApp](./iosApp/iosApp) contains an iOS application. Even if you’re sharing your UI with Compose Multiplatform,
  you need this entry point for your iOS app. This is also where you should add SwiftUI code for your project.

* [/shared](./shared/src) is for code that will be shared across your Compose Multiplatform applications.
  It contains several subfolders:
  - [commonMain](./shared/src/commonMain/kotlin) is for code that’s common for all targets.
  - Other folders are for Kotlin code that will be compiled for only the platform indicated in the folder name.
    For example, if you want to use Apple’s CoreCrypto for the iOS part of your Kotlin app,
    the [iosMain](./shared/src/iosMain/kotlin) folder would be the right place for such calls.
    Similarly, if you want to edit the Desktop (JVM) specific part, the [jvmMain](./shared/src/jvmMain/kotlin)
    folder is the appropriate location.

### Running the apps

Use the run configurations provided by the run widget in your IDE's toolbar. You can also use these commands and options:

- Android app: `./gradlew :androidApp:assembleDebug`
- iOS app: open the [/iosApp](./iosApp) directory in Xcode and run it from there.

### Сервер

Приложение работает с сервером MatchLab Gateway (`docs/openapi.yaml`, `Backend.Gateway` в `:shared`): вход, регистрация и проверка сессии,
профили (работы, документы, отзывы), настройки, поиск людей, лента и проекты (поиск, страница проекта, создание, «Я инициатор», добавление роли,
«Снять с публикации») идут на сервер. Локальными остались только чаты: в API их нет (G50), сообщения никуда не уходят, чаты начинаются пустыми.
В API нет перечислений: значения полей, которые сервер только хранит (тип проекта, сфера, срок, типы проектов профиля), задаёт клиент
по дизайну и отправляет текстом (`scientific`, `recruiting`, `up_to_3_months`…); таблица значений — в `docs/api-values.md`, на сервере они не проверены. Отключено остаётся то, что сервер пишет сам:
статусы работ и документов и всё, что на них стоит (G12); реестр пробелов — `docs/api-gaps.md`.
Режим Mock удалён: смотреть экраны без сервера нельзя, а iOS всегда ходит на продовый адрес.

Адрес сервера для Android задаётся при сборке:

```
./gradlew :androidApp:assembleDebug                                   # продовый адрес
./gradlew :androidApp:assembleDebug -Pmatchlab.gatewayUrl=http://10.0.2.2:8080   # свой Gateway, например Docker Compose
                                                                      # бэкенда на машине разработки (адрес хоста из эмулятора)
```

`matchlab.gatewayUrl` — адрес с `http://` или `https://` (пусто — продовый сервер); неверное значение останавливает сборку. Значение
попадает в `BuildConfig.GATEWAY_URL`. Открытый HTTP разрешён только в отладочной сборке и только для `10.0.2.2` и `localhost`; отладочная сборка
пишет в лог метод, адрес и статус вызовов (без тел и токенов). Превью Android Studio запускает `App` с пустым хранилищем (виден только вход, запросов
нет), каталог компонентов от сервера не зависит (он не использует `App`). Ничего из этого не проверено на живом сервере и на устройстве (запрет
пользователя на запуск); iOS — только компиляция Kotlin.

### Running tests

Use the run button in your IDE's editor gutter, or run tests using Gradle tasks:

- Android tests: `./gradlew :shared:testAndroidHostTest`
- iOS tests: `./gradlew :shared:iosSimulatorArm64Test`

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)…

### Trivy security scanning

GitHub Actions → `Trivy security scan` runs on pull requests, pushes to `master`, Mondays, and manual dispatches. Findings are advisory: vulnerabilities do not block a release, but failed dependency resolution, an empty/incomplete scan, and scanner/report errors fail the workflow. Results appear in Security → Code scanning and Actions artifacts (14 days). Fork PRs retain artifacts without uploading SARIF to Security. No additional tokens, signing keys, analytics keys, or backend credentials are required.

The workflow uses Java 21 and Android API 37 to resolve Android debug/release runtime dependencies, including transitive dependencies from shared KMP modules and JVM unit-test variants when present. An isolated Gradle init script creates `.trivy-dependencies/gradle.lockfile` only for this scan; normal builds and version selection are unchanged. Trivy scans that native lockfile and repository configuration. A coverage check verifies that every resolved Maven coordinate appears in the Trivy report. The workflow does not compile/install an APK or call MatchLab APIs.

Coverage is Android Maven dependencies and supported configuration checks; this is not a Kotlin code audit, a complete iOS/native dependency scan, or Android Manifest security analysis. Gradle wrapper JAR scanning is excluded; setup-gradle validates the wrapper. Secret scanning is not enabled. Trivy is pinned to 0.76.0 and its action to the v0.36.0 commit, matching the other MatchLab repositories.

To generate the dependency inventory locally (Java 21 and Android SDK required):

```sh
bash ./gradlew --no-daemon --no-configuration-cache --max-workers=2 \
  --write-locks --init-script scripts/security/gradle-locks.init.gradle \
  :androidApp:writeSecurityDependencyLocks
trivy fs --scanners vuln,misconfig --include-dev-deps --list-all-pkgs \
  --skip-dirs .git,.gradle,.kotlin,.cache --skip-dirs '**/build' \
  --skip-files gradle/wrapper/gradle-wrapper.jar \
  --format json --output trivy-source.json .
python3 scripts/security/check-trivy-report.py trivy-source.json
```
