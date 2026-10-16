package nl.lrvdlinden.mangavault.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.EuroSymbol
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import nl.lrvdlinden.mangavault.app.AppState
import nl.lrvdlinden.mangavault.app.LocalApp
import nl.lrvdlinden.mangavault.app.MainTab
import nl.lrvdlinden.mangavault.app.Route
import nl.lrvdlinden.mangavault.core.Base44
import nl.lrvdlinden.mangavault.core.Engines
import nl.lrvdlinden.mangavault.core.ProfileStore
import nl.lrvdlinden.mangavault.core.VaultService
import nl.lrvdlinden.mangavault.model.AchievementDef
import nl.lrvdlinden.mangavault.model.CollectionItem
import nl.lrvdlinden.mangavault.model.DashboardSection
import nl.lrvdlinden.mangavault.model.DayKey
import nl.lrvdlinden.mangavault.model.LevelInfo
import nl.lrvdlinden.mangavault.model.QuestDef
import nl.lrvdlinden.mangavault.model.QuestState
import nl.lrvdlinden.mangavault.model.ReadStatus
import nl.lrvdlinden.mangavault.model.TradingCard
import nl.lrvdlinden.mangavault.model.UnlockedAchievement
import nl.lrvdlinden.mangavault.model.UserCard
import nl.lrvdlinden.mangavault.model.UserProfile
import nl.lrvdlinden.mangavault.model.XP
import nl.lrvdlinden.mangavault.model.euro
import nl.lrvdlinden.mangavault.model.nonEmpty
import nl.lrvdlinden.mangavault.ui.components.CardSize
import nl.lrvdlinden.mangavault.ui.components.CoverInfo
import nl.lrvdlinden.mangavault.ui.components.CoverView
import nl.lrvdlinden.mangavault.ui.components.EventBannerView
import nl.lrvdlinden.mangavault.ui.components.OutlineButton
import nl.lrvdlinden.mangavault.ui.components.PrimaryButton
import nl.lrvdlinden.mangavault.ui.components.ProgressBar
import nl.lrvdlinden.mangavault.ui.components.SectionCard
import nl.lrvdlinden.mangavault.ui.components.StatCard
import nl.lrvdlinden.mangavault.ui.components.TradingCardView
import nl.lrvdlinden.mangavault.ui.components.cardStyle
import nl.lrvdlinden.mangavault.ui.theme.Vault
import java.util.Locale
import java.util.TreeMap
import kotlin.coroutines.cancellation.CancellationException

// MARK: Model

/** A collected card paired with its definition (dashboard "Recent Trading Cards"). */
private data class DashRecentCard(val owned: UserCard, val card: TradingCard)

private data class DashDatum(val name: String, val value: Int, val key: String = "")

private fun dashXpBase(items: List<CollectionItem>, genres: Int): Int {
    val completed = items.count { it.effectiveStatus == ReadStatus.COMPLETED }
    val rated = items.count { (it.rating ?: 0.0) > 0 }
    val favs = items.count { it.isFavorite }
    val notes = items.count { it.hasNotes }
    return items.size * 10 + completed * 30 + rated * 15 + favs * 5 + notes * 8 + maxOf(0, genres) * 20
}

private class DashModel {
    var items by mutableStateOf<List<CollectionItem>>(emptyList())
    var genreMap by mutableStateOf<Map<String, String>>(emptyMap())
    var profile by mutableStateOf<UserProfile?>(null)
    var streak by mutableStateOf(0)
    var level by mutableStateOf(XP.level(0))
    var loading by mutableStateOf(true)
    var layout by mutableStateOf(DashboardSection.defaults)
    var dailyQuests by mutableStateOf<List<QuestState>>(emptyList())
    var claimedQuests by mutableStateOf<Set<String>>(emptySet())
    var bumping by mutableStateOf<String?>(null)
    var cardCount by mutableStateOf<Int?>(null)
    var capsuleCount by mutableStateOf<Int?>(null)
    var recentCards by mutableStateOf<List<DashRecentCard>?>(null)
    var serverAchievements by mutableStateOf<List<UnlockedAchievement>?>(null)

    val unlockedAchievements: List<UnlockedAchievement>
        get() = serverAchievements ?: profile?.unlocked_achievements ?: emptyList()

    val reading: List<CollectionItem>
        get() = items.filter { it.effectiveStatus == ReadStatus.READING }
            .sortedByDescending { it.last_read_date ?: "" }
            .take(6)

    suspend fun load(app: AppState) {
        loading = true
        try {
            val (its, acts) = coroutineScope {
                val a = async { VaultService.collection() }
                val b = async { VaultService.activities() }
                a.await() to b.await()
            }
            val gm = VaultService.genreMap(its)
            items = its
            genreMap = gm
            streak = VaultService.streak(acts)
            val uid = app.user?.id
            if (uid != null) {
                ProfileStore.invalidate()
                profile = ProfileStore.current(uid)
            }
            layout = DashboardSection.normalized(profile?.dashboard_layout)

            val today = DayKey.local()
            val dc = mutableMapOf<String, Int>()
            acts.filter { it.day == today }.forEach { val k = it.type ?: ""; dc[k] = (dc[k] ?: 0) + 1 }
            val local = QuestDef.all.map { QuestState(it, minOf(dc[it.key] ?: 0, it.total)) }
            dailyQuests = local

            val st = try { Engines.rewardsState() } catch (e: CancellationException) { throw e } catch (e: Exception) { null }
            if (st != null) {
                level = st.level.info
                serverAchievements = st.achievements
                val server = st.quests ?: emptyList()
                if (server.isNotEmpty()) {
                    dailyQuests = local.map { q ->
                        val s = server.firstOrNull { it.id == q.def.id }
                        if (s == null) q else QuestState(q.def, minOf(s.value, q.def.total), s.completed)
                    }
                }
                claimedQuests = server.filter { it.claimed == true }.map { it.id }.toSet()
                val fresh = st.fresh ?: emptyList()
                for (f in fresh) app.show("Unlocked: ${f.label ?: ""}", if (f.xp > 0) "+${f.xp} XP" else "Open it in Vault Capsules")
                if (fresh.isNotEmpty()) app.celebrate()
            } else {
                level = XP.level(dashXpBase(its, VaultService.genreCount(its, gm)) + (profile?.xp_bonus ?: 0.0).toInt())
            }
            loadVaultCounts(app)
        } catch (e: CancellationException) {
            loading = false
            throw e
        } catch (e: Exception) {
            app.fail("Could not load dashboard", e)
        }
        loading = false
    }

    private suspend fun <T> quiet(block: suspend () -> T): T? =
        try { block() } catch (e: CancellationException) { throw e } catch (e: Exception) { null }

    private suspend fun loadVaultCounts(app: AppState) {
        cardCount = quiet { Base44.count("UserCard") } ?: 0
        val uid = app.user?.id
        if (uid != null) {
            capsuleCount = quiet { Base44.count("VaultCapsule", mapOf("user_id" to uid, "opened" to false)) } ?: 0
        }
        val mine = quiet { Engines.myCards(limit = 6, sort = "-created_date") } ?: emptyList()
        if (mine.isEmpty()) { recentCards = emptyList(); return }
        val defs = quiet { Engines.cards(mine.map { it.card_id }.distinct()) } ?: emptyList()
        recentCards = mine.mapNotNull { u -> defs.firstOrNull { it.card_id == u.card_id }?.let { DashRecentCard(u, it) } }
    }

    /** "+1 chapter" quick action (web: Continue Reading card). */
    suspend fun bumpChapter(app: AppState, i: CollectionItem) {
        bumping = i.id
        val next = i.chapter + 1
        val today = DayKey.local()
        try {
            Base44.update("CollectionItem", i.id, mapOf("current_chapter" to next, "last_read_date" to today))
            items = items.map { if (it.id == i.id) it.copy(current_chapter = next.toDouble(), last_read_date = today) else it }
            VaultService.log("progress", i.manga_id, i.title, i.volume, "ch.$next")
            dailyQuests = dailyQuests.map { if (it.def.key == "progress") QuestState(it.def, it.def.total) else it }
            app.show("Progress saved: chapter $next")
        } catch (e: Exception) {
            app.fail("Could not save progress", e)
        } finally {
            bumping = null
        }
    }

    suspend fun saveLayout(app: AppState, new: List<DashboardSection>) {
        layout = new
        val p = profile ?: return
        try {
            Base44.update("UserProfile", p.id, mapOf("dashboard_layout" to new.map { mapOf("id" to it.id, "hidden" to it.hidden) }))
            ProfileStore.invalidate()
        } catch (e: Exception) {
            app.fail("Could not save layout", e)
        }
    }
}

private fun dashStatusData(items: List<CollectionItem>): List<DashDatum> = listOf(
    DashDatum("Reading", items.count { it.effectiveStatus == ReadStatus.READING }, "reading"),
    DashDatum("Completed", items.count { it.effectiveStatus == ReadStatus.COMPLETED }, "completed"),
    DashDatum("Plan", items.count { it.effectiveStatus == ReadStatus.PLAN_TO_READ }, "plan_to_read"),
    DashDatum("On Hold", items.count { it.effectiveStatus == ReadStatus.ON_HOLD }, "on_hold"),
)

private fun dashGrowthData(items: List<CollectionItem>): List<DashDatum> {
    val perMonth = TreeMap<String, Int>()
    for (i in items) {
        val m = (i.created_date ?: "").take(7)
        if (m.isNotEmpty()) perMonth[m] = (perMonth[m] ?: 0) + 1
    }
    var total = 0
    return perMonth.entries.map { (k, v) -> total += v; DashDatum(k, total) }
}

private fun dashRatingData(items: List<CollectionItem>): List<DashDatum> {
    val b = IntArray(5)
    for (i in items) {
        when (i.ratingValue) {
            in 1..2 -> b[0]++
            in 3..4 -> b[1]++
            in 5..6 -> b[2]++
            in 7..8 -> b[3]++
            in 9..10 -> b[4]++
        }
    }
    return listOf("1-2", "3-4", "5-6", "7-8", "9-10").mapIndexed { idx, n -> DashDatum(n, b[idx]) }
}

private fun dashStatusColor(key: String): Color = Vault.statusColor(ReadStatus.of(key) ?: ReadStatus.READING)

private fun dashCapitalized(s: String): String =
    s.split(" ").joinToString(" ") { w -> w.replaceFirstChar { it.titlecase(Locale.US) } }

// MARK: Screen

@Composable
fun DashboardScreen() {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    val model = remember { DashModel() }
    var showCustomize by remember { mutableStateOf(false) }

    LaunchedEffect(app.dataVersion) { model.load(app) }

    val items = model.items
    val name = nonEmpty(model.profile?.display_name) ?: app.displayName
    val level = model.level
    val reading = model.reading

    Column(
        Modifier.fillMaxSize().background(Vault.background).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        // Header
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Filled.AutoAwesome, null, tint = Vault.primary, modifier = Modifier.size(14.dp))
                    Text("DASHBOARD", color = Vault.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                }
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = Vault.primary)) { append("Welcome back, ") }
                        withStyle(SpanStyle(color = Vault.foreground)) { append(name) }
                    },
                    fontSize = 30.sp, fontWeight = FontWeight.Black, letterSpacing = (-1).sp, lineHeight = 32.sp,
                )
                Text("Level ${level.level} · ${level.total} XP · ${model.streak}-day streak", color = Vault.muted, fontSize = 14.sp)
            }
            IconButton(onClick = { showCustomize = true }) {
                Icon(Icons.Filled.Tune, "Customize dashboard", tint = Vault.foreground)
            }
        }

        DashLevelHero(
            level, reading.firstOrNull(),
            onAchievements = { app.open(Route.Achievements) },
            onCollection = { app.selectTab(MainTab.COLLECTION) },
            onContinue = { first ->
                val mid = first?.manga_id
                if (first != null && mid != null) app.open(Route.Volume(mid, first.volume ?: 1)) else app.selectTab(MainTab.COLLECTION)
            },
        )

        EventBannerView()

        if (model.loading && items.isEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                repeat(4) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        repeat(2) {
                            Box(Modifier.weight(1f).height(84.dp).clip(RoundedCornerShape(14.dp)).background(Vault.card.copy(alpha = 0.5f)))
                        }
                    }
                }
            }
        } else {
            DashStatGrid(items)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DashVaultLink(Icons.Filled.Style, "Trading Cards", model.cardCount?.toString() ?: "…", Modifier.weight(1f)) { app.open(Route.Cards) }
                DashVaultLink(Icons.Filled.EmojiEvents, "Achievements", "${model.unlockedAchievements.size}", Modifier.weight(1f)) { app.open(Route.Achievements) }
                DashVaultLink(Icons.Filled.Inventory2, "Sealed Capsules", model.capsuleCount?.toString() ?: "…", Modifier.weight(1f)) { app.open(Route.Capsules) }
            }
            for (section in model.layout.filter { !it.hidden }) {
                key(section.id) {
                    when (section.id) {
                        "continue" -> DashContinueReading(model, reading,
                            onBump = { i -> scope.launch { model.bumpChapter(app, i) } })
                        "quests" -> DashQuests(model)
                        "achievements" -> DashRecentAchievements(model.unlockedAchievements)
                        "cards" -> DashRecentCards(model.recentCards)
                        "recent" -> DashRecentlyAdded(items)
                        "charts" -> DashCharts(items, model.genreMap)
                        else -> {}
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlineButton("Full Statistics", onClick = { app.open(Route.Statistics) }, modifier = Modifier.weight(1f),
                    icon = Icons.Filled.BarChart, color = Vault.primary, borderColor = Vault.primary.copy(alpha = 0.4f))
                PrimaryButton("Rewards", onClick = { app.open(Route.Rewards) }, modifier = Modifier.weight(1f), icon = Icons.Filled.CardGiftcard)
            }
        }
    }

    if (showCustomize) {
        DashCustomizeSheet(
            initial = model.layout,
            onChange = { new -> scope.launch { model.saveLayout(app, new) } },
            onDismiss = { showCustomize = false },
        )
    }
}

@Composable
private fun DashLevelHero(
    level: LevelInfo,
    first: CollectionItem?,
    onAchievements: () -> Unit,
    onCollection: () -> Unit,
    onContinue: (CollectionItem?) -> Unit,
) {
    Column(Modifier.fillMaxWidth().cardStyle(highlighted = true).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.fillMaxWidth().clickable(onClick = onAchievements), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Level ${level.level}", color = Vault.primary, fontSize = 17.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                Text("${level.into} / ${level.span} XP to level ${level.level + 1}", color = Vault.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            ProgressBar(level.progress)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton("My Collection", onClick = onCollection, modifier = Modifier.weight(1f), icon = Icons.AutoMirrored.Filled.LibraryBooks)
            OutlineButton("Continue Reading", onClick = { onContinue(first) }, modifier = Modifier.weight(1f), icon = Icons.AutoMirrored.Filled.MenuBook)
        }
    }
}

@Composable
private fun DashStatGrid(items: List<CollectionItem>) {
    fun count(s: ReadStatus) = items.count { it.effectiveStatus == s }
    val rated = items.filter { (it.rating ?: 0.0) > 0 }
    val avg = if (rated.isEmpty()) "—" else String.format(Locale.US, "%.1f", rated.sumOf { it.rating ?: 0.0 } / rated.size)
    val cards: List<Triple<ImageVector, String, Pair<String, Color>>> = listOf(
        Triple(Icons.AutoMirrored.Filled.LibraryBooks, "Volumes", "${items.size}" to Vault.primary),
        Triple(Icons.AutoMirrored.Filled.MenuBook, "Reading", "${count(ReadStatus.READING)}" to Vault.primary),
        Triple(Icons.Filled.CheckCircle, "Completed", "${count(ReadStatus.COMPLETED)}" to Vault.emerald),
        Triple(Icons.Filled.Bookmark, "Plan to Read", "${count(ReadStatus.PLAN_TO_READ)}" to Vault.sky),
        Triple(Icons.Filled.Pause, "On Hold", "${count(ReadStatus.ON_HOLD)}" to Vault.orange),
        Triple(Icons.Filled.Favorite, "Favorites", "${items.count { it.isFavorite }}" to Vault.red),
        Triple(Icons.Filled.EuroSymbol, "Total Value", items.sumOf { it.price ?: 0.0 }.euro() to Vault.primary),
        Triple(Icons.Filled.Star, "Avg Rating", avg to Vault.orange),
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (row in cards.chunked(2)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (c in row) StatCard(c.first, c.second, c.third.first, Modifier.weight(1f), accent = c.third.second)
            }
        }
    }
}

@Composable
private fun DashVaultLink(icon: ImageVector, label: String, value: String, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.cardStyle().clickable(onClick = onClick).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, null, tint = Vault.muted, modifier = Modifier.size(12.dp))
            Text(label, color = Vault.muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(value, color = Vault.foreground, fontSize = 20.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun DashSectionTitle(title: String, icon: ImageVector, link: Pair<String, Route>? = null) {
    val app = LocalApp.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Vault.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(title, color = Vault.foreground, fontSize = 20.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
        if (link != null) {
            Text(link.first, color = Vault.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { app.open(link.second) }.padding(4.dp))
        }
    }
}

@Composable
private fun DashContinueReading(model: DashModel, reading: List<CollectionItem>, onBump: (CollectionItem) -> Unit) {
    val app = LocalApp.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DashSectionTitle("CONTINUE READING", Icons.Filled.LocalFireDepartment)
        if (reading.isEmpty()) {
            Column(Modifier.fillMaxWidth().cardStyle().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("You're not reading anything right now. Set a volume to \"Reading\" in your collection.",
                    color = Vault.muted, fontSize = 14.sp, textAlign = TextAlign.Center)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PrimaryButton("My Collection", onClick = { app.selectTab(MainTab.COLLECTION) })
                    OutlineButton("Browse catalog", onClick = { app.selectTab(MainTab.CATALOG) })
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                for (i in reading) DashReadingRow(i, model.bumping == i.id, onBump = { onBump(i) })
            }
        }
    }
}

@Composable
private fun DashReadingRow(i: CollectionItem, bumping: Boolean, onBump: () -> Unit) {
    val app = LocalApp.current
    Row(Modifier.fillMaxWidth().cardStyle().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        CoverView(CoverInfo.of(i), Modifier.width(56.dp).clickable { app.open(Route.Manga(i.manga_id ?: "")) }, cornerRadius = 6.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(i.titleText, color = Vault.foreground, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Vol. ${i.volume?.toString() ?: "—"} · Reading", color = Vault.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            val chapter = if (i.chapter > 0) "Chapter ${i.chapter}" else "No chapter logged yet"
            val d = nonEmpty(i.last_read_date)
            Text(if (d == null) chapter else "$chapter · $d", color = Vault.muted, fontSize = 11.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val shape = RoundedCornerShape(8.dp)
                Row(
                    Modifier.clip(shape).background(Vault.primary, shape).clickable(enabled = !bumping, onClick = onBump)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (bumping) CircularProgressIndicator(Modifier.size(12.dp), color = Color.White, strokeWidth = 2.dp)
                    else Icon(Icons.Filled.Add, null, tint = Color.White, modifier = Modifier.size(13.dp))
                    Text("1 chapter", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Text(
                    "Open", color = Vault.foreground, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(shape).border(1.dp, Vault.border, shape)
                        .clickable { app.open(Route.Volume(i.manga_id ?: "", i.volume ?: 1)) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun DashQuests(model: DashModel) {
    val app = LocalApp.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DashSectionTitle("DAILY QUESTS", Icons.Filled.Flag, "Claim rewards" to Route.Quests)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (q in model.dailyQuests) {
                Column(Modifier.fillMaxWidth().cardStyle().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(q.def.name, color = Vault.foreground, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Icon(Icons.Filled.Star, null, tint = Vault.primary, modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(3.dp))
                        Text("${q.def.reward} XP", color = Vault.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    ProgressBar(q.value.toFloat() / maxOf(q.def.total, 1), height = 6.dp)
                    Row {
                        Spacer(Modifier.weight(1f))
                        when {
                            model.claimedQuests.contains(q.id) -> Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Check, null, tint = Vault.emerald, modifier = Modifier.size(11.dp))
                                Spacer(Modifier.width(3.dp))
                                Text("Claimed", color = Vault.emerald, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            q.completed -> Text("Ready to claim →", color = Vault.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable { app.open(Route.Quests) })
                            else -> Text("${q.value}/${q.def.total}", color = Vault.muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DashRecentAchievements(unlockedAll: List<UnlockedAchievement>) {
    val unlocked = unlockedAll.sortedByDescending { it.date ?: "" }
    val rows = mutableListOf<Pair<AchievementDef, String>>()
    for (u in unlocked) {
        if (rows.size >= 4) break
        AchievementDef.all.firstOrNull { it.id == u.id }?.let { rows += it to (u.date ?: "").take(10) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DashSectionTitle("RECENT ACHIEVEMENTS", Icons.Filled.EmojiEvents, "View all" to Route.Achievements)
        if (rows.isEmpty()) {
            Text("No achievements unlocked yet. Visit Achievements to check your progress.", color = Vault.muted, fontSize = 14.sp,
                modifier = Modifier.fillMaxWidth().cardStyle().padding(16.dp))
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                for (pair in rows.chunked(2)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.height(IntrinsicSize.Min)) {
                        for ((def, date) in pair) {
                            Column(Modifier.weight(1f).fillMaxHeight().cardStyle().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(def.rarity.label.uppercase(), color = Vault.primary, fontSize = 10.sp, fontWeight = FontWeight.Black)
                                Text(def.name, color = Vault.foreground, fontSize = 14.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(def.desc, color = Vault.muted, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text("$date · +${def.reward} XP", color = Vault.muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun DashRecentCards(cards: List<DashRecentCard>?) {
    val app = LocalApp.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DashSectionTitle("RECENT TRADING CARDS", Icons.Filled.Style, "View all" to Route.Cards)
        if (cards == null) {
            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Vault.primary)
            }
        } else if (cards.isEmpty()) {
            Text("No cards yet. Claim your starter pack on the Trading Cards page.", color = Vault.muted, fontSize = 14.sp,
                modifier = Modifier.fillMaxWidth().cardStyle().padding(16.dp))
        } else {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for (pair in cards) {
                    TradingCardView(pair.card, owned = true, count = pair.owned.count, size = CardSize.SM, onClick = { app.open(Route.Cards) })
                }
            }
        }
    }
}

@Composable
private fun DashRecentlyAdded(items: List<CollectionItem>) {
    val app = LocalApp.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DashSectionTitle("RECENTLY ADDED", Icons.Filled.AutoAwesome)
        if (items.isEmpty()) {
            Box(Modifier.fillMaxWidth().cardStyle().padding(24.dp), contentAlignment = Alignment.Center) {
                Text("Your collection is empty.", color = Vault.muted, fontSize = 14.sp)
            }
        } else {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                for (i in items.take(6)) {
                    Column(Modifier.width(110.dp).clickable { app.open(Route.Manga(i.manga_id ?: "")) }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        CoverView(CoverInfo.of(i), Modifier.fillMaxWidth())
                        Text(i.titleText, color = Vault.foreground, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DashCharts(items: List<CollectionItem>, genreMap: Map<String, String>) {
    val app = LocalApp.current
    val status = dashStatusData(items).filter { it.value > 0 }
    val genres = VaultService.genreDistribution(items, genreMap, 8).map { DashDatum(it.first, it.second) }
    val growth = dashGrowthData(items)
    val viewAll: @Composable () -> Unit = {
        Text("View all", color = Vault.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable { app.open(Route.Statistics) })
    }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionCard("Status Distribution", trailing = viewAll) {
            if (status.isEmpty()) DashEmptyChart()
            else {
                DashDonut(status, Modifier.fillMaxWidth().height(190.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (d in status) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            Box(Modifier.size(9.dp).background(dashStatusColor(d.key), CircleShape))
                            Text("${d.name} ${d.value}", color = Vault.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        SectionCard("Top Genres", trailing = viewAll) {
            if (genres.isEmpty()) DashEmptyChart() else DashGenreBars(genres)
        }
        SectionCard("Collection Growth", trailing = viewAll) {
            if (growth.isEmpty()) DashEmptyChart() else DashGrowthChart(growth, 190.dp)
        }
        SectionCard("Rating Distribution", trailing = viewAll) {
            DashBarChart(dashRatingData(items), 180.dp)
        }
    }
}

@Composable
private fun DashEmptyChart() {
    Box(Modifier.fillMaxWidth().heightIn(min = 120.dp), contentAlignment = Alignment.Center) {
        Text("No data yet.", color = Vault.muted, fontSize = 14.sp)
    }
}

// MARK: Charts (Canvas / Boxes)

@Composable
private fun DashDonut(data: List<DashDatum>, modifier: Modifier) {
    val total = data.sumOf { it.value }.coerceAtLeast(1)
    Canvas(modifier) {
        val d = minOf(size.width, size.height)
        val strokeW = d * 0.45f / 2f
        val inset = strokeW / 2f
        val topLeft = Offset((size.width - d) / 2f + inset, (size.height - d) / 2f + inset)
        val arcSize = Size(d - strokeW, d - strokeW)
        var start = -90f
        val gap = if (data.size > 1) 2f else 0f
        for (item in data) {
            val sweep = 360f * item.value / total
            drawArc(
                color = dashStatusColor(item.key),
                startAngle = start + gap / 2f,
                sweepAngle = (sweep - gap).coerceAtLeast(0.5f),
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeW, cap = StrokeCap.Butt),
            )
            start += sweep
        }
    }
}

@Composable
private fun DashGenreBars(data: List<DashDatum>) {
    val maxV = maxOf(1, data.maxOfOrNull { it.value } ?: 1)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (d in data) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(dashCapitalized(d.name), color = Vault.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(96.dp))
                Box(Modifier.weight(1f).height(20.dp)) {
                    Box(Modifier.fillMaxWidth(maxOf(0.02f, d.value.toFloat() / maxV)).fillMaxHeight()
                        .clip(RoundedCornerShape(4.dp)).background(Vault.primary))
                }
                Text("${d.value}", color = Vault.foreground, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun DashGrowthChart(data: List<DashDatum>, height: Dp) {
    val maxV = maxOf(1, data.maxOfOrNull { it.value } ?: 1)
    val primary = Vault.primary
    val grid = Vault.border
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth().height(height)) {
            Column(Modifier.width(30.dp).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                Text("$maxV", color = Vault.muted, fontSize = 10.sp, maxLines = 1)
                Text("${maxV / 2}", color = Vault.muted, fontSize = 10.sp, maxLines = 1)
                Text("0", color = Vault.muted, fontSize = 10.sp, maxLines = 1)
            }
            Canvas(Modifier.weight(1f).fillMaxHeight().padding(vertical = 6.dp)) {
                val w = size.width
                val h = size.height
                for (i in 0..2) {
                    val y = h * i / 2f
                    drawLine(grid, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
                }
                if (data.isEmpty()) return@Canvas
                val n = data.size
                fun px(i: Int): Float = if (n == 1) w / 2f else w * i / (n - 1).toFloat()
                fun py(v: Int): Float = h - h * v.toFloat() / maxV.toFloat()
                val line = Path()
                val fill = Path()
                data.forEachIndexed { i, d ->
                    if (i == 0) { line.moveTo(px(i), py(d.value)); fill.moveTo(px(i), py(d.value)) }
                    else { line.lineTo(px(i), py(d.value)); fill.lineTo(px(i), py(d.value)) }
                }
                fill.lineTo(px(n - 1), h)
                fill.lineTo(px(0), h)
                fill.close()
                drawPath(fill, Brush.verticalGradient(listOf(primary.copy(alpha = 0.5f), primary.copy(alpha = 0f)), startY = 0f, endY = h))
                drawPath(line, primary, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                if (n == 1) drawCircle(primary, 3.dp.toPx(), Offset(px(0), py(data[0].value)))
            }
        }
        val idx = if (data.size <= 4) data.indices.toList() else (0 until 4).map { it * (data.size - 1) / 3 }.distinct()
        Row(Modifier.fillMaxWidth().padding(start = 30.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            for (i in idx) Text(data[i].name, color = Vault.muted, fontSize = 10.sp, maxLines = 1)
        }
    }
}

@Composable
private fun DashBarChart(data: List<DashDatum>, height: Dp) {
    val maxV = maxOf(1, data.maxOfOrNull { it.value } ?: 1)
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
            for (d in data) {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${d.value}", color = Vault.muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Box(Modifier.fillMaxWidth().height(height - 40.dp), contentAlignment = Alignment.BottomCenter) {
                        if (d.value > 0) {
                            Box(Modifier.fillMaxWidth().fillMaxHeight(d.value.toFloat() / maxV)
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)).background(Vault.primary))
                        }
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Vault.border))
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            for (d in data) {
                Text(d.name, color = Vault.muted, fontSize = 10.sp, maxLines = 1, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
    }
}

// MARK: Customize sheet (reorder / hide dashboard sections)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashCustomizeSheet(
    initial: List<DashboardSection>,
    onChange: (List<DashboardSection>) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    var layout by remember { mutableStateOf(initial) }

    fun move(i: Int, d: Int) {
        val j = i + d
        if (j < 0 || j >= layout.size) return
        val m = layout.toMutableList()
        val t = m[i]; m[i] = m[j]; m[j] = t
        layout = m
        onChange(layout)
    }

    fun toggle(i: Int) {
        layout = layout.mapIndexed { idx, s -> if (idx == i) s.copy(hidden = !s.hidden) else s }
        onChange(layout)
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = Vault.background, contentColor = Vault.foreground) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Customize Dashboard", color = Vault.foreground, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("Done", color = Vault.primary, fontWeight = FontWeight.Bold) }
            }
            Column(Modifier.fillMaxWidth().cardStyle()) {
                layout.forEachIndexed { idx, s ->
                    Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 2.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(s.label, color = if (s.hidden) Vault.muted else Vault.foreground, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        IconButton(onClick = { move(idx, -1) }, enabled = idx > 0) {
                            Icon(Icons.Filled.KeyboardArrowUp, "Move up", tint = if (idx > 0) Vault.primary else Vault.muted.copy(alpha = 0.4f))
                        }
                        IconButton(onClick = { move(idx, 1) }, enabled = idx < layout.size - 1) {
                            Icon(Icons.Filled.KeyboardArrowDown, "Move down",
                                tint = if (idx < layout.size - 1) Vault.primary else Vault.muted.copy(alpha = 0.4f))
                        }
                        IconButton(onClick = { toggle(idx) }) {
                            Icon(if (s.hidden) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, if (s.hidden) "Show" else "Hide",
                                tint = Vault.primary)
                        }
                    }
                    if (idx < layout.size - 1) Box(Modifier.fillMaxWidth().padding(start = 14.dp).height(1.dp).background(Vault.border))
                }
            }
            Row(
                Modifier.fillMaxWidth().cardStyle().clickable {
                    layout = DashboardSection.defaults
                    onChange(layout)
                }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Filled.Refresh, null, tint = Vault.primary, modifier = Modifier.size(18.dp))
                Text("Restore default", color = Vault.primary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
