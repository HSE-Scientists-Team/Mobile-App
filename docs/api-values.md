# Значения, которые задаёт клиент (текст)

> **Обновление 2026-10-20.** Бэкенд описал значения в OpenAPI (matchlab-backend PR #16, `scripts/api/string-enums.json`, ещё не влит на момент
> сверки). Источник правды для статусов, типов, действий и сроков — он, а не таблицы ниже, где они расходятся: статусы проекта `active` и
> `completed` (были `started`, `finished`), статусы отклика `rejected` и `cancelled` (были `declined`, `withdrawn`), действие `unpublish` (было
> `pause`), сроки `up_to_3m`, `3_to_6m`, `over_6m`. Статусы достижений: `unverified`, `pending_review`, `verified`, `needs_evidence`; статус
> участника: `active`, `left`, `removed`; фильтр `mine`: `owner`, `participant`. Таблицы раздела 2 приведены в соответствие; раздел 6 устарел.

Статус на 2026-10-19. Решение пользователя: в Gateway API **нет перечислений**; поля, у которых в `docs/openapi.yaml` стоит просто
`type: string`, задаём мы, по дизайну. Первое решение было «числом», позднее изменено: **«Field и Type у проектов надо передавать текстом. То же
самое касается всех остальных enum значений»**. На сервер уходит и с сервера читается стабильный текст (не зависит от языка интерфейса). Всё, чего не
хватает в бэкенде, игнорируем: включён весь функционал, для которого есть ручки; неизвестные значения в ответах не мешают (читаются как «неизвестно» и не
прячут сами записи). Тестирует пользователь на реальном сервере; значения, которые сервер понимает иначе, правятся в одном месте кода.

Документ — контракт для `domain-data`, `compose-ui` и `tester`. Что в реестре `docs/api-gaps.md` закрыто этим решением, а что нет, см. раздел 6.

## 1. Правила

1. **Запрос.** Отправляется текст из таблицы раздела 2. Неизвестного значения не отправляем никогда.
2. **Ответ.** Значение читается точным совпадением после `trim()`, регистр не меняется. Всё остальное (пусто, другая строка) — `null`: «неизвестно». Из-за
   неизвестного значения запись не скрывается и не теряется, пропадает только то, что без значения не нарисовать (чип, подпись).
3. **Единое место.** Текст каждого значения живёт в одном месте (колонка «Где в коде»; свойство Kotlin по-прежнему называется `code`).
4. **Помечаем непроверенное.** Значения, которые сервер сам интерпретирует (`action`, `status`), на сервере не проверены: `ProjectActions` помечен
   `@UnverifiedContract` (он в `:gateway`), у `ProjectStatus` в `:common` пометка только в KDoc «не подтверждено бэкендом».

## 2. Таблицы значений

| Значение | Где в API | Текст (по дизайну) | Где в коде |
|---|---|---|---|
| Тип проекта | `Project.type` (в `ProjectCreate` обязателен), параметр `type` поиска; `project_types` профиля (массив); параметр `project_type` поиска людей | `scientific` «Научный», `practical` «Проектный» | `ProjectType.code`, `fromCode()` в `:common` |
| Статус проекта (только чтение) | `Project.status` | `recruiting` «Набор открыт», `unpublished` «Снят с публикации», `active` «В работе», `completed` «Завершён», `cancelled` «Отменён». Другие значения сервера — `null` | `ProjectStatus.code`, `fromCode()` в `:common` |
| Срок проекта (фильтр) | параметр `duration` поиска | `up_to_3m` «до 3 мес» (не более 92 дней), `3_to_6m` «3–6 мес» (92–183 дня), `over_6m` «6+ мес» (больше 183 дней) | `ProjectDuration.code` в `:feature:projectsearch:domain` |
| Сфера | `Project.field`, параметр `field` поиска | название сферы как есть: `Computer Science`, `Data Science`, `Инженерия`, `Математика`, `Физика`, `Химия`, `Биология`, `Медицина`, `Экономика`, `Социология`, `Лингвистика`, `Дизайн` | `Spheres` в `:common` (`Sphere.id` = название) |
| Действие перехода | `ProjectTransitionRequest.action` | `publish` (unpublished → recruiting), `unpublish` (recruiting → unpublished, «Снять с публикации»), `start` (recruiting → active), `complete` (active → completed), `cancel` (recruiting, unpublished, active → cancelled); недопустимый переход — 409 | `ProjectActions` в `:gateway` (`contract`) |
| Тип отклика | `ProjectJoinRequest.type` (чтение) | `application` «отклик», `invitation` «приглашение» | `ProjectJoinType` в `:common` |
| Статус отклика | `ProjectJoinRequest.status` (чтение) | `pending` «ожидает», `accepted` «принят», `rejected` «отклонён», `cancelled` «отменён» (инициатором или сервером после смены проекта, роли или принятия другого отклика) | `ProjectJoinStatus` в `:common` |
| Причина жалобы на пользователя | `CreateComplaint.subject` | `spam`, `insults`, `portfolio_fraud`, `fake_account`, `scam`, `other` | `UserComplaintReason` в `:feature:complaints:domain` |
| Причина жалобы на проект | `CreateComplaint.subject` | `spam`, `false_information`, `scam`, `other` | `ProjectComplaintReason` |
| Уровень обучения | `study_level` | остаётся **свободным текстом** | — |

Даты проекта (`planned_start`, `planned_end`): строка ISO-даты `YYYY-MM-DD`. Начало — первое число месяца начала, конец — последнее число месяца
окончания. Чтение: берутся `YYYY-MM` из начала строки (подходит и `YYYY-MM-DD`, и дата со временем); не разобралось — `period = null`.
Функции форматирования и разбора — в `:gateway/mapping/ProjectDates.kt`.

## 3. Что включаем (флаги возможностей источников Gateway)

| Флаг | Было | Станет | Что делает |
|---|---|---|---|
| `ProjectsCapabilities.canCreate` | `false` (G40, G43) | `true` | `POST /projects`: `title`, `type`, `field`, `problem`, `goal`, `tags`, `planned_start`, `planned_end`, `roles` (каждая роль: `title`, `skills`, `slots = 1`) |
| `ProjectsCapabilities.canListMine` | `false` (G44) | `true` | «Я инициатор»: `GET /projects` (каталог и мои проекты) страницами по 100 до 10 страниц, без `mine` и `profile_user_id`; остаются проекты с `owner_user_id` = пользователь сессии; порядок сервера |
| `ProjectsCapabilities.canUnpublish` | `false` (G42) | `true` | `POST /projects/{id}/transitions` с `action = "unpublish"` и `expected_revision` из кэша |
| `ProjectSearchCapabilities.typeFilter` | `false` (G40) | `true` | параметр `type` поиска |
| `ProjectSearchCapabilities.durationFilter` | `false` (G45) | `true` | параметр `duration` поиска |
| `CollaboratorsCapabilities.projectTypeFilter` | `false` (G11) | `true` | параметр `project_type` поиска людей |
| `ProfileCapabilities.interestTypes` | `false` (G11) | `true` | `project_types` профиля: читаем и пишем текст значений |
| `ProfileCapabilities.verificationStatuses` | `false` (G12) | `true` (с 2026-10-20) | статус работ и документов: `unverified` → «Не подтверждено», `pending_review` → «На проверке», `verified` → «Подтверждено», `needs_evidence` → «Нужны подтверждения» (`AchievementStatuses` в `:gateway`) |

**Фильтры с несколькими чипами.** Параметры `type`, `duration`, `project_type` в API — одна строка. Отправляем значение, только когда выбран **ровно один**
чип; ноль или несколько — параметр не отправляется (два типа из двух — это и есть «любой»). Для `duration` выбор двух из трёх так не выразить:
это известное ограничение, сервер вернёт больше, чем выбрано.

**Поле `field`** в поиске и при создании — название сферы текстом.

**Профиль.** `project_types` пишется текстом значений; записи, которых мы не знаем, при сохранении профиля возвращаются на сервер как пришли (данные других
клиентов не теряем). Верхний предел курса `study_year` (`MaxStudyYear = 6`) убран: «ориентируемся на сервер»; остаётся только `>= 1`.

## 4. Поведение при неизвестных значениях

- `Project.type`, `Project.status`, `Project.period`, `Project.sphere` — nullable; `null` = неизвестно, чипа или строки нет, сам проект показывается.
- Проект с неизвестным статусом **не прячется** ни в поиске, ни в «Я инициатор». Действие «Снять с публикации» для своего проекта предлагается, пока
  статус не `Unpublished` (то есть и при неизвестном).
- Неизвестные `project_types` профиля не показываются как чипы, но сохраняются при записи (см. выше).
- Статусы работ и документов (G12), `author_role` отзыва (G13), `ProjectMember.status` (G52): остаются «неизвестно».

## 5. Решения по неясному

| Вопрос | Решение | Где менять |
|---|---|---|
| Формат `planned_*` | ISO-дата `YYYY-MM-DD` | `ProjectDates.kt` |
| Какое действие — «Снять с публикации» | `pause` («приостановить набор»), не «отменить» | `ProjectActions` |
| Как получить «мои проекты» без `mine` | `GET /projects`, фильтр по `owner_user_id` на клиенте | `GatewayProjectsDataSource.observeMyProjects` |
| Сфера числом или названием | названием текстом (решение «передавать текстом») | `Spheres` |
| Пустой `roles` при создании | роли необязательны (допущение спринта 1): отправляется `roles: []`; в спецификации у `roles` нет `minItems`, принимает ли сервер пустой массив — не проверено. Если откажет (400), добавим ошибку «нужна роль» | `ProjectDraft` |

## 6. Что остаётся открытым (бэкенд не ответил, мы не знаем)

Статусы работ и документов (G12) и всё, что на них стоит: чипы, очередь проверки, результаты, бейдж вкладки; значения, которые сервер пишет
сам (`Project.status` другими значениями, `author_role`, `ProjectMember.status`, `ProjectJoinRequest.type/status`); семантика `mine` и
`profile_user_id`; формат дат и `action` проверены только по тексту спецификации. Весь список вопросов к бэкенду — отдельный документ.

## 7. Значения спринта 2

Тип и статус отклика, причины жалоб и статусы проекта `started`/`finished`/`cancelled` — в таблице раздела 2 (тип и статус отклика пишет сервер сам; значения — чтения приложения, не подтверждены; неизвестное — `null`, запись не прячется). Статус участника `ProjectMember.status` не читается (G52). Флаг `ProjectsCapabilities.canChangeLifecycle = true`.
