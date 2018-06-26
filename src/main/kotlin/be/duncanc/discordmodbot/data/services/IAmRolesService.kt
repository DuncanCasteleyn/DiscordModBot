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

package be.duncanc.discordmodbot.data.services

import be.duncanc.discordmodbot.data.entities.IAmRolesCategory
import be.duncanc.discordmodbot.data.repositories.IAmRolesRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Transactional(readOnly = true)
@Service
class IAmRolesService
@Autowired constructor(
        private val iAmRolesRepository: IAmRolesRepository
) {

    fun getAllCategoriesForGuild(guildId: Long): List<IAmRolesCategory> {
        return iAmRolesRepository.findByGuildId(guildId)
    }


    fun getExistingCategoryNames(guildId: Long): Set<String> {
        val list: MutableSet<String> = HashSet()
        iAmRolesRepository.findByGuildId(guildId).forEach { it.categoryName?.let { it1 -> list.add(it1) } }
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
        iAmRolesRepository.save(IAmRolesCategory(guildId, null, categoryName, allowedRoles))
    }

    @Transactional
    fun removeCategory(guildId: Long, categoryId: Long) {
        iAmRolesRepository.deleteById(IAmRolesCategory.IAmRoleId(guildId, categoryId))
    }

    @Transactional
    fun changeCategoryName(guildId: Long, categoryId: Long, newName: String) {
        val iAmRolesCategory = iAmRolesRepository.findById(IAmRolesCategory.IAmRoleId(guildId, categoryId)).orElseThrow { IllegalArgumentException("The entity does not exist within the database.") }
        iAmRolesCategory.categoryName = newName
        iAmRolesRepository.save(iAmRolesCategory)
    }

    /**
     * @return returns true when added and false when the role was removed
     */
    @Transactional
    fun addOrRemoveRole(guildId: Long, categoryId: Long, roleId: Long): Boolean {
        val iAmRolesCategory = iAmRolesRepository.findById(IAmRolesCategory.IAmRoleId(guildId, categoryId)).orElseThrow { IllegalArgumentException("The entity does not exist within the database.") }
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
        val iAmRolesCategory = iAmRolesRepository.findById(IAmRolesCategory.IAmRoleId(guildId, categoryId)).orElseThrow { IllegalArgumentException("The entity does not exist within the database.") }
        iAmRolesCategory.allowedRoles = newAmount
        iAmRolesRepository.save(iAmRolesCategory)
    }

    fun getRoleIds(guildId: Long, categoryId: Long): Set<Long> {
        val iAmRolesCategory = iAmRolesRepository.findById(IAmRolesCategory.IAmRoleId(guildId, categoryId)).orElseThrow { IllegalArgumentException("The entity does not exist within the database.") }
        return HashSet(iAmRolesCategory.roles)
    }
}