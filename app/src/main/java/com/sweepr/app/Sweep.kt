package com.sweepr.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What a sweep should do to each app. */
enum class SweepMode(val forceStop: Boolean, val clearCache: Boolean, val label: String) {
    Both(true, true, "Both"),
    CacheOnly(false, true, "Clear cache"),
    StopOnly(true, false, "Force stop"),
}

/** [protect] = never force-stop (keyboard, launcher, phone…); its cache can still be cleared. */
data class Target(val packageName: String, val label: String, val protect: Boolean = false)

/** Outcome of one step on one app. */
enum class StepResult { Done, AlreadyClean, NotFound, Skipped }

data class AppOutcome(
    val target: Target,
    val stop: StepResult,
    val cache: StepResult,
)

sealed interface SweepState {
    data object Idle : SweepState

    data class Running(
        val mode: SweepMode,
        val index: Int,
        val total: Int,
        val current: String,
        /** Most recent finished apps, newest first, for the live ticker. */
        val log: List<AppOutcome> = emptyList(),
        /** Cache total measured before the sweep started, if usage access is on. */
        val startCache: Long? = null,
    ) : SweepState

    data class Finished(
        val mode: SweepMode,
        val outcomes: List<AppOutcome>,
        val cancelled: Boolean,
        val freedBytes: Long?,
    ) : SweepState {
        val stopped get() = outcomes.count { it.stop == StepResult.Done }
        val cleared get() = outcomes.count { it.cache == StepResult.Done }
        val problems get() = outcomes.filter {
            it.stop == StepResult.NotFound || it.cache == StepResult.NotFound
        }
    }
}

/** Shared state between the screen and the accessibility service. */
object Sweep {
    private val _state = MutableStateFlow<SweepState>(SweepState.Idle)
    val state: StateFlow<SweepState> = _state.asStateFlow()

    internal fun set(s: SweepState) {
        _state.value = s
    }

    fun dismiss() {
        if (_state.value is SweepState.Finished) _state.value = SweepState.Idle
    }
}
