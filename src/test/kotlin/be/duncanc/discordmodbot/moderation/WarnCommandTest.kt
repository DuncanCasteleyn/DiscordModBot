package be.duncanc.discordmodbot.moderation

import be.duncanc.discordmodbot.moderation.persistence.GuildWarnPointsSettingsRepository
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExtendWith(MockitoExtension::class)
class WarnCommandTest {
    @Mock
    private lateinit var guildWarnPointsSettingsRepository: GuildWarnPointsSettingsRepository

    @Mock
    private lateinit var slashEvent: SlashCommandInteractionEvent

    @Mock
    private lateinit var guild: Guild

    @Mock
    private lateinit var member: Member

    @Mock
    private lateinit var replyAction: ReplyCallbackAction

    private lateinit var command: WarnCommand

    @BeforeEach
    fun setUp() {
        command = WarnCommand(guildWarnPointsSettingsRepository)
    }

    @Test
    fun `missing manage roles permission returns error`() {
        whenever(slashEvent.name).thenReturn("warn")
        whenever(slashEvent.guild).thenReturn(guild)
        whenever(slashEvent.member).thenReturn(member)
        whenever(member.hasPermission(Permission.MANAGE_ROLES)).thenReturn(false)
        whenever(slashEvent.reply(any<String>())).thenReturn(replyAction)
        whenever(replyAction.setEphemeral(true)).thenReturn(replyAction)

        command.onSlashCommandInteraction(slashEvent)

        verify(slashEvent).reply("You need manage roles permission to use this command.")
    }
}
