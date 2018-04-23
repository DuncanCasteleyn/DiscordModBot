package be.duncanc.discordmodbot.data.entities

import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
@Entity
data class ReportChannel constructor(@Id val guildId : Long? = null, @Column(nullable = false) val textChannelId : Long? = null)