package nl.lrvdlinden.mangavault.ui.rewards

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Style
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import nl.lrvdlinden.mangavault.app.AppState
import nl.lrvdlinden.mangavault.app.LocalApp
import nl.lrvdlinden.mangavault.core.Base44
import nl.lrvdlinden.mangavault.core.Engines
import nl.lrvdlinden.mangavault.core.flexInt
import nl.lrvdlinden.mangavault.core.parseObject
import nl.lrvdlinden.mangavault.core.str
import nl.lrvdlinden.mangavault.model.CommunityEvent
import nl.lrvdlinden.mangavault.model.CommunityProgress
import nl.lrvdlinden.mangavault.model.EventEngineState
import nl.lrvdlinden.mangavault.model.EventQuestDef
import nl.lrvdlinden.mangavault.model.GrandEventState
import nl.lrvdlinden.mangavault.model.ServerDate
import nl.lrvdlinden.mangavault.model.TradingCard
import nl.lrvdlinden.mangavault.model.nonEmpty
import nl.lrvdlinden.mangavault.ui.components.Badge
import nl.lrvdlinden.mangavault.ui.components.CardSize
import nl.lrvdlinden.mangavault.ui.components.CountdownView
import nl.lrvdlinden.mangavault.ui.components.LoadingView
import nl.lrvdlinden.mangavault.ui.components.OutlineButton
import nl.lrvdlinden.mangavault.ui.components.PrimaryButton
import nl.lrvdlinden.mangavault.ui.components.ProgressBar
import nl.lrvdlinden.mangavault.ui.components.TradingCardView
import nl.lrvdlinden.mangavault.ui.components.cardStyle
import nl.lrvdlinden.mangavault.ui.theme.Vault
import nl.lrvdlinden.mangavault.ui.theme.hexColor
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException

// Port of EventDetailView (web /event/:id): eventEngine events plus the grand-event variant (communityEngine).

private class RwEventDetailModel(private val app: AppState, private val eventId: String) {
    var state by mutableStateOf<EventEngineState?>(null)
    var grand by mutableStateOf<GrandEventState?>(null)
    var grandCards by mutableStateOf<List<TradingCard>>(emptyList())
    var grandOwned by mutableStateOf<Set<String>>(emptySet())
    var loading by mutableStateOf(true)
    var errorText by mutableStateOf<String?>(null)
    var busy by mutableStateOf<String?>(null)

    suspend fun load() {
        try {
            val s = Engines.eventState(eventId, visit = true)
            if (s.event?.type == "grand_event") {
                loadGrand()
            } else if (s.event == null) {
                state = null
                errorText = nonEmpty(s.error) ?: "This event could not be found."
            } else {
                state = s
                errorText = null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            errorText = e.message ?: "This event could not be found."
        }
        loading = false
    }

    suspend fun loadGrand() {
        try {
            val d = Engines.community("grand_state", mapOf("event_id" to eventId, "visit" to true))
            val g = Base44.decode<GrandEventState>(d)
            val err = nonEmpty(g.error)
            if (g.event == null && err != null) {
                errorText = err
                return
            }
            grand = g
            errorText = null
            val ids = mutableListOf<String>()
            val candidates = (g.event?.extra_card_ids ?: emptyList()) + listOfNotNull(nonEmpty(g.event?.reward_card_id))
            for (id in candidates) if (id.isNotEmpty() && id !in ids) ids += id
            if (ids.isNotEmpty()) {
                val cards = runCatching { Engines.cards(ids) }.getOrNull() ?: emptyList()
                val mine = runCatching {
                    Engines.myCards(limit = 100, query = mapOf("card_id" to mapOf("\$in" to ids)))
                }.getOrNull() ?: emptyList()
                grandCards = ids.mapNotNull { id -> cards.firstOrNull { it.card_id == id } }
                grandOwned = mine.map { it.card_id }.toSet()
            } else {
                grandCards = emptyList()
                grandOwned = emptySet()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            errorText = e.message ?: "This event could not be found."
        }
    }

    private suspend fun guarded(key: String, block: suspend () -> Unit) {
        if (busy != null) return
        busy = key
        try {
            block()
        } finally {
            busy = null
        }
    }

    suspend fun join() = guarded("join") {
        try {
            Engines.eventAction("join", eventId)
            app.show("Joined event!")
            load()
        } catch (e: Exception) {
            app.fail("Could not join event", e)
        }
    }

    suspend fun claimQuest(q: EventQuestDef) = guarded(q.key) {
        try {
            val o = Engines.eventAction("claim_quest", eventId, questKey = q.key)
            val xp = (o["xp"] as? JsonPrimitive)?.flexInt() ?: q.xp
            app.show(if (xp > 0) "+$xp XP claimed" else "Reward claimed")
            app.celebrate()
            app.dataChanged()
            load()
        } catch (e: Exception) {
            app.fail("Could not claim reward", e)
        }
    }

    suspend fun claimEvent() = guarded("claim_event") {
        try {
            Engines.eventAction("claim_event", eventId)
            app.show("Reward claimed!", "Your event rewards were added to your profile.")
            app.celebrate()
            app.dataChanged()
            load()
        } catch (e: Exception) {
            app.fail("Could not claim reward", e)
        }
    }

    suspend fun grandClaim() = guarded("grand_claim") {
        try {
            val d = Engines.community("grand_claim", mapOf("event_id" to eventId))
            val r = runCatching { Base44.decode<GrandEventState>(d) }.getOrNull()
            val granted = r?.granted ?: emptyList()
            if (granted.isNotEmpty()) {
                app.show("Rewards claimed!", "${granted.size} card${rwS(granted.size)} added to your collection")
                app.celebrate()
                app.dataChanged()
                loadGrand()
            } else {
                val fallback = parseObject(d).str("error") ?: "Nothing to claim yet."
                app.show("Cannot claim", nonEmpty(r?.error) ?: fallback, error = true)
            }
        } catch (e: Exception) {
            app.fail("Cannot claim", e)
        }
    }
}

@Composable
fun EventDetailScreen(eventId: String) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    val m = remember(eventId) { RwEventDetailModel(app, eventId) }
    var refreshing by remember { mutableStateOf(false) }

    LaunchedEffect(eventId) { m.load() }

    RwScroll(refreshing, onRefresh = {
        scope.launch {
            refreshing = true
            m.load()
            refreshing = false
        }
    }) {
        val s = m.state
        val g = m.grand
        val e = s?.event
        if (m.loading && s == null && g == null) {
            LoadingView()
        } else if (g != null) {
            RwGrandContent(g, m, onClaim = { scope.launch { m.grandClaim() } })
        } else if (s != null && e != null) {
            RwEngineContent(
                s, e, m,
                onReload = { scope.launch { m.load() } },
                onJoin = { scope.launch { m.join() } },
                onClaimQuest = { q -> scope.launch { m.claimQuest(q) } },
                onClaimEvent = { scope.launch { m.claimEvent() } },
            )
        } else {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RwErrorState(m.errorText ?: "This event could not be found.") { scope.launch { m.load() } }
                OutlineButton("Back", onClick = { app.back() })
            }
        }
    }
}

// MARK: - Engine variant

@Composable
private fun RwEngineContent(
    s: EventEngineState,
    e: CommunityEvent,
    m: RwEventDetailModel,
    onReload: () -> Unit,
    onJoin: () -> Unit,
    onClaimQuest: (EventQuestDef) -> Unit,
    onClaimEvent: () -> Unit,
) {
    val accent = hexColor(e.accent)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        RwEngineHero(s, e, accent, m.busy, onJoin)
        RwEngineNotice(s, e)
        RwEngineQuests(s, accent, m.busy, onReload, onClaimQuest)
        RwEngineProgress(s, accent)
        RwEngineRewards(s, accent, m.busy, onClaimEvent)
        RwEngineAbout(e)
    }
}

private fun rwEngineStatusLabel(status: String): String = when (status) {
    "active" -> "● Live now"
    "scheduled" -> "Starts soon"
    "ended" -> "Event ended"
    "draft" -> "Draft — only admins see this"
    "archived" -> "Archived"
    else -> rwCapitalized(status)
}

@Composable
private fun RwEngineHero(s: EventEngineState, e: CommunityEvent, accent: Color, busy: String?, onJoin: () -> Unit) {
    val status = s.status ?: ""
    val deco = e.decoration ?: ""
    val snow = (deco == "snow" || deco == "lights") && status != "ended"
    val canJoin = (e.requires_join ?: false) && s.participation == null && status == "active"
    RwHero(e.heroImage, accent, snow = snow, lights = deco == "lights", minHeight = 240.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(rwEngineStatusLabel(status), color = accent, fontSize = 12.sp, fontWeight = FontWeight.Black)
            if (e.typeLabel.isNotEmpty()) {
                Text(e.typeLabel.uppercase(Locale.US), color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            }
        }
        Text(if (e.displayTitle.isEmpty()) "Event" else e.displayTitle, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black, lineHeight = 26.sp)
        nonEmpty(e.subtitle)?.let { Text(it, color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp, fontWeight = FontWeight.Bold) }
        nonEmpty(e.short_description)?.let { Text(it, color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp) }
        Text(rwDateRange(e), color = Color.White.copy(alpha = 0.65f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
        nonEmpty(e.reward_claim_end)?.let { Text("Rewards claimable until $it", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp) }
        if (status == "active") {
            RwCountdownTitle("ENDS IN")
            CountdownView(ServerDate.parse(s.end), compact = true)
        } else if (status == "scheduled") {
            RwCountdownTitle("STARTS IN")
            CountdownView(ServerDate.parse(s.start), compact = true)
        }
        if (canJoin) {
            PrimaryButton(
                if (busy == "join") "Joining…" else "Join this event",
                onClick = onJoin,
                modifier = Modifier.padding(top = 4.dp),
                enabled = busy == null,
                color = accent,
            )
        }
    }
}

@Composable
private fun RwCountdownTitle(text: String) {
    Text(text, color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 2.dp))
}

@Composable
private fun RwEngineNotice(s: EventEngineState, e: CommunityEvent) {
    if (s.status == "scheduled") {
        val whenText = listOfNotNull(nonEmpty(e.start_date), nonEmpty(e.start_time)).joinToString(" ")
        val tz = nonEmpty(e.timezone) ?: "Europe/Amsterdam"
        RwNotice(
            "This event has not started yet. Come back on $whenText ($tz) — your quests start counting the moment you start the event.",
            Vault.sky, Icons.Filled.Schedule,
        )
    } else if (s.status == "ended") {
        val claimEnd = nonEmpty(e.reward_claim_end) ?: (e.end_date ?: "")
        RwNotice(
            "This event has ended. Everything you earned stays on your profile, and you can still claim rewards until $claimEnd.",
            Vault.amber, Icons.Filled.Flag,
        )
    }
}

@Composable
private fun RwEngineQuests(s: EventEngineState, accent: Color, busy: String?, onReload: () -> Unit, onClaim: (EventQuestDef) -> Unit) {
    val quests = s.quests ?: emptyList()
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RwSectionTitle("Event quests", Modifier.weight(1f), icon = Icons.Filled.EmojiEvents, color = accent)
            Text("${s.done_count}/${quests.size} quests done", color = Vault.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Icon(Icons.Filled.Refresh, "Refresh", tint = accent, modifier = Modifier.size(18.dp).clickable(onClick = onReload))
        }
        if (quests.isEmpty()) {
            Text("This event has no quests yet.", color = Vault.muted, fontSize = 14.sp, modifier = Modifier.fillMaxWidth().cardStyle().padding(16.dp))
        } else {
            for (q in quests) RwEngineQuestRow(q, s, accent, busy, onClaim)
        }
    }
}

@Composable
private fun RwEngineQuestRow(q: EventQuestDef, s: EventEngineState, accent: Color, busy: String?, onClaim: (EventQuestDef) -> Unit) {
    val done = q.done ?: false
    val claimed = q.claimed ?: false
    val repeatable = q.repeatable ?: false
    val target = maxOf(q.target, 1)
    val shown = minOf(q.count, target)
    val repeatSuffix = if (repeatable) " · repeatable" else ""
    val kindText = if (q.kindLabel.isEmpty()) (if (repeatable) "Repeatable" else "") else q.kindLabel + repeatSuffix
    Column(Modifier.fillMaxWidth().cardStyle(highlighted = done).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(44.dp).background(accent.copy(alpha = 0.15f), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                Text(nonEmpty(q.icon) ?: "⭐", fontSize = 22.sp)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(nonEmpty(q.label) ?: q.key, color = Vault.foreground, fontSize = 14.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                    Text("+${q.xp} XP", color = accent, fontSize = 12.sp, fontWeight = FontWeight.Black)
                }
                nonEmpty(q.description)?.let { Text(it, color = Vault.muted, fontSize = 12.sp) }
                if (kindText.isNotEmpty()) Text(kindText, color = Vault.muted.copy(alpha = 0.8f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ProgressBar(shown.toFloat() / target.toFloat(), Modifier.weight(1f), height = 6.dp, color = accent)
            Text("$shown/$target", color = Vault.muted, fontSize = 11.sp, fontWeight = FontWeight.Black)
        }
        val hasCard = nonEmpty(q.reward_card_id) != null
        val hasBadge = nonEmpty(q.badge_id) != null
        val claimedOnce = claimed && !repeatable
        if (hasCard || hasBadge || claimedOnce) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (hasCard) Badge("Trading card", icon = Icons.Filled.Style, fg = Vault.sky, bg = Vault.sky.copy(alpha = 0.15f))
                if (hasBadge) Badge("Badge", icon = Icons.Filled.EmojiEvents, fg = Vault.amber, bg = Vault.amber.copy(alpha = 0.15f))
                if (claimedOnce) Badge("Reward claimed", icon = Icons.Filled.Check, fg = Vault.emerald, bg = Vault.emerald.copy(alpha = 0.15f))
            }
        }
        if (done && (!claimed || repeatable)) {
            if (s.can_claim ?: false) {
                PrimaryButton(
                    if (busy == q.key) "Claiming…" else if (claimed) "Claim again" else "Claim reward",
                    onClick = { onClaim(q) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = busy == null,
                    color = accent,
                )
            } else {
                Text("Reward claim period closed", color = Vault.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        } else if (!done) {
            Text("Not completed yet", color = Vault.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun RwEngineProgress(s: EventEngineState, accent: Color) {
    Column(Modifier.fillMaxWidth().cardStyle().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        RwSectionTitle("Your progress", icon = Icons.Filled.BarChart, color = accent)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Earned XP", color = Vault.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("${s.earned} / ${s.max_xp}", color = Vault.foreground, fontSize = 12.sp, fontWeight = FontWeight.Black)
        }
        ProgressBar(s.earned.toFloat() / maxOf(s.max_xp, 1).toFloat(), height = 8.dp, color = accent)
        Text(
            if (s.participation != null) "Points from completed quests: ${s.points}"
            else "Your progress starts counting the moment you start this event.",
            color = Vault.muted, fontSize = 12.sp,
        )
        val c = s.community
        if (c != null) RwCommunityBlock(c, accent)
    }
}

@Composable
private fun RwCommunityBlock(c: CommunityProgress, accent: Color) {
    Column(Modifier.fillMaxWidth().padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Community goal", color = Vault.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("${c.total}/${c.goal}", color = Vault.foreground, fontSize = 12.sp, fontWeight = FontWeight.Black)
        }
        ProgressBar(c.total.toFloat() / maxOf(c.goal, 1).toFloat(), height = 8.dp, color = accent)
        Text("${c.participants} participant${rwS(c.participants)} · you contributed ${c.mine}", color = Vault.muted, fontSize = 11.sp)
    }
}

@Composable
private fun RwCardStrip(cards: List<TradingCard>, owned: Set<String>) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        for (c in cards) TradingCardView(c, owned = c.card_id in owned, size = CardSize.SM)
    }
}

@Composable
private fun RwEngineRewards(s: EventEngineState, accent: Color, busy: String?, onClaim: () -> Unit) {
    val cards = s.rewards?.cards ?: emptyList()
    val owned = (s.owned_cards ?: emptyList()).toSet()
    val completed = s.quests_completed ?: false
    val rewardClaimed = s.participation?.reward_claimed ?: false
    Column(Modifier.fillMaxWidth().cardStyle().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        RwSectionTitle("Rewards", icon = Icons.Filled.CardGiftcard, color = accent)
        if (cards.isEmpty()) {
            Text("No trading card rewards for this event.", color = Vault.muted, fontSize = 12.sp)
        } else {
            RwCardStrip(cards, owned)
        }
        nonEmpty(s.rewards?.badge_name)?.let { RwLabel("Badge: $it", Icons.Filled.EmojiEvents, Vault.amber) }
        if (completed) {
            if (rewardClaimed) {
                Badge("Completion reward claimed", icon = Icons.Filled.Check, fg = Vault.emerald, bg = Vault.emerald.copy(alpha = 0.2f), ring = Vault.emerald.copy(alpha = 0.4f))
            } else {
                PrimaryButton(
                    if (busy == "claim_event") "Claiming…" else "Claim completion reward",
                    onClick = onClaim,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = busy == null,
                    color = accent,
                )
            }
        } else {
            Text("Complete every quest to unlock the event reward.", color = Vault.muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun RwEngineAbout(e: CommunityEvent) {
    val long = nonEmpty(e.long_description)
    val source = nonEmpty(e.image_source)
    if (long == null && source == null) return
    Column(Modifier.fillMaxWidth().cardStyle().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        RwSectionTitle("About this event", icon = Icons.Filled.Info)
        if (long != null) Text(rwStripMarkdown(long), color = Vault.foreground, fontSize = 14.sp)
        if (source != null) Text("Image: $source", color = Vault.muted, fontSize = 11.sp)
    }
}

/** iOS renders the description as inline Markdown; strip the most common markers for plain text. */
private fun rwStripMarkdown(s: String): String =
    s.replace("**", "").replace("__", "").replace(Regex("\\[([^\\]]+)]\\(([^)]+)\\)"), "\$1")

// MARK: - Grand variant (communityEngine)

@Composable
private fun RwGrandContent(g: GrandEventState, m: RwEventDetailModel, onClaim: () -> Unit) {
    val accent = hexColor(g.event?.accent ?: "#FF7A00")
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        RwGrandHero(g, accent)
        val c = g.community
        if (c != null) RwGrandCommunity(c, accent)
        RwGrandQuests(g, accent)
        RwGrandRewards(g, accent, m.grandCards, m.grandOwned, m.busy, onClaim)
    }
}

@Composable
private fun RwGrandHero(g: GrandEventState, accent: Color) {
    val status = g.status ?: ""
    val label = if (status == "live") "● Live now" else if (status == "ended") "Event ended" else "Coming soon"
    val e = g.event
    val deco = e?.decoration ?: ""
    val snow = (deco == "snow" || deco == "lights") && status != "ended"
    RwHero(e?.heroImage, accent, snow = snow, lights = deco == "lights", minHeight = 230.dp) {
        Text(label, color = accent, fontSize = 12.sp, fontWeight = FontWeight.Black)
        Text(nonEmpty(e?.displayTitle) ?: "Grand Event", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black, lineHeight = 26.sp)
        val sub = nonEmpty(e?.subtitle) ?: nonEmpty(e?.short_description)
        if (sub != null) Text(sub, color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
        if (e != null) Text(rwDateRange(e), color = Color.White.copy(alpha = 0.65f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
        if (status == "live") {
            RwCountdownTitle("ENDS IN")
            CountdownView(ServerDate.parse(g.end), compact = true)
        } else if (status != "ended") {
            RwCountdownTitle("STARTS IN")
            CountdownView(ServerDate.parse(g.start), compact = true)
        }
        val q = g.quests
        if (q != null) {
            Text("Your points: ${q.points}", color = accent, fontSize = 14.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
private fun RwGrandCommunity(c: CommunityProgress, accent: Color) {
    Column(Modifier.fillMaxWidth().cardStyle().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        RwSectionTitle("Community challenge", icon = Icons.Filled.Groups, color = accent)
        Text("Log reading progress together. Each unique volume counts once per reader.", color = Vault.muted, fontSize = 12.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${c.total}/${c.goal} volumes", color = Vault.foreground, fontSize = 14.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
            Text("${c.participants} participant${rwS(c.participants)}", color = Vault.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        ProgressBar(c.total.toFloat() / maxOf(c.goal, 1).toFloat(), height = 10.dp, color = accent)
        Text("Your contribution: ${c.mine}", color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun RwGrandQuests(g: GrandEventState, accent: Color) {
    val quests = g.quests?.quests ?: emptyList()
    Column(Modifier.fillMaxWidth().cardStyle().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        RwSectionTitle("Quests", icon = Icons.Filled.EmojiEvents, color = accent)
        if (quests.isEmpty()) {
            Text("Quests unlock when the event goes live.", color = Vault.muted, fontSize = 12.sp)
        } else {
            for (q in quests) RwGrandQuestRow(q, accent)
        }
    }
}

@Composable
private fun RwGrandQuestRow(q: EventQuestDef, accent: Color) {
    val done = q.done ?: false
    val target = maxOf(q.target, 1)
    Row(
        Modifier.fillMaxWidth()
            .background(if (done) Vault.emerald.copy(alpha = 0.08f) else Vault.secondary.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(if (done) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked, null,
            tint = if (done) Vault.emerald else Vault.muted, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(nonEmpty(q.label) ?: q.key, color = Vault.foreground, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            nonEmpty(q.description)?.let { Text(it, color = Vault.muted, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text("${minOf(q.count, target)}/$target", color = Vault.foreground, fontSize = 12.sp, fontWeight = FontWeight.Black)
            Text("+${q.points}", color = accent, fontSize = 11.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun RwGrandRewards(g: GrandEventState, accent: Color, cards: List<TradingCard>, owned: Set<String>, busy: String?, onClaim: () -> Unit) {
    val ownedCount = cards.count { it.card_id in owned }
    val canClaim = g.status != "upcoming" && g.quests != null
    Column(Modifier.fillMaxWidth().cardStyle().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RwSectionTitle("Exclusive rewards", Modifier.weight(1f), icon = Icons.Filled.CardGiftcard, color = accent)
            if (cards.isNotEmpty()) {
                Text("($ownedCount/${cards.size} collected)", color = Vault.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (cards.isNotEmpty()) RwCardStrip(cards, owned)
        nonEmpty(g.event?.badge_name)?.let { RwLabel("Badge: $it", Icons.Filled.EmojiEvents, Vault.amber) }
        if (canClaim) {
            PrimaryButton(
                if (busy == "grand_claim") "Claiming…" else "Claim rewards",
                onClick = onClaim,
                modifier = Modifier.fillMaxWidth(),
                enabled = busy == null,
                color = accent,
            )
        }
    }
}

