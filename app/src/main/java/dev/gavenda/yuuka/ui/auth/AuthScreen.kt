package dev.gavenda.yuuka.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Mirrors `LoginView.vue`. */
@Composable
fun AuthScreen(onLogin: () -> Unit, onSignUp: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("yuuka", style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold))
        Text(
            "Personal budgeting and financial tracking.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 32.dp),
        )
        Button(onClick = onLogin, modifier = Modifier.fillMaxWidth()) { Text("Log in") }
        OutlinedButton(onClick = onSignUp, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) { Text("Sign up") }
    }
}
