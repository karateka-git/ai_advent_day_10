package devtools.comparison

import java.nio.file.Path

/**
 * Преобразует JSON-отчёт сравнения стратегий в краткую консольную сводку.
 */
object StrategyComparisonConsoleFormatter {
    fun format(
        report: StrategyComparisonReport,
        reportPath: Path
    ): String =
        buildString {
            appendLine("Сравнение стратегий завершено.")
            appendLine("Выбранная модель: ${report.selectedModelId}")
            appendLine("Провайдерная модель: ${report.providerModelName}")
            appendLine("JSON-отчёт: $reportPath")
            appendLine()
            appendLine("Пояснение к метрикам:")
            appendLine("  Локальные prompt-токены: локальная оценка размера основного запроса к модели.")
            appendLine("  Provider prompt-токены: prompt-токены по данным провайдера, включая внутренние служебные вызовы стратегии.")
            appendLine("  Provider completion-токены: токены, которые модель сгенерировала в ответах.")
            appendLine("  Provider total-токены: суммарные токены по данным провайдера.")
            appendLine("  Внутренние LLM-вызовы по шагам: сколько дополнительных обращений к модели стратегия делала внутри каждого шага помимо основного ответа.")
            appendLine("  Финальный ответ сохранён в JSON-отчёт: полный итоговый текст и детальные данные лежат в report.json.")
            appendLine()
            appendLine("Важно:")
            appendLine("  Для стратегии summary Provider prompt-токены могут быть заметно больше локальных, потому что сюда входят внутренние запросы на обновление summary.")
            appendLine()
            report.executions.forEach { execution ->
                appendLine("${execution.strategyId} (${execution.strategyDisplayName})")
                appendLine("  Шагов: ${execution.steps.size}")
                appendLine("  Локальные prompt-токены: ${execution.totalLocalPromptTokens ?: "н/д"}")
                appendLine("  Provider prompt-токены: ${execution.totalProviderPromptTokens ?: "н/д"}")
                appendLine("  Provider completion-токены: ${execution.totalProviderCompletionTokens ?: "н/д"}")
                appendLine("  Provider total-токены: ${execution.totalProviderTokens ?: "н/д"}")
                appendLine("  Внутренние LLM-вызовы по шагам: ${execution.steps.joinToString { it.internalModelCallCount.toString() }}")
                appendLine("  Финальный ответ сохранён в JSON-отчёт.")
                appendLine()
            }
            report.judgeResult?.let { judgeResult ->
                appendLine("--------------------------------------------------")
                appendLine("Финальный вывод judge")
                appendLine()
                appendLine("Модель-судья:")
                appendLine(judgeResult.judgeModelId.prependIndent("  "))
                appendLine()
                appendLine("Общий итог:")
                appendLine(judgeResult.summary.prependIndent("  "))
                appendLine()
                appendLine("Рейтинг:")
                appendLine(judgeResult.ranking.joinToString(" > ").prependIndent("  "))
                appendLine()
                appendLine("Оценки:")
                judgeResult.evaluations.forEach { evaluation ->
                    appendLine(
                        "  ${evaluation.strategyId}: качество=${evaluation.qualityScore}, " +
                            "стабильность=${evaluation.stabilityScore}, удобство=${evaluation.usabilityScore}"
                    )
                    appendLine("    Вердикт: ${evaluation.verdict}")
                    appendLine()
                }
                appendLine("--------------------------------------------------")
                appendLine()
            }
            appendLine("Judge payload включён в JSON-отчёт для следующего этапа.")
        }.trimEnd()
}
