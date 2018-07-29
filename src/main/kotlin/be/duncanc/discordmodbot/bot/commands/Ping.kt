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

import be.duncanc.discordmodbot.data.services.UserBlock
import net.dv8tion.jda.core.events.message.MessageReceivedEvent
import org.springframework.stereotype.Component

@Component
class Ping(
        userBlock: UserBlock
) : CommandModule(
        ALIASES,
        null,
        DESCRIPTION,
        userBlock = userBlock
) {
    companion object {
        private val ALIASES = arrayOf("Ping")
        private const val DESCRIPTION = "responds with \"pong!\"."
    }

    public override fun commandExec(event: MessageReceivedEvent, command: String, arguments: String?) {
        val millisBeforeRequest = System.currentTimeMillis()
        event.channel.sendMessage("pong!\nIt took Discord " + event.jda.ping + " milliseconds to respond to our last heartbeat.").queue { message -> message.editMessage(message.contentRaw + "\nIt took Discord " + (System.currentTimeMillis() - millisBeforeRequest) + " milliseconds to process this message.").queue() }
    }
}
