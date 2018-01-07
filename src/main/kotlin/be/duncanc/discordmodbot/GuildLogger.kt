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

package be.duncanc.discordmodbot


import be.duncanc.discordmodbot.commands.CommandModule
import be.duncanc.discordmodbot.sequence.Sequence
import net.dv8tion.jda.core.EmbedBuilder
import net.dv8tion.jda.core.MessageBuilder
import net.dv8tion.jda.core.audit.ActionType
import net.dv8tion.jda.core.audit.AuditLogEntry
import net.dv8tion.jda.core.audit.AuditLogOption
import net.dv8tion.jda.core.entities.Guild
import net.dv8tion.jda.core.entities.Member
import net.dv8tion.jda.core.entities.MessageChannel
import net.dv8tion.jda.core.entities.User
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
import net.dv8tion.jda.core.events.message.guild.GuildMessageUpdateEvent
import net.dv8tion.jda.core.events.user.UserNameUpdateEvent
import net.dv8tion.jda.core.hooks.ListenerAdapter
import org.json.JSONObject
import org.slf4j.LoggerFactory
import java.awt.Color
import java.io.IOException
import java.io.Serializable
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Paths
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
 * The modifying the lastCheckedMessageDeleteEntries HashMap needs to happen using the guildLoggerExecutor because its
 * thread are executed sequentially there is no need to lock the object, however if you try to do this without using the
 * service the chances of hitting a ConcurrentModificationException are 100%.
 *
 * @author Duncan
 * @since 1.0
 */
class GuildLogger internal constructor(private val logger: be.duncanc.discordmodbot.LogToChannel, private val settings: LogSettings) : ListenerAdapter() {

    companion object {
        private val DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd-M-yyyy hh:mm a O")
        private val LOG = LoggerFactory.getLogger(GuildLogger::class.java)
        private val LIGHT_BLUE = Color(52, 152, 219)
        private const val LOG_ENTRY_CHECK_LIMIT = 5

        fun getCaseNumberSerializable(guildId: Long): Serializable {
            val caseNumber: Long = try {
                be.duncanc.discordmodbot.CaseSystem(guildId).newCaseNumber
            } catch (e: IOException) {
                -1
            }

            return if (caseNumber != -1L) caseNumber else "IOException - failed retrieving number"
        }
    }

    private val guildLoggerExecutor: ScheduledExecutorService
    private val lastCheckedMessageDeleteEntries: HashMap<Long, AuditLogEntry>

    init {
        this.guildLoggerExecutor = Executors.newSingleThreadScheduledExecutor { r ->
            val thread = Thread(ThrowableSafeRunnable(r, LOG), GuildLogger::class.java.simpleName)
            thread.isDaemon = true
            thread
        }
        this.lastCheckedMessageDeleteEntries = HashMap()
    }

    override fun onReady(event: ReadyEvent) {
        logger.initChannelList(event.jda)
        logger.logChannels.forEach { textChannel ->
            textChannel.guild.auditLogs.type(ActionType.MESSAGE_DELETE).limit(1).cache(false).queue { auditLogEntries ->
                val auditLogEntry = if (auditLogEntries.isEmpty()) {
                    AuditLogEntry(ActionType.MESSAGE_DELETE, -1, -1, textChannel.guild as GuildImpl, null, null, null, null)
                    //Creating a dummy
                } else {
                    auditLogEntries[0]
                }
                lastCheckedMessageDeleteEntries.put(auditLogEntry.guild.idLong, auditLogEntry)
            }
        }
    }

    override fun onGuildMessageUpdate(event: GuildMessageUpdateEvent) {
        if (!settings.getGuildSettings().filter { it.guildId == event.guild.idLong }[0].logMessageUpdate) {
            return
        }

        val guild = event.guild
        val channel = event.channel
        if (settings.isExceptedFromLogging(channel.idLong)) {
            return
        }

        var history: be.duncanc.discordmodbot.MessageHistory? = null
        for (messageHistory in be.duncanc.discordmodbot.MessageHistory.getInstanceList()) {
            try {
                if (event.jda === messageHistory.instance) {
                    history = messageHistory
                }
            } catch (ignored: be.duncanc.discordmodbot.MessageHistory.EmptyCacheException) {
            }

        }

        if (history == null) {
            return
        }

        val oldMessage = history.getMessage(java.lang.Long.parseUnsignedLong(event.messageId), false)
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
            guildLoggerExecutor.execute { logger.log(logEmbed, oldMessage.author, guild, oldMessage.embeds, LogTypeAction.USER) }
        }
    }

    /**
     * This functions will be called each time a message is deleted on a discord
     * server.
     *
     * @param event The event that trigger this method
     */
    override fun onGuildMessageDelete(event: GuildMessageDeleteEvent) {
        if (!settings.getGuildSettings().filter { it.guildId == event.guild.idLong }[0].logMessageDelete) {
            return
        }
        val guild = event.guild
        val channel = event.channel
        if (settings.isExceptedFromLogging(channel.idLong)) {
            return
        }

        var history: be.duncanc.discordmodbot.MessageHistory? = null
        for (messageHistory in be.duncanc.discordmodbot.MessageHistory.getInstanceList()) {
            try {
                if (event.jda === messageHistory.instance) {
                    history = messageHistory
                }
            } catch (ignored: be.duncanc.discordmodbot.MessageHistory.EmptyCacheException) {
            }

        }

        if (history == null) {
            guildLoggerExecutor.execute {
                val logEntry = event.guild.auditLogs.type(ActionType.MESSAGE_DELETE).cache(false).limit(1).complete()[0]
                lastCheckedMessageDeleteEntries.put(event.guild.idLong, logEntry)
            }
            return
        }

        val oldMessage = history.getMessage(java.lang.Long.parseUnsignedLong(event.messageId))
        if (oldMessage != null) {
            val attachmentString = history.getAttachmentsString(java.lang.Long.parseUnsignedLong(event.messageId))

            val name: String = try {
                JDALibHelper.getEffectiveNameAndUsername(oldMessage.guild.getMember(oldMessage.author))
            } catch (e: IllegalArgumentException) {
                oldMessage.author.name
            }
            guildLoggerExecutor.schedule({
                var moderator: User? = null
                run {
                    var i = 0
                    var firstCheckedAuditLogEntry: AuditLogEntry? = null
                    for (logEntry in event.guild.auditLogs.type(ActionType.MESSAGE_DELETE).cache(false).limit(LOG_ENTRY_CHECK_LIMIT)) {
                        if (i == 0) {
                            firstCheckedAuditLogEntry = logEntry
                        }
                        if (!lastCheckedMessageDeleteEntries.containsKey(event.guild.idLong)) {
                            i = LOG_ENTRY_CHECK_LIMIT
                        } else {
                            val cachedAuditLogEntry = lastCheckedMessageDeleteEntries[event.guild.idLong]
                            if (logEntry.idLong == cachedAuditLogEntry?.idLong) {
                                if (logEntry.targetIdLong == oldMessage.author.idLong && logEntry.getOption<Any>(AuditLogOption.COUNT) != cachedAuditLogEntry.getOption<Any>(AuditLogOption.COUNT)) {
                                    moderator = logEntry.user
                                }
                                break
                            }
                        }
                        if (logEntry.targetIdLong == oldMessage.author.idLong) {
                            moderator = logEntry.user
                            break
                        }
                        i++
                        if (i >= LOG_ENTRY_CHECK_LIMIT) {
                            break
                        }
                    }
                    if (firstCheckedAuditLogEntry != null) {
                        lastCheckedMessageDeleteEntries.put(event.guild.idLong, firstCheckedAuditLogEntry)
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
                logger.log(logEmbed, oldMessage.author, guild, oldMessage.embeds, if (moderator == null) LogTypeAction.USER else LogTypeAction.MODERATOR)
            }, 1, TimeUnit.SECONDS)
        }
    }

    override fun onMessageBulkDelete(event: MessageBulkDeleteEvent) {
        if (!settings.getGuildSettings().filter { it.guildId == event.guild.idLong }[0].logMessageDelete) {
            return
        }
        val channel = event.channel
        if (settings.isExceptedFromLogging(channel.idLong)) {
            return
        }

        val logEmbed = EmbedBuilder()
                .setColor(LIGHT_BLUE)
                .setTitle("#" + event.channel.name + ": Bulk delete")
                .addField("Amount of deleted messages", event.messageIds.size.toString(), false)

        var history: be.duncanc.discordmodbot.MessageHistory? = null
        for (messageHistory in be.duncanc.discordmodbot.MessageHistory.getInstanceList()) {
            try {
                if (event.jda === messageHistory.instance) {
                    history = messageHistory
                }
            } catch (ignored: be.duncanc.discordmodbot.MessageHistory.EmptyCacheException) {
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

    override fun onGuildMemberLeave(event: GuildMemberLeaveEvent) {
        if (!settings.getGuildSettings().filter { it.guildId == event.guild.idLong }[0].logMemberRemove) {
            return
        }

        guildLoggerExecutor.schedule({
            var moderator: User? = null
            var reason: String? = null
            run {
                var i = 0
                for (logEntry in event.guild.auditLogs.type(ActionType.KICK).cache(false).limit(LOG_ENTRY_CHECK_LIMIT)) {
                    if (logEntry.targetIdLong == event.member.user.idLong) {
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
                    .setTitle("User kicked | Case: " + getCaseNumberSerializable(guild.idLong))
                    .addField("Moderator", JDALibHelper.getEffectiveNameAndUsername(moderator), true)
            if (reason != null) {
                logEmbed.addField("Reason", reason, false)
            }
            logger.log(logEmbed, member.user, guild, null, LogTypeAction.MODERATOR)
        }
    }

    override fun onGuildBan(event: GuildBanEvent) {
        if (!settings.getGuildSettings().filter { it.guildId == event.guild.idLong }[0].logMemberBan) {
            return
        }

        guildLoggerExecutor.schedule({
            var moderator: User? = null
            var reason: String? = null
            run {
                var i = 0
                for (logEntry in event.guild.auditLogs.type(ActionType.BAN).cache(false).limit(LOG_ENTRY_CHECK_LIMIT)) {
                    if (logEntry.targetIdLong == event.user.idLong) {
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
                    .setTitle("User banned | Case: " + getCaseNumberSerializable(event.guild.idLong))
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

    override fun onGuildMemberJoin(event: GuildMemberJoinEvent) {
        if (!settings.getGuildSettings().filter { it.guildId == event.guild.idLong }[0].logMemberAdd) {
            return
        }

        val logEmbed = EmbedBuilder()
                .setColor(Color.GREEN)
                .setTitle("User joined", null)
                .addField("User", event.member.user.name, false)
                .addField("Account created", event.member.user.creationTime.format(DATE_TIME_FORMATTER), false)
        guildLoggerExecutor.execute { logger.log(logEmbed, event.member.user, event.guild, null, LogTypeAction.USER) }
    }


    override fun onGuildUnban(event: GuildUnbanEvent) {
        if (!settings.getGuildSettings().filter { it.guildId == event.guild.idLong }[0].logMemberRemoveBan) {
            return
        }

        guildLoggerExecutor.schedule({
            var moderator: User? = null
            run {
                var i = 0
                for (logEntry in event.guild.auditLogs.type(ActionType.UNBAN).cache(false).limit(LOG_ENTRY_CHECK_LIMIT)) {
                    if (logEntry.targetIdLong == event.user.idLong) {
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

    override fun onUserNameUpdate(event: UserNameUpdateEvent) {
        for (guild in logger.userOnGuilds(event.user)) {
            val logEmbed = EmbedBuilder()
                    .setColor(LIGHT_BLUE)
                    .setTitle("User has changed username")
                    .addField("Old username & discriminator", event.oldName + "#" + event.oldDiscriminator, false)
                    .addField("New username & discriminator", event.user.name + "#" + event.user.discriminator, false)
            guildLoggerExecutor.execute { logger.log(logEmbed, event.user, guild, null, LogTypeAction.USER) }
        }
    }

    override fun onGuildMemberNickChange(event: GuildMemberNickChangeEvent) {
        guildLoggerExecutor.schedule({
            var moderator: User? = null
            run {
                var i = 0
                for (logEntry in event.guild.auditLogs.type(ActionType.MEMBER_UPDATE).cache(false).limit(LOG_ENTRY_CHECK_LIMIT)) {
                    if (logEntry.targetIdLong == event.member.user.idLong) {
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

    object LogSettings : CommandModule(arrayOf("LogSettings"), null, "Adjust server settings.") {

        private val FILE_PATH = Paths.get("GuildSettings.json")
        private const val OWNER_ID = 159419654148718593L
        private val exceptedFromLogging = ArrayList<Long>()
        private val guildSettings = HashSet<GuildSettings>()

        init {
            loadGuildSettingFromFile()
        }

        private fun writeGuildSettingToFile() {
            synchronized(FILE_PATH) {
                synchronized(exceptedFromLogging) {
                    synchronized(guildSettings) {
                        val jsonObject = JSONObject()
                        jsonObject.put("exceptedFromLogging", exceptedFromLogging)
                        jsonObject.put("guildSettings", guildSettings)
                        Files.write(FILE_PATH, Collections.singletonList(jsonObject.toString()))
                    }
                }
            }
        }

        private fun loadGuildSettingFromFile() {
            synchronized(FILE_PATH) {
                synchronized(exceptedFromLogging) {
                    synchronized(guildSettings) {
                        try {
                            val stringBuilder = StringBuilder()
                            Files.readAllLines(FILE_PATH).map { stringBuilder.append(it) }
                            val jsonObject = JSONObject(stringBuilder.toString())
                            jsonObject.getJSONArray("guildSettings").forEach {
                                it as JSONObject
                                guildSettings.add(GuildSettings(it.getLong("guildId"), it.getBoolean("logMessageDelete"), it.getBoolean("logMessageUpdate"), it.getBoolean("logMemberRemove"), it.getBoolean("logMemberBan"), it.getBoolean("logMemberAdd"), it.getBoolean("logMemberRemoveBan")))
                            }
                            jsonObject.getJSONArray("exceptedFromLogging").forEach {

                                exceptedFromLogging.add(it.toString().toLong())
                            }
                        } catch (ignored: NoSuchFileException) {
                        }
                    }
                }
            }
        }

        init {
            try {
                loadGuildSettingFromFile()
            } catch (e: NoSuchFileException) {
                LoggerFactory.getLogger(CommandModule::class.java).warn("Loading config file failed", e)
            }
        }

        fun isExceptedFromLogging(channelId: Long): Boolean {
            synchronized(exceptedFromLogging) {
                return exceptedFromLogging.contains(channelId)
            }
        }

        fun getGuildSettings(): Set<GuildSettings> {
            synchronized(guildSettings) {
                return Collections.unmodifiableSet(guildSettings)
            }
        }

        override fun onReady(event: ReadyEvent) {
            synchronized(guildSettings) {
                event.jda.guilds.forEach {
                    val settings = GuildSettings(it.idLong)
                    guildSettings.add(settings)
                    if (it.idLong == 175856762677624832L) {
                        settings.logMessageUpdate = false
                    }
                }
            }
            writeGuildSettingToFile()
        }

        override fun onGuildJoin(event: GuildJoinEvent) {
            synchronized(guildSettings) {
                guildSettings.add(GuildSettings(event.guild.idLong))
            }
        }

        override fun onGuildLeave(event: GuildLeaveEvent) {
            synchronized(guildSettings) {
                guildSettings.removeIf { it.guildId == event.guild.idLong }
            }
        }

        override fun commandExec(event: MessageReceivedEvent, command: String, arguments: String?) {
            if (event.author.idLong == OWNER_ID) {
                event.jda.addEventListener(SettingsSequence(event.author, event.channel))
            } else {
                event.channel.sendMessage("Sorry, only the owner can use this command right now.").queue { it.delete().queueAfter(1, TimeUnit.MINUTES) }
            }
        }

        class SettingsSequence(user: User, channel: MessageChannel) : Sequence(user, channel) {
            private val eventManger: EventsManager? = try {
                super.channel.jda.registeredListeners.filter { it is be.duncanc.discordmodbot.EventsManager }[0] as be.duncanc.discordmodbot.EventsManager
            } catch (e: IndexOutOfBoundsException) {
                null
            }
            private var sequenceNumber = 0.toByte()

            init {
                super.channel.sendMessage("What would you like to do? Respond with the number of the action you'd like to perform?\n\n" +
                        "0. Add or remove this guild from the event manager\n" +
                        "1. Modify log settings").queue { super.addMessageToCleaner(it) }
            }

            override fun onMessageReceivedDuringSequence(event: MessageReceivedEvent) {
                val guildId = event.guild.idLong

                when (sequenceNumber) {
                    0.toByte() -> {
                        when (event.message.contentRaw.toByte()) {
                            0.toByte() -> {
                                if (eventManger != null) {
                                    val response: Array<String> = if (eventManger.events.containsKey(guildId)) {
                                        arrayOf("already", "remove")
                                    } else {
                                        arrayOf("not", "add")
                                    }
                                    super.channel.sendMessageFormat("The guild is currently %s in the list to use the event manager, do you want to %s it? Respond with \"yes\" or \"no\".", response[0], response[1]).queue { addMessageToCleaner(it) }
                                } else {
                                    throw UnsupportedOperationException("This bot does not have an event manager")
                                }
                                sequenceNumber = 1
                            }
                            1.toByte() -> {
                                val settingFields = GuildSettings::class.java.declaredFields.filter { it.type == Boolean::class.java }.map { it.name }
                                val messageBuilder = MessageBuilder().append("Enter the number of the boolean you'd like to invert.\nIf you don't want to invert anything type \"STOP\" (case sensitive).\n\n")
                                for (i in 0 until settingFields.size) {
                                    messageBuilder.append(i)
                                            .append(". ")
                                            .append(settingFields[i])
                                            .append(" = ")
                                            .append(GuildSettings::class.java.getMethod("get" + settingFields[i].capitalize()).invoke(guildSettings.filter { it.guildId == event.guild.idLong }[0]))
                                            .append('\n')
                                }
                                channel.sendMessage(messageBuilder.build()).queue { addMessageToCleaner(it) }
                                sequenceNumber = 2
                            }
                        }
                    }
                    1.toByte() -> {
                        when (event.message.contentRaw.toLowerCase()) {
                            "yes" -> {
                                if (eventManger!!.events.containsKey(guildId)) {
                                    eventManger.events.remove(guildId)
                                } else {
                                    eventManger.events.put(guildId, ArrayList())
                                }
                                eventManger.cleanExpiredEvents()
                                eventManger.writeEventsToFile()
                                super.channel.sendMessage("Executed without problem.").queue { it.delete().queueAfter(1, TimeUnit.MINUTES) }
                                super.destroy()
                            }
                            else -> {
                                super.destroy()
                            }
                        }
                    }
                    2.toByte() -> {
                        val settingField = GuildSettings::class.java.declaredFields.filter { it.type == Boolean::class.java }[event.message.contentRaw.toInt()].name.capitalize()
                        GuildSettings::class.java.getMethod("set" + settingField, Boolean::class.java)
                                .invoke(guildSettings.filter { it.guildId == event.guild.idLong }[0], !(GuildSettings::class.java.getMethod("get" + settingField).invoke(guildSettings.filter { it.guildId == event.guild.idLong }[0]) as Boolean))
                        writeGuildSettingToFile()
                        channel.sendMessage("Successfully inverted " + settingField.decapitalize() + ".").queue { it.delete().queueAfter(1, TimeUnit.MINUTES) }
                        destroy()
                    }
                }
            }
        }

        class GuildSettings @JvmOverloads constructor(val guildId: Long, var logMessageDelete: Boolean = true, var logMessageUpdate: Boolean = true, var logMemberRemove: Boolean = true, var logMemberBan: Boolean = true, var logMemberAdd: Boolean = true, var logMemberRemoveBan: Boolean = true) {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (javaClass != other?.javaClass) return false

                other as GuildSettings

                return guildId == other.guildId
            }

            override fun hashCode(): Int {
                return guildId.hashCode()
            }
        }
    }
}
