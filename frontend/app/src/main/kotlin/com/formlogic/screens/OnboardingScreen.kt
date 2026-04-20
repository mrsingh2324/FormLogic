@file:OptIn(
    androidx.compose.animation.ExperimentalAnimationApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class
)

package com.formlogic.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.formlogic.viewmodels.AuthViewModel

data class ChipOpt(val label: String, val value: String, val emoji: String = "")

val GOALS_LIST  = listOf(ChipOpt("Lose Weight","weight_loss","🔥"),ChipOpt("Muscle Gain","muscle_gain","💪"),ChipOpt("Endurance","endurance","🏃"),ChipOpt("Toning","toning","✨"),ChipOpt("Flexibility","flexibility","🧘"),ChipOpt("Stay Healthy","health","❤️"))
val EQUIP_LIST  = listOf(ChipOpt("No Equipment","bodyweight","🤸"),ChipOpt("Dumbbells","dumbbells","🏋️"),ChipOpt("Bands","bands","↔️"),ChipOpt("Pull-up Bar","pullup_bar","🔝"),ChipOpt("Gym","gym","🏟️"))
val DIET_LIST   = listOf(ChipOpt("Vegetarian","vegetarian","🥦"),ChipOpt("Vegan","vegan","🌱"),ChipOpt("Jain","jain","🙏"),ChipOpt("Gluten-Free","gluten_free","🌾"),ChipOpt("No Restrictions","none","🍽️"))
val FIT_LEVELS  = listOf(ChipOpt("Beginner","beginner","🌱"),ChipOpt("Intermediate","intermediate","🌿"),ChipOpt("Advanced","advanced","🌳"))
val GENDER_LIST = listOf(ChipOpt("Male","male","♂️"),ChipOpt("Female","female","♀️"),ChipOpt("Other","other","⚧"),ChipOpt("Prefer not to say","prefer_not_to_say","🤐"))

// ── Body-type chips (used in physique steps) ──────────────────────────────────
val BODY_TYPE_LIST = listOf(
    ChipOpt("Lean / Ectomorph",     "ectomorph",  "🦴"),
    ChipOpt("Athletic / Mesomorph", "mesomorph",  "💪"),
    ChipOpt("Stocky / Endomorph",   "endomorph",  "🏋️"),
    ChipOpt("Unsure",               "unsure",     "🤔"),
)

val BODY_FAT_LIST = listOf(
    ChipOpt("< 10 %",   "under_10",  ""),
    ChipOpt("10–15 %",  "10_15",     ""),
    ChipOpt("15–20 %",  "15_20",     ""),
    ChipOpt("20–25 %",  "20_25",     ""),
    ChipOpt("25–30 %",  "25_30",     ""),
    ChipOpt("> 30 %",   "over_30",   ""),
    ChipOpt("Not sure", "unknown",   ""),
)

val TARGET_LOOK_LIST = listOf(
    ChipOpt("Lean & Shredded",    "lean_shredded",   "🔪"),
    ChipOpt("Athletic & Toned",   "athletic_toned",  "🏅"),
    ChipOpt("Big & Muscular",     "big_muscular",    "🦾"),
    ChipOpt("Slim & Flexible",    "slim_flexible",   "🧘"),
    ChipOpt("Healthy & Fit",      "healthy_fit",     "❤️"),
)

@Composable
fun OnboardingScreen(authViewModel: AuthViewModel, onComplete: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    val total = 7   // Expanded from 5 → 7 (added two physique steps)

    // ── Existing fields ───────────────────────────────────────────────────────
    var age     by remember { mutableStateOf("") }
    var weight  by remember { mutableStateOf("") }
    var height  by remember { mutableStateOf("") }
    var gender  by remember { mutableStateOf("") }
    var level   by remember { mutableStateOf("") }
    var goals   by remember { mutableStateOf(listOf<String>()) }
    var equip   by remember { mutableStateOf(listOf<String>()) }
    var diet    by remember { mutableStateOf(listOf<String>()) }
    var saving  by remember { mutableStateOf(false) }

    // ── New physique fields ───────────────────────────────────────────────────
    // Step 5 — current physique
    var currentBodyType   by remember { mutableStateOf("") }
    var currentBodyFatPct by remember { mutableStateOf("") }
    var currentPhysiqueDesc by remember { mutableStateOf("") }
    // Step 6 — target physique
    var targetLook        by remember { mutableStateOf("") }
    var targetBodyType    by remember { mutableStateOf("") }
    var targetPhysiqueDesc by remember { mutableStateOf("") }

    fun toggle(list: List<String>, value: String) = if (value in list) list - value else list + value

    val canContinue = when (step) {
        0 -> age.isNotBlank() && weight.isNotBlank() && height.isNotBlank()
        1 -> gender.isNotBlank()
        2 -> level.isNotBlank()
        3 -> goals.isNotEmpty() && equip.isNotEmpty()
        4 -> diet.isNotEmpty()
        5 -> currentBodyType.isNotBlank() // physique description optional but body type required
        6 -> targetLook.isNotBlank()
        else -> true
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0A0A0F), Color(0xFF151529))))
    ) {
        Column(Modifier.fillMaxSize()) {
            // ── Header + progress bar ─────────────────────────────────────────
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Setup your training profile", color = Color.White, style = MaterialTheme.typography.titleLarge)
                Text("Step ${step + 1} of $total", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(total) { i ->
                        LinearProgressIndicator(
                            progress = { if (i < step) 1f else if (i == step) 0.55f else 0f },
                            modifier = Modifier.weight(1f).height(6.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                }
            }

            // ── Step content ──────────────────────────────────────────────────
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                AnimatedContent(
                    targetState = step,
                    transitionSpec = { slideInHorizontally { it } togetherWith slideOutHorizontally { -it } },
                    label = "step"
                ) { s ->
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        when (s) {
                            // ── Step 0: Basic metrics ─────────────────────────
                            0 -> {
                                StepTitle("Welcome", "Let's personalize your plan in under a minute.")
                                OField(age,    { age = it },    "Age",         KeyboardType.Number)
                                OField(weight, { weight = it }, "Weight (kg)", KeyboardType.Decimal)
                                OField(height, { height = it }, "Height (cm)", KeyboardType.Decimal)
                            }
                            // ── Step 1: Gender ────────────────────────────────
                            1 -> {
                                StepTitle("Gender", "Used for energy and macro estimates.")
                                ChipGrp(GENDER_LIST, listOf(gender), true) { gender = it }
                            }
                            // ── Step 2: Fitness level ─────────────────────────
                            2 -> {
                                StepTitle("Training level", "Choose the level that matches your current consistency.")
                                ChipGrp(FIT_LEVELS, listOf(level), true) { level = it }
                            }
                            // ── Step 3: Goals + equipment ─────────────────────
                            3 -> {
                                StepTitle("Goals and equipment", "We'll adapt exercise selection and volume from these.")
                                ChipGrp(GOALS_LIST, goals) { goals = toggle(goals, it) }
                                Text("Equipment", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                ChipGrp(EQUIP_LIST, equip) { equip = toggle(equip, it) }
                            }
                            // ── Step 4: Nutrition preferences ─────────────────
                            4 -> {
                                StepTitle("Nutrition preferences", "Used for meal suggestions and macro targets.")
                                ChipGrp(DIET_LIST, diet) { diet = toggle(diet, it) }
                            }
                            // ── Step 5: Current physique ──────────────────────
                            5 -> {
                                StepTitle(
                                    "What do you look like now?",
                                    "Be honest — the AI uses this to set your starting point and detect progress."
                                )
                                Text("Body type", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                ChipGrp(BODY_TYPE_LIST, listOf(currentBodyType), single = true) { currentBodyType = it }

                                Text("Estimated body fat", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                ChipGrp(BODY_FAT_LIST, listOf(currentBodyFatPct), single = true) { currentBodyFatPct = it }

                                OutlinedTextField(
                                    value = currentPhysiqueDesc,
                                    onValueChange = { if (it.length <= 300) currentPhysiqueDesc = it },
                                    label = { Text("Describe yourself (optional)") },
                                    placeholder = { Text("e.g. Skinny-fat, some belly fat, arms are thin…") },
                                    modifier = Modifier.fillMaxWidth(),
                                    minLines = 3,
                                    maxLines = 5,
                                    supportingText = { Text("${currentPhysiqueDesc.length}/300") }
                                )
                            }
                            // ── Step 6: Target physique ───────────────────────
                            6 -> {
                                StepTitle(
                                    "What do you want to look like?",
                                    "Your AI trainer will build every plan around this goal physique."
                                )
                                Text("Target look", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                ChipGrp(TARGET_LOOK_LIST, listOf(targetLook), single = true) { targetLook = it }

                                Text("Target body type", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                ChipGrp(BODY_TYPE_LIST, listOf(targetBodyType), single = true) { targetBodyType = it }

                                OutlinedTextField(
                                    value = targetPhysiqueDesc,
                                    onValueChange = { if (it.length <= 300) targetPhysiqueDesc = it },
                                    label = { Text("Describe your goal physique (optional)") },
                                    placeholder = { Text("e.g. Visible abs, strong legs, V-taper back…") },
                                    modifier = Modifier.fillMaxWidth(),
                                    minLines = 3,
                                    maxLines = 5,
                                    supportingText = { Text("${targetPhysiqueDesc.length}/300") }
                                )

                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "🔒  This stays on your profile. The AI coach reads it every session to tailor feedback.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // ── Nav buttons ───────────────────────────────────────────────────
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (step > 0) {
                    OutlinedButton(onClick = { step-- }, modifier = Modifier.weight(1f)) { Text("Back") }
                }
                Button(
                    onClick = {
                        if (step < total - 1) {
                            step++
                        } else {
                            saving = true
                            // Persist physique fields via AuthViewModel before completing
                            authViewModel.updatePhysiqueProfile(
                                currentBodyType       = currentBodyType,
                                currentBodyFatPct     = currentBodyFatPct,
                                currentPhysiqueDesc   = currentPhysiqueDesc,
                                targetLook            = targetLook,
                                targetBodyType        = targetBodyType,
                                targetPhysiqueDesc    = targetPhysiqueDesc,
                            )
                            onComplete()
                        }
                    },
                    modifier = Modifier.weight(1.5f).height(52.dp),
                    enabled  = !saving && canContinue
                ) {
                    Text(
                        when {
                            step == total - 1 && saving -> "Saving…"
                            step == total - 1            -> "Finish setup 🎉"
                            else                         -> "Continue"
                        },
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun StepTitle(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun OField(
    value: String,
    onValue: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType)
    )
}

@Composable
private fun ChipGrp(
    options: List<ChipOpt>,
    selected: List<String>,
    single: Boolean = false,
    onToggle: (String) -> Unit,
) {
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement   = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { opt ->
            FilterChip(
                selected = opt.value in selected,
                onClick  = { onToggle(opt.value) },
                label    = { Text(if (opt.emoji.isNotEmpty()) "${opt.emoji} ${opt.label}" else opt.label) },
                colors   = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                    selectedLabelColor     = Color.White
                )
            )
        }
    }
}
