package be.duncanc.discordmodbot.voting

import be.duncanc.discordmodbot.voting.persistence.VoteEmotes
import be.duncanc.discordmodbot.voting.persistence.VotingEmotesRepository
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.emoji.RichCustomEmoji
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.InteractionContextType
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.lenient
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.*

@ExtendWith(MockitoExtension::class)
class VoteConfigCommandTest {
    @Mock
    private lateinit var votingEmotesRepository: VotingEmotesRepository

    @Mock
    private lateinit var slashEvent: SlashCommandInteractionEvent

    @Mock
    private lateinit var guild: Guild

    @Mock
    private lateinit var member: Member

    @Mock
    private lateinit var replyAction: ReplyCallbackAction

    @Mock
    private lateinit var jda: JDA

    @Mock
    private lateinit var yesEmoji: RichCustomEmoji

    @Mock
    private lateinit var noEmoji: RichCustomEmoji

    private lateinit var command: TestVoteConfigCommand

    @BeforeEach
    fun setUp() {
        command = TestVoteConfigCommand(votingEmotesRepository)

        lenient().whenever(slashEvent.name).thenReturn("voteconfig")
        lenient().whenever(slashEvent.guild).thenReturn(guild)
        lenient().whenever(slashEvent.member).thenReturn(member)
        lenient().whenever(slashEvent.jda).thenReturn(jda)
        lenient().whenever(guild.idLong).thenReturn(1L)
        lenient().whenever(member.hasPermission(Permission.MANAGE_GUILD_EXPRESSIONS)).thenReturn(true)
        lenient().whenever(slashEvent.reply(any<String>())).thenReturn(replyAction)
        lenient().whenever(replyAction.setEphemeral(true)).thenReturn(replyAction)
    }

    @Test
    fun `missing permission returns error`() {
        whenever(member.hasPermission(Permission.MANAGE_GUILD_EXPRESSIONS)).thenReturn(false)

        command.onSlashCommandInteraction(slashEvent)

        verify(slashEvent).reply("You need manage expressions permission to use this command.")
    }

    @Test
    fun `show displays default fallback when not configured`() {
        whenever(slashEvent.subcommandName).thenReturn("show")
        whenever(votingEmotesRepository.findById(1L)).thenReturn(Optional.empty())

        command.onSlashCommandInteraction(slashEvent)

        val replyCaptor = argumentCaptor<String>()
        verify(slashEvent).reply(replyCaptor.capture())
        assertTrue(replyCaptor.firstValue.contains("default ✅"))
        assertTrue(replyCaptor.firstValue.contains("default ❎"))
    }

    @Test
    fun `set stores configured emoji ids`() {
        whenever(slashEvent.subcommandName).thenReturn("set")
        command.yesInput = "<:yes:100>"
        command.noInput = "<:no:101>"
        command.yesEmoji = yesEmoji
        command.noEmoji = noEmoji
        whenever(yesEmoji.idLong).thenReturn(100L)
        whenever(noEmoji.idLong).thenReturn(101L)
        whenever(yesEmoji.asMention).thenReturn("<:yes:100>")
        whenever(noEmoji.asMention).thenReturn("<:no:101>")

        command.onSlashCommandInteraction(slashEvent)

        val voteEmotesCaptor = argumentCaptor<VoteEmotes>()
        verify(votingEmotesRepository).save(voteEmotesCaptor.capture())
        assertEquals(100L, voteEmotesCaptor.firstValue.voteYesEmote)
        assertEquals(101L, voteEmotesCaptor.firstValue.voteNoEmote)
        verify(slashEvent).reply("Vote emoji updated: yes <:yes:100>, no <:no:101>.")
    }

    @Test
    fun `set rejects duplicate emoji`() {
        whenever(slashEvent.subcommandName).thenReturn("set")
        command.yesInput = "100"
        command.noInput = "100"
        command.yesEmoji = yesEmoji
        command.noEmoji = yesEmoji
        whenever(yesEmoji.idLong).thenReturn(100L)

        command.onSlashCommandInteraction(slashEvent)

        verify(slashEvent).reply("Please choose two different emoji.")
    }

    @Test
    fun `reset deletes stored config`() {
        whenever(slashEvent.subcommandName).thenReturn("reset")

        command.onSlashCommandInteraction(slashEvent)

        verify(votingEmotesRepository).deleteById(1L)
        verify(slashEvent).reply("Vote emoji configuration reset to the default reactions.")
    }

    @Test
    fun `show displays missing emoji clearly`() {
        whenever(slashEvent.subcommandName).thenReturn("show")
        whenever(votingEmotesRepository.findById(1L)).thenReturn(Optional.of(VoteEmotes(1L, 100L, 101L)))
        whenever(jda.getEmojiById(100L)).thenReturn(null)
        whenever(jda.getEmojiById(101L)).thenReturn(noEmoji)
        whenever(noEmoji.asMention).thenReturn("<:no:101>")

        command.onSlashCommandInteraction(slashEvent)

        val replyCaptor = argumentCaptor<String>()
        verify(slashEvent).reply(replyCaptor.capture())
        assertTrue(replyCaptor.firstValue.contains("Missing custom emoji (ID: 100)"))
        assertTrue(replyCaptor.firstValue.contains("<:no:101>"))
    }

    @Test
    fun `command data exposes expected subcommands`() {
        val commandData = command.getCommandsData().single()

        assertEquals("voteconfig", commandData.name)
        assertEquals(setOf(InteractionContextType.GUILD), commandData.contexts)
        assertEquals(listOf("show", "set", "reset"), commandData.subcommands.map(SubcommandData::getName))
    }

    private class TestVoteConfigCommand(
        votingEmotesRepository: VotingEmotesRepository
    ) : VoteConfigCommand(votingEmotesRepository) {
        var yesInput: String? = null
        var noInput: String? = null
        var yesEmoji: RichCustomEmoji? = null
        var noEmoji: RichCustomEmoji? = null

        override fun getYesEmojiInput(event: SlashCommandInteractionEvent): String? {
            return yesInput
        }

        override fun getNoEmojiInput(event: SlashCommandInteractionEvent): String? {
            return noInput
        }

        override fun resolveCustomEmoji(event: SlashCommandInteractionEvent, input: String): RichCustomEmoji? {
            return when (input) {
                yesInput -> yesEmoji
                noInput -> noEmoji
                else -> null
            }
        }
    }
}
