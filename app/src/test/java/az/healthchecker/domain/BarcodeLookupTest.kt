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
    @Test fun localLookupNeverUsesNetworkAndCancelDropsStaleResult()=runTest {
        var calls=0
        val f=Food("custom:local","Local",per100g=Nutrients(2.0),barcode=normalizeBarcode(raw))
        val local=BarcodeLookup(this,{if(it==f.barcode)f else null},{calls++;label(it)})
        local.accept(raw);assertEquals(f,local.state.value.food);assertEquals(0,calls)
        local.lookupOnline();advanceUntilIdle();assertEquals(0,calls)
        val gate=CompletableDeferred<Unit>()
        val session=BarcodeLookup(this,{null},{code->withContext(NonCancellable){gate.await()};label(code)})
        session.accept(raw);session.lookupOnline();runCurrent()
        session.accept("3017620422003");gate.complete(Unit);advanceUntilIdle()
        assertEquals(normalizeBarcode("3017620422003"),session.state.value.code)
        assertNull(session.state.value.label)
    }
    @Test fun networkFailureLeavesManualImportPossibleAndRetryWorks()=runTest {
        var calls=0
        val s=BarcodeLookup(this,{null},{if(calls++==0)throw IOException("network");label(it)})
        s.accept(raw);assertEquals(0,calls)
        s.lookupOnline();advanceUntilIdle();assertFalse(s.state.value.loading);assertNotNull(s.state.value.message)
        s.lookupOnline();advanceUntilIdle();assertNotNull(s.state.value.label);assertNull(s.state.value.message)
        s.accept("broken");assertNotNull(s.state.value.message);assertEquals("",s.state.value.code)
    }
    @Test fun clientRestrictsEndpointAndDistinguishesHttpErrors() {
        var target=""
        val client=OpenFoodFactsClient {url->target=url;HttpReply(404,"")}
        assertNull(client.lookup(raw));assertTrue(target.startsWith("https://world.openfoodfacts.org/api/v3.6/product/"))
        assertFalse(target.contains("profile"))
        assertThrows(IllegalStateException::class.java){OpenFoodFactsClient{HttpReply(429,"")}.lookup(raw)}
        assertThrows(IllegalStateException::class.java){OpenFoodFactsClient{HttpReply(503,"")}.lookup(raw)}
        assertThrows(IllegalArgumentException::class.java){OpenFoodFactsClient{HttpReply(200,"x".repeat(OpenFoodFactsClient.MAX_BYTES+1))}.lookup(raw)}
    }
}
