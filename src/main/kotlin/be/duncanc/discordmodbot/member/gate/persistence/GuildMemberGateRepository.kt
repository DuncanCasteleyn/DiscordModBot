package be.duncanc.discordmodbot.member.gate.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface GuildMemberGateRepository : JpaRepository<GuildMemberGate, Long>
