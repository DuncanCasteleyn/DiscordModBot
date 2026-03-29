package be.duncanc.discordmodbot.member.gate

import be.duncanc.discordmodbot.member.gate.persistence.GuildMemberGate
import be.duncanc.discordmodbot.member.gate.persistence.GuildMemberGateRepository
import be.duncanc.discordmodbot.member.gate.persistence.JoinModalQuestionRepository
import be.duncanc.discordmodbot.member.gate.persistence.MemberGateQuestionRepository
import net.dv8tion.jda.api.JDA
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.*

@ExtendWith(MockitoExtension::class)
class MemberGateServiceTest {
    @Mock
    private lateinit var guildMemberGateRepository: GuildMemberGateRepository

    @Mock
    private lateinit var jda: JDA

    @Mock
    private lateinit var memberGateQuestionRepository: MemberGateQuestionRepository

    @Mock
    private lateinit var joinModalQuestionRepository: JoinModalQuestionRepository

    @Test
    fun `set purge time creates a guild config when one does not exist`() {
        val service = createService()
        whenever(guildMemberGateRepository.findById(1L)).thenReturn(Optional.empty())

        service.setPurgeTime(1L, 24L)

        val savedGate = argumentCaptor<GuildMemberGate>()
        verify(guildMemberGateRepository).save(savedGate.capture())
        kotlin.test.assertEquals(1L, savedGate.firstValue.guildId)
        kotlin.test.assertEquals(24L, savedGate.firstValue.removeTimeHours)
    }

    @Test
    fun `set reminder time creates a guild config when one does not exist`() {
        val service = createService()
        whenever(guildMemberGateRepository.findById(1L)).thenReturn(Optional.empty())

        service.setReminderTime(1L, 6L)

        val savedGate = argumentCaptor<GuildMemberGate>()
        verify(guildMemberGateRepository).save(savedGate.capture())
        kotlin.test.assertEquals(1L, savedGate.firstValue.guildId)
        kotlin.test.assertEquals(6L, savedGate.firstValue.reminderTimeHours)
    }

    private fun createService(): MemberGateService {
        return MemberGateService(
            guildMemberGateRepository,
            jda,
            memberGateQuestionRepository,
            joinModalQuestionRepository
        )
    }
}
