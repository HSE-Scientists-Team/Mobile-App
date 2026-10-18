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
профили (работы, документы, отзывы), настройки, поиск людей, лента и проекты (поиск, страница проекта, добавление роли) идут на сервер.
Локальными остались только чаты: в API их нет (G50), сообщения никуда не уходят, чаты начинаются пустыми. Что API не определяет (создание проекта,
«Мои проекты», «Снять с публикации», фильтры типа и сроков, статусы записей и проектов, …), отключено и описано в `docs/api-gaps.md`.
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