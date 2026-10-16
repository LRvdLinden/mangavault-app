package nl.lrvdlinden.mangavault.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import nl.lrvdlinden.mangavault.app.AppState
import nl.lrvdlinden.mangavault.app.LocalApp
import nl.lrvdlinden.mangavault.core.Base44
import nl.lrvdlinden.mangavault.model.Manga
import nl.lrvdlinden.mangavault.model.TierDef
import nl.lrvdlinden.mangavault.model.TierEntry
import nl.lrvdlinden.mangavault.model.TierList
import nl.lrvdlinden.mangavault.model.nonEmpty
import nl.lrvdlinden.mangavault.ui.components.Badge
import nl.lrvdlinden.mangavault.ui.components.CoverInfo
import nl.lrvdlinden.mangavault.ui.components.CoverView
import nl.lrvdlinden.mangavault.ui.components.EmptyState
import nl.lrvdlinden.mangavault.ui.components.LoadingView
import nl.lrvdlinden.mangavault.ui.components.OutlineButton
import nl.lrvdlinden.mangavault.ui.components.PrimaryButton
import nl.lrvdlinden.mangavault.ui.components.RemoteImage
import nl.lrvdlinden.mangavault.ui.components.ScreenHeader
import nl.lrvdlinden.mangavault.ui.components.SectionCard
import nl.lrvdlinden.mangavault.ui.components.cardStyle
import nl.lrvdlinden.mangavault.ui.theme.Vault
import nl.lrvdlinden.mangavault.ui.theme.hexColor
import java.util.UUID

/** An entry in the editor, with a stable local id (entries have no id of their own). */
private data class TierItem(val uid: String, val entry: TierEntry)

private data class TierDraft(
    val recordId: String,
    val name: String,
    val description: String,
    val listType: String,
    val tiers: List<TierDef>,
    val entries: List<TierItem>,
    val isPublic: Boolean,
) {
    companion object {
        fun of(list: TierList): TierDraft {
            val t = list.tiers ?: emptyList()
            val defs = if (t.isEmpty()) TierList.defaultTiers else t
            return TierDraft(
                recordId = list.id,
                name = list.name ?: "My Tier List",
                description = list.description ?: "",
                listType = list.list_type ?: "manga",
                tiers = defs,
                entries = (list.entries ?: emptyList()).map { e ->
                    val fixed = if (e.tier < -1 || e.tier >= defs.size) e.copy(tier = -1) else e
                    TierItem(UUID.randomUUID().toString(), fixed)
                },
                isPublic = list.is_public ?: false,
            )
        }

        fun new(listType: String) = TierDraft(
            recordId = "", name = "My Tier List", description = "", listType = listType,
            tiers = TierList.defaultTiers, entries = emptyList(), isPublic = false,
        )
    }
}

/** Tier Lists: rank series and characters into tiers, save and share. */
@Composable
fun TierListsScreen() {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    var lists by remember { mutableStateOf<List<TierList>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<TierDraft?>(null) }

    suspend fun load() {
        loading = true
        try {
            lists = Base44.list<TierList>("TierList", sort = "-created_date", limit = 200).items.distinctBy { it.id }
        } catch (e: Exception) {
            app.fail("Could not load tier lists", e)
        }
        loading = false
    }

    suspend fun delete(t: TierList) {
        try {
            Base44.delete("TierList", t.id)
            lists = lists.filter { it.id != t.id }
            app.show("Tier list deleted")
        } catch (e: Exception) {
            app.fail("Could not delete tier list", e)
        }
    }

    LaunchedEffect(Unit) { load() }

    LibRefreshBox(
        refreshing = refreshing,
        onRefresh = { scope.launch { refreshing = true; load(); refreshing = false } },
        modifier = Modifier.fillMaxSize().background(Vault.background),
    ) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                ScreenHeader(
                    lead = "MANGA & CHARACTER", accent = "TIER LISTS",
                    eyebrow = "Tier Lists", eyebrowIcon = Icons.Filled.FormatListNumbered,
                    subtitle = "Rank series and characters into tiers. Drag and drop, save, and share.",
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PrimaryButton("New Manga List", { editing = TierDraft.new("manga") }, Modifier.weight(1f), icon = Icons.AutoMirrored.Filled.LibraryBooks)
                    OutlineButton("New Character List", { editing = TierDraft.new("character") }, Modifier.weight(1f), icon = Icons.Filled.People, color = Vault.primary)
                }
            }
            if (loading && lists.isEmpty()) {
                item { LoadingView() }
            } else if (lists.isEmpty()) {
                item { EmptyState(Icons.Filled.FormatListNumbered, "No tier lists yet. Create your first one above.") }
            } else {
                items(lists, key = { it.id }) { t ->
                    TierListCard(
                        t,
                        onEdit = { editing = TierDraft.of(t) },
                        onDelete = { scope.launch { delete(t) } },
                    )
                }
            }
        }
    }

    val d = editing
    if (d != null) {
        TierEditorDialog(
            initial = d,
            app = app,
            onDismiss = { editing = null },
            onSaved = { scope.launch { load() } },
        )
    }
}

@Composable
private fun TierCount(def: TierDef, count: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            Modifier.size(20.dp).background(hexColor(def.color), RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(def.name, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black, maxLines = 1)
        }
        Text("$count", color = Vault.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TierListCard(t: TierList, onEdit: () -> Unit, onDelete: () -> Unit) {
    val raw = t.tiers ?: emptyList()
    val tiers = if (raw.isEmpty()) TierList.defaultTiers else raw
    val entries = t.entries ?: emptyList()
    val isPublic = t.is_public ?: false
    val isCharacter = t.list_type == "character"
    Column(Modifier.fillMaxWidth().cardStyle().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(
                if (isCharacter) Icons.Filled.People else Icons.AutoMirrored.Filled.LibraryBooks, null,
                tint = Vault.primary, modifier = Modifier.size(18.dp),
            )
            Text(
                nonEmpty(t.name) ?: "Untitled", color = Vault.foreground, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            Badge(
                if (isPublic) "PUBLIC" else "PRIVATE",
                icon = if (isPublic) Icons.Filled.Public else Icons.Filled.Lock,
                fg = if (isPublic) Vault.emerald else Vault.muted,
                bg = if (isPublic) Vault.emerald.copy(alpha = 0.15f) else Vault.secondary,
            )
        }
        nonEmpty(t.description)?.let {
            Text(it, color = Vault.muted, fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            tiers.forEachIndexed { i, def -> TierCount(def, entries.count { it.tier == i }) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LibSmallButton(text = "Edit", icon = Icons.Filled.Edit, color = Vault.primary, onClick = onEdit)
            LibSmallButton(text = "Delete", icon = Icons.Filled.Delete, color = Vault.red, onClick = onDelete)
        }
    }
}

// MARK: Editor mutations (pure, on a copy of the entry list)

private fun tierRenumber(list: MutableList<TierItem>, tier: Int) {
    val idxs = list.indices.filter { list[it].entry.tier == tier }.sortedBy { list[it].entry.order }
    idxs.forEachIndexed { n, idx -> list[idx] = list[idx].copy(entry = list[idx].entry.copy(order = n)) }
}

private fun tierMove(entries: List<TierItem>, uid: String, tier: Int): List<TierItem> {
    val list = entries.toMutableList()
    val i = list.indexOfFirst { it.uid == uid }
    if (i < 0) return entries
    val old = list[i].entry.tier
    if (old == tier) return entries
    list[i] = list[i].copy(entry = list[i].entry.copy(tier = tier, order = 1_000_000))
    tierRenumber(list, tier)
    tierRenumber(list, old)
    return list
}

private fun tierShift(entries: List<TierItem>, uid: String, delta: Int): List<TierItem> {
    val list = entries.toMutableList()
    val i = list.indexOfFirst { it.uid == uid }
    if (i < 0) return entries
    val tier = list[i].entry.tier
    tierRenumber(list, tier)
    val ordered = list.indices.filter { list[it].entry.tier == tier }.sortedBy { list[it].entry.order }
    val pos = ordered.indexOf(i)
    if (pos < 0) return entries
    val target = pos + delta
    if (target < 0 || target >= ordered.size) return entries
    val j = ordered[target]
    val a = list[i].entry.order
    list[i] = list[i].copy(entry = list[i].entry.copy(order = list[j].entry.order))
    list[j] = list[j].copy(entry = list[j].entry.copy(order = a))
    return list
}

private fun tierRemove(entries: List<TierItem>, uid: String): List<TierItem> {
    val list = entries.toMutableList()
    val i = list.indexOfFirst { it.uid == uid }
    if (i < 0) return entries
    val tier = list[i].entry.tier
    list.removeAt(i)
    tierRenumber(list, tier)
    return list
}

private fun tierItemsIn(entries: List<TierItem>, tier: Int): List<TierItem> =
    entries.filter { it.entry.tier == tier }.sortedBy { it.entry.order }

// MARK: Editor

@Composable
private fun TierEditorDialog(
    initial: TierDraft,
    app: AppState,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var draft by remember(initial) { mutableStateOf(initial) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Manga>>(emptyList()) }
    var characterName by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(query) {
        val q = libTrim(query)
        if (q.isEmpty() || draft.listType == "character") { results = emptyList(); return@LaunchedEffect }
        delay(250)
        val fields = listOf("id", "title", "cover_image_url", "cover_seed", "total_volumes", "publisher")
        val page = try {
            Base44.list<Manga>("Manga", query = mapOf("title" to libRegex(q)), limit = 10, fields = fields)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            null
        }
        results = page?.items ?: emptyList()
    }

    fun move(uid: String, tier: Int) { draft = draft.copy(entries = tierMove(draft.entries, uid, tier)) }
    fun shift(uid: String, delta: Int) { draft = draft.copy(entries = tierShift(draft.entries, uid, delta)) }
    fun remove(uid: String) { draft = draft.copy(entries = tierRemove(draft.entries, uid)) }

    fun addManga(m: Manga) {
        if (draft.entries.any { it.entry.ref_id == m.id }) {
            app.show("Already in this list")
            return
        }
        val e = TierEntry(kind = "manga", ref_id = m.id, label = m.title, cover_url = m.cover_image_url ?: "",
            tier = -1, order = draft.entries.size)
        draft = draft.copy(entries = draft.entries + TierItem(UUID.randomUUID().toString(), e))
        query = ""
        results = emptyList()
    }

    fun addCharacter() {
        val name = libTrim(characterName)
        if (name.isEmpty()) return
        val e = TierEntry(kind = "character", ref_id = null, label = name, cover_url = null,
            tier = -1, order = draft.entries.size)
        draft = draft.copy(entries = draft.entries + TierItem(UUID.randomUUID().toString(), e))
        characterName = ""
    }

    suspend fun save() {
        val name = libTrim(draft.name)
        saving = true
        val payload = mapOf<String, Any?>(
            "name" to (if (name.isEmpty()) "My Tier List" else name),
            "description" to draft.description,
            "list_type" to draft.listType,
            "tiers" to draft.tiers.map { mapOf("name" to it.name, "color" to it.color) },
            "entries" to draft.entries.map { it.entry.toMap() },
            "is_public" to draft.isPublic,
        )
        try {
            if (draft.recordId.isEmpty()) {
                Base44.create<TierList>("TierList", payload)
            } else {
                Base44.update("TierList", draft.recordId, payload)
            }
            app.show("Tier list saved")
            saving = false
            onSaved()
            onDismiss()
        } catch (e: Exception) {
            app.fail("Could not save tier list", e)
        }
        saving = false
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Vault.background).imePadding()) {
            // Toolbar
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss) { Text("Cancel", color = Vault.muted) }
                Text(
                    if (draft.recordId.isEmpty()) "New Tier List" else "Edit Tier List",
                    color = Vault.foreground, fontSize = 17.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
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
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                // Details
                Column(Modifier.fillMaxWidth().cardStyle().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    LibFieldLabel("Title")
                    LibTextField(draft.name, { draft = draft.copy(name = it) }, "My Tier List", Modifier.fillMaxWidth())
                    LibFieldLabel("Description")
                    LibTextField(
                        draft.description, { draft = draft.copy(description = it) }, "What is this list about?",
                        Modifier.fillMaxWidth(), singleLine = false, minLines = 2, maxLines = 4,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(if (draft.isPublic) Icons.Filled.Public else Icons.Filled.Lock, null, tint = Vault.foreground, modifier = Modifier.size(18.dp))
                        Text(if (draft.isPublic) "Public" else "Private", color = Vault.foreground, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        Switch(
                            checked = draft.isPublic,
                            onCheckedChange = { draft = draft.copy(isPublic = it) },
                            colors = SwitchDefaults.colors(checkedTrackColor = Vault.primary, checkedThumbColor = Color.White),
                        )
                    }
                }
                // Add entries
                SectionCard(title = "Add entries") {
                    if (draft.listType == "character") {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            LibTextField(characterName, { characterName = it }, "Character name", Modifier.weight(1f))
                            PrimaryButton("Add", { addCharacter() }, icon = Icons.Filled.Add)
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            LibTextField(query, { query = it }, "Search manga to add...", Modifier.fillMaxWidth())
                            for (m in results) {
                                Row(
                                    Modifier.fillMaxWidth()
                                        .background(Vault.secondary.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                        .clickable { addManga(m) }
                                        .padding(6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    CoverView(CoverInfo.of(m), Modifier.width(30.dp), cornerRadius = 4.dp)
                                    Text(
                                        m.title, color = Vault.foreground, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                                    )
                                    Icon(Icons.Filled.AddCircle, "Add", tint = Vault.primary)
                                }
                            }
                        }
                    }
                }
                // Tiers
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    draft.tiers.forEachIndexed { index, def ->
                        Row(
                            Modifier.fillMaxWidth().cardStyle().padding(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(
                                Modifier.width(46.dp).heightIn(min = 120.dp).background(hexColor(def.color), RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(def.name, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
                            }
                            TierStrip(
                                list = tierItemsIn(draft.entries, index),
                                emptyText = "Drop entries here",
                                tiers = draft.tiers,
                                onMove = { u, t -> move(u, t) }, onShift = { u, d -> shift(u, d) }, onRemove = { u -> remove(u) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    // Unranked pool
                    Column(
                        Modifier.fillMaxWidth().cardStyle().padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Unranked pool — drag into a tier above", color = Vault.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        TierStrip(
                            list = tierItemsIn(draft.entries, -1),
                            emptyText = if (draft.listType == "character") "Add characters above" else "Search and add manga above",
                            tiers = draft.tiers,
                            onMove = { u, t -> move(u, t) }, onShift = { u, d -> shift(u, d) }, onRemove = { u -> remove(u) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TierStrip(
    list: List<TierItem>,
    emptyText: String,
    tiers: List<TierDef>,
    onMove: (String, Int) -> Unit,
    onShift: (String, Int) -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (list.isEmpty()) {
        Box(modifier.heightIn(min = 100.dp), contentAlignment = Alignment.Center) {
            Text(emptyText, color = Vault.muted.copy(alpha = 0.7f), fontSize = 12.sp, textAlign = TextAlign.Center)
        }
    } else {
        Row(
            modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (item in list) {
                androidx.compose.runtime.key(item.uid) {
                    TierTile(item, tiers, onMove, onShift, onRemove)
                }
            }
        }
    }
}

@Composable
private fun TierTile(
    item: TierItem,
    tiers: List<TierDef>,
    onMove: (String, Int) -> Unit,
    onShift: (String, Int) -> Unit,
    onRemove: (String) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val e = item.entry
    Column(Modifier.width(64.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(64.dp, 92.dp).clickable { menu = true }) {
            val cover = nonEmpty(e.cover_url)
            if (e.kind == "manga" && cover != null) {
                RemoteImage(cover, Modifier.fillMaxSize().clip(RoundedCornerShape(6.dp)))
            } else {
                val shape = RoundedCornerShape(6.dp)
                Box(
                    Modifier.fillMaxSize().background(Vault.secondary, shape).border(1.dp, Vault.border, shape).padding(4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        e.label, color = Vault.foreground, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center, maxLines = 4, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Box(
                Modifier.align(Alignment.TopEnd).padding(3.dp).size(20.dp)
                    .background(Color.Black.copy(alpha = 0.75f), CircleShape)
                    .clickable { onRemove(item.uid) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Close, "Remove", tint = Color.White, modifier = Modifier.size(11.dp))
            }
        }
        Box {
            Row(
                Modifier.width(64.dp).clickable { menu = true },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    e.label, color = Vault.foreground, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                )
                Icon(Icons.Filled.KeyboardArrowDown, null, tint = Vault.foreground, modifier = Modifier.size(12.dp))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                tiers.forEachIndexed { i, def ->
                    DropdownMenuItem(text = { Text("Move to ${def.name}") }, onClick = { menu = false; onMove(item.uid, i) })
                }
                DropdownMenuItem(text = { Text("Move to unranked pool") }, onClick = { menu = false; onMove(item.uid, -1) })
                HorizontalDivider(color = Vault.border)
                DropdownMenuItem(
                    text = { Text("Move left") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) },
                    onClick = { menu = false; onShift(item.uid, -1) },
                )
                DropdownMenuItem(
                    text = { Text("Move right") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.ArrowForward, null) },
                    onClick = { menu = false; onShift(item.uid, 1) },
                )
                DropdownMenuItem(
                    text = { Text("Remove", color = Vault.red) },
                    leadingIcon = { Icon(Icons.Filled.Delete, null, tint = Vault.red) },
                    onClick = { menu = false; onRemove(item.uid) },
                )
            }
        }
    }
}
