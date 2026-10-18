package az.healthchecker.domain
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

fun validateSnapshot(s:UserSnapshot) {
    require(s.entries.size<=50000 && s.customFoods.size<=10000 && s.goals.size<=10000 && s.completedDays.size<=50000) { "Слишком много записей" }
    fun unique(ids:List<String>) { require(ids.size==ids.toSet().size) { "Повторяющиеся идентификаторы" } }
    unique(s.entries.map{it.id});unique(s.customFoods.map{it.id});unique(s.goals.map{it.effectiveFrom})
    s.profile?.let { estimateMaintenance(it);require(it.initialWeight.isFinite() && it.initialWeight in 2.0..500.0) }
    fun nutrient(n:Nutrients,max:Double) { listOf(n.kcal,n.protein,n.fat,n.carbs).forEach{require(it==null || it.isFinite() && it in 0.0..max)} }
    s.goals.forEach { g -> LocalDate.parse(g.effectiveFrom);listOf(g.kcal,g.protein,g.waterMl).forEach { require(it==null || it.isFinite() && it>0 && it<=100000) } }
    s.completedDays.forEach{LocalDate.parse(it)}
    s.customFoods.forEach{ f -> require(f.id.startsWith("custom:") && f.name.isNotBlank() && f.name.length<=200 && f.aliases.size<=30);nutrient(f.per100g,1000.0);require(f.per100g.kcal!=null);require(f.density==null || f.density.isFinite() && f.density>0 && f.density<=20);f.portions.forEach{require(it.grams.isFinite() && it.grams>0 && it.grams<=100000)} }
    s.entries.forEach { e ->
        require(UUID.fromString(e.id).toString()==e.id);require(e.name.isNotBlank() && e.name.length<=500 && e.foodId.length<=200)
        require(e.grams.isFinite() && e.grams>0 && e.grams<=100000 && e.waterMl.isFinite() && e.waterMl in 0.0..100000.0)
        require(e.quantity.value.isFinite() && e.quantity.value>0 && e.quantity.value<=100000)
        if(e.quantity.unit==Measure.PIECES)require(e.quantity.portionGrams!=null && e.quantity.portionGrams.isFinite() && e.quantity.portionGrams>0)
        nutrient(e.nutrients,1000000.0);Instant.parse(e.instant);ZoneId.of(e.zoneId);LocalDate.parse(e.localDate)
    }
}
