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
import net.dunciboy.discord_bot.RunBots;
import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.MessageBuilder;
import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.*;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;

import java.awt.*;

/**
 * Created by Duncan on 24/02/2017.
 * <p>
 * This class creates an RemoveMute command that is logged.
 */
public class RemoveMute extends CommandModule {
    private static final String[] ALIASES = new String[]{"Unmute", "RemoveMute"};
    private static final String ARGUMENTATION_SYNTAX = "[User mention] [Reason~]";
    private static final String DESCRIPTION = "This command allows you to remove the mute from a user and log it to the log channel.";

    /**
     * Constructor for abstract class
     */
    public RemoveMute() {
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
                privateChannel.sendMessage(event.getAuthor().getAsMention() + " you need manage roles permission to remove a mute!");
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
            Member toRemoveMute = event.getGuild().getMember(event.getMessage().getMentionedUsers().get(0));
            if (!toRemoveMute.getRoles().contains(event.getGuild().getRoleById("221678882342830090"))) {
                throw new UnsupportedOperationException("Can't remove a mute from a user that is not muted.");
            }
            event.getGuild().getController().removeRolesFromMember(toRemoveMute, event.getGuild().getRoleById("221678882342830090")).reason(reason).queue(aVoid -> {
                RunBots runBots = RunBots.Companion.getRunBot(event.getJDA());
                if (runBots != null) {
                    EmbedBuilder logEmbed = new EmbedBuilder()
                            .setColor(Color.GREEN)
                            .setTitle("User's mute was removed", null)
                            .addField("User", JDALibHelper.getEffectiveNameAndUsername(toRemoveMute), true)
                            .addField("Moderator", JDALibHelper.getEffectiveNameAndUsername(event.getMember()), true)
                            .addField("Reason", reason, false);

                    runBots.getLogToChannel().log(logEmbed, toRemoveMute.getUser(), event.getGuild(), null);

                    //runBots.getLogToChannel().log(JDALibHelper.getEffectiveNameAndUsername(event.getMember()) + " removed mute on " + JDALibHelper.getEffectiveNameAndUsername(toRemoveMute), "Reason: " + arguments, event.getGuild(), toRemoveMute.getUser().getId(), toRemoveMute.getUser().getEffectiveAvatarUrl(), true);
                }
                MessageEmbed muteRemoveNotification = new EmbedBuilder()
                        .setColor(Color.green)
                        .setAuthor(JDALibHelper.getEffectiveNameAndUsername(event.getMember()), null, event.getAuthor().getEffectiveAvatarUrl())
                        .setTitle(event.getGuild().getName() + ": Your mute has been removed by " + JDALibHelper.getEffectiveNameAndUsername(event.getMember()), null)
                        .addField("Reason", reason, false)
                        .build();

                toRemoveMute.getUser().openPrivateChannel().queue(
                        privateChannelUserToRemoveMute -> privateChannelUserToRemoveMute.sendMessage(muteRemoveNotification).queue(
                                message -> onSuccessfulInformUser(privateChannel, toRemoveMute, muteRemoveNotification),
                                throwable -> onFailToInformUser(privateChannel, toRemoveMute, throwable)
                        ),
                        throwable -> onFailToInformUser(privateChannel, toRemoveMute, throwable)
                );
            }, throwable -> {
                if (privateChannel == null) {
                    return;
                }

                Message creatorMessage = new MessageBuilder()
                        .append("Failed removing mute ").append(toRemoveMute.toString()).append(".\n")
                        .append(throwable.getClass().getSimpleName()).append(": ").append(throwable.getMessage())
                        .build();
                privateChannel.sendMessage(creatorMessage).queue();
            });
        }
    }

    private void onSuccessfulInformUser(PrivateChannel privateChannel, Member toRemoveMute, MessageEmbed muteRemoveNotification) {
        if (privateChannel == null) {
            return;
        }

        Message creatorMessage = new MessageBuilder()
                .append("Removed mute from ").append(toRemoveMute.toString()).append(".\n\nThe following message was sent to the user:")
                .setEmbed(muteRemoveNotification)
                .build();
        privateChannel.sendMessage(creatorMessage).queue();
    }

    private void onFailToInformUser(PrivateChannel privateChannel, Member toRemoveMute, Throwable throwable) {
        if (privateChannel == null) {
            return;
        }

        Message creatorMessage = new MessageBuilder()
                .append("Removed mute from ").append(toRemoveMute.toString()).append(".\n\nWas unable to send a DM to the user please inform the user manually.\n")
                .append(throwable.getClass().getSimpleName()).append(": ").append(throwable.getMessage())
                .build();
        privateChannel.sendMessage(creatorMessage).queue();
    }
}
