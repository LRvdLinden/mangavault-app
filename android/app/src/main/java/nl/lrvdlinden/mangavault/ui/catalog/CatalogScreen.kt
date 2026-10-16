package nl.lrvdlinden.mangavault.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import nl.lrvdlinden.mangavault.app.AppState
import nl.lrvdlinden.mangavault.app.LocalApp
import nl.lrvdlinden.mangavault.app.Route
import nl.lrvdlinden.mangavault.core.Base44
import nl.lrvdlinden.mangavault.core.Page
import nl.lrvdlinden.mangavault.model.Manga
import nl.lrvdlinden.mangavault.ui.components.Chip
import nl.lrvdlinden.mangavault.ui.components.EmptyState
import nl.lrvdlinden.mangavault.ui.components.EventBannerView
import nl.lrvdlinden.mangavault.ui.components.MangaCard
import nl.lrvdlinden.mangavault.ui.components.OutlineButton
import nl.lrvdlinden.mangavault.ui.theme.Vault
import kotlin.coroutines.cancellation.CancellationException

private val catalogFilters = listOf("all", "shonen_jump", "Kodansha", "Shogakukan", "Square Enix", "Kadokawa", "Shueisha", "Other")
private const val CATALOG_PAGE_SIZE = 60

/** Keeps the loaded grid per mode so going back from a manga restores the list and scroll position. */
private class CatalogMemoryEntry(
    val search: String,
    val filter: String,
    val items: List<Manga>,
    val cursor: String?,
    val hasMore: Boolean,
    val query: Map<String, Any?>,
    val index: Int,
    val offset: Int,
)

private object CatalogMemory {
    val entries = mutableMapOf<Boolean, CatalogMemoryEntry>()
}

/** NSRegularExpression.escapedPattern equivalent. */
private fun catalogEscapeRegex(s: String): String {
    val special = "\\^$.|?*+()[]{}/"
    val sb = StringBuilder()
    for (c in s) {
        if (special.indexOf(c) >= 0) sb.append('\\')
        sb.append(c)
    }
    return sb.toString()
}

private fun catalogTitleQuery(search: String): MutableMap<String, Any?> {
    val s = search.trim()
    return if (s.isEmpty()) mutableMapOf()
    else mutableMapOf("title" to mapOf("\$regex" to catalogEscapeRegex(s), "\$options" to "i"))
}

private fun catalogBuildQuery(search: String, filter: String): Map<String, Any?> {
    val q = catalogTitleQuery(search)
    when (filter) {
        "all" -> q["is_shonen_jump"] = mapOf("\$ne" to true)
        "shonen_jump" -> q["is_shonen_jump"] = true
        else -> q["publisher"] = filter
    }
    return q
}

private fun catalogFilterLabel(f: String): String = when (f) {
    "all" -> "All"
    "shonen_jump" -> "Shonen Jump"
    else -> f
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogScreen(discover: Boolean) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    val memory = remember { CatalogMemory.entries[discover] }

    var search by remember { mutableStateOf(memory?.search ?: "") }
    var debounced by remember { mutableStateOf(memory?.search ?: "") }
    var filter by remember { mutableStateOf(memory?.filter ?: "all") }
    var mangas by remember { mutableStateOf(memory?.items ?: emptyList()) }
    var loading by remember { mutableStateOf(memory == null) }
    var refreshing by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    var cursor by remember { mutableStateOf(memory?.cursor) }
    var hasMore by remember { mutableStateOf(memory?.hasMore ?: false) }
    var currentQuery by remember { mutableStateOf(memory?.query ?: emptyMap()) }
    var owned by remember { mutableStateOf(setOf<String>()) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var reloadTick by remember { mutableIntStateOf(0) }
    var skipFirstReload by remember { mutableStateOf(memory != null) }

    val gridState = rememberLazyGridState(memory?.index ?: 0, memory?.offset ?: 0)

    // Remember the list when the screen leaves composition (e.g. a manga is opened).
    DisposableEffect(Unit) {
        onDispose {
            if (mangas.isNotEmpty()) {
                CatalogMemory.entries[discover] = CatalogMemoryEntry(
                    search = debounced, filter = filter, items = mangas, cursor = cursor, hasMore = hasMore,
                    query = currentQuery, index = gridState.firstVisibleItemIndex, offset = gridState.firstVisibleItemScrollOffset,
                )
            }
        }
    }

    // Debounce the search text (250 ms).
    LaunchedEffect(search) {
        if (search == debounced) return@LaunchedEffect
        delay(250)
        debounced = search
    }

    suspend fun reload() {
        errorText = null
        val q = catalogBuildQuery(debounced, filter)
        currentQuery = q
        try {
            val result: Pair<List<Manga>, Page<Manga>> = coroutineScope {
                if (filter == "all") {
                    val sjQuery = catalogTitleQuery(debounced)
                    sjQuery["is_shonen_jump"] = true
                    val a = async { Base44.list<Manga>("Manga", query = sjQuery, sort = "title", limit = 100, fields = Manga.listFields) }
                    val b = async { Base44.list<Manga>("Manga", query = q, sort = "title", limit = CATALOG_PAGE_SIZE, fields = Manga.listFields) }
                    a.await().items to b.await()
                } else {
                    emptyList<Manga>() to Base44.list<Manga>("Manga", query = q, sort = "title", limit = CATALOG_PAGE_SIZE, fields = Manga.listFields)
                }
            }
            val head = result.first
            val rest = result.second
            val headIds = head.map { it.id }.toSet()
            mangas = (head + rest.items.filter { it.id !in headIds }).distinctBy { it.id }
            cursor = rest.next_cursor
            hasMore = rest.has_more ?: false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            errorText = e.message ?: "Could not load the catalog."
            mangas = emptyList()
        }
        loading = false
    }

    suspend fun loadMore() {
        val c = cursor
        if (!hasMore || c == null || loadingMore) return
        loadingMore = true
        try {
            val page = Base44.list<Manga>("Manga", query = currentQuery, sort = "title", limit = CATALOG_PAGE_SIZE,
                fields = Manga.listFields, cursor = c)
            val known = mangas.map { it.id }.toSet()
            mangas = mangas + page.items.filter { it.id !in known }.distinctBy { it.id }
            cursor = page.next_cursor
            hasMore = page.has_more ?: false
        } catch (e: Exception) {
            app.fail("Could not load more", e)
        } finally {
            loadingMore = false
        }
    }

    LaunchedEffect(debounced, filter, reloadTick) {
        if (skipFirstReload) { skipFirstReload = false; return@LaunchedEffect }
        if (!refreshing) loading = true
        reload()
        refreshing = false
    }

    LaunchedEffect(app.dataVersion) {
        if (!discover) return@LaunchedEffect
        try {
            owned = Base44.distinct("CollectionItem", "manga_id").toSet()
        } catch (e: CancellationException) {
            throw e
        } catch (ignored: Exception) {
        }
    }

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = { refreshing = true; reloadTick++ },
        modifier = Modifier.fillMaxSize().background(Vault.background),
    ) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "search") {
                CatalogSearchField(search, { search = it }, Modifier.padding(top = 12.dp))
            }
            if (!discover) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "banner") {
                    EventBannerView()
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }, key = "hero") {
                CatalogHero(discover, app)
            }
            item(span = { GridItemSpan(maxLineSpan) }, key = "filters") {
                CatalogFilterBar(
                    discover = discover,
                    filter = filter,
                    surpriseEnabled = mangas.isNotEmpty(),
                    onSurprise = { mangas.randomOrNull()?.let { app.open(Route.Manga(it.id)) } },
                    onFilter = { filter = it },
                )
            }

            when {
                loading -> {
                    items(8) {
                        Box(
                            Modifier.fillMaxWidth().aspectRatio(2f / 3f)
                                .background(Vault.card.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                        )
                    }
                }
                errorText != null && mangas.isEmpty() -> {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "error") {
                        EmptyState(Icons.Filled.Warning, errorText ?: "", buttonTitle = "Try again", action = { reloadTick++ })
                    }
                }
                mangas.isEmpty() -> {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "empty") {
                        EmptyState(Icons.AutoMirrored.Filled.MenuBook, "No manga found.")
                    }
                }
                else -> {
                    itemsIndexed(mangas, key = { _, m -> "m-" + m.id }) { index, m ->
                        MangaCard(
                            manga = m,
                            owned = discover && m.id in owned,
                            showGenre = discover,
                            onClick = { app.open(Route.Manga(m.id)) },
                        )
                        if (index == mangas.lastIndex && hasMore) {
                            LaunchedEffect(m.id) { loadMore() }
                        }
                    }
                    if (hasMore) {
                        item(span = { GridItemSpan(maxLineSpan) }, key = "more") {
                            Box(Modifier.fillMaxWidth().padding(top = 4.dp), contentAlignment = Alignment.Center) {
                                if (loadingMore) {
                                    CircularProgressIndicator(color = Vault.primary, modifier = Modifier.size(28.dp))
                                } else {
                                    OutlineButton(
                                        text = "Load More",
                                        onClick = { scope.launch { loadMore() } },
                                        modifier = Modifier.widthIn(min = 140.dp),
                                        color = Vault.primary,
                                        borderColor = Vault.primary.copy(alpha = 0.5f),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CatalogSearchField(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        placeholder = { Text("Search manga...") },
        leadingIcon = { Icon(Icons.Filled.Search, null) },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onChange("") }) { Icon(Icons.Filled.Close, "Clear") }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Vault.foreground,
            unfocusedTextColor = Vault.foreground,
            focusedContainerColor = Vault.card,
            unfocusedContainerColor = Vault.card,
            focusedBorderColor = Vault.primary,
            unfocusedBorderColor = Vault.border,
            cursorColor = Vault.primary,
            focusedLeadingIconColor = Vault.muted,
            unfocusedLeadingIconColor = Vault.muted,
            focusedTrailingIconColor = Vault.muted,
            unfocusedTrailingIconColor = Vault.muted,
            focusedPlaceholderColor = Vault.muted,
            unfocusedPlaceholderColor = Vault.muted,
        ),
    )
}

@Composable
private fun CatalogHero(discover: Boolean, app: AppState) {
    Column(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        listOf(Vault.primary.copy(alpha = 0.35f), Color.Transparent),
                        center = Offset(size.width / 2f, 0f),
                        radius = 320.dp.toPx(),
                    )
                )
            }
            .padding(vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier
                    .background(Vault.primary.copy(alpha = 0.1f), CircleShape)
                    .border(1.dp, Vault.primary.copy(alpha = 0.3f), CircleShape)
                    .padding(horizontal = 12.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    if (discover) Icons.Filled.Explore else Icons.Filled.LocalFireDepartment, null,
                    tint = Vault.primary, modifier = Modifier.size(14.dp),
                )
                Text(
                    if (discover) "DISCOVER" else "EVERY MANGA. EVERY LANGUAGE.",
                    color = Vault.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.weight(1f))
            if (!discover) {
                // iOS has this in the toolbar; the shared top bar has no per-screen actions.
                IconButton(onClick = { app.open(Route.Discover) }) {
                    Icon(Icons.Filled.Explore, "Discover", tint = Vault.primary)
                }
            }
        }
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = Vault.foreground)) { append(if (discover) "FIND YOUR NEXT " else "THE MANGA ") }
                withStyle(SpanStyle(color = Vault.primary)) { append(if (discover) "READ" else "VAULT") }
            },
            fontSize = 36.sp, fontWeight = FontWeight.Black, letterSpacing = (-1.5).sp, lineHeight = 38.sp,
        )
        Text(
            if (discover) "Browse the vault, filter by publisher, or get a surprise recommendation."
            else "The full MangaDex catalog, in English. Open a title to add the individual volumes you own, track your reading, and total your collection's value.",
            color = Vault.muted, fontSize = 14.sp,
        )
        Box(Modifier.fillMaxWidth().height(1.dp).background(Vault.primary.copy(alpha = 0.1f)))
    }
}

@Composable
private fun CatalogFilterBar(
    discover: Boolean,
    filter: String,
    surpriseEnabled: Boolean,
    onSurprise: () -> Unit,
    onFilter: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 4.dp),
        ) {
            if (discover) {
                item(key = "surprise") {
                    val shape = RoundedCornerShape(9.dp)
                    Row(
                        Modifier
                            .border(1.dp, Vault.primary.copy(alpha = if (surpriseEnabled) 0.4f else 0.15f), shape)
                            .clickable(enabled = surpriseEnabled, onClick = onSurprise)
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        val c = if (surpriseEnabled) Vault.primary else Vault.primary.copy(alpha = 0.4f)
                        Icon(Icons.Filled.Shuffle, null, tint = c, modifier = Modifier.size(14.dp))
                        Text("Surprise me", color = c, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            items(catalogFilters, key = { it }) { f ->
                Chip(title = catalogFilterLabel(f), selected = filter == f) { onFilter(f) }
            }
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Vault.border))
    }
}
