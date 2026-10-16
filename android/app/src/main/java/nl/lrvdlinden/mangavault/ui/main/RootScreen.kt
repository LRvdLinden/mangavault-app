package nl.lrvdlinden.mangavault.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import nl.lrvdlinden.mangavault.app.AuthPhase
import nl.lrvdlinden.mangavault.app.LocalApp
import nl.lrvdlinden.mangavault.app.MainTab
import nl.lrvdlinden.mangavault.app.Route
import nl.lrvdlinden.mangavault.ui.auth.AuthFlowScreen
import nl.lrvdlinden.mangavault.ui.catalog.CatalogScreen
import nl.lrvdlinden.mangavault.ui.catalog.MangaDetailScreen
import nl.lrvdlinden.mangavault.ui.catalog.VolumeDetailScreen
import nl.lrvdlinden.mangavault.ui.collection.CollectionScreen
import nl.lrvdlinden.mangavault.ui.collection.WishlistScreen
import nl.lrvdlinden.mangavault.ui.components.ConfettiOverlay
import nl.lrvdlinden.mangavault.ui.components.LogoMark
import nl.lrvdlinden.mangavault.ui.components.ToastView
import nl.lrvdlinden.mangavault.ui.components.Wordmark
import nl.lrvdlinden.mangavault.ui.library.*
import nl.lrvdlinden.mangavault.ui.profile.ProfileScreen
import nl.lrvdlinden.mangavault.ui.rewards.*
import nl.lrvdlinden.mangavault.ui.social.*
import nl.lrvdlinden.mangavault.ui.stats.*
import nl.lrvdlinden.mangavault.ui.theme.Vault

@Composable
fun RootScreen() {
    val app = LocalApp.current
    Box(Modifier.fillMaxSize().background(Vault.background)) {
        Crossfade(targetState = app.phase, label = "phase") { phase ->
            when (phase) {
                AuthPhase.LAUNCHING -> SplashScreen()
                AuthPhase.SIGNED_OUT -> AuthFlowScreen()
                AuthPhase.SIGNED_IN -> MainScaffold()
            }
        }
        AnimatedVisibility(
            visible = app.toast != null,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 6.dp),
        ) {
            app.toast?.let { t -> ToastView(t, Modifier.clickable { app.toast = null }) }
        }
        ConfettiOverlay(app.confettiTrigger)
    }
}

@Composable
private fun SplashScreen() {
    val t = rememberInfiniteTransition(label = "pulse")
    val s by t.animateFloat(0.95f, 1.05f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "s")
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.scale(s)) { LogoMark(72.dp) }
        Spacer(Modifier.height(16.dp))
        Wordmark(28.sp)
    }
}

private fun tabIcon(t: MainTab): ImageVector = when (t) {
    MainTab.HOME -> Icons.Filled.Home
    MainTab.CATALOG -> Icons.Filled.MenuBook
    MainTab.COLLECTION -> Icons.Filled.LibraryBooks
    MainTab.EVENTS -> Icons.Filled.CalendarMonth
    MainTab.MORE -> Icons.Filled.MoreHoriz
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScaffold() {
    val app = LocalApp.current
    val stack = app.stacks.getValue(app.tab)
    val top: Route? = stack.lastOrNull()

    BackHandler(enabled = stack.isNotEmpty() || app.tab != MainTab.HOME) {
        if (!app.back()) app.selectTab(MainTab.HOME)
    }

    // Light polling replaces the web app's realtime subscriptions.
    LaunchedEffect(Unit) {
        while (true) {
            app.refreshSocialCounts()
            delay(30_000)
        }
    }

    Scaffold(
        containerColor = Vault.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Vault.background, titleContentColor = Vault.foreground,
                    navigationIconContentColor = Vault.foreground, actionIconContentColor = Vault.foreground),
                navigationIcon = {
                    if (top != null) IconButton(onClick = { app.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                title = {
                    if (top == null && (app.tab == MainTab.HOME || app.tab == MainTab.CATALOG)) Wordmark(20.sp)
                    else Text(top?.title ?: app.tab.label, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                actions = { NotificationBell() },
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Vault.background, tonalElevation = 0.dp) {
                for (t in MainTab.entries) {
                    val badge = if (t == MainTab.MORE) app.unreadMessages + app.pendingFriendRequests else 0
                    NavigationBarItem(
                        selected = app.tab == t,
                        onClick = { app.selectTab(t) },
                        icon = {
                            BadgedBox(badge = { if (badge > 0) Badge { Text("$badge") } }) { Icon(tabIcon(t), t.label) }
                        },
                        label = { Text(t.label, fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Vault.primary, selectedTextColor = Vault.primary,
                            unselectedIconColor = Vault.muted, unselectedTextColor = Vault.muted,
                            indicatorColor = Vault.primary.copy(alpha = 0.12f),
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            // key() gives every stack entry its own state, so going back restores nothing stale.
            key(app.tab, stack.size) {
                if (top != null) RouteScreen(top) else TabRoot(app.tab)
            }
        }
    }
}

@Composable
private fun TabRoot(tab: MainTab) {
    when (tab) {
        MainTab.HOME -> DashboardScreen()
        MainTab.CATALOG -> CatalogScreen(discover = false)
        MainTab.COLLECTION -> CollectionScreen()
        MainTab.EVENTS -> EventsScreen()
        MainTab.MORE -> MoreScreen()
    }
}

@Composable
fun RouteScreen(route: Route) {
    when (route) {
        is Route.Manga -> MangaDetailScreen(route.id)
        is Route.Volume -> VolumeDetailScreen(route.mangaId, route.volume)
        Route.Discover -> CatalogScreen(discover = true)
        Route.Statistics -> StatisticsScreen()
        Route.Achievements -> AchievementsScreen()
        Route.Quests -> QuestsScreen()
        Route.Wrapped -> WrappedScreen()
        Route.Profile -> ProfileScreen(profileId = null)
        is Route.PublicProfile -> ProfileScreen(profileId = route.profileId)
        Route.Wishlist -> WishlistScreen()
        Route.Calendar -> CalendarScreen()
        Route.Recommendations -> RecommendationsScreen()
        Route.Journal -> JournalScreen()
        Route.RandomManga -> RandomMangaScreen()
        Route.Compare -> CompareScreen()
        Route.Series -> SeriesTrackerScreen()
        Route.TierLists -> TierListsScreen()
        Route.Friends -> FriendsScreen()
        Route.Messages -> MessagesScreen()
        is Route.Chat -> ChatScreen(route.userId)
        is Route.UserCollection -> UserCollectionScreen(route.profileId)
        Route.Rewards -> RewardsScreen()
        Route.Capsules -> CapsulesScreen()
        Route.Cards -> CardsScreen()
        Route.Events -> EventsScreen()
        is Route.Event -> EventDetailScreen(route.id)
        Route.ChristmasEvent -> ChristmasEventScreen()
        Route.BorutoEvent -> BorutoEventScreen()
        Route.Roulette -> RouletteScreen()
        is Route.Room -> MangaRoomScreen(route.userId)
    }
}
