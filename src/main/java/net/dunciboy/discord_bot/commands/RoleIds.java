/*
 * Copyright 2017 Duncan C.
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

package net.dunciboy.discord_bot.commands;

import net.dv8tion.jda.core.MessageBuilder;
import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.ChannelType;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.Role;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;

import java.util.Queue;
import java.util.concurrent.TimeUnit;

/**
 * Commands to get all role ids of the current guild where executed.
 * <p>
 * Created by Duncan on 19/02/2017.
 */
public class RoleIds extends CommandModule {
    private static final String[] ALIASES = new String[]{"RoleIds", "GetRoleIds"};
    private static final String DESCRIPTION = "Get all the role ids of the guild where executed.";

    public RoleIds() {
        super(ALIASES, null, DESCRIPTION);
    }

    /**
     * Do something with the event, command and arguments.
     *
     * @param event     A MessageReceivedEvent that came with the command
     * @param command   The command alias that was used to trigger this commandExec
     * @param arguments The arguments that where entered after the command alias
     */
    @Override
    public void commandExec(MessageReceivedEvent event, String command, String arguments) {
        if (!event.isFromType(ChannelType.TEXT)) {
            event.getChannel().sendMessage("This command only works in a guild.").queue(message -> message.delete().queueAfter(1, TimeUnit.MINUTES));
        } else if (!event.getMember().hasPermission(Permission.MANAGE_ROLES)) {
            event.getChannel().sendMessage(event.getAuthor().getAsMention() + " you need manage roles permission to use this command.").queue(message -> message.delete().queueAfter(1, TimeUnit.MINUTES));
        } else {
            StringBuilder result = new StringBuilder();
            if (event.getGuild() != null) {
                event.getGuild().getRoles().forEach((Role role) -> result.append(role.toString()).append("\n"));
            }
            event.getAuthor().openPrivateChannel().queue(privateChannel -> {
                Queue<Message> messages = new MessageBuilder().appendCodeBlock(result.toString(), "text").buildAll(MessageBuilder.SplitPolicy.NEWLINE);
                messages.forEach(message -> privateChannel.sendMessage(message).queue());
            });
        }
    }
}
