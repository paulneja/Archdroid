package io.github.paulneja.archdroid.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import io.github.paulneja.archdroid.R
import io.github.paulneja.archdroid.core.chroot.ChrootStatus
import io.github.paulneja.archdroid.ui.components.GlassBottomBar
import io.github.paulneja.archdroid.ui.components.GlassNavItem
import io.github.paulneja.archdroid.ui.home.HomeScreen
import io.github.paulneja.archdroid.ui.install.InstallWizardScreen
import io.github.paulneja.archdroid.ui.onboarding.RootCheckScreen
import io.github.paulneja.archdroid.ui.settings.AboutScreen
import io.github.paulneja.archdroid.ui.settings.BackupScreen
import io.github.paulneja.archdroid.ui.settings.BehaviorScreen
import io.github.paulneja.archdroid.ui.settings.EnvScreen
import io.github.paulneja.archdroid.ui.settings.MountsScreen
import io.github.paulneja.archdroid.ui.settings.SettingsScreen
import io.github.paulneja.archdroid.ui.settings.UserScreen
import io.github.paulneja.archdroid.ui.terminal.TerminalScreen
import io.github.paulneja.archdroid.ui.theme.ArchDroidTheme
import io.github.paulneja.archdroid.ui.theme.BgDeepBottom
import io.github.paulneja.archdroid.ui.theme.BgDeepTop

@Composable
fun AppRoot(
    requestedRoute: String? = null,
    onRouteConsumed: () -> Unit = {},
    viewModel: AppViewModel = viewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    ArchDroidTheme(dynamicColor = settings.dynamicColor) {
        val background = Brush.verticalGradient(listOf(BgDeepTop, BgDeepBottom))
        // Keep glass outside its sampled layer to avoid a RenderNode cycle.
        val backgroundBackdrop = rememberLayerBackdrop { drawRect(background) }
        val rootState by viewModel.rootState.collectAsStateWithLifecycle()

        Box(Modifier.fillMaxSize().background(background)) {
            Box(Modifier.fillMaxSize().layerBackdrop(backgroundBackdrop))

            when (rootState) {
                AppViewModel.RootState.CHECKING ->
                    RootCheckScreen(backgroundBackdrop, checking = true, onRetry = viewModel::checkRoot)
                AppViewModel.RootState.DENIED ->
                    RootCheckScreen(backgroundBackdrop, checking = false, onRetry = viewModel::checkRoot)
                AppViewModel.RootState.GRANTED ->
                    MainScaffold(viewModel, backgroundBackdrop, requestedRoute, onRouteConsumed)
            }
        }
    }
}

@Composable
private fun MainScaffold(
    viewModel: AppViewModel,
    backgroundBackdrop: Backdrop,
    requestedRoute: String?,
    onRouteConsumed: () -> Unit,
) {
    // The bar must remain a sibling of the layer it samples.
    val contentBackdrop = rememberLayerBackdrop { drawContent() }
    val navController = rememberNavController()
    val status by viewModel.chroot.status.collectAsStateWithLifecycle()
    val startRoute = if (status == ChrootStatus.NOT_INSTALLED) Routes.INSTALL else Routes.HOME

    LaunchedEffect(requestedRoute) {
        if (Routes.acceptsExternalRequest(requestedRoute)) {
            navController.navigate(requestedRoute!!) { launchSingleTop = true }
        }
        if (requestedRoute != null) onRouteConsumed()
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val barVisible = currentRoute == Routes.HOME || currentRoute == Routes.SETTINGS

    Box(Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = startRoute,
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(contentBackdrop),
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    backdrop = backgroundBackdrop,
                    onOpenTerminal = { navController.navigate(Routes.TERMINAL) },
                    onInstall = { navController.navigate(Routes.INSTALL) },
                )
            }
            composable(Routes.TERMINAL) {
                TerminalScreen(onClose = { navController.popBackStack() })
            }
            composable(Routes.INSTALL) {
                InstallWizardScreen(
                    backdrop = backgroundBackdrop,
                    onDone = {
                        navController.navigate(Routes.HOME) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(backdrop = backgroundBackdrop, onOpenSection = { navController.navigate(it) })
            }
            composable(Routes.SETTINGS_MOUNTS) {
                MountsScreen(backdrop = backgroundBackdrop, onBack = { navController.popBackStack() })
            }
            composable(Routes.SETTINGS_ENV) {
                EnvScreen(backdrop = backgroundBackdrop, onBack = { navController.popBackStack() })
            }
            composable(Routes.SETTINGS_USER) {
                UserScreen(backdrop = backgroundBackdrop, onBack = { navController.popBackStack() })
            }
            composable(Routes.SETTINGS_BEHAVIOR) {
                BehaviorScreen(backdrop = backgroundBackdrop, onBack = { navController.popBackStack() })
            }
            composable(Routes.SETTINGS_BACKUP) {
                BackupScreen(backdrop = backgroundBackdrop, onBack = { navController.popBackStack() })
            }
            composable(Routes.SETTINGS_ABOUT) {
                AboutScreen(backdrop = backgroundBackdrop, onBack = { navController.popBackStack() })
            }
        }

        AnimatedVisibility(
            visible = barVisible,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 12.dp),
        ) {
            GlassBottomBar(
                backdrop = contentBackdrop,
                items = listOf(
                    GlassNavItem(Routes.HOME, Icons.Rounded.Home, stringResource(R.string.nav_home)),
                    GlassNavItem(Routes.TERMINAL, Icons.Rounded.Terminal, stringResource(R.string.nav_terminal)),
                    GlassNavItem(Routes.SETTINGS, Icons.Rounded.Settings, stringResource(R.string.nav_settings)),
                ),
                selectedRoute = currentRoute,
                onSelect = { route ->
                    if (route != currentRoute) {
                        navController.navigate(route) {
                            launchSingleTop = true
                            popUpTo(Routes.HOME)
                        }
                    }
                },
            )
        }
    }
}
