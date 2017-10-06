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

package be.duncanc.discordmodbot;

import net.dv8tion.jda.core.entities.Role;
import net.dv8tion.jda.core.events.role.RoleDeleteEvent;
import net.dv8tion.jda.core.hooks.ListenerAdapter;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.util.ArrayList;

/**
 * Created by dunci on 30/01/2017.
 * <p>
 * Allows to dynamically configure roles that can be used with the iam command.
 * <p>
 */
//todo Add methods to set class and boolean to decide if they can obtain multiple roles in this object or just one.
//todo Replace DataStorageParser, move data and remove deprecation suppression
public class IAmRoles extends ListenerAdapter{
    //private static final SimpleLog LOG = SimpleLog.getLog(IAmRoles.class.getSimpleName());

    private final long guildId;
    private ArrayList<Long> roleIds;

    /**
     * Constructor, needs an unique id for storage that stays the same so the object can reload it's data after reboot.
     * If you have multiple objects with the same id and and different roleIds corruption will occur after the next reboot!
     *
     * @param guildId an unique id for this object.
     */
    public IAmRoles(long guildId) {
        this.guildId = guildId;
        //todo
        throw new NotImplementedException();
    }

    synchronized private ArrayList<String> loadData() {
        //todo
        throw new NotImplementedException();
    }

    /**
     * This method is to be used to save all the data that needs to be stored by the DataStorageParser auto saver
     */
    synchronized public void saveAllData() {
        //todo change
        throw new NotImplementedException();
    }

    public synchronized void roleAdd(Role role) {
        if (!roleIds.contains(role.getIdLong())) {
            roleIds.add(role.getIdLong());
        }
    }

    public synchronized boolean roleDel(long roleId) {
        return roleIds.remove(roleId);
    }

    public synchronized boolean isListed(Role role) {
        return roleIds.contains(role.getIdLong());
    }

    @Override
    public void onRoleDelete(RoleDeleteEvent event) {
        roleDel(event.getRole().getIdLong());
    }
}
