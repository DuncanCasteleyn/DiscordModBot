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

package be.duncanc.discordmodbot.commands.roles;

import be.duncanc.discordmodbot.commands.Help;
import be.duncanc.discordmodbot.commands.QuitBot;
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
            //todo rewrite
            /*IAmRoles[] iAmRoles = new IAmRoles[]{
                    new IAmRoles("175856762677624832_WaifuRoles"),
                    new IAmRoles("175856762677624832_OtherRoles")
            };
            //event.getJDA().addEventListener(roleRanking);
            CommandModule[] roleCommands = RoleCommands.createCommands();

            for (IAmRoles iAmRole : iAmRoles) {
                event.getJDA().addEventListener(iAmRole);
            }
            for (CommandModule roleCommand : roleCommands) {
                event.getJDA().addEventListener(roleCommand);
            }
            help.loadCommands(event.getJDA());
            */
        }
        event.getJDA().removeEventListener(this); //cleanup
    }
}
