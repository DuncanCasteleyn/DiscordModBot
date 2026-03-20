package be.duncanc.discordmodbot.server.config

import be.duncanc.discordmodbot.server.config.persistence.ChannelOrderLock
import be.duncanc.discordmodbot.server.config.persistence.ChannelOrderLockRepository
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
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.*

@ExtendWith(MockitoExtension::class)
class ToggleChannelOrderLockTest {
    @Mock
    private lateinit var slashEvent: SlashCommandInteractionEvent

    @Mock
    private lateinit var member: Member

    @Mock
    private lateinit var guild: Guild

    @Mock
    private lateinit var replyAction: ReplyCallbackAction

    @Mock
    private lateinit var channelOrderLockRepository: ChannelOrderLockRepository

    private lateinit var command: ToggleChannelOrderLock

    @BeforeEach
    fun setUp() {
        command = ToggleChannelOrderLock(channelOrderLockRepository)
    }

    @Test
    fun `non-matching command name returns early`() {
        whenever(slashEvent.name).thenReturn("othercommand")

        command.onSlashCommandInteraction(slashEvent)

        verify(slashEvent, never()).reply(any<String>())
    }

    @Test
    fun `missing member returns guild error`() {
        whenever(slashEvent.name).thenReturn("togglechannelorderlock")
        whenever(slashEvent.member).thenReturn(null)
        whenever(slashEvent.reply(any<String>())).thenReturn(replyAction)
        whenever(replyAction.setEphemeral(true)).thenReturn(replyAction)

        command.onSlashCommandInteraction(slashEvent)

        verify(slashEvent).reply("This command only works in a guild.")
    }

    @Test
    fun `missing permission returns error`() {
        whenever(slashEvent.name).thenReturn("togglechannelorderlock")
        whenever(slashEvent.member).thenReturn(member)
        whenever(member.hasPermission(Permission.MANAGE_CHANNEL)).thenReturn(false)
        whenever(slashEvent.reply(any<String>())).thenReturn(replyAction)
        whenever(replyAction.setEphemeral(true)).thenReturn(replyAction)

        command.onSlashCommandInteraction(slashEvent)

        verify(slashEvent).reply("You need manage channel permission to use this command.")
    }

    @Test
    fun `toggling from disabled to enabled`() {
        whenever(slashEvent.name).thenReturn("togglechannelorderlock")
        whenever(slashEvent.member).thenReturn(member)
        whenever(member.guild).thenReturn(guild)
        whenever(guild.idLong).thenReturn(1L)
        whenever(member.hasPermission(Permission.MANAGE_CHANNEL)).thenReturn(true)
        whenever(slashEvent.reply(any<String>())).thenReturn(replyAction)
        whenever(replyAction.setEphemeral(true)).thenReturn(replyAction)
        val existingLock = ChannelOrderLock(guildId = 1L, enabled = false)
        whenever(channelOrderLockRepository.findById(1L)).thenReturn(Optional.of(existingLock))

        command.onSlashCommandInteraction(slashEvent)

        verify(channelOrderLockRepository).save(ChannelOrderLock(guildId = 1L, enabled = true))
        verify(slashEvent).reply("Channel order locking is now enabled.")
    }

    @Test
    fun `toggling from enabled to disabled`() {
        whenever(slashEvent.name).thenReturn("togglechannelorderlock")
        whenever(slashEvent.member).thenReturn(member)
        whenever(member.guild).thenReturn(guild)
        whenever(guild.idLong).thenReturn(1L)
        whenever(member.hasPermission(Permission.MANAGE_CHANNEL)).thenReturn(true)
        whenever(slashEvent.reply(any<String>())).thenReturn(replyAction)
        whenever(replyAction.setEphemeral(true)).thenReturn(replyAction)
        val existingLock = ChannelOrderLock(guildId = 1L, enabled = true)
        whenever(channelOrderLockRepository.findById(1L)).thenReturn(Optional.of(existingLock))

        command.onSlashCommandInteraction(slashEvent)

        verify(channelOrderLockRepository).save(ChannelOrderLock(guildId = 1L, enabled = false))
        verify(slashEvent).reply("Channel order locking is now disabled.")
    }

    @Test
    fun `creates new record when none exists`() {
        whenever(slashEvent.name).thenReturn("togglechannelorderlock")
        whenever(slashEvent.member).thenReturn(member)
        whenever(member.guild).thenReturn(guild)
        whenever(guild.idLong).thenReturn(1L)
        whenever(member.hasPermission(Permission.MANAGE_CHANNEL)).thenReturn(true)
        whenever(slashEvent.reply(any<String>())).thenReturn(replyAction)
        whenever(replyAction.setEphemeral(true)).thenReturn(replyAction)
        whenever(channelOrderLockRepository.findById(1L)).thenReturn(Optional.empty())

        command.onSlashCommandInteraction(slashEvent)

        verify(channelOrderLockRepository).save(ChannelOrderLock(guildId = 1L, enabled = true))
        verify(slashEvent).reply("Channel order locking is now enabled.")
    }
}
