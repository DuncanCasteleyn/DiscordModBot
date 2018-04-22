package be.duncanc.discordmodbot.data.repositories

import be.duncanc.discordmodbot.data.entities.ReportChannel
import org.springframework.data.repository.CrudRepository


interface ReportChannelRepository : CrudRepository<ReportChannel, Long>