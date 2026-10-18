package az.healthchecker.domain
import org.junit.Assert.*
import org.junit.Test
class GoalCalculatorTest {
    @Test fun adultMaintenance() { assertEquals(2136.0,estimateMaintenance(Profile(age=30,heightCm=180.0,currentWeight=80.0))!!,0.01) }
    @Test fun minorHasNoAdultEstimate() { assertNull(estimateMaintenance(Profile(age=17))) }
    @Test fun invalidProfileRejected() { assertThrows(IllegalArgumentException::class.java){estimateMaintenance(Profile(currentWeight=Double.NaN))} }
}
