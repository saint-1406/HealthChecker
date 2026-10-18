package az.healthchecker.backup
import az.healthchecker.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID
class BackupCodecTest {
    private fun snapshot()=UserSnapshot(profile=Profile(initialWeight=80.0,currentWeight=77.0),goals=listOf(DailyGoals("2026-10-18",2200.0)),entries=listOf(DiaryEntry(UUID.randomUUID().toString(),"missing-catalog-food","Яйцо",Quantity(2.0,Measure.PIECES,50.0),100.0,Nutrients(143.0,12.56),instant="2026-10-18T08:00:00Z",zoneId="Asia/Baku",localDate="2026-10-18")),onboarded=true)
    @Test fun preservesAllPersonalDataAndMissingCatalogReference() { val s=snapshot();assertEquals(s,BackupCodec.decode(BackupCodec.encode(s))) }
    @Test fun duplicateIdsRejected() { val s=snapshot();assertThrows(IllegalArgumentException::class.java){BackupCodec.encode(s.copy(entries=s.entries+s.entries))} }
    @Test fun futureSchemaAndBrokenJsonRejected() { val b=BackupCodec.encode(snapshot()).toString(Charsets.UTF_8);assertThrows(IllegalArgumentException::class.java){BackupCodec.decode(b.replace("\"schemaVersion\":1","\"schemaVersion\":2").toByteArray())};assertThrows(IllegalArgumentException::class.java){BackupCodec.decode(b.dropLast(8).toByteArray())} }
    @Test fun oversizedAndNegativeRejected() { assertThrows(IllegalArgumentException::class.java){BackupCodec.decode(ByteArray(25*1024*1024+1))};val s=snapshot();assertThrows(IllegalArgumentException::class.java){BackupCodec.encode(s.copy(entries=listOf(s.entries.single().copy(grams=-1.0))))} }
}
