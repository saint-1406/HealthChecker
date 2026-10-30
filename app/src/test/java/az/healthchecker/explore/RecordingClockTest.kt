package az.healthchecker.explore

import org.junit.Assert.assertEquals
import org.junit.Test

class RecordingClockTest {
    @Test fun pauseAndResumeCountOnlyMonotonicActiveTime() {
        val clock = RecordingClock()
        clock.start(savedDurationMs = 5_000, elapsedMs = 100_000)
        assertEquals(8_000, clock.duration(103_000))
        assertEquals(9_000, clock.pause(104_000))
        assertEquals(9_000, clock.duration(500_000))
        clock.start(savedDurationMs = 9_000, elapsedMs = 500_000)
        assertEquals(11_000, clock.duration(502_000))
    }
}
