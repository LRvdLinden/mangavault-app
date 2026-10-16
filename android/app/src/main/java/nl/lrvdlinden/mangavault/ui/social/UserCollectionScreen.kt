package nl.lrvdlinden.mangavault.ui.social

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import nl.lrvdlinden.mangavault.app.LocalApp
import nl.lrvdlinden.mangavault.app.Route
import nl.lrvdlinden.mangavault.core.Social
import nl.lrvdlinden.mangavault.model.CollectionItem
import nl.lrvdlinden.mangavault.model.ReadStatus
import nl.lrvdlinden.mangavault.model.SocialCollectionResponse
import nl.lrvdlinden.mangavault.ui.components.Badge
import nl.lrvdlinden.mangavault.ui.components.CoverInfo
import nl.lrvdlinden.mangavault.ui.components.CoverView
import nl.lrvdlinden.mangavault.ui.components.EmptyState
import nl.lrvdlinden.mangavault.ui.components.LoadingView
import nl.lrvdlinden.mangavault.ui.components.PrimaryButton
import nl.lrvdlinden.mangavault.ui.components.cardStyle
import nl.lrvdlinden.mangavault.ui.theme.Vault

private fun socSortLabel(sort: String): String = when (sort) {
    "title" -> "Title A-Z"
    "volume" -> "Volume number"
    else -> "Recently added"
}

private fun socFilterItems(items: List<CollectionItem>, search: String, status: ReadStatus?, sort: String): List<CollectionItem> {
    val q = search.trim().lowercase()
    var r = items.filter { q.isEmpty() || it.titleText.lowercase().contains(q) }
    if (status != null) r = r.filter { it.effectiveStatus == status }
    r = when (sort) {
        "title" -> r.sortedWith(Comparator<CollectionItem> { a, b -> String.CASE_INSENSITIVE_ORDER.compare(a.titleText, b.titleText) })
        "volume" -> r.sortedWith(Comparator<CollectionItem> { a, b ->
            val va = a.volume_number ?: 0.0
            val vb = b.volume_number ?: 0.0
            if (va != vb) va.compareTo(vb) else String.CASE_INSENSITIVE_ORDER.compare(a.titleText, b.titleText)
        })
        else -> r.sortedByDescending { it.created_date ?: "" }
    }
    return r
}

private fun socItemRoute(i: CollectionItem): Route {
    val v = i.volume
    if (v != null && !i.isOmnibus) return Route.Volume(i.manga_id ?: "", v)
    return Route.Manga(i.manga_id ?: "")
}

@Composable
fun UserCollectionScreen(profileId: String) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()

    var data by remember { mutableStateOf<SocialCollectionResponse?>(null) }
    var loading by remember { mutableStateOf(true) }
    var search by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf<ReadStatus?>(null) }
    var sort by remember { mutableStateOf("recent") }
    var sending by remember { mutableStateOf(false) }
    var requestSent by remember { mutableStateOf(false) }

    LaunchedEffect(profileId) {
        try {
            data = Social.collection(profileId)
        } catch (e: Exception) {
            app.fail("Could not load collection", e)
        }
        loading = false
    }

    fun sendRequest() {
        val uid = data?.owner?.user_id ?: return
        sending = true
        scope.launch {
            try {
                Social.friends("send", mapOf("target_user_id" to uid))
                requestSent = true
                app.show("Friend request sent")
                app.refreshSocialCounts()
            } catch (e: Exception) {
                app.fail("Action failed", e)
            }
            sending = false
        }
    }

    val d = data
    val ownerName = d?.owner?.name ?: "Reader"
    val items = d?.items ?: emptyList()

    Box(Modifier.fillMaxSize().background(Vault.background)) {
        when {
            loading -> LoadingView()
            d == null -> EmptyState(Icons.AutoMirrored.Filled.LibraryBooks, "Collection not found.")
            d.authorized == false -> {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                    SocLockedCard(
                        d, ownerName, requestSent, sending,
                        onSend = { sendRequest() },
                        onBack = { app.back() },
                    )
                }
            }
            else -> {
                val filtered = socFilterItems(items, search, statusFilter, sort)
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(110.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SocCollectionHeader(d, ownerName, items.size)
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            SocSearchField(search, { search = it }, "Search titles...")
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                SocMenu(
                                    label = statusFilter?.label ?: "All statuses",
                                    icon = Icons.Filled.FilterList,
                                    options = listOf<Pair<ReadStatus?, String>>(null to "All statuses") +
                                        ReadStatus.volumeCases.map { it to it.label },
                                    selected = statusFilter,
                                    onSelect = { statusFilter = it },
                                    modifier = Modifier.weight(1f),
                                )
                                SocMenu(
                                    label = socSortLabel(sort),
                                    icon = Icons.AutoMirrored.Filled.Sort,
                                    options = listOf("recent" to "Recently added", "title" to "Title A-Z", "volume" to "Volume number"),
                                    selected = sort,
                                    onSelect = { sort = it },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                    if (items.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            EmptyState(Icons.AutoMirrored.Filled.LibraryBooks, "This collection is empty for now.")
                        }
                    } else if (filtered.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            EmptyState(Icons.Filled.Search, "No volumes match your filters.")
                        }
                    } else {
                        items(filtered) { item ->
                            SocCollectionCard(item) { app.open(socItemRoute(item)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SocLockedCard(
    d: SocialCollectionResponse,
    ownerName: String,
    requestSent: Boolean,
    sending: Boolean,
    onSend: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().cardStyle().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            Modifier.size(76.dp).clip(CircleShape).background(Vault.primary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Lock, null, tint = Vault.primary, modifier = Modifier.size(34.dp))
        }
        Text("Private Collection", color = Vault.foreground, fontSize = 22.sp, fontWeight = FontWeight.Black)
        Text(
            "$ownerName's collection is private. You need to be friends to view it.",
            color = Vault.muted, fontSize = 14.sp, textAlign = TextAlign.Center,
        )
        if (d.friend == true || requestSent) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Filled.Schedule, null, tint = Vault.muted, modifier = Modifier.size(16.dp))
                Text("Your friend request is pending.", color = Vault.muted, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        } else {
            PrimaryButton(
                "Send Friend Request", onSend,
                modifier = Modifier.fillMaxWidth(),
                enabled = d.owner?.user_id != null,
                loading = sending,
                icon = Icons.Filled.PersonAdd,
            )
        }
        TextButton(onClick = onBack) {
            Text("Back to profile", color = Vault.primary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SocCollectionHeader(d: SocialCollectionResponse, ownerName: String, count: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        SocAvatar(d.owner?.avatar_url, ownerName, 52.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("$ownerName's Collection", color = Vault.foreground, fontSize = 20.sp, fontWeight = FontWeight.Black,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("$count volume${if (count == 1) "" else "s"}", color = Vault.muted, fontSize = 14.sp)
            if (d.friend == true && d.collection_public != true) {
                Badge("PRIVATE COLLECTION — VISIBLE TO FRIENDS", icon = Icons.Filled.Lock, fg = Vault.primary,
                    bg = Vault.primary.copy(alpha = 0.15f), ring = Vault.primary.copy(alpha = 0.4f))
            } else if (d.collection_public == true) {
                Badge("PUBLIC", icon = Icons.Filled.Public, fg = Vault.emerald,
                    bg = Vault.emerald.copy(alpha = 0.18f), ring = Vault.emerald.copy(alpha = 0.4f))
            }
        }
    }
}

@Composable
private fun <T> SocMenu(
    label: String,
    icon: ImageVector,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    Box(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(Vault.card, shape)
                .border(1.dp, Vault.border, shape)
                .clickable { open = true }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(icon, null, tint = Vault.foreground, modifier = Modifier.size(16.dp))
            Text(label, color = Vault.foreground, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.ExpandMore, null, tint = Vault.foreground, modifier = Modifier.size(14.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for ((value, title) in options) {
                DropdownMenuItem(
                    text = { Text(title) },
                    onClick = { onSelect(value); open = false },
                    trailingIcon = if (value == selected) {
                        { Icon(Icons.Filled.Check, null, tint = Vault.primary) }
                    } else null,
                )
            }
        }
    }
}

@Composable
private fun SocCollectionCard(item: CollectionItem, onClick: () -> Unit) {
    val st = ReadStatus.of(item.status)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .shadow(6.dp, RoundedCornerShape(10.dp))
                .clickable(enabled = item.manga_id != null, onClick = onClick)
        ) {
            CoverView(CoverInfo.of(item), Modifier.fillMaxWidth())
            if (st != null && st != ReadStatus.PLAN_TO_READ) {
                Badge(st.label.uppercase(), Modifier.align(Alignment.TopEnd).padding(6.dp), bg = Vault.statusColor(st))
            }
            if (item.read == true) {
                Badge("READ", Modifier.align(Alignment.TopStart).padding(6.dp), icon = Icons.Filled.Check, bg = Vault.emerald)
            }
        }
        Text(item.titleText, color = Vault.foreground, fontSize = 12.sp, fontWeight = FontWeight.Bold,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(item.volumeLabel, color = Vault.muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
