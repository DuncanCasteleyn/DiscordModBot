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
import net.dv8tion.jda.core.entities.MessageChannel
import net.dv8tion.jda.core.entities.User
import net.dv8tion.jda.core.events.ReadyEvent
import net.dv8tion.jda.core.events.guild.GuildJoinEvent
import net.dv8tion.jda.core.events.guild.GuildLeaveEvent
import net.dv8tion.jda.core.events.message.MessageReceivedEvent
import net.dv8tion.jda.core.utils.SimpleLog
import org.json.JSONObject
import org.slf4j.event.Level
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * This allows for settings to be adjusted.
 *
 * @author Duncan
 * @since 1.0.0
 */

//todo Add GuildSettings to sequence
internal open class Settings : CommandModule(ALIAS, null, DESCRIPTION) {

    companion object {
        private val FILE_PATH = Paths.get("GuildSettings.json")
        private val ALIAS = arrayOf("settings")
        private const val DESCRIPTION = "Adjust server settings."
        private const val OWNER_ID = 159419654148718593L
        private val exceptedFromLogging = ArrayList<Long>()
        private val guildSettings = ArrayList<GuildSettings>()

        init {
            loadGuildSettingFromFile()
        }

        private fun writeGuildSettingToFile() {
            synchronized(this) {
                val jsonObject = JSONObject()
                jsonObject.put("guildSettings", guildSettings)
                jsonObject.put("exceptedFromLogging", exceptedFromLogging)
                Files.write(FILE_PATH, Collections.singletonList(jsonObject.toString()))
            }
        }

        private fun loadGuildSettingFromFile() {
            synchronized(this) {
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
            }
        }
    }

    init {
        try {
            loadGuildSettingFromFile()
        } catch (e: NoSuchFileException) {
            SimpleLog.getLog(CommandModule::class.java.simpleName).log(Level.WARN, e)
        }
    }

    fun isExceptedFromLogging(channelId: Long): Boolean {
        synchronized(Companion) {
            return exceptedFromLogging.contains(channelId)
        }
    }

    fun getGuildSettings(): List<GuildSettings> {
        synchronized(Companion) {
            return Collections.unmodifiableList(guildSettings)
        }
    }

    override fun onReady(event: ReadyEvent) {
        synchronized(Companion) {
            event.jda.guilds.forEach {
                val settings = GuildSettings(it.idLong)
                guildSettings.add(settings)
                if (it.idLong == 175856762677624832L) {
                    settings.isLogMessageUpdate = false
                }
            }
        }
    }

    override fun onGuildJoin(event: GuildJoinEvent) {
        synchronized(Companion) {
            guildSettings.add(GuildSettings(event.guild.idLong))
        }
    }

    override fun onGuildLeave(event: GuildLeaveEvent) {
        synchronized(Companion) {
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

    inner class SettingsSequence(user: User, channel: MessageChannel) : Sequence(user, channel) {
        private val eventManger: be.duncanc.discordmodbot.EventsManager
        private var sequenceNumber = 0.toByte()

        init {
            try {
                eventManger = super.channel.jda.registeredListeners.filter { it is be.duncanc.discordmodbot.EventsManager }[0] as be.duncanc.discordmodbot.EventsManager
            } catch (e: IndexOutOfBoundsException) {
                destroy()
                throw UnsupportedOperationException("This bot does not have an event manager", e)
            }
            super.channel.sendMessage("What would you like to do? Respond with the number of the action you'd like to perform.\n\n" +
                    "0. Add or remove this guild from the event manager").queue { super.addMessageToCleaner(it) }
        }

        override fun onMessageReceivedDuringSequence(event: MessageReceivedEvent) {
            val guildId = event.guild.idLong

            when (sequenceNumber) {
                0.toByte() -> {
                    when (event.message.rawContent.toByte()) {
                        0.toByte() -> {
                            val response: Array<String> = if (eventManger.events.containsKey(guildId)) {
                                arrayOf("already", "remove")
                            } else {
                                arrayOf("not", "add")
                            }
                            super.channel.sendMessageFormat("The guild is currently %s in the list to use the event manager, do you want to %s it? Respond with \"yes\" or \"no\".", response[0], response[1]).queue { addMessageToCleaner(it) }
                            sequenceNumber = 1
                        }
                    }
                }
                1.toByte() -> {
                    when (event.message.rawContent.toLowerCase()) {
                        "yes" -> {
                            if (eventManger.events.containsKey(guildId)) {
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
            }
        }
    }

    class GuildSettings @JvmOverloads constructor(val guildId: Long, isLogMessageDelete: Boolean = true, isLogMessageUpdate: Boolean = true, isLogMemberRemove: Boolean = true, isLogMemberBan: Boolean = true, isLogMemberAdd: Boolean = true, isLogMemberRemoveBan: Boolean = true) {
        var isLogMemberRemoveBan = isLogMemberRemoveBan
            set(value) {
                field = value
                writeGuildSettingToFile()
            }
        var isLogMemberAdd = isLogMemberAdd
            set(value) {
                field = value
                writeGuildSettingToFile()
            }
        var isLogMemberBan = isLogMemberBan
            set(value) {
                field = value
                writeGuildSettingToFile()
            }
        var isLogMemberRemove = isLogMemberRemove
            set(value) {
                field = value
                writeGuildSettingToFile()
            }
        var isLogMessageUpdate = isLogMessageUpdate
            set(value) {
                field = value
                writeGuildSettingToFile()
            }
        var isLogMessageDelete = isLogMessageDelete
            set(value) {
                field = value
                writeGuildSettingToFile()
            }
    }
}
