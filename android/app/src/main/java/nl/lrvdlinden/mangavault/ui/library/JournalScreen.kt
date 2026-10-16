package nl.lrvdlinden.mangavault.ui.library

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Note
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import nl.lrvdlinden.mangavault.app.AppState
import nl.lrvdlinden.mangavault.app.LocalApp
import nl.lrvdlinden.mangavault.app.Route
import nl.lrvdlinden.mangavault.core.Base44
import nl.lrvdlinden.mangavault.model.JournalEntry
import nl.lrvdlinden.mangavault.model.JournalType
import nl.lrvdlinden.mangavault.model.nonEmpty
import nl.lrvdlinden.mangavault.ui.components.Badge
import nl.lrvdlinden.mangavault.ui.components.Chip
import nl.lrvdlinden.mangavault.ui.components.EmptyState
import nl.lrvdlinden.mangavault.ui.components.LoadingView
import nl.lrvdlinden.mangavault.ui.components.PrimaryButton
import nl.lrvdlinden.mangavault.ui.components.ScreenHeader
import nl.lrvdlinden.mangavault.ui.components.StarRating
import nl.lrvdlinden.mangavault.ui.components.cardStyle
import nl.lrvdlinden.mangavault.ui.theme.Vault

private data class JournalDraft(
    val entryId: String?,
    val mangaId: String,
    val title: String,
    val volume: String,
    val type: JournalType,
    val content: String,
    val rating: Int,
    val spoiler: Boolean,
) {
    companion object {
        fun of(entry: JournalEntry?) = JournalDraft(
            entryId = entry?.id,
            mangaId = entry?.manga_id ?: "",
            title = entry?.title ?: "",
            volume = entry?.volume_number?.toInt()?.toString() ?: "",
            type = entry?.type ?: JournalType.NOTE,
            content = entry?.content ?: "",
            rating = (entry?.rating ?: 0.0).toInt(),
            spoiler = entry?.is_spoiler ?: false,
        )
    }
}

private val journalSorts = listOf("recent" to "Most Recent", "title" to "Manga A-Z", "type" to "By Type")

/** Manga Journal: private notes, favorite moments, ratings and reminders. */
@Composable
fun JournalScreen() {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    var entries by remember { mutableStateOf<List<JournalEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf("recent") }
    var typeFilter by remember { mutableStateOf<JournalType?>(null) }
    var draft by remember { mutableStateOf<JournalDraft?>(null) }
    var revealed by remember { mutableStateOf<Set<String>>(emptySet()) }
    var sortMenu by remember { mutableStateOf(false) }

    suspend fun load() {
        loading = true
        try {
            entries = Base44.list<JournalEntry>("JournalEntry", sort = "-created_date", limit = 1000).items.distinctBy { it.id }
        } catch (e: Exception) {
            app.fail("Could not load journal", e)
        }
        loading = false
    }

    suspend fun delete(e: JournalEntry) {
        try {
            Base44.delete("JournalEntry", e.id)
            entries = entries.filter { it.id != e.id }
            app.show("Entry deleted")
        } catch (ex: Exception) {
            app.fail("Could not delete entry", ex)
        }
    }

    LaunchedEffect(Unit) { load() }

    val filtered: List<JournalEntry> = run {
        val q = libTrim(search).lowercase()
        var r = entries.filter { e ->
            q.isEmpty() || (e.content ?: "").lowercase().contains(q) || (e.title ?: "").lowercase().contains(q)
        }
        val tf = typeFilter
        if (tf != null) r = r.filter { it.type == tf }
        when (sort) {
            "title" -> r.sortedWith(compareBy<JournalEntry, String>(String.CASE_INSENSITIVE_ORDER) { it.title ?: "" })
            "type" -> r.sortedWith(
                compareBy<JournalEntry> { it.entry_type ?: "" }.thenByDescending { it.created_date ?: "" },
            )
            else -> r.sortedByDescending { it.created_date ?: "" }
        }
    }

    Box(Modifier.fillMaxSize().background(Vault.background)) {
        LibRefreshBox(
            refreshing = refreshing,
            onRefresh = { scope.launch { refreshing = true; load(); refreshing = false } },
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    ScreenHeader(
                        lead = "MY MANGA", accent = "JOURNAL",
                        eyebrow = "Manga Journal", eyebrowIcon = Icons.AutoMirrored.Filled.Note,
                        subtitle = "Private notes, favorite moments, ratings and reminders — for series and volumes.",
                    )
                }
                item {
                    PrimaryButton("New Entry", { draft = JournalDraft.of(null) }, Modifier.fillMaxWidth(), icon = Icons.Filled.Add)
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LibTextField(search, { search = it }, "Search entries...", Modifier.weight(1f))
                        Box {
                            val shape = RoundedCornerShape(10.dp)
                            Box(
                                Modifier.size(52.dp).background(Vault.card, shape).border(1.dp, Vault.border, shape)
                                    .clickable { sortMenu = true },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Sort, "Sort", tint = Vault.foreground)
                            }
                            DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                                for ((key, label) in journalSorts) {
                                    DropdownMenuItem(
                                        text = { Text(label, color = if (sort == key) Vault.primary else Vault.foreground) },
                                        onClick = { sort = key; sortMenu = false },
                                    )
                                }
                            }
                        }
                    }
                }
                item {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip("All", typeFilter == null) { typeFilter = null }
                        for (t in JournalType.entries) {
                            Chip(t.label, typeFilter == t, icon = libJournalIcon(t)) { typeFilter = t }
                        }
                    }
                }
                if (loading && entries.isEmpty()) {
                    item { LoadingView() }
                } else if (filtered.isEmpty()) {
                    item {
                        EmptyState(Icons.AutoMirrored.Filled.Note, if (entries.isEmpty()) "Your journal is empty." else "No entries match your filters.")
                    }
                } else {
                    items(filtered, key = { it.id }) { e ->
                        JournalEntryCard(
                            e = e,
                            revealed = revealed.contains(e.id),
                            onToggleReveal = {
                                revealed = if (revealed.contains(e.id)) revealed - e.id else revealed + e.id
                            },
                            onOpen = { r -> app.open(r) },
                            onEdit = { draft = JournalDraft.of(e) },
                            onDelete = { scope.launch { delete(e) } },
                        )
                    }
                }
            }
        }
    }

    val d = draft
    if (d != null) {
        JournalEditorDialog(
            initial = d,
            app = app,
            onDismiss = { draft = null },
            onSaved = { scope.launch { load() } },
        )
    }
}

private fun journalMetaLine(e: JournalEntry): String {
    val parts = mutableListOf<String>()
    nonEmpty(e.title)?.let { parts.add(it) }
    e.volume_number?.let { parts.add("Vol. ${it.toInt()}") }
    return parts.joinToString(" · ")
}

private fun journalRoute(e: JournalEntry): Route? {
    val id = nonEmpty(e.manga_id) ?: return null
    val v = e.volume_number
    return if (v != null) Route.Volume(id, v.toInt()) else Route.Manga(id)
}

@Composable
private fun JournalEntryCard(
    e: JournalEntry,
    revealed: Boolean,
    onToggleReveal: () -> Unit,
    onOpen: (Route) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val rating = (e.rating ?: 0.0).toInt()
    val isSpoiler = e.is_spoiler ?: false
    val hidden = isSpoiler && !revealed
    val meta = journalMetaLine(e)
    Column(Modifier.fillMaxWidth().cardStyle().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Badge(e.type.label.uppercase(), icon = libJournalIcon(e.type), fg = Vault.primary, bg = Vault.primary.copy(alpha = 0.15f))
            if (isSpoiler) Badge("SPOILER", icon = Icons.Filled.VisibilityOff, fg = Vault.red, bg = Vault.red.copy(alpha = 0.15f))
            Spacer(Modifier.weight(1f))
            Text((e.created_date ?: "").take(10), color = Vault.muted, fontSize = 11.sp)
        }
        if (meta.isNotEmpty()) Text(meta, color = Vault.foreground, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        if (rating > 0) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StarRating(rating, size = 11.dp)
                Text("$rating/10", color = Vault.orange, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
        val content = e.content ?: ""
        // Modifier.blur only works on Android 12+; mask the text on older devices instead.
        val canBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        val shown = if (hidden && !canBlur) content.map { if (it.isWhitespace()) it else '•' }.joinToString("") else content
        Text(
            shown,
            color = Vault.foreground.copy(alpha = 0.9f),
            fontSize = 14.sp,
            modifier = Modifier
                .fillMaxWidth()
                .then(if (hidden && canBlur) Modifier.blur(6.dp) else Modifier)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { if (isSpoiler) onToggleReveal() },
        )
        if (hidden) Text("Tap to reveal spoiler", color = Vault.muted, fontSize = 11.sp)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val route = journalRoute(e)
            if (route != null) {
                LibSmallButton(text = "Open manga", icon = Icons.AutoMirrored.Filled.MenuBook, color = Vault.primary, onClick = { onOpen(route) })
            }
            Spacer(Modifier.weight(1f))
            LibSmallButton(text = "Edit", icon = Icons.Filled.Edit, onClick = onEdit)
            LibSmallButton(text = "Delete", icon = Icons.Filled.Delete, color = Vault.red, onClick = onDelete)
        }
    }
}

// MARK: Editor

@Composable
private fun JournalEditorDialog(
    initial: JournalDraft,
    app: AppState,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var draft by remember(initial) { mutableStateOf(initial) }
    var saving by remember { mutableStateOf(false) }

    suspend fun save() {
        val content = libTrim(draft.content)
        if (content.isEmpty()) {
            app.show("Content is required", error = true)
            return
        }
        saving = true
        val payload = mapOf<String, Any?>(
            "manga_id" to draft.mangaId,
            "title" to libTrim(draft.title),
            "volume_number" to libNumberOrNull(draft.volume),
            "entry_type" to draft.type.raw,
            "content" to content,
            "rating" to draft.rating,
            "is_spoiler" to draft.spoiler,
        )
        try {
            val id = draft.entryId
            if (id != null) {
                Base44.update("JournalEntry", id, payload)
                app.show("Entry updated")
            } else {
                Base44.create<JournalEntry>("JournalEntry", payload)
                app.show("Journal entry saved")
            }
            saving = false
            onSaved()
            onDismiss()
        } catch (e: Exception) {
            app.fail("Could not save entry", e)
        }
        saving = false
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Vault.background).imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss) { Text("Cancel", color = Vault.muted) }
                Text(
                    if (draft.entryId == null) "New Entry" else "Edit Entry",
                    color = Vault.foreground, fontSize = 17.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                if (saving) {
                    Box(Modifier.size(64.dp, 40.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(18.dp), color = Vault.primary, strokeWidth = 2.dp)
                    }
                } else {
                    TextButton(onClick = { scope.launch { save() } }) { Text("Save", color = Vault.primary, fontWeight = FontWeight.Bold) }
                }
            }
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                JournalEditorSection("Manga") {
                    LibTextField(draft.title, { draft = draft.copy(title = it) }, "Manga Title", Modifier.fillMaxWidth())
                    LibTextField(draft.volume, { draft = draft.copy(volume = it) }, "Volume (optional)", Modifier.fillMaxWidth(), number = true)
                }
                JournalEditorSection("Type") {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (t in JournalType.entries) {
                            Chip(t.label, draft.type == t, icon = libJournalIcon(t)) { draft = draft.copy(type = t) }
                        }
                    }
                }
                JournalEditorSection("Content") {
                    LibTextField(
                        draft.content, { draft = draft.copy(content = it) }, "Write your thoughts...",
                        Modifier.fillMaxWidth(), singleLine = false, minLines = 4, maxLines = 12,
                    )
                }
                JournalEditorSection("Rating") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StarRating(draft.rating, size = 24.dp) { r ->
                            draft = draft.copy(rating = if (draft.rating == r) 0 else r)
                        }
                        Spacer(Modifier.weight(1f))
                        Text(
                            if (draft.rating > 0) "${draft.rating}/10" else "Not rated",
                            color = Vault.muted, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Row(
                    Modifier.fillMaxWidth().cardStyle().padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Contains spoilers", color = Vault.foreground, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Switch(
                        checked = draft.spoiler,
                        onCheckedChange = { draft = draft.copy(spoiler = it) },
                        colors = SwitchDefaults.colors(checkedTrackColor = Vault.primary, checkedThumbColor = Color.White),
                    )
                }
            }
        }
    }
}

@Composable
private fun JournalEditorSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LibFieldLabel(title)
        Column(
            Modifier.fillMaxWidth().cardStyle().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            content()
        }
    }
}
