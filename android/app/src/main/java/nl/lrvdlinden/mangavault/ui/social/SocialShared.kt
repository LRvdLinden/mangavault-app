package nl.lrvdlinden.mangavault.ui.social

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nl.lrvdlinden.mangavault.app.LocalApp
import nl.lrvdlinden.mangavault.app.Route
import nl.lrvdlinden.mangavault.model.SocialUser
import nl.lrvdlinden.mangavault.model.nonEmpty
import nl.lrvdlinden.mangavault.ui.components.RemoteImage
import nl.lrvdlinden.mangavault.ui.components.cardStyle
import nl.lrvdlinden.mangavault.ui.theme.Vault

// Shared helpers for the social screens (port of the fileprivate SV* views in SocialViews.swift).

/** Avatar: the image when an avatar URL exists, else the first letter of the name in a circle. */
@Composable
internal fun SocAvatar(url: String?, name: String, size: Dp = 44.dp, modifier: Modifier = Modifier) {
    val t = name.trim()
    val initial = if (t.isEmpty()) "?" else t.take(1).uppercase()
    Box(
        modifier.size(size).clip(CircleShape).border(1.dp, Vault.border, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        val u = nonEmpty(url)
        if (u != null) {
            RemoteImage(u, Modifier.fillMaxSize())
        } else {
            Box(Modifier.fillMaxSize().background(Vault.primary.copy(alpha = 0.2f)))
            Text(initial, color = Vault.primary, fontSize = (size.value * 0.42f).sp, fontWeight = FontWeight.Black)
        }
    }
}

/** Small rounded action button used in social rows. */
@Composable
internal fun SocSmallButton(
    title: String,
    icon: ImageVector? = null,
    filled: Boolean = false,
    color: Color = Vault.primary,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    val fg = if (filled) Color.White else color
    Row(
        Modifier
            .alpha(if (enabled) 1f else 0.5f)
            .clip(shape)
            .background(if (filled) color else Vault.card, shape)
            .border(1.dp, if (filled) Color.Transparent else Vault.border, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (icon != null) Icon(icon, null, tint = fg, modifier = Modifier.size(14.dp))
        Text(title, color = fg, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/** Square icon button (chat / remove friend). */
@Composable
internal fun SocIconSquare(
    icon: ImageVector,
    tint: Color,
    background: Color,
    bordered: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        Modifier
            .alpha(if (enabled) 1f else 0.5f)
            .size(34.dp)
            .clip(shape)
            .background(background, shape)
            .border(1.dp, if (bordered) Vault.border else Color.Transparent, shape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp))
    }
}

/** A user row: avatar, display name (links to the public profile), bio and trailing actions. */
@Composable
internal fun SocUserRow(user: SocialUser, actions: @Composable RowScope.() -> Unit) {
    val app = LocalApp.current
    Row(
        Modifier.fillMaxWidth().cardStyle().padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SocAvatar(user.avatar_url, user.name, 46.dp, Modifier.clickable { app.open(Route.PublicProfile(user.id)) })
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                user.name,
                color = Vault.foreground, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable { app.open(Route.PublicProfile(user.id)) },
            )
            val bio = nonEmpty(user.bio)
            if (bio != null) Text(bio, color = Vault.muted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(2.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) { actions() }
    }
}

/** Search field with a magnifier and an optional spinner. */
@Composable
internal fun SocSearchField(value: String, onValueChange: (String) -> Unit, placeholder: String, busy: Boolean = false) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        placeholder = { Text(placeholder, color = Vault.muted) },
        leadingIcon = { Icon(Icons.Filled.Search, null, tint = Vault.muted) },
        trailingIcon = if (busy) {
            { CircularProgressIndicator(Modifier.size(18.dp), color = Vault.primary, strokeWidth = 2.dp) }
        } else null,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
        shape = RoundedCornerShape(10.dp),
        colors = socFieldColors(),
    )
}

@Composable
internal fun socFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Vault.foreground,
    unfocusedTextColor = Vault.foreground,
    focusedContainerColor = Vault.card,
    unfocusedContainerColor = Vault.card,
    focusedBorderColor = Vault.primary,
    unfocusedBorderColor = Vault.border,
    cursorColor = Vault.primary,
)

/** Small red/orange count pill ("9+"). */
@Composable
internal fun SocCountPill(count: Int, color: Color, modifier: Modifier = Modifier, ring: Color? = null) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .then(if (ring != null) Modifier.border(2.dp, ring, shape) else Modifier)
            .background(color, shape)
            .defaultMinSize(minWidth = 16.dp, minHeight = 16.dp)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(if (count > 9) "9+" else "$count", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Black, maxLines = 1)
    }
}
