[Русский](README.md) · **English**

# VitaCore

**VITACORE · by OCTO** — health and wellness. VitaCore is part of the OCTO ecosystem: OCTO is the publisher, and every product has a name of its own (OCTO Atlas — IT automation and infrastructure, OCTO Folio — reading and notes, OCTO VitaCore — health and physical condition). Until version 1.24.0 the app was called HealthChecker.

VitaCore is a standalone Android app for a food diary and home workouts. Android 8.0 or newer. The interface is in Russian or English, with an offline food catalog in both languages: calories, protein, fat, carbohydrate, fibre and drinking water; your own dishes made from ingredients; medicines and vitamins with a card for each; steps with a calorie estimate; steps, sleep, workouts and heart rate from a Xiaomi watch through Health Connect; a weight log with a trend; 245 home exercises in both languages with animated movement diagrams; a workout screen with a rest timer and a stopwatch; 307 workout videos (Jaeho and Mady Morrison: YouTube or your own file) with exercise timelines that are logged to the diary after you check the list; a program set up from a questionnaire (calories, macros, water, workouts per week); and updates from inside the app. No sign-up; the internet is used only to check for updates. Barcodes are looked up in the full bundled Open Food Facts database; YouTube links open in an external app.

## Download

| What | Where |
| --- | --- |
| **Install the app or update it** | **[Download the latest version](https://github.com/saint-1406/VitaCore/releases/latest)** → under **Assets** pick the `.apk` file. `HealthChecker-update-vN.apk` is the full VitaCore installer and also works for a first install; the file keeps its old name so that installed versions keep finding updates. The `.sha256` file holds the checksum and does not need to be installed. |
| **Archived base installer v6** | [HealthChecker-base-v6.apk](https://github.com/saint-1406/VitaCore/raw/main/releases/HealthChecker-base-v6.apk) (2.4 MB), checksum in [SHA256SUMS.txt](releases/SHA256SUMS.txt). For a first install we recommend the latest release above. |

Every APK in the releases contains the whole app. There is no need to install the base version or the updates in between.

**1.24.1:** OCTO octopus app icon (vector, with Android 13+ themed icons). [Details](docs/releases/v1.24.1.md) (in Russian).
**1.24.0:** HealthChecker renamed VitaCore (by OCTO); compact screen tops (a one-row header, a slim Day / Favorites switch), dense rows in the diary and in training. [Details](docs/releases/v1.24.0.md).
**1.23.0:** food categories with quick navigation (fast food, coffee and tea, drinks, alcohol, ingredients and more), a compact search list, the notice can be swiped away in any direction. [Details](docs/releases/v1.23.0.md).
**1.22.0:** KFC Baku menu in the catalog — 55 items with calories and macros, found by their Azerbaijani receipt names. [Details](docs/releases/v1.22.0.md).
**1.21.0:** strength progress — last time's result is filled in, a "Step up" button, personal records and estimated 1RM, a chart per exercise, sets with an automatic rest timer. [Details](docs/releases/v1.21.0.md).
**1.20.0:** weight from smart scales (Mi Fitness → Health Connect) goes into the weight log by itself and sharpens the energy expenditure; on Xiaomi phones the Reminders section offers to lift battery limits and turn on autostart. [Details](docs/releases/v1.20.0.md).
**1.19.0:** "Energy expenditure" — what you really burn, from the diary and the weight trend, with a new calorie target; "Reminders" — medicines with a "Taken" button, water, weigh-in, workouts and logging food in the evening. [Details](docs/releases/v1.19.0.md).

**1.18.0:** a Favorites tab in Training (exercises and workout videos) and in the Diary (foods), plus "Often done / Often eaten" suggestions. [Details](docs/releases/v1.18.0.md).

**1.17.0:** Xiaomi watches through Mi Fitness and Health Connect: steps (they replace the phone's count), sleep, workouts and heart rate in Profile → Watch and Health Connect. [Details](docs/releases/v1.17.0.md), [how it works](docs/health-connect.md).

**1.16.7:** the workout video list shows the approximate kcal of the whole session (for your weight) instead of a play icon. [Details](docs/releases/v1.16.7.md).

**1.16.6:** a shorter profile — a summary and sections instead of a long list, the inventory opens from a button; a program set up from a questionnaire (goal, limitations, equipment, activity) works out calories, macros and water and picks workouts; updates from inside the app; 164 Mady Morrison videos; editing the exercise list before a video workout is logged. [Details](docs/releases/v1.16.6.md).

**1.16.5:** the catalog is cleared of American meat-cut variants, baby food and exotic items; added 4,062 prepared dishes, 147 Baku and home-cooking dishes, 39 typical shelf products and 617 Bazarstore products. [Details](docs/releases/v1.16.5.md).

**1.16.4:** the location search uses every available system source, including the fused one, accepts a fresh approximate fix with its accuracy and time, shows its progress and can be cancelled. [Details](docs/releases/v1.16.4.md).

**1.16.3:** QR codes — a product code, a shopping list, a link or text — and a network source for "My place". [Details](docs/releases/v1.16.3.md).

**1.16.2:** findings (including non-food products), photos, favorites and places of purchase; a built-in map of Azerbaijan; GPS walks started by a button, with pause and history; ZIP backups with photos and routes. The map and the catalog work offline. [Details](docs/explore.md).

The release notes and the other documents in `docs/` are in Russian.

How to install on Android 8.0 or newer:

1. Open the link on the phone and download the APK.
2. Open the downloaded file. If Android asks, allow installing apps from this browser or file manager.
3. Google Play Protect may warn that the app comes from an unknown developer: the app is not from Google Play, tap "More details" → "Install anyway".
4. To update, open **Profile → About → Updates → "Check for updates" → "Update"**: the app downloads the latest release's APK, checks its SHA-256 and signing key and opens Android's installer ([details](docs/updates.md)). You can also do it by hand: download the APK from the latest release (a higher number than the installed one) and open it over the app. The diary is kept. Do not uninstall the app before updating.
5. The installed version is shown at the bottom of **Profile → About** ("VitaCore · version N", before 1.24.0 "HealthChecker · version N") and in Android settings → Apps → VitaCore (formerly HealthChecker; from v11 on, earlier versions show "1.0").

No account is needed and the data stays on the phone. The built-in catalog, scanner, map, diary and your own videos work offline. The INTERNET permission is used only for updates: the app contacts GitHub when it checks for and downloads a new version (from the button, or once a day if left on) and never sends diary data anywhere. Health Connect permissions give read-only access to steps, sleep, workouts, heart rate and weight while the app is open ([details](docs/health-connect.md)). A YouTube link opens only in an external app, when you tap it.

## Quick food logging

**Product scanner (v16.1).** Open food logging and tap the barcode icon to the right of the search. Choose **"Barcode"** or **"QR code"**, point the camera at the code, pick a photo of it or type the digits. The app first looks among your own cards, then in the bundled Open Food Facts database. The network is not used. If the product is missing, tap "Add manually". For a card that does not match there is "Wrong product? Add manually".

**QR codes (1.16.3).** A QR code holding a product code (GS1: digits, `(01)…` or a `…/01/<code>` link) is looked up like a barcode. A QR code holding a list (shopping, for example) is matched against the catalog: a found food is logged with the "Log" button. Other text or a link can be copied or opened in an external app. In Findings a list from a QR code becomes several findings, and text becomes the note of a new finding. e-kassa receipt links are recognised, but the receipt's contents live on the server and cannot be read offline — the receipt opens in the browser.

The APK includes a full food snapshot of [Open Food Facts](https://huggingface.co/datasets/openfoodfacts/product-database) from 29 Oct 2026: every card with a valid GTIN, not only those tagged with Azerbaijan. The exact number of imported cards and known nutrition values, the gaps and SHA-256 are in the [report](app/src/main/assets/catalog/barcodes/report.json). It is the available database, not a promise that every product in Baku is there. The ordinary name search uses the built-in catalog (about 10,500 foods, 617 of them Bazarstore products with their labels); the multi-million card database is reached through the scanner or barcode digits. On the first lookup the database is unpacked into the phone's private storage; later lookups use an index without loading every card into memory.

Check the name, brand, calories and macros against the package: community data may be incomplete or wrong. Unknown fields stay empty; values are never guessed. You can photograph the product or pick a photo from the gallery. Once confirmed, your own card with its photo is kept on the phone and goes into backup format 9; the whole bundled catalog is not copied into the diary backup. The photo is shown on the portion page. Backups of formats 1–8 are read. The photo is reduced to 960 px and 180 KiB; the backup limit is 25 MB overall.

The derived OFF database is distributed under ODbL 1.0, individual contents under DbCL 1.0. The full SQLite in gzip is a separate file in the release; compressed parts are in the repository. The [importer](tools/catalog/build_barcodes.py), the [source notice](app/src/main/assets/catalog/barcodes/NOTICE.txt) and the license texts are included. OFF product images were not included; photos are added by the user.

State whether the values are per 100 g or per 100 ml. For values per 100 ml the app asks for the weight of those 100 ml in grams, so that they convert honestly into the mass-based model. Water density is not assumed. If the package size uses other units than the nutrition values, the package size is entered by hand. The camera needs permission; without it the gallery and manual entry remain. This is a barcode scanner: nutrition tables are not read from photos.

The source of imported cards is Open Food Facts; the data is available under the [ODbL](https://opendatacommons.org/licenses/odbl/1-0/); a link to the source is kept on the card. Barcodes are decoded with [ZXing Android Embedded](https://github.com/journeyapps/zxing-android-embedded), Apache-2.0, with no downloaded model and no Google Play Services.

On the Overview, food and water actions sit right under the energy card. The diary offers recent foods, favorites and named **saved meals**. The star in the search adds a food to favorites; filters search among all, favorite or your own foods, and category tiles (fast food, coffee and tea, drinks, alcohol, ingredients and more) jump straight to a shelf. Labels help tell dry, raw and cooked foods apart; local dishes with an averaged composition are marked as estimates.

One food is logged at once. **"Add another"** builds a meal: the foods stay separate entries, portions can be changed before **"Log all"**, and undo removes the whole last meal. An unfinished meal is kept on the device between launches. It can be saved as a meal, such as "My breakfast", and repeated with changed portions. A saved meal can also be made from selected diary entries.

The app remembers the last portion of each food. Switching between grams, millilitres and pieces keeps the mass when the density and the weight of a piece are known; an unknown weight has to be entered. Water is added in portions of 200, 250 or 500 ml. In the profile you can hide the weight, steps and training cards for a more compact overview.

The save status shows the data on the phone and the backup separately. Favorites, saved meals, barcodes and product photos go into backup format 9; an unfinished meal and interface settings stay on this device. After the app is uninstalled, restoring needs a backup file created beforehand in a folder the user chose.

## Installing and updating

In the `dist` folder of a local build (the repository's `releases/` holds only the base installer; updates are published as GitHub releases):

| File | What it is |
| --- | --- |
| `HealthChecker-base-v3.apk`, `HealthChecker-base-v6.apk` | base installers: the first version given out, 3 (archive), and the current base v6 |
| `HealthChecker-update-v4.apk`, `…-v5.apk`, … | updates; the number in the name is the versionCode |
| `SHA256SUMS.txt` | checksums of every file |
| `HealthChecker.version` | the number of the last update given out |

Copy the APK to the phone and open it; when Android asks, allow installing from the file manager you use. For a new install you can take the latest update straight away. To update, open the file with the higher number **over the installed app**: the diary is kept. Updates in between can be skipped. Do not uninstall the previous install to update, and do not install a lower number over a higher one — Android will refuse.

All files are signed with one key; the applicationId stays `az.healthchecker.app`.

## First steps

1. Choose the language — "Русский" or "English" (it can be changed later in **Profile → Look and language**). Enter your age, sex for the formula, height, weight and activity, or tap "Start without the estimate".
2. In the profile you can fill in an approximate maintenance calorie target or set every goal by hand. Set protein and water yourself; an empty field means no goal.
3. In the diary find a food and choose grams, pieces or — for liquids — millilitres. With pieces, check **the weight of one piece**. Add each thing you ate separately.
4. The Overview shows the chosen calendar day and the last seven days. Older entries and goals are reached by date. This is not a rolling last 24 hours.
5. Weigh yourself in the morning and tap **"Log"** on the **"Weight"** card of the Overview: the card shows the latest weight, the change over the week and the direction — down, up or stable.
6. Tap the **"Energy for the day"** card: a window shows the day's protein, fat, carbohydrate and fibre, and the same for every entry. In the diary each entry also shows "P · F · C · Fb".

Raw and cooked foods are different. 50 g of dry oats is not 50 g of cooked porridge. Tap an entry to correct it; a correction does not move the entry to another day. Deleting and quick water can be undone with "Undo" at the bottom of the screen. "Recent foods" in the diary fill in the amount from the last entry.

At the end of the day mark "Day complete" to count even an empty day in the averages. Days without entries and days where the calories of some foods are unknown are left out of the average.

## Protein

Tap the **"Protein"** card on the Overview to open a screen of its own:
- today: protein eaten, how much is left to the goal, protein's share of the day's calories and grams per kilogram of body weight;
- a chart by day for 7 or 30 days with the goal line, the average over days with complete data and the number of days the goal was met; average grams per kilogram (adults are usually advised from 0.8 g/kg, 1.2–2 g/kg with strength training);
- the main protein sources over the period;
- **protein foods** per 100 g: search by name and by grams of protein — "at least", "about" (±10 %) or "at most"; sort "more → less", "less → more" and "more per 100 kcal". By default the list holds everyday foods and your own; the "Whole catalog" switch searches the whole catalog (about 10,500 foods). Tapping a food opens its diary entry; after saving you come back to the protein screen.

## Photos for later

No time to log right now — take a photo. In the **Diary**, next to "Log food", the camera button offers "Photograph food", "Photograph a medicine" or "Add from gallery". While photos wait, the diary shows a **"Photos for later"** card. A photo is kept inside the app (it is not in the phone's gallery), reduced to 2048 pixels, and waits; the Overview reminds you that "N photos are waiting". The app needs no camera permission: the system camera app takes the picture.

When you have time, tap the photo: the top half of the screen is the picture (pinch or double-tap to zoom, the "⤢" button enlarges it to most of the screen), the bottom half is the usual form. For food, find the product or **create your own from the label**, copying the numbers from the photo, and add a portion; for a medicine, mark the dose or create its card. Everything is logged on the day the photo was taken. After "Done" the photo can be deleted (with undo) or kept. There is no text recognition or counting from photos: offline and without neural networks it is unreliable, so you copy the numbers. Photos for later are not in the backup.

## Look

**Profile → Look and language → "Themes, colors and background"**:
- **mode** — follow the system, light or dark;
- **interface color** — "Mint" (the original), "Ocean", "Lavender", "Berry", "Coral", "Amber", "Forest", "Graphite" or **your own color** with a hue slider. The whole palette is built from the color: buttons, switches, charts, screen and card backgrounds; text contrast is checked automatically (a test checks every palette and 24 hues against WCAG in light and dark mode);
- **holiday themes** switch on by themselves: **Halloween** from 25 October to 7 November (pumpkin orange and purple, an almost black background at night), **New Year** from 26 December to 8 January (red and pine green on a snowy or night-blue background). Choosing another color during a holiday turns its theme off until the next holiday; a holiday theme can be tried on any day, and switching them on automatically can be turned off;
- **decorations and animation**: falling snow and a blinking garland at New Year, a moon, bats, cobwebs and pumpkins at Halloween; they are pale, sit behind the cards and can be turned off;
- **your own background** — any picture from the gallery behind every screen (kept only in the app, reduced to 1920 pixels), with "Dim the picture" and "Card density" sliders so the text stays readable;
- "Restore the standard look".

The look is kept on the phone and is not in the backup.

At the top of the **diary** is the day's summary: calories against the goal as a ring, a bar splitting the energy between protein, fat and carbohydrate, grams of each, and fibre with water. Each entry has a badge (a drop for water, a plate for your dishes, otherwise the first letter), the time it was logged and the same macro bar. The "Photos for later" camera is the button next to "Log food".

## Dishes and inventory

**Profile → Inventory** keeps the dishes you cooked — each in its own cell with an icon. The "New dish" cell (or "+ Dish from ingredients" in the food search) opens the builder: a name, an icon, ingredients in grams or pieces (the weight of a piece is shown and can be edited). For example, chicken fillet 500 g, carrots, potatoes 5 pcs, a bunch of herbs.

The app adds up the calories and macros of the ingredients. If you enter **the weight of the cooked dish** (weigh it without the pot), the values per 100 g follow it: water boils off or is absorbed while cooking. Without it the raw ingredients are used. You can enter the number of servings — a "1/N" portion appears. If a nutrient is unknown for one ingredient, it is shown as unknown for the dish too.

Tap a dish in the inventory: a window shows its composition and nutrition. There you can enter grams and tap **"Eat"** — the entry goes to the diary for the chosen day — or **"Delete the dish"**. The dish is also found in the ordinary food search. Deleting or changing a dish does not change food already logged: every entry keeps its own values.

## Training

The **Training** tab: the "Trained / Rest day" mark for the chosen day (logged exercises mark the day by themselves), the list of what was done and today's suggestions with exercise pictures and a **"Start the workout"** button. In the tab's settings say whether you have dumbbells and choose your level.

**The "Workout now" screen** opens from that card. Each exercise of the set is a card of its own: a movement picture, the Russian and English name, sets and reps, the full technique, the rest between sets. "Log it" saves the exercise exactly as suggested (undo in the notice at the bottom), "Adjust" opens the exercise form. At the top: how much of the set is done and an estimate of time and calories. The bottom of the screen always shows a **rest timer** (30 s, 60 s, 90 s, 2 min, "+15 s", pause) and a **stopwatch**; timed exercises have a "Timer 30 s" button for the length of a set. Three seconds before the end the timer beeps briefly, at the end there is a long tone and vibration (the sound goes through media volume and is heard in headphones over a video). The screen stays on while it is open. The set is built from past days, so exercises you log do not reshuffle it.

**Workout videos** — the "Videos" button on the Training tab, right under "Add exercise". The videos themselves are not in the app: it holds exercise timelines, and the video plays from your file or opens in YouTube. The library has **307 workouts**: 143 from the "재호 - Fitness" (Jaeho) channel and 164 from Mady Morrison. The two you train with are timed from the videos themselves: "30 MIN FULL BODY WORKOUT with WARM UP" (34 exercises: a 9 × 30 s warm-up, then legs, upper body, abs and cardio at 40 s of work and 20 s of rest) and "25 MIN FULL BODY WORKOUT AT HOME" (3 rounds of 8 exercises, 40/20 s). For each, the timeline says which exercise starts at which second, its name on the video's screen and the catalog exercise with its movement diagram. The video screen shows what is on now and how much is left ("Now · Squat Knee to Elbow, 0:23") or "Rest · next: …"; tapping an exercise in the list moves the video to it.

- **141 more channel videos** are timed from the chapters the author lists in the description ("00:07 air squat, 00:35 reverse lunge…"): which exercise starts at which second. A chapter runs to the next one and includes the rest around the exercise, so the working time for the diary is estimated — 20 s less than a one-minute chapter, 10 s less than a short one. Videos with gym equipment (bench, rings, pull-up bar, fitball) and with chapters that could not be matched to an exercise were left out (14 of 156). The list searches by title and by exercise ("mcgill", "burpee"); filters — full body, abs, cardio, legs, upper body, dumbbells, no jumping, warm-up, length and "My videos". Every link opens in the external YouTube app or a browser; the timeline in the app follows a timer.
- **Your own file from the phone.** "Attach your own file" opens the system file picker; the video plays right in the app, offline, together with the timeline (pause, seek, full screen). The app keeps only a link to the file; the file stays where it is. After reinstalling the app the file has to be attached again.
- **YouTube.** "In YouTube" opens the link in an external app. Start the "Timeline timer" together with the video; "−10 s"/"+10 s" help to keep them in sync. There is no built-in network player.
- **Your own videos.** "YouTube link" adds any video by its link (watch, youtu.be, shorts), "File from the phone" — a video file. Your own videos have no timeline: they are logged as a "Video cardio workout" (moderate or vigorous) by minutes.
- **Edit a video** — the pencil in the header: title, description and YouTube link. The built-in workout's timeline is kept. "Restore the original" brings back the app's card. Edits go into the backup.
- **Offline.** The app does not download videos. For local playback attach your own file; links open outside VitaCore.
- **"Log the workout to the diary".** The whole video or only up to the current position: each exercise of the timeline goes into "Done" for the chosen day, and a repeated exercise (jumping jacks in the warm-up and in cardio) becomes one more set. The time is the working time from the timeline, rest is not counted; reps are estimated from the exercise's tempo; calories are worked out like for any entry, from MET and weight. Logging the whole 30-minute workout is undone with one "Undo" button.

**Movement pictures** are drawn by the app itself for all 245 exercises: a stick figure that repeats the movement through key poses (the far arm and leg are paler, dumbbells are short bars in the hands). They are the app's own and not taken from other databases (the generator is `tools/exercises/figures.py`). A picture you add replaces the diagram.

The catalog holds home exercises without equipment and with dumbbells. 16 were added from the Jaeho channel's exercise lists: McGill curl-up, glute bridge with legs up, star, extended crunch, single-leg raises lying, cross-body mountain climber, seated knee tuck, twisting crunches, plank with arm reach, knee-to-elbow plank, push-up and V-up, explosive push-ups, standing side knee-to-elbow, standing knee raise to elbows, plank with toe touch, bodyweight woodchopper. 11 were added from the 30-minute video workout: standing T arm raise, lateral arm raises, side-step squats, reverse lunge with knee raise, push-up with knee tuck, T-rotation push-up, lying leg press, lying knee rotations (T-Rotation), plank with hip rotation, squat and jumping jack, A-skip. Directions: warm-up, strength, aerobic, endurance, interval (HIIT), stretching, yoga and Pilates, balance. Every exercise has a Russian and an English name and a technique description; the card shows both names and, where the exercise is known from the workout videos, the name used in the video too (for example "Prisoner squat · OH Squat").

The search understands Russian and English names, names from videos, everyday words ("abs", "skipping rope", "warm-up"), muscles and direction; the best matches come first, word forms are handled. Filters: muscles, direction, dumbbells.

In an exercise's card you can **add your own picture** — a screenshot from a video or a photo that reminds you of the technique. It is chosen through the system photo picker (no permissions), reduced to 1280 pixels, kept in the app and included in the backup; it can be opened full screen, replaced or removed.

**Strength progress (1.21.0).** A new entry starts from what you did last time; "Step up" suggests the next small step (+1 rep, then a heavier dumbbell); personal records (heaviest dumbbell, estimated 1RM by Epley, most reps, longest set) show as soon as you beat them and as trophies in the day's list; **Training → Progress** lists every exercise done in sets with a chart, records and all sessions. On "Workout now", "Set 1/3 done" starts the rest timer by itself.

A **stopwatch** is on the workout screen and in the exercise card: start, pause, reset. While it runs, the screen stays on, and the time keeps counting when you move between screens (while you watch a video, for example). In the exercise card the "Use" button moves the time into the field: into the seconds of a set for timed exercises, into the total time for the others.

Energy burned is an estimate from the 2024 Adult Compendium of Physical Activities: **kcal ≈ MET × body weight × time**. For sets the time is estimated from the tempo and a one-minute rest and can be edited. Body weight is needed: the latest logged weight, otherwise the profile's. Workout calories are shown separately and do not raise the food goal.

The suggestions put together a warm-up, 4–6 exercises for muscle groups not worked yesterday, cardio while the weekly guideline is not reached, and a cool-down. The WHO guideline for adults: 150 minutes of moderate aerobic activity a week (vigorous minutes count double) and strength exercises on at least 2 days. "Another set" changes the exercises. This is not a medical program: with pain or health conditions ask a doctor.

## Weight

The **"Weight"** card on the Overview: the latest logged weight and its date, the change over a week and a month, the change from the profile's starting weight and a 90-day chart. A morning weight swings by a kilogram with water and salt, so the direction compares **7-day averages** with the previous week's; changes under 0.2 kg show as "stable". With less than two weeks of data the first and last weight of the week are compared. One entry per day: a second one on the same day replaces the first. Tap the card to open the history with a chart, where an extra entry can be deleted (with undo). A new weight becomes the profile's current weight (the starting weight does not change) and is used for workout and step calories. Weigh-ins from a smart scale come in by themselves through Health Connect (1.20.0).

## Medicines and vitamins

In the diary, under food, is the **"Medicines and vitamins"** section. "Add a medicine" opens a card: type (medicine, vitamin, supplement), name, form, active substances with their doses (for example vitamin D3 2000 IU in a capsule), how much to take at a time and how many times a day, dose times, before/with/after food or on an empty stomach, a course with start and end dates, what it is for, who prescribed it and notes. While the course runs, the medicine is shown in every day's diary: **"Taken"** marks a dose with its time, the counter shows "Taken: 1 of 2", and an extra mark can be removed. Since 1.19.0 **Profile → Reminders** can send a notification at the dose times, with a "Taken" button right in the notification. The app does not check doses or interactions.

## Steps

The **"Steps"** card on the Overview opens the chosen day's steps. A number can be entered by hand (from a watch, for example) — it replaces the phone's count for that day. Calories use the profile's height and weight: walking 3.8 MET (Compendium 2024, code 17190) × weight × time, with a step length of ≈ 0.415 × height and a speed of 5 km/h. They are shown separately and do not change the food goal.

The **"Count steps automatically"** switch turns on a pedometer on the phone's built-in sensor, like Google Fit: offline and with almost no battery use. It needs the "Physical activity" permission (Android 10+); while counting is on, a quiet notification stays in the shade — without it Android does not let apps receive steps in the background. Steps are counted from the moment it is switched on, and counting goes on after a reboot. On phones without a step sensor, manual entry remains. If a watch is connected through Health Connect (**Profile → Watch and Health Connect**), its daily total replaces the phone's count.

## Keeping data after uninstalling

Since **1.16.2** the app also keeps your own findings (including non-food products), photos and places of purchase, an offline map of Azerbaijan and the GPS history of walks. Details: [findings and walks](docs/explore.md). New backups are written as **ZIP**, including photos and confirmed route points. Either a ZIP or an older JSON can be chosen for restoring. An old JSON backup replaces all data: findings and walks that are not in it are cleared. During a walk, make a manual backup to save the part already recorded; automatic backups run when cards change and when a recording starts, pauses and ends, not on every GPS point.

Android deletes the internal database when the app is uninstalled. To be able to restore, open **Profile → Backup → Choose a folder** beforehand and pick a separate folder in internal storage, for example `Documents/VitaCore`. Make sure the time of a successful backup is shown and there is no message about unsaved changes.

Once the folder is connected, the app saves a backup when something changes, while it is running. The file is first written separately and checked by reading it back; the previous good backup is kept. If access is withdrawn, storage runs out or the app is closed before the write finishes, the last successful backup stays, and the newest changes may not be in it yet. The status and the error text are shown in the profile, and the Overview shows the warning "The backup is not being updated"; there is a button to retry by hand. If access to the folder is lost, choose the same folder again — a backup is written at once. The folder keeps the two latest checked backups.

After reinstalling, choose **"I already have a backup"** on the start screen, or **Profile → Restore from a file**. Pick the latest good `VitaCore-….json` (`HealthChecker-….json` for backups before 1.24.0), check the number of entries and confirm replacing the current diary. Then **choose the folder again** for the next backups: Android drops the permission when the app is uninstalled.

Backups hold your profile, diary, dishes, favorites, saved meals, barcodes and product photos, workouts, exercise pictures, weight, medicines with their dose marks, steps, and the videos you added and your edits to the built-in ones (titles, descriptions, YouTube links) as readable JSON (pictures are inside the file as JPEG). Attached video files are not in the backup: they are links to files on this phone. Backups of earlier versions (formats 1–8) are restored by the new version too; version 15 and older cannot read format 9 (v16: product photos). Keep them in a safe place of your choice. Cloud sync and Android Auto Backup are not used. Without a saved external backup, restoring after uninstalling is impossible. The theme setting and the folder permission are not in the backup.

## Catalog and accuracy

12,754 foods, about 10,500 of them in search: 7,793 from USDA SR Legacy (about 2,200 — American meat-cut variants, baby food and exotic items — are hidden from search but still open from old entries), 4,062 prepared dishes from USDA FNDDS, 147 Baku and home-cooking dishes from recipes, 39 typical shelf products (the median of their labels), 617 Bazarstore products, 41 restaurant items and sushi, and 55 items of the KFC Baku menu ([sources](docs/catalog-sources.md), in Russian). Every food and every portion has a Russian and an English name: the English one is USDA's original, the Russian one is its translation (the values were not changed). Every food is on a shelf (fast food, coffee and tea, drinks, alcohol, ingredients and others), taken from the source's own category. The search understands both languages, Azerbaijani words (also without ə, ş, ç) and word forms; everyday foods — boiled chicken breast, espresso, double espresso, tea, cola, cottage cheese, sour cream and more — come first. Fibre is given for most foods; for entries made before it was added, it is filled in from the catalog.

Baku restaurant dishes — beef with pomegranate (with and without chestnuts), boiled khinkali, chicken popcorn, fried dushbara, tempura shrimp, sausages, Antep pistachios — are estimates from the data that was sent: calories, protein and fat are taken as given, carbohydrate and fibre come from USDA ingredients or as the energy left over; how every value was obtained is written on the food's card. When the exact brand is missing, create your own food from its label. This is not a complete catalog of the shops of Azerbaijan.

KFC Baku (1.22.0): KFC Azerbaijan publishes no nutrition values. Where KFC Ukraine publishes the same item, its weight and energy are kept and protein and carbohydrate come from the composition (USDA, including its analyses of KFC chicken) with fat as the remainder; the other items are estimated from their kfc.az description. Count and size variants (3/6/9 pcs, S/M/L, small/medium/basket) are portions of one card.

Sushi as served in Baku sushi bars — 32 items: Philadelphia, California, Alaska, Canada, Dragon, Green Dragon, Bonito, Kani, Chuka, Chips and spicy cheese rolls, baked rolls with salmon and crab, hot (tempura) rolls with shrimp, salmon and chicken; maki with salmon, shrimp, cucumber, avocado, tuna and eel; nigiri, gunkan, sashimi, chuka salad and miso soup. They are easiest to log **in pieces**: the weight of a piece is filled in, and there is a "Roll of 8" portion (6 for maki). For the 12 rolls from the set that was sent, calories, protein and carbohydrate are the middle of the given ranges per piece and fat is the energy left over; the other items are worked out from the typical composition of a piece from USDA ingredients (rice with sugar, nori, fish, cheese, sauces, batter and frying oil). Soy sauce, ginger, wasabi and edamame come first. Restaurants differ — the weight of a piece can be corrected.

Open Food Facts is included as a separate full offline barcode database; the USDA catalog stays separate. Details, the limits of regional equivalents and how to reproduce the import: [sources](docs/catalog-sources.md).

Food values are averages, portions in pieces are approximate. Unknown nutrients are shown as a dash, partial sums with a special mark. The water goal counts only drinking water. Calories by Mifflin–St Jeor are a guide for adults maintaining their weight; the automatic estimate is not used under 18.

## Building from source

Requires JDK 17, Android SDK 36 and Build Tools 36.0.0. The SDK is set in a local `local.properties` with a line like `sdk.dir=C\:/path/to/sdk` (the colon is escaped, otherwise lint stops the build). In this working copy the SDK, the emulator and the API 36 image are in `.local/android-sdk`, the test AVD in `.local/avds`. The Gradle wrapper is included.

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
python -m unittest discover -s tools/catalog
python -m unittest discover -s tools/exercises
python tools/exercises/videos.py
.\gradlew.bat :app:connectedDebugAndroidTest
.\scripts\build-apk.ps1 -VersionCode 19 -VersionName 1.16.3 -Publish -NotesFile docs\releases\v1.16.3.md
```

The last command checks the unit tests, lint, signature, applicationId and versionCode of the release build, then puts the new `dist/HealthChecker-update-v<number>.apk` next to the previous ones (the base version and older updates are left alone), appends its SHA-256 to `dist/SHA256SUMS.txt` and the number to `dist/HealthChecker.version`. `dist/HealthChecker-update-v<number>.apk.sha256` is written next to it. With `-Publish` (after `git push` to `main`) the script creates the GitHub release `v<number>` with this APK and its checksum; `-NotesFile` takes the release description from a file and appends the APK's SHA-256; the GitHub CLI is needed, signed in once with `gh auth login`. The base installer in `releases/` changes only by a separate decision. The internal versionCode must be higher than the one given out (16.1 — code **17**, 1.16.2 — **18**, 1.16.3 — **19**), otherwise Android will not install the update over the old one; the script checks this. On an error the previous APK in `dist` is not replaced.

The local permanent key of the current install is in `.local/signing/healthchecker.jks`; the password is in `.local/signing/password.txt`, the build configuration in `signing.properties`. These files are excluded from Git. **Keep the key and the password separately:** without the previous key an installed APK cannot be updated. Do not publish them with the source.

For a signature of your own, create a keystore and a local `signing.properties` with `storeFile` (an absolute path), `storePassword`, `keyAlias`, `keyPassword`. Changing the signature requires uninstalling the old install; save the diary first.

Checks and limits of the current build are recorded in [docs/testing.md](docs/testing.md) (in Russian).
