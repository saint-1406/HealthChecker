package az.healthchecker.backup
import az.healthchecker.domain.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
class BackupServiceTest {
    private class Store:BackupStore {
        val files=mutableMapOf("previous" to byteArrayOf(1,2));var corrupt=false;var failWrite=false
        override suspend fun writeNew(bytes:ByteArray):String { if(failWrite)error("Disk full");files["new"]=if(corrupt)byteArrayOf(0) else bytes;return "new" }
        override suspend fun read(id:String)=files.getValue(id)
        override suspend fun delete(id:String){files.remove(id)}
    }
    @Test fun verifiesWrittenContent()=runTest { val store=Store();val s=UserSnapshot(profile=Profile());val id=BackupService(store).writeVerified(s);assertEquals(s,BackupCodec.decode(store.files.getValue(id)));assertTrue(store.files.containsKey("previous")) }
    @Test fun writeFailurePreservesPrevious()=runTest { val store=Store();store.failWrite=true;assertTrue(runCatching{BackupService(store).writeVerified(UserSnapshot())}.isFailure);assertArrayEquals(byteArrayOf(1,2),store.files["previous"]) }
    @Test fun corruptNewCopyNeverDeletesPrevious()=runTest { val store=Store();store.corrupt=true;assertTrue(runCatching{BackupService(store).writeVerified(UserSnapshot())}.isFailure);assertArrayEquals(byteArrayOf(1,2),store.files["previous"]) }
}
