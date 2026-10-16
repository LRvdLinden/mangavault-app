package nl.lrvdlinden.mangavault.ui.auth

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import nl.lrvdlinden.mangavault.app.LocalApp
import nl.lrvdlinden.mangavault.core.Base44
import nl.lrvdlinden.mangavault.core.Config
import nl.lrvdlinden.mangavault.core.openInBrowser
import nl.lrvdlinden.mangavault.ui.components.PrimaryButton
import nl.lrvdlinden.mangavault.ui.components.cardStyle
import nl.lrvdlinden.mangavault.ui.theme.Vault
import nl.lrvdlinden.mangavault.ui.theme.hexColor
import kotlin.coroutines.cancellation.CancellationException

private enum class AuthStep { LOGIN, REGISTER, FORGOT }

@Composable
fun AuthFlowScreen() {
    var step by remember { mutableStateOf(AuthStep.LOGIN) }
    Box(
        Modifier
            .fillMaxSize()
            .background(Vault.background)
            .systemBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier.widthIn(max = 460.dp).fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Spacer(Modifier.height(30.dp))
            Crossfade(targetState = step, label = "auth") { s ->
                when (s) {
                    AuthStep.LOGIN -> AuthLoginView { step = it }
                    AuthStep.REGISTER -> AuthRegisterView { step = it }
                    AuthStep.FORGOT -> AuthForgotView { step = it }
                }
            }
        }
    }
}

// MARK: - Building blocks

@Composable
private fun AuthCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    footer: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier
                    .size(56.dp)
                    .shadow(14.dp, RoundedCornerShape(14.dp), ambientColor = Vault.primary, spotColor = Vault.primary)
                    .background(Vault.primary, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = Color.White, modifier = Modifier.size(28.dp))
            }
            Text(title, color = Vault.foreground, fontSize = 28.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
            Text(subtitle, color = Vault.muted, fontSize = 14.sp, textAlign = TextAlign.Center)
        }
        Column(
            Modifier.fillMaxWidth().cardStyle().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
        footer()
    }
}

@Composable
private fun authFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Vault.foreground,
    unfocusedTextColor = Vault.foreground,
    focusedContainerColor = Vault.background,
    unfocusedContainerColor = Vault.background,
    focusedBorderColor = Vault.primary,
    unfocusedBorderColor = Vault.border,
    cursorColor = Vault.primary,
    focusedLeadingIconColor = Vault.muted,
    unfocusedLeadingIconColor = Vault.muted,
    focusedPlaceholderColor = Vault.muted,
    unfocusedPlaceholderColor = Vault.muted,
)

@Composable
private fun AuthField(
    label: String,
    icon: ImageVector,
    value: String,
    onChange: (String) -> Unit,
    secure: Boolean = false,
    email: Boolean = false,
    placeholder: String = "",
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = Vault.foreground, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text(placeholder) },
            leadingIcon = { Icon(icon, null, modifier = Modifier.size(18.dp)) },
            visualTransformation = if (secure) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                keyboardType = when {
                    secure -> KeyboardType.Password
                    email -> KeyboardType.Email
                    else -> KeyboardType.Text
                },
            ),
            shape = RoundedCornerShape(10.dp),
            colors = authFieldColors(),
        )
    }
}

@Composable
private fun AuthErrorBanner(text: String) {
    Text(
        text,
        color = Vault.red,
        fontSize = 13.sp,
        modifier = Modifier
            .fillMaxWidth()
            .background(Vault.red.copy(alpha = 0.1f), RoundedCornerShape(10.dp))
            .padding(12.dp),
    )
}

@Composable
private fun AuthOrDivider() {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.weight(1f).height(1.dp).background(Vault.border))
        Text("OR", color = Vault.muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Box(Modifier.weight(1f).height(1.dp).background(Vault.border))
    }
}

/** Opens the Base44 Google flow in a Custom Tab; the backend redirects to mangavault://auth?access_token=… (handled by AppState.handle). */
@Composable
private fun AuthGoogleButton() {
    val context = LocalContext.current
    OutlinedButton(
        onClick = { openInBrowser(context, Base44.googleLoginUrl("${Config.URL_SCHEME}://auth")) },
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Vault.border),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = Vault.card.copy(alpha = 0.4f), contentColor = Vault.foreground),
    ) {
        val gBrush = Brush.linearGradient(listOf(hexColor("#4285F4"), hexColor("#EA4335"), hexColor("#FBBC05"), hexColor("#34A853")))
        Text(
            "G",
            color = Color.White,
            fontSize = 17.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier
                .graphicsLayer(alpha = 0.99f)
                .drawWithContent {
                    drawContent()
                    drawRect(brush = gBrush, blendMode = BlendMode.SrcAtop)
                },
        )
        Spacer(Modifier.width(10.dp))
        Text("Continue with Google", fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

@Composable
private fun AuthFooterLink(prefix: String?, action: String, onClick: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (prefix != null) Text(prefix, color = Vault.muted, fontSize = 14.sp)
        Text(action, color = Vault.primary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable(onClick = onClick))
    }
}

private fun authMessage(e: Exception, fallback: String): String =
    e.message?.takeIf { it.isNotBlank() } ?: fallback

// MARK: - Login

@Composable
private fun AuthLoginView(go: (AuthStep) -> Unit) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }

    fun submit() {
        error = ""; loading = true
        scope.launch {
            try {
                Base44.login(email.trim(), password)
                app.didSignIn()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = authMessage(e, "Invalid email or password")
            } finally {
                loading = false
            }
        }
    }

    AuthCard(
        icon = Icons.Filled.Person,
        title = "Welcome back",
        subtitle = "Log in to your account",
        footer = { AuthFooterLink("Don't have an account?", "Create one") { go(AuthStep.REGISTER) } },
    ) {
        AuthGoogleButton()
        AuthOrDivider()
        if (error.isNotEmpty()) AuthErrorBanner(error)
        AuthField("Email", Icons.Filled.Email, email, { email = it }, email = true, placeholder = "you@example.com")
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            AuthField("Password", Icons.Filled.Lock, password, { password = it }, secure = true, placeholder = "••••••••")
            Text(
                "Forgot password?",
                color = Vault.primary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { go(AuthStep.FORGOT) },
            )
        }
        PrimaryButton(
            text = "Log in",
            onClick = { submit() },
            modifier = Modifier.fillMaxWidth(),
            enabled = email.isNotEmpty() && password.isNotEmpty(),
            loading = loading,
        )
    }
}

// MARK: - Register + OTP

@Composable
private fun AuthRegisterView(go: (AuthStep) -> Unit) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var awaitingCode by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf("") }

    fun register() {
        error = ""
        if (password != confirm) { error = "Passwords do not match"; return }
        loading = true
        scope.launch {
            try {
                Base44.register(email.trim(), password)
                awaitingCode = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = authMessage(e, "Registration failed")
            } finally {
                loading = false
            }
        }
    }

    fun verify() {
        error = ""; loading = true
        scope.launch {
            try {
                val mail = email.trim()
                Base44.verifyOtp(mail, code)
                if (Base44.token == null) Base44.login(mail, password)
                app.didSignIn()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = authMessage(e, "Invalid verification code")
            } finally {
                loading = false
            }
        }
    }

    fun resend() {
        error = ""
        scope.launch {
            try {
                Base44.resendOtp(email.trim())
                app.show("Code sent", "Check your email for the new code.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = authMessage(e, "Could not resend the code")
            }
        }
    }

    if (awaitingCode) {
        AuthCard(
            icon = Icons.Filled.Email,
            title = "Verify your email",
            subtitle = "We sent a code to ${email.trim()}",
            footer = { AuthFooterLink("Didn't receive the code?", "Resend") { resend() } },
        ) {
            if (error.isNotEmpty()) AuthErrorBanner(error)
            OutlinedTextField(
                value = code,
                onValueChange = { v -> code = v.filter { it.isDigit() }.take(6) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                singleLine = true,
                placeholder = {
                    Text("6-digit code", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontSize = 20.sp)
                },
                textStyle = TextStyle(
                    fontSize = 28.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center, color = Vault.foreground,
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                shape = RoundedCornerShape(10.dp),
                colors = authFieldColors(),
            )
            PrimaryButton(
                text = "Verify",
                onClick = { verify() },
                modifier = Modifier.fillMaxWidth(),
                enabled = code.length >= 6,
                loading = loading,
            )
        }
    } else {
        AuthCard(
            icon = Icons.Filled.PersonAdd,
            title = "Create your account",
            subtitle = "Sign up to get started",
            footer = { AuthFooterLink("Already have an account?", "Log in") { go(AuthStep.LOGIN) } },
        ) {
            AuthGoogleButton()
            AuthOrDivider()
            if (error.isNotEmpty()) AuthErrorBanner(error)
            AuthField("Email", Icons.Filled.Email, email, { email = it }, email = true, placeholder = "you@example.com")
            AuthField("Password", Icons.Filled.Lock, password, { password = it }, secure = true, placeholder = "••••••••")
            AuthField("Confirm Password", Icons.Filled.Lock, confirm, { confirm = it }, secure = true, placeholder = "••••••••")
            PrimaryButton(
                text = "Create account",
                onClick = { register() },
                modifier = Modifier.fillMaxWidth(),
                enabled = email.isNotEmpty() && password.isNotEmpty(),
                loading = loading,
            )
        }
    }
}

// MARK: - Forgot password

@Composable
private fun AuthForgotView(go: (AuthStep) -> Unit) {
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var sent by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }

    AuthCard(
        icon = Icons.Filled.Email,
        title = "Reset password",
        subtitle = "We'll send you a link to reset it",
        footer = {
            Row(
                Modifier.clickable { go(AuthStep.LOGIN) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Vault.primary, modifier = Modifier.size(16.dp))
                Text("Back to log in", color = Vault.primary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        },
    ) {
        if (sent) {
            Text(
                "If an account exists with that email, you'll receive a password reset link shortly.",
                color = Vault.foreground, fontSize = 14.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            AuthField("Email address", Icons.Filled.Email, email, { email = it }, email = true, placeholder = "you@example.com")
            PrimaryButton(
                text = "Send reset link",
                onClick = {
                    loading = true
                    scope.launch {
                        try {
                            Base44.resetPasswordRequest(email.trim())
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            // Same as iOS (try?): always show the neutral confirmation.
                        }
                        loading = false
                        sent = true
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = email.isNotEmpty(),
                loading = loading,
            )
        }
    }
}
