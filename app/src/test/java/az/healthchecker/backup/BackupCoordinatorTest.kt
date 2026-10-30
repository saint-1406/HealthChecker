package az.healthchecker.backup
import az.healthchecker.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Test
import org.junit.Assert.*
@OptIn(ExperimentalCoroutinesApi::class)
class BackupCoordinatorTest {
    @Test fun recordsRevisionActuallyCapturedByArchiveAndSkipsItsNextCopy()=runTest {
        var version=1L;var saved=-1L;var writes=0
        var config=BackupConfiguration("A",0,emptyList())
        val env=object:BackupEnvironment {
            override suspend fun configuration()=config
            override suspend fun selectFolder(folder:String){}
            override suspend fun snapshot()=version to UserSnapshot()
            override suspend fun savedRevision()=saved
            override suspend fun recordSaved(folder:String,files:List<String>,revision:Long){saved=revision;config=config.copy(lastBackup=1,files=files)}
            override fun store(folder:String)=object:BackupStore {
                override suspend fun writeNew(bytes:ByteArray):String=error("Archive path required")
                override suspend fun read(id:String):ByteArray=error("Archive path required")
                override suspend fun delete(id:String){}
            }
            override suspend fun writeVersioned(folder:String,revision:Long,snapshot:UserSnapshot):Pair<Long,String>{writes++;version=2;return version to "copy"}
        }
        val coordinator=BackupCoordinator(env)
        coordinator.backup();assertEquals(2L,saved)
        coordinator.backup();assertEquals(1,writes)
    }
    @Test fun folderChangeCannotCommitOldFolderMetadataIntoNewFolder()=runTest {
        val started=CompletableDeferred<Unit>();val finish=CompletableDeferred<Unit>()
        val files=mutableMapOf<String,ByteArray>();var config=BackupConfiguration("A",0,emptyList());var saved=-1L
        val env=object:BackupEnvironment {
            override suspend fun configuration()=config
            override suspend fun selectFolder(folder:String){config=BackupConfiguration(folder,0,emptyList())}
            override suspend fun snapshot()=1L to UserSnapshot()
            override suspend fun savedRevision()=saved
            override suspend fun recordSaved(folder:String,files:List<String>,revision:Long){config=config.copy(lastBackup=1,files=files);saved=revision}
            override fun store(folder:String)=object:BackupStore {
                override suspend fun writeNew(bytes:ByteArray):String {if(folder=="A"){started.complete(Unit);finish.await()};val id="$folder/new";files[id]=bytes;return id}
                override suspend fun read(id:String)=files.getValue(id)
                override suspend fun delete(id:String){files.remove(id)}
            }
        }
        val c=BackupCoordinator(env);val first=async{c.backup()};started.await();val selection=async{c.selectFolder("B")};runCurrent();val folderDuringWrite=config.folder
        finish.complete(Unit);first.await();selection.await();c.backup()
        assertEquals("A",folderDuringWrite);assertTrue(files.containsKey("B/new"));assertEquals(listOf("B/new"),config.files)
    }
    @Test fun rotationKeepsNewestPreviousCopyAndSkipsUnchangedData()=runTest {
        val files=linkedMapOf<String,ByteArray>();var config=BackupConfiguration("A",0,emptyList());var saved=-1L;var version=1L;var writes=0
        val env=object:BackupEnvironment {
            override suspend fun configuration()=config
            override suspend fun selectFolder(folder:String){config=BackupConfiguration(folder,0,emptyList())}
            override suspend fun snapshot()=version to UserSnapshot(completedDays=setOf("2026-10-1$version"))
            override suspend fun savedRevision()=saved
            override suspend fun recordSaved(folder:String,files:List<String>,revision:Long){config=config.copy(lastBackup=1,files=files);saved=revision}
            override fun store(folder:String)=object:BackupStore {
                override suspend fun writeNew(bytes:ByteArray):String {writes++;val id="copy$writes";files[id]=bytes;return id}
                override suspend fun read(id:String)=files.getValue(id)
                override suspend fun delete(id:String){files.remove(id)}
            }
        }
        val c=BackupCoordinator(env)
        c.backup();c.backup();assertEquals(1,writes)
        version=2;c.backup();version=3;c.backup()
        assertEquals(listOf("copy2","copy3"),config.files);assertEquals(setOf("copy2","copy3"),files.keys)
        c.backup(force=true);assertEquals(listOf("copy3","copy4"),config.files)
    }
}
