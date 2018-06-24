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
 */

package be.duncanc.discordmodbot.bot.commands

import be.duncanc.discordmodbot.bot.services.GuildLogger
import be.duncanc.discordmodbot.bot.utils.JDALibHelper
import be.duncanc.discordmodbot.bot.utils.ThrowableSafeRunnable
import net.dv8tion.jda.core.EmbedBuilder
import net.dv8tion.jda.core.Permission
import net.dv8tion.jda.core.entities.ChannelType
import net.dv8tion.jda.core.entities.Member
import net.dv8tion.jda.core.entities.PrivateChannel
import net.dv8tion.jda.core.entities.TextChannel
import net.dv8tion.jda.core.events.message.MessageReceivedEvent
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent
import net.dv8tion.jda.core.hooks.ListenerAdapter
import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component
import java.awt.Color
import java.util.*
import java.util.concurrent.*

/**
 * Slow mode command for channels.
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
class SlowMode private constructor() : CommandModule(ALIASES, ARGUMENTATION_SYNTAX, DESCRIPTION, true, true) {
    companion object {
        private val ALIASES = arrayOf("SlowMode")
        private const val ARGUMENTATION_SYNTAX = "[Threshold message limit] [Threshold reset time] [Mute time when threshold hit]"
        private const val DESCRIPTION = "This command will prevent spamming in channels by temporary revoking permissions on users that spam in a channel."
    }

    private val slowedChannels: ArrayList<SlowModeOnChannel> = ArrayList()

    public override fun commandExec(event: MessageReceivedEvent, command: String, arguments: String?) {
        val args: Array<String>? = arguments?.split(" ".toRegex())?.dropLastWhile { it.isEmpty() }?.toTypedArray()
        if (!event.isFromType(ChannelType.TEXT)) {
            event.author.openPrivateChannel().queue { privateChannel -> privateChannel.sendMessage("This command only works in a guild.").queue() }
        } else if (!event.member.hasPermission(event.textChannel, Permission.MESSAGE_MANAGE)) {
            val noPermissionMessage = event.author.asMention + " You need manage messages in this channel to toggle SlowMode!"
            event.author.openPrivateChannel().queue { privateChannel -> privateChannel.sendMessage(noPermissionMessage).queue() }
        } else {
            if (event.guild.getMember(event.jda.selfUser).permissions.contains(Permission.MANAGE_PERMISSIONS)) {
                val guildLogger = event.jda.registeredListeners.stream().filter { o -> o is GuildLogger }.findFirst().orElse(null) as GuildLogger
                val logToChannel = guildLogger.logger
                var wasSlowed = false
                for (slowChannel in slowedChannels) {
                    if (slowChannel.slowChannel === event.channel) {
                        slowChannel.disable()
                        slowedChannels.remove(slowChannel)
                        val logEmbed = EmbedBuilder()
                                .setColor(Color.GREEN)
                                .setTitle("Slow mode disabled", null)
                                .addField("Moderator", JDALibHelper.getEffectiveNameAndUsername(event.member), true)
                                .addField("Channel", event.textChannel.name, true)

                        logToChannel.log(logEmbed, event.author, event.guild, null, GuildLogger.LogTypeAction.MODERATOR)

                        //logger.log("Slow mode on #" + event.getTextChannel().getName() + " disabled", "toggled by " + JDALibHelper.getEffectiveNameAndUsername(event.getMember()), event.getGuild(), event.getAuthor().getId(), event.getAuthor().getEffectiveAvatarUrl());
                        wasSlowed = true
                        break
                    }
                }
                if (!wasSlowed) {
                    if (args != null && args.size >= 3) {
                        try {
                            slowedChannels.add(SlowModeOnChannel(event.textChannel, Integer.parseInt(args[0]), Integer.parseInt(args[1]), Integer.parseInt(args[2])))
                            val logEmbed = EmbedBuilder()
                                    .setColor(Color.YELLOW)
                                    .setTitle("Slow mode enabled", null)
                                    .addField("Moderator", JDALibHelper.getEffectiveNameAndUsername(event.member), true)
                                    .addField("Channel", event.textChannel.name, true)
                                    .addBlankField(false)
                                    .addField("Threshold", args[0], true)
                                    .addField("Threshold time", args[1], true)
                                    .addField("Mute time", args[2], true)

                            logToChannel.log(logEmbed, event.author, event.guild, null, GuildLogger.LogTypeAction.MODERATOR)
                        } catch (e: NumberFormatException) {
                            val privateChannel: PrivateChannel = event.author.openPrivateChannel().complete()
                            privateChannel.sendMessage("The provided argument for the command !slowmode was incorrect. Provide integer numbers.").queue()
                        }

                    } else {
                        val threshold = 3
                        val thresholdTime = 5
                        val muteTime = 5
                        slowedChannels.add(SlowModeOnChannel(event.textChannel, threshold, thresholdTime, muteTime))
                        val logEmbed = EmbedBuilder()
                                .setColor(Color.YELLOW)
                                .setTitle("Slow mode enabled", null)
                                .addField("Moderator", JDALibHelper.getEffectiveNameAndUsername(event.member), true)
                                .addField("Channel", event.textChannel.name, true)
                                .addBlankField(false)
                                .addField("Threshold", threshold.toString(), true)
                                .addField("Threshold time", thresholdTime.toString(), true)
                                .addField("Mute time", muteTime.toString(), true)

                        logToChannel.log(logEmbed, event.author, event.guild, null, GuildLogger.LogTypeAction.MODERATOR)
                    }
                }
            } else {
                val privateChannel: PrivateChannel = event.author.openPrivateChannel().complete()
                privateChannel.sendMessage("Cannot perform slow mode due to a lack of Permission. Missing permission: " + Permission.MANAGE_PERMISSIONS).queue()
            }
        }
    }

    /**
     * Created by Dunciboy on 23/10/2016.
     *
     *
     * This class executed activate slow mode on a channel that will prevent people from posting message rapidly after each other to prevent spam and abuse.
     */
    internal inner class SlowModeOnChannel
    /**
     * Will create an object that is going to slow the channel messages.
     *
     * @param slowChannel The channel that needs to be slowed
     * @param muteTime    The amount of time in seconds members need to wait before sending a new message.
     */
    internal constructor(
            /**
             * This method will the return the channel that is being slowed by the object.
             *
             * @return the channel object that is being slowed by this object.
             */
            internal val slowChannel: TextChannel, private val threshold: Int, private val thresholdResetTime: Int, private var muteTime: Int) : ListenerAdapter() {
        private val removeThreads: ThreadGroup = ThreadGroup("Slow mode user remove threads")
        private val removeThreadsPool: ScheduledExecutorService
        private val memberSlowModeCleanerMap: HashMap<Long, MemberSlowModeCleanerAndDataHolder> = HashMap()

        init {
            this.removeThreads.isDaemon = false
            this.removeThreadsPool = Executors.newScheduledThreadPool(5) { r ->
                val thread = Thread(removeThreads, ThrowableSafeRunnable(r, CommandModule.LOG), this.toString())
                thread.isDaemon = true
                thread
            }

            slowChannel.jda.addEventListener(this)
        }

        override fun toString(): String {
            return "SlowModeOnChannel{" +
                    "slowChannel=" + slowChannel +
                    '}'.toString()
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) {
                return true
            }
            if (other == null || javaClass != other.javaClass) {
                return false
            }

            val that = other as SlowModeOnChannel?
            return slowChannel == that!!.slowChannel
        }

        override fun hashCode(): Int {
            return slowChannel.hashCode()
        }

        /**
         * This function must be called before removing the object storage or it will permanently filter the slowed channel in this object!
         */
        internal fun disable() {
            slowChannel.jda.removeEventListener(this)
            removeThreadsPool.shutdown()
            val terminated: Boolean = try {
                removeThreadsPool.awaitTermination(6, TimeUnit.SECONDS)
            } catch (e: InterruptedException) {
                false
            }

            if (!terminated) {
                removeThreadsPool.shutdownNow()
            }
            synchronized(removeThreads) {
                while (removeThreads.activeCount() > 0) {
                    try {
                        removeThreads.interrupt()
                        TimeUnit.SECONDS.timedWait(removeThreads, 5)
                    } catch (ignored: InterruptedException) {
                    }

                }
                removeThreads.destroy()
            }
        }

        /**
         * This method is used to listen on messages that are send to the channel that needs to be filtered from sending messages rapidly after each other
         *
         * @param event The event that triggered this method call
         */
        override fun onGuildMessageReceived(event: GuildMessageReceivedEvent?) {
            if (event!!.member.hasPermission(Permission.MESSAGE_MANAGE) || event.author.isBot || event.author.idLong == 172478011923234816L) {
                return
            }

            if (event.channel === slowChannel) {
                if (!memberSlowModeCleanerMap.containsKey(event.author.idLong)) {
                    val memberSlowModeCleanerAndDataHolder: MemberSlowModeCleanerAndDataHolder = if (slowChannel.getPermissionOverride(event.member) != null) {
                        MemberSlowModeCleanerAndDataHolder(event.member, false, event.channel.getPermissionOverride(event.member).allowed.contains(Permission.MESSAGE_WRITE))
                    } else {
                        MemberSlowModeCleanerAndDataHolder(event.member, true)
                    }
                    memberSlowModeCleanerMap[event.author.idLong] = memberSlowModeCleanerAndDataHolder
                } else {
                    memberSlowModeCleanerMap[event.author.idLong]?.newMessage()
                }
            }
        }


        /**
         * This inner class provides the functionality to remove a person that was added to the slowed list so that he will not permanently remain slowed after posting a message.
         */
        internal inner class MemberSlowModeCleanerAndDataHolder
        /**
         * Constructor
         *
         * @param memberToClean            member to remove from mute
         * @param deletePermissionOverride If the user his permission override should be deleted or not.
         * @param grantPerm                If the user should get override write permissions assigned after his Mute.
         */
        @JvmOverloads internal constructor(private val memberToClean: Member, private val deletePermissionOverride: Boolean, private val grantPerm: Boolean = false) : Runnable {
            private val scheduledCleaner: ScheduledFuture<*>
            private var wasMuted: Boolean = false
            private var messagesAmount: Byte = 0


            init {
                this.wasMuted = false
                this.messagesAmount = 0
                this.scheduledCleaner = removeThreadsPool.schedule(this, thresholdResetTime.toLong(), TimeUnit.SECONDS)

                newMessage()
            }

            /**
             * Called when a new message is send to the channel with slow mode
             */
            internal fun newMessage() {
                if (wasMuted) {
                    return
                }

                messagesAmount++
                if (messagesAmount >= threshold) {
                    mute()
                }
            }

            /**
             * Mutes the person and makes sure the Mute is cleaned up properly
             */
            @Synchronized
            private fun mute() {
                if (!scheduledCleaner.isDone) {
                    wasMuted = true
                    val canceled = scheduledCleaner.cancel(false)
                    if (slowChannel.getPermissionOverride(memberToClean) != null) {
                        slowChannel.getPermissionOverride(memberToClean).manager.deny(Permission.MESSAGE_WRITE).reason("SlowMode: mute").queue()
                    } else {
                        slowChannel.createPermissionOverride(memberToClean).queue { permissionOverride -> permissionOverride.manager.deny(Permission.MESSAGE_WRITE).reason("SlowMode: mute").queue() }
                    }
                    if (canceled) {
                        try {
                            removeThreadsPool.execute(this)
                        } catch (e: RejectedExecutionException) {
                            muteTime = 0
                            this.run()
                        }

                    }
                }
            }

            /**
             * Will handle the removing of mutes and removing from the map
             *
             * @see Thread.run
             */
            @Synchronized
            override fun run() {
                try {
                    //TimeUnit.SECONDS.timedWait(this, thresholdResetTime); No longer needed thanks to scheduler service
                    if (wasMuted) {
                        TimeUnit.SECONDS.sleep(muteTime.toLong())
                    }
                } catch (ignored: InterruptedException) {
                } finally {
                    try {
                        memberSlowModeCleanerMap.remove(memberToClean.user.idLong)
                        if (wasMuted) {
                            when {
                                deletePermissionOverride -> slowChannel.getPermissionOverride(memberToClean).delete().reason("SlowMode: remove mute").queue()
                                grantPerm -> slowChannel.getPermissionOverride(memberToClean).manager.grant(Permission.MESSAGE_WRITE).reason("SlowMode: remove mute").queue()
                                else -> slowChannel.getPermissionOverride(memberToClean).manager.clear(Permission.MESSAGE_WRITE).reason("SlowMode: remove mute").queue()
                            }
                        }
                    } catch (t: Throwable) {
                        LOG.error("Something went wrong while cleaning users", t)
                    }

                }
            }
        }
    }
}
