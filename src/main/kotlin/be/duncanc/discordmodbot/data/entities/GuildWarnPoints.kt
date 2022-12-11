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

import java.io.Serializable
import java.time.OffsetDateTime
import javax.persistence.*

@Entity
@IdClass(GuildWarnPoints.GuildWarnPointsId::class)
@Table(name = "guild_warn_points")
data class GuildWarnPoints(
    @Id
    @Column(updatable = false)
    val userId: Long,
    @Id
    @Column(updatable = false)
    val guildId: Long,
    @OneToMany(fetch = FetchType.EAGER, cascade = [(CascadeType.ALL)], orphanRemoval = true)
    @JoinTable(name = "user_has_warn_points")
    val points: MutableSet<UserWarnPoints> = HashSet()
) : Comparable<GuildWarnPoints> {

    /**
     * Compares the object so that the user with the most active points will be ordered first and those with no activate points last
     * Alternatively if the users have the same amount of points the lowest userId will be ordered first and highest last.
     */
    override fun compareTo(other: GuildWarnPoints): Int {
        val totalPoints = activePointsAmount()
        val totalPointsOther = other.activePointsAmount()
        return when {
            totalPoints > totalPointsOther -> -1
            totalPoints < totalPointsOther -> +1
            userId < other.userId -> +1
            userId > other.userId -> -1
            else -> 0
        }
    }

    fun activePointsAmount(): Int {
        var totalPoints = 0
        filterExpiredPoints().forEach { totalPoints += it.points }
        return totalPoints
    }

    fun filterExpiredPoints() = points.filter { it.expireDate.isAfter(OffsetDateTime.now()) }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as GuildWarnPoints

        if (userId != other.userId) return false
        return guildId == other.guildId
    }

    override fun hashCode(): Int {
        var result = userId.hashCode()
        result = 31 * result + guildId.hashCode()
        return result
    }

    data class GuildWarnPointsId(
        @Id
        val userId: Long? = null,
        @Id
        val guildId: Long? = null
    ) : Serializable
}
