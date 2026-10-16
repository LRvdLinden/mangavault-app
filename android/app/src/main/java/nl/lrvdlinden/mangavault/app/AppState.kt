package nl.lrvdlinden.mangavault.app

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import nl.lrvdlinden.mangavault.core.ApiException
import nl.lrvdlinden.mangavault.core.Base44
import nl.lrvdlinden.mangavault.core.Config
import nl.lrvdlinden.mangavault.core.ProfileStore
import nl.lrvdlinden.mangavault.core.Social
import nl.lrvdlinden.mangavault.model.AppUser
import nl.lrvdlinden.mangavault.model.VaultNotification
import kotlin.coroutines.cancellation.CancellationException

/** Every screen that can be pushed onto a tab's stack (same set as the iOS app). */
sealed interface Route {
    val title: String

    data class Manga(val id: String) : Route { override val title get() = "Manga" }
    data class Volume(val mangaId: String, val volume: Int) : Route { override val title get() = "Vol. $volume" }
    data object Discover : Route { override val title get() = "Discover" }
    data object Statistics : Route { override val title get() = "Statistics" }
    data object Achievements : Route { override val title get() = "Achievements" }
    data object Quests : Route { override val title get() = "Quests" }
    data object Wrapped : Route { override val title get() = "Manga Wrapped" }
    data object Profile : Route { override val title get() = "Profile" }
    data class PublicProfile(val profileId: String) : Route { override val title get() = "Profile" }
    data object Wishlist : Route { override val title get() = "Wishlist" }
    data object Calendar : Route { override val title get() = "Reading Calendar" }
    data object Recommendations : Route { override val title get() = "Recommendations" }
    data object Journal : Route { override val title get() = "Manga Journal" }
    data object RandomManga : Route { override val title get() = "Random Manga" }
    data object Compare : Route { override val title get() = "Manga Versus" }
    data object Series : Route { override val title get() = "Series Tracker" }
    data object TierLists : Route { override val title get() = "Tier Lists" }
    data object Friends : Route { override val title get() = "Friends" }
    data object Messages : Route { override val title get() = "Messages" }
    /** Chat with another reader; userId = auth user id. */
    data class Chat(val userId: String) : Route { override val title get() = "Chat" }
    data class UserCollection(val profileId: String) : Route { override val title get() = "Collection" }
    data object Rewards : Route { override val title get() = "Rewards" }
    data object Capsules : Route { override val title get() = "Vault Capsules" }
    data object Cards : Route { override val title get() = "Trading Cards" }
    data object Events : Route { override val title get() = "Events" }
    data class Event(val id: String) : Route { override val title get() = "Event" }
    data object ChristmasEvent : Route { override val title get() = "Christmas Event" }
    data object BorutoEvent : Route { override val title get() = "Boruto Vol. 7" }
    data object Roulette : Route { override val title get() = "Manga Roulette" }
    /** userId null = your own room. */
    data class Room(val userId: String?) : Route { override val title get() = "Manga Room" }
}

/** Same tabs as the web app's mobile bottom bar. */
enum class MainTab(val label: String) { HOME("Home"), CATALOG("Catalog"), COLLECTION("Collection"), EVENTS("Events"), MORE("More") }

enum class AuthPhase { LAUNCHING, SIGNED_OUT, SIGNED_IN }

data class Toast(val title: String, val message: String? = null, val isError: Boolean = false, val stamp: Long = System.nanoTime())

/** App-wide state, provided to every screen through [LocalApp]. All members must be used from the main thread. */
class AppState(private val scope: CoroutineScope) {
    var phase by mutableStateOf(AuthPhase.LAUNCHING)
    var user by mutableStateOf<AppUser?>(null)
    var toast by mutableStateOf<Toast?>(null)
    var confettiTrigger by mutableIntStateOf(0)
    /** Bumped whenever collection / wishlist data changes so other screens reload (use as LaunchedEffect key). */
    var dataVersion by mutableIntStateOf(0)

    var tab by mutableStateOf(MainTab.HOME)
    val stacks: Map<MainTab, SnapshotStateList<Route>> = MainTab.entries.associateWith { mutableStateListOf<Route>() }

    // Social counters (refreshed by polling every 30 s).
    var unreadMessages by mutableIntStateOf(0)
    var pendingFriendRequests by mutableIntStateOf(0)
    var unreadNotifications by mutableIntStateOf(0)
    var notifications by mutableStateOf<List<VaultNotification>>(emptyList())

    private var toastJob: Job? = null

    init {
        Base44.onUnauthorized = { scope.launch { signOut() } }
    }

    val displayName: String
        get() {
            user?.full_name?.takeIf { it.isNotEmpty() }?.let { return it }
            user?.email?.let { return it.substringBefore("@") }
            return "Reader"
        }

    // MARK: Navigation

    /** Push a screen onto the current tab. */
    fun open(route: Route) {
        if (route == Route.Events) { tab = MainTab.EVENTS; stacks.getValue(MainTab.EVENTS).clear(); return }
        stacks.getValue(tab).add(route)
    }

    /** Open a screen from anywhere (e.g. a notification): Events → Events tab, others on a fresh More stack. */
    fun openFromAnywhere(route: Route) {
        if (route == Route.Events) { open(route); return }
        tab = MainTab.MORE
        val s = stacks.getValue(MainTab.MORE)
        s.clear(); s.add(route)
    }

    /** Pops the current tab's stack; returns false when already at the root. */
    fun back(): Boolean {
        val s = stacks.getValue(tab)
        if (s.isEmpty()) return false
        s.removeAt(s.lastIndex)
        return true
    }

    /** Switch tab; tapping the active tab again pops it to its root. */
    fun selectTab(t: MainTab) {
        if (t == tab) stacks.getValue(t).clear()
        tab = t
    }

    // MARK: Session

    fun bootstrap() {
        scope.launch {
            if (Base44.token == null) { phase = AuthPhase.SIGNED_OUT; return@launch }
            try {
                user = Base44.me()
                phase = AuthPhase.SIGNED_IN
            } catch (e: ApiException) {
                if (e.status == 401 || e.status == 403) signOut() else phase = AuthPhase.SIGNED_IN
            } catch (e: Exception) {
                // Offline: keep the session, screens show their own errors.
                phase = AuthPhase.SIGNED_IN
            }
        }
    }

    suspend fun didSignIn() {
        ProfileStore.invalidate()
        user = runCatching { Base44.me() }.getOrNull()
        MainTab.entries.forEach { stacks.getValue(it).clear() }
        tab = MainTab.HOME
        phase = AuthPhase.SIGNED_IN
    }

    fun signOut() {
        Base44.logout()
        user = null
        unreadMessages = 0; pendingFriendRequests = 0; unreadNotifications = 0; notifications = emptyList()
        ProfileStore.invalidate()
        phase = AuthPhase.SIGNED_OUT
    }

    // MARK: Feedback

    fun show(title: String, message: String? = null, error: Boolean = false) {
        toast = Toast(title, message, error)
        toastJob?.cancel()
        toastJob = scope.launch {
            delay(2600)
            toast = null
        }
    }

    /** Shows an error toast; ignores coroutine cancellation. */
    fun fail(title: String, error: Throwable) {
        if (error is CancellationException) return
        show(title, error.message ?: "Something went wrong", error = true)
    }

    fun celebrate() { confettiTrigger++ }

    fun dataChanged() { dataVersion++ }

    suspend fun refreshSocialCounts() {
        if (phase != AuthPhase.SIGNED_IN) return
        runCatching { Social.notifications() }.getOrNull()?.let { (items, unread) -> notifications = items; unreadNotifications = unread }
        runCatching { Social.conversations() }.getOrNull()?.let { c -> unreadMessages = c.sumOf { it.unread } }
        runCatching { Social.overview() }.getOrNull()?.let { pendingFriendRequests = it.incoming.size }
    }

    /** Deep links: mangavault://u/<profileId> and the Google sign-in callback mangavault://auth?access_token=… */
    fun handle(uri: Uri) {
        if (uri.scheme != Config.URL_SCHEME) return
        when (uri.host) {
            "auth" -> {
                val token = uri.getQueryParameter("access_token")
                    ?: uri.fragment?.split("&")?.firstOrNull { it.startsWith("access_token=") }?.substringAfter("=")
                if (!token.isNullOrEmpty()) {
                    Base44.token = token
                    scope.launch { didSignIn() }
                } else show("Sign-in failed", "No access token received", error = true)
            }
            "u" -> uri.pathSegments.firstOrNull()?.let { id ->
                if (phase == AuthPhase.SIGNED_IN) openFromAnywhere(Route.PublicProfile(id))
            }
        }
    }
}

val LocalApp = staticCompositionLocalOf<AppState> { error("AppState not provided") }
