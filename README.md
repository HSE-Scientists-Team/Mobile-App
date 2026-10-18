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

### Режимы приложения: сервер или мок

Приложение работает в одном из двух режимов (`Backend` в `:shared`), на процесс:

- **Gateway** (по умолчанию): вход, регистрация и проверка сессии идут на сервер MatchLab Gateway (`docs/openapi.yaml`). Остальные
  экраны пока отдают моки. iOS всегда запускается в этом режиме.
- **Mock**: всё работает в памяти, сеть не нужна. Нужен, чтобы смотреть экраны: в режиме Gateway при недоступном сервере видны только
  экраны ошибок. Зарегистрироваться можно любой почтой доверенного домена (например, `student@hse.ru`, список — в
  `:mockserver`), ссылка из письма — `123456`.

Режим Android выбирается при сборке Gradle-свойствами:

```
./gradlew :androidApp:assembleDebug                                   # Gateway, продовый адрес
./gradlew :androidApp:assembleDebug -Pmatchlab.backend=mock           # мок
./gradlew :androidApp:assembleDebug -Pmatchlab.gatewayUrl=http://10.0.2.2:8080   # свой Gateway, например Docker Compose
                                                                      # бэкенда на машине разработки (адрес хоста из эмулятора)
```

`matchlab.backend` — `gateway` или `mock`, `matchlab.gatewayUrl` — адрес с `http://` или `https://` (пусто — продовый сервер). Значения
попадают в `BuildConfig` (`MOCK_BACKEND`, `GATEWAY_URL`); неверное значение останавливает сборку. Открытый HTTP разрешён только в
отладочной сборке и только для `10.0.2.2` и `localhost`; отладочная сборка пишет в лог метод, адрес и статус вызовов (без тел и токенов).
Превью работает на моке, каталог компонентов от режима не зависит (он не использует `App`).

### Running tests

Use the run button in your IDE's editor gutter, or run tests using Gradle tasks:

- Android tests: `./gradlew :shared:testAndroidHostTest`
- iOS tests: `./gradlew :shared:iosSimulatorArm64Test`

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)…