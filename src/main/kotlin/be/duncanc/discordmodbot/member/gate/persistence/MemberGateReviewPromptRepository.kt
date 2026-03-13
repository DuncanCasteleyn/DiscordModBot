package be.duncanc.discordmodbot.member.gate.persistence

import org.springframework.data.keyvalue.repository.KeyValueRepository
import org.springframework.stereotype.Repository

@Repository
interface MemberGateReviewPromptRepository : KeyValueRepository<MemberGateReviewPrompt, String>
