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
 *
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
