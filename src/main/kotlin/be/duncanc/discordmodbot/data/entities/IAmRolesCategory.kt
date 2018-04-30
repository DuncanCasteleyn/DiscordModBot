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
data class IAmRolesCategory
/**
 * Constructor for a new IAmRolesCategory.
 *
 * @param iAmRoleId The name of the category.
 * @param allowedRoles The amount of roles you can have from the same category.
 */
(
        @EmbeddedId
        val iAmRoleId: IAmRoleId? = null,

        @Column(unique = true, nullable = false)
        var categoryName: String? = null,

        @NotNull
        var allowedRoles: Int = 0,

        @NotNull
        @ElementCollection
        val roles: MutableSet<Long> = HashSet()) {

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null || javaClass != other.javaClass) {
            return false
        }

        val that = other as IAmRolesCategory

        return iAmRoleId == that.iAmRoleId
    }

    override fun hashCode(): Int = iAmRoleId?.hashCode() ?: 0

    override fun toString(): String {
        return "IAmRolesCategory(iAmRoleId=$iAmRoleId, allowedRoles=$allowedRoles)"
    }

    @Embeddable
    data class IAmRoleId(
            @NotNull
            val guildId: Long? = null,


            @NotNull
            @Column(insertable = false)
            @GeneratedValue(strategy = GenerationType.IDENTITY)
            val categoryUUID: UUID = UUID.randomUUID()) : Serializable {

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as IAmRoleId

            if (guildId != other.guildId) return false
            if (categoryUUID != other.categoryUUID) return false

            return true
        }

        override fun hashCode(): Int {
            var result = guildId?.hashCode() ?: 0
            result = 31 * result + (categoryUUID.hashCode())
            return result
        }

        override fun toString(): String {
            return "IAmRoleId(guildId=$guildId, iAmRoleId=$categoryUUID)"
        }
    }
}