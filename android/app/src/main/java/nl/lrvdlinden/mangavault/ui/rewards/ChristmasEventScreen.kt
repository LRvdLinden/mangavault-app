package nl.lrvdlinden.mangavault.ui.rewards

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.automirrored.filled.Send
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import nl.lrvdlinden.mangavault.app.AppState
import nl.lrvdlinden.mangavault.app.LocalApp
import nl.lrvdlinden.mangavault.app.MainTab
import nl.lrvdlinden.mangavault.app.Route
import nl.lrvdlinden.mangavault.core.Base44
import nl.lrvdlinden.mangavault.core.Engines
import nl.lrvdlinden.mangavault.core.bool
import nl.lrvdlinden.mangavault.core.parseObject
import nl.lrvdlinden.mangavault.model.CommunityEvent
import nl.lrvdlinden.mangavault.model.SpecialEventState
import nl.lrvdlinden.mangavault.model.TradingCard
import nl.lrvdlinden.mangavault.model.nonEmpty
import nl.lrvdlinden.mangavault.ui.components.Badge
import nl.lrvdlinden.mangavault.ui.components.CardSize
import nl.lrvdlinden.mangavault.ui.components.CountdownView
import nl.lrvdlinden.mangavault.ui.components.EmptyState
import nl.lrvdlinden.mangavault.ui.components.LoadingView
import nl.lrvdlinden.mangavault.ui.components.PrimaryButton
import nl.lrvdlinden.mangavault.ui.components.TradingCardView
import nl.lrvdlinden.mangavault.ui.components.cardStyle
import nl.lrvdlinden.mangavault.ui.theme.Vault
import kotlin.coroutines.cancellation.CancellationException

// Port of ChristmasEventView (web /christmas-event): festival quests via communityEngine christmas_state.

private class RwChristmasModel(private val app: AppState) {
    var event by mutableStateOf<CommunityEvent?>(null)
    var st by mutableStateOf<SpecialEventState?>(null)
    var card by mutableStateOf<TradingCard?>(null)
    var ownsCard by mutableStateOf(false)
    var loading by mutableStateOf(true)
    var noEvent by mutableStateOf(false)
    var errorText by mutableStateOf<String?>(null)
    var busy by mutableStateOf<String?>(null)
    var recommendation by mutableStateOf("")

    val status: String get() = st?.status ?: "upcoming"
    val isLive: Boolean get() = status == "live"

    suspend fun load() {
        try {
            val page = Base44.list<CommunityEvent>("CommunityEvent", query = mapOf("type" to "christmas_event"), sort = "-start_date", limit = 1)
            val e = page.items.firstOrNull()
            if (e == null) {
                event = null
                noEvent = true
                loading = false
                return
            }
            noEvent = false
            val d = Engines.community("christmas_state", mapOf("event_id" to e.id))
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
            event = e
            st = state
            card = rewardCard
            ownsCard = owns
            errorText = null
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            errorText = ex.message ?: "Could not load the Christmas event."
        }
        loading = false
    }

    suspend fun publishRecommendation() {
        if (!isLive) return
        val text = recommendation.trim()
        if (text.length < 20) {
            app.show("Write at least 20 characters", error = true)
            return
        }
        if (busy != null) return
        busy = "c4"
        try {
            Base44.create<JsonElement>("JournalEntry", mapOf(
                "entry_type" to "note", "title" to "Christmas recommendation", "content" to text,
            ))
            recommendation = ""
            app.show("Recommendation published!")
            app.dataChanged()
            load()
        } catch (ex: Exception) {
            app.fail("Could not publish", ex)
        } finally {
            busy = null
        }
    }

    suspend fun claim() {
        val e = event ?: return
        if (busy != null) return
        busy = "claim"
        try {
            val o = parseObject(Engines.community("claim", mapOf("event_id" to e.id)))
            if (o.bool("ok")) {
                app.show("Christmas Manga Spirit claimed!", "Card and Christmas Champion badge added permanently.")
                app.celebrate()
                app.dataChanged()
                load()
            } else {
                app.show("Could not claim reward", rwError(o, "Please try again later."), error = true)
            }
        } catch (ex: Exception) {
            app.fail("Could not claim reward", ex)
        } finally {
            busy = null
        }
    }
}

@Composable
fun ChristmasEventScreen() {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    val m = remember { RwChristmasModel(app) }
    var refreshing by remember { mutableStateOf(false) }
    val accent = Vault.red

    LaunchedEffect(Unit) { m.load() }

    RwScroll(refreshing, onRefresh = {
        scope.launch {
            refreshing = true
            m.load()
            refreshing = false
        }
    }) {
        val e = m.event
        if (m.loading && e == null && !m.noEvent) {
            LoadingView()
        } else if (m.noEvent) {
            EmptyState(Icons.Filled.AcUnit, "No Christmas event configured.")
        } else if (e != null) {
            RwChristmasHero(e, m.status, accent)
            if (!m.isLive) {
                if (m.status == "ended") {
                    RwNotice("The festival has ended. Earned cards, badges and points stay on your profile forever.", Vault.amber, Icons.Filled.Flag)
                } else {
                    RwNotice("Quests open on ${e.start_date ?: "the start date"}. Activity from that day on counts!", Vault.sky, Icons.Filled.Schedule)
                }
            }
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RwSectionTitle("Festival quests", Modifier.weight(1f), icon = Icons.Filled.AcUnit, color = accent)
                    Text("${m.st?.quests?.points ?: 0} event points", color = Vault.emerald, fontSize = 12.sp, fontWeight = FontWeight.Black)
                    Icon(Icons.Filled.Refresh, "Refresh", tint = accent,
                        modifier = Modifier.size(18.dp).clickable { scope.launch { m.load() } })
                }
                for (q in rwChristmasQuests) {
                    RwSpecialQuestRow(q, m.st?.count(q.key) ?: 0, m.st?.isDone(q.key) ?: false, accent) {
                        RwChristmasQuestAction(q.key, m, accent, app, onPublish = { scope.launch { m.publishRecommendation() } })
                    }
                }
            }
            RwChristmasReward(m, accent, onClaim = { scope.launch { m.claim() } }, onCards = { app.open(Route.Cards) })
            Text("Photo: Pexels (free to use under the Pexels License).", color = Vault.muted, fontSize = 11.sp)
        } else {
            RwErrorState(m.errorText ?: "Could not load the Christmas event.") { scope.launch { m.load() } }
        }
    }
}

@Composable
private fun RwChristmasHero(e: CommunityEvent, status: String, accent: Color) {
    val isLive = status == "live"
    RwHero(nonEmpty(e.banner_url) ?: e.heroImage, accent, snow = true, lights = true, minHeight = 240.dp, topPadding = 20.dp) {
        Text(
            if (isLive) "● Live now" else if (status == "ended") "Event ended" else "Coming soon",
            color = Vault.emerald, fontSize = 12.sp, fontWeight = FontWeight.Black,
        )
        Text("🎄 CHRISTMAS READING FESTIVAL", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black, lineHeight = 26.sp)
        Text("Celebrate the holidays, discover manga and earn exclusive rewards!", color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp)
        Text("${e.start_date ?: ""} → ${e.end_date ?: ""}", color = Color.White.copy(alpha = 0.65f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
        if (isLive) {
            Text("ENDS IN", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 2.dp))
            CountdownView(RwDay.endOfDay(e.end_date), compact = true)
        } else if (status != "ended") {
            Text("STARTS IN", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 2.dp))
            CountdownView(RwDay.start(e.start_date), compact = true)
        }
    }
}

@Composable
private fun RwChristmasQuestAction(key: String, m: RwChristmasModel, accent: Color, app: AppState, onPublish: () -> Unit) {
    when (key) {
        "c1_reader" -> RwSmallButton("Go to Collection", Icons.AutoMirrored.Filled.LibraryBooks, Vault.primary) {
            app.selectTab(MainTab.COLLECTION)
        }
        "c2_collector" -> RwSmallButton("Discover", Icons.Filled.Explore, accent) { app.open(Route.Discover) }
        "c3_discovery" -> RwSmallButton("Find one", Icons.Filled.Casino, accent) { app.open(Route.RandomManga) }
        "c4_spirit" -> if (m.isLive) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RwTextEditorBox("Which manga would you recommend this Christmas, and why?", m.recommendation) { m.recommendation = it }
                RwSmallButton(if (m.busy == "c4") "Publishing…" else "Publish", Icons.AutoMirrored.Filled.Send, accent, enabled = m.busy == null, onClick = onPublish)
            }
        }
        else -> {}
    }
}

@Composable
private fun RwChristmasReward(m: RwChristmasModel, accent: Color, onClaim: () -> Unit, onCards: () -> Unit) {
    val champion = m.st?.isDone("c5_champion") ?: false
    val claimed = m.st?.participation?.reward_claimed ?: false
    val card = m.card
    Column(Modifier.fillMaxWidth().cardStyle().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        RwSectionTitle("Exclusive reward", icon = Icons.Filled.CardGiftcard, color = accent)
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (card != null) {
                TradingCardView(card, Modifier.fillMaxWidth(), owned = m.ownsCard, size = CardSize.LG)
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Become Christmas Champion to earn the epic Christmas Manga Spirit card and the Christmas Champion badge.",
                    color = Vault.muted, fontSize = 14.sp,
                )
                if (claimed) {
                    Badge("Reward earned", icon = Icons.Filled.Check, fg = Vault.emerald, bg = Vault.emerald.copy(alpha = 0.2f), ring = Vault.emerald.copy(alpha = 0.4f))
                } else if (champion) {
                    PrimaryButton(
                        if (m.busy == "claim") "Claiming…" else "Claim Reward",
                        onClick = onClaim,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = m.busy == null,
                        color = accent,
                    )
                } else {
                    Text("Complete 3 quests to unlock", color = Vault.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                RwLink("View my cards →", accent, onCards)
            }
        }
    }
}
