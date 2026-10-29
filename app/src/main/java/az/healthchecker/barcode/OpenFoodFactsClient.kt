package az.healthchecker.barcode

import az.healthchecker.domain.*
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class HttpReply(val status:Int,val body:String)

/** Explicit public product queries only; never sends diary, images or profile. */
class OpenFoodFactsClient(private val request:(String)->HttpReply=::downloadProduct) {
    fun lookup(raw:String):LabelProduct? {
        val code=normalizeBarcode(raw)
        val fields="code,product_name,brands,product_quantity,product_quantity_unit,nutrition"
        val response=request("https://world.openfoodfacts.org/api/v3.6/product/$code.json?fields=$fields")
        require(response.body.toByteArray(Charsets.UTF_8).size<=MAX_BYTES){tr("Слишком большой ответ сервера","The server response is too large")}
        return when(response.status) {
            200->OpenFoodFacts.parse(code,response.body)
            404->null
            429->error(tr("Слишком много запросов. Повторите через минуту.","Too many requests. Try again in a minute."))
            else->error(tr("Сервис поиска недоступен. Можно ввести данные по этикетке.","Product lookup is unavailable. You can enter the label manually."))
        }
    }
    companion object {const val MAX_BYTES=524288}
}

private fun downloadProduct(url:String):HttpReply {
    val connection=URL(url).openConnection() as HttpURLConnection
    try {
        connection.connectTimeout=10000;connection.readTimeout=10000;connection.instanceFollowRedirects=false
        connection.setRequestProperty("User-Agent","HealthChecker/15 (https://github.com/saint-1406/HealthChecker)")
        connection.setRequestProperty("Accept","application/json")
        val status=connection.responseCode
        if(status!=200)return HttpReply(status,"")
        require(connection.contentLengthLong<=OpenFoodFactsClient.MAX_BYTES){tr("Слишком большой ответ сервера","The server response is too large")}
        val out=ByteArrayOutputStream();val buffer=ByteArray(4096);val started=System.nanoTime()
        connection.inputStream.use{input->while(true){
            check((System.nanoTime()-started)<15_000_000_000L){tr("Поиск занял слишком много времени","Product lookup timed out")}
            val n=input.read(buffer);if(n<0)break
            require(out.size()+n<=OpenFoodFactsClient.MAX_BYTES){tr("Слишком большой ответ сервера","The server response is too large")}
            out.write(buffer,0,n)
        }}
        return HttpReply(status,out.toString(Charsets.UTF_8.name()))
    } finally {connection.disconnect()}
}
