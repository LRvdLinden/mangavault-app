package nl.lrvdlinden.mangavault.ui.catalog

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import nl.lrvdlinden.mangavault.app.AppState
import nl.lrvdlinden.mangavault.app.LocalApp
import nl.lrvdlinden.mangavault.app.Route
import nl.lrvdlinden.mangavault.core.Base44
import nl.lrvdlinden.mangavault.core.VaultService
import nl.lrvdlinden.mangavault.core.obj
import nl.lrvdlinden.mangavault.core.openInBrowser
import nl.lrvdlinden.mangavault.core.parseObject
import nl.lrvdlinden.mangavault.model.CollectionItem
import nl.lrvdlinden.mangavault.model.DayKey
import nl.lrvdlinden.mangavault.model.Manga
import nl.lrvdlinden.mangavault.model.ReadStatus
import nl.lrvdlinden.mangavault.model.VolumeCover
import nl.lrvdlinden.mangavault.model.VolumeSynopsis
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
import nl.lrvdlinden.mangavault.ui.components.RemoteImage
import nl.lrvdlinden.mangavault.ui.components.StarRating
import nl.lrvdlinden.mangavault.ui.components.cardStyle
import nl.lrvdlinden.mangavault.ui.theme.Vault
import kotlin.coroutines.cancellation.CancellationException

/** In-memory caches, like the Maps the web app keeps for the volume page. */
private object VdCache {
    val manga = mutableMapOf<String, Manga>()
    /** mangadex id → (fetched at millis, volume → chapters). */
    val chapters = mutableMapOf<String, Pair<Long, Map<String, List<String>>>>()
    val synopsis = mutableMapOf<String, VolumeSynopsis>()
    /** "mangaId:volume" → verified MangaFire URL ("" = checked, none found). */
    val mangaFire = mutableMapOf<String, String>()
}

@Serializable
private data class VdFireResponse(
    val url: String? = null,
    val slug: String? = null,
    val volume_url: String? = null,
    val verified: Boolean? = null,
)

@Serializable
private data class VdCoversResponse(val covers: List<VolumeCover>? = null)

private val vdSlugRegex = Regex("^[a-z0-9]{3,12}-[a-z0-9][a-z0-9-]*[a-z0-9]\$")
private const val VD_LOCATION_PLACEHOLDER = "Location information not available yet."

/** JSON scalar (string or number) → string; 3.0 → "3". */
private fun vdLooseString(p: JsonPrimitive): String {
    if (p is JsonNull) return ""
    if (p.isString) return p.content
    val d = p.doubleOrNull ?: return p.content
    return if (d == Math.floor(d) && !d.isInfinite()) d.toLong().toString() else d.toString()
}

private fun vdStatusBg(s: ReadStatus): Color = when (s) {
    ReadStatus.PLAN_TO_READ -> Vault.secondary
    ReadStatus.READING -> Vault.primary
    ReadStatus.COMPLETED -> Vault.emerald
    ReadStatus.ON_HOLD -> Vault.amber
    ReadStatus.DROPPED -> Vault.secondary
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VolumeDetailScreen(mangaId: String, volume: Int) {
    val app = LocalApp.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var vol by remember { mutableStateOf(volume) }
    var manga by remember { mutableStateOf<Manga?>(null) }
    var loading by remember { mutableStateOf(true) }
    var cover by remember { mutableStateOf("") }
    var maxVolume by remember { mutableStateOf(1) }
    var item by remember { mutableStateOf<CollectionItem?>(null) }
    var wish by remember { mutableStateOf<WishlistItem?>(null) }
    var busy by remember { mutableStateOf(false) }
    var wishBusy by remember { mutableStateOf(false) }
    var synopsis by remember { mutableStateOf<VolumeSynopsis?>(null) }
    var synopsisLoading by remember { mutableStateOf(false) }
    var chapters by remember { mutableStateOf(listOf<String>()) }
    var editing by remember { mutableStateOf<CollectionItem?>(null) }
    var mangaFireUrl by remember { mutableStateOf<String?>(null) }
    var mangaFireLoading by remember { mutableStateOf(false) }
    var statusMenu by remember { mutableStateOf(false) }

    // MARK: Data

    /** Same rules as the web app: a stored `mangaFireSlug` gives a per-volume URL; otherwise ask `resolveMangaFireUrl`. */
    suspend fun loadMangaFire(m: Manga, v: Int) {
        val key = "${m.id}:$v"
        val slug = (m.mangaFireSlug ?: "").trim()
        if (vdSlugRegex.matches(slug)) {
            val u = "https://mangafire.to/title/$slug/volume/$v"
            mangaFireUrl = u
            VdCache.mangaFire[key] = u
            return
        }
        VdCache.mangaFire[key]?.let { cached ->
            mangaFireUrl = cached.ifEmpty { null }
            return
        }
        mangaFireLoading = true
        try {
            val r = try {
                Base44.decode<VdFireResponse>(Base44.invoke("resolveMangaFireUrl", mapOf("manga_id" to m.id, "volume" to v)))
            } catch (e: CancellationException) {
                throw e
            } catch (ignored: Exception) {
                null
            }
            val sl = nonEmpty(r?.slug)
            if (r != null && r.verified == true && sl != null) {
                val u = nonEmpty(r.volume_url) ?: "https://mangafire.to/title/$sl/volume/$v"
                mangaFireUrl = u
                VdCache.mangaFire[key] = u
                VdCache.manga[m.id] = (VdCache.manga[m.id] ?: m).copy(mangaFireSlug = sl, mangaFireUrl = r.url)
            } else {
                mangaFireUrl = null
            }
        } finally {
            mangaFireLoading = false
        }
    }

    suspend fun loadExtras(m: Manga, v: Int, storedCount: Int, hasCover: Boolean) {
        val mdx = nonEmpty(m.mangadex_id) ?: return
        if (!hasCover) {
            val r = try {
                Base44.decode<VdCoversResponse>(Base44.invoke("getVolumeCovers", mapOf("mangadex_id" to mdx, "manga_id" to m.id)))
            } catch (e: CancellationException) {
                throw e
            } catch (ignored: Exception) {
                null
            }
            if (r != null) {
                val all = (r.covers ?: emptyList()).filter { it.volume > 0 && nonEmpty(it.url) != null }
                if (all.size > storedCount) {
                    val mx = all.maxOfOrNull { it.volume.toInt() } ?: 0
                    maxVolume = if (mx > 0) mx else maxOf(m.totalVolumes, 1)
                    all.firstOrNull { it.volume.toInt() == v }?.url?.let { cover = it }
                    VdCache.manga[m.id] = (VdCache.manga[m.id] ?: m).copy(volume_covers = all)
                }
            }
        }
        val cached = VdCache.chapters[mdx]
        val vols: Map<String, List<String>> =
            if (cached != null && System.currentTimeMillis() - cached.first < 600_000) {
                cached.second
            } else {
                try {
                    val o = parseObject(Base44.invoke("getVolumeChapters", mapOf("mangadex_id" to mdx)))
                    val volsObj = o.obj("volumes")
                    val parsed = volsObj?.entries?.associate { (k, arr) ->
                        k to ((arr as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.let(::vdLooseString) } ?: emptyList())
                    } ?: emptyMap()
                    VdCache.chapters[mdx] = System.currentTimeMillis() to parsed
                    parsed
                } catch (e: CancellationException) {
                    throw e
                } catch (ignored: Exception) {
                    emptyMap()
                }
            }
        chapters = vols[v.toString()] ?: emptyList()
    }

    suspend fun loadSynopsis(m: Manga, v: Int) {
        val key = "${m.id}:$v"
        VdCache.synopsis[key]?.let { synopsis = it; return }
        synopsisLoading = true
        try {
            val payload = mapOf<String, Any?>(
                "title" to m.title, "description" to (m.description ?: ""), "genre" to (m.genre ?: ""),
                "volume" to v, "total_volumes" to m.totalVolumes,
            )
            val s = try {
                Base44.decode<VolumeSynopsis>(Base44.invoke("getVolumeSynopsis", payload))
            } catch (e: CancellationException) {
                throw e
            } catch (ignored: Exception) {
                null
            }
            if (s != null) {
                synopsis = s
                VdCache.synopsis[key] = s
            }
        } finally {
            synopsisLoading = false
        }
    }

    suspend fun loadUserState(v: Int) {
        coroutineScope {
            launch {
                try {
                    val c = Base44.list<CollectionItem>("CollectionItem", query = mapOf("manga_id" to mangaId), limit = 500)
                    item = c.items.firstOrNull { !it.isOmnibus && it.volume == v }
                } catch (e: CancellationException) {
                    throw e
                } catch (ignored: Exception) {
                }
            }
            launch {
                try {
                    val w = Base44.list<WishlistItem>("WishlistItem", query = mapOf("manga_id" to mangaId), limit = 500)
                    wish = w.items.firstOrNull { !it.isOmnibus && it.volume == v }
                } catch (e: CancellationException) {
                    throw e
                } catch (ignored: Exception) {
                }
            }
        }
    }

    suspend fun load(v: Int) {
        loading = manga == null
        synopsis = null; chapters = emptyList(); item = null; wish = null
        try {
            val m = VdCache.manga[mangaId] ?: Base44.get<Manga>("Manga", mangaId).also { VdCache.manga[mangaId] = it }
            manga = m
            val stored = (m.volume_covers ?: emptyList()).filter { it.volume > 0 && nonEmpty(it.url) != null }
            val found = stored.firstOrNull { it.volume.toInt() == v }
            val maxStored = stored.maxOfOrNull { it.volume.toInt() } ?: 0
            maxVolume = if (maxStored > 0) maxStored else maxOf(m.totalVolumes, 1)
            cover = found?.url ?: ""
            loading = false

            coroutineScope {
                launch { loadSynopsis(m, v) }
                launch { loadExtras(m, v, stored.size, found != null) }
                launch { loadUserState(v) }
                launch { loadMangaFire(m, v) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            app.fail("Could not load volume", e)
            loading = false
        }
    }

    suspend fun add() {
        val m = manga ?: return
        if (item != null) return
        busy = true
        try {
            val created = Base44.create<CollectionItem>("CollectionItem",
                VaultService.collectionPayload(m, vol, cover, ReadStatus.PLAN_TO_READ))
            item = created
            VaultService.log("added", m.id, created.title, vol)
            app.show("Added Vol. $vol to your collection"); app.dataChanged()
        } catch (e: Exception) {
            app.fail("Could not add", e)
        } finally {
            busy = false
        }
    }

    suspend fun remove() {
        val it = item ?: return
        busy = true
        try {
            Base44.delete("CollectionItem", it.id)
            item = null
            app.show("Removed Vol. $vol"); app.dataChanged()
        } catch (e: Exception) {
            app.fail("Could not remove", e)
        } finally {
            busy = false
        }
    }

    suspend fun setStatus(s: ReadStatus) {
        val it = item ?: return
        val m = manga ?: return
        busy = true
        try {
            Base44.update("CollectionItem", it.id, mapOf("status" to s.raw))
            item = it.copy(status = s.raw)
            VaultService.log(if (s == ReadStatus.COMPLETED) "completed" else "status", m.id, it.title, vol)
            app.show("Status updated"); app.dataChanged()
        } catch (e: Exception) {
            app.fail("Update failed", e)
        } finally {
            busy = false
        }
    }

    suspend fun toggleWish() {
        val m = manga ?: return
        wishBusy = true
        try {
            val w = wish
            if (w != null) {
                try {
                    Base44.delete("WishlistItem", w.id)
                    wish = null
                    app.show("Removed Vol. $vol from wishlist"); app.dataChanged()
                } catch (e: Exception) { app.fail("Could not remove", e) }
            } else {
                try {
                    wish = Base44.create<WishlistItem>("WishlistItem", VaultService.wishlistPayload(m, vol, cover))
                    app.show("Added Vol. $vol to your wishlist"); app.dataChanged()
                } catch (e: Exception) { app.fail("Could not add to wishlist", e) }
            }
        } finally {
            wishBusy = false
        }
    }

    LaunchedEffect(vol) { load(vol) }

    val scroll = rememberScrollState()
    LaunchedEffect(vol) { scroll.scrollTo(0) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Vault.background)
            .verticalScroll(scroll)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        val m = manga
        when {
            loading -> LoadingView()
            m == null -> EmptyState(Icons.Filled.Info, "Manga not found.")
            else -> {
                // MARK: Header
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        VdCover(cover, vol)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (m.isShonenJump) Badge("SHONEN JUMP")
                        nonEmpty(m.publisher)?.let { p -> Badge(p.uppercase(), fg = Vault.muted, bg = Vault.card, ring = Vault.border) }
                        item?.let { it ->
                            val st = it.effectiveStatus
                            Badge(st.volumeLabel.uppercase(), fg = if (st == ReadStatus.ON_HOLD) Color.Black else Color.White, bg = vdStatusBg(st))
                        }
                    }
                    Text(
                        m.title, color = Vault.foreground, fontSize = 28.sp, fontWeight = FontWeight.Black,
                        letterSpacing = (-1).sp, lineHeight = 30.sp,
                        modifier = Modifier.clickable { app.open(Route.Manga(m.id)) },
                    )
                    Text("VOLUME $vol", color = Vault.primary, fontSize = 14.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                    nonEmpty(m.genre)?.let { Text(it, color = Vault.muted, fontSize = 14.sp) }

                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        val current = item
                        if (current != null) {
                            PrimaryButton(
                                text = "In Collection",
                                onClick = { scope.launch { remove() } },
                                enabled = !busy,
                                icon = Icons.Filled.CheckCircle,
                                color = Vault.secondary,
                            )
                            Box {
                                Row(
                                    Modifier
                                        .heightIn(min = 44.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Vault.card, RoundedCornerShape(10.dp))
                                        .border(1.dp, Vault.border, RoundedCornerShape(10.dp))
                                        .clickable(enabled = !busy) { statusMenu = true }
                                        .padding(horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(current.effectiveStatus.volumeLabel, color = Vault.foreground, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    Icon(Icons.Filled.KeyboardArrowDown, null, tint = Vault.foreground, modifier = Modifier.size(16.dp))
                                }
                                DropdownMenu(expanded = statusMenu, onDismissRequest = { statusMenu = false }) {
                                    for (s in ReadStatus.volumeCases) {
                                        DropdownMenuItem(
                                            text = { Text(s.volumeLabel) },
                                            onClick = {
                                                statusMenu = false
                                                scope.launch { setStatus(s) }
                                            },
                                            trailingIcon = {
                                                if (s == current.effectiveStatus) Icon(Icons.Filled.Check, null, tint = Vault.primary)
                                            },
                                        )
                                    }
                                }
                            }
                        } else {
                            PrimaryButton(
                                text = "Add to Collection",
                                onClick = { scope.launch { add() } },
                                enabled = !busy,
                                icon = Icons.Filled.Add,
                            )
                        }
                        OutlineButton(
                            text = if (wish != null) "On Wishlist" else "Wishlist",
                            onClick = { scope.launch { toggleWish() } },
                            enabled = !wishBusy,
                            icon = if (wish != null) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            color = if (wish != null) Vault.red else Vault.foreground,
                            borderColor = if (wish != null) Vault.red.copy(alpha = 0.5f) else Vault.border,
                        )
                    }
                    item?.let { it ->
                        Row(
                            Modifier.clickable { editing = it },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(Icons.Filled.Tune, null, tint = Vault.primary, modifier = Modifier.size(16.dp))
                            Text("Rating, progress & notes", color = Vault.primary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    VdMangaFireRow(mangaFireLoading, mangaFireUrl) { url -> openInBrowser(context, url) }
                }

                VdInfoCard(Icons.Filled.Book, "What Happens") {
                    if (synopsisLoading) VdSkeletonLines()
                    else VdBodyText(nonEmpty(synopsis?.synopsis) ?: "No synopsis available for this volume.")
                }
                VdInfoCard(Icons.Filled.History, if (vol == 1) "Introduction" else "Previously in the Story") {
                    if (synopsisLoading) VdSkeletonLines()
                    else VdBodyText(nonEmpty(synopsis?.recap) ?: "Recap not available yet.")
                }
                VdInfoCard(Icons.Filled.Place, "Location & Setting") {
                    if (synopsisLoading) VdSkeletonLines()
                    else {
                        val loc = synopsis?.location?.takeIf { it != VD_LOCATION_PLACEHOLDER }?.let { nonEmpty(it) }
                        val set = synopsis?.setting?.takeIf { it != VD_LOCATION_PLACEHOLDER }?.let { nonEmpty(it) }
                        if (loc == null && set == null) {
                            Text(VD_LOCATION_PLACEHOLDER, color = Vault.muted, fontSize = 14.sp, fontStyle = FontStyle.Italic)
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (loc != null) Text(loc, color = Vault.primary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                if (set != null) Text(set, color = Vault.muted, fontSize = 14.sp)
                            }
                        }
                    }
                }
                VdOverview(m, vol, maxVolume, chapters, item, synopsis)
                VdPager(vol, maxVolume, onPrev = { vol -= 1 }, onNext = { vol += 1 })
            }
        }
    }

    editing?.let { ed ->
        VdItemEditorSheet(
            app = app,
            initial = ed,
            onDismiss = { editing = null },
            onSave = { updated -> item = updated },
            onRemoved = { item = null; editing = null },
        )
    }
}

// MARK: - Sections

@Composable
private fun VdCover(cover: String, vol: Int) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier
            .width(190.dp)
            .aspectRatio(2f / 3f)
            .shadow(20.dp, shape)
            .clip(shape)
            .border(1.dp, Vault.border, shape),
    ) {
        if (cover.isEmpty()) {
            Box(
                Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Vault.primary.copy(alpha = 0.3f), Vault.background))),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Filled.Book, null, tint = Vault.primary.copy(alpha = 0.6f), modifier = Modifier.size(36.dp))
                    Text("VOL. $vol", color = Vault.muted, fontSize = 12.sp, fontWeight = FontWeight.Black)
                    Text("Cover not available", color = Vault.muted.copy(alpha = 0.7f), fontSize = 11.sp)
                }
            }
        } else {
            RemoteImage(cover, Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun VdMangaFireRow(loading: Boolean, url: String?, onOpen: (String) -> Unit) {
    when {
        loading -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CircularProgressIndicator(Modifier.size(14.dp), color = Vault.muted, strokeWidth = 2.dp)
            Text("Finding MangaFire link…", color = Vault.muted, fontSize = 12.sp)
        }
        url != null -> {
            val shape = RoundedCornerShape(10.dp)
            Row(
                Modifier
                    .heightIn(min = 40.dp)
                    .clip(shape)
                    .background(Vault.primary.copy(alpha = 0.1f), shape)
                    .border(1.dp, Vault.primary.copy(alpha = 0.4f), shape)
                    .clickable { onOpen(url) }
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("📖 Read On MangaFire", color = Vault.primary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Icon(Icons.AutoMirrored.Filled.OpenInNew, null, tint = Vault.primary, modifier = Modifier.size(16.dp))
            }
        }
        else -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Filled.Book, null, tint = Vault.muted, modifier = Modifier.size(14.dp))
            Text("MangaFire link unavailable for this manga", color = Vault.muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun VdTintedLabel(icon: ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, null, tint = Vault.primary, modifier = Modifier.size(18.dp))
        Text(title.uppercase(), color = Vault.foreground, fontSize = 17.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun VdInfoCard(icon: ImageVector, title: String, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().cardStyle().padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        VdTintedLabel(icon, title)
        content()
    }
}

@Composable
private fun VdBodyText(text: String) {
    Text(text, color = Vault.foreground.copy(alpha = 0.9f), fontSize = 14.sp, lineHeight = 20.sp)
}

@Composable
private fun VdSkeletonLines() {
    val t = rememberInfiniteTransition(label = "skeleton")
    val a by t.animateFloat(1f, 0.5f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "a")
    Column(Modifier.fillMaxWidth().alpha(a), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (w in listOf(1f, 0.85f, 0.65f)) {
            Box(Modifier.fillMaxWidth(w).height(12.dp).background(Vault.secondary, RoundedCornerShape(4.dp)))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VdOverview(
    m: Manga,
    vol: Int,
    maxVolume: Int,
    chapters: List<String>,
    item: CollectionItem?,
    synopsis: VolumeSynopsis?,
) {
    val rows = mutableListOf("Volume" to "$vol")
    nonEmpty(m.genre)?.let { rows += "Genres" to it }
    nonEmpty(m.publisher)?.let { rows += "Publisher" to it }
    m.languages?.takeIf { it.isNotEmpty() }?.let { rows += "Language" to it.joinToString(", ") }
    rows += "Total Volumes" to "$maxVolume"
    if (chapters.isNotEmpty()) {
        rows += "Chapters Included" to (
            if (chapters.size > 3) "${chapters.first()}–${chapters.last()} (${chapters.size})" else chapters.joinToString(", ")
            )
    }
    rows += "Price" to m.priceValue.euro()
    if (item != null && item.chapter > 0) rows += "Reading Progress" to "Chapter ${item.chapter}"
    if (item != null && item.ratingValue > 0) rows += "Your Rating" to "${item.ratingValue} / 10"
    val characters = (synopsis?.main_characters ?: emptyList()).filter { it.isNotBlank() }

    Column(
        Modifier.fillMaxWidth().cardStyle().padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        VdTintedLabel(Icons.AutoMirrored.Filled.LibraryBooks, "Volume Overview")
        for (pair in rows.chunked(2)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for (r in pair) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(r.first.uppercase(), color = Vault.muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Text(r.second, color = Vault.foreground, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        if (characters.isNotEmpty()) {
            Text("MAIN CHARACTERS", color = Vault.muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (c in characters) {
                    Text(
                        c, color = Vault.foreground, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .background(Vault.secondary, RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun VdPager(vol: Int, maxVolume: Int, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (vol > 1) VdPagerButton("Previous", "Volume ${vol - 1}", leading = true, onClick = onPrev, modifier = Modifier.weight(1f))
        else Spacer(Modifier.weight(1f))
        if (vol < maxVolume) VdPagerButton("Next", "Volume ${vol + 1}", leading = false, onClick = onNext, modifier = Modifier.weight(1f))
        else Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun VdPagerButton(label: String, title: String, leading: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.cardStyle().clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (leading) Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null, tint = Vault.primary)
        Column(
            Modifier.weight(1f),
            horizontalAlignment = if (leading) Alignment.Start else Alignment.End,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(label.uppercase(), color = Vault.muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text(title, color = Vault.foreground, fontSize = 14.sp, fontWeight = FontWeight.Black)
        }
        if (!leading) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Vault.primary)
    }
}

// MARK: - Editor sheet (status, rating, favorite, chapter progress, notes)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VdItemEditorSheet(
    app: AppState,
    initial: CollectionItem,
    onDismiss: () -> Unit,
    onSave: (CollectionItem) -> Unit,
    onRemoved: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var item by remember { mutableStateOf(initial) }
    var chapterText by remember { mutableStateOf(if (initial.chapter > 0) initial.chapter.toString() else "") }
    var notes by remember { mutableStateOf(initial.notes ?: "") }
    var saving by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }
    var statusOpen by remember { mutableStateOf(false) }

    suspend fun update(data: Map<String, Any?>, toast: String?): Boolean = try {
        Base44.update("CollectionItem", item.id, data)
        if (toast != null) app.show(toast)
        app.dataChanged()
        true
    } catch (e: Exception) {
        app.fail("Update failed", e)
        false
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Vault.foreground, unfocusedTextColor = Vault.foreground,
        focusedContainerColor = Vault.background, unfocusedContainerColor = Vault.background,
        focusedBorderColor = Vault.primary, unfocusedBorderColor = Vault.border, cursorColor = Vault.primary,
        focusedPlaceholderColor = Vault.muted, unfocusedPlaceholderColor = Vault.muted,
    )

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = Vault.background) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss) { Text("Close", color = Vault.muted) }
                Text("Details", color = Vault.foreground, fontSize = 17.sp, fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                TextButton(
                    enabled = !saving,
                    onClick = {
                        saving = true
                        scope.launch {
                            var current = item
                            val ch = chapterText.trim().toIntOrNull() ?: 0
                            if (chapterText.isNotEmpty() && ch != current.chapter) {
                                val today = DayKey.local()
                                if (update(mapOf("current_chapter" to ch, "last_read_date" to today), "Progress updated")) {
                                    VaultService.log("progress", current.manga_id, current.title, current.volume, meta = "ch.$ch")
                                    current = current.copy(current_chapter = ch.toDouble(), last_read_date = today)
                                }
                            }
                            if (notes != (current.notes ?: "")) {
                                if (update(mapOf("notes" to notes), "Note saved")) current = current.copy(notes = notes)
                            }
                            item = current
                            saving = false
                            onSave(current)
                            onDismiss()
                        }
                    },
                ) {
                    if (saving) CircularProgressIndicator(Modifier.size(16.dp), color = Vault.primary, strokeWidth = 2.dp)
                    else Text("Save", color = Vault.primary, fontWeight = FontWeight.Bold)
                }
            }

            Row(
                Modifier.fillMaxWidth().cardStyle().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                CoverView(CoverInfo.of(item), Modifier.width(60.dp), cornerRadius = 6.dp)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(item.titleText, color = Vault.foreground, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("${item.volumeLabel} · ${(item.price ?: 0.0).euro()}", color = Vault.muted, fontSize = 12.sp)
                }
            }

            // Status + favorite
            Column(Modifier.fillMaxWidth().cardStyle().padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("STATUS", color = Vault.muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Box {
                    Row(
                        Modifier.fillMaxWidth().clickable { statusOpen = true }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Status", color = Vault.foreground, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        Text(item.effectiveStatus.label, color = Vault.primary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Icon(Icons.Filled.KeyboardArrowDown, null, tint = Vault.primary, modifier = Modifier.size(18.dp))
                    }
                    DropdownMenu(expanded = statusOpen, onDismissRequest = { statusOpen = false }) {
                        for (s in ReadStatus.entries) {
                            DropdownMenuItem(
                                text = { Text(s.label) },
                                onClick = {
                                    statusOpen = false
                                    val snapshot = item.copy(status = s.raw)
                                    item = snapshot
                                    scope.launch {
                                        if (update(mapOf("status" to s.raw), "Status updated")) {
                                            VaultService.log("status", snapshot.manga_id, snapshot.title, snapshot.volume)
                                            if (s == ReadStatus.COMPLETED) VaultService.log("completed", snapshot.manga_id, snapshot.title, snapshot.volume)
                                        }
                                        onSave(snapshot)
                                    }
                                },
                                trailingIcon = { if (s == item.effectiveStatus) Icon(Icons.Filled.Check, null, tint = Vault.primary) },
                            )
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
                            val snapshot = item.copy(favorite = v)
                            item = snapshot
                            scope.launch {
                                val ok = update(
                                    mapOf("favorite" to !before.isFavorite),
                                    if (before.isFavorite) "Removed from favorites" else "Added to favorites",
                                )
                                if (ok) {
                                    VaultService.log("favorite", before.manga_id, before.title, before.volume)
                                    onSave(snapshot)
                                } else {
                                    item = before
                                }
                            }
                        },
                        colors = SwitchDefaults.colors(checkedTrackColor = Vault.primary, checkedThumbColor = Color.White),
                    )
                }
            }

            // Rating
            Column(Modifier.fillMaxWidth().cardStyle().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("RATING", color = Vault.muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StarRating(item.ratingValue, size = 26.dp) { r ->
                        val snapshot = item.copy(rating = r.toDouble())
                        item = snapshot
                        scope.launch {
                            if (update(mapOf("rating" to r), "Rating updated")) {
                                VaultService.log("rated", snapshot.manga_id, snapshot.title, snapshot.volume)
                                onSave(snapshot)
                            }
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Text(if (item.ratingValue > 0) "${item.ratingValue}/10" else "Not rated",
                        color = Vault.muted, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Reading progress
            Column(Modifier.fillMaxWidth().cardStyle().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("READING PROGRESS", color = Vault.muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
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
                        colors = fieldColors,
                    )
                }
                nonEmpty(item.last_read_date)?.let { d ->
                    Text("Updated $d", color = Vault.muted, fontSize = 12.sp)
                }
            }

            // Notes
            Column(Modifier.fillMaxWidth().cardStyle().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("NOTES", color = Vault.muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Add a note...") },
                    minLines = 3,
                    maxLines = 8,
                    shape = RoundedCornerShape(10.dp),
                    colors = fieldColors,
                )
            }

            Row(
                Modifier.fillMaxWidth().cardStyle().clickable { confirmRemove = true }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Filled.Delete, null, tint = Vault.red, modifier = Modifier.size(18.dp))
                Text("Remove from collection", color = Vault.red, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
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
                    scope.launch {
                        try {
                            Base44.delete("CollectionItem", item.id)
                            app.show("Removed \"${item.titleText}\"")
                            app.dataChanged()
                            onRemoved()
                        } catch (e: Exception) {
                            app.fail("Remove failed", e)
                        }
                    }
                }) { Text("Remove", color = Vault.red, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = false }) { Text("Cancel", color = Vault.muted) }
            },
        )
    }
}
