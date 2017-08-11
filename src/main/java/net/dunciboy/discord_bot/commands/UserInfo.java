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
import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.entities.ChannelType;
import net.dv8tion.jda.core.entities.Member;
import net.dv8tion.jda.core.entities.MessageEmbed;
import net.dv8tion.jda.core.entities.PrivateChannel;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;

import java.time.format.DateTimeFormatter;

/**
 * Created by Duncan on 3/03/2017.
 * <p>
 * User info command.
 */
public class UserInfo extends CommandModule {
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd-M-yyyy hh:mm:ss a O");
    private static final String[] ALIASES = new String[]{"UserInfo", "GetUserInfo"};
    private static final String ARGUMENTATION_SYNTAX = "[Username#Discriminator] (Without @)";
    private static final String DESCRIPTION = "Prints out user information of the user given as argument";

    public UserInfo() {
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
        if (event.isFromType(ChannelType.TEXT)) {
            PrivateChannel privateChannel;
            privateChannel = event.getAuthor().openPrivateChannel().complete();
            if (arguments == null) {
                privateChannel.sendMessage("Please mention the user you want to get the dates from by using username#discriminator without @ sign, e.g.: \"Puck#5244\"\n").queue();
            } else if (!arguments.contains("#")) {
                privateChannel.sendMessage("Discriminator missing use username#discrimanator without @ sign, e.g.: \"Puck#5244\"").queue();
            } else {
                String[] searchTerms = event.getMessage().getContent().substring(command.length() + 2).toLowerCase().split("#");
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
