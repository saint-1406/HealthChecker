package az.healthchecker.domain

import org.junit.Assert.*
import org.junit.Test

class QrContentTest {
    private val gtin = normalizeBarcode("4006381333931")

    @Test fun plainDigitsAreAProduct() {
        assertEquals(QrContent.Product(gtin), parseQr(" 4006381333931 "))
    }

    @Test fun gs1DigitalLinkIsAProduct() {
        assertEquals(QrContent.Product(gtin), parseQr("https://id.gs1.org/01/04006381333931"))
        assertEquals(QrContent.Product(gtin), parseQr("https://brand.example.com/product/01/04006381333931/10/ABC?17=261231"))
        assertEquals(QrContent.Product(gtin), parseQr("https://example.az/01/4006381333931"))
    }

    @Test fun gs1ElementStringIsAProduct() {
        assertEquals(QrContent.Product(gtin), parseQr("(01)04006381333931(17)261231"))
        assertEquals(QrContent.Product(gtin), parseQr("010400638133393117261231"))
    }

    @Test fun invalidCheckDigitIsNotAProduct() {
        assertTrue(parseQr("4006381333932") is QrContent.Text)
        assertTrue(parseQr("https://id.gs1.org/01/04006381333932") is QrContent.Text)
    }

    @Test fun shoppingListLosesBulletsNumbersAndDuplicates() {
        val list = parseQr("Список:\n- Молоко\r\n* хлеб\n1. Яйца\n2) Сыр\n[ ] Гречка\n[x] Молоко\n\n☐ Яблоки")
        assertEquals(QrContent.ItemList(listOf("Список:", "Молоко", "хлеб", "Яйца", "Сыр", "Гречка", "Яблоки")), list)
    }

    @Test fun listIsBounded() {
        val items = (parseQr((1..300).joinToString("\n") { "item $it " + "x".repeat(300) }) as QrContent.ItemList).items
        assertEquals(MAX_QR_ITEMS, items.size)
        assertTrue(items.all { it.length <= MAX_QR_ITEM_LENGTH })
    }

    @Test fun linkIsTextWithUrl() {
        val text = parseQr("https://www.example.com/page?a=1") as QrContent.Text
        assertEquals("https://www.example.com/page?a=1", text.url)
        assertFalse(text.receipt)
        assertEquals("example.com", qrTitle(text))
    }

    @Test fun ekassaReceiptIsRecognized() {
        val text = parseQr("https://monitoring.e-kassa.gov.az/#/index?doc=ABCDEF123") as QrContent.Text
        assertTrue(text.receipt)
    }

    @Test fun oneLineTextHasNoUrl() {
        val text = parseQr("WIFI:S:Home;T:WPA;P:secret;;") as QrContent.Text
        assertNull(text.url)
        assertEquals("WIFI:S:Home;T:WPA;P:secret;;", qrTitle(text))
    }

    @Test fun linesWithLinksStayText() {
        assertTrue(parseQr("Наш сайт\nhttps://example.com") is QrContent.Text)
    }

    @Test fun emptyQrIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { parseQr("  \n ") }
    }
}
