package com.edulock.app

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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



    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Parent PIN & fingerprint") },
        text = {
            Column {
                Text(
                    "Protect parent settings with a private 4–12 digit PIN and optional fingerprint. Your PIN is stored as a salted verifier, not readable text.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Security timing: the first time EduLock opens, it asks you to unlock. After leaving EduLock, it locks again after 30 seconds. If you remove EduLock from Recents, it locks immediately. Fingerprint unlocks first when enabled; PIN remains the backup method.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                        } else if (existingPin.isBlank()) {
                            error = "Enter your current parent PIN to change fingerprint protection."
                        } else if (!ParentSecurity.verifyPin(context, existingPin.toCharArray())) {
                            existingPin = ""
                            error = "Incorrect parent PIN. Enter it again."
                        } else if (biometricEnabled) {
                            // Disabling must remain possible even if the sensor is unavailable.
                            ParentSecurity.setBiometricEnabled(context, false)
                            onSaved("Fingerprint protection disabled.")
                            onDismiss()
                        } else {
                            val availability = BiometricManager.from(context)
                                .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                            if (availability == BiometricManager.BIOMETRIC_SUCCESS) {
                                ParentSecurity.setBiometricEnabled(context, true)
                                onSaved("Fingerprint protection enabled. You can now unlock EduLock with fingerprint.")
                                onDismiss()
                            } else {
                                error = when (availability) {
                                    BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> "Enroll a fingerprint in Android Settings first."
                                    BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> "This phone has no supported biometric sensor."
                                    BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> "The biometric sensor is temporarily unavailable."
                                    else -> "Strong fingerprint authentication is not available on this phone."
                                }
                            }
                        }
                    },
                    enabled = pinSet,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (biometricEnabled) "Disable fingerprint" else "Enable fingerprint")
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
    val activity = context as? FragmentActivity
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var biometricPromptStarted by remember { mutableStateOf(false) }
    var biometricAttempted by remember { mutableStateOf(false) }
    val biometricEnabled = ParentSecurity.isBiometricEnabled(context)

    fun startBiometric() {
        if (!biometricEnabled) {
            error = "Fingerprint unlock is disabled. Enter your parent PIN."
            return
        }
        if (activity == null) {
            error = "Fingerprint is unavailable here. Enter your parent PIN."
            return
        }
        val availability = BiometricManager.from(context)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        if (availability != BiometricManager.BIOMETRIC_SUCCESS) {
            error = when (availability) {
                BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> "No fingerprint is enrolled. Unlock with your parent PIN."
                BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> "This device has no fingerprint sensor. Unlock with your parent PIN."
                else -> "Fingerprint is temporarily unavailable. Unlock with your parent PIN."
            }
            biometricAttempted = true
            return
        }
        if (biometricPromptStarted) return
        biometricPromptStarted = true
        val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(context),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onUnlocked()
                }
                override fun onAuthenticationFailed() {
                    error = "Fingerprint not recognized. Try again or enter your parent PIN."
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    biometricPromptStarted = false
                    biometricAttempted = true
                    error = if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                        errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                        errorCode == BiometricPrompt.ERROR_CANCELED
                    ) {
                        "Use your parent PIN below, or tap Try fingerprint again."
                    } else {
                        "$errString. You can unlock with your parent PIN."
                    }
                }
            })
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock EduLock")
                .setSubtitle("Verify your fingerprint to continue")
                .setNegativeButtonText("Use parent PIN")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .build()
        )
    }

    LaunchedEffect(biometricEnabled) {
        if (biometricEnabled && !biometricAttempted) startBiometric()
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = {},
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        androidx.compose.material3.Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 32.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
            ) {
                Text("EduLock locked", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(12.dp))
                Text(
                    if (biometricEnabled && !biometricAttempted)
                        "Confirm your fingerprint to return to EduLock."
                    else
                        "Enter the parent PIN to continue. Your family settings stay hidden until you unlock.",
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(Modifier.height(28.dp))
                if (biometricEnabled) {
                    Button(onClick = {
                        error = null
                        biometricAttempted = false
                        biometricPromptStarted = false
                        startBiometric()
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text("Try fingerprint")
                    }
                    Spacer(Modifier.height(12.dp))
                }
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter(Char::isDigit).take(12); error = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Parent PIN") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                )
                error?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = {
                        if (ParentSecurity.verifyPin(context, pin.toCharArray())) onUnlocked()
                        else {
                            pin = ""
                            error = "Incorrect parent PIN. Please try again."
                        }
                    },
                    enabled = pin.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Unlock with PIN") }
            }
        }
    }
}
