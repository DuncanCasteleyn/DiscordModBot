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
import org.json.JSONObject
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * This allows for settings to be adjusted.
 *
 * @author Duncan
 * @since 1.0.0
 */

//todo remove hard coded code.
internal open class Settings : CommandModule(ALIAS, null, DESCRIPTION) {

    companion object {
        private val FILE_PATH = Paths.get("GuildSettings.json")
        private val ALIAS = arrayOf("settings")
        private const val DESCRIPTION = "Adjust server settings."
        private const val OWNER_ID = 159419654148718593L
    }

    private val exceptedFromLogging = ArrayList<Long>()
    private val guildSettings = ArrayList<GuildSettings>()

    init {
        exceptedFromLogging.add(231422572011585536L)
        exceptedFromLogging.add(205415791238184969L)
        exceptedFromLogging.add(204047108318298112L)
    }

    fun writeGuildSettingToFile() {
        val jsonObject = JSONObject()
        jsonObject.put("guildSettings", guildSettings)
        jsonObject.put("exceptedFromLogging", exceptedFromLogging)
        Files.write(FILE_PATH, Collections.singletonList(jsonObject.toString()))
    }

    fun loadGuildSettingFromFile() {
        val stringBuilder = StringBuilder()
        Files.readAllLines(FILE_PATH).map { stringBuilder.append(it) }
        val jsonObject = JSONObject(stringBuilder.toString())
        jsonObject["guildSettings"].to(guildSettings)
        jsonObject["exceptedFromLogging"].to(exceptedFromLogging)
    }

    fun isExceptedFromLogging(channelId: Long): Boolean {
        return exceptedFromLogging.contains(channelId)
    }

    fun getGuildSettings(): List<GuildSettings> {
        return Collections.unmodifiableList(guildSettings)
    }

    override fun onReady(event: ReadyEvent) {
        event.jda.guilds.forEach {
            val settings = GuildSettings(it.idLong)
            guildSettings.add(settings)
            if (it.idLong == 175856762677624832L) {
                settings.isLogMessageUpdate = false
            }
        }
    }

    override fun onGuildJoin(event: GuildJoinEvent) {
        guildSettings.add(GuildSettings(event.guild.idLong))
    }

    override fun onGuildLeave(event: GuildLeaveEvent) {
        guildSettings.removeIf { it.guildId == event.guild.idLong }
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

    inner class GuildSettings @JvmOverloads constructor(val guildId: Long, var isLogMessageDelete: Boolean = true, var isLogMessageUpdate: Boolean = true, var isLogMemberRemove: Boolean = true, var isLogMemberBan: Boolean = true, var isLogMemberAdd: Boolean = true, var isLogMemberRemoveBan: Boolean = true)
}
