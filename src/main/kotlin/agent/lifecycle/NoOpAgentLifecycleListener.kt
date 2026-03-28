package agent.lifecycle

object NoOpAgentLifecycleListener : AgentLifecycleListener {
    override fun onModelWarmupStarted() = Unit

    override fun onModelWarmupFinished() = Unit

    override fun onContextCompressionStarted() = Unit

    override fun onContextCompressionFinished(stats: ContextCompressionStats) = Unit
}
