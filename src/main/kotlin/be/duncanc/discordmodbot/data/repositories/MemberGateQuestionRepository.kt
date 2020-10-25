package be.duncanc.discordmodbot.data.repositories

import be.duncanc.discordmodbot.data.entities.MemberGateQuestion
import org.springframework.data.repository.CrudRepository

interface MemberGateQuestionRepository : CrudRepository<MemberGateQuestion, Long>
