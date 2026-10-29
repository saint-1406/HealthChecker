# Product photo and barcode flow implementation plan

**Goal:** Поиск после сканирования и ручная личная карточка с фото и БЖУ.
**Architecture:** Существующий BarcodeLookup, Food, Room JSON и BackupCodec; ограниченный JPEG в optional поле Food. Отдельный Compose компонент фото.
**Tech Stack:** Kotlin, Compose, Activity Result, kotlinx.serialization, JUnit/Robolectric.
**Spec:** docs/superpowers/specs/2026-10-29-product-photo-design.md

## Constraints and review focus
- Сохранённые товары доступны без сети; никакие фото не отправляются.
- Отсутствие товара отличается от сетевой ошибки; поздний ответ не заменяет новый код.
- Фото не меняет БЖУ, расчёт порций и совместимость старых копий.
- Отмена камеры/галереи сохраняет прежнее фото; разрешение камеры запрашивается.
- Обработка фото вне UI потока, save выключен на время обработки; ограничение размеров до сохранения.

## Tasks
- [x] Tests: add photo persistence/backup/old format and invalid/oversize photo tests; add lookup absent/error/accept-and-search tests. Run and observe failure.
- [x] Implement optional Food.productPhoto, validation and backup schema 9; bounded JPEG encoding and reusable preview/photo editor.
- [x] Update barcode UI automatic search, found/missing/error state, manual replacement and photo editor; show photo in portion form.
- [x] Run JVM suite, lint, compile UI tests; run targeted emulator flow for manual photo save, restart and offline scan; build signed v16 installer.
- [x] Review final diff and document verification. Leave changes local until publication is requested.

Verification: 155 JVM tests, 4 scanner UI tests in airplane mode, lint 0 errors/45 warnings, signed APK v16. Activity recreation regression reproduced red and then passed after a JSON Saver fix. Fresh reviewer found no important issues; results and artifact hash are in docs/testing.md.
