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

import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.MessageBuilder
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.User
import java.awt.Color
import javax.persistence.*


@Entity
data class GuildMemberGate(
    @Id
    val guildId: Long,
    val memberRole: Long? = null,
    val rulesTextChannel: Long? = null,
    val gateTextChannel: Long? = null,
    val welcomeTextChannel: Long? = null,
    val removeTimeHours: Long? = null,
    @Suppress("DEPRECATION")
    @ElementCollection(targetClass = WelcomeMessage::class)
    @Deprecated("To be removed due to length limitations", level = DeprecationLevel.WARNING)
    val welcomeMessages: MutableSet<WelcomeMessage> = HashSet(),
    @ElementCollection
    @Column(name = "question")
    val questions: MutableSet<String> = HashSet()
) {

    /**
     * class containing a welcome message and url for an image to be used in an embed.
     */
    @Embeddable
    @Deprecated("To be removed due to length limitations", level = DeprecationLevel.WARNING)
    data class WelcomeMessage(
        @Column(nullable = false)
        private var imageUrl: String? = null,
        @Column(nullable = false, length = 2048)
        private var message: String? = null
    ) {

        fun getWelcomeMessage(user: User): Message {
            val joinEmbed = EmbedBuilder()
                .setDescription(this.message)
                .setImage(imageUrl)
                .setColor(Color.GREEN)
                .build()
            return MessageBuilder()
                .append(user.asMention)
                .setEmbed(joinEmbed)
                .build()
        }
    }
}
