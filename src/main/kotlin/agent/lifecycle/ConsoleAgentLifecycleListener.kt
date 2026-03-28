package agent.lifecycle

class ConsoleAgentLifecycleListener(
    private val loadingIndicator: LoadingIndicator
) : AgentLifecycleListener {
    override fun onModelWarmupStarted() {
        loadingIndicator.start("Подготовка модели")
    }

    override fun onModelWarmupFinished() {
        loadingIndicator.stop()
    }

    override fun onContextCompressionStarted() {
        loadingIndicator.start("Сжимаем контекст")
    }

    override fun onContextCompressionFinished(stats: ContextCompressionStats) {
        loadingIndicator.stop()

        val message =
            if (stats.tokensBefore != null && stats.tokensAfter != null && stats.savedTokens != null) {
                "Контекст сжат: ${stats.tokensBefore} -> ${stats.tokensAfter} токенов, экономия ${stats.savedTokens}"
            } else {
                "Контекст сжат."
            }

        println(message)
    }
}
