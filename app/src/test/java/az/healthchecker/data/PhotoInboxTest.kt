package az.healthchecker.data
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import az.healthchecker.domain.InboxPhoto
import az.healthchecker.domain.PhotoKind
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.ZoneId
@RunWith(RobolectricTestRunner::class) @Config(sdk=[34])
class PhotoInboxTest {
    private val inbox=PhotoInbox(ApplicationProvider.getApplicationContext<Context>())
    @Test fun namesCarryKindAndMomentAndRejectOtherFiles() {
        val p=InboxPhoto(PhotoKind.MEDICINE,1_761_300_000_000)
        assertEquals("med-1761300000000.jpg",p.fileName);assertEquals(p,InboxPhoto.parse(p.fileName))
        assertNull(InboxPhoto.parse("food-1.jpg.tmp"));assertNull(InboxPhoto.parse("../food-1.jpg"));assertNull(InboxPhoto.parse("photo.jpg"))
        // 2025-10-24 10:00 UTC is already the next day in Baku only after 20:00 UTC.
        assertEquals("2025-10-24",p.date(ZoneId.of("Asia/Baku")));assertEquals("2025-10-25",InboxPhoto(PhotoKind.FOOD,1_761_336_000_000).date(ZoneId.of("Asia/Baku")))
    }
    @Test fun keepsListsNewestFirstChangesKindAndDeletes() {
        val a=inbox.add(PhotoKind.FOOD,1_000,byteArrayOf(1,2,3));val b=inbox.add(PhotoKind.FOOD,1_000,byteArrayOf(4))
        // The same millisecond does not overwrite the first photo.
        assertEquals(1_001,b.takenAt);assertEquals(listOf(b,a),inbox.list());assertArrayEquals(byteArrayOf(1,2,3),inbox.read(a))
        val med=inbox.setKind(a,PhotoKind.MEDICINE)
        assertEquals(PhotoKind.MEDICINE,med.kind);assertEquals(listOf(b,med),inbox.list());assertNull(inbox.read(a));assertArrayEquals(byteArrayOf(1,2,3),inbox.read(med))
        inbox.delete(med);inbox.delete(b);assertTrue(inbox.list().isEmpty())
    }
}
