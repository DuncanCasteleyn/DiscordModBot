package be.duncanc.discordmodbot.discord

import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Answers
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.verify
import kotlin.test.assertEquals

@ExtendWith(MockitoExtension::class)
class JDALibHelperTest {
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private lateinit var textChannel: TextChannel

    @Test
    fun `bulk delete helper keeps the original ids intact`() {
        val messageIds = arrayListOf(1L, 2L)

        textChannel.limitLessBulkDeleteByIds(messageIds)

        verify(textChannel).deleteMessagesByIds(listOf("1", "2"))
        assertEquals(listOf(1L, 2L), messageIds)
    }
}
