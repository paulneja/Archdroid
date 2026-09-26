package io.github.paulneja.archdroid.ui.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kyant.backdrop.Backdrop
import io.github.paulneja.archdroid.R
import io.github.paulneja.archdroid.ui.install.InstallPlan
import io.github.paulneja.archdroid.ui.components.GlassCard
import io.github.paulneja.archdroid.ui.components.LiquidButton

@Composable
fun UserScreen(
    backdrop: Backdrop,
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val user = settings.user
    var newPassword by remember { mutableStateOf("") }
    val applyError by viewModel.applyError.collectAsStateWithLifecycle()

    SettingsSubScreen(backdrop, stringResource(R.string.settings_user), onBack) {
        applyError?.let { ApplyErrorCard(backdrop, it, viewModel::clearApplyError) }
        GlassCard(backdrop = backdrop, modifier = Modifier.fillMaxWidth()) {
            SwitchRow(
                stringResource(R.string.wizard_login_as_root),
                user.loginAsRoot,
                backdrop,
            ) { viewModel.updateUser(user.copy(loginAsRoot = it)) }

            if (!user.loginAsRoot) {
                Spacer(Modifier.height(8.dp))
                DraftField(
                    value = user.username,
                    label = stringResource(R.string.wizard_username),
                    onCommit = { viewModel.updateUser(user.copy(username = it)) },
                    isValid = InstallPlan::usernameValid,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = { Text(stringResource(R.string.user_new_password)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                LiquidButton(
                    onClick = {
                        viewModel.updateUser(user, newPassword)
                        newPassword = ""
                    },
                    backdrop = backdrop,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                    enabled = newPassword.length >= 4,
                ) {
                    Text(
                        stringResource(R.string.user_change_password),
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
            }
        }
    }
}
