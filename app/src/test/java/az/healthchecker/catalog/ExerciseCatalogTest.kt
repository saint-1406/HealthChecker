package az.healthchecker.catalog
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import az.healthchecker.domain.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
@RunWith(RobolectricTestRunner::class) @Config(sdk=[34])
class ExerciseCatalogTest {
    private val catalog by lazy{ExerciseCatalog(ApplicationProvider.getApplicationContext<Context>())}
    @Test fun bundledHomeExercisesLoad() {
        val all=catalog.exercises;assertEquals(229,all.size);assertEquals(all.size,all.map{it.id}.toSet().size)
        assertTrue(all.all{it.met in 1.0..15.0&&it.metCode.length==5&&it.cue.isNotBlank()&&it.level in 1..3})
        assertEquals(37,all.count{it.equipment==Equipment.DUMBBELLS})
    }
    @Test fun searchByRussianOrEnglishAndFilters() {
        assertTrue(catalog.search("отжимания",null,null,false).any{it.id=="pushup"})
        assertTrue(catalog.search("push-up",null,null,false).any{it.id=="pushup"})
        assertTrue(catalog.search("",Muscle.BACK,ExerciseCategory.STRENGTH,false).none{it.equipment==Equipment.DUMBBELLS})
        assertTrue(catalog.search("тяга",Muscle.BACK,null,true).any{it.id=="db_bent_row"})
    }
    @Test fun videoTitlesFindTheirExerciseFirst() {
        mapOf("OH Squat" to "prisoner_squat","Jog on the Spot" to "jog_in_place","High Plank SH Tap" to "plank_shoulder_tap","Heel Touch" to "standing_heel_touch",
            "T-Raise" to "prone_t_raise","Plank Pike" to "forearm_plank_pike","High Plank & Pike" to "plank_pike","Air Squat" to "squat","Jump Rope" to "skip_imitation",
            "wide squat" to "sumo_squat","mountain climber toe taps" to "climber_toe_tap").forEach{(q,id)->assertEquals(q,id,catalog.search(q,null,null,true).first().id)}
    }
    @Test fun titlesTheUserAskedForAfterV7FindTheirExerciseFirst() {
        // Spelled as the user wrote them, typo in "squad" included.
        mapOf("oblique crunch" to "oblique_crunch","arm walking" to "inchworm","Table top crunch" to "tabletop_crunch","Cross knee to elbow" to "standing_cross_crunch",
            "Cross toe touches" to "cross_toe_touch","Negative squad" to "squat_negative","prone pull-up" to "prone_pullup","high plank toe in out" to "high_plank_toe_in_out",
            "Front toe touch" to "front_toe_touch","пресс с наклоном в разные стороны" to "oblique_crunch").forEach{(q,id)->assertEquals(q,id,catalog.search(q,null,null,true).first().id)}
    }
    @Test fun titlesOfTheThirtyMinuteVideoFindTheirExerciseFirst() {
        mapOf("Single Arm Circles" to "arm_circles","Arm Raises" to "arm_raises","Side Toe Touch" to "cross_toe_touch","Side Lunge" to "side_lunge","Reverse Lunge Knee Up" to "reverse_lunge_knee_up",
            "Side to Side Squat" to "side_step_squat","Single Leg Bridge" to "single_leg_bridge","Push Up & Knee Up" to "pushup_knee_up","Prone Pull Up" to "prone_pullup",
            "Push Up & T-Rotation" to "pushup_t_rotation","Reverse Snow Angel" to "snow_angel","Crunches" to "crunch","Leg Press" to "lying_leg_press","T-Rotation" to "lying_knee_rotation",
            "Plank & Twist" to "plank_hip_twist","Plank Hold" to "plank","Squat & Jack" to "squat_and_jack","Slow Burpee" to "half_burpee","A-Skip" to "a_skip",
            "Standing T-Raise" to "standing_t_raise").forEach{(q,id)->assertEquals(q,id,catalog.search(q,null,null,true).first().id)}
    }
    @Test fun chapterTitlesOfTheChannelFindTheirExerciseFirst() {
        mapOf("McGill Crunch" to "mcgill_crunch","Star Crunch" to "star_crunch","Seated Twist" to "russian_twist","L-Sit Up" to "situp","Mountain Climber Twist" to "cross_mountain_climber",
            "Standing Crunch" to "standing_knee_crunch","Hip Raise" to "hip_raise","Extended Crunch" to "extended_crunch","V-Sit In & Out" to "v_sit_in_out",
            "Side Knee to Elbow" to "standing_side_crunch","Push Up & Pike" to "pushup_pike","Woodchop" to "woodchop","Plank Reach" to "plank_reach")
            .forEach{(q,id)->assertEquals(q,id,catalog.search(q,null,null,true).first().id)}
    }
    @Test fun builtInVideosPointAtCatalogExercises() {
        val videos=VideoLibrary(ApplicationProvider.getApplicationContext<Context>()).videos
        // The user's two videos first (timed from the video itself), then 141 more of the channel by the author's chapters.
        assertEquals(listOf("eoY6qlkp1xA","jFvQl1boyM0"),videos.take(2).map{it.youtubeId});assertTrue(videos.take(2).none{it.embeddable||it.own||it.byChapters})
        assertEquals(143,videos.size);assertEquals(141,videos.count{it.byChapters});assertEquals(2,videos.count{it.embeddable})
        videos.forEach{v->v.segments.forEach{s->assertNotNull(s.exerciseId,catalog.byId(s.exerciseId));assertTrue(s.workSeconds in 1..s.seconds)};assertTrue(v.segments.zipWithNext().all{(a,b)->a.end<=b.start});validateVideo(v.copy(id="video:check"))}
        assertEquals(1922,videos.sumOf{it.segments.size})
        val thirty=videos.first()
        assertEquals(listOf(34,32,1812),listOf(thirty.segments.size,thirty.workDone().size,thirty.durationSec))
        // Jumping jacks open the warm-up and close the cardio: one entry with two sets, 30 s and 40 s.
        assertEquals(VideoWork("jumping_jacks",2,70),thirty.workDone().single{it.exerciseId=="jumping_jacks"})
        assertEquals(24,videos[1].segments.size)
    }
    @Test fun keywordsMusclesAndDirectionsNarrowTheSearch() {
        assertTrue(catalog.search("приседания",null,null,false).take(3).all{it.name.startsWith("Присед")})
        assertTrue(catalog.search("пресс",null,null,false).isNotEmpty()&&catalog.search("пресс",null,null,false).all{Muscle.CORE in it.muscles})
        assertTrue(catalog.search("разминка",null,null,false).all{it.category==ExerciseCategory.WARMUP})
        assertTrue(catalog.search("планка таз",null,null,false).map{it.id}.containsAll(listOf("plank_pike","forearm_plank_pike")))
        assertEquals("skip_imitation",catalog.search("скакалка",null,null,false).first().id)
        assertTrue(catalog.search("отжиманий",null,null,false).any{it.id=="pushup"})
        assertEquals(21,catalog.search("",null,ExerciseCategory.WARMUP,false).size)
    }
    @Test fun everyBeginnerHomePlanCanBeBuilt() {
        val plan=recommend(catalog.exercises,UserSnapshot(),"2026-10-19",TrainingPrefs(false,1))
        assertEquals(4,plan.items.count{it.role==Role.MAIN});assertTrue(plan.items.all{it.exercise.equipment==Equipment.NONE&&it.exercise.level==1})
        val strong=recommend(catalog.exercises,UserSnapshot(),"2026-10-19",TrainingPrefs(true,3))
        assertEquals(6,strong.items.count{it.role==Role.MAIN})
    }
    @Test fun everyExerciseHasAGroundedMotionScheme() {
        catalog.exercises.forEach{e->
            val m=catalog.figure(e.id);assertNotNull(e.id,m)
            assertTrue(e.id,m!!.frames.size in 1..6);assertEquals(e.id,m.frames.size==1,m.hold)
            m.frames.forEach{f->
                val points=groundedJoints(f).points
                val bottom=maxOf(points.filterKeys{it!="head"}.values.maxOf{it.y},points.getValue("head").y+Body.HEAD)
                assertEquals(e.id,-f.lift,bottom,1e-4f)
            }
        }
    }
}
