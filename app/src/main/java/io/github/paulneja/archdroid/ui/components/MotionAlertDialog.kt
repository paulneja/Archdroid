package io.github.paulneja.archdroid.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.paulneja.archdroid.ui.theme.panelEntrance

@Composable
fun MotionAlertDialog(onDismissRequest: () -> Unit, confirmButton: @Composable () -> Unit,
                      title: @Composable () -> Unit, text: @Composable () -> Unit,
                      dismissButton: (@Composable () -> Unit)? = null, icon: (@Composable () -> Unit)? = null) {
    AlertDialog(onDismissRequest = onDismissRequest, confirmButton = confirmButton, title = title,
        text = text, dismissButton = dismissButton, icon = icon, modifier = Modifier.panelEntrance())
}
