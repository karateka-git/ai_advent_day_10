package agent.storage

import agent.storage.model.ConversationMemoryState
import agent.storage.model.StoredMemoryMetadata
import agent.storage.model.StoredMessage
import agent.storage.model.StoredSummary
import java.nio.file.Files
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals

class JsonConversationStoreTest {
    @Test
    fun `load returns empty list when file does not exist`() {
        val tempDir = Files.createTempDirectory("conversation-store-test")
        val store = JsonConversationStore(tempDir.resolve("conversation.json"))

        assertEquals(emptyList(), store.load())
        assertEquals(ConversationMemoryState(), store.loadState())
    }

    @Test
    fun `save and load preserve messages`() {
        val tempDir = Files.createTempDirectory("conversation-store-test")
        val store = JsonConversationStore(tempDir.resolve("conversation.json"))
        val messages = listOf(
            StoredMessage(role = "system", content = "Ты помощник."),
            StoredMessage(role = "user", content = "Привет"),
            StoredMessage(role = "assistant", content = "Здравствуйте")
        )

        store.save(messages)

        assertEquals(messages, store.load())
    }

    @Test
    fun `loadState reads legacy conversation history format`() {
        val tempDir = Files.createTempDirectory("conversation-store-test")
        val storagePath = tempDir.resolve("conversation.json")
        storagePath.writeText(
            """
            {
              "messages": [
                { "role": "system", "content": "Ты помощник." },
                { "role": "user", "content": "Привет" }
              ]
            }
            """.trimIndent()
        )
        val store = JsonConversationStore(storagePath)

        assertEquals(
            ConversationMemoryState(
                messages = listOf(
                    StoredMessage(role = "system", content = "Ты помощник."),
                    StoredMessage(role = "user", content = "Привет")
                )
            ),
            store.loadState()
        )
    }

    @Test
    fun `saveState and loadState preserve summary and metadata`() {
        val tempDir = Files.createTempDirectory("conversation-store-test")
        val store = JsonConversationStore(tempDir.resolve("conversation.json"))
        val state = ConversationMemoryState(
            messages = listOf(
                StoredMessage(role = "system", content = "Ты помощник."),
                StoredMessage(role = "user", content = "Привет")
            ),
            summary = StoredSummary(
                content = "Пользователь поздоровался.",
                coveredMessagesCount = 2
            ),
            metadata = StoredMemoryMetadata(strategyId = "no_compression")
        )

        store.saveState(state)

        assertEquals(state, store.loadState())
    }
}
