package nl.lrvdlinden.mangavault.ui.social

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import nl.lrvdlinden.mangavault.app.LocalApp
import nl.lrvdlinden.mangavault.app.Route
import nl.lrvdlinden.mangavault.core.Social
import nl.lrvdlinden.mangavault.model.ServerDate
import nl.lrvdlinden.mangavault.model.VaultNotification
import nl.lrvdlinden.mangavault.model.nonEmpty
import nl.lrvdlinden.mangavault.ui.components.EmptyState
import nl.lrvdlinden.mangavault.ui.components.cardStyle
import nl.lrvdlinden.mangavault.ui.theme.Vault

/** Bell with an unread badge; opens a sheet listing `app.notifications`. */
@Composable
fun NotificationBell() {
    val app = LocalApp.current
    var showing by remember { mutableStateOf(false) }

    Box(contentAlignment = Alignment.Center) {
        IconButton(onClick = { showing = true }) {
            Icon(Icons.Filled.Notifications, "Notifications", tint = Vault.foreground)
        }
        if (app.unreadNotifications > 0) {
            SocCountPill(
                app.unreadNotifications, Vault.red,
                Modifier.align(Alignment.TopEnd).offset(x = (-4).dp, y = 6.dp),
            )
        }
    }

    if (showing) {
        SocNotificationsSheet(onDismiss = { showing = false })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SocNotificationsSheet(onDismiss: () -> Unit) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    var marking by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { app.refreshSocialCounts() }

    fun openNotification(n: VaultNotification) {
        val type = n.type ?: ""
        val related = nonEmpty(n.related_id)
        val route: Route? = when {
            type == "message" -> Route.Messages
            type == "friend_request" || type == "friend_accepted" -> Route.Friends
            type.contains("event") && related != null -> Route.Event(related)
            else -> null
        }
        scope.launch {
            sheetState.hide()
        }.invokeOnCompletion {
            onDismiss()
            if (route != null) app.openFromAnywhere(route)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Vault.background,
        contentColor = Vault.foreground,
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = {
                    scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
                }) { Text("Close", color = Vault.primary) }
                Text(
                    "Notifications",
                    color = Vault.foreground, fontSize = 17.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Box(Modifier.size(width = 110.dp, height = 40.dp), contentAlignment = Alignment.CenterEnd) {
                    if (app.unreadNotifications > 0) {
                        if (marking) {
                            CircularProgressIndicator(Modifier.size(18.dp), color = Vault.primary, strokeWidth = 2.dp)
                        } else {
                            TextButton(onClick = {
                                scope.launch {
                                    marking = true
                                    Social.markAllNotificationsRead()
                                    app.refreshSocialCounts()
                                    marking = false
                                }
                            }) { Text("Mark all read", color = Vault.primary, fontSize = 13.sp, maxLines = 1) }
                        }
                    }
                }
            }

            val list = app.notifications
            if (list.isEmpty()) {
                EmptyState(Icons.Filled.NotificationsNone, "No notifications yet.")
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(list) { n ->
                        SocNotificationRow(n) { openNotification(n) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SocNotificationRow(n: VaultNotification, onClick: () -> Unit) {
    val unread = n.read != true
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (unread) Vault.primary.copy(alpha = 0.06f) else Vault.background, shape)
            .cardStyle(highlighted = unread)
            .clickable(onClick = onClick)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(34.dp).clip(CircleShape).background(Vault.primary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(socNotificationIcon(n.type), null, tint = Vault.primary, modifier = Modifier.size(17.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(n.title ?: "Notification", color = Vault.foreground, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            val body = nonEmpty(n.body)
            if (body != null) {
                Text(body, color = Vault.muted, fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            Text(ServerDate.relative(n.created_date), color = Vault.muted.copy(alpha = 0.8f), fontSize = 11.sp)
        }
        if (unread) {
            Column {
                Spacer(Modifier.size(6.dp))
                Box(Modifier.size(8.dp).clip(CircleShape).background(Vault.primary))
            }
        }
    }
}

private fun socNotificationIcon(type: String?): ImageVector = when (type ?: "") {
    "message" -> Icons.AutoMirrored.Filled.Chat
    "friend_request" -> Icons.Filled.PersonAdd
    "friend_accepted" -> Icons.Filled.People
    else -> Icons.Filled.Notifications
}
