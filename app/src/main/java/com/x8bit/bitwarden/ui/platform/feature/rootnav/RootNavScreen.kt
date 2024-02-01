package com.x8bit.bitwarden.ui.platform.feature.rootnav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navOptions
import com.x8bit.bitwarden.ui.auth.feature.auth.AUTH_GRAPH_ROUTE
import com.x8bit.bitwarden.ui.auth.feature.auth.authGraph
import com.x8bit.bitwarden.ui.auth.feature.auth.navigateToAuthGraph
import com.x8bit.bitwarden.ui.auth.feature.resetpassword.RESET_PASSWORD_ROUTE
import com.x8bit.bitwarden.ui.auth.feature.resetpassword.navigateToResetPasswordGraph
import com.x8bit.bitwarden.ui.auth.feature.resetpassword.resetPasswordDestination
import com.x8bit.bitwarden.ui.auth.feature.vaultunlock.VAULT_UNLOCK_ROUTE
import com.x8bit.bitwarden.ui.auth.feature.vaultunlock.navigateToVaultUnlock
import com.x8bit.bitwarden.ui.auth.feature.vaultunlock.vaultUnlockDestination
import com.x8bit.bitwarden.ui.platform.feature.rootnav.util.toVaultItemListingType
import com.x8bit.bitwarden.ui.platform.feature.splash.SPLASH_ROUTE
import com.x8bit.bitwarden.ui.platform.feature.splash.navigateToSplash
import com.x8bit.bitwarden.ui.platform.feature.splash.splashDestination
import com.x8bit.bitwarden.ui.platform.feature.vaultunlocked.VAULT_UNLOCKED_GRAPH_ROUTE
import com.x8bit.bitwarden.ui.platform.feature.vaultunlocked.navigateToVaultUnlockedGraph
import com.x8bit.bitwarden.ui.platform.feature.vaultunlocked.vaultUnlockedGraph
import com.x8bit.bitwarden.ui.platform.theme.NonNullEnterTransitionProvider
import com.x8bit.bitwarden.ui.platform.theme.NonNullExitTransitionProvider
import com.x8bit.bitwarden.ui.platform.theme.RootTransitionProviders
import com.x8bit.bitwarden.ui.tools.feature.send.addsend.model.AddSendType
import com.x8bit.bitwarden.ui.tools.feature.send.addsend.navigateToAddSend
import com.x8bit.bitwarden.ui.vault.feature.addedit.navigateToVaultAddEdit
import com.x8bit.bitwarden.ui.vault.feature.itemlisting.navigateToVaultItemListingAsRoot
import com.x8bit.bitwarden.ui.vault.model.VaultAddEditType
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.util.concurrent.atomic.AtomicReference

/**
 * Controls root level [NavHost] for the app.
 */
@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
fun RootNavScreen(
    viewModel: RootNavViewModel = hiltViewModel(),
    navController: NavHostController = rememberNavController(),
    onSplashScreenRemoved: () -> Unit = {},
) {
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    val previousStateReference = remember { AtomicReference(state) }

    val isNotSplashScreen = state != RootNavState.Splash
    LaunchedEffect(isNotSplashScreen) {
        if (isNotSplashScreen) onSplashScreenRemoved()
    }

    LaunchedEffect(Unit) {
        navController
            .currentBackStackEntryFlow
            .onEach {
                viewModel.trySendAction(RootNavAction.BackStackUpdate)
            }
            .launchIn(this)
    }

    NavHost(
        navController = navController,
        startDestination = SPLASH_ROUTE,
        enterTransition = { this.targetState.destination.route.toEnterTransition()(this) },
        exitTransition = { this.targetState.destination.route.toExitTransition()(this) },
        popEnterTransition = { this.targetState.destination.route.toEnterTransition()(this) },
        popExitTransition = { this.targetState.destination.route.toExitTransition()(this) },
    ) {
        splashDestination()
        authGraph(navController)
        resetPasswordDestination()
        vaultUnlockDestination()
        vaultUnlockedGraph(navController)
    }

    val targetRoute = when (state) {
        RootNavState.Auth -> AUTH_GRAPH_ROUTE
        RootNavState.ResetPassword -> RESET_PASSWORD_ROUTE
        RootNavState.Splash -> SPLASH_ROUTE
        RootNavState.VaultLocked -> VAULT_UNLOCK_ROUTE
        is RootNavState.VaultUnlocked,
        is RootNavState.VaultUnlockedForAutofillSave,
        is RootNavState.VaultUnlockedForAutofillSelection,
        is RootNavState.VaultUnlockedForNewSend,
        -> VAULT_UNLOCKED_GRAPH_ROUTE
    }
    val currentRoute = navController.currentDestination?.rootLevelRoute()

    // Don't navigate if we are already at the correct root. This notably happens during process
    // death. In this case, the NavHost already restores state, so we don't have to navigate.
    // However, if the route is correct but the underlying state is different, we should still
    // proceed in order to get a fresh version of that route.
    if (currentRoute == targetRoute && previousStateReference.get() == state) {
        previousStateReference.set(state)
        return
    }
    previousStateReference.set(state)

    // When state changes, navigate to different root navigation state
    val rootNavOptions = navOptions {
        // When changing root navigation state, pop everything else off the back stack:
        popUpTo(navController.graph.id) {
            inclusive = false
            saveState = false
        }
        launchSingleTop = true
        restoreState = false
    }

    when (val currentState = state) {
        RootNavState.Auth -> navController.navigateToAuthGraph(rootNavOptions)
        RootNavState.ResetPassword -> navController.navigateToResetPasswordGraph(rootNavOptions)
        RootNavState.Splash -> navController.navigateToSplash(rootNavOptions)
        RootNavState.VaultLocked -> navController.navigateToVaultUnlock(rootNavOptions)
        is RootNavState.VaultUnlocked -> navController.navigateToVaultUnlockedGraph(rootNavOptions)
        RootNavState.VaultUnlockedForNewSend -> {
            navController.navigateToVaultUnlock(rootNavOptions)
            navController.navigateToAddSend(
                sendAddType = AddSendType.AddItem,
                navOptions = rootNavOptions,
            )
        }

        is RootNavState.VaultUnlockedForAutofillSave -> {
            navController.navigateToVaultUnlockedGraph(rootNavOptions)
            navController.navigateToVaultAddEdit(
                vaultAddEditType = VaultAddEditType.AddItem,
                navOptions = rootNavOptions,
            )
        }

        is RootNavState.VaultUnlockedForAutofillSelection -> {
            navController.navigateToVaultUnlockedGraph(rootNavOptions)
            navController.navigateToVaultItemListingAsRoot(
                vaultItemListingType = currentState.type.toVaultItemListingType(),
                navOptions = rootNavOptions,
            )
        }
    }
}

/**
 * Helper method that returns the highest level route for the given [NavDestination].
 *
 * As noted above, this can be removed after upgrading to latest compose navigation, since
 * the nav args can prevent us from having to do this check.
 */
@Suppress("ReturnCount")
private fun NavDestination?.rootLevelRoute(): String? {
    if (this == null) {
        return null
    }
    if (parent?.route == null) {
        return route
    }
    return parent.rootLevelRoute()
}

/**
 * Define the enter transition for each route.
 */
private fun String?.toEnterTransition(): NonNullEnterTransitionProvider = when (this) {
    RESET_PASSWORD_ROUTE -> RootTransitionProviders.Enter.slideUp
    else -> RootTransitionProviders.Enter.fadeIn
}

/**
 * Define the exit transition for each route.
 */
private fun String?.toExitTransition(): NonNullExitTransitionProvider = when (this) {
    RESET_PASSWORD_ROUTE -> RootTransitionProviders.Exit.slideDown
    else -> RootTransitionProviders.Exit.fadeOut
}