package com.thatgfsj.iching.ui.oracle

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.thatgfsj.iching.data.Hexagram
import kotlinx.coroutines.delay
import kotlin.math.absoluteValue
import kotlin.random.Random

/**
 * The single screen of the I Ching oracle app.
 *
 * Three meaningful states, all rendered in one screen:
 *  - [OracleUiState.Initial]  → home page: 64-gua word cloud +
 *    "八卦" title + "点击抽取" button.
 *  - [OracleUiState.Drawing]  → six lines fade in from the bottom
 *    one by one (≤ 3 s).
 *  - [OracleUiState.Loaded]  → full hexagram card with two
 *    buttons: "再抽一签" and "问 AI". The Ask-AI button opens a
 *    confirmation dialog and, on confirm, fires an ACTION_SEND
 *    intent routed at the DeepSeek app.
 */
@Composable
fun IChingOracleScreen(
    modifier: Modifier = Modifier,
    viewModel: IChingViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            when (val s = state) {
                is OracleUiState.Initial -> HomePage(
                    allNames = viewModel.allHexagramNames,
                    onDraw = viewModel::draw,
                )
                is OracleUiState.Drawing -> DrawingView(hexagram = s.hexagram)
                is OracleUiState.Loaded -> LoadedView(
                    hexagram = s.hexagram,
                    fadeKey = s.fadeKey,
                    onDraw = viewModel::draw,
                    onAskAi = { hex -> shareToDeepSeek(context, hex) },
                )
                is OracleUiState.Error -> ErrorView(message = s.message, onRetry = viewModel::draw)
            }
        }
    }
}

/* ------------------------------------------------------------------ */
/*  Home page: word cloud + 八卦 title + 点击抽取 button               */
/* ------------------------------------------------------------------ */

@Composable
private fun HomePage(
    allNames: List<String>,
    onDraw: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Slow-drifting word cloud, behind everything else.
        HexagramWordCloud(names = allNames)

        // Centered title + button.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "八卦",
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 72.sp,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "心诚则灵",
                fontFamily = FontFamily.Serif,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 4.sp,
            )
            Spacer(Modifier.height(56.dp))
            Button(
                onClick = onDraw,
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
                contentPadding = PaddingValues(horizontal = 40.dp, vertical = 16.dp),
            ) {
                Text(
                    text = "点击抽取",
                    fontFamily = FontFamily.Serif,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}

/**
 * 64 gua names scattered across the screen as a low-opacity word
 * cloud. Positions are seeded once per names list so the layout
 * doesn't reshuffle on every recomposition; a slow infinite
 * vertical drift adds a touch of life.
 */
@Composable
private fun HexagramWordCloud(names: List<String>) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val widthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val heightPx = with(density) { configuration.screenHeightDp.dp.toPx() }

    val positions = remember(names) {
        // Deterministic seeded random so the cloud is stable across
        // recompositions. 64 names → seed by list hash for stability.
        val seed = names.hashCode().toLong()
        val rng = Random(seed)
        names.map { Pair(rng.nextFloat(), rng.nextFloat()) }
    }

    val infinite = rememberInfiniteTransition(label = "cloud")
    val drift by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 24_000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "cloud-drift",
    )
    // Drift amplitude — small enough not to distract, big enough to feel alive.
    val driftPx = with(density) { 12.dp.toPx() }

    Box(modifier = Modifier.fillMaxSize().graphicsLayer { translationY = (drift - 0.5f) * 2f * driftPx }) {
        names.forEachIndexed { i, name ->
            val (fx, fy) = positions[i]
            Text(
                text = name,
                fontFamily = FontFamily.Serif,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f),
                modifier = Modifier
                    .graphicsLayer {
                        translationX = fx * (widthPx - with(density) { 80.dp.toPx() })
                        translationY = fy * (heightPx - with(density) { 80.dp.toPx() })
                    }
                    .padding(start = 24.dp, top = 48.dp),
            )
        }
    }
}

/* ------------------------------------------------------------------ */
/*  Drawing state: six lines fade in from the bottom                   */
/* ------------------------------------------------------------------ */

@Composable
private fun DrawingView(hexagram: Hexagram) {
    // Drive visibility per-line on a staggered schedule so the
    // bottom line appears first and the top line appears last.
    val lineCount = hexagram.lines.size
    val totalMs = 2_200L  // must match ViewModel DRAW_ANIMATION_MS
    val perLineMs = totalMs / lineCount  // ≈366ms per line

    var visibleCount by remember(hexagram.id) { mutableStateOf(0) }
    LaunchedEffect(hexagram.id) {
        for (i in 1..lineCount) {
            visibleCount = i
            delay(perLineMs)
        }
    }

    Column(
        modifier = Modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val linesTopDown = hexagram.lines.sortedByDescending { it.position }
        linesTopDown.forEachIndexed { idx, line ->
            // idx 0 is the top line (position 6), which is the last
            // to appear. line.position 1 is bottom = first to appear.
            // visibleCount counts from bottom up, so map:
            // visible threshold for this top-down row = lineCount - idx
            val threshold = lineCount - idx
            AnimatedVisibility(
                visible = visibleCount >= threshold,
                enter = fadeIn(animationSpec = tween(durationMillis = 200)),
                exit = fadeOut(animationSpec = tween(durationMillis = 100)),
            ) {
                Text(
                    text = line.glyph,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 22.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }
    }
}

/* ------------------------------------------------------------------ */
/*  Loaded state: hexagram card + dual action buttons                 */
/* ------------------------------------------------------------------ */

@Composable
private fun LoadedView(
    hexagram: Hexagram,
    fadeKey: Int,
    onDraw: () -> Unit,
    onAskAi: (Hexagram) -> Unit,
) {
    var showAskAiDialog by remember(fadeKey) { mutableStateOf(false) }

    if (showAskAiDialog) {
        AskAiDialog(
            onDismiss = { showAskAiDialog = false },
            onConfirm = {
                showAskAiDialog = false
                onAskAi(hexagram)
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        HexagramCard(
            hexagram = hexagram,
            onClick = onDraw,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = onDraw,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text("再抽一签", fontFamily = FontFamily.Serif)
            }
            Button(
                onClick = { showAskAiDialog = true },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
                modifier = Modifier.weight(1f),
            ) {
                Text("✨  问 AI", fontFamily = FontFamily.Serif)
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun HexagramCard(
    hexagram: Hexagram,
    onClick: () -> Unit,
) {
    // The card itself is the tap target — we wrap the whole thing
    // in a clickable Surface so tapping anywhere on the card (the
    // lines, the name, the judgment) triggers a redraw. The
    // button below is the explicit, discoverable affordance.
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            HexagramLines(hexagram = hexagram)
            HexagramHeader(hexagram = hexagram)
            JudgmentAndImage(hexagram = hexagram)
        }
    }
}

/**
 * The six lines, painted top-down visually (line 6 on top, line 1
 * at the bottom). We sort the `lines` list from highest position to
 * lowest before rendering so iterating top-to-bottom in the
 * Composable matches the visual top-to-bottom order.
 *
 * No animation here: the [OracleUiState.Drawing] phase already
 * animates the six lines in sequence; by the time we reach
 * [OracleUiState.Loaded] the lines are fully visible, so we just
 * render them statically. Re-animating on entry to Loaded would
 * cause a flicker.
 */
@Composable
private fun HexagramLines(hexagram: Hexagram) {
    val linesTopDown = hexagram.lines.sortedByDescending { it.position }
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        linesTopDown.forEach { line ->
            Text(
                text = line.glyph,
                fontFamily = FontFamily.Monospace,
                fontSize = 22.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun HexagramHeader(hexagram: Hexagram) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = hexagram.name_zh,
                fontFamily = FontFamily.Serif,
                fontSize = 32.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = hexagram.name_pinyin,
                fontFamily = FontFamily.Serif,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        Text(
            text = "#${hexagram.id} · ${hexagram.name_en}",
            fontFamily = FontFamily.SansSerif,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 0.5.sp,
        )
    }
}

@Composable
private fun JudgmentAndImage(hexagram: Hexagram) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SectionLabel("卦辞")
            Text(
                text = hexagram.judgment,
                fontFamily = FontFamily.Serif,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SectionLabel("象传")
            Text(
                text = hexagram.image,
                fontFamily = FontFamily.Serif,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        fontFamily = FontFamily.Monospace,
        fontSize = 10.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 1.sp,
    )
}

@Composable
private fun ErrorView(message: String, onRetry: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.padding(24.dp),
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.error,
            fontFamily = FontFamily.Serif,
        )
        OutlinedButton(onClick = onRetry) {
            Text("重试")
        }
    }
}

/**
 * Confirmation dialog shown when the user taps "问 AI". Confirms
 * the intent (share current hexagram to DeepSeek) and reminds the
 * user to type their actual question inside DeepSeek itself.
 */
@Composable
private fun AskAiDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "请向 AI 描述你的问题",
                fontFamily = FontFamily.Serif,
            )
        },
        text = {
            Text(
                text = "将分享当前卦象（卦名、卦辞、象传）到 DeepSeek。\n\n分享后，请在 DeepSeek 里输入你想问的事。",
                fontFamily = FontFamily.Serif,
                fontSize = 14.sp,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("去分享", fontFamily = FontFamily.Serif)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", fontFamily = FontFamily.Serif)
            }
        },
    )
}