"""
seedData.py — Seed the MongoDB database with Indian food items.
Run with: python -m app.utils.seed_data
"""
import asyncio
import os
from app.utils.database import connect_db, disconnect_db
from app.models.models import FoodItem, NutritionInfo


SEED_FOODS = [
    {"name": "Roti (Whole Wheat)", "name_hindi": "रोटी", "category": "Grains", "nutrition": {"calories": 297, "protein": 9.7, "carbs": 57, "fats": 3.7, "fiber": 10}, "serving_size": 40, "serving_unit": "roti", "is_vegetarian": True, "is_vegan": True, "is_jain": True, "is_gluten_free": False, "tags": ["staple", "indian", "wheat"]},
    {"name": "Basmati Rice (Cooked)", "name_hindi": "चावल", "category": "Grains", "nutrition": {"calories": 130, "protein": 2.7, "carbs": 28.2, "fats": 0.3, "fiber": 0.4}, "serving_size": 100, "serving_unit": "g", "is_vegetarian": True, "is_vegan": True, "is_jain": True, "is_gluten_free": True, "tags": ["staple", "indian", "rice"]},
    {"name": "Dal (Toor/Arhar)", "name_hindi": "तुअर दाल", "category": "Legumes", "nutrition": {"calories": 343, "protein": 22.3, "carbs": 57.6, "fats": 1.7, "fiber": 15}, "serving_size": 200, "serving_unit": "katori", "is_vegetarian": True, "is_vegan": True, "is_jain": True, "is_gluten_free": True, "tags": ["protein", "dal", "lentil"]},
    {"name": "Paneer (Cottage Cheese)", "name_hindi": "पनीर", "category": "Dairy", "nutrition": {"calories": 265, "protein": 18.3, "carbs": 1.2, "fats": 20.8, "fiber": 0}, "serving_size": 100, "serving_unit": "g", "is_vegetarian": True, "is_vegan": False, "is_jain": True, "is_gluten_free": True, "tags": ["protein", "dairy", "paneer"]},
    {"name": "Chicken Breast (Grilled)", "category": "Meat", "nutrition": {"calories": 165, "protein": 31, "carbs": 0, "fats": 3.6, "fiber": 0}, "serving_size": 100, "serving_unit": "g", "is_vegetarian": False, "is_vegan": False, "is_jain": False, "is_gluten_free": True, "tags": ["protein", "chicken", "non-veg"]},
    {"name": "Egg (Whole, Boiled)", "name_hindi": "अंडा", "category": "Eggs", "nutrition": {"calories": 155, "protein": 13, "carbs": 1.1, "fats": 11, "fiber": 0}, "serving_size": 50, "serving_unit": "egg", "is_vegetarian": False, "is_vegan": False, "is_jain": False, "is_gluten_free": True, "tags": ["protein", "egg", "breakfast"]},
    {"name": "Poha (Flattened Rice)", "name_hindi": "पोहा", "category": "Breakfast", "nutrition": {"calories": 110, "protein": 2.5, "carbs": 23, "fats": 0.5, "fiber": 1}, "serving_size": 150, "serving_unit": "katori", "is_vegetarian": True, "is_vegan": True, "is_jain": False, "is_gluten_free": True, "tags": ["breakfast", "maharashtra", "light"]},
    {"name": "Idli (Plain)", "name_hindi": "इडली", "category": "Breakfast", "nutrition": {"calories": 58, "protein": 2, "carbs": 11.5, "fats": 0.4, "fiber": 0.5}, "serving_size": 40, "serving_unit": "idli", "is_vegetarian": True, "is_vegan": True, "is_jain": True, "is_gluten_free": True, "tags": ["south-indian", "breakfast", "low-cal"]},
    {"name": "Dosa (Plain)", "name_hindi": "डोसा", "category": "Breakfast", "nutrition": {"calories": 112, "protein": 2.7, "carbs": 20, "fats": 2.7, "fiber": 0.8}, "serving_size": 80, "serving_unit": "dosa", "is_vegetarian": True, "is_vegan": True, "is_jain": True, "is_gluten_free": True, "tags": ["south-indian", "breakfast"]},
    {"name": "Rajma (Kidney Beans, Cooked)", "name_hindi": "राजमा", "category": "Legumes", "nutrition": {"calories": 127, "protein": 8.7, "carbs": 22.8, "fats": 0.5, "fiber": 6.4}, "serving_size": 200, "serving_unit": "katori", "is_vegetarian": True, "is_vegan": True, "is_jain": True, "is_gluten_free": True, "tags": ["protein", "north-indian", "rajma"]},
    {"name": "Chole (Chickpea Curry)", "name_hindi": "छोले", "category": "Legumes", "nutrition": {"calories": 150, "protein": 8, "carbs": 25, "fats": 3.5, "fiber": 7}, "serving_size": 200, "serving_unit": "katori", "is_vegetarian": True, "is_vegan": True, "is_jain": False, "is_gluten_free": True, "tags": ["protein", "north-indian", "chickpea"]},
    {"name": "Palak Paneer", "name_hindi": "पालक पनीर", "category": "Curry", "nutrition": {"calories": 243, "protein": 11.5, "carbs": 8, "fats": 18.5, "fiber": 2.5}, "serving_size": 200, "serving_unit": "katori", "is_vegetarian": True, "is_vegan": False, "is_jain": True, "is_gluten_free": True, "tags": ["north-indian", "spinach", "paneer", "curry"]},
    {"name": "Sambar", "name_hindi": "सांबर", "category": "Lentil Soup", "nutrition": {"calories": 100, "protein": 5, "carbs": 14, "fats": 2.5, "fiber": 4}, "serving_size": 200, "serving_unit": "katori", "is_vegetarian": True, "is_vegan": True, "is_jain": False, "is_gluten_free": True, "tags": ["south-indian", "lentil", "soup"]},
    {"name": "Upma", "name_hindi": "उपमा", "category": "Breakfast", "nutrition": {"calories": 145, "protein": 3.5, "carbs": 26, "fats": 3.5, "fiber": 2}, "serving_size": 200, "serving_unit": "katori", "is_vegetarian": True, "is_vegan": True, "is_jain": False, "is_gluten_free": False, "tags": ["south-indian", "breakfast", "semolina"]},
    {"name": "Banana", "name_hindi": "केला", "category": "Fruit", "nutrition": {"calories": 89, "protein": 1.1, "carbs": 22.8, "fats": 0.3, "fiber": 2.6}, "serving_size": 118, "serving_unit": "banana", "is_vegetarian": True, "is_vegan": True, "is_jain": True, "is_gluten_free": True, "tags": ["fruit", "pre-workout", "energy"]},
    {"name": "Milk (Full Fat)", "name_hindi": "दूध", "category": "Dairy", "nutrition": {"calories": 61, "protein": 3.2, "carbs": 4.8, "fats": 3.25, "fiber": 0}, "serving_size": 240, "serving_unit": "glass", "is_vegetarian": True, "is_vegan": False, "is_jain": True, "is_gluten_free": True, "tags": ["dairy", "protein", "calcium"]},
    {"name": "Curd / Dahi (Plain)", "name_hindi": "दही", "category": "Dairy", "nutrition": {"calories": 61, "protein": 3.5, "carbs": 4.7, "fats": 3.25, "fiber": 0}, "serving_size": 150, "serving_unit": "katori", "is_vegetarian": True, "is_vegan": False, "is_jain": True, "is_gluten_free": True, "tags": ["dairy", "probiotic", "dahi"]},
    {"name": "Moong Dal (Yellow, Cooked)", "name_hindi": "मूंग दाल", "category": "Legumes", "nutrition": {"calories": 105, "protein": 7.7, "carbs": 18, "fats": 0.4, "fiber": 5}, "serving_size": 200, "serving_unit": "katori", "is_vegetarian": True, "is_vegan": True, "is_jain": True, "is_gluten_free": True, "tags": ["protein", "light", "moong"]},
    {"name": "Aloo Paratha", "name_hindi": "आलू परांठा", "category": "Bread", "nutrition": {"calories": 220, "protein": 5, "carbs": 33, "fats": 8, "fiber": 3}, "serving_size": 80, "serving_unit": "paratha", "is_vegetarian": True, "is_vegan": False, "is_jain": False, "is_gluten_free": False, "tags": ["north-indian", "breakfast", "paratha"]},
    {"name": "Whey Protein Shake", "category": "Supplement", "nutrition": {"calories": 120, "protein": 24, "carbs": 3, "fats": 1.5, "fiber": 0}, "serving_size": 30, "serving_unit": "scoop", "is_vegetarian": True, "is_vegan": False, "is_jain": True, "is_gluten_free": True, "tags": ["supplement", "protein", "post-workout"]},
]


async def seed_foods():
    await connect_db()
    count = await FoodItem.count()
    if count > 0:
        print(f"ℹ️  Food database already has {count} items. Skipping seed.")
        await disconnect_db()
        return

    items = []
    for f in SEED_FOODS:
        nutrition = NutritionInfo(**f.pop("nutrition"))
        items.append(FoodItem(nutrition=nutrition, **f))

    await FoodItem.insert_many(items)
    print(f"✅ Seeded {len(items)} Indian food items.")
    await disconnect_db()


if __name__ == "__main__":
    asyncio.run(seed_foods())
