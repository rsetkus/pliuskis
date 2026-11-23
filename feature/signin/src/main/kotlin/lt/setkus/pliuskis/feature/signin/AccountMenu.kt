package lt.setkus.pliuskis.feature.signin

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import lt.setkus.pliuskis.core.designsystem.icon.PliuskisIcons

/**
 * The "Account" icon button of the device list's top bar and its menu: "Sign out" and "Delete account". Deleting asks
 * for confirmation first ([AccountDeletionDialog]); this menu only starts it.
 */
@Composable
fun AccountMenu(
    onSignOut: () -> Unit,
    onDeleteAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box(modifier = modifier) {
        IconButton(onClick = { expanded = true }) {
            Icon(imageVector = PliuskisIcons.Account, contentDescription = stringResource(id = R.string.account_menu))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(text = stringResource(id = R.string.account_sign_out)) },
                onClick = {
                    expanded = false
                    onSignOut()
                }
            )
            DropdownMenuItem(
                text = {
                    Text(text = stringResource(id = R.string.account_delete), color = MaterialTheme.colorScheme.error)
                },
                onClick = {
                    expanded = false
                    onDeleteAccount()
                }
            )
        }
    }
}
