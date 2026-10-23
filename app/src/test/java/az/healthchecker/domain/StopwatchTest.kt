package az.healthchecker.domain
import org.junit.Assert.*
import org.junit.Test
class StopwatchTest {
    @Test fun pausesKeepTimeAndRestartsContinue() {
        var s=Stopwatch().start(1_000);assertTrue(s.running);assertEquals(2_500,s.elapsed(3_500))
        s=s.pause(4_000);assertFalse(s.running);assertEquals(3_000,s.elapsed(99_000))
        s=s.start(10_000).pause(10_400);assertEquals(3_400,s.elapsed(20_000))
        assertEquals(s,s.pause(30_000));assertEquals(Stopwatch(),s.reset())
        assertEquals(0,Stopwatch().start(5_000).elapsed(4_000))
    }
    @Test fun formatsMinutesTenthsAndHours() {
        assertEquals("0:00.0",formatStopwatch(0));assertEquals("0:07.3",formatStopwatch(7_349));assertEquals("12:05.0",formatStopwatch(725_000))
        assertEquals("1:02:03",formatStopwatch(3_723_000))
    }
}
