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

package be.duncanc.discordmodbot.bot.sequences

import be.duncanc.discordmodbot.bot.utils.JDALibHelper
import net.dv8tion.jda.core.MessageBuilder
import net.dv8tion.jda.core.entities.Message
import net.dv8tion.jda.core.entities.MessageChannel
import net.dv8tion.jda.core.entities.TextChannel
import net.dv8tion.jda.core.entities.User
import net.dv8tion.jda.core.events.guild.member.GuildMemberLeaveEvent
import net.dv8tion.jda.core.events.message.MessageReceivedEvent
import net.dv8tion.jda.core.hooks.ListenerAdapter
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * This class allows for users to be asked or do something in sequences.
 *
 * @since 1.1.0
 */
abstract class Sequence @JvmOverloads protected constructor(val user: User, val channel: MessageChannel, cleanAfterSequence: Boolean = true, informUser: Boolean = true) : ListenerAdapter() {
    companion object {
        private val LOG = LoggerFactory.getLogger(Sequence::class.java)
    }

    private val cleanAfterSequence: ArrayList<Message>?
    private val sequenceKillerExecutor: ScheduledExecutorService
    private var cleaner: ScheduledFuture<*>

    init {
        if (cleanAfterSequence && channel is TextChannel) {
            this.cleanAfterSequence = ArrayList()
        } else {
            this.cleanAfterSequence = null
        }
        sequenceKillerExecutor = Executors.newSingleThreadScheduledExecutor { r ->
            val t = Thread(r, "Sequence Thread " + this.toString())
            t.isDaemon = true
            t
        }
        cleaner = sequenceKillerExecutor.schedule({ this.destroy() }, 5, TimeUnit.MINUTES)
        if (informUser) {
            try {
                val sequenceInformMessage: Message = channel.sendMessage(user.asMention + " You are now in a sequences. The bot will ignore all further commands as you first need to complete the sequences.\n" +
                        "To complete the sequence answer the questions or tasks given by the bot in " + (if (channel is TextChannel) channel.asMention else "Private chat") + " \n" +
                        "Any message you send in this channel will be used as input.\n" +
                        "\nA sequences automatically expires after not receiving a message for 5 minutes within this channel.\n" +
                        "You can also kill a sequences by sending \"STOP\" (Case sensitive).").complete()
                addMessageToCleaner(sequenceInformMessage)
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
        if (cleaner.isDone || sequenceKillerExecutor.isShutdown || event.channel != channel || event.author != user) {
            return
        }

        cleaner.cancel(false)
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
            val errorMessage: Message = MessageBuilder().append(user.asMention + " The sequences has been terminated due to an error; see the message below for more information.")
                    .appendCodeBlock(t.javaClass.simpleName + ": " + t.message, "text")
                    .build()
            channel.sendMessage(errorMessage).queue { it.delete().queueAfter(1, TimeUnit.MINUTES) }
            return
        }

        if (!sequenceKillerExecutor.isShutdown) {
            cleaner = sequenceKillerExecutor.schedule({ this.destroy() }, 5, TimeUnit.MINUTES)
        }
    }

    override fun onGuildMemberLeave(event: GuildMemberLeaveEvent) {
        if (channel is TextChannel && channel.guild == event.member.guild && user == event.user) {
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

    protected fun destroy() {
        user.jda.removeEventListener(this)
        sequenceKillerExecutor.shutdown()
        cleanAfterSequence?.let {
            synchronized(it) {
                if (it.isNotEmpty()) {
                    JDALibHelper.limitLessBulkDelete(it[0].textChannel, it)
                }
            }
        }
    }

    protected fun addMessageToCleaner(message: Message) {
        if (message.channel != channel) {
            throw IllegalArgumentException("The message needs to be from the same channel as the sequence.")
        }
        if (sequenceKillerExecutor.isShutdown) {
            throw IllegalStateException("Cleaner shutdown.")
        }
        cleanAfterSequence?.let {
            synchronized(it) {
                it.add(message)
            }
        }
    }
}