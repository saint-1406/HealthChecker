package az.healthchecker.domain
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

fun validateSnapshot(s:UserSnapshot) {
    require(s.favoriteFoods.size<=10000 && s.favoriteFoods.all{it.length in 1..200} && s.savedMeals.size<=200)
    require(s.savedMeals.map{it.id}.toSet().size==s.savedMeals.size)
    s.savedMeals.forEach(::validateSavedMeal)
    require(s.entries.size<=50000 && s.customFoods.size<=10000 && s.goals.size<=10000 && s.completedDays.size<=50000 && s.workouts.size<=50000 && s.trainingDays.size<=50000) { tr("Слишком много записей","Too many records") }
    fun unique(ids:List<String>) { require(ids.size==ids.toSet().size) { tr("Повторяющиеся идентификаторы","Duplicate identifiers") } }
    unique(s.entries.map{it.id});unique(s.customFoods.map{it.id});unique(s.goals.map{it.effectiveFrom});unique(s.workouts.map{it.id})
    unique(s.customFoods.mapNotNull{it.barcode})
    s.customFoods.forEach{f->require(f.barcode==null||normalizeBarcode(f.barcode)==f.barcode)}
    s.profile?.let { estimateMaintenance(it);require(it.initialWeight.isFinite() && it.initialWeight in 2.0..500.0) }
    fun nutrient(n:Nutrients,max:Double) { listOf(n.kcal,n.protein,n.fat,n.carbs,n.fiber).forEach{require(it==null || it.isFinite() && it in 0.0..max)} }
    s.goals.forEach { g -> LocalDate.parse(g.effectiveFrom);listOf(g.kcal,g.protein,g.waterMl).forEach { require(it==null || it.isFinite() && it>0 && it<=100000) } }
    s.completedDays.forEach{LocalDate.parse(it)}
    s.customFoods.forEach{ f -> require(f.id.startsWith("custom:") && f.name.isNotBlank() && f.name.length<=200 && f.aliases.size<=30);nutrient(f.per100g,1000.0);require(f.per100g.kcal!=null);require(f.density==null || f.density.isFinite() && f.density>0 && f.density<=20);f.portions.forEach{require(it.grams.isFinite() && it.grams>0 && it.grams<=200000)}
        f.recipe?.let{r->
            require(r.ingredients.size in 1..100 && (r.cookedGrams==null || r.cookedGrams.isFinite() && r.cookedGrams>0 && r.cookedGrams<=200000) && (r.servings==null || r.servings in 1..100) && r.icon.length in 1..20)
            r.ingredients.forEach{i->require(i.name.isNotBlank() && i.name.length<=500 && i.foodId.length<=200 && i.grams.isFinite() && i.grams>0 && i.grams<=100000 && i.quantity.value.isFinite() && i.quantity.value>0 && i.quantity.value<=100000);nutrient(i.nutrients,1000000.0)}
        } }
    s.entries.forEach { e ->
        require(UUID.fromString(e.id).toString()==e.id);require(e.name.isNotBlank() && e.name.length<=500 && e.foodId.length<=200)
        require(e.grams.isFinite() && e.grams>0 && e.grams<=100000 && e.waterMl.isFinite() && e.waterMl in 0.0..100000.0)
        require(e.quantity.value.isFinite() && e.quantity.value>0 && e.quantity.value<=100000)
        if(e.quantity.unit==Measure.PIECES)require(e.quantity.portionGrams!=null && e.quantity.portionGrams.isFinite() && e.quantity.portionGrams>0)
        nutrient(e.nutrients,1000000.0);Instant.parse(e.instant);ZoneId.of(e.zoneId);LocalDate.parse(e.localDate)
    }
    s.workouts.forEach { w ->
        require(UUID.fromString(w.id).toString()==w.id);require(w.name.isNotBlank() && w.name.length<=200 && w.exerciseId.length<=100 && w.muscles.size<=Muscle.entries.size)
        require(w.minutes.isFinite() && w.minutes>0 && w.minutes<=600 && w.met.isFinite() && w.met>0 && w.met<=25)
        require((w.sets==null || w.sets in 1..50) && (w.reps==null || w.reps in 1..1000) && (w.seconds==null || w.seconds in 1..3600) && (w.loadKg==null || w.loadKg.isFinite() && w.loadKg>0 && w.loadKg<=200))
        require((w.bodyWeightKg==null || w.bodyWeightKg.isFinite() && w.bodyWeightKg in 2.0..500.0) && (w.kcal==null || w.kcal.isFinite() && w.kcal in 0.0..20000.0))
        Instant.parse(w.instant);ZoneId.of(w.zoneId);LocalDate.parse(w.localDate)
    }
    s.trainingDays.keys.forEach{LocalDate.parse(it)}
    s.trainingPrefs?.let{require(it.level in 1..3)}
    require(s.medications.size<=1000 && s.medicationIntakes.size<=100000 && s.steps.size<=50000) { tr("Слишком много записей","Too many records") }
    unique(s.medications.map{it.id});unique(s.medicationIntakes.map{it.id});unique(s.steps.map{it.date})
    s.medications.forEach{require(it.id.startsWith("med:") && it.id.length<=100);validateMedication(it)}
    s.medicationIntakes.forEach(::validateIntake)
    s.steps.forEach(::validateSteps)
    require(s.weights.size<=50000 && s.exerciseImages.size<=MAX_EXERCISE_IMAGES) { tr("Слишком много записей","Too many records") }
    unique(s.weights.map{it.date})
    s.weights.forEach(::validateWeight)
    require(s.videos.size<=1000) { tr("Слишком много записей","Too many records") }
    unique(s.videos.map{it.id})
    s.videos.forEach(::validateVideo)
    s.exerciseImages.forEach{(id,data)->
        require(EXERCISE_ID.matches(id) && data.length<=MAX_IMAGE_BASE64)
        val bytes=java.util.Base64.getDecoder().decode(data)
        require(bytes.size>=4 && bytes[0]==0xFF.toByte() && bytes[1]==0xD8.toByte()) { tr("Картинка упражнения повреждена","An exercise picture is damaged") }
    }
}
const val MAX_EXERCISE_IMAGES=300
/** About 1 MB of JPEG; pictures are stored at most 1280 px on the long side, which is far below this. */
const val MAX_IMAGE_BASE64=1_400_000
val EXERCISE_ID=Regex("[a-z0-9_]{1,100}")
