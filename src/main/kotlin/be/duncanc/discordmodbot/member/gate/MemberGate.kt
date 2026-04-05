package be.duncanc.discordmodbot.member.gate

import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleAddEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

@Component
@Transactional
class MemberGate(
    private val memberGateService: MemberGateService,
    private val welcomeMessageService: WelcomeMessageService,
    private val reviewManager: ReviewManager
) : ListenerAdapter() {
    companion object {
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
     * If the channel contains more than 100 messages, all messages past 100 will be ignored.
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
        val userMessages = ArrayList<Message>()
        gateTextChannel.iterableHistory
            .takeAsync(100)
            .thenApply { messages ->
                messages.forEach {
                    if (it.author == user || it.contentRaw.contains(user.id)) {
                        userMessages.add(it)
                    }
                }
                true
            }.thenRun {
                gateTextChannel.purgeMessages(userMessages)
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
}
