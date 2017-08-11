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

import net.dunciboy.discord_bot.JDALibHelper;
import net.dunciboy.discord_bot.LogToChannel;
import net.dunciboy.discord_bot.RunBots;
import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.ChannelType;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.entities.User;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;

import java.awt.*;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Created by Duncan on 27/02/2017.
 * <p>
 * This command purges a channel of messages.
 */
public class PurgeChannel extends CommandModule {
    private static final String[] ALIASES = new String[]{"PurgeChannel", "Purge"};
    private static final String ARGUMENTATION_SYNTAX = "[Amount of messages] (Mention user(s) to filter on)";
    private static final String DESCRIPTION = "Cleans the amount of messages given as argument. If (a) user(s) are/is mentioned at the end of this command only their/his messages will be deleted. (Messages older than 2 weeks are ignored due to api issues.)";

    /**
     * Constructor for abstract class
     */
    public PurgeChannel() {
        super(ALIASES, ARGUMENTATION_SYNTAX, DESCRIPTION, false);
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
        try {
            event.getMessage().delete().complete();
        } catch (Exception ignored) {
        }
        String[] args = arguments.split(" ");
        if (!event.isFromType(ChannelType.TEXT)) {
            event.getChannel().sendMessage("This command only works in a guild.").queue();
        } else if (!event.getMember().hasPermission(event.getTextChannel(), Permission.MESSAGE_MANAGE)) {
            event.getChannel().sendMessage(event.getAuthor().getAsMention() + " you need manage messages permission in this channel to use this command.").queue(message -> message.delete().queueAfter(1, TimeUnit.MINUTES));
        } else if (event.getMessage().getMentionedUsers().size() > 0) {
            final int amount;
            try {
                amount = parseAmountOfMessages(args[0]);
            } catch (NumberFormatException ex) {
                event.getChannel().sendMessage(event.getAuthor().getAsMention() + " the first argument needs to be a number of maximum 100 and minimum 2").queue(message -> message.delete().queueAfter(1, TimeUnit.MINUTES));
                return;
            }
            TextChannel textChannel = event.getTextChannel();
            ArrayList<Message> messageList = new ArrayList<>();
            List<User> targetUsers = event.getMessage().getMentionedUsers();
            for (Message m : textChannel.getIterableHistory().cache(false)) {
                if (targetUsers.contains(m.getAuthor()) && m.getCreationTime().isAfter(OffsetDateTime.now().minusWeeks(2))) {
                    messageList.add(m);
                } else if (m.getCreationTime().isBefore(OffsetDateTime.now().minusWeeks(2))) {
                    break;
                }
                if (messageList.size() >= amount) {
                    break;
                }
            }
            int amountDeleted = messageList.size();
            JDALibHelper.INSTANCE.limitLessBulkDelete(textChannel, messageList);
            StringBuilder stringBuilder = new StringBuilder(event.getAuthor().getAsMention()).append(" deleted ").append(amountDeleted).append(" most recent messages from ");
            for (int i = 0; i < targetUsers.size(); i++) {
                stringBuilder.append(targetUsers.get(i).getAsMention());
                if (i != targetUsers.size() - 1) {
                    stringBuilder.append(", ");
                } else {
                    stringBuilder.append('.');
                }
            }
            event.getChannel().sendMessage(stringBuilder.toString()).queue(message -> message.delete().queueAfter(1, TimeUnit.MINUTES));

            RunBots runBots = RunBots.Companion.getRunBot(event.getJDA());
            LogToChannel logger;
            if (runBots != null) {
                logger = runBots.getLogToChannel();
            } else {
                logger = null;
            }
            if (logger != null) {
                StringBuilder filterString = new StringBuilder();
                targetUsers.forEach(user -> filterString.append(user.getAsMention()).append("\n"));
                EmbedBuilder logEmbed = new EmbedBuilder()
                        .setColor(Color.YELLOW)
                        .setTitle("Filtered channel purge", null)
                        .addField("Moderator", JDALibHelper.INSTANCE.getEffectiveNameAndUsername(event.getMember()), true)
                        .addField("Channel", textChannel.getName(), true)
                        .addField("Filter", filterString.toString(), true);

                runBots.getLogToChannel().log(logEmbed, event.getAuthor(), event.getGuild(), null);

                //logger.log("Executed a message purge in #" + event.getTextChannel().getName(), "Message purge by " + JDALibHelper.getEffectiveNameAndUsername(event.getMember()), event.getGuild(), event.getAuthor().getId(), event.getAuthor().getEffectiveAvatarUrl());
            }

        } else {
            final int amount;
            try {
                amount = parseAmountOfMessages(args[0]);
            } catch (NumberFormatException ex) {
                event.getChannel().sendMessage(event.getAuthor().getAsMention() + " the first argument needs to be a number of maximum 1000 and minimum 2").queue(message -> message.delete().queueAfter(1, TimeUnit.MINUTES));
                return;
            }
            TextChannel textChannel = event.getTextChannel();
            ArrayList<Message> messageList = new ArrayList<>();
            for (Message m : textChannel.getIterableHistory().cache(false)) {
                if (m.getCreationTime().isAfter(OffsetDateTime.now().minusWeeks(2))) {
                    messageList.add(m);
                } else {
                    break;
                }
                if (messageList.size() >= amount) {
                    break;
                }
            }
            int amountDeleted = messageList.size();
            JDALibHelper.INSTANCE.limitLessBulkDelete(textChannel, messageList);
            textChannel.sendMessage(event.getAuthor().getAsMention() + " deleted " + amountDeleted + " most recent messages not older than 2 weeks.").queue(message -> message.delete().queueAfter(1, TimeUnit.MINUTES));

            RunBots runBots = RunBots.Companion.getRunBot(event.getJDA());
            LogToChannel logger;
            if (runBots != null) {
                logger = runBots.getLogToChannel();
            } else {
                logger = null;
            }
            if (logger != null) {
                EmbedBuilder logEmbed = new EmbedBuilder()
                        .setColor(Color.YELLOW)
                        .setTitle("Channel purge", null)
                        .addField("Moderator", JDALibHelper.INSTANCE.getEffectiveNameAndUsername(event.getMember()), true)
                        .addField("Channel", textChannel.getName(), true);

                runBots.getLogToChannel().log(logEmbed, event.getAuthor(), event.getGuild(), null);

                //logger.log("Executed a message purge in #" + event.getTextChannel().getName(), "Message purge by " + JDALibHelper.getEffectiveNameAndUsername(event.getMember()), event.getGuild(), event.getAuthor().getId(), event.getAuthor().getEffectiveAvatarUrl());
            }
        }
    }

    private int parseAmountOfMessages(String number) throws NumberFormatException {
        int amount = Integer.parseInt(number);
        if (amount > 1000) {
            throw new NumberFormatException("Expected number between 1 and 1000 got " + amount + ".");
        } else if (amount < 1) {
            throw new NumberFormatException("Expected number between 1 and 1000 got " + amount + ".");
        }
        return amount;
    }
}
