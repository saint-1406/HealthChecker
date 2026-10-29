# Everyday Use Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Implement the seven accepted daily-use improvements and produce signed Android v14.

**Architecture:** Keep Compose/Room/DataStore. Food collections are snapshot records; a persistent device-only meal draft uses a separate Room record ignored by backup export. Recoverable image directory replacement coordinates with a database generation marker. New small domain/UI files isolate collections and batch entry from existing diary code.

**Tech Stack:** Kotlin, Compose, Room, DataStore, kotlinx.serialization, JUnit/Robolectric, Compose instrumentation.

**Spec:** `docs/superpowers/specs/2026-10-29-everyday-use.md`

## Global Constraints

- Android 8+, RU/EN, offline, app ID/key unchanged. APK v14; no GitHub publication.
- Existing nutrition values and diary snapshots preserved. Backup schema 7 reads 1–7.
- No simultaneous Gradle processes. All source edits finish before release build.

## Review Focus

- Restore disk/rename failure and process death: old or new database and pictures agree; task 1 failure/recovery tests.
- Batch saved twice or after midnight: unique IDs/date frozen, one transaction; task 2 repository tests.
- Removed catalog food and changed user recipe: saved meal snapshots remain usable; task 2 tests.
- Unit conversion and fractional pieces: mass preserved; task 3 tests.
- Large font, empty medicine list, hidden cards, back/relaunch draft: task 4/5 instrumentation.

### Task 1: Recoverable restore
Files: `data/ExerciseImages.kt`, new `data/ImageReplacement.kt`, `data/HealthRepository.kt`, new `data/ImageReplacementTest.kt`, `data/HealthRepositoryTest.kt`.
Interfaces: `ImageReplacement.prepare(Map<String,ByteArray>): String`, `activate()`, `recover(committedGeneration:String?)`; repository owns generation marker within Room transaction, file store owns staging/journal.
- [ ] RED: injected preparation/second-rename failure leaves old DB and images; recovery chooses old files without committed marker and new with marker.
- [ ] Implement staging, durable journal, rollback and cleanup; serialize restore/image access with database transactions.
- [ ] GREEN: targeted tests; commit.

### Task 2: Food collections and meal drafts
Files: new `domain/Meals.kt`, `domain/UserSnapshot.kt`, `domain/Validation.kt`, `backup/BackupCodec.kt`, `data/HealthRepository.kt`, new `domain/MealsTest.kt`, repository/backup tests.
Interfaces: `MealItem(food:Food,quantity:Quantity)`, `SavedMeal(id,name,items)`, `MealDraft(id,date,items)`, `MealDraft.entries(now,zone)`, repository draft CRUD, batch commit/delete, favorite toggle and saved-meal CRUD. Snapshot adds `favoriteFoods:Set<String>` and `savedMeals:List<SavedMeal>`.
- [ ] RED: separate products, snapshot stability, duplicate commit, rollback invalid item, draft survives restart but excluded from backup, legacy backup reads, new fields roundtrip.
- [ ] Implement repository transaction operations and validation; schema 7.
- [ ] GREEN: domain, Room, backup tests; commit.

### Task 3: Portion memory and settings
Files: new `domain/PortionInput.kt`, `data/SettingsRepository.kt`, `ui/diary/EntryEditor.kt`, tests.
Interfaces: `convertQuantity(food,q,target,pieceGrams):Quantity` preserves grams; `Settings` adds last portions, waterMl, visible overview cards and backup prompt dismissed. Atomic DataStore updates per setting.
- [ ] RED: 150 g ↔ 3 × 50 g, density 0.8 ml conversion, same-unit no-op, unknown piece mass validation, settings persist.
- [ ] Implement controls/memory, 200/250/500 water presets, task 2 collection validation reuse.
- [ ] GREEN: targeted tests; commit.

### Task 4: Daily-use UI
Files: new `ui/diary/MealScreen.kt`, `ui/diary/FoodShortcuts.kt`, new `ui/SaveStatus.kt`, `ui/HealthApp.kt`, `ui/AppViewModel.kt`, diary/search/dashboard/profile screens.
- [ ] Add instrumentation scenarios: favorite → search favorite; batch 2 eggs + 50 g oats → single commit; saved meal → edit quantities → commit; draft back/relaunch; remembered portions; hidden cards; compact medicines; backup prompt dismiss.
- [ ] Integrate persistent draft and meal editing/routes. Keep immediate existing-entry and photo recording behavior.
- [ ] Move actions/shortcuts; source/preparation badges, filter search; settings and status feedback. Freeze draft date.
- [ ] GREEN: compile plus domain suite, instrumentation; commit.

### Task 5: Release and review
Files: instrumentation tests, README, `docs/testing.md`.
- [ ] Emulator normal and small screen/font 200% workflows; fix actual layout failures. Check 48 dp controls and accessibility labels.
- [ ] Fresh whole-change review; fix important findings once with regression tests.
- [ ] Unit/Python/lint, release v14, signature/package/version/hash checks, install over v13 and confirm diary/settings persist; record actual evidence and limits.
- [ ] Commit documentation and final result. Leave publication for a separate request.
