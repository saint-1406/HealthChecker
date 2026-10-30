package az.healthchecker.domain
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID
class ExploreTest {
    @Test fun rejectsInvalidCoordinatesAndDanglingOffers() {
        assertThrows(IllegalArgumentException::class.java){validateExplore(ExploreSnapshot(places=listOf(SavedPlace(UUID.randomUUID().toString(),"X",GeoPoint(Double.NaN,0.0)))))}
        assertThrows(IllegalArgumentException::class.java){validateExplore(ExploreSnapshot(findings=listOf(Finding(UUID.randomUUID().toString(),"Soap",offers=listOf(FindOffer(UUID.randomUUID().toString()))))))}
    }
    @Test fun distanceHasPhysicalUnitsAndIgnoresDatelineWrapping() {
        assertEquals(111195.0,geoDistanceMeters(GeoPoint(0.0,0.0),GeoPoint(0.0,1.0)),5.0)
        assertEquals(22239.0,geoDistanceMeters(GeoPoint(0.0,179.9),GeoPoint(0.0,-179.9)),5.0)
    }
}
