package be.duncanc.discordmodbot.member.gate

import be.duncanc.discordmodbot.member.gate.persistence.MemberGateQuestion
import be.duncanc.discordmodbot.member.gate.persistence.MemberGateQuestionRepository
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.exceptions.ErrorResponseException
import net.dv8tion.jda.api.requests.ErrorResponse
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.concurrent.TimeUnit

@Component
class ReviewManager(
    private val memberGateQuestionRepository: MemberGateQuestionRepository,
    private val memberGateService: MemberGateService,
    private val promptRegistry: ReviewPromptRegistry
) {
    companion object {
        private val LOG = LoggerFactory.getLogger(ReviewManager::class.java)
    }

    @Transactional(readOnly = true)
    fun createSession(guildId: Long, maxMembers: Int? = null): ReviewSession? {
        val storedQuestions = memberGateQuestionRepository.findAll()

        val pendingUserIds = storedQuestions
            .asSequence()
            .filterNotNull()
            .filter { it.guildId == guildId && it.userId.toULong() > 0uL }
            .sortedBy { it.queuedAt }
            .map { it.userId }
            .take(maxMembers ?: Int.MAX_VALUE)
            .toList()

        return pendingUserIds.takeIf { it.isNotEmpty() }?.let(::ReviewSession)
    }

    @Transactional
    fun pruneStaleApplicants(guild: Guild, jda: JDA) {
        memberGateQuestionRepository.findAll()
            .filterNotNull()
            .filter { it.guildId == guild.idLong && it.userId.toULong() > 0uL }
            .forEach { question ->
                guild.retrieveMemberById(question.userId).queue(
                    { },
                    { throwable ->
                        if ((throwable as? ErrorResponseException)?.errorResponse == ErrorResponse.UNKNOWN_MEMBER) {
                            clearPendingQuestion(guild.idLong, jda, question.userId)
                        } else {
                            LOG.warn(
                                "Failed to check membership of {} in guild {}; keeping the pending question.",
                                question.userId,
                                guild.idLong,
                                throwable
                            )
                        }
                    }
                )
            }
    }

    @Transactional(readOnly = true)
    fun hasPendingApplicants(guildId: Long): Boolean {
        return memberGateQuestionRepository.findAll()
            .filterNotNull()
            .any { it.guildId == guildId && it.userId.toULong() > 0uL }
    }

    @Transactional(readOnly = true)
    fun countPendingApplicants(guildId: Long): Int {
        return memberGateQuestionRepository.findAll()
            .filterNotNull()
            .count { it.guildId == guildId && it.userId.toULong() > 0uL }
    }

    @Transactional(readOnly = true)
    fun getPendingQuestion(guildId: Long, userId: Long): MemberGateQuestion? {
        return memberGateQuestionRepository.findById(MemberGateQuestion.createId(guildId, userId)).orElse(null)
    }

    @Transactional(readOnly = true)
    fun hasPendingQuestion(guildId: Long, userId: Long): Boolean = getPendingQuestion(guildId, userId) != null

    @Transactional
    fun savePendingQuestion(member: Member, question: String, answer: String) {
        memberGateQuestionRepository.save(
            MemberGateQuestion(
                id = MemberGateQuestion.createId(member.guild.idLong, member.user.idLong),
                userId = member.user.idLong,
                question = question,
                answer = answer,
                guildId = member.guild.idLong,
                queuedAt = System.currentTimeMillis()
            )
        )
    }

    fun rememberInformPrompt(guildId: Long, userId: Long, messageId: Long) {
        promptRegistry.remember(guildId, userId, messageId)
    }

    fun clearInformPrompt(guildId: Long, jda: JDA, userId: Long) {
        val messageId = promptRegistry.forget(guildId, userId) ?: return
        memberGateService.getGateChannel(guildId, jda)
            ?.retrieveMessageById(messageId)
            ?.queue({ message -> message.delete().queue() }) { }
    }

    @Transactional
    fun approve(guild: Guild, jda: JDA, userId: Long): String {
        if (!hasPendingQuestion(guild.idLong, userId)) {
            return "This applicant is no longer waiting for approval."
        }

        val member = guild.getMemberById(userId)
        if (member != null) {
            memberGateService.getMemberRole(guild.idLong, jda)
                ?.let { guild.addRoleToMember(member, it).reason("Member gate approval.").queue() }
        }

        clearPendingQuestion(guild.idLong, jda, userId)
        return if (member != null) {
            "Approved ${member.user.asMention}."
        } else {
            "The user has left; no further action is needed."
        }
    }

    @Transactional
    fun reject(guild: Guild, jda: JDA, userId: Long, manualAction: Boolean = false): String {
        if (!hasPendingQuestion(guild.idLong, userId)) {
            return "This applicant is no longer waiting for approval."
        }

        val member = guild.getMemberById(userId)
        val gateChannel = memberGateService.getGateChannel(guild.idLong, jda)
        if (member != null && !manualAction) {
            gateChannel?.sendMessage(
                "Your answer was incorrect ${member.user.asMention}. You can use the `/join` command to try again."
            )?.queue { it.delete().queueAfter(1, TimeUnit.HOURS) }
        }

        clearPendingQuestion(guild.idLong, jda, userId)
        return when {
            member == null -> "The user already left; no further action is needed."
            manualAction -> "Marked ${member.user.asMention} for manual action and removed them from the review queue."
            else -> "Rejected ${member.user.asMention}. They can use `/join` to try again."
        }
    }

    @Transactional
    fun clearPendingQuestion(guildId: Long, jda: JDA, userId: Long) {
        if (!hasPendingQuestion(guildId, userId)) {
            return
        }

        memberGateQuestionRepository.deleteById(MemberGateQuestion.createId(guildId, userId))
        clearInformPrompt(guildId, jda, userId)
    }
}
