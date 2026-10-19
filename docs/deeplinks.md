# Ссылки, которые открывают приложение

Статус на 2026-10-20. Закрывает G01 (формат письма) и добавляет подстановку по ссылке.

## Ссылка из письма

Формат подтверждён кодом бэкенда (`matchlab-backend`, `cmd/mail`, `internal/mail/smtp`): `smtp.verification_url` плюс параметр `token`.
Боевая ссылка: `https://matchlab.space/verify-email?token=<токен>`; токен — base64url, живёт 30 минут, тратится один раз. Страница по ссылке
должна взять `token` и отправить его в `POST /api/v1/auth/email/confirm`; приложение делает то же самое, когда ссылку открывает система.

## Как приложение ведёт себя

| Состояние | Что происходит |
|---|---|
| Пользователь не вошёл, приложение помнит пароль регистрации (она сделана в этом запуске) | Экран «подтверждаем», запрос сразу; при успехе вход выполняется сам, открываются вкладки |
| Пользователь не вошёл, пароль не помнится (процесс убивали) | Запрос сразу; при успехе экран входа с пометкой «Почта подтверждена. Войдите» и адресом, который пользователь вводил последним |
| Ссылка недействительна, использована или просрочена, домен не разрешён | Экран с причиной и кнопкой «Вернуться ко входу» |
| Сбой сети или сервера | Экран с причиной и кнопкой «Повторить» (токен не потрачен) |
| Пользователь уже вошёл | Ссылка отбрасывается, ничего не тратится |
| Идёт чтение сохранённой сессии | Ссылка ждёт, пока станет известно, вошёл ли пользователь |
| Любая другая ссылка (чужой хост, `http`, другой путь, нет токена) | Отбрасывается (`LetterLinks.tokenOf`) |

Пересоздание activity (поворот, смерть процесса) не обрабатывает ссылку второй раз: `MainActivity` берёт `intent.data` только при `savedInstanceState == null`.

## Android App Links

В `AndroidManifest.xml` у `MainActivity` фильтр `https://matchlab.space/verify-email*` с `autoVerify="true"`, `launchMode="singleTask"` (ссылка при открытом
приложении приходит в `onNewIntent`).

Чтобы система открывала приложение сразу, без выбора, на домене нужен файл `https://matchlab.space/.well-known/assetlinks.json` (отдаётся по HTTPS,
без редиректа, `Content-Type: application/json`):

```json
[{
  "relation": ["delegate_permission/common.handle_all_urls"],
  "target": {
    "namespace": "android_app",
    "package_name": "org.matchlab.academic_net",
    "sha256_cert_fingerprints": [
      "00:2D:35:39:2C:DB:2F:E0:13:C4:46:8E:0F:DE:0A:AB:DE:FE:47:7E:F6:B8:9F:3C:07:4D:9C:90:98:A4:6D:37"
    ]
  }
}]
```

Отпечаток выше — **отладочный** ключ (`~/.android/debug.keystore` на машине разработчика). Для релиза добавьте SHA-256 ключа подписи релиза (и ключа
Play App Signing, если публикация через Google Play). Без файла ссылка всё равно откроется — в браузере или через выбор приложения. С машины разработки
домен `matchlab.space` в этой среде не открывался, наличие файла не проверено.

Проверка на устройстве (запрещена в этом проекте, делает пользователь): `adb shell pm get-app-links org.matchlab.academic_net`, затем
`adb shell am start -a android.intent.action.VIEW -d "https://matchlab.space/verify-email?token=…"`.

## iOS

Общая часть готова: `App(…, incomingLink, onIncomingLinkHandled)` принимает ссылку и обрабатывает её так же. Подключение — в `iosApp/` (эту папку агенты не
правят): Associated Domains `applinks:matchlab.space` в entitlements, файл `https://matchlab.space/.well-known/apple-app-site-association` на домене с
`appIDs` вида `<TeamID>.<bundle id>` и путями `/verify-email*`, а в SwiftUI-обёртке — `.onOpenURL { url in link = url.absoluteString }` и передача `link` в
`App`.
