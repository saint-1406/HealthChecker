package az.healthchecker.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import az.healthchecker.domain.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID
import java.io.RandomAccessFile

@RunWith(RobolectricTestRunner::class) @Config(sdk=[34])
class ExploreRepositoryTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private fun id()=UUID.randomUUID().toString()
    @Test fun careFindingStaysOutsideFoodAndDeletingPlaceUnlinksOnlyOffers()=runTest {
        val name="explore-${id()}.db";val r=ExploreRepository(context,name);val core=HealthRepository(context,name)
        val place=SavedPlace(id(),"Shop",GeoPoint(40.4,49.8));r.savePlace(place)
        val f=Finding(id(),"Soap",category=FindingCategory.CARE,offers=listOf(FindOffer(place.id,"3 AZN")))
        r.saveFinding(f);r.deletePlace(place.id)
        assertEquals(f.copy(offers=emptyList()),r.exportSnapshot().findings.single())
        assertTrue(core.exportSnapshot().customFoods.isEmpty());assertTrue(core.exportSnapshot().entries.isEmpty())
    }
    @Test fun trackReadsCommittedPrefixAndPauseBreaksDistanceWithoutBackupOnEveryFix()=runTest {
        val name="explore-${id()}.db";val r=ExploreRepository(context,name);val core=HealthRepository(context,name)
        var w=r.beginWalk(now=1000);val revision=core.exportVersioned().first
        val a=TrackPoint(GeoPoint(40.4,49.8),1000,5.0);w=r.appendPoint(w.id,a,0)
        assertEquals(revision,core.exportVersioned().first)
        RandomAccessFile(r.trackFile(w),"rw").use{it.seek(it.length());it.write(ByteArray(32))}
        assertEquals(listOf(a.copy(breakBefore=true)),r.readTrack(w))
        r.updateWalk(w.id,WalkStatus.PAUSED,1000,2000);r.resumeWalk(w.id)
        w=r.appendPoint(w.id,TrackPoint(GeoPoint(41.0,50.0),3000,5.0,breakBefore=true),1000)
        assertEquals(2,w.pointCount);assertEquals(0.0,w.distanceMeters,0.0)
        r.updateWalk(w.id,WalkStatus.FINISHED,1000,4000)
        assertEquals(2,r.appendPoint(w.id,a,1000).pointCount)
    }
    @Test fun upgradePreservesVersionOneRecords()=runTest {
        val name="migration-${id()}.db"
        val old=context.openOrCreateDatabase(name,0,null)
        old.execSQL("CREATE TABLE records (`key` TEXT NOT NULL PRIMARY KEY, kind TEXT NOT NULL, payload TEXT NOT NULL)")
        old.execSQL("CREATE TABLE revision (id INTEGER NOT NULL PRIMARY KEY, current INTEGER NOT NULL, backedUp INTEGER NOT NULL)")
        old.execSQL("INSERT INTO records VALUES ('onboarded','onboarded','true')");old.version=1;old.close()
        val r=HealthRepository(context,name);assertTrue(r.exportSnapshot().onboarded)
        val e=ExploreRepository(context,name);e.savePlace(SavedPlace(id(),"Park",GeoPoint(40.4,49.8)))
        assertEquals(1,e.exportSnapshot().places.size)
    }
    @Test fun streamingArchiveDoesNotBlockNewGpsCheckpoints()=runTest {
        val name="concurrent-${id()}.db";val r=ExploreRepository(context,name);val core=HealthRepository(context,name)
        val w=r.beginWalk(now=1000);r.appendPoint(w.id,TrackPoint(GeoPoint(40.4,49.8),1000,5.0),0)
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            kotlinx.coroutines.withTimeout(10_000) {
                core.withCurrentArchive { _,_,_ ->
                    r.appendPoint(w.id,TrackPoint(GeoPoint(40.4001,49.8),2000,5.0),1000)
                }
            }
        }
        assertEquals(2,r.exportSnapshot().walks.single().pointCount)
    }
    @Test fun thinningCarriesBreaksFromSkippedPoints()=runTest {
        val r=ExploreRepository(context,"thinning-${id()}.db")
        val points=(0..5).map{TrackPoint(GeoPoint(40.0+it*0.001,49.0),1000L+it,5.0,it==0||it==2)}
        val w=Walk(id(),"Test",1000,pointCount=6,lastPoint=points.last())
        val file=r.trackFile(w);file.parentFile!!.mkdirs();file.outputStream().use{out->points.forEach{out.write(TrackBinary.encode(it))}}
        val thinned=r.readTrack(w,3)
        assertEquals(listOf(1000L,1003L,1005L),thinned.map{it.timeMillis})
        assertEquals(listOf(true,true,false),thinned.map{it.breakBefore})
    }
    @Test fun clockReversalKeepsAcceptedFixAsNewSegment()=runTest {
        val r=ExploreRepository(context,"clock-${id()}.db");val w=r.beginWalk(now=1000)
        r.appendPoint(w.id,TrackPoint(GeoPoint(40.0,49.0),2000,5.0),0)
        val saved=r.appendPoint(w.id,TrackPoint(GeoPoint(41.0,50.0),1500,5.0),1000)
        assertEquals(2,saved.pointCount);assertTrue(saved.lastPoint!!.breakBefore)
        assertEquals(0.0,saved.distanceMeters,0.0)
    }
    @Test fun deletionAndPhotoReplacementCleanFilesWithoutConfiguredBackup()=runTest {
        val r=ExploreRepository(context,"cleanup-${id()}.db")
        val jpeg=byteArrayOf(-1,-40,1,2,-1,-39)
        val first=r.saveFinding(Finding(id(),"Soap"),jpeg)
        val oldFile=r.photoFile(first)!!
        val replacement=r.saveFinding(first,jpeg)
        assertFalse("Replaced photo remains on disk",oldFile.exists())
        val newFile=r.photoFile(replacement)!!
        r.deleteFinding(first.id)
        assertFalse("Deleted finding photo remains on disk",newFile.exists())
        var walk=r.beginWalk(now=1000)
        walk=r.appendPoint(walk.id,TrackPoint(GeoPoint(40.4,49.8),1000,5.0),0)
        val track=r.trackFile(walk)
        r.updateWalk(walk.id,WalkStatus.FINISHED,1000,2000)
        r.deleteWalk(walk.id)
        assertFalse("Deleted walk remains on disk",track.exists())
    }
    @Test fun archiveCapturesFreshReferencesAfterQueuedSnapshotPhotoReplacement()=runTest {
        val name="archive-race-${id()}.db";val r=ExploreRepository(context,name);val core=HealthRepository(context,name)
        val first=r.saveFinding(Finding(id(),"Soap"),byteArrayOf(-1,-40,1,2,-1,-39))
        val oldFile=r.photoFile(first)!!
        val queued=core.exportVersioned()
        val replacement=r.saveFinding(first,byteArrayOf(-1,-40,3,4,-1,-39))
        assertFalse(oldFile.exists())
        val root=java.nio.file.Files.createTempDirectory("fresh-archive").toFile()
        try {
            val archive=java.io.File(root,"copy.zip")
            core.withCurrentArchive { revision,snapshot,files ->
                assertTrue(revision>queued.first)
                archive.outputStream().use{az.healthchecker.backup.ArchiveBackupCodec.write(snapshot,files,it)}
            }
            archive.inputStream().use{az.healthchecker.backup.ArchiveBackupCodec.read(it,java.io.File(root,"stage"))}.use {
                assertEquals(replacement,it.snapshot.explore!!.findings.single())
            }
        } finally {root.deleteRecursively()}
    }
}
