/*
 * Copyright 2018 Duncan Casteleyn
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

package be.duncanc.discordmodbot.bot.services

import net.dv8tion.jda.core.EmbedBuilder
import net.dv8tion.jda.core.JDA
import net.dv8tion.jda.core.MessageBuilder
import net.dv8tion.jda.core.entities.Guild
import net.dv8tion.jda.core.entities.MessageEmbed
import net.dv8tion.jda.core.entities.TextChannel
import net.dv8tion.jda.core.entities.User
import net.dv8tion.jda.core.exceptions.PermissionException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component
import java.time.OffsetDateTime
import java.util.*

/**
 * This class will provide logging functionality towards a predefined logging
 * channel.
 *
 * @author Duncan
 * @version 2.0
 * @since 1.0
 */
//todo add to settings class
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
class LogToChannel {

    companion object {
        private val LOG = LoggerFactory.getLogger(LogToChannel::class.java)
    }


    private val _logChannels: MutableList<TextChannel>

    val logChannels: List<TextChannel>
        get() = Collections.unmodifiableList(_logChannels)

    private val userLogChannels: MutableList<TextChannel>

    /**
     * Find the the logging channels.
     */
    init {
        _logChannels = ArrayList()
        userLogChannels = ArrayList()
    }

    internal fun initChannelList(jda: JDA) {
        if (jda.selfUser.idLong == 232853504404881418L) {
            //Re: Zero
            _logChannels.add(jda.getTextChannelById(205415791238184969L))
            userLogChannels.add(jda.getTextChannelById(375783695711207424L))
            //KoRn
            val kornLogChannel = jda.getTextChannelById(324994155480743936L)
            _logChannels.add(kornLogChannel)
            userLogChannels.add(kornLogChannel)
        }
        if (jda.selfUser.idLong == 247032890024525825L) {
            val logChannel = jda.getTextChannelById(247081384110194688L)
            _logChannels.add(logChannel)
            userLogChannels.add(logChannel)
        }

        if (jda.selfUser.idLong == 235529232426598401L) {
            val logChannel = jda.getTextChannelById(318070708125171712L)
            _logChannels.add(logChannel)
            userLogChannels.add(logChannel)
        }

        if (jda.selfUser.idLong == 368811552960413696L) {
            val logChannel = jda.getTextChannelById(368820484139122688L)
            _logChannels.add(logChannel)
            userLogChannels.add(logChannel)
        }
    }

    /**
     * With this method you receive a list of guilds that have the user in there guild and want information about them logged.
     *
     * @param user the user to check.
     * @return a list of guilds that want logging and have the user on there guild.
     */
    internal fun userOnGuilds(user: User): List<Guild> {
        val guildList = ArrayList<Guild>()
        for (logChannel in _logChannels) {
            if (logChannel.guild.getMember(user) != null) {
                if (!guildList.contains(logChannel.guild)) {
                    guildList.add(logChannel.guild)
                }
            }
        }
        return guildList
    }

    /**
     * Logs to the log channel
     *
     * @param logEmbed An embed to be used as log message a time stamp will be added to the footer and
     * @param guild    The guild where the message needs to be logged to
     */
    @JvmOverloads
    fun log(logEmbed: EmbedBuilder, associatedUser: User?, guild: Guild, embeds: List<MessageEmbed>?, actionType: GuildLogger.LogTypeAction, bytes: ByteArray? = null) {
        val targetChannel: List<TextChannel> = if (actionType === GuildLogger.LogTypeAction.MODERATOR) {
            _logChannels
        } else {
            userLogChannels
        }
        for (logTo in targetChannel) {
            if (logTo.guild == guild) {
                try {
                    logEmbed.setTimestamp(OffsetDateTime.now())
                    if (associatedUser != null) {
                        logEmbed.setFooter(associatedUser.id, associatedUser.effectiveAvatarUrl)
                    }
                    if (bytes == null) {
                        logTo.sendMessage(logEmbed.build()).queue()
                    } else {
                        logTo.sendFile(bytes, "chat.log", MessageBuilder().setEmbed(logEmbed.build()).build()).queue()
                    }
                    if (embeds != null) {
                        for (embed in embeds) {
                            logTo.sendMessage(MessageBuilder().setEmbed(embed).append("The embed below was deleted with the previous message").build()).queue()
                        }
                    }
                } catch (e: PermissionException) {
                    LOG.warn(e.javaClass.simpleName + ": " + e.message + "\n" +
                            "Guild: " + guild.toString())
                }

                break
            }
        }
    }
}
