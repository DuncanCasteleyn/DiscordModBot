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
import net.dunciboy.discord_bot.commands.Help;
import net.dunciboy.discord_bot.commands.QuitBot;
import net.dunciboy.discord_bot.utils.DataStorageParser;
import net.dv8tion.jda.core.events.ReadyEvent;
import net.dv8tion.jda.core.hooks.ListenerAdapter;

/**
 * Created by Duncan on 3/03/2017.
 * <p>
 * Will create the command needed for the role commands when the ready event is triggered.
 */
public final class CreateRoleCommandsOnReady extends ListenerAdapter {
    private final Help help;
    private final QuitBot quitBot;

    public CreateRoleCommandsOnReady(Help helpCommand, QuitBot quitBot) {
        this.help = helpCommand;
        this.quitBot = quitBot;
    }

    @Override
    public void onReady(ReadyEvent event) {
        if (event.getJDA().getSelfUser().getId().equals("232853504404881418")) {
            IAmRoles[] iAmRoles = new IAmRoles[]{
                    new IAmRoles("175856762677624832_WaifuRoles"),
                    new IAmRoles("175856762677624832_OtherRoles")
            };
            DataStorageParser.addToAutoSave(iAmRoles);
            //event.getJDA().addEventListener(roleRanking);
            CommandModule[] roleCommands = RoleCommands.createCommands(iAmRoles, quitBot);

            for (IAmRoles iAmRole : iAmRoles) {
                event.getJDA().addEventListener(iAmRole);
            }
            for (CommandModule roleCommand : roleCommands) {
                event.getJDA().addEventListener(roleCommand);
            }
            help.addCommands(roleCommands);
        }
        event.getJDA().removeEventListener(this); //cleanup
    }
}
