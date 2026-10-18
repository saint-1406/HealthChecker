package az.healthchecker.catalog
fun normalizeSearch(text:String):String = text.replace('İ','i').replace('ı','i').lowercase(java.util.Locale.ROOT).replace('ё','е').trim()
