# Barcode Scanner Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Ship offline camera decoding, local barcode lookup and explicit Open Food Facts lookup with verified import, as signed v15.

**Architecture:** Keep Food records/Room, add optional canonical barcode and transactional uniqueness. Isolate domain barcode/import math, bounded HTTPS client and Compose workflow. Existing food/meal logging stays the consumer of saved cards.

**Tech Stack:** Kotlin, Compose, kotlinx.serialization, Room, ZXing Android Embedded 4.3.0, HttpURLConnection, JUnit/Robolectric/instrumentation.

**Spec:** docs/superpowers/specs/2026-10-29-barcode-scanner.md

## Global Constraints
- Android min26, RU/EN, appId/key unchanged, v15, local APK only.
- Schema 8 reads 1–8; older unset Food fields retain original encoded shape.
- Network only on user action; fixed HTTPS host, time/size bounds, no private diary or images sent.
- Preserve unknown nutrition, distinguish 100g/100ml, never auto-save fetched results.
- One Gradle build at a time; stop emulator before release compilation if memory pressure.

## Review Focus
- Leading zeros/UPC alias and duplicate codes: normalize, transactional conflict, restore validation.
- Wrong product code, missing/invalid kcal, serving-only data, kJ, liquid basis: fail safe editable preview.
- Network cancellation/stale results/back/relaunch: never show a different code's fetched card or save on completion.
- Camera denial/cancel/no camera: manual input remains available, no external URL execution.
- Existing saved meals/history/backup/export: old bytes stable and new barcode restored.

### Task 1: Domain and data
- [x] RED BarcodeTest for check digits, canonical leading zeros, numeric validation, import kJ/null/invalid/basis/density; backup/Room barcode conflicts and roundtrip.
- [x] Add domain/Barcode.kt and barcode/OpenFoodFacts.kt (pure parser); Food optional barcode; schema8, validation and transactional unique save.
- [x] GREEN full JVM tests and commit.

### Task 2: Scanner workflow
- [x] Add ZXing dependency; test actual generated EAN image decoding; bounded explicit HTTP client with local fake transport tests.
- [x] Add barcode state/controller and Compose screen: scan/manual/local/online/review/edit/save, back and cancellation.
- [x] Add search entry, route callbacks, permissions and attribution; preserve ingredient/meal/date routes.
- [x] Add instrumentation offline-first create/relaunch/lookup/log, edit imported preview/missing fields, invalid code/duplicate, small font viewport.
- [x] GREEN debug APK + instrumented tests; fix failures and commit.

### Task 3: Verification and delivery
- [x] Fresh review if required by applicable workflow; fix material findings with targeted tests.
- [x] Unit/catalog/lint and old UI regressions; real public API read and optical image decode (no live camera claim without device).
- [x] Signed v15, same signature, upgrade14→15, backup roundtrip; update README/testing/results and final commit.
