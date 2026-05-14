package be.duncanc.discordmodbot.logging

import be.duncanc.discordmodbot.logging.persistence.MessageDeleteAuditStateEntry
import be.duncanc.discordmodbot.logging.persistence.MessageDeleteAuditStateRepository
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
class MessageDeleteAuditStateRegistryTest {
    @Mock
    private lateinit var messageDeleteAuditStateRepository: MessageDeleteAuditStateRepository

    private lateinit var messageDeleteAuditStateRegistry: MessageDeleteAuditStateRegistry

    @BeforeEach
    fun setUp() {
        messageDeleteAuditStateRegistry = MessageDeleteAuditStateRegistry(messageDeleteAuditStateRepository)
    }

    @Test
    fun `remember stores audit state in redis repository`() {
        messageDeleteAuditStateRegistry.remember(
            guildId = 1L,
            channelId = 2L,
            targetUserId = 3L,
            state = MessageDeleteAuditState(consumedCounts = mapOf(42L to 1))
        )

        verify(messageDeleteAuditStateRepository).save(
            MessageDeleteAuditStateEntry(
                id = MessageDeleteAuditStateEntry.createId(1L, 2L, 3L),
                guildId = 1L,
                channelId = 2L,
                targetUserId = 3L,
                consumedCounts = mapOf(42L to 1)
            )
        )
    }

    @Test
    fun `remember deletes empty audit state`() {
        messageDeleteAuditStateRegistry.remember(
            guildId = 1L,
            channelId = 2L,
            targetUserId = 3L,
            state = MessageDeleteAuditState(consumedCounts = emptyMap())
        )

        verify(messageDeleteAuditStateRepository).deleteById(MessageDeleteAuditStateEntry.createId(1L, 2L, 3L))
    }

    @Test
    fun `get recreates state from redis entry`() {
        whenever(messageDeleteAuditStateRepository.findById(MessageDeleteAuditStateEntry.createId(1L, 2L, 3L))).thenReturn(
            Optional.of(
                MessageDeleteAuditStateEntry(
                    id = MessageDeleteAuditStateEntry.createId(1L, 2L, 3L),
                    guildId = 1L,
                    channelId = 2L,
                    targetUserId = 3L,
                    consumedCounts = mapOf(42L to 1, 43L to 2)
                )
            )
        )

        val state = messageDeleteAuditStateRegistry.get(1L, 2L, 3L)

        assertEquals(MessageDeleteAuditState(consumedCounts = mapOf(42L to 1, 43L to 2)), state)
    }

    @Test
    fun `get returns null when redis state is missing`() {
        whenever(messageDeleteAuditStateRepository.findById(MessageDeleteAuditStateEntry.createId(1L, 2L, 3L))).thenReturn(
            Optional.empty()
        )

        assertNull(messageDeleteAuditStateRegistry.get(1L, 2L, 3L))
    }
}
