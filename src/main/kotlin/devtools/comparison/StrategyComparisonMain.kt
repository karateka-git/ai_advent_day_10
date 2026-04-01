package devtools.comparison

import agent.memory.strategy.MemoryStrategyFactory
import agent.memory.strategy.MemoryStrategyType
import bootstrap.ApplicationBootstrap
import java.io.FileDescriptor
import java.io.FileOutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.serialization.json.Json

private const val COMPARISON_TEMPERATURE = 0.0
private const val COMPARISON_STEPS_PROPERTY = "comparison.steps"
private const val COMPARISON_JUDGE_PROPERTY = "comparison.judge"
private const val COMPARISON_STRATEGIES_PROPERTY = "comparison.strategies"
private val reportJson = Json {
    prettyPrint = true
}

/**
 * Точка входа dev-инструмента, который прогоняет один сценарий на нескольких
 * стратегиях памяти и сохраняет JSON-отчёт для последующего сравнения.
 */
fun main() {
    configureUtf8Console()

    val runtime = ApplicationBootstrap.createRuntime(
        temperature = COMPARISON_TEMPERATURE
    )
    val selectedModelId = runtime.selectedModelId
    val languageModel = runtime.languageModel

    ApplicationBootstrap.warmUpTokenCounter(languageModel)

    val linearScenario = defaultTechnicalSpecificationScenario()
        .limitedToConfiguredSteps()
    val branchingScenario = defaultBranchingScenario()
    val judgeEnabled = System.getProperty(COMPARISON_JUDGE_PROPERTY)?.toBooleanStrictOrNull() == true
    val reportPath = Path.of("build", "reports", "strategy-comparison", "report.json")
    val strategies = selectedStrategies()
    val service = StrategyComparisonService()
    val linearExecutor = DefaultStrategyConversationExecutor(
        baseLanguageModel = languageModel,
        stateDirectory = Path.of("build", "strategy-comparison", "state"),
        onStepStarted = { option, stepNumber, totalSteps ->
            println("[${option.id}] шаг $stepNumber/$totalSteps...")
        },
        onStepFinished = { option, step, totalSteps ->
            println(
                "[${option.id}] шаг ${step.stepNumber}/$totalSteps завершён: " +
                    "вызовов модели=${step.modelCallCount}, prompt-токены=${step.promptTokensLocal ?: "н/д"}"
            )
        }
    )
    val branchingExecutor = BranchingStrategyConversationExecutor(
        baseLanguageModel = languageModel,
        stateDirectory = Path.of("build", "strategy-comparison", "state"),
        onStepStarted = { phaseLabel, stepNumber, totalSteps ->
            println("[branching:$phaseLabel] шаг $stepNumber/$totalSteps...")
        },
        onStepFinished = { phaseLabel, step, totalSteps ->
            println(
                "[branching:$phaseLabel] шаг ${step.stepNumber}/$totalSteps завершён: " +
                    "вызовов модели=${step.modelCallCount}, prompt-токены=${step.promptTokensLocal ?: "н/д"}"
            )
        }
    )

    val executions = mutableListOf<StrategyExecutionReport>()
    val totalStrategies = strategies.size
    var strategyIndex = 0

    strategies.filter { it.type != MemoryStrategyType.BRANCHING }.forEach { option ->
        strategyIndex += 1
        println("Стратегия $strategyIndex/$totalStrategies: ${option.displayName} (${option.id})")
        val execution = linearExecutor.execute(option, linearScenario)
        executions += execution
        saveReport(
            reportPath,
            service.createReport(
                comparisonName = combinedComparisonName(linearScenario, branchingScenario),
                selectedModelId = selectedModelId,
                providerModelName = languageModel.info.model,
                executions = executions,
                judgeInput = buildUnifiedJudgeInput(linearScenario, branchingScenario, executions)
            )
        )
        println("Стратегия ${option.id} завершена.")
        println("Промежуточный отчёт сохранён (${executions.size} стратегий готово).")
        println("Длина финального ответа=${execution.finalResponse.length} символов.")
        println()
    }

    strategies.firstOrNull { it.type == MemoryStrategyType.BRANCHING }?.let { option ->
        strategyIndex += 1
        println("Стратегия $strategyIndex/$totalStrategies: ${option.displayName} (${option.id})")
        val execution = branchingExecutor.execute(option, branchingScenario)
        executions += execution
        saveReport(
            reportPath,
            service.createReport(
                comparisonName = combinedComparisonName(linearScenario, branchingScenario),
                selectedModelId = selectedModelId,
                providerModelName = languageModel.info.model,
                executions = executions,
                judgeInput = buildUnifiedJudgeInput(linearScenario, branchingScenario, executions)
            )
        )
        println("Стратегия ${option.id} завершена.")
        println("Промежуточный отчёт сохранён (${executions.size} стратегий готово).")
        println("Длина финального ответа=${execution.finalResponse.length} символов.")
        println()
    }

    val report = service.createReport(
        comparisonName = combinedComparisonName(linearScenario, branchingScenario),
        selectedModelId = selectedModelId,
        providerModelName = languageModel.info.model,
        executions = executions,
        judgeInput = buildUnifiedJudgeInput(linearScenario, branchingScenario, executions)
    )

    val finalReport =
        if (judgeEnabled) {
            println()
            println("Запускаем дополнительную judge-оценку...")
            val judgeResult = StrategyComparisonJudgeService().evaluate(
                judgeModelId = selectedModelId,
                judgeInput = report.judgeInput,
                languageModel = languageModel
            )
            service.createReport(
                comparisonName = report.scenarioName,
                selectedModelId = selectedModelId,
                providerModelName = languageModel.info.model,
                executions = report.executions,
                judgeInput = report.judgeInput,
                judgeResult = judgeResult
            )
        } else {
            report
        }

    saveReport(reportPath, finalReport)
    println()
    println(StrategyComparisonConsoleFormatter.format(finalReport, reportPath))
}

private fun combinedComparisonName(
    linearScenario: StrategyComparisonScenario,
    branchingScenario: BranchingComparisonScenario
): String =
    "Сравнение стратегий памяти: ${linearScenario.name} + ${branchingScenario.name}"

private fun buildUnifiedJudgeInput(
    linearScenario: StrategyComparisonScenario,
    branchingScenario: BranchingComparisonScenario,
    executions: List<StrategyExecutionReport>
): StrategyComparisonJudgeInput =
    StrategyComparisonJudgeInput(
        comparisonName = combinedComparisonName(linearScenario, branchingScenario),
        prompts = linearScenario.prompts,
        candidates = executions.map { execution ->
            StrategyJudgeCandidate(
                strategyId = execution.strategyId,
                strategyDisplayName = execution.strategyDisplayName,
                scenarioDescription =
                    if (execution.branchExecutions.isNotEmpty()) {
                        buildBranchingScenarioDescription(branchingScenario)
                    } else {
                        buildLinearScenarioDescription(linearScenario)
                    },
                finalResponse = execution.finalResponse,
                totalLocalPromptTokens = execution.totalLocalPromptTokens,
                totalProviderTokens = execution.totalProviderTokens
            )
        }
    )

/**
 * Возвращает список стратегий для текущего прогона, опционально ограниченный системным свойством.
 */
private fun selectedStrategies() =
    MemoryStrategyFactory.availableOptions().let { availableOptions ->
        val selectedIds = System.getProperty(COMPARISON_STRATEGIES_PROPERTY)
            ?.split(',')
            ?.map(String::trim)
            ?.filter(String::isNotEmpty)
            ?.toSet()
            ?: return@let availableOptions

        availableOptions.filter { it.id in selectedIds }
            .also { selectedOptions ->
                require(selectedOptions.isNotEmpty()) {
                    "Не удалось выбрать ни одну стратегию для сравнения. " +
                        "Проверьте значение свойства $COMPARISON_STRATEGIES_PROPERTY."
                }
            }
    }

/**
 * Принудительно настраивает UTF-8 для stdout/stderr внутри comparison runner.
 */
private fun configureUtf8Console() {
    val utf8Out = PrintStream(FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8)
    val utf8Err = PrintStream(FileOutputStream(FileDescriptor.err), true, StandardCharsets.UTF_8)
    System.setOut(utf8Out)
    System.setErr(utf8Err)
}

/**
 * Возвращает сценарий по умолчанию для сравнения стратегий на задаче сбора ТЗ.
 */
private fun defaultTechnicalSpecificationScenario(): StrategyComparisonScenario =
    StrategyComparisonScenario(
        name = "Собираем ТЗ на чат-бота",
        prompts = listOf(
            "Помоги собрать ТЗ на Telegram-бота для школы английского языка.",
            "Цель бота: записывать учеников на пробный урок и отвечать на частые вопросы.",
            "Бот должен работать на русском языке и без голосовых сообщений.",
            "Интеграции нужны с Google Sheets и Telegram, но без CRM на первом этапе.",
            "Бюджет на MVP до 120 тысяч рублей.",
            "Срок запуска MVP две недели.",
            "Важно, чтобы администратор мог менять FAQ без участия разработчика.",
            "Пользователи должны выбирать удобный слот из доступного расписания.",
            "Нужна аналитика: сколько заявок пришло и сколько дошло до пробного урока.",
            "Не добавляем оплату внутри бота на первом этапе.",
            "Сделай список открытых вопросов, которые ещё нужно уточнить у заказчика.",
            "Теперь собери итоговое краткое ТЗ списком: цель, функции, ограничения, интеграции, сроки и открытые вопросы."
        )
    )

private fun defaultBranchingScenario(): BranchingComparisonScenario =
    BranchingComparisonScenario(
        name = "Сравниваем две альтернативные реализации ТЗ",
        sharedPrompts = listOf(
            "Помоги собрать ТЗ на Telegram-бота для школы английского языка.",
            "Цель бота: записывать учеников на пробный урок и отвечать на частые вопросы.",
            "Бот должен работать на русском языке и без голосовых сообщений.",
            "Интеграции нужны с Google Sheets и Telegram, но без CRM на первом этапе."
        ),
        checkpointName = "base_tz",
        firstBranchName = "mvp_light",
        firstBranchPrompts = listOf(
            "Ветка A: делаем максимально лёгкий MVP без аналитики и без личного кабинета. Собери краткое ТЗ.",
            "Ветка A: перечисли только обязательные функции для запуска за 2 недели."
        ),
        secondBranchName = "analytics_first",
        secondBranchPrompts = listOf(
            "Ветка B: добавляем аналитику заявок и конверсии уже в первой версии. Собери краткое ТЗ.",
            "Ветка B: перечисли обязательные функции для запуска с аналитикой."
        )
    )

/**
 * Возвращает сценарий, ограниченный числом шагов из системного свойства, если оно задано.
 */
private fun StrategyComparisonScenario.limitedToConfiguredSteps(): StrategyComparisonScenario {
    val configuredSteps = System.getProperty(COMPARISON_STEPS_PROPERTY)?.toIntOrNull()
        ?: return this
    if (configuredSteps <= 0) {
        return this
    }

    return copy(prompts = prompts.take(configuredSteps))
}

private fun buildLinearScenarioDescription(scenario: StrategyComparisonScenario): String =
    buildString {
        append("Линейный сценарий '${scenario.name}'. ")
        append("Сообщения пользователя: ")
        append(scenario.prompts.joinToString(" | "))
    }

private fun buildBranchingScenarioDescription(scenario: BranchingComparisonScenario): String =
    buildString {
        append("Сценарий ветвления '${scenario.name}'. ")
        append("Общая часть: ")
        append(scenario.sharedPrompts.joinToString(" | "))
        append(". ")
        append("После checkpoint '${scenario.checkpointName}' есть ветка '${scenario.firstBranchName}': ")
        append(scenario.firstBranchPrompts.joinToString(" | "))
        append(". ")
        append("И ветка '${scenario.secondBranchName}': ")
        append(scenario.secondBranchPrompts.joinToString(" | "))
    }

/**
 * Сохраняет сериализованный отчёт сравнения стратегий в файл.
 */
private fun saveReport(
    reportPath: Path,
    report: StrategyComparisonReport
) {
    Files.createDirectories(reportPath.parent)
    Files.writeString(
        reportPath,
        reportJson.encodeToString(report),
        StandardCharsets.UTF_8
    )
}


