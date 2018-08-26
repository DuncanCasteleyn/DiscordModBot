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
import java.util.*
import javax.persistence.*
import javax.validation.constraints.NotNull

@Entity
@Table(name = "i_am_roles_categories")
@IdClass(IAmRolesCategory.IAmRoleId::class)
data class IAmRolesCategory
constructor(
        @Id
        @Column(updatable = false)
        val guildId: Long? = null,

        @Id
        @GeneratedValue(generator = "i_am_roles_category_id_seq")
        @SequenceGenerator(name = "i_am_roles_category_id_seq", sequenceName = "i_am_roles_seq", allocationSize = 1)
        @Column(insertable = false, updatable = false)
        val categoryId: Long? = null,

        @Column(unique = true, nullable = false)
        var categoryName: String? = null,

        @Column(nullable = false)
        @NotNull
        var allowedRoles: Int = 0,

        @Column(nullable = false)
        @field:NotNull
        @CollectionTable(name = "i_am_roles_category_roles")
        @ElementCollection
        val roles: MutableSet<Long> = HashSet()
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as IAmRolesCategory

        if (guildId != other.guildId) return false
        if (categoryId != other.categoryId) return false

        return true
    }

    override fun hashCode(): Int {
        var result = guildId?.hashCode() ?: 0
        result = 31 * result + (categoryId?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String {
        return "IAmRolesCategory(guildId=$guildId, categoryId=$categoryId, categoryName=$categoryName, allowedRoles=$allowedRoles)"
    }


    data class IAmRoleId(
            val guildId: Long? = null,

            val categoryId: Long? = null) : Serializable {

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as IAmRoleId

            if (guildId != other.guildId) return false
            if (categoryId != other.categoryId) return false

            return true
        }

        override fun hashCode(): Int {
            var result = guildId?.hashCode() ?: 0
            result = 31 * result + (categoryId?.hashCode() ?: 0)
            return result
        }

        override fun toString(): String {
            return "IAmRoleId(guildId=$guildId, iAmRoleId=$categoryId)"
        }
    }
}