package az.healthchecker.explore

import org.junit.Assert.*
import org.junit.Test

class GpsTrackFilterTest {
    private fun fix(lat: Double = 40.4, lon: Double = 49.8, seconds: Long = 1, accuracy: Double = 5.0) =
        GpsFix(lat, lon, seconds * 1_000, seconds * 1_000_000_000, accuracy)

    @Test fun firstFixStartsSegmentAndStationaryJitterDoesNotAccumulate() {
        val filter = GpsTrackFilter()
        assertTrue(filter.accept(fix())!!.breakBefore)
        assertNull(filter.accept(fix(lat = 40.400002, seconds = 3)))
        assertNull(filter.accept(fix(lat = 40.400001, seconds = 6)))
        assertFalse(filter.accept(fix(lat = 40.4001, seconds = 10))!!.breakBefore)
    }

    @Test fun poorAccuracyBreaksNextSegmentRatherThanBridgingMissingVisit() {
        val filter = GpsTrackFilter()
        filter.accept(fix())
        assertNull(filter.accept(fix(lat = 40.4002, seconds = 3, accuracy = 36.0)))
        assertTrue(filter.accept(fix(lat = 40.4004, seconds = 6))!!.breakBefore)
    }

    @Test fun overlappingTwentyMeterAccuracyCirclesDoNotCountStationaryDrift() {
        val filter = GpsTrackFilter()
        filter.accept(fix(accuracy = 20.0))
        // About 15.6 m north, comfortably inside the uncertainty of two stationary fixes.
        assertNull(filter.accept(fix(lat = 40.40014, seconds = 5, accuracy = 20.0)))
        assertNull(filter.accept(fix(lat = 40.39986, seconds = 9, accuracy = 20.0)))
    }

    @Test fun pauseAndLongGpsGapBreakSegments() {
        val filter = GpsTrackFilter()
        filter.accept(fix())
        filter.breakSegment()
        assertTrue(filter.accept(fix(lat = 40.4001, seconds = 4))!!.breakBefore)
        assertTrue(filter.accept(fix(lat = 40.4005, seconds = 90))!!.breakBefore)
    }

    @Test fun impossibleJumpAndNonMonotonicFixCannotCreateTravel() {
        val filter = GpsTrackFilter()
        filter.accept(fix())
        assertNull(filter.accept(fix(lat = 41.0, seconds = 3)))
        assertTrue(filter.accept(fix(lat = 40.4001, seconds = 6))!!.breakBefore)
        assertNull(filter.accept(fix(lat = 40.4002, seconds = 5)))
        assertTrue(filter.accept(fix(lat = 40.4003, seconds = 9))!!.breakBefore)
    }

    @Test fun invalidCoordinatesAndAccuracyAreRejected() {
        val filter = GpsTrackFilter()
        assertNull(filter.accept(fix(lat = Double.NaN)))
        assertNull(filter.accept(fix(lat = 91.0, seconds = 2)))
        assertNull(filter.accept(fix(seconds = 3, accuracy = -1.0)))
        assertNull(filter.accept(fix(seconds = 4, accuracy = Double.NaN)))
        assertTrue(filter.accept(fix(seconds = 5))!!.breakBefore)
    }

    @Test fun visitedCellsNeverFillTheLineAcrossBreaks() {
        val cells = VisitedCells(cellMeters = 50.0)
        cells.add(40.4, 49.8, true)
        val first = cells.size
        cells.add(40.41, 49.8, true)
        assertEquals(first + 1, cells.size)
        cells.add(40.411, 49.8, false)
        assertTrue(cells.size > first + 2)
    }
}
