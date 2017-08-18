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

package net.dunciboy.discord_bot.commands;

import net.dv8tion.jda.core.MessageBuilder;
import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.ChannelType;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;

import java.util.Queue;
import java.util.concurrent.TimeUnit;

/**
 * Retrieves all the channel ids of the current guild.
 * <p>
 * Created by Duncan on 19/02/2017.
 */
public class ChannelIds extends CommandModule {
    private static final String[] ALIASES = new String[]{"ChannelIds", "GetChannelIds"};
    private static final String DESCRIPTION = "Returns all channel ids of the guild where executed.";

    public ChannelIds() {
        super(ALIASES, null, DESCRIPTION);
    }

    /**
     * Fetches all channel ids of the guild where executed.
     *
     * @param event     A MessageReceivedEvent that came with the command
     * @param command   The command alias that was used to trigger this commandExec
     * @param arguments The arguments that where entered after the command alias
     */
    @Override
    public void commandExec(MessageReceivedEvent event, String command, String arguments) {
        if (!event.isFromType(ChannelType.TEXT)) {
            event.getChannel().sendMessage("This command only works in a guild.").queue();
        } else if (!event.getMember().hasPermission(Permission.MANAGE_CHANNEL)) {
            event.getChannel().sendMessage("You need manage channels permission to use this command.").queue(message -> message.delete().queueAfter(1, TimeUnit.MINUTES));
        } else {
            StringBuilder result = new StringBuilder();
            if (event.getGuild() != null) {
                event.getGuild().getTextChannels().forEach((TextChannel channel) -> result.append(channel.toString()).append("\n"));
            }
            event.getAuthor().openPrivateChannel().queue(privateChannel -> {
                Queue<Message> messages = new MessageBuilder().appendCodeBlock(result.toString(), "text").buildAll(MessageBuilder.SplitPolicy.NEWLINE);
                messages.forEach(message -> privateChannel.sendMessage(message).queue());
            });
        }
    }
}
