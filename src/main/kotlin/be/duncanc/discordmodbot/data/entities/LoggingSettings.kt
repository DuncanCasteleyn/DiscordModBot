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

import javax.persistence.*

@Entity
@Table(name = "logging_settings")
data class LoggingSettings
constructor(
        @Id
        val guildId: Long? = null,
        var logMessageDelete: Boolean = true,
        var logMessageUpdate: Boolean = true,
        var logMemberRemove: Boolean = true,
        var logMemberBan: Boolean = true,
        var logMemberAdd: Boolean = true,
        var logMemberRemoveBan: Boolean = true,
        @ElementCollection
        @CollectionTable(name = "logging_ignored_channels")
        val ignoredChannels: Set<Long> = HashSet<Long>()
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as LoggingSettings

        return guildId == other.guildId
    }

    override fun hashCode(): Int {
        return guildId?.hashCode() ?: 0
    }
}