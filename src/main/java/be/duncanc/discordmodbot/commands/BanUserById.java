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

package be.duncanc.discordmodbot.commands;

import be.duncanc.discordmodbot.RunBots;
import be.duncanc.discordmodbot.services.GuildLogger;
import be.duncanc.discordmodbot.utils.JDALibHelper;
import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.MessageBuilder;
import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.ChannelType;
import net.dv8tion.jda.core.entities.Member;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.PrivateChannel;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import org.jetbrains.annotations.NotNull;

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
    void commandExec(@NotNull MessageReceivedEvent event, String arguments, PrivateChannel privateChannel) {
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
                if (toBanMemberCheck != null && !event.getMember().canInteract(toBanMemberCheck)) {
                    if (privateChannel != null) {
                        privateChannel.sendMessage("You can't ban a user that you can't interact with.").queue();
                    }
                    return;
                }
                event.getGuild().getController().ban(userId, 1, reason).queue(aVoid -> {
                    RunBots runBots = RunBots.Companion.getRunBot(event.getJDA());
                    if (runBots != null) {
                        EmbedBuilder logEmbed = new EmbedBuilder()
                                .setColor(Color.RED)
                                .setTitle("User banned by id  | Case: " + GuildLogger.Companion.getCaseNumberSerializable(event.getGuild().getIdLong()))
                                .addField("User", toBan.getName(), true)
                                .addField("Moderator", JDALibHelper.INSTANCE.getEffectiveNameAndUsername(event.getMember()), true)
                                .addField("Reason", reason, false);

                        runBots.getLogToChannel().log(logEmbed, toBan, event.getGuild(), null, GuildLogger.LogTypeAction.MODERATOR);
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