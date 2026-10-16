package nl.lrvdlinden.mangavault.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Note
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nl.lrvdlinden.mangavault.core.Base44
import nl.lrvdlinden.mangavault.model.JournalType
import nl.lrvdlinden.mangavault.ui.theme.Vault

// Shared helpers for the library & discovery screens (ports of the `lv*` helpers in LibraryViews.swift).

/** A user-facing message thrown by a screen's own logic (shown as an error toast, not "Could not …"). */
internal class LibMessage(val text: String) : Exception(text)

internal fun libTrim(s: String): String = s.trim()

/** Int from a text field, or null (JSON null) for the payload. */
internal fun libNumberOrNull(s: String): Int? = s.trim().toIntOrNull()

/** Same as NSRegularExpression.escapedPattern: backslash-escapes regex metacharacters. */
internal fun libEscapePattern(s: String): String {
    val meta = "\\^$.|?*+()[]{}"
    val sb = StringBuilder()
    for (c in s) {
        if (meta.indexOf(c) >= 0) sb.append('\\')
        sb.append(c)
    }
    return sb.toString()
}

/** Case-insensitive Mongo regex filter. */
internal fun libRegex(s: String): Map<String, Any?> = mapOf("\$regex" to libEscapePattern(s), "\$options" to "i")

/** Swift `String.capitalized`: first letter of every word upper-case, the rest lower-case. */
internal fun libCapitalized(s: String): String =
    s.split(" ").joinToString(" ") { w -> w.lowercase().replaceFirstChar { it.uppercaseChar() } }

/** Distinct genre names of every manga (comma-separated values split and trimmed). */
internal suspend fun libDistinctGenres(lowercase: Boolean): Set<String> {
    val all = Base44.distinct("Manga", "genre")
    val set = mutableSetOf<String>()
    for (g in all) {
        for (part in g.split(",")) {
            val t = part.trim().let { if (lowercase) it.lowercase() else it }
            if (t.isNotEmpty()) set.add(t)
        }
    }
    return set
}

internal fun libJournalIcon(t: JournalType): ImageVector = when (t) {
    JournalType.NOTE -> Icons.AutoMirrored.Filled.Note
    JournalType.MOMENT -> Icons.Filled.AutoAwesome
    JournalType.REMINDER -> Icons.Filled.Notifications
    JournalType.RATING -> Icons.Filled.Star
}

/** Small bordered button (LVSmallButtonStyle). */
@Composable
internal fun LibSmallButton(
    text: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    color: Color = Vault.foreground,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier
            .clip(shape)
            .background(Vault.secondary.copy(alpha = 0.6f), shape)
            .border(1.dp, Vault.border, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
    ) {
        val tint = if (enabled) color else color.copy(alpha = 0.4f)
        if (icon != null) Icon(icon, null, tint = tint, modifier = Modifier.size(14.dp))
        if (text != null) Text(text, color = tint, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/** Upper-case field caption (lvFieldLabel). */
@Composable
internal fun LibFieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), modifier = modifier, color = Vault.muted, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
}

@Composable
internal fun libFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Vault.foreground,
    unfocusedTextColor = Vault.foreground,
    focusedContainerColor = Vault.card,
    unfocusedContainerColor = Vault.card,
    focusedBorderColor = Vault.primary,
    unfocusedBorderColor = Vault.border,
    cursorColor = Vault.primary,
    focusedPlaceholderColor = Vault.muted,
    unfocusedPlaceholderColor = Vault.muted,
)

/** Vault-styled text field. */
@Composable
internal fun LibTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    number: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        placeholder = { Text(placeholder, fontSize = 14.sp) },
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        keyboardOptions = if (number) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
        shape = RoundedCornerShape(10.dp),
        colors = libFieldColors(),
    )
}

/** Pull-to-refresh container (iOS `.refreshable`). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LibRefreshBox(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh, modifier = modifier) {
        content()
    }
}
