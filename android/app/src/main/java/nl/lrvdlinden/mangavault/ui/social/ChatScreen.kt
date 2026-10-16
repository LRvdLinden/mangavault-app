package nl.lrvdlinden.mangavault.ui.social

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.EmojiPeople
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import nl.lrvdlinden.mangavault.app.LocalApp
import nl.lrvdlinden.mangavault.app.Route
import nl.lrvdlinden.mangavault.core.Social
import nl.lrvdlinden.mangavault.model.ChatMessage
import nl.lrvdlinden.mangavault.model.Conversation
import nl.lrvdlinden.mangavault.model.ServerDate
import nl.lrvdlinden.mangavault.ui.components.EmptyState
import nl.lrvdlinden.mangavault.ui.components.LoadingView
import nl.lrvdlinden.mangavault.ui.theme.Vault
import kotlin.coroutines.cancellation.CancellationException

/**
 * Chat with another reader (port of ChatLauncherView + SVChatScreen). Finds the existing
 * conversation; if there is none yet the composer still works — the first message creates
 * the conversation on the server.
 */
@Composable
fun ChatScreen(userId: String) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var conversation by remember { mutableStateOf<Conversation?>(null) }
    var messages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var markedOnce by remember { mutableStateOf(false) }

    val myId = app.user?.id
    val trimmed = text.trim()

    suspend fun refresh() {
        try {
            if (conversation == null) {
                val all = Social.conversations()
                conversation = all.firstOrNull { it.other_user_id == userId }
            }
            val c = conversation
            if (c == null) {
                loaded = true
                return
            }
            val fetched = Social.messages(c.id)
            val sorted = fetched.sortedBy { it.created_date ?: "" }
            if (sorted != messages) messages = sorted
            loaded = true
            val me = app.user?.id
            val hasUnread = sorted.any { it.read != true && it.sender_id != me }
            if (hasUnread || !markedOnce) {
                markedOnce = true
                Social.markRead(c.id)
                app.refreshSocialCounts()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            loaded = true
        }
    }

    fun send() {
        val content = trimmed.take(1000)
        if (content.isEmpty() || sending) return
        sending = true
        scope.launch {
            try {
                Social.send(userId, content)
                text = ""
                sending = false
                refresh()
            } catch (e: Exception) {
                sending = false
                app.fail("Failed to send", e)
            }
        }
    }

    // Poll like iOS (every few seconds).
    LaunchedEffect(userId) {
        while (true) {
            refresh()
            delay(5_000)
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Column(Modifier.fillMaxSize().background(Vault.background).imePadding()) {
        // Header
        val other = conversation?.other
        if (other != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Vault.card)
                    .clickable { app.open(Route.PublicProfile(other.id)) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SocAvatar(other.avatar_url, other.name, 36.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(other.name, color = Vault.foreground, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text("View profile", color = Vault.muted, fontSize = 11.sp)
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Vault.muted, modifier = Modifier.size(18.dp))
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Vault.border))
        }

        // Messages
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            state = listState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!loaded) {
                item { LoadingView() }
            } else if (messages.isEmpty()) {
                item { EmptyState(Icons.Filled.EmojiPeople, "Say hello!") }
            } else {
                items(messages) { m ->
                    SocBubble(m, mine = m.sender_id != null && m.sender_id == myId)
                }
            }
        }

        // Composer
        Box(Modifier.fillMaxWidth().height(1.dp).background(Vault.border))
        Row(
            Modifier.fillMaxWidth().background(Vault.background).padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { v -> text = if (v.length > 1000) v.take(1000) else v },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Type a message...", color = Vault.muted) },
                maxLines = 4,
                shape = RoundedCornerShape(18.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                colors = socFieldColors(),
            )
            val canSend = trimmed.isNotEmpty() && !sending
            Box(
                Modifier
                    .padding(bottom = 6.dp)
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (trimmed.isEmpty()) Vault.secondary else Vault.primary)
                    .clickable(enabled = canSend) { send() },
                contentAlignment = Alignment.Center,
            ) {
                if (sending) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.AutoMirrored.Filled.Send, "Send", tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun SocBubble(message: ChatMessage, mine: Boolean) {
    val shape = RoundedCornerShape(16.dp)
    Row(Modifier.fillMaxWidth()) {
        if (mine) Spacer(Modifier.weight(1f))
        Column(
            Modifier
                .padding(start = if (mine) 48.dp else 0.dp, end = if (mine) 0.dp else 48.dp)
                .widthIn(max = 320.dp)
                .background(if (mine) Vault.primary else Vault.secondary, shape)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(message.content ?: "", color = if (mine) Color.White else Vault.foreground, fontSize = 15.sp)
            Text(
                ServerDate.short(message.created_date),
                color = if (mine) Color.White.copy(alpha = 0.75f) else Vault.muted,
                fontSize = 9.sp,
            )
        }
        if (!mine) Spacer(Modifier.weight(1f))
    }
}
