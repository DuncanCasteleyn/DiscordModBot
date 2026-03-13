package be.duncanc.discordmodbot.member.gate.persistence

import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.Id

@Entity
data class GuildMemberGate(
    @Id
    val guildId: Long,
    val memberRole: Long? = null,
    val rulesTextChannel: Long? = null,
    val gateTextChannel: Long? = null,
    val welcomeTextChannel: Long? = null,
    val removeTimeHours: Long? = null,
    val reminderTimeHours: Long? = null,
    @ElementCollection
    @Column(name = "question")
    val questions: MutableSet<String> = HashSet()
)
