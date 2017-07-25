/*
 * Copyright 2017 Duncan C.
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

package net.dunciboy.discord_bot;

import net.dunciboy.discord_bot.utils.DataStorageParser;
import net.dv8tion.jda.core.entities.Role;
import net.dv8tion.jda.core.events.role.RoleDeleteEvent;
import net.dv8tion.jda.core.hooks.ListenerAdapter;

import java.util.ArrayList;

/**
 * Created by dunci on 30/01/2017.
 * <p>
 * Allows to dynamically configure roles that can be used with the iam command.
 * <p>
 * todo add methods to set class and boolean to decide if they can obtain mutiple roles in this object or just one.
 */
public class IAmRoles extends ListenerAdapter implements DataStorageParser.StorageAutoSavable {
    //private static final SimpleLog LOG = SimpleLog.getLog(IAmRoles.class.getSimpleName());

    private final String objectId;
    private ArrayList<String> roleIds;

    /**
     * Constructor, needs an unique id for storage that stays the same so the object can reload it's data after reboot.
     * If you have multiple objects with the same id and and different roleIds corruption will occur after the next reboot!
     *
     * @param objectId an unique id for this object.
     */
    public IAmRoles(String objectId) {
        this.objectId = objectId;
        this.roleIds = loadData();
    }

    synchronized private ArrayList<String> loadData() {
        return new ArrayList<>(DataStorageParser.readListFromJson("IAmRoles_" + objectId, "Roles"));
    }

    /**
     * This method is to be used to save all the data that needs to be stored by the DataStorageParser auto saver
     */
    @Override
    synchronized public void saveAllData() {
        DataStorageParser.writeToJson("IAmRoles_" + objectId, "Roles", new ArrayList<>(roleIds));
    }

    public synchronized void roleAdd(Role role) {
        if (!roleIds.contains(role.getId())) {
            roleIds.add(role.getId());
        }
    }

    public synchronized boolean roleDel(String roleId) {
        return roleIds.remove(roleId);
    }

    public synchronized boolean isListed(Role role) {
        return roleIds.contains(role.getId());
    }

    @Override
    public void onRoleDelete(RoleDeleteEvent event) {
        roleDel(event.getRole().getId());
    }
}
