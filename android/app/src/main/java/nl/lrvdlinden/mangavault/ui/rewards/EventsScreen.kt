package nl.lrvdlinden.mangavault.ui.rewards

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import nl.lrvdlinden.mangavault.app.AppState
import nl.lrvdlinden.mangavault.app.LocalApp
import nl.lrvdlinden.mangavault.app.Route
import nl.lrvdlinden.mangavault.core.Base44
import nl.lrvdlinden.mangavault.core.Engines
import nl.lrvdlinden.mangavault.core.bool
import nl.lrvdlinden.mangavault.core.parseObject
import nl.lrvdlinden.mangavault.model.CommunityEvent
import nl.lrvdlinden.mangavault.model.EventParticipation
import nl.lrvdlinden.mangavault.model.LeaderboardRow
import nl.lrvdlinden.mangavault.model.TradingCard
import nl.lrvdlinden.mangavault.model.nonEmpty
import nl.lrvdlinden.mangavault.ui.components.Badge
import nl.lrvdlinden.mangavault.ui.components.EventBannerView
import nl.lrvdlinden.mangavault.ui.components.LoadingView
import nl.lrvdlinden.mangavault.ui.components.OutlineButton
import nl.lrvdlinden.mangavault.ui.components.PrimaryButton
import nl.lrvdlinden.mangavault.ui.components.ProgressBar
import nl.lrvdlinden.mangavault.ui.components.RemoteImage
import nl.lrvdlinden.mangavault.ui.components.ScreenHeader
import nl.lrvdlinden.mangavault.ui.components.cardStyle
import nl.lrvdlinden.mangavault.ui.theme.Vault
import nl.lrvdlinden.mangavault.ui.theme.hexColor
import java.time.LocalDate
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException

// Port of EventsView (web /events): active / upcoming / completed community events, legacy join + progress flow.

private class RwEventsModel(private val app: AppState) {
    var events by mutableStateOf<List<CommunityEvent>>(emptyList())
    var participations by mutableStateOf<Map<String, EventParticipation>>(emptyMap())
    var rewardCards by mutableStateOf<Map<String, TradingCard>>(emptyMap())
    var loading by mutableStateOf(true)
    var errorText by mutableStateOf<String?>(null)
    var busy by mutableStateOf<String?>(null)
    var openLeaderboards by mutableStateOf<Set<String>>(emptySet())
    var leaderboards by mutableStateOf<Map<String, List<LeaderboardRow>>>(emptyMap())

    class Groups(val active: List<CommunityEvent>, val upcoming: List<CommunityEvent>, val completed: List<CommunityEvent>)

    val grouped: Groups
        get() {
            val today = RwDay.today
            val active = mutableListOf<CommunityEvent>()
            val upcoming = mutableListOf<CommunityEvent>()
            val completed = mutableListOf<CommunityEvent>()
            for (e in events) {
                val end = RwDay.parse(e.end_date)
                val start = RwDay.parse(e.start_date)
                val endedByDate = end?.let { it < today } ?: false
                val futureByDate = start?.let { it > today } ?: false
                if (e.status == "completed" || endedByDate) completed += e
                else if (e.status == "upcoming" || futureByDate) upcoming += e
                else active += e
            }
            upcoming.sortBy { RwDay.parse(it.start_date) ?: LocalDate.MAX }
            return Groups(active, upcoming, completed)
        }

    suspend fun load() {
        try {
            val page = Base44.list<CommunityEvent>("CommunityEvent", sort = "-start_date", limit = 50)
            val evs = page.items.filter { it.publish_status != "draft" && it.publish_status != "archived" }

            val parts = mutableMapOf<String, EventParticipation>()
            val uid = nonEmpty(app.user?.id)
            if (uid != null) {
                val pp = runCatching {
                    Base44.list<EventParticipation>(
                        "EventParticipation", query = mapOf("user_id" to uid), limit = 100,
                        fields = listOf("event_id", "progress", "completed", "reward_claimed"),
                    )
                }.getOrNull()
                for (p in pp?.items ?: emptyList()) nonEmpty(p.event_id)?.let { parts[it] = p }
            }

            val ids = evs.mapNotNull { nonEmpty(it.reward_card_id) }.distinct()
            val rc = mutableMapOf<String, TradingCard>()
            if (ids.isNotEmpty()) {
                val cs = runCatching {
                    Base44.list<TradingCard>(
                        "TradingCard", query = mapOf("card_id" to mapOf("\$in" to ids)), limit = maxOf(ids.size, 1),
                        fields = listOf("card_id", "title", "rarity", "artwork_url"),
                    )
                }.getOrNull()
                for (c in cs?.items ?: emptyList()) rc[c.card_id] = c
            }

            events = evs
            participations = parts
            rewardCards = rc
            errorText = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            errorText = e.message ?: "Could not load events."
        }
        loading = false
    }

    private suspend fun guarded(e: CommunityEvent, block: suspend () -> Unit) {
        if (busy != null) return
        busy = e.id
        try {
            block()
        } finally {
            busy = null
        }
    }

    suspend fun join(e: CommunityEvent) = guarded(e) {
        try {
            val o = parseObject(Engines.community("join", mapOf("event_id" to e.id)))
            val p = rwParticipation(o)
            if (p != null) {
                participations = participations + (e.id to p)
                app.show("Joined event!")
            } else {
                app.show("Could not join event", rwError(o, "Please try again later."), error = true)
            }
        } catch (ex: Exception) {
            app.fail("Could not join event", ex)
        }
    }

    suspend fun updateProgress(e: CommunityEvent) = guarded(e) {
        try {
            val o = parseObject(Engines.community("progress", mapOf("event_id" to e.id)))
            val p = rwParticipation(o)
            if (p != null) {
                participations = participations + (e.id to p)
                app.show("Progress: ${p.progress}/${e.requirement_value}")
            } else {
                app.show("Could not update progress", rwError(o, "Please try again later."), error = true)
            }
        } catch (ex: Exception) {
            app.fail("Could not update progress", ex)
        }
    }

    suspend fun claimLegacy(e: CommunityEvent) = guarded(e) {
        try {
            val o = parseObject(Engines.community("claim", mapOf("event_id" to e.id)))
            if (o.bool("ok")) {
                app.show("Reward claimed!", "A new card was added to your collection")
                app.celebrate()
                app.dataChanged()
                load()
            } else {
                app.show("Could not claim reward", rwError(o, "Please try again later."), error = true)
            }
        } catch (ex: Exception) {
            app.fail("Could not claim reward", ex)
        }
    }

    fun toggleLeaderboard(e: CommunityEvent, scope: CoroutineScope) {
        if (e.id in openLeaderboards) {
            openLeaderboards = openLeaderboards - e.id
            return
        }
        openLeaderboards = openLeaderboards + e.id
        val eid = e.id
        scope.launch {
            val rows = runCatching { Engines.leaderboard(eid) }.getOrNull() ?: emptyList()
            leaderboards = leaderboards + (eid to rows.take(8))
        }
    }
}

@Composable
fun EventsScreen() {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    val m = remember { RwEventsModel(app) }
    var refreshing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { m.load() }

    RwScroll(refreshing, onRefresh = {
        scope.launch {
            refreshing = true
            m.load()
            refreshing = false
        }
    }) {
        ScreenHeader(
            "COMMUNITY", "EVENTS", eyebrow = "Events", eyebrowIcon = Icons.Filled.CalendarMonth,
            subtitle = "Join reading events, track real progress, and earn exclusive trading cards.",
        )
        EventBannerView()
        val err = m.errorText
        if (m.loading && m.events.isEmpty()) {
            LoadingView()
        } else if (err != null && m.events.isEmpty()) {
            RwErrorState(err) { scope.launch { m.load() } }
        } else {
            val g = m.grouped
            RwEventSection("Active Events", Icons.Filled.Bolt, g.active, "No active events at the moment.", true, m, app, scope)
            RwEventSection("Upcoming", Icons.Filled.CalendarMonth, g.upcoming, "No upcoming events scheduled.", false, m, app, scope)
            RwEventSection("Completed", Icons.Filled.CheckCircle, g.completed, "No completed events yet.", false, m, app, scope)
        }
    }
}

@Composable
private fun RwEventSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    list: List<CommunityEvent>,
    empty: String,
    active: Boolean,
    m: RwEventsModel,
    app: AppState,
    scope: CoroutineScope,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        RwSectionTitle(title, icon = icon)
        if (list.isEmpty()) {
            Text(empty, color = Vault.muted, fontSize = 14.sp, modifier = Modifier.fillMaxWidth().cardStyle().padding(16.dp))
        } else {
            for (e in list) RwEventCard(e, active, m, app, scope)
        }
    }
}

@Composable
private fun RwEventCard(e: CommunityEvent, active: Boolean, m: RwEventsModel, app: AppState, scope: CoroutineScope) {
    val accent = hexColor(e.accent)
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(Vault.card.copy(alpha = 0.6f))
            .border(1.dp, if (active) accent.copy(alpha = 0.5f) else Vault.border, shape),
    ) {
        RwEventCardBanner(e, accent, active)
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            RwEventInfo(e, m.rewardCards)
            RwEventLinks(e, accent) { app.open(it) }
            if (e.publish_status == null && e.type != "christmas_event" && e.type != "grand_event") {
                RwLegacyBlock(e, accent, m, scope)
            }
        }
    }
}

@Composable
private fun RwEventCardBanner(e: CommunityEvent, accent: Color, active: Boolean) {
    val img = nonEmpty(e.banner_url) ?: e.heroImage
    Box(Modifier.fillMaxWidth().height(130.dp)) {
        if (img != null) RemoteImage(img, Modifier.fillMaxSize())
        else Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(accent, Vault.background))))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)))))
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (e.typeLabel.isNotEmpty()) {
                Badge(e.typeLabel.uppercase(Locale.US), fg = Color.White, bg = Color.Black.copy(alpha = 0.65f), ring = accent.copy(alpha = 0.6f))
            }
            Spacer(Modifier.weight(1f))
            if (active) Badge("LIVE", icon = Icons.Filled.Bolt, fg = Color.White, bg = Vault.red)
        }
    }
}

@Composable
private fun RwEventInfo(e: CommunityEvent, rewardCards: Map<String, TradingCard>) {
    val reqLabel = CommunityEvent.requirementLabels[e.requirement_type ?: ""] ?: "goals"
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(if (e.displayTitle.isEmpty()) "Event" else e.displayTitle, color = Vault.foreground, fontSize = 17.sp, fontWeight = FontWeight.Black)
        val d = nonEmpty(e.description) ?: nonEmpty(e.short_description)
        if (d != null) Text(d, color = Vault.muted, fontSize = 12.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RwLabel(rwDateRange(e), Icons.Filled.CalendarMonth, Vault.muted, Modifier.weight(1f, fill = false), fontSize = 11)
            if (e.requirement_value > 0) {
                RwLabel("${e.requirement_value} $reqLabel", Icons.Filled.EmojiEvents, Vault.muted, fontSize = 11)
            }
        }
        val rid = nonEmpty(e.reward_card_id)
        val card = if (rid != null) rewardCards[rid] else null
        if (card != null) RwRewardMini(card)
    }
}

@Composable
private fun RwRewardMini(c: TradingCard) {
    val art = nonEmpty(c.artwork_url)
    val shape = RoundedCornerShape(6.dp)
    Row(
        Modifier.fillMaxWidth().background(Vault.secondary.copy(alpha = 0.5f), RoundedCornerShape(10.dp)).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(36.dp, 50.dp).clip(shape).background(Vault.secondary).border(1.dp, hexColor(c.rarityValue.hex), shape),
            contentAlignment = Alignment.Center,
        ) {
            if (art != null) RemoteImage(art, Modifier.fillMaxSize())
            else Icon(Icons.Filled.Image, null, tint = Vault.muted, modifier = Modifier.size(14.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("REWARD CARD", color = Vault.muted, fontSize = 9.sp, fontWeight = FontWeight.Black)
            Text(c.title, color = Vault.foreground, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            RwRarityPill(c.rarity)
        }
    }
}

@Composable
private fun RwEventLinks(e: CommunityEvent, accent: Color, open: (Route) -> Unit) {
    val kind = e.type ?: ""
    val showGeneric = e.publish_status != null || kind !in listOf("special_release", "christmas_event")
    if (kind == "special_release") RwLink("View Event Page →", accent) { open(Route.BorutoEvent) }
    if (kind == "christmas_event") RwLink("View Event Page →", accent) { open(Route.ChristmasEvent) }
    if (showGeneric) RwLink("View Event →", accent) { open(Route.Event(e.id)) }
}

@Composable
private fun RwLegacyBlock(e: CommunityEvent, accent: Color, m: RwEventsModel, scope: CoroutineScope) {
    val isBusy = m.busy == e.id
    val p = m.participations[e.id]
    if (p == null) {
        PrimaryButton(
            if (isBusy) "Joining…" else "Join Event",
            onClick = { scope.launch { m.join(e) } },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isBusy,
            color = accent,
        )
        return
    }
    val lbOpen = e.id in m.openLeaderboards
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Your progress", color = Vault.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("${p.progress}/${e.requirement_value}", color = Vault.foreground, fontSize = 12.sp, fontWeight = FontWeight.Black)
        }
        ProgressBar(p.progress.toFloat() / maxOf(e.requirement_value, 1).toFloat(), height = 6.dp, color = accent)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlineButton("Update", onClick = { scope.launch { m.updateProgress(e) } }, enabled = !isBusy, icon = Icons.Filled.Refresh)
            OutlineButton(
                "Leaderboard", onClick = { m.toggleLeaderboard(e, scope) }, enabled = !isBusy, icon = Icons.Filled.Leaderboard,
                color = if (lbOpen) accent else Vault.foreground,
            )
        }
        if ((p.completed ?: false) && !(p.reward_claimed ?: false)) {
            PrimaryButton(
                if (isBusy) "Claiming…" else "Claim Reward",
                onClick = { scope.launch { m.claimLegacy(e) } },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isBusy,
                color = accent,
            )
        }
        if (p.reward_claimed ?: false) {
            Badge("Reward claimed", icon = Icons.Filled.Check, fg = Vault.emerald, bg = Vault.emerald.copy(alpha = 0.2f), ring = Vault.emerald.copy(alpha = 0.4f))
        }
        if (lbOpen) {
            RwLeaderboardList(m.leaderboards[e.id], target = e.requirement_value, accent = accent)
        }
    }
}

