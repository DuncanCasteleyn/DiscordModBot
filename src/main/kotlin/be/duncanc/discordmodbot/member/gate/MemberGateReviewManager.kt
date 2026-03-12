package be.duncanc.discordmodbot.member.gate

import be.duncanc.discordmodbot.member.gate.persistence.MemberGateQuestion
import be.duncanc.discordmodbot.member.gate.persistence.MemberGateQuestionRepository
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.concurrent.TimeUnit

@Component
class MemberGateReviewManager(
    private val memberGateQuestionRepository: MemberGateQuestionRepository,
    private val memberGateService: MemberGateService,
    private val promptRegistry: MemberGateReviewPromptRegistry
) {
    @Transactional(readOnly = true)
    fun createSession(guildId: Long): MemberGateReviewSession? {
        val storedQuestions = memberGateQuestionRepository.findAll()

        val pendingUserIds = storedQuestions
            .asSequence()
            .filterNotNull()
            .filter { it.guildId == guildId }
            .sortedBy { it.queuedAt }
            .map { it.id }
            .toList()

        return pendingUserIds.takeIf { it.isNotEmpty() }?.let(::MemberGateReviewSession)
    }

    @Transactional(readOnly = true)
    fun getPendingQuestion(guildId: Long, userId: Long): MemberGateQuestion? {
        return memberGateQuestionRepository.findById(userId).orElse(null)
            ?.takeIf { it.guildId == guildId }
    }

    @Transactional(readOnly = true)
    fun hasPendingQuestion(guildId: Long, userId: Long): Boolean = getPendingQuestion(guildId, userId) != null

    @Transactional
    fun savePendingQuestion(member: Member, question: String, answer: String) {
        memberGateQuestionRepository.save(
            MemberGateQuestion(
                id = member.user.idLong,
                question = question,
                answer = answer,
                guildId = member.guild.idLong,
                queuedAt = System.currentTimeMillis()
            )
        )
    }

    fun rememberInformPrompt(userId: Long, messageId: Long) {
        promptRegistry.remember(userId, messageId)
    }

    fun clearInformPrompt(guildId: Long, jda: JDA, userId: Long) {
        val messageId = promptRegistry.forget(userId) ?: return
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
            memberGateService.getMemberRole(guild.idLong, jda)?.let { guild.addRoleToMember(member, it).queue() }
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
                "Your answer was incorrect ${member.user.asMention}. You can use the `!join` command to try again."
            )?.queue { it.delete().queueAfter(1, TimeUnit.HOURS) }
        }

        clearPendingQuestion(guild.idLong, jda, userId)
        return when {
            member == null -> "The user already left; no further action is needed."
            manualAction -> "Marked ${member.user.asMention} for manual action and removed them from the review queue."
            else -> "Rejected ${member.user.asMention}. They can use `!join` to try again."
        }
    }

    @Transactional
    fun clearPendingQuestion(guildId: Long, jda: JDA, userId: Long) {
        if (!hasPendingQuestion(guildId, userId)) {
            return
        }

        memberGateQuestionRepository.deleteById(userId)
        clearInformPrompt(guildId, jda, userId)
    }
}
