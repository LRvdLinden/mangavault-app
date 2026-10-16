package nl.lrvdlinden.mangavault.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import nl.lrvdlinden.mangavault.model.ReadStatus

/** Colours from the web app's CSS variables (theme "kaiachu-orange"). */
object Vault {
    val primary = Color(0xFFFF7A00)
    val primaryHover = Color(0xFFFF5100)
    val background = Color(0xFF121212)
    val card = Color(0xFF1B1B1B)
    val secondary = Color(0xFF222222)
    val border = Color(0xFF333333)
    val foreground = Color(0xFFF5F5F5)
    val muted = Color(0xFFA3A3A3)
    val emerald = Color(0xFF10B981)
    val sky = Color(0xFF38BDF8)
    val orange = Color(0xFFFB923C)
    val amber = Color(0xFFF59E0B)
    val red = Color(0xFFEF4444)
    val purple = Color(0xFFA855F7)

    fun statusColor(s: ReadStatus): Color = when (s) {
        ReadStatus.READING -> primary
        ReadStatus.COMPLETED -> emerald
        ReadStatus.PLAN_TO_READ -> sky
        ReadStatus.ON_HOLD -> Color(0xFFEA580C)
        ReadStatus.DROPPED -> Color(0xFFA3A3A3)
    }
}

/** "#FF7A00" / "#f80" / "FF7A00" → Color (falls back to primary). */
fun hexColor(hex: String?): Color {
    var s = (hex ?: "").trim().removePrefix("#")
    if (s.length == 3) s = s.map { "$it$it" }.joinToString("")
    val v = s.take(6).toLongOrNull(16) ?: return Vault.primary
    return Color(0xFF000000 or v)
}

@Composable
fun MangaVaultTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Vault.primary,
            onPrimary = Color.White,
            secondary = Vault.primary,
            background = Vault.background,
            onBackground = Vault.foreground,
            surface = Vault.background,
            onSurface = Vault.foreground,
            surfaceVariant = Vault.card,
            onSurfaceVariant = Vault.muted,
            surfaceContainer = Vault.card,
            surfaceContainerHigh = Vault.secondary,
            surfaceContainerHighest = Vault.secondary,
            surfaceContainerLow = Vault.card,
            outline = Vault.border,
            outlineVariant = Vault.border,
            error = Vault.red,
        ),
        content = content,
    )
}
