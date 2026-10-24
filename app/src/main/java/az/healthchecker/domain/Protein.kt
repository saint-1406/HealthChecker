package az.healthchecker.domain
import java.time.LocalDate
import kotlin.math.abs

/** Protein of one day: eaten ("complete" is false when part of it is unknown), the goal then in force and the day's calories. */
data class ProteinDay(val date:String,val grams:NutrientTotal,val goal:Double?,val kcal:Double,val logged:Boolean) {
    /** Share of the day's energy from protein at 4 kcal per gram; null on a day without calories. */
    val energyShare:Double? get() = if(kcal>0)grams.value*4/kcal else null
}
data class ProteinSource(val foodId:String,val name:String,val grams:Double,val times:Int)
data class ProteinStats(val days:List<ProteinDay>,val average:Double?,val goalMet:Int,val goalDays:Int,val perKg:Double?,val sources:List<ProteinSource>)

/**
 * Protein over the [days] ending on [endDate]: amounts by day, the average of logged days with a known total, how many
 * logged days with a goal reached it, that average per kg of body weight and the foods that gave the most protein.
 */
fun proteinStats(snapshot:UserSnapshot,endDate:String,days:Int,name:(DiaryEntry)->String={it.name}):ProteinStats {
    val end=LocalDate.parse(endDate)
    val states=(days-1 downTo 0).map{snapshot.day(end.minusDays(it.toLong()).toString())}
    val list=states.map{ProteinDay(it.date,it.summary.protein,it.goals?.protein,it.summary.kcal.value,isLogged(it))}
    val average=list.filter{it.logged&&it.grams.complete}.takeIf{it.isNotEmpty()}?.map{it.grams.value}?.average()
    val withGoal=list.filter{it.logged&&it.goal!=null}
    val sources=states.flatMap{it.entries}.filter{(it.nutrients.protein?:0.0)>0}.groupBy{it.foodId}
        .map{(id,entries)->ProteinSource(id,name(entries.first()),entries.sumOf{it.nutrients.protein?:0.0},entries.size)}
        .sortedByDescending{it.grams}.take(8)
    return ProteinStats(list,average,withGoal.count{it.grams.value>=it.goal!!},withGoal.size,average?.let{a->snapshot.bodyWeight(endDate)?.let{a/it}},sources)
}

enum class ProteinMatch { AT_LEAST, ABOUT, AT_MOST }
enum class ProteinSort { HIGH_FIRST, LOW_FIRST, PER_KCAL }

/** Grams of protein per 100 kcal: how much protein a food gives for its energy. */
fun Food.proteinPer100Kcal():Double? = per100g.protein?.let{p->per100g.kcal?.takeIf{it>0}?.let{p*100/it}}

/**
 * Foods by protein per 100 g. Without [grams] every food with a known protein value is listed; with it, foods with at
 * least, about (within 10 %, at least ±1 g) or at most that much. Drinking water is left out.
 */
fun proteinFoods(foods:List<Food>,grams:Double?,match:ProteinMatch,sort:ProteinSort):List<Food> {
    val found=foods.filter{f->
        val p=f.per100g.protein
        p!=null&&!f.isWater&&(grams==null||when(match){
            ProteinMatch.AT_LEAST->p>=grams
            ProteinMatch.AT_MOST->p<=grams
            ProteinMatch.ABOUT->abs(p-grams)<=maxOf(1.0,grams*0.1)
        })
    }
    return when(sort){
        ProteinSort.HIGH_FIRST->found.sortedByDescending{it.per100g.protein}
        ProteinSort.LOW_FIRST->found.sortedBy{it.per100g.protein}
        ProteinSort.PER_KCAL->found.sortedByDescending{it.proteinPer100Kcal()?:-1.0}
    }
}
