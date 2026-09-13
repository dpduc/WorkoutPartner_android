package com.workoutpartner.app.onboarding

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.workoutpartner.data.AuthRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Sign-up/sign-in, wired to ticket 07's [AuthRepository] (ticket 13's own
 * scope line). [AuthRepository.signUp] is also where a Guest's local data
 * gets migrated (ticket 08) — handled entirely inside `signUp` itself, this
 * screen just calls it and reports the outcome.
 */
@Composable
fun SignUpScreen(authRepository: AuthRepository, onSignedUp: (accountId: String) -> Unit, onCancel: () -> Unit) {
    AuthForm(
        title = "Sign up",
        submitLabel = "Create Account",
        onCancel = onCancel,
        onSubmit = { email, password -> authRepository.signUp(email, password) },
        onSuccess = onSignedUp,
    )
}

@Composable
fun SignInScreen(authRepository: AuthRepository, onSignedIn: (accountId: String) -> Unit, onCancel: () -> Unit) {
    AuthForm(
        title = "Sign in",
        submitLabel = "Sign in",
        onCancel = onCancel,
        onSubmit = { email, password -> authRepository.signIn(email, password) },
        onSuccess = onSignedIn,
    )
}

@Composable
private fun AuthForm(
    title: String,
    submitLabel: String,
    onSubmit: suspend (email: String, password: String) -> String,
    onSuccess: (accountId: String) -> Unit,
    onCancel: () -> Unit,
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
            errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp)) }
            Button(
                onClick = {
                    errorMessage = null
                    isSubmitting = true
                    scope.launch {
                        try {
                            // Only onSubmit (the actual Firebase call) is
                            // what can legitimately fail here — wrapping
                            // onSuccess too would report a bug in the
                            // caller's own navigation as "Something went
                            // wrong," and catching plain Exception would
                            // silently swallow CancellationException if
                            // this composable left composition mid-request.
                            val accountId = onSubmit(email, password)
                            onSuccess(accountId)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            errorMessage = e.message ?: "Something went wrong — please try again."
                        } finally {
                            isSubmitting = false
                        }
                    }
                },
                enabled = !isSubmitting && email.isNotBlank() && password.isNotBlank(),
                modifier = Modifier.padding(top = 20.dp),
            ) {
                if (isSubmitting) CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp)) else Text(submitLabel)
            }
            TextButton(onClick = onCancel, modifier = Modifier.padding(top = 4.dp)) { Text("Cancel") }
        }
    }
}
