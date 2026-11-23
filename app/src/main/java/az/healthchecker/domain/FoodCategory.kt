package az.healthchecker.domain

/**
 * Shelves of the food search. Every catalog food carries one ([Food.category], set when the catalog is built from the
 * source's own category, see tools/catalog/categories.py); the user's own foods and dishes have none and show under
 * "All" and "My foods".
 */
enum class FoodCategory(val id:String) {
    FAST_FOOD("fastfood"), DISHES("dishes"), COFFEE("coffee"), DRINKS("drinks"), ALCOHOL("alcohol"), SWEETS("sweets"), SNACKS("snacks"),
    DAIRY("dairy"), MEAT("meat"), FISH("fish"), FRUIT_VEG("fruitveg"), GRAINS("grains"), INGREDIENTS("ingredients");
    companion object { fun of(id:String?):FoodCategory? = entries.find{it.id==id} }
}

fun foodCategoryLabel(c:FoodCategory):String = when(c) {
    FoodCategory.FAST_FOOD->tr("Фастфуд","Fast food");FoodCategory.DISHES->tr("Блюда","Dishes");FoodCategory.COFFEE->tr("Кофе и чай","Coffee & tea")
    FoodCategory.DRINKS->tr("Напитки","Drinks");FoodCategory.ALCOHOL->tr("Алкоголь","Alcohol");FoodCategory.SWEETS->tr("Сладкое","Sweets")
    FoodCategory.SNACKS->tr("Снеки и орехи","Snacks & nuts");FoodCategory.DAIRY->tr("Молочное и яйца","Dairy & eggs");FoodCategory.MEAT->tr("Мясо и птица","Meat & poultry")
    FoodCategory.FISH->tr("Рыба","Fish & seafood");FoodCategory.FRUIT_VEG->tr("Фрукты и овощи","Fruit & veg");FoodCategory.GRAINS->tr("Хлеб и крупы","Bread & grains")
    FoodCategory.INGREDIENTS->tr("Ингредиенты","Ingredients")
}
