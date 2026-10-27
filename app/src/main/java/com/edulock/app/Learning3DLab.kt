package com.edulock.app

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import com.edulock.app.AppUiText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

data class LearningModelOption(
    val id: String,
    val icon: String,
    val title: String,
    val subject: String,
    val question: String,
    val detail: String
)

val LearningModelOptions = listOf(
    LearningModelOption("solar-system", "🪐", "Solar System", "Astronomy", "Can you identify a planet from its colour, size and orbit?", "Rotate, zoom and spread the orbital model; tap a planet to reveal facts."),
    LearningModelOption("atom", "⚛️", "Atom Explorer", "Chemistry", "Which negatively charged particle is shown around the nucleus?", "Rotate, zoom and separate the atomic components; tap each part for its role."),
    LearningModelOption("molecule", "🧪", "Molecule Lab", "Chemistry", "Which two elements form a water molecule?", "Tap the oxygen, hydrogen atoms and bonds to inspect the water model."),
    LearningModelOption("chemistry-lab", "🧫", "Chemistry Reaction Lab", "Chemistry", "What visible result can appear when simulated reactants combine?", "Choose two virtual chemicals, tap Mix, and watch the liquids change, particles collide and a product form. Simulation only; do not mix real chemicals."),
    LearningModelOption("geometry", "📐", "4D Geometry Studio", "Mathematics", "How many faces, edges and vertices does a cube have?", "Rotate 360°, zoom and explode the cube to inspect individual faces and inner structure."),
    LearningModelOption("cell", "🧬", "Cell Explorer", "Biology", "Which cell structure holds most genetic material?", "Inspect the cell membrane, nucleus and illustrative organelles."),
    LearningModelOption("force", "🏀", "Forces & Motion", "Physics", "How can an unbalanced force change motion?", "Explore the ball, slope and directional force arrows."),
    LearningModelOption("water-cycle", "🌧️", "Water Cycle", "Earth Science", "What process turns surface water into vapour?", "Touch the Sun, cloud, droplets and water collection."),
    LearningModelOption("earth", "🌍", "Earth & Atmosphere", "Geography", "What causes day and night?", "Rotate Earth and inspect land and atmosphere markers."),
    LearningModelOption("circuit", "⚡", "Electric Circuit", "Physics", "What happens when a simple circuit is closed?", "Touch the battery, wire, charge markers and lamp."),
    LearningModelOption("magnet", "🧲", "Magnet Lab", "Physics", "Where are a horseshoe magnet's poles?", "Touch each pole and inspect a simplified magnetic-field illustration."),
    LearningModelOption("volcano", "🌋", "Volcano Explorer", "Earth Science", "What is molten rock called before it reaches the surface?", "Explore the crater, lava and surrounding volcanic layers."),
    LearningModelOption("flower", "🌸", "Flower Anatomy", "Biology", "Which flower part commonly attracts pollinators?", "Touch petals, centre, stem and leaves to inspect their roles."),
    LearningModelOption("social-studies", "🧭", "People & Places", "Social Studies", "How do maps help us understand communities?", "Explore the globe, map symbols and directions."),
    LearningModelOption("computer-skills", "💻", "Computer Skills", "Computer Skills", "How can we keep accounts and devices safer?", "Explore circuit parts and learn how digital tools work."),
    LearningModelOption("health-safety", "🩺", "Health & Safety", "Health & Safety", "Which daily habit helps prevent the spread of germs?", "Explore body systems and healthy choices."),
    LearningModelOption("art-design", "🎨", "Art & Design", "Art & Design", "How do primary colours mix to make new colours?", "Explore colour, shape, light and composition."),
    LearningModelOption("drawing-colour", "✏️", "Drawing & Colour", "Drawing & Colour", "How do light and shadow make a drawing look three-dimensional?", "Explore outlines, shading, perspective and colour."),
    LearningModelOption("english", "📖", "English Discovery", "English", "How do clues in a story help us understand a character?", "Explore story structure, vocabulary and reading clues."),
    LearningModelOption("urdu", "اردو", "Urdu Discovery", "Urdu", "لفظ کا مطلب سیاق و سباق سے کیسے سمجھتے ہیں؟", "الفاظ، جملوں اور کہانیوں کو دریافت کریں۔"),
    LearningModelOption("sindhi", "سنڌي", "Sindhi Discovery", "Sindhi", "جملو پڙهي لفظ جي معنيٰ ڪيئن سمجهي سگهجي ٿي؟", "سنڌي لفظن ۽ ڪهاڻين کي دريافت ڪريو۔"),
    LearningModelOption("hindi", "हिंदी", "Hindi Discovery", "Hindi", "वाक्य के संदर्भ से किसी शब्द का अर्थ कैसे समझते हैं?", "शब्दों, वाक्यों और कहानियों को खोजें।"),
    LearningModelOption("faith-islamic", "☪️", "Islamic Studies", "Islamic Studies", "Which value encourages kindness and helping others?", "Explore Islamic learning respectfully with parent-selected lessons."),
    LearningModelOption("faith-hindu", "🪔", "Hindu Studies", "Hindu Studies", "What does Diwali commonly celebrate in many Hindu traditions?", "Explore Hindu traditions respectfully with parent-selected lessons."),
    LearningModelOption("faith-christian", "✝️", "Christian Studies", "Christian Studies", "Which festival celebrates the resurrection of Jesus in Christianity?", "Explore Christian traditions respectfully with parent-selected lessons."),
    LearningModelOption("faith-sikh", "☬", "Sikh Studies", "Sikh Studies", "What does seva mean in Sikh tradition?", "Explore Sikh values, service and community."),
    LearningModelOption("faith-buddhist", "☸️", "Buddhist Studies", "Buddhist Studies", "What is mindfulness?", "Explore compassion, awareness and Buddhist traditions."),
    LearningModelOption("faith-jewish", "✡️", "Jewish Studies", "Jewish Studies", "What is Shabbat in Jewish tradition?", "Explore Jewish traditions respectfully with parent-selected lessons."),
    LearningModelOption("faith-jain", "🕊️", "Jain Studies", "Jain Studies", "What does ahimsa mean?", "Explore non-violence, care and Jain traditions."),
    LearningModelOption("faith-world", "🌍", "World Religions & Ethics", "World Religions & Ethics", "Why compare traditions respectfully?", "Explore diversity, empathy and shared ethical questions.")
)

private fun play4DAnswerSound(context: Context, correct: Boolean) {
    try {
        val tone = ToneGenerator(AudioManager.STREAM_MUSIC, 45)
        tone.startTone(
            if (correct) ToneGenerator.TONE_PROP_ACK else ToneGenerator.TONE_PROP_NACK,
            160
        )
        Handler(Looper.getMainLooper()).postDelayed({ tone.release() }, 240)
    } catch (_: Exception) {
        // Sound feedback is optional; the quiz remains usable when audio is unavailable.
    }
}

@Composable
fun Learning4DLabScreen(
    selectedModel: String,
    onSelectModel: (String) -> Unit,
    onStartSolarQuiz: () -> Unit,
    onCreateQuestion: (LearningModelOption) -> Unit,
    religiousStudyTrack: String = "none",
    gradeLevel: Int = 3,
    ageYears: Int = 9
) {
    val allowedFaithModel = when (religiousStudyTrack.lowercase()) {
        "islamic" -> "faith-islamic"
        "hindu" -> "faith-hindu"
        "christian" -> "faith-christian"
        "sikh" -> "faith-sikh"
        "buddhist" -> "faith-buddhist"
        "jewish" -> "faith-jewish"
        "jain" -> "faith-jain"
        "other" -> "faith-world"
        else -> null
    }
    // Hide every faith model unless the parent explicitly enabled that child's matching track.
    val visibleModelOptions = LearningModelOptions.filter { option ->
        !option.id.startsWith("faith-") || option.id == allowedFaithModel
    }
    val selected = visibleModelOptions.firstOrNull { it.id == selectedModel } ?: visibleModelOptions.first()
    val context = LocalContext.current
    val chosenAnswersState = remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    val questionBankSubject = if (selected.subject == "Earth Science") "General Science" else selected.subject
    val requestedQuestionLanguage = when (questionBankSubject) {
        "Urdu" -> "ur"
        "Sindhi" -> "sd"
        "Hindi" -> "hi"
        else -> "en"
    }
    val bankQuestions = FourDQuestionBanks.forLearningContext(
        questionBankSubject, requestedQuestionLanguage, gradeLevel, ageYears
    ).distinctBy { it.conceptKey }.take(10)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(25.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFECEBFF))
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                AppUiText("✨ 4D Learning Lab • Touch. Explore. Discover.", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = Color(0xFF29245F))
                AppUiText("Explore interactive 4D-style models with motion, 360° rotation, zoom and exploded views. Tap visible objects to reveal names and facts; drag to rotate. A phone screen cannot display a true four-spatial-dimension object, so the fourth dimension is shown through motion and changing states.", color = Color(0xFF566080))
            }
        }

        visibleModelOptions.chunked(2).forEach { group ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                group.forEach { item ->
                    OutlinedButton(
                        onClick = { onSelectModel(item.id) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(17.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            AppUiText(item.icon, style = MaterialTheme.typography.headlineSmall)
                            AppUiText(if (selected.id == item.id) "✓ " + item.title else item.title, fontWeight = if (selected.id == item.id) FontWeight.ExtraBold else FontWeight.SemiBold)
                            AppUiText(item.subject, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (group.size == 1) androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F8FF))
        ) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AppUiText(selected.icon + " " + selected.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                AppUiText(selected.question, fontWeight = FontWeight.SemiBold)
                if (selected.id == "solar-system") SolarSystemModelPreview()
                else Learning3DPreview(selected.id)
                AppUiText(selected.detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(
                    onClick = { onCreateQuestion(selected) },
                    modifier = Modifier.fillMaxWidth()
                ) { AppUiText("Create a 4D-style question from this model") }
                if (selected.id == "solar-system") {
                    OutlinedButton(onClick = onStartSolarQuiz, modifier = Modifier.fillMaxWidth()) {
                        AppUiText("🚀 Start planet identification challenge")
                    }
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F5FF))
        ) {
            Column(
                modifier = androidx.compose.ui.Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                AppUiText(
                    "📚 Ready-made question bank",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold
                )
                AppUiText(
                    bankQuestions.size.toString() + " starter questions for " + questionBankSubject +
                        ". These are also added automatically when you open that subject's Questions screen.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                bankQuestions.forEachIndexed { index, template ->
                    Column(
                        modifier = androidx.compose.ui.Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        AppUiText(
                            (index + 1).toString() + ". " + template.prompt,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (template.options.isNotEmpty()) {
                            template.options.forEach { option ->
                                val selectedOption = chosenAnswersState.value[template.conceptKey]
                                OutlinedButton(
                                    onClick = {
                                        chosenAnswersState.value = chosenAnswersState.value + (template.conceptKey to option)
                                        play4DAnswerSound(context, option == template.answer)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = selectedOption == null || selectedOption != template.answer
                                ) {
                                    AppUiText((if (selectedOption == option) "◉ " else "○ ") + option)
                                }
                            }
                            val selectedOption = chosenAnswersState.value[template.conceptKey]
                            if (selectedOption != null) {
                                AppUiText(
                                    if (selectedOption == template.answer) "✨ Correct! " + template.solution else "Not quite—try another choice.",
                                    color = if (selectedOption == template.answer) Color(0xFF087E61) else Color(0xFF9A4A16),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
