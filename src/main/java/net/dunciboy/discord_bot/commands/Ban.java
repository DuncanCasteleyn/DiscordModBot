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

import net.dunciboy.discord_bot.GuildLogger;
import net.dunciboy.discord_bot.JDALibHelper;
import net.dunciboy.discord_bot.RunBots;
import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.MessageBuilder;
import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.*;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import net.dv8tion.jda.core.exceptions.PermissionException;
import net.dv8tion.jda.core.requests.RestAction;

import java.awt.*;
import java.util.concurrent.TimeUnit;

/**
 * Created by Duncan on 24/02/2017.
 * <p>
 * This class creates a command to ban users with logging.
 */
public class Ban extends CommandModule {
    private static final String[] ALIASES = new String[]{"Ban"};
    private static final String ARGUMENTATION_SYNTAX = "[User mention] [Reason~]";
    private static final String DESCRIPTION = "Will ban the mentioned user, clear all message that where posted by the user in the last 24 hours and log it to the log channel.";

    /**
     * Constructor for abstract class.
     */
    public Ban() {
        super(ALIASES, ARGUMENTATION_SYNTAX, DESCRIPTION);
    }

    /**
     * Constructor for abstract class
     *
     * @param aliases             the description for this command
     * @param argumentationSyntax the syntax for the argumentation of the command, put null if none needed.
     * @param description         The description of the command
     */
    Ban(String[] aliases, String argumentationSyntax, String description) {
        super(aliases, argumentationSyntax, description);
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
        event.getAuthor().openPrivateChannel().queue(
                privateChannel -> commandExec(event, arguments, privateChannel),
                throwable -> commandExec(event, arguments, (PrivateChannel) null)
        );
    }

    void commandExec(MessageReceivedEvent event, String arguments, PrivateChannel privateChannel) {
        if (!event.isFromType(ChannelType.TEXT)) {
            if (privateChannel != null) {
                event.getChannel().sendMessage("This command only works in a guild.").queue(message -> message.delete().queueAfter(1, TimeUnit.MINUTES));
            }
        } else if (!event.getMember().hasPermission(Permission.BAN_MEMBERS)) {
            if (privateChannel != null) {
                privateChannel.sendMessage(event.getAuthor().getAsMention() + " you don't have permission to ban!").queue(message -> message.delete().queueAfter(1, TimeUnit.MINUTES));
            }
        } else if (event.getMessage().getMentionedUsers().size() < 1) {
            if (privateChannel != null) {
                privateChannel.sendMessage("Illegal argumentation, you need to mention a user that is still in the server.").queue();
            }
        } else {
            String reason;
            try {
                reason = arguments.substring(arguments.split(" ")[0].length() + 1);
            } catch (IndexOutOfBoundsException e) {
                throw new IllegalArgumentException("No reason provided for this action.");
            }
            Member toBan = event.getGuild().getMember(event.getMessage().getMentionedUsers().get(0));
            if (!event.getMember().canInteract(toBan)) {
                throw new PermissionException("You can't interact with this member");
            }
            RestAction<Void> banRestAction = event.getGuild().getController().ban(toBan, 1, reason);
            StringBuilder description = new StringBuilder("Reason: " + reason);
            if (event.getGuild().getIdLong() == 175856762677624832L) {
                description.append("\n\n")
                        .append("If you'd like to appeal the ban, please use this form: https://goo.gl/forms/SpWg49gaQlMt4lSG3");
                //todo make this configurable per guild.
            }
            MessageEmbed userKickNotification = new EmbedBuilder()
                    .setColor(Color.red)
                    .setAuthor(JDALibHelper.INSTANCE.getEffectiveNameAndUsername(event.getMember()), null, event.getAuthor().getEffectiveAvatarUrl())
                    .setTitle(event.getGuild().getName() + ": You have been banned by " + JDALibHelper.INSTANCE.getEffectiveNameAndUsername(event.getMember()), null)
                    .setDescription(description.toString())
                    .build();

            toBan.getUser().openPrivateChannel().queue(
                    privateChannelUserToMute -> privateChannelUserToMute.sendMessage(userKickNotification).queue(
                            message -> onSuccessfulInformUser(event, reason, privateChannel, toBan, message, banRestAction),
                            throwable -> onFailToInformUser(event, reason, privateChannel, toBan, throwable, banRestAction)
                    ),
                    throwable -> onFailToInformUser(event, reason, privateChannel, toBan, throwable, banRestAction)
            );
        }
    }

    private void logBan(MessageReceivedEvent event, String reason, Member toBan) {
        RunBots runBots = RunBots.Companion.getRunBot(event.getJDA());
        if (runBots != null) {
            EmbedBuilder logEmbed = new EmbedBuilder()
                    .setColor(Color.RED)
                    .setTitle("User was banned | Case: " + GuildLogger.Companion.getCaseNumberSerializable(event.getGuild().getIdLong()))
                    .addField("User", JDALibHelper.INSTANCE.getEffectiveNameAndUsername(toBan), true)
                    .addField("Moderator", JDALibHelper.INSTANCE.getEffectiveNameAndUsername(event.getMember()), true)
                    .addField("Reason", reason, false);

            runBots.getLogToChannel().log(logEmbed, toBan.getUser(), event.getGuild(), null);
        }
    }

    private void onSuccessfulInformUser(MessageReceivedEvent event, String reason, PrivateChannel privateChannel, Member toBan, Message userBanWarning, RestAction<Void> banRestAction) {
        banRestAction.queue(aVoid -> {
            logBan(event, reason, toBan);
            if (privateChannel == null) {
                return;
            }

            Message creatorMessage = new MessageBuilder()
                    .append("Banned ").append(toBan.toString()).append(".\n\nThe following message was sent to the user:")
                    .setEmbed(userBanWarning.getEmbeds().get(0))
                    .build();
            privateChannel.sendMessage(creatorMessage).queue();
        }, throwable -> {
            userBanWarning.delete().queue();
            if (privateChannel == null) {
                return;
            }

            Message creatorMessage = new MessageBuilder()
                    .append("Ban failed on ").append(toBan.toString())
                    .append(throwable.getClass().getSimpleName()).append(": ").append(throwable.getMessage())
                    .append(".\n\nThe following message was sent to the user but was automatically deleted:")
                    .setEmbed(userBanWarning.getEmbeds().get(0))
                    .build();
            privateChannel.sendMessage(creatorMessage).queue();
        });
    }

    private void onFailToInformUser(MessageReceivedEvent event, String reason, PrivateChannel privateChannel, Member toBan, Throwable throwable, RestAction<Void> banRestAction) {
        banRestAction.queue(aVoid -> {
            logBan(event, reason, toBan);
            if (privateChannel == null) {
                return;
            }

            Message creatorMessage = new MessageBuilder()
                    .append("Banned ").append(toBan.toString())
                    .append(".\n\nWas unable to send a DM to the user please inform the user manually, if possible.\n")
                    .append(throwable.getClass().getSimpleName()).append(": ").append(throwable.getMessage())
                    .build();
            privateChannel.sendMessage(creatorMessage).queue();
        }, banThrowable -> {
            if (privateChannel == null) {
                return;
            }

            Message creatorMessage = new MessageBuilder()
                    .append("Ban failed on ").append(toBan.toString())
                    .append("\n\nWas unable to ban the user\n")
                    .append(banThrowable.getClass().getSimpleName()).append(": ").append(banThrowable.getMessage())
                    .append(".\n\nWas unable to send a DM to the user.\n")
                    .append(throwable.getClass().getSimpleName()).append(": ").append(throwable.getMessage())
                    .build();
            privateChannel.sendMessage(creatorMessage).queue();
        });
    }
}
