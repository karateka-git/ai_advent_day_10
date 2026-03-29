package ui.cli

import agent.core.AgentTokenStats

/**
 * Форматирует статистику токенов в удобный для чтения консольный вывод.
 */
class ConsoleTokenStatsFormatter {
    fun formatPreview(tokenStats: AgentTokenStats): String? {
        val lines = buildList {
            add("Оценка перед запросом:")
            tokenStats.userPromptTokens?.let { add("  текущее сообщение: $it") }
            tokenStats.historyTokens?.let { add("  история диалога: $it") }
            tokenStats.promptTokensLocal?.let { add("  полный запрос: $it") }
        }

        return if (lines.size == 1) {
            null
        } else {
            lines.joinToString(separator = System.lineSeparator())
        }
    }

    fun formatResponse(tokenStats: AgentTokenStats): String? {
        val usage = tokenStats.apiUsage ?: return null

        return listOf(
            "Токены ответа:",
            "  запрос: ${usage.promptTokens}",
            "  ответ: ${usage.completionTokens}",
            "  всего: ${usage.totalTokens}"
        ).joinToString(separator = System.lineSeparator())
    }
}
