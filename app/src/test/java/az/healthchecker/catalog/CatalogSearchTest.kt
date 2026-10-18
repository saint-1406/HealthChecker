package az.healthchecker.catalog
import org.junit.Test
import org.junit.Assert.*
class CatalogSearchTest {
    @Test fun russianAndAzerbaijaniVariants() { assertEquals(normalizeSearch("İŞIQ"),normalizeSearch("ışıq"));assertEquals(normalizeSearch("Орёх"),normalizeSearch("орех")) }
}
