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

import be.duncanc.discordmodbot.data.services.UserBlockService
import net.dv8tion.jda.api.events.ReadyEvent
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import org.springframework.stereotype.Component

@Component
class Ping(
    userBlockService: UserBlockService
) : CommandModule(
    ALIASES,
    null,
    DESCRIPTION,
    userBlockService = userBlockService
) {
    companion object {
        private val ALIASES = arrayOf("Ping")
        private const val DESCRIPTION = "responds with \"pong!\"."
    }

    override fun onReady(event: ReadyEvent) {
        event.jda.upsertCommand("ping", DESCRIPTION).queue()
    }

    public override fun commandExec(event: MessageReceivedEvent, command: String, arguments: String?) {
        event.jda.restPing.queue {
            event.channel.sendMessage(
                """pong!
It took Discord ${event.jda.gatewayPing} milliseconds to respond to our last heartbeat (gateway).
The REST API responded within $it milliseconds"""
            ).queue()
        }
    }

    override fun onSlashCommand(event: SlashCommandEvent) {
        if (event.name == "ping") {
            event.deferReply().queue {
                it.editOriginal(
                    """pong!
It took Discord ${event.jda.gatewayPing} milliseconds to respond to our last heartbeat (gateway).
The REST API responded within $it milliseconds"""
                ).queue()
            }
        }

    }
}
