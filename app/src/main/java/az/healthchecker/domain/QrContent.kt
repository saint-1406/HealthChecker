package az.healthchecker.domain

/** What a scanned QR code holds. Parsing is local; links are never fetched. */
sealed interface QrContent {
    /** A product identifier: plain GTIN digits, a GS1 element string "(01)…" or a GS1 Digital Link ".../01/<gtin>". */
    data class Product(val gtin:String):QrContent
    /** Several lines, e.g. a shopping list. Bullets, numbering and checkboxes are removed. */
    data class ItemList(val items:List<String>):QrContent
    /** Anything else. [url] is set for http(s) links; [receipt] marks an Azerbaijani e-kassa receipt link. */
    data class Text(val text:String,val url:String?=null,val receipt:Boolean=false):QrContent
}

const val MAX_QR_ITEMS=100
const val MAX_QR_ITEM_LENGTH=200
const val MAX_QR_TEXT_LENGTH=10_000

private val digitalLink=Regex("""^https?://[^\s/]+(?:/[^\s]*)?/01/(\d{8}|\d{12,14})(?:[/?#].*)?$""",RegexOption.IGNORE_CASE)
private val elementString=Regex("""^(?:\(01\)|\u001D?01)(\d{14})""")
private val listMarker=Regex("""^(?:[-*•·–—+]+|\d{1,3}[.)]|\[[ xX✓✔]?]|[☐☑✓✔])\s*""")
private val url=Regex("""^https?://\S+$""",RegexOption.IGNORE_CASE)

private fun gtinOrNull(digits:String)=runCatching{normalizeBarcode(digits)}.getOrNull()

fun parseQr(raw:String):QrContent {
    val text=raw.replace("\r\n","\n").replace('\r','\n').trim().take(MAX_QR_TEXT_LENGTH)
    require(text.isNotEmpty()){tr("QR-код пустой","The QR code is empty")}
    gtinOrNull(text)?.let{return QrContent.Product(it)}
    digitalLink.find(text)?.let{m->gtinOrNull(m.groupValues[1])?.let{return QrContent.Product(it)}}
    elementString.find(text)?.let{m->gtinOrNull(m.groupValues[1])?.let{return QrContent.Product(it)}}
    val lines=text.lines().map{it.trim()}.filter{it.isNotEmpty()}
    if(lines.size>=2&&lines.none{url.matches(it)}) {
        val items=lines.map{listMarker.replace(it,"").trim().take(MAX_QR_ITEM_LENGTH)}.filter{it.isNotEmpty()}
            .distinctBy{it.lowercase()}.take(MAX_QR_ITEMS)
        if(items.size>=2)return QrContent.ItemList(items)
    }
    val link=text.takeIf{url.matches(it)}
    val receipt=link!=null&&Regex("""^https?://([a-z0-9-]+\.)*e-?kassa\.(gov\.)?az(/|$)""",RegexOption.IGNORE_CASE).containsMatchIn(link)
    return QrContent.Text(text,link,receipt)
}

/** A short finding name for free QR text: the link host or the first line. */
fun qrTitle(content:QrContent.Text):String =
    content.url?.let{Regex("""^https?://(?:www\.)?([^/?#:]+)""",RegexOption.IGNORE_CASE).find(it)?.groupValues?.get(1)}
        ?:content.text.lineSequence().first().trim().take(60)
