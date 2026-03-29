import kotlin.test.Test
import kotlin.test.assertEquals

class CliCommandParserTest {
    private val parser = CliCommandParser()

    @Test
    fun `parses empty input`() {
        assertEquals(CliCommand.Empty, parser.parse(""))
    }

    @Test
    fun `parses clear command`() {
        assertEquals(CliCommand.Clear, parser.parse("clear"))
    }

    @Test
    fun `parses models command`() {
        assertEquals(CliCommand.ShowModels, parser.parse("models"))
    }

    @Test
    fun `parses use command`() {
        assertEquals(
            CliCommand.SwitchModel("huggingface"),
            parser.parse("use huggingface")
        )
    }

    @Test
    fun `parses exit aliases`() {
        assertEquals(CliCommand.Exit, parser.parse("exit"))
        assertEquals(CliCommand.Exit, parser.parse("quit"))
    }

    @Test
    fun `treats unknown input as user prompt`() {
        assertEquals(
            CliCommand.UserPrompt("Расскажи историю"),
            parser.parse("Расскажи историю")
        )
    }
}
