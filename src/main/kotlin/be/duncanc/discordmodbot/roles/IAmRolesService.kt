package be.duncanc.discordmodbot.roles

import be.duncanc.discordmodbot.roles.persistence.IAmRolesCategory
import be.duncanc.discordmodbot.roles.persistence.IAmRolesRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private const val ENTITY_DOES_NOT_EXIST_MESSAGE = "The entity does not exist within the database."

@Transactional(readOnly = true)
@Service
class IAmRolesService
@Autowired constructor(
    private val iAmRolesRepository: IAmRolesRepository
) {

    fun getCategory(guildId: Long, categoryId: Long): IAmRolesCategory {
        return iAmRolesRepository.findById(IAmRolesCategory.IAmRoleId(guildId, categoryId))
            .orElseThrow { IllegalArgumentException(ENTITY_DOES_NOT_EXIST_MESSAGE) }
    }

    fun getAllCategoriesForGuild(guildId: Long): List<IAmRolesCategory> {
        return iAmRolesRepository.findByGuildId(guildId).toList()
    }

    fun getSortedCategoriesForGuild(guildId: Long): List<IAmRolesCategory> {
        return getAllCategoriesForGuild(guildId).sortedBy { it.categoryName.lowercase() }
    }


    fun getExistingCategoryNames(guildId: Long): Set<String> {
        val list: MutableSet<String> = HashSet()
        iAmRolesRepository.findByGuildId(guildId)
            .forEach { it.categoryName.let { categoryName -> list.add(categoryName) } }
        return list
    }


    @Transactional
    fun removeRole(guildId: Long, roleId: Long) {
        val byGuildId = iAmRolesRepository.findByGuildId(guildId)
        val effectedCategories = byGuildId.filter { it.roles.contains(roleId) }.toList()
        effectedCategories.forEach {
            it.roles.remove(roleId)
        }
        iAmRolesRepository.saveAll(effectedCategories)
    }

    @Transactional
    fun addNewCategory(guildId: Long, categoryName: String, allowedRoles: Int) {
        validateCategoryNameUniqueness(guildId, categoryName)
        iAmRolesRepository.save(IAmRolesCategory(guildId, null, categoryName, allowedRoles))
    }

    @Transactional
    fun removeCategory(guildId: Long, categoryId: Long) {
        iAmRolesRepository.deleteById(IAmRolesCategory.IAmRoleId(guildId, categoryId))
    }

    @Transactional
    fun changeCategoryName(guildId: Long, categoryId: Long, newName: String) {
        validateCategoryNameUniqueness(guildId, newName, categoryId)
        val iAmRolesCategory = getCategory(guildId, categoryId)
        iAmRolesCategory.categoryName = newName
        iAmRolesRepository.save(iAmRolesCategory)
    }

    @Transactional
    fun addRoleToCategory(guildId: Long, categoryId: Long, roleId: Long) {
        val duplicateCategory = getAllCategoriesForGuild(guildId)
            .firstOrNull { it.categoryId != categoryId && it.roles.contains(roleId) }
        if (duplicateCategory != null) {
            throw IllegalArgumentException("That role is already assigned to category ${duplicateCategory.categoryName}.")
        }

        val category = getCategory(guildId, categoryId)
        if (!category.roles.add(roleId)) {
            throw IllegalArgumentException("That role is already in this category.")
        }

        iAmRolesRepository.save(category)
    }

    @Transactional
    fun removeRoleFromCategory(guildId: Long, categoryId: Long, roleId: Long) {
        val category = getCategory(guildId, categoryId)
        if (!category.roles.remove(roleId)) {
            throw IllegalArgumentException("That role is not in this category.")
        }

        iAmRolesRepository.save(category)
    }

    /**
     * @return returns true when added and false when the role was removed
     */
    @Transactional
    fun addOrRemoveRole(guildId: Long, categoryId: Long, roleId: Long): Boolean {
        val iAmRolesCategory = getCategory(guildId, categoryId)
        return if (iAmRolesCategory.roles.contains(roleId)) {
            iAmRolesCategory.roles.remove(roleId)
            false
        } else {
            iAmRolesCategory.roles.add(roleId)
            true
        }
    }

    @Transactional
    fun changeAllowedRoles(guildId: Long, categoryId: Long, newAmount: Int) {
        val iAmRolesCategory = getCategory(guildId, categoryId)
        iAmRolesCategory.allowedRoles = newAmount
        iAmRolesRepository.save(iAmRolesCategory)
    }

    fun getRoleIds(guildId: Long, categoryId: Long): Set<Long> {
        val iAmRolesCategory = getCategory(guildId, categoryId)
        return HashSet(iAmRolesCategory.roles)
    }

    fun getCategoryByRoleId(guildId: Long, role: Long): IAmRolesCategory {
        return iAmRolesRepository.findByRolesContainsAndGuildId(mutableSetOf(role), guildId).firstOrNull()
            ?: throw IllegalArgumentException(ENTITY_DOES_NOT_EXIST_MESSAGE)
    }

    private fun validateCategoryNameUniqueness(guildId: Long, categoryName: String, currentCategoryId: Long? = null) {
        val duplicateCategory = getAllCategoriesForGuild(guildId)
            .firstOrNull { it.categoryId != currentCategoryId && it.categoryName == categoryName }
        if (duplicateCategory != null) {
            throw IllegalArgumentException("The name you provided is already being used.")
        }
    }
}
