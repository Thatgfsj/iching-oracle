package com.thatgfsj.iching.ui.oracle

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.thatgfsj.iching.data.Hexagram
import com.thatgfsj.iching.data.HexagramRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalTime

/**
 * UI state for the I Ching oracle screen.
 *
 * The screen has four meaningful states:
 * - [Initial]:  no draw yet, home page with "点击抽取" button.
 * - [Drawing]:  a draw is in progress. The hexagram is already
 *   picked (so the UI can render the six lines as they fade in)
 *   but the screen is mid-animation.
 * - [Loaded]:   a hexagram has finished animating in; show it plus
 *   "再抽一签" and "问 AI".
 * - [Error]:    asset load failed; show error + retry button.
 *
 * `fadeKey` bumps on every successful draw so the Compose UI can
 * key the six-line stack on it; bumping the key triggers Compose
 * to replay the fade-in animation. See `IChingOracleScreen` for
 * the actual `LaunchedEffect(fadeKey)` plumbing.
 */
sealed interface OracleUiState {
    data object Initial : OracleUiState
    data class Drawing(val hexagram: Hexagram) : OracleUiState
    data class Loaded(val hexagram: Hexagram, val fadeKey: Int) : OracleUiState
    data class Error(val message: String) : OracleUiState
}

/**
 * Total duration of the draw animation. Must match the schedule
 * in `IChingOracleScreen.DrawingView`:
 *   6 lines × 240 ms = 1440 ms (lines)
 *   + 240 ms (gap before name)
 *   + 280 ms (gap before judgment)
 *   + ~360 ms (image fade-in tween)
 *   ≈ 2.9 s total. We round up to 3100 ms so the image fade-in
 * finishes before we swap to Loaded, no abrupt cut.
 */
private const val DRAW_ANIMATION_MS: Long = 3_100L

class IChingViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = HexagramRepository.getInstance(application)

    private val _state = MutableStateFlow<OracleUiState>(OracleUiState.Initial)
    val state: StateFlow<OracleUiState> = _state.asStateFlow()

    /**
     * A pending hexagram awaiting the user's confirmation to redraw.
     * Non-null means the redraw-confirm dialog should be visible.
     * Held separately from [state] so the main screen content stays
     * on the previous Loaded hexagram (no flicker) while the dialog
     * is up.
     */
    private val _pendingRedraw = MutableStateFlow<Hexagram?>(null)
    val pendingRedraw: StateFlow<Hexagram?> = _pendingRedraw.asStateFlow()

    /**
     * True when the user has tried to draw during the 子时 (Zi hour,
     * 23:00–01:00) and the block-divination dialog should be up.
     * The dialog has a single "确认并退出" action that finishes the
     * activity; this flag clears itself once the user confirms.
     */
    private val _ziHourBlocked = MutableStateFlow(false)
    val ziHourBlocked: StateFlow<Boolean> = _ziHourBlocked.asStateFlow()

    /** All 64 hexagram names for the home-screen word cloud. */
    val allHexagramNames: List<String> = repository.allNames()

    private var fadeCounter = 0

    /**
     * Number of draws the user has initiated in this app process.
     * Reset on cold start. The first draw skips the confirm
     * dialog; every subsequent draw requires confirmation.
     */
    private var sessionDrawCount = 0

    /**
     * Trigger a new draw. On the first call in this session it
     * goes straight into the animation; on subsequent calls it
     * parks the picked hexagram in [pendingRedraw] and waits for
     * the user to confirm via [confirmRedraw] / [cancelRedraw].
     *
     * If the current local time falls in the 子时 (Zi hour,
     * 23:00–01:00), the draw is refused and [ziHourBlocked] is
     * raised instead — the UI shows the "子时不算卦" dialog and
     * the only escape is "确认并退出".
     */
    fun draw() {
        if (isZiHour()) {
            _ziHourBlocked.value = true
            return
        }
        viewModelScope.launch {
            try {
                val hex = repository.drawRandom()
                sessionDrawCount += 1
                if (sessionDrawCount == 1) {
                    runDrawAnimation(hex)
                } else {
                    // Park the new hexagram; the UI will show the
                    // confirm dialog while we keep the previous
                    // Loaded state intact underneath.
                    _pendingRedraw.value = hex
                }
            } catch (t: Throwable) {
                _state.value = OracleUiState.Error(
                    t.message ?: "加载卦象数据失败"
                )
            }
        }
    }

    /** Acknowledge the zi-hour block. The Activity is expected
     *  to call finish() right after this. */
    fun acknowledgeZiHourBlock() {
        _ziHourBlocked.value = false
    }

    private fun isZiHour(): Boolean {
        val hour = LocalTime.now().hour
        return hour == 23 || hour == 0
    }

    /** User confirmed the redraw — animate the parked hexagram in. */
    fun confirmRedraw() {
        val pending = _pendingRedraw.value ?: return
        _pendingRedraw.value = null
        viewModelScope.launch { runDrawAnimation(pending) }
    }

    /** User backed out of the redraw — drop the parked hexagram. */
    fun cancelRedraw() {
        _pendingRedraw.value = null
    }

    private suspend fun runDrawAnimation(hex: Hexagram) {
        _state.value = OracleUiState.Drawing(hex)
        delay(DRAW_ANIMATION_MS)
        fadeCounter += 1
        _state.value = OracleUiState.Loaded(hex, fadeCounter)
    }
}