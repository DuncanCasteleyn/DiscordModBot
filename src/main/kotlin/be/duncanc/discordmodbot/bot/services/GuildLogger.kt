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


import be.duncanc.discordmodbot.bot.commands.CommandModule
import be.duncanc.discordmodbot.bot.sequences.Sequence
import be.duncanc.discordmodbot.bot.utils.JDALibHelper
import be.duncanc.discordmodbot.data.entities.LoggingSettings
import be.duncanc.discordmodbot.data.repositories.LoggingSettingsRepository
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.dv8tion.jda.core.EmbedBuilder
import net.dv8tion.jda.core.JDA
import net.dv8tion.jda.core.MessageBuilder
import net.dv8tion.jda.core.Permission
import net.dv8tion.jda.core.audit.ActionType
import net.dv8tion.jda.core.audit.AuditLogEntry
import net.dv8tion.jda.core.audit.AuditLogOption
import net.dv8tion.jda.core.entities.*
import net.dv8tion.jda.core.entities.impl.GuildImpl
import net.dv8tion.jda.core.events.ReadyEvent
import net.dv8tion.jda.core.events.guild.GuildBanEvent
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
import net.dv8tion.jda.core.exceptions.PermissionException
import net.dv8tion.jda.core.hooks.ListenerAdapter
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.awt.Color
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * This file will create the listeners and the appropriate action to be taken
 * upon each of the listener.
 *
 *
 * IMPORTANT READ BEFORE MODIFYING CODE:
 * Modifying the lastCheckedLogEntries HashMap needs to happen using the guildLoggerExecutor because it's
 * single-threaded and executed sequentially there is no need to lock the object, however if you try to do this without using the
 * service the chances of hitting a ConcurrentModificationException is 100%.
 *
 * @author Duncan
 * @since 1.0
 */
@Component
class GuildLogger
@Autowired constructor(
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
            thread(name = GuildLogger::class.java.simpleName, start = false, isDaemon = false) {
                r.run()
            }
        }
        this.lastCheckedLogEntries = HashMap()
    }

    @Transactional(readOnly = true)
    override fun onGuildMessageReceived(event: GuildMessageReceivedEvent) {
        val loggingSettings = loggingSettingsRepository.findById(event.guild.idLong).orElse(LoggingSettings(event.guild.idLong))
        if (loggingSettings.ignoredChannels.contains(event.channel.idLong)) {
            return
        }
        messageHistory.storeMessage(event)
    }

    override fun onReady(event: ReadyEvent) {
        val guilds = loggingSettingsRepository.findAll().map { it.guildId?.let { id -> event.jda.getGuildById(id) } }.toHashSet()
        guilds.forEach { guild ->
            guild ?: return@forEach
            guild.auditLogs.limit(1).cache(false).queue { auditLogEntries ->
                val auditLogEntry = if (auditLogEntries.isEmpty()) {
                    AuditLogEntry(ActionType.MESSAGE_DELETE, -1, -1, guild as GuildImpl, null, null, null, null, null)
                    //Creating a dummy
                } else {
                    auditLogEntries[0]
                }
                guildLoggerExecutor.execute {
                    lastCheckedLogEntries[auditLogEntry.guild.idLong] = auditLogEntry
                }
            }
            GlobalScope.launch {
                guild.textChannels.forEach { textChannel ->
                    try {
                        messageHistory.cacheHistoryOfChannel(textChannel)
                    } catch (ignored: PermissionException) {
                    }
                }
            }
        }
    }

    @Transactional(readOnly = true)
    override fun onGuildMessageUpdate(event: GuildMessageUpdateEvent) {
        val loggingSettings = loggingSettingsRepository.findById(event.guild.idLong).orElse(LoggingSettings(event.guild.idLong))
        if (!loggingSettings.logMessageUpdate) {
            messageHistory.updateMessage(event)
            return
        }

        val guild = event.guild
        val channel = event.channel
        if (loggingSettings.ignoredChannels.contains(channel.idLong)) {
            return
        }

        val oldMessage = messageHistory.getMessage(event.channel.idLong, event.messageIdLong, false)

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
            guildLoggerExecutor.execute { log(logEmbed, oldMessage.author, guild, oldMessage.embeds, LogTypeAction.USER) }
        }
        messageHistory.updateMessage(event)
    }

    /**
     * This functions will be called each time a message is deleted on a discord
     * server.
     *
     * @param event The event that trigger this method
     */
    @Transactional(readOnly = true)
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

        val oldMessage = messageHistory.getMessage(event.channel.idLong, event.messageIdLong)
        if (oldMessage != null) {
            val attachmentString = messageHistory.getAttachmentsString(event.messageIdLong)

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
                log(logEmbed, oldMessage.author, guild, oldMessage.embeds, if (moderator == null) LogTypeAction.USER else LogTypeAction.MODERATOR)
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

        val logWriter = StringBuilder(event.channel.toString()).append("\n")

        var messageLogged = false
        event.messageIds.forEach { id ->
            val idLong = java.lang.Long.parseUnsignedLong(id)
            val message = messageHistory.getMessage(event.channel.idLong, idLong)
            if (message != null) {
                messageLogged = true
                logWriter.append(message.author.toString()).append(":\n").append(message.contentDisplay).append("\n\n")
                val attachmentString = messageHistory.getAttachmentsString(idLong)
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
        }

    }

    private fun logBulkDelete(event: MessageBulkDeleteEvent, logEmbed: EmbedBuilder, bytes: ByteArray) {
        guildLoggerExecutor.execute { log(logEmbed, null, event.guild, null, LogTypeAction.USER, bytes) }
    }

    @Transactional(readOnly = true)
    override fun onGuildMemberLeave(event: GuildMemberLeaveEvent) {
        if (!loggingSettingsRepository.findById(event.guild.idLong).orElse(LoggingSettings(event.guild.idLong)).logMemberLeave) {
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
                log(logEmbed, event.member.user, event.guild, null, if (moderator == null) LogTypeAction.USER else LogTypeAction.MODERATOR)
            } else {
                logKick(event.member, event.guild, event.guild.getMember(moderator), reason)
            }
        }, 1, TimeUnit.SECONDS)

    }

    fun logKick(member: Member, guild: Guild, moderator: Member?, reason: String?) {
        guildLoggerExecutor.execute {
            val logEmbed = EmbedBuilder()
                    .setColor(Color.RED)
                    .setTitle("User kicked")
                    .addField("UUID", UUID.randomUUID().toString(), false)
                    .addField("User", JDALibHelper.getEffectiveNameAndUsername(member), true)
                    .addField("Moderator", JDALibHelper.getEffectiveNameAndUsername(moderator), true)
            if (reason != null) {
                logEmbed.addField("Reason", reason, false)
            }
            log(logEmbed, member.user, guild, null, LogTypeAction.MODERATOR)
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
            log(logEmbed, event.user, event.guild, null, LogTypeAction.MODERATOR)
        }, 1, TimeUnit.SECONDS)
    }

    @Transactional(readOnly = true)
    override fun onGuildMemberJoin(event: GuildMemberJoinEvent) {
        if (!loggingSettingsRepository.findById(event.guild.idLong).orElse(LoggingSettings(event.guild.idLong)).logMemberJoin) {
            return
        }

        val logEmbed = EmbedBuilder()
                .setColor(Color.GREEN)
                .setTitle("User joined", null)
                .addField("User", event.member.user.name, false)
                .addField("Account created", event.member.user.creationTime.format(DATE_TIME_FORMATTER), false)
        guildLoggerExecutor.execute { log(logEmbed, event.member.user, event.guild, null, LogTypeAction.USER) }
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
            log(logEmbed, event.user, event.guild, null, LogTypeAction.MODERATOR)
        }, 1, TimeUnit.SECONDS)
    }

    override fun onUserUpdateName(event: UserUpdateNameEvent) {
        for (guild in getGuildsWithLogging(event.jda)) {
            guild ?: continue
            val logEmbed = EmbedBuilder()
                    .setColor(LIGHT_BLUE)
                    .setTitle("User has changed username")
                    .addField("Old username", event.oldName, false)
                    .addField("New username", event.newName, false)
            guildLoggerExecutor.execute { log(logEmbed, event.user, guild, null, LogTypeAction.USER) }
        }
    }

    override fun onUserUpdateDiscriminator(event: UserUpdateDiscriminatorEvent) {
        for (guild in getGuildsWithLogging(event.jda)) {
            guild ?: continue
            val logEmbed = EmbedBuilder()
                    .setColor(LIGHT_BLUE)
                    .setTitle("User's discriminator changed")
                    .addField("Old discriminator", event.oldDiscriminator, false)
                    .addField("New discriminator", event.newDiscriminator, false)
            guildLoggerExecutor.execute { log(logEmbed, event.user, guild, null, LogTypeAction.USER) }
        }
    }

    private fun getGuildsWithLogging(jda: JDA): Set<Guild?> =
            loggingSettingsRepository.findAll().map { it.guildId?.let { id -> jda.getGuildById(id) } }.toHashSet()

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
            log(logEmbed, event.member.user, event.guild, null, if (moderator == null || moderator == event.member.user) LogTypeAction.USER else LogTypeAction.MODERATOR)
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
        override fun onGuildLeave(event: GuildLeaveEvent) {
            loggingSettingsRepository.deleteById(event.guild.idLong)
        }

        override fun commandExec(event: MessageReceivedEvent, command: String, arguments: String?) {
            event.jda.addEventListener(SettingsSequence(event.author, event.channel))
        }

        open inner class SettingsSequence(user: User, channel: MessageChannel) : Sequence(user, channel) {
            private var sequenceNumber: Byte = 0

            init {
                if (channel !is TextChannel) {
                    throw UnsupportedOperationException("Command needs to be executed in a TextChannel")
                }
                val guildId = channel.guild.idLong
                val logSettings = loggingSettingsRepository.findById(guildId).orElse(LoggingSettings(guildId))
                channel.sendMessage("Enter number of the action you'd like to perform:\n\n" +
                        "0. Set the mod logging channel. Currently: ${logSettings.modLogChannel?.let { "<#$it>" }
                                ?: "None (Required to set before setting/changing other setting)"}\n" +
                        "1. Set the user logging channel. Currently: ${logSettings.userLogChannel?.let { "<#$it>" }
                                ?: "Using same channel as mod logging"}\n" +
                        "2. " + (if (logSettings.logMessageUpdate) "Disable" else "Enable ") + " logging for edited messages.\n" +
                        "3. " + (if (logSettings.logMessageDelete) "Disable" else "Enable ") + " logging for deleted messages.\n" +
                        "4. " + (if (logSettings.logMemberJoin) "Disable" else "Enable ") + " logging for members joining.\n" +
                        "5. " + (if (logSettings.logMemberLeave) "Disable" else "Enable ") + " logging for members leaving (includes kicks).\n" +
                        "6. " + (if (logSettings.logMemberBan) "Disable" else "Enable ") + " logging for banning members.\n" +
                        "7. " + (if (logSettings.logMemberBan) "Disable" else "Enable ") + " logging for removing bans.").queue { addMessageToCleaner(it) }
            }

            @Transactional
            override fun onMessageReceivedDuringSequence(event: MessageReceivedEvent) {
                channel as TextChannel
                val guildId = channel.guild.idLong
                val logSettings = loggingSettingsRepository.findById(guildId).orElse(LoggingSettings(guildId))
                when (sequenceNumber) {
                    0.toByte() -> {
                        when (event.message.contentRaw.toByte()) {
                            0.toByte() -> {
                                sequenceNumber = 1
                                channel.sendMessage("${user.asMention} Please mention the channel you want to be used as moderator log.").queue { addMessageToCleaner(it) }
                                return
                            }
                            1.toByte() -> {
                                sequenceNumber = 2
                                channel.sendMessage("${user.asMention} Please mention the channel you want to be used as user log.").queue { addMessageToCleaner(it) }
                                return
                            }
                            2.toByte() -> {
                                logSettings.logMessageUpdate = !logSettings.logMessageUpdate
                            }
                            3.toByte() -> {
                                logSettings.logMessageDelete = !logSettings.logMessageDelete
                            }
                            4.toByte() -> {
                                logSettings.logMemberJoin = !logSettings.logMemberJoin
                            }
                            5.toByte() -> {
                                logSettings.logMemberLeave = !logSettings.logMemberLeave
                            }
                            6.toByte() -> {
                                logSettings.logMemberBan = !logSettings.logMemberBan
                            }
                            7.toByte() -> {
                                logSettings.logMemberRemoveBan = !logSettings.logMemberRemoveBan
                            }
                        }
                    }
                    1.toByte() -> {
                        logSettings.modLogChannel = event.message.mentionedChannels[0].idLong
                    }
                    2.toByte() -> {
                        logSettings.userLogChannel = event.message.mentionedChannels[0].idLong
                    }
                }
                loggingSettingsRepository.save(logSettings)
                channel.sendMessage("${user.asMention} Settings successfully saved.").queue { it.delete().queueAfter(1, TimeUnit.MINUTES) }
                destroy()
            }
        }
    }

    /**
     * Logs to the log channel
     *
     * @param logEmbed An embed to be used as log message a time stamp will be added to the footer and
     * @param guild    The guild where the message needs to be logged to
     */
    fun log(logEmbed: EmbedBuilder, associatedUser: User?, guild: Guild, embeds: List<MessageEmbed>?, actionType: GuildLogger.LogTypeAction, bytes: ByteArray? = null) {
        val logSettings = loggingSettingsRepository.findById(guild.idLong).orElse(null)
                ?: return

        val targetChannel: TextChannel = if (actionType === GuildLogger.LogTypeAction.MODERATOR) {
            logSettings.modLogChannel?.let { guild.getTextChannelById(it) }
        } else {
            logSettings.userLogChannel?.let { guild.getTextChannelById(it) }
                    ?: logSettings.modLogChannel?.let { guild.getTextChannelById(it) }
        } ?: return

        try {
            logEmbed.setTimestamp(OffsetDateTime.now())
            if (associatedUser != null) {
                logEmbed.setFooter(associatedUser.id, associatedUser.effectiveAvatarUrl)
            }
            if (bytes == null) {
                targetChannel.sendMessage(logEmbed.build()).queue()
            } else {
                targetChannel.sendFile(bytes, "chat.log", MessageBuilder().setEmbed(logEmbed.build()).build()).queue()
            }
            if (embeds != null) {
                for (embed in embeds) {
                    targetChannel.sendMessage(MessageBuilder().setEmbed(embed).append("The embed below was deleted with the previous message").build()).queue()
                }
            }
        } catch (e: PermissionException) {
            LOG.warn(e.javaClass.simpleName + ": " + e.message + "\n" +
                    "Guild: " + guild.toString())
        }
    }
}
