package com.edulock.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import com.edulock.app.AppUiText as Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.min

private data class ArtStroke(val points: List<Offset>, val color: Color, val width: Float)

@Composable
fun DrawingStudioScreen(onBack: () -> Unit) {
    val strokes = remember { mutableStateListOf<ArtStroke>() }
    var currentPoints by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var selectedColor by remember { mutableStateOf(Color(0xFF635BEE)) }
    var strokeWidth by remember { mutableStateOf(8f) }
    var guide by remember { mutableStateOf("Free draw") }
    val palette = listOf(
        Color(0xFFEF476F), Color(0xFFFF8C42), Color(0xFFFFD166), Color(0xFF06D6A0),
        Color(0xFF118AB2), Color(0xFF635BEE), Color(0xFF9B5DE5), Color(0xFF20243A),
        Color(0xFF8D5524), Color(0xFFFFFFFF)
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF0F7))) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                AppUiText("🎨 Drawing & Colour Studio", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = Color(0xFF66315A))
                AppUiText("Draw with your finger, explore colours and practise shapes. Your artwork stays on this screen unless you save or share it yourself.", color = Color(0xFF784E70))
            }
        }
        AppUiText("Choose a drawing challenge", fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Free draw", "Sun & sky", "House", "Flower").forEach { item ->
                OutlinedButton(onClick = { guide = item }, modifier = Modifier.weight(1f)) {
                    AppUiText(if (guide == item) "✓ $item" else item)
                }
            }
        }
        Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Canvas(
                modifier = Modifier.fillMaxWidth().height(330.dp)
                    .background(Color.White, RoundedCornerShape(18.dp))
                    .pointerInput(selectedColor, strokeWidth, guide) {
                        detectDragGestures(
                            onDragStart = { start -> currentPoints = listOf(start) },
                            onDrag = { change, _ ->
                                change.consume()
                                currentPoints = currentPoints + change.position
                            },
                            onDragEnd = {
                                if (currentPoints.size > 1) strokes.add(ArtStroke(currentPoints, selectedColor, strokeWidth))
                                currentPoints = emptyList()
                            },
                            onDragCancel = { currentPoints = emptyList() }
                        )
                    }
            ) {
                if (guide != "Free draw") drawGuide(guide)
                strokes.forEach { drawStroke(it.points, it.color, it.width) }
                if (currentPoints.isNotEmpty()) drawStroke(currentPoints, selectedColor, strokeWidth)
            }
        }
        AppUiText("Pick a colour", fontWeight = FontWeight.Bold)
        palette.chunked(5).forEach { rowColors ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowColors.forEach { color ->
                    OutlinedButton(
                        onClick = { selectedColor = color },
                        modifier = Modifier.weight(1f).height(42.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        AppUiText(if (selectedColor == color) "✓" else "●", color = color, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { strokeWidth = 4f }, modifier = Modifier.weight(1f)) { AppUiText(if (strokeWidth == 4f) "✓ Fine pen" else "Fine pen") }
            OutlinedButton(onClick = { strokeWidth = 8f }, modifier = Modifier.weight(1f)) { AppUiText(if (strokeWidth == 8f) "✓ Brush" else "Brush") }
            OutlinedButton(onClick = { strokeWidth = 15f }, modifier = Modifier.weight(1f)) { AppUiText(if (strokeWidth == 15f) "✓ Thick" else "Thick") }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { if (strokes.isNotEmpty()) strokes.removeAt(strokes.lastIndex) }, modifier = Modifier.weight(1f)) { AppUiText("Undo") }
            OutlinedButton(onClick = { strokes.clear(); currentPoints = emptyList() }, modifier = Modifier.weight(1f)) { AppUiText("Clear page") }
        }
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { AppUiText("Done drawing") }
    }
}

private fun DrawScope.drawStroke(points: List<Offset>, color: Color, width: Float) {
    if (points.isEmpty()) return
    if (points.size == 1) {
        drawCircle(color = color, radius = width / 2f, center = points.first())
        return
    }
    val path = Path().apply {
        moveTo(points.first().x, points.first().y)
        points.drop(1).forEach { lineTo(it.x, it.y) }
    }
    drawPath(path, color, style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

private fun DrawScope.drawGuide(type: String) {
    val ink = Color(0xFFB8C1D9)
    val w = size.width
    val h = size.height
    when (type) {
        "Sun & sky" -> {
            drawCircle(ink, radius = min(w, h) * .09f, center = Offset(w * .28f, h * .28f), style = Stroke(4f))
            for (i in 0 until 8) {
                val angle = i * Math.PI / 4.0
                val start = Offset(w * .28f + kotlin.math.cos(angle).toFloat() * w * .12f, h * .28f + kotlin.math.sin(angle).toFloat() * h * .12f)
                val end = Offset(w * .28f + kotlin.math.cos(angle).toFloat() * w * .16f, h * .28f + kotlin.math.sin(angle).toFloat() * h * .16f)
                drawLine(ink, start, end, 3f)
            }
            drawLine(ink, Offset(0f, h * .75f), Offset(w, h * .75f), 3f)
        }
        "House" -> {
            val x1 = w * .18f; val x2 = w * .82f; val y1 = h * .42f; val y2 = h * .82f
            drawLine(ink, Offset(x1, y1), Offset(w * .5f, h * .16f), 4f)
            drawLine(ink, Offset(w * .5f, h * .16f), Offset(x2, y1), 4f)
            drawLine(ink, Offset(x1, y1), Offset(x1, y2), 4f); drawLine(ink, Offset(x2, y1), Offset(x2, y2), 4f)
            drawLine(ink, Offset(x1, y2), Offset(x2, y2), 4f)
            drawRect(ink, topLeft = Offset(w * .43f, h * .59f), size = androidx.compose.ui.geometry.Size(w * .14f, h * .23f), style = Stroke(3f))
            drawRect(ink, topLeft = Offset(w * .24f, h * .49f), size = androidx.compose.ui.geometry.Size(w * .12f, h * .12f), style = Stroke(3f))
            drawRect(ink, topLeft = Offset(w * .64f, h * .49f), size = androidx.compose.ui.geometry.Size(w * .12f, h * .12f), style = Stroke(3f))
        }
        "Flower" -> {
            val c = Offset(w * .5f, h * .35f)
            for (i in 0 until 6) {
                val a = i * Math.PI / 3.0
                drawCircle(ink, w * .07f, Offset(c.x + kotlin.math.cos(a).toFloat() * w * .1f, c.y + kotlin.math.sin(a).toFloat() * w * .1f), style = Stroke(3f))
            }
            drawCircle(ink, w * .055f, c, style = Stroke(3f))
            drawLine(ink, Offset(w * .5f, h * .45f), Offset(w * .5f, h * .88f), 4f)
            drawOval(ink, topLeft = Offset(w * .32f, h * .63f), size = androidx.compose.ui.geometry.Size(w * .18f, h * .08f), style = Stroke(3f))
            drawOval(ink, topLeft = Offset(w * .5f, h * .72f), size = androidx.compose.ui.geometry.Size(w * .18f, h * .08f), style = Stroke(3f))
        }
    }
}