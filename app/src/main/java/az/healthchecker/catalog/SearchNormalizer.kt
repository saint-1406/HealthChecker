package az.healthchecker.catalog
import az.healthchecker.domain.Food

/** Case, ё and Azerbaijani letters folded, so "yumurta", "YUMURTA", "çay"/"cay" and "варёное"/"вареное" meet. */
fun normalizeSearch(text:String):String = text.replace('İ','i').replace('ı','i').lowercase(java.util.Locale.ROOT).replace('ё','е')
    .replace('ə','e').replace('ş','s').replace('ç','c').replace('ğ','g').replace('ö','o').replace('ü','u').trim()

fun searchWords(text:String):List<String> = normalizeSearch(text).split(NON_WORD).filter{it.isNotEmpty()}
private val NON_WORD=Regex("[^\\p{L}\\p{N}%]+")

// Longest first: "грудки" → "грудк", "варёная" → "варен", "яйца" → "яйц".
private val RU_ENDINGS=listOf("ями","ами","ого","его","ому","ему","ыми","ими","ая","яя","ое","ее","ые","ие","ый","ий","ой","ам","ям","ах","ях","ов","ев","ей","ом","ем","а","я","ы","и","у","ю","е","о","ь")
/** A light stem so another grammatical form of the same word still matches; short words stay whole. */
fun searchStem(term:String):String {
    if(term.any{it in 'а'..'я'}) {
        if(term.length<4)return term
        return RU_ENDINGS.firstOrNull{term.endsWith(it)&&term.length-it.length>=3}?.let{term.dropLast(it.length)}?:term
    }
    return when{term.length>=5&&term.endsWith("ies")->term.dropLast(3);term.length>=5&&term.endsWith("oes")->term.dropLast(2);term.length>=4&&term.endsWith("s")&&!term.endsWith("ss")->term.dropLast(1);else->term}
}

/** One food prepared for search: words of its Russian and English names, then words of its search aliases. */
class SearchEntry(val food:Food,val order:Int,val preferred:Boolean) {
    private val ru=searchWords(food.name);private val en=searchWords(food.nameEn?:"")
    val names=ru+en
    val aliases=food.aliases.flatMap(::searchWords)
    fun leads(term:String):Boolean = ru.firstOrNull()?.startsWith(term)==true||en.firstOrNull()?.startsWith(term)==true
    /** 0 a name word starts with the term, 1 an alias word does, 2 another form of the word, 3 inside a word; null no match. */
    fun match(term:String,stem:String):Int? = when {
        names.any{it.startsWith(term)} -> 0
        aliases.any{it.startsWith(term)} -> 1
        stem!=term&&(names.any{it.startsWith(stem)}||aliases.any{it.startsWith(stem)}) -> 2
        term.length>=3&&(names.any{term in it}||aliases.any{term in it}) -> 3
        else -> null
    }
}

/**
 * Every query word must match. Results are ordered by how well the words match, then foods whose name starts with
 * the first word, then the user's own and everyday foods, then shorter names.
 */
fun rankSearch(query:String,entries:List<SearchEntry>,limit:Int):List<Food> {
    val terms=searchWords(query)
    if(terms.isEmpty())return entries.take(limit).map{it.food}
    val stems=terms.map(::searchStem)
    data class Ranked(val entry:SearchEntry,val score:Int,val lead:Int)
    return entries.mapNotNull{e->
        var score=0
        for(i in terms.indices)score+=e.match(terms[i],stems[i])?:return@mapNotNull null
        Ranked(e,score,if(e.leads(terms[0])||e.leads(stems[0]))0 else 1)
    }.sortedWith(compareBy({it.score},{it.lead},{if(it.entry.preferred)0 else 1},{it.entry.food.name.length},{it.entry.order})).take(limit).map{it.entry.food}
}
