package nl.lrvdlinden.mangavault.ui.rewards

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Visibility
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import nl.lrvdlinden.mangavault.app.AppState
import nl.lrvdlinden.mangavault.app.LocalApp
import nl.lrvdlinden.mangavault.core.ApiException
import nl.lrvdlinden.mangavault.core.Base44
import nl.lrvdlinden.mangavault.core.Engines
import nl.lrvdlinden.mangavault.core.bool
import nl.lrvdlinden.mangavault.core.parseObject
import nl.lrvdlinden.mangavault.core.str
import nl.lrvdlinden.mangavault.model.CommunityEvent
import nl.lrvdlinden.mangavault.model.LeaderboardRow
import nl.lrvdlinden.mangavault.model.ServerDate
import nl.lrvdlinden.mangavault.model.SpecialEventState
import nl.lrvdlinden.mangavault.model.TradingCard
import nl.lrvdlinden.mangavault.model.nonEmpty
import nl.lrvdlinden.mangavault.ui.components.Badge
import nl.lrvdlinden.mangavault.ui.components.CardSize
import nl.lrvdlinden.mangavault.ui.components.CountdownView
import nl.lrvdlinden.mangavault.ui.components.EmptyState
import nl.lrvdlinden.mangavault.ui.components.LoadingView
import nl.lrvdlinden.mangavault.ui.components.PrimaryButton
import nl.lrvdlinden.mangavault.ui.components.ProgressBar
import nl.lrvdlinden.mangavault.ui.components.RemoteImage
import nl.lrvdlinden.mangavault.ui.components.TradingCardView
import nl.lrvdlinden.mangavault.ui.components.cardStyle
import nl.lrvdlinden.mangavault.ui.theme.Vault
import nl.lrvdlinden.mangavault.ui.theme.hexColor
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException

// Port of BorutoEventView (web /boruto-event): special release event via communityEngine release_state.

private object RwBoruto {
    const val MANGA_ID = "6acd0e7d0901a2ed093a067a"
    const val COVER = "https://uploads.mangadex.org/covers/0b094aab-0cfb-4837-a49b-7267bdb86bec/baf472db-4658-46fb-8130-d4abdd43f171.jpg.512.jpg"
    const val MANGA_TITLE = "Boruto - Two Blue Vortex"
    const val TOTAL_POINTS = 475
}

private class RwBorutoModel(private val app: AppState) {
    var event by mutableStateOf<CommunityEvent?>(null)
    var st by mutableStateOf<SpecialEventState?>(null)
    var card by mutableStateOf<TradingCard?>(null)
    var ownsCard by mutableStateOf(false)
    var leaders by mutableStateOf<List<LeaderboardRow>?>(null)
    var loading by mutableStateOf(true)
    var noEvent by mutableStateOf(false)
    var errorText by mutableStateOf<String?>(null)
    var busy by mutableStateOf<String?>(null)
    var reviewOpen by mutableStateOf(false)
    var reviewText by mutableStateOf("")

    val accent: Color get() = hexColor(event?.accent ?: "#FF7A00")
    val status: String get() = st?.status ?: "upcoming"
    val isLive: Boolean get() = status == "live"

    suspend fun load() {
        try {
            val page = Base44.list<CommunityEvent>("CommunityEvent", query = mapOf("type" to "special_release"), limit = 1)
            val e = page.items.firstOrNull()
            if (e == null) {
                event = null
                noEvent = true
                loading = false
                return
            }
            noEvent = false
            val d = Engines.community("release_state", mapOf("event_id" to e.id))
            val state = runCatching { Base44.decode<SpecialEventState>(d) }.getOrNull()
            var rewardCard: TradingCard? = null
            var owns = false
            val rid = nonEmpty(e.reward_card_id)
            if (rid != null) {
                rewardCard = runCatching {
                    Base44.list<TradingCard>("TradingCard", query = mapOf("card_id" to rid), limit = 1)
                }.getOrNull()?.items?.firstOrNull()
                val mine = runCatching { Engines.myCards(limit = 5, query = mapOf("card_id" to rid)) }.getOrNull() ?: emptyList()
                owns = mine.isNotEmpty()
            }
            val rows = runCatching { Engines.leaderboard(e.id) }.getOrNull() ?: emptyList()
            event = e
            st = state
            card = rewardCard
            ownsCard = owns
            leaders = rows.take(8)
            errorText = null
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            errorText = ex.message ?: "Could not load the release event."
        }
        loading = false
    }

    /** Runs a quest action (only while the event is live), then reloads the state. */
    fun perform(key: String, scope: CoroutineScope, op: suspend () -> String) {
        if (!isLive) {
            app.show("Quests open on release day", "Boruto Vol. 7 drops on the planned release date.")
            return
        }
        if (busy != null) return
        busy = key
        scope.launch {
            try {
                val toast = op()
                app.show(toast)
                app.dataChanged()
                load()
            } catch (ex: Exception) {
                app.fail("Action failed", ex)
            } finally {
                busy = null
            }
        }
    }

    fun markVisit(scope: CoroutineScope) {
        val eid = event?.id ?: return
        perform("q1_visit", scope) {
            val o = parseObject(Engines.community("release_visit", mapOf("event_id" to eid)))
            nonEmpty(o.str("error"))?.let { throw ApiException(0, it) }
            "Visit registered!"
        }
    }

    fun addVolume(scope: CoroutineScope) {
        perform("q2_collect", scope) {
            Base44.create<JsonElement>("CollectionItem", mapOf(
                "manga_id" to RwBoruto.MANGA_ID,
                "title" to RwBoruto.MANGA_TITLE,
                "volume_number" to 7,
                "total_volumes" to 9,
                "cover_image_url" to RwBoruto.COVER,
                "edition" to "standard",
                "publisher" to "Other",
                "is_shonen_jump" to false,
                "status" to "plan_to_read",
            ))
            "Volume 7 added to your collection!"
        }
    }

    fun logProgress(scope: CoroutineScope) {
        perform("q3_progress", scope) {
            Base44.create<JsonElement>("ReadingActivity", mapOf(
                "type" to "progress",
                "manga_id" to RwBoruto.MANGA_ID,
                "title" to RwBoruto.MANGA_TITLE,
                "volume_number" to 7,
                "meta" to "Volume 7",
            ))
            "Reading progress logged for Volume 7!"
        }
    }

    fun publishReview(scope: CoroutineScope) {
        val text = reviewText.trim()
        if (isLive && text.isEmpty()) {
            app.show("Write a short review first", error = true)
            return
        }
        perform("q4_review", scope) {
            Base44.create<JsonElement>("JournalEntry", mapOf(
                "manga_id" to RwBoruto.MANGA_ID,
                "title" to RwBoruto.MANGA_TITLE,
                "entry_type" to "note",
                "content" to text,
            ))
            reviewText = ""
            reviewOpen = false
            "Review published!"
        }
    }

    suspend fun claim() {
        val e = event ?: return
        if (busy != null) return
        busy = "claim"
        try {
            val o = parseObject(Engines.community("claim", mapOf("event_id" to e.id)))
            if (o.bool("ok")) {
                app.show("Exclusive card claimed!", "Boruto Volume 7 — Release Edition was added to your collection.")
                app.celebrate()
                app.dataChanged()
                load()
            } else {
                app.show("Could not claim card", rwError(o, "Please try again later."), error = true)
            }
        } catch (ex: Exception) {
            app.fail("Could not claim card", ex)
        } finally {
            busy = null
        }
    }
}

@Composable
fun BorutoEventScreen() {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    val m = remember { RwBorutoModel(app) }
    var refreshing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { m.load() }

    RwScroll(refreshing, onRefresh = {
        scope.launch {
            refreshing = true
            m.load()
            refreshing = false
        }
    }) {
        val e = m.event
        val accent = m.accent
        if (m.loading && e == null && !m.noEvent) {
            LoadingView()
        } else if (m.noEvent) {
            EmptyState(Icons.Filled.Park, "No special release event is configured yet.")
        } else if (e != null) {
            RwBorutoHero(e, m.status, accent)
            if (!m.isLive) {
                if (m.status == "ended") {
                    RwNotice("This event has ended. Earned cards, badges and points are kept on your profile.", Vault.amber, Icons.Filled.Flag)
                } else {
                    RwNotice("Quests and rewards activate on the release day. Check back when Volume 7 drops!", Vault.sky, Icons.Filled.Schedule)
                }
            }
            RwBorutoProgress(m, accent)
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RwSectionTitle("Release quests", Modifier.weight(1f), icon = Icons.Filled.EmojiEvents, color = accent)
                    Icon(Icons.Filled.Refresh, "Refresh", tint = accent,
                        modifier = Modifier.size(18.dp).clickable { scope.launch { m.load() } })
                }
                for (q in rwBorutoQuests) {
                    RwSpecialQuestRow(q, m.st?.count(q.key) ?: 0, m.st?.isDone(q.key) ?: false, accent) {
                        RwBorutoQuestAction(q.key, m, accent, scope)
                    }
                }
            }
            RwBorutoReward(m, accent) { scope.launch { m.claim() } }
            Column(Modifier.fillMaxWidth().cardStyle().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                RwSectionTitle("Leaderboard", icon = Icons.Filled.Leaderboard, color = accent)
                RwLeaderboardList(m.leaders, suffix = "pts", accent = accent)
            }
        } else {
            RwErrorState(m.errorText ?: "Could not load the release event.") { scope.launch { m.load() } }
        }
    }
}

@Composable
private fun RwBorutoHero(e: CommunityEvent, status: String, accent: Color) {
    val isLive = status == "live"
    val pillText = if (isLive) "Live" else if (status == "ended") "Event Ended" else "Coming Soon"
    val pillColor = if (isLive) Vault.emerald else if (status == "ended") Vault.muted else Vault.sky
    val start = e.start_date ?: ""
    val coverShape = RoundedCornerShape(10.dp)
    RwHero(e.heroImage ?: RwBoruto.COVER, accent, spacing = 12.dp) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            RemoteImage(
                RwBoruto.COVER,
                Modifier.size(100.dp, 150.dp).clip(coverShape).border(1.dp, Color.White.copy(alpha = 0.15f), coverShape),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Special Release Event".uppercase(Locale.US), color = accent, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                    Badge(pillText, fg = Color.White, bg = pillColor)
                }
                Text("🍃 Boruto Volume 7 Release Celebration", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black, lineHeight = 24.sp)
                Text(
                    "Boruto: Two Blue Vortex — Volume 7 (English edition). Celebrate the release, complete quests, and earn an exclusive legendary trading card.",
                    color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp,
                )
            }
        }
        if (status != "ended" && start.isNotEmpty()) {
            CountdownView(ServerDate.parse("${start}T00:00:00+01:00"), compact = true)
        }
        Text("Planned release $start. This date may change — admins can update it.", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
    }
}

@Composable
private fun RwBorutoProgress(m: RwBorutoModel, accent: Color) {
    val points = m.st?.quests?.points ?: 0
    val baseKeys = listOf("q1_visit", "q2_collect", "q3_progress", "q4_review")
    val baseDone = baseKeys.count { m.st?.isDone(it) ?: false }
    Column(Modifier.fillMaxWidth().cardStyle().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RwSectionTitle("Your progress", Modifier.weight(1f), icon = Icons.Filled.BarChart, color = accent)
            Text("$points pts", color = accent, fontSize = 17.sp, fontWeight = FontWeight.Black)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Quests completed", color = Vault.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("$baseDone/4 base quests", color = Vault.foreground, fontSize = 12.sp, fontWeight = FontWeight.Black)
        }
        ProgressBar(baseDone / 4f, height = 8.dp, color = accent)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Points to champion", color = Vault.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("$points/${RwBoruto.TOTAL_POINTS}", color = Vault.foreground, fontSize = 12.sp, fontWeight = FontWeight.Black)
        }
        ProgressBar(points.toFloat() / RwBoruto.TOTAL_POINTS.toFloat(), height = 8.dp, color = Vault.amber)
    }
}

@Composable
private fun RwBorutoQuestAction(key: String, m: RwBorutoModel, accent: Color, scope: CoroutineScope) {
    val working = m.busy == key
    val idle = m.busy == null
    when (key) {
        "q1_visit" -> RwSmallButton(if (working) "Working…" else "Mark Visit", Icons.Filled.Visibility, accent, enabled = idle) { m.markVisit(scope) }
        "q2_collect" -> RwSmallButton(if (working) "Adding…" else "Add Vol. 7", Icons.Filled.Add, accent, enabled = idle) { m.addVolume(scope) }
        "q3_progress" -> RwSmallButton(if (working) "Logging…" else "Log Progress", Icons.AutoMirrored.Filled.MenuBook, accent, enabled = idle) { m.logProgress(scope) }
        "q4_review" -> Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            RwSmallButton(
                if (m.reviewOpen) "Cancel" else "Review", Icons.Filled.Edit,
                if (m.reviewOpen) Vault.secondary else accent,
            ) { m.reviewOpen = !m.reviewOpen }
            if (m.reviewOpen) {
                RwTextEditorBox("What do you think of Boruto: Two Blue Vortex?", m.reviewText) { m.reviewText = it }
                RwSmallButton(if (working) "Publishing…" else "Publish", Icons.AutoMirrored.Filled.Send, accent, enabled = idle) { m.publishReview(scope) }
            }
        }
        else -> Text("Complete 3 quests", color = Vault.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun RwBorutoReward(m: RwBorutoModel, accent: Color, onClaim: () -> Unit) {
    val champion = m.st?.isDone("q5_champion") ?: false
    val claimed = (m.st?.participation?.reward_claimed ?: false) || m.ownsCard
    val card = m.card
    Column(Modifier.fillMaxWidth().cardStyle().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        RwSectionTitle("Exclusive reward", icon = Icons.Filled.CardGiftcard, color = accent)
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            if (card != null) TradingCardView(card, owned = m.ownsCard, size = CardSize.MD)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Earn the Boruto Volume 7 — Release Edition legendary card by completing the Release Week Champion quest.",
                    color = Vault.muted, fontSize = 14.sp,
                )
                if (claimed) {
                    Badge("Exclusive card earned", icon = Icons.Filled.Check, fg = Vault.emerald, bg = Vault.emerald.copy(alpha = 0.2f), ring = Vault.emerald.copy(alpha = 0.4f))
                } else if (champion) {
                    PrimaryButton(
                        if (m.busy == "claim") "Claiming…" else "Claim Exclusive Card",
                        onClick = onClaim,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = m.busy == null,
                        color = accent,
                    )
                } else {
                    Text("Complete the Champion quest to unlock", color = Vault.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
