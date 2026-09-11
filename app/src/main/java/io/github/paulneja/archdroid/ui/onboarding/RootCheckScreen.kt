package io.github.paulneja.archdroid.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EnhancedEncryption
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import io.github.paulneja.archdroid.R
import io.github.paulneja.archdroid.ui.components.GlassCard
import io.github.paulneja.archdroid.ui.components.LiquidButton

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RootCheckScreen(
    backdrop: Backdrop,
    checking: Boolean,
    onRetry: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        if (checking) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                ContainedLoadingIndicator(modifier = Modifier.size(72.dp))
                Spacer(Modifier.height(24.dp))
                Text(
                    text = stringResource(R.string.checking_root),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        } else {
            GlassCard(
                backdrop = backdrop,
                modifier = Modifier.padding(32.dp),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.EnhancedEncryption,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(56.dp),
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.root_required_title),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.root_required_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(24.dp))
                    LiquidButton(
                        onClick = onRetry,
                        backdrop = backdrop,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                    ) {
                        Text(
                            text = stringResource(R.string.retry),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}
