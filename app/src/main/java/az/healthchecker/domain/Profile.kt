package az.healthchecker.domain
import kotlinx.serialization.Serializable
@Serializable data class Profile(val sex: String = "male", val age: Int = 30, val heightCm: Double = 170.0, val initialWeight: Double = 70.0, val currentWeight: Double = 70.0, val activity: Double = 1.2)
@Serializable data class DailyGoals(val effectiveFrom: String, val kcal: Double? = null, val protein: Double? = null, val waterMl: Double? = null, val manual: Boolean = true)
fun estimateMaintenance(profile: Profile): Double? {
    require(profile.age in 1..120 && profile.heightCm.isFinite() && profile.heightCm in 40.0..250.0 && profile.currentWeight.isFinite() && profile.currentWeight in 2.0..500.0)
    require(profile.sex in listOf("male","female") && profile.activity in listOf(1.2,1.375,1.55,1.725,1.9))
    if(profile.age<18)return null
    return (10*profile.currentWeight+6.25*profile.heightCm-5*profile.age+if(profile.sex=="male")5 else -161)*profile.activity
}
