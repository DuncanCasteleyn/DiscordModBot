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
import be.duncanc.discordmodbot.services.ModNotes;
import be.duncanc.discordmodbot.utils.JDALibHelper;
import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.MessageBuilder;
import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.*;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import net.dv8tion.jda.core.exceptions.PermissionException;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.io.Serializable;

/**
 * Created by Duncan on 24/02/2017.
 * <p>
 * This class creates a command that allowed you to warn users by sending them a dm and logging.
 */
public class Warn extends CommandModule {

    private static final String[] ALIASES = {"Warn"};
    private static final String ARGUMENTATION_SYNTAX = "[User mention] [Reason~]";
    private static final String DESCRIPTION = "Warns as user by sending the user mentioned a message and logs the warning to the log channel.";

    /**
     * Constructor for abstract class
     */
    public Warn() {
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
    public void commandExec(@NotNull MessageReceivedEvent event, @NotNull String command, String arguments) {
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
        } else if (!(event.getMember().hasPermission(Permission.KICK_MEMBERS) || event.getMember().hasPermission(Permission.BAN_MEMBERS))) {
            if (privateChannel != null) {
                privateChannel.sendMessage(event.getAuthor().getAsMention() + " you need kick/ban members permissions to warn users.").queue();
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
            Member toWarn = event.getGuild().getMember(event.getMessage().getMentionedUsers().get(0));
            if (!event.getMember().canInteract(toWarn)) {
                throw new PermissionException("You can't interact with this member");
            }
            RunBots runBots = RunBots.Companion.getRunBot(event.getJDA());
            if (runBots != null) {
                Serializable serializableCaseResult = GuildLogger.Companion.getCaseNumberSerializable(event.getGuild().getIdLong());
                EmbedBuilder logEmbed = new EmbedBuilder()
                        .setColor(Color.YELLOW)
                        .setTitle("User warned | Case: " + serializableCaseResult)
                        .addField("User", JDALibHelper.INSTANCE.getEffectiveNameAndUsername(toWarn), true)
                        .addField("Moderator", JDALibHelper.INSTANCE.getEffectiveNameAndUsername(event.getMember()), true)
                        .addField("Reason", reason, false);

                runBots.getLogToChannel().log(logEmbed, toWarn.getUser(), event.getGuild(), null, GuildLogger.LogTypeAction.MODERATOR);

                //runBots.getLogToChannel().log(JDALibHelper.getEffectiveNameAndUsername(event.getMember()) + " warned " + JDALibHelper.getEffectiveNameAndUsername(toWarn), "Reason: " + arguments, event.getGuild(), toWarn.getUser().getId(), toWarn.getUser().getEffectiveAvatarUrl(), true);
            }

            EmbedBuilder userWarning = new EmbedBuilder()
                    .setColor(Color.YELLOW)
                    .setAuthor(JDALibHelper.INSTANCE.getEffectiveNameAndUsername(event.getMember()), null, event.getAuthor().getEffectiveAvatarUrl())
                    .setTitle(event.getGuild().getName() + ": You have been warned by " + JDALibHelper.INSTANCE.getEffectiveNameAndUsername(event.getMember()), null)
                    .addField("Reason", reason, false);

            toWarn.getUser().openPrivateChannel().queue(
                    privateChannelUserToWarn -> privateChannelUserToWarn.sendMessage(userWarning.build()).queue(
                            message -> onSuccessfulWarnUser(privateChannel, toWarn, userWarning.build()),
                            throwable -> onFailToWarnUser(privateChannel, toWarn, throwable)
                    ),
                    throwable -> onFailToWarnUser(privateChannel, toWarn, throwable)
            );

            ModNotes.INSTANCE.addNote(reason, ModNotes.NoteType.WARN, toWarn.getUser().getIdLong(), event.getGuild().getIdLong(), event.getAuthor().getIdLong());
        }
    }

    private void onSuccessfulWarnUser(PrivateChannel privateChannel, Member toWarn, MessageEmbed userWarning) {
        Message creatorMessage = new MessageBuilder()
                .append("Warned ").append(toWarn.toString()).append(".\n\nThe following message was sent to the user:")
                .setEmbed(userWarning)
                .build();
        privateChannel.sendMessage(creatorMessage).queue();
    }

    private void onFailToWarnUser(PrivateChannel privateChannel, Member toWarn, Throwable throwable) {
        Message creatorMessage = new MessageBuilder()
                .append("Warned ").append(toWarn.toString()).append(".\n\nWas unable to send a DM to the user please inform the user manually.\n")
                .append(throwable.getClass().getSimpleName()).append(": ").append(throwable.getMessage())
                .build();
        privateChannel.sendMessage(creatorMessage).queue();
    }
}
