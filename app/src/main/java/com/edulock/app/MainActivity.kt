package com.edulock.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { EduLockApp() }
    }
}

private enum class ProfileMode { Parent, Child }

@Composable
private fun EduLockApp() {
    var mode by remember { mutableStateOf(ProfileMode.Parent) }
    MaterialTheme {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Spacer(Modifier.height(12.dp))
                Text("EduLock", fontSize = 32.sp, fontWeight = FontWeight.Bold)
                Text("Learn first. Play with purpose.", style = MaterialTheme.typography.bodyLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (mode == ProfileMode.Parent) {
                        Button(onClick = { mode = ProfileMode.Parent }) { Text("Parent") }
                        OutlinedButton(onClick = { mode = ProfileMode.Child }) { Text("Child") }
                    } else {
                        OutlinedButton(onClick = { mode = ProfileMode.Parent }) { Text("Parent") }
                        Button(onClick = { mode = ProfileMode.Child }) { Text("Child") }
                    }
                }
                if (mode == ProfileMode.Parent) ParentHome() else ChildHome()
                Spacer(Modifier.weight(1f))
                Text("Foundation preview: online sign-in and Supabase sync are not connected yet.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ParentHome() {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Parent dashboard", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text("Create subjects and questions in your preferred language, set learning tasks, and choose reward limits.")
            Button(onClick = {}, modifier = Modifier.fillMaxWidth()) { Text("Manage subjects & questions") }
            OutlinedButton(onClick = {}, modifier = Modifier.fillMaxWidth()) { Text("Tasks, rewards & app limits") }
            OutlinedButton(onClick = {}, modifier = Modifier.fillMaxWidth()) { Text("Child progress report") }
        }
    }
}

@Composable
private fun ChildHome() {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.Start) {
            Text("Your learning space", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text("Complete your assigned questions to earn the screen time your parent has allowed.")
            Button(onClick = {}, modifier = Modifier.fillMaxWidth()) { Text("View my tasks") }
            Text("Earned time: —", style = MaterialTheme.typography.bodyLarge)
        }
    }
}
