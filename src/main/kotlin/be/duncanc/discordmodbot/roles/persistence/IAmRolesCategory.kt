package be.duncanc.discordmodbot.roles.persistence

import jakarta.persistence.*
import jakarta.validation.constraints.NotNull
import java.io.Serializable

@Entity
@Table(name = "i_am_roles_categories")
@IdClass(IAmRolesCategory.IAmRoleId::class)
data class IAmRolesCategory(
    @Id
    @Column(updatable = false)
    val guildId: Long,

    @Id
    @GeneratedValue(generator = "i_am_roles_category_id_seq")
    @SequenceGenerator(name = "i_am_roles_category_id_seq", sequenceName = "i_am_roles_seq", allocationSize = 1)
    @Column(insertable = false, updatable = false)
    val categoryId: Long? = null,

    @Column(unique = true, nullable = false)
    var categoryName: String,

    @Column(nullable = false)
    @NotNull
    var allowedRoles: Int = 0,

    @Column(nullable = false)
    @field:NotNull
    @CollectionTable(name = "i_am_roles_category_roles")
    @ElementCollection(fetch = FetchType.EAGER)
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
        var result = guildId.hashCode()
        result = 31 * result + (categoryId?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String {
        return "IAmRolesCategory(guildId=$guildId, categoryId=$categoryId, categoryName=$categoryName, allowedRoles=$allowedRoles)"
    }


    data class IAmRoleId(
        val guildId: Long? = null,
        val categoryId: Long? = null
    ) : Serializable {

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
