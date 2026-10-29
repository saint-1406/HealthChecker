package az.healthchecker.domain

/** Canonical GTIN-14; zero-prefixed aliases of EAN/UPC identify the same trade item. */
fun normalizeBarcode(input:String):String {
    val code=input.trim()
    require(code.length in listOf(8,12,13,14)&&code.all{it in '0'..'9'}&&code.any{it!='0'}) {
        tr("Введите 8, 12, 13 или 14 цифр штрихкода","Enter an 8, 12, 13 or 14 digit product barcode")
    }
    val sum=code.dropLast(1).reversed().mapIndexed{i,c->(c-'0')*(if(i%2==0)3 else 1)}.sum()
    require((10-sum%10)%10==code.last()-'0'){tr("Проверьте цифры штрихкода","Check the barcode digits")}
    return code.padStart(14,'0')
}
