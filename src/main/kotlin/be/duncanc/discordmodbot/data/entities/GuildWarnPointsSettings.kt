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

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive

@Entity
@Table(name = "guild_warn_point_settings")
data class GuildWarnPointsSettings(
    @Id
    val guildId: Long,
    @Positive
    @Column(nullable = false)
    var maxPointsPerReason: Int = 1,
    @Positive
    @Column(nullable = false)
    var announcePointsSummaryLimit: Int = 3,
    @NotNull
    @Column(nullable = false)
    var announceChannelId: Long,
    var overrideWarnCommand: Boolean = false
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as GuildWarnPointsSettings

        return guildId == other.guildId
    }

    override fun hashCode(): Int {
        return guildId.hashCode()
    }
}
