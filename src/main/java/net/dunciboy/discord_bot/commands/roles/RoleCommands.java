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

package net.dunciboy.discord_bot.commands.roles;

import net.dunciboy.discord_bot.IAmRoles;
import net.dunciboy.discord_bot.commands.CommandModule;
import net.dunciboy.discord_bot.commands.QuitBot;

/**
 * Created by Duncan on 23/02/2017.
 * <p>
 * Creates all commands in the roles package that are dependant on each other.
 */
final class RoleCommands implements QuitBot.BeforeBotQuit {
    private final net.dunciboy.discord_bot.IAmRoles[] iAmRoles;

    private RoleCommands(net.dunciboy.discord_bot.IAmRoles[] iAmRoles) {
        this.iAmRoles = iAmRoles;
    }

    static CommandModule[] createCommands(IAmRoles[] iAmRoles, QuitBot quitBot) {
        final RoleCommands roleCommands = new RoleCommands(iAmRoles);

        quitBot.addCallBeforeQuit(roleCommands);

        return new CommandModule[]{
                new IAm(roleCommands),
                new IAmNot(roleCommands),
                new IAmRolesCommand(roleCommands)
        };
    }

    net.dunciboy.discord_bot.IAmRoles[] getIAmRoles() {
        return iAmRoles;
    }

    /**
     * Actions to perform before the quit command is finished with executing.
     */
    @Override
    public void onAboutToQuit() {
        for (IAmRoles iAmRole : iAmRoles) {
            iAmRole.saveAllData();
        }
    }
}
