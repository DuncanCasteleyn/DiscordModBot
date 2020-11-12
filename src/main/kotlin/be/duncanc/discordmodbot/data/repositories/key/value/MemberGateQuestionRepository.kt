package be.duncanc.discordmodbot.data.repositories.key.value

import be.duncanc.discordmodbot.data.redis.hash.MemberGateQuestion
import org.springframework.data.keyvalue.repository.KeyValueRepository
import org.springframework.stereotype.Repository

@Repository
interface MemberGateQuestionRepository : KeyValueRepository<MemberGateQuestion, Long>
