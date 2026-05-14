package be.duncanc.discordmodbot.moderation.persistence

import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.Table

@Entity
@Table(name = "sticky_role_configs")
data class StickyRoleConfig(
    @Id
    @Column(name = "guild_id", updatable = false)
    val guildId: Long,

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
        name = "sticky_role_config_roles",
        joinColumns = [JoinColumn(name = "sticky_role_config_guild_id")]
    )
    @Column(name = "role_id", nullable = false)
    var roleIds: MutableSet<Long> = HashSet()
)
