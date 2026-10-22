package az.healthchecker.domain

fun parseQuantity(text: String): Double {
    require(text.length <= 24) { tr("Слишком длинное число","The number is too long") }
    val value = text.trim().replace(',', '.').toDoubleOrNull()
    require(value != null && value.isFinite() && value > 0 && value <= 100000) { tr("Введите число больше нуля","Enter a number greater than zero") }
    return value
}
fun calculate(food: Food, quantity: Quantity): CalculatedPortion {
    val q=quantity.value
    require(q.isFinite() && q>0 && q<=if(quantity.unit==Measure.PIECES) 10000.0 else 100000.0) { tr("Проверьте количество","Check the amount") }
    val grams=when(quantity.unit) {
        Measure.GRAMS -> q
        Measure.ML -> { val d=food.density; require(d!=null && d.isFinite() && d>0) { tr("Для этого продукта укажите граммы","Enter grams for this food") };q*d }
        Measure.PIECES -> { val p=quantity.portionGrams;require(p!=null && p.isFinite() && p>0) { tr("Укажите вес одной порции","Enter the weight of one piece") };q*p }
    }
    require(grams.isFinite() && grams<=100000) { tr("Слишком большая порция","The portion is too large") }
    val n=food.per100g
    listOf(n.kcal,n.protein,n.fat,n.carbs,n.fiber).forEach{require(it==null || it.isFinite() && it>=0)}
    return CalculatedPortion(grams,n.times(grams/100))
}
