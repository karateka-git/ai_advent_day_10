package ui.cli

import ui.UiEvent
import ui.UiEventSink

/**
 * CLI-реализация presentation-слоя, которая отвечает за весь пользовательский вывод.
 */
class CliRenderer(
    private val loadingIndicator: LoadingIndicator = LoadingIndicator(),
    private val tokenStatsFormatter: ConsoleTokenStatsFormatter = ConsoleTokenStatsFormatter()
) : UiEventSink {
    override fun emit(event: UiEvent) {
        when (event) {
            is UiEvent.SessionStarted -> {
                println("Чат готов. Введите 'exit' или 'quit', чтобы завершить работу.")
                println(
                    "Для просмотра моделей введите '${event.modelsCommand}'. " +
                        "Для переключения модели введите '${event.useCommand} <id>'."
                )
            }

            is UiEvent.MemoryStrategySelectionRequested -> {
                println("Выберите стратегию памяти перед стартом агента:")
                event.options.forEachIndexed { index, option ->
                    println("${index + 1}. ${option.displayName} - ${option.description}")
                }
            }

            is UiEvent.MemoryStrategySelectionPromptRequested -> {
                print("Введите номер стратегии [1-${event.optionsCount}]: ")
            }

            is UiEvent.MemoryStrategySelected -> {
                println("Выбрана стратегия: ${event.option.displayName}")
            }

            UiEvent.MemoryStrategySelectionRejected -> {
                println("Некорректный выбор. Попробуйте ещё раз.")
            }

            is UiEvent.AgentInfoAvailable -> {
                println("Агент: ${event.info.name}")
                println("Описание: ${event.info.description}")
                println("Модель: ${event.info.model}")
                println("Стратегия памяти: ${event.strategy.displayName}")
            }

            is UiEvent.ModelsAvailable -> {
                println(
                    buildString {
                        appendLine("Доступные модели:")
                        event.options.forEach { option ->
                            val marker = if (option.id == event.currentModelId) "*" else " "
                            append(marker)
                            append(" ")
                            append(option.id)
                            append(" - ")
                            append(option.displayName)
                            if (!option.isConfigured) {
                                append(" (недоступна: ${option.unavailableReason})")
                            }
                            appendLine()
                        }
                    }.trimEnd()
                )
            }

            is UiEvent.UserInputPrompt -> {
                print("${event.role.displayName}: ")
            }

            is UiEvent.AssistantResponseAvailable -> {
                println()
                println("${event.role.displayName}: ${event.content}")
                tokenStatsFormatter.formatResponse(event.tokenStats)?.let {
                    println()
                    println(it)
                }
                println()
            }

            is UiEvent.TokenPreviewAvailable -> {
                tokenStatsFormatter.formatPreview(event.tokenStats)?.let { preview ->
                    println()
                    println(preview)
                    println()
                }
            }

            UiEvent.ContextCleared -> println("Контекст очищен. Системное сообщение сохранено.")
            UiEvent.ModelChanged -> println("Текущая модель изменена.")
            is UiEvent.ModelSwitchFailed -> println("Не удалось переключить модель: ${event.details}")
            is UiEvent.RequestFailed -> println("Не удалось выполнить запрос: ${event.details}")
            UiEvent.SessionFinished -> println("Чат завершён.")
            UiEvent.ModelWarmupStarted -> loadingIndicator.start("Подготовка модели")
            UiEvent.ModelWarmupFinished -> loadingIndicator.stop()
            UiEvent.ModelRequestStarted -> loadingIndicator.start("Ассистент думает")
            UiEvent.ModelRequestFinished -> loadingIndicator.stop()
            UiEvent.ContextCompressionStarted -> loadingIndicator.start("Сжимаем контекст")

            is UiEvent.ContextCompressionFinished -> {
                loadingIndicator.stop()

                val message =
                    if (
                        event.stats.tokensBefore != null &&
                        event.stats.tokensAfter != null &&
                        event.stats.savedTokens != null
                    ) {
                        "Контекст сжат: ${event.stats.tokensBefore} -> ${event.stats.tokensAfter} токенов, " +
                            "экономия ${event.stats.savedTokens}"
                    } else {
                        "Контекст сжат."
                    }

                println(message)
            }
        }
    }
}
