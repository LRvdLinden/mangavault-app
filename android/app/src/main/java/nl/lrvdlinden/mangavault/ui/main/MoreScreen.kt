package nl.lrvdlinden.mangavault.ui.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Note
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nl.lrvdlinden.mangavault.BuildConfig
import nl.lrvdlinden.mangavault.app.LocalApp
import nl.lrvdlinden.mangavault.app.Route
import nl.lrvdlinden.mangavault.core.Config
import nl.lrvdlinden.mangavault.core.openInBrowser
import nl.lrvdlinden.mangavault.ui.components.LogoMark
import nl.lrvdlinden.mangavault.ui.components.cardStyle
import nl.lrvdlinden.mangavault.ui.theme.Vault

private data class MoreItem(val title: String, val icon: ImageVector, val route: Route, val desc: String, val badge: Int = 0)

/** Mirrors the web "More" page: every feature in one place. */
@Composable
fun MoreScreen() {
    val app = LocalApp.current
    val context = LocalContext.current
    var confirmLogout by remember { mutableStateOf(false) }

    val sections = listOf(
        "Featured" to listOf(
            MoreItem("Rewards", Icons.Filled.CardGiftcard, Route.Rewards, "Daily reward, level rewards and reward history."),
            MoreItem("Vault Capsules", Icons.Filled.Inventory2, Route.Capsules, "Open free capsules earned by being active."),
            MoreItem("Trading Cards", Icons.Filled.Style, Route.Cards, "Collect and browse your trading cards."),
            MoreItem("Achievements", Icons.Filled.EmojiEvents, Route.Achievements, "Badges and milestones you have earned."),
            MoreItem("Daily Quests", Icons.Filled.TrackChanges, Route.Quests, "Daily and weekly reading challenges."),
            MoreItem("Statistics", Icons.Filled.BarChart, Route.Statistics, "Reading statistics and collection data."),
        ),
        "Social" to listOf(
            MoreItem("Friends", Icons.Filled.People, Route.Friends, "Find readers and manage requests.", app.pendingFriendRequests),
            MoreItem("Messages", Icons.Filled.Forum, Route.Messages, "Chat with your manga friends.", app.unreadMessages),
            MoreItem("Manga Room", Icons.Filled.Chair, Route.Room(null), "Decorate and share your own manga room."),
        ),
        "Library" to listOf(
            MoreItem("Wishlist", Icons.Filled.Favorite, Route.Wishlist, "Volumes you want to buy next."),
            MoreItem("Series Tracker", Icons.AutoMirrored.Filled.MenuBook, Route.Series, "Follow progress per series."),
            MoreItem("Reading Calendar", Icons.Filled.CalendarMonth, Route.Calendar, "Your reading sessions by day."),
            MoreItem("Manga Journal", Icons.AutoMirrored.Filled.Note, Route.Journal, "Reading notes and personal memories."),
            MoreItem("Tier Lists", Icons.Filled.FormatListNumbered, Route.TierLists, "Rank your favourite manga and characters."),
            MoreItem("Manga Wrapped", Icons.Filled.AutoAwesome, Route.Wrapped, "Your personal manga year in review."),
        ),
        "Discover" to listOf(
            MoreItem("Discover", Icons.Filled.Explore, Route.Discover, "Explore new series to start reading."),
            MoreItem("Recommendations", Icons.Filled.AutoFixHigh, Route.Recommendations, "Picks based on your collection."),
            MoreItem("Manga Roulette", Icons.Filled.Casino, Route.Roulette, "Spin the wheel for your next read."),
            MoreItem("Random Manga", Icons.Filled.Shuffle, Route.RandomManga, "Discover a random title from the catalog."),
            MoreItem("Manga Versus", Icons.AutoMirrored.Filled.CompareArrows, Route.Compare, "Compare manga side by side."),
        ),
    )

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(
            Modifier.fillMaxWidth().cardStyle().clickable { app.open(Route.Profile) }.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LogoMark(40.dp)
            Column(Modifier.weight(1f)) {
                Text(app.displayName, color = Vault.foreground, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(app.user?.email ?: "", color = Vault.muted, fontSize = 12.sp)
            }
            Icon(Icons.Filled.ChevronRight, null, tint = Vault.muted)
        }

        for ((header, items) in sections) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(header.uppercase(), color = Vault.muted, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                Column(Modifier.fillMaxWidth().cardStyle()) {
                    items.forEachIndexed { i, item ->
                        if (i > 0) HorizontalDivider(color = Vault.border)
                        Row(
                            Modifier.fillMaxWidth().clickable { app.open(item.route) }.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(item.icon, null, tint = Vault.primary, modifier = Modifier.size(22.dp))
                            Column(Modifier.weight(1f)) {
                                Text(item.title, color = Vault.foreground, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                Text(item.desc, color = Vault.muted, fontSize = 12.sp)
                            }
                            if (item.badge > 0) Badge(containerColor = Vault.red) { Text("${item.badge}") }
                            Icon(Icons.Filled.ChevronRight, null, tint = Vault.muted)
                        }
                    }
                }
            }
        }

        Column(Modifier.fillMaxWidth().cardStyle()) {
            Row(Modifier.fillMaxWidth().clickable { openInBrowser(context, Config.APP_BASE_URL) }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Filled.Public, null, tint = Vault.primary)
                Text("Open web version", color = Vault.foreground, fontSize = 15.sp)
            }
            HorizontalDivider(color = Vault.border)
            Row(Modifier.fillMaxWidth().clickable { confirmLogout = true }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.AutoMirrored.Filled.Logout, null, tint = Vault.red)
                Text("Log out", color = Vault.red, fontSize = 15.sp)
            }
        }
        Text("Manga Vault ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · data synced with mangas.base44.app",
            color = Vault.muted, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("Log out of Manga Vault?") },
            confirmButton = { TextButton(onClick = { confirmLogout = false; app.signOut() }) { Text("Log out", color = Vault.red) } },
            dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("Cancel") } },
            containerColor = Vault.card,
        )
    }
}
