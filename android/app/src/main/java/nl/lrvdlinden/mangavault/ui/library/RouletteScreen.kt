package nl.lrvdlinden.mangavault.ui.library

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import nl.lrvdlinden.mangavault.app.LocalApp
import nl.lrvdlinden.mangavault.app.Route
import nl.lrvdlinden.mangavault.core.Base44
import nl.lrvdlinden.mangavault.model.CollectionItem
import nl.lrvdlinden.mangavault.model.Manga
import nl.lrvdlinden.mangavault.model.ReadStatus
import nl.lrvdlinden.mangavault.model.WishlistItem
import nl.lrvdlinden.mangavault.model.nonEmpty
import nl.lrvdlinden.mangavault.ui.components.Chip
import nl.lrvdlinden.mangavault.ui.components.CoverInfo
import nl.lrvdlinden.mangavault.ui.components.CoverView
import nl.lrvdlinden.mangavault.ui.components.EmptyState
import nl.lrvdlinden.mangavault.ui.components.LoadingView
import nl.lrvdlinden.mangavault.ui.components.OutlineButton
import nl.lrvdlinden.mangavault.ui.components.PrimaryButton
import nl.lrvdlinden.mangavault.ui.components.ScreenHeader
import nl.lrvdlinden.mangavault.ui.components.cardStyle
import nl.lrvdlinden.mangavault.ui.theme.Vault
import nl.lrvdlinden.mangavault.ui.theme.hexColor
import kotlin.math.ceil
import kotlin.random.Random

private val rouletteColors = listOf("#ff7a00", "#1b1b1b", "#ff5100", "#2a2a2a", "#ff9a3d", "#161616").map { hexColor(it) }

private val rouletteSources = listOf(
    "wishlist" to "My Wishlist",
    "collection" to "My Collection",
    "not_started" to "Not Started",
    "all" to "Everything",
)

private fun rouletteShort(s: String): String = if (s.length > 16) s.take(15) + "…" else s

/** The wheel, drawn on a Canvas and rotated by [rotation] degrees (clockwise). */
@Composable
private fun RouletteWheel(titles: List<String>, rotation: Float, modifier: Modifier = Modifier) {
    Box(modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val n = maxOf(titles.size, 1)
            val seg = 360f / n
            val r = minOf(size.width, size.height) / 2f
            val c = Offset(size.width / 2f, size.height / 2f)
            val topLeft = Offset(c.x - r, c.y - r)
            val arcSize = Size(r * 2, r * 2)
            rotate(rotation, pivot = c) {
                for (i in titles.indices) {
                    drawArc(
                        color = rouletteColors[i % rouletteColors.size],
                        startAngle = i * seg - 90f,
                        sweepAngle = seg,
                        useCenter = true,
                        topLeft = topLeft,
                        size = arcSize,
                    )
                }
                if (titles.size <= 40) {
                    val paint = Paint().apply {
                        isAntiAlias = true
                        color = Color.White.toArgb()
                        textSize = (if (titles.size > 20) 7 else 9).sp.toPx()
                        typeface = Typeface.DEFAULT_BOLD
                        textAlign = Paint.Align.RIGHT
                        setShadowLayer(2f, 0f, 0f, Color.Black.copy(alpha = 0.8f).toArgb())
                    }
                    for (i in titles.indices) {
                        val mid = i * seg + seg / 2f
                        rotate(mid - 90f, pivot = c) {
                            drawContext.canvas.nativeCanvas.drawText(
                                rouletteShort(titles[i]),
                                c.x + r * 0.81f,
                                c.y + paint.textSize / 3f,
                                paint,
                            )
                        }
                    }
                }
                drawCircle(Vault.primary, radius = r - 2.dp.toPx(), center = c, style = Stroke(width = 4.dp.toPx()))
            }
        }
        // Hub
        Box(
            Modifier.size(54.dp).background(Vault.background, CircleShape).border(3.dp, Vault.primary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("MV", color = Vault.primary, fontSize = 16.sp, fontWeight = FontWeight.Black)
        }
        // Pointer
        Canvas(Modifier.align(Alignment.TopCenter).offset(y = (-8).dp).size(24.dp, 28.dp)) {
            val p = Path().apply {
                moveTo(0f, 0f)
                lineTo(size.width, 0f)
                lineTo(size.width / 2f, size.height)
                close()
            }
            drawPath(p, Color.White)
        }
    }
}

/** Manga Roulette: spin the wheel to pick your next read from real manga in the vault. */
@Composable
fun RouletteScreen() {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    var source by remember { mutableStateOf("not_started") }
    var publisher by remember { mutableStateOf("all") }
    var excludeCompleted by remember { mutableStateOf(true) }
    var mangas by remember { mutableStateOf<List<Manga>>(emptyList()) }
    var ownedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var completedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var wishIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var loading by remember { mutableStateOf(true) }
    val rotation = remember { Animatable(0f) }
    var spinning by remember { mutableStateOf(false) }
    var lastIndex by remember { mutableStateOf<Int?>(null) }
    var result by remember { mutableStateOf<Manga?>(null) }
    var busy by remember { mutableStateOf(false) }
    var publisherMenu by remember { mutableStateOf(false) }

    val publishers = mangas.mapNotNull { nonEmpty(it.publisher) }.toSet().sortedWith(String.CASE_INSENSITIVE_ORDER)

    val eligible: List<Manga> = run {
        var list = mangas
        when (source) {
            "wishlist" -> list = list.filter { wishIds.contains(it.id) }
            "collection" -> list = list.filter { ownedIds.contains(it.id) }
            "not_started" -> list = list.filter { !ownedIds.contains(it.id) }
        }
        if (excludeCompleted) list = list.filter { !completedIds.contains(it.id) }
        if (publisher != "all") list = list.filter { it.publisher == publisher }
        list
    }

    suspend fun load() {
        loading = true
        try {
            val col = Base44.list<CollectionItem>("CollectionItem", limit = 500, fields = listOf("id", "manga_id", "status", "read")).items
            val wish = Base44.list<WishlistItem>("WishlistItem", limit = 500, fields = listOf("id", "manga_id")).items
            val fields = listOf("id", "title", "cover_image_url", "genre", "publisher", "description", "total_volumes", "price")
            val ms = Base44.list<Manga>("Manga", sort = "-created_date", limit = 120, fields = fields).items
            ownedIds = col.mapNotNull { nonEmpty(it.manga_id) }.toSet()
            completedIds = col.filter { it.effectiveStatus == ReadStatus.COMPLETED }.mapNotNull { nonEmpty(it.manga_id) }.toSet()
            wishIds = wish.mapNotNull { nonEmpty(it.manga_id) }.toSet()
            mangas = ms
        } catch (e: Exception) {
            app.fail("Could not load roulette", e)
        }
        loading = false
    }

    fun spin() {
        val list = eligible
        val n = list.size
        if (n <= 0 || spinning) return
        var idx = Random.nextInt(n)
        val last = lastIndex
        if (n > 1 && last != null) {
            while (idx == last) idx = Random.nextInt(n)
        }
        lastIndex = idx
        val seg = 360f / n
        val base = ceil(rotation.value / 360f) * 360f
        val target = base + 2160f - (idx * seg + seg / 2f)
        val chosen = list[idx]
        spinning = true
        result = null
        scope.launch {
            try {
                rotation.animateTo(target, tween(4200, easing = CubicBezierEasing(0.17f, 0.67f, 0.12f, 0.99f)))
            } finally {
                result = chosen
                spinning = false
            }
        }
    }

    suspend fun addToWishlist(m: Manga) {
        if (wishIds.contains(m.id)) return
        busy = true
        val payload = mapOf<String, Any?>(
            "manga_id" to m.id,
            "title" to m.title,
            "cover_image_url" to (m.cover_image_url ?: ""),
            "cover_seed" to m.title,
            "publisher" to (m.publisher ?: ""),
            "price" to (m.price ?: 0.0),
            "edition" to "standard",
        )
        try {
            Base44.create<WishlistItem>("WishlistItem", payload)
            wishIds = wishIds + m.id
            app.show("Added to wishlist")
            app.dataChanged()
        } catch (e: Exception) {
            app.fail("Could not add to wishlist", e)
        }
        busy = false
    }

    suspend fun startTracking(m: Manga) {
        busy = true
        val payload = mapOf<String, Any?>(
            "manga_id" to m.id,
            "title" to "${m.title} Vol. 1",
            "volume_number" to 1,
            "cover_image_url" to (m.cover_image_url ?: ""),
            "cover_seed" to (m.cover_seed ?: m.title),
            "publisher" to (m.publisher ?: ""),
            "price" to (m.price ?: 0.0),
            "edition" to "standard",
            "status" to "reading",
            "read" to false,
        )
        try {
            Base44.create<CollectionItem>("CollectionItem", payload)
            ownedIds = ownedIds + m.id
            app.show("Now tracking Vol. 1")
            app.dataChanged()
        } catch (e: Exception) {
            app.fail("Could not start tracking", e)
        }
        busy = false
    }

    LaunchedEffect(Unit) { load() }

    Column(
        Modifier.fillMaxSize().background(Vault.background).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        ScreenHeader(
            lead = "MANGA", accent = "ROULETTE",
            eyebrow = "Roulette", eyebrowIcon = Icons.Filled.Casino,
            subtitle = "Spin the wheel to discover your next read from real manga in the vault.",
        )
        // Filters
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((key, label) in rouletteSources) {
                    Chip(label, source == key) {
                        source = key
                        result = null
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    Row(
                        Modifier.clickable { publisherMenu = true }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            if (publisher == "all") "All publishers" else publisher,
                            color = Vault.primary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                        )
                        Icon(Icons.Filled.KeyboardArrowDown, null, tint = Vault.primary, modifier = Modifier.size(18.dp))
                    }
                    DropdownMenu(expanded = publisherMenu, onDismissRequest = { publisherMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("All publishers", color = if (publisher == "all") Vault.primary else Vault.foreground) },
                            onClick = { publisher = "all"; publisherMenu = false },
                        )
                        for (p in publishers) {
                            DropdownMenuItem(
                                text = { Text(p, color = if (publisher == p) Vault.primary else Vault.foreground) },
                                onClick = { publisher = p; publisherMenu = false },
                            )
                        }
                    }
                }
                Text("Exclude completed", color = Vault.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Switch(
                    checked = excludeCompleted,
                    onCheckedChange = { excludeCompleted = it },
                    modifier = Modifier.padding(start = 8.dp),
                    colors = SwitchDefaults.colors(checkedTrackColor = Vault.primary, checkedThumbColor = Color.White),
                )
            }
        }
        if (loading && mangas.isEmpty()) {
            LoadingView()
        } else if (eligible.isEmpty()) {
            EmptyState(Icons.Filled.Casino, "No eligible manga for these filters. Try another source.")
        } else {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                RouletteWheel(
                    titles = eligible.map { it.title },
                    rotation = rotation.value,
                    modifier = Modifier.padding(top = 8.dp).widthIn(max = 340.dp).fillMaxWidth(),
                )
                Text("${eligible.size} manga on the wheel", color = Vault.muted, fontSize = 12.sp)
                PrimaryButton(
                    if (spinning) "Spinning..." else "Spin the wheel",
                    { spin() },
                    Modifier.fillMaxWidth(),
                    enabled = !spinning,
                    icon = Icons.Filled.Refresh,
                )
                val m = result
                if (m != null && !spinning) {
                    RouletteResultCard(
                        m = m,
                        onWishlist = wishIds.contains(m.id),
                        owned = ownedIds.contains(m.id),
                        busy = busy,
                        onOpen = { app.open(Route.Manga(m.id)) },
                        onAddWishlist = { scope.launch { addToWishlist(m) } },
                        onStartTracking = { scope.launch { startTracking(m) } },
                        onSpinAgain = { spin() },
                    )
                }
            }
        }
    }
}

private fun rouletteMetaLine(m: Manga): String {
    val parts = mutableListOf<String>()
    nonEmpty(m.genre)?.let { parts.add(it) }
    nonEmpty(m.publisher)?.let { parts.add(it) }
    if (m.totalVolumes > 0) parts.add("${m.totalVolumes} vols")
    return parts.joinToString(" · ")
}

@Composable
private fun RouletteResultCard(
    m: Manga,
    onWishlist: Boolean,
    owned: Boolean,
    busy: Boolean,
    onOpen: () -> Unit,
    onAddWishlist: () -> Unit,
    onStartTracking: () -> Unit,
    onSpinAgain: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().cardStyle(highlighted = true).padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            CoverView(CoverInfo.of(m), Modifier.width(96.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("THE WHEEL CHOSE", color = Vault.primary, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                Text(m.title, color = Vault.foreground, fontSize = 20.sp, fontWeight = FontWeight.Black)
                Text(rouletteMetaLine(m), color = Vault.muted, fontSize = 12.sp)
                nonEmpty(m.description)?.let {
                    Text(it, color = Vault.foreground.copy(alpha = 0.8f), fontSize = 12.sp, maxLines = 3,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton("Open Details", onOpen, Modifier.weight(1f), icon = Icons.AutoMirrored.Filled.MenuBook)
                OutlineButton(
                    if (onWishlist) "On Wishlist" else "Add to Wishlist",
                    onAddWishlist,
                    Modifier.weight(1f),
                    enabled = !onWishlist && !busy,
                    icon = if (onWishlist) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    color = if (onWishlist) Vault.red else Vault.foreground,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!owned) {
                    OutlineButton("Start Tracking", onStartTracking, Modifier.weight(1f), enabled = !busy, icon = Icons.Filled.Add, color = Vault.primary)
                }
                OutlineButton("Spin Again", onSpinAgain, Modifier.weight(1f), icon = Icons.Filled.Refresh)
            }
        }
    }
}
