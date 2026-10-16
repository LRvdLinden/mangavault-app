package nl.lrvdlinden.mangavault.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import nl.lrvdlinden.mangavault.app.AppState
import nl.lrvdlinden.mangavault.app.LocalApp
import nl.lrvdlinden.mangavault.core.AchievementEngine
import nl.lrvdlinden.mangavault.core.Engines
import nl.lrvdlinden.mangavault.core.VaultService
import nl.lrvdlinden.mangavault.model.AchievementEval
import nl.lrvdlinden.mangavault.model.DayKey
import nl.lrvdlinden.mangavault.model.LevelInfo
import nl.lrvdlinden.mangavault.model.QuestDef
import nl.lrvdlinden.mangavault.model.QuestState
import nl.lrvdlinden.mangavault.model.Rarity
import nl.lrvdlinden.mangavault.model.ServerQuest
import nl.lrvdlinden.mangavault.ui.components.Badge
import nl.lrvdlinden.mangavault.ui.components.LoadingView
import nl.lrvdlinden.mangavault.ui.components.PrimaryButton
import nl.lrvdlinden.mangavault.ui.components.ProgressBar
import nl.lrvdlinden.mangavault.ui.components.ScreenHeader
import nl.lrvdlinden.mangavault.ui.components.cardStyle
import nl.lrvdlinden.mangavault.ui.theme.Vault
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException

// MARK: Achievements

@Composable
fun AchievementsScreen() {
    val app = LocalApp.current
    var result by remember { mutableStateOf<AchievementEngine.Result?>(null) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        try {
            val r = AchievementEngine.run()
            result = r
            val fresh = r.fresh.filter { it.kind == "achievement" }
            if (fresh.isNotEmpty()) {
                app.celebrate()
                for (a in fresh) app.show("Achievement unlocked: ${a.label ?: ""}", "+${a.xp} XP")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            app.fail("Could not load achievements", e)
        }
        loading = false
    }

    Column(
        Modifier.fillMaxSize().background(Vault.background).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        ScreenHeader("ACHIEVEMENTS &", "XP", eyebrow = "Achievements", eyebrowIcon = Icons.Filled.EmojiEvents)
        val r = result
        if (loading && r == null) {
            LoadingView()
        } else if (r != null) {
            LevelCard(r.level, r.totalXP)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                for (e in r.evals) AchCard(e, r.unlockedDates[e.id])
            }
        }
    }
}

@Composable
private fun AchCard(e: AchievementEval, date: String?) {
    Column(Modifier.fillMaxWidth().cardStyle(highlighted = e.unlocked).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            Box(
                Modifier.size(50.dp)
                    .background(if (e.unlocked) Vault.primary.copy(alpha = 0.15f) else Vault.secondary, RoundedCornerShape(10.dp))
                    .alpha(if (e.unlocked) 1f else 0.6f),
                contentAlignment = Alignment.Center,
            ) {
                // Emoji can't be desaturated reliably with a Text colour; dim locked ones instead.
                Text(e.def.icon, fontSize = 26.sp, color = if (e.unlocked) Vault.foreground else Vault.muted)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(e.def.name, color = Vault.foreground, fontSize = 17.sp, fontWeight = FontWeight.Black, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    Icon(if (e.unlocked) Icons.Filled.CheckCircle else Icons.Filled.Lock, null,
                        tint = if (e.unlocked) Vault.primary else Vault.muted, modifier = Modifier.size(14.dp))
                }
                Text(e.def.desc, color = Vault.muted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            RarityBadge(e.def.rarity)
            Spacer(Modifier.weight(1f))
            Icon(Icons.Filled.Star, null, tint = Vault.primary, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(4.dp))
            Text("+${e.def.reward} XP", color = Vault.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        if (!e.unlocked) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row {
                    Text("${e.value} / ${e.total}", color = Vault.muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("${e.percent}%", color = Vault.muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                ProgressBar(e.percent / 100f, height = 6.dp)
            }
        } else if (!date.isNullOrEmpty()) {
            Text("Unlocked $date", color = Vault.muted, fontSize = 11.sp)
        }
    }
}

@Composable
fun RarityBadge(rarity: Rarity) {
    val c = when (rarity) {
        Rarity.COMMON -> Color(0xFFCCCCCC)
        Rarity.RARE -> Vault.sky
        Rarity.EPIC -> Vault.primary
        Rarity.LEGENDARY -> Vault.orange
    }
    val shape = RoundedCornerShape(4.dp)
    Text(
        rarity.label, color = c, fontSize = 10.sp, fontWeight = FontWeight.Black,
        modifier = Modifier.background(c.copy(alpha = 0.15f), shape).border(1.dp, c.copy(alpha = 0.5f), shape)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
fun LevelCard(level: LevelInfo, totalXP: Int, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().cardStyle().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column {
                Text("LEVEL", color = Vault.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Text("${level.level}", color = Vault.primary, fontSize = 52.sp, fontWeight = FontWeight.Black, lineHeight = 56.sp)
            }
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text("TOTAL XP", color = Vault.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Text(String.format(Locale.US, "%,d", totalXP), color = Vault.foreground, fontSize = 28.sp, fontWeight = FontWeight.Black)
            }
        }
        Row {
            Text("${level.into} / ${level.span} XP to Level ${level.level + 1}", color = Vault.muted, fontSize = 12.sp,
                fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("${level.percent}%", color = Vault.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        ProgressBar(level.progress, height = 12.dp)
    }
}

// MARK: Daily & weekly quests (claims go through the server `rewards` function)

private class QuestModel {
    var daily by mutableStateOf<List<QuestState>>(emptyList())
    var weekly by mutableStateOf<List<QuestState>>(emptyList())
    var dailyClaimed by mutableStateOf<Set<String>>(emptySet())
    var weeklyClaimed by mutableStateOf<Set<String>>(emptySet())
    var loading by mutableStateOf(true)
    var claiming by mutableStateOf<String?>(null)

    private fun merge(defs: List<QuestDef>, local: Map<String, Int>, server: List<ServerQuest>): List<QuestState> =
        defs.map { def ->
            val s = server.firstOrNull { it.id == def.id }
            if (s != null) QuestState(def, minOf(s.value, def.total), s.completed)
            else QuestState(def, minOf(local[def.key] ?: 0, def.total))
        }

    /** Local progress (like the web app), overridden by the server's value/completed/claimed. */
    suspend fun load(app: AppState) {
        try {
            val today = DayKey.local()
            val weekStartKey = DayKey.local(DayKey.weekStart())
            val acts = VaultService.activities(limit = 2000)

            val dc = mutableMapOf<String, Int>()
            acts.filter { it.day == today }.forEach { val k = it.type ?: ""; dc[k] = (dc[k] ?: 0) + 1 }
            val weekActs = acts.filter { it.day >= weekStartKey }
            val wc = mutableMapOf<String, Int>()
            weekActs.forEach { val k = it.type ?: ""; wc[k] = (wc[k] ?: 0) + 1 }
            wc["activeDays"] = weekActs.map { it.day }.toSet().size

            val state = Engines.rewardsState()
            daily = merge(QuestDef.all, dc, state.quests ?: emptyList())
            weekly = merge(QuestDef.weekly, wc, state.weekly ?: emptyList())
            dailyClaimed = (state.quests ?: emptyList()).filter { it.claimed == true }.map { it.id }.toSet()
            weeklyClaimed = (state.weekly ?: emptyList()).filter { it.claimed == true }.map { it.id }.toSet()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            app.fail("Could not load quests", e)
        }
        loading = false
    }

    suspend fun claim(app: AppState, q: QuestState, scope: String) {
        if (claiming != null) return
        claiming = (if (scope == "weekly") "w-" else "d-") + q.id
        try {
            val xp = Engines.claimQuest(scope, q.id)
            if (scope == "weekly") weeklyClaimed = weeklyClaimed + q.id else dailyClaimed = dailyClaimed + q.id
            app.celebrate()
            app.show("${if (scope == "weekly") "Weekly" else "Daily"} quest complete: +$xp XP")
            app.dataChanged()
        } catch (e: Exception) {
            app.fail("Could not claim", e)
        } finally {
            claiming = null
        }
    }
}

@Composable
fun QuestsScreen() {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    val model = remember { QuestModel() }

    LaunchedEffect(app.dataVersion) { model.load(app) }

    val weekEnd = remember { DateTimeFormatter.ofPattern("MMM d", Locale.US).format(DayKey.weekStart().plusDays(6)) }

    Column(
        Modifier.fillMaxSize().background(Vault.background).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        ScreenHeader("DAILY &", "WEEKLY QUESTS", eyebrow = "Quests", eyebrowIcon = Icons.Filled.Flag)
        if (model.loading && model.daily.isEmpty()) {
            LoadingView()
        } else {
            QuestSectionHeader("DAILY", Icons.Filled.Schedule, "Resets at 00:00 Amsterdam time")
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                for (q in model.daily) {
                    QuestCard(q, model.dailyClaimed.contains(q.id), "d-" + q.id, model.claiming) {
                        scope.launch { model.claim(app, q, "daily") }
                    }
                }
            }
            QuestSectionHeader("WEEKLY", Icons.Filled.CalendarMonth, "Resets Sunday end of week")
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                for (q in model.weekly) {
                    QuestCard(q, model.weeklyClaimed.contains(q.id), "w-" + q.id, model.claiming) {
                        scope.launch { model.claim(app, q, "weekly") }
                    }
                }
            }
            Text("Week runs until $weekEnd. Progress is derived from your real activity — no fake completions.",
                color = Vault.muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun QuestSectionHeader(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, note: String) {
    Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, null, tint = Vault.primary, modifier = Modifier.size(20.dp))
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = Vault.foreground)) { append("$title ") }
                    withStyle(SpanStyle(color = Vault.primary)) { append("QUESTS") }
                },
                fontSize = 20.sp, fontWeight = FontWeight.Black,
            )
        }
        Text(note, color = Vault.muted, fontSize = 12.sp)
    }
}

@Composable
private fun QuestCard(q: QuestState, isClaimed: Boolean, key: String, claiming: String?, onClaim: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier.fillMaxWidth()
            .background(if (q.def.bonus) Vault.primary.copy(alpha = 0.05f) else Vault.card.copy(alpha = 0.6f), shape)
            .border(1.dp, if (q.def.bonus) Vault.primary.copy(alpha = 0.4f) else Vault.border, shape)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(if (q.def.bonus) Icons.Filled.AutoAwesome else Icons.Filled.Flag, null, tint = Vault.primary, modifier = Modifier.size(18.dp))
            Text(q.def.name, color = Vault.foreground, fontSize = 17.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
            if (isClaimed) {
                Badge("Claimed", icon = Icons.Filled.Check, fg = Vault.emerald, bg = Vault.emerald.copy(alpha = 0.2f), ring = Vault.emerald.copy(alpha = 0.4f))
            } else if (q.def.bonus) {
                Badge("BONUS", fg = Vault.primary, bg = Vault.primary.copy(alpha = 0.2f), ring = Vault.primary.copy(alpha = 0.4f))
            }
        }
        Text(q.def.desc, color = Vault.muted, fontSize = 14.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${q.value} / ${q.def.total}", color = Vault.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.Star, null, tint = Vault.primary, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(4.dp))
            Text("${q.def.reward} XP", color = Vault.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        ProgressBar(q.percent / 100f)
        val label = when {
            claiming == key -> "Claiming..."
            isClaimed -> "Claimed"
            q.completed -> "Claim Reward"
            else -> "In Progress"
        }
        PrimaryButton(
            label,
            onClick = onClaim,
            modifier = Modifier.fillMaxWidth(),
            enabled = q.completed && !isClaimed && claiming == null,
        )
    }
}
