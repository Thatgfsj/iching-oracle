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
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
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
import kotlin.random.Random

/**
 * The single screen of the I Ching oracle app.
 *
 * Three meaningful states, all rendered in one screen:
 *  - [OracleUiState.Initial]  → home page: 64-gua word cloud +
 *    "八卦" title + "点击抽取" button.
 *  - [OracleUiState.Drawing]  → six lines + name + judgment + image
 *    fade in from the bottom one by one (≤ 3 s).
 *  - [OracleUiState.Loaded]  → full hexagram card with two
 *    buttons: "再抽一签" and "问 AI". The bottom button bar slides
 *    up so the card-to-loaded transition isn't an abrupt swap.
 */
@Composable
fun IChingOracleScreen(
    modifier: Modifier = Modifier,
    viewModel: IChingViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pendingRedraw by viewModel.pendingRedraw.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Repeat-draw reminder. Shown above whatever state is on
    // screen (typically the previous Loaded hexagram), so the
    // user can still see the卦 they already drew.
    if (pendingRedraw != null) {
        RedrawConfirmDialog(
            onCancel = viewModel::cancelRedraw,
            onConfirm = viewModel::confirmRedraw,
        )
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        when (val s = state) {
            is OracleUiState.Initial -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                HomePage(
                    allNames = viewModel.allHexagramNames,
                    onDraw = viewModel::draw,
                )
            }
            is OracleUiState.Drawing -> DrawingView(
                hexagram = s.hexagram,
                onDraw = viewModel::draw,
            )
            is OracleUiState.Loaded -> LoadedView(
                hexagram = s.hexagram,
                fadeKey = s.fadeKey,
                onDraw = viewModel::draw,
                onAskAi = { hex -> shareToDeepSeek(context, hex) },
            )
            is OracleUiState.Error -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                ErrorView(message = s.message, onRetry = viewModel::draw)
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
            Spacer(Modifier.height(10.dp))
            Text(
                text = "请在心中思考你想问的问题",
                fontFamily = FontFamily.Serif,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
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

        // Bottom-anchored developer / repo footer. Static, no link.
        HomeFooter(modifier = Modifier.align(Alignment.BottomCenter))
    }
}

private const val REPO_NAME: String = "iching-oracle"

@Composable
private fun HomeFooter(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = "开发者：Thatgfsj",
            fontFamily = FontFamily.SansSerif,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
        )
        Text(
            text = "仓库：$REPO_NAME",
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
        )
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
/*  Drawing state: hexagram card reveals piece by piece               */
/* ------------------------------------------------------------------ */

/**
 * Stages of the hexagram reveal. The values are ordered to match
 * the timing schedule in [DrawingView] — bumping [linesRevealed]
 * advances the animation, then the named flags flip in sequence.
 */
private data class RevealState(
    val linesRevealed: Int = 0,   // 0..6
    val nameRevealed: Boolean = false,
    val judgmentRevealed: Boolean = false,
    val imageRevealed: Boolean = false,
) {
    companion object {
        val FullyRevealed = RevealState(
            linesRevealed = 6,
            nameRevealed = true,
            judgmentRevealed = true,
            imageRevealed = true,
        )
    }
}

private const val LINE_STEP_MS: Long = 240L
private const val NAME_STEP_MS: Long = 240L
private const val JUDGMENT_STEP_MS: Long = 280L
// IMAGE reveal triggers the Loaded transition in the ViewModel —
// no delay needed here.

/**
 * Shared layout for the Drawing and Loaded states. The hexagram
 * card and the action row live in the same Column, centered on
 * screen as one unit. This keeps the buttons right under the card
 * (the v1.0.5 feel) while still giving the page a centered
 * composition. When the buttons fade in the card shifts up by
 * roughly half the button row height — a small, smooth transition.
 */
@Composable
private fun HexagramScreenLayout(
    hexagram: Hexagram,
    reveal: RevealState,
    bottomBarVisible: Boolean,
    onDraw: () -> Unit,
    onAskAi: (Hexagram) -> Unit = {},
) {
    var showAskAiDialog by remember(hexagram.id) { mutableStateOf(false) }

    if (showAskAiDialog) {
        AskAiDialog(
            onDismiss = { showAskAiDialog = false },
            onConfirm = {
                showAskAiDialog = false
                onAskAi(hexagram)
            },
        )
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            HexagramCardColumn(
                hexagram = hexagram,
                reveal = reveal,
                onClick = if (bottomBarVisible) onDraw else null,
            )
            AnimatedVisibility(
                visible = bottomBarVisible,
                enter = slideInVertically(animationSpec = tween(180)) { it } +
                    fadeIn(animationSpec = tween(180)),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = onDraw,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("再算一卦", fontFamily = FontFamily.Serif)
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
            }
        }
    }
}

@Composable
private fun DrawingView(hexagram: Hexagram, onDraw: () -> Unit) {
    var reveal by remember(hexagram.id) { mutableStateOf(RevealState()) }

    LaunchedEffect(hexagram.id) {
        // Six lines: bottom → top, ~240ms apart (6 × 240 = 1440ms).
        for (i in 1..6) {
            reveal = reveal.copy(linesRevealed = i)
            delay(LINE_STEP_MS)
        }
        // Then name, judgment, image — each gets a short reveal pause
        // so the eye can take it in before the next section appears.
        reveal = reveal.copy(nameRevealed = true)
        delay(NAME_STEP_MS)
        reveal = reveal.copy(judgmentRevealed = true)
        delay(JUDGMENT_STEP_MS)
        reveal = reveal.copy(imageRevealed = true)
    }

    HexagramScreenLayout(
        hexagram = hexagram,
        reveal = reveal,
        bottomBarVisible = false,
        onDraw = onDraw,
    )
}

/* ------------------------------------------------------------------ */
/*  Loaded state: card fully revealed + slide-in bottom bar           */
/* ------------------------------------------------------------------ */

@Composable
private fun LoadedView(
    hexagram: Hexagram,
    fadeKey: Int,
    onDraw: () -> Unit,
    onAskAi: (Hexagram) -> Unit,
) {
    var showBottomBar by remember(fadeKey) { mutableStateOf(false) }

    // No delay — buttons fade in the same instant the ViewModel
    // hands us the Loaded state, i.e. right after the draw
    // animation finishes. The 180 ms tween on AnimatedVisibility
    // handles the easing.
    LaunchedEffect(fadeKey) {
        showBottomBar = true
    }

    HexagramScreenLayout(
        hexagram = hexagram,
        reveal = RevealState.FullyRevealed,
        bottomBarVisible = showBottomBar,
        onDraw = onDraw,
        onAskAi = onAskAi,
    )
}

/* ------------------------------------------------------------------ */
/*  Hexagram card content (shared between Drawing and Loaded)         */
/* ------------------------------------------------------------------ */

/**
 * Card layout shared by [DrawingView] and [LoadedView]. The card
 * itself is a tap target in [LoadedView]; during [DrawingView] the
 * [onClick] is null and tapping does nothing.
 */
@Composable
private fun HexagramCardColumn(
    hexagram: Hexagram,
    reveal: RevealState,
    onClick: (() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val cardModifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(16.dp))
        .let { m ->
            if (onClick != null) {
                m.clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                )
            } else m
        }

    Surface(
        modifier = cardModifier,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            HexagramLines(hexagram = hexagram, linesRevealed = reveal.linesRevealed)
            AnimatedVisibility(
                visible = reveal.nameRevealed,
                enter = fadeIn(animationSpec = tween(360)) +
                    scaleIn(initialScale = 0.96f, animationSpec = tween(360)),
            ) {
                HexagramHeader(hexagram = hexagram)
            }
            JudgmentAndImage(
                hexagram = hexagram,
                judgmentVisible = reveal.judgmentRevealed,
                imageVisible = reveal.imageRevealed,
            )
        }
    }
}

/**
 * The six lines, painted top-down visually (line 6 on top, line 1
 * at the bottom). Each line is wrapped in [AnimatedVisibility]
 * gated on `line.position <= linesRevealed`, so lines fade in
 * bottom-to-top as the parent reveal state advances.
 */
@Composable
private fun HexagramLines(hexagram: Hexagram, linesRevealed: Int) {
    val linesTopDown = hexagram.lines.sortedByDescending { it.position }
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        linesTopDown.forEach { line ->
            AnimatedVisibility(
                visible = line.position <= linesRevealed,
                enter = fadeIn(animationSpec = tween(220)) +
                    scaleIn(initialScale = 0.94f, animationSpec = tween(220)),
                exit = fadeOut(animationSpec = tween(120)),
            ) {
                Text(
                    text = line.glyph,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 22.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
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
private fun JudgmentAndImage(
    hexagram: Hexagram,
    judgmentVisible: Boolean,
    imageVisible: Boolean,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AnimatedVisibility(
            visible = judgmentVisible,
            enter = fadeIn(animationSpec = tween(360)) +
                slideInVertically(animationSpec = tween(360)) { 12 },
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
        }
        AnimatedVisibility(
            visible = judgmentVisible,
            enter = fadeIn(animationSpec = tween(360)),
        ) {
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
        }
        AnimatedVisibility(
            visible = imageVisible,
            enter = fadeIn(animationSpec = tween(360)) +
                slideInVertically(animationSpec = tween(360)) { 12 },
        ) {
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

/**
 * Reminder shown on every redraw after the first in a session.
 * Divination tradition discourages re-rolling the same question;
 * this asks the user to confirm the new draw is for a different
 * question.
 *
 * Button layout per user request: 继续抽取 on the left, 取消 on the
 * right. The Material 3 AlertDialog renders `dismissButton` on the
 * left and `confirmButton` on the right, so the labels are placed
 * in those slots accordingly (semantically inverted from the usual
 * confirm/cancel mapping).
 */
@Composable
private fun RedrawConfirmDialog(
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = {
            Text(
                text = "提醒",
                fontFamily = FontFamily.Serif,
            )
        },
        text = {
            Text(
                text = "如果一件事连续两次进行测算，就会破坏其发展，导致最后的算卦结果变得很不准确。\n\n请确定你的问题是否不一致。",
                fontFamily = FontFamily.Serif,
                fontSize = 14.sp,
            )
        },
        confirmButton = {
            // Right-side slot: 取消.
            TextButton(onClick = onCancel) {
                Text("取消", fontFamily = FontFamily.Serif)
            }
        },
        dismissButton = {
            // Left-side slot: 继续抽取.
            TextButton(onClick = onConfirm) {
                Text("继续抽取", fontFamily = FontFamily.Serif)
            }
        },
    )
}