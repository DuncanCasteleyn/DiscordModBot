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
        promptRegistry.remember(10L, 20L)

        verify(reviewPromptRepository).save(MemberGateReviewPrompt(userId = 10L, messageId = 20L))
    }

    @Test
    fun `forget returns message id and deletes prompt from redis repository`() {
        whenever(reviewPromptRepository.findById(10L)).thenReturn(Optional.of(MemberGateReviewPrompt(10L, 20L)))

        assertEquals(20L, promptRegistry.forget(10L))
        verify(reviewPromptRepository).deleteById(10L)
    }

    @Test
    fun `forget returns null when prompt does not exist`() {
        whenever(reviewPromptRepository.findById(10L)).thenReturn(Optional.empty())

        assertNull(promptRegistry.forget(10L))
    }
}
