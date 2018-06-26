/*
 * MIT License
 *
 * Copyright (c) 2017 Duncan Casteleyn
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
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
        @NotNull
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