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
import net.dv8tion.jda.core.entities.*;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import net.dv8tion.jda.core.exceptions.PermissionException;
import net.dv8tion.jda.core.requests.RestAction;

import java.awt.*;
import java.util.concurrent.TimeUnit;

/**
 * Created by Duncan on 24/02/2017.
 * <p>
 * This class create a kick command that will be logged.
 */
public class Kick extends CommandModule {
    private static final String[] ALIASES = new String[]{"Kick"};
    private static final String ARGUMENTATION_SYNTAX = "[User mention] [Reason~]";
    private static final String DESCRIPTION = "This command will kick the mentioned users and log this to the log channel. A reason is required.";

    /**
     * Constructor for abstract class
     */
    public Kick() {
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
        event.getAuthor().openPrivateChannel().queue(
                privateChannel -> commandExec(event, arguments, privateChannel),
                throwable -> commandExec(event, arguments, (PrivateChannel) null)
        );
    }

    private void commandExec(MessageReceivedEvent event, String arguments, PrivateChannel privateChannel) {

        if (!event.isFromType(ChannelType.TEXT)) {
            if (privateChannel != null) {
                privateChannel.sendMessage("This command only works in a guild.").queue();
            }
        } else if (!event.getMember().hasPermission(Permission.KICK_MEMBERS)) {
            if (privateChannel != null) {
                privateChannel.sendMessage(event.getAuthor().getAsMention() + " you need kick members permission to use this command!").queue(message -> message.delete().queueAfter(1, TimeUnit.MINUTES));
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
            Member toKick = event.getGuild().getMember(event.getMessage().getMentionedUsers().get(0));
            if (!event.getMember().canInteract(toKick)) {
                throw new PermissionException("You can't interact with this member");
            }
            RestAction<Void> kickRestAction = event.getGuild().getController().kick(toKick, reason);

            MessageEmbed userKickNotification = new EmbedBuilder()
                    .setColor(Color.RED)
                    .setAuthor(JDALibHelper.INSTANCE.getEffectiveNameAndUsername(event.getMember()), null, event.getAuthor().getEffectiveAvatarUrl())
                    .setTitle(event.getGuild().getName() + ": You have been kicked by " + JDALibHelper.INSTANCE.getEffectiveNameAndUsername(event.getMember()), null)
                    .setDescription("Reason: " + reason)
                    .build();

            toKick.getUser().openPrivateChannel().queue(
                    privateChannelUserToMute -> privateChannelUserToMute.sendMessage(userKickNotification).queue(
                            message -> onSuccessfulInformUser(event, reason, privateChannel, toKick, message, kickRestAction),
                            throwable -> onFailToInformUser(event, reason, privateChannel, toKick, throwable, kickRestAction)
                    ),
                    throwable -> onFailToInformUser(event, reason, privateChannel, toKick, throwable, kickRestAction)
            );
        }
    }

    private void logKick(MessageReceivedEvent event, String reason, Member toKick) {
        RunBots runBots = RunBots.Companion.getRunBot(event.getJDA());
        if (runBots != null) {
            EmbedBuilder logEmbed = new EmbedBuilder()
                    .setColor(Color.RED)
                    .setTitle("User was kicked | Case: " + GuildLogger.Companion.getCaseNumberSerializable(event.getGuild().getIdLong()))
                    .addField("User", JDALibHelper.INSTANCE.getEffectiveNameAndUsername(toKick), true)
                    .addField("Moderator", JDALibHelper.INSTANCE.getEffectiveNameAndUsername(event.getMember()), true)
                    .addField("Reason", reason, false);

            runBots.getLogToChannel().log(logEmbed, toKick.getUser(), event.getGuild(), null);
        }
    }

    private void onSuccessfulInformUser(MessageReceivedEvent event, String reason, PrivateChannel privateChannel, Member toKick, Message userKickWarning, RestAction<Void> kickRestAction) {
        kickRestAction.queue(aVoid -> {
            logKick(event, reason, toKick);
            if (privateChannel == null) {
                return;
            }

            Message creatorMessage = new MessageBuilder()
                    .append("Kicked ").append(toKick.toString()).append(".\n\nThe following message was sent to the user:")
                    .setEmbed(userKickWarning.getEmbeds().get(0))
                    .build();
            privateChannel.sendMessage(creatorMessage).queue();
        }, throwable -> {
            userKickWarning.delete().queue();
            if (privateChannel == null) {
                return;
            }

            Message creatorMessage = new MessageBuilder()
                    .append("Kick failed ").append(toKick.toString())
                    .append(throwable.getClass().getSimpleName()).append(": ").append(throwable.getMessage())
                    .append(".\n\nThe following message was sent to the user but was automatically deleted:")
                    .setEmbed(userKickWarning.getEmbeds().get(0))
                    .build();
            privateChannel.sendMessage(creatorMessage).queue();
        });
    }

    private void onFailToInformUser(MessageReceivedEvent event, String reason, PrivateChannel privateChannel, Member toKick, Throwable throwable, RestAction<Void> kickRestAction) {
        kickRestAction.queue(aVoid -> {
            logKick(event, reason, toKick);
            if (privateChannel == null) {
                return;
            }

            Message creatorMessage = new MessageBuilder()
                    .append("Kicked ").append(toKick.toString())
                    .append(".\n\nWas unable to send a DM to the user please inform the user manually, if possible.\n")
                    .append(throwable.getClass().getSimpleName()).append(": ").append(throwable.getMessage())
                    .build();
            privateChannel.sendMessage(creatorMessage).queue();
        }, banThrowable -> {
            if (privateChannel == null) {
                return;
            }

            Message creatorMessage = new MessageBuilder()
                    .append("Ban failed ").append(toKick.toString())
                    .append("\n\nWas unable to ban the user\n")
                    .append(banThrowable.getClass().getSimpleName()).append(": ").append(banThrowable.getMessage())
                    .append(".\n\nWas unable to send a DM to the user.\n")
                    .append(throwable.getClass().getSimpleName()).append(": ").append(throwable.getMessage())
                    .build();
            privateChannel.sendMessage(creatorMessage).queue();
        });
    }
}
