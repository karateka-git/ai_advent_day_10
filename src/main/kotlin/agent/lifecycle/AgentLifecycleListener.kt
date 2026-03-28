package agent.lifecycle

interface AgentLifecycleListener {
    /**
     * Вызывается перед прогревом локального токенизатора для выбранной модели.
     */
    fun onModelWarmupStarted()

    /**
     * Вызывается после завершения прогрева локального токенизатора.
     */
    fun onModelWarmupFinished()

    /**
     * Вызывается перед запуском сжатия истории диалога.
     */
    fun onContextCompressionStarted()

    /**
     * Вызывается после завершения сжатия истории диалога.
     *
     * @param stats статистика токенов до и после сжатия
     */
    fun onContextCompressionFinished(stats: ContextCompressionStats)
}
