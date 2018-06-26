/*
 * MIT License
 *
 * Copyright (c) 2017 Duncan Casteleyn
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 */

package be.duncanc.discordmodbot.bot.commands

import net.dv8tion.jda.core.events.message.MessageReceivedEvent
import org.springframework.stereotype.Component

@Component
class Ping : CommandModule(
        ALIASES,
        null,
        DESCRIPTION
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