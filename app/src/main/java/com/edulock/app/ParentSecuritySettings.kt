package com.edulock.app

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import java.util.concurrent.Executor
import androidx.core.content.ContextCompat

@Composable
internal fun ParentSecuritySettingsDialog(
    onDismiss: () -> Unit,
    onSaved: (String) -> Unit
) {
    val context = LocalContext.current
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var existingPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val pinSet = ParentSecurity.isPinSet(context)
    val biometricEnabled = ParentSecurity.isBiometricEnabled(context)

    fun authenticateBiometric() {
        val activity = context as? FragmentActivity
        if (activity == null) {
            error = "Biometric authentication is unavailable in this activity."
            return
        }
        val manager = BiometricManager.from(context)
        val availability = manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        if (availability != BiometricManager.BIOMETRIC_SUCCESS) {
            error = when (availability) {
                BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> "Enroll a fingerprint in Android Settings first."
                BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> "This phone has no supported biometric sensor."
                BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> "The biometric sensor is temporarily unavailable."
                else -> "Strong fingerprint authentication is not available on this phone."
            }
            return
        }
        val executor: Executor = ContextCompat.getMainExecutor(context)
        val prompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                ParentSecurity.setBiometricEnabled(context, !biometricEnabled)
                onSaved(if (biometricEnabled) "Fingerprint protection disabled." else "Fingerprint protection enabled.")
                onDismiss()
            }
            override fun onAuthenticationFailed() {
                error = "Fingerprint not recognized. Try again."
                busy = false
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                error = errString.toString()
                busy = false
            }
        })
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(if (biometricEnabled) "Disable fingerprint protection" else "Enable fingerprint protection")
                .setSubtitle("Confirm with your enrolled fingerprint")
                .setNegativeButtonText("Cancel")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .build()
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Parent PIN & fingerprint") },
        text = {
            Column {
                Text(
                    "Set a parent-only PIN (4–12 digits). The PIN is stored as a salted PBKDF2 verifier, not as plain text.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(12.dp))
                if (pinSet) {
                    OutlinedTextField(
                        value = existingPin,
                        onValueChange = { existingPin = it.filter(Char::isDigit).take(12); error = null },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Current parent PIN") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                    )
                }
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter(Char::isDigit).take(12); error = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (pinSet) "New PIN" else "Create PIN") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                )
                OutlinedTextField(
                    value = confirmPin,
                    onValueChange = { confirmPin = it.filter(Char::isDigit).take(12); error = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Confirm PIN") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = {
                        if (!pinSet) {
                            error = "Create a parent PIN before enabling fingerprint protection."
                        } else if (!ParentSecurity.verifyPin(context, existingPin.toCharArray())) {
                            error = "Enter the correct current parent PIN before changing fingerprint protection."
                        } else {
                            authenticateBiometric()
                        }
                    },
                    enabled = pinSet && existingPin.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (biometricEnabled) "Turn fingerprint protection OFF" else "Turn fingerprint protection ON")
                }
                Text(
                    if (biometricEnabled) "Fingerprint protection is enabled on this device." else "Fingerprint protection is currently off.",
                    style = MaterialTheme.typography.bodySmall
                )
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !busy && pin.length in 4..12 && pin == confirmPin && (!pinSet || existingPin.isNotBlank()),
                onClick = {
                    if (pinSet && !ParentSecurity.verifyPin(context, existingPin.toCharArray())) {
                        error = "Current PIN is incorrect."
                        return@Button
                    }
                    try {
                        ParentSecurity.setPin(context, pin.toCharArray())
                        onSaved("Parent PIN saved.")
                        onDismiss()
                    } catch (e: Exception) {
                        error = e.message ?: "Could not save PIN."
                    }
                }
            ) { Text(if (pinSet) "Change PIN" else "Save PIN") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}


@Composable
internal fun ParentSecurityLockDialog(onUnlocked: () -> Unit) {
    val context = LocalContext.current
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    fun startBiometric() {
        val activity = context as? FragmentActivity
        if (activity == null) {
            error = "Fingerprint authentication is unavailable."
            return
        }
        val availability = BiometricManager.from(context)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        if (availability != BiometricManager.BIOMETRIC_SUCCESS) {
            error = "Fingerprint is unavailable. Unlock with your parent PIN."
            return
        }
        val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(context),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { onUnlocked() }
                override fun onAuthenticationFailed() { error = "Fingerprint not recognized." }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) { error = errString.toString() }
            })
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock EduLock")
                .setSubtitle("Verify it is the parent")
                .setNegativeButtonText("Use PIN")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .build()
        )
    }

    AlertDialog(
        onDismissRequest = {},
        title = { Text("Parent authentication required") },
        text = {
            Column {
                Text("EduLock was left in the background. Enter the parent PIN to continue.")
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter(Char::isDigit).take(12); error = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Parent PIN") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                )
                if (ParentSecurity.isBiometricEnabled(context)) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { startBiometric() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Unlock with fingerprint")
                    }
                }
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (ParentSecurity.verifyPin(context, pin.toCharArray())) onUnlocked()
                else { pin = ""; error = "Incorrect parent PIN." }
            }, enabled = pin.isNotEmpty()) { Text("Unlock") }
        },
        dismissButton = {}
    )
}
