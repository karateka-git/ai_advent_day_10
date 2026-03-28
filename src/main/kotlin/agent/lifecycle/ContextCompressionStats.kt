package agent.lifecycle

data class ContextCompressionStats(
    val tokensBefore: Int?,
    val tokensAfter: Int?
) {
    val savedTokens: Int?
        get() =
            if (tokensBefore == null || tokensAfter == null) {
                null
            } else {
                tokensBefore - tokensAfter
            }
}
