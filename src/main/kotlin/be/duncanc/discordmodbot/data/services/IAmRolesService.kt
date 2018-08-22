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
        return iAmRolesRepository.findByGuildId(guildId).toList()
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