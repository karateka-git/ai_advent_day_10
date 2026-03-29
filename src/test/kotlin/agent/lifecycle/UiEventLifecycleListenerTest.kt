package agent.lifecycle

import kotlin.test.Test
import kotlin.test.assertEquals
import ui.UiEvent
import ui.UiEventSink

class UiEventLifecycleListenerTest {
    @Test
    fun `publishes lifecycle callbacks as ui events`() {
        val sink = RecordingUiEventSink()
        val listener = UiEventLifecycleListener(sink)
        val stats = ContextCompressionStats(tokensBefore = 10, tokensAfter = 7)

        listener.onModelWarmupStarted()
        listener.onModelWarmupFinished()
        listener.onModelRequestStarted()
        listener.onModelRequestFinished()
        listener.onContextCompressionStarted()
        listener.onContextCompressionFinished(stats)

        assertEquals(
            listOf(
                UiEvent.ModelWarmupStarted,
                UiEvent.ModelWarmupFinished,
                UiEvent.ModelRequestStarted,
                UiEvent.ModelRequestFinished,
                UiEvent.ContextCompressionStarted,
                UiEvent.ContextCompressionFinished(stats)
            ),
            sink.events
        )
    }
}

private class RecordingUiEventSink : UiEventSink {
    val events: MutableList<UiEvent> = mutableListOf()

    override fun emit(event: UiEvent) {
        events += event
    }
}
