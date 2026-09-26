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
import io.github.paulneja.archdroid.ui.components.LiquidBottomTabs
import io.github.paulneja.archdroid.ui.components.LiquidTabItem
import io.github.paulneja.archdroid.ui.desktop.DesktopScreen
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import io.github.paulneja.archdroid.ui.home.HomeScreen
import io.github.paulneja.archdroid.ui.install.InstallWizardScreen
import io.github.paulneja.archdroid.ui.onboarding.RootCheckScreen
import io.github.paulneja.archdroid.ui.settings.AboutScreen
import io.github.paulneja.archdroid.ui.settings.BackupScreen
import io.github.paulneja.archdroid.ui.settings.BehaviorScreen
import io.github.paulneja.archdroid.ui.settings.EnvScreen
import io.github.paulneja.archdroid.ui.settings.MountsScreen
import io.github.paulneja.archdroid.ui.settings.ProtectionScreen
import io.github.paulneja.archdroid.ui.settings.InitScreen
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

/** Top-level pages shown in the pager, in bar order. */
enum class MainPage { HOME, TERMINAL, DESKTOP, SETTINGS }

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
    val startRoute = if (status == ChrootStatus.NOT_INSTALLED) Routes.INSTALL else Routes.MAIN
    val pagerState = rememberPagerState { MainPage.entries.size }
    val scope = rememberCoroutineScope()
    val showPage: (MainPage) -> Unit = { page ->
        if (navController.currentDestination?.route != Routes.MAIN) {
            navController.navigate(Routes.MAIN) {
                popUpTo(Routes.MAIN) { inclusive = false }
                launchSingleTop = true
            }
        }
        scope.launch { pagerState.animateScrollToPage(page.ordinal) }
    }

    LaunchedEffect(requestedRoute) {
        when {
            requestedRoute == Routes.TERMINAL -> showPage(MainPage.TERMINAL)
            Routes.acceptsExternalRequest(requestedRoute) ->
                navController.navigate(requestedRoute!!) { launchSingleTop = true }
        }
        if (requestedRoute != null) onRouteConsumed()
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val currentPage = MainPage.entries[pagerState.currentPage]
    // Terminal and desktop own the whole screen; their keys and pointer must not sit under the bar.
    val barVisible = currentRoute == Routes.MAIN &&
        (currentPage == MainPage.HOME || currentPage == MainPage.SETTINGS)

    Box(Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = startRoute,
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(contentBackdrop),
        ) {
            composable(Routes.MAIN) {
                // Leaving a full-screen page with back returns to Home instead of closing anything.
                BackHandler(enabled = currentPage != MainPage.HOME) { showPage(MainPage.HOME) }
                HorizontalPager(
                    state = pagerState,
                    userScrollEnabled = false,
                    modifier = Modifier.fillMaxSize(),
                ) { index ->
                    when (MainPage.entries[index]) {
                        MainPage.HOME -> HomeScreen(
                            backdrop = backgroundBackdrop,
                            onOpenTerminal = { showPage(MainPage.TERMINAL) },
                            onInstall = { navController.navigate(Routes.INSTALL) },
                        )
                        MainPage.TERMINAL -> TerminalScreen(
                            visible = pagerState.settledPage == MainPage.TERMINAL.ordinal,
                            onClose = { showPage(MainPage.HOME) },
                        )
                        MainPage.DESKTOP -> DesktopScreen(
                            backdrop = backgroundBackdrop,
                            onClose = { showPage(MainPage.HOME) },
                        )
                        MainPage.SETTINGS -> SettingsScreen(
                            backdrop = backgroundBackdrop,
                            onOpenSection = { navController.navigate(it) },
                        )
                    }
                }
            }
            composable(Routes.INSTALL) {
                InstallWizardScreen(
                    backdrop = backgroundBackdrop,
                    onDone = {
                        navController.navigate(Routes.MAIN) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                )
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
            composable(Routes.SETTINGS_INIT) {
                InitScreen(backdrop = backgroundBackdrop, onBack = { navController.popBackStack() })
            }
            composable(Routes.SETTINGS_PROTECTION) {
                ProtectionScreen(backdrop = backgroundBackdrop, onBack = { navController.popBackStack() })
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
                .padding(start = 24.dp, end = 24.dp, bottom = 12.dp),
        ) {
            LiquidBottomTabs(
                items = listOf(
                    LiquidTabItem(Icons.Rounded.Home, stringResource(R.string.nav_home)),
                    LiquidTabItem(Icons.Rounded.Terminal, stringResource(R.string.nav_terminal)),
                    LiquidTabItem(Icons.Rounded.DesktopWindows, stringResource(R.string.nav_desktop)),
                    LiquidTabItem(Icons.Rounded.Settings, stringResource(R.string.nav_settings)),
                ),
                selectedIndex = pagerState.currentPage,
                onSelected = { showPage(MainPage.entries[it]) },
                backdrop = contentBackdrop,
            )
        }
    }
}
