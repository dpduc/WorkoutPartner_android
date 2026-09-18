package com.workoutpartner.app.onboarding

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.workoutpartner.data.AuthRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Sign-up/sign-in supporting Email/Password and Google Sign-In. Wired to
 * [AuthRepository].
 */
@Composable
fun SignUpScreen(authRepository: AuthRepository, onSignedUp: (accountId: String) -> Unit, onCancel: () -> Unit) {
    AuthForm(
        title = "Sign up",
        submitLabel = "Create Account",
        authRepository = authRepository,
        onCancel = onCancel,
        onSubmitEmail = { email, password -> authRepository.signUp(email, password) },
        onSuccess = onSignedUp,
    )
}

@Composable
fun SignInScreen(authRepository: AuthRepository, onSignedIn: (accountId: String) -> Unit, onCancel: () -> Unit) {
    AuthForm(
        title = "Sign in",
        submitLabel = "Sign in",
        authRepository = authRepository,
        onCancel = onCancel,
        onSubmitEmail = { email, password -> authRepository.signIn(email, password) },
        onSuccess = onSignedIn,
    )
}

@Composable
private fun AuthForm(
    title: String,
    submitLabel: String,
    authRepository: AuthRepository,
    onSubmitEmail: suspend (email: String, password: String) -> String,
    onSuccess: (accountId: String) -> Unit,
    onCancel: () -> Unit,
) {
    var generalError by remember { mutableStateOf<String?>(null) }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall)

            EmailAuthFields(
                submitLabel = submitLabel,
                onSubmit = onSubmitEmail,
                onSuccess = onSuccess,
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HorizontalDivider(modifier = Modifier.weight(1f))
                Text(" OR ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                HorizontalDivider(modifier = Modifier.weight(1f))
            }

            GoogleSignInButton(
                authRepository = authRepository,
                onSuccess = onSuccess,
                onError = { generalError = it },
            )

            generalError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp))
            }

            TextButton(onClick = onCancel, modifier = Modifier.padding(top = 8.dp)) {
                Text("Cancel")
            }
        }
    }
}

@Composable
private fun EmailAuthFields(
    submitLabel: String,
    onSubmit: suspend (email: String, password: String) -> String,
    onSuccess: (accountId: String) -> Unit,
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
        Button(
            onClick = {
                errorMessage = null
                isSubmitting = true
                scope.launch {
                    try {
                        val accountId = onSubmit(email.trim(), password)
                        onSuccess(accountId)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        errorMessage = e.message ?: "Authentication failed — please try again."
                    } finally {
                        isSubmitting = false
                    }
                }
            },
            enabled = !isSubmitting && email.isNotBlank() && password.isNotBlank(),
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        ) {
            if (isSubmitting) CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp)) else Text(submitLabel)
        }
    }
}

@Composable
private fun GoogleSignInButton(
    authRepository: AuthRepository,
    onSuccess: (accountId: String) -> Unit,
    onError: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }

    OutlinedButton(
        onClick = {
            isLoading = true
            scope.launch {
                try {
                    val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
                    val webClientId = if (resId != 0) context.getString(resId) else null
                    if (webClientId.isNullOrBlank()) {
                        onError("Google Sign-In Web Client ID not found. Ensure Google provider is enabled in Firebase Console and google-services.json is updated.")
                        isLoading = false
                        return@launch
                    }

                    val credentialManager = CredentialManager.create(context)
                    val googleIdOption = GetGoogleIdOption.Builder()
                        .setFilterByAuthorizedAccounts(false)
                        .setServerClientId(webClientId)
                        .setAutoSelectEnabled(false)
                        .build()

                    val request = GetCredentialRequest.Builder()
                        .addCredentialOption(googleIdOption)
                        .build()

                    val result = credentialManager.getCredential(context = context, request = request)
                    val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(result.credential.data)
                    val idToken = googleIdTokenCredential.idToken
                    val accountId = authRepository.signInWithGoogle(idToken)
                    onSuccess(accountId)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    onError(e.message ?: "Google Sign-In failed.")
                } finally {
                    isLoading = false
                }
            }
        },
        enabled = !isLoading,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
        }
        Text("Continue with Google")
    }
}
