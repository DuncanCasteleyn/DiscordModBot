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

package be.duncanc.discordmodbot.bot.sequences

import be.duncanc.discordmodbot.bot.utils.limitLessBulkDeleteByIds
import net.dv8tion.jda.api.MessageBuilder
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageChannel
import net.dv8tion.jda.api.entities.TextChannel
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * This class allows for users to be asked or do something in sequences.
 *
 * @since 1.1.0
 */
abstract class Sequence
@JvmOverloads protected constructor(
    val user: User,
    val channel: MessageChannel,
    cleanAfterSequence: Boolean = true,
    informUser: Boolean = true
) : ListenerAdapter() {
    companion object {
        private val LOG = LoggerFactory.getLogger(Sequence::class.java)
    }

    private val cleanAfterSequence: ArrayList<Long>?
    private val expireInstant: Instant = Instant.now().plus(5, ChronoUnit.MINUTES)

    init {
        if (cleanAfterSequence && channel is TextChannel) {
            this.cleanAfterSequence = ArrayList()
        } else {
            this.cleanAfterSequence = null
        }
        if (informUser) {
            try {
                channel.sendMessage(
                    user.asMention + " You are now in a sequences. The bot will ignore all further commands as you first need to complete the sequences.\n" +
                            "To complete the sequence answer the questions or tasks given by the bot in " + (if (channel is TextChannel) channel.asMention else "Private chat") + " \n" +
                            "Any message you send in this channel will be used as input.\n" +
                            "\nA sequences automatically expires after not receiving a message for 5 minutes within this channel.\n" +
                            "You can also kill a sequences by sending \"STOP\" (Case sensitive)."
                ).queue {
                    addMessageToCleaner(it)
                }
            } catch (e: Exception) {
                LOG.info("A sequence was terminated due to an exception during initialization", e)
                destroy()
                throw e
            }
        }
    }

    /**
     * Will be called after the necessary operations are performed to keep the sequences alive.
     */
    protected abstract fun onMessageReceivedDuringSequence(event: MessageReceivedEvent)

    /**
     * Will perform the required actions while in a sequences and then send it to the {@code onMessageReceivedDuringSequence(event)}.
     *
     * When {@code onMessageReceivedDuringSequence(event)} throws an exception it catches it, send the exception name and message and terminate the sequences.
     */
    override fun onMessageReceived(event: MessageReceivedEvent) {
        if (event.author != user || event.message.channel != channel) {
            return
        }
        addMessageToCleaner(event.message)
        if (event.message.contentRaw == "STOP") {
            destroy()
            return
        }
        try {
            onMessageReceivedDuringSequence(event)
        } catch (t: Throwable) {
            LOG.info("A sequence was terminated due to an exception", t)
            destroy()
            val errorMessage: Message =
                MessageBuilder().append(user.asMention + " The sequences has been terminated due to an error; see the message below for more information.")
                    .appendCodeBlock(t.javaClass.simpleName + ": " + t.message, "text")
                    .build()
            channel.sendMessage(errorMessage).queue { it.delete().queueAfter(1, TimeUnit.MINUTES) }
            return
        }
    }

    override fun onMessageReactionAdd(event: MessageReactionAddEvent) {
        if (this !is ReactionSequence || event.user != user) {
            return
        }
        this.onReactionReceivedDuringSequence(event)
    }

    override fun onGuildMemberRemove(event: GuildMemberRemoveEvent) {
        if (channel is TextChannel && channel.guild == event.guild && user == event.user) {
            destroy()
        } else if (event.user.mutualGuilds.isEmpty()) {
            destroy()
        }
    }

    /**
     * Let the garbage collector destroy this object.
     */
    @Suppress("unused")
    @Throws(Throwable::class)
    protected open fun finalize() {
        destroy()
    }

    fun destroy() {
        user.jda.removeEventListener(this)
        cleanAfterSequence?.let {
            synchronized(it) {
                if (it.isNotEmpty()) {
                    try {
                        (channel as TextChannel).limitLessBulkDeleteByIds(it)
                    } catch (e: Exception) {
                        LOG.error("Was unable to clear messages when destroying sequence", e)
                    }
                }
            }
        }
    }

    protected fun addMessageToCleaner(message: Message) {
        if (message.channel != channel) {
            throw IllegalArgumentException("The message needs to be from the same channel as the sequence.")
        }
        cleanAfterSequence?.let {
            synchronized(it) {
                it.add(message.idLong)
            }
        }
    }

    fun sequenceIsExpired(): Boolean {
        return Instant.now().isAfter(expireInstant)
    }
}
