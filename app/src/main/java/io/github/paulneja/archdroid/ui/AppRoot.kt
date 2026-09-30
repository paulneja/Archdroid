package io.github.paulneja.archdroid.ui

import androidx.compose.runtime.getValue
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
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
import io.github.paulneja.archdroid.ui.home.HomeScreen
import io.github.paulneja.archdroid.ui.install.InstallWizardScreen
import io.github.paulneja.archdroid.ui.onboarding.RootCheckScreen
import io.github.paulneja.archdroid.ui.settings.AboutScreen
import io.github.paulneja.archdroid.ui.settings.BackupScreen
import io.github.paulneja.archdroid.ui.settings.BehaviorScreen
import io.github.paulneja.archdroid.ui.settings.EnvScreen
import io.github.paulneja.archdroid.ui.settings.InitScreen
import io.github.paulneja.archdroid.ui.settings.MountsScreen
import io.github.paulneja.archdroid.ui.settings.ProtectionScreen
import io.github.paulneja.archdroid.ui.settings.SettingsScreen
import io.github.paulneja.archdroid.ui.settings.UserScreen
import io.github.paulneja.archdroid.ui.terminal.TerminalScreen
import io.github.paulneja.archdroid.ui.theme.ArchDroidTheme
import io.github.paulneja.archdroid.ui.theme.BgDeepBottom
import io.github.paulneja.archdroid.ui.theme.BgDeepTop
import io.github.paulneja.archdroid.ui.theme.LocalAppMotion
import io.github.paulneja.archdroid.ui.theme.LocalPageVisible
import io.github.paulneja.archdroid.ui.theme.LocalPerformanceMode
import kotlin.math.absoluteValue
import kotlinx.coroutines.launch

@Composable
fun AppRoot(
    requestedRoute: String? = null,
    onRouteConsumed: () -> Unit = {},
    viewModel: AppViewModel = viewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    ArchDroidTheme(dynamicColor = settings.dynamicColor, visualStyle = settings.visualStyle, performanceMode = settings.performanceMode) {
        val background = Brush.verticalGradient(listOf(BgDeepTop, BgDeepBottom))

        val backgroundBackdrop = rememberLayerBackdrop { drawRect(background) }
        val rootState by viewModel.rootState.collectAsStateWithLifecycle()

        Box(Modifier.fillMaxSize().background(background)) {
            if (!settings.performanceMode) Box(Modifier.fillMaxSize().layerBackdrop(backgroundBackdrop))

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

enum class MainPage { HOME, TERMINAL, DESKTOP, SETTINGS }

@Composable
private fun MainScaffold(
    viewModel: AppViewModel,
    backgroundBackdrop: Backdrop,
    requestedRoute: String?,
    onRouteConsumed: () -> Unit,
) {

    val contentBackdrop = rememberLayerBackdrop { drawContent() }
    val navController = rememberNavController()
    val status by viewModel.chroot.status.collectAsStateWithLifecycle()
    val startRoute = if (status == ChrootStatus.NOT_INSTALLED) Routes.INSTALL else Routes.MAIN
    val pagerState = rememberPagerState { MainPage.entries.size }
    val motion = LocalAppMotion.current
    val performance = LocalPerformanceMode.current
    val scope = rememberCoroutineScope()
    val showPage: (MainPage) -> Unit = { page ->
        if (navController.currentDestination?.route != Routes.MAIN) {
            navController.navigate(Routes.MAIN) {
                popUpTo(Routes.MAIN) { inclusive = false }
                launchSingleTop = true
            }
        }
        scope.launch { if (motion) pagerState.animateScrollToPage(page.ordinal, animationSpec = tween(380)) else pagerState.scrollToPage(page.ordinal) }
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

    val barVisible = currentRoute == Routes.MAIN &&
        pagerState.targetPage != MainPage.TERMINAL.ordinal

    Box(Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = startRoute,
            modifier = Modifier
                .fillMaxSize()
                .then(if (performance) Modifier else Modifier.layerBackdrop(contentBackdrop)),
            enterTransition = { if (motion) fadeIn(tween(220)) + slideInHorizontally(tween(300)) { it / 10 } else EnterTransition.None },
            exitTransition = { if (motion) fadeOut(tween(160)) + slideOutHorizontally(tween(240)) { -it / 14 } else ExitTransition.None },
            popEnterTransition = { if (motion) fadeIn(tween(220)) + slideInHorizontally(tween(300)) { -it / 10 } else EnterTransition.None },
            popExitTransition = { if (motion) fadeOut(tween(160)) + slideOutHorizontally(tween(240)) { it / 14 } else ExitTransition.None },
        ) {
            composable(Routes.MAIN) {

                BackHandler(enabled = currentPage != MainPage.HOME) { showPage(MainPage.HOME) }
                HorizontalPager(
                    state = pagerState,
                    userScrollEnabled = false,
                    modifier = Modifier.fillMaxSize(),
                ) { index ->

                    Box(Modifier.fillMaxSize().then(if (motion && index != MainPage.TERMINAL.ordinal) Modifier.graphicsLayer {
                        if (motion) {
                            val distance = ((pagerState.currentPage - index) + pagerState.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
                            alpha = 1f - distance * .35f
                            scaleX = 1f - distance * .035f
                            scaleY = scaleX
                        }
                    } else Modifier)) {
                        CompositionLocalProvider(LocalPageVisible provides (pagerState.settledPage == index && currentRoute == Routes.MAIN)) {
                            when (MainPage.entries[index]) {
                                MainPage.HOME -> HomeScreen(
                                    onOpenTerminal = { showPage(MainPage.TERMINAL) },
                                    onOpenDesktop = { showPage(MainPage.DESKTOP) },
                                    onOpenSettings = { showPage(MainPage.SETTINGS) },
                                    onInstall = { navController.navigate(Routes.INSTALL) },
                                    visible = pagerState.settledPage == MainPage.HOME.ordinal,
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
            enter = if (motion) fadeIn(tween(200)) + slideInVertically(tween(280)) { it / 2 } else EnterTransition.None,
            exit = if (motion) fadeOut(tween(160)) + slideOutVertically(tween(220)) { it / 2 } else ExitTransition.None,
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
                selectedIndex = pagerState.targetPage,
                onSelected = { showPage(MainPage.entries[it]) },
                backdrop = contentBackdrop,
            )
        }
    }
}
