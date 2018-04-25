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

package be.duncanc.discordmodbot.bot.commands;

import be.duncanc.discordmodbot.bot.utils.JDALibHelper;
import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.entities.ChannelType;
import net.dv8tion.jda.core.entities.Member;
import net.dv8tion.jda.core.entities.MessageEmbed;
import net.dv8tion.jda.core.entities.PrivateChannel;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;

/**
 * User info command.
 */
@Component
public class UserInfo extends CommandModule {
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd-M-yyyy hh:mm:ss a O");
    private static final String[] ALIASES = new String[]{"UserInfo", "GetUserInfo"};
    private static final String ARGUMENTATION_SYNTAX = "[Username#Discriminator] (Without @)";
    private static final String DESCRIPTION = "Prints out user information of the user given as argument";

    private UserInfo() {
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
        if (event.isFromType(ChannelType.TEXT)) {
            PrivateChannel privateChannel;
            privateChannel = event.getAuthor().openPrivateChannel().complete();
            if (arguments == null) {
                privateChannel.sendMessage("Please mention the user you want to get the dates from by using username#discriminator without @ sign, e.g.: \"Puck#5244\"\n").queue();
            } else if (!arguments.contains("#")) {
                privateChannel.sendMessage("Discriminator missing use username#discrimanator without @ sign, e.g.: \"Puck#5244\"").queue();
            } else {
                String[] searchTerms = event.getMessage().getContentRaw().substring(command.length() + 2).toLowerCase().split("#");
                boolean targetFound = false;
                for (Member member : event.getGuild().getMembers()) {
                    if (searchTerms[0].equals(member.getUser().getName().toLowerCase()) && searchTerms[1].equals(member.getUser().getDiscriminator())) {
//                                privateChannel.sendMessage("Dates from user " + member.toString() + "\n" +
//                                        "Guild join date: " + member.getJoinDate().format(DATE_TIME_FORMATTER) + "\n" +
//                                        "Account creation date: " + member.getUser().getCreationTime().format(DATE_TIME_FORMATTER)).queue();
                        MessageEmbed userDateMessage = new EmbedBuilder()
                                .setAuthor(JDALibHelper.INSTANCE.getEffectiveNameAndUsername(member), null, member.getUser().getEffectiveAvatarUrl())
                                .setThumbnail(member.getUser().getEffectiveAvatarUrl())
                                .setTitle("Guild: " + member.getGuild().getName(), null)
                                .addField("User id", member.getUser().getId(), false)
                                .addField("Discriminator", member.getUser().getDiscriminator(), false)
                                .addField("Online status", member.getOnlineStatus().name(), false)
                                .addField("In voice channel?", String.valueOf(member.getVoiceState().inVoiceChannel()), true)
                                .addField("Guild owner?", String.valueOf(member.isOwner()), true)
                                .addField("is a bot?", String.valueOf(member.getUser().isBot()), true)
                                .addField("Permissions", member.getPermissions().toString(), false)
                                .addField("Roles", member.getRoles().toString(), false)
                                .addField("Guild join date", member.getJoinDate().format(DATE_TIME_FORMATTER), true)
                                .addField("Account creation date", member.getUser().getCreationTime().format(DATE_TIME_FORMATTER), true)
                                .build();
                        privateChannel.sendMessage(userDateMessage).queue();
                        targetFound = true;
                        break;
                    }
                }

                if (!targetFound) {
                    privateChannel.sendMessage("The specified user was not found.").queue();
                }
            }
        } else {
            event.getChannel().sendMessage("This command only works in a guild text channel.").queue();
        }
    }
}
