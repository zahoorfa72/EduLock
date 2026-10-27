package com.edulock.app

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
    LearningModelOption("solar-system", "🪐", "Solar System", "Astronomy", "Can you identify a planet from its colour, size and orbit?", "Tap planets in the animated orbital model to reveal planet facts."),
    LearningModelOption("atom", "⚛️", "Atom Explorer", "Chemistry", "Which negatively charged particle is shown around the nucleus?", "Touch the nucleus and electron markers to learn their roles."),
    LearningModelOption("molecule", "🧪", "Molecule Lab", "Chemistry", "Which two elements form a water molecule?", "Tap the oxygen, hydrogen atoms and bonds to inspect the water model."),
    LearningModelOption("geometry", "📐", "3D Shape Studio", "Mathematics", "How many faces, edges and vertices does a cube have?", "Rotate the cube and touch individual faces."),
    LearningModelOption("cell", "🧬", "Cell Explorer", "Biology", "Which cell structure holds most genetic material?", "Inspect the cell membrane, nucleus and illustrative organelles."),
    LearningModelOption("force", "🏀", "Forces & Motion", "Physics", "How can an unbalanced force change motion?", "Explore the ball, slope and directional force arrows."),
    LearningModelOption("water-cycle", "🌧️", "Water Cycle", "Earth Science", "What process turns surface water into vapour?", "Touch the Sun, cloud, droplets and water collection."),
    LearningModelOption("earth", "🌍", "Earth & Atmosphere", "Geography", "What causes day and night?", "Rotate Earth and inspect land and atmosphere markers."),
    LearningModelOption("circuit", "⚡", "Electric Circuit", "Physics", "What happens when a simple circuit is closed?", "Touch the battery, wire, charge markers and lamp."),
    LearningModelOption("magnet", "🧲", "Magnet Lab", "Physics", "Where are a horseshoe magnet's poles?", "Touch each pole and inspect a simplified magnetic-field illustration."),
    LearningModelOption("volcano", "🌋", "Volcano Explorer", "Earth Science", "What is molten rock called before it reaches the surface?", "Explore the crater, lava and surrounding volcanic layers."),
    LearningModelOption("flower", "🌸", "Flower Anatomy", "Biology", "Which flower part commonly attracts pollinators?", "Touch petals, centre, stem and leaves to inspect their roles.")
)

@Composable
fun Learning3DLabScreen(
    selectedModel: String,
    onSelectModel: (String) -> Unit,
    onStartSolarQuiz: () -> Unit,
    onCreateQuestion: (LearningModelOption) -> Unit
) {
    val selected = LearningModelOptions.firstOrNull { it.id == selectedModel } ?: LearningModelOptions.first()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(25.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFECEBFF))
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                AppUiText("✨ Touch. Explore. Discover.", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = Color(0xFF29245F))
                AppUiText("Choose a subject model below. Tap visible objects to reveal their names and facts; drag the model to rotate it.", color = Color(0xFF566080))
            }
        }

        LearningModelOptions.chunked(2).forEach { group ->
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
                ) { AppUiText("Create a question from this model") }
                if (selected.id == "solar-system") {
                    OutlinedButton(onClick = onStartSolarQuiz, modifier = Modifier.fillMaxWidth()) {
                        AppUiText("🚀 Start planet identification challenge")
                    }
                }
            }
        }
    }
}
