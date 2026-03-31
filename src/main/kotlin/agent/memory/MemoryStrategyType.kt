package agent.memory

/**
 * Тип стратегии памяти с устойчивым строковым идентификатором для CLI и storage.
 *
 * @property id строковый идентификатор стратегии.
 */
enum class MemoryStrategyType(val id: String) {
    NO_COMPRESSION("no_compression"),
    SUMMARY_COMPRESSION("summary_compression"),
    SLIDING_WINDOW("sliding_window");

    companion object {
        /**
         * Ищет тип стратегии по устойчивому строковому идентификатору.
         */
        fun fromId(id: String): MemoryStrategyType? =
            entries.firstOrNull { it.id == id }
    }
}
