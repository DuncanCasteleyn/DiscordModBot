/*
 * Copyright 2017 Duncan C.
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

package net.dunciboy.discord_bot

import net.dunciboy.discord_bot.commands.CommandModule
import net.dunciboy.discord_bot.sequence.Sequence
import net.dv8tion.jda.core.EmbedBuilder
import net.dv8tion.jda.core.MessageBuilder
import net.dv8tion.jda.core.Permission
import net.dv8tion.jda.core.entities.Member
import net.dv8tion.jda.core.entities.Message
import net.dv8tion.jda.core.entities.MessageChannel
import net.dv8tion.jda.core.entities.User
import net.dv8tion.jda.core.events.guild.member.GuildMemberJoinEvent
import net.dv8tion.jda.core.events.guild.member.GuildMemberRoleAddEvent
import net.dv8tion.jda.core.events.message.MessageReceivedEvent
import net.dv8tion.jda.core.utils.SimpleLog
import org.apache.commons.collections4.map.LinkedMap
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
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
open class MemberGate internal constructor(private val guildId: Long, private val memberRole: Long, private val gateTextChannel: Long, private val welcomeTextChannel: Long, private val welcomeMessages: Array<WelcomeMessage>) : CommandModule(ALIASES, null, null) {
    companion object {
        private val ALIASES: Array<String> = arrayOf("gateConfig", "join", "review")
        private val FILE_PATH: Path = Paths.get("MemberGate.json")
        private val LOG: SimpleLog = SimpleLog.getLog(MemberGate::class.java.simpleName)
    }

    internal val questions: ArrayList<Question>
    private val needManualApproval: LinkedMap<Long, String> = LinkedMap()

    init {
        var tempQuestions: ArrayList<Question>
        try {
            val stringBuilder = StringBuilder()
            Files.newBufferedReader(FILE_PATH).lines().forEachOrdered { stringBuilder.append(it) }
            val jsonObject = JSONObject(stringBuilder.toString())
            tempQuestions = ArrayList()
            jsonObject.getJSONArray("questions").forEach {
                if (it is JSONObject) {
                    val question: Question = Question(it.getString("question"))
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
            LOG.log(ioE)
        } catch(jE: JSONException) {
            tempQuestions = ArrayList()
            LOG.log(jE)
        }
        questions = tempQuestions
    }

    override fun onGuildMemberRoleAdd(event: GuildMemberRoleAddEvent) {
        val guild = event.guild
        if (guild.idLong != guildId) {
            return
        }
        if (guild.getRoleById(memberRole) in event.roles) {
            val welcomeMessage = welcomeMessages[Random().nextInt(welcomeMessages.size)].getWelcomeMessage(event.user)
            guild.getTextChannelById(welcomeTextChannel).sendMessage(welcomeMessage).queue {
                val embed = EmbedBuilder(it.embeds[0]).setImage(null).build()
                val message: Message = MessageBuilder().append(it.rawContent).setEmbed(embed).build()
                it.editMessage(message).queueAfter(1, TimeUnit.MINUTES)
            }
            synchronized(needManualApproval) {
                needManualApproval.remove(event.user.idLong)
            }
        }
    }

    override fun onGuildMemberJoin(event: GuildMemberJoinEvent) {
        if (event.guild.idLong != guildId) {
            return
        }
        event.jda.getTextChannelById(gateTextChannel).sendMessage("Welcome " + event.member.asMention + ", this server requires you to read the " + event.guild.publicChannel.asMention + " and answer a question regarding those before you gain full access.\n\n" +
                "If you have read the rules and are ready to answer the question type ``!" + super.aliases[1] + "`` and follow the instructions from the bot.\n\n" +
                "Please read the pinned message for more information.").queue({ message -> message.delete().queueAfter(5, TimeUnit.MINUTES) })
    }

    /**
     * Do something with the event, command and arguments.
     *
     * @param event A MessageReceivedEvent that came with the command
     * @param command The command alias that was used to trigger this commandExec
     * @param arguments The arguments that where entered after the command alias
     */
    override fun commandExec(event: MessageReceivedEvent, command: String, arguments: String?) {
        when (command.toLowerCase()) {
            super.aliases[0].toLowerCase() -> {
                if (event.jda.getGuildById(guildId).getMember(event.author).hasPermission(Permission.MANAGE_ROLES)) {
                    event.jda.addEventListener(ConfigureSequence(event.author, event.channel))
                }
            }
            super.aliases[1].toLowerCase() -> {
                if (event.jda.getGuildById(guildId).getMember(event.author).roles.any({ it.idLong == memberRole })) {
                    return
                }
                synchronized(needManualApproval) {
                    if (event.author.idLong in needManualApproval) {
                        event.channel.sendMessage("You have already tried answering a question, a moderator needs to manually review you, please state that you have read the rules, agree with them and need manual approval.").queue { it.delete().queueAfter(1, TimeUnit.MINUTES) }
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
        textChannel.sendMessage(member.asMention + " Sorry it appears the answer you gave was not correct, the mods will now manually check your answer and ask further questions if required.\n\n" +
                "A moderator can use ``!" + super.aliases[2] + " " + member.user.idLong + "``").queue()
        synchronized(needManualApproval) {
            while (needManualApproval.size >= 50) {
                needManualApproval.remove(needManualApproval.firstKey())
            }
            needManualApproval.put(member.user.idLong, question + "\n" + answer)
        }
    }

    /**
     * Saves all the questions and keywords to a JSON file.
     */
    internal fun saveQuestions() {
        val jsonObject: JSONObject = JSONObject()
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
        init {
            super.channel.sendMessage(user.asMention + " please answer the following question:\n" + question.question).queue { super.addMessageToCleaner(it) }
        }

        override fun onMessageReceivedDuringSequence(event: MessageReceivedEvent) {
            if (super.user != event.author || super.channel != event.channel) {
                return
            }
            destroy()
            val member = event.jda.getGuildById(guildId).getMemberById(user.idLong)
            if (question.checkAnswer(event)) {
                accept(member)
            } else {
                failedQuestion(member = member, question = question.question, answer = event.message.content)
            }
        }
    }

    /**
     * This sequence allows to configure questions.
     */
    private inner class ConfigureSequence internal constructor(user: User, channel: MessageChannel) : Sequence(user, channel) {
        private var sequenceNumber: Int = 0

        init {
            channel.sendMessage(user.asMention + " Welcome to the member gate configuration sequence.\n\n" +
                    "Please state what action you like to perform:\n" +
                    "add a question\n" +
                    "remove a question").queue { super.addMessageToCleaner(it) }
        }

        override fun onMessageReceivedDuringSequence(event: MessageReceivedEvent) {
            if (super.user != user || super.channel != channel) {
                return
            }
            val messageContent: String = event.message.content.toLowerCase()
            when (sequenceNumber) {
                0 -> {
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
                            val questionListMessage: MessageBuilder = MessageBuilder()
                            for (i in 0 until questions.size) {
                                questionListMessage.append(i.toString()).append(". ").append(questions[i].question).append('\n')
                            }
                            questionListMessage.append('\n').append("Respond with the question number to remove it.")
                            channel.sendMessage(questionListMessage.build()).queue { super.addMessageToCleaner(it) }
                        }
                    }
                }
                1 -> {
                    val inputQuestionList: List<String> = messageContent.toLowerCase().split('\n')
                    if (inputQuestionList.size < 2) {
                        super.channel.sendMessage("Syntax mismatch.").queue { super.addMessageToCleaner(it) }
                    }
                    val question: Question = Question(inputQuestionList[0])
                    for (i in 1 until inputQuestionList.size) {
                        question.addKeywords(ArrayList(inputQuestionList[i].split(',')))
                    }
                    questions.add(question)
                    saveQuestions()
                    super.destroy()
                    super.channel.sendMessage(super.user.asMention + " Question and keywords added.").queue { it.delete().queueAfter(1, TimeUnit.MINUTES) }
                }
                2 -> {
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

        internal fun addKeywords(keywords: ArrayList<String>) {
            keywordList.add(keywords)
        }

        internal fun toJsonObject(): JSONObject {
            val jsonObject: JSONObject = JSONObject()
            jsonObject.put("question", question)
            jsonObject.put("keywordsList", keywordList)
            return jsonObject
        }
    }

    /**
     * Allows answers to be manually review if keyword checking fails.
     */
    private inner class ReviewSequence internal constructor(user: User, channel: MessageChannel, private val userId: Long) : Sequence(user, channel) {

        init {
            synchronized(needManualApproval) {
                if (userId in needManualApproval) {
                    val userQuestionAndAnswer = needManualApproval[userId]
                    if (userQuestionAndAnswer != null) {
                        val message: Message = MessageBuilder().append("The user answered with the following question:\n").appendCodeBlock(userQuestionAndAnswer, "text").append("\nWas the answer good? answer with \"yes\" or \"no\"").build()
                        channel.sendMessage(message).queue { super.addMessageToCleaner(it) }
                    } else {
                        super.destroy()
                        throw IllegalArgumentException("The user you tried to review is still in the list, but another moderator already declared the question wrong.")
                    }
                } else {
                    super.destroy()
                    throw IllegalArgumentException("The user you tried to review is not or no longer in the manual review list.")
                }
            }
        }

        override fun onMessageReceivedDuringSequence(event: MessageReceivedEvent) {
            synchronized(needManualApproval) {
                if (userId !in needManualApproval) {
                    throw IllegalStateException("The user is no longer in the queue, another moderator might have reviewed it already.")
                }
                val messageContent: String = event.message.content.toLowerCase()
                when (messageContent) {
                    "yes" -> {
                        super.channel.sendMessage("The user has been approved.").queue { it.delete().queueAfter(1, TimeUnit.MINUTES) }
                        accept(event.guild.getMemberById(userId))
                        needManualApproval.remove(userId)
                        destroy()
                    }
                    "no" -> {
                        super.channel.sendMessage("Please give the user a new question in the chat, that can be manually checked by you or by another moderator.").queue { it.delete().queueAfter(1, TimeUnit.MINUTES) }
                        needManualApproval.replace(userId, null)
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

     