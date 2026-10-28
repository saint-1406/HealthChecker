package az.healthchecker.domain
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
class WorkoutVideoTest {
    private fun seg(start:Int,end:Int,id:String,title:String=id)=VideoSegment(start,end,id,title)
    // Shaped like the 30-minute video: a 17 s ready screen, 30 s warm-ups with 5 s gaps, then 40 s of work and 20 s of rest.
    private val video=WorkoutVideo("yt:test","Test",durationSec=200,segments=listOf(seg(17,47,"arm_circles"),seg(52,82,"jumping_jacks"),seg(100,140,"squat"),seg(160,200,"jumping_jacks")))
    private fun ex(id:String,measure:ExerciseMeasure,secondsPerRep:Double?=null)=Exercise(id,id,muscles=listOf(Muscle.LEGS),equipment=Equipment.NONE,kind=ExerciseKind.STRENGTH,level=1,met=3.8,metCode="02022",measure=measure,secondsPerRep=secondsPerRep)
    private val zone=ZoneId.of("Asia/Baku");private val now=Instant.parse("2026-10-28T08:00:00Z")

    @Test fun youtubeLinksOfEveryKindGiveTheId() {
        listOf("https://www.youtube.com/watch?v=eoY6qlkp1xA","https://youtube.com/watch?feature=share&v=eoY6qlkp1xA&t=17s","https://youtu.be/eoY6qlkp1xA?si=abc","youtu.be/eoY6qlkp1xA",
            "https://m.youtube.com/watch?v=eoY6qlkp1xA","https://www.youtube.com/shorts/eoY6qlkp1xA","https://www.youtube.com/embed/eoY6qlkp1xA","https://www.youtube-nocookie.com/embed/eoY6qlkp1xA",
            "https://www.youtube.com/live/eoY6qlkp1xA?feature=shared"," eoY6qlkp1xA ").forEach{assertEquals(it,"eoY6qlkp1xA",youtubeId(it))}
        listOf("https://vimeo.com/123456789","https://www.youtube.com/watch?v=short","https://www.youtube.com/channel/UCPwxWbDnHIbOv0HqpNXOvUQ","https://evil.example/watch?v=eoY6qlkp1xA","","кошка").forEach{assertNull(it,youtubeId(it))}
        assertEquals("https://www.youtube.com/watch?v=eoY6qlkp1xA&t=352s",youtubeWatchUrl("eoY6qlkp1xA",352))
        assertEquals("https://www.youtube.com/watch?v=eoY6qlkp1xA",youtubeWatchUrl("eoY6qlkp1xA"))
    }
    @Test fun momentTellsTheRunningExerciseOrTheRestBeforeTheNext() {
        video.moment(0).let{assertNull(it.current);assertEquals("arm_circles",it.next!!.exerciseId);assertEquals(17,it.left);assertEquals(0,it.index)}
        video.moment(17).let{assertEquals("arm_circles",it.current!!.exerciseId);assertEquals(30,it.left);assertEquals("jumping_jacks",it.next!!.exerciseId)}
        video.moment(46).let{assertEquals(1,it.left)}
        video.moment(47).let{assertNull(it.current);assertEquals(5,it.left);assertEquals(1,it.index)}
        video.moment(199).let{assertEquals(3,it.index);assertNull(it.next)}
        assertTrue(video.moment(200).finished);assertTrue(video.moment(5000).finished)
        assertEquals(140,video.workSeconds)
    }
    @Test fun aRepeatedExerciseBecomesMoreSetsAndACutSegmentCountsItsDonePart() {
        assertEquals(listOf(VideoWork("arm_circles",1,30),VideoWork("jumping_jacks",2,70),VideoWork("squat",1,40)),video.workDone())
        // Stopped 25 s into the squats: they count; the jumping jacks after them do not.
        assertEquals(listOf(VideoWork("arm_circles",1,30),VideoWork("jumping_jacks",1,30),VideoWork("squat",1,25)),video.workDone(125))
        // 9 s into a segment is not yet a set; before the first exercise nothing is done.
        assertEquals(listOf(VideoWork("arm_circles",1,30),VideoWork("jumping_jacks",1,30)),video.workDone(109))
        assertEquals(emptyList<VideoWork>(),video.workDone(10))
        // A segment shorter than the threshold still counts once it is over.
        assertEquals(listOf(VideoWork("plank",1,8)),WorkoutVideo("yt:x","x",segments=listOf(seg(0,8,"plank"))).workDone(8))
    }
    @Test fun videoWorkGoesToTheDiaryAsSetsOfItsMeasure() {
        val reps=videoWorkout(UUID.randomUUID().toString(),ex("squat",ExerciseMeasure.REPS,3.0),VideoWork("squat",2,80),"2026-10-28",now,zone,80.0)
        // 40 s a set at 3 s a rep ≈ 13 reps; the time is the work time only: 80 s.
        assertEquals(listOf(2,13,null),listOf(reps.sets,reps.reps,reps.seconds));assertEquals(80/60.0,reps.minutes,1e-9);assertEquals(3.8*80*80/3600,reps.kcal!!,1e-9)
        val timed=videoWorkout(UUID.randomUUID().toString(),ex("jacks",ExerciseMeasure.SECONDS),VideoWork("jacks",2,70),"2026-10-28",now,zone,null)
        assertEquals(listOf(2,null,35),listOf(timed.sets,timed.reps,timed.seconds));assertNull(timed.kcal)
        val jog=videoWorkout(UUID.randomUUID().toString(),ex("jog",ExerciseMeasure.MINUTES),VideoWork("jog",2,80),"2026-10-28",now,zone,70.0)
        assertEquals(listOf(null,null,null),listOf(jog.sets,jog.reps,jog.seconds));assertEquals(80/60.0,jog.minutes,1e-9);assertEquals("2026-10-28",jog.localDate)
    }
    @Test fun ownVideoNeedsANameAndAYouTubeLinkIfAny() {
        val v=makeOwnVideo("video:1"," Утренняя ","https://youtu.be/ml6cT4AZdqI",now)
        assertEquals(listOf("Утренняя","ml6cT4AZdqI","2026-10-28T08:00:00Z"),listOf(v.title,v.youtubeId,v.addedAt));assertTrue(v.own);validateVideo(v)
        assertNull(makeOwnVideo("video:2","Файл","",now).youtubeId)
        assertThrows(IllegalArgumentException::class.java){makeOwnVideo("video:3","Имя","https://vimeo.com/1",now)}
        assertThrows(IllegalArgumentException::class.java){makeOwnVideo("video:4","  ","https://youtu.be/ml6cT4AZdqI",now)}
        assertThrows(IllegalArgumentException::class.java){validateVideo(v.copy(id="yt:ml6cT4AZdqI"))}
        assertThrows(IllegalArgumentException::class.java){validateVideo(v.copy(youtubeId="bad"))}
        assertThrows(IllegalArgumentException::class.java){validateVideo(v.copy(segments=listOf(seg(10,5,"squat"))))}
    }
    @Test fun followAlongClockJumpsAndKeepsItsState() {
        val running=Stopwatch().start(1000).seek(5000,352_000)
        assertTrue(running.running);assertEquals(353_000,running.elapsed(6000))
        val paused=Stopwatch(4000).seek(9000,60_000);assertFalse(paused.running);assertEquals(60_000,paused.elapsed(20_000))
        assertEquals(0,Stopwatch().seek(0,-5).elapsed(0))
        assertEquals(listOf("0:00","1:05","30:12","1:00:00"),listOf(0,65,1812,3600).map(::formatVideoTime))
    }
}
