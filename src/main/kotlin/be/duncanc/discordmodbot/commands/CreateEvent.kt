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

package be.duncanc.discordmodbot.commands

import be.duncanc.discordmodbot.sequences.Sequence
import net.dv8tion.jda.core.JDA
import net.dv8tion.jda.core.Permission
import net.dv8tion.jda.core.entities.*
import net.dv8tion.jda.core.events.ReadyEvent
import net.dv8tion.jda.core.events.message.MessageReceivedEvent
import net.dv8tion.jda.core.events.message.guild.GuildMessageDeleteEvent
import net.dv8tion.jda.core.events.message.guild.react.GuildMessageReactionAddEvent
import net.dv8tion.jda.core.events.message.guild.react.GuildMessageReactionRemoveEvent
import net.dv8tion.jda.core.events.role.RoleDeleteEvent
import org.json.JSONArray
import org.json.JSONObject
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList

object CreateEvent : CommandModule(arrayOf("CreateEvent"), "<event id/name> <subscribers role> <emote to react to> <event text>", "Creates an event, including role and message to announce the event", requiredPermissions = *arrayOf(Permission.MANAGE_ROLES)) {
    private val FILE = Paths.get("EventsTool.json")
    private val events = HashMap<Guild, ArrayList<EventRole>>()

    override fun onReady(event: ReadyEvent) {
        load(event.jda)
    }

    override fun commandExec(event: MessageReceivedEvent, command: String, arguments: String?) {
        event.jda.addEventListener(EventCreationSequence(event.author, event.textChannel))
    }

    override fun onGuildMessageReactionAdd(event: GuildMessageReactionAddEvent) {
        synchronized(events) {
            val serverEvent = events[event.guild]
            if (serverEvent != null && serverEvent.any { it.announceMessage.idLong == event.messageIdLong }) {
                val reactedEvent = serverEvent.filter { it.announceMessage.idLong == event.messageIdLong }[0]
                event.guild.controller.addSingleRoleToMember(event.member, reactedEvent.eventRole).reason("Voted on event reaction").queue()
            }
        }
    }

    override fun onGuildMessageReactionRemove(event: GuildMessageReactionRemoveEvent) {
        synchronized(events) {
            val serverEvent = events[event.guild]
            if (serverEvent != null && serverEvent.any { it.announceMessage.idLong == event.messageIdLong }) {
                val reactedEvent = serverEvent.filter { it.announceMessage.idLong == event.messageIdLong }[0]
                event.guild.controller.removeSingleRoleFromMember(event.member, reactedEvent.eventRole).reason("Remove vote on event reaction").queue()
            }
        }
    }

    override fun onGuildMessageDelete(event: GuildMessageDeleteEvent) {
        synchronized(events) {
            val serverEvent = events[event.guild]
            if (serverEvent != null && serverEvent.any { it.announceMessage.idLong == event.messageIdLong }) {
                val toRemove = serverEvent.filter { it.announceMessage.idLong == event.messageIdLong }.toList()
                toRemove.forEach {
                    it.eventRole.delete().queue()
                }
                serverEvent.removeAll(toRemove)
            }
        }
    }

    override fun onRoleDelete(event: RoleDeleteEvent) {
        synchronized(events) {
            val serverEvent = events[event.guild]
            if (serverEvent != null && serverEvent.any { it.eventRole.idLong == event.role.idLong }) {
                val toRemove = serverEvent.filter { it.eventRole.idLong == event.role.idLong }.toList()
                toRemove.forEach {
                    it.announceMessage.delete().queue()
                }
                serverEvent.removeAll(toRemove)
            }
        }
    }

    private fun save() {
        synchronized(FILE) {
            val json = JSONObject()
            synchronized(events) {
                events.forEach {
                    val jsonArray = JSONArray()
                    it.value.forEach {
                        val eventRoleJSONObject = JSONObject()
                        eventRoleJSONObject.put("eventId", it.eventId)
                        eventRoleJSONObject.put("eventRole", it.eventRole.idLong)
                        eventRoleJSONObject.put("reactEmote", it.reactEmote.idLong)
                        eventRoleJSONObject.put("announceMessage", it.announceMessage.idLong)
                        eventRoleJSONObject.put("announceChannel", it.announceMessage.textChannel.idLong)
                        jsonArray.put(eventRoleJSONObject)
                    }
                    json.put(it.key.id, jsonArray)
                }
            }
            Files.write(FILE, Collections.singletonList(json.toString()))
        }
    }

    private fun load(jda: JDA) {
        if (FILE.toFile().exists()) {
            synchronized(events) {
                val stringBuilder = StringBuilder()
                synchronized(FILE) {
                    Files.readAllLines(FILE).forEach {
                        stringBuilder.append(it)
                    }
                }
                val jsonObject = JSONObject(stringBuilder.toString())
                val loadedEvents = HashMap<Guild, ArrayList<EventRole>>()
                jsonObject.keys().forEach {
                    val guild = jda.getGuildById(it)
                    if (guild != null) {
                        val eventsArray = jsonObject.getJSONArray(it)
                        val newArrayList = ArrayList<EventRole>()
                        eventsArray.forEach {
                            it as JSONObject
                            try {
                                newArrayList.add(EventRole(it.getString("eventId"), jda.getRoleById(it.getLong("eventRole")), jda.getEmoteById(it.getLong("reactEmote")), jda.getTextChannelById(it.getLong("announceChannel")).getMessageById(it.getLong("announceMessage")).complete()))
                            } catch (ignored: Exception) {
                            }
                        }
                        loadedEvents[guild] = newArrayList
                    }
                }
                events.putAll(loadedEvents)
            }
        }
    }

    class EventCreationSequence(user: User, channel: MessageChannel) : Sequence(user, channel, cleanAfterSequence = true, informUser = true) {
        private var eventName: String? = null
        private var eventRole: String? = null
        private var reactEmote: Emote? = null
        private var announceChannel: TextChannel? = null

        init {
            channel.sendMessage("Please enter the event id/name").queue { super.addMessageToCleaner(it) }
        }

        override fun onMessageReceivedDuringSequence(event: MessageReceivedEvent) = when {
            eventName == null -> {
                eventName = event.message.contentStripped
                channel.sendMessage("What should the name of the new event role be?").queue { super.addMessageToCleaner(it) }
            }
            eventRole == null -> {
                eventRole = event.message.contentDisplay
                channel.sendMessage("Please post the emote to be used.").queue { super.addMessageToCleaner(it) }
            }
            reactEmote == null -> {
                reactEmote = event.message.emotes[0]
                channel.sendMessage("Please mention the channel were you want the announcement to be made.").queue { super.addMessageToCleaner(it) }
            }
            announceChannel == null -> {
                announceChannel = event.message.mentionedChannels[0]
                channel.sendMessage("Please enter the announcement text.").queue { super.addMessageToCleaner(it) }
            }
            else -> {
                val guild = event.guild

                val newRoleFuture = guild.controller.createRole().submit()
                val announceFuture = announceChannel!!.sendMessage(event.message.contentRaw).submit()
                try {
                    val changeRoleName = newRoleFuture.get().manager.setName(eventRole).submit()
                    val reactFuture = announceFuture.get().addReaction(reactEmote).submit()
                    try {
                        changeRoleName.get()
                        reactFuture.get()
                    } catch (exception: Exception) {
                        try {
                            announceFuture.get().delete().queue()
                        } catch (ignored: InterruptedException) {
                        } catch (ignored: ExecutionException) {
                        } catch (ignored: CancellationException) {
                        }
                        try {
                            announceFuture.get().delete().queue()
                        } catch (ignored: InterruptedException) {
                        } catch (ignored: ExecutionException) {
                        } catch (ignored: CancellationException) {
                        }
                        throw exception
                    }
                } catch (exception: Exception) {
                    if (!newRoleFuture.cancel(false)) {
                        try {
                            newRoleFuture.get().delete().queue()
                        } catch (ignored: InterruptedException) {
                        } catch (ignored: ExecutionException) {
                        } catch (ignored: CancellationException) {
                        }
                    }
                    if (!announceFuture.cancel(false) && announceFuture.isDone) {
                        try {
                            announceFuture.get().delete().queue()
                        } catch (ignored: InterruptedException) {
                        } catch (ignored: ExecutionException) {
                        } catch (ignored: CancellationException) {
                        }
                    }
                    throw exception
                }
                val eventRole = EventRole(eventName!!, newRoleFuture.get(), reactEmote!!, announceFuture.get())
                synchronized(events) {
                    if (events[event.guild] != null) {
                        events[event.guild]!!.add(eventRole)
                    } else {
                        val newArrayList = ArrayList<EventRole>()
                        newArrayList.add(eventRole)
                        events[event.guild] = newArrayList
                    }
                    save()
                    super.channel.sendMessage(super.user.asMention + " All tasks where completed without errors.").queue { it.delete().queueAfter(1, TimeUnit.MINUTES) }
                    destroy()
                }
            }
        }
    }


    class EventRole(val eventId: String, val eventRole: Role, val reactEmote: Emote, internal val announceMessage: Message)
}