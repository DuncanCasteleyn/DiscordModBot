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

import org.springframework.util.Assert
import java.time.OffsetDateTime
import java.util.*
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Table
import javax.validation.constraints.Future
import javax.validation.constraints.NotBlank
import javax.validation.constraints.NotNull
import javax.validation.constraints.Positive

@Entity
@Table(name = "user_warn_points")
data class UserWarnPoints(
        @Id
        @Column(updatable = false, columnDefinition = "BINARY(16)")
        val id: UUID = UUID.randomUUID(),
        @Positive
        @Column(nullable = false, updatable = false)
        val points: Int? = null,
        @NotNull
        @Column(nullable = false, updatable = false)
        val creatorId: Long? = null,
        @NotBlank
        @Column(nullable = false, updatable = false, columnDefinition = "TEXT")
        val reason: String? = null,
        @NotNull
        @Column(nullable = false, updatable = false)
        val creationDate: OffsetDateTime = OffsetDateTime.now(),
        @Future
        @NotNull
        @Column(nullable = false, updatable = false)
        val expireDate: OffsetDateTime? = null
) {
    init {
        if (points != null && points <= 0) {
            throw IllegalArgumentException("Points need to be a positive number")
        }
        if (expireDate != null && expireDate.isBefore(creationDate)) {
            throw IllegalArgumentException("UserWarnPoints can't expire before the date it was created.")
        }
        if (reason != null) {
            Assert.hasLength(reason, "The reason can not be empty.")
        }
    }
}