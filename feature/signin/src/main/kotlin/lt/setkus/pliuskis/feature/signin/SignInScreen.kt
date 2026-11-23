package lt.setkus.pliuskis.feature.signin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import lt.setkus.pliuskis.core.auth.AuthProvider
import lt.setkus.pliuskis.core.designsystem.icon.PliuskisIcons
import lt.setkus.pliuskis.core.designsystem.theme.Spacing
import lt.setkus.pliuskis.core.presentation.signin.SignInError
import lt.setkus.pliuskis.core.presentation.signin.SignInState
import lt.setkus.pliuskis.core.presentation.signin.SignInViewModel
import org.koin.androidx.compose.koinViewModel

private val LogoContainerSize = 88.dp
private val LogoSize = 48.dp
private val ContentMaxWidth = 420.dp
private val ButtonProgressSize = 18.dp
private val ButtonProgressStroke = 2.dp

/** The sign-in screen over the shared [SignInViewModel]; its ViewModel lives as long as the sign-in destination. */
@Composable
internal fun SignInRoute(
    modifier: Modifier = Modifier,
    viewModel: SignInViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SignInScreen(
        state = state,
        onGoogleClick = { viewModel.signIn(AuthProvider.GOOGLE) },
        onAppleClick = { viewModel.signIn(AuthProvider.APPLE) },
        modifier = modifier
    )
}

/**
 * The first screen of a signed-out app: "Continue with Google" and "Sign in with Apple". While a sign-in runs
 * ([SignInState.SigningIn]) both buttons are disabled and the chosen one shows progress; after a failure
 * ([SignInState.Error]) an error line says what happened, and either button starts a new sign-in. Stateless.
 */
@Composable
internal fun SignInScreen(
    state: SignInState,
    onGoogleClick: () -> Unit,
    onAppleClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val busy = state is SignInState.SigningIn || state == SignInState.SignedIn
    val signingInWith = (state as? SignInState.SigningIn)?.provider
    Box(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = ContentMaxWidth)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg, vertical = Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Logo()
            Spacer(modifier = Modifier.height(Spacing.lg))
            Text(
                text = stringResource(id = R.string.sign_in_title),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() }
            )
            Spacer(modifier = Modifier.height(Spacing.xs))
            Text(
                text = stringResource(id = R.string.sign_in_message),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(Spacing.xl))
            GoogleButton(enabled = !busy, inProgress = signingInWith == AuthProvider.GOOGLE, onClick = onGoogleClick)
            Spacer(modifier = Modifier.height(Spacing.sm))
            AppleButton(enabled = !busy, inProgress = signingInWith == AuthProvider.APPLE, onClick = onAppleClick)
            if (state is SignInState.Error) {
                Spacer(modifier = Modifier.height(Spacing.md))
                ErrorLine(reason = state.reason)
            }
        }
    }
}

@Composable
private fun Logo() {
    Box(
        modifier = Modifier
            .size(LogoContainerSize)
            .background(MaterialTheme.colorScheme.primaryContainer, shape = CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = PliuskisIcons.Plant,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(LogoSize)
        )
    }
}

@Composable
private fun GoogleButton(enabled: Boolean, inProgress: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.minTouchTarget)
    ) {
        ButtonContent(label = stringResource(id = R.string.sign_in_google), inProgress = inProgress)
    }
}

/** Apple's colours: black on a light background, white on a dark one. */
@Composable
private fun AppleButton(enabled: Boolean, inProgress: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.onSurface,
            contentColor = MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.minTouchTarget)
    ) {
        ButtonContent(label = stringResource(id = R.string.sign_in_apple), inProgress = inProgress)
    }
}

@Composable
private fun ButtonContent(label: String, inProgress: Boolean) {
    if (inProgress) {
        val progressDescription = stringResource(id = R.string.sign_in_progress)
        CircularProgressIndicator(
            strokeWidth = ButtonProgressStroke,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(ButtonProgressSize)
                .semantics { contentDescription = progressDescription }
        )
        Spacer(modifier = Modifier.size(Spacing.xs))
    }
    Text(text = label, style = MaterialTheme.typography.labelLarge)
}

@Composable
private fun ErrorLine(reason: SignInError) {
    val text = when (reason) {
        SignInError.NETWORK -> stringResource(id = R.string.sign_in_error_network)
        SignInError.FAILED -> stringResource(id = R.string.sign_in_error_failed)
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
        textAlign = TextAlign.Center,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
    )
}
