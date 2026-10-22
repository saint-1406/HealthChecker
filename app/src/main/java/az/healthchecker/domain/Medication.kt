package az.healthchecker.domain
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

@Serializable enum class MedKind { MEDICINE, VITAMIN, SUPPLEMENT }
@Serializable enum class MedForm { TABLET, CAPSULE, DROPS, LIQUID, POWDER, SPRAY, INJECTION, GUMMY, OTHER }
@Serializable enum class StrengthUnit { MG, MCG, G, IU, ML, PERCENT }
@Serializable enum class FoodTiming { ANY, BEFORE, WITH, AFTER, EMPTY_STOMACH }
/** One active substance with its strength per unit of the form, e.g. cholecalciferol 2000 IU per capsule. */
@Serializable data class ActiveSubstance(val name:String,val amount:Double?=null,val unit:StrengthUnit=StrengthUnit.MG)
/**
 * The card of a medicine, vitamin or supplement. [dose] is how many units of [form] are taken at once
 * (1 tablet, 10 drops, 5 ml); [times] are planned clock times "HH:mm", optional.
 */
@Serializable data class Medication(
    val id:String,val name:String,val kind:MedKind=MedKind.MEDICINE,val form:MedForm=MedForm.TABLET,
    val substances:List<ActiveSubstance> = emptyList(),val dose:Double=1.0,val timesPerDay:Int=1,val times:List<String> = emptyList(),
    val timing:FoodTiming=FoodTiming.ANY,val startDate:String?=null,val endDate:String?=null,
    val purpose:String="",val prescribedBy:String="",val notes:String="",val archived:Boolean=false
)
/** A taken dose; the name is a snapshot so history survives deleting the card. */
@Serializable data class MedicationIntake(val id:String,val medicationId:String,val name:String,val dose:Double,val instant:String,val zoneId:String,val localDate:String)

fun Medication.activeOn(date:String):Boolean = !archived && (startDate==null||startDate<=date) && (endDate==null||date<=endDate)
data class MedicationDay(val medication:Medication,val intakes:List<MedicationIntake>) { val done:Boolean get() = intakes.size>=medication.timesPerDay }
/** Cards in course on [date] plus any card that has an intake that day, in name order. */
fun UserSnapshot.medicationDay(date:String):List<MedicationDay> {
    val taken=medicationIntakes.filter{it.localDate==date}.sortedBy{it.instant}
    return medications.filter{it.activeOn(date)||taken.any{t->t.medicationId==it.id}}.sortedBy{it.name.lowercase()}
        .map{m->MedicationDay(m,taken.filter{it.medicationId==m.id})}
}

fun validateMedication(m:Medication) {
    require(m.name.isNotBlank()&&m.name.length<=200){tr("Введите название препарата","Enter the name")}
    require(m.dose.isFinite()&&m.dose>0&&m.dose<=1000){tr("Проверьте, сколько принимать за раз","Check the amount per dose")}
    require(m.timesPerDay in 1..24){tr("Приёмов в день может быть от 1 до 24","Doses per day must be 1 to 24")}
    require(m.times.size<=24&&m.times.all{runCatching{LocalTime.parse(it)}.isSuccess}){tr("Время приёма — в формате 08:00","Use 08:00 for dose times")}
    require(m.substances.size<=30&&m.substances.all{it.name.isNotBlank()&&it.name.length<=200&&(it.amount==null||it.amount.isFinite()&&it.amount>0&&it.amount<=1_000_000)}){tr("Проверьте действующие вещества","Check the active substances")}
    m.startDate?.let(LocalDate::parse);m.endDate?.let(LocalDate::parse)
    require(m.startDate==null||m.endDate==null||m.startDate<=m.endDate){tr("Курс заканчивается раньше, чем начинается","The course ends before it starts")}
    require(m.purpose.length<=500&&m.prescribedBy.length<=200&&m.notes.length<=2000){tr("Слишком длинный текст","The text is too long")}
}
fun validateIntake(t:MedicationIntake) {
    require(UUID.fromString(t.id).toString()==t.id&&t.medicationId.length<=200&&t.name.isNotBlank()&&t.name.length<=200)
    require(t.dose.isFinite()&&t.dose>0&&t.dose<=1000);Instant.parse(t.instant);ZoneId.of(t.zoneId);LocalDate.parse(t.localDate)
}
fun makeIntake(medication:Medication,date:String,now:Instant,zone:ZoneId):MedicationIntake =
    MedicationIntake(UUID.randomUUID().toString(),medication.id,medication.name,medication.dose,now.toString(),zone.id,date)
