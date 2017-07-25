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

import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.ChannelType;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;

import java.util.concurrent.TimeUnit;

/**
 * Created by Duncan on 7/05/2017.
 * <p>
 * This class creates a command that allows you to remove emotes from a message.
 * @deprecated No longer required clients can now right click on a message to delete all reactions if they have the permission to do so.
 */
@Deprecated
public class ClearEmotes extends CommandModule {
    private static final String[] ALIASES = new String[]{"ClearEmotes"};
    private static final String ARGUMENTATION_SYNTAX = "[Message id]";
    private static final String DESCRIPTION = "Removes all emote reactions from a message.";

    /**
     * Constructor for class
     */
    public ClearEmotes() {
        super(ALIASES, ARGUMENTATION_SYNTAX, DESCRIPTION);
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
        if (event.isFromType(ChannelType.TEXT) && event.getMember().hasPermission(event.getTextChannel(), Permission.MESSAGE_MANAGE)) {
            event.getChannel().getMessageById(arguments).queue(message -> message.clearReactions().queue());
        } else if (event.isFromType(ChannelType.TEXT)) {
            event.getChannel().sendMessage(event.getAuthor().getAsMention() + " you need manage messages in this channel to use this command.").queue(message -> message.delete().queueAfter(1, TimeUnit.MINUTES));
        } else {
            event.getChannel().sendMessage(event.getAuthor().getAsMention() + " this command only work in a guild.").queue(message -> message.delete().queueAfter(1, TimeUnit.MINUTES));
        }
    }
}
