package nl.lrvdlinden.mangavault.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nl.lrvdlinden.mangavault.app.LocalApp
import nl.lrvdlinden.mangavault.core.VaultService
import nl.lrvdlinden.mangavault.model.CollectionItem
import nl.lrvdlinden.mangavault.model.ReadStatus
import nl.lrvdlinden.mangavault.model.ReadingActivity
import nl.lrvdlinden.mangavault.ui.components.Chip
import nl.lrvdlinden.mangavault.ui.components.LoadingView
import nl.lrvdlinden.mangavault.ui.components.ScreenHeader
import nl.lrvdlinden.mangavault.ui.components.SectionCard
import nl.lrvdlinden.mangavault.ui.components.StatCard
import nl.lrvdlinden.mangavault.ui.theme.Vault
import java.time.LocalDate
import java.util.Locale
import java.util.TreeMap
import kotlin.coroutines.cancellation.CancellationException

private data class StatDatum(val name: String, val value: Int, val key: String = "")

private val statRanges = listOf(7 to "7 days", 30 to "30 days", 90 to "90 days", 0 to "All time")

private fun statStatusData(items: List<CollectionItem>): List<StatDatum> = listOf(
    StatDatum("Reading", items.count { it.effectiveStatus == ReadStatus.READING }, "reading"),
    StatDatum("Completed", items.count { it.effectiveStatus == ReadStatus.COMPLETED }, "completed"),
    StatDatum("Plan", items.count { it.effectiveStatus == ReadStatus.PLAN_TO_READ }, "plan_to_read"),
    StatDatum("On Hold", items.count { it.effectiveStatus == ReadStatus.ON_HOLD }, "on_hold"),
)

/** Cumulative volumes per month (yyyy-MM). */
private fun statGrowthData(items: List<CollectionItem>): List<StatDatum> {
    val perMonth = TreeMap<String, Int>()
    for (i in items) {
        val m = (i.created_date ?: "").take(7)
        if (m.isNotEmpty()) perMonth[m] = (perMonth[m] ?: 0) + 1
    }
    var total = 0
    return perMonth.entries.map { (k, v) -> total += v; StatDatum(k, total) }
}

private fun statStatusColor(key: String): Color = Vault.statusColor(ReadStatus.of(key) ?: ReadStatus.READING)

private fun statCapitalized(s: String): String =
    s.split(" ").joinToString(" ") { w -> w.replaceFirstChar { it.titlecase(Locale.US) } }

/** Activities per day (yyyy-MM-dd → count), with empty days filled in for fixed ranges. */
private fun statActivitySeries(activities: List<ReadingActivity>, range: Int): List<StatDatum> {
    val counts = TreeMap<String, Int>()
    val today = LocalDate.now()
    if (range > 0) for (n in 0 until range) counts[today.minusDays(n.toLong()).toString()] = 0
    val start: LocalDate? = if (range == 0) null else today.minusDays((range - 1).toLong())
    for (a in activities) {
        val day = a.day
        if (day.isEmpty()) continue
        if (start != null) {
            val d = runCatching { LocalDate.parse(day) }.getOrNull()
            if (d != null && d.isBefore(start)) continue
        }
        counts[day] = (counts[day] ?: 0) + 1
    }
    return counts.entries.map { (k, v) -> StatDatum(if (range == 0) k else k.drop(5), v) }
}

@Composable
fun StatisticsScreen() {
    val app = LocalApp.current
    var items by remember { mutableStateOf<List<CollectionItem>>(emptyList()) }
    var activities by remember { mutableStateOf<List<ReadingActivity>>(emptyList()) }
    var genreMap by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var loading by remember { mutableStateOf(true) }
    var range by remember { mutableStateOf(30) }

    LaunchedEffect(Unit) {
        try {
            val its = VaultService.collection()
            val acts = VaultService.activities()
            val gm = VaultService.genreMap(its)
            items = its; activities = acts; genreMap = gm
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            app.fail("Could not load statistics", e)
        }
        loading = false
    }

    val rangeLabel = statRanges.firstOrNull { it.first == range }?.second ?: ""

    Column(
        Modifier.fillMaxSize().background(Vault.background).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        ScreenHeader("YOUR", "STATISTICS", eyebrow = "Statistics", eyebrowIcon = Icons.Filled.BarChart,
            subtitle = "Deep dive into your reading activity — all from your real data.")
        Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Filled.CalendarMonth, null, tint = Vault.muted, modifier = Modifier.size(18.dp))
            for ((r, label) in statRanges) Chip(label, range == r) { range = r }
        }
        if (loading) {
            LoadingView()
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCard(Icons.AutoMirrored.Filled.LibraryBooks, "Total Volumes", "${items.size}", Modifier.weight(1f))
                    StatCard(Icons.AutoMirrored.Filled.TrendingUp, "Activities", "${activities.size}", Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCard(Icons.Filled.Explore, "Genres Explored", "${VaultService.genreCount(items, genreMap)}", Modifier.weight(1f))
                    StatCard(Icons.Filled.LocalFireDepartment, "Streak", "${VaultService.streak(activities)}d", Modifier.weight(1f))
                }
            }
            SectionCard("Reading Activity ($rangeLabel)") {
                val series = remember(activities, range) { statActivitySeries(activities, range) }
                if (series.isEmpty()) {
                    Box(Modifier.fillMaxWidth().heightIn(min = 160.dp), contentAlignment = Alignment.Center) {
                        Text("No activity in this range yet.", color = Vault.muted, fontSize = 14.sp)
                    }
                } else {
                    StatLineChart(series, 220.dp, area = false)
                }
            }
            SectionCard("Collection Growth") {
                val g = statGrowthData(items)
                if (g.isEmpty()) Text("No data yet.", color = Vault.muted, fontSize = 14.sp)
                else StatLineChart(g, 190.dp, area = true)
            }
            SectionCard("Status Distribution") {
                StatBarChart(statStatusData(items), 200.dp) { statStatusColor(it.key) }
            }
            SectionCard("Genre Distribution") {
                val g = VaultService.genreDistribution(items, genreMap, 10).map { StatDatum(it.first, it.second) }
                if (g.isEmpty()) Text("No data yet.", color = Vault.muted, fontSize = 14.sp)
                else StatGenreBars(g)
            }
        }
    }
}

// MARK: Charts (Canvas / Boxes)

/** Line chart with an optional gradient area; x labels thinned to ~5. */
@Composable
private fun StatLineChart(data: List<StatDatum>, height: Dp, area: Boolean) {
    val maxV = maxOf(1, data.maxOfOrNull { it.value } ?: 1)
    val primary = Vault.primary
    val grid = Vault.border
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth().height(height)) {
            Column(Modifier.width(30.dp).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                Text("$maxV", color = Vault.muted, fontSize = 10.sp, maxLines = 1)
                Text("${maxV / 2}", color = Vault.muted, fontSize = 10.sp, maxLines = 1)
                Text("0", color = Vault.muted, fontSize = 10.sp, maxLines = 1)
            }
            Canvas(Modifier.weight(1f).fillMaxHeight().padding(vertical = 6.dp)) {
                val w = size.width
                val h = size.height
                for (i in 0..2) {
                    val y = h * i / 2f
                    drawLine(grid, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
                }
                if (data.isEmpty()) return@Canvas
                val n = data.size
                fun px(i: Int): Float = if (n == 1) w / 2f else w * i / (n - 1).toFloat()
                fun py(v: Int): Float = h - h * v.toFloat() / maxV.toFloat()
                val line = Path()
                data.forEachIndexed { i, d -> if (i == 0) line.moveTo(px(i), py(d.value)) else line.lineTo(px(i), py(d.value)) }
                if (area) {
                    val fill = Path()
                    data.forEachIndexed { i, d -> if (i == 0) fill.moveTo(px(i), py(d.value)) else fill.lineTo(px(i), py(d.value)) }
                    fill.lineTo(px(n - 1), h)
                    fill.lineTo(px(0), h)
                    fill.close()
                    drawPath(fill, Brush.verticalGradient(listOf(primary.copy(alpha = 0.5f), primary.copy(alpha = 0f)), startY = 0f, endY = h))
                }
                drawPath(line, primary, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                if (n == 1) drawCircle(primary, 3.dp.toPx(), Offset(px(0), py(data[0].value)))
            }
        }
        val idx = if (data.size <= 5) data.indices.toList() else (0 until 5).map { it * (data.size - 1) / 4 }.distinct()
        Row(Modifier.fillMaxWidth().padding(start = 30.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            for (i in idx) Text(data[i].name, color = Vault.muted, fontSize = 10.sp, maxLines = 1)
        }
    }
}

/** Vertical bars with value on top and label below. */
@Composable
private fun StatBarChart(data: List<StatDatum>, height: Dp, color: (StatDatum) -> Color) {
    val maxV = maxOf(1, data.maxOfOrNull { it.value } ?: 1)
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
            for (d in data) {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${d.value}", color = Vault.muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Box(Modifier.fillMaxWidth().height(height - 40.dp), contentAlignment = Alignment.BottomCenter) {
                        if (d.value > 0) {
                            Box(
                                Modifier.fillMaxWidth().fillMaxHeight(d.value.toFloat() / maxV)
                                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)).background(color(d)),
                            )
                        }
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Vault.border))
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            for (d in data) {
                Text(d.name, color = Vault.muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
    }
}

/** Horizontal genre bars. */
@Composable
private fun StatGenreBars(data: List<StatDatum>) {
    val maxV = maxOf(1, data.maxOfOrNull { it.value } ?: 1)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (d in data) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(statCapitalized(d.name), color = Vault.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(96.dp))
                Box(Modifier.weight(1f).height(20.dp)) {
                    Box(
                        Modifier.fillMaxWidth(maxOf(0.02f, d.value.toFloat() / maxV)).fillMaxHeight()
                            .clip(RoundedCornerShape(4.dp)).background(Vault.primary),
                    )
                }
                Text("${d.value}", color = Vault.foreground, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
