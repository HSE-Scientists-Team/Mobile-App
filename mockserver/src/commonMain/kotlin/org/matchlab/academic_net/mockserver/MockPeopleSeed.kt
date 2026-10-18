package org.matchlab.academic_net.mockserver

import kotlinx.datetime.YearMonth
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.mockserver.MockCheckState.Pending
import org.matchlab.academic_net.mockserver.MockCheckState.Rejected
import org.matchlab.academic_net.mockserver.MockCheckState.Verified

private fun id(key: String) = UserId("user-$key")

private fun person(
    key: String,
    name: String,
    university: String,
    completed: Int = 0,
) = MockServerPerson(id(key), name, university, completed)

private fun education(owner: String, n: Int, title: String, issuer: String, year: Int?, state: MockCheckState = Verified) =
    MockServerDocument("doc-$owner-$n", id(owner), isEducation = true, title, issuer, year, state)

private fun certificate(owner: String, n: Int, title: String, issuer: String, year: Int?, state: MockCheckState = Verified) =
    MockServerDocument("doc-$owner-$n", id(owner), isEducation = false, title, issuer, year, state)

private fun review(
    subject: String,
    n: Int,
    author: String,
    asInitiator: Boolean,
    project: String,
    finished: YearMonth,
    title: String,
    rating: Int,
    text: String,
) = MockServerReview("review-$subject-$n", id(author), id(subject), asInitiator, project, finished, title, rating, text)

private const val CampusNavigation = "AI-навигация по кампусу"

/**
 * The seeded content of [MockPeople.default].
 *
 * It has a record for every person that appears in the other mock data sources (the people search,
 * the feed, the chats, the projects and the catalog of projects), under the same id and name.
 * The viewer, `user-me`, is "Алексей Морозов" here: the content of the Figma screen "10 Мой
 * профиль"; the other mocks call them "Вы".
 *
 * Reviews are written so that a person has a rating exactly when they have finished projects:
 * a person without reviews has no finished projects, and vice versa.
 */
internal object MockPeopleSeed {
    fun create(): MockPeople = MockPeople(
        persons = persons(),
        documents = documents(),
        reviews = reviews(),
    )

    private fun persons() = listOf(
        // The viewer and the person of "09 Профиль": the same content, one is a copy of the other.
        person(
            key = "me",
            name = "Алексей Морозов",
            university = "Stanford",
            completed = 3,
        ),
        person(
            key = "arjun",
            name = "Арджун Мехта",
            university = "Stanford",
            completed = 3,
        ),
        person(
            key = "maria",
            name = "Мария Соколова",
            university = "МГУ",
            completed = 2,
        ),
        person(
            key = "daniil",
            name = "Даниил Крюков",
            university = "ИТМО",
            completed = 1,
        ),
        person(
            key = "elena",
            name = "Проф. Елена Васкес",
            university = "Stanford",
            completed = 12,
        ),
        person(
            key = "anna-petrova-sechenov",
            name = "Д-р Анна Петрова",
            university = "Сеченовский",
            completed = 4,
        ),
        person(
            key = "ivan-orlov",
            name = "Иван Орлов",
            university = "ИТМО",
            completed = 3,
        ),
        person(
            key = "lina",
            name = "Лина Ковач",
            university = "ВШЭ",
            completed = 5,
        ),
        person(
            key = "anna",
            name = "Анна Петрова",
            university = "СПбГУ",
            completed = 2,
        ),
        person(
            key = "olga",
            name = "Ольга Никитина",
            university = "Stanford",
            completed = 4,
        ),
        person(
            key = "timur",
            name = "Тимур Алиев",
            university = "ИТМО",
            completed = 1,
        ),
        person(
            key = "li-chen",
            name = "Д-р Ли Чен",
            university = "Stanford",
            completed = 6,
        ),
        person(
            key = "kirill",
            name = "Кирилл Назаров",
            university = "ИТМО",
            completed = 1,
        ),
        person(
            key = "sofia",
            name = "София Лебедева",
            university = "ВШЭ",
            completed = 1,
        ),
        person(
            key = "mikhail-gromov",
            name = "Михаил Громов",
            university = "СПбГУ",
            completed = 2,
        ),
        person(
            key = "natalia-egorova",
            name = "Проф. Наталья Егорова",
            university = "МФТИ",
            completed = 8,
        ),
        person(
            key = "olga-kuznetsova",
            name = "Д-р Ольга Кузнецова",
            university = "СПбГУ",
            completed = 5,
        ),
        // Students who have not finished a project yet: no reviews and no verified documents.
        person(
            key = "ilyas",
            name = "Ильяс Каримов",
            university = "КФУ",
        ),
        person(
            key = "sergey",
            name = "Сергей Волков",
            university = "МФТИ",
        ),
        person(
            key = "eva",
            name = "Ева Миронова",
            university = "МГУ",
        ),
        person(
            key = "daria-smirnova",
            name = "Дарья Смирнова",
            university = "ВШЭ",
        ),
        person(
            key = "pavel-zaitsev",
            name = "Павел Зайцев",
            university = "РЭУ им. Плеханова",
        ),
        person(
            key = "denis-belov",
            name = "Денис Белов",
            university = "РХТУ им. Менделеева",
        ),
    )

    private fun documents() = listOf(
        // The viewer: a certificate is under moderation, as on "10 Мой профиль".
        education("me", 1, "B.S. Computer Science", "Stanford University", 2027),
        certificate("me", 2, "Сертификат Deep Learning", "Coursera", 2025, Pending),
        education("arjun", 1, "B.S. Computer Science", "Stanford University", 2027),
        certificate("arjun", 2, "Сертификат Computer Vision", "Udacity", 2025),
        education("maria", 1, "Специалитет, биоинформатика", "МГУ", 2023),
        education("daniil", 1, "Бакалавриат, робототехника", "ИТМО", 2024),
        education("elena", 1, "Ph.D. Computer Science", "Stanford University", 2009),
        education("anna-petrova-sechenov", 1, "Ординатура, радиология", "Сеченовский университет", 2015),
        education("ivan-orlov", 1, "Кандидат технических наук", "ИТМО", 2018),
        education("lina", 1, "Бакалавриат, прикладная математика", "ВШЭ", 2024),
        education("anna", 1, "Студенческий билет", "СПбГУ", null),
        education("olga", 1, "M.S. Computer Science", "Stanford University", 2023),
        education("timur", 1, "Студенческий билет", "ИТМО", null),
        education("li-chen", 1, "Ph.D. Computer Vision", "Stanford University", 2021),
        education("kirill", 1, "Бакалавриат, информатика", "ИТМО", 2025),
        education("sofia", 1, "Студенческий билет", "ВШЭ", null),
        education("mikhail-gromov", 1, "Магистратура, социология", "СПбГУ", 2024),
        education("natalia-egorova", 1, "Доктор физико-математических наук", "МФТИ", 2012),
        education("olga-kuznetsova", 1, "Кандидат филологических наук", "СПбГУ", 2016),
        // Unverified people: a document is under moderation or was rejected, or there is none.
        education("ilyas", 1, "Студенческий билет", "КФУ", null, Pending),
        education("sergey", 1, "Студенческий билет", "МФТИ", null, Rejected),
        education("pavel-zaitsev", 1, "Студенческий билет", "РЭУ им. Плеханова", null, Pending),
    )

    private fun reviews() = buildList {
        // "09 Профиль" and "10 Мой профиль": twelve reviews, eleven of them positive; the first
        // three are the cards of "09b Отзывы". The viewer has the same reviews as Арджун.
        for (subject in listOf("me", "arjun")) {
            add(
                review(
                    subject, 1, "elena", asInitiator = true, CampusNavigation, YearMonth(2026, 3),
                    "Довёл модели до демо", 5,
                    "Взял на себя обучение моделей и довёл до демо. Сроки соблюдал, писал понятные отчёты. " +
                        "Сам предложил метрику качества сегментации и защитил её перед командой. " +
                        "Помогал студентке второго курса разобраться с обучением, объяснял спокойно и по делу. " +
                        "Из минусов: пару раз затягивал с ответом в чате, но предупреждал заранее. " +
                        "Рекомендую в научные проекты, где нужен человек с самостоятельной инженерной позицией.",
                ),
            )
            add(
                review(
                    subject, 2, "lina", asInitiator = false, CampusNavigation, YearMonth(2026, 3),
                    "Помогал команде с данными", 5,
                    "Сильный инженер, помогал команде с пайплайном данных.",
                ),
            )
            add(
                review(
                    subject, 3, "li-chen", asInitiator = true, "Обзор методов сегментации", YearMonth(2026, 3),
                    "Быстро включился в работу", 5,
                    "Разобрался в предметной области за неделю, предложил свой метод разметки.",
                ),
            )
            add(
                review(
                    subject, 4, "maria", asInitiator = true, "Микробиом и стресс: пилот", YearMonth(2026, 2),
                    "Аккуратные данные", 5,
                    "Подготовил чистый датасет и воспроизводимые ноутбуки, с ним удобно работать.",
                ),
            )
            add(
                review(
                    subject, 5, "daniil", asInitiator = false, "Робот-курьер для корпуса", YearMonth(2026, 1),
                    "Надёжный партнёр", 5,
                    "Сделал модуль распознавания дверей, сроки не срывал.",
                ),
            )
            add(
                review(
                    subject, 6, "olga", asInitiator = true, "Few-shot обнаружение приборов", YearMonth(2025, 12),
                    "Сильная инженерная работа", 5,
                    "Быстро собрал стенд для замеров и честно описал ограничения модели.",
                ),
            )
            add(
                review(
                    subject, 7, "timur", asInitiator = false, "Бот расписания", YearMonth(2025, 10),
                    "Хороший командный игрок", 4,
                    "Делал мобильный клиент, иногда не хватало внимания к деталям интерфейса.",
                ),
            )
            add(
                review(
                    subject, 8, "ivan-orlov", asInitiator = true, "Цифровой двойник лаборатории", YearMonth(2025, 9),
                    "Помог с прототипом", 5,
                    "Собрал прототип панели мониторинга за две недели.",
                ),
            )
            add(
                review(
                    subject, 9, "mikhail-gromov", asInitiator = true, "Цифровые привычки студентов", YearMonth(2025, 7),
                    "Справился с анализом", 5,
                    "Аккуратно посчитал статистику по опросу и оформил результаты.",
                ),
            )
            add(
                review(
                    subject, 10, "natalia-egorova", asInitiator = true, "Стабильность перовскитных элементов",
                    YearMonth(2025, 6), "Нужна точность в сроках", 3,
                    "Результат хороший, но сроки сдвигались, а о задержках сообщали поздно.",
                ),
            )
            add(
                review(
                    subject, 11, "anna", asInitiator = false, "Юзабилити кампусного приложения", YearMonth(2025, 5),
                    "С ним комфортно работать", 5,
                    "Всегда на связи, быстро отвечает на вопросы по задачам.",
                ),
            )
            add(
                review(
                    subject, 12, "kirill", asInitiator = false, "Классификатор помещений", YearMonth(2025, 3),
                    "Хорошо разбирается в моделях", 5,
                    "Помог выбрать архитектуру и ускорить обучение вдвое.",
                ),
            )
        }

        // "Проф. Елена Васкес": the initiator of "04 Проект"; ten reviews give the rating 4.9.
        val elenaReviews = listOf(
            Triple("kirill", CampusNavigation, YearMonth(2026, 3)),
            Triple("sofia", CampusNavigation, YearMonth(2026, 3)),
            Triple("arjun", CampusNavigation, YearMonth(2026, 3)),
            Triple("olga", "Распознавание аудиторий по фото", YearMonth(2025, 12)),
            Triple("daniil", "Робот-курьер для корпуса", YearMonth(2025, 11)),
            Triple("li-chen", "Датасет интерьеров кампуса", YearMonth(2025, 9)),
            Triple("maria", "Обработка снимков для атласа корпусов", YearMonth(2025, 7)),
            Triple("timur", "Серверная часть навигатора", YearMonth(2025, 5)),
            Triple("lina", "Аналитика посещаемости корпусов", YearMonth(2025, 3)),
            Triple("anna", "Исследование навигации для студентов", YearMonth(2025, 1)),
        )
        elenaReviews.forEachIndexed { index, (author, project, finished) ->
            // The last one is a four: 9 × 5 + 4 = 49 over 10 reviews is 4.9.
            val last = index == elenaReviews.lastIndex
            add(
                review(
                    "elena", index + 1, author, asInitiator = false, project, finished,
                    title = if (last) "Требовательная и справедливая" else "Понятные задачи и честная обратная связь",
                    rating = if (last) 4 else 5,
                    text = if (last) {
                        "Высокая планка, но все требования были объяснены заранее."
                    } else {
                        "Чётко ставит задачи, быстро отвечает и помогает довести работу до результата."
                    },
                ),
            )
        }

        // The rest of the people with finished projects: one or two reviews each.
        add(review("maria", 1, "eva", false, "Микробиом кишечника и стресс", YearMonth(2026, 2), "Внимательная к деталям", 5, "Хорошо объясняет методику и проверяет каждый этап."))
        add(review("maria", 2, "olga", true, "Статистика для биоинформатики", YearMonth(2025, 8), "Сильная аналитика", 5, "Грамотно подобрала тесты и описала ограничения."))
        add(review("daniil", 1, "arjun", true, "Робот-курьер для корпуса", YearMonth(2026, 1), "Отличный партнёр по железу", 5, "Помог довести робота до рабочего прототипа."))
        add(review("lina", 1, "ivan-orlov", true, "Цифровой двойник лаборатории", YearMonth(2025, 11), "Быстро строит дашборды", 5, "Сделала панель и автоматические отчёты."))
        add(review("lina", 2, "sofia", true, "Прогноз спроса в столовых", YearMonth(2025, 6), "Делится знаниями", 5, "Показала, как строить прогнозы на временных рядах."))
        add(review("anna", 1, "daria-smirnova", true, "Мобильное приложение библиотеки", YearMonth(2025, 12), "Помогла с исследованием", 5, "Провела серию интервью и дала понятные выводы."))
        add(review("olga", 1, "li-chen", true, "Few-shot обнаружение приборов", YearMonth(2025, 10), "Сильный исследователь", 5, "Аккуратные эксперименты и честные выводы."))
        add(review("olga", 2, "elena", true, "Распознавание аудиторий по фото", YearMonth(2025, 4), "Самостоятельная и надёжная", 5, "Довела модель до публикации в срок."))
        add(review("timur", 1, "ivan-orlov", true, "Цифровой двойник лаборатории", YearMonth(2026, 2), "Надёжный бэкенд", 5, "Сделал API для датчиков без единой просрочки."))
        add(review("li-chen", 1, "elena", true, "Датасет интерьеров кампуса", YearMonth(2025, 9), "Строгий к качеству", 5, "Разметка получилась аккуратной и согласованной."))
        add(review("kirill", 1, "elena", true, CampusNavigation, YearMonth(2026, 3), "Обучил модели вовремя", 5, "Обучил модели и подготовил демо к сроку."))
        add(review("sofia", 1, "elena", true, CampusNavigation, YearMonth(2026, 3), "Чистые данные", 4, "Подготовила данные для моделей, иногда не хватало документации."))
        add(review("anna-petrova-sechenov", 1, "maria", true, "Обработка снимков для атласа", YearMonth(2025, 10), "Профессиональный подход", 5, "Грамотно объясняет задачу и ценит чужое время."))
        add(review("ivan-orlov", 1, "timur", false, "Цифровой двойник лаборатории", YearMonth(2026, 2), "Ясные задачи", 5, "Хорошо расписывает требования, быстро принимает работу."))
        add(review("mikhail-gromov", 1, "anna", false, "Цифровые привычки студентов", YearMonth(2025, 7), "Хорошая организация", 5, "Интервью были подготовлены заранее, а выводы — аккуратные."))
        add(review("natalia-egorova", 1, "olga", false, "Стабильность перовскитных элементов", YearMonth(2025, 6), "Научный руководитель", 5, "Даёт глубокую обратную связь и защищает команду."))
        add(review("natalia-egorova", 2, "li-chen", false, "Защитное покрытие для перовскита", YearMonth(2024, 11), "Требовательная", 4, "Высокие требования к воспроизводимости, но справедливые."))
        add(review("olga-kuznetsova", 1, "mikhail-gromov", false, "Корпус региональных говоров", YearMonth(2025, 9), "Прозрачный процесс", 5, "Чётко описала правила разметки и ответила на все вопросы."))
    }
}
