package az.healthchecker.domain

fun parseQuantity(text: String): Double {
    require(text.length <= 24) { "Слишком длинное число" }
    val value = text.trim().replace(',', '.').toDoubleOrNull()
    require(value != null && value.isFinite() && value > 0 && value <= 100000) { "Введите число больше нуля" }
    return value
}
fun calculate(food: Food, quantity: Quantity): CalculatedPortion {
    val q=quantity.value
    require(q.isFinite() && q>0 && q<=if(quantity.unit==Measure.PIECES) 10000.0 else 100000.0) { "Проверьте количество" }
    val grams=when(quantity.unit) {
        Measure.GRAMS -> q
        Measure.ML -> { val d=food.density; require(d!=null && d.isFinite() && d>0) { "Для этого продукта укажите граммы" };q*d }
        Measure.PIECES -> { val p=quantity.portionGrams;require(p!=null && p.isFinite() && p>0) { "Укажите вес одной порции" };q*p }
    }
    require(grams.isFinite() && grams<=100000) { "Слишком большая порция" }
    fun scale(v:Double?):Double? { require(v==null || v.isFinite() && v>=0);return v?.times(grams/100) }
    val n=food.per100g
    return CalculatedPortion(grams,Nutrients(scale(n.kcal),scale(n.protein),scale(n.fat),scale(n.carbs)))
}
