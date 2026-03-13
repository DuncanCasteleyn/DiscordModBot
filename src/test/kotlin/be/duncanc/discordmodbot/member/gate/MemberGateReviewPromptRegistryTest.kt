package be.duncanc.discordmodbot.member.gate

import be.duncanc.discordmodbot.member.gate.persistence.MemberGateReviewPrompt
import be.duncanc.discordmodbot.member.gate.persistence.MemberGateReviewPromptRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.*

@ExtendWith(MockitoExtension::class)
class MemberGateReviewPromptRegistryTest {
    @Mock
    private lateinit var reviewPromptRepository: MemberGateReviewPromptRepository

    private lateinit var promptRegistry: MemberGateReviewPromptRegistry

    @BeforeEach
    fun setUp() {
        promptRegistry = MemberGateReviewPromptRegistry(reviewPromptRepository)
    }

    @Test
    fun `remember stores prompt in redis repository`() {
        promptRegistry.remember(1L, 10L, 20L)

        verify(reviewPromptRepository).save(
            MemberGateReviewPrompt(
                id = MemberGateReviewPrompt.createId(1L, 10L),
                guildId = 1L,
                userId = 10L,
                messageId = 20L
            )
        )
    }

    @Test
    fun `forget returns message id and deletes prompt from redis repository`() {
        whenever(reviewPromptRepository.findById("1:10")).thenReturn(
            Optional.of(MemberGateReviewPrompt(id = "1:10", guildId = 1L, userId = 10L, messageId = 20L))
        )

        assertEquals(20L, promptRegistry.forget(1L, 10L))
        verify(reviewPromptRepository).deleteById("1:10")
    }

    @Test
    fun `forget returns null when prompt does not exist`() {
        whenever(reviewPromptRepository.findById("1:10")).thenReturn(Optional.empty())

        assertNull(promptRegistry.forget(1L, 10L))
    }

    @Test
    fun `remember stores the same user separately for different guilds`() {
        promptRegistry.remember(1L, 10L, 20L)
        promptRegistry.remember(2L, 10L, 30L)

        val captor = argumentCaptor<MemberGateReviewPrompt>()
        verify(reviewPromptRepository, org.mockito.kotlin.times(2)).save(captor.capture())
        assertEquals(listOf("1:10", "2:10"), captor.allValues.map { it.id })
    }
}
