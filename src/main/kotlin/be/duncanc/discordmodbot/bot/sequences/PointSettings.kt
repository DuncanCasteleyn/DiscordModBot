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

package be.duncanc.discordmodbot.bot.sequences

import be.duncanc.discordmodbot.bot.commands.CommandModule
import net.dv8tion.jda.core.Permission
import net.dv8tion.jda.core.entities.MessageChannel
import net.dv8tion.jda.core.entities.User
import net.dv8tion.jda.core.events.message.MessageReceivedEvent

class PointSettings : CommandModule(
        arrayOf("PointSettings"),
        null,
        "This command allows you to modify the settings for the point system.",
        requiredPermissions = *arrayOf(Permission.KICK_MEMBERS)
) {
    override fun commandExec(event: MessageReceivedEvent, command: String, arguments: String?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    class PointSettingsSequence(
            user: User,
            channel: MessageChannel
    ) : Sequence(
            user,
            channel
    ) {

        override fun onMessageReceivedDuringSequence(event: MessageReceivedEvent) {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }
    }
}