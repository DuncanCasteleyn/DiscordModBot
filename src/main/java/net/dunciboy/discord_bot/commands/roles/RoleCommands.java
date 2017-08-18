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
