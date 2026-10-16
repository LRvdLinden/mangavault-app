package nl.lrvdlinden.mangavault.ui.social

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Chair
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import nl.lrvdlinden.mangavault.app.LocalApp
import nl.lrvdlinden.mangavault.core.Base44
import nl.lrvdlinden.mangavault.core.Config
import nl.lrvdlinden.mangavault.core.Engines
import nl.lrvdlinden.mangavault.core.shareText
import nl.lrvdlinden.mangavault.model.CollectionItem
import nl.lrvdlinden.mangavault.model.RoomItem
import nl.lrvdlinden.mangavault.model.RoomLayout
import nl.lrvdlinden.mangavault.model.nonEmpty
import nl.lrvdlinden.mangavault.ui.components.EmptyState
import nl.lrvdlinden.mangavault.ui.components.LoadingView
import nl.lrvdlinden.mangavault.ui.components.RemoteImage
import nl.lrvdlinden.mangavault.ui.components.ScreenHeader
import nl.lrvdlinden.mangavault.ui.components.cardStyle
import nl.lrvdlinden.mangavault.ui.theme.Vault
import nl.lrvdlinden.mangavault.ui.theme.hexColor
import kotlin.math.roundToInt

@Serializable
private data class SocRoomProfileName(
    val id: String = "",
    val display_name: String? = null,
)

private fun socClamp(v: Double, lo: Double, hi: Double): Double = minOf(maxOf(v, lo), hi)

private fun socRoomShowsArt(item: RoomItem): Boolean {
    if (nonEmpty(item.art) == null) return false
    return item.kind == "volume" || item.kind == "card" || item.kind == "poster"
}

private fun socRoomIcon(kind: String): ImageVector = when (kind) {
    "trophy" -> Icons.Filled.EmojiEvents
    "shelf" -> Icons.AutoMirrored.Filled.LibraryBooks
    "decoration" -> Icons.Filled.Lightbulb
    "poster" -> Icons.Filled.Image
    "card" -> Icons.Filled.Style
    else -> Icons.Filled.Book
}

/** userId null (or the signed-in user's id) = my own editable room; otherwise a read-only room. */
@Composable
fun MangaRoomScreen(userId: String?) {
    val app = LocalApp.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var items by remember { mutableStateOf<List<RoomItem>>(emptyList()) }
    var layout by remember { mutableStateOf<RoomLayout?>(null) }
    var isPublic by remember { mutableStateOf(false) }
    var ownerName by remember { mutableStateOf("") }
    var favorites by remember { mutableStateOf<List<RoomItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var togglingPublic by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }

    val myId = app.user?.id
    val isOwn = userId == null || userId == myId
    val targetId = userId ?: myId

    suspend fun loadFavorites() {
        val out = mutableListOf<RoomItem>()
        val vols = runCatching {
            Base44.list<CollectionItem>(
                "CollectionItem", query = mapOf("favorite" to true), limit = 24,
                fields = listOf("id", "title", "manga_id", "cover_image_url", "volume_cover_url"),
            )
        }.getOrNull()
        if (vols != null) {
            for (v in vols.items) {
                val art = nonEmpty(v.volume_cover_url) ?: nonEmpty(v.cover_image_url) ?: ""
                out += RoomItem(kind = "volume", ref = v.id, label = v.title ?: "", art = art, x = 50.0, y = 50.0, scale = 1.0)
            }
        }
        val favCards = runCatching { Engines.myCards(limit = 12, query = mapOf("favorite" to true)) }.getOrNull()
        if (favCards != null) {
            val ids = favCards.map { it.card_id }.filter { it.isNotEmpty() }
            val cards = runCatching { Engines.cards(ids) }.getOrNull()
            if (cards != null) {
                for (c in cards) {
                    out += RoomItem(kind = "card", ref = c.card_id, label = c.title, art = c.artwork_url ?: "", x = 50.0, y = 50.0, scale = 1.0)
                }
            }
        }
        favorites = out
    }

    LaunchedEffect(targetId) {
        val target = targetId
        if (target == null) { loading = false; return@LaunchedEffect }
        try {
            val page = Base44.list<RoomLayout>("RoomLayout", query = mapOf("created_by_id" to target), limit = 1)
            val found = page.items.firstOrNull()
            layout = found
            items = found?.items ?: emptyList()
            isPublic = found?.is_public ?: false
        } catch (e: Exception) {
            app.fail("Could not load room", e)
        }
        val p = runCatching {
            Base44.list<SocRoomProfileName>(
                "UserProfile", query = mapOf("created_by_id" to target), limit = 1,
                fields = listOf("id", "display_name"),
            )
        }.getOrNull()
        val name = nonEmpty(p?.items?.firstOrNull()?.display_name)
        if (name != null) ownerName = name
        if (isOwn) loadFavorites()
        loading = false
    }

    fun togglePublic() {
        val newValue = !isPublic
        togglingPublic = true
        scope.launch {
            try {
                val l = layout
                if (l != null) {
                    Base44.update("RoomLayout", l.id, mapOf("is_public" to newValue))
                    layout = l.copy(is_public = newValue)
                } else {
                    val body = mapOf<String, Any?>("items" to items.map { it.toMap() }, "is_public" to newValue)
                    layout = Base44.create<RoomLayout>("RoomLayout", body)
                }
                isPublic = newValue
                app.show(if (newValue) "Room is now public" else "Room is private")
            } catch (e: Exception) {
                app.fail("Action failed", e)
            }
            togglingPublic = false
        }
    }

    fun save() {
        saving = true
        scope.launch {
            val snapshot = items
            val payload = snapshot.map { it.toMap() }
            try {
                val l = layout
                val body = mapOf<String, Any?>("items" to payload, "is_public" to isPublic)
                if (l != null) {
                    Base44.update("RoomLayout", l.id, body)
                    layout = l.copy(items = snapshot, is_public = isPublic)
                } else {
                    layout = Base44.create<RoomLayout>("RoomLayout", body)
                }
                app.show("Room saved!")
            } catch (e: Exception) {
                app.fail("Save failed", e)
            }
            saving = false
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Vault.background)
            .verticalScroll(rememberScrollState(), enabled = !dragging)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        ScreenHeader(
            lead = "MANGA", accent = "ROOM",
            subtitle = if (isOwn) "Arrange your collection, cards, and decorations."
            else "${nonEmpty(ownerName) ?: "Reader"}'s Manga Room",
        )
        if (loading) {
            LoadingView()
        } else {
            if (isOwn) {
                // Action bar
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SocSmallButton(
                        if (isPublic) "Public" else "Private",
                        if (isPublic) Icons.Filled.Public else Icons.Filled.Lock,
                        filled = isPublic,
                        color = if (isPublic) Vault.emerald else Vault.muted,
                        enabled = !togglingPublic,
                    ) { togglePublic() }
                    SocSmallButton("Reset", Icons.Filled.Refresh, color = Vault.red, enabled = items.isNotEmpty()) {
                        confirmReset = true
                    }
                    SocSmallButton(if (saving) "Saving..." else "Save", Icons.Filled.Save, filled = true, enabled = !saving) {
                        save()
                    }
                    if (isPublic && myId != null) {
                        SocSmallButton("Share", Icons.Filled.Share) {
                            shareText(context, "${Config.APP_BASE_URL}/u/$myId/room")
                        }
                    }
                }
            }
            if (!isOwn && layout == null) {
                EmptyState(Icons.Filled.Chair, "This room is empty or private.")
            } else {
                SocRoomCanvas(
                    items = items,
                    editable = isOwn,
                    onMove = { index, x, y ->
                        val cur = items
                        if (index in cur.indices) {
                            items = cur.toMutableList().also { it[index] = it[index].copy(x = x, y = y) }
                        }
                    },
                    onRemove = { index ->
                        val cur = items
                        if (index in cur.indices) {
                            dragging = false
                            items = cur.toMutableList().also { it.removeAt(index) }
                        }
                    },
                    currentItem = { index -> items.getOrNull(index) },
                    onDragging = { dragging = it },
                )
            }
            if (isOwn) {
                SocRoomPalette(
                    favorites = favorites,
                    onAdd = { item -> items = items + item },
                )
            }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Remove all objects from your room?") },
            confirmButton = {
                TextButton(onClick = { items = emptyList(); confirmReset = false }) {
                    Text("Reset", color = Vault.red, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text("Cancel", color = Vault.foreground) }
            },
            containerColor = Vault.card,
            titleContentColor = Vault.foreground,
        )
    }
}

// MARK: Canvas

@Composable
private fun SocRoomCanvas(
    items: List<RoomItem>,
    editable: Boolean,
    onMove: (Int, Double, Double) -> Unit,
    onRemove: (Int) -> Unit,
    currentItem: (Int) -> RoomItem?,
    onDragging: (Boolean) -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val w: Dp = maxWidth
        val h: Dp = maxOf(w * 0.75f, 360.dp)
        val wPx = with(density) { w.toPx() }
        val hPx = with(density) { h.toPx() }
        Box(
            Modifier
                .size(w, h)
                .clip(shape)
                .border(1.dp, Vault.primary.copy(alpha = 0.25f), shape)
        ) {
            SocRoomBackground(Modifier.fillMaxSize())
            if (items.isEmpty() && editable) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Add objects from the panel ↓", color = Vault.muted, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
            items.forEachIndexed { index, item ->
                key(index) {
                    val art = socRoomShowsArt(item)
                    val objW = if (art) 52.dp else 62.dp
                    val objH = if (art) 72.dp else 62.dp
                    val objWPx = with(density) { objW.toPx() }
                    val objHPx = with(density) { objH.toPx() }
                    val px = wPx * socClamp(item.x, 2.0, 96.0) / 100.0
                    val py = hPx * socClamp(item.y, 2.0, 95.0) / 100.0
                    val scale = (item.scale ?: 1.0).toFloat()

                    val dragModifier = if (editable) {
                        Modifier.pointerInput(index, wPx, hPx) {
                            var originX = 0.0
                            var originY = 0.0
                            var tx = 0f
                            var ty = 0f
                            detectDragGestures(
                                onDragStart = {
                                    val cur = currentItem(index)
                                    originX = cur?.x ?: 50.0
                                    originY = cur?.y ?: 50.0
                                    tx = 0f
                                    ty = 0f
                                    onDragging(true)
                                },
                                onDragEnd = { onDragging(false) },
                                onDragCancel = { onDragging(false) },
                            ) { change, dragAmount ->
                                change.consume()
                                if (wPx > 0f && hPx > 0f) {
                                    tx += dragAmount.x
                                    ty += dragAmount.y
                                    val nx = originX + (tx / wPx) * 100.0
                                    val ny = originY + (ty / hPx) * 100.0
                                    onMove(index, socClamp(nx, 2.0, 96.0), socClamp(ny, 2.0, 95.0))
                                }
                            }
                        }
                    } else Modifier

                    Box(
                        Modifier
                            .offset { IntOffset((px - objWPx / 2).roundToInt(), (py - objHPx / 2).roundToInt()) }
                            .then(dragModifier)
                            .graphicsLayer { scaleX = scale; scaleY = scale }
                    ) {
                        SocRoomObject(item, art, objW, objH)
                        if (editable) {
                            Box(
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = 8.dp, y = (-8).dp)
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(Color.White)
                                    .clickable { onRemove(index) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Filled.Cancel, "Remove", tint = Vault.red, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SocRoomObject(item: RoomItem, showsArt: Boolean, w: Dp, h: Dp) {
    if (showsArt) {
        val shape = RoundedCornerShape(6.dp)
        Box(
            Modifier
                .size(w, h)
                .shadow(6.dp, shape)
                .clip(shape)
                .border(1.dp, Color.White.copy(alpha = 0.15f), shape)
        ) {
            RemoteImage(item.art, Modifier.fillMaxSize())
        }
    } else {
        val shape = RoundedCornerShape(10.dp)
        Column(
            Modifier
                .size(w, h)
                .shadow(6.dp, shape)
                .background(Vault.card.copy(alpha = 0.85f), shape)
                .border(1.dp, Vault.primary.copy(alpha = 0.3f), shape)
                .padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
        ) {
            Icon(socRoomIcon(item.kind), null, tint = Vault.primary, modifier = Modifier.size(26.dp))
            val l = nonEmpty(item.label)
            if (l != null) {
                Text(l, color = Vault.muted, fontSize = 8.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun SocRoomBackground(modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val step = with(density) { 60.dp.toPx() }
    val line = with(density) { 1.dp.toPx() }
    val wall = listOf(hexColor("#1a1410"), hexColor("#0d0d0d"), hexColor("#2a1a0a"))
    val floor = listOf(hexColor("#3a2512"), hexColor("#1a0f06"))
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawRect(Brush.verticalGradient(wall, startY = 0f, endY = h))
        var x = step
        while (x < w) {
            drawLine(Vault.primary.copy(alpha = 0.06f), Offset(x, 0f), Offset(x, h), strokeWidth = line)
            x += step
        }
        val floorTop = h * 0.8f
        drawRect(
            Brush.verticalGradient(floor, startY = floorTop, endY = h),
            topLeft = Offset(0f, floorTop),
            size = Size(w, h - floorTop),
        )
        drawRect(Vault.primary.copy(alpha = 0.25f), topLeft = Offset(0f, floorTop), size = Size(w, line))
    }
}

// MARK: Palette

@Composable
private fun SocRoomPalette(favorites: List<RoomItem>, onAdd: (RoomItem) -> Unit) {
    Column(
        Modifier.fillMaxWidth().cardStyle().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("ADD TO ROOM", color = Vault.muted, fontSize = 13.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val defs = listOf(
                Triple("Bookshelf", Icons.AutoMirrored.Filled.LibraryBooks, "shelf"),
                Triple("Trophy", Icons.Filled.EmojiEvents, "trophy"),
                Triple("Poster", Icons.Filled.Image, "poster"),
                Triple("Lamp", Icons.Filled.Lightbulb, "decoration"),
            )
            for ((title, icon, kind) in defs) {
                val shape = RoundedCornerShape(10.dp)
                Column(
                    Modifier
                        .weight(1f)
                        .clip(shape)
                        .background(Vault.secondary, shape)
                        .border(1.dp, Vault.border, shape)
                        .clickable {
                            onAdd(RoomItem(kind = kind, ref = null, label = title, art = "", x = 50.0, y = 50.0, scale = 1.0))
                        }
                        .padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(icon, null, tint = Vault.primary, modifier = Modifier.size(22.dp))
                    Text(title, color = Vault.foreground, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
        }
        Text("YOUR FAVORITES", color = Vault.muted, fontSize = 13.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
        if (favorites.isEmpty()) {
            Text("Mark volumes or cards as favorites to place them here.", color = Vault.muted, fontSize = 12.sp)
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                itemsIndexed(favorites) { _, fav ->
                    SocFavoriteButton(fav) { onAdd(fav.copy(x = 50.0, y = 50.0, scale = 1.0)) }
                }
            }
        }
    }
}

@Composable
private fun SocFavoriteButton(fav: RoomItem, onClick: () -> Unit) {
    Column(
        Modifier.width(60.dp).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val shape = RoundedCornerShape(6.dp)
        Box(Modifier.size(52.dp, 72.dp)) {
            Box(Modifier.fillMaxSize().clip(shape).background(Vault.secondary), contentAlignment = Alignment.Center) {
                val art = nonEmpty(fav.art)
                if (art != null) {
                    RemoteImage(art, Modifier.fillMaxSize())
                } else {
                    Icon(if (fav.kind == "card") Icons.Filled.Style else Icons.Filled.Book, null, tint = Vault.muted)
                }
            }
            Box(
                Modifier.align(Alignment.BottomEnd).padding(2.dp).size(16.dp).clip(CircleShape).background(Color.White),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.AddCircle, null, tint = Vault.primary, modifier = Modifier.size(16.dp))
            }
        }
        Text(fav.label ?: "", color = Vault.muted, fontSize = 9.sp, fontWeight = FontWeight.Bold,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}
