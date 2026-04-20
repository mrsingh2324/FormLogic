package com.formlogic.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.formlogic.BuildConfig
import com.formlogic.services.*
import com.formlogic.store.CoachStore
import com.formlogic.viewmodels.AuthViewModel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import java.time.LocalDate

private val MEAL_TYPES = listOf(
    Triple("breakfast",    "Breakfast","🌅"),
    Triple("morning_snack","Snack",    "🍌"),
    Triple("lunch",        "Lunch",    "☀️"),
    Triple("evening_snack","Eve Snack","🫖"),
    Triple("dinner",       "Dinner",  "🌙"),
    Triple("post_workout", "Post-Workout","💪"),
)

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun NutritionScreen(authVm: AuthViewModel) {
    val token by authVm.accessToken.collectAsStateWithLifecycle(null)
    val api   = remember { ApiClient.create(BuildConfig.BASE_URL) }
    val scope = rememberCoroutineScope()
    val today = LocalDate.now().toString()
    val coachStructured by CoachStore.latestStructured.collectAsStateWithLifecycle(null)

    var summary    by remember { mutableStateOf<JsonObject?>(null) }
    var meals      by remember { mutableStateOf<Map<String, List<JsonObject>>>(emptyMap()) }
    var searchQ    by remember { mutableStateOf("") }
    var searchRes  by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var selMeal    by remember { mutableStateOf("breakfast") }
    var selFood    by remember { mutableStateOf<JsonObject?>(null) }
    var quantity   by remember { mutableStateOf("1") }
    var showSearch by remember { mutableStateOf(false) }
    var searching  by remember { mutableStateOf(false) }
    var logging    by remember { mutableStateOf(false) }

    fun reload() = scope.launch { token?.let { t -> runCatching { val res = api.dailySummary("Bearer $t", today); res.body()?.data?.let { d -> summary = d["summary"]?.jsonObject; meals = d["meals"]?.jsonObject?.entries?.associate { (k, v) -> k to (v.jsonArray.mapNotNull { it.jsonObject }) } ?: emptyMap() } } } }
    LaunchedEffect(token) { if (token != null) reload() }
    LaunchedEffect(searchQ) { if (searchQ.length < 2) { searchRes = emptyList(); return@LaunchedEffect }; searching = true; runCatching { searchRes = api.searchFood(searchQ).body()?.data ?: emptyList() }; searching = false }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Nutrition")
                        Text(today, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
            )
        }
    ) { pad ->
        LazyColumn(
            Modifier
                .padding(pad)
                .background(Brush.verticalGradient(listOf(Color(0xFF0A0A0F), Color(0xFF151529)))),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val nutritionUpdates = coachStructured?.get("nutrition_updates")?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
            val proposedProtein = coachStructured?.get("proposed_updates")?.jsonObject?.get("target_protein_g")?.jsonPrimitive?.intOrNull
            if (nutritionUpdates.isNotEmpty() || proposedProtein != null) {
                item {
                    Card(
                        Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f))
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Coach nutrition update", fontWeight = FontWeight.Bold)
                            nutritionUpdates.take(2).forEach { Text("• $it") }
                            if (proposedProtein != null) Text("• Daily protein target: ${proposedProtein}g")
                        }
                    }
                }
            }


            // Macro card
            item { summary?.let { s ->
                val cal  = s["calories"]?.jsonPrimitive?.floatOrNull ?: 0f
                val pro  = s["protein"]?.jsonPrimitive?.floatOrNull  ?: 0f
                val carb = s["carbs"]?.jsonPrimitive?.floatOrNull    ?: 0f
                val fat  = s["fats"]?.jsonPrimitive?.floatOrNull     ?: 0f
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                ) { Column(Modifier.padding(20.dp)) {
                    Text("Today's Intake", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 1.sp)
                    Row(verticalAlignment = Alignment.Bottom) { Text("${cal.toInt()}", fontSize = 40.sp, fontWeight = FontWeight.ExtraBold); Text(" / 2000 kcal", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    LinearProgressIndicator(progress = { (cal / 2000f).coerceIn(0f,1f) }, Modifier.fillMaxWidth().padding(vertical = 8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        MacroBar("Protein", pro,  120f, "g", Color(0xFFE74C3C))
                        MacroBar("Carbs",   carb, 250f, "g", Color(0xFFF39C12))
                        MacroBar("Fats",    fat,   65f, "g", Color(0xFF9B59B6))
                    }
                }}
            } ?: Card(Modifier.fillMaxWidth()) { Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) { Text("No meals logged today.", color = MaterialTheme.colorScheme.onSurfaceVariant) } } }

            // Meal type tabs
            item { ScrollableTabRow(MEAL_TYPES.indexOfFirst { it.first == selMeal }.coerceAtLeast(0)) { MEAL_TYPES.forEach { (k, l, e) -> Tab(k == selMeal, { selMeal = k }, text = { Text("$e $l", fontSize = 12.sp) }) } } }

            // Add food button
            item {
                Button({ showSearch = !showSearch }, Modifier.fillMaxWidth().height(50.dp)) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (showSearch) "Hide Food Search" else "Add Food")
                }
            }

            // Search panel
            if (showSearch) item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(searchQ, { searchQ = it }, label = { Text("Search Indian foods (e.g. dal, roti…)") }, modifier = Modifier.fillMaxWidth(), singleLine = true, trailingIcon = { if (searching) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) })
                searchRes.forEach { food ->
                    val isSel = selFood?.get("_id") == food["_id"]
                    Card(onClick = { selFood = if (isSel) null else food }, border = if (isSel) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null, colors = CardDefaults.cardColors(containerColor = if (isSel) MaterialTheme.colorScheme.primary.copy(0.08f) else MaterialTheme.colorScheme.surface)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(food["name"]?.jsonPrimitive?.content ?: "", fontWeight = FontWeight.Medium); Text("${food["nutrition"]?.jsonObject?.get("calories")?.jsonPrimitive?.floatOrNull?.toInt()} kcal · per ${food["serving_size"]?.jsonPrimitive?.content}${food["serving_unit"]?.jsonPrimitive?.content}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }; if (isSel) Text("✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) }
                    }
                }
                selFood?.let { f ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Qty (${f["serving_unit"]?.jsonPrimitive?.content ?: "g"}):", Modifier.weight(1f))
                        OutlinedTextField(quantity, { quantity = it }, Modifier.width(80.dp), singleLine = true)
                        Button(onClick = { logging = true; scope.launch { runCatching { token?.let { t -> api.logMeal("Bearer $t", LogMealRequest(selMeal, f["_id"]?.jsonPrimitive?.content ?: "", quantity.toDoubleOrNull() ?: 1.0)); reload(); showSearch = false; searchQ = ""; selFood = null; quantity = "1" } }; logging = false } }, enabled = !logging) { Text(if (logging) "…" else "Log") }
                    }
                }
            } } }

            // Logged meals
            val currentMeals = meals[selMeal] ?: emptyList()
            if (currentMeals.isNotEmpty()) {
                item { Text("${MEAL_TYPES.find { it.first == selMeal }?.let { "${it.third} ${it.second}" }}", fontWeight = FontWeight.SemiBold) }
                items(currentMeals) { e -> Card(Modifier.fillMaxWidth()) { Row(Modifier.padding(12.dp)) { Text(e["food_name"]?.jsonPrimitive?.content ?: "", Modifier.weight(1f)); Text("${e["calculated_nutrition"]?.jsonObject?.get("calories")?.jsonPrimitive?.floatOrNull?.toInt()} kcal", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp) } } }
            }
        }
    }
}

@Composable
private fun MacroBar(label: String, value: Float, target: Float, unit: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("${value.toInt()}$unit", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = color)
        LinearProgressIndicator(progress = { (value / target).coerceIn(0f, 1f) }, Modifier.width(60.dp).padding(vertical = 4.dp), color = color)
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
