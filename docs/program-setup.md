# Profile and program setup

## Profile layout

The Profile tab is now a short summary with entries that open their own screens:

| Entry | Route | What is there |
|---|---|---|
| Header | — | body line (sex · age · height · weight), the goal from the applied setup |
| Today's targets | — | kcal, protein, fat, carbs, water as chips |
| Program setup card | `setup`, `program` | start / continue the questionnaire; "My program" and "Change" once applied |
| Body and activity | `profile-body` | sex, age, height, weight, activity, starting weight. Saving changes only the body, never the goals |
| Nutrition targets | `profile-targets` | kcal, protein, fat, carbs, water from today; "fill in maintenance calories" |
| Inventory (count) | `inventory` | the dish inventory, now a screen of its own; dishes saved from it return here |
| Backup | `profile-backup` | folder, back up now, restore; the row turns red when changes are not backed up |
| Home screen and water | `profile-home` | overview card switches, quick water glass |
| Appearance and language | `profile-look` | opens the themes screen (`appearance`, back returns here), language chips |
| About | `profile-about` | version (`appVersion` tag), catalogs, map, licences, data sources. `AboutScreen(extra=…)` is the slot for an "app update" entry |

Every setting of the old single list is still reachable. Routes live in `ProfileRoutes`; back from any of them returns to the Profile tab.

First start (not onboarded) keeps the language chips, "Start without a calculation", restore and the short manual body/goal form, and adds **Set up my program** as the main button.

## Program setup (`domain/ProgramSetup.kt`)

Seven steps: about you (sex, age 14–100, height, weight, optional target weight) → limitations (knee, lower back, shoulder, wrist, neck, hernia, high blood pressure, pregnancy/postpartum — the latter only for women; none = empty) → equipment (dumbbells, kettlebell, barbell, pull-up bar, dip bars, bench, bands, mat, jump rope; none = empty) → goal (build mass, just gain weight, build muscle, lose fat, maintain, get toned, endurance) → daily activity (×1.2…1.9) and experience (1–3) → days per week (1–7) and minutes (15–90) → the plan.

Nothing is saved until **Apply the plan**. Then, in one database transaction (`HealthRepository.applySetup`): the answers (with `appliedOn`), the profile (starting weight kept), today's `DailyGoals` (`manual=false`), training settings (dumbbells = dumbbells or kettlebell, level = experience) and the onboarded flag. Past days keep their goals. Goals the plan leaves open (calories under 18) keep their current values.

### Nutrition

* BMR — Mifflin–St Jeor; maintenance = BMR × activity.
* Goal factor: mass +15 %, gain weight +10 %, muscle +5 %, fat loss −20 %, toned −10 %, maintain/endurance 0; capped at −750 / +500 kcal.
* Safe minimum: never below max(BMR, 1500 kcal men / 1200 kcal women), unless that is above maintenance (then maintenance).
* Pregnancy: no deficit. Under 18: no calorie or macro targets, only water.
* Protein g/kg: fat loss and muscle 2.0, mass 1.8, gain/toned 1.6, endurance 1.5, maintain 1.4 — counted on the weight at BMI 25 when BMI > 27.
* Fat: max(25 % of kcal, 0.6 g/kg). Carbs: the rest. Rounded to 5 g; kcal to 10.
* Water: 30 ml/kg + ~0.5 l per training hour spread over the week (+300 ml in pregnancy), 1.5–4 l, rounded to 50 ml.
* Expected weekly change = (kcal − maintenance) × 7 / 7700 kg; weeks to the target weight when it points the same way.

### Training week

* Exercises: the bundled catalog filtered by level ≤ experience, equipment (dumbbell moves need dumbbells; a listed subset also works with a kettlebell), and `unsafeFor(exercise)` — conservative id lists and attributes per limitation (jumps/HIIT, deep knee bends, spinal flexion/loaded hinges, overhead and dips, weight on straight hands, crunches lifting the head, intra-abdominal pressure, isometric holds and head-down positions, lying on back/belly). The rules are heuristics, documented in code; a test checks that every id they name exists.
* Week layout: training days spread over the week (`WEEK_LAYOUT`). Session types by goal — mass/muscle: full body ×≤3, upper/lower split from 4 days; fat loss/toned: full body with a cardio finisher alternating with cardio days; endurance: mostly cardio; maintain: mixed with mobility.
* Each session: 1–2 warm-ups, the main part filled round-robin over the muscle groups up to the minutes asked (then extra sets up to 5), 1–2 cool-down stretches. Exercises rotate across the week. The result is deterministic for the same answers.
* Sets/reps: `prescribe()` by level, more sets and 8–10 dumbbell reps for muscle goals, +3 reps for endurance/toned.
* Barbell, pull-up bar, dip bars, bands, bench and jump rope have no exercises in the catalog yet; they produce tips instead.

### Safety texts

Every plan shows: “general guidance for healthy adults, not medical advice…”. With pregnancy, high blood pressure, hernia, age ≥ 65 or BMI ≥ 35 the note is highlighted and asks to see a doctor first. Each limitation adds a short tip, plus “pain is a signal to stop”.

## Storage and backups

* `ProgramSetup` is a record of kind `setup` in the records table (no Room migration needed).
* `UserSnapshot.setup` and `DailyGoals.fat/carbs` are new optional fields, omitted from JSON when empty.
* Backup schema **11**. Older files (1–10) decode with no setup and no fat/carb goals; nothing else changes.

## Tests

* `NutritionPlanTest` — formula, goal factors, caps, safe minimum, pregnancy, under 18, BMI reference weight, weeks to target, validation.
* `TrainingProgramTest` (Robolectric, real catalog) — rule ids exist, equipment/level filter, every limitation removes its exercises, days/times for every goal × days × minutes, goal-shaped weeks, determinism, tips and the doctor note.
* `SetupPersistenceTest` — apply in one go, invalid answers change nothing, schema 11 round-trip, schema 10 restore.
* `ProgramSetupFlowTest` (instrumented) — new user runs the setup and gets 2210 kcal on the overview; profile entries open their screens. Existing instrumented tests were updated for the new Profile navigation.
