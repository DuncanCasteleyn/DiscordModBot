/*
 * Copyright 2018 Duncan Casteleyn
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package be.duncanc.discordmodbot.data.entities

import jakarta.persistence.*
import jakarta.validation.constraints.Future
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import org.hibernate.Hibernate
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
        val userId: Long? = null,
        @Id
        val guildId: Long? = null,
        @Id
        val id: UUID? = null,
    ) : Serializable
}
