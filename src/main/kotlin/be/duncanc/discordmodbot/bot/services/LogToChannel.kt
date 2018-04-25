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
class LogToChannel private constructor() {

    companion object {
        private val LOG = LoggerFactory.getLogger(LogToChannel::class.java)
    }

    internal val logChannels: MutableList<TextChannel>
    private val userLogChannels: MutableList<TextChannel>

    /**
     * Find the the logging channels.
     */
    init {
        logChannels = ArrayList()
        userLogChannels = ArrayList()
    }

    internal fun initChannelList(jda: JDA) {
        if (jda.selfUser.idLong == 232853504404881418L) {
            //Re: Zero
            logChannels.add(jda.getTextChannelById(205415791238184969L))
            userLogChannels.add(jda.getTextChannelById(375783695711207424L))
            //KoRn
            val kornLogChannel = jda.getTextChannelById(324994155480743936L)
            logChannels.add(kornLogChannel)
            userLogChannels.add(kornLogChannel)
        }
        if (jda.selfUser.idLong == 247032890024525825L) {
            val logChannel = jda.getTextChannelById(247081384110194688L)
            logChannels.add(logChannel)
            userLogChannels.add(logChannel)
        }

        if (jda.selfUser.idLong == 235529232426598401L) {
            val logChannel = jda.getTextChannelById(318070708125171712L)
            logChannels.add(logChannel)
            userLogChannels.add(logChannel)
        }

        if (jda.selfUser.idLong == 368811552960413696L) {
            val logChannel = jda.getTextChannelById(368820484139122688L)
            logChannels.add(logChannel)
            userLogChannels.add(logChannel)
        }
    }

    fun getLogChannels(): List<TextChannel> {
        return Collections.unmodifiableList(logChannels)
    }

    /**
     * With this method you receive a list of guilds that have the user in there guild and want information about them logged.
     *
     * @param user the user to check.
     * @return a list of guilds that want logging and have the user on there guild.
     */
    internal fun userOnGuilds(user: User): List<Guild> {
        val guildList = ArrayList<Guild>()
        for (logChannel in logChannels) {
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
            logChannels
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
