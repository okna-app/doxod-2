package kg.depter.app.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kg.depter.app.ui.entry.EntryScreen
import kg.depter.app.ui.home.MainScreen
import kg.depter.app.ui.ledger.LedgerScreen
import kg.depter.app.ui.lock.PinSetupScreen
import kg.depter.app.ui.onboarding.OnboardingScreen
import kg.depter.app.ui.party.PartyEditScreen
import kg.depter.app.ui.pro.ProScreen

object Routes {
    const val ONBOARDING = "onboarding"
    const val MAIN = "main"
    const val PARTY = "party/{id}"
    const val PARTY_EDIT = "party_edit?id={id}&type={type}"
    const val ENTRY = "entry?party={party}&entry={entry}&kind={kind}"
    const val PRO = "pro"
    const val PIN_SETUP = "pin_setup"

    fun party(id: Long) = "party/$id"
    fun partyEdit(id: Long = -1L, type: Int = 0) = "party_edit?id=$id&type=$type"
    fun entry(party: Long, entry: Long = -1L, kind: Int = 0) = "entry?party=$party&entry=$entry&kind=$kind"
}

private const val ANIM_MS = 260

@Composable
fun AppNav(startOnboarding: Boolean, openParty: Long?, onPartyOpened: () -> Unit) {
    val nav: NavHostController = rememberNavController()
    val start = remember { if (startOnboarding) Routes.ONBOARDING else Routes.MAIN }

    LaunchedEffect(openParty) {
        if (openParty != null && !startOnboarding) {
            nav.navigate(Routes.party(openParty)) { launchSingleTop = true }
            onPartyOpened()
        }
    }

    NavHost(
        navController = nav,
        startDestination = start,
        enterTransition = {
            slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(ANIM_MS)) { it / 6 } + fadeIn(tween(ANIM_MS))
        },
        exitTransition = { fadeOut(tween(ANIM_MS / 2)) },
        popEnterTransition = { fadeIn(tween(ANIM_MS)) },
        popExitTransition = {
            slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(ANIM_MS)) { it / 6 } + fadeOut(tween(ANIM_MS))
        },
    ) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(onDone = {
                nav.navigate(Routes.MAIN) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
            })
        }
        composable(Routes.MAIN) {
            MainScreen(nav)
        }
        composable(
            Routes.PARTY,
            arguments = listOf(navArgument("id") { type = NavType.LongType }),
        ) { entry ->
            LedgerScreen(partyId = entry.arguments?.getLong("id") ?: -1L, nav = nav)
        }
        composable(
            Routes.PARTY_EDIT,
            arguments = listOf(
                navArgument("id") { type = NavType.LongType; defaultValue = -1L },
                navArgument("type") { type = NavType.IntType; defaultValue = 0 },
            ),
        ) { entry ->
            PartyEditScreen(
                partyId = entry.arguments?.getLong("id") ?: -1L,
                initialType = entry.arguments?.getInt("type") ?: 0,
                nav = nav,
            )
        }
        composable(
            Routes.ENTRY,
            arguments = listOf(
                navArgument("party") { type = NavType.LongType; defaultValue = -1L },
                navArgument("entry") { type = NavType.LongType; defaultValue = -1L },
                navArgument("kind") { type = NavType.IntType; defaultValue = 0 },
            ),
        ) { entry ->
            EntryScreen(
                partyId = entry.arguments?.getLong("party") ?: -1L,
                entryId = entry.arguments?.getLong("entry") ?: -1L,
                initialKind = entry.arguments?.getInt("kind") ?: 0,
                onBack = { nav.popBackStack() },
            )
        }
        composable(Routes.PRO) {
            ProScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.PIN_SETUP) {
            PinSetupScreen(onDone = { nav.popBackStack() })
        }
    }
}
