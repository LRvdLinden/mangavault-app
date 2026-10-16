package nl.lrvdlinden.mangavault.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import nl.lrvdlinden.mangavault.app.LocalApp
import nl.lrvdlinden.mangavault.app.Route
import nl.lrvdlinden.mangavault.core.Base44
import nl.lrvdlinden.mangavault.model.CollectionItem
import nl.lrvdlinden.mangavault.model.Manga
import nl.lrvdlinden.mangavault.model.ReadStatus
import nl.lrvdlinden.mangavault.model.WishlistItem
import nl.lrvdlinden.mangavault.model.euro
import nl.lrvdlinden.mangavault.model.nonEmpty
import nl.lrvdlinden.mangavault.ui.components.CoverInfo
import nl.lrvdlinden.mangavault.ui.components.CoverView
import nl.lrvdlinden.mangavault.ui.components.EmptyState
import nl.lrvdlinden.mangavault.ui.components.OutlineButton
import nl.lrvdlinden.mangavault.ui.components.ScreenHeader
import nl.lrvdlinden.mangavault.ui.components.SectionCard
import nl.lrvdlinden.mangavault.ui.components.cardStyle
import nl.lrvdlinden.mangavault.ui.theme.Vault
import java.util.Locale
import kotlin.math.roundToLong

private data class CompareStats(
    val owned: Int,
    val read: Int,
    val favorite: Boolean,
    val avgRating: Double?,
    val wishlist: Int,
)

private suspend fun compareStatsFor(id: String): CompareStats {
    val q = mapOf<String, Any?>("manga_id" to id)
    val items = Base44.list<CollectionItem>("CollectionItem", query = q, limit = 500).items
    val wish = Base44.list<WishlistItem>("WishlistItem", query = q, limit = 500).items
    val owned = items.count { !it.isOmnibus }
    val read = items.count { it.effectiveStatus == ReadStatus.COMPLETED }
    val fav = items.any { it.isFavorite }
    val rated = items.mapNotNull { it.rating }.filter { it > 0 }
    var avg: Double? = null
    if (rated.isNotEmpty()) {
        val raw = rated.sum() / rated.size
        avg = (raw * 10).roundToLong() / 10.0
    }
    return CompareStats(owned, read, fav, avg, wish.size)
}

/** Manga Comparison: two series side by side — public metadata and your personal stats. */
@Composable
fun CompareScreen() {
    val app = LocalApp.current
    var pickA by remember { mutableStateOf<Manga?>(null) }
    var pickB by remember { mutableStateOf<Manga?>(null) }
    var statsA by remember { mutableStateOf<CompareStats?>(null) }
    var statsB by remember { mutableStateOf<CompareStats?>(null) }
    var loadingStats by remember { mutableStateOf(false) }

    LaunchedEffect(pickA?.id, pickB?.id) {
        statsA = null
        statsB = null
        val a = pickA
        val b = pickB
        if (a == null || b == null) return@LaunchedEffect
        loadingStats = true
        try {
            val sa = compareStatsFor(a.id)
            val sb = compareStatsFor(b.id)
            statsA = sa
            statsB = sb
        } catch (e: Exception) {
            app.fail("Could not load your stats", e)
        }
        loadingStats = false
    }

    Column(
        Modifier.fillMaxSize().background(Vault.background).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        ScreenHeader(
            lead = "MANGA", accent = "COMPARISON",
            eyebrow = "Compare", eyebrowIcon = Icons.AutoMirrored.Filled.CompareArrows,
            subtitle = "Pick two series to compare side by side. Public metadata on top, your personal stats below.",
        )
        ComparePicker("Manga A", pickA) { pickA = it }
        ComparePicker("Manga B", pickB) { pickB = it }
        val a = pickA
        val b = pickB
        if (a != null && b != null) {
            ComparePublicTable(a, b)
            ComparePersonalTable(statsA, statsB, loadingStats)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlineButton("Open A", { app.open(Route.Manga(a.id)) }, Modifier.weight(1f), color = Vault.primary)
                OutlineButton("Open B", { app.open(Route.Manga(b.id)) }, Modifier.weight(1f), color = Vault.primary)
            }
        } else {
            EmptyState(Icons.AutoMirrored.Filled.CompareArrows, "Search and select two manga to compare them.")
        }
    }
}

@Composable
private fun CompareHeaderRow() {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("", modifier = Modifier.width(100.dp))
        Text("A", color = Vault.primary, fontSize = 12.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
        Text("B", color = Vault.primary, fontSize = 12.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun CompareRow(label: String, a: String, b: String) {
    Column {
        Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, color = Vault.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(100.dp))
            Text(a, color = Vault.foreground, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Text(b, color = Vault.foreground, fontSize = 12.sp, modifier = Modifier.weight(1f))
        }
        HorizontalDivider(color = Vault.border)
    }
}

private fun compareVolumes(m: Manga) = if (m.totalVolumes > 0) "${m.totalVolumes}" else "Unknown"

private fun compareLanguages(m: Manga): String {
    val l = m.languages ?: emptyList()
    return if (l.isEmpty()) "—" else l.joinToString(", ")
}

@Composable
private fun ComparePublicTable(a: Manga, b: Manga) {
    SectionCard(title = "Public metadata") {
        Column {
            CompareHeaderRow()
            CompareRow("Title", a.title, b.title)
            CompareRow("Publisher", nonEmpty(a.publisher) ?: "—", nonEmpty(b.publisher) ?: "—")
            CompareRow("Genres", nonEmpty(a.genre) ?: "—", nonEmpty(b.genre) ?: "—")
            CompareRow("Known Volumes", compareVolumes(a), compareVolumes(b))
            CompareRow("Edition", if (a.isOmnibus) "Omnibus" else "Standard", if (b.isOmnibus) "Omnibus" else "Standard")
            CompareRow("Price", a.priceValue.euro(), b.priceValue.euro())
            CompareRow("Languages", compareLanguages(a), compareLanguages(b))
        }
    }
}

private fun compareAvgText(s: CompareStats?): String {
    if (s == null) return "…"
    val avg = s.avgRating ?: return "Not rated"
    return String.format(Locale.US, "%.1f/10", avg)
}

@Composable
private fun ComparePersonalTable(sa: CompareStats?, sb: CompareStats?, loading: Boolean) {
    val dash = if (loading) "…" else "—"
    SectionCard(title = "Your personal stats") {
        Column {
            CompareHeaderRow()
            CompareRow("Owned Volumes", sa?.owned?.toString() ?: dash, sb?.owned?.toString() ?: dash)
            CompareRow("Read Volumes", sa?.read?.toString() ?: dash, sb?.read?.toString() ?: dash)
            CompareRow("On Wishlist", sa?.wishlist?.toString() ?: dash, sb?.wishlist?.toString() ?: dash)
            CompareRow("Your Avg Rating", compareAvgText(sa), compareAvgText(sb))
            CompareRow(
                "Favorite",
                sa?.let { if (it.favorite) "Yes" else "No" } ?: dash,
                sb?.let { if (it.favorite) "Yes" else "No" } ?: dash,
            )
        }
    }
}

// MARK: Picker

@Composable
private fun ComparePicker(label: String, selection: Manga?, onSelect: (Manga?) -> Unit) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Manga>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }

    LaunchedEffect(query) {
        val q = libTrim(query)
        if (q.isEmpty()) { results = emptyList(); return@LaunchedEffect }
        delay(250)
        searching = true
        val fields = Manga.listFields + listOf("description", "languages")
        val page = try {
            Base44.list<Manga>("Manga", query = mapOf("title" to libRegex(q)), sort = "title", limit = 12, fields = fields)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) { searching = false; throw e }
            null
        }
        results = page?.items ?: emptyList()
        searching = false
    }

    Column(
        Modifier.fillMaxWidth().cardStyle().padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        LibFieldLabel(label)
        if (selection != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CoverView(CoverInfo.of(selection), Modifier.width(52.dp), cornerRadius = 6.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(selection.title, color = Vault.foreground, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(nonEmpty(selection.genre) ?: "—", color = Vault.muted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                LibSmallButton(text = "Clear", color = Vault.red, onClick = {
                    onSelect(null)
                    query = ""
                    results = emptyList()
                })
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LibTextField(query, { query = it }, "Search manga...", Modifier.weight(1f))
                    if (searching) CircularProgressIndicator(Modifier.size(20.dp), color = Vault.primary, strokeWidth = 2.dp)
                }
                for (m in results) {
                    Row(
                        Modifier.fillMaxWidth()
                            .background(Vault.secondary.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            .clickable {
                                onSelect(m)
                                results = emptyList()
                            }
                            .padding(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        CoverView(CoverInfo.of(m), Modifier.width(30.dp), cornerRadius = 4.dp)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(m.title, color = Vault.foreground, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(nonEmpty(m.publisher) ?: "", color = Vault.muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}
