package be.duncanc.discordmodbot.member.gate

import be.duncanc.discordmodbot.member.gate.persistence.*
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import org.springframework.context.annotation.Lazy
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.awt.Color
import java.time.OffsetDateTime
import java.util.*
import java.util.concurrent.TimeUnit

@Service
@Transactional(readOnly = true)
class MemberGateService(
    private val guildMemberGateRepository: GuildMemberGateRepository,
    @Lazy
    private val jda: JDA,
    private val memberGateQuestionRepository: MemberGateQuestionRepository,
    private val joinModalQuestionRepository: JoinModalQuestionRepository
) {

    fun getMemberGates(): List<GuildMemberGate> {
        return guildMemberGateRepository.findAll()
    }

    /**
     * @return null when not configured or channel no longer exists.
     */
    fun getGateChannel(guildId: Long, jda: JDA): TextChannel? {
        val memberGate: GuildMemberGate? = guildMemberGateRepository.findById(guildId).orElse(null)

        return memberGate?.gateTextChannel?.let { jda.getTextChannelById(it) }
    }

    @Transactional
    fun setGateChannel(guildId: Long, gateChannel: TextChannel) {
        val memberGate: GuildMemberGate = guildMemberGateRepository.findById(guildId)
            .orElse(null) ?: GuildMemberGate(guildId)

        guildMemberGateRepository.save(memberGate.copy(gateTextChannel = gateChannel.idLong))
    }

    /**
     * @return null when not configured or channel no longer exists.
     */
    fun getWelcomeChannel(guildId: Long, jda: JDA): TextChannel? {
        val memberGate: GuildMemberGate? = guildMemberGateRepository.findById(guildId).orElse(null)
        return memberGate?.welcomeTextChannel?.let { jda.getTextChannelById(it) }
    }

    @Transactional
    fun setWelcomeChannel(guildId: Long, welcomeChannel: TextChannel) {
        val memberGate: GuildMemberGate = guildMemberGateRepository.findById(guildId)
            .orElse(null) ?: GuildMemberGate(guildId)

        guildMemberGateRepository.save(memberGate.copy(welcomeTextChannel = welcomeChannel.idLong))
    }

    /**
     * @return null when not configured or channel no longer exists.
     */
    fun getRuleChannel(guildId: Long, jda: JDA): TextChannel? {
        val memberGate: GuildMemberGate? = guildMemberGateRepository.findById(guildId).orElse(null)

        return memberGate?.rulesTextChannel?.let { jda.getTextChannelById(it) }
    }

    @Transactional
    fun setRulesChannel(guildId: Long, rulesChannel: TextChannel) {
        val memberGate = guildMemberGateRepository.findById(guildId).orElse(GuildMemberGate(guildId))
        if (memberGate != null) {
            guildMemberGateRepository.save(memberGate.copy(rulesTextChannel = rulesChannel.idLong))
        }
    }

    /**
     * @return null when not configured or member role no longer exists.
     */
    fun getMemberRole(guildId: Long, jda: JDA): Role? {
        val memberGate: GuildMemberGate? = guildMemberGateRepository.findById(guildId).orElse(null)

        return memberGate?.memberRole?.let { jda.getRoleById(it) }
    }


    @Transactional
    fun setMemberRole(guildId: Long, memberRole: Role) {
        val memberGate: GuildMemberGate = guildMemberGateRepository.findById(guildId)
            .orElse(null) ?: GuildMemberGate(guildId)

        guildMemberGateRepository.save(memberGate.copy(memberRole = memberRole.idLong))
    }

    fun getQuestions(guildId: Long): Set<String> {
        val memberGate: GuildMemberGate? = guildMemberGateRepository.findById(guildId).orElse(null)

        return if (memberGate != null) {
            Collections.unmodifiableSet(memberGate.questions)
        } else {
            Collections.emptySet()
        }
    }

    @Transactional
    fun addQuestion(guildId: Long, question: String) {
        val memberGate: GuildMemberGate = guildMemberGateRepository.findById(guildId)
            .orElse(null) ?: GuildMemberGate(guildId)

        memberGate.questions.add(question)

        guildMemberGateRepository.save(memberGate)
    }

    @Transactional
    fun removeQuestion(guildId: Long, question: String) {
        val memberGate: GuildMemberGate = guildMemberGateRepository.findById(guildId)
            .orElse(null) ?: GuildMemberGate(guildId)

        memberGate.questions.remove(question)

        guildMemberGateRepository.save(memberGate)
    }

    @Transactional
    fun resetGateSettings(guildId: Long) {
        val guildMemberGate: GuildMemberGate? = guildMemberGateRepository.findById(guildId).orElse(null)

        if (guildMemberGate != null) {
            guildMemberGate.questions.clear()
            guildMemberGateRepository.save(
                guildMemberGate.copy(
                    gateTextChannel = null,
                    memberRole = null,
                    rulesTextChannel = null,
                    removeTimeHours = null
                )
            )
        }
    }

    @Transactional
    fun resetWelcomeSettings(guildId: Long) {
        guildMemberGateRepository.findById(guildId).ifPresent {
            guildMemberGateRepository.save(it.copy(welcomeTextChannel = null))
        }
    }

    @Transactional
    fun resetAllSettings(guildId: Long) {
        guildMemberGateRepository.deleteById(guildId)
    }

    @Transactional
    fun setPurgeTime(guildId: Long, purgeTime: Long?) {
        val guildMemberGate = guildMemberGateRepository.findById(guildId).orElse(null) ?: GuildMemberGate(guildId)

        guildMemberGateRepository.save(guildMemberGate.copy(removeTimeHours = purgeTime))
    }

    fun getPurgeTime(guildId: Long): Long? {
        return guildMemberGateRepository.findById(guildId).orElse(null)?.removeTimeHours
    }

    @Transactional
    fun setReminderTime(guildId: Long, reminderTimeHours: Long?) {
        val guildMemberGate = guildMemberGateRepository.findById(guildId).orElse(null) ?: GuildMemberGate(guildId)

        guildMemberGateRepository.save(guildMemberGate.copy(reminderTimeHours = reminderTimeHours))
    }

    fun getReminderTime(guildId: Long): Long? {
        return guildMemberGateRepository.findById(guildId).orElse(null)?.reminderTimeHours
    }

    @Transactional(readOnly = true)
    @Scheduled(cron = "@hourly")
    fun purgeMembersWithoutRoles() {
        jda.guilds.forEach { guild ->
            val guildSettings = guildMemberGateRepository.findById(guild.idLong).orElse(null)
            val removeTimeHours = guildSettings?.removeTimeHours

            if (removeTimeHours != null && guildSettings.memberRole != null && guildSettings.gateTextChannel != null) {
                guild.members.filter {
                    val reachedTimeLimit =
                        it.timeJoined.isBefore(OffsetDateTime.now().minusHours(removeTimeHours))
                    val notQueuedForApproval = !hasPendingQuestion(guild.idLong, it.user.idLong)
                    val noRoles = it.roles.isEmpty()
                    noRoles && reachedTimeLimit && notQueuedForApproval
                }.forEach { member ->
                    val userKickNotification = EmbedBuilder()
                        .setColor(Color.RED)
                        .setTitle("${guild.name}: You have been kicked", null)
                        .setDescription("Reason: You did not complete the server entry process within ${guildSettings.removeTimeHours} hour(s)")
                        .build()
                    member.user.openPrivateChannel().queue(
                        {
                            it.sendMessageEmbeds(userKickNotification)
                                .queue({ guild.kick(member).queue() }, { guild.kick(member).queue() })
                        },
                        {
                            guild.kick(member).queue()
                        })
                }
            }
        }
    }


    @Transactional(readOnly = true)
    @Scheduled(cron = "@hourly")
    fun sendReminders() {
        jda.guilds.forEach { guild ->
            val guildSettings: GuildMemberGate? = guildMemberGateRepository.findById(guild.idLong).orElse(null)
            val gateTextChannel = guildSettings?.gateTextChannel
            val removeTimeHours = guildSettings?.removeTimeHours

            if (removeTimeHours != null && guildSettings.memberRole != null && gateTextChannel != null && guildSettings.reminderTimeHours != null) {
                guild.members.filter {
                    val minusHours = OffsetDateTime.now().minusHours(removeTimeHours)
                    val shouldBeReminded =
                        it.timeJoined.isBefore(minusHours) && it.timeJoined.isAfter(minusHours.plusHours(1))
                    val notQueuedForApproval = !hasPendingQuestion(guild.idLong, it.user.idLong)
                    val noRoles = it.roles.size < 1

                    noRoles && shouldBeReminded && notQueuedForApproval

                }.forEach { member ->
                    val message = """
                                | Hi, this is a reminder that you have not completed the entry process on ${guild.name} you will be kicked if you don't complete the entry process.
                                | 
                                | Please complete the process in <#$gateTextChannel>.
                            """.trimMargin()

                    member.user.openPrivateChannel().queue(
                        {
                            it.sendMessage(message)
                        },
                        {
                            member.guild.getTextChannelById(gateTextChannel)?.sendMessage(message)
                                ?.queue { it.delete().queueAfter(1, TimeUnit.HOURS) }
                        })
                }
            }
        }
    }

    private fun hasPendingQuestion(guildId: Long, userId: Long): Boolean {
        return memberGateQuestionRepository.findById(MemberGateQuestion.createId(guildId, userId)).isPresent
    }

    @Transactional
    fun saveModalQuestion(guildId: Long, userId: Long, question: String) {
        val modalQuestion = JoinModalQuestion(
            id = JoinModalQuestion.createId(guildId, userId),
            guildId = guildId,
            userId = userId,
            question = question
        )
        joinModalQuestionRepository.save(modalQuestion)
    }

    @Transactional(readOnly = true)
    fun getModalQuestion(guildId: Long, userId: Long): String? {
        return joinModalQuestionRepository
            .findById(JoinModalQuestion.createId(guildId, userId))
            .map { it.question }
            .orElse(null)
    }

    @Transactional
    fun deleteModalQuestion(guildId: Long, userId: Long) {
        joinModalQuestionRepository.deleteById(
            JoinModalQuestion.createId(guildId, userId)
        )
    }
}
