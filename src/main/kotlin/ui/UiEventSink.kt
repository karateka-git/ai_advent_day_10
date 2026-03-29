package ui

/**
 * Принимает UI-события, которые приложение хочет отобразить пользователю.
 */
interface UiEventSink {
    /**
     * Передаёт событие в presentation-слой.
     */
    fun emit(event: UiEvent)
}
