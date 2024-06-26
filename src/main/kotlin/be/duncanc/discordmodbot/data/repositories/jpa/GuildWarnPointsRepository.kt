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

package be.duncanc.discordmodbot.data.repositories.jpa

import be.duncanc.discordmodbot.data.entities.GuildWarnPoint
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime
import java.util.*

@Repository
interface GuildWarnPointsRepository : JpaRepository<GuildWarnPoint, GuildWarnPoint.GuildWarnPointId> {

    fun countAllByGuildIdAndUserIdAndExpireDateAfter(guildId: Long, userId: Long, expireDate: OffsetDateTime): Int

    fun findAllByGuildIdAndExpireDateAfter(guildId: Long, expireDate: OffsetDateTime): Collection<GuildWarnPoint>

    fun findAllByGuildIdAndUserId(guildId: Long, userId: Long): Collection<GuildWarnPoint>

    fun findAllByGuildIdAndUserIdAndExpireDateAfter(
        guildId: Long,
        userId: Long,
        expireDate: OffsetDateTime
    ): Collection<GuildWarnPoint>

    fun deleteAllById(id: UUID)

    fun existsByGuildIdAndUserId(guildId: Long, userId: Long): Boolean
}
