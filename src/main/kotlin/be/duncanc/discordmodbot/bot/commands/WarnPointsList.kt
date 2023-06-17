/*
 * Copyright 2018 Duncan Casteleyn
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package be.duncanc.discordmodbot.bot.commands

import be.duncanc.discordmodbot.data.entities.GuildWarnPoint
import be.duncanc.discordmodbot.data.repositories.jpa.GuildWarnPointsRepository
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.utils.SplitUtil
import org.springframework.stereotype.Component
import java.time.OffsetDateTime

@Component
class WarnPointsList(
    private val guildWarnPointsRepository: GuildWarnPointsRepository
) : CommandModule(
    arrayOf("WarnPointsList", "WarnList"),
    null,
    null,
    requiredPermissions = arrayOf(Permission.KICK_MEMBERS)
) {
    override fun commandExec(event: MessageReceivedEvent, command: String, arguments: String?) {
        val userWarnPoints =
            guildWarnPointsRepository.findAllByGuildIdAndExpireDateAfter(
                event.guild.idLong, OffsetDateTime.now()
            )

        val message = StringBuilder()
        message.append("Summary of active points per user:\n")

        val groupByUserAndGuild =
            userWarnPoints.groupBy { guildWarnPoint: GuildWarnPoint ->
                guildWarnPoint.guildId.toString() + "-" + guildWarnPoint.userId.toString()
            }

        groupByUserAndGuild.forEach { (_, userWarnings) ->
            val totalPoints = userWarnings.size
            message.append("\n")
                .append("<@${userWarnings.first().userId}>")
                .append(" [$totalPoints]")
        }
        val messages = SplitUtil.split(message.toString(), Message.MAX_CONTENT_LENGTH, SplitUtil.Strategy.NEWLINE)

        messages.forEach { event.channel.sendMessage(it).queue() }
    }
}
