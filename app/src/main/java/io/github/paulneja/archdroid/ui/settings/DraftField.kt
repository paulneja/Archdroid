package io.github.paulneja.archdroid.ui.settings

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import io.github.paulneja.archdroid.R

@Composable
fun DraftField(
    value: String,
    label: String,
    onCommit: (String) -> Unit,
    modifier: Modifier = Modifier,
    isValid: (String) -> Boolean = { true },
    monospace: Boolean = true,
) {
    var draft by rememberSaveable(value) { mutableStateOfString(value) }
    val dirty = draft != value
    val valid = isValid(draft)
    val commit = { if (dirty && valid) onCommit(draft) }
    OutlinedTextField(
        value = draft,
        onValueChange = { draft = it },
        label = { Text(label) },
        singleLine = true,
        isError = !valid,
        modifier = modifier,
        textStyle = if (monospace) {
            MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
        } else {
            MaterialTheme.typography.bodyMedium
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { commit() }),
        trailingIcon = {
            if (dirty) {
                IconButton(onClick = commit, enabled = valid) {
                    Icon(Icons.Rounded.Check, contentDescription = stringResource(R.string.apply))
                }
            }
        },
    )
}

private fun mutableStateOfString(value: String) = androidx.compose.runtime.mutableStateOf(value)
