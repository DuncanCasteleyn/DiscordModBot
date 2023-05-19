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
import be.duncanc.discordmodbot.bot.sequences.MessageSequence
import be.duncanc.discordmodbot.bot.sequences.Sequence
import be.duncanc.discordmodbot.bot.utils.nicknameAndUsername
import be.duncanc.discordmodbot.data.entities.LoggingSettings
import be.duncanc.discordmodbot.data.redis.hash.DiscordMessage
import be.duncanc.discordmodbot.data.repositories.jpa.LoggingSettingsRepository
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.MessageBuilder
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.audit.ActionType
import net.dv8tion.jda.api.audit.AuditLogEntry
import net.dv8tion.jda.api.audit.AuditLogOption
import net.dv8tion.jda.api.entities.*
import net.dv8tion.jda.api.events.ReadyEvent
import net.dv8tion.jda.api.events.guild.GuildBanEvent
import net.dv8tion.jda.api.events.guild.GuildLeaveEvent
import net.dv8tion.jda.api.events.guild.GuildUnbanEvent
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent
import net.dv8tion.jda.api.events.guild.member.update.GuildMemberUpdateNicknameEvent
import net.dv8tion.jda.api.events.message.MessageBulkDeleteEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.events.message.guild.GuildMessageDeleteEvent
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import net.dv8tion.jda.api.events.message.guild.GuildMessageUpdateEvent
import net.dv8tion.jda.api.events.user.update.UserUpdateDiscriminatorEvent
import net.dv8tion.jda.api.events.user.update.UserUpdateNameEvent
import net.dv8tion.jda.api.exceptions.PermissionException
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.internal.entities.GuildImpl
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
        val LIGHT_BLUE = Color(52, 152, 219)
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
        val loggingSettings =
            loggingSettingsRepository.findById(event.guild.idLong).orElse(LoggingSettings(event.guild.idLong))
        if (loggingSettings.ignoredChannels.contains(event.channel.idLong)) {
            return
        }
        messageHistory.storeMessage(event)
    }

    override fun onReady(event: ReadyEvent) {
        val guilds =
            loggingSettingsRepository.findAll().map { it.guildId.let { id -> event.jda.getGuildById(id) } }.toHashSet()
        guilds.forEach { guild ->
            guild ?: return@forEach
            guild.retrieveAuditLogs().limit(1).cache(false).queue { auditLogEntries ->
                val auditLogEntry = if (auditLogEntries.isEmpty()) {
                    AuditLogEntry(
                        ActionType.MESSAGE_DELETE,
                        -1,
                        -1,
                        -1,
                        guild as GuildImpl,
                        null,
                        null,
                        null,
                        null,
                        null
                    )
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
        val loggingSettings =
            loggingSettingsRepository.findById(event.guild.idLong).orElse(LoggingSettings(event.guild.idLong))
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
            event.jda.retrieveUserById(oldMessage.userId).queue { user ->
                val name: String = try {
                    event.jda.getGuildChannelById(oldMessage.channelId)?.guild?.getMember(user)?.nicknameAndUsername
                        ?: user.name
                } catch (e: IllegalArgumentException) {
                    user.name
                }
                val logEmbed = EmbedBuilder()
                    .setTitle("#" + channel.name + ": Message was modified!")
                    .setDescription("Old message was:\n" + oldMessage.content)
                    .setColor(LIGHT_BLUE)
                    .addField("Author", name, true)
                    .addField("Message URL", "[Link](${oldMessage.jumpUrl})", false)
                oldMessage.emotes?.let {
                    logEmbed.addField("Emote(s)", it, false)
                }

                guildLoggerExecutor.execute {
                    log(
                        logEmbed,
                        user,
                        guild,
                        null,
                        LogTypeAction.USER
                    )
                }
            }
            messageHistory.updateMessage(event)
        }
    }

    /**
     * This functions will be called each time a message is deleted on a discord
     * server.
     *
     * @param event The event that trigger this method
     */
    @Transactional(readOnly = true)
    override fun onGuildMessageDelete(event: GuildMessageDeleteEvent) {
        val loggingSettings =
            loggingSettingsRepository.findById(event.guild.idLong).orElse(LoggingSettings(event.guild.idLong))
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

            event.jda.retrieveUserById(oldMessage.userId).queue { user ->

                val name: String = try {
                    event.jda.getGuildChannelById(oldMessage.channelId)?.guild?.getMember(user)?.nicknameAndUsername
                        ?: user.name
                } catch (e: IllegalArgumentException) {
                    user.name
                }
                guildLoggerExecutor.schedule({
                    val moderator = findModerator(event, oldMessage)

                    if (moderator != null && moderator == event.jda.selfUser) {
                        return@schedule  //Bot has removed message no need to log, if needed it will be placed in the module that is issuing the remove.
                    }

                    val logEmbed = EmbedBuilder()
                        .setTitle("#" + channel.name + ": Message was deleted!")
                        .setDescription("Old message was:\n" + oldMessage.content)
                    if (attachmentString != null) {
                        logEmbed.addField("Attachment(s)", attachmentString, false)
                    }
                    logEmbed.addField("Author", name, true)
                    if (moderator != null) {
                        logEmbed.addField("Deleted by", event.guild.getMember(moderator)?.nicknameAndUsername, true)
                            .setColor(Color.YELLOW)
                    } else {
                        logEmbed.setColor(LIGHT_BLUE)
                    }
                    logEmbed.addField("Message URL", "[Link](${oldMessage.jumpUrl})", false)
                    oldMessage.emotes?.let {
                        logEmbed.addField("Emote(s)", oldMessage.emotes, false)
                    }
                    log(
                        logEmbed,
                        user,
                        guild,
                        null,
                        if (moderator == null) LogTypeAction.USER else LogTypeAction.MODERATOR
                    )
                }, 1, TimeUnit.SECONDS)
            }
        }
    }

    private fun findModerator(event: GuildMessageDeleteEvent, oldMessage: DiscordMessage): User? {
        var foundModerator: User? = null
        var i = 0
        for (logEntry in event.guild.retrieveAuditLogs().cache(false).limit(LOG_ENTRY_CHECK_LIMIT)) {
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
                    if (logEntry.type == ActionType.MESSAGE_DELETE && logEntry.targetIdLong == oldMessage.userId && logEntry.getOption<Any>(
                            AuditLogOption.COUNT
                        ) != cachedAuditLogEntry.getOption<Any>(AuditLogOption.COUNT)
                    ) {
                        foundModerator = logEntry.user
                    }
                    break
                }
            }
            if (logEntry.type == ActionType.MESSAGE_DELETE && logEntry.targetIdLong == oldMessage.userId) {
                foundModerator = logEntry.user
                break
            }
            i++
            if (i >= LOG_ENTRY_CHECK_LIMIT) {
                break
            }
        }
        return foundModerator
    }

    @Transactional(readOnly = true)
    override fun onMessageBulkDelete(event: MessageBulkDeleteEvent) {
        val loggingSettings =
            loggingSettingsRepository.findById(event.guild.idLong).orElse(LoggingSettings(event.guild.idLong))
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
                event.jda.retrieveUserById(message.userId).queue { user ->
                    logWriter.append(user.toString()).append(":\n").append(message.content).append("\n\n")
                    val attachmentString = messageHistory.getAttachmentsString(idLong)
                    if (attachmentString != null) {
                        logWriter.append("Attachment(s):\n").append(attachmentString).append("\n")
                    } else {
                        logWriter.append("\n")
                    }
                    message.emotes?.let {
                        logWriter.append("Emote(s):\n")
                        logWriter.append(it)
                    }
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
    override fun onGuildMemberRemove(event: GuildMemberRemoveEvent) {
        if (!loggingSettingsRepository.findById(event.guild.idLong)
                .orElse(LoggingSettings(event.guild.idLong)).logMemberLeave
        ) {
            return
        }

        guildLoggerExecutor.schedule({
            val moderator: User?
            val reason: String?
            run {
                var findModerator: User? = null
                var findReason: String? = null
                var i = 0
                for (logEntry in event.guild.retrieveAuditLogs().cache(false).limit(LOG_ENTRY_CHECK_LIMIT)) {
                    if (logEntry.type == ActionType.KICK && logEntry.targetIdLong == event.user.idLong && logEntry.idLong != lastCheckedLogEntries[event.guild.idLong]?.idLong) {
                        findModerator = logEntry.user
                        findReason = logEntry.reason
                        break
                    }
                    i++
                    if (i >= LOG_ENTRY_CHECK_LIMIT) {
                        break
                    }
                }
                moderator = findModerator
                reason = findReason
            }

            if (moderator != null && moderator == event.jda.selfUser) {
                return@schedule  //Bot is kicking no need to log, if needed it will be placed in the module that is issuing the kick.
            }


            if (moderator == null) {
                val logEmbed = EmbedBuilder()
                    .setColor(Color.RED)
                    .addField("User", event.user.name, true)
                    .setTitle("User left")
                log(
                    logEmbed,
                    event.user,
                    event.guild,
                    null,
                    LogTypeAction.USER
                )
            } else {
                logKick(event.user, event.guild, event.guild.getMember(moderator), reason)
            }
        }, 1, TimeUnit.SECONDS)

    }

    fun logKick(user: User, guild: Guild, moderator: Member?, reason: String?) {
        guildLoggerExecutor.execute {
            val logEmbed = EmbedBuilder()
                .setColor(Color.RED)
                .setTitle("User kicked")
                .addField("UUID", UUID.randomUUID().toString(), false)
                .addField("User", user.name, true)
            moderator?.let { logEmbed.addField("Moderator", it.nicknameAndUsername, true) }
            reason?.let { logEmbed.addField("Reason", it, false) }
            log(logEmbed, user, guild, null, LogTypeAction.MODERATOR)
        }
    }

    @Transactional(readOnly = true)
    override fun onGuildBan(event: GuildBanEvent) {
        if (!loggingSettingsRepository.findById(event.guild.idLong)
                .orElse(LoggingSettings(event.guild.idLong)).logMemberBan
        ) {
            return
        }

        guildLoggerExecutor.schedule({
            val moderator: User?
            val reason: String?
            run {
                var findModerator: User? = null
                var findReason: String? = null
                var i = 0
                for (logEntry in event.guild.retrieveAuditLogs().cache(false).limit(LOG_ENTRY_CHECK_LIMIT)) {
                    if (logEntry.type == ActionType.BAN && logEntry.targetIdLong == event.user.idLong) {
                        findModerator = logEntry.user
                        findReason = logEntry.reason
                        break
                    }
                    i++
                    if (i >= LOG_ENTRY_CHECK_LIMIT) {
                        break
                    }
                }
                moderator = findModerator
                reason = findReason
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
                logEmbed.addField(
                    "Moderator", event.guild.getMember(moderator)?.nicknameAndUsername
                        ?: moderator.name, true
                )
                if (reason != null) {
                    logEmbed.addField("Reason", reason, false)
                }
            }
            log(logEmbed, event.user, event.guild, null, LogTypeAction.MODERATOR)
        }, 1, TimeUnit.SECONDS)
    }

    @Transactional(readOnly = true)
    override fun onGuildMemberJoin(event: GuildMemberJoinEvent) {
        if (!loggingSettingsRepository.findById(event.guild.idLong)
                .orElse(LoggingSettings(event.guild.idLong)).logMemberJoin
        ) {
            return
        }

        val logEmbed = EmbedBuilder()
            .setColor(Color.GREEN)
            .setTitle("User joined", null)
            .addField("User", event.member.user.name, false)
            .addField("Account created", event.member.user.timeCreated.format(DATE_TIME_FORMATTER), false)
        guildLoggerExecutor.execute { log(logEmbed, event.member.user, event.guild, null, LogTypeAction.USER) }
    }

    @Transactional(readOnly = true)
    override fun onGuildUnban(event: GuildUnbanEvent) {
        if (!loggingSettingsRepository.findById(event.guild.idLong)
                .orElse(LoggingSettings(event.guild.idLong)).logMemberRemoveBan
        ) {
            return
        }

        guildLoggerExecutor.schedule({
            val moderator: User? = run {
                var findModerator: User? = null
                var i = 0
                for (logEntry in event.guild.retrieveAuditLogs().cache(false).limit(LOG_ENTRY_CHECK_LIMIT)) {
                    if (i == 0) {
                        guildLoggerExecutor.execute {
                            lastCheckedLogEntries[event.guild.idLong] = logEntry
                        }
                    }
                    if (logEntry.type == ActionType.UNBAN && logEntry.targetIdLong == event.user.idLong) {
                        findModerator = logEntry.user
                        break
                    }
                    i++
                    if (i >= LOG_ENTRY_CHECK_LIMIT) {
                        break
                    }
                }
                findModerator
            }

            val logEmbed = EmbedBuilder()
                .setColor(Color.GREEN)
                .setTitle("User ban revoked", null)
                .addField("User", event.user.name, true)
            if (moderator != null) {
                logEmbed.addField(
                    "Moderator", event.guild.getMember(moderator)?.nicknameAndUsername
                        ?: moderator.name, true
                )
            }
            log(logEmbed, event.user, event.guild, null, LogTypeAction.MODERATOR)
        }, 1, TimeUnit.SECONDS)
    }

    override fun onUserUpdateName(event: UserUpdateNameEvent) {
        for (guild in getGuildsWithLogging(event.jda)) {
            guild ?: continue
            guild.getMember(event.user) ?: continue
            val logEmbed = EmbedBuilder()
                .setColor(LIGHT_BLUE)
                .setTitle("User has changed username")
                .addField("Old username", event.oldName, false)
                .addField("New username", event.newName, false)
            guildLoggerExecutor.execute { log(logEmbed, event.user, guild, null, LogTypeAction.USER) }
        }
    }

    private fun getGuildsWithLogging(jda: JDA): Set<Guild?> =
        loggingSettingsRepository.findAll().map { it.guildId.let { id -> jda.getGuildById(id) } }.toHashSet()

    override fun onGuildMemberUpdateNickname(event: GuildMemberUpdateNicknameEvent) {
        guildLoggerExecutor.schedule({
            val moderator: User? = run {
                var findModerator: User? = null
                var i = 0
                for (logEntry in event.guild.retrieveAuditLogs().type(ActionType.MEMBER_UPDATE).cache(false).limit(
                    LOG_ENTRY_CHECK_LIMIT
                )) {
                    if (logEntry.type == ActionType.MEMBER_UPDATE && logEntry.targetIdLong == event.member.user.idLong) {
                        findModerator = logEntry.user
                        break
                    }
                    i++
                    if (i >= LOG_ENTRY_CHECK_LIMIT) {
                        break
                    }
                }
                findModerator
            }

            val logEmbed = EmbedBuilder()
                .setColor(LIGHT_BLUE)
                .addField("User", event.member.user.name, false)
                .addField("Old nickname", if (event.oldNickname != null) event.oldNickname else "None", true)
                .addField("New nickname", if (event.newNickname != null) event.newNickname else "None", true)
            if (moderator == null || moderator == event.member.user) {
                logEmbed.setTitle("User has changed nickname")
            } else {
                logEmbed.setTitle("Moderator has changed nickname")
                    .addField(
                        "Moderator", event.guild.getMember(moderator)?.nicknameAndUsername
                            ?: moderator.name, false
                    )
            }
            log(
                logEmbed,
                event.member.user,
                event.guild,
                null,
                if (moderator == null || moderator == event.member.user) LogTypeAction.USER else LogTypeAction.MODERATOR
            )
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
        requiredPermissions = arrayOf(Permission.MANAGE_CHANNEL)
    ) {

        @Transactional
        override fun onGuildLeave(event: GuildLeaveEvent) {
            loggingSettingsRepository.deleteById(event.guild.idLong)
        }

        override fun commandExec(event: MessageReceivedEvent, command: String, arguments: String?) {
            event.jda.addEventListener(SettingsSequence(event.author, event.channel))
        }

        open inner class SettingsSequence(user: User, channel: MessageChannel) :
            Sequence(user, channel), MessageSequence {
            private var sequenceNumber: Byte = 0

            init {
                if (channel !is TextChannel) {
                    throw UnsupportedOperationException("Command needs to be executed in a TextChannel")
                }
                val guildId = channel.guild.idLong
                val logSettings = loggingSettingsRepository.findById(guildId).orElse(LoggingSettings(guildId))
                channel.sendMessage(
                    "Enter number of the action you'd like to perform:\n\n" +
                            "0. Set the mod logging channel. Currently: ${
                                logSettings.modLogChannel?.let { "<#$it>" }
                                    ?: "None (Required to set before setting/changing other setting)"
                            }\n" +
                            "1. Set the user logging channel. Currently: ${
                                logSettings.userLogChannel?.let { "<#$it>" }
                                    ?: "Using same channel as mod logging"
                            }\n" +
                            "2. " + (if (logSettings.logMessageUpdate) "Disable" else "Enable ") + " logging for edited messages.\n" +
                            "3. " + (if (logSettings.logMessageDelete) "Disable" else "Enable ") + " logging for deleted messages.\n" +
                            "4. " + (if (logSettings.logMemberJoin) "Disable" else "Enable ") + " logging for members joining.\n" +
                            "5. " + (if (logSettings.logMemberLeave) "Disable" else "Enable ") + " logging for members leaving (includes kicks).\n" +
                            "6. " + (if (logSettings.logMemberBan) "Disable" else "Enable ") + " logging for banning members.\n" +
                            "7. " + (if (logSettings.logMemberBan) "Disable" else "Enable ") + " logging for removing bans.")
                    .queue { addMessageToCleaner(it) }
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
                                channel.sendMessage("${user.asMention} Please mention the channel you want to be used as moderator log.")
                                    .queue { addMessageToCleaner(it) }
                                return
                            }
                            1.toByte() -> {
                                sequenceNumber = 2
                                channel.sendMessage("${user.asMention} Please mention the channel you want to be used as user log.")
                                    .queue { addMessageToCleaner(it) }
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
                channel.sendMessage("${user.asMention} Settings successfully saved.")
                    .queue { it.delete().queueAfter(1, TimeUnit.MINUTES) }
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
    fun log(
        logEmbed: EmbedBuilder,
        associatedUser: User? = null,
        guild: Guild,
        embeds: List<MessageEmbed>? = null,
        actionType: LogTypeAction,
        bytes: ByteArray? = null
    ) {
        val logSettings = loggingSettingsRepository.findById(guild.idLong).orElse(null)
            ?: return

        val targetChannel: TextChannel = if (actionType === LogTypeAction.MODERATOR) {
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
                targetChannel.sendMessageEmbeds(logEmbed.build()).queue()
            } else {
                targetChannel.sendFile(bytes, "chat.log").queue {
                    it.editMessage(MessageBuilder().setEmbeds(logEmbed.build()).build()).queue()
                }
            }
            if (embeds != null) {
                for (embed in embeds) {
                    targetChannel.sendMessage(
                        MessageBuilder().setEmbeds(embed)
                            .append("The embed below was deleted with the previous message")
                            .build()
                    )
                        .queue()
                }
            }
        } catch (e: PermissionException) {
            LOG.warn(
                e.javaClass.simpleName + ": " + e.message + "\n" +
                        "Guild: " + guild.toString()
            )
        }
    }
}
