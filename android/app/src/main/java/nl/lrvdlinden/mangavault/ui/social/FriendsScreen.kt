package nl.lrvdlinden.mangavault.ui.social

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import nl.lrvdlinden.mangavault.app.LocalApp
import nl.lrvdlinden.mangavault.app.Route
import nl.lrvdlinden.mangavault.core.Social
import nl.lrvdlinden.mangavault.model.FriendsOverview
import nl.lrvdlinden.mangavault.model.SocialUser
import nl.lrvdlinden.mangavault.ui.components.Badge
import nl.lrvdlinden.mangavault.ui.components.EmptyState
import nl.lrvdlinden.mangavault.ui.components.LoadingView
import nl.lrvdlinden.mangavault.ui.components.ScreenHeader
import nl.lrvdlinden.mangavault.ui.theme.Vault
import kotlin.coroutines.cancellation.CancellationException

private enum class SocFriendsTab { FRIENDS, REQUESTS, SENT, FIND }

@Composable
fun FriendsScreen() {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()

    var tab by remember { mutableStateOf(SocFriendsTab.FRIENDS) }
    var overview by remember { mutableStateOf(FriendsOverview()) }
    var loading by remember { mutableStateOf(true) }
    var busyId by remember { mutableStateOf<String?>(null) }
    var removing by remember { mutableStateOf<SocialUser?>(null) }

    // Find
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<SocialUser>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var sentIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    suspend fun loadOverview() {
        try {
            overview = Social.overview()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (loading) app.fail("Could not load friends", e)
        }
        loading = false
    }

    suspend fun runSearch() {
        val q = query.trim()
        if (q.isEmpty()) { results = emptyList(); searching = false; return }
        searching = true
        try {
            val r = Social.search(q)
            // Ignore stale responses when the query changed meanwhile.
            if (q == query.trim()) results = r
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            results = emptyList()
        }
        searching = false
    }

    suspend fun act(rowId: String, action: String, params: Map<String, Any?>, toast: String?): Boolean {
        busyId = rowId
        var ok = false
        try {
            Social.friends(action, params)
            if (toast != null) app.show(toast)
            ok = true
        } catch (e: Exception) {
            app.fail("Action failed", e)
        }
        busyId = null
        loadOverview()
        app.refreshSocialCounts()
        return ok
    }

    LaunchedEffect(Unit) {
        while (true) {
            loadOverview()
            delay(20_000)
        }
    }

    LaunchedEffect(query) {
        delay(300)
        runSearch()
    }

    fun label(t: SocFriendsTab): String = when (t) {
        SocFriendsTab.FRIENDS -> "Friends (${overview.friends.size})"
        SocFriendsTab.REQUESTS -> "Requests (${overview.incoming.size})"
        SocFriendsTab.SENT -> "Sent (${overview.outgoing.size})"
        SocFriendsTab.FIND -> "Find"
    }

    LazyColumn(
        Modifier.fillMaxSize().background(Vault.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            ScreenHeader(lead = "MY", accent = "FRIENDS", subtitle = "Find readers, manage requests and chat.")
        }
        item {
            SocSegmented(SocFriendsTab.entries.map { it to label(it) }, tab) { tab = it }
        }

        when (tab) {
            SocFriendsTab.FRIENDS -> {
                if (loading && overview.friends.isEmpty()) {
                    item { LoadingView() }
                } else if (overview.friends.isEmpty()) {
                    item { EmptyState(Icons.Filled.People, "No friends yet. Use Find to search for users.") }
                } else {
                    items(overview.friends) { user ->
                        SocUserRow(user) {
                            val uid = user.user_id
                            if (uid != null) {
                                SocIconSquare(Icons.AutoMirrored.Filled.Chat, Color.White, Vault.primary, bordered = false) {
                                    app.open(Route.Chat(uid))
                                }
                            }
                            SocIconSquare(Icons.Filled.PersonRemove, Vault.red, Vault.card, bordered = true, enabled = busyId != user.id) {
                                removing = user
                            }
                        }
                    }
                }
            }
            SocFriendsTab.REQUESTS -> {
                if (loading && overview.incoming.isEmpty()) {
                    item { LoadingView() }
                } else if (overview.incoming.isEmpty()) {
                    item { EmptyState(Icons.Filled.Inbox, "No pending requests.") }
                } else {
                    items(overview.incoming) { user ->
                        SocUserRow(user) {
                            SocSmallButton("Accept", Icons.Filled.Check, filled = true, enabled = busyId != user.id) {
                                val rid = user.request_id
                                if (rid != null) scope.launch {
                                    act(user.id, "accept", mapOf("request_id" to rid), "Friend request accepted")
                                }
                            }
                            SocSmallButton("Decline", color = Vault.muted, enabled = busyId != user.id) {
                                val rid = user.request_id
                                if (rid != null) scope.launch {
                                    act(user.id, "decline", mapOf("request_id" to rid), "Request declined")
                                }
                            }
                        }
                    }
                }
            }
            SocFriendsTab.SENT -> {
                if (loading && overview.outgoing.isEmpty()) {
                    item { LoadingView() }
                } else if (overview.outgoing.isEmpty()) {
                    item { EmptyState(Icons.AutoMirrored.Filled.Send, "No sent requests.") }
                } else {
                    items(overview.outgoing) { user ->
                        SocUserRow(user) {
                            SocSmallButton("Cancel", Icons.Filled.Close, color = Vault.muted, enabled = busyId != user.id) {
                                val rid = user.request_id
                                if (rid != null) scope.launch {
                                    act(user.id, "cancel", mapOf("request_id" to rid), "Request cancelled")
                                }
                            }
                        }
                    }
                }
            }
            SocFriendsTab.FIND -> {
                item {
                    SocSearchField(query, { query = it }, "Search by username...", busy = searching)
                }
                if (query.trim().isEmpty()) {
                    item { EmptyState(Icons.Filled.PersonSearch, "Search for readers by their username.") }
                } else if (results.isEmpty() && !searching) {
                    item { EmptyState(Icons.Filled.PersonOff, "No users found.") }
                } else {
                    items(results) { user ->
                        SocUserRow(user) {
                            val uid = user.user_id
                            if (user.friend == true) {
                                Badge("FRIENDS", icon = Icons.Filled.Check, fg = Vault.emerald,
                                    bg = Vault.emerald.copy(alpha = 0.18f), ring = Vault.emerald.copy(alpha = 0.4f))
                            } else if (uid != null && sentIds.contains(uid)) {
                                Badge("REQUESTED", icon = Icons.Filled.Schedule, fg = Vault.muted, bg = Vault.card, ring = Vault.border)
                            } else {
                                SocSmallButton("Add", Icons.Filled.PersonAdd, filled = true,
                                    enabled = busyId != user.id && uid != null) {
                                    if (uid != null) scope.launch {
                                        val ok = act(user.id, "send", mapOf("target_user_id" to uid), "Friend request sent")
                                        if (ok) sentIds = sentIds + uid
                                        runSearch()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val pending = removing
    if (pending != null) {
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("Remove friend?") },
            text = { Text("${pending.name} will no longer be your friend and will lose access to your private collection.") },
            confirmButton = {
                TextButton(onClick = {
                    removing = null
                    val uid = pending.user_id
                    if (uid != null) scope.launch {
                        act(pending.id, "remove", mapOf("target_user_id" to uid), "Friend removed")
                    }
                }) { Text("Remove", color = Vault.red, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { removing = null }) { Text("Cancel", color = Vault.foreground) }
            },
            containerColor = Vault.card,
            titleContentColor = Vault.foreground,
            textContentColor = Vault.muted,
        )
    }
}

/** Segmented control (port of `.pickerStyle(.segmented)`). */
@Composable
private fun <T> SocSegmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(Vault.card, shape).border(1.dp, Vault.border, shape).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        for ((value, title) in options) {
            val isOn = value == selected
            val inner = RoundedCornerShape(8.dp)
            Box(
                Modifier
                    .weight(1f)
                    .clip(inner)
                    .background(if (isOn) Vault.primary else Color.Transparent, inner)
                    .clickable { onSelect(value) }
                    .padding(vertical = 8.dp, horizontal = 2.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    title,
                    color = if (isOn) Color.White else Vault.muted,
                    fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
