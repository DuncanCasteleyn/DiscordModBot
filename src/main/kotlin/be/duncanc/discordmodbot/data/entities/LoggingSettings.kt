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
import jakarta.validation.constraints.NotNull
import org.hibernate.Hibernate

@Entity
@Table(name = "logging_settings")
data class LoggingSettings
    (
    @Id
    @Column(updatable = false)
    val guildId: Long,
    @Column(nullable = false)
    @field:NotNull
    var modLogChannel: Long? = null,
    var userLogChannel: Long? = null,
    var logMessageUpdate: Boolean = true,
    var logMessageDelete: Boolean = true,
    var logMemberJoin: Boolean = true,
    var logMemberLeave: Boolean = true,
    var logMemberBan: Boolean = true,
    var logMemberRemoveBan: Boolean = true,
    @ElementCollection
    @CollectionTable(name = "logging_ignored_channels")
    val ignoredChannels: MutableSet<Long> = HashSet()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || Hibernate.getClass(this) != Hibernate.getClass(other)) return false
        other as LoggingSettings

        return guildId != null && guildId == other.guildId
    }

    override fun hashCode(): Int = javaClass.hashCode()

    @Override
    override fun toString(): String {
        return this::class.simpleName + "(guildId = $guildId )"
    }

}
