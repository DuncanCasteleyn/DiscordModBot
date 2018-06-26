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


import be.duncanc.discordmodbot.bot.commands.CommandModule
import be.duncanc.discordmodbot.bot.sequences.Sequence
import be.duncanc.discordmodbot.bot.utils.JDALibHelper
import be.duncanc.discordmodbot.bot.utils.ThrowableSafeRunnable
import be.duncanc.discordmodbot.data.entities.LoggingSettings
import be.duncanc.discordmodbot.data.repositories.LoggingSettingsRepository
import net.dv8tion.jda.core.EmbedBuilder
import net.dv8tion.jda.core.MessageBuilder
import net.dv8tion.jda.core.Permission
import net.dv8tion.jda.core.audit.ActionType
import net.dv8tion.jda.core.audit.AuditLogEntry
import net.dv8tion.jda.core.audit.AuditLogOption
import net.dv8tion.jda.core.entities.*
import net.dv8tion.jda.core.entities.impl.GuildImpl
import net.dv8tion.jda.core.events.ReadyEvent
import net.dv8tion.jda.core.events.guild.GuildBanEvent
import net.dv8tion.jda.core.events.guild.GuildJoinEvent
import net.dv8tion.jda.core.events.guild.GuildLeaveEvent
import net.dv8tion.jda.core.events.guild.GuildUnbanEvent
import net.dv8tion.jda.core.events.guild.member.GuildMemberJoinEvent
import net.dv8tion.jda.core.events.guild.member.GuildMemberLeaveEvent
import net.dv8tion.jda.core.events.guild.member.GuildMemberNickChangeEvent
import net.dv8tion.jda.core.events.message.MessageBulkDeleteEvent
import net.dv8tion.jda.core.events.message.MessageReceivedEvent
import net.dv8tion.jda.core.events.message.guild.GuildMessageDeleteEvent
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent
import net.dv8tion.jda.core.events.message.guild.GuildMessageUpdateEvent
import net.dv8tion.jda.core.events.user.update.UserUpdateDiscriminatorEvent
import net.dv8tion.jda.core.events.user.update.UserUpdateNameEvent
import net.dv8tion.jda.core.hooks.ListenerAdapter
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.awt.Color
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * This file will create the listeners and the appropriate action to be taken
 * upon each of the listener.
 *
 *
 * IMPORTANT READ BEFORE MODIFYING CODE:
 * The modifying the lastCheckedLogEntries HashMap needs to happen using the guildLoggerExecutor because its
 * thread are executed sequentially there is no need to lock the object, however if you try to do this without using the
 * service the chances of hitting a ConcurrentModificationException is 100%.
 *
 * @author Duncan
 * @since 1.0
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
class GuildLogger constructor(
        val logger: LogToChannel,
        val messageHistory: MessageHistory,
        val loggingSettingsRepository: LoggingSettingsRepository
) : ListenerAdapter() {

    companion object {
        private val DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd-M-yyyy hh:mm a O")
        private val LOG = LoggerFactory.getLogger(GuildLogger::class.java)
        private val LIGHT_BLUE = Color(52, 152, 219)
        private const val LOG_ENTRY_CHECK_LIMIT = 5
    }


    private val guildLoggerExecutor: ScheduledExecutorService
    private val lastCheckedLogEntries: HashMap<Long, AuditLogEntry> //Long key is the guild id and the value is the last checked log entry.

    init {
        this.guildLoggerExecutor = Executors.newSingleThreadScheduledExecutor { r ->
            val thread = Thread(ThrowableSafeRunnable(r, LOG), GuildLogger::class.java.simpleName)
            thread.isDaemon = true
            thread
        }
        this.lastCheckedLogEntries = HashMap()
    }


    override fun onGuildMessageReceived(event: GuildMessageReceivedEvent?) {
        messageHistory.onGuildMessageReceived(event)
    }

    override fun onReady(event: ReadyEvent) {
        logger.initChannelList(event.jda)
        logger.logChannels.forEach { textChannel ->
            textChannel.guild.auditLogs.limit(1).cache(false).queue { auditLogEntries ->
                val auditLogEntry = if (auditLogEntries.isEmpty()) {
                    AuditLogEntry(ActionType.MESSAGE_DELETE, -1, -1, textChannel.guild as GuildImpl, null, null, null, null, null)
                    //Creating a dummy
                } else {
                    auditLogEntries[0]
                }
                guildLoggerExecutor.execute {
                    lastCheckedLogEntries[auditLogEntry.guild.idLong] = auditLogEntry
                }
            }
        }
    }

    @Transactional(readOnly = true)
    override fun onGuildMessageUpdate(event: GuildMessageUpdateEvent) {
        val loggingSettings = loggingSettingsRepository.findById(event.guild.idLong).orElse(LoggingSettings(event.guild.idLong))
        if (!loggingSettings.logMessageUpdate) {
            messageHistory.onGuildMessageUpdate(event)
            return
        }

        val guild = event.guild
        val channel = event.channel
        if (loggingSettings.ignoredChannels.contains(channel.idLong)) {
            return
        }

        val oldMessage = messageHistory.getMessage(java.lang.Long.parseUnsignedLong(event.messageId), false)

        if (oldMessage != null) {
            val name: String = try {
                JDALibHelper.getEffectiveNameAndUsername(oldMessage.guild.getMember(oldMessage.author))
            } catch (e: IllegalArgumentException) {
                oldMessage.author.name
            }
            val logEmbed = EmbedBuilder()
                    .setTitle("#" + channel.name + ": Message was modified!")
                    .setDescription("Old message was:\n" + oldMessage.contentDisplay)
                    .setColor(LIGHT_BLUE)
                    .addField("Author", name, true)
            linkEmotes(oldMessage.emotes, logEmbed)
            guildLoggerExecutor.execute { logger.log(logEmbed, oldMessage.author, guild, oldMessage.embeds, LogTypeAction.USER) }
        }
        messageHistory.onGuildMessageUpdate(event)
    }

    /**
     * This functions will be called each time a message is deleted on a discord
     * server.
     *
     * @param event The event that trigger this method
     */
    override fun onGuildMessageDelete(event: GuildMessageDeleteEvent) {
        val loggingSettings = loggingSettingsRepository.findById(event.guild.idLong).orElse(LoggingSettings(event.guild.idLong))
        if (!loggingSettings.logMessageDelete) {
            return
        }
        val guild = event.guild
        val channel = event.channel
        if (loggingSettings.ignoredChannels.contains(channel.idLong)) {
            return
        }

        val oldMessage = messageHistory.getMessage(java.lang.Long.parseUnsignedLong(event.messageId))
        if (oldMessage != null) {
            val attachmentString = messageHistory.getAttachmentsString(java.lang.Long.parseUnsignedLong(event.messageId))

            val name: String = try {
                JDALibHelper.getEffectiveNameAndUsername(oldMessage.guild.getMember(oldMessage.author))
            } catch (e: IllegalArgumentException) {
                oldMessage.author.name
            }
            guildLoggerExecutor.schedule({
                var moderator: User? = null
                run {
                    var i = 0
                    for (logEntry in event.guild.auditLogs.cache(false).limit(LOG_ENTRY_CHECK_LIMIT)) {
                        if (i == 0) {
                            guildLoggerExecutor.execute {
                                lastCheckedLogEntries[event.guild.idLong] = logEntry
                            }
                        }
                        if (!lastCheckedLogEntries.containsKey(event.guild.idLong)) {
                            i = LOG_ENTRY_CHECK_LIMIT
                        } else {
                            val cachedAuditLogEntry = lastCheckedLogEntries[event.guild.idLong]
                            if (logEntry.idLong == cachedAuditLogEntry?.idLong) {
                                if (logEntry.type == ActionType.MESSAGE_DELETE && logEntry.targetIdLong == oldMessage.author.idLong && logEntry.getOption<Any>(AuditLogOption.COUNT) != cachedAuditLogEntry.getOption<Any>(AuditLogOption.COUNT)) {
                                    moderator = logEntry.user
                                }
                                break
                            }
                        }
                        if (logEntry.type == ActionType.MESSAGE_DELETE && logEntry.targetIdLong == oldMessage.author.idLong) {
                            moderator = logEntry.user
                            break
                        }
                        i++
                        if (i >= LOG_ENTRY_CHECK_LIMIT) {
                            break
                        }
                    }
                }

                val logEmbed = EmbedBuilder()
                        .setTitle("#" + channel.name + ": Message was deleted!")
                        .setDescription("Old message was:\n" + oldMessage.contentDisplay)
                if (attachmentString != null) {
                    logEmbed.addField("Attachment(s)", attachmentString, false)
                }
                logEmbed.addField("Author", name, true)
                if (moderator != null) {
                    logEmbed.addField("Deleted by", JDALibHelper.getEffectiveNameAndUsername(event.guild.getMember(moderator)), true)
                            .setColor(Color.YELLOW)
                } else {
                    logEmbed.setColor(LIGHT_BLUE)
                }
                linkEmotes(oldMessage.emotes, logEmbed)
                logger.log(logEmbed, oldMessage.author, guild, oldMessage.embeds, if (moderator == null) LogTypeAction.USER else LogTypeAction.MODERATOR)
            }, 1, TimeUnit.SECONDS)
        }
    }

    private fun linkEmotes(emotes: MutableList<Emote>, logEmbed: EmbedBuilder) {
        if (emotes.isNotEmpty()) {
            val stringBuilder = StringBuilder()
            emotes.forEach {
                stringBuilder.append("[" + it.name + "](" + it.imageUrl + ")\n")
            }
            logEmbed.addField("Emote(s)", stringBuilder.toString(), false)
        }
    }

    @Transactional(readOnly = true)
    override fun onMessageBulkDelete(event: MessageBulkDeleteEvent) {
        val loggingSettings = loggingSettingsRepository.findById(event.guild.idLong).orElse(LoggingSettings(event.guild.idLong))
        if (!loggingSettings.logMessageDelete) {
            return
        }
        val channel = event.channel
        if (loggingSettings.ignoredChannels.contains(channel.idLong)) {
            return
        }

        val logEmbed = EmbedBuilder()
                .setColor(LIGHT_BLUE)
                .setTitle("#" + event.channel.name + ": Bulk delete")
                .addField("Amount of deleted messages", event.messageIds.size.toString(), false)

        var history: MessageHistory? = null
        for (messageHistory in MessageHistory.getInstanceList()) {
            try {
                if (event.jda === messageHistory.instance) {
                    history = messageHistory
                }
            } catch (ignored: MessageHistory.EmptyCacheException) {
            }
        }

        if (history == null) {
            //logBulkDelete(event, logEmbed)
            return
        }

        val logWriter = StringBuilder(event.channel.toString()).append("\n")

        var messageLogged = false
        event.messageIds.forEach { id ->
            val message = history.getMessage(java.lang.Long.parseUnsignedLong(id))
            if (message != null) {
                messageLogged = true
                logWriter.append(message.author.toString()).append(":\n").append(message.contentDisplay).append("\n\n")
                val attachmentString = history.getAttachmentsString(java.lang.Long.parseUnsignedLong(id))
                if (attachmentString != null) {
                    logWriter.append("Attachment(s):\n").append(attachmentString).append("\n")
                } else {
                    logWriter.append("\n")
                }
                val emotes = message.emotes
                if (emotes.isNotEmpty()) {
                    logWriter.append("Emote(s):\n")
                    emotes.forEach {
                        logWriter.append(it.name).append(": ").append(it.imageUrl).append('\n')
                    }
                    logWriter.append('\n')
                }
            }
        }
        if (messageLogged) {
            logWriter.append("Logged on ").append(OffsetDateTime.now().toString())
            logBulkDelete(event, logEmbed, logWriter.toString().toByteArray())
        } /*else {
            logBulkDelete(event, logEmbed)
        }*/

    }

    private fun logBulkDelete(event: MessageBulkDeleteEvent, logEmbed: EmbedBuilder, bytes: ByteArray) {
        guildLoggerExecutor.execute { logger.log(logEmbed, null, event.guild, null, LogTypeAction.USER, bytes) }
    }

    @Transactional(readOnly = true)
    override fun onGuildMemberLeave(event: GuildMemberLeaveEvent) {
        if (!loggingSettingsRepository.findById(event.guild.idLong).orElse(LoggingSettings(event.guild.idLong)).logMemberRemove) {
            return
        }

        guildLoggerExecutor.schedule({
            var moderator: User? = null
            var reason: String? = null
            run {
                var i = 0
                for (logEntry in event.guild.auditLogs.cache(false).limit(LOG_ENTRY_CHECK_LIMIT)) {
                    if (logEntry.type == ActionType.KICK && logEntry.targetIdLong == event.member.user.idLong && logEntry.idLong != lastCheckedLogEntries[event.guild.idLong]?.idLong) {
                        moderator = logEntry.user
                        reason = logEntry.reason
                        break
                    }
                    i++
                    if (i >= LOG_ENTRY_CHECK_LIMIT) {
                        break
                    }
                }
            }

            if (moderator != null && moderator == event.jda.selfUser) {
                return@schedule  //Bot is kicking no need to log, if needed it will be placed in the module that is issuing the kick.
            }


            if (moderator == null) {
                val logEmbed = EmbedBuilder()
                        .setColor(Color.RED)
                        .addField("User", JDALibHelper.getEffectiveNameAndUsername(event.member), true)
                        .setTitle("User left")
                logger.log(logEmbed, event.member.user, event.guild, null, if (moderator == null) LogTypeAction.USER else LogTypeAction.MODERATOR)
            } else {
                logKick(event.member, event.guild, event.guild.getMember(moderator), reason)
            }
        }, 1, TimeUnit.SECONDS)

    }

    fun logKick(member: Member, guild: Guild, moderator: Member?, reason: String?) {
        guildLoggerExecutor.execute {
            val logEmbed = EmbedBuilder()
                    .setColor(Color.RED)
                    .addField("User", JDALibHelper.getEffectiveNameAndUsername(member), true)
                    .setTitle("User kicked")
                    .addField("UUID", UUID.randomUUID().toString(), false)
                    .addField("Moderator", JDALibHelper.getEffectiveNameAndUsername(moderator), true)
            if (reason != null) {
                logEmbed.addField("Reason", reason, false)
            }
            logger.log(logEmbed, member.user, guild, null, LogTypeAction.MODERATOR)
        }
    }

    @Transactional(readOnly = true)
    override fun onGuildBan(event: GuildBanEvent) {
        if (!loggingSettingsRepository.findById(event.guild.idLong).orElse(LoggingSettings(event.guild.idLong)).logMemberBan) {
            return
        }

        guildLoggerExecutor.schedule({
            var moderator: User? = null
            var reason: String? = null
            run {
                var i = 0
                for (logEntry in event.guild.auditLogs.cache(false).limit(LOG_ENTRY_CHECK_LIMIT)) {
                    if (logEntry.type == ActionType.BAN && logEntry.targetIdLong == event.user.idLong) {
                        moderator = logEntry.user
                        reason = logEntry.reason
                        break
                    }
                    i++
                    if (i >= LOG_ENTRY_CHECK_LIMIT) {
                        break
                    }
                }
            }

            if (moderator != null && moderator == event.jda.selfUser) {
                return@schedule  //Bot is banning no need to log, if needed it will be placed in the module that is issuing the ban.
            }

            val logEmbed = EmbedBuilder()
                    .setColor(Color.RED)
                    .setTitle("User banned")
                    .addField("UUID", UUID.randomUUID().toString(), false)
                    .addField("User", event.user.name, true)
            if (moderator != null) {
                logEmbed.addField("Moderator", JDALibHelper.getEffectiveNameAndUsername(event.guild.getMember(moderator)), true)
                if (reason != null) {
                    logEmbed.addField("Reason", reason, false)
                }
            }
            logger.log(logEmbed, event.user, event.guild, null, LogTypeAction.MODERATOR)
        }, 1, TimeUnit.SECONDS)
    }

    @Transactional(readOnly = true)
    override fun onGuildMemberJoin(event: GuildMemberJoinEvent) {
        if (!loggingSettingsRepository.findById(event.guild.idLong).orElse(LoggingSettings(event.guild.idLong)).logMemberAdd) {
            return
        }

        val logEmbed = EmbedBuilder()
                .setColor(Color.GREEN)
                .setTitle("User joined", null)
                .addField("User", event.member.user.name, false)
                .addField("Account created", event.member.user.creationTime.format(DATE_TIME_FORMATTER), false)
        guildLoggerExecutor.execute { logger.log(logEmbed, event.member.user, event.guild, null, LogTypeAction.USER) }
    }

    @Transactional(readOnly = true)
    override fun onGuildUnban(event: GuildUnbanEvent) {
        if (!loggingSettingsRepository.findById(event.guild.idLong).orElse(LoggingSettings(event.guild.idLong)).logMemberRemoveBan) {
            return
        }

        guildLoggerExecutor.schedule({
            var moderator: User? = null
            run {
                var i = 0
                for (logEntry in event.guild.auditLogs.cache(false).limit(LOG_ENTRY_CHECK_LIMIT)) {
                    if (i == 0) {
                        guildLoggerExecutor.execute {
                            lastCheckedLogEntries[event.guild.idLong] = logEntry
                        }
                    }
                    if (logEntry.type == ActionType.UNBAN && logEntry.targetIdLong == event.user.idLong) {
                        moderator = logEntry.user
                        break
                    }
                    i++
                    if (i >= LOG_ENTRY_CHECK_LIMIT) {
                        break
                    }
                }
            }

            val logEmbed = EmbedBuilder()
                    .setColor(Color.GREEN)
                    .setTitle("User ban revoked", null)
                    .addField("User", event.user.name, true)
            if (moderator != null) {
                logEmbed.addField("Moderator", JDALibHelper.getEffectiveNameAndUsername(event.guild.getMember(moderator)), true)
            }
            logger.log(logEmbed, event.user, event.guild, null, LogTypeAction.MODERATOR)
        }, 1, TimeUnit.SECONDS)
    }

    override fun onUserUpdateName(event: UserUpdateNameEvent) {
        for (guild in logger.userOnGuilds(event.user)) {
            val logEmbed = EmbedBuilder()
                    .setColor(LIGHT_BLUE)
                    .setTitle("User has changed username")
                    .addField("Old username", event.oldName, false)
                    .addField("New username", event.newName, false)
            guildLoggerExecutor.execute { logger.log(logEmbed, event.user, guild, null, LogTypeAction.USER) }
        }
    }

    override fun onUserUpdateDiscriminator(event: UserUpdateDiscriminatorEvent) {
        for (guild in logger.userOnGuilds(event.user)) {
            val logEmbed = EmbedBuilder()
                    .setColor(LIGHT_BLUE)
                    .setTitle("User's discriminator changed")
                    .addField("Old discriminator", event.oldDiscriminator, false)
                    .addField("New discriminator", event.newDiscriminator, false)
            guildLoggerExecutor.execute { logger.log(logEmbed, event.user, guild, null, LogTypeAction.USER) }
        }
    }

    override fun onGuildMemberNickChange(event: GuildMemberNickChangeEvent) {
        guildLoggerExecutor.schedule({
            var moderator: User? = null
            run {
                var i = 0
                for (logEntry in event.guild.auditLogs.type(ActionType.MEMBER_UPDATE).cache(false).limit(LOG_ENTRY_CHECK_LIMIT)) {
                    if (logEntry.type == ActionType.MEMBER_UPDATE && logEntry.targetIdLong == event.member.user.idLong) {
                        moderator = logEntry.user
                        break
                    }
                    i++
                    if (i >= LOG_ENTRY_CHECK_LIMIT) {
                        break
                    }
                }
            }

            val logEmbed = EmbedBuilder()
                    .setColor(LIGHT_BLUE)
                    .addField("User", event.member.user.name, false)
                    .addField("Old nickname", if (event.prevNick != null) event.prevNick else "None", true)
                    .addField("New nickname", if (event.newNick != null) event.newNick else "None", true)
            if (moderator == null || moderator == event.member.user) {
                logEmbed.setTitle("User has changed nickname")
            } else {
                logEmbed.setTitle("Moderator has changed nickname")
                        .addField("Moderator", JDALibHelper.getEffectiveNameAndUsername(event.guild.getMember(moderator)), false)
            }
            logger.log(logEmbed, event.member.user, event.guild, null, if (moderator == null || moderator == event.member.user) LogTypeAction.USER else LogTypeAction.MODERATOR)
        }, 1, TimeUnit.SECONDS)
    }

    enum class LogTypeAction {
        MODERATOR, USER
    }

    @Component
    class LogSettings
    @Autowired constructor(
            private val loggingSettingsRepository: LoggingSettingsRepository
    ) : CommandModule(
            arrayOf("LogSettings"),
            null,
            "Adjust server settings.",
            requiredPermissions = *arrayOf(Permission.MANAGE_CHANNEL)
    ) {

        @Transactional
        override fun onGuildJoin(event: GuildJoinEvent) {
            loggingSettingsRepository.save(LoggingSettings(event.guild.idLong))
        }

        @Transactional
        override fun onGuildLeave(event: GuildLeaveEvent) {
            loggingSettingsRepository.deleteById(event.guild.idLong)
        }

        override fun commandExec(event: MessageReceivedEvent, command: String, arguments: String?) {
            event.jda.addEventListener(SettingsSequence(event.author, event.channel))
        }

        open inner class SettingsSequence(user: User, channel: MessageChannel) : Sequence(user, channel) {
            init {
                val settingFields = LoggingSettings::class.java.declaredFields.filter { it.type == Boolean::class.java }.map { it.name }
                val messageBuilder = MessageBuilder().append("Enter the number of the boolean you'd like to invert.\nIf you don't want to invert anything type \"STOP\" (case sensitive).\n\n")
                for (i in 0 until settingFields.size) {
                    val channelId = (channel as TextChannel).guild.idLong
                    messageBuilder.append(i)
                            .append(". ")
                            .append(settingFields[i])
                            .append(" = ")
                            .append(LoggingSettings::class.java.getMethod("get" + settingFields[i].capitalize()).invoke(loggingSettingsRepository.findById(channelId).orElse(LoggingSettings(channelId))))
                            .append('\n')
                }
                channel.sendMessage(messageBuilder.build()).queue { addMessageToCleaner(it) }
            }

            @Transactional
            override fun onMessageReceivedDuringSequence(event: MessageReceivedEvent) {
                val settingField = LoggingSettings::class.java.declaredFields.filter { it.type == Boolean::class.java }[event.message.contentRaw.toInt()].name.capitalize()
                val guildLoggingSettings = loggingSettingsRepository.findById(event.guild.idLong).orElse(LoggingSettings(event.guild.idLong))
                LoggingSettings::class.java.getMethod("set$settingField", Boolean::class.java)
                        .invoke(guildLoggingSettings, !(LoggingSettings::class.java.getMethod("get$settingField").invoke(guildLoggingSettings) as Boolean))
                loggingSettingsRepository.save(guildLoggingSettings)
                channel.sendMessage("Successfully inverted " + settingField.decapitalize() + ".").queue { it.delete().queueAfter(1, TimeUnit.MINUTES) }
                destroy()
            }
        }
    }
}
