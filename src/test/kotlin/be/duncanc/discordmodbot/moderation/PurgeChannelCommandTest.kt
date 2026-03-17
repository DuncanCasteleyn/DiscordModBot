package be.duncanc.discordmodbot.moderation

import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.SelfMember
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.OptionMapping
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.lenient
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExtendWith(MockitoExtension::class)
class PurgeChannelCommandTest {
    @Mock
    private lateinit var slashEvent: SlashCommandInteractionEvent

    @Mock
    private lateinit var member: Member

    @Mock
    private lateinit var selfMember: SelfMember

    @Mock
    private lateinit var guild: Guild

    @Mock
    private lateinit var channelUnion: MessageChannelUnion

    @Mock
    private lateinit var textChannel: TextChannel

    @Mock
    private lateinit var replyAction: ReplyCallbackAction

    @Mock
    private lateinit var amountOption: OptionMapping

    private lateinit var command: PurgeChannelCommand

    @BeforeEach
    fun setUp() {
        command = PurgeChannelCommand()
        lenient().whenever(replyAction.setEphemeral(true)).thenReturn(replyAction)
    }

    @Test
    fun `command name filter - non-matching name returns early`() {
        whenever(slashEvent.name).thenReturn("othercommand")

        command.onSlashCommandInteraction(slashEvent)

        verify(slashEvent, never()).reply(any<String>())
    }

    @Test
    fun `missing member returns guild error`() {
        whenever(slashEvent.name).thenReturn("purgechannel")
        whenever(slashEvent.member).thenReturn(null)
        whenever(slashEvent.reply(any<String>())).thenReturn(replyAction)

        command.onSlashCommandInteraction(slashEvent)

        verify(slashEvent).reply("This command only works in a guild.")
    }

    @Test
    fun `missing manage messages permission returns error`() {
        stubGuildContext(subcommandName = "all")
        whenever(member.hasPermission(textChannel, Permission.MESSAGE_MANAGE)).thenReturn(false)

        command.onSlashCommandInteraction(slashEvent)

        verify(slashEvent).reply("You need manage messages permission in this channel to use this command.")
    }

    @Test
    fun `missing bot permissions returns error`() {
        stubGuildContext(subcommandName = "all")
        whenever(
            selfMember.hasPermission(
                textChannel,
                Permission.MESSAGE_MANAGE,
                Permission.MESSAGE_HISTORY
            )
        ).thenReturn(false)

        command.onSlashCommandInteraction(slashEvent)

        verify(slashEvent).reply("I need manage messages and read message history permissions in this channel to use this command.")
    }

    @Test
    fun `invalid amount returns error`() {
        stubGuildContext(subcommandName = "all")
        whenever(slashEvent.getOption("amount")).thenReturn(amountOption)
        whenever(amountOption.asInt).thenReturn(1001)

        command.onSlashCommandInteraction(slashEvent)

        verify(slashEvent).reply("Please provide an amount between 1 and 1000.")
    }

    @Test
    fun `filtered mode rejects missing target user`() {
        stubGuildContext(subcommandName = "filtered")
        whenever(slashEvent.getOption("amount")).thenReturn(amountOption)
        whenever(amountOption.asInt).thenReturn(10)
        whenever(slashEvent.getOption("target")).thenReturn(null)

        command.onSlashCommandInteraction(slashEvent)

        verify(slashEvent).reply("Please select a user to delete messages from.")
    }

    private fun stubGuildContext(subcommandName: String) {
        lenient().whenever(slashEvent.name).thenReturn("purgechannel")
        lenient().whenever(slashEvent.member).thenReturn(member)
        lenient().whenever(slashEvent.guild).thenReturn(guild)
        lenient().whenever(slashEvent.subcommandName).thenReturn(subcommandName)
        lenient().whenever(slashEvent.channel).thenReturn(channelUnion)
        lenient().whenever(channelUnion.asTextChannel()).thenReturn(textChannel)
        lenient().whenever(guild.selfMember).thenReturn(selfMember)
        lenient().whenever(member.hasPermission(textChannel, Permission.MESSAGE_MANAGE)).thenReturn(true)
        lenient().whenever(selfMember.hasPermission(textChannel, Permission.MESSAGE_MANAGE, Permission.MESSAGE_HISTORY))
            .thenReturn(true)
        lenient().whenever(slashEvent.reply(any<String>())).thenReturn(replyAction)
    }
}
