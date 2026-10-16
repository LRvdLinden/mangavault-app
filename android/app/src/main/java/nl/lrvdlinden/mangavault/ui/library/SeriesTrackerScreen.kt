package nl.lrvdlinden.mangavault.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import nl.lrvdlinden.mangavault.app.LocalApp
import nl.lrvdlinden.mangavault.app.Route
import nl.lrvdlinden.mangavault.core.Base44
import nl.lrvdlinden.mangavault.core.VaultService
import nl.lrvdlinden.mangavault.model.CollectionItem
import nl.lrvdlinden.mangavault.model.Manga
import nl.lrvdlinden.mangavault.model.ReadStatus
import nl.lrvdlinden.mangavault.model.nonEmpty
import nl.lrvdlinden.mangavault.ui.components.Badge
import nl.lrvdlinden.mangavault.ui.components.Chip
import nl.lrvdlinden.mangavault.ui.components.CoverInfo
import nl.lrvdlinden.mangavault.ui.components.CoverView
import nl.lrvdlinden.mangavault.ui.components.EmptyState
import nl.lrvdlinden.mangavault.ui.components.LoadingView
import nl.lrvdlinden.mangavault.ui.components.ProgressBar
import nl.lrvdlinden.mangavault.ui.components.ScreenHeader
import nl.lrvdlinden.mangavault.ui.components.StatCard
import nl.lrvdlinden.mangavault.ui.components.cardStyle
import nl.lrvdlinden.mangavault.ui.theme.Vault
import kotlin.math.roundToInt

private data class SeriesRow(
    val id: String,
    val title: String,
    val genre: String,
    val cover: CoverInfo,
    val owned: Int,
    val read: Int,
    val total: Int,
) {
    val pct: Double? get() = if (total > 0) minOf(100.0, owned.toDouble() / total * 100) else null
    val readPct: Double get() = if (owned > 0) minOf(100.0, read.toDouble() / owned * 100) else 0.0
    val allRead: Boolean get() = owned > 0 && read == owned
    val unread: Int get() = maxOf(0, owned - read)
}

private val seriesFilters = listOf("all" to "All", "reading" to "Reading", "incomplete" to "Incomplete", "complete" to "Complete")

private suspend fun seriesLoad(): List<SeriesRow> {
    val items = VaultService.collection(limit = 2000)
    val ids = items.mapNotNull { nonEmpty(it.manga_id) }.distinct()
    val mangas = mutableMapOf<String, Manga>()
    val fields = listOf("id", "title", "total_volumes", "genre", "cover_image_url", "cover_seed", "is_shonen_jump")
    for (chunk in ids.chunked(500)) {
        val page = Base44.list<Manga>("Manga", query = mapOf("id" to mapOf("\$in" to chunk)), limit = 500, fields = fields)
        for (m in page.items) mangas[m.id] = m
    }
    val groups = linkedMapOf<String, MutableList<CollectionItem>>()
    for (item in items) {
        val id = nonEmpty(item.manga_id) ?: continue
        groups.getOrPut(id) { mutableListOf() }.add(item)
    }
    val out = mutableListOf<SeriesRow>()
    for ((id, list) in groups) {
        val first = list.firstOrNull() ?: continue
        val m = mangas[id]
        val owned = list.count { !it.isOmnibus }
        val read = list.count { it.effectiveStatus == ReadStatus.COMPLETED }
        val cover = if (m != null) CoverInfo.of(m) else CoverInfo.of(first)
        out.add(
            SeriesRow(
                id = id,
                title = m?.title ?: first.titleText,
                genre = m?.genre ?: "",
                cover = cover,
                owned = owned,
                read = read,
                total = m?.totalVolumes ?: 0,
            ),
        )
    }
    return out.sortedByDescending { it.owned + it.read }
}

/** Series Tracker: completion per series you own, volume by volume. */
@Composable
fun SeriesTrackerScreen() {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    var series by remember { mutableStateOf<List<SeriesRow>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("all") }

    suspend fun load() {
        loading = true
        try {
            series = seriesLoad()
        } catch (e: Exception) {
            app.fail("Could not load series", e)
        }
        loading = false
    }

    LaunchedEffect(app.dataVersion) { load() }

    val q = libTrim(search).lowercase()
    val filtered = series.filter { s ->
        if (q.isNotEmpty() && !s.title.lowercase().contains(q)) false
        else when (filter) {
            "reading" -> s.read > 0
            "incomplete" -> !s.allRead
            "complete" -> s.allRead
            else -> true
        }
    }

    LibRefreshBox(
        refreshing = refreshing,
        onRefresh = { scope.launch { refreshing = true; load(); refreshing = false } },
        modifier = Modifier.fillMaxSize().background(Vault.background),
    ) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                ScreenHeader(
                    lead = "SERIES", accent = "COMPLETION",
                    eyebrow = "Series Tracker", eyebrowIcon = Icons.AutoMirrored.Filled.LibraryBooks,
                    subtitle = "Track your progress through every series you own, volume by volume.",
                )
            }
            item {
                val owned = series.sumOf { it.owned }
                val read = series.sumOf { it.read }
                val complete = series.count { it.allRead }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatCard(Icons.AutoMirrored.Filled.LibraryBooks, "Series", "${series.size}", Modifier.weight(1f))
                        StatCard(Icons.AutoMirrored.Filled.MenuBook, "Owned Volumes", "$owned", Modifier.weight(1f), accent = Vault.sky)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatCard(Icons.Filled.CheckCircle, "Read Volumes", "$read", Modifier.weight(1f), accent = Vault.emerald)
                        StatCard(Icons.Filled.EmojiEvents, "Completed Series", "$complete", Modifier.weight(1f), accent = Vault.amber)
                    }
                }
            }
            item {
                LibTextField(search, { search = it }, "Search series...", Modifier.fillMaxWidth())
            }
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for ((key, label) in seriesFilters) {
                        Chip(label, filter == key) { filter = key }
                    }
                }
            }
            if (loading && series.isEmpty()) {
                item { LoadingView() }
            } else if (filtered.isEmpty()) {
                item {
                    EmptyState(
                        Icons.AutoMirrored.Filled.LibraryBooks,
                        if (series.isEmpty()) "Add manga to your collection to start tracking series." else "No series match your filters.",
                    )
                }
            } else {
                items(filtered, key = { it.id }) { s ->
                    SeriesRowCard(s) { app.open(Route.Manga(s.id)) }
                }
            }
        }
    }
}

@Composable
private fun SeriesRowCard(s: SeriesRow, onOpen: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().cardStyle(highlighted = s.allRead).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CoverView(s.cover, Modifier.width(64.dp).clickable(onClick = onOpen), cornerRadius = 6.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // Header
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        s.title, color = Vault.foreground, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable(onClick = onOpen),
                    )
                    if (s.genre.isNotEmpty()) {
                        Text(s.genre, color = Vault.muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (s.allRead) Badge("SERIES COMPLETE", icon = Icons.Filled.CheckCircle, bg = Vault.emerald)
                }
                LibSmallButton(text = "Open", color = Vault.primary, onClick = onOpen)
            }
            // Counts
            val totalText = if (s.total > 0) "${s.total}" else "?"
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Owned: ${s.owned} / $totalText", color = Vault.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text("Read: ${s.read}", color = Vault.emerald, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text("Unread: ${s.unread}", color = Vault.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            // Bars
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                val pct = s.pct
                if (pct != null) {
                    Text("Collection ${pct.roundToInt()}%", color = Vault.muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    ProgressBar((pct / 100).toFloat(), height = 6.dp)
                } else {
                    Text("Owned volumes", color = Vault.muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    ProgressBar(1f, height = 6.dp, color = Vault.sky)
                }
                Text("Read ${s.readPct.roundToInt()}%", color = Vault.muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                ProgressBar((s.readPct / 100).toFloat(), height = 6.dp, color = Vault.emerald)
            }
        }
    }
}
