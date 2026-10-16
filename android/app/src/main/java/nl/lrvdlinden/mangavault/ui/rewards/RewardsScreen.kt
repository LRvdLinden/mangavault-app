package nl.lrvdlinden.mangavault.ui.rewards

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import nl.lrvdlinden.mangavault.app.AppState
import nl.lrvdlinden.mangavault.app.LocalApp
import nl.lrvdlinden.mangavault.app.Route
import nl.lrvdlinden.mangavault.core.Engines
import nl.lrvdlinden.mangavault.model.LevelReward
import nl.lrvdlinden.mangavault.model.RewardHistoryItem
import nl.lrvdlinden.mangavault.model.RewardsState
import nl.lrvdlinden.mangavault.model.ServerDate
import nl.lrvdlinden.mangavault.model.nonEmpty
import nl.lrvdlinden.mangavault.ui.components.LoadingView
import nl.lrvdlinden.mangavault.ui.components.OutlineButton
import nl.lrvdlinden.mangavault.ui.components.PrimaryButton
import nl.lrvdlinden.mangavault.ui.components.ProgressBar
import nl.lrvdlinden.mangavault.ui.components.ScreenHeader
import nl.lrvdlinden.mangavault.ui.components.cardStyle
import nl.lrvdlinden.mangavault.ui.theme.Vault
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException

// Port of RewardsView (web /rewards): daily reward, level rewards and reward history.

private class RwRewardsModel(private val app: AppState) {
    var state by mutableStateOf<RewardsState?>(null)
    var loading by mutableStateOf(true)
    var errorText by mutableStateOf<String?>(null)
    var claiming by mutableStateOf(false)
    var history by mutableStateOf<List<RewardHistoryItem>>(emptyList())
    var historyCursor: String? = null
    var historyHasMore by mutableStateOf(false)
    var historyLoading by mutableStateOf(false)
    var historyVersion by mutableIntStateOf(0)

    suspend fun load() {
        try {
            state = Engines.rewardsState()
            errorText = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            errorText = e.message ?: "Could not load your rewards."
        }
        loading = false
    }

    suspend fun loadHistory(reset: Boolean) {
        if (historyLoading && !reset) return
        historyLoading = true
        try {
            val page = Engines.rewardHistory(if (reset) null else historyCursor)
            history = if (reset) page.items else history + page.items
            historyCursor = nonEmpty(page.next_cursor)
            historyHasMore = (page.has_more ?: true) && historyCursor != null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!reset) app.fail("Could not load reward history", e)
        } finally {
            historyLoading = false
        }
    }

    suspend fun claimDaily() {
        if (claiming) return
        claiming = true
        try {
            val r = Engines.claimDaily()
            app.show("Daily reward claimed: +${r.xp} XP", if (r.capsule) "Streak bonus: a Vault Capsule was added!" else null)
            app.celebrate()
            val st = r.state
            if (st != null) state = st else load()
            historyVersion++
            app.dataChanged()
        } catch (e: Exception) {
            app.fail("Could not claim daily reward", e)
        } finally {
            claiming = false
        }
    }
}

private val rwHistoryFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy, h:mm a", Locale.US)

@Composable
fun RewardsScreen() {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    val m = remember { RwRewardsModel(app) }
    var refreshing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { m.load() }
    LaunchedEffect(m.historyVersion) { m.loadHistory(reset = true) }

    RwScroll(refreshing, onRefresh = {
        scope.launch {
            refreshing = true
            m.load()
            m.historyVersion++
            refreshing = false
        }
    }) {
        ScreenHeader(
            "MY", "REWARDS", eyebrow = "Rewards", eyebrowIcon = Icons.Filled.CardGiftcard,
            subtitle = "Daily rewards, level rewards and everything you earned.",
        )
        val s = m.state
        if (m.loading && s == null) {
            LoadingView()
        } else if (s != null) {
            RwDailyCard(s, m.claiming) { scope.launch { m.claimDaily() } }
            RwLevelRewardsCard(s) { app.open(Route.Capsules) }
            RwHistoryCard(m) { scope.launch { m.loadHistory(reset = false) } }
        } else {
            RwErrorState(m.errorText ?: "Could not load your rewards.") { scope.launch { m.load() } }
        }
    }
}

@Composable
private fun RwDailyCard(s: RewardsState, claiming: Boolean, onClaim: () -> Unit) {
    val claimed = s.daily?.claimedToday ?: false
    val streak = s.daily?.streak ?: 0
    val xp = s.daily?.xp ?: 0
    val capsule = (s.daily?.capsule ?: false) && !claimed
    val note = if (claimed) "Next reward available tomorrow at 00:00 (Amsterdam time)."
    else "A new day starts at 00:00 Amsterdam time. Missing a day only resets the streak bonus."
    Column(Modifier.fillMaxWidth().cardStyle(highlighted = !claimed).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        RwSectionTitle("Daily Reward", icon = Icons.Filled.CalendarMonth)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Streak: $streak day${rwS(streak)}", color = Vault.foreground, fontSize = 17.sp, fontWeight = FontWeight.Black)
                Text("+$xp XP" + (if (capsule) " + Capsule" else ""), color = Vault.primary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            Icon(Icons.Filled.LocalFireDepartment, null, tint = if (streak > 0) Vault.orange else Vault.muted, modifier = Modifier.size(30.dp))
        }
        PrimaryButton(
            if (claimed) "Claimed today" else if (claiming) "Claiming…" else "Claim Daily Reward",
            onClick = onClaim,
            modifier = Modifier.fillMaxWidth(),
            enabled = !(claimed || claiming),
            color = if (claimed) Vault.secondary else Vault.primary,
        )
        Text(note, color = Vault.muted, fontSize = 12.sp)
    }
}

@Composable
private fun RwLevelRewardsCard(s: RewardsState, onCapsules: () -> Unit) {
    val info = s.level.info
    val rewards = s.levelRewards ?: emptyList()
    Column(Modifier.fillMaxWidth().cardStyle().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        RwSectionTitle("Level Rewards", icon = Icons.Filled.Star)
        Text("You are level ${info.level} · ${info.into}/${info.span} XP to next level", color = Vault.muted, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        ProgressBar(info.progress, height = 8.dp)
        for (r in rewards) RwLevelRow(r)
        RwLink("Open unlocked capsules →", Vault.primary, onCapsules)
    }
}

@Composable
private fun RwLevelRow(r: LevelReward) {
    val unlocked = r.unlocked ?: false
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier.fillMaxWidth()
            .background(if (unlocked) Vault.primary.copy(alpha = 0.1f) else Vault.secondary.copy(alpha = 0.5f), shape)
            .border(1.dp, if (unlocked) Vault.primary.copy(alpha = 0.4f) else Color.Transparent, shape)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.width(22.dp), contentAlignment = Alignment.Center) {
            Icon(if (unlocked) Icons.Filled.CheckCircle else Icons.Filled.Lock, null,
                tint = if (unlocked) Vault.primary else Vault.muted, modifier = Modifier.size(18.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text("Level ${r.level}", color = if (unlocked) Vault.primary else Vault.muted, fontSize = 12.sp, fontWeight = FontWeight.Black)
            Text(r.label ?: "", color = if (unlocked) Vault.foreground else Vault.muted, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun RwHistoryCard(m: RwRewardsModel, onMore: () -> Unit) {
    Column(Modifier.fillMaxWidth().cardStyle().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        RwSectionTitle("Reward History", icon = Icons.Filled.History)
        if (m.history.isEmpty() && m.historyLoading) {
            Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Vault.muted, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            }
        } else if (m.history.isEmpty()) {
            Text("No rewards yet.", color = Vault.muted, fontSize = 14.sp)
        } else {
            for (item in m.history) RwHistoryRow(item)
            if (m.historyHasMore) {
                OutlineButton(
                    if (m.historyLoading) "Loading…" else "Load more",
                    onClick = onMore,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !m.historyLoading,
                )
            }
        }
    }
}

@Composable
private fun RwHistoryRow(item: RewardHistoryItem) {
    val date = ServerDate.parse(nonEmpty(item.granted_at) ?: item.created_date)
    val dateText = date?.let { rwHistoryFormatter.format(it.atZone(ZoneId.systemDefault())) } ?: ""
    val kind = RewardHistoryItem.kindLabels[item.kind ?: ""] ?: (item.kind ?: "")
    val duplicate = item.status == "duplicate"
    Column(
        Modifier.fillMaxWidth().background(Vault.secondary.copy(alpha = 0.5f), RoundedCornerShape(10.dp)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row {
            Text(dateText, color = Vault.muted, fontSize = 11.sp, modifier = Modifier.weight(1f))
            Text(if (duplicate) "Duplicate (ignored)" else "Granted", color = if (duplicate) Vault.muted else Vault.emerald,
                fontSize = 11.sp, fontWeight = FontWeight.Black)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(kind, color = Vault.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(item.label ?: "", color = Vault.foreground, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text(if (item.xp > 0) "+${item.xp} XP" else "—", color = Vault.foreground, fontSize = 14.sp, fontWeight = FontWeight.Black)
        }
    }
}
