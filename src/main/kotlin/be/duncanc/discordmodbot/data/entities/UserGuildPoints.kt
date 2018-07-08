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

import be.duncanc.discordmodbot.data.embeddables.UserPoints
import javax.persistence.*

@Entity
@IdClass(UserGuildPoints.UserGuildPointsId::class)
@Table(name = "user_guild_points")
data class UserGuildPoints(
        @Id
        @Column(updatable = false)
        val userId: Long? = null,
        @Id
        @Column(updatable = false)
        val guildId: Long? = null,
        @ElementCollection(targetClass = UserPoints::class, fetch = FetchType.EAGER)
        @CollectionTable(name = "user_has_guild_points")
        val points: MutableSet<UserPoints> = HashSet()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UserGuildPoints

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
    )
}