package nl.lrvdlinden.mangavault.ui.rewards

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.serialization.json.JsonObject
import nl.lrvdlinden.mangavault.core.VaultJson
import nl.lrvdlinden.mangavault.core.str
import nl.lrvdlinden.mangavault.model.CardRarity
import nl.lrvdlinden.mangavault.model.CommunityEvent
import nl.lrvdlinden.mangavault.model.EventParticipation
import nl.lrvdlinden.mangavault.model.LeaderboardRow
import nl.lrvdlinden.mangavault.model.nonEmpty
import nl.lrvdlinden.mangavault.ui.components.PrimaryButton
import nl.lrvdlinden.mangavault.ui.components.RemoteImage
import nl.lrvdlinden.mangavault.ui.components.SnowLayer
import nl.lrvdlinden.mangavault.ui.components.cardStyle
import nl.lrvdlinden.mangavault.ui.theme.Vault
import nl.lrvdlinden.mangavault.ui.theme.hexColor
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

// Shared helpers for the Rewards, Capsules, Cards and Events screens (ports of the `RV…` helpers on iOS).

/** Event dates are plain `yyyy-MM-dd` strings (local time zone). */
internal object RwDay {
    fun parse(s: String?): LocalDate? {
        val v = nonEmpty(s) ?: return null
        return runCatching { LocalDate.parse(v.take(10)) }.getOrNull()
    }

    /** Start of that day, local time. */
    fun start(s: String?): Instant? = parse(s)?.atStartOfDay(ZoneId.systemDefault())?.toInstant()

    /** Start of the day after the given date (i.e. end of that day). */
    fun endOfDay(s: String?): Instant? = parse(s)?.plusDays(1)?.atStartOfDay(ZoneId.systemDefault())?.toInstant()

    val today: LocalDate get() = LocalDate.now()
}

/** Swift `String.capitalized`. */
internal fun rwCapitalized(s: String): String =
    s.split(" ").joinToString(" ") { w -> w.lowercase(Locale.US).replaceFirstChar { it.titlecase(Locale.US) } }

internal fun rwError(o: JsonObject, fallback: String): String = nonEmpty(o.str("error")) ?: fallback

/** Decodes `o["participation"]` into a model. */
internal fun rwParticipation(o: JsonObject): EventParticipation? {
    val el = o["participation"] as? JsonObject ?: return null
    return runCatching { VaultJson.decodeFromJsonElement(EventParticipation.serializer(), el) }.getOrNull()
}

internal fun rwDateRange(e: CommunityEvent): String {
    val start = listOfNotNull(nonEmpty(e.start_date), nonEmpty(e.start_time)).joinToString(" ")
    val end = listOfNotNull(nonEmpty(e.end_date), nonEmpty(e.end_time)).joinToString(" ")
    var s = "$start → $end"
    nonEmpty(e.timezone)?.let { s += " ($it)" }
    return s
}

internal val rwTierOrder = listOf("common", "rare", "epic", "legendary", "event")

internal fun rwTierRank(t: String?): Int {
    val i = rwTierOrder.indexOf(t ?: "")
    return if (i >= 0) i else rwTierOrder.size
}

internal fun rwTierColors(tier: String?): List<Color> = when (tier ?: "") {
    "rare" -> listOf(Vault.sky, hexColor("#0369a1"))
    "epic" -> listOf(Vault.orange, Vault.red)
    "legendary" -> listOf(Vault.amber, Vault.primary)
    "event" -> listOf(Vault.primary, Vault.red)
    else -> listOf(Color(0xFF8C8C8C), Color(0xFF383838))
}

internal fun rwPercent(v: Double): String {
    val p = if (v <= 1) v * 100 else v
    if (p == Math.rint(p)) return "${p.toInt()}%"
    return String.format(Locale.US, "%.1f%%", p)
}

internal fun rwRarityRank(r: String): Int {
    val i = CardRarity.entries.indexOfFirst { it.raw == r }
    return if (i >= 0) i else 99
}

/** Plural "s" helper. */
internal fun rwS(n: Int): String = if (n == 1) "" else "s"

// MARK: - Composables

/** Scrollable screen body with pull-to-refresh. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RwScroll(refreshing: Boolean, onRefresh: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            content = content,
        )
    }
}

@Composable
internal fun RwErrorState(message: String, retry: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 50.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(Icons.Filled.Warning, null, tint = Vault.red.copy(alpha = 0.8f), modifier = Modifier.size(40.dp))
        Text(message, color = Vault.muted, fontSize = 14.sp, textAlign = TextAlign.Center)
        PrimaryButton("Try again", retry)
    }
}

@Composable
internal fun RwSectionTitle(title: String, modifier: Modifier = Modifier, icon: ImageVector? = null, color: Color = Vault.primary) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (icon != null) Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
        Text(title.uppercase(Locale.US), color = Vault.foreground, fontSize = 13.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
    }
}

@Composable
internal fun RwRarityPill(rarity: String?) {
    val r = CardRarity.of(rarity)
    val c = hexColor(r.hex)
    val shape = RoundedCornerShape(4.dp)
    Text(
        r.label, color = c, fontSize = 10.sp, fontWeight = FontWeight.Black,
        modifier = Modifier.background(c.copy(alpha = 0.15f), shape).border(1.dp, c.copy(alpha = 0.5f), shape)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
internal fun RwNotice(text: String, color: Color, icon: ImageVector) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier.fillMaxWidth().background(color.copy(alpha = 0.12f), shape).border(1.dp, color.copy(alpha = 0.4f), shape).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
        Text(text, color = Vault.foreground, fontSize = 12.sp, modifier = Modifier.weight(1f))
    }
}

/** Hero card: image (or accent gradient), dark gradient, optional snow; content anchored bottom-left. */
@Composable
internal fun RwHero(
    image: String?,
    accent: Color,
    modifier: Modifier = Modifier,
    snow: Boolean = false,
    lights: Boolean = false,
    minHeight: Dp = 0.dp,
    topPadding: Dp = 0.dp,
    spacing: Dp = 8.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Box(modifier.fillMaxWidth().heightIn(min = minHeight).clip(shape).border(1.dp, accent.copy(alpha = 0.5f), shape)) {
        if (image != null) RemoteImage(image, Modifier.matchParentSize())
        else Box(Modifier.matchParentSize().background(Brush.linearGradient(listOf(accent, Vault.background))))
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Black.copy(alpha = 0.92f)))))
        if (snow) SnowLayer(Modifier.matchParentSize(), lights = lights)
        Column(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(16.dp).padding(top = topPadding),
            verticalArrangement = Arrangement.spacedBy(spacing),
            content = content,
        )
    }
}

@Composable
internal fun RwLeaderboardList(rows: List<LeaderboardRow>?, target: Int = 0, suffix: String = "", accent: Color = Vault.primary) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (rows == null) {
            Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Vault.muted, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            }
        } else if (rows.isEmpty()) {
            Text("No participants yet.", color = Vault.muted, fontSize = 12.sp)
        } else {
            rows.take(8).forEachIndexed { i, r ->
                val rank = i + 1
                val value = if (target > 0) "${r.progress}/$target" else "${r.progress}"
                Row(
                    Modifier.fillMaxWidth().background(Vault.secondary.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("#$rank", color = if (rank <= 3) accent else Vault.muted, fontSize = 12.sp, fontWeight = FontWeight.Black, modifier = Modifier.width(30.dp))
                    Text(nonEmpty(r.name) ?: "Reader", color = Vault.foreground, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(if (suffix.isEmpty()) value else "$value $suffix", color = Vault.foreground, fontSize = 12.sp, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

/** Non-lazy adaptive grid (safe inside a verticalScroll column). */
@Composable
internal fun <T> RwGrid(items: List<T>, minCell: Dp, spacing: Dp, cell: @Composable (T) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cols = maxOf(1, ((maxWidth + spacing) / (minCell + spacing)).toInt())
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing)) {
            for (row in items.chunked(cols)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing)) {
                    for (item in row) {
                        Box(Modifier.weight(1f)) { cell(item) }
                    }
                    repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

// MARK: - Special events (Christmas & Boruto)

internal class RwSpecialQuest(val key: String, val title: String, val desc: String, val points: Int, val goal: Int)

internal val rwChristmasQuests = listOf(
    RwSpecialQuest("c1_reader", "Winter Reader", "Register reading progress for 2 different volumes.", 75, 2),
    RwSpecialQuest("c2_collector", "Christmas Collector", "Add 3 different volumes to your collection.", 100, 3),
    RwSpecialQuest("c3_discovery", "Holiday Discovery", "Add a manga that was not in your collection yet.", 75, 1),
    RwSpecialQuest("c4_spirit", "Share the Christmas Spirit", "Publish a manga recommendation or review (min. 20 characters).", 100, 1),
    RwSpecialQuest("c5_champion", "Christmas Champion", "Complete 3 of the quests above. Unlocks the exclusive card + badge.", 200, 3),
)

internal val rwBorutoQuests = listOf(
    RwSpecialQuest("q1_visit", "The Shinobi Returns", "Visit the Boruto Volume 7 event page.", 50, 1),
    RwSpecialQuest("q2_collect", "Boruto Collector", "Add Boruto: Two Blue Vortex Volume 7 (English) to your collection.", 100, 1),
    RwSpecialQuest("q3_progress", "New Volume, New Adventure", "Register reading progress for Volume 7.", 100, 1),
    RwSpecialQuest("q4_review", "Share the Hype", "Publish a recommendation or review about Boruto: Two Blue Vortex.", 75, 1),
    RwSpecialQuest("q5_champion", "Release Week Champion", "Complete three of the quests above.", 150, 3),
)

@Composable
internal fun RwSpecialQuestRow(quest: RwSpecialQuest, count: Int, done: Boolean, accent: Color, trailing: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().cardStyle(highlighted = done).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(
                if (done) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked, null,
                tint = if (done) Vault.emerald else Vault.muted, modifier = Modifier.size(22.dp),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(quest.title, color = Vault.foreground, fontSize = 14.sp, fontWeight = FontWeight.Black)
                Text(quest.desc, color = Vault.muted, fontSize = 12.sp)
                Text("+${quest.points} pts · ${minOf(count, quest.goal)}/${quest.goal}", color = accent, fontSize = 11.sp, fontWeight = FontWeight.Black)
            }
            if (done) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Icon(Icons.Filled.Check, null, tint = Vault.emerald, modifier = Modifier.size(14.dp))
                    Text("Done", color = Vault.emerald, fontSize = 12.sp, fontWeight = FontWeight.Black)
                }
            }
        }
        if (!done) trailing()
    }
}

@Composable
internal fun RwTextEditorBox(placeholder: String, text: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = text,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth().heightIn(min = 90.dp),
        placeholder = { Text(placeholder, color = Vault.muted, fontSize = 14.sp) },
        minLines = 3,
        shape = RoundedCornerShape(10.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Vault.secondary,
            unfocusedContainerColor = Vault.secondary,
            focusedBorderColor = Vault.primary,
            unfocusedBorderColor = Vault.border,
            focusedTextColor = Vault.foreground,
            unfocusedTextColor = Vault.foreground,
            cursorColor = Vault.primary,
        ),
    )
}

/** Smaller in-card action button used by the special event quests. */
@Composable
internal fun RwSmallButton(title: String, icon: ImageVector? = null, color: Color = Vault.primary, enabled: Boolean = true, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Row(
        Modifier
            .clip(shape)
            .background(if (enabled) color else color.copy(alpha = 0.5f), shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (icon != null) Icon(icon, null, tint = Color.White, modifier = Modifier.size(14.dp))
        Text(title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

/** Swift `Label(text, systemImage:)`. */
@Composable
internal fun RwLabel(text: String, icon: ImageVector, color: Color, modifier: Modifier = Modifier, fontSize: Int = 12, bold: Boolean = true) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, null, tint = color, modifier = Modifier.size((fontSize + 2).dp))
        Text(text, color = color, fontSize = fontSize.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Pill-style link text ("View Event →"). */
@Composable
internal fun RwLink(text: String, color: Color, onClick: () -> Unit) {
    Text(text, color = color, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = onClick).padding(vertical = 2.dp))
}
