package be.duncanc.discordmodbot.data.repositories

import be.duncanc.discordmodbot.data.entities.MuteRole
import org.springframework.data.repository.CrudRepository

interface MuteRolesRepository : CrudRepository<MuteRole, Long>