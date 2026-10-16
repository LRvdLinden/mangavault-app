package nl.lrvdlinden.mangavault.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.ThumbDown
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import nl.lrvdlinden.mangavault.app.LocalApp
import nl.lrvdlinden.mangavault.app.Route
import nl.lrvdlinden.mangavault.core.Base44
import nl.lrvdlinden.mangavault.core.ProfileStore
import nl.lrvdlinden.mangavault.core.VaultService
import nl.lrvdlinden.mangavault.model.Manga
import nl.lrvdlinden.mangavault.model.nonEmpty
import nl.lrvdlinden.mangavault.ui.components.Chip
import nl.lrvdlinden.mangavault.ui.components.CoverInfo
import nl.lrvdlinden.mangavault.ui.components.CoverView
import nl.lrvdlinden.mangavault.ui.components.EmptyState
import nl.lrvdlinden.mangavault.ui.components.LoadingView
import nl.lrvdlinden.mangavault.ui.components.ScreenHeader
import nl.lrvdlinden.mangavault.ui.theme.Vault

private data class RecItem(val manga: Manga, val reason: String)

private fun recReason(m: Manga, top: List<String>): String {
    val g = (m.genre ?: "").lowercase()
    for (t in top) if (g.contains(t)) return "Because you enjoy $t"
    return "Popular in the vault"
}

/** Smart Recommendations: picks from your top genres, plus a random genre you don't own yet. */
@Composable
fun RecommendationsScreen() {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf("foryou") }
    var recs by remember { mutableStateOf<List<RecItem>>(emptyList()) }
    var newGenre by remember { mutableStateOf<String?>(null) }
    var newRecs by remember { mutableStateOf<List<Manga>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var hasCollection by remember { mutableStateOf(false) }
    var dismissed by remember { mutableStateOf<Set<String>>(emptySet()) }

    suspend fun loadNewGenre(top: List<String>, owned: Set<String>, dis: Set<String>) {
        val all = try { libDistinctGenres(lowercase = true) } catch (e: Exception) { null }
        if (all == null) { newGenre = null; newRecs = emptyList(); return }
        val candidates = all - top.toSet()
        val g = candidates.randomOrNull()
        if (g == null) { newGenre = null; newRecs = emptyList(); return }
        val list = try {
            Base44.list<Manga>("Manga", query = mapOf("genre" to libRegex(g)), limit = 6, fields = Manga.listFields).items
        } catch (e: Exception) { emptyList() }
        newGenre = g
        newRecs = list.filter { !owned.contains(it.id) && !dis.contains(it.id) }
    }

    suspend fun load() {
        val user = app.user
        if (user == null) { loading = false; return }
        loading = true
        try {
            val profile = ProfileStore.ensure(user.id)
            val dis = (profile.dismissed_recommendations ?: emptyList()).toSet()
            dismissed = dis
            val items = VaultService.collection(limit = 2000)
            hasCollection = items.isNotEmpty()
            val gmap = VaultService.genreMap(items)
            val owned = items.mapNotNull { nonEmpty(it.manga_id) }.toSet()
            val top = VaultService.genreDistribution(items, gmap, 5).map { it.first }

            val picked = mutableListOf<Manga>()
            val seen = mutableSetOf<String>()
            if (top.isNotEmpty()) {
                val pattern = top.joinToString("|") { libEscapePattern(it) }
                val q = mapOf<String, Any?>("genre" to mapOf("\$regex" to pattern, "\$options" to "i"))
                val page = Base44.list<Manga>("Manga", query = q, sort = "title", limit = 500, fields = Manga.listFields)
                for (m in page.items) {
                    if (!owned.contains(m.id) && !dis.contains(m.id) && !seen.contains(m.id)) { picked.add(m); seen.add(m.id) }
                }
            }
            if (picked.size < 6) {
                val page = Base44.list<Manga>("Manga", sort = "-created_date", limit = 200, fields = Manga.listFields)
                for (m in page.items) {
                    if (!owned.contains(m.id) && !dis.contains(m.id) && !seen.contains(m.id)) { picked.add(m); seen.add(m.id) }
                }
            }
            recs = picked.take(24).map { RecItem(it, recReason(it, top)) }

            loadNewGenre(top, owned, dis)
        } catch (e: Exception) {
            app.fail("Could not load recommendations", e)
        }
        loading = false
    }

    suspend fun dismiss(m: Manga) {
        recs = recs.filter { it.manga.id != m.id }
        newRecs = newRecs.filter { it.id != m.id }
        dismissed = dismissed + m.id
        val user = app.user ?: return
        try {
            val profile = ProfileStore.ensure(user.id)
            val merged = ((profile.dismissed_recommendations ?: emptyList()).toSet() + dismissed).toList()
            Base44.update("UserProfile", profile.id, mapOf("dismissed_recommendations" to merged))
            ProfileStore.invalidate()
        } catch (e: Exception) {
            app.fail("Could not save preference", e)
        }
    }

    LaunchedEffect(Unit) { load() }

    val onOpen: (Manga) -> Unit = { app.open(Route.Manga(it.id)) }
    val onDismiss: (Manga) -> Unit = { m -> scope.launch { dismiss(m) } }

    LibRefreshBox(
        refreshing = refreshing,
        onRefresh = { scope.launch { refreshing = true; load(); refreshing = false } },
        modifier = Modifier.fillMaxSize().background(Vault.background),
    ) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                ScreenHeader(
                    lead = "SMART", accent = "RECOMMENDATIONS",
                    eyebrow = "Recommendations", eyebrowIcon = Icons.Filled.AutoFixHigh,
                    subtitle = "Based on your favorite genres and the series you already own — no fabricated ratings or data.",
                )
            }
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("Recommended for you", mode == "foryou", icon = Icons.Filled.AutoAwesome) { mode = "foryou" }
                    Chip("Discover a new genre", mode == "new", icon = Icons.Filled.Explore) { mode = "new" }
                }
            }
            if (loading) {
                item { LoadingView() }
            } else if (mode == "foryou") {
                if (recs.isEmpty()) {
                    item {
                        if (hasCollection) {
                            EmptyState(
                                Icons.Filled.AutoAwesome, "No more recommendations right now. Try refreshing.",
                                buttonTitle = "Refresh", action = { scope.launch { load() } },
                            )
                        } else {
                            EmptyState(Icons.AutoMirrored.Filled.LibraryBooks, "Add a few manga to your collection so we can learn your taste.")
                        }
                    }
                } else {
                    val rows = recs.chunked(2)
                    items(rows.size) { i ->
                        RecGridRow(rows[i].map { it.manga to it.reason }, onOpen, onDismiss)
                    }
                }
            } else {
                val g = newGenre
                if (g != null) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Try a new genre: ${libCapitalized(g)}", color = Vault.foreground, fontSize = 20.sp, fontWeight = FontWeight.Black)
                            Text("You don't own any manga in this genre yet.", color = Vault.muted, fontSize = 14.sp)
                        }
                    }
                    val rows = newRecs.chunked(2)
                    items(rows.size) { i ->
                        RecGridRow(rows[i].map { it to "New genre: ${libCapitalized(g)}" }, onOpen, onDismiss)
                    }
                } else {
                    item { EmptyState(Icons.Filled.Explore, "No new genres to suggest right now — you're already exploring widely!") }
                }
            }
        }
    }
}

@Composable
private fun RecGridRow(cells: List<Pair<Manga, String>>, onOpen: (Manga) -> Unit, onDismiss: (Manga) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        for ((m, reason) in cells) {
            RecCard(m, reason, Modifier.weight(1f), onOpen, onDismiss)
        }
        if (cells.size < 2) Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun RecCard(m: Manga, reason: String, modifier: Modifier, onOpen: (Manga) -> Unit, onDismiss: (Manga) -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        CoverView(CoverInfo.of(m), Modifier.fillMaxWidth().clickable { onOpen(m) })
        Text(m.title, color = Vault.foreground, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(Icons.Filled.AutoAwesome, null, tint = Vault.primary, modifier = Modifier.size(12.dp))
            Text(reason, color = Vault.primary, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LibSmallButton(text = "View", color = Vault.primary, onClick = { onOpen(m) }, modifier = Modifier.weight(1f))
            LibSmallButton(text = null, icon = Icons.Filled.ThumbDown, color = Vault.muted, onClick = { onDismiss(m) })
        }
    }
}
