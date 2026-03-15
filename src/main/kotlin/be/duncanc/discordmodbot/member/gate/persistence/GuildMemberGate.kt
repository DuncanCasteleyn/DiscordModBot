package be.duncanc.discordmodbot.member.gate.persistence

import jakarta.persistence.*

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
    @ElementCollection(fetch = FetchType.EAGER)
    @Column(name = "question")
    val questions: MutableSet<String> = HashSet()
)
