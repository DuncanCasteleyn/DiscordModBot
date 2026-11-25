package be.duncanc.discordmodbot.moderation.persistence

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.test.context.TestConstructor

@DataJpaTest
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class ChannelLockdownRepositoryTest(
    private val channelLockdownRepository: ChannelLockdownRepository
) {
    @Test
    fun `saved overrides can be found per channel and deleted per channel`() {
        channelLockdownRepository.save(ChannelLockdown(1L, 10L, LockdownTargetType.ROLE, 1L, 5L, 6L, true))
        channelLockdownRepository.save(ChannelLockdown(1L, 10L, LockdownTargetType.MEMBER, 1L, 0L, 0L, false))
        channelLockdownRepository.save(ChannelLockdown(1L, 11L, LockdownTargetType.ROLE, 1L, 0L, 0L, false))
        channelLockdownRepository.flush()

        val saved = channelLockdownRepository.findAllByGuildIdAndChannelId(1L, 10L)

        assertEquals(2, saved.size)
        val role = saved.single { it.targetType == LockdownTargetType.ROLE }
        assertEquals(5L, role.previousAllow)
        assertEquals(6L, role.previousDeny)
        assertTrue(role.existed)

        assertTrue(channelLockdownRepository.existsByGuildIdAndChannelId(1L, 10L))
        channelLockdownRepository.deleteAllByGuildIdAndChannelId(1L, 10L)
        channelLockdownRepository.flush()
        assertFalse(channelLockdownRepository.existsByGuildIdAndChannelId(1L, 10L))
        assertTrue(channelLockdownRepository.existsByGuildIdAndChannelId(1L, 11L))
    }
}
