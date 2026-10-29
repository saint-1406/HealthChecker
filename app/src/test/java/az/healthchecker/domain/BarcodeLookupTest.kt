package az.healthchecker.domain

import az.healthchecker.barcode.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class BarcodeLookupTest {
    private val raw="4006381333931"
    private fun label(code:String)=LabelProduct(code,"Test","Brand",Nutrients(200.0))
    @Test fun scanSearchesBundledCatalogOnlyWhenMissingLocallyAndDistinguishesAbsenceFromFailure()=runTest {
        var calls=0
        val f=Food("custom:local","Local",per100g=Nutrients(2.0),barcode=normalizeBarcode(raw))
        val s=BarcodeLookup(this,{if(it==f.barcode)f else null},{calls++;null})
        s.acceptAndSearch(raw);advanceUntilIdle();assertEquals(0,calls);assertEquals(f,s.state.value.food)
        s.acceptAndSearch("3017620422003");advanceUntilIdle()
        assertEquals(1,calls);assertTrue(s.state.value.notFound);assertNull(s.state.value.message)
        val failed=BarcodeLookup(this,{null},{throw IOException("offline")})
        failed.acceptAndSearch(raw);advanceUntilIdle()
        assertFalse(failed.state.value.notFound);assertNotNull(failed.state.value.message)
        s.acceptAndSearch("invalid");advanceUntilIdle();assertEquals(1,calls);assertFalse(s.state.value.notFound)
    }
    @Test fun localLookupNeverUsesNetworkAndCancelDropsStaleResult()=runTest {
        var calls=0
        val f=Food("custom:local","Local",per100g=Nutrients(2.0),barcode=normalizeBarcode(raw))
        val local=BarcodeLookup(this,{if(it==f.barcode)f else null},{calls++;label(it)})
        local.accept(raw);assertEquals(f,local.state.value.food);assertEquals(0,calls)
        local.lookupCatalog();advanceUntilIdle();assertEquals(0,calls)
        val gate=CompletableDeferred<Unit>()
        val session=BarcodeLookup(this,{null},{code->withContext(NonCancellable){gate.await()};label(code)})
        session.accept(raw);session.lookupCatalog();runCurrent()
        session.accept("3017620422003");gate.complete(Unit);advanceUntilIdle()
        assertEquals(normalizeBarcode("3017620422003"),session.state.value.code)
        assertNull(session.state.value.label)
    }
    @Test fun catalogFailureLeavesManualImportPossibleAndRetryWorks()=runTest {
        var calls=0
        val s=BarcodeLookup(this,{null},{if(calls++==0)throw IOException("network");label(it)})
        s.accept(raw);assertEquals(0,calls)
        s.lookupCatalog();advanceUntilIdle();assertFalse(s.state.value.loading);assertNotNull(s.state.value.message)
        s.lookupCatalog();advanceUntilIdle();assertNotNull(s.state.value.label);assertNull(s.state.value.message)
        s.accept("broken");assertNotNull(s.state.value.message);assertEquals("",s.state.value.code)
    }
    @Test fun anOldPhotoCannotReplaceANewerManualCode()=runTest {
        val gate=CompletableDeferred<Unit>()
        val session=BarcodeLookup(this,{null},{label(it)})
        session.readPhoto({withContext(NonCancellable){gate.await()};raw})
        runCurrent();assertTrue(session.state.value.readingPhoto)
        session.accept("3017620422003")
        gate.complete(Unit);advanceUntilIdle()
        assertEquals(normalizeBarcode("3017620422003"),session.state.value.code)
        assertFalse(session.state.value.readingPhoto)
    }
}
