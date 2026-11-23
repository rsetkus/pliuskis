package lt.setkus.pliuskis.feature.signin

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import lt.setkus.pliuskis.core.designsystem.theme.Spacing
import lt.setkus.pliuskis.core.presentation.session.AccountDeletionState

private val DeletingProgressSize = 18.dp

/**
 * The dialogs of the "Delete account" flow, for the session's [state]: the confirmation (Delete or Cancel; nothing is
 * deleted before Delete), the same dialog with progress while Cognito deletes the user, and a failure message (Try
 * again or Cancel) after which the user is still signed in. Nothing is shown while [AccountDeletionState.Idle].
 */
@Composable
fun AccountDeletionDialog(
    state: AccountDeletionState,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    when (state) {
        AccountDeletionState.Idle -> Unit
        AccountDeletionState.Confirming, AccountDeletionState.Deleting -> ConfirmDeletionDialog(
            deleting = state == AccountDeletionState.Deleting,
            onConfirm = onConfirm,
            onCancel = onCancel
        )
        AccountDeletionState.DeleteFailed -> AlertDialog(
            onDismissRequest = onCancel,
            title = { Text(text = stringResource(id = R.string.account_delete_failed_title)) },
            text = { Text(text = stringResource(id = R.string.account_delete_failed_message)) },
            confirmButton = {
                TextButton(onClick = onConfirm) { Text(text = stringResource(id = R.string.account_delete_retry)) }
            },
            dismissButton = {
                TextButton(onClick = onCancel) { Text(text = stringResource(id = R.string.account_delete_cancel)) }
            }
        )
    }
}

@Composable
private fun ConfirmDeletionDialog(deleting: Boolean, onConfirm: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        // While Cognito deletes the user, the dialog stays: it can't be cancelled half way.
        onDismissRequest = { if (!deleting) onCancel() },
        title = { Text(text = stringResource(id = R.string.account_delete_title)) },
        text = {
            if (deleting) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(DeletingProgressSize))
                    Spacer(modifier = Modifier.size(Spacing.sm))
                    Text(text = stringResource(id = R.string.account_deleting))
                }
            } else {
                Text(text = stringResource(id = R.string.account_delete_message))
            }
        },
        confirmButton = {
            val colors = MaterialTheme.colorScheme
            TextButton(onClick = onConfirm, enabled = !deleting) {
                Text(
                    text = stringResource(id = R.string.account_delete_confirm),
                    color = if (deleting) colors.onSurfaceVariant else colors.error
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel, enabled = !deleting) {
                Text(text = stringResource(id = R.string.account_delete_cancel))
            }
        }
    )
}
