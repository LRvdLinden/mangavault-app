package nl.lrvdlinden.mangavault.ui.rewards

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import nl.lrvdlinden.mangavault.app.AppState
import nl.lrvdlinden.mangavault.app.LocalApp
import nl.lrvdlinden.mangavault.core.Base44
import nl.lrvdlinden.mangavault.core.Engines
import nl.lrvdlinden.mangavault.core.arr
import nl.lrvdlinden.mangavault.core.bool
import nl.lrvdlinden.mangavault.core.parseObject
import nl.lrvdlinden.mangavault.model.CardRarity
import nl.lrvdlinden.mangavault.model.TradingCard
import nl.lrvdlinden.mangavault.model.nonEmpty
import nl.lrvdlinden.mangavault.ui.components.CardSize
import nl.lrvdlinden.mangavault.ui.components.Chip
import nl.lrvdlinden.mangavault.ui.components.EmptyState
import nl.lrvdlinden.mangavault.ui.components.LoadingView
import nl.lrvdlinden.mangavault.ui.components.PrimaryButton
import nl.lrvdlinden.mangavault.ui.components.ScreenHeader
import nl.lrvdlinden.mangavault.ui.components.StatCard
import nl.lrvdlinden.mangavault.ui.components.TradingCardView
import nl.lrvdlinden.mangavault.ui.components.cardStyle
import nl.lrvdlinden.mangavault.ui.theme.Vault
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt

// Port of CardsView (web /cards): collection stats, starter pack, filters and the card grid.

private data class RwOwnedCard(val id: String, val count: Int, val favorite: Boolean, val source: String?)

private class RwCardsModel(private val app: AppState) {
    var cards by mutableStateOf<List<TradingCard>>(emptyList())
    var owned by mutableStateOf<Map<String, RwOwnedCard>>(emptyMap())
    var loading by mutableStateOf(true)
    var errorText by mutableStateOf<String?>(null)
    var search by mutableStateOf("")
    var rarity by mutableStateOf<CardRarity?>(null)
    var favoritesOnly by mutableStateOf(false)
    var detail by mutableStateOf<TradingCard?>(null)
    var claimingStarter by mutableStateOf(false)

    val filtered: List<TradingCard>
        get() {
            val q = search.trim().lowercase()
            return cards.filter { c ->
                val r = rarity
                if (r != null && c.rarityValue != r) return@filter false
                if (favoritesOnly && !(owned[c.card_id]?.favorite ?: false)) return@filter false
                if (q.isEmpty()) return@filter true
                c.title.lowercase().contains(q) || (c.description ?: "").lowercase().contains(q)
            }
        }

    val hasStarter: Boolean get() = owned.values.any { it.source == "starter" }

    suspend fun load() {
        try {
            val all = Engines.allCards()
            val mine = Engines.myCards()
            val map = mutableMapOf<String, RwOwnedCard>()
            for (u in mine) {
                if (u.card_id.isEmpty()) continue
                val existing = map[u.card_id]
                map[u.card_id] = if (existing != null) {
                    existing.copy(
                        count = existing.count + maxOf(u.count, 1),
                        favorite = existing.favorite || (u.favorite ?: false),
                        source = existing.source ?: u.source,
                    )
                } else {
                    RwOwnedCard(u.id, maxOf(u.count, 1), u.favorite ?: false, u.source)
                }
            }
            cards = all
            owned = map
            errorText = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            errorText = e.message ?: "Could not load cards."
        }
        loading = false
    }

    suspend fun claimStarter() {
        if (claimingStarter) return
        claimingStarter = true
        try {
            val o = parseObject(Engines.community("starter"))
            if (o.bool("ok")) {
                val n = o.arr("granted")?.size ?: 0
                app.show("Starter pack claimed!", "$n cards added")
                app.celebrate()
                app.dataChanged()
                load()
            } else {
                app.show("Could not claim starter pack", rwError(o, "Please try again later."), error = true)
            }
        } catch (e: Exception) {
            app.fail("Could not claim starter pack", e)
        } finally {
            claimingStarter = false
        }
    }

    fun toggleFavorite(card: TradingCard, scope: CoroutineScope) {
        val o = owned[card.card_id] ?: return
        val newValue = !o.favorite
        owned = owned + (card.card_id to o.copy(favorite = newValue))
        val userCardId = o.id
        val cardId = card.card_id
        scope.launch {
            try {
                Base44.update("UserCard", userCardId, mapOf("favorite" to newValue))
            } catch (e: Exception) {
                owned[cardId]?.let { revert -> owned = owned + (cardId to revert.copy(favorite = !newValue)) }
                app.fail("Could not update favorite", e)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardsScreen() {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    val m = remember { RwCardsModel(app) }
    var refreshing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { m.load() }

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = {
            scope.launch {
                refreshing = true
                m.load()
                refreshing = false
            }
        },
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(130.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                ScreenHeader(
                    "TRADING", "CARDS", eyebrow = "Cards", eyebrowIcon = Icons.Filled.Style,
                    subtitle = "Collect manga cards by reading, completing events, and joining the community.",
                )
            }
            val err = m.errorText
            if (m.loading && m.cards.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { LoadingView() }
            } else if (err != null && m.cards.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { RwErrorState(err) { scope.launch { m.load() } } }
            } else {
                item(span = { GridItemSpan(maxLineSpan) }) { RwCardStats(m) }
                if (!m.hasStarter) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        RwStarterBanner(m.claimingStarter) { scope.launch { m.claimStarter() } }
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }) { RwCardFilters(m) }
                val list = m.filtered
                if (list.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) { EmptyState(Icons.Filled.Style, "No cards match your filters.") }
                } else {
                    items(list) { c ->
                        val o = m.owned[c.card_id]
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                            TradingCardView(
                                card = c,
                                owned = o != null,
                                count = o?.count ?: 0,
                                favorite = o?.favorite ?: false,
                                size = CardSize.MD,
                                onClick = { m.detail = c },
                                onFavorite = if (o != null) ({ m.toggleFavorite(c, scope) }) else null,
                            )
                        }
                    }
                }
            }
        }
    }

    val detail = m.detail
    if (detail != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { m.detail = null },
            sheetState = sheetState,
            containerColor = Vault.background,
        ) {
            RwCardDetail(detail, m.owned[detail.card_id], onDone = { m.detail = null }, onFavorite = { m.toggleFavorite(detail, scope) })
        }
    }
}

@Composable
private fun RwCardStats(m: RwCardsModel) {
    val total = m.cards.size
    val ownedCount = m.cards.count { m.owned[it.card_id] != null }
    val pct = if (total > 0) (ownedCount.toDouble() / total * 100).roundToInt() else 0
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatCard(Icons.Filled.Style, "Collected", "$ownedCount/$total", Modifier.weight(1f))
        StatCard(Icons.Filled.BarChart, "Complete", "$pct%", Modifier.weight(1f), accent = Vault.emerald)
    }
}

@Composable
private fun RwStarterBanner(claiming: Boolean, onClaim: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier.fillMaxWidth()
            .background(Vault.primary.copy(alpha = 0.08f), shape)
            .border(1.dp, Vault.primary.copy(alpha = 0.45f), shape)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Filled.CardGiftcard, null, tint = Vault.primary, modifier = Modifier.size(26.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Free Starter Pack", color = Vault.foreground, fontSize = 17.sp, fontWeight = FontWeight.Black)
            Text("Claim 3 common cards to begin your collection.", color = Vault.muted, fontSize = 12.sp)
        }
        PrimaryButton(if (claiming) "Claiming…" else "Claim Pack", onClick = onClaim, enabled = !claiming)
    }
}

@Composable
private fun RwCardFilters(m: RwCardsModel) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = m.search,
            onValueChange = { m.search = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("Search cards…", color = Vault.muted) },
            leadingIcon = { Icon(Icons.Filled.Search, null, tint = Vault.muted) },
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Vault.card,
                unfocusedContainerColor = Vault.card,
                focusedBorderColor = Vault.primary,
                unfocusedBorderColor = Vault.border,
                focusedTextColor = Vault.foreground,
                unfocusedTextColor = Vault.foreground,
                cursorColor = Vault.primary,
            ),
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            var menuOpen by remember { mutableStateOf(false) }
            val r = m.rarity
            Box {
                val shape = RoundedCornerShape(9.dp)
                Row(
                    Modifier
                        .clip(shape)
                        .background(if (r == null) Vault.card else Vault.primary, shape)
                        .border(1.dp, if (r == null) Vault.border else Color.Transparent, shape)
                        .clickable { menuOpen = true }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(r?.label ?: "All rarities", color = if (r == null) Vault.muted else Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Icon(Icons.Filled.ExpandMore, null, tint = if (r == null) Vault.muted else Color.White, modifier = Modifier.size(14.dp))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("All rarities") }, onClick = { m.rarity = null; menuOpen = false })
                    for (option in CardRarity.entries) {
                        DropdownMenuItem(text = { Text(option.label) }, onClick = { m.rarity = option; menuOpen = false })
                    }
                }
            }
            Chip(
                "Favorites", m.favoritesOnly,
                icon = if (m.favoritesOnly) Icons.Filled.Star else Icons.Filled.StarBorder,
            ) { m.favoritesOnly = !m.favoritesOnly }
        }
    }
}

@Composable
private fun RwCardDetail(card: TradingCard, owned: RwOwnedCard?, onDone: () -> Unit, onFavorite: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Card", color = Vault.foreground, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(onClick = onDone) { Text("Done", color = Vault.primary, fontWeight = FontWeight.Bold) }
        }
        TradingCardView(card, owned = owned != null, count = owned?.count ?: 0, favorite = owned?.favorite ?: false, size = CardSize.LG)
        Text(card.title, color = Vault.foreground, fontSize = 22.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
        RwRarityPill(card.rarity)
        Text(
            nonEmpty(card.description) ?: "A rare collectible from the MangaVault universe.",
            color = Vault.muted, fontSize = 14.sp, textAlign = TextAlign.Center,
        )
        val flavor = nonEmpty(card.flavor)
        if (flavor != null) {
            Text("“$flavor”", color = Vault.foreground, fontSize = 14.sp, fontStyle = FontStyle.Italic, textAlign = TextAlign.Center)
        }
        Column(Modifier.fillMaxWidth().cardStyle().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Category: ${rwCapitalized(nonEmpty(card.category) ?: "—")}", color = Vault.foreground, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(
                if (owned != null) "Status: Owned ×${owned.count}" else "Not collected yet",
                color = if (owned != null) Vault.emerald else Vault.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold,
            )
        }
        if (owned != null) {
            PrimaryButton(
                if (owned.favorite) "Favorite" else "Add to favorites",
                onClick = onFavorite,
                modifier = Modifier.fillMaxWidth(),
                icon = if (owned.favorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                color = if (owned.favorite) Vault.primary else Vault.secondary,
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}
