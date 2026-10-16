package nl.lrvdlinden.mangavault.ui.social

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import nl.lrvdlinden.mangavault.app.LocalApp
import nl.lrvdlinden.mangavault.app.Route
import nl.lrvdlinden.mangavault.core.Social
import nl.lrvdlinden.mangavault.model.Conversation
import nl.lrvdlinden.mangavault.model.ServerDate
import nl.lrvdlinden.mangavault.model.nonEmpty
import nl.lrvdlinden.mangavault.ui.components.EmptyState
import nl.lrvdlinden.mangavault.ui.components.LoadingView
import nl.lrvdlinden.mangavault.ui.components.ScreenHeader
import nl.lrvdlinden.mangavault.ui.components.cardStyle
import nl.lrvdlinden.mangavault.ui.theme.Vault
import kotlin.coroutines.cancellation.CancellationException

@Composable
fun MessagesScreen() {
    val app = LocalApp.current
    var conversations by remember { mutableStateOf<List<Conversation>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        while (true) {
            try {
                conversations = Social.conversations()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (loading) app.fail("Could not load messages", e)
            }
            loading = false
            delay(15_000)
        }
    }

    LazyColumn(
        Modifier.fillMaxSize().background(Vault.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            ScreenHeader(lead = "MY", accent = "MESSAGES", subtitle = "Chat with your manga friends.",
                modifier = Modifier.padding(bottom = 8.dp))
        }
        if (loading && conversations.isEmpty()) {
            item { LoadingView() }
        } else if (conversations.isEmpty()) {
            item {
                EmptyState(Icons.Filled.Forum,
                    "No conversations yet. Accept a friend request or message a friend from their profile.")
            }
        } else {
            items(conversations) { c ->
                val uid = c.other_user_id
                SocConversationRow(c, if (uid != null) ({ app.open(Route.Chat(uid)) }) else null)
            }
        }
    }
}

@Composable
private fun SocConversationRow(c: Conversation, onClick: (() -> Unit)?) {
    val name = nonEmpty(c.other?.display_name) ?: "Reader"
    val unread = c.unread
    Row(
        Modifier
            .fillMaxWidth()
            .cardStyle(highlighted = unread > 0)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box {
            SocAvatar(c.other?.avatar_url, name, 48.dp)
            if (unread > 0) {
                SocCountPill(unread, Vault.primary, Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-4).dp), ring = Vault.background)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, color = Vault.foreground, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(ServerDate.relative(c.last_message_date), color = Vault.muted, fontSize = 11.sp)
            }
            Text(
                nonEmpty(c.last_message) ?: "No messages yet",
                color = if (unread > 0) Vault.foreground else Vault.muted,
                fontSize = 12.sp,
                fontWeight = if (unread > 0) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Vault.muted, modifier = Modifier.size(18.dp))
    }
}
