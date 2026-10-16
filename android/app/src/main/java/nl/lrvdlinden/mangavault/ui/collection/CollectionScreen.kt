package nl.lrvdlinden.mangavault.ui.collection

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Note
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EuroSymbol
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import nl.lrvdlinden.mangavault.app.AppState
import nl.lrvdlinden.mangavault.app.LocalApp
import nl.lrvdlinden.mangavault.app.MainTab
import nl.lrvdlinden.mangavault.app.Route
import nl.lrvdlinden.mangavault.core.Base44
import nl.lrvdlinden.mangavault.core.VaultService
import nl.lrvdlinden.mangavault.model.CollectionItem
import nl.lrvdlinden.mangavault.model.DayKey
import nl.lrvdlinden.mangavault.model.ReadStatus
import nl.lrvdlinden.mangavault.model.euro
import nl.lrvdlinden.mangavault.model.nonEmpty
import nl.lrvdlinden.mangavault.ui.components.Chip
import nl.lrvdlinden.mangavault.ui.components.CoverInfo
import nl.lrvdlinden.mangavault.ui.components.CoverView
import nl.lrvdlinden.mangavault.ui.components.EmptyState
import nl.lrvdlinden.mangavault.ui.components.OutlineButton
import nl.lrvdlinden.mangavault.ui.components.PrimaryButton
import nl.lrvdlinden.mangavault.ui.components.ProgressBar
import nl.lrvdlinden.mangavault.ui.components.ScreenHeader
import nl.lrvdlinden.mangavault.ui.components.StarRating
import nl.lrvdlinden.mangavault.ui.components.StatCard
import nl.lrvdlinden.mangavault.ui.components.cardStyle
import nl.lrvdlinden.mangavault.ui.theme.Vault
import java.text.Collator
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt

// MARK: Shared mutations (same activity logging as the web app)

private object ColActions {
    suspend fun update(app: AppState, item: CollectionItem, data: Map<String, Any?>, toast: String?): Boolean =
        try {
            Base44.update("CollectionItem", item.id, data)
            if (toast != null) app.show(toast)
            app.dataChanged()
            true
        } catch (e: Exception) {
            app.fail("Update failed", e)
            false
        }

    suspend fun setStatus(app: AppState, item: CollectionItem, s: ReadStatus): Boolean {
        val ok = update(app, item, mapOf("status" to s.raw), "Status updated")
        if (ok) {
            VaultService.log("status", item.manga_id, item.title, item.volume)
            if (s == ReadStatus.COMPLETED) VaultService.log("completed", item.manga_id, item.title, item.volume)
        }
        return ok
    }

    suspend fun setRating(app: AppState, item: CollectionItem, r: Int): Boolean {
        val ok = update(app, item, mapOf("rating" to r), "Rating updated")
        if (ok) VaultService.log("rated", item.manga_id, item.title, item.volume)
        return ok
    }

    suspend fun toggleFavorite(app: AppState, item: CollectionItem): Boolean {
        val ok = update(app, item, mapOf("favorite" to !item.isFavorite),
            if (item.isFavorite) "Removed from favorites" else "Added to favorites")
        if (ok) VaultService.log("favorite", item.manga_id, item.title, item.volume)
        return ok
    }

    suspend fun setChapter(app: AppState, item: CollectionItem, ch: Int): String? {
        val today = DayKey.local()
        val ok = update(app, item, mapOf("current_chapter" to ch, "last_read_date" to today), "Progress updated")
        if (ok) VaultService.log("progress", item.manga_id, item.title, item.volume, "ch.$ch")
        return if (ok) today else null
    }

    suspend fun setNotes(app: AppState, item: CollectionItem, notes: String): Boolean =
        update(app, item, mapOf("notes" to notes), "Note saved")

    suspend fun remove(app: AppState, item: CollectionItem): Boolean =
        try {
            Base44.delete("CollectionItem", item.id)
            app.show("Removed \"${item.titleText}\"")
            app.dataChanged()
            true
        } catch (e: Exception) {
            app.fail("Remove failed", e)
            false
        }
}

// MARK: Model

private data class ColMangaInfo(val genre: String, val total: Int)

@Serializable
private data class ColMangaInfoDto(val id: String = "", val genre: String? = null, val total_volumes: Double? = null)

private class ColSeriesRow(val id: String, val title: String, val total: Int) {
    var owned = 0
    var read = 0
    var current = 0
    var updated = ""
}

private val colSorts = listOf(
    "recent" to "Recently Added", "updated" to "Last Updated", "title" to "Title A-Z", "title_desc" to "Title Z-A",
    "progress" to "Reading Progress", "rating" to "Highest Rating", "volume" to "Volume Number",
    "owned_asc" to "Owned Volumes ↑", "owned_desc" to "Owned Volumes ↓", "total_asc" to "Total Volumes ↑", "total_desc" to "Total Volumes ↓",
)

private fun colGenres(g: String?): List<String> =
    (g ?: "").split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }

private class ColModel {
    var items by mutableStateOf<List<CollectionItem>>(emptyList())
    var loading by mutableStateOf(true)
    var errorText by mutableStateOf<String?>(null)
    var mangaInfo by mutableStateOf<Map<String, ColMangaInfo>>(emptyMap())
    var lastLoadedVersion = -1

    /** Loads every collection item (cursor paging) plus genre/total info per manga (chunks of 150). */
    suspend fun load(app: AppState) {
        loading = true
        errorText = null
        try {
            val all = mutableListOf<CollectionItem>()
            var cursor: String? = null
            do {
                val page = Base44.list<CollectionItem>("CollectionItem", sort = "-created_date", limit = 500, cursor = cursor)
                all += page.items
                cursor = if (page.has_more == true) page.next_cursor else null
            } while (cursor != null)
            items = all
            lastLoadedVersion = app.dataVersion
            val ids = all.mapNotNull { nonEmpty(it.manga_id) }.distinct()
            val info = mutableMapOf<String, ColMangaInfo>()
            for (chunk in ids.chunked(150)) {
                val page = Base44.list<ColMangaInfoDto>("Manga", query = mapOf("id" to mapOf("\$in" to chunk)), limit = 150,
                    fields = listOf("id", "genre", "total_volumes"))
                for (m in page.items) info[m.id] = ColMangaInfo(m.genre ?: "", (m.total_volumes ?: 0.0).toInt())
            }
            mangaInfo = info
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            errorText = e.message ?: "Unknown error"
        }
        loading = false
    }

    private fun patch(id: String, change: (CollectionItem) -> CollectionItem) {
        items = items.map { if (it.id == id) change(it) else it }
    }

    /** Mark our own change as seen so the dataVersion bump doesn't trigger a full reload. */
    private fun markSeen(app: AppState) { lastLoadedVersion = app.dataVersion + 1 }

    suspend fun setStatus(app: AppState, item: CollectionItem, s: ReadStatus) {
        patch(item.id) { it.copy(status = s.raw) }
        markSeen(app)
        if (!ColActions.setStatus(app, item, s)) load(app)
    }

    suspend fun setRating(app: AppState, item: CollectionItem, r: Int) {
        patch(item.id) { it.copy(rating = r.toDouble()) }
        markSeen(app)
        if (!ColActions.setRating(app, item, r)) load(app)
    }

    suspend fun toggleFavorite(app: AppState, item: CollectionItem) {
        patch(item.id) { it.copy(favorite = !item.isFavorite) }
        markSeen(app)
        if (!ColActions.toggleFavorite(app, item)) load(app)
    }

    suspend fun remove(app: AppState, item: CollectionItem) {
        items = items.filter { it.id != item.id }
        markSeen(app)
        if (!ColActions.remove(app, item)) load(app)
    }

    fun replace(updated: CollectionItem) {
        items = items.map { if (it.id == updated.id) updated else it }
    }
}

/** Same filtering & sorting rules as the web collection page. */
private fun colFiltered(
    items: List<CollectionItem>,
    mangaInfo: Map<String, ColMangaInfo>,
    search: String,
    statusFilter: ReadStatus?,
    favoritesOnly: Boolean,
    genreFilter: Set<String>,
    ratingFilter: String,
    progressFilter: String,
    sort: String,
): List<CollectionItem> {
    val q = search.trim().lowercase()
    var r = items.filter { i ->
        if (q.isEmpty()) true
        else listOf(i.titleText, i.publisher ?: "", i.notes ?: "", i.volumes_included ?: "", i.volume?.let { "vol $it" } ?: "")
            .any { it.lowercase().contains(q) }
    }
    if (statusFilter != null) r = r.filter { it.effectiveStatus == statusFilter }
    if (favoritesOnly) r = r.filter { it.isFavorite }
    if (genreFilter.isNotEmpty()) {
        r = r.filter { i ->
            val g = colGenres(mangaInfo[i.manga_id ?: ""]?.genre)
            genreFilter.any { g.contains(it) }
        }
    }
    if (ratingFilter == "unrated") r = r.filter { (it.rating ?: 0.0) <= 0.0 }
    else ratingFilter.toDoubleOrNull()?.let { min -> r = r.filter { (it.rating ?: 0.0) >= min } }
    when (progressFilter) {
        "done" -> r = r.filter { it.effectiveStatus == ReadStatus.COMPLETED }
        "unread" -> r = r.filter { it.effectiveStatus != ReadStatus.COMPLETED && it.chapter <= 0 }
        "partial" -> r = r.filter { it.effectiveStatus != ReadStatus.COMPLETED && (it.chapter > 0 || it.effectiveStatus == ReadStatus.READING) }
    }
    val ownedPerManga = mutableMapOf<String, Int>()
    items.forEach { val k = it.manga_id ?: ""; ownedPerManga[k] = (ownedPerManga[k] ?: 0) + 1 }
    fun ownedCount(i: CollectionItem) = ownedPerManga[i.manga_id ?: ""] ?: 0
    fun total(i: CollectionItem): Int {
        val t = mangaInfo[i.manga_id ?: ""]?.total ?: 0
        return if (t > 0) t else (i.total_volumes ?: 0.0).toInt()
    }
    fun byTotal(dir: Int) = Comparator<CollectionItem> { a, b ->
        val ta = total(a)
        val tb = total(b)
        when {
            ta == 0 && tb == 0 -> 0
            ta == 0 -> 1 // unknown totals last
            tb == 0 -> -1
            dir > 0 -> ta.compareTo(tb)
            else -> tb.compareTo(ta)
        }
    }
    val collator = Collator.getInstance()
    return when (sort) {
        "updated" -> r.sortedByDescending { it.last_read_date ?: it.updated_date ?: "" }
        "title" -> r.sortedWith(Comparator<CollectionItem> { a, b ->
            val c = collator.compare(a.titleText, b.titleText)
            if (c == 0) (a.volume ?: 0).compareTo(b.volume ?: 0) else c
        })
        "title_desc" -> r.sortedWith(Comparator<CollectionItem> { a, b -> collator.compare(b.titleText, a.titleText) })
        "progress" -> r.sortedByDescending { it.chapter }
        "rating" -> r.sortedByDescending { it.rating ?: 0.0 }
        "volume" -> r.sortedBy { it.volume ?: 0 }
        "owned_asc" -> r.sortedBy { ownedCount(it) }
        "owned_desc" -> r.sortedByDescending { ownedCount(it) }
        "total_asc" -> r.sortedWith(byTotal(1))
        "total_desc" -> r.sortedWith(byTotal(-1))
        else -> r.sortedByDescending { it.created_date ?: "" }
    }
}

private fun colSeriesRows(items: List<CollectionItem>, mangaInfo: Map<String, ColMangaInfo>): List<ColSeriesRow> {
    val map = LinkedHashMap<String, ColSeriesRow>()
    for (i in items) {
        val mid = i.manga_id ?: continue
        val row = map.getOrPut(mid) { ColSeriesRow(mid, i.titleText, mangaInfo[mid]?.total ?: (i.total_volumes ?: 0.0).toInt()) }
        row.owned += 1
        if (i.effectiveStatus == ReadStatus.COMPLETED) row.read += 1
        val v = i.volume ?: 0
        if (i.effectiveStatus == ReadStatus.READING && v > row.current) row.current = v
        val upd = nonEmpty(i.last_read_date) ?: (i.updated_date ?: "").take(10)
        if (upd > row.updated) row.updated = upd
    }
    return map.values.sortedByDescending { it.updated }
}

private fun colSeriesSummary(r: ColSeriesRow): String {
    var s = "${r.read} read · ${r.owned} owned"
    if (r.total > 0) s += " / ${r.total} vols"
    if (r.current > 0) s += " · on vol. ${r.current}"
    return s
}

private fun colSeriesFooter(r: ColSeriesRow): String {
    var s = if (r.total > 0) {
        val pct = minOf(100, (r.read.toDouble() / r.total * 100).roundToInt())
        "$pct% complete"
    } else "${r.read} volumes read"
    if (r.updated.isNotEmpty()) s += " · updated ${r.updated}"
    return s
}

private fun colRoute(i: CollectionItem): Route {
    val v = i.volume
    return if (i.isOmnibus || v == null) Route.Manga(i.manga_id ?: "") else Route.Volume(i.manga_id ?: "", v)
}

private fun colProgressLabel(p: String) = when (p) {
    "unread" -> "Not started (0%)"
    "partial" -> "Partially read"
    "done" -> "Completed (100%)"
    else -> "Any progress"
}

private fun colRatingLabel(r: String) = when (r) {
    "all" -> "Any rating"
    "unrated" -> "Not rated"
    else -> "${(r.toIntOrNull() ?: 0) / 2}+ stars"
}

@Composable
private fun colFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Vault.foreground, unfocusedTextColor = Vault.foreground,
    focusedBorderColor = Vault.primary, unfocusedBorderColor = Vault.border,
    focusedContainerColor = Vault.card, unfocusedContainerColor = Vault.card,
    cursorColor = Vault.primary,
    focusedPlaceholderColor = Vault.muted, unfocusedPlaceholderColor = Vault.muted,
    focusedLeadingIconColor = Vault.muted, unfocusedLeadingIconColor = Vault.muted,
)

// MARK: Screen

@Composable
fun CollectionScreen() {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    val model = remember { ColModel() }

    var search by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf<ReadStatus?>(null) }
    var favoritesOnly by remember { mutableStateOf(false) }
    var sort by remember { mutableStateOf("recent") }
    var gridMode by remember { mutableStateOf(true) }
    var editing by remember { mutableStateOf<CollectionItem?>(null) }
    var genreFilter by remember { mutableStateOf<Set<String>>(emptySet()) }
    var ratingFilter by remember { mutableStateOf("all") }
    var progressFilter by remember { mutableStateOf("all") }
    var showSeries by remember { mutableStateOf(false) }

    LaunchedEffect(app.dataVersion) {
        // Reload when another screen changed data (but not after our own optimistic updates).
        if (app.dataVersion != model.lastLoadedVersion) model.load(app)
    }

    val items = model.items
    val mangaInfo = model.mangaInfo
    val allGenres = remember(mangaInfo) { mangaInfo.values.flatMap { colGenres(it.genre) }.toSet().sorted() }
    val filtered = remember(items, mangaInfo, search, statusFilter, favoritesOnly, genreFilter, ratingFilter, progressFilter, sort) {
        colFiltered(items, mangaInfo, search, statusFilter, favoritesOnly, genreFilter, ratingFilter, progressFilter, sort)
    }
    val seriesRows = remember(items, mangaInfo) { colSeriesRows(items, mangaInfo) }
    val filtersActive = genreFilter.isNotEmpty() || ratingFilter != "all" || progressFilter != "all" ||
        statusFilter != null || favoritesOnly || search.isNotEmpty()
    val clearFilters = {
        genreFilter = emptySet(); ratingFilter = "all"; progressFilter = "all"; statusFilter = null; favoritesOnly = false; search = ""
    }

    LazyColumn(
        Modifier.fillMaxSize().background(Vault.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item(key = "header") {
            ScreenHeader("MY", "COLLECTION", subtitle = "Your personal manga vault, volume by volume.")
        }
        item(key = "toolbar") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text("Search your collection...", fontSize = 14.sp) },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    trailingIcon = {
                        if (search.isNotEmpty()) IconButton(onClick = { search = "" }) { Icon(Icons.Filled.Close, "Clear", tint = Vault.muted) }
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = colFieldColors(),
                )
                Box {
                    var sortOpen by remember { mutableStateOf(false) }
                    IconButton(onClick = { sortOpen = true }) {
                        Icon(Icons.AutoMirrored.Filled.Sort, "Sort", tint = if (sort != "recent") Vault.primary else Vault.foreground)
                    }
                    DropdownMenu(expanded = sortOpen, onDismissRequest = { sortOpen = false }) {
                        for ((k, label) in colSorts) {
                            DropdownMenuItem(
                                text = { Text(label, color = if (sort == k) Vault.primary else Vault.foreground) },
                                onClick = { sort = k; sortOpen = false },
                                leadingIcon = {
                                    if (sort == k) Icon(Icons.Filled.Check, null, tint = Vault.primary) else Spacer(Modifier.size(24.dp))
                                },
                            )
                        }
                    }
                }
                IconButton(onClick = { gridMode = !gridMode }) {
                    Icon(if (gridMode) Icons.AutoMirrored.Filled.List else Icons.Filled.Apps, if (gridMode) "List" else "Grid", tint = Vault.foreground)
                }
            }
        }
        item(key = "stats") { ColStats(items) }
        item(key = "filters") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("All", statusFilter == null) { statusFilter = null }
                    for (s in ReadStatus.entries) Chip(s.label, statusFilter == s) { statusFilter = s }
                    Chip("Favorites", favoritesOnly, icon = if (favoritesOnly) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        selectedColor = Vault.red) { favoritesOnly = !favoritesOnly }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ColFilterMenu(
                        label = colRatingLabel(ratingFilter),
                        active = ratingFilter != "all",
                        options = listOf("all" to "Any rating", "unrated" to "Not rated") +
                            listOf(5, 4, 3, 2, 1).map { n -> (n * 2).toString() to (if (n == 5) "5 stars" else "$n+ stars") },
                        selected = ratingFilter,
                        onSelect = { ratingFilter = it },
                    )
                    ColFilterMenu(
                        label = colProgressLabel(progressFilter),
                        active = progressFilter != "all",
                        options = listOf("all" to "Any progress", "unread" to "Not started (0%)", "partial" to "Partially read", "done" to "Completed (100%)"),
                        selected = progressFilter,
                        onSelect = { progressFilter = it },
                    )
                    if (filtersActive) {
                        Row(
                            Modifier.clip(RoundedCornerShape(8.dp)).clickable { clearFilters() }.padding(horizontal = 4.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Icon(Icons.Filled.Close, null, tint = Vault.primary, modifier = Modifier.size(14.dp))
                            Text("Clear filters", color = Vault.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }
                }
                if (allGenres.isNotEmpty()) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (g in allGenres) {
                            Chip(g, genreFilter.contains(g)) {
                                genreFilter = if (genreFilter.contains(g)) genreFilter - g else genreFilter + g
                            }
                        }
                    }
                }
            }
        }
        if (!model.loading && model.errorText == null && seriesRows.isNotEmpty()) {
            item(key = "series") {
                ColSeriesPanel(seriesRows, showSeries, onToggle = { showSeries = !showSeries }, onOpen = { app.open(Route.Manga(it)) })
            }
        }
        val err = model.errorText
        if (err != null) {
            item(key = "error") {
                Column(
                    Modifier.fillMaxWidth().cardStyle().padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("Could not load your collection: $err", color = Vault.muted, fontSize = 14.sp, textAlign = TextAlign.Center)
                    PrimaryButton("Try again", onClick = { scope.launch { model.load(app) } })
                }
            }
        } else if (model.loading && items.isEmpty()) {
            items(3) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    repeat(2) {
                        Box(Modifier.weight(1f).aspectRatio(2f / 3f).clip(RoundedCornerShape(10.dp)).background(Vault.card.copy(alpha = 0.6f)))
                    }
                }
            }
        } else if (filtered.isEmpty()) {
            item(key = "empty") {
                if (items.isEmpty()) {
                    EmptyState(Icons.AutoMirrored.Filled.LibraryBooks, "Your collection is empty.", buttonTitle = "Browse catalog",
                        action = { app.selectTab(MainTab.CATALOG) })
                } else {
                    EmptyState(Icons.AutoMirrored.Filled.LibraryBooks, "No volumes match your filters.", buttonTitle = "Clear filters",
                        action = { clearFilters() })
                }
            }
        } else if (gridMode) {
            items(filtered.chunked(2), key = { "g-" + it.first().id }) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    for (item in row) {
                        Box(Modifier.weight(1f)) {
                            ColGridCard(
                                item,
                                onOpen = { app.open(colRoute(item)) },
                                onEdit = { editing = item },
                                onFavorite = { scope.launch { model.toggleFavorite(app, item) } },
                                onRating = { r -> scope.launch { model.setRating(app, item, r) } },
                                onStatus = { s -> scope.launch { model.setStatus(app, item, s) } },
                                onRemove = { scope.launch { model.remove(app, item) } },
                            )
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        } else {
            items(filtered, key = { "l-" + it.id }) { item ->
                ColListRow(
                    item,
                    onOpen = { app.open(colRoute(item)) },
                    onEdit = { editing = item },
                    onFavorite = { scope.launch { model.toggleFavorite(app, item) } },
                    onRating = { r -> scope.launch { model.setRating(app, item, r) } },
                    onStatus = { s -> scope.launch { model.setStatus(app, item, s) } },
                    onRemove = { scope.launch { model.remove(app, item) } },
                )
            }
        }
    }

    editing?.let { e ->
        ColItemEditor(
            initial = e,
            scope = scope,
            onSave = { updated -> model.replace(updated) },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun ColStats(items: List<CollectionItem>) {
    val completed = items.count { it.effectiveStatus == ReadStatus.COMPLETED }
    val value = items.sumOf { it.price ?: 0.0 }
    val rated = items.filter { (it.rating ?: 0.0) > 0 }
    val avg = if (rated.isEmpty()) "—" else String.format(Locale.US, "%.1f", rated.sumOf { it.rating ?: 0.0 } / rated.size)
    val cards: List<Triple<ImageVector, String, Pair<String, Color>>> = listOf(
        Triple(Icons.AutoMirrored.Filled.LibraryBooks, "Volumes", "${items.size}" to Vault.primary),
        Triple(Icons.Filled.CheckCircle, "Completed", "$completed" to Vault.emerald),
        Triple(Icons.Filled.EuroSymbol, "Total Value", value.euro() to Vault.primary),
        Triple(Icons.Filled.Star, "Avg Rating", avg to Vault.orange),
        Triple(Icons.Filled.Favorite, "Favorites", "${items.count { it.isFavorite }}" to Vault.red),
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (row in cards.chunked(2)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (c in row) StatCard(c.first, c.second, c.third.first, Modifier.weight(1f), accent = c.third.second)
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ColFilterMenu(label: String, active: Boolean, options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    Box {
        Row(
            Modifier.clip(shape)
                .background(if (active) Vault.primary else Vault.card, shape)
                .border(1.dp, if (active) Color.Transparent else Vault.border, shape)
                .clickable { open = true }
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(label, color = if (active) Color.White else Vault.foreground, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Icon(Icons.Filled.KeyboardArrowDown, null, tint = if (active) Color.White else Vault.foreground, modifier = Modifier.size(14.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for ((k, l) in options) {
                DropdownMenuItem(
                    text = { Text(l, color = if (k == selected) Vault.primary else Vault.foreground) },
                    onClick = { onSelect(k); open = false },
                    leadingIcon = {
                        if (k == selected) Icon(Icons.Filled.Check, null, tint = Vault.primary) else Spacer(Modifier.size(24.dp))
                    },
                )
            }
        }
    }
}

@Composable
private fun ColSeriesPanel(rows: List<ColSeriesRow>, expanded: Boolean, onToggle: () -> Unit, onOpen: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().cardStyle().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onToggle), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Filled.LibraryBooks, null, tint = Vault.primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Series progress (${rows.size})", color = Vault.foreground, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.ExpandMore, null, tint = Vault.foreground, modifier = Modifier.rotate(if (expanded) 180f else 0f))
        }
        if (expanded) {
            for (r in rows) {
                Column(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Vault.background.copy(alpha = 0.5f))
                        .clickable { onOpen(r.id) }
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(r.title, color = Vault.foreground, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(colSeriesSummary(r), color = Vault.muted, fontSize = 12.sp)
                    if (r.total > 0) ProgressBar(r.read.toFloat() / r.total, height = 5.dp)
                    Text(colSeriesFooter(r), color = Vault.muted, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun ColStatusMenu(item: CollectionItem, onStatus: (ReadStatus) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Icon(
            Icons.Filled.MoreHoriz, "Status", tint = Vault.muted,
            modifier = Modifier.size(26.dp).clip(CircleShape).border(1.5.dp, Vault.muted, CircleShape).clickable { open = true }.padding(3.dp),
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for (s in ReadStatus.entries) {
                DropdownMenuItem(
                    text = { Text(s.label) },
                    onClick = { open = false; onStatus(s) },
                    leadingIcon = {
                        if (s == item.effectiveStatus) Icon(Icons.Filled.Check, null, tint = Vault.primary) else Spacer(Modifier.size(24.dp))
                    },
                )
            }
        }
    }
}

/** Long-press menu (iOS context menu). */
@Composable
private fun ColItemMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    item: CollectionItem,
    onStatus: (ReadStatus) -> Unit,
    onEdit: () -> Unit,
    onFavorite: () -> Unit,
    onRemove: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        Text("STATUS", color = Vault.muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        for (s in ReadStatus.entries) {
            DropdownMenuItem(
                text = { Text(s.label) },
                onClick = { onDismiss(); onStatus(s) },
                leadingIcon = {
                    if (s == item.effectiveStatus) Icon(Icons.Filled.Check, null, tint = Vault.primary) else Spacer(Modifier.size(24.dp))
                },
            )
        }
        DropdownMenuItem(text = { Text("Rating, progress & notes") }, onClick = { onDismiss(); onEdit() },
            leadingIcon = { Icon(Icons.Filled.Tune, null) })
        DropdownMenuItem(text = { Text(if (item.isFavorite) "Remove favorite" else "Add favorite") }, onClick = { onDismiss(); onFavorite() },
            leadingIcon = { Icon(if (item.isFavorite) Icons.Filled.FavoriteBorder else Icons.Filled.Favorite, null) })
        DropdownMenuItem(text = { Text("Remove", color = Vault.red) }, onClick = { onDismiss(); onRemove() },
            leadingIcon = { Icon(Icons.Filled.Delete, null, tint = Vault.red) })
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ColGridCard(
    item: CollectionItem,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onFavorite: () -> Unit,
    onRating: (Int) -> Unit,
    onStatus: (ReadStatus) -> Unit,
    onRemove: () -> Unit,
) {
    val st = item.effectiveStatus
    val stColor = Vault.statusColor(st)
    var menu by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box {
            CoverView(
                CoverInfo.of(item),
                Modifier.fillMaxWidth().combinedClickable(onClick = onOpen, onLongClick = { menu = true }),
            )
            Row(
                Modifier.align(Alignment.TopEnd).padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Box(Modifier.size(6.dp).background(stColor, CircleShape))
                Text(st.label, color = stColor, fontSize = 9.sp, fontWeight = FontWeight.Black, maxLines = 1)
            }
            if (item.chapter > 0) {
                Text(
                    "Ch. ${item.chapter}", color = Vault.primary, fontSize = 9.sp, fontWeight = FontWeight.Black,
                    modifier = Modifier.align(Alignment.BottomStart).padding(8.dp)
                        .background(Vault.background.copy(alpha = 0.85f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                )
            }
            Box(
                Modifier.align(Alignment.TopStart).padding(6.dp).size(30.dp).clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f)).clickable(onClick = onFavorite),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (item.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, "Favorite",
                    tint = if (item.isFavorite) Vault.red else Vault.muted, modifier = Modifier.size(15.dp),
                )
            }
            ColItemMenu(menu, { menu = false }, item, onStatus, onEdit, onFavorite, onRemove)
        }
        Text(item.titleText, color = Vault.foreground, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(item.volumeLabel, color = Vault.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text((item.price ?: 0.0).euro(), color = Vault.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            StarRating(item.ratingValue, onSet = onRating)
            Spacer(Modifier.weight(1f))
            ColStatusMenu(item, onStatus)
        }
        OutlineButton("Details", onClick = onEdit, modifier = Modifier.fillMaxWidth(), color = Vault.primary)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ColListRow(
    item: CollectionItem,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onFavorite: () -> Unit,
    onRating: (Int) -> Unit,
    onStatus: (ReadStatus) -> Unit,
    onRemove: () -> Unit,
) {
    val st = item.effectiveStatus
    val stColor = Vault.statusColor(st)
    var menu by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.fillMaxWidth().cardStyle()
                .combinedClickable(onClick = onEdit, onLongClick = { menu = true })
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CoverView(CoverInfo.of(item), Modifier.width(52.dp).clickable(onClick = onOpen), cornerRadius = 6.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(item.titleText, color = Vault.foreground, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val chapter = if (item.chapter > 0) " · Ch. ${item.chapter}" else ""
                Text("${item.volumeLabel} · ${(item.price ?: 0.0).euro()}$chapter", color = Vault.muted, fontSize = 12.sp, maxLines = 1)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        st.label, color = stColor, fontSize = 10.sp, fontWeight = FontWeight.Black,
                        modifier = Modifier.background(stColor.copy(alpha = 0.18f), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                    StarRating(item.ratingValue, size = 11.dp, onSet = onRating)
                }
                if (item.hasNotes) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.AutoMirrored.Filled.Note, null, tint = Vault.muted, modifier = Modifier.size(11.dp))
                        Text(item.notes ?: "", color = Vault.muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(
                    if (item.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, "Favorite",
                    tint = if (item.isFavorite) Vault.red else Vault.muted,
                    modifier = Modifier.size(22.dp).clickable(onClick = onFavorite),
                )
                ColStatusMenu(item, onStatus)
            }
        }
        ColItemMenu(menu, { menu = false }, item, onStatus, onEdit, onFavorite, onRemove)
    }
}

// MARK: Editor sheet (status, rating, favorite, chapter progress, notes)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColItemEditor(
    initial: CollectionItem,
    scope: CoroutineScope,
    onSave: (CollectionItem) -> Unit,
    onDismiss: () -> Unit,
) {
    val app = LocalApp.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var item by remember(initial.id) { mutableStateOf(initial) }
    var chapterText by remember(initial.id) { mutableStateOf(if (initial.chapter > 0) initial.chapter.toString() else "") }
    var notes by remember(initial.id) { mutableStateOf(initial.notes ?: "") }
    var saving by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }
    var statusOpen by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Vault.background,
        contentColor = Vault.foreground,
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding()
                .padding(horizontal = 16.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss) { Text("Close", color = Vault.muted) }
                Text("Details", color = Vault.foreground, fontSize = 17.sp, fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                TextButton(
                    enabled = !saving,
                    onClick = {
                        scope.launch {
                            saving = true
                            try {
                                var cur = item
                                val ch = chapterText.trim().toIntOrNull() ?: 0
                                if (chapterText.isNotEmpty() && ch != cur.chapter) {
                                    ColActions.setChapter(app, cur, ch)?.let { day ->
                                        cur = cur.copy(current_chapter = ch.toDouble(), last_read_date = day)
                                    }
                                }
                                if (notes != (cur.notes ?: "")) {
                                    if (ColActions.setNotes(app, cur, notes)) cur = cur.copy(notes = notes)
                                }
                                item = cur
                                onSave(cur)
                                onDismiss()
                            } finally {
                                saving = false
                            }
                        }
                    },
                ) {
                    if (saving) CircularProgressIndicator(Modifier.size(18.dp), color = Vault.primary, strokeWidth = 2.dp)
                    else Text("Save", color = Vault.primary, fontWeight = FontWeight.Bold)
                }
            }

            // Header
            Row(Modifier.fillMaxWidth().cardStyle().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically) {
                CoverView(CoverInfo.of(item), Modifier.width(60.dp), cornerRadius = 6.dp)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(item.titleText, color = Vault.foreground, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text("${item.volumeLabel} · ${(item.price ?: 0.0).euro()}", color = Vault.muted, fontSize = 12.sp)
                }
            }

            // Status + favorite
            ColEditorSection("Status") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Status", color = Vault.foreground, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    Box {
                        val st = item.effectiveStatus
                        Row(
                            Modifier.clip(RoundedCornerShape(8.dp)).clickable { statusOpen = true }.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(st.label, color = Vault.statusColor(st), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Icon(Icons.Filled.KeyboardArrowDown, null, tint = Vault.muted, modifier = Modifier.size(16.dp))
                        }
                        DropdownMenu(expanded = statusOpen, onDismissRequest = { statusOpen = false }) {
                            for (s in ReadStatus.entries) {
                                DropdownMenuItem(
                                    text = { Text(s.label) },
                                    onClick = {
                                        statusOpen = false
                                        item = item.copy(status = s.raw)
                                        val snapshot = item
                                        scope.launch {
                                            ColActions.setStatus(app, snapshot, s)
                                            onSave(snapshot)
                                        }
                                    },
                                    leadingIcon = {
                                        if (s == item.effectiveStatus) Icon(Icons.Filled.Check, null, tint = Vault.primary) else Spacer(Modifier.size(24.dp))
                                    },
                                )
                            }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Favorite, null, tint = Vault.red, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Favorite", color = Vault.red, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    Switch(
                        checked = item.isFavorite,
                        onCheckedChange = { v ->
                            val before = item
                            item = item.copy(favorite = v)
                            val snapshot = item
                            scope.launch { if (ColActions.toggleFavorite(app, before)) onSave(snapshot) }
                        },
                        colors = SwitchDefaults.colors(checkedTrackColor = Vault.primary, checkedThumbColor = Color.White),
                    )
                }
            }

            ColEditorSection("Rating") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StarRating(item.ratingValue, size = 26.dp, onSet = { r ->
                        item = item.copy(rating = r.toDouble())
                        val snapshot = item
                        scope.launch { if (ColActions.setRating(app, snapshot, r)) onSave(snapshot) }
                    })
                    Spacer(Modifier.weight(1f))
                    Text(if (item.ratingValue > 0) "${item.ratingValue}/10" else "Not rated", color = Vault.muted, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }

            ColEditorSection("Reading progress") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Current chapter", color = Vault.foreground, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    OutlinedTextField(
                        value = chapterText,
                        onValueChange = { v -> chapterText = v.filter { it.isDigit() } },
                        modifier = Modifier.width(110.dp),
                        singleLine = true,
                        placeholder = { Text("0") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(10.dp),
                        colors = colFieldColors(),
                    )
                }
                nonEmpty(item.last_read_date)?.let { d ->
                    Text("Updated $d", color = Vault.muted, fontSize = 12.sp)
                }
            }

            ColEditorSection("Notes") {
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Add a note...") },
                    minLines = 3,
                    maxLines = 8,
                    shape = RoundedCornerShape(10.dp),
                    colors = colFieldColors(),
                )
            }

            Row(
                Modifier.fillMaxWidth().cardStyle().clickable { confirmRemove = true }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Filled.Delete, null, tint = Vault.red, modifier = Modifier.size(18.dp))
                Text("Remove from collection", color = Vault.red, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
    }

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            containerColor = Vault.card,
            title = { Text("Remove \"${item.titleText}\"?", color = Vault.foreground) },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = false
                    val target = item
                    scope.launch { if (ColActions.remove(app, target)) onDismiss() }
                }) { Text("Remove", color = Vault.red, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = false }) { Text("Cancel", color = Vault.muted) }
            },
        )
    }
}

@Composable
private fun ColEditorSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title.uppercase(), color = Vault.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp))
        Column(Modifier.fillMaxWidth().cardStyle().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}
