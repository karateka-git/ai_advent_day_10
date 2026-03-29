package agent.lifecycle

import ui.UiEvent
import ui.UiEventSink

/**
 * Адаптер между lifecycle-событиями приложения и presentation-слоем на основе UI-событий.
 */
class UiEventLifecycleListener(
    private val uiEventSink: UiEventSink
) : AgentLifecycleListener {
    override fun onModelWarmupStarted() {
        uiEventSink.emit(UiEvent.ModelWarmupStarted)
    }

    override fun onModelWarmupFinished() {
        uiEventSink.emit(UiEvent.ModelWarmupFinished)
    }

    override fun onModelRequestStarted() {
        uiEventSink.emit(UiEvent.ModelRequestStarted)
    }

    override fun onModelRequestFinished() {
        uiEventSink.emit(UiEvent.ModelRequestFinished)
    }

    override fun onContextCompressionStarted() {
        uiEventSink.emit(UiEvent.ContextCompressionStarted)
    }

    override fun onContextCompressionFinished(stats: ContextCompressionStats) {
        uiEventSink.emit(UiEvent.ContextCompressionFinished(stats))
    }
}
