# Offline products 16.1 implementation plan

**Goal:** Весь доступный OFF food-каталог для офлайн-сканирования, выпуск 16.1.
**Architecture:** Python streaming Parquet → validated SQLite → gzip asset parts → Android indexed read-only database. Личные карточки и фото v16 имеют приоритет.
**Tech Stack:** Python/PyArrow/sqlite3, Android SQLiteDatabase, Compose, JUnit/Robolectric.
**Spec:** docs/superpowers/specs/2026-10-30-offline-products-design.md

## Constraints and review focus
- Отсутствие INTERNET в merged manifest; нет HTTP-кода сканера и embedded YouTube.
- Все строки пищевого снимка рассматриваются, пропуски/дубли/ошибки отражены в отчёте; никаких оценочных значений вместо известных.
- 100 мл не выдаются за 100 г, кДж пересчитываются только при отсутствии ккал, неизвестные БЖУ сохраняются.
- Распаковка выполняется вне главного потока, прерванная/повреждённая копия не активируется.
- Версия 16.1/code17, прежний ключ и applicationId, код и бинарник соответствуют одному коммиту.

## Tasks
- [x] Download fixed official snapshot and verify source SHA-256. Inspect actual schema before choosing fields.
- [x] Write importer fixture tests for GTIN aliases, invalid codes, nutrient limits/units/preparation, missing fields and duplicate handling; observe failures, implement streaming import and publish report/database/license assets.
- [x] Write Android indexed lookup/atomic extraction tests; implement BundledProductCatalog, offline scanner states and manual fallback. Remove network code/permission and embedded YouTube; keep external links/local files.
- [x] Add versionName to release tooling, metadata/attribution in profile and documentation. Test meaningful version16→16.1 behavior.
- [x] Run Python, JVM, lint, scanner and video UI tests offline, signed APK inspection, targeted release smoke. Fresh whole-change review and fixes.
- Publication: commit all authorized changes, push without rewriting history, release v16.1 Latest with full signed APK/checksum/database/notes; verify uploaded assets and remote commit/tag. This external step is recorded by the release and the local publication verification log after committing this plan.

Verification before publication: 20 Python tests,153 JVM tests,9 UI tests in airplane mode; lint0errors/45warnings; signed R8 candidate16→16.1 upgrade preserves155kcal/250ml and finds bundled Nutella offline. Two source-only review passes found/fixed dense-volume nutrition limits; packaging review clean. Full results in docs/testing.md. Snapshot4,882,161rows→4,682,262valid unique GTINs;943,729known calories. Runtime/index/asset checksums in the published report.
