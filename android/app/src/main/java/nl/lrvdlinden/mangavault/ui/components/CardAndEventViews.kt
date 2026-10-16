package nl.lrvdlinden.mangavault.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import nl.lrvdlinden.mangavault.app.LocalApp
import nl.lrvdlinden.mangavault.app.Route
import nl.lrvdlinden.mangavault.core.Engines
import nl.lrvdlinden.mangavault.model.EventBannerData
import nl.lrvdlinden.mangavault.model.ServerDate
import nl.lrvdlinden.mangavault.model.TradingCard
import nl.lrvdlinden.mangavault.model.nonEmpty
import nl.lrvdlinden.mangavault.ui.theme.Vault
import nl.lrvdlinden.mangavault.ui.theme.hexColor
import java.time.Duration
import java.time.Instant
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.sin

// Trading card, countdown, snow and the community event banner (ports of CardAndEventViews.swift).

// MARK: - Trading card

enum class CardSize { SM, MD, LG }

private fun CardSize.cardWidth(): Dp = when (this) {
    CardSize.SM -> 96.dp
    CardSize.MD -> 128.dp
    CardSize.LG -> 192.dp
}

/** Swift `String.capitalized`: every word starts upper-case, the rest lower-case. */
private fun cevCapitalized(s: String): String =
    s.split(" ").joinToString(" ") { w -> w.lowercase(Locale.US).replaceFirstChar { it.titlecase(Locale.US) } }

@Composable
fun TradingCardView(
    card: TradingCard,
    modifier: Modifier = Modifier,
    owned: Boolean = false,
    count: Int = 0,
    favorite: Boolean = false,
    size: CardSize = CardSize.MD,
    onClick: (() -> Unit)? = null,
    onFavorite: (() -> Unit)? = null,
) {
    val r = card.rarityValue
    val color = hexColor(r.hex)
    val w = size.cardWidth()
    val shape = RoundedCornerShape(12.dp)
    val art = nonEmpty(card.artwork_url)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier
                .width(w)
                .height(w * 1.4f)
                .shadow(10.dp, shape, ambientColor = color.copy(alpha = 0.5f), spotColor = color.copy(alpha = 0.5f))
                .clip(shape)
                .background(Vault.secondary)
                .border(2.dp, color, shape)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        ) {
            when {
                owned && art != null -> RemoteImage(art, Modifier.fillMaxSize())
                owned -> Box(
                    Modifier.fillMaxSize().background(Brush.linearGradient(listOf(color, Vault.background))),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Filled.Image, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(22.dp))
                        Text("No image", color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp)
                    }
                }
                else -> Box(Modifier.fillMaxSize().background(Vault.secondary), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Lock, null, tint = Vault.muted, modifier = Modifier.size(22.dp))
                }
            }
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.85f))))
            Column(Modifier.fillMaxSize().padding(6.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        r.label, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Black,
                        modifier = Modifier.background(color, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 3.dp),
                    )
                    Spacer(Modifier.weight(1f))
                    if (count > 1) {
                        Text(
                            "×$count", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black,
                            modifier = Modifier.background(Color.Black.copy(alpha = 0.7f), CircleShape).padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
                if (card.isLimited) {
                    Text(
                        "Limited Event", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(top = 4.dp).align(Alignment.CenterHorizontally)
                            .background(Vault.primary, CircleShape).padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                Row(verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(
                            card.title, color = Color.White, fontSize = if (size == CardSize.SM) 9.sp else 11.sp,
                            fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            lineHeight = if (size == CardSize.SM) 11.sp else 13.sp,
                        )
                        Text(cevCapitalized(card.category ?: ""), color = Color.White.copy(alpha = 0.6f), fontSize = 8.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                    if (favorite) Icon(Icons.Filled.Star, null, tint = Vault.orange, modifier = Modifier.size(12.dp))
                }
            }
            if (card.card_id.startsWith("christmas")) {
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = 3.dp.toPx()
                    drawRoundRect(
                        color = Vault.red,
                        topLeft = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2),
                        size = androidx.compose.ui.geometry.Size(this.size.width - stroke, this.size.height - stroke),
                        cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx()),
                        style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()), 0f)),
                    )
                }
            }
        }
        if (onFavorite != null && owned) {
            Text(
                if (favorite) "★ Favorite" else "☆ Favorite",
                color = if (favorite) Color.White else Vault.muted,
                fontSize = 10.sp, fontWeight = FontWeight.Bold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier
                    .width(w)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (favorite) Vault.primary else Vault.secondary)
                    .clickable(onClick = onFavorite)
                    .padding(vertical = 5.dp),
            )
        }
    }
}

// MARK: - Countdown

@Composable
fun CountdownView(target: Instant?, modifier: Modifier = Modifier, compact: Boolean = false) {
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Instant.now()
            delay(1000)
        }
    }
    val s = maxOf(0L, Duration.between(now, target ?: now).seconds)
    if (s <= 0L) {
        Text("It's here!", modifier = modifier, color = Vault.primary, fontSize = 17.sp, fontWeight = FontWeight.Black)
    } else {
        Row(modifier, horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 8.dp)) {
            CevCountdownUnit(s / 86_400, "Days", compact)
            CevCountdownUnit(s % 86_400 / 3600, "Hrs", compact)
            CevCountdownUnit(s % 3600 / 60, "Min", compact)
            CevCountdownUnit(s % 60, "Sec", compact)
        }
    }
}

@Composable
private fun CevCountdownUnit(v: Long, label: String, compact: Boolean) {
    Column(
        Modifier
            .widthIn(min = if (compact) 34.dp else 46.dp)
            .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
            .padding(vertical = if (compact) 4.dp else 6.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Text(
            String.format(Locale.US, "%02d", v), color = Color.White,
            fontSize = if (compact) 16.sp else 22.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace,
        )
        Text(label.uppercase(Locale.US), color = Vault.muted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
    }
}

// MARK: - Snow

private class CevFlake(val x: Float, val size: Float, val dur: Float, val delay: Float)

private val cevFlakes: List<CevFlake> = (0 until 18).map { t ->
    CevFlake((t * 37 % 100) / 100f, (8 + t * 7 % 10).toFloat(), (6 + t * 3 % 7).toFloat(), t * 0.7f)
}

private val cevLightColors = listOf(Vault.primary, Color.White, Vault.red, Vault.emerald)

/** Falling snow + optional string lights (web: SnowLayer). Does not intercept touches. */
@Composable
fun SnowLayer(modifier: Modifier = Modifier, lights: Boolean = false) {
    val time = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) {
            withFrameNanos { frame -> time.floatValue = ((frame - start) / 1_000_000L) / 1000f }
        }
    }
    BoxWithConstraints(modifier) {
        val wPx = if (constraints.hasBoundedWidth) constraints.maxWidth.toFloat() else 0f
        val hPx = if (constraints.hasBoundedHeight) constraints.maxHeight.toFloat() else 0f
        for (f in cevFlakes) {
            Text(
                "❄", color = Color.White.copy(alpha = 0.8f), fontSize = f.size.sp,
                modifier = Modifier.offset {
                    val t = time.floatValue
                    val p = ((t + f.delay) / f.dur) % 1f
                    val half = f.size.sp.toPx() / 2f
                    val pad = 10.dp.toPx()
                    IntOffset((wPx * f.x - half).toInt(), (-pad + (hPx + 2 * pad) * p - half).toInt())
                },
            )
        }
        if (lights) {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                for (i in 0 until 14) {
                    val c = cevLightColors[i % 4]
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Box(
                            Modifier
                                .size(6.dp)
                                .graphicsLayer { alpha = (0.5f + 0.5f * sin(time.floatValue * 3f + (i % 4).toFloat())).coerceIn(0f, 1f) }
                                .shadow(4.dp, CircleShape, ambientColor = c, spotColor = c)
                                .background(c, CircleShape),
                        )
                    }
                }
            }
        }
    }
}

// MARK: - Event banner (communityEngine.banner) — shown on Home, Catalog and Events

@Composable
fun EventBannerView(modifier: Modifier = Modifier) {
    val app = LocalApp.current
    var data by remember { mutableStateOf<EventBannerData?>(null) }
    LaunchedEffect(Unit) {
        while (true) {
            try {
                data = Engines.banner()
            } catch (e: CancellationException) {
                throw e
            } catch (ignored: Exception) {
            }
            delay(60_000)
        }
    }
    val d = data ?: return
    val kind = d.kind ?: return

    if (kind == "neutral") {
        Column(
            modifier.fillMaxWidth().cardStyle().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("MANGAVAULT", color = Vault.primary, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
            Text("Your manga journey continues", color = Vault.foreground, fontSize = 17.sp, fontWeight = FontWeight.Black)
            Text("No event scheduled right now. Keep reading and building your vault!", color = Vault.muted, fontSize = 12.sp)
        }
        return
    }
    val e = d.event ?: return
    val live = d.status == "live"
    val ended = d.status == "ended"
    val accent = hexColor(e.accent)
    val route: Route = if (kind == "christmas") Route.ChristmasEvent
    else if (kind == "event" || kind == "grand") Route.Event(e.id)
    else Route.BorutoEvent
    val shape = RoundedCornerShape(16.dp)
    val image = e.heroImage

    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = 210.dp)
            .clip(shape)
            .border(1.dp, accent.copy(alpha = 0.5f), shape)
            .clickable { app.open(route) },
    ) {
        if (image != null) RemoteImage(image, Modifier.matchParentSize())
        else Box(Modifier.matchParentSize().background(Brush.linearGradient(listOf(accent, Vault.background))))
        Box(Modifier.matchParentSize().background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.92f), Color.Black.copy(alpha = 0.45f)))))
        if (e.decoration == "snow" || kind == "christmas" || e.decoration == "lights") {
            SnowLayer(Modifier.matchParentSize(), lights = true)
        }
        Column(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                if (live) "● Live now" else if (ended) "Event ended" else "Coming soon",
                color = accent, fontSize = 12.sp, fontWeight = FontWeight.Black,
            )
            Text(
                if (kind == "christmas") "🎄 CHRISTMAS READING FESTIVAL" else e.displayTitle,
                color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black, lineHeight = 24.sp,
            )
            val sub = nonEmpty(e.subtitle) ?: nonEmpty(e.short_description)
            if (sub != null) {
                Text(sub, color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text("${e.start_date ?: ""} → ${e.end_date ?: ""}", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            val p = d.participation
            if (p != null && d.max_points > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Your points", color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("${p.progress}/${d.max_points}", color = accent, fontSize = 11.sp, fontWeight = FontWeight.Black)
                }
                ProgressBar(p.progress.toFloat() / maxOf(d.max_points, 1).toFloat(), height = 5.dp, color = accent)
            }
            if (!ended) {
                Text(
                    if (live) "ENDS IN" else "STARTS IN", color = Color.White.copy(alpha = 0.7f),
                    fontSize = 11.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 2.dp),
                )
                CountdownView(ServerDate.parse(d.target), compact = true)
            }
        }
    }
}
