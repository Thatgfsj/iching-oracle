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
 * Total duration of the draw animation (six lines appearing in
 * sequence). Tuned to land well under three seconds per the user's
 * "3秒之内解决" requirement; tweak in one place if needed.
 */
private const val DRAW_ANIMATION_MS: Long = 2_200L

class IChingViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = HexagramRepository.getInstance(application)

    private val _state = MutableStateFlow<OracleUiState>(OracleUiState.Initial)
    val state: StateFlow<OracleUiState> = _state.asStateFlow()

    /** All 64 hexagram names for the home-screen word cloud. */
    val allHexagramNames: List<String> = repository.allNames()

    private var fadeCounter = 0

    /**
     * Trigger a new draw. Picks the next hexagram immediately and
     * transitions to [OracleUiState.Drawing]; after a short delay
     * (so the UI can animate the six lines appearing in sequence)
     * it transitions to [OracleUiState.Loaded].
     *
     * Safe to call from any thread; state mutation is funneled
     * through the StateFlow which Compose reads on the main thread.
     */
    fun draw() {
        viewModelScope.launch {
            try {
                val hex = repository.drawRandom()
                _state.value = OracleUiState.Drawing(hex)
                delay(DRAW_ANIMATION_MS)
                fadeCounter += 1
                _state.value = OracleUiState.Loaded(hex, fadeCounter)
            } catch (t: Throwable) {
                _state.value = OracleUiState.Error(
                    t.message ?: "加载卦象数据失败"
                )
            }
        }
    }
}