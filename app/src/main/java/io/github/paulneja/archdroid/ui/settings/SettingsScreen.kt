package io.github.paulneja.archdroid.ui.settings

import androidx.compose.runtime.getValue
import android.app.Activity
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kyant.backdrop.Backdrop
import io.github.paulneja.archdroid.R
import io.github.paulneja.archdroid.core.util.AppLanguage
import io.github.paulneja.archdroid.data.model.AppSettings
import io.github.paulneja.archdroid.data.model.VisualStyle
import io.github.paulneja.archdroid.ui.Routes
import io.github.paulneja.archdroid.ui.components.GlassCard
import io.github.paulneja.archdroid.ui.components.LiquidToggle
import io.github.paulneja.archdroid.ui.components.MotionIconButton
import io.github.paulneja.archdroid.ui.components.ScreenPage
import io.github.paulneja.archdroid.ui.components.SelectionRow
import io.github.paulneja.archdroid.ui.theme.LocalAppMotion
import io.github.paulneja.archdroid.ui.theme.panelEntrance
import io.github.paulneja.archdroid.ui.theme.pressMotion

@Composable
fun SettingsScreen(
    backdrop: Backdrop,
    onOpenSection: (String) -> Unit,
    viewModel: SettingsViewModel = viewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    ScreenPage(stringResource(R.string.nav_settings), Icons.Rounded.Settings) {
        AppearanceCard(backdrop, settings, viewModel)

        GlassCard(backdrop = backdrop, modifier = Modifier.fillMaxWidth(), entranceDelay = 60) {
            SettingsRow(Icons.Rounded.Folder, stringResource(R.string.settings_mounts)) {
                onOpenSection(Routes.SETTINGS_MOUNTS)
            }
            SettingsRow(Icons.Rounded.Terminal, stringResource(R.string.settings_env)) {
                onOpenSection(Routes.SETTINGS_ENV)
            }
            SettingsRow(Icons.Rounded.Person, stringResource(R.string.settings_user)) {
                onOpenSection(Routes.SETTINGS_USER)
            }
            SettingsRow(Icons.Rounded.PlayCircle, stringResource(R.string.settings_behavior)) {
                onOpenSection(Routes.SETTINGS_BEHAVIOR)
            }
            SettingsRow(Icons.Rounded.Memory, stringResource(R.string.settings_init)) {
                onOpenSection(Routes.SETTINGS_INIT)
            }
            SettingsRow(Icons.Rounded.BatteryChargingFull, stringResource(R.string.settings_protection)) {
                onOpenSection(Routes.SETTINGS_PROTECTION)
            }
            SettingsRow(Icons.Rounded.Backup, stringResource(R.string.settings_backup)) {
                onOpenSection(Routes.SETTINGS_BACKUP)
            }
            SettingsRow(Icons.Rounded.Info, stringResource(R.string.settings_about)) {
                onOpenSection(Routes.SETTINGS_ABOUT)
            }
        }

        LanguageCard(backdrop)

        GlassCard(backdrop = backdrop, modifier = Modifier.fillMaxWidth(), entranceDelay = 180) {
            SwitchRow(stringResource(R.string.settings_dynamic_color), settings.dynamicColor, backdrop, viewModel::updateDynamicColor)
        }
    }
}

@Composable
private fun AppearanceCard(backdrop: Backdrop, settings: AppSettings, viewModel: SettingsViewModel) {
    GlassCard(backdrop, Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.settings_appearance), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VisualStyle.entries.forEach { style ->
                val selected = settings.visualStyle == style
                val motion = LocalAppMotion.current
                val amount = animateFloatAsState(if (selected) 1f else 0f, if (motion) tween(240) else snap(), label = "style selection")
                val interaction = remember { MutableInteractionSource() }
                val color = when (style) {
                    VisualStyle.CHROME -> Color(0xFFCACDD4)
                    VisualStyle.AURORA -> Color(0xFFBBA2FF)
                    VisualStyle.EMBER -> Color(0xFFFFAD77)
                }
                val label = stringResource(when (style) {
                    VisualStyle.CHROME -> R.string.style_chrome
                    VisualStyle.AURORA -> R.string.style_aurora
                    VisualStyle.EMBER -> R.string.style_ember
                })
                Column(Modifier.weight(1f).pressMotion(interaction).clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = .12f * amount.value))
                    .selectable(selected = selected, role = Role.RadioButton, interactionSource = interaction, indication = null, onClick = { viewModel.updateVisualStyle(style) })
                    .padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!settings.performanceMode) Canvas(Modifier.size(36.dp).graphicsLayer { scaleX = .9f + .1f * amount.value; scaleY = scaleX }) {
                        drawCircle(Brush.radialGradient(listOf(Color.White, color, Color(0xFF17171B)),
                            center = center.copy(x = center.x * .6f, y = center.y * .6f), radius = size.width * .75f))
                    }
                    Text(label, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        SwitchRow(stringResource(R.string.settings_performance), settings.performanceMode, backdrop, viewModel::updatePerformanceMode)
        Text(stringResource(R.string.settings_performance_desc), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SettingsRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val motion = LocalAppMotion.current
    val arrow = animateFloatAsState(if (pressed) 1f else 0f, if (motion) tween(180) else snap(), label = "row arrow")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .pressMotion(source)
            .clip(RoundedCornerShape(8.dp))
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(vertical = 10.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.padding(horizontal = 6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            modifier = Modifier.graphicsLayer { translationX = arrow.value * 4.dp.toPx() },
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun SettingsSubScreen(
    backdrop: Backdrop,
    title: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(top = 8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.panelEntrance().padding(horizontal = 8.dp),
        ) {
            MotionIconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.wizard_back),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 8.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            content()
        }
    }
}

@Composable
fun SwitchRow(
    label: String,
    checked: Boolean,
    backdrop: Backdrop,
    onCheckedChange: (Boolean) -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().pressMotion(source).clip(RoundedCornerShape(8.dp))
            .toggleable(value = checked, role = Role.Switch, interactionSource = source, indication = null, onValueChange = onCheckedChange)
            .heightIn(min = 48.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        LiquidToggle(checked = checked, onCheckedChange = onCheckedChange, backdrop = backdrop, modifier = Modifier.clearAndSetSemantics { })
    }
}

@Composable
fun ApplyErrorCard(backdrop: Backdrop, message: String, onDismiss: () -> Unit) {
    GlassCard(backdrop = backdrop, modifier = Modifier.fillMaxWidth(), entranceDelay = 120) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f),
            )
            MotionIconButton(onClick = onDismiss) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.dismiss),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun LanguageCard(backdrop: Backdrop) {
    val context = LocalContext.current
    val activity = context as? Activity
    val current = remember { AppLanguage.current(context) }
    GlassCard(backdrop = backdrop, modifier = Modifier.fillMaxWidth(), entranceDelay = 120) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.Language,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.padding(horizontal = 6.dp))
            Text(
                text = stringResource(R.string.settings_language),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.padding(vertical = 4.dp))
        AppLanguage.Choice.entries.forEach { choice ->
            val label = when (choice) {
                AppLanguage.Choice.SYSTEM -> stringResource(R.string.language_system)

                AppLanguage.Choice.ENGLISH -> "English"
                AppLanguage.Choice.SPANISH -> "Español"
            }
            SelectionRow(label, choice == current, enabled = activity != null) {
                if (choice != current) activity?.let { AppLanguage.apply(it, choice) }
            }
        }
    }
}
