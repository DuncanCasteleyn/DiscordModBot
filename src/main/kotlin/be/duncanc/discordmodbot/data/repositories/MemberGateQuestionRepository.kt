package be.duncanc.discordmodbot.data.repositories

import be.duncanc.discordmodbot.data.entities.MemberGateQuestion
import org.springframework.data.keyvalue.repository.KeyValueRepository

interface MemberGateQuestionRepository : KeyValueRepository<MemberGateQuestion, Long>
