package devtools.comparison

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import llm.core.LanguageModel
import llm.core.model.ChatMessage
import llm.core.model.ChatRole

/**
 * Выполняет дополнительное LLM-судейство по уже собранному comparison report.
 */
class StrategyComparisonJudgeService(
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    /**
     * Запрашивает у LLM качественную оценку стратегий по итогам сравнения.
     */
    fun evaluate(
        judgeModelId: String,
        judgeInput: StrategyComparisonJudgeInput,
        languageModel: LanguageModel
    ): StrategyComparisonJudgeResult {
        val response = languageModel.complete(
            listOf(
                ChatMessage(ChatRole.SYSTEM, buildSystemPrompt()),
                ChatMessage(ChatRole.USER, buildUserPrompt(judgeInput))
            )
        )

        val payload = extractJsonObject(response.content)
        val parsed = json.decodeFromString<JudgeResponsePayload>(payload)

        return StrategyComparisonJudgeResult(
            judgeModelId = judgeModelId,
            summary = parsed.summary,
            ranking = parsed.ranking,
            evaluations = parsed.evaluations.map { evaluation ->
                StrategyJudgeEvaluation(
                    strategyId = evaluation.strategyId,
                    qualityScore = evaluation.qualityScore,
                    stabilityScore = evaluation.stabilityScore,
                    usabilityScore = evaluation.usabilityScore,
                    strengths = evaluation.strengths,
                    weaknesses = evaluation.weaknesses,
                    verdict = evaluation.verdict
                )
            }
        )
    }

    /**
     * Формирует user-prompt для LLM judge.
     */
    fun buildUserPrompt(judgeInput: StrategyComparisonJudgeInput): String =
        buildString {
            appendLine("Сравни стратегии памяти по одному и тому же сценарию.")
            appendLine("Оцени каждую стратегию по критериям:")
            appendLine("- qualityScore: качество финального ответа")
            appendLine("- stabilityScore: не теряет ли стратегия важные детали и ограничения")
            appendLine("- usabilityScore: насколько ответ удобен и полезен для пользователя")
            appendLine()
            appendLine("Правила:")
            appendLine("- Используй шкалу от 1 до 10.")
            appendLine("- Учитывай сценарий, финальные ответы и токены.")
            appendLine("- Не считай более дорогую стратегию автоматически худшей, если она даёт лучший ответ.")
            appendLine("- Верни только JSON без markdown, пояснений и кодовых блоков.")
            appendLine()
            appendLine("Структура ответа:")
            appendLine(
                """{"summary":"...","ranking":["strategy_id"],"evaluations":[{"strategyId":"...","qualityScore":1,"stabilityScore":1,"usabilityScore":1,"strengths":["..."],"weaknesses":["..."],"verdict":"..."}]}"""
            )
            appendLine()
            appendLine("Данные для оценки:")
            appendLine(json.encodeToString(StrategyComparisonJudgeInput.serializer(), judgeInput))
        }

    private fun buildSystemPrompt(): String =
        "Ты выступаешь как нейтральный judge для сравнения стратегий памяти. Возвращай только валидный JSON."

    /**
     * Извлекает JSON-объект из ответа модели, даже если она всё же обернула его в markdown.
     */
    fun extractJsonObject(rawContent: String): String {
        val fencedJson = Regex("```json\\s*(\\{[\\s\\S]*})\\s*```").find(rawContent)
        if (fencedJson != null) {
            return fencedJson.groupValues[1]
        }

        val fenced = Regex("```\\s*(\\{[\\s\\S]*})\\s*```").find(rawContent)
        if (fenced != null) {
            return fenced.groupValues[1]
        }

        val firstBrace = rawContent.indexOf('{')
        val lastBrace = rawContent.lastIndexOf('}')
        require(firstBrace >= 0 && lastBrace > firstBrace) {
            "LLM judge не вернул JSON-объект."
        }

        return rawContent.substring(firstBrace, lastBrace + 1)
    }
}

@Serializable
private data class JudgeResponsePayload(
    val summary: String,
    val ranking: List<String>,
    val evaluations: List<JudgeEvaluationPayload>
)

@Serializable
private data class JudgeEvaluationPayload(
    val strategyId: String,
    val qualityScore: Int,
    val stabilityScore: Int,
    val usabilityScore: Int,
    val strengths: List<String>,
    val weaknesses: List<String>,
    val verdict: String
)
