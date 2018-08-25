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
import be.duncanc.discordmodbot.data.entities.GuildMemberGate
import be.duncanc.discordmodbot.data.repositories.GuildMemberGateRepository
import net.dv8tion.jda.core.MessageBuilder
import net.dv8tion.jda.core.Permission
import net.dv8tion.jda.core.entities.*
import net.dv8tion.jda.core.events.guild.member.GuildMemberJoinEvent
import net.dv8tion.jda.core.events.guild.member.GuildMemberLeaveEvent
import net.dv8tion.jda.core.events.guild.member.GuildMemberRoleAddEvent
import net.dv8tion.jda.core.events.message.MessageReceivedEvent
import org.apache.commons.collections4.map.LinkedMap
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList


/**
 * Created by Duncan on 30/04/2017.
 * <p>
 * Welcomes users when the join, get accepted and makes them answer questions before they get accepted.
 */
@Component
@Transactional(readOnly = true)
class MemberGate
internal constructor(
        private val guildMemberGateRepository: GuildMemberGateRepository
) : CommandModule(
        arrayOf("gateConfig", "join", "review"),
        null,
        null,
        ignoreWhitelist = true
) {
    private val approvalQueue = LinkedMap<Long, String?>()
    private val informUserMessageIds = HashMap<Long, Long>()

    /**
     * Check if a user was added to the approved role.
     */
    override fun onGuildMemberRoleAdd(event: GuildMemberRoleAddEvent) {
        val guild = event.guild
        val guildMemberGate = guildMemberGateRepository.findById(guild.idLong)
        if (!guildMemberGate.isPresent || event.user.isBot || guild.getRoleById(guildMemberGate.get().memberRole!!) !in event.roles) {
            return
        }
        val welcomeMessages = guildMemberGate.get().welcomeMessages.toTypedArray()
        val welcomeMessage = welcomeMessages[Random().nextInt(welcomeMessages.size)].getWelcomeMessage(event.user)
        guild.getTextChannelById(guildMemberGate.get().welcomeTextChannel!!).sendMessage(welcomeMessage).queue()
        synchronized(approvalQueue) {
            approvalQueue.remove(event.user.idLong)
        }
        cleanMessagesFromUser(event.guild.getTextChannelById(guildMemberGate.get().gateTextChannel!!), event.user)
    }

    /**
     * Cleans the messages from the users and messages containing mentions to the users from the member gate channel.
     */
    private fun cleanMessagesFromUser(gateTextChannel: TextChannel, user: User) {
        val userMessages: ArrayList<Message> = ArrayList()
        gateTextChannel.iterableHistory.map {
            if (it.author == user || it.contentRaw.contains(user.id)) {
                userMessages.add(it)
            }
        }
        JDALibHelper.limitLessBulkDelete(gateTextChannel, userMessages)
    }

    /**
     * Welcomes a new member that joins and informs them about the member gate system.
     */
    override fun onGuildMemberJoin(event: GuildMemberJoinEvent) {
        val guildMemberGate = guildMemberGateRepository.findById(event.guild.idLong)
        if (!guildMemberGate.isPresent || event.user.isBot) {
            return
        }
        val gateTextChannel = event.guild.getTextChannelById(guildMemberGate.get().gateTextChannel!!)
        if (event.guild.verificationLevel == Guild.VerificationLevel.VERY_HIGH) {
            gateTextChannel.sendMessage("Welcome " + event.member.asMention + ", this server uses phone verification.\n" +
                    "If you have verified your phone and are able to chat in this channel, you can simply type ``!join`` to join the server.\n" +
                    "If you can't use phone verification, send " + event.jda.selfUser.asMention + " a dm and type ``!nomobile``. You will be granted a special role! After that, return to this channel and type ``!join`` and follow the instructions.\n" +
                    "\n" +
                    "**Warning: Users that are not mobile verified will be punished much more severely and faster when breaking the rules or when suspected of bypassing a ban.**").queue { message -> message.delete().queueAfter(5, TimeUnit.MINUTES) }
        } else {
            gateTextChannel.sendMessage("Welcome " + event.member.asMention + ", this server requires you to read the " + event.guild.getTextChannelById(guildMemberGate.get().rulesTextChannel!!).asMention + " and answer a question regarding those before you gain full access.\n\n" +
                    "If you have read the rules and are ready to answer the question, type ``!" + super.aliases[1] + "`` and follow the instructions from the bot.\n\n" +
                    "Please read the pinned message for more information.").queue { message -> message.delete().queueAfter(5, TimeUnit.MINUTES) }
        }
    }

    /**
     * Automatically handles unapproved users that leave the server.
     */
    override fun onGuildMemberLeave(event: GuildMemberLeaveEvent) {
        val guildMemberGate = guildMemberGateRepository.findById(event.guild.idLong)
        if (!guildMemberGate.isPresent || event.member.roles.contains(event.guild.getRoleById(guildMemberGate.get().memberRole!!))) {
            return
        }
        val userId = event.user.idLong
        synchronized(approvalQueue) {
            if (approvalQueue.containsKey(userId)) {
                approvalQueue.replace(userId, null)
            }
        }
        synchronized(informUserMessageIds) {
            val messageToRemove = informUserMessageIds.remove(userId)
            if (messageToRemove != null) {
                event.jda.getTextChannelById(guildMemberGate.get().gateTextChannel!!).getMessageById(messageToRemove).queue { it.delete().queue() }
            }
        }
        cleanMessagesFromUser(event.guild.getTextChannelById(guildMemberGate.get().gateTextChannel!!), event.user)

    }

    override fun commandExec(event: MessageReceivedEvent, command: String, arguments: String?) {
        val guildMemberGate = guildMemberGateRepository.findById(event.guild.idLong)
        if (!guildMemberGate.isPresent && !command.equals(super.aliases[0], true) || event.author.isBot) {
            return
        }

        when (command.toLowerCase()) {
            super.aliases[0].toLowerCase() -> {
                if (event.guild.getMember(event.author).hasPermission(Permission.MANAGE_ROLES)) {
                    event.jda.addEventListener(ConfigureSequence(event.author, event.channel, guildMemberGate.get()))
                }
            }

            super.aliases[1].toLowerCase() -> {
                if (event.guild.getMember(event.author).roles.any { it.idLong == guildMemberGate.get().memberRole!! }) {
                    return
                }

                if (event.guild.verificationLevel == Guild.VerificationLevel.VERY_HIGH && event.guild.getMember(event.author).roles.stream().noneMatch { it.name.toLowerCase() == "no mobile verification" }) {
                    accept(event.guild.getMember(event.author))
                    return
                }

                synchronized(approvalQueue) {
                    if (event.author.idLong in approvalQueue) {
                        event.channel.sendMessage("You have already tried answering a question. A moderator now needs to manually review you. Please state that you have read and agree to the rules and need manual approval.").queue { it.delete().queueAfter(1, TimeUnit.MINUTES) }
                        return
                    }
                }
                val questions = guildMemberGate.get().questions.toList()
                event.jda.addEventListener(QuestionSequence(event.author, event.channel, questions[Random().nextInt(questions.size)]))
            }
            super.aliases[2].toLowerCase() -> {
                if (event.guild.getMember(event.author).hasPermission(Permission.MANAGE_ROLES)) {
                    event.jda.addEventListener(ReviewSequence(event.author, event.channel, arguments!!.toLong(), guildMemberGate.get()))
                }
            }
        }
    }

    /**
     * Grants a user access to the accepted role.
     */
    private fun accept(member: Member) {
        val guild = member.guild
        val guildMemberGate = guildMemberGateRepository.findById(guild.idLong).orElseThrow { IllegalArgumentException("The guild of which this member originates has no member gate configuration") }
        guild.controller.addSingleRoleToMember(member, guild.getRoleById(guildMemberGate.memberRole!!)).queue()
    }

    /**
     * Starts the manual review procedure.
     */
    private fun informMember(member: Member, question: String, answer: String, textChannel: TextChannel) {
        textChannel.sendMessage(member.asMention + " Please wait while a moderator manually checks your answer. You might be asked (an) other question(s).\n\n" +
                "A moderator can use ``!" + super.aliases[2] + " " + member.user.idLong + "``").queue {
            synchronized(informUserMessageIds) {
                informUserMessageIds.put(member.user.idLong, it.idLong)
            }
        }
        synchronized(approvalQueue) {
            while (approvalQueue.size >= 50) {
                val userId = approvalQueue.firstKey()
                approvalQueue.remove(userId)
                synchronized(informUserMessageIds) {
                    val messageToRemove = informUserMessageIds.remove(userId)
                    if (messageToRemove != null) {
                        textChannel.getMessageById(messageToRemove).queue { it.delete().queue() }
                    }
                }
            }
            approvalQueue.put(member.user.idLong, question + "\n" + answer)
        }
    }

    /**
     * This sequences questions a user.
     */
    private inner class QuestionSequence internal constructor(user: User, channel: MessageChannel, private val question: String) : Sequence(user, channel, informUser = false) {
        private var sequenceNumber: Byte = 0

        /**
         * Asks first question.
         */
        init {
            super.channel.sendMessage(user.asMention + " Have you read the rules? answer with \"yes\" or \"no\"").queue { super.addMessageToCleaner(it) }
        }

        /**
         * Logic to check answers
         */
        override fun onMessageReceivedDuringSequence(event: MessageReceivedEvent) {
            if (super.user != event.author || super.channel != event.channel) {
                return
            }
            when (sequenceNumber) {
                0.toByte() -> {
                    when (event.message.contentRaw.toLowerCase()) {
                        "yes" -> {
                            super.channel.sendMessage(user.asMention + " Do you accept the rules? Answer with \"yes\" or \"no\"").queue { super.addMessageToCleaner(it) }
                            sequenceNumber = 1
                        }
                        "no" -> {
                            destroy()
                            super.channel.sendMessage(user.asMention + " Please read the rules, before using this command.").queue { it.delete().queueAfter(1, TimeUnit.MINUTES) }
                        }
                        else -> {
                            super.channel.sendMessage(user.asMention + " Invalid response! Answer with \"yes\" or \"no\"!").queue { super.addMessageToCleaner(it) }
                        }
                    }
                }
                1.toByte() -> {
                    when (event.message.contentRaw.toLowerCase()) {
                        "yes" -> {
                            super.channel.sendMessage(user.asMention + " Please answer the following question:\n" + question).queue { super.addMessageToCleaner(it) }
                            sequenceNumber = 2
                        }
                        "no" -> {
                            destroy()
                            val reason = "Doesn't agree with the rules."
                            event.guild.controller.kick(event.member, reason).queue()
                            val logToChannel = event.jda.registeredListeners.firstOrNull { it is GuildLogger }
                            if (logToChannel != null) {
                                logToChannel as GuildLogger
                                logToChannel.logKick(event.member, event.guild, event.guild.getMember(event.jda.selfUser), reason)
                            }
                        }
                        else -> {
                            super.channel.sendMessage(user.asMention + " Invalid response! Answer with \"yes\" or \"no\"!").queue { super.addMessageToCleaner(it) }
                        }
                    }
                }
                else -> {
                    destroy()
                    val member = event.guild.getMemberById(user.idLong)
                    informMember(member = member, question = question, answer = event.message.contentDisplay, textChannel = event.textChannel)
                }
            }
        }
    }

    /**
     * This sequences allows to configure questions.
     */
    @Transactional
    private inner class ConfigureSequence internal constructor(user: User, channel: MessageChannel, val guildMemberGate: GuildMemberGate) : Sequence(user, channel) {
        private var sequenceNumber: Byte = 0
        @Suppress("LeakingThis")
        private val questions = guildMemberGate.questions.toList()

        /**
         * Asks first question
         */
        init {
            channel.sendMessage(user.asMention + " Welcome to the member gate configuration sequences.\n\n" +
                    "Select an action to perform:\n" +
                    "0. add a question\n" +
                    "1. remove a question").queue { super.addMessageToCleaner(it) }
        }

        /**
         * Logic to handle configuration questions.
         */
        override fun onMessageReceivedDuringSequence(event: MessageReceivedEvent) { //todo add options to add welcome messages and remove them
            if (super.user != user || super.channel != channel) {
                return
            }
            val messageContent: String = event.message.contentDisplay.toLowerCase()
            when (sequenceNumber) {
                0.toByte() -> {
                    when (messageContent.toByte()) {
                        0.toByte() -> {
                            sequenceNumber = 1
                            val addAQuestionMessage: MessageBuilder = MessageBuilder()
                                    .append("Please send the question to add:\n")
                            super.channel.sendMessage(addAQuestionMessage.build()).queue { super.addMessageToCleaner(it) }
                        }
                        1.toByte() -> {
                            sequenceNumber = 2
                            val questionListMessage = MessageBuilder()
                            for (i in 0 until questions.size) {
                                questionListMessage.append(i.toString()).append(". ").append(questions[i]).append('\n')
                            }
                            questionListMessage.append('\n').append("Respond with the question number to remove it.")
                            channel.sendMessage(questionListMessage.build()).queue { super.addMessageToCleaner(it) }
                        }
                    }
                }
                1.toByte() -> {
                    guildMemberGate.questions.add(event.message.contentRaw)
                    guildMemberGateRepository.save(guildMemberGate)
                    super.destroy()
                    super.channel.sendMessage(super.user.asMention + " Question added.").queue { it.delete().queueAfter(1, TimeUnit.MINUTES) }
                }
                else -> {
                    val number: Int = event.message.contentDisplay.toInt()
                    guildMemberGate.questions.remove(questions[number])
                    guildMemberGateRepository.save(guildMemberGate)
                    destroy()
                    channel.sendMessage("The question \"" + questions[number] + "\" was removed.").queue { it.delete().queueAfter(1, TimeUnit.MINUTES) }
                }
            }
        }
    }

    /**
     * Allows answers to be manually reviewed, if keyword checking fails.
     */
    private inner class ReviewSequence internal constructor(user: User, channel: MessageChannel, private val userId: Long, val guildMemberGate: GuildMemberGate) : Sequence(user, channel) {

        /**
         * Asks the first question and checks if the user is in the review list.
         */
        init {
            synchronized(approvalQueue) {
                if (userId in approvalQueue) {
                    val userQuestionAndAnswer = approvalQueue[userId]
                    if (userQuestionAndAnswer != null) {
                        val message: Message = MessageBuilder().append("The user answered with the following question:\n").appendCodeBlock(userQuestionAndAnswer, "text").append("\nWas the answer good? answer with \"yes\" or \"no\"").build()
                        channel.sendMessage(message).queue { super.addMessageToCleaner(it) }
                    } else {
                        super.destroy()
                        throw IllegalArgumentException("The user you tried to review is still in the list, but another moderator already declared the question wrong or the user rejoined.")
                    }
                } else {
                    super.destroy()
                    throw IllegalArgumentException("The user you tried to review is not currently in the manual review list.")
                }
            }
        }

        /**
         * Review logic to approve members.
         */
        override fun onMessageReceivedDuringSequence(event: MessageReceivedEvent) {
            synchronized(approvalQueue) {
                if (userId !in approvalQueue) {
                    throw IllegalStateException("The user is no longer in the queue; another moderator may have reviewed it already.")
                }
                val messageContent: String = event.message.contentDisplay.toLowerCase()
                when (messageContent) {
                    "yes" -> {
                        val member: Member? = event.guild.getMemberById(userId)
                        if (member != null) {
                            super.channel.sendMessage("The user has been approved.").queue { it.delete().queueAfter(1, TimeUnit.MINUTES) }
                            accept(member)
                        } else {
                            super.channel.sendMessage("The user has left; no further action is needed.").queue { it.delete().queueAfter(1, TimeUnit.MINUTES) }
                        }
                        approvalQueue.remove(userId)
                        synchronized(informUserMessageIds) {
                            val messageToRemove = informUserMessageIds.remove(userId)
                            if (messageToRemove != null) {
                                event.jda.getTextChannelById(guildMemberGate.gateTextChannel!!).getMessageById(messageToRemove).queue { it.delete().queue() }
                            }
                        }
                        destroy()
                    }
                    "no" -> {
                        val member: Member? = event.guild.getMemberById(userId)
                        if (member != null) {
                            super.channel.sendMessage("Please give " + member.user.asMention + "  a new question in the chat that can be manually reviewed by you or by another moderator.").queue { it.delete().queueAfter(1, TimeUnit.MINUTES) }
                        } else {
                            super.channel.sendMessage("The user already left; no further action is needed.").queue { it.delete().queueAfter(1, TimeUnit.MINUTES) }
                        }
                        approvalQueue.replace(userId, null)
                        synchronized(informUserMessageIds) {
                            val messageToRemove = informUserMessageIds.remove(userId)
                            if (messageToRemove != null) {
                                event.jda.getTextChannelById(guildMemberGate.gateTextChannel!!).getMessageById(messageToRemove).queue { it.delete().queue() }
                            }
                        }
                        destroy()
                    }
                    else -> {
                        super.channel.sendMessage("Wrong answer. Please answer with \"yes\" or \"no\"").queue { super.addMessageToCleaner(it) }
                    }
                }
            }
        }
    }
}

     