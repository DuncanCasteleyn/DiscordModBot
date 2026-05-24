package be.duncanc.discordmodbot.moderation

import be.duncanc.discordmodbot.logging.GuildLogger
import be.duncanc.discordmodbot.moderation.persistence.GuildWarnPointsSettingsRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class ReasonModalCommandDataTest {
    @Test
    fun `moderation commands collect reasons through modals`() {
        val commands = listOf(
            BanCommand().getCommandsData().single(),
            BanUserByIdCommand().getCommandsData().single(),
            KickCommand().getCommandsData().single(),
            MuteCommand(mock()).getCommandsData().single(),
            MuteByIdCommand(mock(), mock(), mock<GuildLogger>()).getCommandsData().single(),
            WarnCommand(mock<GuildWarnPointsSettingsRepository>()).getCommandsData().single()
        )

        assertEquals(
            mapOf(
                "ban" to listOf("user"),
                "banbyid" to listOf("user_id"),
                "kick" to listOf("user"),
                "mute" to listOf("user"),
                "mutebyid" to listOf("user_id"),
                "warn" to listOf("user")
            ),
            commands.associate { it.name to it.options.map { option -> option.name } }
        )

        commands.forEach { command ->
            assertFalse(command.options.any { it.name == "reason" })
        }
    }
}
