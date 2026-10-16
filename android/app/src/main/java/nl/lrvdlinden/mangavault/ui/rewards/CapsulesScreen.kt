package nl.lrvdlinden.mangavault.ui.rewards

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import nl.lrvdlinden.mangavault.app.AppState
import nl.lrvdlinden.mangavault.app.LocalApp
import nl.lrvdlinden.mangavault.core.Engines
import nl.lrvdlinden.mangavault.model.CapsuleReward
import nl.lrvdlinden.mangavault.model.CapsulesState
import nl.lrvdlinden.mangavault.model.CardRarity
import nl.lrvdlinden.mangavault.model.VaultCapsule
import nl.lrvdlinden.mangavault.model.nonEmpty
import nl.lrvdlinden.mangavault.ui.components.LoadingView
import nl.lrvdlinden.mangavault.ui.components.OutlineButton
import nl.lrvdlinden.mangavault.ui.components.PrimaryButton
import nl.lrvdlinden.mangavault.ui.components.RemoteImage
import nl.lrvdlinden.mangavault.ui.components.ScreenHeader
import nl.lrvdlinden.mangavault.ui.components.cardStyle
import nl.lrvdlinden.mangavault.ui.theme.Vault
import nl.lrvdlinden.mangavault.ui.theme.hexColor
import kotlin.coroutines.cancellation.CancellationException

// Port of CapsulesView (web /capsules): sealed capsules, drop rates, opened history and the reveal animation.

private data class RwRevealInfo(val capsuleId: String, val tier: String, val reward: CapsuleReward?, val ready: Boolean)

private class RwCapsulesModel(private val app: AppState) {
    var data by mutableStateOf<CapsulesState?>(null)
    var loading by mutableStateOf(true)
    var errorText by mutableStateOf<String?>(null)
    var reveal by mutableStateOf<RwRevealInfo?>(null)

    val sealed: List<VaultCapsule>
        get() = (data?.capsules ?: emptyList()).filter { !(it.opened ?: false) }.sortedBy { rwTierRank(it.tier) }

    val opened: List<VaultCapsule>
        get() = (data?.capsules ?: emptyList()).filter { it.opened ?: false }
            .sortedByDescending { nonEmpty(it.opened_date) ?: it.created_date ?: "" }

    suspend fun load() {
        try {
            data = Engines.capsules()
            errorText = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            errorText = e.message ?: "Could not load your capsules."
        }
        loading = false
    }

    suspend fun openCapsule(c: VaultCapsule) {
        if (reveal != null) return
        reveal = RwRevealInfo(c.id, c.tier ?: "common", null, false)
        try {
            val openedCapsule = Engines.openCapsule(c.id)
            reveal = reveal?.copy(reward = openedCapsule?.reward, ready = true)
            app.dataChanged()
            load()
        } catch (e: Exception) {
            reveal = null
            app.fail("Could not open capsule", e)
        }
    }
}

@Composable
fun CapsulesScreen() {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    val m = remember { RwCapsulesModel(app) }
    var refreshing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { m.load() }

    Box(Modifier.fillMaxSize()) {
        RwScroll(refreshing, onRefresh = {
            scope.launch {
                refreshing = true
                m.load()
                refreshing = false
            }
        }) {
            ScreenHeader(
                "VAULT", "CAPSULES", eyebrow = "Capsules", eyebrowIcon = Icons.Filled.Inventory2,
                subtitle = "Free capsules earned through collecting, achievements, daily quests and events. No payments, ever.",
            )
            val d = m.data
            if (m.loading && d == null) {
                LoadingView()
            } else if (d != null) {
                RwSealedSection(m.sealed, enabled = m.reveal == null) { c -> scope.launch { m.openCapsule(c) } }
                RwOddsSection(d)
                RwOpenedSection(m.opened)
            } else {
                RwErrorState(m.errorText ?: "Could not load your capsules.") { scope.launch { m.load() } }
            }
        }

        val r = m.reveal
        if (r != null) {
            RwCapsuleReveal(r, onReveal = { app.celebrate() }, onContinue = { m.reveal = null })
        }
    }
}

@Composable
private fun RwSealedSection(list: List<VaultCapsule>, enabled: Boolean, onOpen: (VaultCapsule) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        RwSectionTitle("Sealed (${list.size})", icon = Icons.Filled.Inventory2)
        if (list.isEmpty()) {
            Text(
                "No sealed capsules. Add volumes, finish daily quests or complete events to earn more.",
                color = Vault.muted, fontSize = 14.sp,
                modifier = Modifier.fillMaxWidth().cardStyle().padding(16.dp),
            )
        } else {
            RwGrid(list, minCell = 100.dp, spacing = 12.dp) { c -> RwCapsuleTile(c, enabled) { onOpen(c) } }
        }
    }
}

@Composable
private fun RwCapsuleTile(c: VaultCapsule, enabled: Boolean, onClick: () -> Unit) {
    val tier = c.tier ?: "common"
    val source = rwCapitalized((c.source ?: "").replace("_", " "))
    val colors = rwTierColors(tier)
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 130.dp)
            .shadow(8.dp, shape, ambientColor = colors.first().copy(alpha = 0.4f), spotColor = colors.first().copy(alpha = 0.4f))
            .clip(shape)
            .background(Brush.linearGradient(colors), shape)
            .border(1.dp, Color.White.copy(alpha = 0.2f), shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        Icon(Icons.Filled.Inventory2, null, tint = Color.White, modifier = Modifier.size(34.dp))
        Text("${rwCapitalized(tier)} capsule", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
        if (source.isNotEmpty()) {
            Text(source, color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun RwOddsSection(d: CapsulesState) {
    val tiers = d.odds.keys.sortedBy { rwTierRank(it) }
    Column(Modifier.fillMaxWidth().cardStyle().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        RwSectionTitle("Drop rates", icon = Icons.Filled.BarChart)
        if (tiers.isEmpty()) {
            Text("Drop rates are not available right now.", color = Vault.muted, fontSize = 12.sp)
        }
        for (tier in tiers) {
            RwOddsRow(tier, d.odds[tier] ?: emptyMap(), if (d.fallback_xp.containsKey(tier)) d.fallbackXp(tier) else null)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RwOddsRow(tier: String, odds: Map<String, Double>, fallback: Int?) {
    val rarities = odds.keys.sortedBy { rwRarityRank(it) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(8.dp).background(rwTierColors(tier).first(), CircleShape))
            Text(rwCapitalized(tier), color = Vault.foreground, fontSize = 14.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
            if (fallback != null) {
                Text("Duplicate: $fallback XP", color = Vault.muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (r in rarities) {
                val rar = CardRarity.of(r)
                val c = hexColor(rar.hex)
                Text(
                    "${rar.label} ${rwPercent(odds[r] ?: 0.0)}", color = c, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.background(c.copy(alpha = 0.12f), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 3.dp),
                )
            }
        }
    }
}

@Composable
private fun RwOpenedSection(list: List<VaultCapsule>) {
    Column(Modifier.fillMaxWidth().cardStyle().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        RwSectionTitle("Opened (${list.size})", icon = Icons.Filled.Inventory2)
        if (list.isEmpty()) Text("No opened capsules yet.", color = Vault.muted, fontSize = 12.sp)
        for (c in list) RwOpenedRow(c)
    }
}

@Composable
private fun RwOpenedRow(c: VaultCapsule) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            Modifier.size(30.dp).background(Brush.linearGradient(rwTierColors(c.tier)), RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Inventory2, null, tint = Color.White, modifier = Modifier.size(14.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text("${rwCapitalized(c.tier ?: "common")} capsule", color = Vault.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(c.reward?.title ?: "Opening…", color = Vault.foreground, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (c.reward?.kind == "xp") {
            Text("XP", color = Vault.primary, fontSize = 12.sp, fontWeight = FontWeight.Black)
        } else if (nonEmpty(c.reward?.rarity) != null) {
            RwRarityPill(c.reward?.rarity)
        }
    }
}

// MARK: - Reveal overlay

@Composable
private fun RwCapsuleReveal(info: RwRevealInfo, onReveal: () -> Unit, onContinue: () -> Unit) {
    var elapsed by remember(info.capsuleId) { mutableStateOf(false) }
    var revealed by remember(info.capsuleId) { mutableStateOf(false) }
    LaunchedEffect(info.capsuleId) {
        delay(1400)
        elapsed = true
    }
    val showReward = info.ready && elapsed
    LaunchedEffect(showReward) {
        if (showReward && !revealed) {
            revealed = true
            onReveal()
        }
    }
    BackHandler(enabled = showReward) { onContinue() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.9f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { },
        contentAlignment = Alignment.Center,
    ) {
        if (showReward) RwRevealReward(info.reward, onContinue)
        else RwRevealCharging(info.tier) { elapsed = true }
    }
}

@Composable
private fun RwRevealCharging(tier: String, onSkip: () -> Unit) {
    val colors = rwTierColors(tier)
    val transition = rememberInfiniteTransition(label = "capsule")
    val pulse by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(350, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse",
    )
    val scale = 0.9f + 0.25f * pulse
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                Modifier.size(170.dp).scale(0.7f + 0.4f * pulse)
                    .background(Brush.radialGradient(listOf(colors.first().copy(alpha = 0.55f), Color.Transparent)), CircleShape),
            )
            Icon(
                Icons.Filled.Inventory2, null, tint = Color.White,
                modifier = Modifier
                    .size(90.dp)
                    .scale(scale)
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        drawRect(Brush.linearGradient(colors), blendMode = BlendMode.SrcAtop)
                    },
            )
        }
        Text("Opening ${rwCapitalized(tier)} capsule…", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Black)
        OutlineButton("Skip", onClick = onSkip)
    }
}

@Composable
private fun RwRevealReward(reward: CapsuleReward?, onContinue: () -> Unit) {
    val isCard = reward?.kind == "card"
    val isXP = reward?.kind == "xp"
    val color = hexColor(CardRarity.of(reward?.rarity).hex)
    val appear = remember { Animatable(0.4f) }
    val flip = remember { Animatable(90f) }
    LaunchedEffect(Unit) {
        launch { appear.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)) }
        flip.animateTo(0f, tween(550, easing = FastOutSlowInEasing))
    }
    val shape = RoundedCornerShape(14.dp)
    val art = nonEmpty(reward?.artwork_url)
    Column(
        Modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            Modifier
                .graphicsLayer {
                    scaleX = appear.value
                    scaleY = appear.value
                    rotationY = flip.value
                    cameraDistance = 12f * density
                }
                .size(180.dp, 252.dp)
                .shadow(20.dp, shape, ambientColor = color.copy(alpha = 0.6f), spotColor = color.copy(alpha = 0.6f))
                .clip(shape)
                .border(2.dp, color, shape),
            contentAlignment = Alignment.Center,
        ) {
            when {
                isXP -> Box(
                    Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Vault.primary, Vault.red))),
                    contentAlignment = Alignment.Center,
                ) { Text("XP", color = Color.White, fontSize = 54.sp, fontWeight = FontWeight.Black) }
                art != null -> RemoteImage(art, Modifier.fillMaxSize())
                else -> Box(Modifier.fillMaxSize().background(Vault.secondary), contentAlignment = Alignment.Center) {
                    Text("No image", color = Vault.muted, fontSize = 12.sp)
                }
            }
        }
        if (nonEmpty(reward?.rarity) != null) RwRarityPill(reward?.rarity)
        Text(reward?.title ?: "Reward", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
        Text(
            if (isCard) "Added to your Trading Cards" else "You already own every card at this rarity, so you got Vault XP instead.",
            color = Vault.muted, fontSize = 14.sp, textAlign = TextAlign.Center,
        )
        PrimaryButton("Continue", onClick = onContinue, modifier = Modifier.widthIn(min = 160.dp, max = 220.dp).fillMaxWidth())
    }
}
