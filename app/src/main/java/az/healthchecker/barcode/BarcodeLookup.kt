package az.healthchecker.barcode

import az.healthchecker.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class BarcodeLookupState(val code:String="",val food:Food?=null,val label:LabelProduct?=null,val loading:Boolean=false,val message:String?=null,val readingPhoto:Boolean=false,val notFound:Boolean=false)

/** Superseded/cancelled requests cannot replace the current product. */
class BarcodeLookup(private val scope:CoroutineScope,private val local:(String)->Food?,private val fetch:suspend (String)->LabelProduct?) {
    private val mutable=MutableStateFlow(BarcodeLookupState())
    val state:StateFlow<BarcodeLookupState> = mutable.asStateFlow()
    private var generation=0
    private var job:Job?=null
    fun cancel(){generation++;job?.cancel();job=null;mutable.value=mutable.value.copy(loading=false,readingPhoto=false)}
    fun accept(raw:String) {
        cancel()
        mutable.value=try{val code=normalizeBarcode(raw);BarcodeLookupState(code,food=local(code))}
            catch(e:IllegalArgumentException){BarcodeLookupState(message=e.message)}
    }
    fun acceptAndSearch(raw:String) { accept(raw);lookupCatalog() }
    fun lookupCatalog() {
        val current=state.value
        if(current.code.isEmpty()||current.food!=null||current.loading||current.readingPhoto)return
        val token=++generation
        mutable.value=current.copy(loading=true,label=null,message=null,notFound=false)
        job=scope.launch {
            try {
                val label=fetch(current.code)
                if(generation==token)mutable.value=current.copy(label=label,message=null,notFound=label==null)
            }catch(e:CancellationException){throw e}
            catch(e:Exception){if(generation==token)mutable.value=current.copy(notFound=false,message=tr("Не удалось открыть встроенную базу. Проверьте свободное место или добавьте товар вручную.","Could not open the bundled database. Check available storage or add the product manually."))}
        }
    }
    fun readPhoto(decode:suspend ()->String,accepted:(String)->Unit={accept(it)}) {
        cancel()
        val current=state.value;val token=++generation
        mutable.value=current.copy(readingPhoto=true,message=null)
        job=scope.launch {
            try {val code=decode();if(generation==token)accepted(code)}
            catch(e:CancellationException){throw e}
            catch(e:Exception){if(generation==token)mutable.value=current.copy(message=e.message?:tr("Не удалось прочитать штрихкод","Could not read the barcode"))}
            finally {if(generation==token)mutable.value=mutable.value.copy(readingPhoto=false)}
        }
    }
}
