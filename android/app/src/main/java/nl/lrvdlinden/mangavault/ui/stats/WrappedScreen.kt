package nl.lrvdlinden.mangavault.ui.stats

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.lrvdlinden.mangavault.app.LocalApp
import nl.lrvdlinden.mangavault.core.ProfileStore
import nl.lrvdlinden.mangavault.core.VaultService
import nl.lrvdlinden.mangavault.core.shareText
import nl.lrvdlinden.mangavault.model.CollectionItem
import nl.lrvdlinden.mangavault.model.ReadingActivity
import nl.lrvdlinden.mangavault.model.UserProfile
import nl.lrvdlinden.mangavault.model.nonEmpty
import nl.lrvdlinden.mangavault.ui.components.Chip
import nl.lrvdlinden.mangavault.ui.components.LoadingView
import nl.lrvdlinden.mangavault.ui.components.PrimaryButton
import nl.lrvdlinden.mangavault.ui.components.ScreenHeader
import nl.lrvdlinden.mangavault.ui.components.cardStyle
import nl.lrvdlinden.mangavault.ui.theme.Vault
import nl.lrvdlinden.mangavault.ui.theme.hexColor
import java.time.LocalDate
import java.util.Locale
import java.util.TreeMap
import kotlin.coroutines.cancellation.CancellationException

private fun wrappedCapitalized(s: String): String =
    s.split(" ").joinToString(" ") { w -> w.replaceFirstChar { it.titlecase(Locale.US) } }

/** Saves the rendered card to Pictures/MangaVault and opens the share sheet (text fallback before Android 10). */
private suspend fun wrappedShare(context: Context, bitmap: Bitmap, fallbackText: String): Boolean {
    val uri: Uri? = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "manga-wrapped-${System.currentTimeMillis()}.png")
                put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/MangaVault")
            }
            val resolver = context.contentResolver
            val u = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (u != null) {
                resolver.openOutputStream(u)?.use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
            }
            u
        } else null
    }
    if (uri == null) {
        shareText(context, fallbackText)
        return false
    }
    val send = Intent(Intent.ACTION_SEND)
        .setType("image/png")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, "Share Card").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    return true
}

@Composable
fun WrappedScreen() {
    val app = LocalApp.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val now = remember { LocalDate.now() }

    var items by remember { mutableStateOf<List<CollectionItem>>(emptyList()) }
    var activities by remember { mutableStateOf<List<ReadingActivity>>(emptyList()) }
    var genreMap by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var profile by remember { mutableStateOf<UserProfile?>(null) }
    var loading by remember { mutableStateOf(true) }
    var byYear by remember { mutableStateOf(true) }
    var year by remember { mutableStateOf(now.year) }
    var month by remember { mutableStateOf(String.format(Locale.US, "%04d-%02d", now.year, now.monthValue)) }
    var sharing by remember { mutableStateOf(false) }
    val graphicsLayer = rememberGraphicsLayer()

    LaunchedEffect(Unit) {
        try {
            val its = VaultService.collection()
            val acts = VaultService.activities()
            val gm = VaultService.genreMap(its)
            items = its; activities = acts; genreMap = gm
            val uid = app.user?.id
            if (uid != null) profile = ProfileStore.current(uid)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            app.fail("Could not load Wrapped", e)
        }
        loading = false
    }

    val periodLabel = if (byYear) year.toString() else month
    fun inPeriod(created: String?): Boolean {
        val d = (created ?: "").take(10)
        if (d.isEmpty()) return false
        return if (byYear) d.startsWith(year.toString()) else d.take(7) == month
    }
    val periodItems = items.filter { inPeriod(it.created_date) }
    val periodActs = activities.filter { inPeriod(it.created_date) }
    val years = remember(items, activities) {
        val s = mutableSetOf(now.year)
        for (d in items.mapNotNull { it.created_date } + activities.mapNotNull { it.created_date }) d.take(4).toIntOrNull()?.let { s += it }
        s.sortedDescending()
    }
    val months = (1..12).map { String.format(Locale.US, "%d-%02d", year, it) }
    val name = nonEmpty(profile?.display_name) ?: app.displayName

    Column(
        Modifier.fillMaxSize().background(Vault.background).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        ScreenHeader("MANGA", "WRAPPED", eyebrow = "Manga Wrapped", eyebrowIcon = Icons.Filled.AutoAwesome,
            subtitle = "A recap of your real reading activity for a chosen period.")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Year", byYear) { byYear = true }
            Chip("Month", !byYear) { byYear = false }
            Spacer(Modifier.width(4.dp))
            if (byYear) {
                WrappedPicker(year.toString(), years.map { it.toString() }) { picked ->
                    val y = picked.toIntOrNull() ?: year
                    year = y
                    if (!month.startsWith(y.toString())) month = String.format(Locale.US, "%d-%02d", y, now.monthValue)
                }
            } else {
                WrappedPicker(month, months) { month = it }
            }
        }
        if (loading) {
            LoadingView()
        } else if (periodItems.isEmpty() && periodActs.isEmpty()) {
            Text(
                "No activity recorded for this period. Add manga, update progress, or change statuses to build your Wrapped.",
                color = Vault.muted, fontSize = 14.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().cardStyle().padding(28.dp),
            )
        } else {
            Box(
                Modifier
                    .drawWithContent {
                        graphicsLayer.record { this@drawWithContent.drawContent() }
                        drawLayer(graphicsLayer)
                    }
                    .background(hexColor("#101010"))
                    .padding(12.dp),
            ) {
                WrappedCard(periodLabel, byYear, name, periodItems, periodActs, genreMap)
            }
            val fallbackText = "My Manga Wrapped $periodLabel: ${periodItems.size} added, " +
                "${periodActs.count { it.type == "completed" }} completed, " +
                "${periodActs.count { it.type == "progress" }} progress updates. mangas.base44.app"
            PrimaryButton(
                "Share Card",
                onClick = {
                    scope.launch {
                        sharing = true
                        try {
                            val raw = graphicsLayer.toImageBitmap().asAndroidBitmap()
                            val bmp = if (raw.config == Bitmap.Config.HARDWARE) raw.copy(Bitmap.Config.ARGB_8888, false) else raw
                            if (wrappedShare(context, bmp, fallbackText)) app.show("Card saved", "Saved to Pictures/MangaVault")
                        } catch (e: Exception) {
                            app.fail("Export failed", e)
                        } finally {
                            sharing = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                loading = sharing,
                icon = Icons.Filled.Share,
            )
            if (periodActs.none { it.type == "progress" }) {
                Text("Chapter-level reading isn't tracked yet, so progress updates are shown instead of chapters read.",
                    color = Vault.muted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun WrappedPicker(current: String, options: List<String>, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(9.dp)
    Box {
        Row(
            Modifier.clip(shape).background(Vault.card, shape).border(1.dp, Vault.border, shape).clickable { open = true }
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(current, color = Vault.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Icon(Icons.Filled.KeyboardArrowDown, null, tint = Vault.primary, modifier = Modifier.size(14.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for (o in options) {
                DropdownMenuItem(
                    text = { Text(o, color = if (o == current) Vault.primary else Vault.foreground) },
                    onClick = { open = false; onPick(o) },
                    leadingIcon = {
                        if (o == current) Icon(Icons.Filled.Check, null, tint = Vault.primary) else Spacer(Modifier.size(24.dp))
                    },
                )
            }
        }
    }
}

@Composable
private fun WrappedCard(
    periodLabel: String,
    byYear: Boolean,
    name: String,
    pi: List<CollectionItem>,
    pa: List<ReadingActivity>,
    genreMap: Map<String, String>,
) {
    val accent = Vault.primary
    val completed = pa.count { it.type == "completed" }
    val progress = pa.count { it.type == "progress" }
    val topGenre = VaultService.genreDistribution(pi, genreMap, 1).firstOrNull()?.first
    val topRated = pi.filter { (it.rating ?: 0.0) > 0 }.maxByOrNull { it.rating ?: 0.0 }
    val favorite = pi.firstOrNull { it.isFavorite }
    val perDay = TreeMap<String, Int>()
    pa.forEach { perDay[it.day] = (perDay[it.day] ?: 0) + 1 }
    val days = perDay.entries.map { it.key to it.value }.takeLast(30)
    val maxV = maxOf(1, days.maxOfOrNull { it.second } ?: 1)
    val shape = RoundedCornerShape(18.dp)

    Column(
        Modifier.fillMaxWidth()
            .clip(shape)
            .background(Brush.linearGradient(listOf(hexColor("#191919"), hexColor("#101010"))), shape)
            .border(1.dp, Vault.border, shape)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("MANGA VAULT · WRAPPED", color = accent, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                Text(periodLabel, color = Vault.foreground, fontSize = 34.sp, fontWeight = FontWeight.Black)
            }
            Icon(Icons.Filled.LocalFireDepartment, null, tint = accent, modifier = Modifier.size(40.dp))
        }
        Text("$name's ${if (byYear) "year" else "month"} in manga", color = Vault.muted, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            WrappedTile("Added", "${pi.size}", Modifier.weight(1f), highlight = true)
            WrappedTile("Completed", "$completed", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.height(IntrinsicSize.Min)) {
            WrappedTile("Progress Updates", "$progress", Modifier.weight(1f).fillMaxHeight())
            WrappedTile("Top Genre", topGenre?.let { wrappedCapitalized(it) } ?: "—", Modifier.weight(1f).fillMaxHeight(), small = true)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.height(IntrinsicSize.Min)) {
            WrappedInfoTile(Icons.Filled.Star, "Highest Rated", topRated?.titleText ?: "No ratings yet",
                topRated?.let { "${it.ratingValue}/10" }, Modifier.weight(1f).fillMaxHeight())
            WrappedInfoTile(Icons.AutoMirrored.Filled.MenuBook, "Favorite", favorite?.titleText ?: "None marked", null,
                Modifier.weight(1f).fillMaxHeight())
        }
        Column(
            Modifier.fillMaxWidth().background(Vault.card, RoundedCornerShape(12.dp)).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("READING ACTIVITY", color = Vault.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            if (days.isEmpty()) {
                Text("No reading activity recorded in this period.", color = Vault.muted, fontSize = 14.sp)
            } else {
                Row(Modifier.fillMaxWidth().height(90.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
                    for ((_, v) in days) {
                        Box(
                            Modifier.weight(1f).height(maxOf(4f, 90f * v / maxV).dp)
                                .background(accent, RoundedCornerShape(2.dp)),
                        )
                    }
                }
            }
        }
        Text("mangas.base44.app · Generated from your real collection data.", color = Vault.muted, fontSize = 11.sp)
    }
}

@Composable
private fun WrappedTile(label: String, value: String, modifier: Modifier, highlight: Boolean = false, small: Boolean = false) {
    Column(
        modifier.background(if (highlight) Vault.primary.copy(alpha = 0.1f) else Vault.card, RoundedCornerShape(12.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label.uppercase(), color = Vault.muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            value, color = if (highlight) Vault.primary else Vault.foreground,
            fontSize = if (small) 20.sp else 30.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun WrappedInfoTile(icon: ImageVector, label: String, value: String, extra: String?, modifier: Modifier) {
    Row(
        modifier.background(Vault.card, RoundedCornerShape(12.dp)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, null, tint = Vault.primary, modifier = Modifier.size(22.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label.uppercase(), color = Vault.muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(value, color = Vault.foreground, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (extra != null) Text(extra, color = Vault.primary, fontSize = 12.sp)
        }
    }
}
