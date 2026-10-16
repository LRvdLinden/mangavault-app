package nl.lrvdlinden.mangavault.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import nl.lrvdlinden.mangavault.app.LocalApp
import nl.lrvdlinden.mangavault.app.Route
import nl.lrvdlinden.mangavault.core.Base44
import nl.lrvdlinden.mangavault.core.Config
import nl.lrvdlinden.mangavault.core.VaultService
import nl.lrvdlinden.mangavault.core.shareText
import nl.lrvdlinden.mangavault.model.CollectionItem
import nl.lrvdlinden.mangavault.model.Manga
import nl.lrvdlinden.mangavault.model.VolumeCover
import nl.lrvdlinden.mangavault.model.WishlistItem
import nl.lrvdlinden.mangavault.model.euro
import nl.lrvdlinden.mangavault.model.nonEmpty
import nl.lrvdlinden.mangavault.ui.components.Badge
import nl.lrvdlinden.mangavault.ui.components.CoverInfo
import nl.lrvdlinden.mangavault.ui.components.CoverView
import nl.lrvdlinden.mangavault.ui.components.EmptyState
import nl.lrvdlinden.mangavault.ui.components.LoadingView
import nl.lrvdlinden.mangavault.ui.components.OutlineButton
import nl.lrvdlinden.mangavault.ui.components.PrimaryButton
import nl.lrvdlinden.mangavault.ui.components.ProgressBar
import nl.lrvdlinden.mangavault.ui.components.RemoteImage
import nl.lrvdlinden.mangavault.ui.components.cardStyle
import nl.lrvdlinden.mangavault.ui.theme.Vault
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt

@Serializable
private data class MdVolRef(
    val id: String = "",
    val volume_number: Double? = null,
    val edition: String? = null,
    val status: String? = null,
    val read: Boolean? = null,
)

/** volume key ("1", "2", … or "omnibus") → record id. */
private fun mdMapRefs(refs: List<MdVolRef>): Map<String, String> {
    val m = mutableMapOf<String, String>()
    for (r in refs) {
        if (r.edition == "omnibus") {
            if (m["omnibus"] == null) m["omnibus"] = r.id
        } else {
            val v = r.volume_number ?: continue
            val k = v.toInt().toString()
            if (m[k] == null) m[k] = r.id
        }
    }
    return m
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MangaDetailScreen(mangaId: String) {
    val app = LocalApp.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var manga by remember { mutableStateOf<Manga?>(null) }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var owned by remember { mutableStateOf(mapOf<String, String>()) }
    var wished by remember { mutableStateOf(mapOf<String, String>()) }
    var covers by remember { mutableStateOf(listOf<VolumeCover>()) }
    var coversLoading by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf<String?>(null) }
    var wishBusy by remember { mutableStateOf<String?>(null) }
    var showFullDescription by remember { mutableStateOf(false) }
    var completedIds by remember { mutableStateOf(setOf<String>()) }
    var reloadTick by remember { mutableIntStateOf(0) }

    fun volumeCount(m: Manga): Int = if (covers.isEmpty()) maxOf(m.totalVolumes, 1) else covers.size
    fun coverUrl(v: Int): String = covers.firstOrNull { it.volume.toInt() == v }?.url ?: ""

    suspend fun load() {
        try {
            val (m, c, w) = coroutineScope {
                val mT = async { Base44.get<Manga>("Manga", mangaId) }
                val cT = async {
                    Base44.list<MdVolRef>("CollectionItem", query = mapOf("manga_id" to mangaId), limit = 500,
                        fields = listOf("id", "volume_number", "edition", "status", "read"))
                }
                val wT = async {
                    Base44.list<MdVolRef>("WishlistItem", query = mapOf("manga_id" to mangaId), limit = 500,
                        fields = listOf("id", "volume_number", "edition"))
                }
                Triple(mT.await(), cT.await(), wT.await())
            }
            manga = m
            owned = mdMapRefs(c.items)
            completedIds = c.items.filter { r ->
                (r.status ?: if (r.read == true) "completed" else "plan_to_read") == "completed"
            }.map { it.id }.toSet()
            wished = mdMapRefs(w.items)
            loading = false
            if (!m.isOmnibus) {
                coversLoading = (m.volume_covers ?: emptyList()).isEmpty() && m.mangadex_id != null
                covers = VaultService.volumeCovers(m)
                coversLoading = false
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            app.fail("Could not load manga", e)
            loading = false
            coversLoading = false
        }
    }

    suspend fun toggleVolume(m: Manga, v: Int) {
        val key = v.toString()
        busy = key
        try {
            val id = owned[key]
            if (id != null) {
                try {
                    Base44.delete("CollectionItem", id)
                    owned = owned - key
                    app.show("Removed Vol. $v")
                    app.dataChanged()
                } catch (e: Exception) { app.fail("Could not remove", e) }
            } else {
                try {
                    val item = Base44.create<CollectionItem>("CollectionItem",
                        VaultService.collectionPayload(m, v, coverUrl(v)))
                    owned = owned + (key to item.id)
                    VaultService.log("added", m.id, item.title, v)
                    app.show("Added Vol. $v to your collection")
                    app.dataChanged()
                } catch (e: Exception) { app.fail("Could not add", e) }
            }
        } finally {
            busy = null
        }
    }

    suspend fun toggleWish(m: Manga, v: Int) {
        val key = v.toString()
        wishBusy = key
        try {
            val id = wished[key]
            if (id != null) {
                try {
                    Base44.delete("WishlistItem", id)
                    wished = wished - key
                    app.show("Removed Vol. $v from wishlist")
                    app.dataChanged()
                } catch (e: Exception) { app.fail("Could not remove", e) }
            } else {
                try {
                    val item = Base44.create<WishlistItem>("WishlistItem",
                        VaultService.wishlistPayload(m, v, coverUrl(v)))
                    wished = wished + (key to item.id)
                    app.show("Added Vol. $v to your wishlist")
                    app.dataChanged()
                } catch (e: Exception) { app.fail("Could not add to wishlist", e) }
            }
        } finally {
            wishBusy = null
        }
    }

    suspend fun toggleOmnibusCollection(m: Manga) {
        busy = "omnibus"
        try {
            val id = owned["omnibus"]
            if (id != null) {
                try {
                    Base44.delete("CollectionItem", id)
                    owned = owned - "omnibus"
                    app.show("Removed \"${m.title}\""); app.dataChanged()
                } catch (e: Exception) { app.fail("Could not remove", e) }
            } else {
                try {
                    val item = Base44.create<CollectionItem>("CollectionItem",
                        VaultService.collectionPayload(m, null, ""))
                    owned = owned + ("omnibus" to item.id)
                    VaultService.log("added", m.id, m.title, null, meta = "omnibus")
                    app.show("Added \"${m.title}\" to your collection"); app.dataChanged()
                } catch (e: Exception) { app.fail("Could not add", e) }
            }
        } finally {
            busy = null
        }
    }

    suspend fun toggleOmnibusWishlist(m: Manga) {
        wishBusy = "omnibus"
        try {
            val id = wished["omnibus"]
            if (id != null) {
                try {
                    Base44.delete("WishlistItem", id)
                    wished = wished - "omnibus"
                    app.show("Removed \"${m.title}\" from wishlist"); app.dataChanged()
                } catch (e: Exception) { app.fail("Could not remove", e) }
            } else {
                try {
                    val item = Base44.create<WishlistItem>("WishlistItem",
                        VaultService.wishlistPayload(m, null, ""))
                    wished = wished + ("omnibus" to item.id)
                    app.show("Added \"${m.title}\" to your wishlist"); app.dataChanged()
                } catch (e: Exception) { app.fail("Could not add", e) }
            }
        } finally {
            wishBusy = null
        }
    }

    LaunchedEffect(mangaId, reloadTick) {
        load()
        refreshing = false
    }

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = { refreshing = true; reloadTick++ },
        modifier = Modifier.fillMaxSize().background(Vault.background),
    ) {
        val m = manga
        LazyVerticalGrid(
            columns = GridCells.Adaptive(100.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            when {
                loading -> item(span = { GridItemSpan(maxLineSpan) }) { LoadingView() }
                m == null -> item(span = { GridItemSpan(maxLineSpan) }) { EmptyState(Icons.Filled.Info, "Manga not found.") }
                else -> {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "header") {
                        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                            // Cover + share (iOS keeps share in the toolbar).
                            Box(Modifier.fillMaxWidth()) {
                                CoverView(
                                    CoverInfo.of(m),
                                    Modifier.align(Alignment.Center).width(190.dp)
                                        .shadow(20.dp, RoundedCornerShape(14.dp)),
                                    cornerRadius = 14.dp,
                                )
                                IconButton(
                                    onClick = { shareText(context, "${Config.APP_BASE_URL}/manga/${m.id}") },
                                    modifier = Modifier.align(Alignment.TopEnd),
                                ) {
                                    Icon(Icons.Filled.Share, "Share", tint = Vault.muted)
                                }
                            }
                            MdTitleBlock(m, showFullDescription) { showFullDescription = !showFullDescription }

                            Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                                MdStat(
                                    if (m.isOmnibus) "Volumes Included" else "Volumes",
                                    if (m.isOmnibus) (nonEmpty(m.volumes_included) ?: "${m.totalVolumes}") else "${volumeCount(m)}",
                                )
                                MdStat("Price", m.priceValue.euro(), Vault.primary)
                                if (!m.isOmnibus) MdStat("In Collection", "${owned.size} / ${volumeCount(m)}")
                            }

                            if (!m.isOmnibus && owned.isNotEmpty()) {
                                val readCount = owned.values.count { it in completedIds }
                                MdSeriesProgress(volumeCount(m), owned.size, readCount) { app.open(Route.Series) }
                            }

                            if (m.isOmnibus) {
                                val inColl = owned["omnibus"] != null
                                val onWish = wished["omnibus"] != null
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    PrimaryButton(
                                        text = if (inColl) "In Your Collection" else "Add Omnibus to Collection",
                                        onClick = { scope.launch { toggleOmnibusCollection(m) } },
                                        modifier = Modifier.weight(1f),
                                        loading = busy == "omnibus",
                                        icon = if (inColl) Icons.Filled.CheckCircle else Icons.Filled.Add,
                                        color = if (inColl) Vault.secondary else Vault.primary,
                                    )
                                    OutlineButton(
                                        text = if (onWish) "On Wishlist" else "Add to Wishlist",
                                        onClick = { scope.launch { toggleOmnibusWishlist(m) } },
                                        modifier = Modifier.weight(1f),
                                        enabled = wishBusy != "omnibus",
                                        icon = if (onWish) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                        color = if (onWish) Vault.red else Vault.foreground,
                                        borderColor = if (onWish) Vault.red.copy(alpha = 0.5f) else Vault.border,
                                    )
                                }
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Filled.Book, null, tint = Vault.muted, modifier = Modifier.size(14.dp))
                                    Text("Add the volumes you own below · tap the heart to save to your wishlist.",
                                        color = Vault.muted, fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    if (!m.isOmnibus) {
                        val count = volumeCount(m)
                        item(span = { GridItemSpan(maxLineSpan) }, key = "volumesHeader") {
                            Column(Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.AutoMirrored.Filled.MenuBook, null, tint = Vault.primary, modifier = Modifier.size(20.dp))
                                    Text("ALL VOLUMES", color = Vault.foreground, fontSize = 20.sp, fontWeight = FontWeight.Black)
                                    if (coversLoading) CircularProgressIndicator(Modifier.size(16.dp), color = Vault.primary, strokeWidth = 2.dp)
                                }
                                Text("${owned.size} of $count in collection · ${wished.size} on wishlist",
                                    color = Vault.muted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        items(count, key = { "v-${it + 1}" }) { idx ->
                            val v = idx + 1
                            val key = v.toString()
                            MdVolumeTile(
                                volume = v,
                                url = coverUrl(v),
                                isOwned = owned[key] != null,
                                isWished = wished[key] != null,
                                ownBusy = busy == key,
                                wishLoading = wishBusy == key,
                                onOpen = { app.open(Route.Volume(m.id, v)) },
                                onToggleOwned = { scope.launch { toggleVolume(m, v) } },
                                onToggleWish = { scope.launch { toggleWish(m, v) } },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MdTitleBlock(m: Manga, expanded: Boolean, onToggle: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (m.isOmnibus) Badge("OMNIBUS · 3-IN-1", icon = Icons.AutoMirrored.Filled.LibraryBooks, fg = Vault.primary,
                bg = Vault.background, ring = Vault.primary.copy(alpha = 0.5f))
            if (m.isShonenJump) Badge("SHONEN JUMP")
            nonEmpty(m.publisher)?.let { p -> Badge(p.uppercase(), fg = Vault.muted, bg = Vault.card, ring = Vault.border) }
        }
        Text(m.title, color = Vault.foreground, fontSize = 30.sp, fontWeight = FontWeight.Black, letterSpacing = (-1).sp, lineHeight = 32.sp)
        val of = nonEmpty(m.omnibus_of)
        if (m.isOmnibus && of != null) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = Vault.muted)) { append("Omnibus edition of ") }
                    withStyle(SpanStyle(color = Vault.primary, fontWeight = FontWeight.Bold)) { append(of) }
                    withStyle(SpanStyle(color = Vault.muted)) { append(" · ${m.volumes_included ?: ""}") }
                },
                fontSize = 14.sp,
            )
        }
        nonEmpty(m.genre)?.let { Text(it, color = Vault.muted, fontSize = 14.sp) }
        nonEmpty(m.description)?.let { d ->
            Text(
                d, color = Vault.muted, fontSize = 14.sp,
                maxLines = if (expanded) Int.MAX_VALUE else 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable(onClick = onToggle),
            )
        }
    }
}

@Composable
private fun MdStat(label: String, value: String, color: Color = Vault.foreground) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label.uppercase(), color = Vault.muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Text(value, color = color, fontSize = 22.sp, fontWeight = FontWeight.Black)
    }
}

/** "Series Progress" card (owned vs. read). */
@Composable
private fun MdSeriesProgress(total: Int, ownedCount: Int, readCount: Int, onAllSeries: () -> Unit) {
    val collPct = if (total > 0) minOf(100, (ownedCount.toDouble() / total * 100).roundToInt()) else 0
    val readPct = if (ownedCount > 0) (readCount.toDouble() / ownedCount * 100).roundToInt() else 0
    Column(Modifier.fillMaxWidth().cardStyle().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Filled.LibraryBooks, null, tint = Vault.primary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("Series Progress", color = Vault.foreground, fontSize = 14.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
            Text("All series", color = Vault.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable(onClick = onAllSeries))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = Vault.muted)) { append("Owned: ") }
                    withStyle(SpanStyle(color = Vault.foreground, fontWeight = FontWeight.Bold)) { append("$ownedCount / $total") }
                },
                fontSize = 12.sp,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CheckCircle, null, tint = Vault.emerald, modifier = Modifier.size(12.dp))
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = Vault.muted)) { append(" Read: ") }
                        withStyle(SpanStyle(color = Vault.foreground, fontWeight = FontWeight.Bold)) { append("$readCount") }
                    },
                    fontSize = 12.sp,
                )
            }
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = Vault.muted)) { append("Unread: ") }
                    withStyle(SpanStyle(color = Vault.foreground, fontWeight = FontWeight.Bold)) { append("${ownedCount - readCount}") }
                },
                fontSize = 12.sp,
            )
        }
        ProgressBar(collPct / 100f, height = 6.dp)
        ProgressBar(readPct / 100f, height = 6.dp, color = Vault.emerald)
        Row {
            Text("Collection $collPct%", color = Vault.muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("Read $readPct%", color = Vault.muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun MdVolumeTile(
    volume: Int,
    url: String,
    isOwned: Boolean,
    isWished: Boolean,
    ownBusy: Boolean,
    wishLoading: Boolean,
    onOpen: () -> Unit,
    onToggleOwned: () -> Unit,
    onToggleWish: () -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f)) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(shape)
                .border(
                    if (isOwned) 2.dp else 1.dp,
                    if (isOwned) Vault.primary else if (isWished) Vault.red.copy(alpha = 0.6f) else Vault.border,
                    shape,
                )
                .clickable(onClick = onOpen),
        ) {
            if (url.isEmpty()) {
                Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Vault.primary.copy(alpha = 0.3f), Vault.background))))
            } else {
                RemoteImage(url, Modifier.fillMaxSize())
            }
            Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(50.dp)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f)))),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Text("VOL. $volume", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp,
                    modifier = Modifier.padding(bottom = 6.dp))
            }
        }
        MdCircleButton(
            icon = if (isOwned) Icons.Filled.Check else Icons.Filled.Add,
            active = isOwned, color = Vault.primary, loading = ownBusy, onClick = onToggleOwned,
            modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
        )
        MdCircleButton(
            icon = if (isWished) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            active = isWished, color = Vault.red, loading = wishLoading, onClick = onToggleWish,
            modifier = Modifier.align(Alignment.TopStart).padding(6.dp),
        )
    }
}

@Composable
private fun MdCircleButton(
    icon: ImageVector,
    active: Boolean,
    color: Color,
    loading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .size(30.dp)
            .shadow(4.dp, CircleShape)
            .clip(CircleShape)
            .background(if (active) color else Vault.background.copy(alpha = 0.75f), CircleShape)
            .border(1.dp, if (active) Color.Transparent else color.copy(alpha = 0.5f), CircleShape)
            .clickable(enabled = !loading, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) CircularProgressIndicator(Modifier.size(12.dp), color = Color.White, strokeWidth = 1.5.dp)
        else Icon(icon, null, tint = if (active) Color.White else color, modifier = Modifier.size(14.dp))
    }
}
