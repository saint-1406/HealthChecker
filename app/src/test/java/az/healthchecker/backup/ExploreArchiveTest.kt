package az.healthchecker.backup
import az.healthchecker.domain.*
import java.io.File
import java.nio.file.Files
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.*
import org.junit.Test
class ExploreArchiveTest {
    @Test fun streamsMediaAndRejectsModifiedManifestContent() {
        val root=Files.createTempDirectory("archive").toFile()
        try {
            val photo="${UUID.randomUUID()}.jpg";val source=File(root,photo).apply{writeBytes(byteArrayOf(-1,-40,1,2,-1,-39))}
            val s=UserSnapshot(explore=ExploreSnapshot(findings=listOf(Finding(UUID.randomUUID().toString(),"Soap",photoFile=photo))))
            val zip=File(root,"backup.zip")
            zip.outputStream().use{ArchiveBackupCodec.write(s,mapOf("photos/$photo" to source),it)}
            val staged=zip.inputStream().use{ArchiveBackupCodec.read(it,File(root,"stage"))}
            assertEquals(s,staged.snapshot);assertArrayEquals(source.readBytes(),staged.media.getValue("photos/$photo").readBytes())
            val bad=File(root,"bad.zip")
            java.util.zip.ZipFile(zip).use{original->ZipOutputStream(bad.outputStream()).use{out->original.entries().asSequence().forEach{e->out.putNextEntry(ZipEntry(e.name));out.write(if(e.name.startsWith("photos/"))byteArrayOf(1,2,3) else original.getInputStream(e).readBytes());out.closeEntry()}}}
            assertThrows(IllegalArgumentException::class.java){bad.inputStream().use{ArchiveBackupCodec.read(it,File(root,"bad-stage"))}}
        } finally {root.deleteRecursively()}
    }
    @Test fun legacyJsonStillRestoresAndTraversalIsRejected() {
        val root=Files.createTempDirectory("archive").toFile()
        try {
            val old="""{"format":"healthchecker-backup","schemaVersion":1,"snapshot":{"onboarded":true}}"""
            assertTrue(ArchiveBackupCodec.read(old.byteInputStream(),File(root,"old")).snapshot.onboarded)
            val bad=File(root,"bad.zip");ZipOutputStream(bad.outputStream()).use{it.putNextEntry(ZipEntry("../outside"));it.write(1);it.closeEntry()}
            assertThrows(IllegalArgumentException::class.java){bad.inputStream().use{ArchiveBackupCodec.read(it,File(root,"stage"))}}
            assertFalse(File(root,"outside").exists())
        } finally {root.deleteRecursively()}
    }
    @Test fun backupCapturesOnlyCommittedTrackPrefix() {
        val root=Files.createTempDirectory("archive-track").toFile()
        try {
            val id=UUID.randomUUID().toString()
            val point=TrackPoint(GeoPoint(40.4,49.8),1000,5.0,true)
            val walk=Walk(id,"Walk",1000,pointCount=1,lastPoint=point)
            val bytes=java.nio.ByteBuffer.allocate(32).putDouble(40.4).putDouble(49.8).putLong(1000).putFloat(5f).putInt(1).array()
            val source=File(root,"track.bin").apply{writeBytes(bytes+ByteArray(32){99})}
            val snapshot=UserSnapshot(explore=ExploreSnapshot(walks=listOf(walk)))
            val zip=File(root,"track.zip");zip.outputStream().use{ArchiveBackupCodec.write(snapshot,mapOf("tracks/$id.bin" to source),it)}
            val restored=zip.inputStream().use{ArchiveBackupCodec.read(it,File(root,"stage"))}
            restored.use{assertEquals(32L,it.media.getValue("tracks/$id.bin").length());assertEquals(snapshot,it.snapshot)}
        } finally {root.deleteRecursively()}
    }
    @Test fun clockReversalSegmentRoundTripsWithoutDiscardingFixes() {
        val root=Files.createTempDirectory("archive-clock").toFile()
        try {
            val id=UUID.randomUUID().toString();val first=TrackPoint(GeoPoint(40.0,49.0),2000,5.0,true)
            val last=TrackPoint(GeoPoint(41.0,50.0),1500,5.0,true)
            val source=File(root,"track.bin").apply{writeBytes(az.healthchecker.data.TrackBinary.encode(first)+az.healthchecker.data.TrackBinary.encode(last))}
            val snapshot=UserSnapshot(explore=ExploreSnapshot(walks=listOf(Walk(id,"Clock changed",1000,durationMs=1000,pointCount=2,lastPoint=last))))
            val zip=File(root,"clock.zip");zip.outputStream().use{ArchiveBackupCodec.write(snapshot,mapOf("tracks/$id.bin" to source),it)}
            zip.inputStream().use{ArchiveBackupCodec.read(it,File(root,"stage"))}.use{assertEquals(snapshot,it.snapshot)}
        } finally {root.deleteRecursively()}
    }
}
