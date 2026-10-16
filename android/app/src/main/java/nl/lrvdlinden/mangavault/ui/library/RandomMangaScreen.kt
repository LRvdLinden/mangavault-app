package nl.lrvdlinden.mangavault.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import nl.lrvdlinden.mangavault.app.LocalApp
import nl.lrvdlinden.mangavault.app.Route
import nl.lrvdlinden.mangavault.core.Base44
import nl.lrvdlinden.mangavault.model.CollectionItem
import nl.lrvdlinden.mangavault.model.Manga
import nl.lrvdlinden.mangavault.model.ReadStatus
import nl.lrvdlinden.mangavault.model.WishlistItem
import nl.lrvdlinden.mangavault.model.nonEmpty
import nl.lrvdlinden.mangavault.ui.components.Badge
import nl.lrvdlinden.mangavault.ui.components.Chip
import nl.lrvdlinden.mangavault.ui.components.CoverInfo
import nl.lrvdlinden.mangavault.ui.components.CoverView
import nl.lrvdlinden.mangavault.ui.components.EmptyState
import nl.lrvdlinden.mangavault.ui.components.OutlineButton
import nl.lrvdlinden.mangavault.ui.components.PrimaryButton
import nl.lrvdlinden.mangavault.ui.components.ScreenHeader
import nl.lrvdlinden.mangavault.ui.components.cardStyle
import nl.lrvdlinden.mangavault.ui.theme.Vault

private enum class RandomSource(val raw: String, val label: String, val icon: ImageVector) {
    WISHLIST("wishlist", "From your wishlist", Icons.Filled.Favorite),
    UNREAD("unread", "Unread from your collection", Icons.AutoMirrored.Filled.MenuBook),
    GENRE("genre", "From a genre", Icons.Filled.Bookmark),
    NOTOWNED("notowned", "Manga you do not own", Icons.Filled.Explore),
    FAVORITES("favorites", "From your favorites", Icons.Filled.Star),
}

private data class RandomPick(
    val title: String,
    val cover: CoverInfo,
    val mangaId: String,
    val volume: Int?,
    val genre: String?,
    val owned: Boolean,
)

private suspend fun randomGenreOf(mangaId: String?): String? {
    val id = nonEmpty(mangaId) ?: return null
    val m = try { Base44.get<Manga>("Manga", id) } catch (e: Exception) { null }
    return nonEmpty(m?.genre)
}

private suspend fun randomShelfPick(it: CollectionItem): RandomPick {
    val g = randomGenreOf(it.manga_id)
    val vol: Int? = if (it.isOmnibus) null else it.volume
    return RandomPick(it.titleText, CoverInfo.of(it), it.manga_id ?: "", vol, g, owned = true)
}

private fun randomMangaPick(m: Manga, owned: Boolean) =
    RandomPick(m.title, CoverInfo.of(m), m.id, null, m.genre, owned)

private suspend fun randomPickOne(source: RandomSource, genre: String?, genres: List<String>): RandomPick {
    return when (source) {
        RandomSource.WISHLIST -> {
            val items = Base44.list<WishlistItem>("WishlistItem", limit = 500).items
            val w = items.randomOrNull() ?: throw LibMessage("Your wishlist is empty")
            val g = randomGenreOf(w.manga_id)
            val vol: Int? = if (w.isOmnibus) null else w.volume
            RandomPick(w.title ?: "", CoverInfo.of(w), w.manga_id ?: "", vol, g, owned = false)
        }
        RandomSource.UNREAD -> {
            val items = Base44.list<CollectionItem>("CollectionItem", limit = 1000).items
            val unread = items.filter { it.effectiveStatus != ReadStatus.COMPLETED }
            val it = unread.randomOrNull() ?: throw LibMessage("No unread volumes in your collection")
            randomShelfPick(it)
        }
        RandomSource.FAVORITES -> {
            val items = Base44.list<CollectionItem>("CollectionItem", query = mapOf("favorite" to true), limit = 500).items
            val it = items.randomOrNull() ?: throw LibMessage("No favorites marked yet")
            randomShelfPick(it)
        }
        RandomSource.NOTOWNED -> {
            val owned = Base44.distinct("CollectionItem", "manga_id").toSet()
            val page = Base44.list<Manga>("Manga", sort = "-created_date", limit = 500, fields = Manga.listFields)
            val candidates = page.items.filter { !owned.contains(it.id) }
            val m = candidates.randomOrNull() ?: throw LibMessage("You own everything shown here!")
            randomMangaPick(m, owned = false)
        }
        RandomSource.GENRE -> {
            val g = genre ?: genres.firstOrNull() ?: ""
            if (g.isEmpty()) throw LibMessage("No manga found for $g")
            val page = Base44.list<Manga>("Manga", query = mapOf("genre" to libRegex(g)), limit = 200, fields = Manga.listFields)
            val m = page.items.randomOrNull() ?: throw LibMessage("No manga found for $g")
            val ownedList = try { Base44.distinct("CollectionItem", "manga_id") } catch (e: Exception) { emptyList() }
            randomMangaPick(m, owned = ownedList.contains(m.id))
        }
    }
}

/** Random Manga: let the vault pick your next read from a chosen source. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RandomMangaScreen() {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    var source by remember { mutableStateOf(RandomSource.UNREAD) }
    var genres by remember { mutableStateOf<List<String>>(emptyList()) }
    var genre by remember { mutableStateOf<String?>(null) }
    var rolling by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<RandomPick?>(null) }

    suspend fun roll() {
        if (rolling) return
        rolling = true
        result = null
        try {
            val pick = randomPickOne(source, genre, genres)
            delay(600)
            result = pick
        } catch (m: LibMessage) {
            app.show(m.text, error = true)
        } catch (e: Exception) {
            app.fail("Could not roll", e)
        }
        rolling = false
    }

    LaunchedEffect(Unit) {
        if (genres.isEmpty()) {
            val set = try { libDistinctGenres(lowercase = false) } catch (e: Exception) { null }
            if (set != null) genres = set.sortedWith(String.CASE_INSENSITIVE_ORDER)
        }
    }

    Column(
        Modifier.fillMaxSize().background(Vault.background).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        ScreenHeader(
            lead = "RANDOM", accent = "MANGA",
            eyebrow = "Random Discovery", eyebrowIcon = Icons.Filled.Casino,
            subtitle = "Can't decide what to read next? Let the vault pick for you.",
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            LibFieldLabel("Source")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (s in RandomSource.entries) {
                    Chip(s.label, source == s, icon = s.icon) { source = s }
                }
            }
        }
        if (source == RandomSource.GENRE) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LibFieldLabel("Genre")
                if (genres.isEmpty()) {
                    Text("Loading genres...", color = Vault.muted, fontSize = 12.sp)
                } else {
                    val selected = genre ?: genres.firstOrNull()
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (g in genres.take(20)) {
                            Chip(g, selected == g) { genre = g }
                        }
                    }
                }
            }
        }
        PrimaryButton(
            if (rolling) "Rolling..." else "Roll the dice",
            { scope.launch { roll() } },
            Modifier.fillMaxWidth(),
            enabled = !rolling,
            icon = Icons.Filled.Casino,
        )
        val r = result
        when {
            rolling -> {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 50.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(color = Vault.primary, modifier = Modifier.size(40.dp))
                    Text("Rolling...", color = Vault.muted, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
            r != null -> RandomResultCard(
                r,
                onOpen = {
                    app.open(if (r.volume != null) Route.Volume(r.mangaId, r.volume) else Route.Manga(r.mangaId))
                },
                onReroll = { scope.launch { roll() } },
            )
            else -> EmptyState(Icons.Filled.Casino, "Choose a source and roll the dice to discover your next read.")
        }
    }
}

@Composable
private fun RandomResultCard(r: RandomPick, onOpen: () -> Unit, onReroll: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().cardStyle(highlighted = true).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.width(170.dp).shadow(16.dp, RoundedCornerShape(10.dp), ambientColor = Vault.primary, spotColor = Vault.primary),
        ) {
            CoverView(r.cover, Modifier.fillMaxWidth())
            Badge("YOUR PICK", Modifier.align(Alignment.TopStart).padding(8.dp), icon = Icons.Filled.AutoAwesome)
        }
        Text(
            if (r.owned) "From your collection" else "Not in your collection",
            color = if (r.owned) Vault.emerald else Vault.sky, fontSize = 12.sp, fontWeight = FontWeight.Bold,
        )
        Text(r.title, color = Vault.foreground, fontSize = 20.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
        if (r.volume != null) Text("Volume ${r.volume}", color = Vault.muted, fontSize = 14.sp)
        nonEmpty(r.genre)?.let { Text(it, color = Vault.muted, fontSize = 12.sp, textAlign = TextAlign.Center) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton("Open details", onOpen, Modifier.weight(1f), enabled = r.mangaId.isNotEmpty())
            OutlineButton("Reroll", onReroll, Modifier.weight(1f), icon = Icons.Filled.Refresh)
        }
    }
}
