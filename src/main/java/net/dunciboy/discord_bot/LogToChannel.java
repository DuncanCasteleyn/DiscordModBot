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

package net.dunciboy.discord_bot;

import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.MessageBuilder;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.MessageEmbed;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.entities.User;
import net.dv8tion.jda.core.exceptions.PermissionException;
import net.dv8tion.jda.core.utils.SimpleLog;

import java.io.File;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * This class will provide logging functionality towards a predefined logging
 * channel.
 *
 * @author Duncan
 * @version 2.0
 */
public class LogToChannel {

    private static final SimpleLog LOG = SimpleLog.getLog(LogToChannel.class.getSimpleName());
    private final List<TextChannel> logChannels;

    /**
     * Find the the logging channels.
     */
    LogToChannel() {
        logChannels = new ArrayList<>();
    }

    void initChannelList(JDA jda) {
        if (jda.getSelfUser().getIdLong() == 232853504404881418L) {
            logChannels.add(jda.getTextChannelById(205415791238184969L));
        }
        if (jda.getSelfUser().getIdLong() == 247032890024525825L) {
            logChannels.add(jda.getTextChannelById(247081384110194688L));
        }

        if (jda.getSelfUser().getIdLong() == 235529232426598401L) {
            logChannels.add(jda.getTextChannelById(318070708125171712L));
        }
    }

    public List<TextChannel> getLogChannels() {
        return Collections.unmodifiableList(logChannels);
    }

    /**
     * With this method you receive a list of guilds that have the user in there guild and want information about them logged.
     *
     * @param user the user to check.
     * @return a list of guilds that want logging and have the user on there guild.
     */
    List<Guild> userOnGuilds(User user) {
        List<Guild> guildList = new ArrayList<>();
        for (TextChannel logChannel : logChannels) {
            if (logChannel.getGuild().getMember(user) != null) {
                if (!guildList.contains(logChannel.getGuild())) {
                    guildList.add(logChannel.getGuild());
                }
            }
        }
        return guildList;
    }

    /**
     * Logs to the log channel
     *
     * @param logEmbed An embed to be used as log message a time stamp will be added to the footer and
     * @param guild    The guild where the message needs to be logged to
     */
    public void log(EmbedBuilder logEmbed, User associatedUser, Guild guild, List<MessageEmbed> embeds, Path file) {
        for (TextChannel logTo : logChannels) {
            if (logTo.getGuild().equals(guild)) {
                try {
                    logEmbed = logEmbed.setTimestamp(OffsetDateTime.now());
                    if (associatedUser != null) {
                        logEmbed = logEmbed.setFooter(associatedUser.getId(), associatedUser.getEffectiveAvatarUrl());
                    }
                    if (file == null) {
                        logTo.sendMessage(logEmbed.build()).queue();
                    } else {
                        File fileToSend = file.toFile();
                        logTo.sendFile(fileToSend, new MessageBuilder().setEmbed(logEmbed.build()).build()).queue(message -> {
                            if (fileToSend.exists() && !fileToSend.delete()) {
                                fileToSend.deleteOnExit();
                            }
                        }, throwable -> {
                            if (fileToSend.exists() && !fileToSend.delete()) {
                                fileToSend.deleteOnExit();
                            }
                        });
                    }
                    if (embeds != null) {
                        for (MessageEmbed embed : embeds) {
                            logTo.sendMessage(new MessageBuilder().setEmbed(embed).append("The embedded message below was deleted with the previous deleted message").build()).queue();
                        }
                    }
                } catch (PermissionException e) {
                    LOG.warn(e.getClass().getSimpleName() + ": " + e.getMessage() + "\n" +
                            "Guild: " + guild.toString());
                }
                break;
            }
        }
    }

    /**
     * Logs to the log channel
     *
     * @param logEmbed An embed to be used as log message a time stamp will be added to the footer and
     * @param guild    The guild where the message needs to be logged to
     */
    public void log(EmbedBuilder logEmbed, User associatedUser, Guild guild, List<MessageEmbed> embeds) {
        log(logEmbed, associatedUser, guild, embeds, null);
    }
}
