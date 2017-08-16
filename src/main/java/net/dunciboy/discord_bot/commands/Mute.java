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

import java.awt.*;
import java.io.Serializable;

/**
 * Created by Duncan on 24/02/2017.
 * <p>
 * This class creates a mute command that will be logged.
 */
public class Mute extends CommandModule {
    private static final String[] ALIASES = new String[]{"Mute"};
    private static final String ARGUMENTATION_SYNTAX = "[User mention] [Reason~]";
    private static final String DESCRIPTION = "This command will put a user in the muted group and log the mute to the log channel.";

    /**
     * Constructor for abstract class
     */
    public Mute() {
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
        } else if (!event.getMember().hasPermission(Permission.MANAGE_ROLES)) {
            if (privateChannel != null) {
                privateChannel.sendMessage(event.getAuthor().getAsMention() + " you need manage roles permission to mute!");
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
            Member toMute = event.getGuild().getMember(event.getMessage().getMentionedUsers().get(0));
            if (!event.getMember().canInteract(toMute)) {
                throw new PermissionException("You can't interact with this member");
            }
            event.getGuild().getController().addRolesToMember(toMute, event.getGuild().getRoleById("221678882342830090")).reason(reason).queue(aVoid -> {
                RunBots runBots = RunBots.Companion.getRunBot(event.getJDA());
                if (runBots != null) {
                    Serializable serializableCaseResult = GuildLogger.Companion.getCaseNumberSerializable(event.getGuild().getIdLong());
                    EmbedBuilder logEmbed = new EmbedBuilder()
                            .setColor(Color.YELLOW)
                            .setTitle("User was muted | Case: " + serializableCaseResult)
                            .addField("User", JDALibHelper.INSTANCE.getEffectiveNameAndUsername(toMute), true)
                            .addField("Moderator", JDALibHelper.INSTANCE.getEffectiveNameAndUsername(event.getMember()), true)
                            .addField("Reason", reason, false);

                    runBots.getLogToChannel().log(logEmbed, toMute.getUser(), event.getGuild(), null);
                }
                EmbedBuilder userMuteWarning = new EmbedBuilder()
                        .setColor(Color.YELLOW)
                        .setAuthor(JDALibHelper.INSTANCE.getEffectiveNameAndUsername(event.getMember()), null, event.getAuthor().getEffectiveAvatarUrl())
                        .setTitle(event.getGuild().getName() + ": You have been muted by " + JDALibHelper.INSTANCE.getEffectiveNameAndUsername(event.getMember()))
                        .addField("Reason", reason, false);

                toMute.getUser().openPrivateChannel().queue(
                        privateChannelUserToMute -> privateChannelUserToMute.sendMessage(userMuteWarning.build()).queue(
                                message -> onSuccessfulInformUser(privateChannel, toMute, userMuteWarning.build()),
                                throwable -> onFailToInformUser(privateChannel, toMute, throwable)
                        ),
                        throwable -> onFailToInformUser(privateChannel, toMute, throwable)
                );
            }, throwable -> {
                if (privateChannel == null) {
                    return;
                }

                Message creatorMessage = new MessageBuilder()
                        .append("Failed muting ").append(toMute.toString()).append(".\n")
                        .append(throwable.getClass().getSimpleName()).append(": ").append(throwable.getMessage())
                        .build();
                privateChannel.sendMessage(creatorMessage).queue();
            });
        }
    }

    /**
     * Will be called when the user was successfully informed about his mute.
     *
     * @param privateChannel  The PrivateChannel of the moderator that executed this command.
     * @param toMute          The user that is going to be muted.
     * @param userMuteWarning The warning that was send to the user.
     */
    private void onSuccessfulInformUser(PrivateChannel privateChannel, Member toMute, MessageEmbed userMuteWarning) {
        if (privateChannel == null) {
            return;
        }

        Message creatorMessage = new MessageBuilder()
                .append("Muted ").append(toMute.toString()).append(".\n\nThe following message was sent to the user:")
                .setEmbed(userMuteWarning)
                .build();
        privateChannel.sendMessage(creatorMessage).queue();
    }

    /**
     * Will be called when informing the user about his mute failed.
     *
     * @param privateChannel the PrivateChannel of the moderator that executed this command
     * @param toMute         The user that is going to be muted.
     * @param throwable      The error that occurred, when trying to send a message.
     */
    private void onFailToInformUser(PrivateChannel privateChannel, Member toMute, Throwable throwable) {
        if (privateChannel == null) {
            return;
        }

        Message creatorMessage = new MessageBuilder()
                .append("Muted ").append(toMute.toString()).append(".\n\nWas unable to send a DM to the user please inform the user manually.\n")
                .append(throwable.getClass().getSimpleName()).append(": ").append(throwable.getMessage())
                .build();
        privateChannel.sendMessage(creatorMessage).queue();
    }
}
