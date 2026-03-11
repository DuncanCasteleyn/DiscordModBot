package be.duncanc.discordmodbot.membergate.persistence


import org.springframework.data.keyvalue.repository.KeyValueRepository
import org.springframework.stereotype.Repository

@Repository
interface MemberGateQuestionRepository : KeyValueRepository<MemberGateQuestion, Long>
