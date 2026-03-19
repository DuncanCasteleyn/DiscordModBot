package be.duncanc.discordmodbot.member.gate

import be.duncanc.discordmodbot.discord.CommandModule
import be.duncanc.discordmodbot.discord.MessageSequence
import be.duncanc.discordmodbot.discord.Sequence
import be.duncanc.discordmodbot.discord.limitLessBulkDeleteByIds
import be.duncanc.discordmodbot.member.gate.persistence.WelcomeMessage
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleAddEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.utils.SplitUtil
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom
import java.util.*
import java.util.concurrent.TimeUnit

@Component
@Transactional
class MemberGate(
    private val memberGateService: MemberGateService,
    private val welcomeMessageService: WelcomeMessageService,
    private val reviewManager: MemberGateReviewManager
) : CommandModule(
    arrayOf("gateConfig"),
    null,
    null,
    ignoreWhitelist = true
) {
    companion object {
        private const val MENTION_CHANNEL_TO_SET = "Please mention the channel you want to set."
        private const val NEED_TO_MENTION_CHANNEL = "A channel needs to be mentioned."
        private const val CHANNEL_SET = "Channel set"

        private val random = SecureRandom()
    }

    /**
     * Check if a user was added to the approved role.
     */
    override fun onGuildMemberRoleAdd(event: GuildMemberRoleAddEvent) {
        val guild = event.guild
        val welcomeMessages = ArrayList(welcomeMessageService.getWelcomeMessages(guild.idLong))
        val memberRole = memberGateService.getMemberRole(guild.idLong, guild.jda)
        if (welcomeMessages.isEmpty() || event.user.isBot || memberRole !in event.roles) {
            return
        }
        val welcomeMessage = welcomeMessages[random.nextInt(welcomeMessages.size)].getWelcomeMessage(event.user)
        memberGateService.getWelcomeChannel(guild.idLong, guild.jda)?.sendMessage(welcomeMessage)?.queue()
        reviewManager.clearPendingQuestion(guild.idLong, event.jda, event.user.idLong)
        memberGateService.getGateChannel(guild.idLong, guild.jda)?.let { cleanMessagesFromUser(it, event.user) }
    }

    /**
     * Cleans the messages from the users and messages containing mentions to the users from the member gate channel.
     *
     * If the channel contains more than 1000 messages, all messages past 1000 will be ignored.
     */
    private fun cleanMessagesFromUser(gateTextChannel: TextChannel, user: User) {
        if (!gateTextChannel.guild.selfMember.hasPermission(
                gateTextChannel,
                Permission.MESSAGE_MANAGE,
                Permission.MESSAGE_HISTORY
            )
        ) {
            return
        }
        gateTextChannel.history.size()
        val userMessages: ArrayList<Long> = ArrayList()
        gateTextChannel.iterableHistory
            .takeAsync(1000)
            .thenApply { messages ->
                messages.forEach {
                    if (it.author == user || it.contentRaw.contains(user.id)) {
                        userMessages.add(it.idLong)
                    }
                }
                true
            }.thenRun {
                gateTextChannel.limitLessBulkDeleteByIds(userMessages)
            }
    }

    /**
     * Welcomes a new member that joins and informs them about the member gate system.
     */
    override fun onGuildMemberJoin(event: GuildMemberJoinEvent) {
        if (event.user.isBot) {
            return
        }
        val gateTextChannel = memberGateService.getGateChannel(event.guild.idLong, event.jda)
        val welcomeChannel = memberGateService.getWelcomeChannel(event.guild.idLong, event.jda)
        if (gateTextChannel != null) {
            val memberGateQuestion = reviewManager.getPendingQuestion(event.guild.idLong, event.user.idLong)
            if (memberGateQuestion != null) {
                gateTextChannel.sendMessage(
                    """
                        ${event.member.asMention} Welcome back. We stored your last answer for you.
                        A moderator can review it using `/review`.
                        """.trimIndent()
                ).queue { message ->
                    reviewManager.rememberInformPrompt(event.guild.idLong, memberGateQuestion.userId, message.idLong)
                }
            } else {
                gateTextChannel.sendMessage(
                    "Welcome " + event.member.asMention + ", this server requires you to read the " +
                            (memberGateService.getRuleChannel(event.guild.idLong, event.jda)?.asMention
                                ?: "rules") +
                            " and answer a question regarding those before you gain full access.\n\n" +
                            "If you have read the rules and are ready to answer the question, use the ``/join`` command and follow the instructions from the bot.\n\n" +
                            "Please read the pinned message for more information.\n" +
                            "Never ping moderators unless you have issues which prevent you from completing the entry process."
                ).queue { message -> message.delete().queueAfter(5, TimeUnit.MINUTES) }
            }
        } else if (welcomeChannel != null) {
            val welcomeMessages = welcomeMessageService.getWelcomeMessages(event.guild.idLong).toTypedArray()
            if (welcomeMessages.isNotEmpty()) {
                val welcomeMessage =
                    welcomeMessages[random.nextInt(welcomeMessages.size)].getWelcomeMessage(event.user)
                memberGateService.getWelcomeChannel(event.guild.idLong, event.jda)?.sendMessage(welcomeMessage)?.queue()
            }
        }
    }

    override fun onGuildMemberRemove(event: GuildMemberRemoveEvent) {
        val gateChannel = memberGateService.getGateChannel(event.guild.idLong, event.jda)
        if (gateChannel == null || event.member?.roles?.contains(
                memberGateService.getMemberRole(
                    event.guild.idLong,
                    event.jda
                )
            ) == true
        ) {
            return
        }
        val userId = event.user.idLong
        reviewManager.clearInformPrompt(event.guild.idLong, event.jda, userId)
        cleanMessagesFromUser(gateChannel, event.user)

    }

    override fun commandExec(event: MessageReceivedEvent, command: String, arguments: String?) {
        if (!event.isFromType(ChannelType.TEXT)) {
            throw IllegalStateException("This command cannot be executed outside a text channel.")
        }
        if (event.author.isBot) {
            return
        }

        when (command.lowercase(Locale.getDefault())) {
            super.aliases[0].lowercase(Locale.getDefault()) -> {
                configure(event)
            }
        }
    }

    private fun configure(event: MessageReceivedEvent) {
        if (event.guild.getMember(event.author)?.hasPermission(Permission.MANAGE_ROLES) == true) {
            event.jda.addEventListener(ConfigureSequence(event.author, event.channel))
        }
    }

    open inner class ConfigureSequence(user: User, channel: MessageChannel) :
        Sequence(user, channel), MessageSequence {
        private var sequenceNumber: Byte = 0
        private lateinit var questions: List<String>
        private lateinit var welcomeMessages: List<WelcomeMessage>
        private lateinit var welcomeMessage: WelcomeMessage

        /**
         * Asks first question
         */
        init {
            channel.sendMessage(
                user.asMention + " Welcome to the member gate configuration sequences.\n\n" +
                        "Select an action to perform:\n" +
                        "0: add a question\n" +
                        "1: remove a question\n" +
                        "2: Add welcome message\n" +
                        "3: Remove welcome message\n" +
                        "4: Change welcome channel\n" +
                        "5: Change member gate chanel\n" +
                        "6: Change member role\n" +
                        "7: Change rules channel\n" +
                        "8: Disable member approval gate (wipes your questions, member role, gate channel and rule channel settings)\n" +
                        "9: Disable welcome messages (wipes your welcomes message and channel settings)\n" +
                        "10: Wipe member gate module settings\n" +
                        "11: Set auto purge time in hours (purges members that don't complete entry process)\n" +
                        "12: Disable auto purge\n" +
                        "13: Set entry reminder time in hours (reminds people they will be purged)\n" +
                        "14: Disable entry reminder\n" +
                        "\nTo enable the member gate you need to set at least the member gate channel and the member role\n" +
                        "To enable welcome messages you need to set at least a welcome message and the welcome channel"
            ).queue { super.addMessageToCleaner(it) }
        }

        /**
         * Logic to handle configuration.
         */
        override fun onMessageReceivedDuringSequence(event: MessageReceivedEvent) {
            if (super.user != user || super.channel != channel) {
                return
            }
            val messageContent: String = event.message.contentDisplay.lowercase(Locale.getDefault())
            when (sequenceNumber) {
                0.toByte() -> {
                    findDesiredAction(messageContent, event)
                }

                1.toByte() -> {
                    val guildId = (channel as TextChannel).guild.idLong
                    memberGateService.addQuestion(guildId, event.message.contentRaw)
                    channel.sendMessage(super.user.asMention + " Question added.")
                        .queue { it.delete().queueAfter(1, TimeUnit.MINUTES) }
                    destroy()
                }

                2.toByte() -> {
                    val number: Int = event.message.contentDisplay.toInt()
                    val guildId = (channel as TextChannel).guild.idLong
                    memberGateService.removeQuestion(guildId, questions[number])
                    channel.sendMessage("The question \"" + questions[number] + "\" was removed.")
                        .queue { it.delete().queueAfter(1, TimeUnit.MINUTES) }
                    destroy()
                }

                3.toByte() -> {
                    val guildId = (channel as TextChannel).guild.idLong
                    welcomeMessage = WelcomeMessage(guildId = guildId, imageUrl = event.message.contentRaw)
                    channel.sendMessage("Please enter a welcome message.").queue { addMessageToCleaner(it) }
                    sequenceNumber = 4
                }

                4.toByte() -> {
                    welcomeMessage = welcomeMessage.copy(message = event.message.contentRaw)
                    welcomeMessageService.addWelcomeMessage(welcomeMessage)
                    channel.sendMessage("The new welcome message has been added.")
                        .queue { it.delete().queueAfter(1, TimeUnit.MINUTES) }
                    destroy()
                }

                5.toByte() -> {
                    val welcomeMessage = welcomeMessages[event.message.contentRaw.toInt()]
                    welcomeMessageService.removeWelcomeMessage(welcomeMessage)
                    channel.sendMessage("\"$welcomeMessage\" has been deleted.")
                        .queue { it.delete().queueAfter(1, TimeUnit.MINUTES) }
                    destroy()
                }

                6.toByte() -> {
                    val mentionedChannels = event.message.mentions.channels
                    if (mentionedChannels.isNotEmpty()) {
                        val guildId = (channel as TextChannel).guild.idLong
                        memberGateService.setWelcomeChannel(guildId, mentionedChannels[0] as TextChannel)
                        channel.sendMessage(CHANNEL_SET).queue { it.delete().queueAfter(1, TimeUnit.MINUTES) }
                        destroy()
                    } else {
                        throw IllegalArgumentException(NEED_TO_MENTION_CHANNEL)
                    }
                }

                7.toByte() -> {
                    val mentionedChannels = event.message.mentions.channels
                    if (mentionedChannels.isNotEmpty()) {
                        val guildId = (channel as TextChannel).guild.idLong
                        memberGateService.setGateChannel(guildId, mentionedChannels[0] as TextChannel)
                        channel.sendMessage(CHANNEL_SET).queue { it.delete().queueAfter(1, TimeUnit.MINUTES) }
                        destroy()
                    } else {
                        throw IllegalArgumentException(NEED_TO_MENTION_CHANNEL)
                    }
                }

                8.toByte() -> {
                    val targetRoles = event.guild.getRolesByName(event.message.contentRaw, true)
                    when {
                        targetRoles.size == 1 -> {
                            val guildId = (channel as TextChannel).guild.idLong
                            memberGateService.setMemberRole(guildId, targetRoles[0])
                            channel.sendMessage("Role set").queue { it.delete().queueAfter(1, TimeUnit.MINUTES) }
                            destroy()
                        }

                        targetRoles.isEmpty() -> throw IllegalArgumentException("Couldn't find any roles with that name.")
                        else -> throw IllegalArgumentException("More then 1 match, please rename the role temporary.")
                    }

                }

                9.toByte() -> {
                    val mentionedChannels = event.message.mentions.channels
                    if (mentionedChannels.isNotEmpty()) {
                        val guildId = (channel as TextChannel).guild.idLong
                        memberGateService.setRulesChannel(guildId, mentionedChannels[0] as TextChannel)
                        channel.sendMessage("Channel set").queue { it.delete().queueAfter(1, TimeUnit.MINUTES) }
                        destroy()
                    } else {
                        throw IllegalArgumentException("A channel needs to be mentioned.")
                    }
                }

                10.toByte() -> {
                    val guildId = (channel as TextChannel).guild.idLong
                    memberGateService.setPurgeTime(guildId, event.message.contentRaw.toLong())
                    channel.sendMessage("Purge time set").queue { it.delete().queueAfter(1, TimeUnit.MINUTES) }
                    destroy()
                }

                11.toByte() -> {
                    val guildId = (channel as TextChannel).guild.idLong
                    memberGateService.setReminderTime(guildId, event.message.contentRaw.toLong())
                    channel.sendMessage("Reminder time set").queue { it.delete().queueAfter(1, TimeUnit.MINUTES) }
                    destroy()
                }
            }
        }

        private fun findDesiredAction(
            messageContent: String,
            event: MessageReceivedEvent
        ) {
            when (messageContent.toByte()) {
                0.toByte() -> {
                    sequenceNumber = 1
                    val addAQuestionMessage: MessageCreateBuilder = MessageCreateBuilder()
                        .addContent("Please send the question to add:\n")
                    super.channel.sendMessage(addAQuestionMessage.build())
                        .queue { super.addMessageToCleaner(it) }
                }

                1.toByte() -> {
                    sequenceNumber = 2
                    val questionListMessage = MessageCreateBuilder()
                    val guildId = (channel as TextChannel).guild.idLong
                    questions = memberGateService.getQuestions(guildId).toList()
                    for (i in questions.indices) {
                        questionListMessage.addContent(i.toString()).addContent(": ").addContent(questions[i])
                            .addContent("\n")
                    }
                    questionListMessage.addContent("\n").addContent("Respond with the question number to remove it.")
                    channel.sendMessage(questionListMessage.build()).queue { super.addMessageToCleaner(it) }
                }

                2.toByte() -> {
                    sequenceNumber = 3
                    channel.sendMessage("Please send a url (that will stay online) to an image to be used as welcome image.")
                        .queue { addMessageToCleaner(it) }
                }

                3.toByte() -> {
                    sequenceNumber = 5
                    val welcomeMessageList = StringBuilder()
                    val guildId = (channel as TextChannel).guild.idLong
                    welcomeMessages = ArrayList(welcomeMessageService.getWelcomeMessages(guildId))
                    for (i in welcomeMessages.indices) {
                        welcomeMessageList.append(i.toString()).append(": ").append(welcomeMessages[i])
                            .append('\n')
                    }
                    welcomeMessageList.append('\n')
                        .append("Respond with the welcome message number to remove it.")

                    SplitUtil.split(
                        welcomeMessageList.toString(),
                        Message.MAX_CONTENT_LENGTH,
                        SplitUtil.Strategy.NEWLINE
                    ).forEach { message ->
                        channel.sendMessage(message).queue { super.addMessageToCleaner(it) }
                    }
                }

                4.toByte() -> {
                    channel.sendMessage(MENTION_CHANNEL_TO_SET)
                        .queue { addMessageToCleaner(it) }
                    sequenceNumber = 6
                }

                5.toByte() -> {
                    channel.sendMessage(MENTION_CHANNEL_TO_SET)
                        .queue { addMessageToCleaner(it) }
                    sequenceNumber = 7
                }

                6.toByte() -> {
                    channel.sendMessage("Please type the exact role name you want to set (please make sure the role name is unique).")
                        .queue { addMessageToCleaner(it) }
                    sequenceNumber = 8
                }

                7.toByte() -> {
                    channel.sendMessage("Please mention the channel you want to set.")
                        .queue { addMessageToCleaner(it) }
                    sequenceNumber = 9
                }

                8.toByte() -> {
                    val guildId = (channel as TextChannel).guild.idLong
                    memberGateService.resetGateSettings(guildId)
                    event.channel.sendMessage("Member approval gate settings wiped and disabled.").queue {
                        it.delete().queueAfter(1, TimeUnit.MINUTES)
                    }
                    destroy()
                }

                9.toByte() -> {
                    val guildId = (channel as TextChannel).guild.idLong
                    memberGateService.resetWelcomeSettings(guildId)
                    welcomeMessageService.removeAllWelcomeMessages(guildId)
                    event.channel.sendMessage("Welcome settings wiped and disabled.").queue {
                        it.delete().queueAfter(1, TimeUnit.MINUTES)
                    }
                    destroy()
                }

                10.toByte() -> {
                    val guildId = (channel as TextChannel).guild.idLong
                    memberGateService.resetAllSettings(guildId)
                    event.channel.sendMessage("Member gate module settings wiped and disabled.").queue {
                        it.delete().queueAfter(1, TimeUnit.MINUTES)
                    }
                    destroy()
                }

                11.toByte() -> {
                    sequenceNumber = 10
                    channel.sendMessage("Please enter the amount of hour(s) a user has to complete the joining process")
                        .queue {
                            addMessageToCleaner(it)
                        }
                }

                12.toByte() -> {
                    val guildId = (channel as TextChannel).guild.idLong
                    memberGateService.setPurgeTime(guildId, null)
                    destroy()
                }

                13.toByte() -> {
                    sequenceNumber = 11
                    channel.sendMessage("Please enter the amount of hour(s) before the user receives a reminder")
                        .queue {
                            addMessageToCleaner(it)
                        }
                }

                14.toByte() -> {
                    val guildId = (channel as TextChannel).guild.idLong
                    memberGateService.setReminderTime(guildId, null)
                    destroy()
                }
            }
        }

        @Transactional
        override fun onMessageReceived(event: MessageReceivedEvent) {
            super.onMessageReceived(event)
        }
    }

}
