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

import be.duncanc.discordmodbot.data.embeddables.UserWarnPoints
import java.io.Serializable
import java.time.OffsetDateTime
import javax.persistence.*
import javax.validation.Valid

@Entity
@IdClass(GuildWarnPoints.UserGuildPointsId::class)
@Table(name = "user_warn_points")
data class GuildWarnPoints(
        @Id
        @Column(updatable = false)
        val userId: Long? = null,
        @Id
        @Column(updatable = false)
        val guildId: Long? = null,
        @Valid
        @ElementCollection(targetClass = UserWarnPoints::class, fetch = FetchType.EAGER)
        @CollectionTable(name = "user_has_warn_points")
        val points: MutableSet<UserWarnPoints> = HashSet()
) : Comparable<GuildWarnPoints> {

    /**
     * Compares the object so that the user with the most active points will be ordered first and those with no activate points last
     */
    override fun compareTo(other: GuildWarnPoints): Int {
        val totalPoints = run {
            var totalPoints = 0
            filterExpiredPoints().forEach { totalPoints += it.points ?: 0 }
            totalPoints
        }
        val totalPointsOther = run {
            var totalPointsOther = 0
            other.filterExpiredPoints().forEach { totalPointsOther += it.points ?: 0 }
            totalPointsOther
        }
        return when {
            totalPoints > totalPointsOther -> -1
            totalPoints < totalPointsOther -> +1
            else -> 0
        }
    }

    fun filterExpiredPoints() = points.filter { it.expireDate?.isAfter(OffsetDateTime.now()) == true }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as GuildWarnPoints

        if (userId != other.userId) return false
        return guildId == other.guildId
    }

    override fun hashCode(): Int {
        var result = userId?.hashCode() ?: 0
        result = 31 * result + (guildId?.hashCode() ?: 0)
        return result
    }

    data class UserGuildPointsId(
            @Id
            val userId: Long? = null,
            @Id
            val guildId: Long? = null
    ) : Serializable
}