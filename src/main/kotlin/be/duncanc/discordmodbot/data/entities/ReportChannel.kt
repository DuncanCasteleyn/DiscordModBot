package be.duncanc.discordmodbot.data.entities

import javax.persistence.Entity
import javax.persistence.Id

@Entity
class ReportChannel(@Id val guildId: Long, val textChannelId : Long)