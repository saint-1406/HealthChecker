package az.healthchecker.domain
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import az.healthchecker.catalog.ExerciseCatalog
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Calories, macros and water from the questionnaire. Pure arithmetic, no catalog needed. */
class NutritionPlanTest {
    private val man=ProgramSetup("male",30,180.0,80.0,activity=1.55,goal=FitnessGoal.CUT)

    @Test fun mifflinStJeorWithAModerateDeficit() {
        // BMR 10×80 + 6,25×180 − 5×30 + 5 = 1780; × 1,55 = 2759; −20 % = 2207 → 2210 kcal.
        val n=nutritionPlan(man)
        assertEquals(1780.0,n.bmr!!,0.01);assertEquals(2759.0,n.maintenance!!,0.01);assertEquals(2210.0,n.kcal!!,0.0)
        assertEquals(160.0,n.protein!!,0.0);assertEquals(60.0,n.fat!!,0.0);assertEquals(260.0,n.carbs!!,0.0)
        // 30 ml/kg plus about 0,5 l per training hour spread over the week.
        assertEquals(2500.0,n.waterMl,0.0);assertFalse(n.floorApplied)
        assertTrue(n.weeklyChangeKg!! in -0.6..-0.4)
    }
    @Test fun maintenanceAndSurplusFollowTheGoal() {
        assertEquals(2760.0,nutritionPlan(man.copy(goal=FitnessGoal.MAINTAIN)).kcal!!,0.0)
        assertEquals(2760.0,nutritionPlan(man.copy(goal=FitnessGoal.ENDURANCE)).kcal!!,0.0)
        // +15 % would be +414 kcal: within the +500 cap.
        assertEquals(3170.0,nutritionPlan(man.copy(goal=FitnessGoal.BULK)).kcal!!,0.0)
        // A very active big man: +15 % is over 500, so the surplus is capped.
        val big=man.copy(weightKg=120.0,activity=1.9,goal=FitnessGoal.BULK);val n=nutritionPlan(big)
        assertEquals(n.maintenance!!+500,n.kcal!!,10.0)
    }
    @Test fun neverBelowTheSafeMinimum() {
        // BMR 1058, maintenance 1269: −20 % would be 1015 kcal, below 1200 for women.
        val n=nutritionPlan(ProgramSetup("female",50,155.0,50.0,activity=1.2,goal=FitnessGoal.CUT))
        assertEquals(1200.0,n.kcal!!,0.0);assertTrue(n.floorApplied)
        // A bigger deficit request never goes under the basal rate either.
        val m=nutritionPlan(man.copy(activity=1.2))
        assertTrue(m.kcal!!>=m.bmr!!)
        // When even the minimum is above maintenance, the plan stays at maintenance rather than a surplus.
        val tiny=nutritionPlan(ProgramSetup("female",70,145.0,38.0,activity=1.2,goal=FitnessGoal.CUT))
        assertTrue(tiny.kcal!!<=tiny.maintenance!!+5)
    }
    @Test fun noDeficitInPregnancyAndNoCaloriesUnder18() {
        val pregnant=ProgramSetup("female",30,165.0,65.0,activity=1.375,goal=FitnessGoal.CUT,limitations=setOf(Limitation.PREGNANCY))
        val n=nutritionPlan(pregnant);assertEquals(n.maintenance!!,n.kcal!!,5.0)
        assertTrue(n.waterMl>nutritionPlan(pregnant.copy(limitations=emptySet())).waterMl)
        val teen=nutritionPlan(man.copy(age=16))
        assertNull(teen.kcal);assertNull(teen.protein);assertNull(teen.bmr);assertTrue(teen.waterMl>=1500)
    }
    @Test fun proteinUsesAReferenceWeightWhenBmiIsHigh() {
        // 130 kg at 180 cm is BMI 40: protein is counted on 81 kg (BMI 25), not on 130 kg.
        val n=nutritionPlan(man.copy(weightKg=130.0))
        assertEquals(160.0,n.protein!!,5.0)
    }
    @Test fun weeksToTargetOnlyWhenTheDirectionMatches() {
        // About −0,5 kg a week: 5 kg take 11 weeks.
        assertEquals(11,nutritionPlan(man.copy(targetWeightKg=75.0)).weeksToTarget)
        assertNull(nutritionPlan(man.copy(targetWeightKg=85.0)).weeksToTarget)
        assertNull(nutritionPlan(man.copy(goal=FitnessGoal.MAINTAIN,targetWeightKg=75.0)).weeksToTarget)
    }
    @Test fun invalidAnswersAreRejected() {
        listOf(man.copy(age=10),man.copy(heightCm=90.0),man.copy(weightKg=Double.NaN),man.copy(limitations=setOf(Limitation.PREGNANCY)),
            man.copy(daysPerWeek=0),man.copy(minutesPerSession=200),man.copy(activity=1.3),man.copy(experience=4),man.copy(appliedOn="yesterday"))
            .forEach{s->assertThrows(s.toString(),Exception::class.java){validateSetup(s)}}
        validateSetup(man.copy(targetWeightKg=75.0,appliedOn="2026-11-07"))
    }
    @Test fun answersStartFromTheProfileAndBecomeOne() {
        val profile=Profile("female",41,168.0,90.0,82.0,1.55)
        val s=initialSetup(null,profile,TrainingPrefs(dumbbells=true,level=2))
        assertEquals("female",s.sex);assertEquals(41,s.age);assertEquals(82.0,s.weightKg,0.0);assertEquals(setOf(HomeEquipment.DUMBBELLS),s.equipment);assertEquals(2,s.experience)
        // The starting weight is kept; the current one comes from the answers.
        assertEquals(Profile("female",41,168.0,90.0,80.0,1.55),s.copy(weightKg=80.0).toProfile(profile))
        assertEquals(TrainingPrefs(true,1),ProgramSetup(equipment=setOf(HomeEquipment.KETTLEBELL)).toTrainingPrefs())
        val saved=ProgramSetup(age=50);assertSame(saved,initialSetup(saved,profile,null))
    }
}

/** Program selection from the bundled catalog: equipment, safety filters and the weekly layout. */
@RunWith(RobolectricTestRunner::class) @Config(sdk=[34])
class TrainingProgramTest {
    private val catalog by lazy{ExerciseCatalog(ApplicationProvider.getApplicationContext<Context>()).exercises}
    private fun byId(id:String)=catalog.single{it.id==id}
    private val base=ProgramSetup("male",30,180.0,80.0,activity=1.55,goal=FitnessGoal.MUSCLE,experience=3,equipment=setOf(HomeEquipment.DUMBBELLS))

    @Test fun everyRuleNamesARealExercise() {
        val ids=catalog.map{it.id}.toSet()
        assertEquals(emptySet<String>(),SAFETY_RULE_IDS-ids)
    }
    @Test fun equipmentAndLevelFilter() {
        val none=allowedExercises(catalog,base.copy(equipment=emptySet()))
        assertTrue(none.isNotEmpty());assertTrue(none.none{it.equipment==Equipment.DUMBBELLS})
        val kettlebell=allowedExercises(catalog,base.copy(equipment=setOf(HomeEquipment.KETTLEBELL))).map{it.id}
        assertTrue("db_swing" in kettlebell);assertFalse("db_biceps_curl" in kettlebell)
        assertTrue(allowedExercises(catalog,base).any{it.id=="db_biceps_curl"})
        assertTrue(allowedExercises(catalog,base.copy(experience=1)).all{it.level==1})
    }
    @Test fun limitationsRemoveUnsafeExercises() {
        fun excluded(l:Limitation,vararg ids:String) {
            val allowed=allowedExercises(catalog,base.copy(sex="female",limitations=setOf(l))).map{it.id}.toSet()
            ids.forEach{assertFalse("$it must be excluded for $l",it in allowed)}
            assertTrue("$l leaves exercises",allowed.size>40)
        }
        excluded(Limitation.KNEE,"jump_squat","burpee","forward_lunge","bulgarian_split_squat","pistol_assisted")
        excluded(Limitation.LOWER_BACK,"situp","leg_raise","db_deadlift","good_morning","russian_twist")
        excluded(Limitation.SHOULDER,"pike_pushup","db_shoulder_press","chair_dip")
        excluded(Limitation.WRIST,"pushup","knee_pushup","plank_shoulder_tap","mountain_climber")
        excluded(Limitation.NECK,"crunch","situp","bicycle_crunch")
        excluded(Limitation.HERNIA,"crunch","leg_raise","db_deadlift","jump_squat","plank")
        excluded(Limitation.HIGH_BLOOD_PRESSURE,"tabata","plank","wall_sit","decline_pushup","burpee")
        excluded(Limitation.PREGNANCY,"crunch","superman","glute_bridge","burpee","jumping_jacks","db_swing","pistol_assisted")
        // Gentle options stay available.
        val pregnancy=allowedExercises(catalog,base.copy(sex="female",limitations=setOf(Limitation.PREGNANCY))).map{it.id}
        assertTrue("walk_moderate" in pregnancy);assertTrue("bird_dog" in pregnancy);assertTrue("wall_pushup" in pregnancy)
        assertTrue(unsafeFor(byId("walk_moderate")).isEmpty())
    }
    @Test fun weekHasTheAskedDaysAndFitsTheTime() {
        for(days in 1..7)for(minutes in listOf(15,30,45,60))for(goal in FitnessGoal.entries) {
            val s=base.copy(daysPerWeek=days,minutesPerSession=minutes,goal=goal)
            val p=buildProgram(catalog,s)
            assertEquals(days,p.sessions.size)
            assertEquals(WEEK_LAYOUT.getValue(days),p.sessions.map{it.weekday})
            p.sessions.forEach{session->
                assertTrue("$goal $days×$minutes ${session.type}",session.items.any{it.role==Role.WARMUP}&&session.items.any{it.role==Role.COOLDOWN})
                assertTrue("$goal $days×$minutes ${session.type}: ${session.minutes}",session.minutes<=minutes*1.35+5)
                assertTrue("$goal $days×$minutes ${session.type}: ${session.minutes}",session.minutes>=minutes*0.5)
                assertEquals(session.items.size,session.items.map{it.exercise.id}.toSet().size)
            }
        }
    }
    @Test fun goalShapesTheWeek() {
        val strength=buildProgram(catalog,base.copy(goal=FitnessGoal.BULK,daysPerWeek=4))
        assertEquals(listOf(SessionType.UPPER,SessionType.LOWER,SessionType.UPPER,SessionType.LOWER),strength.sessions.map{it.type})
        val upper=strength.sessions.first().items.filter{it.role==Role.MAIN}.map{it.exercise.muscles.first()}
        assertTrue(upper.none{it==Muscle.LEGS||it==Muscle.GLUTES})
        // Muscle goals use more sets and dumbbells when they are at hand.
        val main=strength.sessions.first().items.first{it.role==Role.MAIN}
        assertTrue(main.prescription.sets!!>=4);assertEquals(Equipment.DUMBBELLS,main.exercise.equipment)
        val endurance=buildProgram(catalog,base.copy(goal=FitnessGoal.ENDURANCE,daysPerWeek=3,minutesPerSession=45))
        assertEquals(2,endurance.sessions.count{it.type==SessionType.CARDIO});assertTrue(endurance.weeklyCardioMinutes>=60)
        val cut=buildProgram(catalog,base.copy(goal=FitnessGoal.CUT,daysPerWeek=3,minutesPerSession=30))
        assertTrue(cut.sessions.first().items.any{it.role==Role.CARDIO})
    }
    @Test fun programNeverUsesWhatTheAnswersExclude() {
        val all=base.copy(sex="female",experience=1,equipment=emptySet(),limitations=Limitation.entries.toSet(),daysPerWeek=5,minutesPerSession=45)
        val allowed=allowedExercises(catalog,all).map{it.id}.toSet()
        val p=buildProgram(catalog,all)
        val used=p.sessions.flatMap{s->s.items.map{it.exercise}}
        assertTrue(used.isNotEmpty());assertTrue(used.all{it.id in allowed})
        assertTrue(used.none{it.equipment==Equipment.DUMBBELLS||it.level>1})
        assertTrue(p.sessions.all{it.items.isNotEmpty()})
        // Same answers, same program.
        assertEquals(p,buildProgram(catalog,all))
    }
    @Test fun exercisesRotateThroughTheWeek() {
        val p=buildProgram(catalog,base.copy(goal=FitnessGoal.MAINTAIN,daysPerWeek=3,minutesPerSession=45))
        val first=p.sessions[0].items.filter{it.role==Role.MAIN}.map{it.exercise.id}.toSet()
        val third=p.sessions[2].items.filter{it.role==Role.MAIN}.map{it.exercise.id}.toSet()
        assertTrue(first.isNotEmpty());assertTrue((first intersect third).size<first.size)
    }
    @Test fun planCarriesAdviceAndTheDoctorNote() {
        val pregnant=buildPlan(catalog,base.copy(sex="female",goal=FitnessGoal.CUT,limitations=setOf(Limitation.PREGNANCY,Limitation.KNEE)))
        assertTrue(pregnant.consultDoctor);assertTrue(pregnant.tips.any{"Беременность" in it});assertTrue(pregnant.tips.any{"Колени" in it})
        val healthy=buildPlan(catalog,base)
        assertFalse(healthy.consultDoctor);assertTrue(healthy.tips.isNotEmpty())
        assertTrue(buildPlan(catalog,base.copy(age=70)).consultDoctor)
        assertTrue(buildPlan(catalog,base.copy(equipment=setOf(HomeEquipment.PULLUP_BAR))).tips.any{"Турник" in it})
        assertTrue(buildPlan(catalog,base.copy(goal=FitnessGoal.CUT,targetWeightKg=90.0)).tips.any{"не совпадает" in it})
        assertTrue(setupDisclaimer().contains("не медицинская"))
    }
}
