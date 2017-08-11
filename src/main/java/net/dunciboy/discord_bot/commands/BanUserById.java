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

import net.dunciboy.discord_bot.GuildLogger;
import net.dunciboy.discord_bot.JDALibHelper;
import net.dunciboy.discord_bot.RunBots;
import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.MessageBuilder;
import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.ChannelType;
import net.dv8tion.jda.core.entities.Member;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.PrivateChannel;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;

import java.awt.*;
import java.util.concurrent.TimeUnit;

/**
 * Created by Duncan on 22/05/2017.
 * This class creates a command to ban users by id with logging.
 */
public class BanUserById extends Ban {
    private static final String[] ALIASES = new String[]{"BanByUserId", "BanById"};
    private static final String ARGUMENTATION_SYNTAX = "[user id] [reason~]";
    private static final String DESCRIPTION = "Will ban the user with the id, clear all message that where posted by the user the last 24 hours and log it to the log channel.";


    /**
     * Constructor for abstract class
     */
    public BanUserById() {
        super(ALIASES, ARGUMENTATION_SYNTAX, DESCRIPTION);
    }

    @Override
    void commandExec(MessageReceivedEvent event, String arguments, PrivateChannel privateChannel) {
        if (!event.isFromType(ChannelType.TEXT)) {
            if (privateChannel != null) {
                event.getChannel().sendMessage("This command only works in a guild.").queue(message -> message.delete().queueAfter(1, TimeUnit.MINUTES));
            }
        } else if (!event.getMember().hasPermission(Permission.BAN_MEMBERS)) {
            event.getChannel().sendMessage("You need ban members permission to use this command.").queue(message -> message.delete().queueAfter(1, TimeUnit.MINUTES));
        } else {
            String userId;
            try {
                userId = arguments.split(" ")[0];
            } catch (NullPointerException e) {
                throw new IllegalArgumentException("No id provided");
            }
            String reason;
            try {
                reason = arguments.substring(arguments.split(" ")[0].length() + 1);
            } catch (IndexOutOfBoundsException e) {
                throw new IllegalArgumentException("No reason provided for this action.");
            }
            event.getJDA().retrieveUserById(userId).queue(toBan ->
            {
                Member toBanMemberCheck = event.getGuild().getMember(toBan);
                if (toBan != null && !event.getMember().canInteract(toBanMemberCheck)) {
                    if (privateChannel != null) {
                        privateChannel.sendMessage("You can't ban a user that you can't interact with.").queue();
                    }
                    return;
                }
                event.getGuild().getController().ban(toBan, 1, reason).queue(aVoid -> {
                    RunBots runBots = RunBots.Companion.getRunBot(event.getJDA());
                    if (runBots != null) {
                        EmbedBuilder logEmbed = new EmbedBuilder()
                                .setColor(Color.RED)
                                .setTitle("User was banned by id  | Case: " + GuildLogger.getCaseNumberSerializable(event.getGuild().getIdLong()))
                                .addField("User", toBan.getName(), true)
                                .addField("Moderator", JDALibHelper.INSTANCE.getEffectiveNameAndUsername(event.getMember()), true)
                                .addField("Reason", reason, false);

                        runBots.getLogToChannel().log(logEmbed, toBan, event.getGuild(), null);
                    }

                    if (privateChannel == null) {
                        return;
                    }

                    Message creatorMessage = new MessageBuilder()
                            .append("Banned ").append(toBan.toString())
                            .build();
                    privateChannel.sendMessage(creatorMessage).queue();
                }, throwable -> {
                    if (privateChannel == null) {
                        return;
                    }

                    Message creatorMessage = new MessageBuilder()
                            .append("Banning user failed\n")
                            .append(throwable.getClass().getSimpleName()).append(": ").append(throwable.getMessage())
                            .build();
                    privateChannel.sendMessage(creatorMessage).queue();
                });
            }, throwable -> {
                if (privateChannel == null) {
                    return;
                }

                Message creatorMessage = new MessageBuilder()
                        .append("Failed retrieving the user, banning failed.\n")
                        .append(throwable.getClass().getSimpleName()).append(": ").append(throwable.getMessage())
                        .build();
                privateChannel.sendMessage(creatorMessage).queue();
            });
        }
    }
}