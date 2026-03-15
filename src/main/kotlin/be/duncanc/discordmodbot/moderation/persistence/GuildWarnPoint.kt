package be.duncanc.discordmodbot.moderation.persistence

import jakarta.persistence.*
import jakarta.validation.constraints.Future
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import org.hibernate.Hibernate
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import org.springframework.util.Assert
import java.io.Serializable
import java.time.OffsetDateTime
import java.util.*

@Entity
@IdClass(GuildWarnPoint.GuildWarnPointId::class)
@Table(name = "guild_warn_points")
data class GuildWarnPoint(
    @Id
    @Column(updatable = false)
    val userId: Long,
    @Id
    @Column(updatable = false)
    val guildId: Long,
    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(updatable = false, columnDefinition = "BINARY(16)", unique = true)
    val id: UUID = UUID.randomUUID(),
    @field:Positive
    @Column(nullable = false, updatable = false)
    val points: Int,
    @field:NotNull
    @Column(nullable = false, updatable = false)
    val creatorId: Long,
    @field:NotBlank
    @Column(nullable = false, updatable = false, columnDefinition = "TEXT")
    val reason: String,
    @field:NotNull
    @Column(nullable = false, updatable = false)
    val creationDate: OffsetDateTime = OffsetDateTime.now(),
    @field:Future
    @field:NotNull
    @Column(nullable = false, updatable = false)
    val expireDate: OffsetDateTime
) {

    init {
        if (points <= 0) {
            throw IllegalArgumentException("Points need to be a positive number")
        }
        if (expireDate.isBefore(creationDate)) {
            throw IllegalArgumentException("UserWarnPoints can't expire before the date it was created.")
        }
        Assert.hasLength(reason, "The reason can not be empty.")
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || Hibernate.getClass(this) != Hibernate.getClass(other)) return false
        other as GuildWarnPoint

        return userId == other.userId
                && guildId == other.guildId
                && id == other.id
    }

    override fun hashCode(): Int = Objects.hash(userId, guildId, id)

    @Override
    override fun toString(): String {
        return this::class.simpleName + "(userId = $userId , guildId = $guildId , id = $id )"
    }


    data class GuildWarnPointId(
        @Id
        @Column(updatable = false)
        val userId: Long? = null,
        @Id
        @Column(updatable = false)
        val guildId: Long? = null,
        @Id
        @JdbcTypeCode(SqlTypes.BINARY)
        @Column(updatable = false, columnDefinition = "BINARY(16)", unique = true)
        val id: UUID? = null,
    ) : Serializable
}
