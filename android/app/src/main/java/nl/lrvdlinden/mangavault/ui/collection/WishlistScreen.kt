package nl.lrvdlinden.mangavault.ui.collection

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EuroSymbol
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import nl.lrvdlinden.mangavault.app.AppState
import nl.lrvdlinden.mangavault.app.LocalApp
import nl.lrvdlinden.mangavault.app.Route
import nl.lrvdlinden.mangavault.core.Base44
import nl.lrvdlinden.mangavault.core.VaultService
import nl.lrvdlinden.mangavault.model.CollectionItem
import nl.lrvdlinden.mangavault.model.WishlistItem
import nl.lrvdlinden.mangavault.model.euro
import nl.lrvdlinden.mangavault.ui.components.Badge
import nl.lrvdlinden.mangavault.ui.components.CoverInfo
import nl.lrvdlinden.mangavault.ui.components.CoverView
import nl.lrvdlinden.mangavault.ui.components.EmptyState
import nl.lrvdlinden.mangavault.ui.components.LoadingView
import nl.lrvdlinden.mangavault.ui.components.ScreenHeader
import nl.lrvdlinden.mangavault.ui.components.StatCard
import nl.lrvdlinden.mangavault.ui.theme.Vault
import kotlin.coroutines.cancellation.CancellationException

private class WishModel {
    var items by mutableStateOf<List<WishlistItem>>(emptyList())
    var loading by mutableStateOf(true)
    var moving by mutableStateOf<String?>(null)

    suspend fun load(app: AppState) {
        loading = true
        try {
            items = VaultService.wishlist()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            app.fail("Could not load wishlist", e)
        }
        loading = false
    }

    /** "Bought" — creates the collection item and removes the wish (same as the web app). */
    suspend fun bought(app: AppState, w: WishlistItem) {
        moving = w.id
        try {
            Base44.create<CollectionItem>("CollectionItem", mapOf(
                "manga_id" to (w.manga_id ?: ""),
                "title" to (w.title ?: ""),
                "volume_number" to w.volume,
                "volume_cover_url" to (w.volume_cover_url ?: ""),
                "cover_image_url" to (w.cover_image_url ?: ""),
                "cover_seed" to (w.cover_seed ?: (w.title ?: "")),
                "publisher" to (w.publisher ?: ""),
                "price" to (w.price ?: 0.0),
                "is_shonen_jump" to (w.is_shonen_jump ?: false),
                "edition" to (w.edition ?: "standard"),
                "volumes_included" to (w.volumes_included ?: ""),
                "omnibus_of" to (w.omnibus_of ?: ""),
                "read" to false,
            ))
            Base44.delete("WishlistItem", w.id)
            items = items.filter { it.id != w.id }
            app.show("Moved \"${w.title ?: ""}\" to your collection")
            app.dataChanged()
        } catch (e: Exception) {
            app.fail("Could not move", e)
        } finally {
            moving = null
        }
    }

    suspend fun remove(app: AppState, w: WishlistItem) {
        try {
            Base44.delete("WishlistItem", w.id)
            items = items.filter { it.id != w.id }
            app.show("Removed \"${w.title ?: ""}\" from wishlist")
        } catch (e: Exception) {
            app.fail("Could not remove", e)
        }
    }
}

private fun wishRoute(w: WishlistItem): Route {
    val v = w.volume
    return if (w.isOmnibus || v == null) Route.Manga(w.manga_id ?: "") else Route.Volume(w.manga_id ?: "", v)
}

@Composable
fun WishlistScreen() {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    val model = remember { WishModel() }

    LaunchedEffect(app.dataVersion) { model.load(app) }

    val items = model.items
    Column(
        Modifier.fillMaxSize().background(Vault.background).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        ScreenHeader("MY", "WISHLIST", accentColor = Vault.red, subtitle = "Manga volumes you want to buy next.")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard(Icons.Filled.Favorite, "Items", "${items.size}", Modifier.weight(1f), accent = Vault.red)
            StatCard(Icons.Filled.EuroSymbol, "Total Value", items.sumOf { it.price ?: 0.0 }.euro(), Modifier.weight(1f), accent = Vault.red)
        }
        if (model.loading && items.isEmpty()) {
            LoadingView()
        } else if (items.isEmpty()) {
            EmptyState(Icons.Filled.FavoriteBorder, "Your wishlist is empty.\nTap the heart on any volume in the Catalog to save it here.")
        } else {
            for (row in items.chunked(2)) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    for (w in row) {
                        Box(Modifier.weight(1f)) {
                            WishCard(
                                w,
                                moving = model.moving,
                                onOpen = { app.open(wishRoute(w)) },
                                onBought = { scope.launch { model.bought(app, w) } },
                                onRemove = { scope.launch { model.remove(app, w) } },
                            )
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WishCard(w: WishlistItem, moving: String?, onOpen: () -> Unit, onBought: () -> Unit, onRemove: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box {
            CoverView(CoverInfo.of(w), Modifier.fillMaxWidth().combinedClickable(onClick = onOpen, onLongClick = { menu = true }))
            Badge(
                if (w.isOmnibus) "3-IN-1" else "VOL. ${w.volume ?: 0}",
                Modifier.align(Alignment.TopEnd).padding(8.dp),
                icon = if (w.isOmnibus) Icons.AutoMirrored.Filled.LibraryBooks else null,
                fg = Vault.red, bg = Vault.background.copy(alpha = 0.85f), ring = Vault.red.copy(alpha = 0.5f),
            )
            Badge("WISH", Modifier.align(Alignment.TopStart).padding(8.dp), icon = Icons.Filled.Favorite, bg = Vault.red)
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Move to collection") }, onClick = { menu = false; onBought() },
                    leadingIcon = { Icon(Icons.Filled.ShoppingCart, null) })
                DropdownMenuItem(text = { Text("Remove", color = Vault.red) }, onClick = { menu = false; onRemove() },
                    leadingIcon = { Icon(Icons.Filled.Delete, null, tint = Vault.red) })
            }
        }
        Text(w.title ?: "", color = Vault.foreground, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(w.volumeLabel, color = Vault.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text((w.price ?: 0.0).euro(), color = Vault.red, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = onBought,
                enabled = moving == null,
                modifier = Modifier.weight(1f).heightIn(min = 40.dp),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Vault.red, contentColor = Color.White,
                    disabledContainerColor = Vault.red.copy(alpha = 0.4f), disabledContentColor = Color.White.copy(alpha = 0.7f)),
            ) {
                if (moving == w.id) CircularProgressIndicator(Modifier.size(14.dp), color = Color.White, strokeWidth = 2.dp)
                else Icon(Icons.Filled.ShoppingCart, null, Modifier.size(15.dp))
                Spacer(Modifier.width(4.dp))
                Text("Bought", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            OutlinedButton(
                onClick = onRemove,
                modifier = Modifier.heightIn(min = 40.dp),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                border = BorderStroke(1.dp, Vault.border),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = Vault.card.copy(alpha = 0.4f), contentColor = Vault.muted),
            ) {
                Icon(Icons.Filled.Delete, "Remove", Modifier.size(16.dp))
            }
        }
    }
}
