# HealthChecker Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Создать самостоятельное офлайн-приложение Android для питания и выдать один подписанный APK с проверяемым восстановлением данных.

**Architecture:** Одно приложение Compose, чистые Kotlin-функции расчётов, Room для личных данных и каталога, DataStore для настроек. Репозитории передают состояния в ViewModel через Flow. Резервное копирование через системный выбор папки не зависит от облака.

**Tech Stack:** JDK 17; Gradle 9.1.0; AGP 9.0.1 со встроенным Kotlin 2.2.10; Compose compiler 2.2.10; KSP 2.2.10-2.0.2; Compose BOM 2025.08.00; Room 2.7.2; DataStore 1.1.7; Kotlin serialization 1.9.0; JUnit 4.13.2. Инструменты сборки Android 36.0.0. Все зависимости фиксировать в version catalog без динамических версий. Совместимость всей комбинации проверить сборкой первой задачи; исправления ограничить инструментами и записать фактические версии.

**Spec:** [Согласованная спецификация](../specs/2026-10-18-healthchecker-design.md).

## Global Constraints

- Минимальная версия Android 8.0 (API 26), целевая сборка с установленным SDK 36.
- Русский язык интерфейса, русские и азербайджанские поисковые названия продуктов.
- Работа без аккаунта, сервера и обязательного интернета.
- Три нижние вкладки: Обзор, Дневник, Профиль. Рецепты отложены.
- Сутки — местный календарный день от 00:00 до следующей полуночи.
- Отсутствующие нутриенты показываются как неизвестные, а не подменяются нулём.
- Один постоянный applicationId `az.healthchecker.app`, один сохраняемый ключ подписи, один актуальный `dist/HealthChecker.apk`.
- Ключ, пароли, local.properties, сырые выгрузки и личные копии не попадают в Git.
- Нет гарантии восстановления после удаления без сохранённой внешней копии.

## Review Focus

1. Двойное нажатие «Сохранить» и пересоздание экрана не должны удваивать запись — задача 5.
2. Пропуск нутриента в одном продукте не должен создавать ложную полную сумму — задача 1.
3. Отзыв доступа к папке, сбой записи и заполненный накопитель не должны уничтожать последнюю копию — задача 7.
4. Азербайджанские I/İ/ı, русские е/ё и десятичная запятая не должны мешать поиску и вводу — задачи 1, 3.
5. Импорт с повторными UUID, будущей схемой или обрывом JSON не должен частично заменить дневник — задача 7.

## Структура файлов и общие типы

Пути Kotlin ниже относительно `app/src/main/java/az/healthchecker/`; тесты повторяют пакеты в `app/src/test/java/az/healthchecker/` и `app/src/androidTest/java/az/healthchecker/`.

- `domain/Models.kt`: `Nutrients(kcal: Double?, protein: Double?, fat: Double?, carbs: Double?)`, `Food(id: String, name: String, aliases: List<String>, per100g: Nutrients, portions: List<Portion>, density: Double?, source: String)`, `Portion(name: String, grams: Double)`, `Quantity(value: Double, unit: Unit, portionGrams: Double?)`; Unit = GRAMS, PIECES, ML.
- `domain/DiaryEntry.kt`: UUID, foodId, снимок названия, quantity, grams, nutrients, waterMl, instant, zoneId, localDate. `DaySummary` содержит сумму известных значений и отдельный признак полноты каждого нутриента.
- `domain/Profile.kt`: `Profile` с полом, возрастом, ростом, исходным и текущим весом, активностью; `DailyGoals(effectiveFrom: LocalDate, kcal: Double?, protein: Double?, waterMl: Double?, manual: Boolean)`.
- `domain/NutritionCalculator.kt`, `GoalCalculator.kt`, `DaySummaryCalculator.kt`: чистая математика.
- `data/HealthDatabase.kt`, `Entities.kt`, `HealthDao.kt`, `HealthRepository.kt`: транзакции и Flow; схема Room экспортируется в `app/schemas/`.
- `catalog/CatalogImporter.kt`, `SearchNormalizer.kt`: каталог и поиск; `tools/catalog/` — воспроизводимая подготовка данных.
- `backup/BackupModels.kt`, `BackupCodec.kt`, `BackupService.kt`, `SafBackupStore.kt`: формат, проверка, восстановление и доступ к документам.
- `ui/HealthApp.kt`, `ui/theme/Theme.kt`, `ui/dashboard/`, `ui/diary/`, `ui/profile/`: экраны, их ViewModel и компоненты.
- `app/src/main/assets/catalog/`: импортируемые продукты, лицензии, происхождение и отчёт покрытия.
- `scripts/build-apk.ps1`, `README.md`, `docs/testing.md`: воспроизводимая сборка, установка и свидетельства проверки.

## Task 1: Собираемое приложение и расчёты порций

**Files:** создать Gradle wrapper, `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`, `.gitignore`, `app/build.gradle.kts`, manifest, `MainActivity.kt`, типы domain и `NutritionCalculator.kt`; тест `domain/NutritionCalculatorTest.kt`.

**Interfaces:** `parseQuantity(text: String): Double`; `calculate(food: Food, quantity: Quantity): CalculatedPortion`, где результат содержит grams и nutrients; `summarize(entries: List<DiaryEntry>): DaySummary`.

- [ ] Подготовить Git-репозиторий и зафиксировать согласованные документы; проверить инструкции родительских AGENTS.md. Создать конфигурацию сборки, пустую Activity и типы, необходимые тестам. Не добавлять внешние сервисы и сетевое разрешение приложению.
- [ ] Написать тесты: продукт с `Nutrients(200.0,10.0,4.0,20.0)` в 50 г даёт `(100,5,2,10)`; две порции по 60 г дают 240 ккал; 0,5 порции даёт 60 ккал; `parseQuantity("1,5") == 1.5`. Ноль, NaN, infinity, отрицательные значения и ML без плотности отклоняются. Неизвестный белок остаётся неизвестным; сумма известного белка помечается неполной, если есть неизвестный.
- [ ] Запустить `./gradlew.bat :app:testDebugUnitTest --tests '*NutritionCalculatorTest'`, убедиться, что тесты не проходят именно из-за отсутствия логики.
- [ ] Реализовать расчёт без промежуточного округления, валидацию и агрегацию. Поля пользовательского ввода ограничить разумной длиной до парсинга; верхний предел количества 100000 г/мл или 10000 штук как защита от ошибочного ввода.
- [ ] Повторить тесты и `./gradlew.bat :app:assembleDebug`; ожидать BUILD SUCCESSFUL. Зафиксировать `feat: bootstrap Android app and nutrition calculations`.

## Task 2: Постоянное хранение дневника и календарных целей

**Files:** файлы data, `domain/DiaryEntry.kt`, `domain/Profile.kt`; тесты `data/HealthRepositoryTest.kt`, `domain/DaySummaryCalculatorTest.kt`.

**Interfaces:** `HealthRepository.observeDay(date: LocalDate): Flow<DayState>`; `suspend upsertEntry(entry: DiaryEntry)`; `suspend deleteEntry(id: String)`; `suspend saveProfile(profile: Profile)`; `suspend saveGoals(goals: DailyGoals)`; `suspend exportSnapshot(): UserSnapshot`; `suspend replaceSnapshot(snapshot: UserSnapshot)`. `UserSnapshot` включает профиль, цели, записи, пользовательские продукты и отметки завершённых дней; определяется в `domain/UserSnapshot.kt`.

- [ ] Добавить провальные тесты: сохранение и повторное чтение после открытия БД, обновление записи по тому же UUID, откат неуспешного импорта, исходный вес неизменен, обновление каталога не меняет снимок записи. Цель 2000 с 18 октября и 2100 с 19 октября возвращает 2000 для 18-го.
- [ ] Запустить unit-тест агрегации и instrumented-тест репозитория (`:app:connectedDebugAndroidTest`) на доступном тестовом устройстве. Если устройства нет, подготовить эмулятор API 36; отсутствие эмулятора не выдавать за прошедший тест.
- [ ] Реализовать Room, DAO и транзакционные репозитории. Профиль хранить в Room вместе с целями; в DataStore только тему и настройки копирования. Не использовать destructive migration. Версию первой схемы установить 1 и экспортировать JSON схемы.
- [ ] Реализовать календарную агрегацию по сохранённой localDate и часы как передаваемую зависимость. Записи в 23:59 и 00:00 относятся к разным дням; смена зоны не меняет старую localDate. Завершённый пустой день отличается от дня без данных.
- [ ] Повторить тесты; зафиксировать `feat: persist diary and dated goals`.

## Task 3: Реальный офлайн-каталог для Азербайджана

**Files:** `tools/catalog/build_catalog.py`, `tools/catalog/test_build_catalog.py`, `tools/catalog/aliases.json`, `catalog/CatalogImporter.kt`, `catalog/SearchNormalizer.kt`, assets catalog, `docs/catalog-sources.md`; тест `catalog/CatalogSearchTest.kt`.

**Interfaces:** Python `normalize_record(record: dict, source: str) -> dict | None`; `build_catalog(inputs: list[Path], output: Path) -> dict`; Kotlin `normalizeSearch(text: String): String`; `suspend searchFoods(query: String, limit: Int = 50): List<Food>` в HealthRepository. JSON содержит schemaVersion, стабильный source:id, названия, алиасы, нутриенты, порции и источник.

- [ ] Зафиксировать источники, даты, условия распространения и доступные выгрузки USDA и OFF с фильтром страны Azerbaijan. Скачать доступные реальные данные; не заменять недоступные источники выдуманными числами. При отсутствии OFF зафиксировать это как пробел регионального покрытия.
- [ ] Написать тесты импорта: кДж не трактуются как ккал, null не становится нулём, дубликат source:id не увеличивает каталог, напиток без плотности не преобразуется из мл в г, готовый и сырой продукт остаются разными. Тест поиска связывает `яйцо/яйца`, `yumurta`, е/ё и варианты I/İ/ı с нужными алиасами.
- [ ] Выполнить `python -m unittest discover -s tools/catalog` и `:app:testDebugUnitTest --tests '*CatalogSearchTest'`; убедиться в ожидаемых провалах.
- [ ] Реализовать сборку каталога, русский перевод распространённых продуктов и проверенные азербайджанские алиасы. Не переводить бренды в обобщённые продукты. Извлекать порции из источника; при отсутствии веса предлагать ввести его, а не приписывать пачке универсальную массу. Искать локально с ограничением результата и обработкой пустого запроса.
- [ ] Импортировать каталог при первом запуске вне UI-потока, атомарно и идемпотентно. Создать отчёт: количество уникальных продуктов по источнику и категории, локализованные позиции, причины исключений, контрольные суммы. Включить атрибуцию и распространяемую производную базу с лицензией OFF.
- [ ] Повторить проверки и сверить минимум яйцо, сухую овсянку, курицу, воду и местный молочный продукт с их источниками. Зафиксировать `feat: bundle sourced offline food catalog`.

## Task 4: Профиль, начальная настройка и редактируемые цели

**Files:** `domain/GoalCalculator.kt`, `ui/profile/ProfileScreen.kt`, `ProfileViewModel.kt`, `OnboardingScreen.kt`; тесты `domain/GoalCalculatorTest.kt`, `ui/profile/ProfileFlowTest.kt`.

**Interfaces:** `estimateMaintenance(profile: Profile): Double?`; `ProfileViewModel.save(profile: Profile, goals: DailyGoals)` использует репозиторий задачи 2. Значение null означает, что взрослая формула неприменима.

- [ ] Проверить первичный источник формулы Mifflin–St Jeor и задокументировать применение: `10*kg + 6.25*cm - 5*age + offset`, offset +5/-161 для мужского/женского коэффициента; активность 1.2, 1.375, 1.55, 1.725, 1.9. Указать, что коэффициенты активности — приближение, а не часть оригинальной формулы.
- [ ] Тест: мужчина 30 лет, 180 см, 80 кг, коэффициент 1.2 → 2136 ккал; возраст 17 → null. Изменение веса не сбрасывает ручную цель 2200. Значения роста/веса должны быть положительны и конечны. Без данных цели можно установить вручную.
- [ ] Запустить тесты до реализации; затем реализовать формулу, формы, доступный ручной режим и явную кнопку применения нового предложения. Белок и воду пользователь задаёт вручную; не выдавать произвольные значения за персональную норму. Неустановленная цель отображается как «Задать цель».
- [ ] В UI-тесте сохранить профиль, перезапустить Activity, изменить текущий вес и проверить сохранение исходного. Повторить тесты; зафиксировать `feat: add profile and editable nutrition goals`.

## Task 5: Дневник, поиск и ввод съеденного

**Files:** `ui/HealthApp.kt`, `ui/theme/Theme.kt`, `ui/diary/DiaryScreen.kt`, `FoodSearchSheet.kt`, `EntryEditor.kt`, `CustomFoodEditor.kt`, `DiaryViewModel.kt`; тесты `ui/diary/DiaryFlowTest.kt`, `ui/diary/DiaryViewModelTest.kt`.

**Interfaces:** `DiaryViewModel.selectDate(date: LocalDate)`, `search(query: String)`, `saveEntry(draft: EntryDraft)`, `deleteEntry(id: String)`, `undoDelete()`. `EntryDraft` в `ui/diary/EntryDraft.kt` содержит устойчивый UUID, food, quantity и дату; сохранение использует calculate и upsertEntry.

- [ ] Тесты: два одинаковых события сохранения одного draft создают одну запись; новое добавление того же продукта с другим UUID создаёт вторую. Дробная запятая принимается. Удаление с отменой возвращает исходный снимок. Редактирование сохраняет UUID.
- [ ] Запустить тесты, реализовать ViewModel с состоянием сохранения, стабильным UUID и SavedStateHandle. Результат поиска обновлять с debounce и отменой предыдущего запроса.
- [ ] Реализовать три вкладки, тему, формы ввода количества, видимый вес порции, предварительные суммы, дату, быстрое добавление воды 250 мл с возможностью изменения, повтор недавних продуктов и пользовательские продукты. Для последних требуются название и ккал на 100 г; остальные нутриенты могут быть неизвестны.
- [ ] UI-тест: найти яйцо, добавить две порции с явной массой, найти сухую овсянку и добавить 50 г, проверить записи и исправить количество. Проверить системный Back, отмену формы и сохранение черновика при повороте.
- [ ] Повторить тесты и проверить большие шрифты/контраст/подписи кнопок. Зафиксировать `feat: add food diary and portion editor`.

## Task 6: Обзор дня и недельная статистика

**Files:** `ui/dashboard/DashboardScreen.kt`, `DashboardViewModel.kt`, `WeekChart.kt`; тесты `ui/dashboard/DashboardTest.kt`.

**Interfaces:** `DashboardViewModel.selectDate(date: LocalDate)`; `HealthRepository.observeWeek(endDate: LocalDate): Flow<List<DayState>>`. DayState содержит дату, записи, DaySummary, актуальную на дату цель и отметку завершённого дня.

- [ ] Добавить тесты: 2400/2000 показывает перерасход 400; пропущенный день не включается в среднее как ноль; завершённый пустой день включается; неизвестный белок отображается с признаком неполноты. Семь дат упорядочены и не исчезают при пустых данных.
- [ ] Запустить тесты, реализовать подписки Flow и экран с калориями, белком, водой, жирами, углеводами, недавними записями и графиком. Дать возможность отметить день завершённым. При возвращении в приложение обновлять текущую дату, если пользователь просматривал сегодня.
- [ ] Проверить обе темы и увеличенный шрифт на скриншотах эмулятора, содержательные accessibility-подписи графика. Повторить тесты; зафиксировать `feat: add daily dashboard and weekly trends`.

## Task 7: Резервные копии, переживающие удаление приложения

**Files:** все backup-файлы, `ui/profile/BackupSection.kt`, `data/SettingsRepository.kt`; тесты `backup/BackupCodecTest.kt`, `BackupServiceTest.kt`, instrumented `backup/SafBackupTest.kt`.

**Interfaces:** `BackupCodec.encode(snapshot: UserSnapshot): ByteArray`; `decode(bytes: ByteArray): UserSnapshot`; `BackupService.backup(): BackupResult`; `restore(bytes: ByteArray): RestorePreview`; `confirmRestore(preview: RestorePreview)`. `BackupStore` в `backup/BackupStore.kt` имеет `suspend writeNew(bytes: ByteArray): String`, `read(id: String): ByteArray`, `delete(id: String)`; SafBackupStore реализует его.

- [ ] Тесты: полный encode/decode сохраняет профиль, исходный вес, записи и цели; схема >1, дубли UUID, NaN, отрицательная масса, обрыв JSON и файл >25 MiB отклоняются до изменения БД. Повторное восстановление не удваивает записи. Несуществующий catalog id допустим при полном снимке старой записи.
- [ ] Тест отказа writeNew/read: предыдущая исправная копия остаётся, статус ошибки виден, «успешно» не обновляется. Параллельные изменения не позволяют старому снимку перезаписать новый.
- [ ] Запустить тесты; реализовать формат schemaVersion=1, format=`healthchecker-backup`, сериализацию, проверку размера до полной загрузки и предварительный просмотр импорта. Подтверждённый импорт заменяет личные данные одной Room-транзакцией; настройки темы восстанавливаются отдельно, разрешения SAF не импортируются.
- [ ] Реализовать ACTION_OPEN_DOCUMENT_TREE с persistable permission. Писать новый уникальный файл, закрывать поток, перечитывать и проверять; только после этого обновлять статус и удалять старые копии сверх двух последних исправных. Не полагаться на атомарное переименование произвольного document provider.
- [ ] После изменения данных ставить флаг dirty в Room в той же транзакции. Один последовательный worker делает копию; при успехе снимает dirty только для сохранённой ревизии. При сбое повторяет при следующем запуске/изменении и по кнопке. Указать в UI время копии и несохранённые изменения.
- [ ] Проверить отмену выбора, отзыв разрешения, повторный выбор после переустановки. Не включать облачный Auto Backup в качестве скрытого обязательного механизма; локальная копия является основным способом.
- [ ] Повторить тесты; зафиксировать `feat: add durable local backup and restore`.

## Task 8: Проверка на Android и выдача подписанного APK

**Files:** `scripts/build-apk.ps1`, `README.md`, `docs/testing.md`, `docs/catalog-sources.md`, `dist/HealthChecker.apk`, `dist/HealthChecker.apk.sha256`.

**Interfaces:** `scripts/build-apk.ps1 -VersionCode <positive int>` собирает release и заменяет единственный актуальный APK только после успешной проверки подписи. Секреты подписи получает локально, не печатает их и не включает в дистрибутив.

- [ ] Создать постоянный ключ вне Git и настройки локальной подписи, записать безопасное местоположение ключа в локальную инструкцию без паролей. Проверить .gitignore до первого коммита кода сборки. Не подписывать выдаваемый release временным ключом, который будет потерян.
- [ ] Запустить `./gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleRelease` и `python -m unittest discover -s tools/catalog`. Исправить реальные ошибки; не выключать проверки ради зелёной сборки.
- [ ] На выделенном эмуляторе, без личных данных, выполнить `:app:connectedDebugAndroidTest`, затем release smoke-test: профиль → яйцо 2 шт → овсянка 50 г → вода → обзор → перезапуск → правка → копия. Сверить суммы с данными каталога, проверить авиарежим, обе темы, масштаб шрифта и клавиатуру.
- [ ] Собрать следующий внутренний versionCode тем же ключом и выполнить `adb install -r`; записи должны остаться. Затем на этом же тестовом экземпляре удалить пакет, установить заново, выбрать сохранённую копию и сравнить все личные данные. Не удалять приложение с личного телефона пользователя ради теста.
- [ ] Проверить APK через `apksigner verify --verbose --print-certs`, записать SHA-256 и размер. Поставить единственный проверенный release как `dist/HealthChecker.apk`; временные сборки не выдавать за отдельные версии.
- [ ] В README дать установку, обновление, резервное копирование, восстановление, ограничения оценки порций и источники. В docs/testing.md записать команды, результаты, устройство/API, количество продуктов и непройденные проверки, если они есть. Провести итоговый review относительно спецификации, исправить находки и повторить затронутые проверки.
- [ ] Зафиксировать `build: deliver verified Android APK` и выдать ссылки на APK, исходники и инструкции с честным статусом тестов.

## Самопроверка плана

- Все разделы спецификации распределены между задачами 1–8; рецепты не добавлены.
- Между задачами согласованы Nutrients, DiaryEntry, UserSnapshot и интерфейс HealthRepository.
- Пять условий Review Focus имеют проверки в соответствующих задачах.
- Профиль и цели в Room позволяют атомарно восстанавливать личные данные; SAF-разрешения намеренно не сериализуются.
- Наличие Java/SDK подтверждено; эмулятор и system image пока не обнаружены в стандартном SDK. Их установка/проверка входят в выполнение, а не считаются готовыми.
- База продуктов пока не собрана; численность будет заявлена только после реального импорта и проверки.
- Совместимость JDK/Gradle/AGP сверена с https://developer.android.com/build/releases/agp-9-0-0-release-notes ; работоспособность всего проекта доказывается сборкой задачи 1.

## Передача в выполнение

План ожидает рассмотрения пользователем и выбора метода выполнения. Рекомендация — выполнение основным агентом в этом чате: задачи последовательно используют общие модели и репозиторий. Альтернатива — отдельные исполнители и ревьюеры для каждой задачи, с большими затратами контекста. После выбора применять соответствующий обязательный навык; не выдавать план за реализацию.
