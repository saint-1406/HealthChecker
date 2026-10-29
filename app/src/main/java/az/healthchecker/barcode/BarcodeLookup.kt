package az.healthchecker.barcode

import az.healthchecker.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class BarcodeLookupState(val code:String="",val food:Food?=null,val label:LabelProduct?=null,val loading:Boolean=false,val message:String?=null,val readingPhoto:Boolean=false)

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
    fun lookupOnline() {
        val current=state.value
        if(current.code.isEmpty()||current.food!=null||current.loading||current.readingPhoto)return
        val token=++generation
        mutable.value=current.copy(loading=true,label=null,message=null)
        job=scope.launch {
            try {
                val label=fetch(current.code)
                if(generation==token)mutable.value=current.copy(label=label,message=if(label==null)tr("Товар не найден. Добавьте его по этикетке.","Product not found. Add it from the label.")else null)
            }catch(e:CancellationException){throw e}
            catch(e:Exception){if(generation==token)mutable.value=current.copy(message=if(e is java.io.IOException)tr("Нет связи с сервисом. Повторите поиск или введите этикетку.","Cannot reach the service. Retry or enter the label manually.")else e.message?:tr("Не удалось найти товар","Could not look up this product"))}
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
