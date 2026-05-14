package be.duncanc.discordmodbot.moderation.persistence

import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.JoinColumn
import jakarta.persistence.Table
import java.io.Serializable

@Entity
@Table(name = "sticky_role_snapshots")
@IdClass(StickyRoleSnapshot.StickyRoleSnapshotId::class)
data class StickyRoleSnapshot(
    @Id
    @Column(name = "guild_id", updatable = false)
    val guildId: Long,
    @Id
    @Column(name = "user_id", updatable = false)
    val userId: Long,

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
        name = "sticky_role_snapshot_roles",
        joinColumns = [
            JoinColumn(name = "sticky_role_snapshot_guild_id", referencedColumnName = "guild_id"),
            JoinColumn(name = "sticky_role_snapshot_user_id", referencedColumnName = "user_id")
        ]
    )
    @Column(name = "role_id", nullable = false)
    var roleIds: MutableSet<Long> = HashSet()
) {
    data class StickyRoleSnapshotId(
        val guildId: Long? = null,
        val userId: Long? = null
    ) : Serializable
}
