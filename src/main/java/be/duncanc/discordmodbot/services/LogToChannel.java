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

package be.duncanc.discordmodbot.services;

import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.MessageBuilder;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.MessageEmbed;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.entities.User;
import net.dv8tion.jda.core.exceptions.PermissionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 * @since 1.0
 */
//todo add to settings class
public class LogToChannel {

    private static final Logger LOG = LoggerFactory.getLogger(LogToChannel.class);
    private final List<TextChannel> logChannels;
    private final List<TextChannel> userLogChannels;

    /**
     * Find the the logging channels.
     */
    public LogToChannel() {
        logChannels = new ArrayList<>();
        userLogChannels = new ArrayList<>();
    }

    void initChannelList(JDA jda) {
        if (jda.getSelfUser().getIdLong() == 232853504404881418L) {
            //Re: Zero
            logChannels.add(jda.getTextChannelById(205415791238184969L));
            userLogChannels.add(jda.getTextChannelById(375783695711207424L));
            //KoRn
            TextChannel kornLogChannel = jda.getTextChannelById(324994155480743936L);
            logChannels.add(kornLogChannel);
            userLogChannels.add(kornLogChannel);
        }
        if (jda.getSelfUser().getIdLong() == 247032890024525825L) {
            TextChannel logChannel = jda.getTextChannelById(247081384110194688L);
            logChannels.add(logChannel);
            userLogChannels.add(logChannel);
        }

        if (jda.getSelfUser().getIdLong() == 235529232426598401L) {
            TextChannel logChannel = jda.getTextChannelById(318070708125171712L);
            logChannels.add(logChannel);
            userLogChannels.add(logChannel);
        }

        if (jda.getSelfUser().getIdLong() == 368811552960413696L) {
            TextChannel logChannel = jda.getTextChannelById(368820484139122688L);
            logChannels.add(logChannel);
            userLogChannels.add(logChannel);
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
    public void log(EmbedBuilder logEmbed, User associatedUser, Guild guild, List<MessageEmbed> embeds, GuildLogger.LogTypeAction actionType, byte[] bytes) {
        List<TextChannel> targetChannel;
        if (actionType == GuildLogger.LogTypeAction.MODERATOR) {
            targetChannel = logChannels;
        } else {
            targetChannel = userLogChannels;
        }
        for (TextChannel logTo : targetChannel) {
            if (logTo.getGuild().equals(guild)) {
                try {
                    logEmbed = logEmbed.setTimestamp(OffsetDateTime.now());
                    if (associatedUser != null) {
                        logEmbed = logEmbed.setFooter(associatedUser.getId(), associatedUser.getEffectiveAvatarUrl());
                    }
                    if (bytes == null) {
                        logTo.sendMessage(logEmbed.build()).queue();
                    } else {
                        logTo.sendFile(bytes, "chat.log", new MessageBuilder().setEmbed(logEmbed.build()).build()).queue();
                    }
                    if (embeds != null) {
                        for (MessageEmbed embed : embeds) {
                            logTo.sendMessage(new MessageBuilder().setEmbed(embed).append("The embed below was deleted with the previous message").build()).queue();
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
    public void log(EmbedBuilder logEmbed, User associatedUser, Guild guild, List<MessageEmbed> embeds, GuildLogger.LogTypeAction actionType) {
        log(logEmbed, associatedUser, guild, embeds, actionType, null);
    }
}
