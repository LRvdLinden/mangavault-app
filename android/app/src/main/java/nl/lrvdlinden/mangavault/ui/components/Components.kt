package nl.lrvdlinden.mangavault.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarHalf
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import nl.lrvdlinden.mangavault.app.Toast
import nl.lrvdlinden.mangavault.model.CollectionItem
import nl.lrvdlinden.mangavault.model.Manga
import nl.lrvdlinden.mangavault.model.WishlistItem
import nl.lrvdlinden.mangavault.model.euro
import nl.lrvdlinden.mangavault.model.nonEmpty
import nl.lrvdlinden.mangavault.ui.theme.Vault
import nl.lrvdlinden.mangavault.ui.theme.hexColor
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// Shared building blocks used by every screen (ports of the iOS components).

val CardShape = RoundedCornerShape(14.dp)

/** Card background + border, like `.cardStyle()` on iOS. */
fun Modifier.cardStyle(highlighted: Boolean = false, shape: Shape = CardShape): Modifier =
    this.clip(shape)
        .background(Vault.card.copy(alpha = 0.6f), shape)
        .border(1.dp, if (highlighted) Vault.primary.copy(alpha = 0.45f) else Vault.border, shape)

/** Big two-tone headline: "LEAD ACCENT", with optional eyebrow and subtitle. */
@Composable
fun ScreenHeader(
    lead: String,
    accent: String,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    eyebrowIcon: ImageVector? = null,
    accentColor: Color = Vault.primary,
    subtitle: String? = null,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (eyebrow != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (eyebrowIcon != null) Icon(eyebrowIcon, null, tint = Vault.primary, modifier = Modifier.size(14.dp))
                Text(eyebrow.uppercase(), color = Vault.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            }
        }
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = Vault.foreground)) { append("$lead ") }
                withStyle(SpanStyle(color = accentColor)) { append(accent) }
            },
            fontSize = 30.sp, fontWeight = FontWeight.Black, lineHeight = 32.sp, letterSpacing = (-1).sp,
        )
        if (subtitle != null) Text(subtitle, color = Vault.muted, fontSize = 14.sp)
    }
}

@Composable
fun StatCard(icon: ImageVector, label: String, value: String, modifier: Modifier = Modifier, accent: Color = Vault.primary) {
    Column(modifier.cardStyle().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(14.dp))
            Text(label.uppercase(), color = Vault.muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(value, color = Vault.foreground, fontSize = 22.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth().cardStyle().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title.uppercase(), color = Vault.muted, fontSize = 13.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp, modifier = Modifier.weight(1f))
            trailing?.invoke()
        }
        content()
    }
}

@Composable
fun Chip(title: String, selected: Boolean, modifier: Modifier = Modifier, icon: ImageVector? = null, selectedColor: Color = Vault.primary, onClick: () -> Unit) {
    val shape = RoundedCornerShape(9.dp)
    Row(
        modifier
            .clip(shape)
            .background(if (selected) selectedColor else Vault.card, shape)
            .border(1.dp, if (selected) Color.Transparent else Vault.border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (icon != null) Icon(icon, null, tint = if (selected) Color.White else Vault.muted, modifier = Modifier.size(14.dp))
        Text(title, color = if (selected) Color.White else Vault.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun Badge(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, fg: Color = Color.White, bg: Color = Vault.primary, ring: Color? = null) {
    val shape = RoundedCornerShape(4.dp)
    Row(
        modifier.background(bg, shape).border(1.dp, ring ?: Color.Transparent, shape).padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        if (icon != null) Icon(icon, null, tint = fg, modifier = Modifier.size(10.dp))
        Text(text, color = fg, fontSize = 9.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
    color: Color = Vault.primary,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier.heightIn(min = 44.dp),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White,
            disabledContainerColor = color.copy(alpha = 0.4f), disabledContentColor = Color.White.copy(alpha = 0.7f)),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    ) {
        if (loading) CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
        else {
            if (icon != null) { Icon(icon, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)) }
            Text(text, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}

@Composable
fun OutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    color: Color = Vault.foreground,
    borderColor: Color = Vault.border,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 44.dp),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, borderColor),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = Vault.card.copy(alpha = 0.4f), contentColor = color),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    ) {
        if (icon != null) { Icon(icon, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)) }
        Text(text, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

/** Rating on the web app's 0–10 scale (each star = 2 points). [onSet] null = read-only. */
@Composable
fun StarRating(value: Int, size: Dp = 14.dp, onSet: ((Int) -> Unit)? = null) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        for (i in 1..5) {
            val full = value >= i * 2
            val half = !full && value >= i * 2 - 1
            Icon(
                if (full) Icons.Filled.Star else if (half) Icons.Filled.StarHalf else Icons.Filled.StarBorder,
                null,
                tint = if (full || half) Vault.orange else Color(0xFF595959),
                modifier = Modifier.size(size).then(if (onSet != null) Modifier.clickable { onSet(i * 2) } else Modifier),
            )
        }
    }
}

@Composable
fun EmptyState(icon: ImageVector, text: String, modifier: Modifier = Modifier, buttonTitle: String? = null, action: (() -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(vertical = 60.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Icon(icon, null, tint = Vault.muted.copy(alpha = 0.5f), modifier = Modifier.size(44.dp))
        Text(text, color = Vault.muted, fontSize = 14.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (buttonTitle != null && action != null) PrimaryButton(buttonTitle, action)
    }
}

@Composable
fun LoadingView(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(vertical = 80.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Vault.primary)
    }
}

@Composable
fun ProgressBar(value: Float, modifier: Modifier = Modifier, height: Dp = 8.dp, color: Color = Vault.primary) {
    Box(modifier.fillMaxWidth().height(height).clip(CircleShape).background(Vault.secondary)) {
        Box(Modifier.fillMaxWidth(value.coerceIn(0f, 1f)).fillMaxHeight().clip(CircleShape).background(color))
    }
}

/** Remote image with placeholder; null/blank url shows the placeholder only. */
@Composable
fun RemoteImage(url: String?, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop, contentDescription: String? = null) {
    if (url.isNullOrBlank()) {
        Box(modifier.background(Vault.secondary))
        return
    }
    SubcomposeAsyncImage(
        model = url,
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = modifier,
        loading = { Box(Modifier.fillMaxSize().background(Vault.secondary)) },
        error = { Box(Modifier.fillMaxSize().background(Vault.secondary)) },
    )
}

// MARK: Covers

data class CoverInfo(
    val title: String,
    val coverUrl: String?,
    val coverSeed: String?,
    val publisher: String?,
    val isOmnibus: Boolean,
    val isShonenJump: Boolean,
    val volumesText: String,
) {
    companion object {
        fun of(m: Manga) = CoverInfo(m.title, nonEmpty(m.cover_image_url), m.cover_seed, m.publisher, m.isOmnibus, m.isShonenJump,
            if (m.isOmnibus) (nonEmpty(m.volumes_included) ?: "OMNIBUS") else "${maxOf(m.totalVolumes, 1)} VOLUMES")
        fun of(i: CollectionItem) = CoverInfo(i.titleText, i.displayCover, i.cover_seed, i.publisher, i.isOmnibus, i.is_shonen_jump ?: false,
            if (i.isOmnibus) (nonEmpty(i.volumes_included) ?: "OMNIBUS") else i.volumeLabel.uppercase())
        fun of(w: WishlistItem) = CoverInfo(w.title ?: "", w.displayCover, w.cover_seed, w.publisher, w.isOmnibus, w.is_shonen_jump ?: false,
            w.volumeLabel.uppercase())
    }
}

/** 2:3 cover: real image, or the web app's generated gradient placeholder. Width comes from [modifier]. */
@Composable
fun CoverView(info: CoverInfo, modifier: Modifier = Modifier, cornerRadius: Dp = 10.dp) {
    val shape = RoundedCornerShape(cornerRadius)
    Box(modifier.aspectRatio(2f / 3f).clip(shape).border(1.dp, Color.White.copy(alpha = 0.1f), shape)) {
        if (info.coverUrl != null) RemoteImage(info.coverUrl, Modifier.fillMaxSize())
        else GeneratedCover(info, Modifier.fillMaxSize())
    }
}

private val coverPalette = listOf(
    "#ff6b00" to "#1a0a00", "#ff3d00" to "#0d0d0d", "#ff8f00" to "#0a0a0a", "#e65100" to "#000000",
    "#ff6b00" to "#0a0500", "#bf360c" to "#050505", "#ff7000" to "#100808", "#cc4400" to "#080808",
)

/** Same 32-bit string hash as the web app. */
fun coverHash(s: String): Long {
    var t = 0
    for (c in s) t = (t shl 5) - t + c.code
    return abs(t.toLong())
}

@Composable
fun GeneratedCover(info: CoverInfo, modifier: Modifier = Modifier) {
    val key = nonEmpty(info.coverSeed) ?: info.title.ifEmpty { "manga" }
    val p = coverPalette[(coverHash(key) % coverPalette.size).toInt()]
    Box(modifier.background(Brush.linearGradient(listOf(hexColor(p.first), hexColor(p.second))))) {
        Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.3f), Color.Transparent), center = Offset(60f, 40f), radius = 300f)))
        Column(Modifier.fillMaxSize().padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (info.isOmnibus) Badge("OMNIBUS", fg = Vault.primary, bg = Color.Black, ring = Vault.primary.copy(alpha = 0.5f))
                else if (info.isShonenJump) Badge("SJ", fg = Color.Black, bg = Color.White)
                Spacer(Modifier.weight(1f))
                Text((info.publisher ?: "").uppercase(), color = Color.White.copy(alpha = 0.6f), fontSize = 8.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
            Spacer(Modifier.weight(1f))
            Box(Modifier.width(24.dp).height(2.dp).background(Color.White.copy(alpha = 0.8f)))
            Spacer(Modifier.height(4.dp))
            Text(info.title.uppercase(), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Black, maxLines = 3, lineHeight = 16.sp)
            Text(info.volumesText, color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** Catalog grid card. */
@Composable
fun MangaCard(manga: Manga, modifier: Modifier = Modifier, owned: Boolean = false, showGenre: Boolean = false, onClick: () -> Unit) {
    Column(modifier.clickable(onClick = onClick), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box {
            CoverView(CoverInfo.of(manga), Modifier.fillMaxWidth().shadow(8.dp, RoundedCornerShape(10.dp)))
            Box(Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                when {
                    owned -> Badge("OWNED", icon = Icons.Filled.CheckCircle, bg = Vault.emerald)
                    manga.isOmnibus -> Badge("3-IN-1", fg = Vault.primary, bg = Vault.background.copy(alpha = 0.8f), ring = Vault.primary.copy(alpha = 0.5f))
                    manga.isShonenJump -> Badge("SJ")
                }
            }
        }
        Text(manga.title, color = Vault.foreground, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(verticalAlignment = Alignment.CenterVertically) {
            val sub = if (showGenre) (nonEmpty(manga.genre) ?: manga.publisher ?: "")
            else if (manga.isOmnibus) (nonEmpty(manga.volumes_included) ?: "Omnibus") else "${manga.totalVolumes} vols"
            Text(sub, color = Vault.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(manga.priceValue.euro(), color = Vault.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// MARK: Branding

@Composable
fun LogoMark(size: Dp = 36.dp) {
    Box(
        Modifier.size(size).shadow(size * 0.3f, RoundedCornerShape(size * 0.25f), ambientColor = Vault.primary, spotColor = Vault.primary)
            .background(Vault.primary, RoundedCornerShape(size * 0.25f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.LocalFireDepartment, null, tint = Color.White, modifier = Modifier.size(size * 0.55f))
    }
}

@Composable
fun Wordmark(size: TextUnit = 20.sp) {
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = Vault.foreground)) { append("MANGA") }
            withStyle(SpanStyle(color = Vault.primary)) { append("VAULT") }
        },
        fontSize = size, fontWeight = FontWeight.Black, letterSpacing = (-1).sp,
    )
}

// MARK: Overlays

@Composable
fun ToastView(toast: Toast, modifier: Modifier = Modifier) {
    Row(
        modifier.padding(horizontal = 16.dp).fillMaxWidth()
            .shadow(12.dp, CardShape)
            .background(Color(0xF2222222), CardShape)
            .border(1.dp, if (toast.isError) Vault.red.copy(alpha = 0.5f) else Vault.border, CardShape)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(if (toast.isError) Icons.Filled.Warning else Icons.Filled.CheckCircle, null, tint = if (toast.isError) Vault.red else Vault.primary)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(toast.title, color = Vault.foreground, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            if (!toast.message.isNullOrEmpty()) Text(toast.message, color = Vault.muted, fontSize = 12.sp)
        }
    }
}

private data class Particle(val color: Color, val vx: Float, val vy: Float, val spin: Float, val size: Float, val circle: Boolean)

private val confettiColors = listOf("#26ccff", "#a25afd", "#ff5e7e", "#88ff5a", "#fcff42", "#ffa62d", "#ff36ff", "#FF7A00").map { hexColor(it) }

/** Lightweight confetti burst, replayed whenever [trigger] changes (> 0). */
@Composable
fun ConfettiOverlay(trigger: Int, modifier: Modifier = Modifier) {
    if (trigger == 0) return
    val progress = remember { Animatable(1f) }
    val particles = remember(trigger) {
        List(90) {
            val angle = Math.toRadians(Random.nextDouble(-125.0, -55.0))
            val speed = Random.nextDouble(350.0, 850.0)
            Particle(confettiColors.random(), (cos(angle) * speed).toFloat(), (sin(angle) * speed).toFloat(),
                Random.nextDouble(-2.0, 2.0).toFloat(), Random.nextDouble(6.0, 11.0).toFloat(), Random.nextBoolean())
        }
    }
    LaunchedEffect(trigger) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(2600, easing = LinearEasing))
    }
    if (progress.value >= 1f) return
    androidx.compose.foundation.Canvas(modifier.fillMaxSize()) {
        val t = progress.value * 2.6f
        val density = this.density
        for (p in particles) {
            val px = size.width * 0.5f + p.vx * t * density / 2.5f
            val py = size.height * 0.4f + (p.vy * t + 450f * t * t) * density / 2.5f
            val alpha = (1f - t / 2.6f).coerceIn(0f, 1f)
            val s = p.size * density
            rotate(p.spin * t * 360f, Offset(px, py)) {
                if (p.circle) drawCircle(p.color.copy(alpha = alpha), s / 2, Offset(px, py))
                else drawRect(p.color.copy(alpha = alpha), Offset(px - s / 2, py - s * 0.3f), Size(s, s * 0.6f))
            }
        }
    }
}
