package nl.lrvdlinden.mangavault.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import nl.lrvdlinden.mangavault.app.LocalApp
import nl.lrvdlinden.mangavault.core.Base44
import nl.lrvdlinden.mangavault.core.VaultService
import nl.lrvdlinden.mangavault.model.DayKey
import nl.lrvdlinden.mangavault.model.ReadingActivity
import nl.lrvdlinden.mangavault.model.ReadingSession
import nl.lrvdlinden.mangavault.model.nonEmpty
import nl.lrvdlinden.mangavault.ui.components.LoadingView
import nl.lrvdlinden.mangavault.ui.components.PrimaryButton
import nl.lrvdlinden.mangavault.ui.components.ScreenHeader
import nl.lrvdlinden.mangavault.ui.components.SectionCard
import nl.lrvdlinden.mangavault.ui.components.StatCard
import nl.lrvdlinden.mangavault.ui.components.cardStyle
import nl.lrvdlinden.mangavault.ui.theme.Vault
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val calWeekdayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
private val calMonthTitle: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US)
private val calMonthShort: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM", Locale.US)

/** Reading Calendar: streaks, month heat-map, last 12 months and manual reading sessions. */
@Composable
fun CalendarScreen() {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    var activities by remember { mutableStateOf<List<ReadingActivity>>(emptyList()) }
    var sessions by remember { mutableStateOf<List<ReadingSession>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var month by remember { mutableStateOf(YearMonth.now()) }
    var showForm by remember { mutableStateOf(false) }
    var fTitle by remember { mutableStateOf("") }
    var fDate by remember { mutableStateOf(LocalDate.now()) }
    var fVol by remember { mutableStateOf("") }
    var fMin by remember { mutableStateOf("") }
    var fCh by remember { mutableStateOf("") }
    var fNotes by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }

    suspend fun load() {
        loading = true
        try {
            val acts = VaultService.activities(limit = 2000)
            val page = Base44.list<ReadingSession>("ReadingSession", sort = "-date", limit = 1000)
            activities = acts
            sessions = page.items
        } catch (e: Exception) {
            app.fail("Could not load calendar", e)
        }
        loading = false
    }

    suspend fun saveSession() {
        val title = libTrim(fTitle)
        val date = DayKey.local(fDate)
        if (title.isEmpty() || date.isEmpty()) {
            app.show("Title and date are required", error = true)
            return
        }
        saving = true
        val payload = mapOf<String, Any?>(
            "manga_id" to "",
            "title" to title,
            "volume_number" to libNumberOrNull(fVol),
            "date" to date,
            "minutes" to (libTrim(fMin).toIntOrNull() ?: 0),
            "chapters_read" to (libTrim(fCh).toIntOrNull() ?: 0),
            "notes" to libTrim(fNotes),
        )
        try {
            Base44.create<ReadingSession>("ReadingSession", payload)
            app.show("Reading session logged")
            fTitle = ""; fVol = ""; fMin = ""; fCh = ""; fNotes = ""; fDate = LocalDate.now()
            showForm = false
            saving = false
            load()
        } catch (e: Exception) {
            app.fail("Could not log session", e)
        }
        saving = false
    }

    suspend fun deleteSession(s: ReadingSession) {
        try {
            Base44.delete("ReadingSession", s.id)
            sessions = sessions.filter { it.id != s.id }
            app.show("Session deleted")
        } catch (e: Exception) {
            app.fail("Could not delete session", e)
        }
    }

    LaunchedEffect(Unit) { load() }

    // Derived data
    val dayCounts: Map<String, Int> = run {
        val m = mutableMapOf<String, Int>()
        for (a in activities) { val d = a.day; if (d.isNotEmpty()) m[d] = (m[d] ?: 0) + 1 }
        for (s in sessions) { val d = s.day; if (d.isNotEmpty()) m[d] = (m[d] ?: 0) + 2 }
        m
    }
    val sessionDays: Set<String> = sessions.map { it.day }.filter { it.isNotEmpty() }.toSet()
    val activeDays = dayCounts.keys.toList()

    LibRefreshBox(
        refreshing = refreshing,
        onRefresh = { scope.launch { refreshing = true; load(); refreshing = false } },
        modifier = Modifier.fillMaxSize().background(Vault.background),
    ) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            ScreenHeader(
                lead = "READING", accent = "CALENDAR",
                eyebrow = "Reading Calendar", eyebrowIcon = Icons.Filled.CalendarMonth,
                subtitle = "Track every reading day, build streaks, and log sessions from your real activity.",
            )
            if (loading && activities.isEmpty() && sessions.isEmpty()) {
                LoadingView()
            } else {
                CalStatsGrid(activeDays)
                CalMonthCard(
                    month = month,
                    counts = dayCounts,
                    sessionDays = sessionDays,
                    onShift = { delta -> month = month.plusMonths(delta.toLong()) },
                    onToday = { month = YearMonth.now() },
                )
                CalYearCard(activeDays)
                SectionCard(
                    title = "Reading Sessions",
                    trailing = {
                        LibSmallButton(
                            text = if (showForm) "Cancel" else "Log Session",
                            icon = if (showForm) Icons.Filled.Close else Icons.Filled.Add,
                            color = Vault.primary,
                            onClick = { showForm = !showForm },
                        )
                    },
                ) {
                    if (showForm) {
                        CalSessionForm(
                            title = fTitle, onTitle = { fTitle = it },
                            date = fDate, onDate = { fDate = it },
                            vol = fVol, onVol = { fVol = it },
                            min = fMin, onMin = { fMin = it },
                            ch = fCh, onCh = { fCh = it },
                            notes = fNotes, onNotes = { fNotes = it },
                            saving = saving,
                            onSave = { scope.launch { saveSession() } },
                        )
                    }
                    if (sessions.isEmpty()) {
                        Text(
                            "No manual sessions logged yet. Your reading activity (progress updates, status changes) still counts toward streaks automatically.",
                            color = Vault.muted, fontSize = 14.sp,
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (s in sessions.take(30)) {
                                CalSessionRow(s, onDelete = { scope.launch { deleteSession(s) } })
                            }
                        }
                    }
                }
            }
        }
    }
}

// MARK: Stats

@Composable
private fun CalStatsGrid(days: List<String>) {
    val set = days.toSet()
    val streak = DayKey.streak(days)
    val longest = DayKey.longest(days)
    val start = DayKey.weekStart()
    val week = (0 until 7).count { set.contains(DayKey.local(start.plusDays(it.toLong()))) }
    val monthKey = DayKey.local().take(7)
    val monthCount = days.count { it.startsWith(monthKey) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard(Icons.Filled.LocalFireDepartment, "Current Streak", "${streak}d", Modifier.weight(1f))
            StatCard(Icons.Filled.EmojiEvents, "Longest Streak", "${longest}d", Modifier.weight(1f), accent = Vault.amber)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard(Icons.Filled.EventAvailable, "Active This Week", "$week/7", Modifier.weight(1f), accent = Vault.emerald)
            StatCard(Icons.Filled.CalendarMonth, "Active This Month", "$monthCount", Modifier.weight(1f), accent = Vault.sky)
        }
    }
}

// MARK: Month grid

private fun calMonthCells(month: YearMonth): List<String?> {
    val start = month.atDay(1)
    val lead = start.dayOfWeek.value - 1 // Monday first
    val cells = mutableListOf<String?>()
    repeat(lead) { cells.add(null) }
    for (i in 0 until month.lengthOfMonth()) cells.add(DayKey.local(start.plusDays(i.toLong())))
    while (cells.size % 7 != 0) cells.add(null)
    return cells
}

private fun calCellFill(count: Int): Color =
    if (count > 0) Vault.primary.copy(alpha = minOf(1f, 0.35f + count * 0.18f)) else Vault.secondary.copy(alpha = 0.5f)

@Composable
private fun CalMonthCard(
    month: YearMonth,
    counts: Map<String, Int>,
    sessionDays: Set<String>,
    onShift: (Int) -> Unit,
    onToday: () -> Unit,
) {
    val today = DayKey.local()
    val cells = calMonthCells(month)
    Column(Modifier.fillMaxWidth().cardStyle().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                calMonthTitle.format(month.atDay(1)),
                color = Vault.foreground, fontSize = 17.sp, fontWeight = FontWeight.Black,
                modifier = Modifier.weight(1f),
            )
            LibSmallButton(text = null, icon = Icons.Filled.ChevronLeft, onClick = { onShift(-1) })
            LibSmallButton(text = "Today", color = Vault.primary, onClick = onToday)
            LibSmallButton(text = null, icon = Icons.Filled.ChevronRight, onClick = { onShift(1) })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (name in calWeekdayNames) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(name, color = Vault.muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        for (row in cells.chunked(7)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (key in row) {
                    Box(Modifier.weight(1f).height(38.dp)) {
                        if (key != null) CalDayCell(key, counts[key] ?: 0, key == today, sessionDays.contains(key))
                    }
                }
            }
        }
    }
}

@Composable
private fun CalDayCell(key: String, count: Int, isToday: Boolean, hasSession: Boolean) {
    val shape = RoundedCornerShape(7.dp)
    val num = key.takeLast(2).toIntOrNull() ?: 0
    Box(
        Modifier.fillMaxSize()
            .background(calCellFill(count), shape)
            .border(2.dp, if (isToday) Vault.primary else Color.Transparent, shape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "$num",
            color = if (count > 0) Color.White else Vault.muted,
            fontSize = 12.sp,
            fontWeight = if (count > 0) FontWeight.Black else FontWeight.Medium,
        )
        if (hasSession) {
            Box(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp).size(4.dp)
                    .background(Color.White, CircleShape),
            )
        }
    }
}

// MARK: Year activity

@Composable
private fun CalYearCard(days: List<String>) {
    val thisMonth = YearMonth.now()
    val buckets = (11 downTo 0).map { i ->
        val d = thisMonth.minusMonths(i.toLong())
        val key = d.toString() // yyyy-MM
        calMonthShort.format(d.atDay(1)) to days.count { it.startsWith(key) }
    }
    val maxCount = maxOf(1, buckets.maxOfOrNull { it.second } ?: 1)
    SectionCard(title = "Year Activity (last 12 months)") {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
            for ((label, count) in buckets) {
                val ratio = count.toFloat() / maxCount
                val alpha = if (count > 0) 0.2f + 0.8f * ratio else 0.08f
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("$count", color = Vault.muted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Box(Modifier.fillMaxWidth().height(44.dp).background(Vault.primary.copy(alpha = alpha), RoundedCornerShape(4.dp)))
                    Text(label.take(1), color = Vault.muted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// MARK: Sessions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalSessionForm(
    title: String, onTitle: (String) -> Unit,
    date: LocalDate, onDate: (LocalDate) -> Unit,
    vol: String, onVol: (String) -> Unit,
    min: String, onMin: (String) -> Unit,
    ch: String, onCh: (String) -> Unit,
    notes: String, onNotes: (String) -> Unit,
    saving: Boolean,
    onSave: () -> Unit,
) {
    var pickDate by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().background(Vault.background.copy(alpha = 0.6f), RoundedCornerShape(12.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        LibFieldLabel("Manga Title")
        LibTextField(title, onTitle, "e.g. One Piece", Modifier.fillMaxWidth())
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Date", color = Vault.foreground, fontSize = 14.sp, modifier = Modifier.weight(1f))
            LibSmallButton(text = DayKey.local(date), icon = Icons.Filled.CalendarMonth, color = Vault.primary, onClick = { pickDate = true })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LibTextField(vol, onVol, "Vol.", Modifier.weight(1f), number = true)
            LibTextField(min, onMin, "Min.", Modifier.weight(1f), number = true)
            LibTextField(ch, onCh, "Ch.", Modifier.weight(1f), number = true)
        }
        LibTextField(notes, onNotes, "Notes (private)", Modifier.fillMaxWidth(), singleLine = false, minLines = 2, maxLines = 5)
        PrimaryButton("Save Session", onSave, Modifier.fillMaxWidth(), loading = saving)
    }
    if (pickDate) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        onDate(Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    pickDate = false
                }) { Text("OK", color = Vault.primary) }
            },
            dismissButton = {
                TextButton(onClick = { pickDate = false }) { Text("Cancel", color = Vault.muted) }
            },
        ) {
            DatePicker(state = state)
        }
    }
}

private fun calSessionSubtitle(s: ReadingSession): String {
    val parts = mutableListOf(s.day)
    s.volume_number?.let { parts.add("Vol. ${it.toInt()}") }
    val mins = (s.minutes ?: 0.0).toInt()
    if (mins > 0) parts.add("$mins min")
    val ch = (s.chapters_read ?: 0.0).toInt()
    if (ch > 0) parts.add("$ch ch")
    return parts.joinToString(" · ")
}

@Composable
private fun CalSessionRow(s: ReadingSession, onDelete: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Vault.secondary.copy(alpha = 0.5f), RoundedCornerShape(10.dp)).padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.AutoMirrored.Filled.MenuBook, null, tint = Vault.primary, modifier = Modifier.padding(top = 2.dp).size(18.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(nonEmpty(s.title) ?: "Reading session", color = Vault.foreground, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(calSessionSubtitle(s), color = Vault.muted, fontSize = 12.sp)
            nonEmpty(s.notes)?.let { Text(it, color = Vault.foreground.copy(alpha = 0.8f), fontSize = 12.sp) }
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Filled.Delete, "Delete", tint = Vault.red, modifier = Modifier.size(18.dp))
        }
    }
}
