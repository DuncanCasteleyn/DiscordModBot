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

import net.dv8tion.jda.core.EmbedBuilder
import net.dv8tion.jda.core.MessageBuilder
import net.dv8tion.jda.core.entities.Message
import net.dv8tion.jda.core.entities.User
import net.dv8tion.jda.core.events.message.MessageReceivedEvent
import java.awt.Color
import javax.persistence.*

@Entity
class GuildMemberGate(
        @Id
        val guildId: Long? = null,
        @Column(nullable = false)
        val memberRole: Long? = null,
        @Column(nullable = false)
        val rulesTextChannel: Long? = null,
        @Column(nullable = false)
        val gateTextChannel: Long? = null,
        @Column(nullable = false)
        val welcomeTextChannel: Long? = null,
        @ElementCollection(targetClass = WelcomeMessage::class)
        val welcomeMessages: MutableSet<WelcomeMessage> = HashSet(),
        @ElementCollection(targetClass = Question::class)
        val questions: MutableSet<Question> = HashSet()

) {

    /**
     * Immutable class containing a welcome message and url for an image to be used in an embed.
     */
    @Embeddable
    data class WelcomeMessage(private val imageUrl: String, private val message: String) {

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

    /**
     * Container class containing questions and the keyword checks.
     */
    @Embeddable
    class Question internal constructor(val question: String) {
        private val keywordList: ArrayList<ArrayList<String>> = ArrayList()

        /**
         * @param event the event containing the message that came from the sequences to answer the question.
         * @return true when the answer is correct, false otherwise.
         */
        internal fun checkAnswer(event: MessageReceivedEvent): Boolean = keywordList.all { it.any { it.toLowerCase() in event.message.contentDisplay.toLowerCase() } }

        /**
         * Adds more keywords to a question.
         */
        internal fun addKeywords(keywords: ArrayList<String>) {
            keywordList.add(keywords)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as GuildMemberGate

        return guildId == other.guildId
    }

    override fun hashCode(): Int {
        return guildId?.hashCode() ?: 0
    }
}