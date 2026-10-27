package com.edulock.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import com.edulock.app.AppUiText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.random.Random

private data class ArcadeQuestion(
    val prompt: String,
    val options: List<String>,
    val answer: String,
    val explanation: String
)

private fun arcadeQuestion(mode: String, previouslySeen: Set<String> = emptySet()): ArcadeQuestion {
    if (mode == "math") {
        // A finite 252-prompt addition pool avoids repeats until the bank is exhausted.
        val all = (2..19).flatMap { a ->
            (1..14).map { b ->
                val answer = a + b
                val choices = listOf(answer, answer + 2, (answer - 1).coerceAtLeast(0), answer + 4)
                    .distinct().map { it.toString() }.shuffled()
                ArcadeQuestion("Math Dash: what is $a + $b?", choices, answer.toString(), "$a + $b = $answer. Great thinking!")
            }
        }
        return all.filterNot { it.prompt in previouslySeen }.ifEmpty { all }.random()
    }
    val bank = when (mode) {
        "science" -> listOf(
            ArcadeQuestion("Which planet is known as the Red Planet?", listOf("Mars", "Venus", "Neptune", "Earth"), "Mars", "Mars looks rusty-red because of iron-rich dust on its surface."),
            ArcadeQuestion("Which part of a plant absorbs most water?", listOf("Roots", "Petals", "Fruit", "Seeds only"), "Roots", "Roots take in water and minerals from the soil."),
            ArcadeQuestion("Which particle has a negative electric charge?", listOf("Electron", "Proton", "Neutron", "Nucleus"), "Electron", "Electrons are negatively charged."),
            ArcadeQuestion("What do green plants use to make sugar during photosynthesis?", listOf("Light energy", "Moonlight only", "Plastic", "Sand only"), "Light energy", "Plants use light energy with water and carbon dioxide to make sugars."),
            ArcadeQuestion("What force pulls objects toward Earth?", listOf("Gravity", "Sound", "Colour", "Magnetism only"), "Gravity", "Gravity attracts objects with mass."),
            ArcadeQuestion("Which poles of two magnets attract?", listOf("North and south", "North and north", "South and south"), "North and south", "Opposite magnet poles attract."),
            ArcadeQuestion("What state of matter is ice?", listOf("Solid", "Liquid", "Gas"), "Solid", "Ice is water in its solid state."),
            ArcadeQuestion("What forms when an object blocks light?", listOf("A shadow", "A sound", "A smell"), "A shadow", "A shadow forms where light is blocked."),
            ArcadeQuestion("What does a closed switch do?", listOf("Completes the circuit", "Breaks every wire", "Removes a battery"), "Completes the circuit", "A closed switch completes a conducting path."),
            ArcadeQuestion("What is liquid water becoming vapour called?", listOf("Evaporation", "Condensation", "Freezing"), "Evaporation", "Evaporation changes liquid water into vapour."),
            ArcadeQuestion("Which tool measures temperature?", listOf("Thermometer", "Ruler", "Compass"), "Thermometer", "A thermometer measures temperature."),
            ArcadeQuestion("Which organ pumps blood?", listOf("Heart", "Lungs", "Stomach"), "Heart", "The heart pumps blood around the body."),
            ArcadeQuestion("Which material conducts electricity well?", listOf("Copper", "Rubber", "Dry paper"), "Copper", "Copper is a good electrical conductor."),
            ArcadeQuestion("What do green plants use light energy to make?", listOf("Sugar", "Plastic", "Metal"), "Sugar", "Photosynthesis helps plants make sugars.")
        )
        "words" -> listOf(
            ArcadeQuestion("Choose the opposite of 'hot'.", listOf("Cold", "Warm", "Boiling", "Bright"), "Cold", "Cold is the opposite of hot."),
            ArcadeQuestion("Which word is a noun?", listOf("Garden", "Quickly", "Blue", "Running"), "Garden", "A noun names a person, place, thing or idea."),
            ArcadeQuestion("Which spelling is correct?", listOf("Because", "Becaus", "Beacause", "Becuase"), "Because", "The correct spelling is because."),
            ArcadeQuestion("Which word rhymes with 'star'?", listOf("Car", "Tree", "Moon", "Book"), "Car", "Star and car share the same ending sound."),
            ArcadeQuestion("In Urdu, what is the opposite of خوش?", listOf("اداس", "تیز", "کتاب", "گھر"), "اداس", "خوش کا مخالف لفظ اداس ہے۔"),
            ArcadeQuestion("Which word tells an action?", listOf("Jump", "Garden", "Blue"), "Jump", "A verb can name an action."),
            ArcadeQuestion("Which punctuation mark can end a question?", listOf("Question mark", "Full stop only", "Hyphen"), "Question mark", "Questions commonly end with a question mark."),
            ArcadeQuestion("What is the plural of child?", listOf("Children", "Childs", "Childes"), "Children", "The plural of child is children."),
            ArcadeQuestion("Which is a vowel?", listOf("A", "B", "T"), "A", "A, E, I, O and U are vowels in English."),
            ArcadeQuestion("Complete this sentence: The sun ___ bright.", listOf("is", "are", "am"), "is", "The correct sentence is: The sun is bright."),
            ArcadeQuestion("Which word rhymes with light?", listOf("Night", "Book", "Tree"), "Night", "Light and night rhyme."),
            ArcadeQuestion("Which word is an adjective in 'a red ball'?", listOf("Red", "Ball", "A"), "Red", "An adjective describes a noun."),
            ArcadeQuestion("Which word is opposite to early?", listOf("Late", "Soon", "Quick"), "Late", "Late is the opposite of early."),
            ArcadeQuestion("Complete the sentence: They ___ playing.", listOf("are", "is", "am"), "are", "Use 'are' with the plural subject 'they'.")
        )
        else -> {
            val count = Random.nextInt(3, 8)
            val pictures = listOf("⭐", "🌟", "✨", "💫")
            val display = List(count) { pictures[Random.nextInt(pictures.size)] }.joinToString(" ")
            val options = (setOf(count, (count + 1).coerceAtMost(9), (count - 1).coerceAtLeast(1), (count + 2).coerceAtMost(10)))
                .map { it.toString() }.shuffled()
            listOf(
                ArcadeQuestion("Memory Count: count the glowing stars.\n$display", options, count.toString(), "There are $count stars. Look carefully, then count once."),
                ArcadeQuestion("Pattern Quest: what comes next? 🔴 🔵 🔴 🔵 🔴 ?", listOf("🔵", "🔴", "🟢", "⭐"), "🔵", "The pattern alternates red, blue, red, blue."),
                ArcadeQuestion("Shape Safari: how many sides does a triangle have?", listOf("3", "4", "5", "6"), "3", "A triangle has three straight sides."),
                ArcadeQuestion("Space Ranger: which object gives Earth most of its light?", listOf("The Sun", "Mars", "The Moon", "Saturn"), "The Sun", "Sunlight is the main source of light and energy for Earth."),
                ArcadeQuestion("Pattern Quest: what comes next? 🟩 🟩 🔺 🟩 🟩 🔺 ?", listOf("🟩", "🔺", "⚪", "⭐"), "🟩", "The pattern repeats two green squares, then a triangle."),
                ArcadeQuestion("Memory Match: which pair is exactly the same?", listOf("🌟🌙 and 🌟🌙", "🌟🌙 and 🌙🌟", "🔵🔴 and 🔵🟢"), "🌟🌙 and 🌟🌙", "A matching pair has the same items in the same order."),
                ArcadeQuestion("Shape Safari: which shape has no corners?", listOf("Circle", "Triangle", "Rectangle"), "Circle", "A circle has no corners."),
                ArcadeQuestion("Memory Count: which number is largest?", listOf("8", "5", "3", "6"), "8", "Eight is greater than five, three and six."),
                ArcadeQuestion("Pattern Quest: what comes next? ⭐ 🌙 ⭐ 🌙 ⭐ ?", listOf("🌙", "⭐", "☀️", "☁️"), "🌙", "The pattern alternates star and moon."),
                ArcadeQuestion("Shape Safari: how many corners has a rectangle?", listOf("4", "3", "5", "6"), "4", "A rectangle has four corners."),
                ArcadeQuestion("Memory Match: which shape is different? 🔵 🔵 🔺 🔵", listOf("The triangle", "The first circle", "The last circle"), "The triangle", "Three objects are circles and one is a triangle.")
            )
        }
    }
    val uniqueBank = bank.distinctBy { it.prompt.trim().lowercase(java.util.Locale.ROOT) }
    return uniqueBank.filterNot { it.prompt in previouslySeen }.ifEmpty { uniqueBank }.random()
}

@Composable
fun KidsGamesScreen(
    childName: String,
    avatarIcon: String,
    remainingSeconds: Int,
    dailyLimitMinutes: Int,
    onBack: () -> Unit
) {
    var mode by remember { mutableStateOf("") }
    var question by remember { mutableStateOf<ArcadeQuestion?>(null) }
    var selectedAnswer by remember { mutableStateOf<String?>(null) }
    var feedback by remember { mutableStateOf("") }
    var score by remember { mutableStateOf(0) }
    var played by remember { mutableStateOf(0) }
    var usedQuestionPrompts by remember { mutableStateOf<Set<String>>(emptySet()) }
    val timedOut = remainingSeconds <= 0
    val choices = listOf(
        Triple("math", "🔢", "Math Dash"),
        Triple("science", "🔬", "Science Safari"),
        Triple("words", "📚", "Word Wizard"),
        Triple("memory", "🧠", "Memory & Patterns")
    )
    fun startGame(nextMode: String) {
        mode = nextMode
        val first = arcadeQuestion(nextMode, emptySet())
        question = first
        usedQuestionPrompts = setOf(first.prompt)
        selectedAnswer = null
        feedback = ""
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFEDEBFF))
        ) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(52.dp).background(Brush.linearGradient(listOf(Color(0xFF5B4CE8), Color(0xFF00A99D))), RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) {
                        AppUiText(avatarIcon, fontSize = 29.sp)
                    }
                    Column {
                        AppUiText("Welcome, $childName!", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleLarge, color = Color(0xFF29245F))
                        AppUiText("Learn while you play 🌈", color = Color(0xFF626987))
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    AppUiText("Today's game time", fontWeight = FontWeight.Bold)
                    val minutes = remainingSeconds / 60
                    val seconds = remainingSeconds % 60
                    AppUiText(String.format(java.util.Locale.ROOT, "%02d:%02d", minutes, seconds), color = if (timedOut) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                }
                val total = (dailyLimitMinutes * 60).coerceAtLeast(1)
                LinearProgressIndicator(
                    progress = { (remainingSeconds.toFloat() / total.toFloat()).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth()
                )
                AppUiText(
                    if (timedOut) "Your game time has finished. Ask a parent to allow more time."
                    else "Games continue until your parent's daily time allowance runs out. There is no question-count cap; questions avoid repeats until the selected bank has been used. Learning tasks and the 4D Solar System are separate.",
                    color = if (timedOut) MaterialTheme.colorScheme.error else Color(0xFF626987),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        AppUiText("Choose a mini-game", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
        choices.chunked(2).forEach { rowChoices ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowChoices.forEach { item ->
                    val isSelected = mode == item.first
                    OutlinedButton(
                        onClick = { if (!timedOut) startGame(item.first) },
                        enabled = !timedOut,
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            AppUiText(item.second, fontSize = 25.sp)
                            AppUiText(item.third, fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        if (question != null && mode.isNotBlank()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F8F3)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    AppUiText("🏆 Score: $score / $played", color = Color(0xFF087E75), fontWeight = FontWeight.Bold)
                    AppUiText(question!!.prompt, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                    question!!.options.forEach { option ->
                        Button(
                            onClick = {
                                if (!timedOut && selectedAnswer == null) {
                                    selectedAnswer = option
                                    played += 1
                                    if (option == question!!.answer) {
                                        score += 1
                                        feedback = "✅ Correct! " + question!!.explanation
                                    } else {
                                        feedback = "🌱 Good try. The answer is " + question!!.answer + ". " + question!!.explanation
                                    }
                                }
                            },
                            enabled = !timedOut && selectedAnswer == null,
                            modifier = Modifier.fillMaxWidth()
                        ) { AppUiText(option) }
                    }
                    if (feedback.isNotBlank()) {
                        AppUiText(feedback, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        OutlinedButton(
                            onClick = {
                                if (!timedOut) {
                                    val next = arcadeQuestion(mode, usedQuestionPrompts)
                                    usedQuestionPrompts = if (next.prompt in usedQuestionPrompts) setOf(next.prompt)
                                        else usedQuestionPrompts + next.prompt
                                    question = next
                                    selectedAnswer = null
                                    feedback = ""
                                }
                            },
                            enabled = !timedOut,
                            modifier = Modifier.fillMaxWidth()
                        ) { AppUiText("Next challenge ✨") }
                    }
                }
            }
        } else {
            AppUiText("Pick a game to start a short challenge. Correct answers teach a concept and build your score.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Spacer(Modifier.padding(2.dp))
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { AppUiText("Back to learning") }
    }
}
