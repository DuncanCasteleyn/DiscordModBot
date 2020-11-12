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

package be.duncanc.discordmodbot.data.services

import be.duncanc.discordmodbot.data.entities.BlockedUser
import be.duncanc.discordmodbot.data.repositories.jpa.BlockedUserRepository
import net.dv8tion.jda.api.entities.User
import org.springframework.stereotype.Service

@Service
class UserBlockService(
    val blockedUserRepository: BlockedUserRepository
) {
    fun blockUser(user: User) {
        val blockedUser = BlockedUser(user.idLong)
        blockedUserRepository.save(blockedUser)
        user.openPrivateChannel().queue {
            it.sendMessage("This is an automated message to inform you that you have been blocked by the bot due to spam.")
                .queue()
        }
    }

    fun isBlocked(userId: Long): Boolean {
        return blockedUserRepository.existsById(userId)
    }
}
