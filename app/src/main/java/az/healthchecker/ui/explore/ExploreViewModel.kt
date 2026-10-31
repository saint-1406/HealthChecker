package az.healthchecker.ui.explore

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import az.healthchecker.barcode.OfflineBarcodeCatalog
import az.healthchecker.data.ExploreRepository
import az.healthchecker.data.HealthRepository
import az.healthchecker.domain.*
import az.healthchecker.explore.WalkTracking
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.Base64
import java.util.UUID

class ExploreViewModel(app:Application):AndroidViewModel(app) {
    private val context=app.applicationContext
    val repository=ExploreRepository(context)
    val snapshot=MutableStateFlow(ExploreSnapshot())
    val busy=MutableStateFlow(false)
    val error=MutableStateFlow<String?>(null)
    val recording=WalkTracking.state
    init {
        viewModelScope.launch(Dispatchers.IO){
            try {WalkTracking.reconcile(context);repository.snapshots.collect{snapshot.value=it}}
            catch(e:CancellationException){throw e}
            catch(e:Exception){error.value=tr("Не удалось открыть находки: ","Could not open findings: ")+e.message}
        }
    }
    private fun action(block:suspend ()->Unit) {
        if(busy.value)return
        busy.value=true
        viewModelScope.launch(Dispatchers.IO){
            try{block()}
            catch(e:CancellationException){throw e}
            catch(e:Exception){error.value=e.message?:tr("Не удалось сохранить изменения","Could not save changes")}
            finally{busy.value=false}
        }
    }
    fun saveFinding(f:Finding,photo:String?,remove:Boolean,done:(Finding)->Unit)=action {
        val saved=repository.saveFinding(f,photo?.let{Base64.getDecoder().decode(it)},remove)
        snapshot.value=repository.exportSnapshot();withContext(Dispatchers.Main){done(saved)}
    }
    fun favorite(f:Finding)=action{repository.saveFinding(f.copy(favorite=!f.favorite,updatedAt=System.currentTimeMillis()));snapshot.value=repository.exportSnapshot()}
    fun deleteFinding(f:Finding,done:()->Unit)=action{repository.deleteFinding(f.id);snapshot.value=repository.exportSnapshot();withContext(Dispatchers.Main){done()}}
    fun savePlace(p:SavedPlace,done:(SavedPlace)->Unit)=action{repository.savePlace(p);snapshot.value=repository.exportSnapshot();withContext(Dispatchers.Main){done(p)}}
    fun deletePlace(p:SavedPlace,done:()->Unit)=action{repository.deletePlace(p.id);snapshot.value=repository.exportSnapshot();withContext(Dispatchers.Main){done()}}
    fun startWalk(id:String?=null,done:()->Unit={})=action{WalkTracking.start(context,id);snapshot.value=repository.exportSnapshot();withContext(Dispatchers.Main){done()}}
    fun pauseWalk()=action{WalkTracking.pause(context);snapshot.value=repository.exportSnapshot()}
    fun finishWalk()=action{WalkTracking.finish(context);snapshot.value=repository.exportSnapshot()}
    fun deleteWalk(w:Walk)=action{repository.deleteWalk(w.id);snapshot.value=repository.exportSnapshot()}
    fun locate(done:(TrackPoint)->Unit)=action{val p=WalkTracking.currentPosition(context);withContext(Dispatchers.Main){done(p)}}
    fun lookupBarcode(raw:String,done:(Finding,String?)->Unit)=action {
        val code=normalizeBarcode(raw)
        repository.exportSnapshot().findings.find{it.barcode==code}?.let{withContext(Dispatchers.Main){done(it,null)};return@action}
        val own=HealthRepository(context).foodByBarcode(code)
        val label=if(own==null)OfflineBarcodeCatalog.shared(context).lookup(code)else null
        val now=System.currentTimeMillis()
        val found=Finding(UUID.randomUUID().toString(),own?.name?:label?.name.orEmpty(),code,
            if(own!=null||label!=null)FindingCategory.FOOD else FindingCategory.OTHER,brand=label?.brand.orEmpty(),createdAt=now,updatedAt=now)
        withContext(Dispatchers.Main){done(found,own?.productPhoto)}
    }
}
