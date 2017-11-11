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
import net.dv8tion.jda.core.Permission
import net.dv8tion.jda.core.entities.*
import net.dv8tion.jda.core.events.guild.member.GuildMemberJoinEvent
import net.dv8tion.jda.core.events.guild.member.GuildMemberLeaveEvent
import net.dv8tion.jda.core.events.guild.member.GuildMemberRoleAddEvent
import net.dv8tion.jda.core.events.message.MessageReceivedEvent
import net.dv8tion.jda.core.utils.SimpleLog
import org.apache.commons.collections4.map.LinkedMap
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.slf4j.event.Level
import java.awt.Color
import java.io.IOException
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.function.Consumer
import kotlin.collections.ArrayList


/**
 * Created by Duncan on 30/04/2017.
 * <p>
 * Welcomes users when the join, get accepted and makes them answer questions before they get accepted.
 */
open class MemberGate internal constructor(private val guildId: Long, private val memberRole: Long, private val rulesTextChannel: Long, private val gateTextChannel: Long, private val welcomeTextChannel: Long, private val welcomeMessages: Array<WelcomeMessage>) : CommandModule(ALIASES, null, null) {
    companion object {
        private val ALIASES: Array<String> = arrayOf("gateConfig", "join", "review")
        private val FILE_PATH: Path = Paths.get("MemberGate.json")
        private val LOG: SimpleLog = SimpleLog.getLog(MemberGate::class.java)
    }

    internal val questions: ArrayList<Question>
    private val needManualApproval: LinkedMap<Long, String?> = LinkedMap()
    private val informUserMessageIds: HashMap<Long, Long> = HashMap()

    /**
     * Loads up the existing questions
     */
    init {
        var tempQuestions: ArrayList<Question>
        try {
            val stringBuilder = StringBuilder()
            Files.readAllLines(FILE_PATH).map { stringBuilder.append(it) }
            val jsonObject = JSONObject(stringBuilder.toString())
            tempQuestions = ArrayList()
            jsonObject.getJSONArray("questions").forEach {
                if (it is JSONObject) {
                    val question = Question(it.getString("question"))
                    it.getJSONArray("keywordsList").forEach {
                        if (it is JSONArray) {
                            val stringArray: ArrayList<String> = ArrayList()
                            it.mapTo(stringArray) { it.toString() }
                            question.addKeywords(stringArray)
                        } else {
                            throw JSONException("JSON file contains unexpected object in JSONArray \"keywordsList\".")
                        }
                    }
                    tempQuestions.add(question)
                } else {
                    throw JSONException("JSON file contains unexpected object in JSONArray \"questions\".")
                }
            }
        } catch (ioE: IOException) {
            tempQuestions = ArrayList()
            LOG.log(Level.ERROR, ioE)
        } catch (jE: JSONException) {
            tempQuestions = ArrayList()
            LOG.log(Level.ERROR, jE)
        }
        questions = tempQuestions
    }

    /**
     * Check if a user was added to the approved role.
     */
    override fun onGuildMemberRoleAdd(event: GuildMemberRoleAddEvent) {
        val guild = event.guild
        if (guild.idLong != guildId || event.user.isBot || guild.getRoleById(memberRole) !in event.roles) {
            return
        }

        val welcomeMessage = welcomeMessages[Random().nextInt(welcomeMessages.size)].getWelcomeMessage(event.user)
        guild.getTextChannelById(welcomeTextChannel).sendMessage(welcomeMessage).queue()
        synchronized(needManualApproval) {
            needManualApproval.remove(event.user.idLong)
        }
        cleanMessagesFromUser(guild, event.user)
    }

    /**
     * Cleans the messages from the users and messages containing mentions to the users from the member gate channel.
     */
    private fun cleanMessagesFromUser(guild: Guild, user: User) {
        val gateTextChannel: TextChannel = guild.getTextChannelById(this.gateTextChannel)
        val userMessages: ArrayList<Message> = ArrayList()
        gateTextChannel.iterableHistory.map {
            if (it.author == user || it.rawContent.contains(user.id)) {
                userMessages.add(it)
            }
        }
        JDALibHelper.limitLessBulkDelete(gateTextChannel, userMessages)
    }

    /**
     * Welcomes a new member that joins and informs them about the member gate system.
     */
    override fun onGuildMemberJoin(event: GuildMemberJoinEvent) {
        if (event.guild.idLong != guildId || event.user.isBot) {
            return
        }
        if(event.guild.verificationLevel == Guild.VerificationLevel.VERY_HIGH) {
            event.jda.getTextChannelById(gateTextChannel).sendMessage("Welcome " + event.member.asMention + ", this server uses phone verification.\n" +
                    "If you have verified your phone and are able to chat in this channel, you can simply type ``!join`` to join the server.\n" +
                    "If you can't use phone verification, send "+ event.jda.selfUser.asMention + " a dm and type ``!nomobile``. You will be granted a special role! After that, return to this channel and type ``!join`` and follow the instructions.\n" +
                    "\n" +
                    "**Warning: Users that are not mobile verified will be punished much more severely and faster when breaking the rules or when suspected of bypassing a ban.**").queue({ message -> message.delete().queueAfter(5, TimeUnit.MINUTES) })
        } else {
            event.jda.getTextChannelById(gateTextChannel).sendMessage("Welcome " + event.member.asMention + ", this server requires you to read the " + event.guild.getTextChannelById(rulesTextChannel).asMention + " and answer a question regarding those before you gain full access.\n\n" +
                    "If you have read the rules and are ready to answer the question, type ``!" + super.aliases[1] + "`` and follow the instructions from the bot.\n\n" +
                    "Please read the pinned message for more information.").queue({ message -> message.delete().queueAfter(5, TimeUnit.MINUTES) })
        }
    }

    /**
     * Automatically handles unapproved users that leave the server.
     */
    override fun onGuildMemberLeave(event: GuildMemberLeaveEvent) {
        if (event.member.roles.contains(event.guild.getRoleById(memberRole))) {
            return
        }
        val userId = event.user.idLong
        synchronized(needManualApproval) {
            if (needManualApproval.containsKey(userId)) {
                needManualApproval.replace(userId, null)
            }
        }
        synchronized(informUserMessageIds) {
            val messageToRemove = informUserMessageIds.remove(userId)
            if (messageToRemove != null) {
                event.jda.getTextChannelById(gateTextChannel).getMessageById(messageToRemove).queue { it.delete().queue() }
            }
        }
        cleanMessagesFromUser(event.guild, event.user)

    }

    /**
     * Do something with the event, command and arguments.
     *
     * @param event A MessageReceivedEvent that came with the command
     * @param command The command alias that was used to trigger this commandExec
     * @param arguments The arguments that where entered after the command alias
     */
    override fun commandExec(event: MessageReceivedEvent, command: String, arguments: String?) {
        if (event.author.isBot) {
            return
        }

        when (command.toLowerCase()) {
            super.aliases[0].toLowerCase() -> {
                if (event.jda.getGuildById(guildId).getMember(event.author).hasPermission(Permission.MANAGE_ROLES)) {
                    event.jda.addEventListener(ConfigureSequence(event.author, event.channel))
                }
            }

            super.aliases[1].toLowerCase() -> {
                if (event.jda.getGuildById(guildId).getMember(event.author).roles.any { it.idLong == memberRole }) {
                    return
                }

                if(event.guild.verificationLevel == Guild.VerificationLevel.VERY_HIGH && event.jda.getGuildById(guildId).getMember(event.author).roles.stream().noneMatch{ it.name.toLowerCase() == "no mobile verification" }) {
                    accept(event.jda.getGuildById(guildId).getMember(event.author))
                    return
                }

                synchronized(needManualApproval) {
                    if (event.author.idLong in needManualApproval) {
                        event.channel.sendMessage("You have already tried answering a question. A moderator now needs to manually review you. Please state that you have read and agree to the rules and need manual approval.").queue { it.delete().queueAfter(1, TimeUnit.MINUTES) }
                        return
                    }
                }

                event.jda.addEventListener(QuestionSequence(event.author, event.channel, questions[Random().nextInt(questions.size)]))
            }
            super.aliases[2].toLowerCase() -> {
                if (event.jda.getGuildById(guildId).getMember(event.author).hasPermission(Permission.MANAGE_ROLES)) {
                    event.jda.addEventListener(ReviewSequence(event.author, event.channel, arguments!!.toLong()))
                }
            }
        }
    }

    /**
     * Grants a user access to the accepted role.
     */
    private fun accept(member: Member) {
        val guild = member.guild
        guild.controller.addRolesToMember(member, guild.getRoleById(memberRole)).queue()
    }

    /**
     * Starts the manual review procedure.
     */
    private fun failedQuestion(member: Member, question: String, answer: String) {
        val guild = member.guild
        val textChannel = guild.getTextChannelById(gateTextChannel)
        textChannel.sendMessage(member.asMention + " Sorry, it appears the answer provided is incorrect. Please wait while a moderator manually checks your answer, or asks another question.\n\n" +
                "A moderator can use ``!" + super.aliases[2] + " " + member.user.idLong + "``").queue {
            synchronized(informUserMessageIds) {
                informUserMessageIds.put(member.user.idLong, it.idLong)
            }
        }
        synchronized(needManualApproval) {
            while (needManualApproval.size >= 50) {
                val userId = needManualApproval.firstKey()
                needManualApproval.remove(userId)
                synchronized(informUserMessageIds) {
                    val messageToRemove = informUserMessageIds.remove(userId)
                    if (messageToRemove != null) {
                        member.jda.getTextChannelById(gateTextChannel).getMessageById(messageToRemove).queue { it.delete().queue() }
                    }
                }
            }
            needManualApproval.put(member.user.idLong, question + "\n" + answer)
        }
    }

    /**
     * Saves all the questions and keywords to a JSON file.
     */
    internal fun saveQuestions() {
        val jsonObject = JSONObject()
        val questionsJsonList: ArrayList<JSONObject> = ArrayList()
        questions.forEach(Consumer { questionsJsonList.add(it.toJsonObject()) })
        jsonObject.put("questions", questionsJsonList)
        Files.write(FILE_PATH, Collections.singletonList(jsonObject.toString()), Charset.defaultCharset())
    }

    /**
     * Immutable class containing a welcome message and url for an image to be used in an embed.
     */
    internal class WelcomeMessage(private val imageUrl: String, private val message: String) {

        fun getWelcomeMessage(user: User): Message {
            val joinEmbed = EmbedBuilder()
                    .setDescription(this.message)
                    .setImage(imageUrl)
                    .setColor(Color.GREEN)
                    .build()
            return MessageBuilder()
                    .append(user.asMention)
                    .setEmbed(joinEmbed)
                    .build()
        }
    }

    /**
     * This sequence questions a user.
     */
    private inner class QuestionSequence internal constructor(user: User, channel: MessageChannel, private val question: Question) : Sequence(user, channel, informUser = false) {
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
                    when (event.message.rawContent.toLowerCase()) {
                        "yes" -> {
                            super.channel.sendMessage(user.asMention + " Do you accept the rules? Answer with \"yes\" or \"no\"").queue { super.addMessageToCleaner(it) }
                            sequenceNumber = 1
                        }
                        "no" -> {
                            destroy()
                            super.channel.sendMessage(user.asMention + " Please read the rules and pinned message, before using this command.").queue { it.delete().queueAfter(1, TimeUnit.MINUTES) }
                        }
                        else -> {
                            super.channel.sendMessage(user.asMention + " Invalid response! Answer with \"yes\" or \"no\"!").queue { super.addMessageToCleaner(it) }
                        }
                    }
                }
                1.toByte() -> {
                    when (event.message.rawContent.toLowerCase()) {
                        "yes" -> {
                            super.channel.sendMessage(user.asMention + " Please answer the following question:\n" + question.question).queue { super.addMessageToCleaner(it) }
                            sequenceNumber = 2
                        }
                        "no" -> {
                            destroy()
                            val reason = "Doesn't agree with the rules."
                            event.guild.controller.kick(event.member, reason).queue()
                            RunBots.getRunBot(event.jda)?.logger?.logKick(event.member, event.guild, event.guild.getMember(event.jda.selfUser), reason)
                        }
                        else -> {
                            super.channel.sendMessage(user.asMention + " Invalid response! Answer with \"yes\" or \"no\"!").queue { super.addMessageToCleaner(it) }
                        }
                    }
                }
                else -> {
                    destroy()
                    val member = event.jda.getGuildById(guildId).getMemberById(user.idLong)
                    if (question.checkAnswer(event)) {
                        accept(member)
                    } else {
                        failedQuestion(member = member, question = question.question, answer = event.message.content)
                    }
                }
            }
        }
    }

    /**
     * This sequence allows to configure questions.
     */
    private inner class ConfigureSequence internal constructor(user: User, channel: MessageChannel) : Sequence(user, channel) {
        private var sequenceNumber: Byte = 0

        /**
         * Asks first question
         */
        init {
            channel.sendMessage(user.asMention + " Welcome to the member gate configuration sequence.\n\n" +
                    "Select an action to perform:\n" +
                    "add a question\n" +
                    "remove a question").queue { super.addMessageToCleaner(it) }
        }

        /**
         * Logic to handle configuration questions.
         */
        override fun onMessageReceivedDuringSequence(event: MessageReceivedEvent) {
            if (super.user != user || super.channel != channel) {
                return
            }
            val messageContent: String = event.message.content.toLowerCase()
            when (sequenceNumber) {
                0.toByte() -> {
                    when (messageContent) {
                        "add a question" -> {
                            sequenceNumber = 1
                            val addAQuestionMessage: MessageBuilder = MessageBuilder()
                                    .append("Please send the question and keywordlists in the following format:\n")
                                    .appendCodeBlock("Question?\nKeyword list (each keyword separated by ',' **without spaces**\nKeyword list\n...", "text")
                                    .append("Example with explanations:\n")
                                    .appendCodeBlock("Why did you answer this question? <- This question will be print exactly the same way as you gave it make sure it's right\n" +
                                            "I <- Meaning at least I must be present in the answer\n" +
                                            "had,need <- meaning at least \"had\" or \"need\" needs to be present and any previous keyword list", "text")
                            super.channel.sendMessage(addAQuestionMessage.build()).queue { super.addMessageToCleaner(it) }
                        }
                        "remove a question" -> {
                            sequenceNumber = 2
                            val questionListMessage = MessageBuilder()
                            for (i in 0 until questions.size) {
                                questionListMessage.append(i.toString()).append(". ").append(questions[i].question).append('\n')
                            }
                            questionListMessage.append('\n').append("Respond with the question number to remove it.")
                            channel.sendMessage(questionListMessage.build()).queue { super.addMessageToCleaner(it) }
                        }
                    }
                }
                1.toByte() -> {
                    val inputQuestionList: List<String> = messageContent.toLowerCase().split('\n')
                    if (inputQuestionList.size < 2) {
                        super.channel.sendMessage("Syntax mismatch.").queue { super.addMessageToCleaner(it) }
                    }
                    val question = Question(inputQuestionList[0])
                    for (i in 1 until inputQuestionList.size) {
                        question.addKeywords(ArrayList(inputQuestionList[i].split(',')))
                    }
                    questions.add(question)
                    saveQuestions()
                    super.destroy()
                    super.channel.sendMessage(super.user.asMention + " Question and keywords added.").queue { it.delete().queueAfter(1, TimeUnit.MINUTES) }
                }
                else -> {
                    val number: Int = event.message.content.toInt()
                    val removedQuestion: Question = questions.removeAt(number)
                    saveQuestions()
                    destroy()
                    channel.sendMessage("The question \"" + removedQuestion.question + "\" was removed.").queue { it.delete().queueAfter(1, TimeUnit.MINUTES) }
                }
            }
        }
    }

    /**
     * Container class containing questions and the keyword checks.
     */
    internal inner class Question internal constructor(internal val question: String) {
        private val keywordList: ArrayList<ArrayList<String>> = ArrayList()

        /**
         * @param event the event containing the message that came from the sequence to answer the question.
         * @return true when the answer is correct, false otherwise.
         */
        internal fun checkAnswer(event: MessageReceivedEvent): Boolean {
            return keywordList.all { it.any { it.toLowerCase() in event.message.content.toLowerCase() } }
        }

        /**
         * Adds more keywords to a question.
         */
        internal fun addKeywords(keywords: ArrayList<String>) {
            keywordList.add(keywords)
        }

        /**
         * Converts this object into a JSONObject.
         */
        internal fun toJsonObject(): JSONObject {
            val jsonObject = JSONObject()
            jsonObject.put("question", question)
            jsonObject.put("keywordsList", keywordList)
            return jsonObject
        }
    }

    /**
     * Allows answers to be manually reviewed, if keyword checking fails.
     */
    private inner class ReviewSequence internal constructor(user: User, channel: MessageChannel, private val userId: Long) : Sequence(user, channel) {

        /**
         * Asks the first question and checks if the user is in the review list.
         */
        init {
            synchronized(needManualApproval) {
                if (userId in needManualApproval) {
                    val userQuestionAndAnswer = needManualApproval[userId]
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
            synchronized(needManualApproval) {
                if (userId !in needManualApproval) {
                    throw IllegalStateException("The user is no longer in the queue; another moderator may have reviewed it already.")
                }
                val messageContent: String = event.message.content.toLowerCase()
                when (messageContent) {
                    "yes" -> {
                        val member: Member? = event.guild.getMemberById(userId)
                        if (member != null) {
                            super.channel.sendMessage("The user has been approved.").queue { it.delete().queueAfter(1, TimeUnit.MINUTES) }
                            accept(member)
                        } else {
                            super.channel.sendMessage("The user has left; no further action is needed.").queue { it.delete().queueAfter(1, TimeUnit.MINUTES) }
                        }
                        needManualApproval.remove(userId)
                        synchronized(informUserMessageIds) {
                            val messageToRemove = informUserMessageIds.remove(userId)
                            if (messageToRemove != null) {
                                event.jda.getTextChannelById(gateTextChannel).getMessageById(messageToRemove).queue { it.delete().queue() }
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
                        needManualApproval.replace(userId, null)
                        synchronized(informUserMessageIds) {
                            val messageToRemove = informUserMessageIds.remove(userId)
                            if (messageToRemove != null) {
                                event.jda.getTextChannelById(gateTextChannel).getMessageById(messageToRemove).queue { it.delete().queue() }
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

     