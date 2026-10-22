# Translation guide for the bundled catalog

The app shows every food, portion and exercise in Russian or English. Source texts are the original
USDA FoodData Central (SR Legacy) English descriptions; Russian texts are translations of them. Nutrition
values are never touched here.

## Food names (English → Russian)

- Natural, concise Russian a shopper would recognise. Keep the USDA structure "main food, qualifiers" with
  commas, main noun first: `Beef, chuck, blade roast, separable lean only, trimmed to 0" fat, choice, cooked,
  braised` → `Говядина, лопатка, только мякоть, жир срезан полностью, категория Choice, тушёная`.
- Never drop a qualifier: many items differ by one word (raw/cooked, with/without salt, skin, fat trim,
  enriched, drained). Never add information that is not in the English text.
- Adjectives agree with the head noun: `Potatoes, boiled` → `Картофель варёный`; `Eggs, raw` → `Яйцо, сырое`.
- Use ё where standard: варёный, тушёный, жаренный/жареный, сушёный, копчёный, обезжиренный, солёный.
- Cooking: raw — сырой; cooked — приготовленный; boiled — варёный; stewed, braised — тушёный; simmered —
  отварной; roasted, baked — запечённый; broiled, grilled — на гриле; fried — жареный; pan-fried — жаренный на
  сковороде; deep-fried — во фритюре; steamed — на пару; microwaved — в микроволновке; dry heat — сухой жар;
  moist heat — влажный жар; heated — разогретый.
- Common qualifiers: without salt — без соли; with salt added — с солью; enriched / unenriched — обогащённый /
  необогащённый; canned — консервированный; drained solids — без жидкости; frozen — замороженный; dehydrated,
  dried — сушёный; prepared with water — приготовленный на воде; ready-to-eat — готовый к употреблению;
  NFS — без уточнения; all commercial varieties — все товарные сорта; separable lean only — только мякоть;
  separable lean and fat — мякоть с жиром; with skin / without skin — с кожей / без кожи; meat only — только мясо.
- Brand and restaurant names stay in Latin as written (McDONALD'S, BURGER KING, KELLOGG'S, KRAFT); translate the
  product description around them. Keep codes such as UPC as they are.
- Measures: keep numbers; `1/8" fat` → `жир 1/8 дюйма`; percentages stay (`2% milkfat` → `жирность 2%`).
- Beef grades: choice / select / prime / all grades → категория Choice / Select / Prime / все категории.
- Latin species names in parentheses stay as they are.
- Up to 200 characters.

## Portion names (English → Russian)

Short labels shown as "label: 28,4 г". `oz` → унция; `fl oz` → жидк. унция; `lb` → фунт; `cup` → чашка;
`tbsp`, `tablespoon` → ст. ложка; `tsp`, `teaspoon` → ч. ложка; `slice` → ломтик; `piece` → кусок;
`serving` → порция; `NLEA serving` → порция NLEA; `medium` → средний; `large` → крупный; `small` → маленький;
`steak` → стейк; `fillet` → филе; `can` → банка; `jar` → банка; `bar` → батончик; `package` → упаковка.
Inches: `(2-3/8" dia)` → `(диаметр 2-3/8 дюйма)`. Keep the meaning of yield notes:
`piece, cooked, excluding refuse (yield from 1 lb raw meat with refuse)` →
`кусок, приготовленный, без отходов (выход из 1 фунта сырого мяса с отходами)`.

## Exercises (Russian → English)

Name: the usual English gym name in sentence case (`Приседания сумо` → `Sumo squat`). Cue: a faithful, plain
English instruction of the same length; keep safety notes.
